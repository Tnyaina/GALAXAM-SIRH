package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Chaîne réelle JDBC : contrat et journée saisis, snapshot figé, bulletin calculé. */
@SpringBootTest
@ActiveProfiles("test")
class PayrollPointageToBulletinIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired PointageSyntheseService pointageSyntheseService;
    @Autowired PayrollService payrollService;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void uneJourneeCompleteProduitUnBulletinAvecSaProvenancePointage(boolean complementDaf) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTestDatabase(connection);
            connection.setAutoCommit(false);
            try {
                int employeeId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, "
                                + "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) "
                                + "VALUES (?, 'Recette', 'Paie', 'M', '1990-01-15', 'Antananarivo', ?, "
                                + "'2010-01-15', 'TNR', 'Rue Test', '0340000000') RETURNING id_employe")) {
                    statement.setString(1, "E2E-" + Long.toString(System.nanoTime(), 36).toUpperCase());
                    statement.setString(2, String.format("%012d", Math.abs(System.nanoTime() % 1_000_000_000_000L)));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        employeeId = result.getInt(1);
                    }
                }
                // Le jeu de tests partagé peut contenir des employés persistés par d'autres suites.
                // Les neutraliser uniquement dans cette transaction, puis tout annuler en sortie.
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE employe SET est_actif = FALSE WHERE id_employe <> ?")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                int contractId = CongeTestFixtureHelper.createContratActif(connection, employeeId,
                        LocalDate.of(2026, 10, 26));
                CongeTestFixtureHelper.createAffectation(connection, employeeId, contractId,
                        LocalDate.of(2026, 10, 26), new BigDecimal("350000"));
                int periodId = CongeTestFixtureHelper.createPeriodePaie(connection,
                        "E2E-" + employeeId, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), "VALIDEE");
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, "
                                + "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, "
                                + "heure_entree_matin_effective, total_heures, version) "
                                + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), "
                                + "'08:00', '12:00', '13:00', '17:00', '08:00', 8, 1)")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    statement.setDate(3, Date.valueOf("2026-10-26"));
                    statement.executeUpdate();
                    statement.setDate(3, Date.valueOf("2026-10-27"));
                    statement.executeUpdate();
                }
                if (complementDaf) {
                    try (var statement=connection.prepareStatement(
                            "INSERT INTO pointage_ajustement_periode (id_employe,id_periode_paie,minutes_signees,motif,saisi_par,valide_par) "
                            + "VALUES (?,?,600,'Complément fin de période validé DAF',"
                            + "(SELECT MIN(id_utilisateur) FROM utilisateur),(SELECT MIN(id_utilisateur) FROM utilisateur))")) {
                        statement.setInt(1,employeeId);statement.setInt(2,periodId);statement.executeUpdate();
                    }
                }
                var preflight = pointageSyntheseService.preflightPeriode(periodId, connection);
                assertTrue(preflight.pret(), () -> "Préflight : " + preflight);
                pointageSyntheseService.gelerPeriode(periodId, null, connection);

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, id_type_mouvement, "
                        + "sens, montant, montant_initial, solde_restant, motif, statut) "
                        + "SELECT ?, ?, code, id_type_mouvement, sens, 10000, 10000, 10000, 'Recette recalcul', 'APPROUVE' "
                        + "FROM ref_type_mouvement_paie WHERE code IN ('PRIME_EXCEPTIONNELLE', 'ACOMPTE_15')")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    assertEquals(2, statement.executeUpdate());
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE paie_avance_acompte SET statut = 'EN_ATTENTE' "
                        + "WHERE id_employe = ? AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE paie_avance_acompte SET date_paiement = '2026-10-26', " +
                        "versement_confirme_at = CURRENT_TIMESTAMP, reference_versement = 'RECETTE-E2E' " +
                        "WHERE id_employe = ? AND type_mouvement = 'ACOMPTE_15'")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                int avanceId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, id_type_mouvement, " +
                        "sens, montant, montant_initial, solde_restant, motif, statut, date_paiement, " +
                        "versement_confirme_at, reference_versement) " +
                        "SELECT ?, ?, code, id_type_mouvement, sens, 100000, 100000, 100000, " +
                        "'Avance versée pour recette', 'APPROUVE', '2026-10-26', CURRENT_TIMESTAMP, 'RECETTE-AVANCE' " +
                        "FROM ref_type_mouvement_paie WHERE code = 'AVANCE_SPECIALE' RETURNING id_avance")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    try (ResultSet rows = statement.executeQuery()) { rows.next(); avanceId = rows.getInt(1); }
                }
                int avanceNonVerseeId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, id_type_mouvement, " +
                        "sens, montant, montant_initial, solde_restant, motif, statut) " +
                        "SELECT ?, ?, code, id_type_mouvement, sens, 20000, 20000, 20000, " +
                        "'Avance approuvée sans preuve de versement', 'APPROUVE' " +
                        "FROM ref_type_mouvement_paie WHERE code = 'AVANCE_SPECIALE' RETURNING id_avance")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    try (ResultSet rows = statement.executeQuery()) { rows.next(); avanceNonVerseeId = rows.getInt(1); }
                }
                int loanId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, "
                        + "nb_echeances, date_premiere_echeance, capital_restant_du, statut, motif) "
                        + "VALUES (?, ?, 12000, 12000, 1, '2026-10-27', 12000, 'EN_COURS', 'Recette recalcul') "
                        + "RETURNING id_pret")) {
                    statement.setInt(1, employeeId);
                    statement.setString(2, "LOAN-E2E-" + employeeId);
                    try (ResultSet rows = statement.executeQuery()) { rows.next(); loanId = rows.getInt(1); }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO paie_pret_echeance (id_pret, numero_echeance, date_echeance, "
                        + "montant_echeance, capital_restant_apres, montant_restant_echeance) "
                        + "VALUES (?, 1, '2026-10-27', 12000, 0, 12000)")) {
                    statement.setInt(1, loanId);
                    statement.executeUpdate();
                }

                assertThrows(IllegalStateException.class, () -> payrollService.calculerBulletinEmploye(
                        employeeId, periodId, false, null, connection));
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE paie_avance_acompte SET statut = 'APPROUVE' "
                        + "WHERE id_employe = ? AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    statement.setInt(1, employeeId);
                    statement.executeUpdate();
                }
                var result = payrollService.calculerBulletinEmploye(employeeId, periodId,
                        false, null, connection);
                assertNotNull(result.getBulletin().getIdBulletin());
                var cockpit = payrollService.obtenirCockpitPeriode(periodId, connection);
                assertEquals(periodId, ((Map<?, ?>) cockpit.get("periode")).get("idPeriodePaie"));
                assertTrue(((List<?>) cockpit.get("bulletins")).stream()
                        .anyMatch(row -> employeeId == (int) ((Map<?, ?>) row).get("idEmploye")));
                assertTrue(payrollService.listerBulletinsEmploye(employeeId, connection).stream()
                        .anyMatch(row -> periodId == (int) row.get("idPeriodePaie")));
                assertEquals(0, new BigDecimal("350000").compareTo(result.getBulletin().getSalaireBase()));
                assertTrue(result.getLignes().stream()
                        .anyMatch(line -> "SALAIRE_BASE".equals(line.getCodeRubrique())));
                assertNotNull(result.getBulletin().getIdSnapshotPointage());
                if (complementDaf) {
                    var hs30=result.getLignes().stream().filter(l->"HEURES_SUP_30".equals(l.getCodeRubrique())).findFirst().orElseThrow();
                    var hs50=result.getLignes().stream().filter(l->"HEURES_SUP_50".equals(l.getCodeRubrique())).findFirst().orElseThrow();
                    assertEquals(0,new BigDecimal("8").compareTo(hs30.getQuantite()));
                    assertEquals(0,new BigDecimal("2").compareTo(hs50.getQuantite()));
                    try(var ps=connection.prepareStatement(
                            "SELECT minutes_hs30_finales,minutes_hs50_finales,total_heures_travaillees FROM pointage_cumul_periode WHERE id_employe=? AND id_periode_paie=?")) {
                        ps.setInt(1,employeeId);ps.setInt(2,periodId);
                        try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(480,rs.getInt(1));assertEquals(120,rs.getInt(2));assertEquals(0,new BigDecimal("16").compareTo(rs.getBigDecimal(3)));}
                    }
                }
                var simulation = payrollService.calculerBulletinEmploye(employeeId, periodId,
                        true, null, connection);
                assertEquals(result.getBulletin().getTotalGainsBruts(), simulation.getBulletin().getTotalGainsBruts(),
                        "Une simulation après calcul doit conserver la prime déjà imputée");
                var recalcul = payrollService.calculerBulletinEmploye(employeeId, periodId,
                        false, null, connection);
                assertEquals(result.getBulletin().getIdBulletin(), recalcul.getBulletin().getIdBulletin());
                assertEquals(result.getBulletin().getNetAPayer(), recalcul.getBulletin().getNetAPayer(),
                        "Un recalcul ne doit ni perdre la prime ni prélever une seconde fois l'acompte ou le prêt");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE paie_avance_acompte SET statut = 'ANNULE', updated_at = CURRENT_TIMESTAMP "
                                + "WHERE id_employe = ? AND id_periode_paie = ? AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    assertEquals(1, statement.executeUpdate());
                }
                var sansPrime = payrollService.calculerBulletinEmploye(employeeId, periodId, true, null, connection);
                assertTrue(sansPrime.getBulletin().getNetAPayer().compareTo(recalcul.getBulletin().getNetAPayer()) < 0,
                        "La simulation doit exclure une prime imputée puis annulée");
                var corrige = payrollService.calculerBulletinEmploye(employeeId, periodId, false, null, connection);
                assertEquals(sansPrime.getBulletin().getNetAPayer(), corrige.getBulletin().getNetAPayer());
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT statut FROM paie_avance_acompte WHERE id_employe = ? AND id_periode_paie = ? "
                                + "AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    statement.setInt(1, employeeId);
                    statement.setInt(2, periodId);
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals("ANNULE", rows.getString(1), "Le recalcul ne doit pas réactiver la prime annulée");
                    }
                }
                BigDecimal imputePremierePeriode;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT montant_impute, solde_restant, date_paiement FROM paie_avance_acompte WHERE id_avance = ?")) {
                    statement.setInt(1, avanceId);
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        imputePremierePeriode = rows.getBigDecimal("montant_impute");
                        assertTrue(imputePremierePeriode.signum() > 0 && imputePremierePeriode.compareTo(new BigDecimal("100000")) < 0);
                        assertEquals(Date.valueOf("2026-10-26"), rows.getDate("date_paiement"),
                                "Le calcul ne doit pas remplacer la date du versement réel");
                    }
                }
                int nextPeriod = CongeTestFixtureHelper.createPeriodePaie(connection,
                        "E2E-SUIV-" + employeeId, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3), "VALIDEE");
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, " +
                        "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                        "heure_entree_matin_effective, total_heures, version) " +
                        "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), " +
                        "'08:00', '12:00', '13:00', '17:00', '08:00', 8, 1)")) {
                    statement.setInt(1, employeeId); statement.setInt(2, nextPeriod);
                    statement.setDate(3, Date.valueOf("2026-11-02")); statement.executeUpdate();
                    statement.setDate(3, Date.valueOf("2026-11-03")); statement.executeUpdate();
                }
                assertTrue(pointageSyntheseService.preflightPeriode(nextPeriod, connection).pret());
                pointageSyntheseService.gelerPeriode(nextPeriod, null, connection);
                payrollService.calculerBulletinEmploye(employeeId, nextPeriod, false, null, connection);
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT a.montant_impute, a.solde_restant, a.id_periode_paie, COUNT(i.id_imputation) AS nb " +
                        "FROM paie_avance_acompte a JOIN paie_mouvement_imputation i ON i.id_avance = a.id_avance " +
                        "WHERE a.id_avance = ? GROUP BY a.id_avance")) {
                    statement.setInt(1, avanceId);
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        assertTrue(rows.getBigDecimal("montant_impute").compareTo(imputePremierePeriode) > 0,
                                "Le reliquat versé doit être retenu sur la période suivante");
                        assertEquals(0, rows.getBigDecimal("montant_impute").add(rows.getBigDecimal("solde_restant"))
                                .compareTo(new BigDecimal("100000")));
                        assertEquals(periodId, rows.getInt("id_periode_paie"), "La période d'origine reste inchangée");
                        assertEquals(2, rows.getInt("nb"), "Chaque retenue est tracée par bulletin");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT montant_impute, solde_restant FROM paie_avance_acompte WHERE id_avance = ?")) {
                    statement.setInt(1, avanceNonVerseeId);
                    try (ResultSet rows = statement.executeQuery()) {
                        assertTrue(rows.next());
                        assertEquals(0, rows.getBigDecimal("montant_impute").compareTo(BigDecimal.ZERO),
                                "Une approbation seule ne déclenche aucune retenue");
                        assertEquals(0, rows.getBigDecimal("solde_restant").compareTo(new BigDecimal("20000")));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT capital_rembourse FROM paie_pret WHERE id_pret = ?")) {
                    statement.setInt(1, loanId);
                    try (ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        assertEquals(0, new BigDecimal("12000").compareTo(rows.getBigDecimal(1)));
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void objectifHebdomadaireRespecteLesDatesDentreeEtDeSortieEnCoursDeSemaine() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTestDatabase(connection);
            connection.setAutoCommit(false);
            try {
                int entrant = insertEmployee(connection, "ENTREE");
                int sortant = insertEmployee(connection, "SORTIE");
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE employe SET est_actif = FALSE WHERE id_employe NOT IN (?, ?)")) {
                    statement.setInt(1, entrant);
                    statement.setInt(2, sortant);
                    statement.executeUpdate();
                }
                LocalDate monday = LocalDate.of(2026, 10, 19);
                int entrantContract = CongeTestFixtureHelper.createContratActif(connection, entrant, monday.plusDays(1));
                int entrantAssignment = CongeTestFixtureHelper.createAffectation(connection, entrant, entrantContract,
                        monday.plusDays(1), new BigDecimal("350000"));
                int sortantContract = CongeTestFixtureHelper.createContratActif(connection, sortant, monday.minusDays(7));
                int sortantAssignment = CongeTestFixtureHelper.createAffectation(connection, sortant, sortantContract,
                        monday.minusDays(7), new BigDecimal("350000"));
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE employe_affectation_carriere SET id_categorie_professionnelle = 2 "
                                + "WHERE id_affectation IN (?, ?)")) {
                    statement.setInt(1, entrantAssignment);
                    statement.setInt(2, sortantAssignment);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    statement.setDate(1, Date.valueOf(monday.plusDays(2)));
                    statement.setInt(2, sortantContract);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = ? WHERE id_affectation = ?")) {
                    statement.setDate(1, Date.valueOf(monday.plusDays(2)));
                    statement.setInt(2, sortantAssignment);
                    statement.executeUpdate();
                }
                int periodId = CongeTestFixtureHelper.createPeriodePaie(connection,
                        "PW-" + Long.toUnsignedString(System.nanoTime(), 36).toUpperCase(),
                        monday, monday.plusDays(4), "EN_COURS");
                insertPresentPointages(connection, entrant, periodId, monday.plusDays(1), monday.plusDays(4), 10);
                insertPresentPointages(connection, sortant, periodId, monday, monday.plusDays(2), 10);

                var syntheses = pointageSyntheseService.getSynthesePeriode(periodId, null, connection);
                var entrantSynthese = syntheses.stream().filter(value -> value.getIdEmploye() == entrant).findFirst().orElseThrow();
                var sortantSynthese = syntheses.stream().filter(value -> value.getIdEmploye() == sortant).findFirst().orElseThrow();

                assertEquals(32.0, entrantSynthese.getTronconsSemaine().get(0).getSeuilHeuresTroncon());
                assertEquals(0.0, entrantSynthese.getTronconsSemaine().get(0).getHeuresDeficit());
                assertEquals(24.0, sortantSynthese.getTronconsSemaine().get(0).getSeuilHeuresTroncon());
                assertEquals(0.0, sortantSynthese.getTronconsSemaine().get(0).getHeuresDeficit());
                assertEquals(6.4, entrantSynthese.getTronconsSemaine().get(0).getHeuresHs30(), 0.001);
                assertEquals(1.6, entrantSynthese.getTronconsSemaine().get(0).getHeuresHs50(), 0.001);
                assertEquals(4.8, sortantSynthese.getTronconsSemaine().get(0).getHeuresHs30(), 0.001);
                assertEquals(1.2, sortantSynthese.getTronconsSemaine().get(0).getHeuresHs50(), 0.001);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void employeInactifSurPeriodeEstExcluOuRejeteEnPaie() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertTestDatabase(connection);
            connection.setAutoCommit(false);
            try {
                int employeeId = insertEmployee(connection, "Inactif");
                // Contrat terminé dans le passé (2020)
                int contractId = CongeTestFixtureHelper.createContratActif(connection, employeeId, LocalDate.of(2020, 1, 1));
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE contrat_travail SET date_fin = '2020-03-01', id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'CLOTURE') WHERE id_contrat = ?")) {
                    ps.setInt(1, contractId);
                    ps.executeUpdate();
                }
                CongeTestFixtureHelper.createAffectation(connection, employeeId, contractId, LocalDate.of(2020, 1, 1), new BigDecimal("350000"));
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = '2020-03-01' WHERE id_contrat = ?")) {
                    ps.setInt(1, contractId);
                    ps.executeUpdate();
                }

                int periodId = CongeTestFixtureHelper.createPeriodePaie(connection,
                        "E2E-" + employeeId, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), "VALIDEE");

                // 1. preparerInput doit lever SirhBusinessException avec PAYROLL_EMPLOYEE_INACTIVE
                mg.galaxam.sirh.common.exception.SirhBusinessException ex = assertThrows(
                        mg.galaxam.sirh.common.exception.SirhBusinessException.class,
                        () -> payrollService.preparerInput(employeeId, periodId, true, connection));
                assertEquals(mg.galaxam.sirh.common.exception.SirhErrorCode.PAYROLL_EMPLOYEE_INACTIVE, ex.getErrorCode());

                // 2. PayrollPopulationResolver doit exclure l'employé
                var resolver = new PayrollPopulationResolver();
                var population = resolver.resolve(periodId, connection);
                var memberOpt = population.members().stream().filter(m -> m.employeeId() == employeeId).findFirst();
                assertTrue(memberOpt.isPresent());
                assertEquals("EXCLUDED", memberOpt.get().decision());
                assertEquals("NO_APPLICABLE_ASSIGNMENT", memberOpt.get().reasonCode());
            } finally {
                connection.rollback();
            }
        }
    }

    private int insertEmployee(Connection connection, String label) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO employe (matricule, nom, prenom, sexe, date_naissance, lieu_naissance, "
                        + "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) "
                        + "VALUES (?, ?, 'Test', 'M', '1990-01-15', 'Antananarivo', ?, '2010-01-15', 'TNR', 'Rue Test', '0340000000') "
                        + "RETURNING id_employe")) {
            String suffix = Long.toUnsignedString(System.nanoTime(), 36).toUpperCase();
            statement.setString(1, "PW-" + suffix);
            statement.setString(2, "Partial " + label);
            statement.setString(3, String.format("%012d", Math.abs(System.nanoTime() % 1_000_000_000_000L)));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private void insertPresentPointages(Connection connection, int employeeId, int periodId,
            LocalDate start, LocalDate end, int hours) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, "
                        + "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, "
                        + "heure_entree_matin_effective, total_heures, version) "
                        + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), "
                        + "'08:00', '12:00', '13:00', ?, '08:00', ?, 1)")) {
            statement.setInt(1, employeeId);
            statement.setInt(2, periodId);
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                statement.setDate(3, Date.valueOf(day));
                statement.setTime(4, java.sql.Time.valueOf(hours > 8 ? "19:00:00" : "17:00:00"));
                statement.setInt(5, hours);
                statement.executeUpdate();
            }
        }
    }

    private void assertTestDatabase(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_database()")) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals("sirh_galaxam_test", result.getString(1),
                        "Aucune écriture de test ne doit être faite hors sirh_galaxam_test");
            }
        }
    }
}
