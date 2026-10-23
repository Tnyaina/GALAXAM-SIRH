//! Embedded PostgreSQL lifecycle for the macOS desktop prototype.
use crate::connection::config::DesktopConfig;
use rand::RngCore;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, Instant};
use tauri::{AppHandle, Emitter};

const DATABASE: &str = "sirh_galaxam";
const APP_ROLE: &str = "galaxam_app";
const ADMIN_ROLE: &str = "galaxam_cluster_admin";
const SCHEMAS: [&str; 6] = [
    "01_schema_core_securite.sql",
    "02_schema_referentiels_rh.sql",
    "03_schema_employes_contrats.sql",
    "04_schema_conges.sql",
    "05_schema_pointage.sql",
    "06_schema_paie.sql",
];

fn pg_binary(bin: &Path, name: &str) -> PathBuf {
    #[cfg(windows)]
    {
        return bin.join(format!("{name}.exe"));
    }
    #[cfg(not(windows))]
    {
        bin.join(name)
    }
}

fn configure_pg_environment(command: &mut Command, bin: &Path) {
    command.env_clear();
    #[cfg(unix)]
    {
        command.env("PATH", "/usr/bin:/bin:/usr/sbin:/sbin");
        let _ = bin;
    }
    #[cfg(windows)]
    {
        let system_root = std::env::var_os("SystemRoot")
            .map(PathBuf::from)
            .unwrap_or_else(|| PathBuf::from(r"C:\Windows"));
        let system32 = system_root.join("System32");
        let path = std::env::join_paths([bin, system32.as_path(), system_root.as_path()]);
        command.env("PATH", path.unwrap_or_else(|_| system32.into_os_string()));
        for variable in [
            "SystemRoot",
            "WINDIR",
            "SystemDrive",
            "ComSpec",
            "PATHEXT",
            "TEMP",
            "TMP",
            "USERPROFILE",
            "APPDATA",
            "LOCALAPPDATA",
        ] {
            if let Some(value) = std::env::var_os(variable) {
                command.env(variable, value);
            }
        }
    }
    #[cfg(not(any(unix, windows)))]
    let _ = bin;
}

struct PgPaths {
    bin: PathBuf,
    schemas: PathBuf,
    base: PathBuf,
    cluster: PathBuf,
    pending: PathBuf,
    config: PathBuf,
    logs: PathBuf,
    backups: PathBuf,
}

struct InstallationSecrets {
    admin: String,
    app: String,
    jwt: String,
}

pub struct PostgresStart {
    pub app_password: String,
    pub has_admin: bool,
}

pub struct PostgresProcess {
    process: Mutex<
        Option<(
            Child,
            PathBuf,
            PathBuf,
            crate::platform::service::ServiceLock,
        )>,
    >,
    operation: Mutex<()>,
}

impl PostgresProcess {
    pub fn new() -> Self {
        Self {
            process: Mutex::new(None),
            operation: Mutex::new(()),
        }
    }

    pub fn start(
        &self,
        app: &AppHandle,
        config: &DesktopConfig,
    ) -> Result<PostgresStart, &'static str> {
        if cfg!(target_os = "windows") {
            return Err("PostgreSQL local doit être démarré par le service Windows");
        }
        let resources = crate::app::resources::resource_dir(app)
            .map_err(|_| "Répertoire des ressources PostgreSQL introuvable")?;
        self.start_with_resources(&resources, config, |stage| {
            let _ = app.emit("setup-progress", stage);
        })
    }

    pub(crate) fn start_with_resources(
        &self,
        resources: &Path,
        config: &DesktopConfig,
        progress: impl Fn(&str),
    ) -> Result<PostgresStart, &'static str> {
        let _operation = self
            .operation
            .lock()
            .map_err(|_| "Gestionnaire PostgreSQL indisponible")?;
        if !cfg!(any(target_os = "macos", target_os = "windows")) {
            return Err("PostgreSQL embarqué non disponible sur cette plateforme");
        }
        if self
            .process
            .lock()
            .map_err(|_| "Gestionnaire PostgreSQL indisponible")?
            .is_some()
        {
            return Err("PostgreSQL est déjà géré par cette instance");
        }
        let ownership_lock = crate::platform::service::ServiceLock::acquire(&config.home)
            .map_err(|_| "Une autre opération contrôle déjà le service local")?;
        if config
            .home
            .symlink_metadata()
            .map(|m| m.file_type().is_symlink())
            .unwrap_or(false)
        {
            return Err("Racine desktop symbolique interdite");
        }
        let reservation = TcpListener::bind(("127.0.0.1", config.postgres_port))
            .map_err(|_| "Port PostgreSQL occupé ; aucune base existante ne sera utilisée")?;
        let bin = resources.join("postgresql/bin");
        let schemas = resources.join("postgresql/schema");
        for name in ["postgres", "initdb", "pg_ctl", "pg_isready", "psql"] {
            if !pg_binary(&bin, name).is_file() {
                return Err("Binaire PostgreSQL absent du bundle");
            }
        }
        for name in SCHEMAS {
            if !schemas.join(name).is_file() {
                return Err("Schéma canonique absent du bundle");
            }
        }
        let base = config.home.join("data/postgresql");
        let paths = PgPaths {
            bin,
            schemas,
            cluster: base.join("cluster"),
            pending: base.join("cluster.pending"),
            config: base.join("config"),
            logs: base.join("logs"),
            backups: base.join("backups"),
            base,
        };
        let support = config.home.parent().ok_or("Racine desktop invalide")?;
        fs::create_dir_all(support).map_err(|_| "Dossier Application Support inaccessible")?;
        private_directory(&config.home)?;
        private_directory(&config.home.join("data"))?;
        if paths.pending.exists() {
            return Err("Initialisation PostgreSQL interrompue ; intervention manuelle requise");
        }
        let fresh = !paths.cluster.exists();
        if fresh && paths.base.exists() {
            return Err(
                "Répertoire PostgreSQL existant sans cluster validé ; intervention requise",
            );
        }
        if fresh {
            private_directory(&paths.base)?;
            fs::create_dir(&paths.pending)
                .map_err(|_| "Création du cluster provisoire impossible")?;
            private_mode(&paths.pending, 0o700)?;
            private_directory(&paths.config)?;
            private_directory(&paths.logs)?;
            private_directory(&paths.backups)?;
        } else {
            for directory in [
                &paths.base,
                &paths.cluster,
                &paths.config,
                &paths.logs,
                &paths.backups,
            ] {
                check_private_directory(directory)?;
            }
            if !paths.cluster.join("PG_VERSION").is_file()
                || fs::read_to_string(paths.cluster.join("PG_VERSION"))
                    .ok()
                    .as_deref()
                    != Some("15\n")
                || !paths.cluster.join("global/pg_control").is_file()
                || check_private_file(&paths.config.join("installation.ready")).is_err()
                || fs::read_to_string(paths.config.join("installation.ready"))
                    .ok()
                    .as_deref()
                    != Some("postgresql=15\nschema=canonical-01-06\n")
            {
                return Err(
                    "Cluster PostgreSQL existant mais non validé ; aucune réinitialisation",
                );
            }
        }
        let secrets = if fresh {
            let new = InstallationSecrets::generate();
            write_new_private(
                &paths.config.join("installation.secrets"),
                &format!("admin={}\napp={}\njwt={}\n", new.admin, new.app, new.jwt),
            )?;
            new
        } else {
            InstallationSecrets::read(&paths.config.join("installation.secrets"))?
        };
        write_config(&paths, config.postgres_port)?;
        let has_admin = if fresh {
            progress("cluster");
            let password_path = paths.config.join("initdb-password.pending");
            write_new_private(&password_path, &format!("{}\n", secrets.admin))?;
            let mut initdb = Command::new(pg_binary(&paths.bin, "initdb"));
            initdb
                .arg("-D")
                .arg(&paths.pending)
                .arg("-U")
                .arg(ADMIN_ROLE)
                .arg("--encoding=UTF8")
                .arg("--locale=C")
                .arg("--auth-local=scram-sha-256")
                .arg("--auth-host=scram-sha-256")
                .arg(format!("--pwfile={}", password_path.display()));
            configure_pg_environment(&mut initdb, &paths.bin);
            let init_result = run_quiet(&mut initdb, &paths.logs.join("initdb.log"));
            let _ = fs::remove_file(&password_path);
            init_result.map_err(|_| "initdb a échoué ; cluster provisoire conservé")?;
            self.launch(
                &paths,
                &paths.pending,
                config.postgres_port,
                &secrets.admin,
                reservation,
                ownership_lock.clone(),
            )?;
            progress("schema");
            let setup = (|| {
                self.psql(&paths, config.postgres_port, ADMIN_ROLE, &secrets.admin, "postgres",
                    &format!("CREATE ROLE {APP_ROLE} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD '{}'", secrets.app))?;
                self.psql(
                    &paths,
                    config.postgres_port,
                    ADMIN_ROLE,
                    &secrets.admin,
                    "postgres",
                    &format!("CREATE DATABASE {DATABASE} OWNER {APP_ROLE}"),
                )?;
                for schema in SCHEMAS {
                    self.psql_file(
                        &paths,
                        config.postgres_port,
                        &secrets.app,
                        &paths.schemas.join(schema),
                    )?;
                }
                self.verify_schema(&paths, config.postgres_port, &secrets.app)?;
                self.has_admin_at(&paths.bin, config, &secrets.app)
            })();
            self.stop_inner();
            let has_admin =
                setup.map_err(|_| "Initialisation SQL incomplète ; cluster provisoire conservé")?;
            fs::rename(&paths.pending, &paths.cluster)
                .map_err(|_| "Validation du cluster impossible ; intervention requise")?;
            write_new_private(
                &paths.config.join("installation.ready"),
                "postgresql=15\nschema=canonical-01-06\n",
            )?;
            has_admin
        } else {
            progress("database-check");
            self.launch(
                &paths,
                &paths.cluster,
                config.postgres_port,
                &secrets.admin,
                reservation,
                ownership_lock.clone(),
            )?;
            let checked = self
                .verify_schema(&paths, config.postgres_port, &secrets.app)
                .and_then(|_| self.has_admin_at(&paths.bin, config, &secrets.app));
            if checked.is_err() {
                self.stop_inner();
            }
            checked?
        };
        Ok(PostgresStart {
            app_password: secrets.app,
            has_admin,
        })
    }

    fn launch(
        &self,
        paths: &PgPaths,
        cluster: &Path,
        port: u16,
        admin_password: &str,
        reservation: TcpListener,
        ownership_lock: crate::platform::service::ServiceLock,
    ) -> Result<(), &'static str> {
        let log = private_append(&paths.logs.join("postgres.log"))?;
        let error = log
            .try_clone()
            .map_err(|_| "Journal PostgreSQL inaccessible")?;
        let mut command = Command::new(pg_binary(&paths.bin, "postgres"));
        command
            .arg("-D")
            .arg(cluster)
            .arg("-c")
            .arg(format!(
                "config_file={}",
                paths.config.join("postgresql.conf").display()
            ))
            .stdin(Stdio::null())
            .stdout(Stdio::from(log))
            .stderr(Stdio::from(error));
        configure_pg_environment(&mut command, &paths.bin);
        command.env("HOME", paths.base.parent().unwrap_or(&paths.base));
        drop(reservation);
        let child = command
            .spawn()
            .map_err(|_| "Démarrage PostgreSQL impossible")?;
        *self
            .process
            .lock()
            .map_err(|_| "Gestionnaire PostgreSQL indisponible")? = Some((
            child,
            cluster.to_path_buf(),
            pg_binary(&paths.bin, "pg_ctl"),
            ownership_lock,
        ));
        let deadline = Instant::now() + Duration::from_secs(20);
        while Instant::now() < deadline {
            if pg_ready(&pg_binary(&paths.bin, "pg_isready"), port)
                && self.identity_ok(paths, cluster, port, admin_password)
            {
                return Ok(());
            }
            if self
                .process
                .lock()
                .ok()
                .and_then(|mut p| {
                    p.as_mut()
                        .and_then(|(child, _, _, _)| child.try_wait().ok())
                        .flatten()
                })
                .is_some()
            {
                self.stop_inner();
                return Err("PostgreSQL s'est arrêté ; consulter le journal");
            }
            thread::sleep(Duration::from_millis(200));
        }
        self.stop_inner();
        Err("PostgreSQL ne répond pas ou le port mène à un autre cluster")
    }

    fn identity_ok(&self, paths: &PgPaths, cluster: &Path, port: u16, password: &str) -> bool {
        let result = psql_output(
            &pg_binary(&paths.bin, "psql"),
            port,
            ADMIN_ROLE,
            password,
            "postgres",
            Some("SHOW data_directory;\n"),
            None,
        );
        result
            .ok()
            .and_then(|value| fs::canonicalize(value.trim()).ok())
            == fs::canonicalize(cluster).ok()
    }

    fn psql(
        &self,
        paths: &PgPaths,
        port: u16,
        user: &str,
        password: &str,
        database: &str,
        sql: &str,
    ) -> Result<String, &'static str> {
        psql_output(
            &pg_binary(&paths.bin, "psql"),
            port,
            user,
            password,
            database,
            Some(&format!("{sql};\n")),
            None,
        )
    }

    fn psql_file(
        &self,
        paths: &PgPaths,
        port: u16,
        password: &str,
        path: &Path,
    ) -> Result<(), &'static str> {
        psql_output(
            &pg_binary(&paths.bin, "psql"),
            port,
            APP_ROLE,
            password,
            DATABASE,
            None,
            Some(path),
        )
        .map(|_| ())
    }

    fn verify_schema(
        &self,
        paths: &PgPaths,
        port: u16,
        password: &str,
    ) -> Result<(), &'static str> {
        let result = self.psql(
            paths,
            port,
            APP_ROLE,
            password,
            DATABASE,
            "SELECT NOT EXISTS (
                SELECT 1 FROM (VALUES
                    ('ref_role'), ('utilisateur'), ('utilisateur_session'),
                    ('ref_site'), ('ref_calendrier_ferie'),
                    ('employe'), ('contrat_travail'),
                    ('demande_conge'), ('conge_journal_droit'),
                    ('periode_paie'), ('pointage_jour'), ('pointage_cumul_periode'),
                    ('paie_config_legale'), ('bulletin_paie'), ('paie_stc_reglement')
                ) AS required(name)
                WHERE NOT EXISTS (
                    SELECT 1 FROM information_schema.tables AS actual
                    WHERE actual.table_schema = 'public'
                      AND actual.table_type = 'BASE TABLE'
                      AND actual.table_name = required.name
                )
            )",
        )?;
        if result.trim() != "t" {
            return Err("Schéma GALAXAM incomplet");
        }
        let extension = self.psql(
            paths,
            port,
            APP_ROLE,
            password,
            DATABASE,
            "SELECT extname FROM pg_extension WHERE extname='btree_gist'",
        )?;
        if extension.trim() != "btree_gist" {
            return Err("Extension btree_gist absente");
        }
        Ok(())
    }

    pub fn stop(&self) {
        let Ok(_operation) = self.operation.lock() else {
            return;
        };
        self.stop_inner();
    }

    fn stop_inner(&self) {
        let Some((mut child, cluster, pg_ctl, _ownership_lock)) =
            self.process.lock().ok().and_then(|mut state| state.take())
        else {
            return;
        };
        let mut command = Command::new(&pg_ctl);
        command
            .arg("-D")
            .arg(&cluster)
            .arg("-m")
            .arg("fast")
            .arg("-w")
            .arg("-t")
            .arg("10")
            .arg("stop")
            .stdout(Stdio::null())
            .stderr(Stdio::null());
        if let Some(bin) = pg_ctl.parent() {
            configure_pg_environment(&mut command, bin);
        }
        let _ = command.status();
        let deadline = Instant::now() + Duration::from_secs(10);
        while Instant::now() < deadline {
            if child.try_wait().ok().flatten().is_some() {
                return;
            }
            thread::sleep(Duration::from_millis(100));
        }
        let _ = child.kill();
        let _ = child.wait();
    }

    fn has_admin_at(
        &self,
        bin: &Path,
        config: &DesktopConfig,
        app_password: &str,
    ) -> Result<bool, &'static str> {
        let binary = pg_binary(bin, "psql");
        let count = psql_output(
            &binary,
            config.postgres_port,
            APP_ROLE,
            app_password,
            DATABASE,
            Some("SELECT count(*) FROM utilisateur u JOIN ref_role r ON r.id_role=u.id_role WHERE r.code_role='ROLE_ADMIN';\n"),
            None,
        )?;
        Ok(count
            .trim()
            .parse::<u64>()
            .map_err(|_| "État administrateur invalide")?
            > 0)
    }
}

impl Drop for PostgresProcess {
    fn drop(&mut self) {
        self.stop();
    }
}

impl InstallationSecrets {
    fn generate() -> Self {
        fn random_hex(bytes: usize) -> String {
            let mut value = vec![0u8; bytes];
            rand::rngs::OsRng.fill_bytes(&mut value);
            value.iter().map(|byte| format!("{byte:02x}")).collect()
        }
        Self {
            admin: random_hex(32),
            app: random_hex(32),
            jwt: random_hex(64),
        }
    }

    fn read(path: &Path) -> Result<Self, &'static str> {
        check_private_file(path)?;
        let text = fs::read_to_string(path).map_err(|_| "Secrets PostgreSQL illisibles")?;
        let mut admin = None;
        let mut app = None;
        let mut jwt = None;
        for line in text.lines() {
            let (key, value) = line.split_once('=').ok_or("Secrets PostgreSQL invalides")?;
            if !value.bytes().all(|c| c.is_ascii_hexdigit()) {
                return Err("Secrets PostgreSQL invalides");
            }
            match key {
                "admin" if value.len() == 64 && admin.is_none() => admin = Some(value.to_owned()),
                "app" if value.len() == 64 && app.is_none() => app = Some(value.to_owned()),
                "jwt" if value.len() == 128 && jwt.is_none() => jwt = Some(value.to_owned()),
                _ => return Err("Secrets PostgreSQL invalides"),
            }
        }
        Ok(Self {
            admin: admin.ok_or("Secret administrateur absent")?,
            app: app.ok_or("Secret applicatif absent")?,
            jwt: jwt.ok_or("Secret JWT absent")?,
        })
    }
}

fn pg_ready(binary: &Path, port: u16) -> bool {
    let mut command = Command::new(binary);
    command
        .arg("-h")
        .arg("127.0.0.1")
        .arg("-p")
        .arg(port.to_string())
        .arg("-q")
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    if let Some(bin) = binary.parent() {
        configure_pg_environment(&mut command, bin);
    }
    command.status().is_ok_and(|s| s.success())
}

fn psql_output(
    binary: &Path,
    port: u16,
    user: &str,
    password: &str,
    database: &str,
    sql: Option<&str>,
    file: Option<&Path>,
) -> Result<String, &'static str> {
    let mut command = Command::new(binary);
    command
        .arg("-X")
        .arg("-A")
        .arg("-t")
        .arg("-v")
        .arg("ON_ERROR_STOP=1")
        .arg("-w")
        .arg("-h")
        .arg("127.0.0.1")
        .arg("-p")
        .arg(port.to_string())
        .arg("-U")
        .arg(user)
        .arg("-d")
        .arg(database)
        .stdout(Stdio::piped())
        .stderr(Stdio::null());
    if let Some(bin) = binary.parent() {
        configure_pg_environment(&mut command, bin);
    }
    command.env("PGPASSWORD", password);
    if let Some(path) = file {
        command.arg("-f").arg(path).stdin(Stdio::null());
    } else {
        command.arg("-f").arg("-").stdin(Stdio::piped());
    }
    let mut child = command
        .spawn()
        .map_err(|_| "Commande PostgreSQL impossible")?;
    if let Some(sql) = sql {
        child
            .stdin
            .take()
            .ok_or("Entrée PostgreSQL inaccessible")?
            .write_all(sql.as_bytes())
            .map_err(|_| "Écriture SQL impossible")?;
    }
    let output = child
        .wait_with_output()
        .map_err(|_| "Commande PostgreSQL interrompue")?;
    if !output.status.success() {
        return Err("Commande SQL PostgreSQL échouée");
    }
    String::from_utf8(output.stdout).map_err(|_| "Réponse PostgreSQL invalide")
}

fn run_quiet(command: &mut Command, error_log: &Path) -> Result<(), &'static str> {
    let output = command
        .stdout(Stdio::null())
        .stderr(Stdio::piped())
        .output()
        .map_err(|_| "Commande PostgreSQL impossible")?;
    if !output.status.success() {
        if let Ok(mut log) = private_append(error_log) {
            let _ = log.write_all(&output.stderr);
            let _ = log.sync_all();
        }
        return Err("Commande PostgreSQL échouée");
    }
    Ok(())
}

fn write_config(paths: &PgPaths, port: u16) -> Result<(), &'static str> {
    let hba = paths.config.join("pg_hba.conf");
    let conf = paths.config.join("postgresql.conf");
    let hba_text = "local all all scram-sha-256\nhost all all 127.0.0.1/32 scram-sha-256\n";
    write_private_replace(&hba, hba_text)?;
    let hba_path = hba
        .to_str()
        .ok_or("Chemin PostgreSQL invalide")?
        .replace('\\', "/")
        .replace('\'', "''");
    let config = format!(
        "listen_addresses = '127.0.0.1'\nport = {port}\nunix_socket_directories = ''\nhba_file = '{hba_path}'\nssl = off\npassword_encryption = 'scram-sha-256'\nmax_connections = 30\nshared_buffers = 64MB\nwork_mem = 2MB\nmaintenance_work_mem = 32MB\neffective_cache_size = 512MB\nlog_statement = 'none'\nlog_min_error_statement = 'panic'\n"
    );
    write_private_replace(&conf, &config)
}

fn private_directory(path: &Path) -> Result<(), &'static str> {
    if path.exists() {
        return check_private_directory(path);
    }
    fs::create_dir(path).map_err(|_| "Répertoire PostgreSQL inaccessible")?;
    private_mode(path, 0o700)
}

fn check_private_directory(path: &Path) -> Result<(), &'static str> {
    let meta = fs::symlink_metadata(path).map_err(|_| "Répertoire PostgreSQL absent")?;
    if !meta.file_type().is_dir() {
        return Err("Répertoire PostgreSQL invalide");
    }
    #[cfg(unix)]
    if meta.permissions().mode() & 0o077 != 0 || meta.uid() != unsafe { libc::geteuid() } {
        return Err("Permissions PostgreSQL trop ouvertes");
    }
    Ok(())
}

fn check_private_file(path: &Path) -> Result<(), &'static str> {
    let meta = fs::symlink_metadata(path).map_err(|_| "Fichier PostgreSQL absent")?;
    if !meta.file_type().is_file() {
        return Err("Fichier PostgreSQL invalide");
    }
    #[cfg(unix)]
    if meta.permissions().mode() & 0o077 != 0 || meta.uid() != unsafe { libc::geteuid() } {
        return Err("Permissions PostgreSQL trop ouvertes");
    }
    Ok(())
}

fn private_mode(path: &Path, mode: u32) -> Result<(), &'static str> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(mode))
            .map_err(|_| "Permissions PostgreSQL invalides")?;
    }
    let _ = (path, mode);
    Ok(())
}

fn write_new_private(path: &Path, text: &str) -> Result<(), &'static str> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options
        .open(path)
        .map_err(|_| "Création d'un fichier PostgreSQL impossible")?;
    file.write_all(text.as_bytes())
        .map_err(|_| "Écriture PostgreSQL impossible")?;
    file.sync_all()
        .map_err(|_| "Synchronisation PostgreSQL impossible")
}

fn write_private_replace(path: &Path, text: &str) -> Result<(), &'static str> {
    if path.exists() {
        check_private_file(path)?;
    }
    let temp = path.with_extension("new");
    if temp.exists() {
        return Err("Configuration PostgreSQL provisoire existante");
    }
    write_new_private(&temp, text)?;
    fs::rename(&temp, path).map_err(|_| "Configuration PostgreSQL non enregistrée")
}

fn private_append(path: &Path) -> Result<File, &'static str> {
    if path.exists() {
        check_private_file(path)?;
    }
    let file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .map_err(|_| "Journal PostgreSQL inaccessible")?;
    private_mode(path, 0o600)?;
    Ok(file)
}

#[cfg(unix)]
use std::os::unix::fs::{MetadataExt, PermissionsExt};

#[cfg(test)]
mod tests {
    use super::pg_binary;
    use std::path::Path;

    #[test]
    fn bundled_postgres_binary_has_platform_extension() {
        let name = pg_binary(Path::new("postgresql/bin"), "pg_dump");
        #[cfg(windows)]
        assert_eq!(name, Path::new("postgresql/bin/pg_dump.exe"));
        #[cfg(not(windows))]
        assert_eq!(name, Path::new("postgresql/bin/pg_dump"));
    }
}
