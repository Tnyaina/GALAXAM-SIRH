package mg.galaxam.sirh.securite;

import bean.ClassMere;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

public class AuditLog extends ClassMere {

    private Integer idAudit;
    private Integer idUtilisateur;
    private String action;
    private String nomTable;
    private String idEnregistrement;
    private String anciennesValeurs;
    private String nouvellesValeurs;
    private String adresseIp;
    private Timestamp dateAction;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AuditLog() {
        this.setNomTable("AUDIT_LOG");
    }

    @Override
    public Integer getTuppleID() {
        return idAudit;
    }

    @Override
    public String getAttributIDName() {
        return "id_audit";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO audit_log " +
                "(id_utilisateur, action, nom_table, id_enregistrement, anciennes_valeurs, nouvelles_valeurs, adresse_ip) " +
                "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idUtilisateur != null) {
                ps.setInt(1, idUtilisateur);
            } else {
                ps.setNull(1, java.sql.Types.INTEGER);
            }
            ps.setString(2, action);
            ps.setString(3, nomTable);
            ps.setString(4, idEnregistrement);
            ps.setObject(5, anciennesValeurs, java.sql.Types.OTHER);
            ps.setObject(6, nouvellesValeurs, java.sql.Types.OTHER);
            ps.setString(7, adresseIp);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("Les logs d'audit ne peuvent pas être modifiés.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("Les logs d'audit ne peuvent pas être supprimés.");
    }

    @Override
    protected ClassMere rechercherParId(String id, Connection c) throws Exception {
        return null;
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES D'AUDIT STATIQUES
    // =========================================================================

    public static void audit(
            Connection c,
            String action,
            String nomTable,
            String idEnregistrement,
            Object oldState,
            Object newState
    ) {
        java.sql.Savepoint sp = null;
        try {
            if (c != null && !c.getAutoCommit()) {
                sp = c.setSavepoint("audit_sp");
            }
            AuditLog log = new AuditLog();
            log.idUtilisateur = getCurrentUserId();
            log.action = action;
            log.nomTable = nomTable;
            log.idEnregistrement = idEnregistrement;
            log.anciennesValeurs = serializeState(oldState);
            log.nouvellesValeurs = serializeState(newState);
            log.adresseIp = getClientIp();

            log.save(c);
        } catch (Exception e) {
            System.err.println("Erreur d'audit log : " + e.getMessage());
            if (sp != null && c != null) {
                try { c.rollback(sp); } catch (Exception ignored) {}
            }
        }
    }

    private static Integer getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CustomUserDetails) {
                return ((CustomUserDetails) auth.getPrincipal()).getIdUtilisateur();
            }
        } catch (Exception e) {
            // Ignorer si pas de contexte de sécurité
        }
        return null;
    }

    private static String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                return ip;
            }
        } catch (Exception e) {
            // Ignorer
        }
        return "127.0.0.1";
    }

    private static String serializeState(Object state) {
        if (state == null) {
            return null;
        }
        try {
            if (state instanceof Utilisateur) {
                Utilisateur u = (Utilisateur) state;
                Map<String, Object> map = new HashMap<>();
                map.put("idUtilisateur", u.getIdUtilisateur());
                map.put("identifiant", u.getIdentifiant());
                map.put("idRole", u.getIdRole());
                map.put("estActif", u.isEstActif());
                return objectMapper.writeValueAsString(map);
            }
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            return "{}";
        }
    }

    // =========================================================================
    // GETTERS & SETTERS
    // =========================================================================

    public Integer getIdAudit() { return idAudit; }
    public Integer getIdUtilisateur() { return idUtilisateur; }
    public void setIdUtilisateur(Integer idUtilisateur) { this.idUtilisateur = idUtilisateur; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getNomTable() { return nomTable; }
    public void setNomTable(String nomTable) { this.nomTable = nomTable; }
    public String getIdEnregistrement() { return idEnregistrement; }
    public void setIdEnregistrement(String idEnregistrement) { this.idEnregistrement = idEnregistrement; }
    public String getAnciennesValeurs() { return anciennesValeurs; }
    public void setAnciennesValeurs(String anciennesValeurs) { this.anciennesValeurs = anciennesValeurs; }
    public String getNouvellesValeurs() { return nouvellesValeurs; }
    public void setNouvellesValeurs(String nouvellesValeurs) { this.nouvellesValeurs = nouvellesValeurs; }
    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }
    public Timestamp getDateAction() { return dateAction; }
}
