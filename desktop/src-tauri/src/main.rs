#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod backend;

use axum::body::Bytes;
use axum::extract::DefaultBodyLimit;
use axum::extract::{OriginalUri, State};
use axum::http::{header, HeaderMap, Method, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::any;
use axum::Router;
use include_dir::{include_dir, Dir};
use percent_encoding::percent_decode_str;
use std::sync::Arc;
use std::time::Duration;
use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};

use backend::{BackendProcess, DesktopConfig};

const DEFAULT_WEB_PORT: u16 = 18888;
const MAX_REQUEST_BYTES: usize = 55 * 1024 * 1024;
const CSP: &str = "default-src 'self'; connect-src 'self'; img-src 'self' blob: data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; object-src 'none'; frame-ancestors 'none'";
static FRONTEND: Dir<'_> = include_dir!("$CARGO_MANIFEST_DIR/../../frontend/dist");

#[derive(Clone)]
struct ServerState {
    client: reqwest::Client,
    web_origin: String,
    api_upstream: String,
    startup_error: Option<&'static str>,
}

async fn handle(
    State(state): State<Arc<ServerState>>,
    method: Method,
    OriginalUri(uri): OriginalUri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    let expected_host = state.web_origin.trim_start_matches("http://");
    if headers.get(header::HOST).map(|value| value.as_bytes()) != Some(expected_host.as_bytes()) {
        return StatusCode::FORBIDDEN.into_response();
    }
    let path = uri.path();
    if path == "/desktop-start-error" {
        if method != Method::GET && method != Method::HEAD {
            return StatusCode::METHOD_NOT_ALLOWED.into_response();
        }
        let message = state
            .startup_error
            .unwrap_or("Erreur de démarrage du backend");
        let page = format!(
            "<!doctype html><html lang=\"fr\"><meta charset=\"utf-8\"><title>GALAXAM SIRH</title><body style=\"font:16px system-ui;max-width:680px;margin:10vh auto;padding:24px\"><h1>GALAXAM SIRH</h1><p>{message}</p><p>Vérifiez la configuration dans le dossier Application Support et les journaux du backend.</p></body></html>"
        );
        return ([(header::CONTENT_TYPE, "text/html; charset=utf-8")], page).into_response();
    }
    if state.startup_error.is_some() {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    }
    if path == "/api" || path.starts_with("/api/") {
        if !matches!(
            method,
            Method::GET
                | Method::HEAD
                | Method::POST
                | Method::PUT
                | Method::PATCH
                | Method::DELETE
                | Method::OPTIONS
        ) {
            return StatusCode::METHOD_NOT_ALLOWED.into_response();
        }
        return proxy(state, method, uri, headers, body).await;
    }
    if method != Method::GET && method != Method::HEAD {
        return StatusCode::METHOD_NOT_ALLOWED.into_response();
    }

    let requested = match percent_decode_str(path.trim_start_matches('/')).decode_utf8() {
        Ok(value) => value,
        Err(_) => return StatusCode::BAD_REQUEST.into_response(),
    };
    if requested
        .split('/')
        .any(|segment| segment == "." || segment == "..")
        || requested.contains('\\')
        || requested.contains('\0')
    {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let asset = if requested.is_empty() {
        "index.html"
    } else {
        requested.as_ref()
    };
    let file = FRONTEND.get_file(asset).or_else(|| {
        if asset.contains('.') {
            None
        } else {
            FRONTEND.get_file("index.html")
        }
    });
    match file {
        Some(file) => {
            let mime = mime_guess::from_path(file.path()).first_or_octet_stream();
            (
                [
                    (header::CONTENT_TYPE, mime.as_ref()),
                    (header::CONTENT_SECURITY_POLICY, CSP),
                    (header::X_FRAME_OPTIONS, "DENY"),
                ],
                file.contents().to_vec(),
            )
                .into_response()
        }
        None => StatusCode::NOT_FOUND.into_response(),
    }
}

async fn proxy(
    state: Arc<ServerState>,
    method: Method,
    uri: axum::http::Uri,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    // The desktop server accepts browser requests only from its own origin.
    // Spring receives an internal same-origin proxy request, without a foreign Origin.
    if let Some(origin) = headers.get(header::ORIGIN) {
        if origin.as_bytes() != state.web_origin.as_bytes() {
            return StatusCode::FORBIDDEN.into_response();
        }
    }
    let target = format!(
        "{}{}",
        state.api_upstream,
        uri.path_and_query()
            .map(|value| value.as_str())
            .unwrap_or("/api/")
    );
    let mut request = state.client.request(method, target).body(body);
    for (name, value) in &headers {
        if matches!(
            name.as_str(),
            "accept"
                | "accept-language"
                | "authorization"
                | "cache-control"
                | "content-type"
                | "cookie"
                | "if-modified-since"
                | "if-none-match"
                | "range"
                | "x-idempotency-key"
        ) {
            request = request.header(name, value);
        }
    }
    let upstream = match request.send().await {
        Ok(response) => response,
        Err(_) => return StatusCode::BAD_GATEWAY.into_response(),
    };
    let status = upstream.status();
    let upstream_headers = upstream.headers().clone();
    let bytes = match upstream.bytes().await {
        Ok(bytes) => bytes,
        Err(_) => return StatusCode::BAD_GATEWAY.into_response(),
    };
    let mut response = (status, bytes).into_response();
    for (name, value) in &upstream_headers {
        if name != header::CONNECTION
            && name != header::CONTENT_LENGTH
            && name != header::TRANSFER_ENCODING
            && name != header::ACCESS_CONTROL_ALLOW_ORIGIN
            && name != header::ACCESS_CONTROL_ALLOW_CREDENTIALS
        {
            response.headers_mut().append(name, value.clone());
        }
    }
    response
}

fn main() {
    let web_port = std::env::var("GALAXAM_DESKTOP_WEB_PORT")
        .ok()
        .and_then(|value| value.parse::<u16>().ok().filter(|port| *port != 0))
        .unwrap_or(DEFAULT_WEB_PORT);
    let web_origin = format!("http://127.0.0.1:{web_port}");
    // Reserve the desktop port before Tauri enters the macOS app callback.
    // Returning an error from setup would panic inside that callback.
    let listener = match std::net::TcpListener::bind(("127.0.0.1", web_port)) {
        Ok(listener) => listener,
        Err(error) => {
            eprintln!("GALAXAM SIRH cannot start its local server: {error}");
            std::process::exit(1);
        }
    };
    if let Err(error) = listener.set_nonblocking(true) {
        eprintln!("GALAXAM SIRH cannot configure its local server: {error}");
        std::process::exit(1);
    }
    let app = tauri::Builder::default()
        .setup(move |app| {
            let process = BackendProcess::new();
            let (api_upstream, startup_error) = match DesktopConfig::load() {
                Ok(config) => {
                    let error = if config.local_port.is_some() {
                        process.start(app, &config, &web_origin).err()
                    } else {
                        None
                    };
                    (config.api_upstream, error)
                }
                Err(error) => (String::from("http://127.0.0.1:18080"), Some(error)),
            };
            app.manage(process);
            #[cfg(unix)]
            {
                let handle = app.handle().clone();
                tauri::async_runtime::spawn(async move {
                    if let Ok(mut signal) =
                        tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
                    {
                        signal.recv().await;
                        handle.exit(0);
                    }
                });
            }
            let state = Arc::new(ServerState {
                client: reqwest::Client::builder()
                    .redirect(reqwest::redirect::Policy::none())
                    .timeout(Duration::from_secs(125))
                    .build()?,
                web_origin: web_origin.clone(),
                api_upstream,
                startup_error,
            });
            tauri::async_runtime::spawn(async move {
                let listener = tokio::net::TcpListener::from_std(listener)
                    .expect("failed to start desktop HTTP listener");
                let router = Router::new()
                    .fallback(any(handle))
                    .layer(DefaultBodyLimit::max(MAX_REQUEST_BYTES))
                    .with_state(state);
                if let Err(error) = axum::serve(listener, router).await {
                    eprintln!("Desktop HTTP server stopped: {error}");
                }
            });
            let page = if startup_error.is_some() {
                format!("{web_origin}/desktop-start-error")
            } else {
                web_origin.clone()
            };
            WebviewWindowBuilder::new(app, "main", WebviewUrl::External(page.parse()?))
                .title("GALAXAM SIRH")
                .inner_size(1440.0, 900.0)
                .min_inner_size(1024.0, 700.0)
                .build()?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() == "main" && matches!(event, tauri::WindowEvent::Destroyed) {
                window.app_handle().state::<BackendProcess>().stop();
            }
        })
        .build(tauri::generate_context!())
        .expect("failed to launch GALAXAM SIRH desktop");
    app.run(|handle, event| {
        if matches!(event, tauri::RunEvent::Exit) {
            handle.state::<BackendProcess>().stop();
        }
    });
}
