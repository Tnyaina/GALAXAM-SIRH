use crate::connection::config::DesktopConfig;
use crate::database::postgres::PostgresProcess;
use std::sync::{Arc, Mutex, RwLock};

pub(crate) struct LocalServices {
    pub(crate) postgres: PostgresProcess,
}

impl LocalServices {
    pub(crate) fn new() -> Self {
        Self {
            postgres: PostgresProcess::new(),
        }
    }

    pub(crate) fn stop(&self) {
        self.postgres.stop();
    }
}

pub(crate) struct DesktopRuntime {
    pub(crate) config: Mutex<DesktopConfig>,
    pub(crate) status: Mutex<DesktopStatusState>,
    pub(crate) bootstrap_token: Mutex<Option<String>>,
    pub(crate) api_upstream: Arc<RwLock<Option<String>>>,
    pub(crate) tested_remote: Mutex<Option<String>>,
    pub(crate) backup_operation: Mutex<()>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct DesktopStatusState {
    pub(crate) mode: String,
    pub(crate) starting: bool,
    pub(crate) setup_required: bool,
    pub(crate) local_available: bool,
    pub(crate) startup_error: Option<String>,
}

#[derive(Debug, PartialEq, Eq, serde::Serialize)]
pub(crate) struct RuntimeStatus {
    pub(crate) mode: String,
    pub(crate) starting: bool,
    pub(crate) setup_required: bool,
    pub(crate) api_available: bool,
    pub(crate) local_available: bool,
    pub(crate) startup_error: Option<String>,
    pub(crate) connection_target: Option<String>,
}

#[derive(serde::Deserialize, serde::Serialize)]
pub(crate) struct AdminInput {
    pub(crate) identifiant: String,
    pub(crate) email: String,
    #[serde(rename = "motDePasse")]
    pub(crate) mot_de_passe: String,
}

#[derive(serde::Serialize)]
pub(crate) struct BackupResult {
    pub(crate) path: String,
    pub(crate) bytes: u64,
}

#[derive(serde::Serialize)]
pub(crate) struct BackupAutomationStatus {
    pub(crate) active: bool,
    pub(crate) last_success_unix_seconds: Option<u64>,
    pub(crate) last_archive: Option<String>,
    pub(crate) last_error: Option<String>,
}
