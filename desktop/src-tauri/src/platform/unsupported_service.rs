//! Explicit placeholder until the Windows service supervisor is implemented.
use std::path::Path;
use tauri::AppHandle;

#[derive(Clone)]
pub struct ServiceLock;
impl ServiceLock {
    pub fn acquire(_home: &Path) -> Result<Self, String> {
        Err("Service local indisponible sur ce système".into())
    }
}
pub fn ensure_running(
    _app: &AppHandle,
    _home: &Path,
    _api_port: u16,
    _pg_port: u16,
) -> Result<(), String> {
    Err("Service local indisponible sur ce système".into())
}
pub fn stop(_home: &Path) -> Result<(), String> {
    Err("Service local indisponible sur ce système".into())
}
pub fn disable(_home: &Path) -> Result<(), String> {
    Err("Service local indisponible sur ce système".into())
}
pub fn backup_agent_active(_home: &Path) -> bool {
    false
}
