package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.domain.PrestataireFacturePolicy;
import org.springframework.stereotype.Service;

/** Commandes et règles de cycle de vie des factures prestataires. */
@Service
public class PrestataireFactureService {
    private final DataSource dataSource;

    public PrestataireFactureService(DataSource dataSource) { this.dataSource = dataSource; }

    public Map<String, Integer> create(Integer contractId, String reference, LocalDate invoiceDate, BigDecimal amount) throws Exception {
        if (contractId == null || blank(reference) || invoiceDate == null || amount == null || amount.signum() <= 0)
            throw new IllegalArgumentException("Contrat, référence, date et montant positif sont requis.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO prestataire_facture (id_contrat_prestataire,reference,date_facture,montant) "
                + "SELECT pc.id_contrat_prestataire,?,?,? FROM prestataire_contrat pc "
                + "JOIN prestataire_independant pi ON pi.id_prestataire=pc.id_prestataire "
                + "WHERE pc.id_contrat_prestataire=? AND pi.est_actif=TRUE", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reference.trim()); ps.setDate(2, Date.valueOf(invoiceDate)); ps.setBigDecimal(3, amount); ps.setInt(4, contractId);
            if (ps.executeUpdate() != 1) throw new IllegalArgumentException("Contrat absent ou prestataire inactif.");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Identifiant de facture non retourné.");
                return Map.of("idFacturePrestataire", keys.getInt(1));
            }
        }
    }

    public void cancelDraft(int id, String reason, Integer userId) throws Exception {
        if (blank(reason)) throw new IllegalArgumentException("Le motif d'annulation est obligatoire.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET statut='ANNULEE', motif_annulation=?, annule_par=?, date_annulation=CURRENT_TIMESTAMP "
                + "WHERE id_facture_prestataire=? AND statut='BROUILLON'")) {
            ps.setString(1, reason.trim()); ps.setObject(2, userId); ps.setInt(3, id);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Seul un brouillon de facture peut être annulé.");
        }
    }

    public void validate(int id, Integer userId) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET statut='VALIDEE', valide_par=?, date_validation=CURRENT_TIMESTAMP "
                + "WHERE id_facture_prestataire=? AND statut='BROUILLON' AND chemin_piece IS NOT NULL "
                + "AND EXISTS (SELECT 1 FROM prestataire_contrat pc JOIN prestataire_independant pi "
                + "ON pi.id_prestataire=pc.id_prestataire WHERE pc.id_contrat_prestataire=prestataire_facture.id_contrat_prestataire "
                + "AND pc.chemin_piece IS NOT NULL AND pi.est_actif=TRUE AND pi.nif IS NOT NULL AND pi.nif<>'')")) {
            ps.setObject(1, userId); ps.setInt(2, id);
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Validation impossible : contrôlez le NIF, le contrat PDF et la facture PDF.");
        }
    }

    public void pay(int id, LocalDate paymentDate, String reference, Integer userId) throws Exception {
        if (paymentDate == null || blank(reference) || paymentDate.isAfter(mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today()))
            throw new IllegalArgumentException("Date réelle et référence du paiement obligatoires.");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE prestataire_facture SET statut='PAYEE', date_paiement=?, reference_paiement=?, paye_par=? "
                + "WHERE id_facture_prestataire=? AND statut='VALIDEE' AND date_facture<=?")) {
            ps.setDate(1, Date.valueOf(paymentDate)); ps.setString(2, reference.trim()); ps.setObject(3, userId);
            ps.setInt(4, id); ps.setDate(5, Date.valueOf(paymentDate));
            if (ps.executeUpdate() != 1) throw new IllegalStateException("Facture absente, non validée ou paiement antérieur à la facture.");
        }
    }

    public PrestataireFacturePolicy.Eligibility eligibility(String status, boolean invoicePdf, boolean contractPdf,
            boolean providerNif, boolean providerActive) {
        return PrestataireFacturePolicy.evaluate(status, invoicePdf, contractPdf, providerNif, providerActive);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
