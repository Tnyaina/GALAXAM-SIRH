package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import mg.galaxam.sirh.contrat.persistence.EmployeCompteBanque;
import mg.galaxam.sirh.paie.application.export.VirementBancaireExportService;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayrollBankAccountCompatibilityTest {
    private Connection connection;

    @BeforeEach
    void ouvrirTransaction() throws Exception {
        String url = System.getenv("GALAXAM_BANQUE_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire requise");
        connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_BANQUE_TEST_DB_USER"),
                System.getenv("GALAXAM_BANQUE_TEST_DB_PASSWORD"));
        connection.setAutoCommit(false);
    }

    @AfterEach
    void annulerTransaction() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void contexteDePaieLitCompteUniqueEtTolereSonAbsence() throws Exception {
        PayrollCalculationContextAssembler assembler = new PayrollCalculationContextAssembler(null, null, null, null);
        PayrollCalculationInput avecCompte = new PayrollCalculationInput();
        assembler.chargerBanque(avecCompte, 1, connection);
        EmployeCompteBanque compte = EmployeCompteBanque.findByEmployeId(1, connection);
        assertEquals(compte.getNumCompteRib(), avecCompte.getBanqueRib());
        assertEquals(compte.getNomBanque(), avecCompte.getBanqueNom());
        EmployeCompteBanque.deleteByEmployeId(1, connection);
        PayrollCalculationInput sansCompte = new PayrollCalculationInput();
        assembler.chargerBanque(sansCompte, 1, connection);
        assertNull(sansCompte.getBanqueRib());
        assertNull(sansCompte.getBanqueNom());
    }

    @Test
    void bulletinEtExportConserventLeRibHistoriqueApresModificationDuCompte() throws Exception {
        String ribHistorique = "00005 01234 00010010001 88";
        String sql = "INSERT INTO bulletin_paie (id_periode_paie, id_employe, reference_bulletin, "
                + "matricule, nom_prenom, intitule_poste, nom_site, regime_intervenant, "
                + "salaire_base, taux_horaire, net_a_payer, mode_paiement, banque_nom, banque_rib) "
                + "VALUES (1, 1, 'TEST-BANQUE-HISTORIQUE', '1', 'Employé test', 'Test', 'Test', 'Test', "
                + "100000, 1000, 90000, 'VIREMENT', 'Banque historique', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ribHistorique);
            ps.executeUpdate();
        }
        EmployeCompteBanque compte = EmployeCompteBanque.findByEmployeId(1, connection);
        compte.setNumCompteRib("RIB-ACTUEL-MODIFIE");
        compte.update(connection);
        var export = new VirementBancaireExportService();
        var lignes = export.genererDonneesVirement(1, connection);
        assertEquals(1, lignes.size());
        assertEquals(ribHistorique, lignes.get(0).getRibComplet());
        assertEquals("Banque historique", lignes.get(0).getBanque());
        String csv = export.exporterVirementCsv(1, connection);
        assertTrue(csv.contains(ribHistorique));
        assertFalse(csv.contains("RIB-ACTUEL-MODIFIE"));
    }
}
