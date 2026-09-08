package mg.galaxam.sirh.audit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.model.conge.engine.PurgeFifoEngine;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;
import mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine;
import mg.galaxam.sirh.model.pointage.engine.PointageCalculateurEngine.BilanTronconHebdo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de tests de contre-audit approfondi — GALAXAM-SIRH.
 * Vérifie les cas limites juridiques, plafonnements légaux d'endettement,
 * compensations de temps et calculs fiscaux complexes.
 */
@DisplayName("Contre-Audit Approfondi — Vérifications Poussées & Cas Limites")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuditDeepInspectionExhaustifTest {

    private final CalculateurPaieEngine calculateurPaieEngine = new CalculateurPaieEngine();
    private final PayrollExecutionEngine executionEngine = new PayrollExecutionEngine(calculateurPaieEngine);
    private final PointageCalculateurEngine pointageEngine = new PointageCalculateurEngine();
    private final PurgeFifoEngine purgeFifoEngine = new PurgeFifoEngine();

    private List<TrancheIrsa> getBaremeLf2026Tranches() {
        return List.of(
                new TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350000.00"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400000.00"), new BigDecimal("500000.00"), 10.0),
                new TrancheIrsa(new BigDecimal("500000.00"), new BigDecimal("600000.00"), 15.0),
                new TrancheIrsa(new BigDecimal("600000.00"), new BigDecimal("4000000.00"), 20.0),
                new TrancheIrsa(new BigDecimal("4000000.00"), null, 25.0)
        );
    }

    private PayrollCalculationInput creerBaseInput(BigDecimal salaireBase) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(1);
        in.setMatricule("EMP-AUDIT-TEST");
        in.setNomPrenom("Test Salarié");
        in.setCinNumero("101234567890");
        in.setNumeroCnaps("1234567");
        in.setIntitulePoste("Opérateur");
        in.setNomDepartement("Technique");
        in.setCodeSite("TNR");
        in.setNomSite("Antananarivo - Siège");
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

        in.setSmeNational(new BigDecimal("262544.00"));
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setTranchesIrsa(getBaremeLf2026Tranches());

        in.setCodeSanteOrgane("OSTIE");
        in.setTauxSanteSalarial(0.01);
        in.setTauxSantePatronal(0.05);
        in.setForfaitTransportSite(new BigDecimal("31200.00"));
        in.setForfaitRepasSite(new BigDecimal("2000.00"));

        return in;
    }

    @Test
    @Order(1)
    @DisplayName("AUDIT-01 : Plafonnement légal de la quotité cessible pour prêt d'entreprise (Art. 72 Code Travail)")
    void testPlafonnementQuotiteCessiblePret() {
        PayrollCalculationInput input = creerBaseInput(new BigDecimal("800000.00"));

        // Échéance de prêt théorique très élevée (500 000 Ar)
        PaiePretEcheance ech = new PaiePretEcheance();
        ech.setIdEcheance(101);
        ech.setIdPret(55);
        ech.setNumeroEcheance(1);
        ech.setMontantEcheance(new BigDecimal("500000.00"));
        ech.setMontantRestantEcheance(new BigDecimal("500000.00"));
        ech.setCapitalRestantApres(new BigDecimal("4500000.00"));
        input.setEcheancePretActive(ech);

        BulletinPaieComplet result = executionEngine.calculer(input);
        assertNotNull(result);
        BulletinPaie bp = result.getBulletin();

        // Le net disponible avant trésorerie
        BigDecimal netAvantTresorerie = bp.getNetAvantArrondi().add(bp.getTotalRetenuesAcomptePret());
        BigDecimal plafondTiers = netAvantTresorerie.multiply(PayrollExecutionEngine.PLAFOND_QUOTITE_CESSIBLE)
                .setScale(2, RoundingMode.HALF_UP);

        // La retenue sur le prêt ne peut jamais dépasser le tiers légal (Art. 72)
        assertEquals(0, bp.getTotalRetenuesAcomptePret().compareTo(plafondTiers),
                "La retenue de prêt doit être exactement plafonnée au tiers légal de la quotité saisissable");

        // Statut partiel de l'échéance et préservation d'un net à payer positif
        assertNotNull(result.getImpactPret());
        assertEquals("PRELEVEE_PARTIELLEMENT", result.getImpactPret().nouveauStatut());
        assertTrue(result.getImpactPret().montantRestantEcheance().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(bp.getNetAPayer().compareTo(BigDecimal.ZERO) > 0, "Le net final doit rester strictement positif");
    }

    @Test
    @Order(2)
    @DisplayName("AUDIT-02 : Plafonnement de l'avance spéciale au dixième légal (Art. 68 Code Travail)")
    void testPlafonnementAvanceSpecialeDixieme() {
        PayrollCalculationInput input = creerBaseInput(new BigDecimal("1200000.00"));

        // Demande d'avance spéciale de 300 000 Ar (> 10% du salaire échu)
        PaieAvanceAcompte av = new PaieAvanceAcompte();
        av.setIdAvance(201);
        av.setTypeMouvement("AVANCE_SPECIALE");
        av.setMontant(new BigDecimal("300000.00"));
        av.setMotif("Dépannage exceptionnel");
        input.setAvancesAcomptes(List.of(av));

        BulletinPaieComplet result = executionEngine.calculer(input);
        BulletinPaie bp = result.getBulletin();

        // Plafond du dixième légal
        BigDecimal netAvantAvance = bp.getNetAvantArrondi().add(bp.getTotalRetenuesAcomptePret());
        BigDecimal plafondDixieme = netAvantAvance.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);

        assertEquals(0, bp.getTotalRetenuesAcomptePret().compareTo(plafondDixieme),
                "L'avance spéciale doit être strictement plafonnée au 1/10ème légal (Art. 68)");
        assertTrue(bp.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @Order(3)
    @DisplayName("AUDIT-03 : Compensation stricte 1:1 des retards par les heures supplémentaires de la semaine")
    void testCompensationRetardHeuresSupplementaires() {
        LocalDate debut = LocalDate.of(2026, 8, 3);
        int joursOuvres = 5;
        double seuilHeuresJour = 8.0; // 40h/semaine
        double totalHeures = 48.0;   // 8h supplémentaires brutes
        double minutesRetard = 180.0; // 3h de retards cumulés
        boolean isCadre = false;
        double seuilHs30 = 8.0;

        BilanTronconHebdo bilan = pointageEngine.calculerTroncon(
                debut, joursOuvres, totalHeures, minutesRetard, isCadre, seuilHeuresJour, seuilHs30
        );

        assertNotNull(bilan);
        assertEquals(40.0, bilan.seuilTroncon());
        assertEquals(8.0, bilan.hsBrutes(), "8h brutes au-dessus des 40h");
        assertEquals(3.0, bilan.heuresRetard(), "3h de retards converties");
        assertEquals(3.0, bilan.retardCompense(), "3h de retard compensées 1:1");
        assertEquals(5.0, bilan.hsNettes(), "Il reste 8 - 3 = 5h nettes à rémunérer");
        assertEquals(0.0, bilan.deficitNet(), "0h de déficit restant");
        assertEquals(5.0, bilan.hs30(), "Les 5h sont dans la première tranche 30%");
        assertEquals(0.0, bilan.hs50());
    }

    @Test
    @Order(4)
    @DisplayName("AUDIT-04 : Calcul des droits expirés par PurgeFifoEngine (Art. 85 Code Travail)")
    void testPrescriptionTriennaleCongesPayes() {
        // 10 jours anciens non consommés et non expirés -> 10 jours à expirer
        double aExpirer = purgeFifoEngine.calculerJoursAExpier(10.0, 0.0, 0.0);
        assertEquals(10.0, aExpirer, 0.001);

        // 10 jours anciens, 4 jours pris -> 6 jours restants à expirer
        double aExpirerPartiel = purgeFifoEngine.calculerJoursAExpier(10.0, 4.0, 0.0);
        assertEquals(6.0, aExpirerPartiel, 0.001);

        // 10 jours anciens, 12 jours consommés -> 0 jour à expirer
        double aExpirerNul = purgeFifoEngine.calculerJoursAExpier(10.0, 12.0, 0.0);
        assertEquals(0.0, aExpirerNul, 0.001);
    }

    @Test
    @Order(5)
    @DisplayName("AUDIT-05 : Calcul exact du barème progressif IRSA sur haut revenu (15 000 000 Ar)")
    void testIrsaHautRevenuTrancheSuperieure() {
        BigDecimal baseImposable = new BigDecimal("15000000.00");
        List<TrancheIrsa> tranches = getBaremeLf2026Tranches();
        int nbEnfants = 3;

        // Tranches calculées manuellement :
        // 0 à 350k @ 0% = 0
        // 350k à 400k (50k) @ 5% = 2 500
        // 400k à 500k (100k) @ 10% = 10 000
        // 500k à 600k (100k) @ 15% = 15 000
        // 600k à 4 000k (3 400k) @ 20% = 680 000
        // > 4 000k (11 000 000k) @ 25% = 2 750 000
        // Total brut = 3 457 500 Ar
        // Déduction 3 enfants (3 x 2000) = 6 000 Ar
        // Total net = 3 451 500 Ar
        BigDecimal irsaAttendu = new BigDecimal("3451500.00");

        BigDecimal irsaCalcule = calculateurPaieEngine.calculerIrsa(baseImposable, tranches, nbEnfants);
        assertEquals(0, irsaAttendu.compareTo(irsaCalcule),
                "Le calcul IRSA avec tranche supérieure illimitée doit être exact au centime près");
    }

    @Test
    @Order(6)
    @DisplayName("AUDIT-06 : Minimum de perception IRSA (3 000 Ar) sur salaire partiel inférieur au seuil d'imposition")
    void testMinimumPerceptionIrsaSalaireFaible() {
        BigDecimal baseImposable = new BigDecimal("150000.00"); // 150 000 Ar (dans la tranche 0%)
        List<TrancheIrsa> tranches = getBaremeLf2026Tranches();
        int nbEnfants = 0;

        BigDecimal irsaCalcule = calculateurPaieEngine.calculerIrsa(baseImposable, tranches, nbEnfants);
        assertEquals(0, new BigDecimal("3000.00").compareTo(irsaCalcule),
                "Même avec un impôt progressif nul, le minimum légal de 3 000 Ar doit être appliqué");
    }

    @Test
    @Order(7)
    @DisplayName("AUDIT-07 : Équilibre strict de la balance bancaire Ariary (arrondi à la dizaine)")
    void testEquilibreArrondiAriaryLigneBulletin() {
        BigDecimal brutNet = new BigDecimal("784123.45");
        BigDecimal arrondi = BulletinPaie.arrondirDizaineAriary(brutNet);
        assertEquals(new BigDecimal("784120.00"), arrondi, "Arrondi bancaire malgache officiel à la dizaine");

        BigDecimal ecart = arrondi.subtract(brutNet); // -3.45 Ar
        assertEquals(new BigDecimal("-3.45"), ecart);
        assertEquals(0, brutNet.add(ecart).compareTo(new BigDecimal("784120.00")));
    }

    @Test
    @Order(8)
    @DisplayName("AUDIT-08 : Workflow DAF Réel — Absence décomptée au taux horaire puis régularisation congés au retour")
    void testWorkflowDafAbsenceHoraireEtRegularisationConge() {
        BigDecimal base = new BigDecimal("866650.00"); // Base donnant taux horaire exact = 5 000.00 Ar (866650 / 173.33)
        PayrollCalculationInput inMoisM = creerBaseInput(base);

        // Mois M : 16h d'absence non rémunérées (employé n'avait pas encore posé ou régularisé son congé)
        inMoisM.setNbHeuresAbsence(new BigDecimal("16.00"));
        BulletinPaieComplet bM = executionEngine.calculer(inMoisM);

        // Taux horaire effectif = 866650 / 173.33 = 5000 Ar/h
        BigDecimal tauxHoraire = inMoisM.getTauxHoraireEffectif();
        assertEquals(0, new BigDecimal("5000.00").compareTo(tauxHoraire.setScale(2, RoundingMode.HALF_UP)));

        // Retenue absence = 16h x 5000 = 80 000 Ar
        BigDecimal retenueAbsence = bM.getLignes().stream()
                .filter(l -> "RETENUE_ABSENCE".equals(l.getCodeRubrique()))
                .map(l -> l.getMontantSalarial())
                .findFirst()
                .orElse(BigDecimal.ZERO);
        assertEquals(0, new BigDecimal("80000.00").compareTo(retenueAbsence),
                "L'absence non justifiée doit être déduite au taux horaire réel exact (16h x 5000 Ar = 80 000 Ar)");

        // Mois M+1 : L'employé est revenu avec son justificatif/accord DAF.
        // La DAF opère un mouvement REGULARISATION_CONGE de 80 000 Ar pour restituer la retenue d'absence.
        PayrollCalculationInput inMoisSuivant = creerBaseInput(base);
        inMoisSuivant.setRegularisationPaie(new BigDecimal("80000.00"));
        inMoisSuivant.setMotifRegularisationPaie("Régularisation congé retour mission M-1 (16h)");

        BulletinPaieComplet bM1 = executionEngine.calculer(inMoisSuivant);

        // Vérification de la ligne de régularisation
        var ligneRegul = bM1.getLignes().stream()
                .filter(l -> "REGULARISATION_PAIE".equals(l.getCodeRubrique()))
                .findFirst();
        assertTrue(ligneRegul.isPresent(), "La ligne de régularisation paie doit figurer sur le bulletin");
        assertEquals(0, new BigDecimal("80000.00").compareTo(ligneRegul.get().getMontantSalarial()));
        assertEquals("Régularisation : Régularisation congé retour mission M-1 (16h)", ligneRegul.get().getLibelleRubrique());

        // Le brut doit être exactement augmenté de 80 000 Ar : 866 650 + 80 000 = 946 650 Ar
        assertEquals(0, new BigDecimal("946650.00").compareTo(bM1.getBulletin().getTotalGainsBruts()),
                "Le total brut doit inclure la régularisation en gain de salaire de base");

        // Équilibre strict comptable du bulletin régularisé
        BigDecimal totalGains = bM1.getBulletin().getTotalGainsBruts().add(bM1.getBulletin().getTotalIndemnitesNonImposables());
        BigDecimal totalRetenues = bM1.getBulletin().getTotalCotisationsSalariales()
                .add(bM1.getBulletin().getIrsaNet())
                .add(bM1.getBulletin().getTotalRetenuesAcomptePret());
        BigDecimal netAvantArrondi = bM1.getBulletin().getNetAvantArrondi();

        assertEquals(0, totalGains.subtract(totalRetenues).compareTo(netAvantArrondi),
                "Identité comptable stricte : Total Gains - Total Retenues == Net Avant Arrondi");
        assertEquals(0, BulletinPaie.arrondirDizaineAriary(netAvantArrondi).compareTo(bM1.getBulletin().getNetAPayer()),
                "Le Net à payer doit correspondre exactement à l'arrondi bancaire à la dizaine d'Ariary");
    }
}

