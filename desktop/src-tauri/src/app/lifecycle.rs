use crate::app::runtime::{DesktopRuntime, DesktopStatusState, RuntimeStatus};
use crate::connection::config::DesktopConfig;
use crate::platform;
use crate::platform::service;
#[cfg(not(target_os = "windows"))]
use crate::security::bootstrap::ensure_bootstrap_capability;
use std::sync::Mutex;
use tauri::{AppHandle, Emitter, Manager};

pub(crate) fn start_existing_local(app: AppHandle) -> Result<(), String> {
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
                #[cfg(target_os = "windows")]
                {
                    Some(crate::platform::elevated_bootstrap_token(&home)?)
                }
                #[cfg(not(target_os = "windows"))]
                {
                    let secrets = std::fs::read_to_string(
                        home.join("data/postgresql/config/installation.secrets"),
                    )
                    .map_err(|_| "Secrets de la base locale illisibles".to_string())?;
                    let password = secrets
                        .lines()
                        .find_map(|line| line.strip_prefix("app="))
                        .ok_or("Secret de la base locale absent")?;
                    Some(ensure_bootstrap_capability(&home, password).map_err(str::to_string)?)
                }
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
        if error.is_none() {
            current.local_available = true;
        }
        current.setup_required = token.is_some() || error.is_some();
        current.startup_error = error.clone();
    }
    let _ = app.emit("desktop-startup-complete", ());
    error.map_or(Ok(()), Err)
}

pub(crate) fn diagnostic(config: &DesktopConfig, event: &str, mode: &str) {
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

pub(crate) fn restart_with_installation(
    app: &AppHandle,
    config: &DesktopConfig,
    mode: &str,
) -> Result<(), String> {
    platform::restart_with_installation(app, config)?;
    diagnostic(config, "restart-requested", mode);
    app.exit(0);
    Ok(())
}

pub(crate) fn clear_webview_auth_session(app: &AppHandle) -> Result<(), String> {
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

pub(crate) fn read_runtime_status(
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
