fn main() {
    tauri_build::try_build(tauri_build::Attributes::new().app_manifest(
        tauri_build::AppManifest::new().commands(&[
            "get_desktop_status",
            "start_local_setup",
            "test_remote_api",
            "apply_remote_mode",
            "switch_to_local_mode",
            "create_initial_admin",
            "stop_local_services",
        ]),
    ))
    .expect("failed to build Tauri permissions");
}
