package mg.galaxam.sirh.model.contrat.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Moteur de calcul de l'ancienneté légale et contractuelle (Code du Travail malgache).
 * Source de vérité unique pour :
 * - Le décompte de l'ancienneté effective totale (cumul des contrats successifs sans interruption).
 * - L'ancienneté spécifique au contrat en cours.
 * - Le formatage textuel officiel ("X ans Y mois").
 * - L'évaluation en mémoire (sans N+1 SQL).
 */
@Component
public class AncienneteEngine {

    public record IntervalleContrat(
            Integer idContrat,
            LocalDate dateDebut,
            LocalDate dateFin,
            Integer idContratPrecedent
    ) {}

    public record AncienneteBilan(
            double anneesEffectives,
            int moisEffectifs,
            int ansEffectifs,
            int moisRestantsEffectifs,
            String ancienneteEffectiveTexte,
            double anneesContratActuel,
            int moisContratActuel,
            String ancienneteContratTexte,
            int nombreContratsChaine
    ) {
        public static AncienneteBilan vide() {
            return new AncienneteBilan(0.0, 0, 0, 0, "—", 0.0, 0, "—", 0);
        }
    }

    /**
     * Calcul métier pur en mémoire à partir d'une liste d'intervalles contractuels.
     * Reconstitue la chaîne via id_contrat_precedent et calcule l'ancienneté totale et du contrat en cours.
     *
     * @param contrats Liste des contrats du salarié
     * @param idContratCible Identifiant du contrat actif à évaluer
     * @param dateReference Date de référence (généralement LocalDate.now())
     * @return Bilan complet d'ancienneté
     */
    public AncienneteBilan evaluerAnciennete(
            List<IntervalleContrat> contrats,
            Integer idContratCible,
            LocalDate dateReference
    ) {
        if (contrats == null || contrats.isEmpty() || idContratCible == null) {
            return AncienneteBilan.vide();
        }

        LocalDate refDate = (dateReference != null) ? dateReference : LocalDate.now();

        // Indexation par id_contrat pour navigation rapide O(1)
        Map<Integer, IntervalleContrat> contratMap = new HashMap<>();
        for (IntervalleContrat c : contrats) {
            if (c.idContrat() != null) {
                contratMap.put(c.idContrat(), c);
            }
        }

        IntervalleContrat cible = contratMap.get(idContratCible);
        if (cible == null || cible.dateDebut() == null) {
            return AncienneteBilan.vide();
        }

        // Accumulation précise des périodes calendaires
        int totalMoisCumul = 0;
        int joursRestantsCumul = 0;
        double totalJoursChaine = 0.0;
        double joursContratActuel = 0.0;
        int moisContratActuel = 0;
        int ansContratActuel = 0;
        int moisRestantsContratActuel = 0;
        int nbContrats = 0;

        Integer currentId = idContratCible;
        Set<Integer> visited = new HashSet<>();

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            IntervalleContrat c = contratMap.get(currentId);
            if (c == null || c.dateDebut() == null) {
                break;
            }

            LocalDate debut = c.dateDebut();
            LocalDate fin = (c.dateFin() != null) ? c.dateFin() : refDate;

            // Si la date de début est bien avant ou égale à la fin
            if (!debut.isAfter(fin)) {
                LocalDate finInclusive = fin.plusDays(1);
                long jours = Math.max(0, ChronoUnit.DAYS.between(debut, finInclusive));
                totalJoursChaine += jours;
                nbContrats++;

                java.time.Period p = java.time.Period.between(debut, finInclusive);
                int moisDuContrat = p.getYears() * 12 + p.getMonths();
                totalMoisCumul += moisDuContrat;
                joursRestantsCumul += p.getDays();

                if (currentId.equals(idContratCible)) {
                    joursContratActuel = jours;
                    moisContratActuel = moisDuContrat;
                    ansContratActuel = p.getYears();
                    moisRestantsContratActuel = p.getMonths();
                }
            }

            currentId = c.idContratPrecedent();
        }

        // Ajuster si les jours restants cumulés dépassent 30 jours
        if (joursRestantsCumul >= 30) {
            totalMoisCumul += (joursRestantsCumul / 30);
            joursRestantsCumul = joursRestantsCumul % 30;
        }

        int ansEff = totalMoisCumul / 12;
        int moisRestantsEff = totalMoisCumul % 12;
        double anneesEffectives = Math.round((totalJoursChaine / 365.25) * 100.0) / 100.0;
        String texteEff = formaterAncienneteTexte(ansEff, moisRestantsEff, totalJoursChaine);

        double anneesContrat = Math.round((joursContratActuel / 365.25) * 100.0) / 100.0;
        String texteCtr = formaterAncienneteTexte(ansContratActuel, moisRestantsContratActuel, joursContratActuel);

        return new AncienneteBilan(
                anneesEffectives,
                totalMoisCumul,
                ansEff,
                moisRestantsEff,
                texteEff,
                anneesContrat,
                moisContratActuel,
                texteCtr,
                nbContrats
        );
    }

    /**
     * Formate l'ancienneté sous forme textuelle conviviale ("X ans Y mois", "Y mois", "Moins d'un mois").
     */
    public String formaterAncienneteTexte(int ans, int mois, double totalJours) {
        if (totalJours <= 0) return "—";
        if (ans <= 0 && mois <= 0) return "Moins d'un mois";

        StringBuilder sb = new StringBuilder();
        if (ans > 0) {
            sb.append(ans).append(ans > 1 ? " ans" : " an");
        }
        if (mois > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(mois).append(" mois");
        }
        return sb.toString();
    }

    /**
     * Calcule l'ancienneté d'un contrat via la BDD en une seule requête ciblée
     * (élimine complètement la boucle N+1 de requêtes).
     */
    public AncienneteBilan calculerAnciennete(Integer idContrat, Connection c) {
        return calculerAnciennete(idContrat, LocalDate.now(), c);
    }

    /**
     * Calcule l'ancienneté continue pour un contrat donné à une date de référence arbitraire (ex: date de départ STC).
     */
    public AncienneteBilan calculerAnciennete(Integer idContrat, LocalDate dateReference, Connection c) {
        if (idContrat == null || c == null) {
            return AncienneteBilan.vide();
        }

        // Requête unique pour charger tous les contrats de l'employé rattaché
        String sql = "SELECT c.id_contrat, c.date_debut, c.date_fin, c.id_contrat_precedent " +
                     "FROM contrat_travail c " +
                     "WHERE c.id_employe = (SELECT ct.id_employe FROM contrat_travail ct WHERE ct.id_contrat = ?)";

        List<IntervalleContrat> contrats = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date dDeb = rs.getDate("date_debut");
                    java.sql.Date dFin = rs.getDate("date_fin");
                    Integer idPrec = rs.getObject("id_contrat_precedent") != null ? rs.getInt("id_contrat_precedent") : null;

                    contrats.add(new IntervalleContrat(
                            rs.getInt("id_contrat"),
                            dDeb != null ? dDeb.toLocalDate() : null,
                            dFin != null ? dFin.toLocalDate() : null,
                            idPrec
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur AncienneteEngine.calculerAnciennete : " + e.getMessage());
            return AncienneteBilan.vide();
        }

        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        return evaluerAnciennete(contrats, idContrat, ref);
    }

    /**
     * Calcule l'ancienneté continue pour un employé à une date de référence donnée.
     * Identifie son dernier contrat et évalue la chaîne complète.
     */
    public AncienneteBilan calculerAncienneteEmploye(Integer idEmploye, LocalDate dateReference, Connection c) {
        if (idEmploye == null || c == null) {
            return AncienneteBilan.vide();
        }
        String sqlContrat = "SELECT ct.id_contrat FROM contrat_travail ct " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "WHERE ct.id_employe = ? ORDER BY ct.date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlContrat)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int idContrat = rs.getInt("id_contrat");
                    return calculerAnciennete(idContrat, dateReference, c);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur AncienneteEngine.calculerAncienneteEmploye : " + e.getMessage());
        }
        return AncienneteBilan.vide();
    }

    /**
     * Calcule l'ancienneté en batch pour une collection de contrats (0 N+1).
     */
    public Map<Integer, AncienneteBilan> calculerAncienneteBatch(Set<Integer> idContrats, Connection c) {
        if (idContrats == null || idContrats.isEmpty() || c == null) {
            return Collections.emptyMap();
        }

        Map<Integer, AncienneteBilan> resultats = new HashMap<>();

        // 1. Récupérer les id_employe distincts concernés
        String inSql = String.join(",", Collections.nCopies(idContrats.size(), "?"));
        String sql = "SELECT id_contrat, id_employe, date_debut, date_fin, id_contrat_precedent " +
                     "FROM contrat_travail " +
                     "WHERE id_employe IN (SELECT DISTINCT id_employe FROM contrat_travail WHERE id_contrat IN (" + inSql + "))";

        Map<Integer, List<IntervalleContrat>> contratsParEmploye = new HashMap<>();
        Map<Integer, Integer> employeParContrat = new HashMap<>();

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int idx = 1;
            for (Integer id : idContrats) {
                ps.setInt(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idCtr = rs.getInt("id_contrat");
                    int idEmp = rs.getInt("id_employe");
                    java.sql.Date dDeb = rs.getDate("date_debut");
                    java.sql.Date dFin = rs.getDate("date_fin");
                    Integer idPrec = rs.getObject("id_contrat_precedent") != null ? rs.getInt("id_contrat_precedent") : null;

                    IntervalleContrat ic = new IntervalleContrat(
                            idCtr,
                            dDeb != null ? dDeb.toLocalDate() : null,
                            dFin != null ? dFin.toLocalDate() : null,
                            idPrec
                    );

                    contratsParEmploye.computeIfAbsent(idEmp, k -> new ArrayList<>()).add(ic);
                    employeParContrat.put(idCtr, idEmp);
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur AncienneteEngine.calculerAncienneteBatch : " + e.getMessage());
        }

        LocalDate now = LocalDate.now();
        for (Integer idCtr : idContrats) {
            Integer idEmp = employeParContrat.get(idCtr);
            List<IntervalleContrat> list = (idEmp != null) ? contratsParEmploye.get(idEmp) : Collections.emptyList();
            resultats.put(idCtr, evaluerAnciennete(list, idCtr, now));
        }

        return resultats;
    }
}
