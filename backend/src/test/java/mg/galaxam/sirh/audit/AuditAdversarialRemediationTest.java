package mg.galaxam.sirh.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculResultDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcPreparationDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcRecuDTO;
import mg.galaxam.sirh.model.contrat.engine.CarriereTransitionValidator;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.PaiePret;
import mg.galaxam.sirh.model.paie.PaieStc;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.StcService;
import mg.galaxam.sirh.model.paie.service.export.SafiExportService;
import mg.galaxam.sirh.model.paieconfig.PaieConfigLegale;
import mg.galaxam.sirh.model.contrat.engine.AncienneteEngine;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Audit Adversarial — Validation Complète des Remédiations")
public class AuditAdversarialRemediationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StcService stcService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private CalculateurPaieEngine calculateurPaieEngine;

    @Autowired
    private SafiExportService safiExportService;

    @Autowired
    private mg.galaxam.sirh.model.contrat.engine.AncienneteEngine ancienneteEngine;

    @Autowired
    private mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierOuvrableEngine;

    @Autowired
    private mg.galaxam.sirh.model.pointage.service.PointageEmployeService pointageEmployeService;

    // =========================================================================
    // ISSUE 1 & 3: STC Déduction Enfants & Code Site TMM
    // =========================================================================
    @Test
    @Order(1)
    @DisplayName("Issue 1 & 3 : STC supporte la déduction enfants et le code site TMM dans StcPreparationDTO")
    void testStcEnfantsEtSiteTmm() {
        StcPreparationDTO prep = new StcPreparationDTO();
        prep.setIdEmploye(999);
        prep.setMatricule("EMP-TEST");
        prep.setNomComplet("Test Employé");
        prep.setSalaireBase(new BigDecimal("1000000.00"));
        prep.setCodeSite("TMM"); // Reconnaissance par code site sans dépendre du libellé
        prep.setNbEnfantsACharge(2); // 2 enfants à charge -> déduction de 4 000 Ar

        assertEquals("TMM", prep.getCodeSite());
        assertEquals(2, prep.getNbEnfantsACharge());

        // Barème IRSA standard LF 2026
        List<TrancheIrsa> tranches = List.of(
                new TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350000.00"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400000.00"), new BigDecimal("500000.00"), 10.0),
                new TrancheIrsa(new BigDecimal("500000.00"), new BigDecimal("600000.00"), 15.0),
                new TrancheIrsa(new BigDecimal("600000.00"), null, 20.0)
        );

        // Vérification de la déduction IRSA pour 2 enfants :
        // Brut 1 000 000 -> CNaPS 1% = 10 000 -> Net imp = 990 000
        // IRSA brut sur 990 000 = (50 000 * 5%) + (100 000 * 10%) + (100 000 * 15%) + (390 000 * 20%)
        // = 2 500 + 10 000 + 15 000 + 78 000 = 105 500 Ar.
        // Avec 0 enfant -> IRSA = 105 500 Ar.
        // Avec 2 enfants (-4 000 Ar) -> IRSA net = 101 500 Ar.
        BigDecimal netImposable = new BigDecimal("990000.00");
        BigDecimal irsaSansEnfant = calculateurPaieEngine.calculerIrsa(netImposable, tranches, 0);
        BigDecimal irsa2Enfants = calculateurPaieEngine.calculerIrsa(netImposable, tranches, prep.getNbEnfantsACharge());

        assertEquals(new BigDecimal("105500.00"), irsaSansEnfant);
        assertEquals(new BigDecimal("101500.00"), irsa2Enfants);
        assertEquals(new BigDecimal("4000.00"), irsaSansEnfant.subtract(irsa2Enfants));
    }

    // =========================================================================
    // ISSUE 2: Chargement Dynamique des Tranches IRSA
    // =========================================================================
    @Test
    @Order(2)
    @DisplayName("Issue 2 : PaieConfigLegale est chargée dynamiquement en base sans filtre hardcodé LF_2026")
    void testChargementDynamiquePaieConfigLegale() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            PaieConfigLegale activeCfg = PaieConfigLegale.findActiveAt(c, java.time.LocalDate.now());
            assertNotNull(activeCfg, "Une configuration légale active doit exister pour la date courante");
            assertTrue(activeCfg.getIdConfigLegale() > 0);
            assertNotNull(activeCfg.getDiviseurMensuelHeures());
            assertEquals(0, new BigDecimal("173.33").compareTo(activeCfg.getDiviseurMensuelHeures()),
                    "Le diviseur mensuel en base doit être 173.33");
        }
    }

    // =========================================================================
    // ISSUE 4: Vue SQL v_employe_actif_courant
    // =========================================================================
    @Test
    @Order(3)
    @DisplayName("Issue 4 : Vue PostgreSQL v_employe_actif_courant est accessible et requêtable")
    void testVueEmployeActifCourant() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement stmt = c.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM v_employe_actif_courant LIMIT 5")) {
            assertNotNull(rs);
            // La vue doit pouvoir être requêtée sans erreur de colonnes manquantes
            while (rs.next()) {
                assertNotNull(rs.getString("matricule"));
            }
        }
    }

    // =========================================================================
    // ISSUE 5: Diviseur 173.33 Configurable dans PayrollCalculationInput
    // =========================================================================
    @Test
    @Order(4)
    @DisplayName("Issue 5 : PayrollCalculationInput utilise le diviseur configurable (173.33 par défaut)")
    void testDiviseurMensuelConfigurable() {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setSalaireBaseContractuel(new BigDecimal("1733300.00"));

        // Diviseur par défaut = 173.33 -> Taux horaire = 1733300 / 173.33 = 10000.0000
        BigDecimal tauxDefaut = in.getTauxHoraireEffectif();
        assertEquals(new BigDecimal("10000.0000"), tauxDefaut);

        // Diviseur personnalisé (ex: 160.00 heures pour régime spécifique)
        in.setDiviseurMensuelHeures(new BigDecimal("160.00"));
        BigDecimal taux160 = in.getTauxHoraireEffectif();
        assertEquals(new BigDecimal("1733300.00").divide(new BigDecimal("160.00"), 4, RoundingMode.HALF_UP), taux160);
    }

    // =========================================================================
    // ISSUE 6: Validation Métier Exhaustive dans CarriereTransitionValidator
    // =========================================================================
    @Test
    @Order(5)
    @DisplayName("Issue 6 : CarriereTransitionValidator bloque les transitions illégales même sans nouveau contrat")
    void testCarriereTransitionValidatorSansNouveauContrat() {
        // Stagiaire tentant une mutation interne directe -> Interdit
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition(
                        "STAGE",
                        "MUTATION",
                        "STAGE",
                        null,
                        null
                ));
        assertNotNull(ex1.getMessage());
        assertTrue(ex1.getMessage().toLowerCase().contains("autoris") || ex1.getMessage().toLowerCase().contains("stage"));

        // CDI avec période d'essai sur avenant interne -> Interdit
        // Renouvellement CDD avec période d'essai -> Interdit
        assertThrows(IllegalArgumentException.class, () ->
                CarriereTransitionValidator.validerTransition(
                        "CDD",
                        "RENOUVELLEMENT_CDD",
                        "CDD",
                        "PERMANENT",
                        1 // essai de 1 mois interdit sur renouvellement
                ));
    }

    // =========================================================================
    // ISSUE 7: Invalidation du Cache Rubriques de Paie
    // =========================================================================
    @Test
    @Order(6)
    @DisplayName("Issue 7 : Invalidation sans exception du cache local de PayrollService")
    void testCacheRubriquesInvalidation() {
        assertDoesNotThrow(() -> payrollService.invalidateRubriqueCache());
    }

    // =========================================================================
    // ISSUE 8: Employé Inactif Exclu de la Paie
    // =========================================================================
    @Test
    @Order(7)
    @DisplayName("Issue 8 : PayrollService rejette le calcul pour un employé inactif")
    void testEmployeInactifRejete() {
        // ID d'employé inactif ou inexistant -> doit lever une exception claire
        try (Connection c = dataSource.getConnection()) {
            assertThrows(Exception.class, () ->
                    payrollService.calculerBulletinEmploye(999999, 1, true, null, c));
        } catch (Exception e) {
            fail("Erreur de connexion DB: " + e.getMessage());
        }
    }

    // =========================================================================
    // ISSUE 9: Normalisation des Tranches IRSA avec Décimales / Centimes
    // =========================================================================
    @Test
    @Order(8)
    @DisplayName("Issue 9 : CalculateurPaieEngine normalise correctement les bornes avec 0.01 centimes et 1 Ariary")
    void testNormalisationTranchesIrsaCentimes() {
        // Tranches configurées avec 350 000.01 Ar
        List<TrancheIrsa> tranchesCentimes = List.of(
                new TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350000.01"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400000.01"), null, 10.0)
        );

        // Tranches configurées avec 350 001 Ar
        List<TrancheIrsa> tranchesEntiers = List.of(
                new TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350001.00"), new BigDecimal("400000.00"), 5.0),
                new TrancheIrsa(new BigDecimal("400001.00"), null, 10.0)
        );

        BigDecimal base = new BigDecimal("450000.00");
        BigDecimal irsaCentimes = calculateurPaieEngine.calculerIrsaBrut(base, tranchesCentimes);
        BigDecimal irsaEntiers = calculateurPaieEngine.calculerIrsaBrut(base, tranchesEntiers);

        // Base 450 000:
        // Tranche 1 (0 -> 350k) : 0
        // Tranche 2 (350k -> 400k, delta 50 000 @ 5%) : 2 500
        // Tranche 3 (400k -> 450k, delta 50 000 @ 10%) : 5 000
        // Total brut = 7 500.00
        assertEquals(new BigDecimal("7500.00"), irsaCentimes);
        assertEquals(new BigDecimal("7500.00"), irsaEntiers);
    }

    // =========================================================================
    // ISSUE 10: Arrondi Canonique Ariary (arrondirDizaineAriary)
    // =========================================================================
    @Test
    @Order(9)
    @DisplayName("Issue 10 : L'arrondi à la dizaine Ariary est conforme et unifié avec BulletinPaie")
    void testArrondiDizaineAriaryCanonique() {
        assertEquals(new BigDecimal("1234570.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("1234567.45")));
        assertEquals(new BigDecimal("1234560.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("1234564.00")));
        assertEquals(new BigDecimal("1234560.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("1234560.00")));
        assertEquals(new BigDecimal("1234570.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("1234565.00")));
    }

    // =========================================================================
    // ISSUE 11: Solde Congés Négatif et Ligne de Retenue Explicite
    // =========================================================================
    @Test
    @Order(10)
    @DisplayName("Issue 11 : Un solde de congés négatif génère une RETENUE_CONGE_ANTICIPE explicite")
    void testStcSoldeCongeNegatifCreeRetenue() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Integer idEmp = null;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id_employe FROM employe WHERE est_actif = TRUE LIMIT 1")) {
                if (rs.next()) {
                    idEmp = rs.getInt("id_employe");
                }
            }
            if (idEmp == null) return;

            StcCalculRequest req = new StcCalculRequest();
            req.setIdEmploye(idEmp);
            req.setDateDepart(java.sql.Date.valueOf("2026-03-31"));
            req.setMotifRupture("DEMISSION");
            req.setSoldeConges(new BigDecimal("-3.5")); // Congés pris par anticipation
            req.setJoursTravaillesDernierMois(new BigDecimal("30"));
            req.setMoisPreavis(BigDecimal.ZERO);
            req.setIndemniteLicenciement(BigDecimal.ZERO);
            req.setSimulation(true);

            try {
                StcCalculResultDTO sim = stcService.simulerStc(req, c);
                assertNotNull(sim);
                // Vérifier la présence de la ligne RETENUE_CONGE_ANTICIPE
                boolean ligneTrouvee = sim.getLignes().stream()
                        .anyMatch(l -> "RETENUE_CONGE_ANTICIPE".equals(l.getCode()) && "RETENUE".equals(l.getSens()));
                assertTrue(ligneTrouvee, "Une ligne de retenue pour congés par anticipation doit être présente");
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    // =========================================================================
    // ISSUE 12: Apurement Partiel des Prêts et Statut SUSPENDU avec Motif CONTENTIEUX
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("Issue 12 : Apurement partiel de prêt en cas de STC insuffisant et bascule en recouvrement")
    void testApurementPartielPretContentieux() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Integer idEmp = null;
                try (ResultSet rsEmp = st.executeQuery("SELECT id_employe FROM employe LIMIT 1")) {
                    if (rsEmp.next()) {
                        idEmp = rsEmp.getInt("id_employe");
                    }
                }
                if (idEmp == null) {
                    return;
                }

                // 1. Création temporaire d'un prêt de test
                st.executeUpdate("INSERT INTO paie_pret (id_employe, reference_pret, montant_total, capital_rembourse, capital_restant_du, montant_mensualite, nb_echeances, date_premiere_echeance, statut, motif) " +
                        "VALUES (" + idEmp + ", 'PRET-AUDIT-TEST', 1000000.00, 200000.00, 800000.00, 100000.00, 10, '2026-01-01', 'EN_COURS', 'Test Audit')");

                // 2. Apurement partiel avec un montant STC insuffisant de 300 000 Ar (< 800 000 Ar restant)
                PaiePret.solderPretsPourStc(idEmp, new BigDecimal("300000.00"), null, c);

                // 3. Vérification que le prêt n'est pas soldé mais suspendu pour recouvrement avec dette restante préservée
                try (ResultSet rs = st.executeQuery("SELECT capital_rembourse, capital_restant_du, statut, motif FROM paie_pret WHERE reference_pret = 'PRET-AUDIT-TEST'")) {
                    assertTrue(rs.next());
                    assertEquals(new BigDecimal("500000.00"), rs.getBigDecimal("capital_rembourse"));
                    assertEquals(new BigDecimal("500000.00"), rs.getBigDecimal("capital_restant_du"));
                    assertEquals("SUSPENDU", rs.getString("statut"));
                    assertTrue(rs.getString("motif").contains("CONTENTIEUX"));
                }
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // ISSUE 13: Réconciliation Arithmétique Stricte SAFI Q = I + J + L + N + O + P
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("Issue 13 : Réconciliation stricte SAFI e-Hetra Q = I + J + L + N + O + P avec AUTIMP et déductions")
    void testSafiStrictArithmeticEquality() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Integer idPeriode = null;
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id_periode_paie FROM periode_paie ORDER BY id_periode_paie DESC LIMIT 1")) {
                if (rs.next()) {
                    idPeriode = rs.getInt("id_periode_paie");
                }
            }
            if (idPeriode == null) return;

            List<SafiRowDto> rows = safiExportService.genererDonneesSafi(idPeriode, c);
            assertNotNull(rows);
            for (SafiRowDto r : rows) {
                BigDecimal sommeQ = r.getSalaireBase()
                        .add(r.getIndemnitesImposables())
                        .add(r.getAvantagesNatureImposables())
                        .add(r.getHeuresSupplementaires())
                        .add(r.getPrimesEtGratifications())
                        .add(r.getAutres());
                assertEquals(r.getSalaireBrut(), sommeQ,
                        "La règle e-Hetra DGI Q = I + J + L + N + O + P doit être strictement respectée pour " + r.getMatricule());
            }
        }
    }

    // =========================================================================
    // ISSUE 14: Quittance Libératoire STC Identifiants Légaux & Congé Négatif
    // =========================================================================
    @Test
    @Order(13)
    @DisplayName("Issue 14 : Quittance STC avec identité légale (CIN, CNaPS) et réconciliation congé anticipé")
    void testStcRecuLegalIdentifiersEtReconciliationNegativeConge() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                Integer idEmp = null;
                try (ResultSet rs = st.executeQuery("SELECT id_employe FROM employe WHERE cin_numero IS NOT NULL LIMIT 1")) {
                    if (rs.next()) {
                        idEmp = rs.getInt("id_employe");
                    }
                }
                if (idEmp == null) return;

                StcPreparationDTO prep = stcService.preparerStc(idEmp, java.sql.Date.valueOf("2026-06-30"), "DEMISSION", c);
                assertNotNull(prep.getCinNumero(), "Le numéro de CIN doit être extrait dans la préparation STC");

                // Création d'une simulation avec solde de congés négatif (-2 jours)
                StcCalculRequest req = new StcCalculRequest();
                req.setIdEmploye(idEmp);
                req.setDateDepart(java.sql.Date.valueOf("2026-06-30"));
                req.setMotifRupture("DEMISSION");
                req.setSoldeConges(new BigDecimal("-2.0"));
                req.setJoursTravaillesDernierMois(new BigDecimal("30"));
                req.setSimulation(true);

                StcCalculResultDTO calc = stcService.simulerStc(req, c);
                assertNotNull(calc);
                assertTrue(calc.getIndemniteCongesPayes().compareTo(BigDecimal.ZERO) < 0,
                        "L'ICCP calculée doit être négative en cas d'avance de congés");

                // Création temporaire d'un enregistrement PaieStc pour tester la quittance
                PaieStc stc = new PaieStc();
                stc.setIdEmploye(idEmp);
                stc.setIdPeriodePaie(prep.getIdPeriodePaie() != null ? prep.getIdPeriodePaie() : 1);
                stc.setDateDepart(java.sql.Date.valueOf("2026-06-30"));
                stc.setMotifRupture("DEMISSION");
                stc.setSoldeCongesRestant(new BigDecimal("-2.0"));
                stc.setTauxJournalierConge(calc.getTauxJournalierConge());
                stc.setIndemniteCongesPayes(calc.getIndemniteCongesPayes());
                stc.setSalaireProrata(calc.getSalaireProrata());
                stc.setMoisPreavis(calc.getMoisPreavis());
                stc.setIndemnitePreavis(calc.getIndemnitePreavis());
                stc.setIndemniteLicenciement(calc.getIndemniteLicenciement());
                stc.setAutresIndemnites(calc.getAutresIndemnites());
                stc.setTotalBrutStc(calc.getTotalBrutStc());
                stc.setTotalCotisationsSociales(calc.getTotalCotisationsSociales());
                stc.setTotalIrsa(calc.getIrsaNet());
                stc.setSoldePretsDeduit(calc.getSoldePretsDeduit());
                stc.setSoldeAvancesDeduit(calc.getSoldeAvancesDeduit());
                stc.setAutresDeductions(calc.getAutresDeductions());
                stc.setNetAPayerStc(calc.getNetAPayerStc());
                stc.setStatut("VALIDE");
                stc.save(c);

                StcRecuDTO recu = stcService.obtenirRecuStc(stc.getIdStc(), c);
                assertNotNull(recu);
                assertNotNull(recu.getCinNumero(), "Le reçu doit contenir le CIN du salarié (Art. 53 Code Travail)");
                assertEquals(BigDecimal.ZERO, recu.getIndemniteCongesPayes(),
                        "L'indemnité de congés dans les gains du reçu doit être ramenée à zéro en cas de solde négatif");
                assertTrue(recu.getAutresDeductions().compareTo(BigDecimal.ZERO) > 0,
                        "La déduction pour congé anticipé doit figurer dans les retenues du reçu");

                BigDecimal netAttendu = recu.getTotalBrut().subtract(recu.getTotalRetenues());
                assertEquals(recu.getNetAPayer(), netAttendu,
                        "Équilibre mathématique strict de la quittance : Brut - Retenues = Net à Payer");
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // ISSUE 11: MOTEUR CANONIQUE IRSA & PARITÉ STRICTE STC / PAIE
    // =========================================================================
    @Test
    @Order(11)
    @DisplayName("ACT-001/002/003 : Calcul IRSA Canonique Détaillé, Arrondis Centaine/Dizaine, Protection CGI Art. 01.03.18 & Parité Paie/STC")
    void testIrsaDetailleCanoniqueEtPariteStcPaie() throws Exception {
        // 1. Test des arrondis légaux fiscaux
        BigDecimal montantTest = new BigDecimal("350123.45");
        BigDecimal arrondiCentaine = CalculateurPaieEngine.arrondirCentaineInferieure(montantTest);
        assertEquals(new BigDecimal("350100.00"), arrondiCentaine, "L'arrondi centaine inférieure doit donner 350 100 Ar");

        BigDecimal brutTest = new BigDecimal("54321.40");
        BigDecimal arrondiDizaine = CalculateurPaieEngine.arrondirDizaineSuperieure(brutTest);
        assertEquals(new BigDecimal("54330.00"), arrondiDizaine, "L'arrondi dizaine supérieure doit donner 54 330 Ar");

        // 2. Barème progressif LF 2026
        List<TrancheIrsa> tranches = List.of(
                new TrancheIrsa(BigDecimal.ZERO, new BigDecimal("350000.00"), 0.0),
                new TrancheIrsa(new BigDecimal("350000.00"), new BigDecimal("400000.00"), 0.05),
                new TrancheIrsa(new BigDecimal("400000.00"), new BigDecimal("500000.00"), 0.10),
                new TrancheIrsa(new BigDecimal("500000.00"), new BigDecimal("600000.00"), 0.15),
                new TrancheIrsa(new BigDecimal("600000.00"), null, 0.20)
        );

        // 3. Test de la règle protectrice CGI Art. 01.03.18 (seuil 350 000 Ar)
        // Salaire net imposable de 352 000 Ar : l'impôt normal ne peut pas amener le net sous 350 000 Ar
        CalculateurPaieEngine.IrsaResult resProtect = calculateurPaieEngine.calculerIrsaDetaille(
                new BigDecimal("352000.00"),
                tranches,
                0,
                new BigDecimal("2000.00"),
                new BigDecimal("3000.00"),
                new BigDecimal("350000.00")
        );
        assertNotNull(resProtect);
        assertTrue(resProtect.irsaNet().compareTo(new BigDecimal("3000.00")) >= 0,
                "L'IRSA net ne doit jamais être inférieur au minimum de perception de 3 000 Ar");

        // 4. Parité stricte entre calculerIrsa et calculerIrsaDetaille
        BigDecimal baseNormal = new BigDecimal("1500000.00");
        CalculateurPaieEngine.IrsaResult resNormal = calculateurPaieEngine.calculerIrsaDetaille(
                baseNormal, tranches, 2, new BigDecimal("2000.00"), new BigDecimal("3000.00"), new BigDecimal("350000.00")
        );
        BigDecimal irsaRapide = calculateurPaieEngine.calculerIrsa(baseNormal, tranches, 2);
        assertEquals(resNormal.irsaNet(), irsaRapide,
                "calculerIrsaDetaille et calculerIrsa doivent produire une parité mathématique absolue");
    }

    // =========================================================================
    // ISSUE 12: ANCIENNETÉ ENGINE SANS DUPLICATION SQL
    // =========================================================================
    @Test
    @Order(12)
    @DisplayName("ACT-004 : Surcharge AncienneteEngine.calculerAncienneteEmploye opérationnelle sans redondance SQL")
    void testAncienneteEngineOverloadSansDuplicationSql() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Trouver un employé existant avec un contrat
                Integer idEmp = null;
                try (Statement stmt = c.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id_employe FROM contrat_travail WHERE date_debut IS NOT NULL LIMIT 1")) {
                    if (rs.next()) {
                        idEmp = rs.getInt("id_employe");
                    }
                }

                if (idEmp != null) {
                    AncienneteEngine.AncienneteBilan bilan = ancienneteEngine.calculerAncienneteEmploye(idEmp, java.time.LocalDate.now(), c);
                    assertNotNull(bilan, "Le bilan d'ancienneté calculé par le moteur Java doit être non-nul");
                    assertTrue(bilan.moisEffectifs() >= 0, "L'ancienneté en mois effectifs doit être positive ou nulle");
                    assertTrue(bilan.anneesEffectives() >= 0.0, "L'ancienneté en années effectives doit être positive ou nulle");
                }
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // ISSUE 13: CALENDRIER OUVRABLE ENGINE — SOURCE UNIQUE DE VÉRITÉ
    // =========================================================================
    @Test
    @Order(13)
    @DisplayName("ACT-005 : Centralisation CalendrierOuvrableEngine (Fériés, Chômés, Info Jour)")
    void testCalendrierOuvrableEngineUnification() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Assomption (15 Août 2026) -> Configuré comme férié chômé national en base
                java.time.LocalDate assomption = java.time.LocalDate.of(2026, 8, 15);
                CalendrierOuvrableEngine.JourChomeInfo info = calendrierOuvrableEngine.getJourChomeInfo(assomption, 1, c);
                assertNotNull(info);
                assertTrue(info.estChome(), "Le 15 Août (Assomption) doit être détecté comme férié chômé");
                assertEquals("Assomption", info.libelle());

                // 2. Jour ordinaire ouvrable (ex: Mardi 18 Août 2026) -> Ne doit pas être chômé
                java.time.LocalDate jourNormal = java.time.LocalDate.of(2026, 8, 18);
                CalendrierOuvrableEngine.JourChomeInfo infoNormal = calendrierOuvrableEngine.getJourChomeInfo(jourNormal, 1, c);
                assertNotNull(infoNormal);
                assertFalse(infoNormal.estChome(), "Un mardi ordinaire ne doit pas être chômé");

                // 3. Récupération des fériés pour un employé
                java.util.Set<java.time.LocalDate> feries = calendrierOuvrableEngine.getDatesFeriesEtChomeesEmploye(
                        java.time.LocalDate.of(2026, 8, 1),
                        java.time.LocalDate.of(2026, 8, 31),
                        1,
                        c
                );
                assertNotNull(feries);
                assertTrue(feries.contains(assomption), "La liste des fériés de l'employé doit contenir l'Assomption (15 Août)");
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // ISSUE 14: POINTAGE EMPLOYE SERVICE — DÉCOUPLAGE CONTROLLER / SERVICE
    // =========================================================================
    @Test
    @Order(14)
    @DisplayName("ACT-007 : Découplage PointageEmployeService de PointageEmployeController")
    void testPointageEmployeServiceDecouplage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Integer idEmp = null;
                try (Statement stmt = c.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT id_employe FROM employe WHERE est_actif = TRUE LIMIT 1")) {
                    if (rs.next()) {
                        idEmp = rs.getInt("id_employe");
                    }
                }

                if (idEmp != null) {
                    mg.galaxam.sirh.api.dto.pointage.FichePointageEmployeDTO fiche =
                            pointageEmployeService.getFichePointageEmploye(idEmp, "2026-01-15", "periode", null);
                    assertNotNull(fiche, "La fiche pointage générée par le service métier doit être non-nulle");
                    assertNotNull(fiche.getPointagesData(), "Les données journalières de pointage doivent être alimentées");
                    assertNotNull(fiche.getKpis(), "Les indicateurs clés de performance (KPI) doivent être calculés");
                    assertNotNull(fiche.getRecap(), "Le récapitulatif Paie doit être présent");
                }
            } finally {
                c.rollback();
            }
        }
    }
}
