package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.time.LocalDate;
import mg.galaxam.sirh.contrat.persistence.EmployeCompteBanque;
import mg.galaxam.sirh.paie.application.export.VirementBancaireExportService;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayrollBankAccountCompatibilityTest {
    private Connection connection;
    private int employeeId;
    private int periodId;

    @BeforeEach
    void ouvrirTransaction() throws Exception {
        String url = System.getenv("GALAXAM_BANQUE_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire requise");
        connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_BANQUE_TEST_DB_USER"),
                System.getenv("GALAXAM_BANQUE_TEST_DB_PASSWORD"));
        connection.setAutoCommit(false);
        employeeId = insertEmployee();
        periodId = insertPeriod();
        insertEmploymentContract();
        insertBankAccount("00005 01234 00010010001 88");
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
        assembler.chargerBanque(avecCompte, employeeId, connection);
        EmployeCompteBanque compte = EmployeCompteBanque.findByEmployeId(employeeId, connection);
        assertEquals(compte.getNumCompteRib(), avecCompte.getBanqueRib());
        assertEquals(compte.getNomBanque(), avecCompte.getBanqueNom());
        EmployeCompteBanque.deleteByEmployeId(employeeId, connection);
        PayrollCalculationInput sansCompte = new PayrollCalculationInput();
        assembler.chargerBanque(sansCompte, employeeId, connection);
        assertNull(sansCompte.getBanqueRib());
        assertNull(sansCompte.getBanqueNom());
    }

    @Test
    void bulletinEtExportConserventLeRibHistoriqueApresModificationDuCompte() throws Exception {
        String ribHistorique = "00005 01234 00010010001 88";
        String sql = "INSERT INTO bulletin_paie (id_periode_paie, id_employe, reference_bulletin, "
                + "matricule, nom_prenom, intitule_poste, nom_site, regime_intervenant, "
                + "salaire_base, taux_horaire, net_a_payer, mode_paiement, banque_nom, banque_rib) "
                + "VALUES (?, ?, 'TEST-BANQUE-HISTORIQUE', 'TEST', 'Employé test', 'Test', 'Test', 'Test', "
                + "100000, 1000, 90000, 'VIREMENT', 'Banque historique', ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, periodId);
            ps.setInt(2, employeeId);
            ps.setString(3, ribHistorique);
            ps.executeUpdate();
        }
        EmployeCompteBanque compte = EmployeCompteBanque.findByEmployeId(employeeId, connection);
        compte.setNumCompteRib("RIB-ACTUEL-MODIFIE");
        compte.update(connection);
        var export = new VirementBancaireExportService();
        var lignes = export.genererDonneesVirement(periodId, connection);
        assertEquals(1, lignes.size());
        assertEquals(ribHistorique, lignes.get(0).getRibComplet());
        assertEquals("Banque historique", lignes.get(0).getBanque());
        String csv = export.exporterVirementCsv(periodId, connection);
        assertTrue(csv.contains(ribHistorique));
        assertFalse(csv.contains("RIB-ACTUEL-MODIFIE"));
    }

    private int insertEmployee() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String sql = "INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,"
                + "cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                + "VALUES (?, 'TEST', 'Banque', 'M', DATE '1990-01-01', 'Antananarivo', ?, DATE '2010-01-01', "
                + "'Antananarivo', 'Adresse test', '000000') RETURNING id_employe";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "IT-BA-" + suffix);
            ps.setString(2, "ITBA" + suffix);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private int insertPeriod() throws Exception {
        String sql = "INSERT INTO periode_paie(code,libelle,date_debut,date_fin) VALUES (?, 'Test compte bancaire', ?, ?) "
                + "RETURNING id_periode_paie";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "IT-BA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            ps.setObject(2, LocalDate.of(2032, 1, 1));
            ps.setObject(3, LocalDate.of(2032, 1, 31));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private void insertBankAccount(String rib) throws Exception {
        String sql = "INSERT INTO employe_compte_banque(id_employe,id_banque,num_compte_rib,agence,titulaire_compte) "
                + "SELECT ?,id_banque,?,'Agence test','Employé test' FROM ref_banque WHERE code_banque='00005'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setString(2, rib);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private void insertEmploymentContract() throws Exception {
        String sql = "INSERT INTO contrat_travail (id_employe,id_filiale,id_type_contrat,id_type_intervenant,"
                + "id_statut_rh,id_type_evenement_initial,numero_contrat,date_debut,duree_essai_mois) "
                + "SELECT ?,f.id_filiale,tc.id_type_contrat,ti.id_type_intervenant,s.id_statut_rh,e.id_type_evenement_rh,"
                + "?,DATE '2030-01-01',0 FROM ref_filiale f CROSS JOIN ref_type_contrat tc "
                + "CROSS JOIN ref_type_intervenant ti CROSS JOIN ref_statut_rh s CROSS JOIN ref_type_evenement_rh e "
                + "WHERE f.code_filiale='GLX' AND tc.code='CDI' AND ti.code='PERMANENT' AND s.code='ACTIF' "
                + "AND e.code='EMBAUCHE_INITIALE'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setString(2, "IT-BA-CON-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            assertEquals(1, ps.executeUpdate());
        }
    }
}
