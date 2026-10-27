package mg.galaxam.sirh.admin.api.dto;

import java.util.List;

public class RolePermissionsRequest {
    private List<PermissionUpdateRequest> permissions;

    // Getters
    public List<PermissionUpdateRequest> getPermissions() { return permissions; }

    // Setters
    public void setPermissions(List<PermissionUpdateRequest> permissions) { this.permissions = permissions; }
}
