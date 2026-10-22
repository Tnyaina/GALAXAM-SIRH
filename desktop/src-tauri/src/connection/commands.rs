use crate::app::lifecycle::{
    clear_webview_auth_session, diagnostic, read_runtime_status, restart_with_installation,
    start_existing_local,
};
use crate::app::runtime::{DesktopRuntime, DesktopStatusState, LocalServices, RuntimeStatus};
use crate::platform::service;
use crate::security::bootstrap::ensure_bootstrap_capability;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State as TauriState};

#[tauri::command]
pub(crate) fn get_desktop_status(
    state: TauriState<'_, DesktopRuntime>,
) -> Result<RuntimeStatus, String> {
    let config = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?;
    let api_available = state
        .api_upstream
        .read()
        .map_err(|_| "État API indisponible")?
        .is_some();
    let runtime = read_runtime_status(&state.status, api_available, config.api_upstream.clone())?;
    let configured_mode = if config.local_port.is_some() {
        "local"
    } else {
        "remote"
    };
    if runtime.mode != "setup" && runtime.mode != configured_mode {
        diagnostic(&config, "mode-divergence", &runtime.mode);
        return Err("L’état de connexion en mémoire ne correspond pas à la configuration enregistrée. Redémarrez GALAXAM pour rétablir un état cohérent.".into());
    }
    diagnostic(&config, "status-returned", &runtime.mode);
    Ok(runtime)
}

#[tauri::command]
pub(crate) async fn start_local_setup(app: AppHandle) -> Result<RuntimeStatus, String> {
    tauri::async_runtime::spawn_blocking(move || start_local_setup_blocking(app))
        .await
        .map_err(|_| "Préparation locale interrompue".to_string())?
}

fn start_local_setup_blocking(app: AppHandle) -> Result<RuntimeStatus, String> {
    let state = app.state::<DesktopRuntime>();
    let current_status = state
        .status
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .clone();
    if current_status.mode != "setup" && current_status.startup_error.is_none() {
        return Err("La configuration initiale est déjà terminée".into());
    }
    if current_status.mode == "local" {
        start_existing_local(app.clone())?;
        return get_desktop_status(state);
    }
    let config = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?;
    let processes = app.state::<LocalServices>();
    let _ = app.emit("setup-progress", "database");
    let database = processes.postgres.start(&app, &config)?;
    let has_admin = database.has_admin;
    let capability = if has_admin {
        let _ = std::fs::remove_file(config.home.join("config/bootstrap.state"));
        None
    } else {
        match ensure_bootstrap_capability(&config.home, &database.app_password) {
            Ok(value) => Some(value),
            Err(problem) => {
                processes.stop();
                return Err(problem.into());
            }
        }
    };
    let _ = app.emit("setup-progress", "backend");
    processes.stop();
    let port = config.local_port.unwrap_or(18081);
    if let Err(problem) = service::ensure_running(&app, &config.home, port, config.postgres_port) {
        let cleanup = if config.home.join("config/installation.id").is_file() {
            service::disable(&config.home)
        } else {
            Ok(())
        };
        return Err(match cleanup {
            Ok(()) => problem,
            Err(cleanup) => {
                format!("{problem}; arrêt du service provisoire impossible : {cleanup}")
            }
        });
    }
    if let Err(problem) = config.save_local_mode() {
        let cleanup = service::disable(&config.home);
        return Err(match cleanup {
            Ok(()) => problem.to_string(),
            Err(cleanup) => {
                format!("{problem}; arrêt du service provisoire impossible : {cleanup}")
            }
        });
    }
    *state
        .api_upstream
        .write()
        .map_err(|_| "État API indisponible")? = Some(config.api_upstream.clone());
    *state
        .status
        .lock()
        .map_err(|_| "État desktop indisponible")? = DesktopStatusState {
        mode: "local".into(),
        starting: false,
        setup_required: !has_admin,
        local_available: true,
        startup_error: None,
    };
    *state
        .bootstrap_token
        .lock()
        .map_err(|_| "État desktop indisponible")? = capability;
    drop(config);
    get_desktop_status(state)
}

#[tauri::command]
pub(crate) async fn test_remote_api(
    url: String,
    state: TauriState<'_, DesktopRuntime>,
) -> Result<String, String> {
    let origin = validate_remote_origin(&url)?;
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(10))
        .build()
        .map_err(|_| "Client réseau indisponible")?;
    let response = client
        .get(format!("{origin}/api/auth/ping"))
        .send()
        .await
        .map_err(|_| "Serveur inaccessible ; vérifiez l'adresse et le réseau")?;
    if !response.status().is_success() {
        return Err(format!(
            "L'API a répondu avec le statut {}",
            response.status().as_u16()
        ));
    }
    let payload: serde_json::Value = response
        .json()
        .await
        .map_err(|_| "Cette adresse ne répond pas comme une API GALAXAM")?;
    if payload.get("status").and_then(|value| value.as_str()) != Some("UP") {
        return Err("L'API GALAXAM n'a pas confirmé sa disponibilité".into());
    }
    // A health endpoint alone can be served by a placeholder or a web frontend
    // while the actual Spring API routes are missing. HEAD avoids reading any
    // payroll data while checking the expected protected API route.
    let route = client
        .head(format!("{origin}/api/periodes"))
        .header(reqwest::header::ACCEPT, "application/json")
        .send()
        .await
        .map_err(|_| {
            "Le serveur répond au contrôle de santé, mais l’API GALAXAM est inaccessible"
                .to_string()
        })?;
    if !is_api_route_response(
        route.status(),
        route
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok()),
    ) {
        return Err("Le contrôle de santé répond, mais les routes métier GALAXAM sont introuvables. Vérifiez l’adresse du backend et le routage /api.".into());
    }
    *state
        .tested_remote
        .lock()
        .map_err(|_| "État distant indisponible")? = Some(origin.clone());
    Ok(origin)
}

pub(crate) fn is_api_route_response(
    status: reqwest::StatusCode,
    content_type: Option<&str>,
) -> bool {
    if matches!(
        status,
        reqwest::StatusCode::UNAUTHORIZED | reqwest::StatusCode::FORBIDDEN
    ) {
        return true;
    }
    let json_response = content_type
        .and_then(|value| value.split(';').next())
        .map(str::trim)
        .is_some_and(|value| {
            value.eq_ignore_ascii_case("application/json")
                || value.to_ascii_lowercase().ends_with("+json")
        });
    status.is_success() && json_response
}

fn validate_remote_origin(value: &str) -> Result<String, String> {
    let url = reqwest::Url::parse(value.trim()).map_err(|_| "Adresse invalide")?;
    if url.scheme() != "https"
        && !(url.scheme() == "http" && matches!(url.host_str(), Some("127.0.0.1" | "localhost")))
    {
        return Err("Utilisez une adresse HTTPS sécurisée".into());
    }
    if !url.username().is_empty()
        || url.password().is_some()
        || url.path() != "/"
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err("Saisissez uniquement l'adresse de base du serveur".into());
    }
    Ok(url.as_str().trim_end_matches('/').to_owned())
}

#[tauri::command]
pub(crate) async fn apply_remote_mode(url: String, app: AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || apply_remote_mode_blocking(url, app))
        .await
        .map_err(|_| "Changement vers le serveur distant interrompu".to_string())?
}

fn apply_remote_mode_blocking(url: String, app: AppHandle) -> Result<(), String> {
    let state = app.state::<DesktopRuntime>();
    let origin = validate_remote_origin(&url)?;
    if state
        .tested_remote
        .lock()
        .map_err(|_| "État distant indisponible")?
        .as_deref()
        != Some(origin.as_str())
    {
        return Err("Testez cette adresse avant de l'enregistrer".into());
    }
    clear_webview_auth_session(&app)?;
    let home = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?
        .home
        .clone();
    let was_local = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?
        .local_port
        .is_some();
    if was_local && home.join("config/installation.id").is_file() {
        service::disable(&home)?;
    }
    let save_result = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?
        .save_remote_mode(&origin);
    if let Err(problem) = save_result {
        if was_local {
            let config = state
                .config
                .lock()
                .map_err(|_| "Configuration desktop indisponible")?;
            if let Err(recovery) = service::ensure_running(
                &app,
                &home,
                config.local_port.unwrap_or(18081),
                config.postgres_port,
            ) {
                return Err(format!("{problem}; le redémarrage du service local a aussi échoué : {recovery}. Relancez GALAXAM pour reprendre le mode local."));
            }
        }
        return Err(problem.to_string());
    }
    {
        let mut status = state
            .status
            .lock()
            .map_err(|_| "État desktop indisponible")?;
        status.mode = "remote".into();
        status.setup_required = false;
    }
    let configured_api = origin.clone();
    *state
        .api_upstream
        .write()
        .map_err(|_| "État API indisponible")? = Some(origin);
    {
        let mut config = state
            .config
            .lock()
            .map_err(|_| "Configuration desktop indisponible")?;
        config.local_port = None;
        config.embedded_postgres = false;
        config.auto_local = false;
        config.api_upstream = configured_api;
    }
    let config = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?;
    diagnostic(&config, "mode-saved", "remote");
    restart_with_installation(&app, &config, "remote")
}

#[tauri::command]
pub(crate) async fn switch_to_local_mode(app: AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || switch_to_local_mode_blocking(app))
        .await
        .map_err(|_| "Retour à la base locale interrompu".to_string())?
}

fn switch_to_local_mode_blocking(app: AppHandle) -> Result<(), String> {
    let state = app.state::<DesktopRuntime>();
    if !state
        .status
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .local_available
    {
        return Err("Aucune installation locale valide n'est disponible".into());
    }
    clear_webview_auth_session(&app)?;
    let mut config = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?;
    let cluster = config.home.join("data/postgresql/cluster");
    if !cluster.join("PG_VERSION").is_file()
        || !config
            .home
            .join("data/postgresql/config/installation.ready")
            .is_file()
    {
        return Err("La base locale existante ne peut pas être validée".into());
    }
    let previous_config = (
        config.local_port,
        config.embedded_postgres,
        config.auto_local,
        config.api_upstream.clone(),
    );
    config.local_port = Some(18081);
    config.embedded_postgres = true;
    config.auto_local = false;
    config.api_upstream = format!("http://127.0.0.1:{}", config.local_port.unwrap_or(18081));
    let processes = app.state::<LocalServices>();
    if let Err(problem) = processes.postgres.start(&app, &config) {
        config.local_port = previous_config.0;
        config.embedded_postgres = previous_config.1;
        config.auto_local = previous_config.2;
        config.api_upstream = previous_config.3;
        return Err(problem.into());
    }
    processes.postgres.stop();
    if let Err(problem) = service::ensure_running(
        &app,
        &config.home,
        config.local_port.unwrap_or(18081),
        config.postgres_port,
    ) {
        config.local_port = previous_config.0;
        config.embedded_postgres = previous_config.1;
        config.auto_local = previous_config.2;
        config.api_upstream = previous_config.3;
        let cleanup = service::disable(&config.home);
        return Err(match cleanup {
            Ok(()) => problem,
            Err(cleanup) => {
                format!("{problem}; le service local n'a pas pu être désactivé : {cleanup}")
            }
        });
    }
    if let Err(problem) = config.save_local_mode() {
        let cleanup = service::disable(&config.home);
        config.local_port = previous_config.0;
        config.embedded_postgres = previous_config.1;
        config.auto_local = previous_config.2;
        config.api_upstream = previous_config.3;
        return Err(match cleanup {
            Ok(()) => problem.to_string(),
            Err(cleanup) => {
                format!("{problem}; le service local n'a pas pu être désactivé : {cleanup}")
            }
        });
    }
    *state
        .api_upstream
        .write()
        .map_err(|_| "État API indisponible")? = Some(config.api_upstream.clone());
    state
        .status
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .mode = "local".into();
    diagnostic(&config, "mode-saved", "local");
    restart_with_installation(&app, &config, "local")
}

#[tauri::command]
pub(crate) async fn stop_local_services(app: AppHandle) -> Result<(), String> {
    let state = app.state::<DesktopRuntime>();
    let home = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible".to_string())
        .and_then(|config| {
            if config.local_port.is_none() {
                Err("Aucun service local n'est actif en mode distant".to_string())
            } else {
                Ok(config.home.clone())
            }
        })?;
    tauri::async_runtime::spawn_blocking(move || service::disable(&home))
        .await
        .map_err(|_| "Arrêt du service local interrompu".to_string())?
}
