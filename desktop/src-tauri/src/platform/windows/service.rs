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
    _home: &Path,
    _api_port: u16,
    _pg_port: u16,
) -> Result<(), String> {
    Err("Le service local Windows n'est pas encore activé par l'installateur".into())
}

pub fn stop(_home: &Path) -> Result<(), String> {
    Err("Arrêt du service local Windows non encore disponible".into())
}

pub fn disable(_home: &Path) -> Result<(), String> {
    Err("Désactivation du service local Windows non encore disponible".into())
}

pub fn backup_agent_active(_home: &Path) -> bool {
    false
}
