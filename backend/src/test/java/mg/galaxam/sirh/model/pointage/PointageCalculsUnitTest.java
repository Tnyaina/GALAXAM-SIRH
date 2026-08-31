package mg.galaxam.sirh.model.pointage;

import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

public class PointageCalculsUnitTest {

    @Test
    @DisplayName("Calcul de durée d'une journée normale 08h-12h et 13h-17h")
    public void testCalcHoursJourneeNormale() {
        Time em = PointageSaisieService.parseTime("08:00");
        Time sm = PointageSaisieService.parseTime("12:00");
        Time ea = PointageSaisieService.parseTime("13:00");
        Time sa = PointageSaisieService.parseTime("17:00");

        double hMatin = PointageSaisieService.calcHours(em, sm);
        double hAm = PointageSaisieService.calcHours(ea, sa);
        assertEquals(4.0, hMatin);
        assertEquals(4.0, hAm);
        assertEquals(8.0, hMatin + hAm);
    }

    @Test
    @DisplayName("Calcul de durée avec demi-heures : 07h30-12h00 (4.5h) et 13h00-17h00 (4.0h) = 8.5h")
    public void testCalcHoursJourneeAvecDemiHeure() {
        Time em = PointageSaisieService.parseTime("07:30");
        Time sm = PointageSaisieService.parseTime("12:00");
        Time ea = PointageSaisieService.parseTime("13:00");
        Time sa = PointageSaisieService.parseTime("17:00");

        double hMatin = PointageSaisieService.calcHours(em, sm);
        double hAm = PointageSaisieService.calcHours(ea, sa);
        assertEquals(4.5, hMatin);
        assertEquals(4.0, hAm);
        assertEquals(8.5, hMatin + hAm);
    }

    @Test
    @DisplayName("Calcul de durée décalée : 08h00-12h30 (4.5h) et 13h30-17h00 (3.5h) = 8.0h")
    public void testCalcHoursJourneeDecalee() {
        Time em = PointageSaisieService.parseTime("08:00");
        Time sm = PointageSaisieService.parseTime("12:30");
        Time ea = PointageSaisieService.parseTime("13:30");
        Time sa = PointageSaisieService.parseTime("17:00");

        double hMatin = PointageSaisieService.calcHours(em, sm);
        double hAm = PointageSaisieService.calcHours(ea, sa);
        assertEquals(4.5, hMatin);
        assertEquals(3.5, hAm);
        assertEquals(8.0, hMatin + hAm);
    }

    @Test
    @DisplayName("Calcul des heures pour shift de nuit traversant minuit (20h00 -> 04h00)")
    public void testCalcHoursOvernightShift() {
        Time debut = PointageSaisieService.parseTime("20:00");
        Time fin = PointageSaisieService.parseTime("04:00");

        double total = PointageSaisieService.calcHours(debut, fin);
        assertEquals(8.0, total, "20h00 à 04h00 doit donner exactement 8.0 heures");
    }

    @Test
    @DisplayName("Calcul des heures de nuit au-delà de 22h00")
    public void testCalcHeuresNuit() {
        Time sortie21h30 = PointageSaisieService.parseTime("21:30");
        Time sortie23h30 = PointageSaisieService.parseTime("23:30");

        assertEquals(0.0, PointageSaisieService.calcHeuresNuit(sortie21h30));
        assertEquals(1.5, PointageSaisieService.calcHeuresNuit(sortie23h30));
    }

    @Test
    @DisplayName("Calcul des retards avec 0 tolérance")
    public void testCalcMinutesRetard() {
        Time arrivee07h30 = PointageSaisieService.parseTime("07:30");
        Time arrivee08h00 = PointageSaisieService.parseTime("08:00");
        Time arrivee08h05 = PointageSaisieService.parseTime("08:05");
        Time arrivee08h20 = PointageSaisieService.parseTime("08:20");

        assertEquals(0, PointageSaisieService.calcMinutesRetard(arrivee07h30, LocalTime.of(8, 0)), "Arrivée à 07h30 avant 08h00 doit donner 0 retard");
        assertEquals(0, PointageSaisieService.calcMinutesRetard(arrivee08h00, LocalTime.of(8, 0)));
        assertEquals(5, PointageSaisieService.calcMinutesRetard(arrivee08h05, LocalTime.of(8, 0)));
        assertEquals(20, PointageSaisieService.calcMinutesRetard(arrivee08h20, LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("Priorité Absolue : Férié Chômé avec congé demi-journée ou cadre autonome donne FERIE_CHOME (0h)")
    public void testPrioriteFerieSurCongeEtAutonome() {
        // Cas 1 : Salarié en congé demi-journée le matin sur un jour férié chômé sans heures saisies
        String statutCongeDemiFerie = PointageSaisieService.determinerStatutJour(
                true, // isFerie
                "MATIN", // typeConge
                false, // isAutonome
                null, null, null, null,
                0, false
        );
        assertEquals("FERIE_CHOME", statutCongeDemiFerie, "Un férié chômé prime absolument sur le congé demi-journée");

        // Cas 2 : Cadre autonome sur jour férié chômé sans heures saisies
        String statutAutonomeFerie = PointageSaisieService.determinerStatutJour(
                true, // isFerie
                null, // typeConge
                true, // isAutonome
                null, null, null, null,
                0, false
        );
        assertEquals("FERIE_CHOME", statutAutonomeFerie, "Un cadre autonome sur jour férié chômé est FERIE_CHOME");

        // Cas 3 : Férié travaillé avec saisie manuelle d'heures
        Time em = PointageSaisieService.parseTime("08:00");
        Time sm = PointageSaisieService.parseTime("12:00");
        String statutFerieTravaille = PointageSaisieService.determinerStatutJour(
                true, // isFerie
                null, // typeConge
                false, // isAutonome
                em, sm, null, null,
                0, false
        );
        assertEquals("FERIE_TRAVAILLE", statutFerieTravaille, "Des heures saisies sur un jour férié basculent en FERIE_TRAVAILLE");
    }

    @Test
    @DisplayName("Statut Salarié Standard (ABSENCE_NON_JUSTIFIEE) vs Cadre Autonome (DISPENSE_POINTAGE) sur jour normal")
    public void testStatutStandardVsAutonome() {
        // Standard vierge
        String stStandard = PointageSaisieService.determinerStatutJour(
                false, null, false, null, null, null, null, 0, false
        );
        assertEquals("ABSENCE_NON_JUSTIFIEE", stStandard);

        // Autonome vierge
        String stAutonome = PointageSaisieService.determinerStatutJour(
                false, null, true, null, null, null, null, 0, false
        );
        assertEquals("DISPENSE_POINTAGE", stAutonome);
    }

    @Test
    @DisplayName("Calcul Tronçon Hebdo : Compensation 1:1 du retard par les HS et ventilation 30%/50%")
    public void testCompensationHebdomadaireEtHs() {
        java.time.LocalDate lundi = java.time.LocalDate.of(2026, 1, 5);

        // Semaine complète 5 jours = 40h de seuil
        // 44.0h faites avec 30 min de retard (0.5h)
        var res1 = mg.galaxam.sirh.model.pointage.service.PointageSyntheseService.calculerTroncon(
                lundi, 5, 44.0, 30.0, false
        );
        assertEquals(40.0, res1.seuilHeuresTroncon);
        assertEquals(4.0, res1.heuresHsBrutes);
        assertEquals(0.5, res1.retardCompense, "Le retard de 30 min doit être intégralement compensé");
        assertEquals(0.0, res1.heuresDeficit, "Aucun déficit restant");
        assertEquals(3.5, res1.heuresHsNettes, "Il reste 3.5h de HS nettes payées");
        assertEquals(3.5, res1.heuresHs30);
        assertEquals(0.0, res1.heuresHs50);

        // Tronçon partiel de 3 jours ouvrés = 24h de seuil
        // 26.0h faites sans retard
        var res2 = mg.galaxam.sirh.model.pointage.service.PointageSyntheseService.calculerTroncon(
                lundi, 3, 26.0, 0.0, false
        );
        assertEquals(24.0, res2.seuilHeuresTroncon);
        assertEquals(2.0, res2.heuresHsNettes);
        assertEquals(2.0, res2.heuresHs30);

        // Semaine 5 jours avec dépassement > 8h HS (52.0h faites = 12h HS)
        var res3 = mg.galaxam.sirh.model.pointage.service.PointageSyntheseService.calculerTroncon(
                lundi, 5, 52.0, 0.0, false
        );
        assertEquals(12.0, res3.heuresHsBrutes);
        assertEquals(8.0, res3.heuresHs30, "8 premières heures à 30%");
        assertEquals(4.0, res3.heuresHs50, "Au-delà de 8h payées à 50%");

        // Salarié Cadre : Surplus compense le retard, mais 0 HS payée
        var resCadre = mg.galaxam.sirh.model.pointage.service.PointageSyntheseService.calculerTroncon(
                lundi, 5, 45.0, 60.0, true // Cadre
        );
        assertEquals(1.0, resCadre.heuresRetard);
        assertEquals(5.0, resCadre.heuresHsBrutes);
        assertEquals(1.0, resCadre.retardCompense, "Le retard de 1h du cadre est compensé");
        assertEquals(0.0, resCadre.heuresDeficit);
        assertEquals(0.0, resCadre.heuresHsNettes, "Un cadre ne reçoit aucune HS payée");
        assertEquals(0.0, resCadre.heuresHs30);
        assertEquals(0.0, resCadre.heuresHs50);
    }
}
