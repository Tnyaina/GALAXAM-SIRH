package mg.galaxam.sirh.integration.document;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

@DisplayName("Integration Tests - API Documents RH (Attestation & Certificat de travail)")
public class DocumentRhControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestEmployeFixtures testEmployeFixtures;

    private int testIdEmploye;
    private int testIdContrat;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

        int idSite;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next());
            idSite = rs.getInt(1);
        }

        TestEmployeFixtures.CreatedEmploye emp = testEmployeFixtures.createActiveEmploye("EMP-DOC", "RAZAFY", "Jean Luc", idSite);
        testIdEmploye = emp.idEmploye;
        testIdContrat = emp.idContrat;

        // Mise à jour de quelques champs spécifiques pour le test (CIN, CNaPS)
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET cin_numero = '101234567890', num_affiliation_cnaps = '9876543210' WHERE id_employe = ?")) {
                ps.setInt(1, testIdEmploye);
                ps.executeUpdate();
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("DOC-01: Récupération des données JSON de l'Attestation de travail")
    public void testGetAttestationData() throws Exception {
        mockMvc.perform(get("/api/documents/attestation/" + testIdEmploye)
                        .param("typeAnciennete", "globale")
                        .param("inclureSalaire", "true")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.employe.nom").value("RAZAFY"))
                .andExpect(jsonPath("$.employe.prenom").value("Jean Luc"))
                .andExpect(jsonPath("$.employe.numeroCin").value("101234567890"))
                .andExpect(jsonPath("$.employe.numeroCnaps").value("9876543210"))
                .andExpect(jsonPath("$.entreprise.nom").isNotEmpty())
                .andExpect(jsonPath("$.inclureSalaire").value(true));
    }

    @Test
    @DisplayName("DOC-02: Téléchargement du fichier Word DOCX de l'Attestation de travail")
    public void testDownloadAttestationDocx() throws Exception {
        byte[] docxBytes = mockMvc.perform(get("/api/documents/attestation/" + testIdEmploye + "/docx")
                        .param("typeAnciennete", "globale")
                        .param("inclureSalaire", "true")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Attestation_Travail_EMP-DOC.docx\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 3000, "Le binaire DOCX généré doit avoir une taille valide");
        // Les 2 premiers octets d'un zip/docx sont toujours PK
        assertEquals('P', (char) docxBytes[0]);
        assertEquals('K', (char) docxBytes[1]);
    }

    @Test
    @DisplayName("DOC-03: Récupération des données JSON du Certificat de travail avec mention libératoire")
    public void testGetCertificatData() throws Exception {
        mockMvc.perform(get("/api/documents/certificat/" + testIdContrat)
                        .param("typeAnciennete", "globale")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.employe.nom").value("RAZAFY"))
                .andExpect(jsonPath("$.mentionLiberatoire").value("Nous confirmons que le/la salarié(e) quitte l'entreprise libre de tout engagement et de toute obligation envers la société."))
                .andExpect(jsonPath("$.postesOccupes").isArray());
    }

    @Test
    @DisplayName("DOC-04: Téléchargement du fichier Word DOCX du Certificat de travail")
    public void testDownloadCertificatDocx() throws Exception {
        byte[] docxBytes = mockMvc.perform(get("/api/documents/certificat/" + testIdContrat + "/docx")
                        .param("typeAnciennete", "globale")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Certificat_Travail_EMP-DOC.docx\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertNotNull(docxBytes);
        assertTrue(docxBytes.length > 3000, "Le binaire DOCX généré doit avoir une taille valide");
        assertEquals('P', (char) docxBytes[0]);
        assertEquals('K', (char) docxBytes[1]);
    }
}
