package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PayrollExecutionEngineTest — Tests Unitaires du Moteur de Calcul de Paie")
class PayrollExecutionEngineTest {

    private PayrollExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
    }

    private PayrollCalculationInput creerInputBase(String matricule, BigDecimal salaireBase, String siteCode) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(1);
        in.setMatricule(matricule);
        in.setNomPrenom("RAKOTO Test");
        in.setCinNumero("101234567890");
        in.setNumeroCnaps("1234567");
        in.setIntitulePoste("Mécanicien");
        in.setNomDepartement("Technique");
        in.setCodeSite(siteCode);
        in.setNomSite("TMM".equalsIgnoreCase(siteCode) ? "Toamasina - Agence" : "Antananarivo - Siège");
        in.setRegimeIntervenant("PERMANENT");
        in.setCategorieProfessionnelle("AGENT");
        in.setModeCalculPaie("MENSUEL");
        in.setDateEmbauche(Date.valueOf("2024-01-01"));
        in.setAncienneteMois(24);

        in.setIdPeriodePaie(1);
        in.setCodePeriode("2026-08");
        in.setDateDebutPeriode(Date.valueOf("2026-08-01"));
        in.setDateFinPeriode(Date.valueOf("2026-08-31"));

        in.setSalaireBaseContractuel(salaireBase);
        in.setNbJoursOuvrables(new BigDecimal("22.0"));
        in.setNbJoursPresents(new BigDecimal("22.0"));
        in.setTotalHeuresTravaillees(new BigDecimal("173.33"));
        in.setHeuresNormales(new BigDecimal("173.33"));

        in.setSmeNational(new BigDecimal("262680.00"));
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));

        if ("TMM".equalsIgnoreCase(siteCode)) {
            in.setTauxSanteSalarial(0.015);
            in.setTauxSantePatronal(0.055);
        } else {
            in.setTauxSanteSalarial(0.01);
            in.setTauxSantePatronal(0.05);
        }

        return in;
    }

    private void verifierEquilibreComptable(BulletinPaieComplet complet) {
        BigDecimal sommeGains = BigDecimal.ZERO;
        BigDecimal sommeRetenues = BigDecimal.ZERO;

        for (BulletinPaieLigne l : complet.getLignes()) {
            if ("GAIN".equalsIgnoreCase(l.getSens())) {
                sommeGains = sommeGains.add(l.getMontantSalarial() != null ? l.getMontantSalarial() : BigDecimal.ZERO);
            } else if ("RETENUE".equalsIgnoreCase(l.getSens())) {
                sommeRetenues = sommeRetenues.add(l.getMontantSalarial() != null ? l.getMontantSalarial() : BigDecimal.ZERO);
            }
        }

        BigDecimal netCalcule = sommeGains.subtract(sommeRetenues).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAPayer = complet.getBulletin().getNetAPayer().setScale(2, RoundingMode.HALF_UP);

        assertEquals(netAPayer, netCalcule,
                "L'égalité comptable Débit/Crédit Somme(Gains) - Somme(Retenues) == NetAPayer doit être respectée au centime près");

        // Vérification de l'arrondi officiel bancaire (multiple de 10)
        long netLong = netAPayer.setScale(0, RoundingMode.HALF_UP).longValue();
        assertEquals(0, netLong % 10, "Le net à payer doit être un multiple exact de 10 Ariary");
    }

    @Test
    @DisplayName("1. Salarié Permanent standard (TNR, OSTIE, sans absence ni prêt)")
    void testCalculSalarieStandardTnr() {
        PayrollCalculationInput in = creerInputBase("EMP001", new BigDecimal("1000000.00"), "TNR");
        BulletinPaieComplet complet = engine.calculer(in);

        assertNotNull(complet);
        BulletinPaie bp = complet.getBulletin();

        // 1. Brut
        assertEquals(new BigDecimal("1000000.00"), bp.getTotalGainsBruts());

        // 2. Cotisations sociales
        // CNaPS (1% sal / 13% pat sur 1 000 000)
        assertEquals(new BigDecimal("10000.00"), complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));
        assertEquals(new BigDecimal("130000.00"), complet.getMontantLignePatronale("COTISATION_CNAPS_PAT"));

        // OSTIE (1% sal / 5% pat sur 1 000 000)
        assertEquals(new BigDecimal("10000.00"), complet.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));
        assertEquals(new BigDecimal("50000.00"), complet.getMontantLignePatronale("COTISATION_OSTIE_PAT"));

        // FMFP (1% patronal)
        assertEquals(new BigDecimal("10000.00"), complet.getMontantLignePatronale("COTISATION_FMFP_PAT"));

        assertEquals(new BigDecimal("20000.00"), bp.getTotalCotisationsSalariales());
        assertEquals(new BigDecimal("190000.00"), bp.getTotalCotisationsPatronales());

        // 3. Fiscalité IRSA
        // Net imposable = 1 000 000 - 20 000 = 980 000
        assertEquals(new BigDecimal("980000.00"), bp.getSalaireNetImposable());
        // IRSA brut théorique LF 2026 sur 980 000 Ar :
        // 0 - 350k (0%) = 0
        // 350k - 400k (50k * 5%) = 2 500
        // 400k - 500k (100k * 10%) = 10 000
        // 500k - 600k (100k * 15%) = 15 000
        // 600k - 980k (380k * 20%) = 76 000
        // Total = 103 500 Ar
        assertEquals(new BigDecimal("103500.00"), bp.getIrsaBrut());
        assertEquals(new BigDecimal("103500.00"), bp.getIrsaNet());

        // 4. Indemnités TNR : repas (22 x 2 000 = 44 000) + déplacement (31 200)
        assertEquals(new BigDecimal("44000.00"), complet.getMontantLigneSalariale("INDEMNITE_REPAS"));
        assertEquals(new BigDecimal("31200.00"), complet.getMontantLigneSalariale("INDEMNITE_TRANSPORT"));

        // Net = 980 000 - 103 500 + 44 000 + 31 200 = 951 700 Ar
        assertEquals(new BigDecimal("951700.00"), bp.getNetAPayer());

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("2. Réduction d'impôt pour enfants à charge (Règle des 21 ans)")
    void testReductionEnfantsCharge() {
        PayrollCalculationInput in = creerInputBase("EMP002", new BigDecimal("1000000.00"), "TNR");
        in.setNbEnfantsEligibles(3); // 3 enfants x 2 000 Ar = 6 000 Ar de réduction
        BulletinPaieComplet complet = engine.calculer(in);

        BulletinPaie bp = complet.getBulletin();
        assertEquals(3, bp.getNbEnfantsCharge());
        assertEquals(new BigDecimal("6000.00"), bp.getReductionEnfants());

        // IRSA brut = 103 500 Ar -> IRSA net = 103 500 - 6 000 = 97 500 Ar
        assertEquals(new BigDecimal("97500.00"), bp.getIrsaNet());

        // Net à payer augmenté de 6 000 Ar par rapport au test 1 (951 700 + 6 000 = 957 700 Ar)
        assertEquals(new BigDecimal("957700.00"), bp.getNetAPayer());

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("3. Salarié Agence Toamasina (TMM - Cotisation Santé OMSI 1.5% sal / 5.5% pat)")
    void testCalculSalarieAgenceToamasina() {
        PayrollCalculationInput in = creerInputBase("EMP003", new BigDecimal("1000000.00"), "TMM");
        BulletinPaieComplet complet = engine.calculer(in);

        // Vérification de la cotisation OMSI (et non OSTIE)
        assertNull(complet.trouverLigne("COTISATION_OSTIE_SAL"));
        assertNotNull(complet.trouverLigne("COTISATION_OMSI_SAL"));

        // OMSI salariale 1.5% sur 1 000 000 = 15 000 Ar
        assertEquals(new BigDecimal("15000.00"), complet.getMontantLigneSalariale("COTISATION_OMSI_SAL"));
        // OMSI patronale 5.5% sur 1 000 000 = 55 000 Ar
        assertEquals(new BigDecimal("55000.00"), complet.getMontantLignePatronale("COTISATION_OMSI_PAT"));

        // Total cotisations salariales = CNaPS (10 000) + OMSI (15 000) = 25 000 Ar
        assertEquals(new BigDecimal("25000.00"), complet.getBulletin().getTotalCotisationsSalariales());

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("4. Consultant externe sous régime fiscal libératoire ISI 5%")
    void testCalculConsultantIsi() {
        PayrollCalculationInput in = creerInputBase("CONS001", new BigDecimal("2000000.00"), "TNR");
        in.setRegimeIntervenant("CONSULTANT");
        BulletinPaieComplet complet = engine.calculer(in);

        BulletinPaie bp = complet.getBulletin();

        // 1. Exonération totale de CNaPS et de FMFP
        assertEquals(BigDecimal.ZERO.setScale(2), bp.getTotalCotisationsSalariales());
        assertEquals(BigDecimal.ZERO.setScale(2), bp.getTotalCotisationsPatronales());

        // 2. Retenue libératoire ISI 5% sur 2 000 000 = 100 000 Ar
        assertEquals(new BigDecimal("100000.00"), bp.getRetenueIsi5());
        assertEquals(BigDecimal.ZERO.setScale(2), bp.getIrsaNet());

        BulletinPaieLigne ligneIsi = complet.trouverLigne("RETENUE_ISI_5");
        assertNotNull(ligneIsi);
        assertEquals(new BigDecimal("100000.00"), ligneIsi.getMontantSalarial());

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("5. Prorata d'entrée en cours de mois basé sur les heures réelles travaillées")
    void testProrataEntreeEnCoursDeMois() {
        PayrollCalculationInput in = creerInputBase("EMP005", new BigDecimal("1000000.00"), "TNR");
        in.setEntreeSortieEnCoursDeMois(true);
        // Embauché à mi-mois : 80 heures travaillées sur 173.33h
        in.setHeuresNormales(new BigDecimal("80.00"));
        in.setTotalHeuresTravaillees(new BigDecimal("80.00"));
        in.setNbJoursPresents(new BigDecimal("10.0"));

        BulletinPaieComplet complet = engine.calculer(in);

        // Taux horaire = 1 000 000 / 173.3333 = 5769.2312 Ar/h
        // Salaire proratisé = 80 x 5769.2312 = 461 538.50 Ar
        BigDecimal brutCalcule = complet.getBulletin().getTotalGainsBruts();
        assertTrue(brutCalcule.compareTo(new BigDecimal("460000.00")) > 0);
        assertTrue(brutCalcule.compareTo(new BigDecimal("465000.00")) < 0);

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("6. Garde-fou de retenue d'absence au trentième (mois complet d'absence = 0 Ar)")
    void testGardeFouAbsenceMoisComplet() {
        PayrollCalculationInput in = creerInputBase("EMP006", new BigDecimal("800000.00"), "TNR");
        // Absence totale : 22 jours d'absence sur 22 jours ouvrables
        in.setNbJoursPresents(BigDecimal.ZERO);
        in.setNbJoursAbsence(new BigDecimal("22.0"));
        in.setHeuresNormales(BigDecimal.ZERO);
        in.setTotalHeuresTravaillees(BigDecimal.ZERO);

        BulletinPaieComplet complet = engine.calculer(in);

        // Le brut doit être strictement égal à 0 Ar (pas négatif, pas d'indu)
        assertEquals(BigDecimal.ZERO.setScale(2), complet.getBulletin().getTotalGainsBruts());

        // Net imposable = 0 Ar -> Pas d'impôt retenu à la source
        assertEquals(BigDecimal.ZERO.setScale(2), complet.getBulletin().getIrsaNet());
        assertEquals(BigDecimal.ZERO.setScale(2), complet.getBulletin().getNetAPayer());

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("7. Primes de performance (prorata) et assiduité avec pénalités de retard")
    void testPrimesPerformanceAssiduite() {
        PayrollCalculationInput in = creerInputBase("EMP007", new BigDecimal("1000000.00"), "TNR");
        in.setPrimePerformanceBase(new BigDecimal("100000.00"));
        in.setPrimeAssiduiteForfait(new BigDecimal("50000.00"));

        // Cas A : Présence complète, retards < 45 min -> Primes à 100%
        in.setMinutesRetard(20);
        BulletinPaieComplet c1 = engine.calculer(in);
        assertEquals(new BigDecimal("100000.00"), c1.getMontantLigneSalariale("PRIME_PERFORMANCE"));
        assertEquals(new BigDecimal("50000.00"), c1.getMontantLigneSalariale("PRIME_ASSIDUITE"));

        // Cas B : Retard > 70 min et conge > 1 jour -> Malus -25% sur PP et perte de la prime d'assiduité
        in.setMinutesRetard(80);
        in.setNbJoursConges(new BigDecimal("2.0"));
        BulletinPaieComplet c2 = engine.calculer(in);
        // PP = 100 000 * 0.75 = 75 000 Ar
        assertEquals(new BigDecimal("75000.00"), c2.getMontantLigneSalariale("PRIME_PERFORMANCE"));
        // PA = 0 (retard > 45 min)
        assertEquals(BigDecimal.ZERO, c2.getMontantLigneSalariale("PRIME_ASSIDUITE"));

        verifierEquilibreComptable(c1);
        verifierEquilibreComptable(c2);
    }

    @Test
    @DisplayName("8. Prélèvement partiel d'échéance de prêt (plafonnement quotité cessible 1/3 Art. 68)")
    void testPretPlafonnementQuotiteCessible() {
        PayrollCalculationInput in = creerInputBase("EMP008", new BigDecimal("500000.00"), "TNR");

        // Échéance de prêt de 250 000 Ar
        PaiePretEcheance ech = new PaiePretEcheance();
        ech.setIdEcheance(10);
        ech.setIdPret(5);
        ech.setNumeroEcheance(1);
        ech.setMontantEcheance(new BigDecimal("250000.00"));
        ech.setCapitalRestantApres(new BigDecimal("750000.00"));
        in.setEcheancePretActive(ech);

        // Acompte déjà versé de 100 000 Ar
        PaieAvanceAcompte avance = new PaieAvanceAcompte();
        avance.setIdAvance(1);
        avance.setTypeMouvement("ACOMPTE_15");
        avance.setMontant(new BigDecimal("100000.00"));
        avance.setMotif("Acompte quinzaine");
        in.setAvancesAcomptes(List.of(avance));

        BulletinPaieComplet complet = engine.calculer(in);

        // Le net disponible est d'environ 450 000 Ar
        // Le tiers légal (33.33%) est d'environ 150 000 Ar
        // L'acompte prend 100 000 Ar -> Reste 50 000 Ar max pour le prêt !
        // L'échéance de 250 000 Ar ne peut être prélevée qu'à hauteur de la capacité restante
        BulletinPaieLigne lignePret = complet.trouverLigne("RETENUE_ECHEANCE_PRET");
        assertNotNull(lignePret);
        assertTrue(lignePret.getMontantSalarial().compareTo(new BigDecimal("250000.00")) < 0,
                "L'échéance doit être prélevée partiellement en raison du plafond du tiers");

        assertNotNull(complet.getImpactPret());
        assertEquals("PRELEVEE_PARTIELLEMENT", complet.getImpactPret().nouveauStatut());
        assertTrue(complet.getImpactPret().montantRestantEcheance().compareTo(BigDecimal.ZERO) > 0);

        verifierEquilibreComptable(complet);
    }

    @Test
    @DisplayName("9. Équilibrage comptable de l'arrondi (AJUSTEMENT_ARRONDI)")
    void testEquilibrageArrondiNet() {
        // Salaire générant des centimes
        PayrollCalculationInput in = creerInputBase("EMP009", new BigDecimal("754321.45"), "TNR");
        BulletinPaieComplet complet = engine.calculer(in);

        BulletinPaie bp = complet.getBulletin();
        BigDecimal netAvantArrondi = bp.getNetAvantArrondi();
        BigDecimal netAPayer = bp.getNetAPayer();

        // Le net avant arrondi a des centimes
        assertNotEquals(netAvantArrondi, netAPayer);

        // La ligne AJUSTEMENT_ARRONDI doit combler l'écart
        BulletinPaieLigne ligneArrondi = complet.trouverLigne("AJUSTEMENT_ARRONDI");
        assertNotNull(ligneArrondi);

        verifierEquilibreComptable(complet);
    }
}
