use crate::connection::config::DesktopConfig;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use tauri::{AppHandle, Manager};

const BOOTSTRAP_HELPER_ARG: &str = "--galaxam-bootstrap-helper";
const BOOTSTRAP_OUTPUT_ARG: &str = "--galaxam-token-output=";
const BACKUP_HELPER_ARG: &str = "--galaxam-backup-helper";
const RESTORE_HELPER_ARG: &str = "--galaxam-restore-helper";
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
    std::env::args_os().any(|argument| {
        argument == BOOTSTRAP_HELPER_ARG
            || argument == BACKUP_HELPER_ARG
            || argument == RESTORE_HELPER_ARG
    })
}

pub fn run_bootstrap_helper() -> Result<(), String> {
    if std::env::args_os().any(|argument| argument == BACKUP_HELPER_ARG) {
        return run_backup_helper();
    }
    if std::env::args_os().any(|argument| argument == RESTORE_HELPER_ARG) {
        return run_restore_helper();
    }
    if let Some(control) = std::env::args_os().find_map(|argument| {
        argument
            .to_string_lossy()
            .strip_prefix("--galaxam-service-control=")
            .map(str::to_owned)
    }) {
        let remote = std::env::args_os().find_map(|argument| {
            argument
                .to_string_lossy()
                .strip_prefix("--galaxam-remote-api=")
                .map(str::to_owned)
        });
        let result = run_service_control(&control, remote.as_deref());
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

fn run_backup_helper() -> Result<(), String> {
    let result_path = argument_path("--galaxam-backup-result=")
        .ok_or_else(|| "Fichier résultat de sauvegarde absent".to_string())?;
    validate_user_result_path(&result_path, "backup.result")?;
    let destination = argument_path("--galaxam-backup-output=")
        .ok_or_else(|| "Destination de sauvegarde absente".to_string())?;
    if !destination.is_absolute()
        || destination.file_name().is_none()
        || !destination
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("tar"))
        || !destination.parent().is_some_and(Path::is_dir)
    {
        return Err("Destination de sauvegarde invalide".into());
    }
    let home = default_data_home().map_err(str::to_string)?;
    let port = std::env::args_os()
        .find_map(|argument| {
            argument
                .to_string_lossy()
                .strip_prefix("--galaxam-pg-port=")
                .and_then(|value| value.parse::<u16>().ok())
        })
        .filter(|port| *port != 0)
        .unwrap_or(15432);
    let result = (|| {
        let resources = std::env::current_exe()
            .map_err(|_| "Exécutable GALAXAM introuvable".to_string())?
            .parent()
            .ok_or_else(|| "Ressources GALAXAM introuvables".to_string())?
            .to_path_buf();
        let archive = crate::backup::archive::create_from_resources(&resources, &home, port)?;
        crate::backup::archive::export_copy(&archive, &destination)
    })();
    let payload = match &result {
        Ok(bytes) => format!("OK:{bytes}"),
        Err(problem) => format!("ERROR:{problem}"),
    };
    write_elevated_result(&result_path, &payload)?;
    result.map(|_| ())
}

fn run_restore_helper() -> Result<(), String> {
    let result_path = argument_path("--galaxam-restore-result=")
        .ok_or("Fichier résultat de restauration absent")?;
    validate_user_result_path(&result_path, "restore.result")?;
    let source =
        argument_path("--galaxam-restore-input=").ok_or("Archive de restauration absente")?;
    if !source.is_absolute()
        || !source.is_file()
        || !source
            .extension()
            .is_some_and(|value| value.eq_ignore_ascii_case("tar"))
    {
        return Err("Archive de restauration invalide".into());
    }
    let home = default_data_home().map_err(str::to_string)?;
    let port = std::env::args_os()
        .find_map(|arg| {
            arg.to_string_lossy()
                .strip_prefix("--galaxam-pg-port=")
                .and_then(|value| value.parse::<u16>().ok())
        })
        .filter(|value| *value != 0)
        .unwrap_or(15432);
    let api_port = std::env::args_os()
        .find_map(|arg| {
            arg.to_string_lossy()
                .strip_prefix("--galaxam-api-port=")
                .and_then(|value| value.parse::<u16>().ok())
        })
        .filter(|value| *value != 0)
        .unwrap_or(18081);
    let result = (|| {
        let resources = std::env::current_exe()
            .map_err(|_| "Exécutable GALAXAM introuvable".to_string())?
            .parent()
            .ok_or("Ressources GALAXAM introuvables")?
            .to_path_buf();
        let work = home
            .join("backups")
            .join(format!(".restore-work-{}", std::process::id()));
        if work.exists() {
            return Err("Une opération de restauration précédente doit être vérifiée".into());
        }
        std::fs::create_dir_all(&work)
            .map_err(|_| "Dossier temporaire de restauration inaccessible".to_string())?;
        let incoming = work.join("incoming");
        let safety = work.join("safety");
        let outcome = (|| {
            let rollback_archive =
                crate::backup::archive::create_from_resources(&resources, &home, port)?;
            let stable_source = work.join("import.tar");
            crate::backup::archive::export_copy(&source, &stable_source)?;
            let _incoming_has_docs =
                crate::backup::archive::extract_for_restore(&stable_source, &incoming)?;
            let _safety_has_docs =
                crate::backup::archive::extract_for_restore(&rollback_archive, &safety)?;
            run_service_control("stop", None)?;
            let request = home.join("config/restore.request");
            let service_result = home.join("config/restore-service.result");
            if request.exists() {
                return Err("Une restauration Windows précédente doit être vérifiée".into());
            }
            if service_result.exists() {
                std::fs::remove_file(&service_result).map_err(|_| {
                    "Ancien résultat de restauration impossible à supprimer".to_string()
                })?;
            }
            std::fs::write(
                &request,
                format!(".restore-work-{}\n{port}\n", std::process::id()),
            )
            .map_err(|_| "Demande de restauration au service impossible".to_string())?;
            run_service_control("start", None)?;
            let deadline = std::time::Instant::now() + std::time::Duration::from_secs(900);
            while std::time::Instant::now() < deadline {
                if let Ok(response) = std::fs::read_to_string(&service_result) {
                    let _ = std::fs::remove_file(&service_result);
                    return match response.strip_prefix("ERROR:") {
                        Some(problem) => Err(problem.to_string()),
                        None if response == "OK" => {
                            while std::time::Instant::now() < deadline {
                                if windows_api_ping(api_port) {
                                    return Ok(());
                                }
                                std::thread::sleep(std::time::Duration::from_millis(500));
                            }
                            Err("L'API locale n'a pas redémarré après la restauration".into())
                        }
                        None => Err("Résultat de restauration du service invalide".into()),
                    };
                }
                std::thread::sleep(std::time::Duration::from_millis(250));
            }
            Err("Le service Windows n'a pas terminé la restauration dans le délai attendu".into())
        })();
        if outcome.is_ok() {
            let _ = std::fs::remove_dir_all(&work);
        }
        outcome
    })();
    let payload = result
        .as_ref()
        .map(|_| "OK".to_string())
        .unwrap_or_else(|problem| format!("ERROR:{problem}"));
    write_elevated_result(&result_path, &payload)?;
    result
}

fn argument_path(prefix: &str) -> Option<PathBuf> {
    std::env::args_os().find_map(|argument| {
        argument
            .to_string_lossy()
            .strip_prefix(prefix)
            .map(PathBuf::from)
    })
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

pub fn elevated_backup_export(home: &Path, port: u16, destination: &Path) -> Result<u64, String> {
    use std::os::windows::ffi::OsStrExt;
    if home != default_data_home().map_err(str::to_string)?
        || !destination.is_absolute()
        || destination.file_name().is_none()
        || !destination
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("tar"))
    {
        return Err("Destination de sauvegarde invalide".into());
    }
    let local_app_data = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .ok_or_else(|| "Profil utilisateur Windows indisponible".to_string())?;
    let result_path = local_app_data.join("GALAXAM SIRH/backup.result");
    validate_user_result_path(&result_path, "backup.result")?;
    if result_path.exists() {
        std::fs::remove_file(&result_path)
            .map_err(|_| "Ancien résultat de sauvegarde impossible à supprimer".to_string())?;
    }
    let executable =
        std::env::current_exe().map_err(|_| "Exécutable GALAXAM introuvable".to_string())?;
    let arguments = [
        BACKUP_HELPER_ARG.to_string(),
        format!("--galaxam-data-home={}", home.display()),
        format!("--galaxam-pg-port={port}"),
        format!("--galaxam-backup-output={}", destination.display()),
        format!("--galaxam-backup-result={}", result_path.display()),
    ];
    let parameters = arguments
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
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(600);
    while std::time::Instant::now() < deadline {
        if result_path.is_file() {
            let result = std::fs::read_to_string(&result_path)
                .map_err(|_| "Résultat de sauvegarde illisible".to_string())?;
            let _ = std::fs::remove_file(&result_path);
            if let Some(bytes) = result.strip_prefix("OK:") {
                return bytes
                    .parse()
                    .map_err(|_| "Taille de sauvegarde invalide".to_string());
            }
            return Err(result
                .strip_prefix("ERROR:")
                .unwrap_or("Création de la sauvegarde impossible")
                .to_owned());
        }
        std::thread::sleep(std::time::Duration::from_millis(250));
    }
    Err("La sauvegarde n'a pas abouti dans le délai imparti".into())
}

pub fn restore_local_backup(
    _app: &AppHandle,
    home: &Path,
    api_port: u16,
    port: u16,
    archive: &Path,
) -> Result<(), String> {
    use std::os::windows::ffi::OsStrExt;
    if home != default_data_home().map_err(str::to_string)?
        || !archive.is_absolute()
        || !archive.is_file()
        || !archive
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("tar"))
    {
        return Err("Archive de restauration invalide".into());
    }
    let local_app_data = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .ok_or("Profil utilisateur Windows indisponible")?;
    let result_path = local_app_data.join("GALAXAM SIRH/restore.result");
    validate_user_result_path(&result_path, "restore.result")?;
    if result_path.exists() {
        std::fs::remove_file(&result_path)
            .map_err(|_| "Ancien résultat de restauration impossible à supprimer".to_string())?;
    }
    let executable =
        std::env::current_exe().map_err(|_| "Exécutable GALAXAM introuvable".to_string())?;
    let arguments = [
        RESTORE_HELPER_ARG.to_string(),
        format!("--galaxam-pg-port={port}"),
        format!("--galaxam-api-port={api_port}"),
        format!("--galaxam-restore-input={}", archive.display()),
        format!("--galaxam-restore-result={}", result_path.display()),
    ];
    let parameters = arguments
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
    let deadline = std::time::Instant::now() + std::time::Duration::from_secs(900);
    while std::time::Instant::now() < deadline {
        if result_path.is_file() {
            let result = std::fs::read_to_string(&result_path)
                .map_err(|_| "Résultat de restauration illisible".to_string())?;
            let _ = std::fs::remove_file(&result_path);
            return if result == "OK" {
                Ok(())
            } else {
                Err(result
                    .strip_prefix("ERROR:")
                    .unwrap_or("Restauration échouée")
                    .to_string())
            };
        }
        std::thread::sleep(std::time::Duration::from_millis(250));
    }
    Err("La restauration n’a pas abouti dans le délai imparti".into())
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
    run_elevated_service_control(home, "start", None)?;
    ensure_local_api_ready(port)
}

pub fn stop_windows_service(home: &Path) -> Result<(), String> {
    run_elevated_service_control(home, "stop", None)
}

pub fn disable_windows_service(home: &Path) -> Result<(), String> {
    run_elevated_service_control(home, "disable", None)
}

pub fn set_windows_remote_mode(home: &Path, origin: &str) -> Result<(), String> {
    run_elevated_service_control(home, "remote", Some(origin))
}

pub fn set_windows_local_mode(home: &Path) -> Result<(), String> {
    run_elevated_service_control(home, "local", None)
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

fn run_elevated_service_control(
    home: &Path,
    action: &str,
    remote: Option<&str>,
) -> Result<(), String> {
    use std::os::windows::ffi::OsStrExt;
    if home != default_data_home().map_err(str::to_string)?
        || !matches!(action, "start" | "stop" | "disable" | "local" | "remote")
        || (action == "remote") != remote.is_some()
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
    let mut parameters = vec![
        BOOTSTRAP_HELPER_ARG.to_string(),
        format!("--galaxam-service-control={action}"),
        format!("--galaxam-control-result={}", result_path.display()),
    ];
    if let Some(origin) = remote {
        parameters.push(format!("--galaxam-remote-api={origin}"));
    }
    let parameters = parameters
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
    let deadline = std::time::Instant::now()
        + std::time::Duration::from_secs(if action == "local" { 240 } else { 120 });
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

fn run_service_control(action: &str, remote: Option<&str>) -> Result<(), String> {
    if matches!(action, "local" | "remote") {
        let home = default_data_home().map_err(str::to_string)?;
        let config = DesktopConfig::load().map_err(str::to_string)?;
        if config.home != home {
            return Err("Répertoire du service GALAXAM inattendu".into());
        }
        return match action {
            "remote" => {
                let origin = remote.ok_or("Adresse distante absente")?;
                // Validate the destination before stopping the current service.
                validate_remote_api_origin(origin)?;
                run_service_control("disable", None)?;
                if let Err(problem) = config.save_remote_mode(origin) {
                    let recovery = if config.local_port.is_some() {
                        run_service_control("start", None)
                    } else {
                        Ok(())
                    };
                    return Err(match recovery {
                        Ok(()) => problem.to_string(),
                        Err(error) => format!("{problem}; redémarrage local échoué : {error}"),
                    });
                }
                Ok(())
            }
            "local" => {
                if !home.join("data/postgresql/cluster/PG_VERSION").is_file()
                    || !home
                        .join("data/postgresql/config/installation.ready")
                        .is_file()
                {
                    return Err("Base locale existante non validée".into());
                }
                run_service_control("start", None)?;
                let port = DesktopConfig::new_for_setup().local_port.unwrap_or(18081);
                if let Err(problem) = ensure_local_api_ready(port) {
                    let _ = run_service_control("disable", None);
                    return Err(problem);
                }
                let local = DesktopConfig::new_for_setup();
                if let Err(problem) = local.save_local_mode() {
                    let cleanup = run_service_control("disable", None);
                    return Err(match cleanup {
                        Ok(()) => problem.to_string(),
                        Err(error) => format!("{problem}; arrêt du service local échoué : {error}"),
                    });
                }
                Ok(())
            }
            _ => unreachable!(),
        };
    }
    if remote.is_some() {
        return Err("Adresse distante inattendue".into());
    }
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

fn validate_remote_api_origin(origin: &str) -> Result<(), String> {
    let url = reqwest::Url::parse(origin).map_err(|_| "Adresse distante invalide")?;
    if (url.scheme() != "https"
        && !(url.scheme() == "http" && matches!(url.host_str(), Some("127.0.0.1" | "localhost"))))
        || !url.username().is_empty()
        || url.password().is_some()
        || url.path() != "/"
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err("Adresse API distante invalide".into());
    }
    Ok(())
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

#[repr(C)]
struct OpenFileNameW {
    size: u32,
    owner: *mut std::ffi::c_void,
    instance: *mut std::ffi::c_void,
    filter: *const u16,
    custom_filter: *mut u16,
    max_custom_filter: u32,
    filter_index: u32,
    file: *mut u16,
    max_file: u32,
    file_title: *mut u16,
    max_file_title: u32,
    initial_dir: *const u16,
    title: *const u16,
    flags: u32,
    file_offset: u16,
    file_extension: u16,
    default_extension: *const u16,
    custom_data: isize,
    hook: Option<unsafe extern "system" fn(*mut std::ffi::c_void, u32, usize, isize) -> usize>,
    template_name: *const u16,
    reserved: *mut std::ffi::c_void,
    reserved_value: u32,
    flags_ex: u32,
}

#[link(name = "comdlg32")]
unsafe extern "system" {
    fn GetSaveFileNameW(dialog: *mut OpenFileNameW) -> i32;
    fn GetOpenFileNameW(dialog: *mut OpenFileNameW) -> i32;
    fn CommDlgExtendedError() -> u32;
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
    use std::os::windows::ffi::OsStringExt;
    let mut file_name = vec![0u16; 32_768];
    let default_name: Vec<u16> = "galaxam-sauvegarde.tar\0".encode_utf16().collect();
    file_name[..default_name.len()].copy_from_slice(&default_name);
    let filter: Vec<u16> = "Sauvegarde GALAXAM (*.tar)\0*.tar\0Tous les fichiers (*.*)\0*.*\0\0"
        .encode_utf16()
        .collect();
    let title: Vec<u16> = "Enregistrer une sauvegarde GALAXAM SIRH\0"
        .encode_utf16()
        .collect();
    let extension: Vec<u16> = "tar\0".encode_utf16().collect();
    let mut dialog = OpenFileNameW {
        size: std::mem::size_of::<OpenFileNameW>() as u32,
        owner: std::ptr::null_mut(),
        instance: std::ptr::null_mut(),
        filter: filter.as_ptr(),
        custom_filter: std::ptr::null_mut(),
        max_custom_filter: 0,
        filter_index: 1,
        file: file_name.as_mut_ptr(),
        max_file: file_name.len() as u32,
        file_title: std::ptr::null_mut(),
        max_file_title: 0,
        initial_dir: std::ptr::null(),
        title: title.as_ptr(),
        flags: 0x0008_0000 | 0x0000_0002 | 0x0000_0800 | 0x0000_0008,
        file_offset: 0,
        file_extension: 0,
        default_extension: extension.as_ptr(),
        custom_data: 0,
        hook: None,
        template_name: std::ptr::null(),
        reserved: std::ptr::null_mut(),
        reserved_value: 0,
        flags_ex: 0,
    };
    if unsafe { GetSaveFileNameW(&mut dialog) } == 0 {
        return if unsafe { CommDlgExtendedError() } == 0 {
            Ok(None)
        } else {
            Err("Choix du fichier de sauvegarde impossible".into())
        };
    }
    let length = file_name
        .iter()
        .position(|value| *value == 0)
        .unwrap_or(file_name.len());
    let destination = PathBuf::from(std::ffi::OsString::from_wide(&file_name[..length]));
    if !destination.is_absolute() || destination.file_name().is_none() {
        return Err("Chemin de sauvegarde invalide".into());
    }
    Ok(Some(destination))
}
pub fn choose_restore_source() -> Result<Option<PathBuf>, String> {
    use std::os::windows::ffi::OsStringExt;
    let mut file_name = vec![0u16; 32_768];
    let filter: Vec<u16> = "Sauvegarde GALAXAM (*.tar)\0*.tar\0Tous les fichiers (*.*)\0*.*\0\0"
        .encode_utf16()
        .collect();
    let title: Vec<u16> = "Restaurer une sauvegarde GALAXAM SIRH\0"
        .encode_utf16()
        .collect();
    let mut dialog = OpenFileNameW {
        size: std::mem::size_of::<OpenFileNameW>() as u32,
        owner: std::ptr::null_mut(),
        instance: std::ptr::null_mut(),
        filter: filter.as_ptr(),
        custom_filter: std::ptr::null_mut(),
        max_custom_filter: 0,
        filter_index: 1,
        file: file_name.as_mut_ptr(),
        max_file: file_name.len() as u32,
        file_title: std::ptr::null_mut(),
        max_file_title: 0,
        initial_dir: std::ptr::null(),
        title: title.as_ptr(),
        flags: 0x0000_1000 | 0x0000_0800 | 0x0000_0008,
        file_offset: 0,
        file_extension: 0,
        default_extension: std::ptr::null(),
        custom_data: 0,
        hook: None,
        template_name: std::ptr::null(),
        reserved: std::ptr::null_mut(),
        reserved_value: 0,
        flags_ex: 0,
    };
    if unsafe { GetOpenFileNameW(&mut dialog) } == 0 {
        return if unsafe { CommDlgExtendedError() } == 0 {
            Ok(None)
        } else {
            Err("Choix de l’archive impossible".into())
        };
    }
    let length = file_name
        .iter()
        .position(|value| *value == 0)
        .unwrap_or(file_name.len());
    let path = PathBuf::from(std::ffi::OsString::from_wide(&file_name[..length]));
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
pub fn reveal_backups(backups: &Path) -> Result<(), String> {
    Command::new("explorer.exe")
        .arg(backups)
        .spawn()
        .map_err(|_| "Impossible d’ouvrir le dossier des sauvegardes".to_string())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{prepare_service_data_home, validate_remote_api_origin};

    #[test]
    fn elevated_mode_change_accepts_only_a_base_api_origin() {
        assert!(validate_remote_api_origin("https://sirh.example.invalid").is_ok());
        assert!(validate_remote_api_origin("http://127.0.0.1:18080").is_ok());
        assert!(validate_remote_api_origin("http://sirh.example.invalid").is_err());
        assert!(validate_remote_api_origin("https://sirh.example.invalid/admin").is_err());
        assert!(validate_remote_api_origin("https://user@sirh.example.invalid").is_err());
    }

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
