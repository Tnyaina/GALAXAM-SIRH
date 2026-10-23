use crate::connection::config::DesktopConfig;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use tauri::{AppHandle, Manager};

const BOOTSTRAP_HELPER_ARG: &str = "--galaxam-bootstrap-helper";
const BOOTSTRAP_OUTPUT_ARG: &str = "--galaxam-token-output=";
static SERVICE_CHILD_JOB: std::sync::atomic::AtomicIsize = std::sync::atomic::AtomicIsize::new(0);

pub struct ServiceChildJob(windows_sys::Win32::Foundation::HANDLE);

impl Drop for ServiceChildJob {
    fn drop(&mut self) {
        let raw = self.0 as isize;
        let _ = SERVICE_CHILD_JOB.compare_exchange(
            raw,
            0,
            std::sync::atomic::Ordering::AcqRel,
            std::sync::atomic::Ordering::Acquire,
        );
        unsafe {
            windows_sys::Win32::Foundation::CloseHandle(self.0);
        }
    }
}

pub fn create_service_child_job() -> Result<ServiceChildJob, String> {
    use windows_sys::Win32::System::JobObjects::{
        CreateJobObjectW, JobObjectExtendedLimitInformation, SetInformationJobObject,
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION, JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE,
    };
    let handle = unsafe { CreateJobObjectW(std::ptr::null(), std::ptr::null()) };
    if handle.is_null() {
        return Err("Création du groupe de processus Windows impossible".into());
    }
    let mut limits = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
    limits.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    let configured = unsafe {
        SetInformationJobObject(
            handle,
            JobObjectExtendedLimitInformation,
            &limits as *const _ as *const std::ffi::c_void,
            std::mem::size_of_val(&limits) as u32,
        )
    };
    if configured == 0 {
        unsafe { windows_sys::Win32::Foundation::CloseHandle(handle) };
        return Err("Configuration du groupe de processus Windows impossible".into());
    }
    if SERVICE_CHILD_JOB
        .compare_exchange(
            0,
            handle as isize,
            std::sync::atomic::Ordering::AcqRel,
            std::sync::atomic::Ordering::Acquire,
        )
        .is_err()
    {
        unsafe { windows_sys::Win32::Foundation::CloseHandle(handle) };
        return Err("Groupe de processus GALAXAM déjà actif".into());
    }
    Ok(ServiceChildJob(handle))
}

pub fn assign_service_child(child: &std::process::Child) -> Result<(), String> {
    use std::os::windows::io::AsRawHandle;
    let job = SERVICE_CHILD_JOB.load(std::sync::atomic::Ordering::Acquire);
    if job == 0 {
        return Err("Groupe de processus du service absent".into());
    }
    let assigned = unsafe {
        windows_sys::Win32::System::JobObjects::AssignProcessToJobObject(
            job as windows_sys::Win32::Foundation::HANDLE,
            child.as_raw_handle() as windows_sys::Win32::Foundation::HANDLE,
        )
    };
    if assigned == 0 {
        return Err("Ajout du processus au groupe de supervision impossible".into());
    }
    Ok(())
}

pub fn is_bootstrap_helper() -> bool {
    std::env::args_os().any(|argument| argument == BOOTSTRAP_HELPER_ARG)
}

pub fn run_bootstrap_helper() -> Result<(), String> {
    if let Some(control) = std::env::args_os().find_map(|argument| {
        argument
            .to_string_lossy()
            .strip_prefix("--galaxam-service-control=")
            .map(str::to_owned)
    }) {
        let result = run_service_control(&control);
        let result_path = std::env::args_os()
            .find_map(|argument| {
                argument
                    .to_string_lossy()
                    .strip_prefix("--galaxam-control-result=")
                    .map(PathBuf::from)
            })
            .ok_or_else(|| "Fichier résultat du service absent".to_string())?;
        validate_user_result_path(&result_path, "service-control.result")?;
        let payload = result
            .as_ref()
            .map(|_| "OK")
            .unwrap_or_else(|error| error.as_str());
        write_elevated_result(&result_path, payload)?;
        return result;
    }
    let home = default_data_home().map_err(str::to_string)?;
    let output = std::env::args_os()
        .find_map(|argument| {
            argument
                .to_string_lossy()
                .strip_prefix(BOOTSTRAP_OUTPUT_ARG)
                .map(PathBuf::from)
        })
        .ok_or_else(|| "Fichier temporaire du jeton absent".to_string())?;
    validate_user_result_path(&output, "bootstrap.token")?;
    let secrets = std::fs::read_to_string(home.join("data/postgresql/config/installation.secrets"))
        .map_err(|_| "Secrets de la base locale illisibles avec les droits élevés".to_string())?;
    let app_secret = secrets
        .lines()
        .find_map(|line| line.strip_prefix("app="))
        .ok_or_else(|| "Secret applicatif absent".to_string())?;
    let token = crate::security::bootstrap::existing_bootstrap_capability(&home, app_secret)
        .map_err(str::to_string)?;
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&output)
        .map_err(|_| "Création du jeton utilisateur impossible".to_string())?;
    file.write_all(token.as_bytes())
        .map_err(|_| "Écriture du jeton utilisateur impossible".to_string())?;
    file.sync_all()
        .map_err(|_| "Synchronisation du jeton utilisateur impossible".to_string())?;
    Ok(())
}

pub fn elevated_bootstrap_token(home: &Path) -> Result<String, String> {
    use std::os::windows::ffi::OsStrExt;
    if home != default_data_home().map_err(str::to_string)? {
        return Err(
            "Le dossier de données Windows n'est pas le dossier ProgramData autorisé".into(),
        );
    }
    let local_app_data = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .ok_or_else(|| "Profil utilisateur Windows indisponible".to_string())?;
    let token_path = local_app_data.join("GALAXAM SIRH/bootstrap.token");
    if token_path.exists() {
        std::fs::remove_file(&token_path)
            .map_err(|_| "Ancien jeton utilisateur impossible à supprimer".to_string())?;
    }
    let executable =
        std::env::current_exe().map_err(|_| "Exécutable GALAXAM introuvable".to_string())?;
    let arguments = vec![
        BOOTSTRAP_HELPER_ARG.to_string(),
        format!("--galaxam-data-home={}", home.display()),
        format!("{BOOTSTRAP_OUTPUT_ARG}{}", token_path.display()),
    ];
    let parameters = arguments
        .into_iter()
        .map(|argument| quote_windows_argument(&argument))
        .collect::<Vec<_>>()
        .join(" ");
    let verb: Vec<u16> = "runas\0".encode_utf16().collect();
    let file: Vec<u16> = executable
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    let parameters: Vec<u16> = parameters.encode_utf16().chain(Some(0)).collect();
    let operation = unsafe {
        ShellExecuteW(
            std::ptr::null_mut(),
            verb.as_ptr(),
            file.as_ptr(),
            parameters.as_ptr(),
            std::ptr::null(),
            0,
        )
    };
    if operation as isize <= 32 {
        return Err("Autorisation administrateur refusée ou impossible".into());
    }
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(120);
    while std::time::Instant::now() < deadline {
        if token_path.is_file() {
            let metadata = std::fs::symlink_metadata(&token_path)
                .map_err(|_| "Jeton utilisateur inaccessible".to_string())?;
            if !metadata.file_type().is_file() {
                return Err("Fichier de jeton invalide".into());
            }
            let token = std::fs::read_to_string(&token_path)
                .map_err(|_| "Lecture du jeton utilisateur impossible".to_string())?;
            std::fs::remove_file(&token_path)
                .map_err(|_| "Suppression du jeton utilisateur impossible".to_string())?;
            return Ok(token);
        }
        std::thread::sleep(std::time::Duration::from_millis(200));
    }
    Err("L'autorisation administrateur n'a pas abouti dans le délai imparti".into())
}

pub fn ensure_local_api_ready(port: u16) -> Result<(), String> {
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(180);
    while std::time::Instant::now() < deadline {
        if windows_api_ping(port) {
            return Ok(());
        }
        std::thread::sleep(std::time::Duration::from_millis(500));
    }
    Err("Le service GALAXAM est arrêté ou l’API locale ne répond pas".into())
}

pub fn ensure_windows_service_ready(home: &Path, port: u16) -> Result<(), String> {
    if windows_api_ping(port) {
        return Ok(());
    }
    run_elevated_service_control(home, "start")?;
    ensure_local_api_ready(port)
}

pub fn stop_windows_service(home: &Path) -> Result<(), String> {
    run_elevated_service_control(home, "stop")
}

pub fn disable_windows_service(home: &Path) -> Result<(), String> {
    run_elevated_service_control(home, "disable")
}

pub fn install_service() -> Result<(), String> {
    use std::ffi::OsString;
    use windows_service::service::{
        ServiceAccess, ServiceErrorControl, ServiceInfo, ServiceStartType, ServiceType,
    };
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    let mut config = DesktopConfig::new_for_setup();
    config.home = default_data_home().map_err(str::to_string)?;
    prepare_service_data_home(&config.home)?;
    config
        .save_local_mode()
        .map_err(|problem| problem.to_string())?;
    remove_service_if_present()?;
    let executable =
        std::env::current_exe().map_err(|_| "Exécutable GALAXAM introuvable".to_string())?;
    let info = ServiceInfo {
        name: OsString::from(crate::platform::windows_service_runtime::SERVICE_NAME),
        display_name: OsString::from("GALAXAM SIRH Local"),
        service_type: ServiceType::OWN_PROCESS,
        start_type: ServiceStartType::AutoStart,
        error_control: ServiceErrorControl::Normal,
        executable_path: executable,
        launch_arguments: vec![
            OsString::from("--galaxam-service"),
            OsString::from(format!("--galaxam-data-home={}", config.home.display())),
        ],
        dependencies: vec![],
        account_name: Some(OsString::from("NT AUTHORITY\\LocalService")),
        account_password: None,
    };
    let manager = ServiceManager::local_computer(
        None::<&str>,
        ServiceManagerAccess::CONNECT | ServiceManagerAccess::CREATE_SERVICE,
    )
    .map_err(|problem| format!("Gestionnaire des services inaccessible : {problem}"))?;
    let service = manager
        .create_service(
            &info,
            ServiceAccess::QUERY_STATUS
                | ServiceAccess::QUERY_CONFIG
                | ServiceAccess::CHANGE_CONFIG
                | ServiceAccess::START
                | ServiceAccess::STOP
                | ServiceAccess::DELETE,
        )
        .map_err(|problem| format!("Création du service GALAXAM impossible : {problem}"))?;
    service
        .set_description("Base PostgreSQL et API locale GALAXAM SIRH")
        .map_err(|problem| format!("Description du service GALAXAM impossible : {problem}"))?;
    service
        .update_failure_actions(windows_service::service::ServiceFailureActions {
            reset_period: windows_service::service::ServiceFailureResetPeriod::After(
                std::time::Duration::from_secs(86_400),
            ),
            reboot_msg: None,
            command: None,
            actions: Some(vec![
                windows_service::service::ServiceAction {
                    action_type: windows_service::service::ServiceActionType::Restart,
                    delay: std::time::Duration::from_secs(5),
                },
                windows_service::service::ServiceAction {
                    action_type: windows_service::service::ServiceActionType::Restart,
                    delay: std::time::Duration::from_secs(30),
                },
                windows_service::service::ServiceAction {
                    action_type: windows_service::service::ServiceActionType::None,
                    delay: std::time::Duration::ZERO,
                },
            ]),
        })
        .map_err(|problem| format!("Configuration de reprise du service impossible : {problem}"))?;
    service
        .set_failure_actions_on_non_crash_failures(true)
        .map_err(|problem| format!("Activation de la reprise du service impossible : {problem}"))?;
    service
        .start::<OsString>(&[])
        .map_err(|problem| format!("Démarrage du service GALAXAM impossible : {problem}"))?;
    Ok(())
}

pub fn remove_service() -> Result<(), String> {
    remove_service_if_present()
}

fn remove_service_if_present() -> Result<(), String> {
    use windows_service::service::{ServiceAccess, ServiceState};
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};
    let manager = ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)
        .map_err(|problem| format!("Gestionnaire des services inaccessible : {problem}"))?;
    let service = match manager.open_service(
        crate::platform::windows_service_runtime::SERVICE_NAME,
        ServiceAccess::QUERY_STATUS | ServiceAccess::STOP | ServiceAccess::DELETE,
    ) {
        Ok(service) => service,
        Err(_) => return Ok(()),
    };
    if service
        .query_status()
        .map_err(|problem| problem.to_string())?
        .current_state
        != ServiceState::Stopped
    {
        service
            .stop()
            .map_err(|problem| format!("Arrêt du service GALAXAM impossible : {problem}"))?;
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
        while std::time::Instant::now() < deadline {
            if service
                .query_status()
                .map_err(|problem| problem.to_string())?
                .current_state
                == ServiceState::Stopped
            {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(250));
        }
        if service
            .query_status()
            .map_err(|problem| problem.to_string())?
            .current_state
            != ServiceState::Stopped
        {
            return Err("Le service GALAXAM ne s'est pas arrêté".into());
        }
    }
    service
        .delete()
        .map_err(|problem| format!("Suppression du service GALAXAM impossible : {problem}"))
}

fn prepare_service_data_home(home: &Path) -> Result<(), String> {
    std::fs::create_dir_all(home).map_err(|_| "Dossier ProgramData inaccessible".to_string())?;
    let config = home.join("config");
    let data = home.join("data");
    let logs = home.join("logs");
    for path in [&config, &data, &logs] {
        std::fs::create_dir_all(path)
            .map_err(|_| "Dossier de données GALAXAM inaccessible".to_string())?;
    }
    apply_acl(
        home,
        &[
            "/inheritance:r",
            "/grant:r",
            "*S-1-5-19:(OI)(CI)F",
            "*S-1-5-32-544:(OI)(CI)F",
            "*S-1-5-18:(OI)(CI)F",
            "*S-1-5-32-545:(RX)",
        ],
    )?;
    apply_acl(
        &data,
        &[
            "/inheritance:r",
            "/grant:r",
            "*S-1-5-19:(OI)(CI)F",
            "*S-1-5-32-544:(OI)(CI)F",
            "*S-1-5-18:(OI)(CI)F",
        ],
    )?;
    apply_acl(
        &config,
        &[
            "/inheritance:r",
            "/grant:r",
            "*S-1-5-19:(OI)(CI)F",
            "*S-1-5-32-544:(OI)(CI)F",
            "*S-1-5-18:(OI)(CI)F",
            "*S-1-5-32-545:(OI)(CI)RX",
        ],
    )?;
    apply_acl(
        &logs,
        &[
            "/inheritance:r",
            "/grant:r",
            "*S-1-5-19:(OI)(CI)F",
            "*S-1-5-32-544:(OI)(CI)F",
            "*S-1-5-18:(OI)(CI)F",
        ],
    )?;
    Ok(())
}

fn apply_acl(path: &Path, rules: &[&str]) -> Result<(), String> {
    let icacls = std::env::var_os("SystemRoot")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
        .join("System32/icacls.exe");
    let result = Command::new(icacls)
        .arg(path)
        .args(rules)
        .output()
        .map_err(|_| "Configuration des permissions NTFS impossible".to_string())?;
    if !result.status.success() {
        return Err(format!(
            "Configuration des permissions NTFS impossible : {}",
            String::from_utf8_lossy(&result.stderr).trim()
        ));
    }
    Ok(())
}

fn windows_api_ping(port: u16) -> bool {
    use std::io::{Read, Write};
    use std::net::{TcpStream, ToSocketAddrs};
    let address = ("127.0.0.1", port)
        .to_socket_addrs()
        .ok()
        .and_then(|mut addresses| addresses.next());
    let Some(address) = address else { return false };
    let Ok(mut stream) = TcpStream::connect_timeout(&address, std::time::Duration::from_secs(1))
    else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(std::time::Duration::from_secs(1)));
    let _ = stream.set_write_timeout(Some(std::time::Duration::from_secs(1)));
    if stream
        .write_all(format!("GET /api/auth/ping HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n").as_bytes())
        .is_err()
    {
        return false;
    }
    let mut response = String::new();
    stream.read_to_string(&mut response).is_ok() && response.starts_with("HTTP/1.1 200")
}

fn run_elevated_service_control(home: &Path, action: &str) -> Result<(), String> {
    use std::os::windows::ffi::OsStrExt;
    if home != default_data_home().map_err(str::to_string)?
        || !matches!(action, "start" | "stop" | "disable")
    {
        return Err("Action de service Windows non autorisée".into());
    }
    let local_app_data = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .ok_or_else(|| "Profil utilisateur Windows indisponible".to_string())?;
    let result_path = local_app_data.join("GALAXAM SIRH/service-control.result");
    validate_user_result_path(&result_path, "service-control.result")?;
    if result_path.exists() {
        std::fs::remove_file(&result_path)
            .map_err(|_| "Ancien résultat de contrôle impossible à supprimer".to_string())?;
    }
    let executable =
        std::env::current_exe().map_err(|_| "Exécutable GALAXAM introuvable".to_string())?;
    let parameters = [
        BOOTSTRAP_HELPER_ARG.to_string(),
        format!("--galaxam-service-control={action}"),
        format!("--galaxam-control-result={}", result_path.display()),
    ]
    .iter()
    .map(|argument| quote_windows_argument(argument))
    .collect::<Vec<_>>()
    .join(" ");
    let verb: Vec<u16> = "runas\0".encode_utf16().collect();
    let file: Vec<u16> = executable
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    let parameters: Vec<u16> = parameters.encode_utf16().chain(Some(0)).collect();
    let operation = unsafe {
        ShellExecuteW(
            std::ptr::null_mut(),
            verb.as_ptr(),
            file.as_ptr(),
            parameters.as_ptr(),
            std::ptr::null(),
            0,
        )
    };
    if operation as isize <= 32 {
        return Err("Autorisation administrateur refusée ou impossible".into());
    }
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(120);
    while std::time::Instant::now() < deadline {
        if result_path.is_file() {
            let result = std::fs::read_to_string(&result_path)
                .map_err(|_| "Résultat du contrôle de service illisible".to_string())?;
            let _ = std::fs::remove_file(&result_path);
            if result == "OK" {
                return Ok(());
            }
            return Err(result);
        }
        std::thread::sleep(std::time::Duration::from_millis(200));
    }
    Err("L'autorisation administrateur n'a pas abouti dans le délai imparti".into())
}

fn run_service_control(action: &str) -> Result<(), String> {
    use windows_service::service::{ServiceAccess, ServiceState};
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};
    let manager = ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)
        .map_err(|problem| format!("Gestionnaire des services inaccessible : {problem}"))?;
    let service = manager
        .open_service(
            crate::platform::windows_service_runtime::SERVICE_NAME,
            ServiceAccess::QUERY_STATUS | ServiceAccess::START | ServiceAccess::STOP,
        )
        .map_err(|problem| format!("Service GALAXAM introuvable : {problem}"))?;
    let state = service
        .query_status()
        .map_err(|problem| problem.to_string())?
        .current_state;
    match action {
        "start" => {
            set_service_start_type("auto")?;
            if state != ServiceState::Running && state != ServiceState::StartPending {
                service
                    .start::<std::ffi::OsString>(&[])
                    .map_err(|problem| {
                        format!("Démarrage du service GALAXAM impossible : {problem}")
                    })?;
            }
            Ok(())
        }
        "stop" | "disable" => {
            if state != ServiceState::Stopped && state != ServiceState::StopPending {
                service.stop().map_err(|problem| {
                    format!("Arrêt du service GALAXAM impossible : {problem}")
                })?;
            }
            let deadline = std::time::Instant::now() + std::time::Duration::from_secs(30);
            while std::time::Instant::now() < deadline {
                if service
                    .query_status()
                    .map_err(|problem| problem.to_string())?
                    .current_state
                    == ServiceState::Stopped
                {
                    if action == "disable" {
                        set_service_start_type("disabled")?;
                    }
                    return Ok(());
                }
                std::thread::sleep(std::time::Duration::from_millis(250));
            }
            Err("Le service GALAXAM ne s'est pas arrêté dans le délai attendu".into())
        }
        _ => Err("Action de service Windows non autorisée".into()),
    }
}

fn set_service_start_type(value: &str) -> Result<(), String> {
    let sc = std::env::var_os("SystemRoot")
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from(r"C:\Windows"))
        .join("System32/sc.exe");
    let output = Command::new(sc)
        .args([
            "config",
            crate::platform::windows_service_runtime::SERVICE_NAME,
            "start=",
            value,
        ])
        .output()
        .map_err(|_| "Configuration du démarrage du service impossible".to_string())?;
    if !output.status.success() {
        return Err("Configuration du démarrage du service impossible".into());
    }
    Ok(())
}

fn write_elevated_result(path: &Path, result: &str) -> Result<(), String> {
    let mut file = std::fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(path)
        .map_err(|_| "Création du résultat d'élévation impossible".to_string())?;
    file.write_all(result.as_bytes())
        .map_err(|_| "Écriture du résultat d'élévation impossible".to_string())?;
    file.sync_all()
        .map_err(|_| "Synchronisation du résultat d'élévation impossible".to_string())
}

fn quote_windows_argument(argument: &str) -> String {
    if !argument
        .chars()
        .any(|character| matches!(character, ' ' | '\t' | '"'))
    {
        return argument.to_owned();
    }
    let mut quoted = String::from("\"");
    let mut slashes = 0;
    for character in argument.chars() {
        match character {
            '\\' => slashes += 1,
            '"' => {
                quoted.push_str(&"\\".repeat(slashes * 2 + 1));
                quoted.push('"');
                slashes = 0;
            }
            _ => {
                quoted.push_str(&"\\".repeat(slashes));
                slashes = 0;
                quoted.push(character);
            }
        }
    }
    quoted.push_str(&"\\".repeat(slashes * 2));
    quoted.push('"');
    quoted
}

fn validate_user_result_path(path: &Path, expected_file: &str) -> Result<(), String> {
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| "Fichier temporaire utilisateur invalide".to_string())?;
    let parent = path
        .parent()
        .ok_or_else(|| "Dossier temporaire utilisateur invalide".to_string())?;
    if !path.is_absolute() || !file_name.eq_ignore_ascii_case(expected_file) {
        return Err("Fichier temporaire utilisateur hors emplacement autorisé".into());
    }
    if !parent.exists() {
        std::fs::create_dir(parent)
            .map_err(|_| "Dossier temporaire utilisateur impossible à créer".to_string())?;
    }
    let metadata = std::fs::symlink_metadata(parent)
        .map_err(|_| "Dossier temporaire utilisateur inaccessible".to_string())?;
    if !metadata.file_type().is_dir() {
        return Err("Dossier temporaire utilisateur invalide".into());
    }
    let mut ancestor = Some(parent);
    while let Some(directory) = ancestor {
        let metadata = std::fs::symlink_metadata(directory)
            .map_err(|_| "Chemin du profil utilisateur inaccessible".to_string())?;
        if metadata.file_type().is_symlink() {
            return Err("Le chemin du profil utilisateur contient un lien symbolique".into());
        }
        ancestor = directory.parent();
    }
    let canonical = std::fs::canonicalize(parent)
        .map_err(|_| "Dossier temporaire utilisateur inaccessible".to_string())?;
    let canonical_text = canonical
        .to_string_lossy()
        .replace('/', "\\")
        .to_lowercase();
    if !canonical_text.ends_with("\\appdata\\local\\galaxam sirh") {
        return Err("Le jeton doit rester dans le profil local de l'utilisateur".into());
    }
    Ok(())
}

#[link(name = "shell32")]
unsafe extern "system" {
    fn ShellExecuteW(
        window: *mut std::ffi::c_void,
        operation: *const u16,
        file: *const u16,
        parameters: *const u16,
        directory: *const u16,
        show_command: i32,
    ) -> *mut std::ffi::c_void;
}

pub fn default_data_home() -> Result<PathBuf, &'static str> {
    Ok(
        PathBuf::from(std::env::var_os("ProgramData").ok_or("Dossier ProgramData introuvable")?)
            .join("GALAXAM SIRH"),
    )
}

pub fn fallback_data_home() -> PathBuf {
    PathBuf::from(std::env::var_os("ProgramData").unwrap_or_default()).join("GALAXAM SIRH")
}

pub fn restart_with_installation(app: &AppHandle, config: &DesktopConfig) -> Result<(), String> {
    let executable = tauri::process::current_binary(&app.env())
        .map_err(|_| "Exécutable GALAXAM introuvable pour le redémarrage".to_string())?;
    let mut command = Command::new(executable);
    for arg in app.env().args_os.iter().skip(1) {
        let text = arg.to_string_lossy();
        if !text.starts_with("--galaxam-data-home=")
            && !text.starts_with("--galaxam-web-port=")
            && !text.starts_with("--galaxam-test-launch-agent-dir=")
        {
            command.arg(arg);
        }
    }
    command
        .args(config.restart_arguments())
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|_| "Impossible de redémarrer GALAXAM".to_string())?;
    Ok(())
}
pub fn choose_export_destination() -> Result<Option<PathBuf>, String> {
    Err("Export non encore disponible sur ce système".into())
}
pub fn reveal_backups(backups: &Path) -> Result<(), String> {
    Command::new("explorer.exe")
        .arg(backups)
        .spawn()
        .map_err(|_| "Impossible d’ouvrir le dossier des sauvegardes".to_string())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::prepare_service_data_home;

    #[test]
    fn preparing_service_home_does_not_create_postgres_cluster_parent() {
        let root = std::env::temp_dir().join(format!(
            "galaxam-windows-service-home-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        let home = root.join("GALAXAM SIRH");

        prepare_service_data_home(&home).expect("service data folders and ACLs should be prepared");

        assert!(home.join("config").is_dir());
        assert!(home.join("data").is_dir());
        assert!(home.join("logs").is_dir());
        assert!(
            !home.join("data/postgresql").exists(),
            "the PostgreSQL parent must be created by first-run initialization"
        );
        std::fs::remove_dir_all(root).expect("temporary home should be removable");
    }
}
