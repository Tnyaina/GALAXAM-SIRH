package mg.galaxam.sirh.pointage;

import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine;
import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pointage Saisie & Engine Integration Test")
class PointageSaisieServiceEngineIntegrationTest {

    private final PointageCalculateurEngine engine = new PointageCalculateurEngine();

    @Nested
    @DisplayName("Délégation Retards & Nuit dans PointageSaisieService")
    class DelegationTests {

        @Test
        @DisplayName("calcMinutesRetard délègue correctement à l'Engine")
        void testMinutesRetardDelegation() {
            // Arrivée à 08h22 pour début prévu à 08h00 -> 22 minutes
            Time reel = Time.valueOf("08:22:00");
            LocalTime prevu = LocalTime.of(8, 0);

            int retard = PointageSaisieService.calcMinutesRetard(reel, prevu);
            assertEquals(22, retard, "Le retard calculé par le service doit être de 22 minutes");
        }

        @Test
        @DisplayName("calcHeuresNuit délègue fidèlement à l'Engine")
        void testHeuresNuitDelegation() {
            // Sortie à 23h30 -> 1.5h de nuit
            Time sortie = Time.valueOf("23:30:00");
            double hn = PointageSaisieService.calcHeuresNuit(sortie);
            assertEquals(1.5, hn, 0.01, "1.5h de travail de nuit après 22h00");

            // Sortie à 02h00 (shift de nuit traversant minuit) -> 4.0h
            Time sortieNuit = Time.valueOf("02:00:00");
            double hnMinit = PointageSaisieService.calcHeuresNuit(sortieNuit, LocalTime.of(22, 0), LocalTime.of(5, 0));
            assertEquals(4.0, hnMinit, 0.01, "4.0h de travail de nuit traversant minuit (2h + 2h)");
        }
    }

    @Nested
    @DisplayName("Calcul des Indemnités Repas et Déplacement (Pilotage Dynamique)")
    class IndemnitesTests {

        @Test
        @DisplayName("Calcul de l'indemnité repas avec majoration soirée")
        void testIndemniteRepas() {
            double montantMidi = 2000.0;
            double montant1 = 2000.0;
            double montant2 = 4000.0;
            LocalTime seuil1 = LocalTime.of(19, 30);
            LocalTime seuil2 = LocalTime.of(21, 0);

            // Cas 1 : Sortie standard 17h30 -> Déjeuner uniquement = 2000 Ar
            double r1 = engine.calculerTotalRepasJour(true, montantMidi, LocalTime.of(17, 30), seuil1, montant1, seuil2, montant2);
            assertEquals(2000.0, r1, 0.01);

            // Cas 2 : Sortie tardive 20h15 -> Déjeuner + Majoration Tranche 1 = 4000 Ar
            double r2 = engine.calculerTotalRepasJour(true, montantMidi, LocalTime.of(20, 15), seuil1, montant1, seuil2, montant2);
            assertEquals(4000.0, r2, 0.01);

            // Cas 3 : Sortie tardive 22h00 -> Déjeuner + Majoration Tranche 2 = 6000 Ar
            double r3 = engine.calculerTotalRepasJour(true, montantMidi, LocalTime.of(22, 0), seuil1, montant1, seuil2, montant2);
            assertEquals(6000.0, r3, 0.01);
        }

        @Test
        @DisplayName("Calcul de l'indemnité déplacement et déduction si cantine")
        void testIndemniteDeplacement() {
            double tarifTrajet = 1500.0;

            // Aller simple matin = 1500 Ar
            assertEquals(1500.0, engine.calculerDeplacementJour(true, false, tarifTrajet, false, 0.0), 0.01);

            // Aller-retour = 3000 Ar
            assertEquals(3000.0, engine.calculerDeplacementJour(true, true, tarifTrajet, false, 0.0), 0.01);

            // Aller-retour avec repas sur site et déduction transport (ex: convention d'entreprise -1000 Ar)
            assertEquals(2000.0, engine.calculerDeplacementJour(true, true, tarifTrajet, true, 1000.0), 0.01);
        }
    }

    @Nested
    @DisplayName("Détection Unifiée des Jours Ouvrables et Repos Légal")
    class CalendrierTests {

        @Test
        @DisplayName("isWeekend détecte fidèlement Samedi et Dimanche")
        void testWeekendDetection() {
            LocalDate vendredi = LocalDate.of(2026, 8, 14);
            LocalDate samedi = LocalDate.of(2026, 8, 15);
            LocalDate dimanche = LocalDate.of(2026, 8, 16);
            LocalDate lundi = LocalDate.of(2026, 8, 17);

            assertFalse(CalendrierOuvrableEngine.isWeekend(vendredi), "Vendredi n'est pas un week-end");
            assertTrue(CalendrierOuvrableEngine.isWeekend(samedi), "Samedi est un week-end");
            assertTrue(CalendrierOuvrableEngine.isWeekend(dimanche), "Dimanche est un week-end");
            assertFalse(CalendrierOuvrableEngine.isWeekend(lundi), "Lundi n'est pas un week-end");
        }
    }

    @Nested
    @DisplayName("Matrice de Statut Pointage selon la Typologie des Congés")
    class CongeTypeStatutMappingTests {

        @Test
        @DisplayName("Congé sans solde qualifié en ABSENCE_JUSTIFIEE (0 indemnité de congé)")
        void testCongeSansSoldeDevientAbsenceJustifiee() {
            LocalDate date = LocalDate.of(2026, 8, 10); // Lundi ouvré
            String statut = PointageSaisieService.determinerStatutJour(
                    date, false, "FULL_DAY", false, false, false, null, null, null, null, 0, false
            );
            assertEquals("ABSENCE_JUSTIFIEE", statut, "Un congé sans solde doit être qualifié en ABSENCE_JUSTIFIEE");
        }

        @Test
        @DisplayName("Congé annuel payé décompté du solde qualifié en CONGE")
        void testCongeAnnuelPayeDevientConge() {
            LocalDate date = LocalDate.of(2026, 8, 10);
            String statut = PointageSaisieService.determinerStatutJour(
                    date, false, "FULL_DAY", true, true, false, null, null, null, null, 0, false
            );
            assertEquals("CONGE", statut, "Un congé annuel payé doit être qualifié en CONGE");
        }

        @Test
        @DisplayName("Congé exceptionnel payé sans décompte de solde qualifié en DISPENSE_POINTAGE (maintien salaire)")
        void testCongeExceptionnelPayeDevientDispensePointage() {
            LocalDate date = LocalDate.of(2026, 8, 10);
            String statut = PointageSaisieService.determinerStatutJour(
                    date, false, "FULL_DAY", true, false, false, null, null, null, null, 0, false
            );
            assertEquals("DISPENSE_POINTAGE", statut, "Une permission exceptionnelle payée doit être qualifiée en DISPENSE_POINTAGE pour maintenir le salaire sans déduire du congé annuel");
        }

        @Test
        @DisplayName("Rétrocompatibilité : appel classique sans flags congePaye/decompteSolde retourne CONGE par défaut")
        void testRetrocompatibiliteCongeParDefaut() {
            LocalDate date = LocalDate.of(2026, 8, 10);
            String statut = PointageSaisieService.determinerStatutJour(
                    date, false, "FULL_DAY", false, null, null, null, null, 0, false
            );
            assertEquals("CONGE", statut, "Par défaut l'ancien appel retourne CONGE");
        }
    }
}
