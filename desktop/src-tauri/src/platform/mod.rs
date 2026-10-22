//! Operating-system service and UI integration. Keep commands and backup format platform-independent.
#[cfg(target_os = "macos")]
#[path = "macos/service.rs"]
pub mod service;
#[cfg(not(target_os = "macos"))]
#[path = "unsupported_service.rs"]
pub mod service;

#[cfg(target_os = "macos")]
mod macos;
#[cfg(not(target_os = "macos"))]
mod unsupported;

#[cfg(target_os = "macos")]
pub use macos::{
    choose_export_destination, default_data_home, fallback_data_home, restart_with_installation,
    reveal_backups,
};
#[cfg(not(target_os = "macos"))]
pub use unsupported::{
    choose_export_destination, default_data_home, fallback_data_home, restart_with_installation,
    reveal_backups,
};
