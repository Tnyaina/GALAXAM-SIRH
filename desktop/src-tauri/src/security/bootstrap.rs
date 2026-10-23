use std::time::{SystemTime, UNIX_EPOCH};

const BOOTSTRAP_LIFETIME_SECS: u64 = 7 * 24 * 60 * 60;

pub fn ensure_bootstrap_capability(
    home: &std::path::Path,
    master_secret: &str,
) -> Result<String, &'static str> {
    use ring::digest;
    use std::fs::{self, OpenOptions};
    use std::io::Write;
    let config = home.join("config");
    if !config.exists() {
        std::fs::create_dir_all(&config)
            .map_err(|_| "Dossier de configuration impossible à créer")?;
    }
    let config_metadata =
        std::fs::symlink_metadata(&config).map_err(|_| "Dossier de configuration inaccessible")?;
    if !config_metadata.file_type().is_dir() {
        return Err("Dossier de configuration invalide");
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(&config, std::fs::Permissions::from_mode(0o700))
            .map_err(|_| "Permissions du dossier de configuration invalides")?;
    }
    let state_path = config.join("bootstrap.state");
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "Horloge système invalide")?
        .as_secs();
    let old = read_bootstrap_state(&state_path).ok();
    if let Some((version, expires, fingerprint)) = old.as_ref() {
        if *expires > now {
            let token = derive_bootstrap_token(master_secret, *version);
            let expected = hex(digest::digest(&digest::SHA256, token.as_bytes()).as_ref());
            if expected == *fingerprint {
                return Ok(token);
            }
            return Err("État d'amorçage incohérent ; aucune création autorisée");
        }
    } else if state_path.exists() {
        return Err("État d'amorçage invalide ; aucune création autorisée");
    }
    let version = old
        .map(|(version, _, _)| version.saturating_add(1))
        .unwrap_or(1);
    let expires = now.saturating_add(BOOTSTRAP_LIFETIME_SECS);
    let token = derive_bootstrap_token(master_secret, version);
    let fingerprint = hex(digest::digest(&digest::SHA256, token.as_bytes()).as_ref());
    let temporary = config.join(format!("bootstrap.state.{}.tmp", std::process::id()));
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options
        .open(&temporary)
        .map_err(|_| "Écriture de l'état d'amorçage impossible")?;
    writeln!(
        file,
        "version={version}\nexpires={expires}\nfingerprint={fingerprint}"
    )
    .map_err(|_| "Écriture de l'état d'amorçage impossible")?;
    file.sync_all()
        .map_err(|_| "Synchronisation de l'état d'amorçage impossible")?;
    fs::rename(&temporary, &state_path)
        .map_err(|_| "Validation de l'état d'amorçage impossible")?;
    Ok(token)
}

/// Reconstructs the one-time capability only from a state previously created
/// by the service. Unlike `ensure_bootstrap_capability`, this never creates or
/// renews bootstrap state and is suitable for the short-lived elevated helper.
#[cfg_attr(not(target_os = "windows"), allow(dead_code))]
pub(crate) fn existing_bootstrap_capability(
    home: &std::path::Path,
    master_secret: &str,
) -> Result<String, &'static str> {
    use ring::digest;
    let (version, expires, fingerprint) =
        read_bootstrap_state(&home.join("config/bootstrap.state"))?;
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|_| "Horloge système invalide")?
        .as_secs();
    if expires <= now {
        return Err("La période de configuration initiale est expirée");
    }
    let token = derive_bootstrap_token(master_secret, version);
    let actual = hex(digest::digest(&digest::SHA256, token.as_bytes()).as_ref());
    if actual != fingerprint {
        return Err("État d'amorçage incohérent ; aucune création autorisée");
    }
    Ok(token)
}

pub(crate) fn derive_bootstrap_token(master_secret: &str, version: u64) -> String {
    let key = ring::hmac::Key::new(ring::hmac::HMAC_SHA256, master_secret.as_bytes());
    let message = format!("GALAXAM-DESKTOP-INITIAL-ADMIN-v1:{version}");
    hex(ring::hmac::sign(&key, message.as_bytes()).as_ref())
}

fn read_bootstrap_state(path: &std::path::Path) -> Result<(u64, u64, String), &'static str> {
    use std::fs;
    let metadata = fs::symlink_metadata(path).map_err(|_| "État d'amorçage absent")?;
    if !metadata.file_type().is_file() {
        return Err("État d'amorçage invalide");
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::{MetadataExt, PermissionsExt};
        if metadata.permissions().mode() & 0o077 != 0
            || metadata.uid() != unsafe { libc::geteuid() }
        {
            return Err("Permissions de l'état d'amorçage invalides");
        }
    }
    let text = fs::read_to_string(path).map_err(|_| "État d'amorçage illisible")?;
    let mut version = None;
    let mut expires = None;
    let mut fingerprint = None;
    for line in text.lines() {
        let (key, value) = line.split_once('=').ok_or("État d'amorçage invalide")?;
        match key {
            "version" if version.is_none() => version = value.parse::<u64>().ok(),
            "expires" if expires.is_none() => expires = value.parse::<u64>().ok(),
            "fingerprint"
                if fingerprint.is_none()
                    && value.len() == 64
                    && value.bytes().all(|b| b.is_ascii_hexdigit()) =>
            {
                fingerprint = Some(value.to_owned())
            }
            _ => return Err("État d'amorçage invalide"),
        }
    }
    Ok((
        version.ok_or("Version d'amorçage absente")?,
        expires.ok_or("Expiration d'amorçage absente")?,
        fingerprint.ok_or("Empreinte d'amorçage absente")?,
    ))
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|value| format!("{value:02x}")).collect()
}
