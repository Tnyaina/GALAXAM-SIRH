use axum::body::{Body, Bytes};
use axum::extract::{DefaultBodyLimit, OriginalUri, State as AxumState};
use axum::http::{header, HeaderMap, Method, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::any;
use axum::Router;
use include_dir::{include_dir, Dir};
use percent_encoding::percent_decode_str;
use std::sync::{Arc, RwLock};
use std::time::Duration;

pub const MAX_REQUEST_BYTES: usize = 55 * 1024 * 1024;
const CSP: &str = "default-src 'self'; connect-src 'self'; img-src 'self' blob: data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; object-src 'none'; frame-ancestors 'none'";
static FRONTEND: Dir<'_> = include_dir!("$CARGO_MANIFEST_DIR/../../frontend/dist");

#[derive(Clone)]
pub struct ServerState {
    client: reqwest::Client,
    web_origin: String,
    api_upstream: Arc<RwLock<Option<String>>>,
}

impl ServerState {
    pub fn new(
        web_origin: String,
        api_upstream: Arc<RwLock<Option<String>>>,
    ) -> Result<Self, reqwest::Error> {
        Ok(Self {
            client: reqwest::Client::builder()
                .redirect(reqwest::redirect::Policy::none())
                .timeout(Duration::from_secs(125))
                .build()?,
            web_origin,
            api_upstream,
        })
    }
}

pub async fn serve(listener: std::net::TcpListener, state: ServerState) -> std::io::Result<()> {
    let listener = tokio::net::TcpListener::from_std(listener)?;
    let router = Router::new()
        .fallback(any(handle))
        .layer(DefaultBodyLimit::max(MAX_REQUEST_BYTES))
        .with_state(Arc::new(state));
    axum::serve(listener, router).await
}

async fn handle(
    AxumState(state): AxumState<Arc<ServerState>>,
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
        let message = "Le démarrage de GALAXAM SIRH a rencontré un problème.";
        let page = format!(
            "<!doctype html><html lang=\"fr\"><meta charset=\"utf-8\"><title>GALAXAM SIRH</title><body style=\"font:16px system-ui;max-width:680px;margin:10vh auto;padding:24px\"><h1>GALAXAM SIRH</h1><p>{message}</p><p>Vérifiez la configuration dans le dossier Application Support et les journaux du backend.</p></body></html>"
        );
        return ([(header::CONTENT_TYPE, "text/html; charset=utf-8")], page).into_response();
    }
    if path == "/api" || path.starts_with("/api/") {
        if path == "/api/desktop/setup/admin" {
            return StatusCode::NOT_FOUND.into_response();
        }
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
    // Forward that validated origin so Spring can apply the same CSRF/origin policy
    // to desktop refresh and logout requests as it does to browser requests.
    let forward_origin = match validate_browser_origin(&headers, &state.web_origin) {
        Ok(present) => present,
        Err(status) => return status.into_response(),
    };
    let Some(upstream) = state
        .api_upstream
        .read()
        .ok()
        .and_then(|value| value.clone())
    else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    let target = format!(
        "{}{}",
        upstream,
        uri.path_and_query()
            .map(|value| value.as_str())
            .unwrap_or("/api/")
    );
    let mut request = state.client.request(method, target).body(body);
    if forward_origin {
        request = request.header(header::ORIGIN, &state.web_origin);
    }
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
    let mut response = (status, Body::from_stream(upstream.bytes_stream())).into_response();
    // Axum assigns application/octet-stream to a byte body. Keep the upstream
    // Content-Type (JSON, PDF, etc.) as the single authoritative value.
    response.headers_mut().remove(header::CONTENT_TYPE);
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

fn validate_browser_origin(headers: &HeaderMap, web_origin: &str) -> Result<bool, StatusCode> {
    match headers.get(header::ORIGIN) {
        Some(origin) if origin.as_bytes() != web_origin.as_bytes() => Err(StatusCode::FORBIDDEN),
        Some(_) => Ok(true),
        None => Ok(false),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_the_desktop_origin_is_forwarded_to_the_api() {
        let mut headers = HeaderMap::new();
        headers.insert(header::ORIGIN, "http://127.0.0.1:18888".parse().unwrap());
        assert!(validate_browser_origin(&headers, "http://127.0.0.1:18888").unwrap());
        assert_eq!(
            validate_browser_origin(&headers, "http://127.0.0.1:19999"),
            Err(StatusCode::FORBIDDEN)
        );
        headers.remove(header::ORIGIN);
        assert!(!validate_browser_origin(&headers, "http://127.0.0.1:18888").unwrap());
    }
}
