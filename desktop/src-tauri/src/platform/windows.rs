use crate::connection::config::DesktopConfig;
use std::path::{Path, PathBuf};
use tauri::AppHandle;

pub fn default_data_home() -> Result<PathBuf, &'static str> {
    Ok(
        PathBuf::from(std::env::var_os("ProgramData").ok_or("Dossier ProgramData introuvable")?)
            .join("GALAXAM SIRH"),
    )
}

pub fn fallback_data_home() -> PathBuf {
    PathBuf::from(std::env::var_os("ProgramData").unwrap_or_default()).join("GALAXAM SIRH")
}

pub fn restart_with_installation(_app: &AppHandle, _config: &DesktopConfig) -> Result<(), String> {
    Err("Redémarrage non encore disponible sur ce système".into())
}
pub fn choose_export_destination() -> Result<Option<PathBuf>, String> {
    Err("Export non encore disponible sur ce système".into())
}
pub fn reveal_backups(_backups: &Path) -> Result<(), String> {
    Err("Ouverture des sauvegardes non encore disponible sur ce système".into())
}
