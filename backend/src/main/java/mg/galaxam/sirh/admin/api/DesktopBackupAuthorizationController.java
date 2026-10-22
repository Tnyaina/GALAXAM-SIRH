package mg.galaxam.sirh.admin.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Confirms a live administrator session before the native desktop exports data. */
@RestController
@RequestMapping("/admin/desktop-backup")
public class DesktopBackupAuthorizationController {
    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        return ResponseEntity.noContent().build();
    }
}
