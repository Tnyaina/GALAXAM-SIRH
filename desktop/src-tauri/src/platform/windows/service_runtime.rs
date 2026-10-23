//! Windows SCM supervisor for the embedded database and Spring API.

use ring::rand::SecureRandom;
use std::ffi::OsString;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::net::{TcpStream, ToSocketAddrs};
use std::ops::{Deref, DerefMut};
use std::path::Path;
use std::process::{Child, Command, Stdio};
use std::sync::mpsc;
use std::thread;
use std::time::{Duration, Instant};
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
        record_service_diagnostic(
            &crate::connection::config::DesktopConfig::new_for_setup().home,
            &format!("Service dispatcher failed: {problem}"),
        );
        if has_argument("--galaxam-pg-smoke") || has_argument("--galaxam-full-smoke") {
            write_smoke_result(
                &crate::connection::config::DesktopConfig::new_for_setup().home,
                &format!("ERROR: {problem}"),
            );
        }
    }
}

fn status(state: ServiceState, controls: ServiceControlAccept, checkpoint: u32) -> ServiceStatus {
    ServiceStatus {
        service_type: ServiceType::OWN_PROCESS,
        current_state: state,
        controls_accepted: controls,
        exit_code: ServiceExitCode::Win32(0),
        checkpoint,
        wait_hint: if state == ServiceState::StartPending || state == ServiceState::StopPending {
            Duration::from_secs(180)
        } else {
            Duration::ZERO
        },
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
        ServiceState::StartPending,
        ServiceControlAccept::empty(),
        1,
    ))?;

    match supervise(&handle, &received) {
        Ok(()) => {
            handle.set_service_status(status(
                ServiceState::StopPending,
                ServiceControlAccept::empty(),
                1,
            ))?;
            handle.set_service_status(status(
                ServiceState::Stopped,
                ServiceControlAccept::empty(),
                0,
            ))?;
        }
        Err(problem) => {
            eprintln!("GALAXAM Windows service startup/runtime error: {problem}");
            record_service_diagnostic(
                &crate::connection::config::DesktopConfig::new_for_setup().home,
                &format!("Service startup/runtime error: {problem}"),
            );
            if has_argument("--galaxam-pg-smoke") || has_argument("--galaxam-full-smoke") {
                write_smoke_result(
                    &crate::connection::config::DesktopConfig::new_for_setup().home,
                    &format!("ERROR: {problem}"),
                );
            }
            let mut stopped = status(ServiceState::Stopped, ServiceControlAccept::empty(), 0);
            stopped.exit_code = ServiceExitCode::ServiceSpecific(1);
            handle.set_service_status(stopped)?;
        }
    }
    Ok(())
}

fn supervise(
    handle: &windows_service::service_control_handler::ServiceStatusHandle,
    received: &mpsc::Receiver<()>,
) -> Result<(), String> {
    let config = crate::connection::config::DesktopConfig::new_for_setup();
    let resources = std::env::current_exe()
        .map_err(|_| "Exécutable du service introuvable".to_string())?
        .parent()
        .map(Path::to_path_buf)
        .ok_or_else(|| "Ressources du service introuvables".to_string())?;
    let _child_job = crate::platform::create_service_child_job()?;
    let postgres = crate::database::postgres::PostgresProcess::new();
    let checkpoint = std::sync::atomic::AtomicU32::new(2);
    record_service_diagnostic(&config.home, "Preparing PostgreSQL resources");
    handle
        .set_service_status(status(
            ServiceState::StartPending,
            ServiceControlAccept::empty(),
            2,
        ))
        .map_err(|problem| problem.to_string())?;
    record_service_diagnostic(&config.home, "Starting PostgreSQL initialization");
    let database = postgres
        .start_with_resources(&resources, &config, |stage| {
            eprintln!("GALAXAM Windows service: préparation PostgreSQL ({stage})");
            let _ = handle.set_service_status(status(
                ServiceState::StartPending,
                ServiceControlAccept::empty(),
                checkpoint.fetch_add(1, std::sync::atomic::Ordering::Relaxed),
            ));
        })
        .map_err(str::to_string)?;
    record_service_diagnostic(&config.home, "PostgreSQL initialized and validated");

    let smoke_mode = has_argument("--galaxam-pg-smoke") || has_argument("--galaxam-full-smoke");
    if smoke_mode && has_argument("--galaxam-pg-smoke") && !has_argument("--galaxam-full-smoke") {
        handle
            .set_service_status(status(
                ServiceState::Running,
                ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
                0,
            ))
            .map_err(|problem| problem.to_string())?;
        write_smoke_result(&config.home, "READY");
        wait_for_stop(received);
        postgres.stop();
        return Ok(());
    }

    if !database.has_admin {
        crate::security::bootstrap::ensure_bootstrap_capability(
            &config.home,
            &database.app_password,
        )
        .map_err(str::to_string)?;
    } else {
        let _ = fs::remove_file(config.home.join("config/bootstrap.state"));
    }

    handle
        .set_service_status(status(
            ServiceState::StartPending,
            ServiceControlAccept::empty(),
            3,
        ))
        .map_err(|problem| problem.to_string())?;
    let api_port = config.local_port.unwrap_or(18081);
    record_service_diagnostic(&config.home, "Starting Spring Boot API");
    let shutdown_token = random_shutdown_token()?;
    let mut backend = spawn_backend(
        &resources,
        &config.home,
        api_port,
        &database.app_password,
        &database.jwt_secret,
        &shutdown_token,
    )?;
    match wait_for_api(
        handle,
        &mut backend,
        api_port,
        received,
        Duration::from_secs(180),
    ) {
        Ok(ApiWait::Ready) => {
            record_service_diagnostic(&config.home, "Spring Boot API is ready");
        }
        Ok(ApiWait::StopRequested) => {
            handle
                .set_service_status(status(
                    ServiceState::StopPending,
                    ServiceControlAccept::empty(),
                    1,
                ))
                .map_err(|problem| problem.to_string())?;
            shutdown_backend(&mut backend, api_port, &shutdown_token);
            postgres.stop();
            return Ok(());
        }
        Ok(ApiWait::TimedOut) => {
            let _ = backend.kill();
            let _ = backend.wait();
            postgres.stop();
            return Err("Spring Boot n’a pas répondu dans le délai imparti".into());
        }
        Err(problem) => {
            let _ = backend.kill();
            let _ = backend.wait();
            postgres.stop();
            return Err(problem);
        }
    }

    if smoke_mode {
        write_smoke_result(&config.home, "READY");
    }
    handle
        .set_service_status(status(
            ServiceState::Running,
            ServiceControlAccept::STOP | ServiceControlAccept::SHUTDOWN,
            0,
        ))
        .map_err(|problem| problem.to_string())?;

    loop {
        match received.recv_timeout(Duration::from_secs(2)) {
            Ok(()) | Err(mpsc::RecvTimeoutError::Disconnected) => {
                handle
                    .set_service_status(status(
                        ServiceState::StopPending,
                        ServiceControlAccept::empty(),
                        1,
                    ))
                    .map_err(|problem| problem.to_string())?;
                break;
            }
            Err(mpsc::RecvTimeoutError::Timeout) => {
                if let Some(exit) = backend.try_wait().map_err(|problem| problem.to_string())? {
                    postgres.stop();
                    return Err(format!("Spring Boot s’est arrêté avec le code {exit}"));
                }
            }
        }
    }

    shutdown_backend(&mut backend, api_port, &shutdown_token);
    postgres.stop();
    Ok(())
}

fn record_service_diagnostic(home: &Path, message: &str) {
    let log_path = home.join("logs/service.log");
    if let Some(parent) = log_path.parent() {
        if fs::create_dir_all(parent).is_err() {
            return;
        }
    }
    let Ok(mut log) = OpenOptions::new().create(true).append(true).open(log_path) else {
        return;
    };
    let timestamp = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|value| value.as_secs())
        .unwrap_or_default();
    let _ = writeln!(log, "{timestamp} {message}");
    let _ = log.sync_all();
}

fn spawn_backend(
    resources: &Path,
    home: &Path,
    port: u16,
    app_password: &str,
    jwt_secret: &str,
    shutdown_token: &str,
) -> Result<ManagedChild, String> {
    let java = resources.join("backend/runtime/bin/java.exe");
    let jar = resources.join("backend/backend.jar");
    if !java.is_file() || !jar.is_file() {
        return Err("Runtime Java ou backend GALAXAM absent du bundle".into());
    }
    let log_path = home.join("logs/backend.log");
    fs::create_dir_all(log_path.parent().unwrap_or(home))
        .map_err(|_| "Dossier des journaux backend inaccessible".to_string())?;
    let stdout = OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)
        .map_err(|_| "Journal backend inaccessible".to_string())?;
    let stderr = stdout
        .try_clone()
        .map_err(|_| "Journal backend inaccessible".to_string())?;
    let mut command = Command::new(java);
    command
        .arg("-Dspring.profiles.active=desktop")
        .arg("-jar")
        .arg(jar)
        .env_clear()
        .env(
            "SystemRoot",
            std::env::var_os("SystemRoot").unwrap_or_else(|| "C:\\Windows".into()),
        )
        .env(
            "WINDIR",
            std::env::var_os("WINDIR").unwrap_or_else(|| "C:\\Windows".into()),
        )
        .env(
            "TEMP",
            std::env::var_os("TEMP").unwrap_or_else(|| "C:\\Windows\\Temp".into()),
        )
        .env(
            "TMP",
            std::env::var_os("TMP").unwrap_or_else(|| "C:\\Windows\\Temp".into()),
        )
        .env("PATH", resources.join("backend/runtime/bin"))
        .env("GALAXAM_DESKTOP_HOME", home)
        .env("GALAXAM_BACKEND_PORT", port.to_string())
        .env(
            "GALAXAM_DESKTOP_DB_URL",
            format!(
                "jdbc:postgresql://127.0.0.1:{}/sirh_galaxam",
                crate::connection::config::DesktopConfig::new_for_setup().postgres_port
            ),
        )
        .env("GALAXAM_DESKTOP_DB_USER", "galaxam_app")
        .env("GALAXAM_DESKTOP_DB_PASSWORD", app_password)
        .env("GALAXAM_DESKTOP_JWT_SECRET", jwt_secret)
        .env("GALAXAM_DESKTOP_SHUTDOWN_TOKEN", shutdown_token)
        .env("GALAXAM_DESKTOP_CORS_ORIGIN", "http://127.0.0.1:18888")
        .env("GALAXAM_DESKTOP_LOG_FILE", &log_path)
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr));
    let child = command
        .spawn()
        .map_err(|_| "Démarrage de Spring Boot impossible".to_string())?;
    if let Err(problem) = crate::platform::assign_service_child(&child) {
        let mut child = child;
        let _ = child.kill();
        let _ = child.wait();
        return Err(problem);
    }
    Ok(ManagedChild(child))
}

struct ManagedChild(Child);

impl Deref for ManagedChild {
    type Target = Child;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for ManagedChild {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl Drop for ManagedChild {
    fn drop(&mut self) {
        let _ = self.0.kill();
        let _ = self.0.wait();
    }
}

fn wait_for_api(
    handle: &windows_service::service_control_handler::ServiceStatusHandle,
    backend: &mut ManagedChild,
    port: u16,
    received: &mpsc::Receiver<()>,
    timeout: Duration,
) -> Result<ApiWait, String> {
    let deadline = Instant::now() + timeout;
    let mut last_checkpoint = Instant::now();
    let mut checkpoint = 4;
    while Instant::now() < deadline {
        match received.try_recv() {
            Ok(()) | Err(mpsc::TryRecvError::Disconnected) => return Ok(ApiWait::StopRequested),
            Err(mpsc::TryRecvError::Empty) => {}
        }
        if backend
            .try_wait()
            .map_err(|problem| problem.to_string())?
            .is_some()
        {
            return Err("Spring Boot s’est arrêté pendant son démarrage".into());
        }
        if api_ping(port) {
            return Ok(ApiWait::Ready);
        }
        if last_checkpoint.elapsed() >= Duration::from_secs(20) {
            handle
                .set_service_status(status(
                    ServiceState::StartPending,
                    ServiceControlAccept::empty(),
                    checkpoint,
                ))
                .map_err(|problem| problem.to_string())?;
            checkpoint += 1;
            last_checkpoint = Instant::now();
        }
        thread::sleep(Duration::from_millis(500));
    }
    Ok(ApiWait::TimedOut)
}

enum ApiWait {
    Ready,
    TimedOut,
    StopRequested,
}

fn random_shutdown_token() -> Result<String, String> {
    let mut bytes = [0u8; 32];
    ring::rand::SystemRandom::new()
        .fill(&mut bytes)
        .map_err(|_| "Génération du secret d’arrêt impossible".to_string())?;
    Ok(bytes.iter().map(|byte| format!("{byte:02x}")).collect())
}

fn shutdown_backend(backend: &mut ManagedChild, port: u16, token: &str) {
    if let Some(address) = ("127.0.0.1", port)
        .to_socket_addrs()
        .ok()
        .and_then(|mut addresses| addresses.next())
    {
        if let Ok(mut stream) = TcpStream::connect_timeout(&address, Duration::from_secs(2)) {
            let request = format!(
                "POST /api/desktop/internal/shutdown HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nX-GALAXAM-Service-Token: {token}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
            );
            let _ = stream.write_all(request.as_bytes());
        }
    }
    let deadline = Instant::now() + Duration::from_secs(25);
    while Instant::now() < deadline {
        if backend.try_wait().ok().flatten().is_some() {
            return;
        }
        thread::sleep(Duration::from_millis(200));
    }
    let _ = backend.kill();
    let _ = backend.wait();
}

fn api_ping(port: u16) -> bool {
    let address = ("127.0.0.1", port)
        .to_socket_addrs()
        .ok()
        .and_then(|mut a| a.next());
    let Some(address) = address else { return false };
    let Ok(mut stream) = TcpStream::connect_timeout(&address, Duration::from_secs(1)) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_secs(1)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(1)));
    stream
        .write_all(format!("GET /api/auth/ping HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n").as_bytes())
        .is_ok()
        && {
            let mut response = String::new();
            std::io::Read::read_to_string(&mut stream, &mut response).is_ok()
                && response.starts_with("HTTP/1.1 200")
        }
}

fn wait_for_stop(received: &mpsc::Receiver<()>) {
    let _ = received.recv();
}

fn write_smoke_result(home: &Path, result: &str) {
    let path = home.join("service/ci-postgres-result.txt");
    if let Some(parent) = path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let _ = fs::write(path, result);
}

fn has_argument(name: &str) -> bool {
    std::env::args_os().any(|argument| argument == name)
}
