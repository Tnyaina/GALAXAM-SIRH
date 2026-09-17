package mg.galaxam.sirh.unit.pointage;

import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Contre-Audit Ciblé : Algorithme de découpage des semaines (26 M-1 -> 25 M)")
public class PeriodSplittingAnalysisTest {

    record TronconInfo(int semNum, LocalDate debutTroncon, LocalDate finTroncon, int nbOuvrables, double seuilHeures) {}

    /**
     * Implémentation exacte de l'algorithme de PointageSyntheseService.getSemainesPeriode (lignes 1226-1253)
     */
    private List<TronconInfo> simulerDecoupageGetSemainesPeriode(LocalDate dateDebut, LocalDate dateFin) {
        List<TronconInfo> result = new ArrayList<>();
        LocalDate cur = dateDebut;
        int semNum = 1;
        while (!cur.isAfter(dateFin)) {
            LocalDate debutSemaine = cur.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate finSemaine = cur.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            if (CalendrierOuvrableEngine.isWeekend(cur) && finSemaine.plusDays(7).isBefore(dateFin.plusDays(7))) {
                cur = cur.plusDays(1);
                continue;
            }

            LocalDate dateDebutTroncon = cur.isBefore(debutSemaine) ? debutSemaine : cur;
            if (dateDebutTroncon.isBefore(dateDebut)) dateDebutTroncon = dateDebut;

            LocalDate dateFinTroncon = finSemaine.isAfter(dateFin) ? dateFin : finSemaine;

            int nbOuvrables = 0;
            for (LocalDate d = dateDebutTroncon; !d.isAfter(dateFinTroncon); d = d.plusDays(1)) {
                if (!CalendrierOuvrableEngine.isWeekend(d)) {
                    nbOuvrables++;
                }
            }
            double seuilHeures = nbOuvrables * 8.0;
            result.add(new TronconInfo(semNum++, dateDebutTroncon, dateFinTroncon, nbOuvrables, seuilHeures));
            cur = dateFinTroncon.plusDays(1);
        }
        return result;
    }

    /**
     * Implémentation exacte du découpage dans calculerSyntheseEmploye (lignes 326-329 & 472-477)
     */
    private List<TronconInfo> simulerDecoupageCalculerSyntheseEmploye(LocalDate dateDebut, LocalDate dateFin) {
        Map<LocalDate, List<LocalDate>> parSemaine = new LinkedHashMap<>();
        for (LocalDate day = dateDebut; !day.isAfter(dateFin); day = day.plusDays(1)) {
            LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            parSemaine.computeIfAbsent(monday, ignored -> new ArrayList<>()).add(day);
        }

        List<TronconInfo> result = new ArrayList<>();
        int semNum = 1;
        for (Map.Entry<LocalDate, List<LocalDate>> entry : parSemaine.entrySet()) {
            LocalDate lundi = entry.getKey();
            LocalDate finTroncon = lundi.plusDays(6);
            if (finTroncon.isAfter(dateFin)) {
                finTroncon = dateFin;
            }
            LocalDate debutTroncon = lundi.isBefore(dateDebut) ? dateDebut : lundi;

            int nbOuvrables = 0;
            for (LocalDate d = debutTroncon; !d.isAfter(finTroncon); d = d.plusDays(1)) {
                if (!CalendrierOuvrableEngine.isWeekend(d)) {
                    nbOuvrables++;
                }
            }
            double seuilHeures = nbOuvrables * 8.0;
            result.add(new TronconInfo(semNum++, debutTroncon, finTroncon, nbOuvrables, seuilHeures));
        }
        return result;
    }

    @Test
    @DisplayName("Cas 1 : Le 26 tombe un Lundi (2026-01-26 -> 2026-02-25)")
    void testCas1_26Lundi() {
        LocalDate debut = LocalDate.of(2026, 1, 26); // Lundi
        LocalDate fin = LocalDate.of(2026, 2, 25);   // Mercredi

        assertEquals(DayOfWeek.MONDAY, debut.getDayOfWeek());

        List<TronconInfo> troncons = simulerDecoupageGetSemainesPeriode(debut, fin);
        assertFalse(troncons.isEmpty());

        // Semaine 1 : Semaine nominale complète du Lundi au Dimanche
        TronconInfo sem1 = troncons.get(0);
        assertEquals(LocalDate.of(2026, 1, 26), sem1.debutTroncon());
        assertEquals(LocalDate.of(2026, 2, 1), sem1.finTroncon());
        assertEquals(5, sem1.nbOuvrables());
        assertEquals(40.0, sem1.seuilHeures());

        // Dernière semaine : Lundi 23 au Mercredi 25 février
        TronconInfo semDerniere = troncons.get(troncons.size() - 1);
        assertEquals(LocalDate.of(2026, 2, 23), semDerniere.debutTroncon());
        assertEquals(LocalDate.of(2026, 2, 25), semDerniere.finTroncon());
        assertEquals(3, semDerniere.nbOuvrables());
        assertEquals(24.0, semDerniere.seuilHeures());
    }

    @Test
    @DisplayName("Cas 2 : Le 26 tombe un Mercredi (2025-11-26 -> 2025-12-25)")
    void testCas2_26Mercredi() {
        LocalDate debut = LocalDate.of(2025, 11, 26); // Mercredi
        LocalDate fin = LocalDate.of(2025, 12, 25);   // Jeudi

        assertEquals(DayOfWeek.WEDNESDAY, debut.getDayOfWeek());

        List<TronconInfo> troncons = simulerDecoupageGetSemainesPeriode(debut, fin);

        // Semaine 1 : Tronçon partiel du Mercredi 26 au Dimanche 30
        TronconInfo sem1 = troncons.get(0);
        assertEquals(LocalDate.of(2025, 11, 26), sem1.debutTroncon());
        assertEquals(LocalDate.of(2025, 11, 30), sem1.finTroncon());
        assertEquals(3, sem1.nbOuvrables()); // Mercredi, Jeudi, Vendredi
        assertEquals(24.0, sem1.seuilHeures());

        // Remarque critique : Lundi 24 et Mardi 25 novembre étaient dans la période précédente (26/10 -> 25/11) !
    }

    @Test
    @DisplayName("Cas 3 : Le 26 tombe un Samedi (2026-09-26 -> 2026-10-25)")
    void testCas3_26Samedi() {
        LocalDate debut = LocalDate.of(2026, 9, 26); // Samedi
        LocalDate fin = LocalDate.of(2026, 10, 25);  // Dimanche

        assertEquals(DayOfWeek.SATURDAY, debut.getDayOfWeek());

        List<TronconInfo> tronconsGetSemaines = simulerDecoupageGetSemainesPeriode(debut, fin);
        List<TronconInfo> tronconsSynthese = simulerDecoupageCalculerSyntheseEmploye(debut, fin);

        // Dans getSemainesPeriode : Le code saute le weekend (continue) et commence Semaine 1 au Lundi 28 !
        TronconInfo sem1 = tronconsGetSemaines.get(0);
        assertEquals(LocalDate.of(2026, 9, 28), sem1.debutTroncon(), "getSemainesPeriode démarre au lundi 28");

        // Dans calculerSyntheseEmploye : Le weekend 26-27 forme un tronçon distinct avec 0 jour ouvré
        TronconInfo sem1Synthese = tronconsSynthese.get(0);
        assertEquals(LocalDate.of(2026, 9, 26), sem1Synthese.debutTroncon());
        assertEquals(LocalDate.of(2026, 9, 27), sem1Synthese.finTroncon());
        assertEquals(0, sem1Synthese.nbOuvrables());
    }

    @Test
    @DisplayName("Cas 4 : Le 25 tombe un Mercredi (2026-02-25)")
    void testCas4_25Mercredi() {
        LocalDate debut = LocalDate.of(2026, 1, 26);
        LocalDate fin = LocalDate.of(2026, 2, 25); // Mercredi

        assertEquals(DayOfWeek.WEDNESDAY, fin.getDayOfWeek());

        List<TronconInfo> troncons = simulerDecoupageGetSemainesPeriode(debut, fin);
        TronconInfo der = troncons.get(troncons.size() - 1);

        assertEquals(LocalDate.of(2026, 2, 23), der.debutTroncon()); // Lundi
        assertEquals(LocalDate.of(2026, 2, 25), der.finTroncon());   // Mercredi
        assertEquals(3, der.nbOuvrables());
        assertEquals(24.0, der.seuilHeures());
    }

    @Test
    @DisplayName("Cas 5 : Le 25 tombe un Samedi (2026-04-25)")
    void testCas5_25Samedi() {
        LocalDate debut = LocalDate.of(2026, 3, 26); // Jeudi
        LocalDate fin = LocalDate.of(2026, 4, 25);   // Samedi

        assertEquals(DayOfWeek.SATURDAY, fin.getDayOfWeek());

        List<TronconInfo> troncons = simulerDecoupageGetSemainesPeriode(debut, fin);
        TronconInfo der = troncons.get(troncons.size() - 1);

        assertEquals(LocalDate.of(2026, 4, 20), der.debutTroncon()); // Lundi
        assertEquals(LocalDate.of(2026, 4, 25), der.finTroncon());   // Samedi
        assertEquals(5, der.nbOuvrables());                          // 5 jours ouvrables (Lundi-Vendredi)
        assertEquals(40.0, der.seuilHeures());
    }
}
