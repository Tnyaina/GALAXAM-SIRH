package mg.galaxam.sirh.paie.api;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAction;
import mg.galaxam.sirh.paie.domain.engine.AdvanceRepaymentPolicy;
import mg.galaxam.sirh.securite.persistence.AuditLog;
import mg.galaxam.sirh.securite.auth.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/paie/avances", "/v1/paie/avances", "/paie/avances"})
public class PaieAvanceRegleController {
    private final DataSource dataSource;
    private final PayrollPeriodWorkflowService periods;

    public PaieAvanceRegleController(DataSource dataSource, PayrollPeriodWorkflowService periods) {
        this.dataSource = dataSource;
        this.periods = periods;
    }

    public record Regle(BigDecimal plafondSalaireBasePct) {}
    public record RetenueChoix(BigDecimal montant, String motif) {}

    @GetMapping("/regle")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public Regle regle() throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT plafond_salaire_base_pct FROM paie_avance_regle WHERE id=1"); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) throw new IllegalStateException("Paramètre d'avance absent.");
            return new Regle(rs.getBigDecimal(1));
        }
    }

    @PutMapping("/regle")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Regle modifierRegle(@RequestBody Regle request, Authentication auth) throws Exception {
        BigDecimal pct = request.plafondSalaireBasePct();
        if (pct == null || pct.signum() < 0 || pct.compareTo(new BigDecimal("500")) > 0)
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Le plafond doit être compris entre 0 et 500 %.");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE paie_avance_regle SET plafond_salaire_base_pct=?,updated_at=NOW(),updated_by=? WHERE id=1")) {
                ps.setBigDecimal(1, pct); ps.setInt(2, actor(auth)); ps.executeUpdate();
                AuditLog.audit(c, "MODIFICATION_REGLE", "paie_avance_regle", "1", null, request);
                c.commit();
            } catch (Exception ex) { c.rollback(); throw ex; }
        }
        return new Regle(pct);
    }

    @GetMapping("/retenues")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public List<Map<String,Object>> retenues(@RequestParam int idPeriode) throws Exception {
        String imputationCampagne = "COALESCE((SELECT SUM(i.montant) FROM paie_mouvement_imputation i "
                + "JOIN bulletin_paie b ON b.id_bulletin=i.id_bulletin "
                + "WHERE i.id_avance=a.id_avance AND b.id_periode_paie=cible.id_periode_paie),0)";
        String sql = "SELECT a.id_avance,a.id_employe,e.matricule,TRIM(CONCAT(e.nom,' ',COALESCE(e.prenom,''))) nom_complet,"
                + "a.montant_initial,a.solde_restant+" + imputationCampagne + " AS solde_avant_campagne,"
                + "a.nombre_mensualites,choix.montant retenue_choisie,choix.motif motif_choix "
                + "FROM paie_avance_acompte a JOIN employe e ON e.id_employe=a.id_employe "
                + "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement=a.id_type_mouvement "
                + "JOIN periode_paie origine ON origine.id_periode_paie=a.id_periode_paie "
                + "JOIN periode_paie cible ON cible.id_periode_paie=? "
                + "LEFT JOIN paie_avance_retenue_choix choix ON choix.id_avance=a.id_avance AND choix.id_periode_paie=cible.id_periode_paie "
                + "WHERE t.categorie='AVANCE' AND a.statut IN ('APPROUVE','IMPUTE_PAIE') AND a.versement_confirme_at IS NOT NULL "
                + "AND a.date_paiement<=cible.date_fin AND origine.date_debut<=cible.date_debut "
                + "AND a.solde_restant+" + imputationCampagne + ">0 "
                + "ORDER BY e.matricule,a.date_demande,a.id_avance";
        List<Map<String,Object>> items = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal restant = rs.getBigDecimal("solde_avant_campagne");
                    BigDecimal echeance = AdvanceRepaymentPolicy.echeance(rs.getBigDecimal("montant_initial"),
                            rs.getInt("nombre_mensualites"), restant);
                    items.add(Map.of("idAvance",rs.getInt("id_avance"),"idEmploye",rs.getInt("id_employe"),
                            "matricule",rs.getString("matricule"),"nomComplet",rs.getString("nom_complet"),
                            "montantInitial",rs.getBigDecimal("montant_initial"),"soldeRestant",restant,
                            "nombreMensualites",rs.getInt("nombre_mensualites"),"echeancePrevue",echeance,
                            "retenueChoisie",rs.getBigDecimal("retenue_choisie") != null ? rs.getBigDecimal("retenue_choisie") : echeance,
                            "motifChoix",rs.getString("motif_choix") != null ? rs.getString("motif_choix") : ""));
                }
            }
        }
        return items;
    }

    @PutMapping("/{idAvance}/retenues/{idPeriode}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> choisirRetenue(@PathVariable int idAvance, @PathVariable int idPeriode,
            @RequestBody RetenueChoix request, Authentication auth) throws Exception {
        periods.requirePayrollAction(idPeriode, PayrollAction.ADD_VARIABLE, PayrollPeriodUserContext.from(auth));
        if (request.montant() == null || request.montant().signum() < 0
                || request.motif() == null || request.motif().trim().length() < 3)
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Montant non négatif et motif de trois caractères requis.");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Même ordre de verrouillage que le calcul : période puis mouvement.
                try (PreparedStatement lock = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE id_periode_paie=? FOR UPDATE")) {
                    lock.setInt(1, idPeriode);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Période de paie introuvable.");
                    }
                }
                BigDecimal restant;
                try (PreparedStatement ps = c.prepareStatement("SELECT a.solde_restant + COALESCE((SELECT SUM(i.montant) "
                        + "FROM paie_mouvement_imputation i JOIN bulletin_paie b ON b.id_bulletin=i.id_bulletin "
                        + "WHERE i.id_avance=a.id_avance AND b.id_periode_paie=cible.id_periode_paie),0) "
                        + "FROM paie_avance_acompte a "
                        + "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement=a.id_type_mouvement "
                        + "JOIN periode_paie origine ON origine.id_periode_paie=a.id_periode_paie "
                        + "JOIN periode_paie cible ON cible.id_periode_paie=? "
                        + "WHERE a.id_avance=? AND t.categorie='AVANCE' AND a.statut IN ('APPROUVE','IMPUTE_PAIE') "
                        + "AND a.versement_confirme_at IS NOT NULL AND a.date_paiement<=cible.date_fin "
                        + "AND origine.date_debut<=cible.date_debut FOR UPDATE OF a")) {
                    ps.setInt(1, idPeriode); ps.setInt(2, idAvance);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Avance non éligible pour cette paie.");
                        restant = rs.getBigDecimal(1);
                    }
                }
                if (request.montant().compareTo(restant) > 0)
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La retenue dépasse le reste dû.");
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO paie_avance_retenue_choix "
                        + "(id_avance,id_periode_paie,montant,motif,choisi_par) VALUES (?,?,?,?,?) "
                        + "ON CONFLICT (id_avance,id_periode_paie) DO UPDATE SET montant=EXCLUDED.montant,motif=EXCLUDED.motif,"
                        + "choisi_par=EXCLUDED.choisi_par,updated_at=NOW()")) {
                    ps.setInt(1,idAvance); ps.setInt(2,idPeriode); ps.setBigDecimal(3,request.montant());
                    ps.setString(4,request.motif().trim()); ps.setInt(5,actor(auth)); ps.executeUpdate();
                }
                AuditLog.audit(c,"CHOIX_RETENUE_AVANCE","paie_avance_retenue_choix",idAvance+":"+idPeriode,null,request);
                c.commit();
                return ResponseEntity.ok(Map.of("idAvance",idAvance,"idPeriode",idPeriode,"montant",request.montant()));
            } catch (Exception ex) { c.rollback(); throw ex; }
        }
    }

    private int actor(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails user) return user.getIdUtilisateur();
        throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Utilisateur non identifié.");
    }
}
