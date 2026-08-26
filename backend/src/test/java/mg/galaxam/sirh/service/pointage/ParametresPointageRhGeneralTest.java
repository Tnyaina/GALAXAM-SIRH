package mg.galaxam.sirh.service.pointage;

import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de tests unitaires validant l'utilisation explicite et conforme
 * des 13 paramètres de configuration (4 RH_GENERAL + 9 POINTAGE).
 */
class ParametresPointageRhGeneralTest {

    private Map<String, String> params;

    @BeforeEach
    void setUp() {
        params = new HashMap<>();
        // 4 RH_GENERAL
        params.put("SEUIL_HEURES_JOUR", "8.0");
        params.put("SEUIL_HEURES_SEMAINE", "40.0");
        params.put("SEUIL_HS_TRANCHE_1", "8.0");
        params.put("PLAFOND_HEURES_SUPP_SEMAINE", "20");

        // 9 POINTAGE
        params.put("SEUIL_NUIT_DEBUT", "22:00");
        params.put("SEUIL_NUIT_FIN", "05:00");
        params.put("MONTANT_REPAS_MIDI", "2000.0");
        params.put("SEUIL_2ND_REPAS_1", "19:30");
        params.put("MONTANT_2ND_REPAS_1", "2000.0");
        params.put("SEUIL_2ND_REPAS_2", "21:00");
        params.put("MONTANT_2ND_REPAS_2", "4000.0");
        params.put("SEUIL_PAUSE_MIDI_MIN", "60.0");
        params.put("REDUCTION_DEPLACT_REPAS_MIDI", "1000.0");
    }

    // =========================================================================
    // 4 PARAMÈTRES RH_GENERAL
    // =========================================================================

    @Test
    @DisplayName("P1. SEUIL_HEURES_JOUR (8h) - Seuil quotidien par jour ouvré d'un tronçon")
    void test1_SeuilHeuresJour() {
        double seuilJour = ParametreGlobal.getDouble(params, "SEUIL_HEURES_JOUR", 8.0);
        int nbJoursOuvresTroncon = 3;
        double seuilTroncon = nbJoursOuvresTroncon * seuilJour;
        assertEquals(24.0, seuilTroncon, 0.001);
    }

    @Test
    @DisplayName("P2. SEUIL_HEURES_SEMAINE (40h) - Seuil pivot hebdomadaire des HS")
    void test2_SeuilHeuresSemaine() {
        double seuilSemaine = ParametreGlobal.getDouble(params, "SEUIL_HEURES_SEMAINE", 40.0);
        double heuresEffectuees = 46.5;
        double hsBrutes = Math.max(0.0, heuresEffectuees - seuilSemaine);
        assertEquals(6.5, hsBrutes, 0.001);
    }

    @Test
    @DisplayName("P3. SEUIL_HS_TRANCHE_1 (8h) - Ventilation entre majoration 30% et 50%")
    void test3_SeuilHsTranche1() {
        double seuilTranche1 = ParametreGlobal.getDouble(params, "SEUIL_HS_TRANCHE_1", 8.0);
        double hsTotal = 12.0;

        double hs30 = Math.min(hsTotal, seuilTranche1);
        double hs50 = Math.max(0.0, hsTotal - seuilTranche1);

        assertEquals(8.0, hs30, 0.001); // 8 premières heures à 30%
        assertEquals(4.0, hs50, 0.001); // 4 heures suivantes à 50%
    }

    @Test
    @DisplayName("P4. PLAFOND_HEURES_SUPP_SEMAINE (20h) - Plafond légal de sécurité")
    void test4_PlafondHeuresSuppSemaine() {
        int plafond = ParametreGlobal.getInt(params, "PLAFOND_HEURES_SUPP_SEMAINE", 20);
        double hsSemaine = 22.5;
        boolean depassementPlafond = hsSemaine > plafond;
        assertTrue(depassementPlafond, "Une alerte de conformité légale doit être levée si > 20h HS");
    }

    // =========================================================================
    // 9 PARAMÈTRES POINTAGE
    // =========================================================================

    @Test
    @DisplayName("P5. SEUIL_NUIT_DEBUT (22:00) - Déclenchement heures de nuit en soirée")
    void test5_SeuilNuitDebut() {
        LocalTime seuilNuitDebut = ParametreGlobal.getLocalTime(params, "SEUIL_NUIT_DEBUT", LocalTime.of(22, 0));
        LocalTime sortieSoir = LocalTime.of(23, 30);

        assertTrue(sortieSoir.isAfter(seuilNuitDebut));
        double heuresNuit = Duration.between(seuilNuitDebut, sortieSoir).toMinutes() / 60.0;
        assertEquals(1.5, heuresNuit, 0.001);
    }

    @Test
    @DisplayName("P6. SEUIL_NUIT_FIN (05:00) - Déclenchement heures de nuit matinales")
    void test6_SeuilNuitFin() {
        LocalTime seuilNuitFin = ParametreGlobal.getLocalTime(params, "SEUIL_NUIT_FIN", LocalTime.of(5, 0));
        LocalTime arriveeMatinale = LocalTime.of(3, 45);

        assertTrue(arriveeMatinale.isBefore(seuilNuitFin));
        double heuresNuitMatin = Duration.between(arriveeMatinale, seuilNuitFin).toMinutes() / 60.0;
        assertEquals(1.25, heuresNuitMatin, 0.001);
    }

    @Test
    @DisplayName("P7. MONTANT_REPAS_MIDI (2 000 Ar) - Déjeuner standard journalier")
    void test7_MontantRepasMidi() {
        double montantMidi = ParametreGlobal.getDouble(params, "MONTANT_REPAS_MIDI", 2000.0);
        int nbJoursPresents = 22;
        double totalRepasMidi = nbJoursPresents * montantMidi;
        assertEquals(44000.0, totalRepasMidi, 0.001);
    }

    @Test
    @DisplayName("P8 & P9. SEUIL_2ND_REPAS_1 (19:30) & MONTANT_2ND_REPAS_1 (2 000 Ar) - 1er Extra Soir")
    void test8et9_ExtraRepasTranche1() {
        LocalTime seuil1 = ParametreGlobal.getLocalTime(params, "SEUIL_2ND_REPAS_1", LocalTime.of(19, 30));
        double montant1 = ParametreGlobal.getDouble(params, "MONTANT_2ND_REPAS_1", 2000.0);
        LocalTime sortieReelle = LocalTime.of(20, 15);

        boolean beneficieTranche1 = sortieReelle.isAfter(seuil1);
        assertTrue(beneficieTranche1);
        assertEquals(2000.0, montant1, 0.001);
    }

    @Test
    @DisplayName("P10 & P11. SEUIL_2ND_REPAS_2 (21:00) & MONTANT_2ND_REPAS_2 (4 000 Ar) - 2e Extra Soir Majoré")
    void test10et11_ExtraRepasTranche2() {
        LocalTime seuil2 = ParametreGlobal.getLocalTime(params, "SEUIL_2ND_REPAS_2", LocalTime.of(21, 0));
        double montant2 = ParametreGlobal.getDouble(params, "MONTANT_2ND_REPAS_2", 4000.0);
        LocalTime sortieTardive = LocalTime.of(21, 45);

        boolean beneficieTranche2 = sortieTardive.isAfter(seuil2);
        assertTrue(beneficieTranche2);
        assertEquals(4000.0, montant2, 0.001);
    }

    @Test
    @DisplayName("P12. SEUIL_PAUSE_MIDI_MIN (60 min) - Détection de la pause courte sur site")
    void test12_SeuilPauseMidiMin() {
        double seuilPauseMin = ParametreGlobal.getDouble(params, "SEUIL_PAUSE_MIDI_MIN", 60.0);
        LocalTime sortieMatin = LocalTime.of(12, 0);
        LocalTime entreeAm = LocalTime.of(12, 45);

        long dureePauseMinutes = Duration.between(sortieMatin, entreeAm).toMinutes();
        boolean estPauseCourteSurSite = dureePauseMinutes <= seuilPauseMin;
        assertTrue(estPauseCourteSurSite, "45 min <= 60 min -> le salarié est resté déjeuner sur site");
    }

    @Test
    @DisplayName("P13. REDUCTION_DEPLACT_REPAS_MIDI (1 000 Ar) - Déduction transport TMM si repas pris sur site")
    void test13_ReductionDeplacementTmm() {
        double reductionMidi = ParametreGlobal.getDouble(params, "REDUCTION_DEPLACT_REPAS_MIDI", 1000.0);
        double tarifJourTmm = 53200.0 / 22.0; // 2418.18 Ar (4 trajets)
        
        // Salarié resté sur site à midi (2 trajets au lieu de 4)
        double tarifNetTmmApresReduction = tarifJourTmm - reductionMidi;
        
        // Doit devenir égal au tarif TNR 2 trajets (31 200 / 22 = 1418.18 Ar)
        double tarifJourTnr = 31200.0 / 22.0;
        assertEquals(tarifJourTnr, tarifNetTmmApresReduction, 0.001);
    }
}
