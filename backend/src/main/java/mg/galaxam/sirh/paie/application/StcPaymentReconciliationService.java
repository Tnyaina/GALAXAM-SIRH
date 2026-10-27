package mg.galaxam.sirh.paie.application;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Une écriture STC est la source du paiement ; sa part mensuelle est une affectation liée. */
@Service
public class StcPaymentReconciliationService {
    private static SirhBusinessException invalid(String message) {
        return new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, message);
    }

    public void affecter(Connection c, int idStc, int idReglement, BigDecimal partMensuelle,
                         PaymentEvidence preuve, Integer userId) throws SQLException {
        BigDecimal part = partMensuelle == null ? BigDecimal.ZERO : partMensuelle;
        if (part.signum() < 0) throw invalid("La part affectée au bulletin ne peut pas être négative.");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.id_bulletin_mensuel,s.solde_bulletin_a_regler,s.statut,r.montant_regle,r.mode_reglement,"
                + "r.date_reglement,r.reference_reglement,r.est_annule,r.nom_justificatif,r.mime_justificatif,r.contenu_justificatif "
                + "FROM paie_stc s JOIN paie_stc_reglement r ON r.id_stc=s.id_stc "
                + "WHERE s.id_stc=? AND r.id_reglement=? FOR UPDATE OF s,r")) {
            ps.setInt(1, idStc);
            ps.setInt(2, idReglement);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getBoolean("est_annule")) throw invalid("Règlement STC actif introuvable.");
                if (preuve == null && rs.getBytes("contenu_justificatif") != null)
                    preuve = new PaymentEvidence(rs.getString("nom_justificatif"), rs.getString("mime_justificatif"), rs.getBytes("contenu_justificatif"));
                if (preuve == null) throw invalid("Un justificatif réel du versement STC est obligatoire.");
                BigDecimal montant = rs.getBigDecimal("montant_regle");
                if (part.compareTo(montant) > 0) throw invalid("La part mensuelle dépasse le montant du versement STC.");
                byte[] preuveExistante = rs.getBytes("contenu_justificatif");
                if (preuveExistante != null && !java.util.Arrays.equals(preuveExistante, preuve.bytes()))
                    throw invalid("Ce versement possède déjà un autre justificatif. Une correction documentée est nécessaire.");
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE paie_stc_reglement SET nom_justificatif=?,mime_justificatif=?,contenu_justificatif=?,taille_justificatif=? WHERE id_reglement=?")) {
                    update.setString(1, preuve.name());
                    update.setString(2, preuve.mime());
                    update.setBytes(3, preuve.bytes());
                    update.setInt(4, preuve.bytes().length);
                    update.setInt(5, idReglement);
                    update.executeUpdate();
                }
                try (PreparedStatement existing = c.prepareStatement(
                        "SELECT montant FROM paie_bulletin_reglement WHERE id_stc_reglement=? AND annule_at IS NULL")) {
                    existing.setInt(1, idReglement);
                    try (ResultSet row = existing.executeQuery()) {
                        if (row.next()) {
                            if (row.getBigDecimal(1).compareTo(part) == 0) return;
                            throw invalid("Ce versement STC est déjà affecté au bulletin pour un autre montant.");
                        }
                    }
                }
                if (part.signum() == 0) return;
                Integer idBulletin = (Integer) rs.getObject("id_bulletin_mensuel");
                if (idBulletin == null) throw invalid("Ce STC ne reprend aucun bulletin mensuel.");
                if (!Set.of("VIREMENT", "CHEQUE", "ESPECES", "MOBILE_MONEY").contains(rs.getString("mode_reglement")))
                    throw invalid("Mode de paiement STC incompatible avec le registre mensuel.");
                BigDecimal plafond = rs.getBigDecimal("solde_bulletin_a_regler");
                if (plafond == null || plafond.signum() <= 0) throw invalid("Aucun solde mensuel n'est repris dans ce STC.");
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT net_a_payer,type_bulletin,statut FROM bulletin_paie WHERE id_bulletin=? FOR UPDATE")) {
                    lock.setInt(1, idBulletin);
                    try (ResultSet b = lock.executeQuery()) {
                        if (!b.next() || !"MENSUEL".equals(b.getString("type_bulletin"))
                                || !Set.of("VALIDE", "CLOTURE").contains(b.getString("statut")))
                            throw invalid("Le bulletin mensuel lié n'est pas réglable.");
                        BigDecimal net = b.getBigDecimal("net_a_payer");
                        BigDecimal dejaAffecte;
                        try (PreparedStatement sum = c.prepareStatement(
                                "SELECT COALESCE(SUM(br.montant),0) FROM paie_bulletin_reglement br "
                                + "JOIN paie_stc_reglement sr ON sr.id_reglement=br.id_stc_reglement "
                                + "WHERE sr.id_stc=? AND br.annule_at IS NULL")) {
                            sum.setInt(1, idStc);
                            try (ResultSet s = sum.executeQuery()) { s.next(); dejaAffecte = s.getBigDecimal(1); }
                        }
                        if (dejaAffecte.add(part).compareTo(plafond) > 0)
                            throw invalid("La part cumulée dépasse le solde mensuel figé dans le STC.");
                        BigDecimal dejaPaye;
                        try (PreparedStatement sum = c.prepareStatement(
                                "SELECT COALESCE(SUM(montant),0) FROM paie_bulletin_reglement WHERE id_bulletin=? AND annule_at IS NULL")) {
                            sum.setInt(1, idBulletin);
                            try (ResultSet s = sum.executeQuery()) { s.next(); dejaPaye = s.getBigDecimal(1); }
                        }
                        if (dejaPaye.add(part).compareTo(net) > 0)
                            throw invalid("La part mensuelle dépasserait le solde du bulletin.");
                    }
                }
                try (PreparedStatement pending = c.prepareStatement(
                        "SELECT 1 FROM paie_bulletin_virement_ligne WHERE id_bulletin=? AND statut='EN_ATTENTE' LIMIT 1")) {
                    pending.setInt(1, idBulletin);
                    try (ResultSet row = pending.executeQuery()) {
                        if (row.next()) throw invalid("Ce bulletin figure dans un lot de virements en attente.");
                    }
                }
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO paie_bulletin_reglement(id_bulletin,id_stc_reglement,montant,mode_reglement,date_reglement,"
                        + "reference_reglement,commentaire,nom_justificatif,mime_justificatif,contenu_justificatif,taille_justificatif,cree_par) "
                        + "VALUES (?,?,?,?,? ,?,'Affectation du versement STC',?,?,?,?,?)")) {
                    insert.setInt(1, idBulletin);
                    insert.setInt(2, idReglement);
                    insert.setBigDecimal(3, part);
                    insert.setString(4, rs.getString("mode_reglement"));
                    insert.setDate(5, rs.getDate("date_reglement"));
                    insert.setString(6, rs.getString("reference_reglement"));
                    insert.setString(7, preuve.name());
                    insert.setString(8, preuve.mime());
                    insert.setBytes(9, preuve.bytes());
                    insert.setInt(10, preuve.bytes().length);
                    if (userId == null) insert.setNull(11, java.sql.Types.INTEGER); else insert.setInt(11, userId);
                    insert.executeUpdate();
                }
            }
        }
    }

    public int idReglement(Connection c, int idStc, String idempotencyKey) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_reglement FROM paie_stc_reglement WHERE id_stc=? AND cle_idempotence=? "
                + "AND est_annule=FALSE ORDER BY id_reglement DESC LIMIT 1")) {
            ps.setInt(1, idStc);
            ps.setString(2, idempotencyKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw invalid("Règlement STC introuvable après enregistrement.");
                return rs.getInt(1);
            }
        }
    }
}
