//! Windows SCM boundary. The UI never initializes PostgreSQL directly.
use std::path::Path;
use tauri::AppHandle;

#[derive(Clone)]
pub struct ServiceLock;

impl ServiceLock {
    pub fn acquire(_home: &Path) -> Result<Self, String> {
        if std::env::args_os().any(|argument| argument == "--galaxam-service") {
            Ok(Self)
        } else {
            Err("Seul le service Windows peut contrôler la base locale".into())
        }
    }
}

pub fn ensure_running(
    _app: &AppHandle,
    home: &Path,
    api_port: u16,
    _pg_port: u16,
) -> Result<(), String> {
    crate::platform::ensure_windows_service_ready(home, api_port)
}

pub fn stop(home: &Path) -> Result<(), String> {
    crate::platform::stop_windows_service(home)
}

pub fn disable(home: &Path) -> Result<(), String> {
    crate::platform::disable_windows_service(home)
}

pub fn backup_agent_active(_home: &Path) -> bool {
    false
}
