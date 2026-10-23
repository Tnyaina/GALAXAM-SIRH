use crate::connection::config::DesktopConfig;
use std::fs;
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

pub fn choose_restore_source() -> Result<Option<PathBuf>, String> {
    let script = "POSIX path of (choose file with prompt \"Choisir une sauvegarde GALAXAM SIRH\")";
    let output = Command::new("/usr/bin/osascript")
        .args(["-e", script])
        .stdin(Stdio::null())
        .output()
        .map_err(|_| "Fenêtre de sélection indisponible".to_string())?;
    if !output.status.success() {
        return if String::from_utf8_lossy(&output.stderr).contains("-128") {
            Ok(None)
        } else {
            Err("Choix de l’archive impossible".into())
        };
    }
    let value = String::from_utf8(output.stdout).map_err(|_| "Chemin de sauvegarde invalide")?;
    let path = PathBuf::from(value.trim_end_matches(['\r', '\n']));
    if !path.is_absolute()
        || !path.is_file()
        || !path
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("tar"))
    {
        return Err("Archive de sauvegarde invalide".into());
    }
    Ok(Some(path))
}

pub fn restore_local_backup(
    app: &AppHandle,
    home: &Path,
    api_port: u16,
    pg_port: u16,
    archive: &Path,
) -> Result<(), String> {
    use crate::backup::archive;
    let resources = crate::app::resources::resource_dir(app)?;
    let work = home
        .join("backups")
        .join(format!(".restore-work-{}", std::process::id()));
    if work.exists() {
        return Err("Une opération de restauration précédente doit être vérifiée".into());
    }
    fs::create_dir_all(&work)
        .map_err(|_| "Dossier temporaire de restauration inaccessible".to_string())?;
    let incoming = work.join("incoming");
    let safety = work.join("safety");
    let result = (|| {
        let rollback_archive = archive::create(app, home, pg_port)?;
        let stable_source = work.join("import.tar");
        archive::export_copy(archive, &stable_source)?;
        let incoming_has_docs = archive::extract_for_restore(&stable_source, &incoming)?;
        let safety_has_docs = archive::extract_for_restore(&rollback_archive, &safety)?;
        crate::platform::service::stop(home)?;
        let pg = resources.join("postgresql/bin/pg_ctl");
        let cluster = home.join("data/postgresql/cluster");
        let config = home.join("data/postgresql/config/postgresql.conf");
        let log = home.join("data/postgresql/logs/postgres.log");
        let options = format!("-c config_file=\"{}\"", config.display());
        let started = Command::new(&pg)
            .args(["-D"])
            .arg(&cluster)
            .args(["-l"])
            .arg(&log)
            .args(["-o", &options, "-w", "-t", "30", "start"])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Démarrage temporaire de PostgreSQL impossible".to_string())?;
        if !started.success() {
            let _ = crate::platform::service::ensure_running(app, home, api_port, pg_port);
            return Err("Démarrage temporaire de PostgreSQL impossible".into());
        }
        let restore = archive::apply_database_restore(
            &resources,
            home,
            pg_port,
            &incoming.join("database.dump"),
        );
        let result = match restore {
            Ok(()) => archive::replace_documents(home, &incoming, incoming_has_docs),
            Err(problem) => Err(problem),
        };
        if let Err(original) = &result {
            if let Err(rollback) = archive::apply_database_restore(
                &resources,
                home,
                pg_port,
                &safety.join("database.dump"),
            ) {
                let _ = Command::new(&pg)
                    .args(["-D"])
                    .arg(&cluster)
                    .args(["-m", "fast", "-w", "-t", "30", "stop"])
                    .status();
                return Err(format!("Restauration échouée ({original}) et retour arrière impossible ({rollback}). Les services restent arrêtés. Sauvegarde de sécurité : {}", rollback_archive.display()));
            }
        }
        let stopped = Command::new(&pg)
            .args(["-D"])
            .arg(&cluster)
            .args(["-m", "fast", "-w", "-t", "30", "stop"])
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status();
        if stopped.map_or(true, |status| !status.success()) {
            return Err("Arrêt temporaire de PostgreSQL après restauration impossible. Les services restent arrêtés pour éviter de démarrer Spring Boot sur un état incertain.".into());
        }
        crate::platform::service::ensure_running(app, home, api_port, pg_port)?;
        let _ = safety_has_docs;
        result
    })();
    let _ = fs::remove_dir_all(&work);
    result
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
