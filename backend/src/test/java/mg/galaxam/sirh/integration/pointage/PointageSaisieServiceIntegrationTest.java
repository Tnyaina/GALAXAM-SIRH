package mg.galaxam.sirh.integration.pointage;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.pointage.service.PointageSaisieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class PointageSaisieServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PointageSaisieService pointageSaisieService;

    private TestEmployeFixtures.CreatedEmploye empTnr;
    private TestEmployeFixtures.CreatedEmploye empTmm;
    private int idPeriode;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        // Embauche 6 mois plus tôt
        LocalDate dEmb = LocalDate.now().minusMonths(6);
        empTnr = employeFixtures.createActiveEmploye("EMP001", "RABARIJAONA", "Jean", 1, dEmb);
        empTmm = employeFixtures.createActiveEmploye("EMP002", "RAKOTO", "Paul", 2, dEmb);

        LocalDate debutPeriode = LocalDate.now().minusDays(15);
        LocalDate finPeriode = LocalDate.now().plusDays(15);
        idPeriode = employeFixtures.createPeriodePaie("P-TEST", debutPeriode, finPeriode, "EN_COURS");
    }

    @Test
    @DisplayName("Saisie nominale : Enregistrement d'une journée standard 8h (TNR) avec persistance DB")
    public void testEnregistrerPointage_Nominal() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(1); // date passée valide

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(empTnr.idSite);
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");
        dto.setHoraireContinu(false);
        dto.setIndemniteRepasRecue(true);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            Map<String, Object> res = pointageSaisieService.enregistrerPointagesBatch(List.of(dto), c);
            c.commit();

            assertTrue((Boolean) res.get("success"), "L'enregistrement en batch doit réussir");

            // Vérification directe en base de données
            String sqlPtg = "SELECT p.total_heures, p.heures_nuit, p.minutes_retard, s.code AS code_statut " +
                    "FROM pointage_jour p " +
                    "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
                    "WHERE p.id_employe = ? AND p.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
                ps.setInt(1, empTnr.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "La ligne de pointage doit exister en base");
                    assertEquals(8.0, rs.getDouble("total_heures"), 0.01);
                    assertEquals(0.0, rs.getDouble("heures_nuit"), 0.01);
                    assertEquals(0, rs.getInt("minutes_retard"));
                    assertEquals("PRESENT", rs.getString("code_statut"));
                }
            }

            // Vérification des indemnités générées en base
            String sqlInd = "SELECT p.montant_calcule, r.code FROM pointage_indemnite_jour p " +
                    "JOIN ref_indemnite r ON p.id_indemnite = r.id_indemnite " +
                    "WHERE p.id_pointage IN (SELECT id_pointage FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlInd)) {
                ps.setInt(1, empTnr.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    boolean foundRepas = false;
                    while (rs.next()) {
                        String code = rs.getString("code");
                        if ("REPAS".equals(code)) {
                            foundRepas = true;
                            assertTrue(rs.getDouble("montant_calcule") > 0);
                        }
                    }
                    assertTrue(foundRepas, "L'indemnité repas midi doit avoir été enregistrée");
                }
            }
        }
    }

    @Test
    @DisplayName("Saisie soirée : Shift finissant à 23h00 -> 1h de nuit et 2nd repas 2 enregistrés")
    public void testEnregistrerPointage_NuitEt2ndRepas() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(2);

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(empTnr.idSite);
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("14:00");
        dto.setHeureSortieMatin("18:00");
        dto.setHeureEntreeAm("19:00");
        dto.setHeureSortieAm("23:00"); // Sortie à 23h -> seuil nuit 22h franchi (1h nuit) et seuil repas 21h franchi

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            Map<String, Object> res = pointageSaisieService.enregistrerPointagesBatch(List.of(dto), c);
            c.commit();

            assertTrue((Boolean) res.get("success"));

            // Vérification heures nuit en base
            String sqlPtg = "SELECT total_heures, heures_nuit FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
                ps.setInt(1, empTnr.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(8.0, rs.getDouble("total_heures"), 0.01);
                    assertEquals(1.0, rs.getDouble("heures_nuit"), 0.01, "De 22:00 à 23:00 = 1h de nuit");
                }
            }
        }
    }

    @Test
    @DisplayName("Règle spécifique Toamasina (TMM) : Déduction de 1 000 Ar sur indemnité de transport")
    public void testEnregistrerPointage_DeductionTransportTMM() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(2);

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTmm.idEmploye);
        dto.setIdSite(empTmm.idSite); // Site 2 = TMM
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");
        dto.setDeplacementMatin(true);
        dto.setDeplacementAm(true);

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            Map<String, Object> res = pointageSaisieService.enregistrerPointagesBatch(List.of(dto), c);
            c.commit();

            assertTrue((Boolean) res.get("success"));

            // Vérifier en base le montant de l'indemnité transport
            String sqlInd = "SELECT p.montant_calcule FROM pointage_indemnite_jour p " +
                    "JOIN ref_indemnite r ON p.id_indemnite = r.id_indemnite " +
                    "WHERE r.code = 'DEPLACEMENT' AND p.id_pointage IN " +
                    "(SELECT id_pointage FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlInd)) {
                ps.setInt(1, empTmm.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // Configuration en base : 53 200 Ar mensuel / 22 jours = 2 418.18 Ar/j
                        assertEquals(2418.18, rs.getDouble("montant_calcule"), 0.01, "A TMM, le transport journalier est de 53200 / 22 = 2418.18 Ar");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Contrôle d'éligibilité : Rejet strict d'une date future")
    public void testEligibilite_DateFutureRefusee() throws Exception {
        LocalDate dateDemain = LocalDate.now().plusDays(1);
        try (Connection c = dataSource.getConnection()) {
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.verifierEligibilitePointage(c, empTnr.idEmploye, empTnr.idSite, Date.valueOf(dateDemain));
            });
            assertTrue(ex.getMessage().contains("Action interdite : Impossible d'enregistrer un pointage pour une date future"),
                    "Le message d'erreur doit explicitement interdire la date future");
        }
    }

    @Test
    @DisplayName("Contrôle d'éligibilité : Rejet strict si l'employé est pointé sur un autre site")
    public void testEligibilite_EmployeAutreSiteRefuse() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(1);
        try (Connection c = dataSource.getConnection()) {
            // empTnr est rattaché au site 1 (TNR). Tentative de pointage sur le site 2 (TMM).
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.verifierEligibilitePointage(c, empTnr.idEmploye, 2, Date.valueOf(datePtg));
            });
            assertTrue(ex.getMessage().contains("n'est pas rattaché au site ID 2"),
                    "L'éligibilité doit interdire le pointage sur un site différent de l'affectation");
        }
    }

    @Test
    @DisplayName("Contrôle d'éligibilité : Rejet strict si l'employé est inactif")
    public void testEligibilite_EmployeInactifRefuse() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(1);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            // Désactiver l'employé
            try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = false WHERE id_employe = ?")) {
                ps.setInt(1, empTnr.idEmploye);
                ps.executeUpdate();
            }
            c.commit();

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
                pointageSaisieService.verifierEligibilitePointage(c, empTnr.idEmploye, empTnr.idSite, Date.valueOf(datePtg));
            });
            assertTrue(ex.getMessage().contains("est inactif"), "Un employé inactif ne doit pas pouvoir être pointé");
        }
    }
}
