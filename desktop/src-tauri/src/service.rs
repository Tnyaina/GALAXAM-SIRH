//! Per-user launchd integration for the local GALAXAM service.
//! Service payloads are copied to a versioned directory outside the movable app bundle.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::sync::Arc;
use tauri::AppHandle;

const SERVICE_VERSION: &str = "0.1.1";

/// Cross-process ownership marker shared by a temporary Tauri PostgreSQL
/// owner and the long-lived LaunchAgent supervisor. The lock is released only
/// after the child database process has stopped.
#[derive(Clone)]
pub struct ServiceLock {
    _inner: Arc<ServiceLockInner>,
}

struct ServiceLockInner {
    path: PathBuf,
    pid: u32,
}

impl ServiceLock {
    pub fn acquire(home: &Path) -> Result<Self, String> {
        let dir = home.join("service");
        fs::create_dir_all(&dir).map_err(|_| "Dossier de service inaccessible".to_string())?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let meta = fs::symlink_metadata(&dir)
                .map_err(|_| "Dossier de service inaccessible".to_string())?;
            if !meta.file_type().is_dir() {
                return Err("Dossier de service invalide".into());
            }
            fs::set_permissions(&dir, fs::Permissions::from_mode(0o700))
                .map_err(|_| "Permissions du service invalides".to_string())?;
        }
        let path = dir.join("supervisor.lock");
        Self::acquire_at(path, std::process::id())
    }

    fn acquire_at(path: PathBuf, pid: u32) -> Result<Self, String> {
        let dir = path.parent().ok_or("Dossier de verrou invalide")?;
        fs::create_dir_all(dir).map_err(|_| "Dossier de verrou inaccessible".to_string())?;
        let pid_text = pid.to_string();
        let output = Command::new("/usr/bin/shlock")
            .args([
                "-f",
                path.to_str().ok_or("Chemin de verrou invalide")?,
                "-p",
                pid_text.as_str(),
            ])
            .output()
            .map_err(|_| "Verrouillage interprocessus indisponible".to_string())?;
        if !output.status.success() {
            return Err("Les services locaux sont déjà contrôlés par une autre opération".into());
        }
        let lock = Self {
            _inner: Arc::new(ServiceLockInner {
                path: path.clone(),
                pid,
            }),
        };
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&path, fs::Permissions::from_mode(0o600))
                .map_err(|_| "Permissions du verrou invalides".to_string())?;
        }
        Ok(lock)
    }
}

impl Drop for ServiceLockInner {
    fn drop(&mut self) {
        if fs::read_to_string(&self.path)
            .ok()
            .and_then(|value| value.trim().parse::<u32>().ok())
            == Some(self.pid)
        {
            let _ = fs::remove_file(&self.path);
        }
    }
}

pub fn ensure_running(
    app: &AppHandle,
    home: &Path,
    api_port: u16,
    pg_port: u16,
) -> Result<(), String> {
    if !cfg!(target_os = "macos") {
        return Err("Le service autonome est disponible uniquement sur macOS".into());
    }
    validate_installation(home)?;
    let resources = crate::resources::resource_dir(app)?;
    let id = installation_id(home)?;
    validate_cluster_identity(home, &id)?;
    let service_dir = stage_resources(home, &resources)?;
    install_agent(home, &service_dir, &id, api_port, pg_port)?;
    let agent = agent_path(home, &id)?;
    if let Some(status) = inspect_agent(home, &id)? {
        if status {
            return wait_api(api_port);
        }
        return Err("Un job launchd GALAXAM existe mais son identité ne correspond pas à cette installation".into());
    }
    let domain = launch_domain();
    let output = Command::new("/bin/launchctl")
        .args([
            "bootstrap",
            &domain,
            agent.to_str().ok_or("Chemin LaunchAgent invalide")?,
        ])
        .output()
        .map_err(|_| "LaunchAgent indisponible".to_string())?;
    if !output.status.success() {
        if inspect_agent(home, &id)?.unwrap_or(false) {
            return wait_api(api_port);
        }
        let detail = String::from_utf8_lossy(&output.stderr).trim().to_owned();
        return Err(if detail.is_empty() {
            "Impossible de démarrer le service local (launchd)".into()
        } else {
            format!("Impossible de démarrer le service local (launchd): {detail}")
        });
    }
    wait_api(api_port)
}

pub fn stop(home: &Path) -> Result<(), String> {
    let id = installation_id_existing(home)?;
    match inspect_agent(home, &id)? {
        None => return Ok(()),
        Some(false) => return Err("L'identité du service launchd est ambiguë; arrêt refusé".into()),
        Some(true) => {}
    }
    let target = launch_target(home, &id);
    let output = Command::new("/bin/launchctl")
        .args(["bootout", &target])
        .output()
        .map_err(|_| "Impossible d'arrêter le service local".to_string())?;
    if !output.status.success() {
        return Err("launchd n'a pas confirmé l'arrêt du service local".into());
    }
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
    while std::time::Instant::now() < deadline {
        if inspect_agent(home, &id)?.is_none() {
            return Ok(());
        }
        std::thread::sleep(std::time::Duration::from_millis(250));
    }
    return Err("Le service local ne s'est pas arrêté dans le délai attendu".into());
}

pub fn disable(home: &Path) -> Result<(), String> {
    stop(home)?;
    let id = installation_id_existing(home)?;
    let plist = agent_path(home, &id)?;
    if plist.exists() {
        let meta =
            fs::symlink_metadata(&plist).map_err(|_| "LaunchAgent inaccessible".to_string())?;
        if !meta.file_type().is_file() {
            return Err("LaunchAgent invalide; suppression refusée".into());
        }
        fs::remove_file(plist)
            .map_err(|_| "Désenregistrement du service local impossible".to_string())?;
    }
    Ok(())
}

fn inspect_agent(home: &Path, id: &str) -> Result<Option<bool>, String> {
    let output = Command::new("/bin/launchctl")
        .args(["print", &launch_target(home, id)])
        .output()
        .map_err(|_| "État launchd inaccessible".to_string())?;
    let text = format!(
        "{}{}",
        String::from_utf8_lossy(&output.stdout),
        String::from_utf8_lossy(&output.stderr)
    );
    // Some macOS versions return status 0 even when the requested label is absent.
    if launchctl_job_absent(output.status.success(), &text) {
        return Ok(None);
    }
    let plist = agent_path(home, id)?;
    let expected_script = home
        .join("service/versions")
        .join(SERVICE_VERSION)
        .join("galaxam-service.sh");
    let expected_script = expected_script.to_string_lossy().into_owned();
    let valid = text.contains(id)
        && text.contains(&expected_script)
        && fs::read_to_string(plist).is_ok_and(|p| p.contains(id) && p.contains(&expected_script));
    Ok(Some(valid))
}

fn validate_cluster_identity(home: &Path, id: &str) -> Result<(), String> {
    let marker = home.join("data/postgresql/config/installation.id");
    if marker.exists() {
        let meta = fs::symlink_metadata(&marker)
            .map_err(|_| "Identité du cluster inaccessible".to_string())?;
        if !meta.file_type().is_file() {
            return Err("Identité du cluster invalide".into());
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::{MetadataExt, PermissionsExt};
            if meta.permissions().mode() & 0o077 != 0 || meta.uid() != unsafe { libc::geteuid() } {
                return Err("Permissions de l'identité du cluster invalides".into());
            }
        }
        let existing =
            fs::read_to_string(marker).map_err(|_| "Identité du cluster illisible".to_string())?;
        if existing.trim() != id {
            return Err("Le cluster appartient à une autre installation GALAXAM".into());
        }
    } else {
        let mut opts = OpenOptions::new();
        opts.write(true).create_new(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            opts.mode(0o600);
        }
        let mut file = match opts.open(&marker) {
            Ok(file) => file,
            Err(_) if fs::read_to_string(&marker).is_ok_and(|value| value.trim() == id) => {
                return Ok(())
            }
            Err(_) => return Err("Identité du cluster impossible à enregistrer".into()),
        };
        writeln!(file, "{id}")
            .map_err(|_| "Identité du cluster impossible à écrire".to_string())?;
        file.sync_all()
            .map_err(|_| "Identité du cluster impossible à synchroniser".to_string())?;
    }
    Ok(())
}

fn validate_installation(home: &Path) -> Result<(), String> {
    let pg = home.join("data/postgresql");
    if !pg.join("cluster/PG_VERSION").is_file()
        || fs::read_to_string(pg.join("cluster/PG_VERSION"))
            .ok()
            .as_deref()
            != Some("15\n")
        || fs::read_to_string(pg.join("config/installation.ready"))
            .ok()
            .as_deref()
            != Some("postgresql=15\nschema=canonical-01-06\n")
        || !pg.join("cluster/global/pg_control").is_file()
    {
        return Err("Installation locale absente ou incomplète; aucune initialisation automatique n'a été lancée".into());
    }
    for path in [
        pg.join("config/installation.secrets"),
        pg.join("config/postgresql.conf"),
        pg.join("config/pg_hba.conf"),
    ] {
        let metadata = fs::symlink_metadata(&path)
            .map_err(|_| "Configuration du service local absente".to_string())?;
        if !metadata.file_type().is_file() {
            return Err("Configuration du service local invalide".into());
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            if metadata.permissions().mode() & 0o077 != 0 {
                return Err("Permissions privées du service local invalides".into());
            }
        }
    }
    Ok(())
}

fn installation_id(home: &Path) -> Result<String, String> {
    let path = home.join("config/installation.id");
    if path.exists() {
        return read_id(&path);
    }
    fs::create_dir_all(path.parent().ok_or("Dossier configuration invalide")?)
        .map_err(|_| "Dossier configuration inaccessible".to_string())?;
    let bytes: [u8; 24] = rand::random();
    let id = bytes.iter().map(|b| format!("{b:02x}")).collect::<String>();
    let mut opts = OpenOptions::new();
    opts.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        opts.mode(0o600);
    }
    let mut f = opts
        .open(&path)
        .map_err(|_| "Identité de l'installation impossible à créer".to_string())?;
    writeln!(f, "{id}")
        .map_err(|_| "Identité de l'installation impossible à écrire".to_string())?;
    f.sync_all()
        .map_err(|_| "Identité de l'installation impossible à synchroniser".to_string())?;
    Ok(id)
}
fn installation_id_existing(home: &Path) -> Result<String, String> {
    read_id(&home.join("config/installation.id"))
}
fn read_id(path: &Path) -> Result<String, String> {
    let meta = fs::symlink_metadata(path).map_err(|_| "Identité du service absente".to_string())?;
    if !meta.file_type().is_file() {
        return Err("Identité du service invalide".into());
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        if meta.permissions().mode() & 0o077 != 0 {
            return Err("Permissions de l'identité invalides".into());
        }
    }
    let value = fs::read_to_string(path)
        .map_err(|_| "Identité du service illisible".to_string())?
        .trim()
        .to_owned();
    if value.len() != 48 || !value.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Identité du service invalide".into());
    }
    Ok(value)
}

fn stage_resources(home: &Path, resources: &Path) -> Result<PathBuf, String> {
    let root = home.join("service/versions").join(SERVICE_VERSION);
    if root.join(".staged").is_file()
        && root.join("backend/backend.jar").is_file()
        && root.join("postgresql/bin/postgres").is_file()
    {
        return Ok(root);
    }
    if root.exists() {
        return Err(
            "Version de service incomplète; intervention requise, aucune ressource remplacée"
                .into(),
        );
    }
    let pending = root.with_extension("pending");
    if pending.exists() {
        return Err(
            "Copie de service interrompue; dossier provisoire conservé pour diagnostic".into(),
        );
    }
    fs::create_dir_all(root.parent().ok_or("Dossier de service invalide")?)
        .map_err(|_| "Dossier des composants du service inaccessible".to_string())?;
    fs::create_dir(&pending)
        .map_err(|_| "Dossier provisoire de service inaccessible".to_string())?;
    for (src, dst) in [("backend", "backend"), ("postgresql", "postgresql")] {
        copy_tree(&resources.join(src), &pending.join(dst))?;
    }
    let script = pending.join("galaxam-service.sh");
    fs::write(&script, SERVICE_SCRIPT)
        .map_err(|_| "Lanceur du service impossible à écrire".to_string())?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&script, fs::Permissions::from_mode(0o700))
            .map_err(|_| "Permissions du lanceur invalides".to_string())?;
    }
    fs::write(
        pending.join(".staged"),
        format!("version={SERVICE_VERSION}\n"),
    )
    .map_err(|_| "Validation des ressources du service impossible".to_string())?;
    fs::rename(&pending, &root)
        .map_err(|_| "Publication des ressources du service impossible".to_string())?;
    Ok(root)
}
fn copy_tree(from: &Path, to: &Path) -> Result<(), String> {
    let meta =
        fs::symlink_metadata(from).map_err(|_| "Ressource de service absente".to_string())?;
    if meta.file_type().is_symlink() {
        return Err("Lien symbolique interdit dans les ressources du service".into());
    }
    if meta.is_dir() {
        fs::create_dir_all(to).map_err(|_| "Copie des ressources impossible".to_string())?;
        for entry in
            fs::read_dir(from).map_err(|_| "Lecture des ressources impossible".to_string())?
        {
            let entry = entry.map_err(|_| "Lecture des ressources impossible".to_string())?;
            copy_tree(&entry.path(), &to.join(entry.file_name()))?;
        }
    } else if meta.is_file() {
        fs::copy(from, to).map_err(|_| "Copie des ressources impossible".to_string())?;
    } else {
        return Err("Type de ressource non pris en charge".into());
    }
    Ok(())
}

fn agent_dir(home: &Path) -> Result<PathBuf, String> {
    if let Some(test_dir) = std::env::args_os()
        .find_map(|arg| {
            arg.to_string_lossy()
                .strip_prefix("--galaxam-test-launch-agent-dir=")
                .filter(|value| !value.is_empty())
                .map(PathBuf::from)
        })
        .or_else(|| std::env::var_os("GALAXAM_TEST_LAUNCH_AGENT_DIR").map(PathBuf::from))
    {
        return Ok(test_dir);
    }
    let user_home = std::env::var_os("HOME").ok_or("Répertoire utilisateur introuvable")?;
    let _ = home;
    Ok(PathBuf::from(user_home).join("Library/LaunchAgents"))
}
fn agent_path(home: &Path, id: &str) -> Result<PathBuf, String> {
    Ok(agent_dir(home)?.join(format!("mg.galaxam.sirh.service.{id}.plist")))
}
fn label(id: &str) -> String {
    format!("mg.galaxam.sirh.service.{id}")
}
fn launch_target(_home: &Path, id: &str) -> String {
    format!("{}/{}", launch_domain(), label(id))
}
fn launch_domain() -> String {
    format!("gui/{}", unsafe { libc::geteuid() })
}
fn launchctl_job_absent(success: bool, output: &str) -> bool {
    !success
        || output
            .to_ascii_lowercase()
            .contains("could not find service")
}
fn install_agent(
    home: &Path,
    service_dir: &Path,
    id: &str,
    api: u16,
    pg: u16,
) -> Result<(), String> {
    let dir = agent_dir(home)?;
    fs::create_dir_all(&dir).map_err(|_| "Dossier LaunchAgents inaccessible".to_string())?;
    let script = service_dir.join("galaxam-service.sh");
    let script_meta =
        fs::symlink_metadata(&script).map_err(|_| "Lanceur du service absent".to_string())?;
    if !script_meta.file_type().is_file() {
        return Err("Lanceur du service invalide".into());
    }
    let plist = agent_path(home, id)?;
    let body = format!(
        r#"<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>Label</key><string>{}</string><key>ProgramArguments</key><array><string>/bin/sh</string><string>{}</string><string>{}</string><string>{}</string><string>{}</string><string>{}</string><string>{}</string></array><key>RunAtLoad</key><true/><key>KeepAlive</key><true/><key>ThrottleInterval</key><integer>10</integer><key>ExitTimeOut</key><integer>45</integer><key>StandardOutPath</key><string>{}</string><key>StandardErrorPath</key><string>{}</string></dict></plist>
"#,
        label(id),
        xml(&script.to_string_lossy()),
        xml(&home.to_string_lossy()),
        xml(&service_dir.to_string_lossy()),
        id,
        api,
        pg,
        xml(&home.join("logs/service.log").to_string_lossy()),
        xml(&home.join("logs/service.log").to_string_lossy())
    );
    fs::create_dir_all(home.join("logs"))
        .map_err(|_| "Dossier de journaux inaccessible".to_string())?;
    let tmp = plist.with_extension(format!("plist.{}.pending", std::process::id()));
    let mut opts = OpenOptions::new();
    opts.write(true).create(true).truncate(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        opts.mode(0o600);
    }
    let mut f = opts
        .open(&tmp)
        .map_err(|_| "LaunchAgent impossible à créer".to_string())?;
    f.write_all(body.as_bytes())
        .map_err(|_| "LaunchAgent impossible à écrire".to_string())?;
    f.sync_all()
        .map_err(|_| "LaunchAgent impossible à synchroniser".to_string())?;
    fs::rename(tmp, plist).map_err(|_| "LaunchAgent impossible à enregistrer".to_string())?;
    Ok(())
}
fn xml(s: &str) -> String {
    s.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&apos;")
}
fn wait_api(port: u16) -> Result<(), String> {
    let until = std::time::Instant::now() + std::time::Duration::from_secs(45);
    while std::time::Instant::now() < until {
        use std::io::{Read, Write};
        if let Ok(mut stream) = std::net::TcpStream::connect_timeout(
            &format!("127.0.0.1:{port}").parse().unwrap(),
            std::time::Duration::from_millis(300),
        ) {
            let _ = stream.set_read_timeout(Some(std::time::Duration::from_millis(500)));
            if stream.write_all(format!("GET /api/auth/ping HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n").as_bytes()).is_ok() {
                let mut response = [0u8; 128];
                if stream.read(&mut response).is_ok_and(|n| response[..n].starts_with(b"HTTP/1.1 200") || response[..n].starts_with(b"HTTP/1.0 200")) { return Ok(()); }
            }
        }
        std::thread::sleep(std::time::Duration::from_millis(300));
    }
    Err("Le service GALAXAM a été enregistré, mais l'API ne répond pas encore".into())
}

const SERVICE_SCRIPT: &str = r##"#!/bin/sh
set -eu
HOME_DIR="$1"; VERSION_DIR="$2"; INSTALL_ID="$3"; API_PORT="$4"; PG_PORT="$5"
umask 077
mkdir -p "$HOME_DIR/service" "$HOME_DIR/logs"
LOCK="$HOME_DIR/service/supervisor.lock"
/usr/bin/shlock -f "$LOCK" -p $$ || { echo 'Service GALAXAM déjà contrôlé par un superviseur.'; exit 73; }
trap 'rm -f "$LOCK"' EXIT
PG="$VERSION_DIR/postgresql/bin"; JAVA="$VERSION_DIR/backend/runtime/bin/java"; JAR="$VERSION_DIR/backend/backend.jar"
CLUSTER="$HOME_DIR/data/postgresql/cluster"; CONFIG="$HOME_DIR/data/postgresql/config"
LOG="$HOME_DIR/logs/service.log"; BACKEND_PID="$HOME_DIR/service/backend.pid"
PG_OWNED=0
shutdown() {
  if [ -f "$BACKEND_PID" ]; then PID=$(cat "$BACKEND_PID" 2>/dev/null || true); if [ -n "$PID" ]; then CMD=$(/bin/ps -p "$PID" -o command= 2>/dev/null || true); case "$CMD" in *"-Dgalaxam.install.id=$INSTALL_ID"*"$JAR"*) /bin/kill -TERM "$PID" 2>/dev/null || true; DEADLINE=20; while [ "$DEADLINE" -gt 0 ] && /bin/kill -0 "$PID" 2>/dev/null; do sleep 1; DEADLINE=$((DEADLINE-1)); done; if /bin/kill -0 "$PID" 2>/dev/null; then /bin/kill -KILL "$PID" 2>/dev/null || true; fi;; esac; fi; fi
  if [ "$PG_OWNED" = 1 ]; then "$PG/pg_ctl" -D "$CLUSTER" -m fast -w -t 20 stop >/dev/null 2>&1 || true; fi
  exit 0
}
trap shutdown TERM INT HUP
APP_PASS=$(/usr/bin/awk -F= '$1=="app"{print $2}' "$CONFIG/installation.secrets")
ADMIN_PASS=$(/usr/bin/awk -F= '$1=="admin"{print $2}' "$CONFIG/installation.secrets")
JWT=$(/usr/bin/awk -F= '$1=="jwt"{print $2}' "$CONFIG/installation.secrets")
DB_URL="jdbc:postgresql://127.0.0.1:$PG_PORT/sirh_galaxam"; DB_USER=galaxam_app
if [ -z "$APP_PASS" ] || [ -z "$ADMIN_PASS" ] || [ -z "$JWT" ]; then echo 'Configuration locale incomplète.'; exit 78; fi
export PGPASSWORD="$APP_PASS"
if [ -z "$PGPASSWORD" ]; then echo 'Secrets de base locale absents.'; exit 78; fi
if [ "$(cat "$CONFIG/installation.id" 2>/dev/null || true)" != "$INSTALL_ID" ]; then echo 'Identité du cluster différente de celle du service.'; exit 78; fi
if [ "$(cat "$CLUSTER/PG_VERSION" 2>/dev/null || true)" != "15" ] || [ "$(cat "$CONFIG/installation.ready" 2>/dev/null || true)" != "postgresql=15
schema=canonical-01-06" ]; then echo 'Cluster GALAXAM incomplet; aucune initialisation ne sera exécutée.'; exit 78; fi
validate_pg() { [ "$("$PG/pg_ctl" -D "$CLUSTER" status >/dev/null 2>&1; echo $?)" = 0 ] || return 1; actual=$(PGPASSWORD="$ADMIN_PASS" "$PG/psql" -X -A -t -w -h 127.0.0.1 -p "$PG_PORT" -U galaxam_cluster_admin -d postgres -c 'show data_directory' 2>/dev/null || true); [ "$(cd "$actual" 2>/dev/null && pwd -P)" = "$(cd "$CLUSTER" && pwd -P)" ]; }
if ! validate_pg; then
  if "$PG/pg_isready" -h 127.0.0.1 -p "$PG_PORT" -q; then echo 'Port PostgreSQL occupé par un service non vérifié.'; exit 73; fi
  "$PG/pg_ctl" -D "$CLUSTER" -l "$HOME_DIR/data/postgresql/logs/postgres.log" -o "-c config_file=$CONFIG/postgresql.conf" -w -t 30 start || { echo 'Démarrage PostgreSQL impossible.'; exit 70; }
  PG_OWNED=1
  validate_pg || { echo 'Identité du cluster PostgreSQL non confirmée.'; shutdown; }
fi
PG_OWNED=1
TABLES=$("$PG/psql" -X -A -t -w -h 127.0.0.1 -p "$PG_PORT" -U galaxam_app -d sirh_galaxam -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'" 2>/dev/null || true)
EXT=$("$PG/psql" -X -A -t -w -h 127.0.0.1 -p "$PG_PORT" -U galaxam_app -d sirh_galaxam -c "SELECT count(*) FROM pg_extension WHERE extname='btree_gist'" 2>/dev/null || true)
case "$TABLES" in ''|*[!0-9]*) echo 'Schéma GALAXAM non vérifiable.'; exit 78;; esac
[ "$TABLES" -ge 90 ] && [ "$EXT" = 1 ] || { echo 'Schéma GALAXAM ou extension requise incomplet.'; exit 78; }
if [ -f "$BACKEND_PID" ]; then
  OLD_PID=$(cat "$BACKEND_PID" 2>/dev/null || true)
  if [ -n "$OLD_PID" ] && /bin/kill -0 "$OLD_PID" 2>/dev/null; then
    CMD=$(/bin/ps -p "$OLD_PID" -o command= 2>/dev/null || true)
    case "$CMD" in *"-Dgalaxam.install.id=$INSTALL_ID"*"$JAR"*) while /bin/kill -0 "$OLD_PID" 2>/dev/null; do sleep 2; done;; *) echo 'Processus backend ambigu; arrêt refusé.'; exit 73;; esac
  fi
  rm -f "$BACKEND_PID"
fi
LISTEN_PIDS=$(/usr/sbin/lsof -nP -iTCP:"$API_PORT" -sTCP:LISTEN -t 2>/dev/null || true)
if [ -n "$LISTEN_PIDS" ]; then
  for PID in $LISTEN_PIDS; do CMD=$(/bin/ps -p "$PID" -o command= 2>/dev/null || true); case "$CMD" in *"-Dgalaxam.install.id=$INSTALL_ID"*"$JAR"*) echo "$PID" > "$BACKEND_PID"; chmod 600 "$BACKEND_PID"; while /bin/kill -0 "$PID" 2>/dev/null; do sleep 2; done;; *) echo 'Port API occupé par un processus inconnu; démarrage refusé.'; exit 73;; esac; done
fi
shutdown() {
  if [ -f "$BACKEND_PID" ]; then PID=$(cat "$BACKEND_PID" 2>/dev/null || true); if [ -n "$PID" ]; then CMD=$(/bin/ps -p "$PID" -o command= 2>/dev/null || true); case "$CMD" in *"-Dgalaxam.install.id=$INSTALL_ID"*"$JAR"*) /bin/kill -TERM "$PID" 2>/dev/null || true; DEADLINE=20; while [ "$DEADLINE" -gt 0 ] && /bin/kill -0 "$PID" 2>/dev/null; do sleep 1; DEADLINE=$((DEADLINE-1)); done; if /bin/kill -0 "$PID" 2>/dev/null; then /bin/kill -KILL "$PID" 2>/dev/null || true; fi;; esac; fi; fi
  "$PG/pg_ctl" -D "$CLUSTER" -m fast -w -t 20 stop >/dev/null 2>&1 || true
  exit 0
}
trap shutdown TERM INT HUP
while :; do
  GALAXAM_DESKTOP_HOME="$HOME_DIR" GALAXAM_BACKEND_PORT="$API_PORT" GALAXAM_DESKTOP_CORS_ORIGIN=http://127.0.0.1:18888 GALAXAM_DESKTOP_BOOTSTRAP_STATE="$HOME_DIR/config/bootstrap.state" GALAXAM_DESKTOP_DB_URL="$DB_URL" GALAXAM_DESKTOP_DB_USER="$DB_USER" GALAXAM_DESKTOP_DB_PASSWORD="$APP_PASS" GALAXAM_DESKTOP_JWT_SECRET="$JWT" "$JAVA" -Dgalaxam.install.id="$INSTALL_ID" -Dspring.profiles.active=desktop -jar "$JAR" >>"$LOG" 2>&1 &
  PID=$!; echo "$PID" > "$BACKEND_PID"; chmod 600 "$BACKEND_PID"
  READY=0; TICKS=0; RESULT=0
  while /bin/kill -0 "$PID" 2>/dev/null; do
    if ! validate_pg; then echo 'PostgreSQL a perdu son identité ou sa disponibilité; arrêt du backend.'; /bin/kill -TERM "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; exit 75; fi
    if [ "$READY" = 0 ] && /usr/bin/curl --silent --show-error --max-time 2 --fail "http://127.0.0.1:$API_PORT/api/auth/ping" >/dev/null 2>&1; then READY=1; echo 'API locale prête.'; fi
    TICKS=$((TICKS+1)); if [ "$READY" = 0 ] && [ "$TICKS" -ge 90 ]; then echo 'Délai de disponibilité Spring Boot dépassé.'; /bin/kill -TERM "$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; break; fi
    sleep 2
  done
  set +e; wait "$PID"; RESULT=$?; set -e
  rm -f "$BACKEND_PID"
  echo "Backend arrêté (code $RESULT); relance contrôlée dans 10 secondes."; sleep 10
done
"##;

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn plist_escapes_paths_and_is_installation_scoped() {
        assert_eq!(xml("/tmp/A&B"), "/tmp/A&amp;B");
        assert!(label(&"a".repeat(48)).contains(&"a".repeat(48)));
    }
    #[test]
    fn script_has_lock_and_data_identity_checks() {
        assert!(SERVICE_SCRIPT.contains("shlock -f"));
        assert!(SERVICE_SCRIPT.contains("show data_directory"));
        assert!(SERVICE_SCRIPT.contains("Processus backend ambigu"));
        assert!(!SERVICE_SCRIPT.contains("--spring.datasource.password"));
        let path =
            std::env::temp_dir().join(format!("galaxam-service-script-{}.sh", std::process::id()));
        fs::write(&path, SERVICE_SCRIPT).unwrap();
        let checked = Command::new("/bin/sh")
            .arg("-n")
            .arg(&path)
            .status()
            .unwrap();
        let _ = fs::remove_file(path);
        assert!(checked.success());
    }
    #[test]
    fn launchctl_success_text_can_still_mean_the_job_is_absent() {
        let absent = "Bad request.\nCould not find service \"mg.galaxam.sirh.service.test\" in domain for user gui: 502\n";
        assert!(launchctl_job_absent(true, absent));
        assert!(!launchctl_job_absent(
            true,
            "state = running\nprogram = /bin/sh\n"
        ));
    }
    #[test]
    fn temporary_database_owner_excludes_another_supervisor_until_release() {
        let root = std::env::temp_dir().join(format!("galaxam-owner-lock-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(&root).unwrap();
        let mut other_owner = Command::new("/bin/sleep").arg("30").spawn().unwrap();
        let lock_path = root.join("supervisor.lock");
        let held = ServiceLock::acquire_at(lock_path.clone(), other_owner.id()).unwrap();
        let competing = Command::new("/usr/bin/shlock")
            .args([
                "-f",
                lock_path.to_str().unwrap(),
                "-p",
                &std::process::id().to_string(),
            ])
            .status()
            .unwrap();
        assert!(
            !competing.success(),
            "another live owner must not acquire the service lock"
        );
        drop(held);
        assert!(
            !lock_path.exists(),
            "the owning operation releases its marker"
        );
        let _ = other_owner.kill();
        let _ = other_owner.wait();
        let _ = fs::remove_dir_all(root);
    }
}
