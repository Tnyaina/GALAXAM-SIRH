use crate::app::runtime::{AdminInput, DesktopRuntime};
use std::time::Duration;
use tauri::State as TauriState;

#[tauri::command]
pub(crate) async fn create_initial_admin(
    input: AdminInput,
    state: TauriState<'_, DesktopRuntime>,
) -> Result<(), String> {
    {
        let status = state
            .status
            .lock()
            .map_err(|_| "État desktop indisponible")?;
        if status.mode != "local" || !status.setup_required {
            return Err("La création initiale n'est pas disponible".into());
        }
    }
    let token = state
        .bootstrap_token
        .lock()
        .map_err(|_| "État desktop indisponible")?
        .clone()
        .ok_or("Jeton d'amorçage indisponible ; redémarrez l'application")?;
    let upstream = state
        .api_upstream
        .read()
        .map_err(|_| "État API indisponible")?
        .clone()
        .ok_or("API locale indisponible")?;
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .timeout(Duration::from_secs(15))
        .build()
        .map_err(|_| "Client réseau indisponible")?;
    let response = client
        .post(format!("{upstream}/api/desktop/setup/admin"))
        .bearer_auth(token)
        .json(&input)
        .send()
        .await
        .map_err(|_| "API locale indisponible")?;
    if response.status() == reqwest::StatusCode::CREATED {
        *state
            .bootstrap_token
            .lock()
            .map_err(|_| "État desktop indisponible")? = None;
        let config = state
            .config
            .lock()
            .map_err(|_| "Configuration desktop indisponible")?;
        let _ = std::fs::remove_file(config.home.join("config/bootstrap.state"));
        state
            .status
            .lock()
            .map_err(|_| "État desktop indisponible")?
            .setup_required = false;
        return Ok(());
    }
    let status = response.status();
    let body: serde_json::Value = response.json().await.unwrap_or_default();
    Err(body
        .get("message")
        .and_then(|v| v.as_str())
        .map(str::to_owned)
        .unwrap_or_else(|| format!("Création refusée ({}).", status.as_u16())))
}
