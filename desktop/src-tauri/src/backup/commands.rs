use crate::platform::service;
use crate::{backup, BackupAutomationStatus, BackupResult, DesktopRuntime};
use std::time::Duration;
use tauri::{AppHandle, Manager};

#[tauri::command]
pub(crate) async fn create_local_backup(
    app: AppHandle,
    token: String,
) -> Result<Option<BackupResult>, String> {
    if token.is_empty() {
        return Err("Session administrateur requise".into());
    }
    let state = app.state::<DesktopRuntime>();
    let status = state
        .status
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .clone();
    if status.mode != "local" || status.starting || status.startup_error.is_some() {
        return Err("La base locale doit être disponible pour l’export".into());
    }
    let (home, port, upstream) = {
        let config = state
            .config
            .lock()
            .map_err(|_| "Configuration desktop indisponible")?;
        if !config.embedded_postgres {
            return Err("Export disponible uniquement pour la base locale intégrée".into());
        }
        (
            config.home.clone(),
            config.postgres_port,
            config.api_upstream.clone(),
        )
    };
    authorize_backup_admin(&upstream, &token).await?;
    let destination =
        tauri::async_runtime::spawn_blocking(crate::platform::choose_export_destination)
            .await
            .map_err(|_| "Choix du fichier d’export interrompu".to_string())??;
    let Some(destination) = destination else {
        return Ok(None);
    };
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<DesktopRuntime>();
        let _operation = state
            .backup_operation
            .try_lock()
            .map_err(|_| "Une sauvegarde est déjà en cours".to_string())?;
        #[cfg(windows)]
        let bytes = crate::platform::elevated_backup_export(&home, port, &destination)?;
        #[cfg(not(windows))]
        let bytes = {
            let path = backup::create(&app, &home, port)?;
            backup::export_copy(&path, &destination)?
        };
        Ok(Some(BackupResult {
            path: destination.to_string_lossy().into_owned(),
            bytes,
        }))
    })
    .await
    .map_err(|_| "Sauvegarde interrompue".to_string())?
}

#[tauri::command]
pub(crate) async fn restore_local_backup(
    app: AppHandle,
    token: String,
) -> Result<Option<String>, String> {
    let state = app.state::<DesktopRuntime>();
    let (home, port, api_port, upstream, embedded) = {
        let config = state
            .config
            .lock()
            .map_err(|_| "Configuration desktop indisponible")?;
        (
            config.home.clone(),
            config.postgres_port,
            config.local_port.unwrap_or(0),
            config.api_upstream.clone(),
            config.embedded_postgres,
        )
    };
    if !embedded || api_port == 0 {
        return Err(
            "Restauration disponible uniquement quand les services locaux sont actifs".into(),
        );
    }
    let status = state
        .status
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .clone();
    if status.mode != "local" || status.starting || status.startup_error.is_some() {
        return Err("La base locale doit être disponible pour la restauration".into());
    }
    authorize_backup_admin(&upstream, &token).await?;
    let Some(archive) =
        tauri::async_runtime::spawn_blocking(crate::platform::choose_restore_source)
            .await
            .map_err(|_| "Sélection de l’archive interrompue".to_string())??
    else {
        return Ok(None);
    };
    let app_for_restore = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let state = app_for_restore.state::<DesktopRuntime>();
        let _operation = state
            .backup_operation
            .try_lock()
            .map_err(|_| "Une opération sur les sauvegardes est déjà en cours".to_string())?;
        crate::platform::restore_local_backup(&app_for_restore, &home, api_port, port, &archive)?;
        Ok(Some(archive.to_string_lossy().into_owned()))
    })
    .await
    .map_err(|_| "Restauration interrompue".to_string())?
}

async fn authorize_backup_admin(upstream: &str, token: &str) -> Result<(), String> {
    if token.is_empty() {
        return Err("Session administrateur requise".into());
    }
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(10))
        .build()
        .map_err(|_| "Client réseau indisponible")?;
    let response = client
        .get(format!("{upstream}/api/admin/desktop-backup/authorize"))
        .bearer_auth(token)
        .send()
        .await
        .map_err(|_| "API locale indisponible".to_string())?;
    if response.status() == reqwest::StatusCode::NO_CONTENT {
        return Ok(());
    }
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        // An already installed service may still run the previous bundled JAR.
        // Its existing security route is admin-only and supports HEAD.
        let legacy = client
            .head(format!("{upstream}/api/securite/utilisateurs"))
            .bearer_auth(token)
            .send()
            .await
            .map_err(|_| "API locale indisponible".to_string())?;
        if legacy.status().is_success() {
            return Ok(());
        }
    }
    Err("Autorisation administrateur refusée".into())
}

#[tauri::command]
pub(crate) async fn get_local_backup_status(
    app: AppHandle,
    token: String,
) -> Result<BackupAutomationStatus, String> {
    let state = app.state::<DesktopRuntime>();
    let (home, upstream, embedded) = {
        let config = state
            .config
            .lock()
            .map_err(|_| "Configuration desktop indisponible")?;
        (
            config.home.clone(),
            config.api_upstream.clone(),
            config.embedded_postgres,
        )
    };
    if !embedded {
        return Err("Sauvegarde automatique locale indisponible".into());
    }
    authorize_backup_admin(&upstream, &token).await?;
    tauri::async_runtime::spawn_blocking(move || {
        #[cfg(not(windows))]
        let backups = home.join("backups");
        #[cfg(windows)]
        let (last_success_unix_seconds, last_archive, last_error) = {
            let config = home.join("config");
            let last_success = std::fs::read_to_string(config.join("backup-last-success"))
                .ok()
                .and_then(|value| value.trim().parse::<u64>().ok());
            let last_archive = std::fs::read_to_string(config.join("backup-last-archive"))
                .ok()
                .map(|value| value.trim().to_owned())
                .filter(|value| !value.is_empty());
            let last_error = std::fs::read_to_string(config.join("backup-last-error"))
                .ok()
                .map(|value| format!("sauvegarde automatique échouée : {value}"));
            (last_success, last_archive, last_error)
        };
        #[cfg(not(windows))]
        let (last_success_unix_seconds, last_archive, last_error) = {
            let last_success_unix_seconds =
                std::fs::read_to_string(backups.join(".automatic-last-success"))
                    .ok()
                    .and_then(|value| value.trim().parse::<u64>().ok());
            let last_archive = std::fs::read_dir(&backups)
                .ok()
                .into_iter()
                .flatten()
                .filter_map(Result::ok)
                .filter_map(|entry| {
                    let name = entry.file_name().into_string().ok()?;
                    if name.starts_with("galaxam-auto-")
                        && name.ends_with(".tar")
                        && entry.file_type().ok()?.is_file()
                    {
                        Some(name)
                    } else {
                        None
                    }
                })
                .max();
            let last_error = std::fs::read_to_string(home.join("logs/backup.log"))
                .ok()
                .and_then(|log| log.lines().last().map(str::to_owned))
                .filter(|line| line.contains("échouée"));
            (last_success_unix_seconds, last_archive, last_error)
        };
        Ok(BackupAutomationStatus {
            active: service::backup_agent_active(&home),
            last_success_unix_seconds,
            last_archive,
            last_error,
        })
    })
    .await
    .map_err(|_| "État des sauvegardes interrompu".to_string())?
}

#[tauri::command]
pub(crate) async fn reveal_local_backups(app: AppHandle) -> Result<(), String> {
    let state = app.state::<DesktopRuntime>();
    let home = state
        .config
        .lock()
        .map_err(|_| "Configuration desktop indisponible")?
        .home
        .clone();
    let backups = home.join("backups");
    tauri::async_runtime::spawn_blocking(move || {
        if !backups.is_dir() {
            return Err("Aucun dossier de sauvegardes disponible".to_string());
        }
        crate::platform::reveal_backups(&backups)
    })
    .await
    .map_err(|_| "Ouverture du dossier interrompue".to_string())?
}
