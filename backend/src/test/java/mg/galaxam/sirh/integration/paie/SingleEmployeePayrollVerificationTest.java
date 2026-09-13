package mg.galaxam.sirh.integration.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculResultDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.StcService;
import mg.galaxam.sirh.model.paie.service.export.DntsExportService;
import mg.galaxam.sirh.model.paie.service.export.SafiExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Integration Tests - Vérification Unitaire et Déterministe sur un Employé Unique")
class SingleEmployeePayrollVerificationTest extends BaseIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private StcService stcService;

    @Autowired
    private SafiExportService safiExportService;

    @Autowired
    private DntsExportService dntsExportService;

    private int idPeriode;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        // Période de paie standard : Janvier 2026 (26/12/2025 au 25/01/2026)
        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-01",
                LocalDate.of(2025, 12, 26),
                LocalDate.of(2026, 1, 25),
                "OUVERTE"
        );
    }

    private void insererPointageScelle(int idEmp, int ouvrables, int presents, double hs30, double hs50) {
        String sql = "INSERT INTO pointage_cumul_periode (" +
                "id_periode_paie, id_employe, nb_jours_ouvrables, nb_jours_presents, " +
                "total_heures_travaillees, heures_hs_30, heures_hs_50) " +
                "VALUES (?, ?, ?, ?, 173.33, ?, ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, idEmp);
            ps.setInt(3, ouvrables);
            ps.setInt(4, presents);
            ps.setDouble(5, hs30);
            ps.setDouble(6, hs50);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("1. Employé Unique Permanent - Calcul Mensuel Déterministe (Base 1 500 000 Ar, Avance, Prêt, 2 Enfants)")
    void testCalculEmployeUniquePermanent() throws Exception {
        // Employé test permanent avec salaire de base 1 500 000 Ar et 2 enfants à charge
        CreatedEmploye emp = employeFixtures.createEmployeWithSalary(
                "EMP-TEST-01",
                "RAKOTO",
                "Jean",
                1,
                new BigDecimal("1500000.00"),
                "PERMANENT",
                2
        );

        // Pointage scellé complet : 22 jours ouvrables, 22 jours présents, 2h HS 30%, 1h HS 50%
        insererPointageScelle(emp.idEmploye, 22, 22, 2.0, 1.0);

        // Avance quinzaine approuvée : 100 000 Ar
        employeFixtures.createAvanceAcompte(
                emp.idEmploye,
                idPeriode,
                "ACOMPTE_15",
                "RETENUE",
                new BigDecimal("100000.00"),
                "APPROUVE"
        );

        // Prêt en cours : 1 000 000 Ar avec échéance de 50 000 Ar
        employeFixtures.createPretAvecEcheances(
                emp.idEmploye,
                new BigDecimal("1000000.00"),
                new BigDecimal("50000.00"),
                20
        );

        // Exécution du calcul pour cet employé unique en mode réel (non-simulation)
        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(emp.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        // 1. Contrôles En-Tête
        assertEquals("MENSUEL", bp.getTypeBulletin());
        assertEquals("EMP-TEST-01", bp.getMatricule());
        assertEquals(new BigDecimal("1500000.00"), bp.getSalaireBase());
        assertEquals("VALIDE", bp.getStatut());

        // Total gains bruts : Salaire base (1.5M) + HS 30% (2h) + HS 50% (1h)
        assertTrue(bp.getTotalGainsBruts().compareTo(new BigDecimal("1500000.00")) > 0, "Les gains bruts doivent inclure les heures supplémentaires");

        // 2. Cotisations Sociales
        // CNaPS Salariale 1% plafonnée à 8 x SME (2 101 440 Ar) -> Base 1.5M < 2.1M -> CNaPS sal = env 15 000 Ar
        assertTrue(bp.getTotalCotisationsSalariales().compareTo(BigDecimal.ZERO) > 0);
        // CNaPS Patronale 13% + OSTIE 5% + FMFP 1% -> Total cotisations patronales > 0
        assertTrue(bp.getTotalCotisationsPatronales().compareTo(BigDecimal.ZERO) > 0);

        // 3. Fiscalité IRSA & Déduction enfants
        assertTrue(bp.getSalaireNetImposable().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(bp.getIrsaNet().compareTo(BigDecimal.ZERO) > 0);
        assertEquals(new BigDecimal("4000.00"), bp.getReductionEnfants(), "2 enfants x 2 000 Ar = 4 000 Ar de réduction");

        // 4. Retenues Trésorerie (Avance 100 000 Ar + Échéance Prêt 50 000 Ar = 150 000 Ar)
        assertEquals(new BigDecimal("150000.00"), bp.getTotalRetenuesAcomptePret());

        // 5. Net à payer (Arrondi monétaire à la dizaine d'Ariary)
        assertNotNull(bp.getNetAPayer());
        assertEquals(bp.getNetAPayer(), bp.getNetAPayer().divide(BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.TEN).setScale(2));

        // 6. Cohérence des lignes
        assertFalse(bpc.getLignes().isEmpty());
        boolean hasBase = false;
        boolean hasCnaps = false;
        boolean hasIrsa = false;
        boolean hasAcompte = false;
        boolean hasPret = false;
        for (BulletinPaieLigne l : bpc.getLignes()) {
            if ("SALAIRE_BASE".equals(l.getCodeRubrique())) hasBase = true;
            if ("COTISATION_CNAPS_SAL".equals(l.getCodeRubrique())) hasCnaps = true;
            if ("IMPOT_IRSA_NET".equals(l.getCodeRubrique())) hasIrsa = true;
            if ("ACOMPTE_15".equals(l.getCodeRubrique()) || "RETENUE_ACOMPTE_15".equals(l.getCodeRubrique())) hasAcompte = true;
            if ("RETENUE_ECHEANCE_PRET".equals(l.getCodeRubrique())) hasPret = true;
        }
        assertTrue(hasBase, "La ligne de salaire de base doit être présente");
        assertTrue(hasCnaps, "La ligne de CNaPS salariale doit être présente");
        assertTrue(hasIrsa, "La ligne d'IRSA net doit être présente");
        assertTrue(hasAcompte, "La ligne d'acompte de 100 000 Ar doit être présente");
        assertTrue(hasPret, "La ligne d'échéance de prêt de 50 000 Ar doit être présente");
    }

    @Test
    @DisplayName("2. Employé Unique Sortant - Validation STC Déterministe (Prorata, ICCP, Préavis, Licenciement, Charges Patronales, Apurement)")
    void testCalculEmployeUniqueStcEtPreservation() throws Exception {
        // Employé test sortant avec salaire de base 1 200 000 Ar
        CreatedEmploye empSortant = employeFixtures.createEmployeWithSalary(
                "EMP-SORTANT-02",
                "RANDRIA",
                "Michel",
                1,
                new BigDecimal("1200000.00"),
                "PERMANENT",
                0
        );

        // Crédit de 10 jours de congés
        employeFixtures.crediterSoldeConge(empSortant.idEmploye, 10.0);

        // Prêt en cours avec solde de 150 000 Ar
        employeFixtures.createPretAvecEcheances(
                empSortant.idEmploye,
                new BigDecimal("150000.00"),
                new BigDecimal("50000.00"),
                3
        );

        // Requête de Solde de Tout Compte
        StcCalculRequest stcReq = new StcCalculRequest();
        stcReq.setIdEmploye(empSortant.idEmploye);
        stcReq.setIdPeriodePaie(idPeriode);
        stcReq.setDateDepart(Date.valueOf("2026-01-15"));
        stcReq.setMotifRupture("LICENCIEMENT_ECONOMIQUE");
        stcReq.setJoursTravaillesDernierMois(new BigDecimal("10.0"));
        stcReq.setMoisPreavis(new BigDecimal("1.0")); // 1 mois de préavis payé
        stcReq.setIndemniteLicenciement(new BigDecimal("400000.00"));
        stcReq.setModeReglement("VIREMENT");

        StcCalculResultDTO stcRes;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            stcRes = stcService.validerStc(stcReq, 1, c);
            c.commit();
        }

        assertNotNull(stcRes);
        assertNotNull(stcRes.getNumeroRecu());
        assertEquals("VALIDE", stcRes.getStatut());
        assertNotNull(stcRes.getIdBulletin());

        // 1. Vérification en base de données de l'entête du bulletin STC
        BulletinPaie bpStc;
        List<BulletinPaieLigne> lignesStc;
        try (Connection c = dataSource.getConnection()) {
            bpStc = BulletinPaie.trouverParId(stcRes.getIdBulletin(), c);
            lignesStc = BulletinPaieLigne.listerParBulletin(stcRes.getIdBulletin(), c);
        }

        assertNotNull(bpStc);
        assertEquals("STC", bpStc.getTypeBulletin(), "Le bulletin doit avoir le type officiel STC");
        assertEquals("VALIDE", bpStc.getStatut());
        assertNotNull(bpStc.getIdConfigLegale(), "L'id_config_legale doit être obligatoirement renseigné (snapshot)");
        assertNotNull(bpStc.getIdTypeBulletin(), "L'id_type_bulletin doit être renseigné");
        assertNotNull(bpStc.getIdModePaiement(), "L'id_mode_paiement doit être renseigné");

        // Vérification des cotisations patronales STC (VULN-03 résolu)
        assertTrue(bpStc.getTotalCotisationsPatronales().compareTo(BigDecimal.ZERO) > 0,
                "Le STC doit obligatoirement comporter des cotisations patronales CNaPS/Santé/FMFP");

        // 2. Vérification de la ventilation complète des lignes STC (VULN-04 résolu)
        assertFalse(lignesStc.isEmpty());
        boolean hasProrata = false;
        boolean hasIccp = false;
        boolean hasPreavis = false;
        boolean hasLicenciement = false;
        boolean hasCnapsPat = false;
        boolean hasFmfpPat = false;
        boolean hasSoldePret = false;

        for (BulletinPaieLigne l : lignesStc) {
            if ("SALAIRE_BASE_PRORATA".equals(l.getCodeRubrique())) hasProrata = true;
            if ("INDEMNITE_COMPENSATRICE_CONGE".equals(l.getCodeRubrique())) hasIccp = true;
            if ("INDEMNITE_PREAVIS".equals(l.getCodeRubrique())) hasPreavis = true;
            if ("INDEMNITE_LICENCIEMENT".equals(l.getCodeRubrique())) hasLicenciement = true;
            if ("COTISATION_CNAPS_PAT".equals(l.getCodeRubrique())) hasCnapsPat = true;
            if ("COTISATION_FMFP_PAT".equals(l.getCodeRubrique())) hasFmfpPat = true;
            if ("SOLDE_PRET_STC".equals(l.getCodeRubrique())) hasSoldePret = true;
            assertNotNull(l.getIdRubrique(), "id_rubrique doit être renseigné pour la ligne " + l.getCodeRubrique());
        }

        assertTrue(hasProrata, "La ligne SALAIRE_BASE_PRORATA doit être enregistrée");
        assertTrue(hasIccp, "La ligne INDEMNITE_COMPENSATRICE_CONGE doit être enregistrée");
        assertTrue(hasPreavis, "La ligne INDEMNITE_PREAVIS doit être enregistrée");
        assertTrue(hasLicenciement, "La ligne INDEMNITE_LICENCIEMENT doit être enregistrée");
        assertTrue(hasCnapsPat, "La cotisation patronale CNaPS (13%) doit être enregistrée dans les lignes du STC");
        assertTrue(hasFmfpPat, "La cotisation patronale FMFP (1%) doit être enregistrée dans les lignes du STC");
        assertTrue(hasSoldePret, "La retenue d'apurement du prêt doit être enregistrée dans les lignes du STC");

        // Vérification que les échéances du prêt sont bien rattachées au bulletin STC (AUDIT2-07)
        try (Connection c = dataSource.getConnection();
             PreparedStatement psEch = c.prepareStatement(
                     "SELECT id_bulletin FROM paie_pret_echeance WHERE id_pret IN (SELECT id_pret FROM paie_pret WHERE id_employe = ?) AND statut = 'REMBOURSEE_ANTICIPEE'")) {
            psEch.setInt(1, empSortant.idEmploye);
            try (ResultSet rsEch = psEch.executeQuery()) {
                assertTrue(rsEch.next(), "Une échéance soldée par anticipation doit exister");
                assertEquals(bpStc.getIdBulletin(), rsEch.getInt("id_bulletin"), "L'échéance soldée doit être liée à id_bulletin du STC");
            }
        }

        // 3. Test de NON-ÉCRASEMENT (VULN-01 résolu)
        // Calculons maintenant un bulletin mensuel pour ce même employé dans la même période
        insererPointageScelle(empSortant.idEmploye, 22, 10, 0, 0);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            payrollService.calculerBulletinEmploye(empSortant.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        // Vérifions que le bulletin STC existe toujours intact avec son reçu et ses lignes !
        BulletinPaie bpStcApresCalculMensuel;
        List<BulletinPaieLigne> lignesStcApres;
        try (Connection c = dataSource.getConnection()) {
            bpStcApresCalculMensuel = BulletinPaie.trouverParId(stcRes.getIdBulletin(), c);
            lignesStcApres = BulletinPaieLigne.listerParBulletin(stcRes.getIdBulletin(), c);
        }

        assertNotNull(bpStcApresCalculMensuel, "Le bulletin STC ne doit JAMAIS être supprimé");
        assertEquals("STC", bpStcApresCalculMensuel.getTypeBulletin());
        assertEquals(bpStc.getNetAPayer(), bpStcApresCalculMensuel.getNetAPayer(), "Le net STC ne doit pas avoir été écrasé");
        assertEquals(lignesStc.size(), lignesStcApres.size(), "Les lignes du STC doivent être intégralement préservées");

        // 4. Vérification de l'export SAFI (VULN-02 & AUDIT2-03 résolus)
        try (Connection c = dataSource.getConnection()) {
            List<SafiRowDto> safiRows = safiExportService.genererDonneesSafi(idPeriode, c);
            SafiRowDto rowSortant = safiRows.stream()
                    .filter(r -> "EMP-SORTANT-02".equals(r.getMatricule()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(rowSortant, "Le salarié sortant doit être présent dans l'export SAFI");
            assertTrue(rowSortant.getSalaireBase().compareTo(BigDecimal.ZERO) > 0,
                    "Le salaire prorata doit être déclaré en colonne Base (et non 0 Ar)");
            assertTrue(rowSortant.getIndemnitesImposables().compareTo(BigDecimal.ZERO) > 0,
                    "L'indemnité de préavis doit être ventilée en Colonne J (Indemnités imposables) et non en Base");
            assertEquals("15/01/2026", rowSortant.getDateDebauchage(),
                    "La date de débauchage doit être égale à la date de départ STC");
        }

        // 5. Vérification de l'export DNTS (VULN-03 & VULN-06 résolus)
        try (Connection c = dataSource.getConnection()) {
            List<DntsRowDto> dntsRows = dntsExportService.genererDonneesDnts(idPeriode, c);
            DntsRowDto dntsSortant = dntsRows.stream()
                    .filter(r -> "EMP-SORTANT-02".equals(r.getMatricule()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(dntsSortant, "Le salarié sortant doit être présent dans l'export DNTS");
            assertTrue(dntsSortant.getCnapsPatronal().compareTo(BigDecimal.ZERO) > 0,
                    "La cotisation patronale CNaPS (13%) doit être déclarée dans la DNTS");
            assertTrue(dntsSortant.getFmfpPatronal().compareTo(BigDecimal.ZERO) > 0,
                    "La cotisation patronale FMFP (1%) doit être déclarée dans la DNTS");
            assertEquals("15/01/2026", dntsSortant.getDateDebauchage(),
                    "La date de débauchage CDI doit être renseignée depuis paie_stc");
        }
    }

    @Test
    @DisplayName("3. Employé Unique - Traitement Dynamique de Prime Personnalisée en BDD (Sans Switch Hardcodé)")
    void testCalculEmployeUniqueAvecPrimeDynamiquePersonalisee() throws Exception {
        CreatedEmploye empDyn = employeFixtures.createEmployeWithSalary(
                "EMP-DYN-03",
                "RAVELO",
                "Faly",
                1,
                new BigDecimal("1000000.00"),
                "PERMANENT",
                0
        );

        insererPointageScelle(empDyn.idEmploye, 22, 22, 0, 0);

        // Insertion en BDD d'une prime personnalisée non hardcodée
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement psRub = c.prepareStatement(
                    "INSERT INTO paie_rubrique (code, libelle, categorie, type_valeur, sens, soumis_cnaps, soumis_sante, soumis_irsa) " +
                    "VALUES ('PRIME_RISQUE', 'Prime de risque métier', 'PRIME', 'MONTANT', 'GAIN', true, true, true) " +
                    "ON CONFLICT (code) DO NOTHING");
                 PreparedStatement psMvt = c.prepareStatement(
                    "INSERT INTO ref_type_mouvement_paie (code, libelle, sens, categorie, id_rubrique) " +
                    "VALUES ('PRIME_RISQUE', 'Prime de risque mensuelle', 'GAIN', 'PRIME', " +
                    "(SELECT id_rubrique FROM paie_rubrique WHERE code = 'PRIME_RISQUE')) " +
                    "ON CONFLICT (code) DO NOTHING");
                 PreparedStatement psAv = c.prepareStatement(
                    "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, sens, montant, date_demande, motif, statut, id_type_mouvement) " +
                    "VALUES (?, ?, 'PRIME_RISQUE', 'GAIN', 75000.00, CURRENT_DATE, 'Prime mensuelle risque', 'APPROUVE', " +
                    "(SELECT id_type_mouvement FROM ref_type_mouvement_paie WHERE code = 'PRIME_RISQUE'))")) {
                psRub.executeUpdate();
                psMvt.executeUpdate();
                psAv.setInt(1, empDyn.idEmploye);
                psAv.setInt(2, idPeriode);
                psAv.executeUpdate();
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }

        // Calcul du bulletin pour cet employé
        BulletinPaieComplet bpc;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            bpc = payrollService.calculerBulletinEmploye(empDyn.idEmploye, idPeriode, false, 1, c);
            c.commit();
        }

        assertNotNull(bpc);
        BulletinPaie bp = bpc.getBulletin();
        assertNotNull(bp);

        // Clés étrangères référentielles
        assertNotNull(bp.getIdTypeBulletin(), "id_type_bulletin doit être renseigné");
        assertNotNull(bp.getIdModePaiement(), "id_mode_paiement doit être renseigné");

        // Gains bruts = Base (1 000 000) + Prime risque (75 000) = 1 075 000
        assertEquals(new BigDecimal("1075000.00"), bp.getTotalGainsBruts(), "La prime dynamique doit être ajoutée au salaire brut");

        // Présence de la ligne ventilée
        boolean hasPrimeRisque = false;
        for (BulletinPaieLigne l : bpc.getLignes()) {
            if ("PRIME_RISQUE".equals(l.getCodeRubrique())) {
                hasPrimeRisque = true;
                assertEquals(new BigDecimal("75000.00"), l.getMontantSalarial());
                assertNotNull(l.getIdRubrique(), "id_rubrique de la prime personnalisée doit être résolu");
            }
        }
        assertTrue(hasPrimeRisque, "La ligne PRIME_RISQUE doit être présente dans les lignes calculées");

        // Imputation automatique de l'élément variable
        try (Connection c = dataSource.getConnection();
             PreparedStatement psChk = c.prepareStatement(
                     "SELECT statut, id_bulletin FROM paie_avance_acompte WHERE id_employe = ? AND type_mouvement = 'PRIME_RISQUE'")) {
            psChk.setInt(1, empDyn.idEmploye);
            try (ResultSet rsChk = psChk.executeQuery()) {
                assertTrue(rsChk.next());
                assertEquals("IMPUTE_PAIE", rsChk.getString("statut"), "Le mouvement doit être marqué IMPUTE_PAIE");
                assertEquals(bp.getIdBulletin(), rsChk.getInt("id_bulletin"), "Le mouvement doit être lié au bulletin");
            }
        }

        // Export SAFI dynamique (Colonne K: Primes)
        try (Connection c = dataSource.getConnection()) {
            List<SafiRowDto> safiRows = safiExportService.genererDonneesSafi(idPeriode, c);
            SafiRowDto rowDyn = safiRows.stream()
                    .filter(r -> "EMP-DYN-03".equals(r.getMatricule()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(rowDyn, "L'employé dynamique doit figurer dans SAFI");
            assertEquals(new BigDecimal("1000000.00"), rowDyn.getSalaireBase());
            assertEquals(new BigDecimal("75000.00"), rowDyn.getPrimesEtGratifications(), "La prime dynamique doit être reportée en colonne Primes SAFI");
        }
    }
}
