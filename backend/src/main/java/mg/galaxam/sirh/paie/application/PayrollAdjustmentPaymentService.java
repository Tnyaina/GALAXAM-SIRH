package mg.galaxam.sirh.paie.application;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.api.dto.ConfirmationVersementRequest;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.stereotype.Service;

@Service
public class PayrollAdjustmentPaymentService {
    private final DataSource dataSource;

    public PayrollAdjustmentPaymentService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean canConfirm(String categorie, String statut, Timestamp confirmedAt) {
        statut = mg.galaxam.sirh.paie.domain.PayrollStatuses.canonicalAdjustment(statut);
        return ("ACOMPTE".equals(categorie) || "AVANCE".equals(categorie))
                && confirmedAt == null && ("APPROUVE".equals(statut) || "IMPUTE_PAIE".equals(statut));
    }

    public record DossierView(String etat, Map<String, Boolean> actions) {}

    /** Même décision pour l'affichage du dossier et les commandes proposées au client. */
    public DossierView describe(String categorie, String statut, Timestamp confirmedAt,
                                BigDecimal montantImpute, BigDecimal soldeRestant, boolean canWrite) {
        statut = mg.galaxam.sirh.paie.domain.PayrollStatuses.canonicalAdjustment(statut);
        boolean verse = confirmedAt != null;
        boolean impute = montantImpute != null && montantImpute.signum() > 0;
        String etat = "EN_ATTENTE".equals(statut) ? "A_APPROUVER"
                : "REJETE".equals(statut) ? "REJETE"
                : "ANNULE".equals(statut) ? "ANNULE"
                : !verse && ("IMPUTE_PAIE".equals(statut) || impute) ? "VERSEMENT_A_VERIFIER"
                : !verse ? "A_VERSER"
                : soldeRestant != null && soldeRestant.signum() == 0 ? "SOLDE"
                : impute ? "REMBOURSEMENT_EN_COURS" : "VERSE_A_IMPUTER";
        return new DossierView(etat, Map.of(
                "APPROVE", canWrite && "EN_ATTENTE".equals(statut),
                "REJECT", canWrite && "EN_ATTENTE".equals(statut),
                "CONFIRM_VERSEMENT", canWrite && canConfirm(categorie, statut, confirmedAt)));
    }

    public void confirm(int id, ConfirmationVersementRequest request, Integer userId) throws Exception {
        if (request.dateVersement().isAfter(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today())) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "La date de versement ne peut pas être future.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "SELECT a.statut, a.date_demande, a.date_accord, a.versement_confirme_at, a.updated_at, "
                        + "t.categorie FROM paie_avance_acompte a LEFT JOIN ref_type_mouvement_paie t "
                        + "ON t.id_type_mouvement = a.id_type_mouvement WHERE a.id_avance = ? FOR UPDATE OF a";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Demande introuvable.");
                        if (!canConfirm(rs.getString("categorie"), rs.getString("statut"), rs.getTimestamp("versement_confirme_at"))) {
                            throw new SirhBusinessException(SirhErrorCode.STATE_CONFLICT,
                                    "Seul un acompte ou une avance approuvé, ou un dossier historique imputé, peut être rapproché d'un versement.");
                        }
                        Timestamp updated = rs.getTimestamp("updated_at");
                        int version = updated == null ? 1 : (int) (updated.getTime() % Integer.MAX_VALUE);
                        if (request.expectedVersion() < 1 || request.expectedVersion() != version) {
                            throw new SirhBusinessException(SirhErrorCode.PAYROLL_ADJUSTMENT_VERSION_CONFLICT,
                                    "La demande a changé. Rechargez-la avant de confirmer le versement.");
                        }
                        Date accord = rs.getDate("date_accord");
                        Date demande = rs.getDate("date_demande");
                        Date plancher = accord != null ? accord : demande;
                        if (plancher != null && request.dateVersement().isBefore(plancher.toLocalDate())) {
                            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                    "Le versement ne peut pas précéder l'approbation.");
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM ref_mode_paiement WHERE code = ? AND actif = TRUE")) {
                    ps.setString(1, request.modeReglement());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                "Mode de règlement inconnu ou inactif.");
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE paie_avance_acompte SET date_paiement = ?, mode_reglement = ?, "
                                + "reference_versement = ?, versement_confirme_at = CURRENT_TIMESTAMP, "
                                + "versement_confirme_par = ?, "
                                + "statut = CASE WHEN solde_restant > 0 THEN 'APPROUVE' ELSE 'IMPUTE_PAIE' END, "
                                + "updated_at = CURRENT_TIMESTAMP WHERE id_avance = ?")) {
                    ps.setDate(1, Date.valueOf(request.dateVersement()));
                    ps.setString(2, request.modeReglement());
                    ps.setString(3, request.referenceVersement().trim());
                    if (userId != null) ps.setInt(4, userId);
                    else ps.setNull(4, java.sql.Types.INTEGER);
                    ps.setInt(5, id);
                    ps.executeUpdate();
                }
                AuditLog.audit(c, "CONFIRM_VERSEMENT", "paie_avance_acompte", String.valueOf(id), null, request);
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }
}
