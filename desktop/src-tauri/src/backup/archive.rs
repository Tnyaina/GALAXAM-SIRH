use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
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
    create_named(resource_root, home, port, "galaxam", None)
}

#[cfg_attr(not(windows), allow(dead_code))]
pub fn create_automatic_from_resources(
    resource_root: &Path,
    home: &Path,
    port: u16,
) -> Result<Option<PathBuf>, String> {
    create_automatic_from_resources_with_cancel(resource_root, home, port, None)
}

pub fn create_automatic_from_resources_with_cancel(
    resource_root: &Path,
    home: &Path,
    port: u16,
    cancel: Option<&AtomicBool>,
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
    let archive = create_named(resource_root, home, port, "galaxam-auto", cancel)?;
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
    cancel: Option<&AtomicBool>,
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
        ensure_not_cancelled(cancel)?;
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
        let status = interruptible_status(&mut dump_command, cancel)
            .map_err(|_| "Outil de sauvegarde PostgreSQL indisponible".to_string())?;
        if !status.success() {
            return Err("Sauvegarde PostgreSQL échouée".into());
        }
        #[cfg(windows)]
        let pg_restore = tools.join("pg_restore.exe");
        #[cfg(not(windows))]
        let pg_restore = tools.join("pg_restore");
        let mut verify_dump = Command::new(pg_restore);
        verify_dump
            .arg("-l")
            .arg(&dump)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        let status = interruptible_status(&mut verify_dump, cancel)
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
        if include_documents {
            ensure_regular_tree(&documents, cancel)?;
        }
        ensure_not_cancelled(cancel)?;
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
        command
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        let status = interruptible_status(&mut command, cancel)
            .map_err(|_| "Archivage de la sauvegarde impossible".to_string())?;
        if !status.success() {
            return Err("Archivage des documents impossible".into());
        }
        let mut verify_archive = Command::new(&tar);
        verify_archive
            .arg("-tf")
            .arg(&pending)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        let status = interruptible_status(&mut verify_archive, cancel)
            .map_err(|_| "Vérification de l’archive impossible".to_string())?;
        if !status.success() {
            return Err("Archive de sauvegarde invalide".into());
        }
        ensure_not_cancelled(cancel)?;
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

fn interruptible_status(
    command: &mut Command,
    cancel: Option<&AtomicBool>,
) -> std::io::Result<std::process::ExitStatus> {
    let Some(cancel) = cancel else {
        return command.status();
    };
    if cancel.load(Ordering::Relaxed) {
        return Err(std::io::Error::new(
            std::io::ErrorKind::Interrupted,
            "Sauvegarde interrompue",
        ));
    }
    let mut child = command.spawn()?;
    loop {
        if let Some(status) = child.try_wait()? {
            return Ok(status);
        }
        if cancel.load(Ordering::Relaxed) {
            let _ = child.kill();
            let _ = child.wait();
            return Err(std::io::Error::new(
                std::io::ErrorKind::Interrupted,
                "Sauvegarde interrompue",
            ));
        }
        std::thread::sleep(Duration::from_millis(200));
    }
}

fn ensure_not_cancelled(cancel: Option<&AtomicBool>) -> Result<(), String> {
    if cancel.is_some_and(|flag| flag.load(Ordering::Relaxed)) {
        Err("Sauvegarde interrompue".into())
    } else {
        Ok(())
    }
}

fn ensure_regular_tree(root: &Path, cancel: Option<&AtomicBool>) -> Result<(), String> {
    let mut pending = vec![root.to_path_buf()];
    while let Some(directory) = pending.pop() {
        ensure_not_cancelled(cancel)?;
        for entry in
            fs::read_dir(&directory).map_err(|_| "Dossier de documents illisible".to_string())?
        {
            ensure_not_cancelled(cancel)?;
            let entry = entry.map_err(|_| "Entrée de document illisible".to_string())?;
            let kind = entry
                .file_type()
                .map_err(|_| "Type de document illisible".to_string())?;
            if kind.is_symlink() || (!kind.is_dir() && !kind.is_file()) {
                return Err("Les sauvegardes ne prennent pas en charge les liens ou fichiers spéciaux dans documents".into());
            }
            if kind.is_dir() {
                pending.push(entry.path());
            }
        }
    }
    Ok(())
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

/// Extract and validate an imported archive without following links or allowing
/// entries to escape the private staging directory.
pub fn extract_for_restore(archive_path: &Path, staging: &Path) -> Result<bool, String> {
    fs::create_dir(staging)
        .map_err(|_| "Dossier temporaire de restauration impossible à créer".to_string())?;
    private_mode(staging, 0o700)?;
    let result = (|| {
        let tar = tar_executable();
        let listing = Command::new(&tar)
            .arg("-tf")
            .arg(archive_path)
            .output()
            .map_err(|_| "Outil d’archive indisponible".to_string())?;
        if !listing.status.success() {
            return Err("Archive de sauvegarde illisible".into());
        }
        validate_archive_listing(&listing.stdout)?;
        let verbose = Command::new(&tar)
            .arg("-tvf")
            .arg(archive_path)
            .output()
            .map_err(|_| "Vérification des types d’entrée impossible".to_string())?;
        if !verbose.status.success()
            || verbose
                .stdout
                .split(|byte| *byte == b'\n')
                .filter(|line| !line.is_empty())
                .any(|line| !matches!(line.first(), Some(b'-' | b'd')))
        {
            return Err(
                "Les liens symboliques et liens physiques sont interdits dans une sauvegarde"
                    .into(),
            );
        }
        let extracted = Command::new(&tar)
            .args(["-xf"])
            .arg(archive_path)
            .arg("-C")
            .arg(staging)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .status()
            .map_err(|_| "Extraction temporaire de l’archive impossible".to_string())?;
        if !extracted.success() {
            return Err("Extraction temporaire de l’archive impossible".into());
        }
        let bytes = fs::read(staging.join("manifest.json"))
            .map_err(|_| "Manifeste de sauvegarde illisible".to_string())?;
        let value: serde_json::Value = serde_json::from_slice(&bytes)
            .map_err(|_| "Manifeste de sauvegarde invalide".to_string())?;
        if value.get("format").and_then(|field| field.as_str()) != Some("galaxam-local-backup-v1")
            || value.get("database").and_then(|field| field.as_str()) != Some("sirh_galaxam")
        {
            return Err("Format de sauvegarde GALAXAM non reconnu".into());
        }
        Ok(staging.join("documents").is_dir())
    })();
    if result.is_err() {
        let _ = fs::remove_dir_all(staging);
    }
    result
}

fn validate_archive_listing(listing: &[u8]) -> Result<(), String> {
    let mut database = false;
    let mut manifest = false;
    let listing = listing.strip_suffix(b"\n").unwrap_or(listing);
    for line in listing.split(|byte| *byte == b'\n') {
        let name = line.strip_suffix(b"\r").unwrap_or(line);
        let name = name.strip_suffix(b"/").unwrap_or(name);
        if name.is_empty()
            || name[0] == b'/'
            || name
                .iter()
                .any(|byte| *byte == b'\\' || *byte == b':' || *byte < b' ')
        {
            return Err("L’archive contient un chemin non autorisé".into());
        }
        let parts = name.split(|byte| *byte == b'/').collect::<Vec<_>>();
        if parts
            .iter()
            .any(|part| part.is_empty() || *part == b"." || *part == b"..")
        {
            return Err("L’archive contient un chemin non autorisé".into());
        }
        if parts.len() == 1 && parts[0] == &b"database.dump"[..] {
            database = true;
        } else if parts.len() == 1 && parts[0] == &b"manifest.json"[..] {
            manifest = true;
        } else if parts[0] != &b"documents"[..] {
            return Err("L’archive contient un fichier inattendu".into());
        }
    }
    if !database || !manifest {
        return Err("L’archive ne contient pas la base et son manifeste".into());
    }
    Ok(())
}

fn tar_executable() -> PathBuf {
    #[cfg(windows)]
    {
        std::env::var_os("SystemRoot")
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
            .join("System32/tar.exe")
    }
    #[cfg(not(windows))]
    {
        PathBuf::from("/usr/bin/tar")
    }
}

pub fn apply_database_restore(
    resource_root: &Path,
    home: &Path,
    port: u16,
    dump: &Path,
) -> Result<(), String> {
    let secrets = fs::read_to_string(home.join("data/postgresql/config/installation.secrets"))
        .map_err(|_| "Secrets de la base locale illisibles".to_string())?;
    let password = secrets
        .lines()
        .find_map(|line| line.strip_prefix("app="))
        .ok_or("Secret applicatif absent")?;
    let tools = resource_root.join("postgresql/bin");
    #[cfg(windows)]
    let pg_restore = tools.join("pg_restore.exe");
    #[cfg(not(windows))]
    let pg_restore = tools.join("pg_restore");
    let status = Command::new(pg_restore)
        .args([
            "--clean",
            "--if-exists",
            "--no-owner",
            "--no-acl",
            "--exit-on-error",
            "-h",
            "127.0.0.1",
            "-p",
        ])
        .arg(port.to_string())
        .args(["-U", "galaxam_app", "-d", "sirh_galaxam"])
        .arg(dump)
        .env("PGPASSWORD", password)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .status()
        .map_err(|_| "Outil de restauration PostgreSQL indisponible".to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err("Restauration PostgreSQL échouée".into())
    }
}

pub fn replace_documents(
    home: &Path,
    staging: &Path,
    include_documents: bool,
) -> Result<(), String> {
    let target = home.join("documents");
    let current = match fs::symlink_metadata(&target) {
        Ok(meta) if meta.file_type().is_dir() => true,
        Ok(_) => return Err("Dossier actuel des documents invalide; remplacement refusé".into()),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => false,
        Err(_) => return Err("Dossier actuel des documents inaccessible".into()),
    };
    let replacement = staging.join("documents");
    if include_documents && !replacement.is_dir() {
        return Err("Documents absents de l’archive".into());
    }
    let old = home.join(format!("documents.restore-{}", std::process::id()));
    if old.exists() {
        return Err("Une restauration précédente doit être vérifiée avant de continuer".into());
    }
    if current {
        fs::rename(&target, &old)
            .map_err(|_| "Mise de côté des documents actuels impossible".to_string())?;
    }
    if include_documents {
        if let Err(error) = fs::rename(&replacement, &target) {
            if current {
                let _ = fs::rename(&old, &target);
            }
            return Err(format!(
                "Installation des documents restaurés impossible : {error}"
            ));
        }
    } else if fs::create_dir(&target).is_err() {
        if current {
            let _ = fs::rename(&old, &target);
        }
        return Err("Création du dossier de documents restauré impossible".into());
    }
    if current && fs::remove_dir_all(&old).is_err() {
        append_backup_log(
            home,
            &format!(
                "restauration terminée; ancienne copie conservée : {}",
                old.display()
            ),
        );
    }
    Ok(())
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

    #[cfg(unix)]
    #[test]
    fn automatic_backup_command_stops_after_cancellation() {
        let cancelled = std::sync::Arc::new(AtomicBool::new(false));
        let signal = std::sync::Arc::clone(&cancelled);
        let notifier = std::thread::spawn(move || {
            std::thread::sleep(Duration::from_millis(100));
            signal.store(true, Ordering::Relaxed);
        });
        let started = std::time::Instant::now();
        let mut command = Command::new("/bin/sleep");
        command.arg("5").stdout(Stdio::null()).stderr(Stdio::null());
        assert!(interruptible_status(&mut command, Some(&cancelled)).is_err());
        assert!(started.elapsed() < Duration::from_secs(2));
        notifier.join().unwrap();
    }

    fn test_root(label: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "galaxam-{label}-{}-{}",
            std::process::id(),
            SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ))
    }

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

    #[test]
    fn restore_extractor_accepts_only_a_valid_galaxam_archive() {
        let root = test_root("restore-test");
        let source = root.join("source");
        fs::create_dir_all(source.join("documents")).unwrap();
        fs::write(source.join("database.dump"), b"database").unwrap();
        fs::write(source.join("manifest.json"), r#"{"format":"galaxam-local-backup-v1","database":"sirh_galaxam","documents_included":true}"#).unwrap();
        fs::write(source.join("documents/file.txt"), b"document").unwrap();
        let archive_path = root.join("backup.tar");
        let status = Command::new(tar_executable())
            .arg("-cf")
            .arg(&archive_path)
            .arg("-C")
            .arg(&source)
            .args(["database.dump", "manifest.json", "documents"])
            .status()
            .unwrap();
        assert!(status.success());
        let staging = root.join("staging");
        assert!(extract_for_restore(&archive_path, &staging).unwrap());
        assert_eq!(
            fs::read(staging.join("documents/file.txt")).unwrap(),
            b"document"
        );
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn archive_listing_accepts_localized_names_and_rejects_unsafe_paths() {
        let prefix = b"database.dump\r\nmanifest.json\r\ndocuments/\r\n";
        let mut localized = prefix.to_vec();
        localized.extend_from_slice(b"documents/preuve \xe9t\xe9.txt\r\n");
        assert!(validate_archive_listing(&localized).is_ok());

        for path in [
            &b"../secret"[..],
            &b"documents/../secret"[..],
            &b"documents/./secret"[..],
            &b"documents//secret"[..],
            &b"documents\\secret"[..],
            &b"C:/secret"[..],
            &b"/secret"[..],
        ] {
            let mut listing = prefix.to_vec();
            listing.extend_from_slice(path);
            listing.push(b'\n');
            assert!(validate_archive_listing(&listing).is_err());
        }
    }

    #[cfg(unix)]
    #[test]
    fn restore_extractor_rejects_symlinks() {
        use std::os::unix::fs::symlink;
        let root = test_root("restore-link-test");
        let source = root.join("source");
        fs::create_dir_all(source.join("documents")).unwrap();
        fs::write(source.join("database.dump"), b"database").unwrap();
        fs::write(
            source.join("manifest.json"),
            r#"{"format":"galaxam-local-backup-v1","database":"sirh_galaxam"}"#,
        )
        .unwrap();
        symlink("/etc/passwd", source.join("documents/link")).unwrap();
        let archive_path = root.join("backup.tar");
        let status = Command::new(tar_executable())
            .arg("-cf")
            .arg(&archive_path)
            .arg("-C")
            .arg(&source)
            .args(["database.dump", "manifest.json", "documents"])
            .status()
            .unwrap();
        assert!(status.success());
        assert!(extract_for_restore(&archive_path, &root.join("staging")).is_err());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn restore_replaces_documents_as_a_directory_unit() {
        let root = test_root("restore-documents-test");
        let home = root.join("home");
        let staging = root.join("staging");
        fs::create_dir_all(home.join("documents")).unwrap();
        fs::create_dir_all(staging.join("documents")).unwrap();
        fs::write(home.join("documents/old.txt"), b"old").unwrap();
        fs::write(staging.join("documents/new.txt"), b"new").unwrap();
        replace_documents(&home, &staging, true).unwrap();
        assert!(!home.join("documents/old.txt").exists());
        assert_eq!(fs::read(home.join("documents/new.txt")).unwrap(), b"new");
        replace_documents(&home, &staging, false).unwrap();
        assert!(fs::read_dir(home.join("documents"))
            .unwrap()
            .next()
            .is_none());
        fs::remove_dir_all(root).unwrap();
    }
}
