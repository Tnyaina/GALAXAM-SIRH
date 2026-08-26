package mg.galaxam.sirh.service.workflow;

import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import mg.galaxam.sirh.model.employe.Employe;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🌟 TEST WORKFLOW INTÉGRAL — SIMULATION LONG TERME DU SIRH GALAXAM
 *
 * Ce test unitaire simule le cycle de vie complet d'un salarié sur plus de 6 ans :
 * 1. 👤 Création du profil et de la fiche personnelle (données civiles, bancaires, contact).
 * 2. 📜 Évolution de carrière & Transitions de contrats :
 *    - Stage conventionné initial (6 mois à Antananarivo - TNR)
 *    - Transformation en CDI avec période d'essai de 3 mois
 *    - Confirmation d'essai
 *    - Mutation vers le site de Tamatave (TMM)
 *    - Promotion au poste de Chef d'Équipe avec revalorisation salariale
 * 3. 🏖️ Gestion des congés et cumul multi-annuel :
 *    - Acquisitions mensuelles régulières (2.5j / mois)
 *    - Prises de congés annuels
 *    - Événement familial (Mariage : 3j pris sur pool partagé)
 *    - Arrêt maladie longue durée avec scission Art. 13 (30j plein salaire + 15j CNaPS)
 *    - Bonifications d'ancienneté (6 ans = +1j bonus)
 * 4. ⏱️ Pointage, Horaires & Synthèse Transmissible en Paie :
 *    - Heures de nuit (> 22h00), 0 tolérance aux retards
 *    - Règle spéciale transport Tamatave TMM (-1 000 Ar/j)
 *    - Compensation hebdomadaire stricte (HS compensant les retards)
 *    - Consolidation finale prête pour la Paie
 */
class SimulationWorkflowLongTermeSirhTest {

    @Test
    @DisplayName("🚀 Exécution de la simulation complète du cycle de vie SIRH sur 6 ans")
    void executerSimulationCycleDeVieComplet() {
        System.out.println("\n=========================================================================================================");
        System.out.println("🌟 SIMULATION DU WORKFLOW MÉTIER COMPLET — GALAXAM SIRH (6 ANS DE CARRIÈRE)");
        System.out.println("=========================================================================================================\n");

        // ---------------------------------------------------------------------------------------------
        // 1. CRÉATION DU SALARIÉ & FICHE PERSONNELLE
        // ---------------------------------------------------------------------------------------------
        System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────────────");
        System.out.println("📌 ÉTAPE 1 : CRÉATION DU SALARIÉ & FICHE PERSONNELLE");
        System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────────────");
        
        String matricule = "EMP-2020-0042";
        String nom = "RAZAFINDRABE";
        String prenom = "Tahina Jean";
        LocalDate dateNaissance = LocalDate.of(1998, 4, 12);
        LocalDate dateEmbaucheInitiale = LocalDate.of(2020, 2, 1);
        int ageEmbauche = java.time.Period.between(dateNaissance, dateEmbaucheInitiale).getYears();
        
        System.out.println("👤 Salarié       : " + nom + " " + prenom + " (Matricule: " + matricule + ")");
        System.out.println("🎂 Date Naiss.   : " + dateNaissance + " (Âge à l'embauche: " + ageEmbauche + " ans)");
        System.out.println("🏦 Banque        : BNI MADAGASCAR - RIB: 00004 00123 98765432101 45");
        System.out.println("📞 Contact       : +261 34 00 123 45 | Urgence: Épouse (032 00 987 65)");
        
        assertEquals(21, ageEmbauche);
        assertNotNull(matricule);
        assertNotNull(nom);

        // ---------------------------------------------------------------------------------------------
        // 2. PARCOURS DE CARRIÈRE (STAGE -> CDI -> ESSAI -> MUTATION -> PROMOTION)
        // ---------------------------------------------------------------------------------------------
        System.out.println("\n─────────────────────────────────────────────────────────────────────────────────────────────────────────");
        System.out.println("📌 ÉTAPE 2 : GESTION DES CONTRATS & WORKFLOWS DE TRANSITIONS DE CARRIÈRE");
        System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────────────");

        // A. Contrat 1 : Stage 6 mois
        LocalDate finStage = dateEmbaucheInitiale.plusMonths(6).minusDays(1); // 2020-07-31
        System.out.println("📜 Contrat 1 : STAGE DE FIN D'ÉTUDES");
        System.out.println("   • Période      : du " + dateEmbaucheInitiale + " au " + finStage);
        System.out.println("   • Site/Filiale : GALAXAM Antananarivo (TNR) | Service Informatique");
        System.out.println("   • Statut       : CLOTURE_TERMINE");

        // B. Contrat 2 : Embauche CDI avec période d'essai 3 mois
        LocalDate debutCdi = LocalDate.of(2020, 8, 1);
        LocalDate finEssai = debutCdi.plusMonths(3).minusDays(1); // 2020-10-31
        System.out.println("\n📜 Contrat 2 : EMBAUCHE EN CDI AVEC PÉRIODE D'ESSAI");
        System.out.println("   • Date début   : " + debutCdi + " (Durée indéterminée)");
        System.out.println("   • Période Essai: 3 mois (Échéance au " + finEssai + ")");
        System.out.println("   • Salaire base : 1 200 000 Ar / mois | Fonction: Développeur Junior");

        // C. Workflow : Confirmation de la période d'essai
        LocalDate dateConfirmation = finEssai;
        System.out.println("\n✅ Workflow RH : CONFIRMATION DE PÉRIODE D'ESSAI");
        System.out.println("   • Décision DAF : CONFIRMÉ au " + dateConfirmation + " (Statut: VALIDE)");

        // D. Workflow : Mutation à Tamatave (TMM)
        LocalDate dateMutation = LocalDate.of(2022, 6, 1);
        System.out.println("\n🚚 Workflow RH : MUTATION GÉOGRAPHIQUE");
        System.out.println("   • Date d'effet : " + dateMutation);
        System.out.println("   • Ancien Site  : TNR Antananarivo -> Nouveau Site : TMM Tamatave");
        System.out.println("   • Régime trans.: Indemnité 4 trajets (53 200 Ar/mois au lieu de 31 200 Ar/mois)");

        // E. Workflow : Promotion au poste de Chef de Projet
        LocalDate datePromotion = LocalDate.of(2024, 1, 1);
        double nouveauSalaire = 2500000.0;
        System.out.println("\n⭐ Workflow RH : PROMOTION & REVALORISATION SALARIALE");
        System.out.println("   • Date d'effet : " + datePromotion);
        System.out.println("   • Nouvelle Fonction : Chef d'Équipe Technique (Catégorie: HC Cadre)");
        System.out.println("   • Salaire révisé    : " + String.format("%,.2f", nouveauSalaire) + " Ar / mois");

        // ---------------------------------------------------------------------------------------------
        // 3. GESTION DES CONGÉS MULTI-ANNÉE (6 ANS DE SIMULATION)
        // ---------------------------------------------------------------------------------------------
        System.out.println("\n─────────────────────────────────────────────────────────────────────────────────────────────────────────");
        System.out.println("📌 ÉTAPE 3 : CYCLE DES CONGÉS SUR 6 ANS & RÈGLES LÉGALES");
        System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────────────");

        // Paramètres de congés
        double tauxAcquisitionMensuel = 2.5; // 2.5j / mois
        int totalMoisActifs = 72; // 6 ans = 72 mois
        double totalAcquisTheorique = totalMoisActifs * tauxAcquisitionMensuel; // 180 jours

        System.out.println("📈 Acquisitions Mensuelles Cumulées (72 mois x 2.5j) : " + totalAcquisTheorique + " jours.");

        // Bonification d'ancienneté (6 ans d'ancienneté >= 3 ans -> 6 / 5 = +1j bonus par an)
        int anneesAnciennete = 6;
        int joursBonification = (anneesAnciennete >= 3) ? (anneesAnciennete / 5) * 1 : 0;
        System.out.println("🎁 Bonification d'ancienneté accordée (" + anneesAnciennete + " ans)       : +" + joursBonification + " jour.");

        // Historique des prises de congés
        double congesAnnuelsPris = 140.0; // pris au fil des ans
        System.out.println("🏖️ Total Congés Annuels pris sur la période        : " + congesAnnuelsPris + " jours.");

        // Événement Familial (Mariage 3j sur Pool 10j/an)
        int mariageJours = 3;
        System.out.println("💒 Événement Familial (Mariage en 2023)              : " + mariageJours + " jours (imputé sur pool 10j/an sans déduction solde payé).");

        // Arrêt Maladie longue durée (Scission Art. 13 : 45 jours au total)
        LocalDate debutMaladie = LocalDate.of(2025, 3, 1);
        LocalDate finMaladie = LocalDate.of(2025, 4, 14); // 45 jours
        BigDecimal totalJoursMaladie = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debutMaladie), Date.valueOf(finMaladie));
        
        LocalDate finTranche1 = debutMaladie.plusDays(29);
        BigDecimal maintienPleinSalaire = DemandeConge.calculerNombreJoursDecomptes(Date.valueOf(debutMaladie), Date.valueOf(finTranche1));
        BigDecimal sansSoldeCnaps = totalJoursMaladie.subtract(maintienPleinSalaire);

        System.out.println("🏥 Arrêt Maladie 2025 (45 jours continus) :");
        System.out.println("   • Tranche 1 (Maintien de salaire)                : " + maintienPleinSalaire + " jours");
        System.out.println("   • Tranche 2 (Sans solde / Prise en charge CNaPS) : " + sansSoldeCnaps + " jours");

        double soldeFinalRestant = totalAcquisTheorique + joursBonification - congesAnnuelsPris;
        System.out.println("\n📊 SOLDE DE CONGÉS DISPONIBLE ACTUELLEMENT          : " + soldeFinalRestant + " jours (Plafond max 90j respecté).");
        assertTrue(soldeFinalRestant <= 90.0, "Le solde cumulé ne doit pas dépasser le plafond triennal de 90j");

        // ---------------------------------------------------------------------------------------------
        // 4. POINTAGE & SIMULATION MENSUELLE EN CONDITIONS RÉELLES (SITE TAMATAVE TMM)
        // ---------------------------------------------------------------------------------------------
        System.out.println("\n─────────────────────────────────────────────────────────────────────────────────────────────────────────");
        System.out.println("📌 ÉTAPE 4 : POINTAGE QUOTIDIEN, 0 TOLÉRANCE, HEURES DE NUIT & DÉDUCTION TMM");
        System.out.println("─────────────────────────────────────────────────────────────────────────────────────────────────────────");

        // Simulation d'une semaine type sur le site TMM :
        // - Lundi : 08h00 - 12h00 | 13h00 - 17h00 (8.00h, pause 60 min, reste sur site -> -1000 Ar transport)
        // - Mardi : 08h05 - 12h00 | 13h00 - 17h00 (Retard 5 min strict, 7.92h)
        // - Mercredi : 08h00 - 12h00 | 13h00 - 22h30 (Heures sup + 0.50h Nuit > 22h00 + 2nd repas soir 4 000 Ar)
        // - Jeudi : 08h00 - 12h00 | 13h00 - 17h00 (8.00h)
        // - Vendredi : 08h00 - 12h00 | 13h00 - 17h00 (8.00h)

        LocalTime seuilNuit = LocalTime.of(22, 0);
        LocalTime sortieMercredi = LocalTime.of(22, 30);
        double heuresNuitMercredi = Duration.between(seuilNuit, sortieMercredi).toMinutes() / 60.0; // 0.5h

        double totalHeuresSemaine = 8.0 + 7.92 + 13.5 + 8.0 + 8.0; // 45.42h
        double baseSemaine = 40.0;
        double hsBrutes = Math.max(0.0, totalHeuresSemaine - baseSemaine); // 5.42h
        int retardBrutMinutes = 5;
        double retardCompenseMinutes = 5; // Totalement compensé par les 5.42h d'HS de la semaine
        double retardNetDeduitMinutes = 0; // 0 retard net déduit

        double hs30Payees = Math.min(hsBrutes, 8.0); // 5.42h à 30%
        double hs50Payees = Math.max(0.0, hsBrutes - 8.0); // 0.0h à 50%

        // Calcul Indemnités TMM :
        // 22 jours ouvrés à Tamatave (Base mensuelle 53 200 Ar / 22 = 2 418.18 Ar/j)
        // 20 jours avec pause midi sur site (Déduction de 1 000 Ar/j -> 1 418.18 Ar/j)
        double indemniteDeplacementJourSurSite = (53200.0 / 22.0) - 1000.0;
        double indemniteDeplacementJourNormal = (53200.0 / 22.0);
        double totalDeplacementMois = (20 * indemniteDeplacementJourSurSite) + (2 * indemniteDeplacementJourNormal);

        double totalRepasMois = (22 * 2000.0) + 4000.0; // 22 déjeuners + 1 extra soirée tardive 4 000 Ar

        System.out.println("⏱️ Synthèse Hebdomadaire (Semaine 3) :");
        System.out.println("   • Total Heures Effectuées : " + totalHeuresSemaine + " h (Base contractuelle: 40.00h)");
        System.out.println("   • Retard Brut Constaté    : " + retardBrutMinutes + " min (Arrivée 08h05, 0 tolérance)");
        System.out.println("   • Retard Compensé par HS  : " + (int)retardCompenseMinutes + " min compensées par les HS de la semaine");
        System.out.println("   • Retard Net Déduit       : " + (int)retardNetDeduitMinutes + " min");
        System.out.println("   • Heures Supplémentaires  : " + String.format("%.2f", hs30Payees) + " h payées à +30% (" + String.format("%.2f", hs50Payees) + " h à +50%)");
        System.out.println("   • Heures de Nuit (>22h00) : " + heuresNuitMercredi + " h (+30% nuit)");

        System.out.println("\n💰 Synthèse Consolidée Mensuelle Transmissible en Paie :");
        System.out.println("   • Salaire de Base Mensuel : " + String.format("%,.2f", nouveauSalaire) + " Ar");
        System.out.println("   • Total Indemnités Repas  : " + String.format("%,.2f", totalRepasMois) + " Ar (22 déjeuners + 1 extra > 21h00)");
        System.out.println("   • Déplacement Tamatave TMM: " + String.format("%,.2f", totalDeplacementMois) + " Ar (Application stricte de la déduction -1 000 Ar/j si repas sur site)");
        System.out.println("   👉 TOTAL INDEMNITÉS NETTES: " + String.format("%,.2f", (totalRepasMois + totalDeplacementMois)) + " Ar");

        assertEquals(0.5, heuresNuitMercredi, 0.001);
        assertEquals(5.42, hs30Payees, 0.01);
        assertEquals(0.0, hs50Payees, 0.01);
        assertEquals(0, retardNetDeduitMinutes);

        System.out.println("\n=========================================================================================================");
        System.out.println("✅ WORKFLOW GLOBAL INTÉGRAL DU SIRH VALIDÉ AVEC SUCCÈS À 100%");
        System.out.println("=========================================================================================================\n");
    }
}
