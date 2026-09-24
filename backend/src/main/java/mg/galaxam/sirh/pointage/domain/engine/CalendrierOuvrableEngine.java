package mg.galaxam.sirh.pointage.domain.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import mg.galaxam.sirh.common.dto.BusinessIssue;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;

/**
 * Moteur autoritaire et centralisé de calcul du calendrier de paie et de pointage.
 * Garantit l'uniformité absolue des règles de gestion des jours ouvrables, fériés et proratas.
 *
 * Règle d'or SIRH :
 * Jours Ouvrables Réels = Jours du Lundi au Vendredi - (Jours Fériés Chômés + Périodes Exceptionnelles Chômées)
 */
@Component
public class CalendrierOuvrableEngine {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CalendrierOuvrableEngine.class);

    /**
     * Récupère l'ensemble des dates fériées ou exceptionnellement chômées pour une période et un site.
     */
    public Set<LocalDate> getDatesFeriesEtChomees(LocalDate debut, LocalDate fin, Integer idSite, Connection c) {
        Set<LocalDate> datesChomees = new HashSet<>();
        if (debut == null || fin == null || c == null) return datesChomees;

        // 1. Jours fériés chômés légaux (nationaux ou spécifiques au site)
        StringBuilder sqlFeries = new StringBuilder(
            "SELECT f.date_ferie FROM ref_calendrier_ferie f " +
            "LEFT JOIN site_calendrier_ferie scf ON f.id_ferie = scf.id_ferie "
        );
        if (idSite != null && idSite > 0) {
            sqlFeries.append("AND scf.id_site = ? ");
        }
        sqlFeries.append(
            "WHERE f.date_ferie BETWEEN ? AND ? AND f.est_chome = TRUE AND f.est_actif IS NOT FALSE " +
            "  AND (f.est_national = TRUE "
        );
        if (idSite != null && idSite > 0) {
            sqlFeries.append("OR scf.id_site IS NOT NULL");
        }
        sqlFeries.append(")");

        try (PreparedStatement ps = c.prepareStatement(sqlFeries.toString())) {
            int idx = 1;
            if (idSite != null && idSite > 0) {
                ps.setInt(idx++, idSite);
            }
            ps.setDate(idx++, java.sql.Date.valueOf(debut));
            ps.setDate(idx++, java.sql.Date.valueOf(fin));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date d = rs.getDate("date_ferie");
                    if (d != null) datesChomees.add(d.toLocalDate());
                }
            }
        } catch (Exception e) {
            log.warn("[CalendrierOuvrableEngine] Impossible de récupérer les jours fériés pour le site {}: {}", idSite, e.getMessage(), e);
        }

        // 2. Périodes exceptionnellement chômées (intempéries, cyclone, fermeture administrative)
        StringBuilder sqlChomee = new StringBuilder(
            "SELECT date_debut, COALESCE(date_fin, date_debut) AS date_fin FROM ref_periode_chomee " +
            "WHERE date_debut <= ? AND COALESCE(date_fin, date_debut) >= ? "
        );
        if (idSite != null && idSite > 0) {
            sqlChomee.append("AND (id_site IS NULL OR id_site = ?) ");
        } else {
            sqlChomee.append("AND id_site IS NULL ");
        }

        try (PreparedStatement ps = c.prepareStatement(sqlChomee.toString())) {
            ps.setDate(1, java.sql.Date.valueOf(fin));
            ps.setDate(2, java.sql.Date.valueOf(debut));
            if (idSite != null && idSite > 0) {
                ps.setInt(3, idSite);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date dStart = rs.getDate("date_debut");
                    java.sql.Date dEnd = rs.getDate("date_fin");
                    if (dStart != null && dEnd != null) {
                        LocalDate start = dStart.toLocalDate().isBefore(debut) ? debut : dStart.toLocalDate();
                        LocalDate end = dEnd.toLocalDate().isAfter(fin) ? fin : dEnd.toLocalDate();
                        for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1)) {
                            datesChomees.add(cur);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[CalendrierOuvrableEngine] Impossible de récupérer les périodes chômées pour le site {}: {}", idSite, e.getMessage(), e);
        }

        return datesChomees;
    }

    /**
     * Information sur le caractère chômé d'une date (férié ou période exceptionnelle).
     */
    public record JourChomeInfo(boolean estChome, String libelle) {
        public static final JourChomeInfo NON_CHOME = new JourChomeInfo(false, null);
    }

    /**
     * Détermine si une date donnée est chômée (férié chômé ou fermeture exceptionnelle) pour un site donné.
     */
    public JourChomeInfo getJourChomeInfo(LocalDate date, Integer idSite, Connection c) {
        if (date == null || c == null) return JourChomeInfo.NON_CHOME;

        // 1. Jour férié chômé légal (national ou spécifique au site)
        StringBuilder sqlFerie = new StringBuilder(
            "SELECT f.libelle FROM ref_calendrier_ferie f " +
            "LEFT JOIN site_calendrier_ferie scf ON f.id_ferie = scf.id_ferie "
        );
        if (idSite != null && idSite > 0) {
            sqlFerie.append("AND scf.id_site = ? ");
        }
        sqlFerie.append(
            "WHERE f.date_ferie = ? AND f.est_chome = TRUE AND f.est_actif IS NOT FALSE " +
            "  AND (f.est_national = TRUE "
        );
        if (idSite != null && idSite > 0) {
            sqlFerie.append("OR scf.id_site IS NOT NULL");
        }
        sqlFerie.append(") LIMIT 1");

        try (PreparedStatement ps = c.prepareStatement(sqlFerie.toString())) {
            int idx = 1;
            if (idSite != null && idSite > 0) {
                ps.setInt(idx++, idSite);
            }
            ps.setDate(idx++, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new JourChomeInfo(true, rs.getString("libelle"));
                }
            }
        } catch (Exception e) {
            log.warn("[CalendrierOuvrableEngine] Erreur recherche férié pour date {}: {}", date, e.getMessage());
        }

        // 2. Période chômée exceptionnelle
        StringBuilder sqlChomee = new StringBuilder(
            "SELECT libelle FROM ref_periode_chomee " +
            "WHERE ? BETWEEN date_debut AND COALESCE(date_fin, date_debut) "
        );
        if (idSite != null && idSite > 0) {
            sqlChomee.append("AND (id_site IS NULL OR id_site = ?) ");
        } else {
            sqlChomee.append("AND id_site IS NULL ");
        }
        sqlChomee.append("LIMIT 1");

        try (PreparedStatement ps = c.prepareStatement(sqlChomee.toString())) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            if (idSite != null && idSite > 0) {
                ps.setInt(2, idSite);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new JourChomeInfo(true, rs.getString("libelle"));
                }
            }
        } catch (Exception e) {
            log.warn("[CalendrierOuvrableEngine] Erreur recherche période chômée pour date {}: {}", date, e.getMessage());
        }

        return JourChomeInfo.NON_CHOME;
    }

    /**
     * Récupère l'ensemble des dates fériées ou exceptionnellement chômées pour un employé en fonction de son site actif.
     */
    public Set<LocalDate> getDatesFeriesEtChomeesEmploye(LocalDate debut, LocalDate fin, Integer idEmploye, Connection c) {
        if (debut == null || fin == null || fin.isBefore(debut) || idEmploye == null || c == null) {
            return Set.of();
        }
        Map<Integer, Set<LocalDate>> chomesParSite = new HashMap<>();
        Set<LocalDate> result = new HashSet<>();
        for (LocalDate date = debut; !date.isAfter(fin); date = date.plusDays(1)) {
            Integer idSite = resolveSiteAt(idEmploye, date, c);
            Set<LocalDate> chomes = chomesParSite.computeIfAbsent(
                    idSite, site -> getDatesFeriesEtChomees(debut, fin, site, c));
            if (chomes.contains(date)) result.add(date);
        }
        return result;
    }

    /** Résout l'unique site applicable à une date métier, sans fallback « dernier id ». */
    public Integer resolveSiteAt(Integer idEmploye, LocalDate targetDate, Connection c) {
        if (idEmploye == null || targetDate == null || c == null) {
            throw affectationInvalide(idEmploye, targetDate, "AFFECTATION_CONTEXT_REQUIRED");
        }
        String sql = "SELECT id_site FROM employe_affectation_carriere "
                + "WHERE id_employe = ? AND date_debut <= ? "
                + "AND (date_fin IS NULL OR date_fin >= ?) "
                + "ORDER BY date_debut DESC, id_affectation DESC LIMIT 2";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, java.sql.Date.valueOf(targetDate));
            ps.setDate(3, java.sql.Date.valueOf(targetDate));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw affectationInvalide(idEmploye, targetDate, "AFFECTATION_GAP");
                int idSite = rs.getInt("id_site");
                if (rs.wasNull()) throw affectationInvalide(idEmploye, targetDate, "AFFECTATION_SITE_MISSING");
                if (rs.next()) throw affectationInvalide(idEmploye, targetDate, "AFFECTATION_OVERLAP");
                return idSite;
            }
        } catch (SirhBusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de résoudre l'affectation datée.", e);
        }
    }

    private static SirhBusinessException affectationInvalide(
            Integer idEmploye, LocalDate targetDate, String issueCode) {
        Map<String, Object> details = new HashMap<>();
        if (idEmploye != null) details.put("employeeId", idEmploye);
        if (targetDate != null) details.put("targetDate", targetDate.toString());
        details.put("configurationType", "AFFECTATION_SITE");
        BusinessIssue issue = new BusinessIssue(
                issueCode,
                "Une affectation unique avec site est obligatoire à la date métier.",
                details);
        return new SirhBusinessException(
                SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                issue.message(), details, java.util.List.of(issue));
    }

    /**
     * Détermine si une date donnée est un jour de week-end (Samedi ou Dimanche).
     */
    public static boolean isWeekend(LocalDate date) {
        if (date == null) return false;
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * Calcule le nombre de jours ouvrables (du lundi au vendredi) hors week-end.
     */
    public static int calculerJoursOuvrables(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return 0;
        int count = 0;
        for (LocalDate cur = debut; !cur.isAfter(fin); cur = cur.plusDays(1)) {
            if (!isWeekend(cur)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Calcule le nombre exact de jours ouvrables réels (Lundi au Vendredi hors jours chômés).
     */
    public int compterJoursOuvrables(LocalDate debut, LocalDate fin, Integer idSite, Connection c) {
        if (debut == null || fin == null) return 0;
        Set<LocalDate> feries = getDatesFeriesEtChomees(debut, fin, idSite, c);

        int count = 0;
        for (LocalDate cur = debut; !cur.isAfter(fin); cur = cur.plusDays(1)) {
            boolean weekend = isWeekend(cur);
            boolean isChome = feries.contains(cur);
            if (!weekend && !isChome) {
                count++;
            }
        }
        return count;
    }

    /**
     * Compte les jours fériés chômés tombant sur la période (entre lundi et vendredi ou total).
     */
    public int compterJoursFeriesChomes(LocalDate debut, LocalDate fin, Integer idSite, Connection c) {
        if (debut == null || fin == null) return 0;
        Set<LocalDate> feries = getDatesFeriesEtChomees(debut, fin, idSite, c);
        return feries.size();
    }

    /**
     * Calcule le nombre total de jours calendaires d'une période.
     */
    public int compterJoursCalendaires(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return 0;
        if (fin.isBefore(debut)) return 0;
        return (int) ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    /**
     * Calcule le montant journalier proratisé sur le nombre exact de jours ouvrables réels.
     */
    public BigDecimal calculerProrataJournalier(BigDecimal montantReference, int nbJoursOuvrables) {
        if (montantReference == null) return BigDecimal.ZERO;
        if (nbJoursOuvrables <= 0) {
            if (montantReference.signum() == 0) return BigDecimal.ZERO;
            throw new IllegalArgumentException("MISSING_WORKING_CALENDAR: impossible de proratiser sans jours ouvrables.");
        }
        return MoneyRoundingPolicy.amount(montantReference.divide(BigDecimal.valueOf(nbJoursOuvrables), 8, java.math.RoundingMode.HALF_UP));
    }

    /**
     * Calcule le montant par trajet (aller ou retour = demi-journée).
     */
    public BigDecimal calculerMontantTrajet(BigDecimal montantJournalier) {
        return MoneyRoundingPolicy.amount(montantJournalier.divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP));
    }
}
