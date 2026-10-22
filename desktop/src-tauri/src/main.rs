#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod backend;
mod backup;
mod postgres;
mod resources;
mod service;

use axum::body::Bytes;
use axum::extract::DefaultBodyLimit;
use axum::extract::{OriginalUri, State as AxumState};
use axum::http::{header, HeaderMap, Method, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::any;
use axum::Router;
use include_dir::{include_dir, Dir};
use percent_encoding::percent_decode_str;
use std::sync::{Arc, Mutex, RwLock};
use std::time::Duration;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter, Manager, State as TauriState, WebviewUrl, WebviewWindowBuilder};

use backend::DesktopConfig;
use postgres::PostgresProcess;

struct LocalServices {
    postgres: PostgresProcess,
}

impl LocalServices {
    fn new() -> Self {
        Self {
            postgres: PostgresProcess::new(),
        }
    }

    fn stop(&self) {
        self.postgres.stop();
    }
}

const DEFAULT_WEB_PORT: u16 = 18888;
const MAX_REQUEST_BYTES: usize = 55 * 1024 * 1024;
const CSP: &str = "default-src 'self'; connect-src 'self'; img-src 'self' blob: data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; object-src 'none'; frame-ancestors 'none'";
static FRONTEND: Dir<'_> = include_dir!("$CARGO_MANIFEST_DIR/../../frontend/dist");

#[derive(Clone)]
struct ServerState {
    client: reqwest::Client,
    web_origin: String,
    api_upstream: Arc<RwLock<Option<String>>>,
}

struct DesktopRuntime {
    config: Mutex<DesktopConfig>,
    status: Mutex<DesktopStatusState>,
    bootstrap_token: Mutex<Option<String>>,
    api_upstream: Arc<RwLock<Option<String>>>,
    tested_remote: Mutex<Option<String>>,
    backup_operation: Mutex<()>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
struct DesktopStatusState {
    mode: String,
    starting: bool,
    setup_required: bool,
    local_available: bool,
    startup_error: Option<String>,
}

#[derive(Debug, PartialEq, Eq, serde::Serialize)]
struct RuntimeStatus {
    mode: String,
    starting: bool,
    setup_required: bool,
    api_available: bool,
    local_available: bool,
    startup_error: Option<String>,
    connection_target: Option<String>,
}

#[derive(serde::Deserialize, serde::Serialize)]
struct AdminInput {
    identifiant: String,
    email: String,
    #[serde(rename = "motDePasse")]
    mot_de_passe: String,
}

#[derive(serde::Serialize)]
struct BackupResult {
    path: String,
    bytes: u64,
}

#[derive(serde::Serialize)]
struct BackupAutomationStatus {
    active: bool,
    last_success_unix_seconds: Option<u64>,
    last_archive: Option<String>,
    last_error: Option<String>,
}

async fn handle(
    AxumState(state): AxumState<Arc<ServerState>>,
    method: Method,
    OriginalUri(uri): OriginalUri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    let expected_host = state.web_origin.trim_start_matches("http://");
    if headers.get(header::HOST).map(|value| value.as_bytes()) != Some(expected_host.as_bytes()) {
        return StatusCode::FORBIDDEN.into_response();
    }
    let path = uri.path();
    if path == "/desktop-start-error" {
        if method != Method::GET && method != Method::HEAD {
            return StatusCode::METHOD_NOT_ALLOWED.into_response();
        }
        let message = "Le démarrage de GALAXAM SIRH a rencontré un problème.";
        let page = format!(
            "<!doctype html><html lang=\"fr\"><meta charset=\"utf-8\"><title>GALAXAM SIRH</title><body style=\"font:16px system-ui;max-width:680px;margin:10vh auto;padding:24px\"><h1>GALAXAM SIRH</h1><p>{message}</p><p>Vérifiez la configuration dans le dossier Application Support et les journaux du backend.</p></body></html>"
        );
        return ([(header::CONTENT_TYPE, "text/html; charset=utf-8")], page).into_response();
    }
    if path == "/api" || path.starts_with("/api/") {
        if path == "/api/desktop/setup/admin" {
            return StatusCode::NOT_FOUND.into_response();
        }
        if !matches!(
            method,
            Method::GET
                | Method::HEAD
                | Method::POST
                | Method::PUT
                | Method::PATCH
                | Method::DELETE
                | Method::OPTIONS
        ) {
            return StatusCode::METHOD_NOT_ALLOWED.into_response();
        }
        return proxy(state, method, uri, headers, body).await;
    }
    if method != Method::GET && method != Method::HEAD {
        return StatusCode::METHOD_NOT_ALLOWED.into_response();
    }

    let requested = match percent_decode_str(path.trim_start_matches('/')).decode_utf8() {
        Ok(value) => value,
        Err(_) => return StatusCode::BAD_REQUEST.into_response(),
    };
    if requested
        .split('/')
        .any(|segment| segment == "." || segment == "..")
        || requested.contains('\\')
        || requested.contains('\0')
    {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let asset = if requested.is_empty() {
        "index.html"
    } else {
        requested.as_ref()
    };
    let file = FRONTEND.get_file(asset).or_else(|| {
        if asset.contains('.') {
            None
        } else {
            FRONTEND.get_file("index.html")
        }
    });
    match file {
        Some(file) => {
            let mime = mime_guess::from_path(file.path()).first_or_octet_stream();
            (
                [
                    (header::CONTENT_TYPE, mime.as_ref()),
                    (header::CONTENT_SECURITY_POLICY, CSP),
                    (header::X_FRAME_OPTIONS, "DENY"),
                ],
                file.contents().to_vec(),
            )
                .into_response()
        }
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

async fn proxy(
    state: Arc<ServerState>,
    method: Method,
    uri: axum::http::Uri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    // The desktop server accepts browser requests only from its own origin.
    // Spring receives an internal same-origin proxy request, without a foreign Origin.
    if let Some(origin) = headers.get(header::ORIGIN) {
        if origin.as_bytes() != state.web_origin.as_bytes() {
            return StatusCode::FORBIDDEN.into_response();
        }
    }
    let Some(upstream) = state
        .api_upstream
        .read()
        .ok()
        .and_then(|value| value.clone())
    else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    let target = format!(
        "{}{}",
        upstream,
        uri.path_and_query()
            .map(|value| value.as_str())
            .unwrap_or("/api/")
    );
    let mut request = state.client.request(method, target).body(body);
    for (name, value) in &headers {
        if matches!(
            name.as_str(),
            "accept"
                | "accept-language"
                | "authorization"
                | "cache-control"
                | "content-type"
                | "cookie"
                | "if-modified-since"
                | "if-none-match"
                | "range"
                | "x-idempotency-key"
        ) {
            request = request.header(name, value);
        }
    }
    let upstream = match request.send().await {
        Ok(response) => response,
        Err(_) => return StatusCode::BAD_GATEWAY.into_response(),
    };
    let status = upstream.status();
    let upstream_headers = upstream.headers().clone();
    let bytes = match upstream.bytes().await {
        Ok(bytes) => bytes,
        Err(_) => return StatusCode::BAD_GATEWAY.into_response(),
    };
    let mut response = (status, bytes).into_response();
    // Axum assigns application/octet-stream to a byte body. Keep the upstream
    // Content-Type (JSON, PDF, etc.) as the single authoritative value.
    response.headers_mut().remove(header::CONTENT_TYPE);
    for (name, value) in &upstream_headers {
        if name != header::CONNECTION
            && name != header::CONTENT_LENGTH
            && name != header::TRANSFER_ENCODING
            && name != header::ACCESS_CONTROL_ALLOW_ORIGIN
            && name != header::ACCESS_CONTROL_ALLOW_CREDENTIALS
        {
            response.headers_mut().append(name, value.clone());
        }
    }
    response
}

fn main() {
    let web_port = std::env::args_os()
        .find_map(|arg| {
            arg.to_string_lossy()
                .strip_prefix("--galaxam-web-port=")
                .and_then(|value| value.parse::<u16>().ok())
        })
        .or_else(|| {
            std::env::var("GALAXAM_DESKTOP_WEB_PORT")
                .ok()
                .and_then(|value| value.parse::<u16>().ok())
        })
        .filter(|port| *port != 0)
        .unwrap_or(DEFAULT_WEB_PORT);
    let web_origin = format!("http://127.0.0.1:{web_port}");
    let listener = (0..50).find_map(|attempt| match std::net::TcpListener::bind(("127.0.0.1", web_port)) {
        Ok(listener) => Some(Ok(listener)),
        Err(error) if error.kind() == std::io::ErrorKind::AddrInUse && attempt < 49 => {
            if attempt == 0 { eprintln!("GALAXAM desktop listener waiting for previous window to release port {web_port}"); }
            std::thread::sleep(Duration::from_millis(100)); None
        }
        Err(error) => Some(Err(error)),
    }).unwrap_or_else(|| Err(std::io::Error::new(std::io::ErrorKind::AddrInUse, "port release timeout")));
    let listener = match listener {
        Ok(listener) => listener,
        Err(error) => {
            eprintln!("GALAXAM desktop HTTP listener unavailable on 127.0.0.1:{web_port}: {error}");
            std::process::exit(1);
        }
    };
    if let Err(error) = listener.set_nonblocking(true) {
        eprintln!("GALAXAM desktop HTTP listener configuration failed: {error}");
        std::process::exit(1);
    }
    let app = tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            get_desktop_status,
            start_local_setup,
            test_remote_api,
            apply_remote_mode,
            switch_to_local_mode,
            create_initial_admin,
            stop_local_services,
            create_local_backup,
            reveal_local_backups,
            get_local_backup_status
        ])
        .setup(move |app| {
            let processes = LocalServices::new();
            let loaded = DesktopConfig::load();
            let (config, mode, mut upstream, setup_required, mut local_valid, error, token, starting) =
                match loaded {
                    Ok(config) if config.auto_local => {
                        (config, "setup".to_string(), None, true, false, None, None, false)
                    }
                    Ok(config) if config.local_port.is_none() => (
                        config,
                        "remote".to_string(),
                        Some(
                            DesktopConfig::load()
                                .map(|c| c.api_upstream)
                                .unwrap_or_default(),
                        ),
                        false,
                        false,
                        None,
                        None,
                        false,
                    ),
                    Ok(config) => {
                        let embedded = config.embedded_postgres;
                        let upstream = if embedded { None } else { Some(config.api_upstream.clone()) };
                        (
                            config,
                            "local".into(),
                            upstream,
                            false,
                            !embedded,
                            None,
                            None,
                            embedded,
                        )
                    }
                    Err(problem) => {
                        let fallback = DesktopConfig::new_for_setup();
                        (
                            fallback,
                            "setup".into(),
                            None,
                            true,
                            false,
                            Some(problem.to_string()),
                            None,
                            false,
                        )
                    }
                };
            if mode == "remote" {
                upstream = Some(config.api_upstream.clone());
            }
            diagnostic(&config, "startup-loaded", &mode);
            local_valid = local_valid
                || (config
                    .home
                    .join("data/postgresql/cluster/PG_VERSION")
                    .is_file()
                    && config
                        .home
                        .join("data/postgresql/config/installation.ready")
                        .is_file());
            let upstream_state = Arc::new(RwLock::new(upstream));
            app.manage(processes);
            app.manage(DesktopRuntime {
                config: Mutex::new(config),
                status: Mutex::new(DesktopStatusState {
                    mode,
                    starting,
                    setup_required,
                    local_available: local_valid,
                    startup_error: error,
                }),
                bootstrap_token: Mutex::new(token),
                api_upstream: upstream_state.clone(),
                tested_remote: Mutex::new(None),
                backup_operation: Mutex::new(()),
            });
            #[cfg(unix)]
            {
                let handle = app.handle().clone();
                tauri::async_runtime::spawn(async move {
                    if let Ok(mut signal) =
                        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
                    {
                        signal.recv().await;
                        handle.exit(0);
                    }
                });
            }
            let server = Arc::new(ServerState {
                client: reqwest::Client::builder()
                    .redirect(reqwest::redirect::Policy::none())
                    .timeout(Duration::from_secs(125))
                    .build()?,
                web_origin: web_origin.clone(),
                api_upstream: upstream_state,
            });
            tauri::async_runtime::spawn(async move {
                let listener =
                    tokio::net::TcpListener::from_std(listener).expect("desktop listener");
                let router = Router::new()
                    .fallback(any(handle))
                    .layer(DefaultBodyLimit::max(MAX_REQUEST_BYTES))
                    .with_state(server);
                if let Err(error) = axum::serve(listener, router).await {
                    eprintln!("Desktop HTTP server stopped: {error}");
                }
            });
            WebviewWindowBuilder::new(app, "main", WebviewUrl::External(web_origin.parse()?))
                .title("GALAXAM SIRH")
                .inner_size(1440.0, 900.0)
                .min_inner_size(1024.0, 700.0)
                .build()?;
            if starting {
                let handle = app.handle().clone();
                tauri::async_runtime::spawn_blocking(move || {
                    let _ = start_existing_local(handle);
                });
            }
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("failed to launch GALAXAM SIRH desktop");
    // PostgreSQL and Spring Boot belong to the per-user LaunchAgent, not to the
    // lifetime of this UI process. Exiting the window must leave them running.
    app.run(|_, _| {});
}

fn start_existing_local(app: AppHandle) -> Result<(), String> {
    let state = app.state::<DesktopRuntime>();
    let configuration = state.config.lock().map(|config| {
        (
            config.home.clone(),
            config.local_port.unwrap_or(18081),
            config.postgres_port,
            config.api_upstream.clone(),
        )
    });
    let outcome = configuration
        .map_err(|_| "Configuration desktop indisponible".to_string())
        .and_then(|(home, port, pg_port, upstream)| {
            service::ensure_running(&app, &home, port, pg_port)?;
            let token = if home.join("config/bootstrap.state").is_file() {
                let secrets = std::fs::read_to_string(home.join("data/postgresql/config/installation.secrets"))
                    .map_err(|_| "Secrets de la base locale illisibles".to_string())?;
                let password = secrets.lines()
                    .find_map(|line| line.strip_prefix("app="))
                    .ok_or("Secret de la base locale absent")?;
                Some(ensure_bootstrap_capability(&home, password).map_err(str::to_string)?)
            } else {
                None
            };
            Ok((upstream, token))
        });
    let (upstream, token, error) = match outcome {
        Ok((upstream, token)) => (Some(upstream), token, None),
        Err(problem) => {
            eprintln!("GALAXAM local supervisor startup failed: {problem}");
            (None, None, Some(problem))
        }
    };
    if let Ok(mut current) = state.api_upstream.write() {
        *current = upstream;
    }
    if let Ok(mut current) = state.bootstrap_token.lock() {
        *current = token.clone();
    }
    if let Ok(mut current) = state.status.lock() {
        current.starting = false;
        current.setup_required = token.is_some() || error.is_some();
        current.startup_error = error.clone();
    }
    let _ = app.emit("desktop-startup-complete", ());
    error.map_or(Ok(()), Err)
}

const BOOTSTRAP_LIFETIME_SECS: u64 = 7 * 24 * 60 * 60;

fn ensure_bootstrap_capability(
    home: &std::path::Path,
    master_secret: &str,
) -> Result<String, &'static str> {
    use ring::digest;
    use std::fs::{self, OpenOptions};
    use std::io::Write;
    let config = home.join("config");
    if !config.exists() {
        std::fs::create_dir_all(&config)
            .map_err(|_| "Dossier de configuration impossible à créer")?;
    }
    let config_metadata =
        std::fs::symlink_metadata(&config).map_err(|_| "Dossier de configuration inaccessible")?;
    if !config_metadata.file_type().is_dir() {
        return Err("Dossier de configuration invalide");
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&config, std::fs::Permissions::from_mode(0o700))
            .map_err(|_| "Permissions du dossier de configuration invalides")?;
    }
    let state_path = config.join("bootstrap.state");
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "Horloge système invalide")?
        .as_secs();
    let old = read_bootstrap_state(&state_path).ok();
    if let Some((version, expires, fingerprint)) = old.as_ref() {
        if *expires > now {
            let token = derive_bootstrap_token(master_secret, *version);
            let expected = hex(digest::digest(&digest::SHA256, token.as_bytes()).as_ref());
            if expected == *fingerprint {
                return Ok(token);
            }
            return Err("État d'amorçage incohérent ; aucune création autorisée");
        }
    } else if state_path.exists() {
        return Err("État d'amorçage invalide ; aucune création autorisée");
    }
    let version = old
        .map(|(version, _, _)| version.saturating_add(1))
        .unwrap_or(1);
    let expires = now.saturating_add(BOOTSTRAP_LIFETIME_SECS);
    let token = derive_bootstrap_token(master_secret, version);
    let fingerprint = hex(digest::digest(&digest::SHA256, token.as_bytes()).as_ref());
    let temporary = config.join(format!("bootstrap.state.{}.tmp", std::process::id()));
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options
        .open(&temporary)
        .map_err(|_| "Écriture de l'état d'amorçage impossible")?;
    writeln!(
        file,
        "version={version}\nexpires={expires}\nfingerprint={fingerprint}"
    )
    .map_err(|_| "Écriture de l'état d'amorçage impossible")?;
    file.sync_all()
        .map_err(|_| "Synchronisation de l'état d'amorçage impossible")?;
    fs::rename(&temporary, &state_path)
        .map_err(|_| "Validation de l'état d'amorçage impossible")?;
    Ok(token)
}

fn derive_bootstrap_token(master_secret: &str, version: u64) -> String {
    let key = ring::hmac::Key::new(ring::hmac::HMAC_SHA256, master_secret.as_bytes());
    let message = format!("GALAXAM-DESKTOP-INITIAL-ADMIN-v1:{version}");
    hex(ring::hmac::sign(&key, message.as_bytes()).as_ref())
}

fn read_bootstrap_state(path: &std::path::Path) -> Result<(u64, u64, String), &'static str> {
    use std::fs;
    let metadata = fs::symlink_metadata(path).map_err(|_| "État d'amorçage absent")?;
    if !metadata.file_type().is_file() {
        return Err("État d'amorçage invalide");
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::{MetadataExt, PermissionsExt};
        if metadata.permissions().mode() & 0o077 != 0
            || metadata.uid() != unsafe { libc::geteuid() }
        {
            return Err("Permissions de l'état d'amorçage invalides");
        }
    }
    let text = fs::read_to_string(path).map_err(|_| "État d'amorçage illisible")?;
    let mut version = None;
    let mut expires = None;
    let mut fingerprint = None;
    for line in text.lines() {
        let (key, value) = line.split_once('=').ok_or("État d'amorçage invalide")?;
        match key {
            "version" if version.is_none() => version = value.parse::<u64>().ok(),
            "expires" if expires.is_none() => expires = value.parse::<u64>().ok(),
            "fingerprint"
                if fingerprint.is_none()
                    && value.len() == 64
                    && value.bytes().all(|b| b.is_ascii_hexdigit()) =>
            {
                fingerprint = Some(value.to_owned())
            }
            _ => return Err("État d'amorçage invalide"),
        }
    }
    Ok((
        version.ok_or("Version d'amorçage absente")?,
        expires.ok_or("Expiration d'amorçage absente")?,
        fingerprint.ok_or("Empreinte d'amorçage absente")?,
    ))
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|value| format!("{value:02x}")).collect()
}

#[tauri::command]
fn get_desktop_status(state: TauriState<'_, DesktopRuntime>) -> Result<RuntimeStatus, String> {
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

fn diagnostic(config: &DesktopConfig, event: &str, mode: &str) {
    use std::io::Write;
    let installation = std::fs::read_to_string(config.home.join("config/installation.id"))
        .map(|value| value.trim().to_owned())
        .unwrap_or_else(|_| "not-created".into());
    let agent_dir = std::env::args_os()
        .find_map(|arg| {
            arg.to_string_lossy()
                .strip_prefix("--galaxam-test-launch-agent-dir=")
                .filter(|value| !value.is_empty())
                .map(std::path::PathBuf::from)
        })
        .or_else(|| std::env::var_os("GALAXAM_TEST_LAUNCH_AGENT_DIR").map(std::path::PathBuf::from))
        .unwrap_or_else(|| {
            std::path::PathBuf::from(std::env::var_os("HOME").unwrap_or_default())
                .join("Library/LaunchAgents")
        });
    let agent_label = if installation == "not-created" {
        installation.clone()
    } else {
        format!("mg.galaxam.sirh.service.{installation}")
    };
    let line = format!(
        "event={event} pid={} installation={} root={} config={} mode={} api_mode={} agent_label={} agent_dir={}\n",
        std::process::id(),
        installation,
        config.home.display(),
        config.home.join("config/desktop.conf").display(),
        mode,
        if config.local_port.is_some() {
            "local"
        } else {
            "remote"
        },
        agent_label,
        agent_dir.display()
    );
    let path = config.home.join("logs/desktop-diagnostics.log");
    if std::fs::create_dir_all(path.parent().unwrap_or(&config.home)).is_ok() {
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = std::fs::set_permissions(&config.home, std::fs::Permissions::from_mode(0o700));
            if let Some(parent) = path.parent() {
                let _ = std::fs::set_permissions(parent, std::fs::Permissions::from_mode(0o700));
            }
        }
        let mut options = std::fs::OpenOptions::new();
        options.create(true).append(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            options.mode(0o600);
        }
        if let Ok(mut file) = options.open(path) {
            let _ = file.write_all(line.as_bytes());
        }
    }
    eprint!("GALAXAM-DIAGNOSTIC {line}");
}

fn restart_with_installation(
    app: &AppHandle,
    config: &DesktopConfig,
    mode: &str,
) -> Result<(), String> {
    use std::path::Component;
    let executable = tauri::process::current_binary(&app.env())
        .map_err(|_| "Exécutable GALAXAM introuvable pour le redémarrage".to_string())?;
    let macos_dir = executable.parent().ok_or("Chemin d'application invalide")?;
    if macos_dir.file_name() != Some(std::ffi::OsStr::new("MacOS"))
        || macos_dir.parent().and_then(|path| path.file_name())
            != Some(std::ffi::OsStr::new("Contents"))
    {
        return Err(
            "GALAXAM doit être lancé depuis son bundle pour appliquer le changement de mode".into(),
        );
    }
    let bundle = macos_dir
        .parent()
        .and_then(|path| path.parent())
        .ok_or("Bundle GALAXAM introuvable")?;
    if bundle
        .components()
        .any(|component| matches!(component, Component::ParentDir))
    {
        return Err("Chemin du bundle invalide".into());
    }
    let mut command = std::process::Command::new("/usr/bin/open");
    command.arg("-n").arg(bundle).arg("--args");
    for arg in app.env().args_os.iter().skip(1) {
        let text = arg.to_string_lossy();
        if !text.starts_with("--galaxam-data-home=")
            && !text.starts_with("--galaxam-web-port=")
            && !text.starts_with("--galaxam-test-launch-agent-dir=")
        {
            command.arg(arg);
        }
    }
    command.args(config.restart_arguments());
    let output = command
        .stdin(std::process::Stdio::null())
        .output()
        .map_err(|_| "Impossible de redémarrer GALAXAM".to_string())?;
    if !output.status.success() {
        return Err("macOS n'a pas accepté le redémarrage de GALAXAM".into());
    }
    diagnostic(config, "restart-requested", mode);
    app.exit(0);
    Ok(())
}

fn clear_webview_auth_session(app: &AppHandle) -> Result<(), String> {
    for window in app.webview_windows().values() {
        window
            .eval("localStorage.removeItem('galaxam-user'); sessionStorage.clear();")
            .map_err(|_| {
                "Impossible de fermer la session avant le changement de connexion".to_string()
            })?;
        let cookies = window
            .cookies()
            .map_err(|_| "Impossible de vérifier les cookies de session".to_string())?;
        for cookie in cookies {
            window.delete_cookie(cookie).map_err(|_| {
                "Impossible d'effacer les cookies avant le changement de connexion".to_string()
            })?;
        }
    }
    Ok(())
}

fn read_runtime_status(
    status: &Mutex<DesktopStatusState>,
    api_available: bool,
    configured_target: String,
) -> Result<RuntimeStatus, String> {
    // Keep status access behind one lock acquisition. This can be exercised
    // without constructing a Tauri WebView and avoids self-deadlocking reads.
    let status = status
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .clone();
    Ok(RuntimeStatus {
        connection_target: if status.mode != "setup" {
            Some(configured_target)
        } else {
            None
        },
        mode: status.mode,
        starting: status.starting,
        setup_required: status.setup_required,
        api_available,
        local_available: status.local_available,
        startup_error: status.startup_error,
    })
}

#[tauri::command]
async fn start_local_setup(app: AppHandle) -> Result<RuntimeStatus, String> {
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
            Err(cleanup) => format!("{problem}; arrêt du service provisoire impossible : {cleanup}"),
        });
    }
    if let Err(problem) = config.save_local_mode() {
        let cleanup = service::disable(&config.home);
        return Err(match cleanup {
            Ok(()) => problem.to_string(),
            Err(cleanup) => format!("{problem}; arrêt du service provisoire impossible : {cleanup}"),
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
async fn test_remote_api(
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

fn is_api_route_response(status: reqwest::StatusCode, content_type: Option<&str>) -> bool {
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
async fn apply_remote_mode(
    url: String,
    app: AppHandle,
) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || apply_remote_mode_blocking(url, app))
        .await.map_err(|_| "Changement vers le serveur distant interrompu".to_string())?
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
async fn switch_to_local_mode(app: AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || switch_to_local_mode_blocking(app))
        .await.map_err(|_| "Retour à la base locale interrompu".to_string())?
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
async fn stop_local_services(app: AppHandle) -> Result<(), String> {
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

#[tauri::command]
async fn create_local_backup(app: AppHandle, token: String) -> Result<Option<BackupResult>, String> {
    if token.is_empty() {
        return Err("Session administrateur requise".into());
    }
    let state = app.state::<DesktopRuntime>();
    let status = state.status.lock().map_err(|_| "État desktop indisponible")?.clone();
    if status.mode != "local" || status.starting || status.startup_error.is_some() {
        return Err("La base locale doit être disponible pour l’export".into());
    }
    let (home, port, upstream) = {
        let config = state.config.lock().map_err(|_| "Configuration desktop indisponible")?;
        if !config.embedded_postgres {
            return Err("Export disponible uniquement pour la base locale intégrée".into());
        }
        (config.home.clone(), config.postgres_port, config.api_upstream.clone())
    };
    authorize_backup_admin(&upstream, &token).await?;
    let destination = tauri::async_runtime::spawn_blocking(backup::choose_export_destination)
        .await.map_err(|_| "Choix du fichier d’export interrompu".to_string())??;
    let Some(destination) = destination else { return Ok(None); };
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<DesktopRuntime>();
        let _operation = state.backup_operation.try_lock()
            .map_err(|_| "Une sauvegarde est déjà en cours".to_string())?;
        let path = backup::create(&app, &home, port)?;
        let bytes = backup::export_copy(&path, &destination)?;
        Ok(Some(BackupResult { path: destination.to_string_lossy().into_owned(), bytes }))
    }).await.map_err(|_| "Sauvegarde interrompue".to_string())?
}

async fn authorize_backup_admin(upstream: &str, token: &str) -> Result<(), String> {
    if token.is_empty() { return Err("Session administrateur requise".into()); }
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(10))
        .build().map_err(|_| "Client réseau indisponible")?;
    let response = client
        .get(format!("{upstream}/api/admin/desktop-backup/authorize"))
        .bearer_auth(token)
        .send().await
        .map_err(|_| "API locale indisponible".to_string())?;
    if response.status() == reqwest::StatusCode::NO_CONTENT { return Ok(()); }
    if response.status() == reqwest::StatusCode::NOT_FOUND {
        // An already installed service may still run the previous bundled JAR.
        // Its existing security route is admin-only and supports HEAD.
        let legacy = client
            .head(format!("{upstream}/api/securite/utilisateurs"))
            .bearer_auth(token)
            .send().await
            .map_err(|_| "API locale indisponible".to_string())?;
        if legacy.status().is_success() { return Ok(()); }
    }
    Err("Autorisation administrateur refusée".into())
}

#[tauri::command]
async fn get_local_backup_status(app: AppHandle, token: String) -> Result<BackupAutomationStatus, String> {
    let state = app.state::<DesktopRuntime>();
    let (home, upstream, embedded) = {
        let config = state.config.lock().map_err(|_| "Configuration desktop indisponible")?;
        (config.home.clone(), config.api_upstream.clone(), config.embedded_postgres)
    };
    if !embedded { return Err("Sauvegarde automatique locale indisponible".into()); }
    authorize_backup_admin(&upstream, &token).await?;
    tauri::async_runtime::spawn_blocking(move || {
        let backups = home.join("backups");
        let last_success_unix_seconds = std::fs::read_to_string(backups.join(".automatic-last-success"))
            .ok().and_then(|value| value.trim().parse::<u64>().ok());
        let last_archive = std::fs::read_dir(&backups).ok().into_iter().flatten()
            .filter_map(Result::ok)
            .filter_map(|entry| {
                let name = entry.file_name().into_string().ok()?;
                if name.starts_with("galaxam-auto-") && name.ends_with(".tar")
                    && entry.file_type().ok()?.is_file() { Some(name) } else { None }
            }).max();
        let last_error = std::fs::read_to_string(home.join("logs/backup.log"))
            .ok().and_then(|log| log.lines().last().map(str::to_owned))
            .filter(|line| line.contains("échouée"));
        Ok(BackupAutomationStatus {
            active: service::backup_agent_active(&home),
            last_success_unix_seconds,
            last_archive,
            last_error,
        })
    }).await.map_err(|_| "État des sauvegardes interrompu".to_string())?
}

#[tauri::command]
async fn reveal_local_backups(app: AppHandle) -> Result<(), String> {
    let state = app.state::<DesktopRuntime>();
    let home = state.config.lock()
        .map_err(|_| "Configuration desktop indisponible")?
        .home.clone();
    let backups = home.join("backups");
    tauri::async_runtime::spawn_blocking(move || {
        if !backups.is_dir() {
            return Err("Aucun dossier de sauvegardes disponible".to_string());
        }
        let result = std::process::Command::new("/usr/bin/open")
            .arg(&backups)
            .status()
            .map_err(|_| "Impossible d’ouvrir le dossier des sauvegardes".to_string())?;
        if result.success() { Ok(()) } else { Err("Impossible d’ouvrir le dossier des sauvegardes".into()) }
    }).await.map_err(|_| "Ouverture du dossier interrompue".to_string())?
}

#[tauri::command]
async fn create_initial_admin(
    input: AdminInput,
    state: TauriState<'_, DesktopRuntime>,
) -> Result<(), String> {
    {
        let status = state
            .status
            .lock()
            .map_err(|_| "État desktop indisponible")?;
        if status.mode != "local" || !status.setup_required {
            return Err("La création initiale n'est pas disponible".into());
        }
    }
    let token = state
        .bootstrap_token
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .clone()
        .ok_or("Jeton d'amorçage indisponible ; redémarrez l'application")?;
    let upstream = state
        .api_upstream
        .read()
        .map_err(|_| "État API indisponible")?
        .clone()
        .ok_or("API locale indisponible")?;
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(15))
        .build()
        .map_err(|_| "Client réseau indisponible")?;
    let response = client
        .post(format!("{upstream}/api/desktop/setup/admin"))
        .bearer_auth(token)
        .json(&input)
        .send()
        .await
        .map_err(|_| "API locale indisponible")?;
    if response.status() == reqwest::StatusCode::CREATED {
        *state
            .bootstrap_token
            .lock()
            .map_err(|_| "État desktop indisponible")? = None;
        let config = state
            .config
            .lock()
            .map_err(|_| "Configuration desktop indisponible")?;
        let _ = std::fs::remove_file(config.home.join("config/bootstrap.state"));
        state
            .status
            .lock()
            .map_err(|_| "État desktop indisponible")?
            .setup_required = false;
        return Ok(());
    }
    let status = response.status();
    let body: serde_json::Value = response.json().await.unwrap_or_default();
    Err(body
        .get("message")
        .and_then(|v| v.as_str())
        .map(str::to_owned)
        .unwrap_or_else(|| format!("Création refusée ({}).", status.as_u16())))
}

#[cfg(test)]
mod tests {
    use super::{
        derive_bootstrap_token, ensure_bootstrap_capability, is_api_route_response,
        read_runtime_status, DesktopStatusState, RuntimeStatus,
    };
    use std::fs;
    use std::sync::Mutex;

    #[test]
    fn api_compatibility_probe_accepts_json_or_auth_challenge_but_rejects_html_placeholders() {
        assert!(is_api_route_response(
            reqwest::StatusCode::UNAUTHORIZED,
            None
        ));
        assert!(is_api_route_response(
            reqwest::StatusCode::OK,
            Some("application/json; charset=utf-8")
        ));
        assert!(is_api_route_response(
            reqwest::StatusCode::OK,
            Some("application/problem+json")
        ));
        assert!(!is_api_route_response(
            reqwest::StatusCode::OK,
            Some("text/html; charset=utf-8")
        ));
        assert!(!is_api_route_response(
            reqwest::StatusCode::NOT_FOUND,
            Some("application/json")
        ));
    }

    #[test]
    fn desktop_status_snapshot_is_readable_without_webview_or_reentrant_locking() {
        let state = Mutex::new(DesktopStatusState {
            mode: "local".into(),
            starting: false,
            setup_required: true,
            local_available: true,
            startup_error: None,
        });

        let status = read_runtime_status(&state, true, "http://127.0.0.1:18081".into())
            .expect("status snapshot should be readable");

        assert_eq!(
            status,
            RuntimeStatus {
                mode: "local".into(),
                starting: false,
                setup_required: true,
                api_available: true,
                local_available: true,
                startup_error: None,
                connection_target: Some("http://127.0.0.1:18081".into()),
            }
        );
    }

    #[test]
    fn bootstrap_capability_survives_restart_without_persisting_bearer_secret() {
        let root = std::env::temp_dir().join(format!("galaxam-bootstrap-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(root.join("config")).unwrap();
        let first = ensure_bootstrap_capability(&root, &"ab".repeat(32)).unwrap();
        assert_eq!(
            first,
            "b10d6146921d2964bc20b90b6a0d51b16f1a38e9b7b19d9aa8f0cdf81fa4687b"
        );
        let after_restart = ensure_bootstrap_capability(&root, &"ab".repeat(32)).unwrap();
        assert_eq!(first, after_restart);
        let persisted = fs::read_to_string(root.join("config/bootstrap.state")).unwrap();
        assert!(!persisted.contains(&first));
        assert!(persisted.contains("fingerprint="));
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            assert_eq!(
                fs::metadata(root.join("config/bootstrap.state"))
                    .unwrap()
                    .permissions()
                    .mode()
                    & 0o777,
                0o600
            );
        }
        fs::write(
            root.join("config/bootstrap.state"),
            format!("version=1\nexpires=1\nfingerprint={}\n", "0".repeat(64)),
        )
        .unwrap();
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(
                root.join("config/bootstrap.state"),
                fs::Permissions::from_mode(0o600),
            )
            .unwrap();
        }
        let renewed = ensure_bootstrap_capability(&root, &"ab".repeat(32)).unwrap();
        assert_ne!(renewed, derive_bootstrap_token(&"ab".repeat(32), 1));
        assert_ne!(renewed, first);
        fs::remove_dir_all(root).unwrap();
    }
}
