use std::path::PathBuf;
use tauri::{AppHandle, Manager};

/// Resolve bundled resources from the running .app first. This is stable when
/// a bundle is copied or moved and avoids relying on LaunchServices metadata.
pub fn resource_dir(app: &AppHandle) -> Result<PathBuf, String> {
    if let Ok(executable) = std::env::current_exe() {
        if let Some(macos_dir) = executable.parent() {
            if macos_dir.file_name().is_some_and(|name| name == "MacOS") {
                if let Some(contents) = macos_dir.parent() {
                    let candidate = contents.join("Resources");
                    if candidate.is_dir() {
                        return Ok(candidate);
                    }
                }
            }
        }
    }
    app.path()
        .resource_dir()
        .map_err(|_| "Répertoire des ressources de l’application introuvable".to_string())
}
