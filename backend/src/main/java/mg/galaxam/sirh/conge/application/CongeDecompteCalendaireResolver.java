package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;

/** Décompte des seuls congés débitant le solde acquis, selon la règle datée de chaque jour. */
public final class CongeDecompteCalendaireResolver {
    public static final String PARAMETRE = "CONGE_DECOMPTE_FERIES";
    private static final CalendrierOuvrableEngine CALENDRIER = new CalendrierOuvrableEngine();

    private CongeDecompteCalendaireResolver() {}

    public static BigDecimal calculer(int employe, LocalDate debut, LocalDate fin,
            boolean demiJournee, Connection c) throws SQLException {
        if (debut == null || fin == null || fin.isBefore(debut))
            throw new IllegalArgumentException("Dates du congé invalides.");
        if (demiJournee && !debut.equals(fin))
            throw new IllegalArgumentException("Une demi-journée ne peut couvrir plusieurs dates.");
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
            var resolution = ParametreGlobal.resolve(
                    ParametreGlobal.loadEtatMapAtDate(Date.valueOf(jour), c), PARAMETRE);
            if (resolution.isMissing()) throw new IllegalStateException(
                    "CONFIGURATION_MISSING: " + PARAMETRE + " au " + jour
                            + " (aucune version applicable ; aucune valeur implicite)");
            if (resolution.isDisabled()) throw new IllegalStateException(
                    "CONFIGURATION_DISABLED: " + PARAMETRE + " au " + jour
                            + " (désactivation explicite ; le décompte ne peut pas être calculé)");
            String valeur = resolution.valeur();
            if (!"INCLUS".equals(valeur) && !"EXCLUS".equals(valeur))
                throw new IllegalStateException("CONFIGURATION_INVALID: " + PARAMETRE
                        + "=" + valeur + " au " + jour);
            if ("EXCLUS".equals(valeur) && estFerie(employe, jour, c)) continue;
            total = total.add(demiJournee ? new BigDecimal("0.5") : BigDecimal.ONE);
        }
        return total.setScale(1);
    }

    private static boolean estFerie(int employe, LocalDate jour, Connection c) throws SQLException {
        Integer site = CALENDRIER.resolveSiteAtOrNull(employe, jour, c);
        if (site == null) throw new IllegalStateException(
                "AFFECTATION_SITE_MISSING: site de l'employé " + employe + " au " + jour);
        String sql = "SELECT EXISTS (SELECT 1 FROM ref_calendrier_ferie f "
                + "WHERE f.date_ferie=? AND f.est_actif IS NOT FALSE AND "
                + "(f.est_national=TRUE OR EXISTS (SELECT 1 FROM site_calendrier_ferie sf "
                + "WHERE sf.id_ferie=f.id_ferie AND sf.id_site=?)))";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(jour));
            ps.setInt(2, site);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }
}
