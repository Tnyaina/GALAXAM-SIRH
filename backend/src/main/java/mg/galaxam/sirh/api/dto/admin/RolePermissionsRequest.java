package mg.galaxam.sirh.api.dto.admin;

import java.util.List;

public class RolePermissionsRequest {
    private List<PermissionUpdateRequest> permissions;

    // Getters
    public List<PermissionUpdateRequest> getPermissions() { return permissions; }

    // Setters
    public void setPermissions(List<PermissionUpdateRequest> permissions) { this.permissions = permissions; }
}
