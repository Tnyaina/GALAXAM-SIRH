package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import mg.galaxam.sirh.pointage.application.PointageSaisieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongePointageIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeService congeService;

    @Autowired
    private PointageSaisieService pointageSaisieService;

    private int adminUserId;
    private int employeId;
    private int typeAnnuelId;
    private int typeExceptionnelId;
    private int typeSansSoldeId;
    private int motifMariageId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.pointage@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-PTG-001", "Rasoa", "Bakoly");
            int contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 1, 1), new BigDecimal("1200000.00"));

            typeAnnuelId = CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL");
            typeExceptionnelId = CongeTestFixtureHelper.getTypeCongeId(c, "EXCEPTIONNEL");
            typeSansSoldeId = CongeTestFixtureHelper.getTypeCongeId(c, "SANS_SOLDE");
            motifMariageId = CongeTestFixtureHelper.getMotifEvenementFamilialId(c, "MARIAGE");

            // Créer une période de paie ouverte pour le mois de septembre 2026
            CongeTestFixtureHelper.createPeriodePaie(c, "2026-09", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "EN_COURS");
        }
    }

    @Test
    @DisplayName("21.A Bridge Réel Congé Annuel (decompte_solde_acquis = true) -> pointage_jour statut CONGE")
    void testBridgePointageCongeAnnuel() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.crediterSolde(c, employeId, 10.0, "Solde 10j");

            LocalDate dateConge = LocalDate.of(2026, 9, 15);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeAnnuelId);
            d.setDateDebut(Date.valueOf(dateConge));
            d.setDateFin(Date.valueOf(dateConge));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation congé annuel", c);

            // Appel du service Pointage réel pour générer la grille de saisie
            Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, dateConge.toString(), c);
            assertNotNull(grille);
            @SuppressWarnings("unchecked")
            List<SaisiePointageJourDTO> lignes = (List<SaisiePointageJourDTO>) grille.get("grilleData");
            assertNotNull(lignes);

            SaisiePointageJourDTO pointageEmp = lignes.stream()
                    .filter(p -> p.getIdEmploye() == employeId)
                    .findFirst()
                    .orElse(null);
            assertNotNull(pointageEmp, "Le collaborateur doit être présent dans la grille de saisie");
            assertEquals("CONGE", pointageEmp.getCodeStatut());
            assertTrue(pointageEmp.isCongeDecompteSolde());
            assertTrue(pointageEmp.isCongePaye());

            // Enregistrement effectif du pointage en base
            pointageSaisieService.enregistrerPointagesBatch(List.of(pointageEmp), c);

            // Vérification dans la table réelle pointage_jour
            String sqlCheck = "SELECT pj.total_heures, spj.code FROM pointage_jour pj " +
                    "JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour " +
                    "WHERE pj.id_employe = ? AND pj.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateConge));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("CONGE", rs.getString("code"));
                    assertEquals(0, new BigDecimal("0.0").compareTo(rs.getBigDecimal("total_heures")));
                }
            }
        }
    }

    @Test
    @DisplayName("21.B Bridge Réel Événement Familial (decompte_solde_acquis = false + payé) -> pointage_jour statut DISPENSE_POINTAGE")
    void testBridgePointageEvenementFamilial() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate dateConge = LocalDate.of(2026, 9, 16);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeExceptionnelId);
            d.setIdMotifEvenementFamilial(motifMariageId);
            d.setDateDebut(Date.valueOf(dateConge));
            d.setDateFin(Date.valueOf(dateConge));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation mariage", c);

            Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, dateConge.toString(), c);
            @SuppressWarnings("unchecked")
            List<SaisiePointageJourDTO> lignes = (List<SaisiePointageJourDTO>) grille.get("grilleData");
            SaisiePointageJourDTO pointageEmp = lignes.stream()
                    .filter(p -> p.getIdEmploye() == employeId)
                    .findFirst()
                    .orElse(null);

            assertNotNull(pointageEmp);
            assertEquals("DISPENSE_POINTAGE", pointageEmp.getCodeStatut());
            assertFalse(pointageEmp.isCongeDecompteSolde());
            assertTrue(pointageEmp.isCongePaye());

            assertEquals(8.0, pointageEmp.getTotalHeures()); // Dans la grille de saisie DTO, 8h forfaitaires payées
            pointageSaisieService.enregistrerPointagesBatch(List.of(pointageEmp), c);

            String sqlCheck = "SELECT pj.total_heures, spj.code FROM pointage_jour pj " +
                    "JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour " +
                    "WHERE pj.id_employe = ? AND pj.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateConge));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("DISPENSE_POINTAGE", rs.getString("code"));
                    // En BDD, total_heures pointe à 0.0 (car aucune heure travaillée physiquement sur site)
                    assertEquals(0, new BigDecimal("0.0").compareTo(rs.getBigDecimal("total_heures")));
                }
            }
        }
    }

    @Test
    @DisplayName("21.C Bridge Réel Sans Solde (est_paye = false) -> pointage_jour statut ABSENCE_JUSTIFIEE")
    void testBridgePointageSansSolde() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate dateConge = LocalDate.of(2026, 9, 17);

            DemandeConge d = new DemandeConge();
            d.setIdEmploye(employeId);
            d.setIdTypeConge(typeSansSoldeId);
            d.setDateDebut(Date.valueOf(dateConge));
            d.setDateFin(Date.valueOf(dateConge));
            d.setDerogationDelaiPreavis(true);
            DemandeConge cree = congeService.creerDemande(d, adminUserId, c);
            congeService.validerDemande(cree.getIdDemandeConge(), adminUserId, "Validation sans solde", c);

            Map<String, Object> grille = pointageSaisieService.getGrilleSaisie(1, dateConge.toString(), c);
            @SuppressWarnings("unchecked")
            List<SaisiePointageJourDTO> lignes = (List<SaisiePointageJourDTO>) grille.get("grilleData");
            SaisiePointageJourDTO pointageEmp = lignes.stream()
                    .filter(p -> p.getIdEmploye() == employeId)
                    .findFirst()
                    .orElse(null);

            assertNotNull(pointageEmp);
            assertEquals("ABSENCE_JUSTIFIEE", pointageEmp.getCodeStatut());
            assertFalse(pointageEmp.isCongePaye());

            pointageSaisieService.enregistrerPointagesBatch(List.of(pointageEmp), c);

            String sqlCheck = "SELECT pj.total_heures, spj.code FROM pointage_jour pj " +
                    "JOIN ref_statut_pointage_jour spj ON pj.id_statut_jour = spj.id_statut_jour " +
                    "WHERE pj.id_employe = ? AND pj.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCheck)) {
                ps.setInt(1, employeId);
                ps.setDate(2, Date.valueOf(dateConge));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("ABSENCE_JUSTIFIEE", rs.getString("code"));
                    assertEquals(0, new BigDecimal("0.0").compareTo(rs.getBigDecimal("total_heures")));
                }
            }
        }
    }
}
