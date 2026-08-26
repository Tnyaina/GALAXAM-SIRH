package mg.galaxam.sirh.service.pointage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de simulation complet : Fiche de présence et compensation hebdomadaire Heures Sup vs Retards.
 * Démontre la compensation hebdomadaire (seuil 40h) :
 * - Semaine 1 : 10 min retard non compensé -> HN = 39.83h, Retard net déduit = 10 min.
 * - Semaine 2 : 35 min retard 100% compensé par les heures du soir -> HN = 40.00h, HS 30% = +2.67h, Retard net = 0 min.
 * - Semaine 3 : Surplus net -> HN = 40.00h, HS 30% = +5.50h, Nuit = 0.50h.
 * - Semaine 4 & 5 : Semaines normales -> HN = 40.00h & 16.00h.
 */
public class FichePresenceMensuelleSimulationTest {

    static class PointageJourSimulation {
        LocalDate date;
        String jourSemaine;
        LocalTime entreeMatin;
        LocalTime sortieMatin;
        LocalTime entreeAm;
        LocalTime sortieAm;
        
        BigDecimal heuresTravaillees = BigDecimal.ZERO;
        BigDecimal heuresNuit = BigDecimal.ZERO;
        int minutesRetard = 0;

        boolean beneficieRepasBase = true;
        BigDecimal montantRepasBase = BigDecimal.ZERO;
        
        boolean beneficieSecondRepas = false;
        BigDecimal montantSecondRepas = BigDecimal.ZERO;

        boolean beneficieDeplacement = true;
        BigDecimal montantDeplacement = BigDecimal.ZERO;

        boolean derogationVehiculeFonction = false;
        String overrideMotif = null;
    }

    static class SyntheseSemaine {
        int numeroSemaine;
        BigDecimal seuilHeuresBase = BigDecimal.valueOf(40.0);
        BigDecimal totalHeures = BigDecimal.ZERO;
        int totalRetardMinutesBrut = 0;
        int retardMinutesCompensees = 0;
        int retardMinutesNetDeduites = 0;
        BigDecimal heuresNormalesHN = BigDecimal.ZERO;
        BigDecimal hs30 = BigDecimal.ZERO;
        BigDecimal hs50 = BigDecimal.ZERO;
        BigDecimal heuresNuit = BigDecimal.ZERO;
        BigDecimal totalRepas = BigDecimal.ZERO;
        BigDecimal totalDeplacement = BigDecimal.ZERO;
    }

    static class SyntheseMensuelleEmploye {
        String matricule;
        String nomPrenom;
        String site;
        String periode;
        int joursTravailles = 0;
        BigDecimal totalHeuresEffectuees = BigDecimal.ZERO;
        BigDecimal totalHeuresNormalesHN = BigDecimal.ZERO;
        int totalRetardsBrutsMinutes = 0;
        int totalRetardsCompensesMinutes = 0;
        int totalRetardsNetsMinutes = 0;
        BigDecimal totalHs30 = BigDecimal.ZERO;
        BigDecimal totalHs50 = BigDecimal.ZERO;
        BigDecimal totalHeuresNuit = BigDecimal.ZERO;
        BigDecimal totalIndemniteRepas = BigDecimal.ZERO;
        BigDecimal totalIndemniteDeplacement = BigDecimal.ZERO;
        BigDecimal totalIndemnitesVariables = BigDecimal.ZERO;
    }

    @Test
    @DisplayName("Simulation Complète avec Compensation Hebdomadaire (HS vs Retards) & Données Réelles BDD")
    void testSimulationFichePresenceMensuelle() {
        System.out.println("=========================================================================================================");
        System.out.println("📊 SIMULATION FICHE DE PRÉSENCE & COMPENSATION HEBDOMADAIRE (DONNÉES RÉELLES BDD GALAXAM)");
        System.out.println("   Période 2026-08 (du 26/07/2026 au 25/08/2026) - Site TNR Antananarivo");
        System.out.println("=========================================================================================================");

        BigDecimal montantRepasBaseJour = new BigDecimal("2000.00");
        BigDecimal montant2eRepas19h30 = new BigDecimal("2000.00");
        BigDecimal montant2eRepas21h00 = new BigDecimal("4000.00");
        BigDecimal forfaitMensuelDeplacementTNR = new BigDecimal("31200.00");
        int joursOuvresMoisTheorique = 22;
        BigDecimal deplacementJourProrata = forfaitMensuelDeplacementTNR.divide(BigDecimal.valueOf(joursOuvresMoisTheorique), 2, RoundingMode.HALF_UP);

        LocalTime heurePrevueMatin = LocalTime.of(8, 0);
        LocalTime heurePrevueAm = LocalTime.of(13, 0);
        LocalTime seuil2eRepasT1 = LocalTime.of(19, 30);
        LocalTime seuil2eRepasT2 = LocalTime.of(21, 0);
        LocalTime seuilNuit = LocalTime.of(22, 0);

        List<PointageJourSimulation> moisPointages = new ArrayList<>();
        LocalDate debut = LocalDate.of(2026, 7, 26);
        LocalDate fin = LocalDate.of(2026, 8, 25);

        for (LocalDate dateJour = debut; !dateJour.isAfter(fin); dateJour = dateJour.plusDays(1)) {
            int dayOfWeek = dateJour.getDayOfWeek().getValue();

            if (dayOfWeek <= 5) {
                PointageJourSimulation p = new PointageJourSimulation();
                p.date = dateJour;
                p.jourSemaine = dateJour.getDayOfWeek().name();

                if (dateJour.equals(LocalDate.of(2026, 7, 28))) { 
                    // Mardi 28/07 (Sem 1) : Arrivée 08h10 (10 min retard)
                    p.entreeMatin = LocalTime.of(8, 10);
                    p.sortieMatin = LocalTime.of(12, 0);
                    p.entreeAm = LocalTime.of(13, 0);
                    p.sortieAm = LocalTime.of(17, 0);
                } else if (dateJour.equals(LocalDate.of(2026, 8, 5))) { 
                    // Mercredi 05/08 (Sem 2) : Retard 35 min (08h35) + Sortie 20h15 (10.67h travaillées)
                    p.entreeMatin = LocalTime.of(8, 35);
                    p.sortieMatin = LocalTime.of(12, 0);
                    p.entreeAm = LocalTime.of(13, 0);
                    p.sortieAm = LocalTime.of(20, 15);
                } else if (dateJour.equals(LocalDate.of(2026, 8, 13))) { 
                    // Jeudi 13/08 (Sem 3) : Sortie 22h30 (13.50h dont 0.5h de nuit + 2e repas 4000 Ar)
                    p.entreeMatin = LocalTime.of(8, 0);
                    p.sortieMatin = LocalTime.of(12, 0);
                    p.entreeAm = LocalTime.of(13, 0);
                    p.sortieAm = LocalTime.of(22, 30);
                } else if (dateJour.equals(LocalDate.of(2026, 8, 14))) { 
                    // Vendredi 14/08 (Sem 3) : Navette société (Déplacement = 0 Ar)
                    p.entreeMatin = LocalTime.of(8, 0);
                    p.sortieMatin = LocalTime.of(12, 0);
                    p.entreeAm = LocalTime.of(13, 0);
                    p.sortieAm = LocalTime.of(17, 0);
                    p.overrideMotif = "Navette société exceptionnelle";
                } else { 
                    // Journée standard (8.00h)
                    p.entreeMatin = LocalTime.of(8, 0);
                    p.sortieMatin = LocalTime.of(12, 0);
                    p.entreeAm = LocalTime.of(13, 0);
                    p.sortieAm = LocalTime.of(17, 0);
                }

                // 1. Calcul des heures réelles
                long minutesMatin = Duration.between(p.entreeMatin, p.sortieMatin).toMinutes();
                long minutesAm = Duration.between(p.entreeAm, p.sortieAm).toMinutes();
                double totalHeuresDouble = (minutesMatin + minutesAm) / 60.0;
                p.heuresTravaillees = BigDecimal.valueOf(totalHeuresDouble).setScale(2, RoundingMode.HALF_UP);

                // 2. Retards stricts (0 tolérance)
                int retMatin = p.entreeMatin.isAfter(heurePrevueMatin) ? (int) Duration.between(heurePrevueMatin, p.entreeMatin).toMinutes() : 0;
                int retAm = p.entreeAm.isAfter(heurePrevueAm) ? (int) Duration.between(heurePrevueAm, p.entreeAm).toMinutes() : 0;
                p.minutesRetard = retMatin + retAm;

                // 3. Heures de Nuit
                if (p.sortieAm.isAfter(seuilNuit)) {
                    long minutesNuit = Duration.between(seuilNuit, p.sortieAm).toMinutes();
                    p.heuresNuit = BigDecimal.valueOf(minutesNuit / 60.0).setScale(2, RoundingMode.HALF_UP);
                }

                // 4. Repas de base (2000 Ar) & 2ème repas
                p.montantRepasBase = montantRepasBaseJour;
                if (!p.sortieAm.isBefore(seuil2eRepasT2)) {
                    p.beneficieSecondRepas = true;
                    p.montantSecondRepas = montant2eRepas21h00;
                } else if (!p.sortieAm.isBefore(seuil2eRepasT1)) {
                    p.beneficieSecondRepas = true;
                    p.montantSecondRepas = montant2eRepas19h30;
                }

                // 5. Déplacement
                if (p.overrideMotif != null || p.derogationVehiculeFonction) {
                    p.beneficieDeplacement = false;
                    p.montantDeplacement = BigDecimal.ZERO;
                } else {
                    p.beneficieDeplacement = true;
                    p.montantDeplacement = deplacementJourProrata;
                }

                moisPointages.add(p);
            }
        }

        // =========================================================================
        // Agrégation par Semaine avec Compensation Heures Sup vs Retards
        // =========================================================================
        List<SyntheseSemaine> semaines = new ArrayList<>();
        SyntheseSemaine semaineEnCours = new SyntheseSemaine();
        semaineEnCours.numeroSemaine = 1;
        int joursDansSemaine = 0;

        for (PointageJourSimulation p : moisPointages) {
            joursDansSemaine++;
            semaineEnCours.totalHeures = semaineEnCours.totalHeures.add(p.heuresTravaillees);
            semaineEnCours.heuresNuit = semaineEnCours.heuresNuit.add(p.heuresNuit);
            semaineEnCours.totalRetardMinutesBrut += p.minutesRetard;
            semaineEnCours.totalRepas = semaineEnCours.totalRepas.add(p.montantRepasBase).add(p.montantSecondRepas);
            semaineEnCours.totalDeplacement = semaineEnCours.totalDeplacement.add(p.montantDeplacement);

            if (joursDansSemaine == 5) {
                semaineEnCours.seuilHeuresBase = BigDecimal.valueOf(40.0);
                appliquerCompensationHebdomadaire(semaineEnCours);
                semaines.add(semaineEnCours);

                SyntheseSemaine newSem = new SyntheseSemaine();
                newSem.numeroSemaine = semaineEnCours.numeroSemaine + 1;
                semaineEnCours = newSem;
                joursDansSemaine = 0;
            }
        }

        if (joursDansSemaine > 0) {
            semaineEnCours.seuilHeuresBase = BigDecimal.valueOf(joursDansSemaine * 8.0);
            appliquerCompensationHebdomadaire(semaineEnCours);
            semaines.add(semaineEnCours);
        }

        // =========================================================================
        // Synthèse Mensuelle Globale
        // =========================================================================
        SyntheseMensuelleEmploye syntheseMois = new SyntheseMensuelleEmploye();
        syntheseMois.matricule = "1";
        syntheseMois.nomPrenom = "RANDRASANA Eric Jean-Joel";
        syntheseMois.site = "Antananarivo (TNR)";
        syntheseMois.periode = "2026-08 (du 26/07/2026 au 25/08/2026)";
        syntheseMois.joursTravailles = moisPointages.size();

        for (SyntheseSemaine s : semaines) {
            syntheseMois.totalHeuresEffectuees = syntheseMois.totalHeuresEffectuees.add(s.totalHeures);
            syntheseMois.totalHeuresNormalesHN = syntheseMois.totalHeuresNormalesHN.add(s.heuresNormalesHN);
            syntheseMois.totalRetardsBrutsMinutes += s.totalRetardMinutesBrut;
            syntheseMois.totalRetardsCompensesMinutes += s.retardMinutesCompensees;
            syntheseMois.totalRetardsNetsMinutes += s.retardMinutesNetDeduites;
            syntheseMois.totalHs30 = syntheseMois.totalHs30.add(s.hs30);
            syntheseMois.totalHs50 = syntheseMois.totalHs50.add(s.hs50);
            syntheseMois.totalHeuresNuit = syntheseMois.totalHeuresNuit.add(s.heuresNuit);
            syntheseMois.totalIndemniteRepas = syntheseMois.totalIndemniteRepas.add(s.totalRepas);
            syntheseMois.totalIndemniteDeplacement = syntheseMois.totalIndemniteDeplacement.add(s.totalDeplacement);
        }

        syntheseMois.totalIndemnitesVariables = syntheseMois.totalIndemniteRepas.add(syntheseMois.totalIndemniteDeplacement);

        // Affichage console clair
        System.out.println("👤 SALARIÉ : " + syntheseMois.matricule + " - " + syntheseMois.nomPrenom + " (" + syntheseMois.site + ")");
        System.out.println("📅 PÉRIODE : " + syntheseMois.periode);
        System.out.println("---------------------------------------------------------------------------------------------------------");
        System.out.println("📋 TABLEAU PAR SEMAINE AVEC COMPENSATION HEBDOMADAIRE (HS vs RETARDS) :");
        for (SyntheseSemaine s : semaines) {
            System.out.printf("   👉 Semaine %d : Base=%.2fh | Effectuées=%.2fh | Retard Brut=%d min | Compensé=%d min | Net Déduit=%d min | HN=%.2fh | HS 30%%=%.2fh | Nuit=%.2fh%n",
                    s.numeroSemaine, s.seuilHeuresBase, s.totalHeures, s.totalRetardMinutesBrut, s.retardMinutesCompensees, s.retardMinutesNetDeduites, s.heuresNormalesHN, s.hs30, s.heuresNuit);
        }
        System.out.println("---------------------------------------------------------------------------------------------------------");
        System.out.println("💰 SYNTHÈSE MENSUELLE CONSOLIDÉE TRANSMISSIBLE EN PAIE :");
        System.out.println("   • Jours Ouvrés Travaillés     : " + syntheseMois.joursTravailles + " jours");
        System.out.println("   • Total Heures Effectuées      : " + syntheseMois.totalHeuresEffectuees + " h");
        System.out.println("   • Heures Normales Payées (HN)  : " + syntheseMois.totalHeuresNormalesHN + " h (Base contractuelle)");
        System.out.println("   • Retards Bruts Constatés      : " + syntheseMois.totalRetardsBrutsMinutes + " minutes");
        System.out.println("   • Retards Compensés par HS     : " + syntheseMois.totalRetardsCompensesMinutes + " minutes (Semaine 2 : 35 min rattrapées)");
        System.out.println("   • Retard Net Déduit sur Paie   : " + syntheseMois.totalRetardsNetsMinutes + " minutes (Semaine 1 : 10 min non compensées)");
        System.out.println("   • Total HS 30% Nettes Payées   : " + syntheseMois.totalHs30 + " h (2.67h Sem 2 + 5.50h Sem 3)");
        System.out.println("   • Total Heures de Nuit (+30%)  : " + syntheseMois.totalHeuresNuit + " h (13/08)");
        System.out.println("   • Total Indemnités Repas       : " + syntheseMois.totalIndemniteRepas + " Ar (22 déjeuners à 2000 Ar + 2 soirs)");
        System.out.println("   • Total Déplacement TNR        : " + syntheseMois.totalIndemniteDeplacement + " Ar (21 jours payés à 1418.18 Ar)");
        System.out.println("   👉 TOTAL INDEMNITÉS NETTES     : " + syntheseMois.totalIndemnitesVariables + " Ar");
        System.out.println("=========================================================================================================");

        // Assertions formelles
        assertEquals(22, syntheseMois.joursTravailles);
        assertEquals(new BigDecimal("184.00"), syntheseMois.totalHeuresEffectuees);
        assertEquals(new BigDecimal("175.83"), syntheseMois.totalHeuresNormalesHN, "Heures normales = 176h - 10 min de retard non compensé = 175.83h");
        assertEquals(45, syntheseMois.totalRetardsBrutsMinutes);
        assertEquals(35, syntheseMois.totalRetardsCompensesMinutes, "35 min compensées en Semaine 2");
        assertEquals(10, syntheseMois.totalRetardsNetsMinutes, "10 min déduites sur le mois (Semaine 1)");
        assertEquals(new BigDecimal("8.17"), syntheseMois.totalHs30);
        assertEquals(new BigDecimal("0.50"), syntheseMois.totalHeuresNuit);
        assertEquals(new BigDecimal("50000.00"), syntheseMois.totalIndemniteRepas);
        assertEquals(new BigDecimal("29781.78"), syntheseMois.totalIndemniteDeplacement);
    }

    private void appliquerCompensationHebdomadaire(SyntheseSemaine s) {
        if (s.totalHeures.compareTo(s.seuilHeuresBase) >= 0) {
            // Solde positif ou égal : le retard est compensé à 100%
            s.heuresNormalesHN = s.seuilHeuresBase;
            s.retardMinutesCompensees = s.totalRetardMinutesBrut;
            s.retardMinutesNetDeduites = 0;

            BigDecimal hsTotal = s.totalHeures.subtract(s.seuilHeuresBase);
            if (hsTotal.compareTo(BigDecimal.valueOf(8)) <= 0) {
                s.hs30 = hsTotal;
                s.hs50 = BigDecimal.ZERO;
            } else {
                s.hs30 = BigDecimal.valueOf(8);
                s.hs50 = hsTotal.subtract(BigDecimal.valueOf(8));
            }
        } else {
            // Solde négatif : pas d'HS, les heures normales correspondent au temps réellement travaillé
            s.heuresNormalesHN = s.totalHeures;
            s.hs30 = BigDecimal.ZERO;
            s.hs50 = BigDecimal.ZERO;
            s.retardMinutesCompensees = 0;
            s.retardMinutesNetDeduites = s.totalRetardMinutesBrut;
        }
    }
}
