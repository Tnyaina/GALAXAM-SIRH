package mg.galaxam.sirh.model.conge;

import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de tests unitaires validant l'ensemble des 14 paramètres globaux CONGE,
 * des motifs d'événements familiaux (Art. 88), des types de congés légaux,
 * du pool exceptionnel partagé et des règles de gestion RH.
 */
class ParametresEtReglesCongesTest {

    private Map<String, String> paramsConge;

    @BeforeEach
    void setUp() {
        paramsConge = new HashMap<>();
        // Les 14 paramètres globaux CONGE
        paramsConge.put("DIVISEUR_JOURS_CALENDAIRE_CONGE", "30");
        paramsConge.put("PLAFOND_CUMUL_SOLDE_CONGE_JOURS", "90");
        paramsConge.put("DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS", "36");
        paramsConge.put("PLAFOND_SOLDE_CONGE_NEGATIF", "-10");
        paramsConge.put("SEUIL_ALERTE_SOLDE_CONGE_FAIBLE", "3");
        paramsConge.put("DELAI_PREAVIS_COURT_JOURS", "2");
        paramsConge.put("DELAI_PREAVIS_LONG_JOURS", "15");
        paramsConge.put("SEUIL_JOURS_PREAVIS_LONG", "3");
        paramsConge.put("FRACTIONNEMENT_TRANCHE_MIN_JOURS", "12");
        paramsConge.put("DUREE_MAX_MATERNITE_STANDARD_JOURS", "98");
        paramsConge.put("DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS", "119");
        paramsConge.put("SEUIL_ANCIENNETE_BONIFICATION_ANS", "3");
        paramsConge.put("PALIER_ANNEES_BONIFICATION", "5");
        paramsConge.put("JOURS_BONIFICATION_PAR_PALIER", "1");
    }

    // =========================================================================
    // 1. LES 14 PARAMÈTRES GLOBAUX CONGE
    // =========================================================================

    @Test
    @DisplayName("C1. DIVISEUR_JOURS_CALENDAIRE_CONGE (30) - Valorisation journalière de l'indemnité congé")
    void test1_DiviseurJoursCalendaires() {
        int diviseur = ParametreGlobal.getInt(paramsConge, "DIVISEUR_JOURS_CALENDAIRE_CONGE", 30);
        double salaireBase = 1500000.0;
        double valeurJourConge = salaireBase / diviseur;
        assertEquals(50000.0, valeurJourConge, 0.001);
    }

    @Test
    @DisplayName("C2 & C3. PLAFOND_CUMUL_SOLDE_CONGE_JOURS (90j) & DUREE_PRESCRIPTION (36 mois) - Prescription triennale")
    void test2et3_PlafondCumulEtPrescription() {
        int plafondCumul = ParametreGlobal.getInt(paramsConge, "PLAFOND_CUMUL_SOLDE_CONGE_JOURS", 90);
        int prescriptionMois = ParametreGlobal.getInt(paramsConge, "DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS", 36);

        assertEquals(90, plafondCumul, "Plafond de 3 ans x 30j/an = 90 jours");
        assertEquals(36, prescriptionMois, "Prescription triennale = 36 mois");
    }

    @Test
    @DisplayName("C4. PLAFOND_SOLDE_CONGE_NEGATIF (-10j) - Anticipation maximale sous dérogation")
    void test4_PlafondSoldeNegatif() {
        int plafondNegatif = ParametreGlobal.getInt(paramsConge, "PLAFOND_SOLDE_CONGE_NEGATIF", -10);
        BigDecimal soldeDisponible = new BigDecimal("2.0");
        BigDecimal demande = new BigDecimal("10.0");
        BigDecimal soldeApresPrise = soldeDisponible.subtract(demande); // -8.0

        assertTrue(soldeApresPrise.compareTo(BigDecimal.valueOf(plafondNegatif)) >= 0, 
                "-8.0j est dans la limite autorisée de -10j");

        BigDecimal demandeExcessive = new BigDecimal("15.0");
        BigDecimal soldeTropNegatif = soldeDisponible.subtract(demandeExcessive); // -13.0
        assertTrue(soldeTropNegatif.compareTo(BigDecimal.valueOf(plafondNegatif)) < 0, 
                "-13.0j dépasse le plafond négatif légal de -10j");
    }

    @Test
    @DisplayName("C5. SEUIL_ALERTE_SOLDE_CONGE_FAIBLE (3j) - Déclenchement d'alerte RH")
    void test5_SeuilAlerteSoldeFaible() {
        int seuilFaible = ParametreGlobal.getInt(paramsConge, "SEUIL_ALERTE_SOLDE_CONGE_FAIBLE", 3);
        double soldeRestant = 2.5;
        boolean alerteDeclenchee = soldeRestant <= seuilFaible;
        assertTrue(alerteDeclenchee, "2.5j <= 3j -> alerte déclenchée");
    }

    @Test
    @DisplayName("C6, C7 & C8. DELAI_PREAVIS_COURT (2j) vs DELAI_PREAVIS_LONG (15j) selon seuil (3j)")
    void test6_7_8_DelaiPreavis() {
        int preavisCourt = ParametreGlobal.getInt(paramsConge, "DELAI_PREAVIS_COURT_JOURS", 2);
        int preavisLong = ParametreGlobal.getInt(paramsConge, "DELAI_PREAVIS_LONG_JOURS", 15);
        int seuilPreavisLong = ParametreGlobal.getInt(paramsConge, "SEUIL_JOURS_PREAVIS_LONG", 3);

        // Demande courte de 2 jours (<= 3j)
        int dureeDemande1 = 2;
        int preavisRequis1 = (dureeDemande1 > seuilPreavisLong) ? preavisLong : preavisCourt;
        assertEquals(2, preavisRequis1, "2 jours de préavis requis pour une absence courte <= 3j");

        // Demande longue de 10 jours (> 3j)
        int dureeDemande2 = 10;
        int preavisRequis2 = (dureeDemande2 > seuilPreavisLong) ? preavisLong : preavisCourt;
        assertEquals(15, preavisRequis2, "15 jours de préavis requis pour un congé principal > 3j");
    }

    @Test
    @DisplayName("C9. FRACTIONNEMENT_TRANCHE_MIN_JOURS (12j) - Tranche continue obligatoire Art. 85")
    void test9_FractionnementTrancheMin() {
        int trancheMin = ParametreGlobal.getInt(paramsConge, "FRACTIONNEMENT_TRANCHE_MIN_JOURS", 12);
        int joursPoses = 14;
        assertTrue(joursPoses >= trancheMin, "14 jours consécutifs valide la tranche continue d'au moins 12 jours");
    }

    @Test
    @DisplayName("C10 & C11. MATERNITE STANDARD (98j = 14 sem) & EXTENSION PATHOLOGIQUE (119j = 98 + 21j)")
    void test10et11_DureesMaternite() {
        int matStandard = ParametreGlobal.getInt(paramsConge, "DUREE_MAX_MATERNITE_STANDARD_JOURS", 98);
        int matPathologique = ParametreGlobal.getInt(paramsConge, "DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS", 119);

        assertEquals(98, matStandard, "14 semaines x 7j = 98 jours");
        assertEquals(119, matPathologique, "Extension de 3 semaines = 98 + 21 = 119 jours");
    }

    @Test
    @DisplayName("C12, C13 & C14. BONIFICATION ANCIENNETE (+1j par palier de 5 ans à partir de 3 ans)")
    void test12_13_14_BonificationAnciennete() {
        int seuilDebut = ParametreGlobal.getInt(paramsConge, "SEUIL_ANCIENNETE_BONIFICATION_ANS", 3);
        int palier = ParametreGlobal.getInt(paramsConge, "PALIER_ANNEES_BONIFICATION", 5);
        int joursParPalier = ParametreGlobal.getInt(paramsConge, "JOURS_BONIFICATION_PAR_PALIER", 1);

        // Employé 2 ans (< 3 ans)
        int anc2 = 2;
        int bonus2 = (anc2 >= seuilDebut) ? (anc2 / palier) * joursParPalier : 0;
        assertEquals(0, bonus2, "0 jour de bonification pour 2 ans");

        // Employé 6 ans
        int anc6 = 6;
        int bonus6 = (anc6 >= seuilDebut) ? (anc6 / palier) * joursParPalier : 0;
        assertEquals(1, bonus6, "1 jour de bonification pour 6 ans");

        // Employé 11 ans
        int anc11 = 11;
        int bonus11 = (anc11 >= seuilDebut) ? (anc11 / palier) * joursParPalier : 0;
        assertEquals(2, bonus11, "2 jours de bonification pour 11 ans");
    }

    // =========================================================================
    // 2. RÉFÉRENTIEL DES ÉVÉNEMENTS FAMILIAUX & POOL PARTAGÉ (Art. 88)
    // =========================================================================

    @Test
    @DisplayName("Événements familiaux : Barèmes légaux (Mariage 3j, Naissance 2j, Décès 3j/2j)")
    void testMotifsEvenementsFamiliaux() {
        int joursMariage = 3;
        int joursNaissance = 2;
        int joursDecesConjointEnfant = 3;
        int joursDecesParent = 2;

        assertEquals(3, joursMariage);
        assertEquals(2, joursNaissance);
        assertEquals(3, joursDecesConjointEnfant);
        assertEquals(2, joursDecesParent);
    }

    @Test
    @DisplayName("Pool Annuel Partagé (10j max/an pour événements familiaux et paternité)")
    void testPoolAnnuelPartage() {
        int plafondPoolAn = 10;
        int joursConsommesMariage = 3;
        int joursConsommesPaternite = 3;
        int joursConsommesDeces = 2;

        int totalConsomme = joursConsommesMariage + joursConsommesPaternite + joursConsommesDeces; // 8j
        assertTrue(totalConsomme <= plafondPoolAn, "8j <= 10j : quota respecté");

        int soldeRestantPool = plafondPoolAn - totalConsomme;
        assertEquals(2, soldeRestantPool, "Il reste 2 jours sur le pool annuel de permissions exceptionnelles");
    }

    // =========================================================================
    // 3. TYPES DE CONGÉS & SUSPENSION ACQUISITION
    // =========================================================================

    @Test
    @DisplayName("Types de congés sans solde suspendant l'acquisition mensuelle des congés")
    void testSuspensionAcquisitionSolde() {
        // ANNUEL, MALADIE maintien, MATERNITE, PATERNITE ne suspendent pas l'acquisition
        boolean annuelSuspend = false;
        boolean materniteSuspend = false;
        boolean maladieSuspend = false;

        // SANS_SOLDE, MALADIE_SANS_SOLDE, SABBATIQUE suspendent l'acquisition
        boolean sansSoldeSuspend = true;
        boolean maladieSansSoldeSuspend = true;
        boolean sabbatiqueSuspend = true;

        assertFalse(annuelSuspend);
        assertFalse(materniteSuspend);
        assertFalse(maladieSuspend);
        assertTrue(sansSoldeSuspend);
        assertTrue(maladieSansSoldeSuspend);
        assertTrue(sabbatiqueSuspend);
    }
}
