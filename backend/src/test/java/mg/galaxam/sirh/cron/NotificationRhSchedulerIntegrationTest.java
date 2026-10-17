package mg.galaxam.sirh.cron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationRhSchedulerIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NotificationRhScheduler notificationRhScheduler;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            c.createStatement().execute("ALTER TABLE notification_rh DROP CONSTRAINT IF EXISTS chk_notif_type");
            c.createStatement().execute("ALTER TABLE notification_rh ADD CONSTRAINT chk_notif_type CHECK (type_alerte IN (" +
                "'CONTRAT_EXPIRE_J', 'CONTRAT_EXPIRE_7J', 'CONTRAT_EXPIRE_30J', 'CONTRAT_ECHU_NON_REGULARISE', " +
                "'ESSAI_EXPIRE_J', 'ESSAI_EXPIRE_7J', 'ESSAI_EXPIRE_15J', 'ESSAI_EXPIRE_30J', 'ESSAI_ECHU_SANS_DECISION', " +
                "'BROUILLON_EN_ATTENTE', 'MOUVEMENT_APPLIQUE', " +
                "'DOCUMENTS_MANQUANTS', 'EMPLOYE_CREE', 'BROUILLON_CREE', " +
                "'CONGE_EN_ATTENTE', 'DEMANDE_CONGE_EN_ATTENTE', 'CONGE_VALIDE', 'CONGE_REFUSE', 'CONGE_ANNULE', " +
                "'SOLDE_CONGE_FAIBLE', 'SOLDE_CONGE_NEGATIF', " +
                "'RETOUR_CONGE_J', 'FRACTIONNEMENT_NON_RESPECTE', " +
                "'MALADIE_PLAFOND_EPISODE_ATTEINT', 'CONGE_EXPIRATION_PROCHE', " +
                "'POOL_EXCEPTIONNEL_EPUISE', 'BONIFICATION_ANCIENNETE_APPLIQUEE', " +
                "'POINTAGE_MANQUANT', 'PERIODE_POINTAGE_A_VALIDER', " +
                "'PERIODE_POINTAGE_VALIDEE', 'PERIODE_POINTAGE_REJETEE', 'RETARD_RECURRENT', " +
                "'ALERTE_ECHEANCE'" +
            "))");
            c.createStatement().execute("DELETE FROM notification_rh");
            c.createStatement().execute("DELETE FROM scheduler_execution");
        }
    }

    private static int creerEmployeTest(Connection c, String matricule, String nom, String prenom, String cin) throws Exception {
        String sql = "INSERT INTO employe (" +
                "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone" +
                ") VALUES (?, ?, ?, 'M', '1990-01-15', 'Antananarivo', ?, '2010-01-15', 'TNR', 'Rue Test 101', '0340000000') " +
                "RETURNING id_employe";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, matricule);
            ps.setString(2, nom);
            ps.setString(3, prenom);
            ps.setString(4, cin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new RuntimeException("Erreur création employé");
    }

    @Test
    void testAlertesContratEchuEtEssaiEchu() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            int emp1 = creerEmployeTest(c, "NOTIF-001", "DUPONT", "Jean", "101999000001");
            int emp2 = creerEmployeTest(c, "NOTIF-002", "DURAND", "Marie", "101999000002");

            // 1. Contrat CDD échu il y a 5 jours
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_intervenant, numero_contrat, id_type_contrat, id_statut_rh, date_debut, date_fin) " +
                    "VALUES (?, 1, 1, 'CDD-2026-001', " +
                    "(SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), " +
                    "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), ?, ?)")) {
                ps.setInt(1, emp1);
                ps.setDate(2, Date.valueOf(LocalDate.now().minusMonths(6)));
                ps.setDate(3, Date.valueOf(LocalDate.now().minusDays(5)));
                ps.executeUpdate();
            }

            // 2. Contrat avec période d'essai échue il y a 3 jours
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_intervenant, numero_contrat, id_type_contrat, id_statut_rh, date_debut, date_fin_essai) " +
                    "VALUES (?, 1, 1, 'CDI-2026-002', " +
                    "(SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), " +
                    "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), ?, ?)")) {
                ps.setInt(1, emp2);
                ps.setDate(2, Date.valueOf(LocalDate.now().minusMonths(3)));
                ps.setDate(3, Date.valueOf(LocalDate.now().minusDays(3)));
                ps.executeUpdate();
            }

            // Exécuter le scheduler
            notificationRhScheduler.genererNotificationsRh();

            // Vérifier la présence de l'alerte pour contrat échu
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type_alerte, priorite, titre, message FROM notification_rh WHERE id_employe_concerne = ? AND type_alerte = 'CONTRAT_ECHU_NON_REGULARISE'")) {
                ps.setInt(1, emp1);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Alerte de contrat échu attendue");
                    assertEquals("CONTRAT_ECHU_NON_REGULARISE", rs.getString("type_alerte"));
                    assertEquals("CRITIQUE", rs.getString("priorite"));
                    assertTrue(rs.getString("titre").contains("Contrat CDD échu non régularisé"));
                }
            }

            // Vérifier la présence de l'alerte pour essai échu
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type_alerte, priorite, titre, message FROM notification_rh WHERE id_employe_concerne = ? AND type_alerte = 'ESSAI_ECHU_SANS_DECISION'")) {
                ps.setInt(1, emp2);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "Alerte d'essai échu attendue");
                    assertEquals("ESSAI_ECHU_SANS_DECISION", rs.getString("type_alerte"));
                    assertEquals("CRITIQUE", rs.getString("priorite"));
                    assertTrue(rs.getString("titre").contains("Période d'essai échue sans décision"));
                }
            }
        }
    }

    @Test
    void testAntiDoublonEtAntiResurfacage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            int emp = creerEmployeTest(c, "NOTIF-003", "TEST", "User", "101999000003");

            // Contrat CDD échu
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_intervenant, numero_contrat, id_type_contrat, id_statut_rh, date_debut, date_fin) " +
                    "VALUES (?, 1, 1, 'CDD-2026-003', " +
                    "(SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), " +
                    "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), ?, ?)")) {
                ps.setInt(1, emp);
                ps.setDate(2, Date.valueOf(LocalDate.now().minusMonths(6)));
                ps.setDate(3, Date.valueOf(LocalDate.now().minusDays(5)));
                ps.executeUpdate();
            }

            // 1. Premier passage : génération de la notification
            notificationRhScheduler.genererNotificationsRh();

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM notification_rh WHERE id_employe_concerne = ? AND type_alerte = 'CONTRAT_ECHU_NON_REGULARISE'")) {
                ps.setInt(1, emp);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "Exactement 1 notification de contrat échu doit être créée");
                }
            }

            // 2. Second passage (redémarrage ou ré-exécution immédiate) : pas de doublon empilé
            c.createStatement().execute("DELETE FROM scheduler_execution");
            notificationRhScheduler.genererNotificationsRh();

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM notification_rh WHERE id_employe_concerne = ? AND type_alerte = 'CONTRAT_ECHU_NON_REGULARISE'")) {
                ps.setInt(1, emp);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "Aucun doublon ne doit être ajouté lors d'un second passage");
                }
            }

            // 3. L'utilisateur marque la notification comme lue
            c.createStatement().execute("UPDATE notification_rh SET est_lue = TRUE, date_lecture = CURRENT_TIMESTAMP WHERE id_employe_concerne = " + emp);

            // 4. Troisième passage (redémarrage le lendemain) : anti-resurfaçage 7 jours actif
            c.createStatement().execute("DELETE FROM scheduler_execution");
            notificationRhScheduler.genererNotificationsRh();

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM notification_rh WHERE id_employe_concerne = ? AND type_alerte = 'CONTRAT_ECHU_NON_REGULARISE' AND est_lue = FALSE")) {
                ps.setInt(1, emp);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    assertEquals(0, rs.getInt(1), "L'alerte acquittée ne doit pas ressusciter comme non lue");
                }
            }
        }
    }
}
