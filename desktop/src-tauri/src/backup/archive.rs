use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::AppHandle;

pub fn create(app: &AppHandle, home: &Path, port: u16) -> Result<PathBuf, String> {
    let resource_root = crate::app::resources::resource_dir(app)?;
    create_from_resources(&resource_root, home, port)
}

pub fn create_from_resources(
    resource_root: &Path,
    home: &Path,
    port: u16,
) -> Result<PathBuf, String> {
    create_named(resource_root, home, port, "galaxam")
}

#[cfg_attr(not(windows), allow(dead_code))]
pub fn create_automatic_from_resources(
    resource_root: &Path,
    home: &Path,
    port: u16,
) -> Result<Option<PathBuf>, String> {
    let directory = home.join("backups");
    private_directory(&directory)?;
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "Horloge système invalide".to_string())?
        .as_secs();
    let marker = home.join("config/backup-last-success");
    let last = fs::read_to_string(&marker)
        .ok()
        .and_then(|value| value.trim().parse::<u64>().ok())
        .filter(|value| *value <= now)
        .unwrap_or(0);
    if now.saturating_sub(last) < 86_400 {
        return Ok(None);
    }
    let archive = create_named(resource_root, home, port, "galaxam-auto")?;
    let latest = home.join("config/backup-last-archive");
    let latest_pending = home.join("config/backup-last-archive.pending");
    let pending = home.join("config/backup-last-success.pending");
    for stale in [&pending, &latest_pending] {
        if stale.exists() {
            fs::remove_file(stale)
                .map_err(|_| "État de sauvegarde automatique impossible à préparer".to_string())?;
        }
    }
    let mut file = private_new(&pending)?;
    writeln!(file, "{now}")
        .map_err(|_| "État de sauvegarde automatique impossible à écrire".to_string())?;
    file.sync_all()
        .map_err(|_| "État de sauvegarde automatique impossible à synchroniser".to_string())?;
    drop(file);
    if marker.exists() {
        fs::remove_file(&marker)
            .map_err(|_| "État de sauvegarde automatique impossible à remplacer".to_string())?;
    }
    fs::rename(&pending, &marker)
        .map_err(|_| "État de sauvegarde automatique impossible à publier".to_string())?;
    let archive_name = archive
        .file_name()
        .ok_or("Nom de sauvegarde automatique invalide")?;
    let mut latest_file = private_new(&latest_pending)?;
    latest_file
        .write_all(archive_name.to_string_lossy().as_bytes())
        .map_err(|_| "Nom de sauvegarde automatique impossible à écrire".to_string())?;
    latest_file
        .sync_all()
        .map_err(|_| "Nom de sauvegarde automatique impossible à synchroniser".to_string())?;
    drop(latest_file);
    if latest.exists() {
        fs::remove_file(&latest)
            .map_err(|_| "Nom de sauvegarde automatique impossible à remplacer".to_string())?;
    }
    fs::rename(&latest_pending, &latest)
        .map_err(|_| "Nom de sauvegarde automatique impossible à publier".to_string())?;
    let _ = fs::remove_file(home.join("config/backup-last-error"));
    rotate_automatic(&directory);
    append_backup_log(
        home,
        &format!("sauvegarde automatique créée : {}", archive.display()),
    );
    Ok(Some(archive))
}

fn create_named(
    resource_root: &Path,
    home: &Path,
    port: u16,
    prefix: &str,
) -> Result<PathBuf, String> {
    let directory = home.join("backups");
    private_directory(&directory)?;
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "Horloge système invalide".to_string())?
        .as_nanos();
    let name = format!("{prefix}-{timestamp}");
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
        let password = secrets
            .lines()
            .find_map(|line| line.strip_prefix("app="))
            .ok_or("Secret de la base locale absent")?;
        let tools = resource_root.join("postgresql/bin");
        let dump = staging.join("database.dump");
        #[cfg(windows)]
        let pg_dump = tools.join("pg_dump.exe");
        #[cfg(not(windows))]
        let pg_dump = tools.join("pg_dump");
        let mut dump_command = Command::new(pg_dump);
        dump_command
            .args(["-Fc", "--no-owner", "--no-acl", "-h", "127.0.0.1", "-p"])
            .arg(port.to_string())
            .args(["-U", "galaxam_app", "-d", "sirh_galaxam", "-f"])
            .arg(&dump)
            .env("PGPASSWORD", password)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        #[cfg(unix)]
        dump_command
            .env_clear()
            .env("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        let status = dump_command
            .status()
            .map_err(|_| "Outil de sauvegarde PostgreSQL indisponible".to_string())?;
        if !status.success() {
            return Err("Sauvegarde PostgreSQL échouée".into());
        }
        #[cfg(windows)]
        let pg_restore = tools.join("pg_restore.exe");
        #[cfg(not(windows))]
        let pg_restore = tools.join("pg_restore");
        let status = Command::new(pg_restore)
            .arg("-l")
            .arg(&dump)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Vérification de l’archive PostgreSQL impossible".to_string())?;
        if !status.success() {
            return Err("Archive PostgreSQL invalide".into());
        }

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
        file.sync_all()
            .map_err(|_| "Manifeste de sauvegarde impossible à synchroniser".to_string())?;
        private_new(&pending)?;
        #[cfg(windows)]
        let tar = std::env::var_os("SystemRoot")
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
            .join("System32/tar.exe");
        #[cfg(not(windows))]
        let tar = PathBuf::from("/usr/bin/tar");
        let mut command = Command::new(&tar);
        command
            .arg("-cf")
            .arg(&pending)
            .arg("-C")
            .arg(&staging)
            .args(["database.dump", "manifest.json"]);
        if include_documents {
            command.arg("-C").arg(home).arg("documents");
        }
        let status = command
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Archivage de la sauvegarde impossible".to_string())?;
        if !status.success() {
            return Err("Archivage des documents impossible".into());
        }
        let status = Command::new(&tar)
            .arg("-tf")
            .arg(&pending)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Vérification de l’archive impossible".to_string())?;
        if !status.success() {
            return Err("Archive de sauvegarde invalide".into());
        }
        fs::rename(&pending, &final_path)
            .map_err(|_| "Publication de la sauvegarde impossible".to_string())?;
        Ok(final_path.clone())
    })();
    let _ = fs::remove_dir_all(&staging);
    if result.is_err() {
        let _ = fs::remove_file(&pending);
    }
    result
}

#[cfg_attr(not(windows), allow(dead_code))]
fn rotate_automatic(directory: &Path) {
    let mut archives = fs::read_dir(directory)
        .ok()
        .into_iter()
        .flatten()
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let name = entry.file_name().into_string().ok()?;
            (name.starts_with("galaxam-auto-")
                && name.ends_with(".tar")
                && entry.file_type().ok()?.is_file())
            .then_some((name, entry.path()))
        })
        .collect::<Vec<_>>();
    archives.sort_by(|left, right| right.0.cmp(&left.0));
    for (_, path) in archives.into_iter().skip(7) {
        let _ = fs::remove_file(path);
    }
}

#[cfg_attr(not(windows), allow(dead_code))]
fn append_backup_log(home: &Path, message: &str) {
    let path = home.join("logs/backup.log");
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(path) {
        let _ = writeln!(file, "{} {message}", chrono_timestamp());
    }
}

#[cfg_attr(not(windows), allow(dead_code))]
fn chrono_timestamp() -> String {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_secs().to_string())
        .unwrap_or_else(|_| "0".to_owned())
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
        let mut input =
            fs::File::open(source).map_err(|_| "Archive source introuvable".to_string())?;
        let mut output = private_new(destination)
            .map_err(|_| "Ce fichier existe déjà ou ne peut pas être créé".to_string())?;
        created = true;
        let bytes = std::io::copy(&mut input, &mut output)
            .map_err(|_| "Copie de l’archive impossible".to_string())?;
        output
            .sync_all()
            .map_err(|_| "Synchronisation de l’export impossible".to_string())?;
        Ok(bytes)
    })();
    if result.is_err() && created {
        let _ = fs::remove_file(destination);
    }
    result
}

fn private_directory(path: &Path) -> Result<(), String> {
    if !path.exists() {
        fs::create_dir(path).map_err(|_| "Dossier de sauvegarde impossible à créer".to_string())?;
    }
    let meta =
        fs::symlink_metadata(path).map_err(|_| "Dossier de sauvegarde inaccessible".to_string())?;
    if !meta.file_type().is_dir() {
        return Err("Dossier de sauvegarde invalide".into());
    }
    #[cfg(windows)]
    {
        let icacls = std::env::var_os("SystemRoot")
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
            .join("System32/icacls.exe");
        let result = Command::new(icacls)
            .arg(path)
            .args([
                "/inheritance:r",
                "/grant:r",
                "*S-1-5-19:(OI)(CI)F",
                "*S-1-5-32-544:(OI)(CI)F",
                "*S-1-5-18:(OI)(CI)F",
            ])
            .output()
            .map_err(|_| "Permissions du dossier de sauvegarde indisponibles".to_string())?;
        if !result.status.success() {
            return Err("Permissions du dossier de sauvegarde invalides".into());
        }
    }
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
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options
        .open(path)
        .map_err(|_| "Fichier de sauvegarde impossible à créer".to_string())
}

fn private_mode(path: &Path, mode: u32) -> Result<(), String> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(mode))
            .map_err(|_| "Permissions de sauvegarde invalides".to_string())?;
    }
    let _ = (path, mode);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn exported_archive_is_copied_to_selected_directory() {
        let root = std::env::temp_dir().join(format!(
            "galaxam-export-test-{}-{}",
            std::process::id(),
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
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
