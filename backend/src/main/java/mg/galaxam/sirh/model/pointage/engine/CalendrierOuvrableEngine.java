package mg.galaxam.sirh.model.pointage.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

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
            "WHERE f.date_ferie BETWEEN ? AND ? AND f.est_chome = TRUE " +
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
     * Détermine si une date donnée est un jour de week-end (Samedi ou Dimanche).
     */
    public static boolean isWeekend(LocalDate date) {
        if (date == null) return false;
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
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
        return (int) ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    /**
     * Calcule le montant journalier proratisé sur le nombre exact de jours ouvrables réels.
     */
    public double calculerProrataJournalier(double montantReference, int nbJoursOuvrables) {
        if (nbJoursOuvrables <= 0) return montantReference;
        return Math.round((montantReference / nbJoursOuvrables) * 100.0) / 100.0;
    }

    /**
     * Calcule le montant par trajet (aller ou retour = demi-journée).
     */
    public double calculerMontantTrajet(double montantJournalier) {
        return Math.round((montantJournalier / 2.0) * 100.0) / 100.0;
    }
}
