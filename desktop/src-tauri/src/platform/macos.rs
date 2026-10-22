use crate::connection::config::DesktopConfig;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use tauri::{AppHandle, Manager};

pub fn default_data_home() -> Result<PathBuf, &'static str> {
    Ok(
        PathBuf::from(std::env::var_os("HOME").ok_or("Répertoire utilisateur introuvable")?)
            .join("Library/Application Support/GALAXAM SIRH"),
    )
}

pub fn fallback_data_home() -> PathBuf {
    PathBuf::from(std::env::var_os("HOME").unwrap_or_default())
        .join("Library/Application Support/GALAXAM SIRH")
}

pub fn restart_with_installation(app: &AppHandle, config: &DesktopConfig) -> Result<(), String> {
    use std::path::Component;
    let executable = tauri::process::current_binary(&app.env())
        .map_err(|_| "Exécutable GALAXAM introuvable pour le redémarrage".to_string())?;
    let macos_dir = executable.parent().ok_or("Chemin d'application invalide")?;
    if macos_dir.file_name() != Some(std::ffi::OsStr::new("MacOS"))
        || macos_dir.parent().and_then(|path| path.file_name())
            != Some(std::ffi::OsStr::new("Contents"))
    {
        return Err(
            "GALAXAM doit être lancé depuis son bundle pour appliquer le changement de mode".into(),
        );
    }
    let bundle = macos_dir
        .parent()
        .and_then(|path| path.parent())
        .ok_or("Bundle GALAXAM introuvable")?;
    if bundle
        .components()
        .any(|component| matches!(component, Component::ParentDir))
    {
        return Err("Chemin du bundle invalide".into());
    }
    let mut command = std::process::Command::new("/usr/bin/open");
    command.arg("-n").arg(bundle).arg("--args");
    for arg in app.env().args_os.iter().skip(1) {
        let text = arg.to_string_lossy();
        if !text.starts_with("--galaxam-data-home=")
            && !text.starts_with("--galaxam-web-port=")
            && !text.starts_with("--galaxam-test-launch-agent-dir=")
        {
            command.arg(arg);
        }
    }
    command.args(config.restart_arguments());
    let output = command
        .stdin(std::process::Stdio::null())
        .output()
        .map_err(|_| "Impossible de redémarrer GALAXAM".to_string())?;
    if !output.status.success() {
        return Err("macOS n'a pas accepté le redémarrage de GALAXAM".into());
    }
    Ok(())
}

pub fn choose_export_destination() -> Result<Option<PathBuf>, String> {
    let script = "POSIX path of (choose file name with prompt \"Enregistrer une copie GALAXAM SIRH\" default name \"galaxam-sauvegarde.tar\")";
    let output = Command::new("/usr/bin/osascript")
        .args(["-e", script])
        .stdin(Stdio::null())
        .output()
        .map_err(|_| "Fenêtre Enregistrer sous indisponible".to_string())?;
    if !output.status.success() {
        let reason = String::from_utf8_lossy(&output.stderr);
        return if reason.contains("-128") {
            Ok(None)
        } else {
            Err("Choix du fichier d’export impossible".into())
        };
    }
    let value =
        String::from_utf8(output.stdout).map_err(|_| "Chemin d’export invalide".to_string())?;
    let target = PathBuf::from(value.trim_end_matches(['\r', '\n']));
    if !target.is_absolute() || target.file_name().is_none() {
        return Err("Chemin d’export invalide".into());
    }
    Ok(Some(target))
}

pub fn reveal_backups(backups: &Path) -> Result<(), String> {
    let result = Command::new("/usr/bin/open")
        .arg(backups)
        .status()
        .map_err(|_| "Impossible d’ouvrir le dossier des sauvegardes".to_string())?;
    if result.success() {
        Ok(())
    } else {
        Err("Impossible d’ouvrir le dossier des sauvegardes".into())
    }
}
