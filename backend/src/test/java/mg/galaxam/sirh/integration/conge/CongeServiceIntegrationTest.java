package mg.galaxam.sirh.integration.conge;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CongeServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CongeService congeService;

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idAdminUser;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        idAdminUser = userFixtures.getAdminUserId();
        // Embauché il y a 2 ans pour valider l'ancienneté légale (Art. 84 : 12 mois)
        LocalDate dateEmbauche = LocalDate.now().minusYears(2);
        emp = employeFixtures.createActiveEmploye("EMP020", "RAVOAVIHASINA", "Fanja", 1, dateEmbauche);
    }

    private int resolveTypeCongeId(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Type de congé non trouvé : " + code);
    }

    @Test
    @DisplayName("Demande nominale et validation FIFO : Débit réel du compte et génération du mouvement de solde")
    public void testCreerEtValiderDemande_FIFO() throws Exception {
        // 1. Créditer 20 jours de congés
        employeFixtures.crediterSoldeConge(emp.idEmploye, 20.0);

        // 2. Créer une demande de 5 jours de congé annuel avec préavis légal > 15j
        LocalDate debut = LocalDate.now().plusDays(20);
        LocalDate fin = debut.plusDays(4); // 5 jours calendaires

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(emp.idEmploye);
        d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(fin));
        d.setCommentaire("Congé annuel d'été");

        DemandeConge created;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            created = congeService.creerDemande(d, idAdminUser, c);
            c.commit();
        }

        assertNotNull(created.getIdDemandeConge());

        // Vérifier statut EN_ATTENTE en base
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT s.code FROM demande_conge dc JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge WHERE dc.id_demande_conge = ?")) {
            ps.setInt(1, created.getIdDemandeConge());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_ATTENTE", rs.getString("code"));
            }
        }

        // 3. Valider la demande
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            congeService.validerDemande(created.getIdDemandeConge(), idAdminUser, "Accordé", c);
            c.commit();
        }

        // 4. Vérifier la création d'un mouvement de type PRISE dans mouvement_solde_conge
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ? AND id_demande_conge = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, created.getIdDemandeConge());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Un mouvement de prise doit avoir été enregistré pour cette demande");
                assertEquals("PRISE", rs.getString("type_mouvement"));
                assertEquals(-5.0, rs.getDouble("nb_jours"), 0.01, "La prise de congé doit être un mouvement négatif (-5j)");
            }
        }
    }

    @Test
    @DisplayName("Règle Art. 13 (Arrêt Maladie > 30j) : Scission automatique en maintien de salaire (30j) + sans solde")
    public void testScissionAutomatiqueArretMaladie() throws Exception {
        LocalDate debut = LocalDate.now().plusDays(2);
        LocalDate fin = debut.plusDays(44); // 45 jours calendaires (> 30j)

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(emp.idEmploye);
        d.setIdTypeConge(resolveTypeCongeId("MALADIE"));
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(fin));
        d.setJustificatifFourni(true);
        d.setCommentaire("Hospitalisation et convalescence");

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            congeService.creerDemande(d, idAdminUser, c);
            c.commit();
        }

        // Vérifier qu'il existe 2 demandes en base pour cet employé :
        // 1ère demande : MALADIE de 30 jours (du debut au debut + 29 jours)
        // 2ème demande : MALADIE_SANS_SOLDE pour le reliquat (15 jours)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT dc.id_demande_conge, t.code, dc.date_debut, dc.date_fin, dc.nb_jours_decomptes " +
                     "FROM demande_conge dc " +
                     "JOIN ref_type_conge t ON dc.id_type_conge = t.id_type_conge " +
                     "WHERE dc.id_employe = ? ORDER BY dc.date_debut ASC")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "La 1ère tranche (30j) doit exister");
                assertEquals("MALADIE", rs.getString("code"));
                assertEquals(30.0, rs.getDouble("nb_jours_decomptes"), 0.01);

                assertTrue(rs.next(), "La 2ème tranche (15j sans solde CNaPS) doit avoir été scindée");
                assertEquals("MALADIE_SANS_SOLDE", rs.getString("code"));
                assertEquals(15.0, rs.getDouble("nb_jours_decomptes"), 0.01);
            }
        }
    }

    @Test
    @DisplayName("Refus de demande : Passage du statut à REFUSE avec enregistrement du motif")
    public void testRefuserDemandeConge() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 10.0);

        LocalDate debut = LocalDate.now().plusDays(20);
        LocalDate fin = debut.plusDays(2);

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(emp.idEmploye);
        d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(fin));

        DemandeConge created;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            created = congeService.creerDemande(d, idAdminUser, c);
            c.commit();
        }

        String motifRefus = "Nécessité de service impérieuse";
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            congeService.refuserDemande(created.getIdDemandeConge(), idAdminUser, motifRefus, c);
            c.commit();
        }

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.code, dc.motif_refus FROM demande_conge dc " +
                     "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                     "WHERE dc.id_demande_conge = ?")) {
            ps.setInt(1, created.getIdDemandeConge());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("REFUSE", rs.getString("code"));
                assertEquals(motifRefus, rs.getString("motif_refus"));
            }
        }
    }
}
