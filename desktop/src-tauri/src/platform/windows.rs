use crate::connection::config::DesktopConfig;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use tauri::{AppHandle, Manager};

pub fn default_data_home() -> Result<PathBuf, &'static str> {
    Ok(
        PathBuf::from(std::env::var_os("ProgramData").ok_or("Dossier ProgramData introuvable")?)
            .join("GALAXAM SIRH"),
    )
}

pub fn fallback_data_home() -> PathBuf {
    PathBuf::from(std::env::var_os("ProgramData").unwrap_or_default()).join("GALAXAM SIRH")
}

pub fn restart_with_installation(app: &AppHandle, config: &DesktopConfig) -> Result<(), String> {
    let executable = tauri::process::current_binary(&app.env())
        .map_err(|_| "Exécutable GALAXAM introuvable pour le redémarrage".to_string())?;
    let mut command = Command::new(executable);
    for arg in app.env().args_os.iter().skip(1) {
        let text = arg.to_string_lossy();
        if !text.starts_with("--galaxam-data-home=")
            && !text.starts_with("--galaxam-web-port=")
            && !text.starts_with("--galaxam-test-launch-agent-dir=")
        {
            command.arg(arg);
        }
    }
    command
        .args(config.restart_arguments())
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|_| "Impossible de redémarrer GALAXAM".to_string())?;
    Ok(())
}
pub fn choose_export_destination() -> Result<Option<PathBuf>, String> {
    Err("Export non encore disponible sur ce système".into())
}
pub fn reveal_backups(backups: &Path) -> Result<(), String> {
    Command::new("explorer.exe")
        .arg(backups)
        .spawn()
        .map_err(|_| "Impossible d’ouvrir le dossier des sauvegardes".to_string())?;
    Ok(())
}
