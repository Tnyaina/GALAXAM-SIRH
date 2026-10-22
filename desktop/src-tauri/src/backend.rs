use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};

pub const DEFAULT_API_PORT: u16 = 18080;
const DEFAULT_EMBEDDED_API_PORT: u16 = 18081;

pub struct DesktopConfig {
    pub home: PathBuf,
    pub api_upstream: String,
    pub local_port: Option<u16>,
    pub embedded_postgres: bool,
    pub postgres_port: u16,
    pub auto_local: bool,
}
impl DesktopConfig {
    pub fn load() -> Result<Self, &'static str> {
        let home = match command_line_value("--galaxam-data-home=")
            .or_else(|| std::env::var_os("GALAXAM_DESKTOP_HOME"))
        {
            Some(value) => PathBuf::from(value),
            None if cfg!(target_os = "macos") => {
                PathBuf::from(std::env::var_os("HOME").ok_or("Répertoire utilisateur introuvable")?)
                    .join("Library/Application Support/GALAXAM SIRH")
            }
            None => PathBuf::from(
                std::env::var_os("APPDATA").ok_or("Répertoire utilisateur introuvable")?,
            )
            .join("GALAXAM SIRH"),
        };
        if !home.is_absolute() {
            return Err("Le répertoire GALAXAM_DESKTOP_HOME doit être absolu");
        }
        let config_path = home.join("config/desktop.conf");
        let auto_local = if config_path.exists() {
            false
        } else {
            // A completed embedded cluster can recover from an interruption
            // between PostgreSQL initialization and desktop.conf creation.
            if home
                .join("data/postgresql/config/installation.ready")
                .exists()
            {
                return Ok(Self::new_embedded(home));
            }
            // An unknown existing directory must never be silently converted.
            match fs::read_dir(&home) {
                Ok(_) => {}
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                    return Ok(Self::new_embedded(home));
                }
                Err(_) => return Err("Répertoire desktop inaccessible"),
            }
            let only_bootstrap_artifacts = bootstrap_artifacts_only(&home)?;
            if !only_bootstrap_artifacts {
                return Err(
                    "Installation desktop existante sans mode explicite ; intervention requise",
                );
            }
            true
        };
        if auto_local {
            return Ok(Self::new_embedded(home));
        }
        let mut mode = String::from("remote");
        let mut remote_api = String::from("http://127.0.0.1:18080");
        let mut local_port = DEFAULT_API_PORT;
        let mut postgres = String::from("external");
        let mut postgres_port: u16 = 15432;
        if config_path.exists() {
            let config =
                fs::read_to_string(&config_path).map_err(|_| "Configuration desktop illisible")?;
            for line in config.lines().map(str::trim) {
                if line.is_empty() || line.starts_with('#') {
                    continue;
                }
                let (key, value) = line
                    .split_once('=')
                    .ok_or("Configuration desktop invalide")?;
                match key.trim() {
                    "mode" => mode = value.trim().to_owned(),
                    "remote-api" => remote_api = value.trim().to_owned(),
                    "local-port" => {
                        local_port = value
                            .trim()
                            .parse()
                            .map_err(|_| "Port backend local invalide")?;
                        if local_port == 0 {
                            return Err("Port backend local invalide");
                        }
                    }
                    "postgres" => postgres = value.trim().to_owned(),
                    "postgres-port" => {
                        postgres_port = value
                            .trim()
                            .parse()
                            .map_err(|_| "Port PostgreSQL invalide")?;
                        if postgres_port == 0 {
                            return Err("Port PostgreSQL invalide");
                        }
                    }
                    _ => return Err("Option desktop inconnue"),
                }
            }
        }
        match mode.as_str() {
            "local" => {
                if !matches!(postgres.as_str(), "external" | "embedded")
                    || (postgres == "embedded" && postgres_port == local_port)
                {
                    return Err("Configuration PostgreSQL locale invalide");
                }
                Ok(Self {
                    home,
                    api_upstream: format!("http://127.0.0.1:{local_port}"),
                    local_port: Some(local_port),
                    embedded_postgres: postgres == "embedded",
                    postgres_port,
                    auto_local: false,
                })
            }
            "remote" => {
                if postgres != "external" {
                    return Err("PostgreSQL embarqué interdit en mode distant");
                }
                let url = reqwest::Url::parse(&remote_api)
                    .map_err(|_| "Adresse API distante invalide")?;
                if !matches!(url.scheme(), "http" | "https")
                    || !url.username().is_empty()
                    || url.password().is_some()
                    || url.path() != "/"
                    || url.query().is_some()
                    || url.fragment().is_some()
                    || (url.scheme() == "http"
                        && !matches!(url.host_str(), Some("127.0.0.1" | "localhost")))
                {
                    return Err("Adresse API distante non autorisée");
                }
                Ok(Self {
                    home,
                    api_upstream: url.as_str().trim_end_matches('/').to_owned(),
                    local_port: None,
                    embedded_postgres: false,
                    postgres_port,
                    auto_local: false,
                })
            }
            _ => Err("Mode desktop inconnu"),
        }
    }

    pub fn new_for_setup() -> Self {
        let home = command_line_value("--galaxam-data-home=")
            .or_else(|| std::env::var_os("GALAXAM_DESKTOP_HOME"))
            .map(PathBuf::from)
            .unwrap_or_else(|| {
                if cfg!(target_os = "macos") {
                    PathBuf::from(std::env::var_os("HOME").unwrap_or_default())
                        .join("Library/Application Support/GALAXAM SIRH")
                } else {
                    PathBuf::from(std::env::var_os("APPDATA").unwrap_or_default())
                        .join("GALAXAM SIRH")
                }
            });
        Self::new_embedded(home)
    }

    pub fn restart_arguments(&self) -> Vec<std::ffi::OsString> {
        let mut args = vec![std::ffi::OsString::from(format!(
            "--galaxam-data-home={}",
            self.home.display()
        ))];
        if let Some(port) = command_line_value("--galaxam-web-port=")
            .or_else(|| std::env::var_os("GALAXAM_DESKTOP_WEB_PORT"))
        {
            args.push(format!("--galaxam-web-port={}", port.to_string_lossy()).into());
        }
        if let Some(dir) = command_line_value("--galaxam-test-launch-agent-dir=")
            .or_else(|| std::env::var_os("GALAXAM_TEST_LAUNCH_AGENT_DIR"))
        {
            args.push(format!("--galaxam-test-launch-agent-dir={}", dir.to_string_lossy()).into());
        }
        args
    }

    fn new_embedded(home: PathBuf) -> Self {
        Self {
            home,
            api_upstream: format!("http://127.0.0.1:{DEFAULT_EMBEDDED_API_PORT}"),
            local_port: Some(DEFAULT_EMBEDDED_API_PORT),
            embedded_postgres: true,
            postgres_port: 15432,
            auto_local: true,
        }
    }

    pub fn save_local_mode(&self) -> Result<(), &'static str> {
        create_private_dirs(&self.home)?;
        let path = self.home.join("config/desktop.conf");
        let temp = self.home.join("config/desktop.conf.pending");
        let mut file =
            private_new_file(&temp).map_err(|_| "Enregistrement du mode local impossible")?;
        file.write_all(
            format!(
                "mode=local\npostgres=embedded\nlocal-port={}\npostgres-port={}\n",
                self.local_port.unwrap_or(DEFAULT_EMBEDDED_API_PORT),
                self.postgres_port
            )
            .as_bytes(),
        )
        .map_err(|_| "Enregistrement du mode local impossible")?;
        file.sync_all()
            .map_err(|_| "Enregistrement du mode local impossible")?;
        fs::rename(temp, path).map_err(|_| "Enregistrement du mode local impossible")
    }

    pub fn save_remote_mode(&self, remote_api: &str) -> Result<(), &'static str> {
        let url = reqwest::Url::parse(remote_api).map_err(|_| "Adresse API distante invalide")?;
        if url.scheme() != "https"
            && !(url.scheme() == "http"
                && matches!(url.host_str(), Some("127.0.0.1" | "localhost")))
        {
            return Err("Une adresse HTTPS est requise pour un serveur distant");
        }
        if !url.username().is_empty()
            || url.password().is_some()
            || url.path() != "/"
            || url.query().is_some()
            || url.fragment().is_some()
        {
            return Err("Saisissez uniquement l'adresse de base de l'API");
        }
        create_private_dirs(&self.home)?;
        let path = self.home.join("config/desktop.conf");
        let temp = self.home.join("config/desktop.conf.pending");
        let mut file =
            private_new_file(&temp).map_err(|_| "Enregistrement du mode distant impossible")?;
        file.write_all(
            format!(
                "mode=remote\npostgres=external\nremote-api={}\n",
                url.as_str().trim_end_matches('/')
            )
            .as_bytes(),
        )
        .map_err(|_| "Enregistrement du mode distant impossible")?;
        file.sync_all()
            .map_err(|_| "Enregistrement du mode distant impossible")?;
        fs::rename(temp, path).map_err(|_| "Enregistrement du mode distant impossible")
    }
}

fn bootstrap_artifacts_only(home: &Path) -> Result<bool, &'static str> {
    for entry in fs::read_dir(home).map_err(|_| "Répertoire desktop illisible")? {
        let entry = entry.map_err(|_| "Répertoire desktop illisible")?;
        match entry.file_name().to_str() {
            Some("config") => {
                let metadata = fs::symlink_metadata(entry.path())
                    .map_err(|_| "Configuration desktop ambiguë")?;
                if !metadata.file_type().is_dir() {
                    return Ok(false);
                }
                let mut contents =
                    fs::read_dir(entry.path()).map_err(|_| "Configuration desktop ambiguë")?;
                if contents.next().is_some() {
                    return Ok(false);
                }
            }
            // Non-sensitive launch diagnostics may be written before
            // the wizard has committed an installation mode.
            Some("logs") => {
                let metadata =
                    fs::symlink_metadata(entry.path()).map_err(|_| "Journaux desktop ambigus")?;
                if !metadata.file_type().is_dir() {
                    return Ok(false);
                }
                let mut contents =
                    fs::read_dir(entry.path()).map_err(|_| "Journaux desktop ambigus")?;
                let first = contents.next();
                let second = contents.next();
                match (first, second) {
                    (None, _) => {}
                    (Some(Ok(file)), None) if file.file_name() == "desktop-diagnostics.log" => {
                        let meta = fs::symlink_metadata(file.path())
                            .map_err(|_| "Journaux desktop ambigus")?;
                        if !meta.file_type().is_file() {
                            return Ok(false);
                        }
                    }
                    _ => return Ok(false),
                }
            }
            _ => return Ok(false),
        }
    }
    Ok(true)
}

fn command_line_value(prefix: &str) -> Option<std::ffi::OsString> {
    std::env::args_os().find_map(|arg| {
        let text = arg.to_string_lossy();
        text.strip_prefix(prefix)
            .filter(|value| !value.is_empty())
            .map(std::ffi::OsString::from)
    })
}

fn private_new_file(path: &Path) -> std::io::Result<File> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options.open(path)
}

fn create_private_dirs(home: &Path) -> Result<(), &'static str> {
    for relative in [
        "config",
        "data",
        "logs",
        "documents/photo",
        "documents/documents",
        "documents/prestataires",
    ] {
        let directory = home.join(relative);
        fs::create_dir_all(&directory).map_err(|_| "Répertoire de données inaccessible")?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&directory, fs::Permissions::from_mode(0o700))
                .map_err(|_| "Permissions du répertoire invalides")?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bootstrap_diagnostics_do_not_make_a_new_installation_ambiguous() {
        let root = std::env::temp_dir().join(format!(
            "galaxam-bootstrap-artifacts-{}",
            std::process::id()
        ));
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(root.join("logs")).unwrap();
        fs::write(
            root.join("logs/desktop-diagnostics.log"),
            "event=startup-loaded mode=setup\n",
        )
        .unwrap();
        assert!(bootstrap_artifacts_only(&root).unwrap());
        fs::write(root.join("logs/other.log"), "unexpected\n").unwrap();
        assert!(!bootstrap_artifacts_only(&root).unwrap());
        let _ = fs::remove_dir_all(root);
    }
}
