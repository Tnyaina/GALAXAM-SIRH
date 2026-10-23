#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod app;
mod backup;
mod connection;
mod database;
mod platform;
mod security;
mod web;

use std::sync::{Arc, Mutex, RwLock};
use std::time::Duration;
use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};

use app::runtime::{
    BackupAutomationStatus, BackupResult, DesktopRuntime, DesktopStatusState, LocalServices,
};
use connection::config::DesktopConfig;

const DEFAULT_WEB_PORT: u16 = 18888;

fn main() {
    #[cfg(target_os = "windows")]
    if std::env::args_os().any(|argument| argument == "--galaxam-install-service") {
        if let Err(problem) = platform::install_service() {
            eprintln!("GALAXAM service installation failed: {problem}");
            std::process::exit(1);
        }
        return;
    }
    #[cfg(target_os = "windows")]
    if std::env::args_os().any(|argument| argument == "--galaxam-remove-service") {
        if let Err(problem) = platform::remove_service() {
            eprintln!("GALAXAM service removal failed: {problem}");
            std::process::exit(1);
        }
        return;
    }
    #[cfg(target_os = "windows")]
    if platform::is_bootstrap_helper() {
        if let Err(problem) = platform::run_bootstrap_helper() {
            eprintln!("GALAXAM bootstrap helper: {problem}");
            std::process::exit(1);
        }
        return;
    }
    #[cfg(target_os = "windows")]
    if std::env::args_os().any(|argument| argument == "--galaxam-service") {
        if let Err(problem) = platform::windows_service_runtime::dispatch() {
            eprintln!("GALAXAM Windows service: {problem}");
            std::process::exit(1);
        }
        return;
    }
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
            connection::commands::get_desktop_status,
            connection::commands::start_local_setup,
            connection::commands::test_remote_api,
            connection::commands::apply_remote_mode,
            connection::commands::switch_to_local_mode,
            security::admin::create_initial_admin,
            connection::commands::stop_local_services,
            backup::commands::create_local_backup,
            backup::commands::reveal_local_backups,
            backup::commands::get_local_backup_status
        ])
        .setup(move |app| {
            let processes = LocalServices::new();
            let loaded = DesktopConfig::load();
            let (
                config,
                mode,
                mut upstream,
                setup_required,
                mut local_valid,
                error,
                token,
                starting,
            ) = match loaded {
                Ok(config) if config.auto_local => (
                    config,
                    "setup".to_string(),
                    None,
                    true,
                    false,
                    None,
                    None,
                    false,
                ),
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
                    let upstream = if embedded {
                        None
                    } else {
                        Some(config.api_upstream.clone())
                    };
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
            app::lifecycle::diagnostic(&config, "startup-loaded", &mode);
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
            let server = web::server::ServerState::new(web_origin.clone(), upstream_state)?;
            tauri::async_runtime::spawn(async move {
                if let Err(error) = web::server::serve(listener, server).await {
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
                    let _ = app::lifecycle::start_existing_local(handle);
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

#[cfg(test)]
mod tests {
    use super::DesktopStatusState;
    use crate::app::lifecycle::read_runtime_status;
    use crate::app::runtime::RuntimeStatus;
    use crate::connection::commands::is_api_route_response;
    use crate::security::bootstrap::{derive_bootstrap_token, ensure_bootstrap_capability};
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
