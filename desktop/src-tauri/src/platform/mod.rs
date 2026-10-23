//! Operating-system service and UI integration. Keep commands and backup format platform-independent.
#[cfg(target_os = "macos")]
#[path = "macos/service.rs"]
pub mod service;
#[cfg(target_os = "windows")]
#[path = "windows/service.rs"]
pub mod service;
#[cfg(not(any(target_os = "macos", target_os = "windows")))]
#[path = "unsupported_service.rs"]
pub mod service;

#[cfg(target_os = "macos")]
mod macos;
#[cfg(not(any(target_os = "macos", target_os = "windows")))]
mod unsupported;
#[cfg(target_os = "windows")]
mod windows;
#[cfg(target_os = "windows")]
#[path = "windows/service_runtime.rs"]
pub mod windows_service_runtime;

#[cfg(target_os = "macos")]
pub use macos::{
    choose_export_destination, default_data_home, fallback_data_home, restart_with_installation,
    reveal_backups,
};
#[cfg(not(any(target_os = "macos", target_os = "windows")))]
pub use unsupported::{
    choose_export_destination, default_data_home, fallback_data_home, restart_with_installation,
    reveal_backups,
};
#[cfg(target_os = "windows")]
pub use windows::{
    assign_service_child, choose_export_destination, create_service_child_job, default_data_home,
    disable_windows_service, elevated_bootstrap_token, ensure_local_api_ready,
    ensure_windows_service_ready, fallback_data_home, install_service, is_bootstrap_helper,
    remove_service, restart_with_installation, reveal_backups, run_bootstrap_helper,
    stop_windows_service,
};
