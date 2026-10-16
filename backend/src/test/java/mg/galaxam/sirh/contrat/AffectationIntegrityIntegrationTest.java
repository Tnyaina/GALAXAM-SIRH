package mg.galaxam.sirh.contrat;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDate;
import java.util.Properties;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.persistence.EmployeAffectationCarriere;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AffectationIntegrityIntegrationTest {

    private Connection c;
    private int employeeId;
    private int otherEmployeeId;
    private int contractId;
    private int otherContractId;
    private int otherDepartmentId;
    private int otherServiceId;

    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application-test.properties")) {
            properties.load(input);
        }
        c = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"));
        c.setAutoCommit(false);

        String suffix = Long.toUnsignedString(System.nanoTime());
        employeeId = CongeTestFixtureHelper.createEmploye(c, "AI" + suffix, "TEST", "Affectation");
        otherEmployeeId = CongeTestFixtureHelper.createEmploye(c, "AJ" + suffix, "TEST", "Contrat");
        contractId = CongeTestFixtureHelper.createContratActif(c, employeeId, LocalDate.of(2025, 1, 1));
        otherContractId = CongeTestFixtureHelper.createContratActif(c, otherEmployeeId, LocalDate.of(2025, 1, 1));

        try (PreparedStatement department = c.prepareStatement(
                "INSERT INTO ref_departement (code_departement, libelle) VALUES (?, ?) RETURNING id_departement")) {
            department.setString(1, "AI" + suffix.substring(Math.max(0, suffix.length() - 8)));
            department.setString(2, "Département test intégrité");
            try (ResultSet rs = department.executeQuery()) {
                assertTrue(rs.next());
                otherDepartmentId = rs.getInt(1);
            }
        }
        try (PreparedStatement service = c.prepareStatement(
                "INSERT INTO ref_service (code_service, libelle, id_departement) VALUES (?, ?, ?) RETURNING id_service")) {
            service.setString(1, "AI" + suffix.substring(Math.max(0, suffix.length() - 8)));
            service.setString(2, "Service test intégrité");
            service.setInt(3, otherDepartmentId);
            try (ResultSet rs = service.executeQuery()) {
                assertTrue(rs.next());
                otherServiceId = rs.getInt(1);
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        if (c != null) {
            c.rollback();
            c.close();
        }
    }

    @Test
    void accepteUneAffectationEtConserveLesVersionsSuccessives() throws Exception {
        EmployeAffectationCarriere first = affectation(employeeId, contractId, 1, 1,
                LocalDate.of(2025, 1, 1));
        first.save(c);

        // Une mutation interne peut changer de service/département sans changer le contrat.
        EmployeAffectationCarriere moved = affectation(employeeId, contractId, otherDepartmentId,
                otherServiceId, LocalDate.of(2025, 6, 1));
        moved.save(c);

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM employe_affectation_carriere WHERE id_employe = ? AND id_contrat = ?")) {
            ps.setInt(1, employeeId);
            ps.setInt(2, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "La nouvelle affectation ne doit pas écraser l'historique.");
            }
        }
    }

    @Test
    void refuseLeContratDUnAutreSalarieDansLaCoucheApplicative() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> affectation(employeeId, otherContractId, 1, 1, LocalDate.of(2025, 1, 1)).save(c));
        assertTrue(error.getMessage().contains("n'appartient pas"));
    }

    @Test
    void refuseLeServiceDUnAutreDepartementDansLaCoucheApplicative() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> affectation(employeeId, contractId, 1, otherServiceId, LocalDate.of(2025, 1, 1)).save(c));
        assertTrue(error.getMessage().contains("n'appartient pas"));
    }

    @Test
    void distingueLesIdentifiantsInexistants() {
        IllegalArgumentException missingEmployee = assertThrows(IllegalArgumentException.class,
                () -> affectation(-1, contractId, 1, 1, LocalDate.of(2025, 1, 1)).save(c));
        assertTrue(missingEmployee.getMessage().contains("employé") && missingEmployee.getMessage().contains("n'existe pas"));

        IllegalArgumentException missingContract = assertThrows(IllegalArgumentException.class,
                () -> affectation(employeeId, -1, 1, 1, LocalDate.of(2025, 1, 1)).save(c));
        assertTrue(missingContract.getMessage().contains("contrat") && missingContract.getMessage().contains("n'existe pas"));

        IllegalArgumentException missingService = assertThrows(IllegalArgumentException.class,
                () -> affectation(employeeId, contractId, 1, -1, LocalDate.of(2025, 1, 1)).save(c));
        assertTrue(missingService.getMessage().contains("service") && missingService.getMessage().contains("n'existe pas"));

        IllegalArgumentException missingDepartment = assertThrows(IllegalArgumentException.class,
                () -> affectation(employeeId, contractId, -1, 1, LocalDate.of(2025, 1, 1)).save(c));
        assertTrue(missingDepartment.getMessage().contains("département") && missingDepartment.getMessage().contains("n'existe pas"));
    }

    @Test
    void refuseUnServiceOuUnDepartementAbsent() {
        EmployeAffectationCarriere missingService = affectation(employeeId, contractId, 1, 1,
                LocalDate.of(2025, 1, 1));
        missingService.setIdService(null);
        IllegalArgumentException serviceError = assertThrows(IllegalArgumentException.class,
                () -> missingService.save(c));
        assertTrue(serviceError.getMessage().contains("service est obligatoire"));

        EmployeAffectationCarriere missingDepartment = affectation(employeeId, contractId, 1, 1,
                LocalDate.of(2025, 1, 1));
        missingDepartment.setIdDepartement(null);
        IllegalArgumentException departmentError = assertThrows(IllegalArgumentException.class,
                () -> missingDepartment.save(c));
        assertTrue(departmentError.getMessage().contains("département est obligatoire"));
    }

    @Test
    void postgresRefuseLesDeuxCouplesIncoherents() throws Exception {
        Savepoint beforeContract = c.setSavepoint();
        SQLException wrongContract = assertThrows(SQLException.class,
                () -> insertRawAffectation(employeeId, otherContractId, 1, 1));
        assertEquals("23503", wrongContract.getSQLState());
        c.rollback(beforeContract);

        Savepoint beforeService = c.setSavepoint();
        SQLException wrongService = assertThrows(SQLException.class,
                () -> insertRawAffectation(employeeId, contractId, 1, otherServiceId));
        assertEquals("23503", wrongService.getSQLState());
        c.rollback(beforeService);
    }

    @Test
    void contratRenouveleDuMemeSalariePeutPorterUneNouvelleAffectation() throws Exception {
        int firstAssignment = CongeTestFixtureHelper.createAffectation(c, employeeId, contractId,
                LocalDate.of(2025, 1, 1), new BigDecimal("1200000.00"));
        int renewedContract;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, "
                        + "id_statut_rh, id_contrat_precedent, date_debut) VALUES (?, 1, 1, 1, 4, ?, ?) "
                        + "RETURNING id_contrat")) {
            ps.setInt(1, employeeId);
            ps.setInt(2, contractId);
            ps.setDate(3, Date.valueOf(LocalDate.of(2026, 1, 1)));
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                renewedContract = rs.getInt(1);
            }
        }
        EmployeAffectationCarriere next = affectation(employeeId, renewedContract, 1, 1,
                LocalDate.of(2026, 1, 1));
        next.save(c);

        assertTrue(renewedContract > contractId);
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM employe_affectation_carriere WHERE id_affectation = ?")) {
            ps.setInt(1, firstAssignment);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
        }
    }

    private EmployeAffectationCarriere affectation(int employee, int contract, int department, int service,
                                                   LocalDate start) {
        EmployeAffectationCarriere value = new EmployeAffectationCarriere();
        value.setIdEmploye(employee);
        value.setIdContrat(contract);
        value.setIdStatutRh(4);
        value.setIdMotifMouvement(1);
        value.setIdSite(1);
        value.setIdDepartement(department);
        value.setIdService(service);
        value.setIdFonction(3);
        value.setIdCategorieProfessionnelle(1);
        value.setIdModeCalculPaie(1);
        value.setSalaireBase(new BigDecimal("1200000.00"));
        value.setDateDebut(Date.valueOf(start));
        return value;
    }

    private void insertRawAffectation(int employee, int contract, int department, int service) throws SQLException {
        String sql = "INSERT INTO employe_affectation_carriere "
                + "(id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, "
                + "id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut) "
                + "VALUES (?, ?, 4, 1, 1, ?, ?, 3, 1, 1, 1200000, DATE '2025-01-01')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employee);
            ps.setInt(2, contract);
            ps.setInt(3, department);
            ps.setInt(4, service);
            ps.executeUpdate();
        }
    }
}
