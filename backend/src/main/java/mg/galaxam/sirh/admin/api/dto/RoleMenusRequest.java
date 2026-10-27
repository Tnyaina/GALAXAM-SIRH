package mg.galaxam.sirh.admin.api.dto;

import java.util.List;

public class RoleMenusRequest {
    private List<String> idMenus;

    // Getters
    public List<String> getIdMenus() { return idMenus; }

    // Setters
    public void setIdMenus(List<String> idMenus) { this.idMenus = idMenus; }
}
