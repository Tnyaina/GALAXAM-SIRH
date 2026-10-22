use crate::resources;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::AppHandle;

pub fn create(app: &AppHandle, home: &Path, port: u16) -> Result<PathBuf, String> {
    let directory = home.join("backups");
    private_directory(&directory)?;
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "Horloge système invalide".to_string())?
        .as_nanos();
    let name = format!("galaxam-{timestamp}");
    let staging = directory.join(format!("{name}.pending"));
    let pending = directory.join(format!("{name}.tar.pending"));
    let final_path = directory.join(format!("{name}.tar"));
    fs::create_dir(&staging).map_err(|_| "Préparation de la sauvegarde impossible".to_string())?;
    if let Err(problem) = private_mode(&staging, 0o700) {
        let _ = fs::remove_dir(&staging);
        return Err(problem);
    }
    let result = (|| {
        let secrets = fs::read_to_string(home.join("data/postgresql/config/installation.secrets"))
            .map_err(|_| "Secrets de la base locale illisibles".to_string())?;
        let password = secrets.lines().find_map(|line| line.strip_prefix("app="))
            .ok_or("Secret de la base locale absent")?;
        let tools = resources::resource_dir(app)?.join("postgresql/bin");
        let dump = staging.join("database.dump");
        let status = Command::new(tools.join("pg_dump"))
            .args(["-Fc", "--no-owner", "--no-acl", "-h", "127.0.0.1", "-p"])
            .arg(port.to_string())
            .args(["-U", "galaxam_app", "-d", "sirh_galaxam", "-f"])
            .arg(&dump)
            .env_clear()
            .env("PATH", "/usr/bin:/bin:/usr/sbin:/sbin")
            .env("PGPASSWORD", password)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Outil de sauvegarde PostgreSQL indisponible".to_string())?;
        if !status.success() {
            return Err("Sauvegarde PostgreSQL échouée".into());
        }
        let status = Command::new(tools.join("pg_restore"))
            .arg("-l").arg(&dump)
            .stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null())
            .status()
            .map_err(|_| "Vérification de l’archive PostgreSQL impossible".to_string())?;
        if !status.success() { return Err("Archive PostgreSQL invalide".into()); }

        let documents = home.join("documents");
        let include_documents = match fs::symlink_metadata(&documents) {
            Ok(meta) if meta.file_type().is_dir() => true,
            Ok(_) => return Err("Dossier des documents invalide".into()),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
            Err(_) => return Err("Dossier des documents inaccessible".into()),
        };
        let manifest = serde_json::json!({
            "format": "galaxam-local-backup-v1",
            "database": "sirh_galaxam",
            "documents_included": include_documents,
            "created_unix_nanos": timestamp.to_string(),
        });
        let mut file = private_new(&staging.join("manifest.json"))?;
        file.write_all(manifest.to_string().as_bytes())
            .map_err(|_| "Manifeste de sauvegarde impossible à écrire".to_string())?;
        file.sync_all().map_err(|_| "Manifeste de sauvegarde impossible à synchroniser".to_string())?;
        private_new(&pending)?;
        let mut command = Command::new("/usr/bin/tar");
        command.arg("-cf").arg(&pending)
            .arg("-C").arg(&staging)
            .args(["database.dump", "manifest.json"]);
        if include_documents {
            command.arg("-C").arg(home).arg("documents");
        }
        let status = command.stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null())
            .status().map_err(|_| "Archivage de la sauvegarde impossible".to_string())?;
        if !status.success() { return Err("Archivage des documents impossible".into()); }
        let status = Command::new("/usr/bin/tar")
            .arg("-tf").arg(&pending)
            .stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null())
            .status().map_err(|_| "Vérification de l’archive impossible".to_string())?;
        if !status.success() { return Err("Archive de sauvegarde invalide".into()); }
        fs::rename(&pending, &final_path)
            .map_err(|_| "Publication de la sauvegarde impossible".to_string())?;
        Ok(final_path.clone())
    })();
    let _ = fs::remove_dir_all(&staging);
    if result.is_err() { let _ = fs::remove_file(&pending); }
    result
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
        return if reason.contains("-128") { Ok(None) } else { Err("Choix du fichier d’export impossible".into()) };
    }
    let value = String::from_utf8(output.stdout)
        .map_err(|_| "Chemin d’export invalide".to_string())?;
    let target = PathBuf::from(value.trim_end_matches(['\r', '\n']));
    if !target.is_absolute() || target.file_name().is_none() {
        return Err("Chemin d’export invalide".into());
    }
    Ok(Some(target))
}

pub fn export_copy(source: &Path, destination: &Path) -> Result<u64, String> {
    let parent = destination.parent().ok_or("Dossier d’export invalide")?;
    if !parent.is_dir() || destination.is_dir() {
        return Err("Destination d’export invalide".into());
    }
    if destination.exists() {
        return Err("Ce fichier existe déjà. Choisissez un autre nom pour l’export".into());
    }
    if source == destination {
        return Err("Choisissez un autre emplacement pour la copie exportée".into());
    }
    let mut created = false;
    let result = (|| {
        let mut input = fs::File::open(source)
            .map_err(|_| "Archive source introuvable".to_string())?;
        let mut output = private_new(destination)
            .map_err(|_| "Ce fichier existe déjà ou ne peut pas être créé".to_string())?;
        created = true;
        let bytes = std::io::copy(&mut input, &mut output)
            .map_err(|_| "Copie de l’archive impossible".to_string())?;
        output.sync_all().map_err(|_| "Synchronisation de l’export impossible".to_string())?;
        Ok(bytes)
    })();
    if result.is_err() && created { let _ = fs::remove_file(destination); }
    result
}

fn private_directory(path: &Path) -> Result<(), String> {
    if !path.exists() {
        fs::create_dir(path).map_err(|_| "Dossier de sauvegarde impossible à créer".to_string())?;
    }
    let meta = fs::symlink_metadata(path).map_err(|_| "Dossier de sauvegarde inaccessible".to_string())?;
    if !meta.file_type().is_dir() { return Err("Dossier de sauvegarde invalide".into()); }
    #[cfg(unix)]
    {
        use std::os::unix::fs::MetadataExt;
        if meta.uid() != unsafe { libc::geteuid() } {
            return Err("Dossier de sauvegarde appartenant à un autre utilisateur".into());
        }
    }
    private_mode(path, 0o700)
}

fn private_new(path: &Path) -> Result<fs::File, String> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    { use std::os::unix::fs::OpenOptionsExt; options.mode(0o600); }
    options.open(path).map_err(|_| "Fichier de sauvegarde impossible à créer".to_string())
}

fn private_mode(path: &Path, mode: u32) -> Result<(), String> {
    #[cfg(unix)]
    { use std::os::unix::fs::PermissionsExt; fs::set_permissions(path, fs::Permissions::from_mode(mode))
        .map_err(|_| "Permissions de sauvegarde invalides".to_string())?; }
    let _ = (path, mode);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exported_archive_is_copied_to_selected_directory() {
        let root = std::env::temp_dir().join(format!("galaxam-export-test-{}-{}",
            std::process::id(), SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos()));
        fs::create_dir(&root).unwrap();
        let source = root.join("source.tar");
        let destination = root.join("selected.tar");
        fs::write(&source, b"archive-content").unwrap();
        assert_eq!(export_copy(&source, &destination).unwrap(), 15);
        assert_eq!(fs::read(&destination).unwrap(), b"archive-content");
        assert!(export_copy(&source, &destination).is_err());
        assert_eq!(fs::read(&destination).unwrap(), b"archive-content");
        fs::remove_dir_all(root).unwrap();
    }
}
