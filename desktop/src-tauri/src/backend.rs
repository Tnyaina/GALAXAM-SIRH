use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::net::{SocketAddr, TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, Instant};
use tauri::{path::BaseDirectory, Manager};

pub const DEFAULT_API_PORT: u16 = 18080;

pub struct DesktopConfig {
    pub home: PathBuf,
    pub api_upstream: String,
    pub local_port: Option<u16>,
}

impl DesktopConfig {
    pub fn load() -> Result<Self, &'static str> {
        let home = match std::env::var_os("GALAXAM_DESKTOP_HOME") {
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
        let mut mode = String::from("remote");
        let mut remote_api = String::from("http://127.0.0.1:18080");
        let mut local_port = DEFAULT_API_PORT;
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
                    _ => return Err("Option desktop inconnue"),
                }
            }
        }
        match mode.as_str() {
            "local" => Ok(Self {
                home,
                api_upstream: format!("http://127.0.0.1:{local_port}"),
                local_port: Some(local_port),
            }),
            "remote" => {
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
                })
            }
            _ => Err("Mode desktop inconnu"),
        }
    }
}

pub struct BackendProcess(pub Mutex<Option<Child>>);

impl BackendProcess {
    pub fn new() -> Self {
        Self(Mutex::new(None))
    }

    pub fn start(
        &self,
        app: &tauri::App,
        config: &DesktopConfig,
        web_origin: &str,
    ) -> Result<(), &'static str> {
        let port = config.local_port.ok_or("Mode local non sélectionné")?;
        let address = SocketAddr::from(([127, 0, 0, 1], port));
        let reservation = TcpListener::bind(address)
            .map_err(|_| "Port du backend occupé ; aucun autre processus ne sera démarré")?;
        let java_name = if cfg!(windows) {
            "backend/runtime/bin/java.exe"
        } else {
            "backend/runtime/bin/java"
        };
        let java = app
            .path()
            .resolve(java_name, BaseDirectory::Resource)
            .map_err(|_| "Runtime Java embarqué introuvable")?;
        let jar = app
            .path()
            .resolve("backend/backend.jar", BaseDirectory::Resource)
            .map_err(|_| "JAR backend embarqué introuvable")?;
        if !java.is_file() || !jar.is_file() {
            return Err("Runtime Java ou JAR backend absent du bundle");
        }
        let secrets = read_backend_env(&config.home.join("config/backend.env"))?;
        create_private_dirs(&config.home)?;
        let console = private_log(&config.home.join("logs/backend-console.log"))?;
        let error = console
            .try_clone()
            .map_err(|_| "Journal backend inaccessible")?;

        let mut command = Command::new(java);
        command
            .arg("-Dspring.profiles.active=desktop")
            .arg("-jar")
            .arg(jar)
            .current_dir(&config.home)
            .env_clear()
            .env("GALAXAM_DESKTOP_HOME", &config.home)
            .env("GALAXAM_BACKEND_PORT", port.to_string())
            .env("GALAXAM_DESKTOP_CORS_ORIGIN", web_origin)
            .envs(secrets)
            .stdin(Stdio::null())
            .stdout(Stdio::from(console))
            .stderr(Stdio::from(error));

        // Release the reservation only when the child is ready to bind.
        drop(reservation);
        let child = command
            .spawn()
            .map_err(|_| "Démarrage du backend impossible")?;
        *self
            .0
            .lock()
            .map_err(|_| "Gestionnaire backend indisponible")? = Some(child);
        let deadline = Instant::now() + Duration::from_secs(25);
        while Instant::now() < deadline {
            if health_ok(address) {
                return Ok(());
            }
            if self
                .0
                .lock()
                .map_err(|_| "Gestionnaire backend indisponible")?
                .as_mut()
                .and_then(|child| child.try_wait().ok())
                .flatten()
                .is_some()
            {
                self.stop();
                return Err(
                    "Le backend s'est arrêté pendant son démarrage ; consulter les journaux",
                );
            }
            thread::sleep(Duration::from_millis(250));
        }
        self.stop();
        Err("Le backend ne répond pas ; consulter les journaux")
    }

    pub fn stop(&self) {
        let Some(mut child) = self.0.lock().ok().and_then(|mut state| state.take()) else {
            return;
        };
        #[cfg(unix)]
        unsafe {
            libc::kill(child.id() as i32, libc::SIGTERM);
        }
        #[cfg(not(unix))]
        let _ = child.kill();
        let deadline = Instant::now() + Duration::from_secs(6);
        while Instant::now() < deadline {
            if child.try_wait().ok().flatten().is_some() {
                return;
            }
            thread::sleep(Duration::from_millis(100));
        }
        let _ = child.kill();
        let _ = child.wait();
    }
}

impl Drop for BackendProcess {
    fn drop(&mut self) {
        self.stop();
    }
}

fn read_backend_env(path: &Path) -> Result<Vec<(String, String)>, &'static str> {
    let metadata = fs::symlink_metadata(path)
        .map_err(|_| "Configuration backend absente : config/backend.env")?;
    if !metadata.file_type().is_file() {
        return Err("Configuration backend invalide");
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        if metadata.permissions().mode() & 0o077 != 0 {
            return Err("Configuration backend trop accessible ; appliquer chmod 600");
        }
    }
    let content = fs::read_to_string(path).map_err(|_| "Configuration backend illisible")?;
    let mut values = Vec::new();
    for line in content.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let (key, value) = line
            .split_once('=')
            .ok_or("Configuration backend invalide")?;
        if !matches!(
            key,
            "GALAXAM_DESKTOP_DB_URL"
                | "GALAXAM_DESKTOP_DB_USER"
                | "GALAXAM_DESKTOP_DB_PASSWORD"
                | "GALAXAM_DESKTOP_JWT_SECRET"
        ) || value.is_empty()
        {
            return Err("Configuration backend invalide");
        }
        values.push((key.to_owned(), value.to_owned()));
    }
    for required in [
        "GALAXAM_DESKTOP_DB_URL",
        "GALAXAM_DESKTOP_DB_USER",
        "GALAXAM_DESKTOP_DB_PASSWORD",
        "GALAXAM_DESKTOP_JWT_SECRET",
    ] {
        if !values.iter().any(|(key, _)| key == required) {
            return Err("Configuration backend incomplète");
        }
    }
    Ok(values)
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

fn private_log(path: &Path) -> Result<File, &'static str> {
    let file = OpenOptions::new()
        .create(true)
        .append(true)
        .open(path)
        .map_err(|_| "Journal backend inaccessible")?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(path, fs::Permissions::from_mode(0o600))
            .map_err(|_| "Permissions du journal invalides")?;
    }
    Ok(file)
}

fn health_ok(address: SocketAddr) -> bool {
    let Ok(mut stream) = TcpStream::connect_timeout(&address, Duration::from_millis(350)) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(350)));
    let _ = stream.set_write_timeout(Some(Duration::from_millis(350)));
    if stream
        .write_all(b"GET /api/auth/ping HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n")
        .is_err()
    {
        return false;
    }
    let mut response = [0_u8; 64];
    matches!(stream.read(&mut response), Ok(n) if response[..n].starts_with(b"HTTP/1.1 200"))
}
