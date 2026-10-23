//! Windows Service Control Manager entry point.
//! The supervisor is deliberately not installed until its PostgreSQL and
//! Spring lifecycle, data ACLs, and recovery path are ready.

use std::ffi::OsString;
use std::fs;
use std::sync::mpsc;
use std::time::Duration;
use windows_service::service::{
    ServiceControl, ServiceControlAccept, ServiceExitCode, ServiceState, ServiceStatus, ServiceType,
};
use windows_service::service_control_handler::{self, ServiceControlHandlerResult};
use windows_service::{define_windows_service, service_dispatcher};

pub const SERVICE_NAME: &str = "GalaxamSirhLocal";

define_windows_service!(ffi_service_main, service_main);

pub fn dispatch() -> Result<(), String> {
    service_dispatcher::start(SERVICE_NAME, ffi_service_main)
        .map_err(|problem| format!("Connexion au gestionnaire des services impossible : {problem}"))
}

fn service_main(_arguments: Vec<OsString>) {
    if let Err(problem) = run_service() {
        eprintln!("GALAXAM Windows service failed: {problem}");
    }
}

fn status(state: ServiceState, controls: ServiceControlAccept) -> ServiceStatus {
    ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: state,
        controls_accepted: controls,
        exit_code: ServiceExitCode::Win32(0),
        checkpoint: 0,
        wait_hint: Duration::ZERO,
        process_id: None,
    }
}

fn run_service() -> windows_service::Result<()> {
    let (shutdown, received) = mpsc::channel();
    let handler = move |control| match control {
        ServiceControl::Stop | ServiceControl::Shutdown => {
            let _ = shutdown.send(());
            ServiceControlHandlerResult::NoError
        }
        ServiceControl::Interrogate => ServiceControlHandlerResult::NoError,
        _ => ServiceControlHandlerResult::NotImplemented,
    };
    let handle = service_control_handler::register(SERVICE_NAME, handler)?;
    handle.set_service_status(status(
        ServiceState::Running,
        ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
    ))?;
    let postgres = crate::database::postgres::PostgresProcess::new();
    if std::env::args_os().any(|argument| argument == "--galaxam-pg-smoke") {
        let config = crate::connection::config::DesktopConfig::new_for_setup();
        let result_path = config.home.join("service/ci-postgres-result.txt");
        let result = std::env::current_exe()
            .ok()
            .and_then(|executable| executable.parent().map(ToOwned::to_owned))
            .ok_or_else(|| "Ressources du service introuvables".to_string())
            .and_then(|resources| {
                postgres
                    .start_with_resources(&resources, &config, |_| {})
                    .map_err(str::to_string)
            });
        let text = match result {
            Ok(_) => "READY".to_string(),
            Err(problem) => format!("ERROR: {problem}"),
        };
        let _ = fs::create_dir_all(config.home.join("service"));
        let _ = fs::write(result_path, text);
    }
    let _ = received.recv();
    handle.set_service_status(status(
        ServiceState::StopPending,
        ServiceControlAccept::empty(),
    ))?;
    postgres.stop();
    handle.set_service_status(status(ServiceState::Stopped, ServiceControlAccept::empty()))?;
    Ok(())
}
