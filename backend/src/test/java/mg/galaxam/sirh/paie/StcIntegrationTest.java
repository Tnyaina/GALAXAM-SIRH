package mg.galaxam.sirh.paie;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculResultDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcPreparationDTO;
import mg.galaxam.sirh.api.dto.paie.stc.StcRecuDTO;
import mg.galaxam.sirh.model.paie.PaiePret;
import mg.galaxam.sirh.model.paie.PaieStc;
import mg.galaxam.sirh.model.paie.service.StcService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("StcIntegrationTest — Validation Métier et REST du Solde de Tout Compte")
public class StcIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private StcService stcService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static String adminToken;
    private static Integer testEmployeId;
    private static Integer testStcId;

    @BeforeEach
    void setUp() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        if (adminToken == null) {
            try (Connection c = dataSource.getConnection()) {
                String hash = passwordEncoder.encode("Pass1234");
                try (PreparedStatement ps = c.prepareStatement("UPDATE utilisateur SET mot_de_passe_hache = ? WHERE identifiant = 'admin'")) {
                    ps.setString(1, hash);
                    ps.executeUpdate();
                }
            }

            String loginPayload = """
                    {
                        "identifiant": "admin",
                        "motDePasse": "Pass1234"
                    }
                    """;

            MvcResult result = mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload))
                    .andExpect(status().isOk())
                    .andReturn();

            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            adminToken = jsonNode.get("accessToken").asText();
        }

        if (testEmployeId == null) {
            try (Connection c = dataSource.getConnection()) {
                String sql = "SELECT e.id_employe FROM employe e " +
                        "JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe " +
                        "LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle " +
                        "WHERE eac.id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF') " +
                        "AND (rcp.code = 'CADRE' OR rcp.code = 'CADRE_DIRIGEANT') " +
                        "ORDER BY e.id_employe ASC LIMIT 1";
                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        testEmployeId = rs.getInt("id_employe");
                    } else {
                        testEmployeId = 1;
                    }
                }
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("STC-01 : Service — Préparation administrative et collecte automatique des droits")
    void testPreparerStcService() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Date dateDepart = Date.valueOf("2026-03-25");
            StcPreparationDTO prep = stcService.preparerStc(testEmployeId, dateDepart, "LICENCIEMENT", c);

            assertNotNull(prep);
            assertEquals(testEmployeId, prep.getIdEmploye());
            assertNotNull(prep.getMatricule());
            assertNotNull(prep.getNomComplet());
            assertTrue(prep.getSalaireBase().compareTo(BigDecimal.ZERO) > 0, "Le salaire de base doit être positif");
            assertTrue(prep.getTauxHoraire().compareTo(BigDecimal.ZERO) > 0, "Le taux horaire doit être calculé");
            assertTrue(prep.getTauxJournalierConge().compareTo(BigDecimal.ZERO) > 0, "Le taux TCONGE doit être valorisé");
            assertNotNull(prep.getIccpEstimee());
            assertEquals(new BigDecimal("3.0"), prep.getMoisPreavisSuggere(), "Préavis de 3 mois pour cadre / ancienneté");
        }
    }

    @Test
    @Order(2)
    @DisplayName("STC-02 : Service — Simulation complète et équilibre comptable strict")
    void testSimulerStcService() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            StcCalculRequest req = new StcCalculRequest();
            req.setIdEmploye(testEmployeId);
            req.setDateDepart(Date.valueOf("2026-03-25"));
            req.setMotifRupture("LICENCIEMENT");
            req.setMoisPreavis(new BigDecimal("2.0"));
            req.setIndemniteLicenciement(new BigDecimal("350000.00"));
            req.setSimulation(true);

            StcCalculResultDTO res = stcService.simulerStc(req, c);

            assertNotNull(res);
            assertEquals("SIMULATION", res.getStatut());
            assertTrue(res.getTotalBrutStc().compareTo(BigDecimal.ZERO) > 0, "Le brut STC doit être calculé");
            assertTrue(res.getTotalCotisationsSociales().compareTo(BigDecimal.ZERO) >= 0, "Cotisations sociales calculées");
            assertTrue(res.getNetAPayerStc().compareTo(BigDecimal.ZERO) > 0, "Le net à payer final doit être positif");

            // Vérification de l'équilibre financier :
            // Net <= Total Brut
            assertTrue(res.getNetAPayerStc().compareTo(res.getTotalBrutStc()) <= 0);
            assertFalse(res.getLignes().isEmpty(), "Les lignes détaillées du STC doivent être générées");
        }
    }

    @Test
    @Order(3)
    @DisplayName("STC-03 : Service — Validation formelle, génération du reçu STC et apurement des prêts")
    void testValiderStcService() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Créer un prêt fictif pour vérifier son apurement intégral par le STC
                String refPret = "PRET-STC-TEST-" + System.currentTimeMillis();
                String sqlPret = "INSERT INTO paie_pret (" +
                        "id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, " +
                        "date_deblocage, date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) " +
                        "VALUES (?, ?, 200000.00, 50000.00, 4, '2026-01-01', '2026-02-01', 0, 200000.00, 'EN_COURS', 'Prêt d urgence personnelle') RETURNING id_pret";
                int idPret;
                try (PreparedStatement ps = c.prepareStatement(sqlPret)) {
                    ps.setInt(1, testEmployeId);
                    ps.setString(2, refPret);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        idPret = rs.getInt(1);
                    }
                }

                // Exécuter la validation du STC
                StcCalculRequest req = new StcCalculRequest();
                req.setIdEmploye(testEmployeId);
                req.setDateDepart(Date.valueOf("2026-03-31"));
                req.setMotifRupture("DEMISSION");
                req.setModeReglement("VIREMENT");
                req.setCommentaire("Départ volontaire validé par la Direction");

                StcCalculResultDTO validRes = stcService.validerStc(req, 1, c);

                assertNotNull(validRes);
                assertNotNull(validRes.getIdStc());
                assertNotNull(validRes.getNumeroRecu());
                assertTrue(validRes.getNumeroRecu().startsWith("STC-2026-"));
                assertEquals("VALIDE", validRes.getStatut());
                testStcId = validRes.getIdStc();

                // Vérifier que le prêt a été intégralement soldé
                PaiePret pretApres = PaiePret.trouverParId(idPret, c);
                assertNotNull(pretApres);
                assertEquals("SOLDE", pretApres.getStatut(), "Le prêt doit être basculé en statut SOLDE");
                assertEquals(0, pretApres.getCapitalRestantDu().compareTo(BigDecimal.ZERO), "Le capital restant dû doit être 0");

                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("STC-04 : Service — Édition de la Quittance Libératoire (StcRecuDTO) Art. 53")
    void testObtenirRecuStc() throws Exception {
        Assumptions.assumeTrue(testStcId != null, "Un STC doit avoir été validé au test précédent");

        try (Connection c = dataSource.getConnection()) {
            StcRecuDTO recu = stcService.obtenirRecuStc(testStcId, c);

            assertNotNull(recu);
            assertEquals("VALIDE", recu.getStatut());
            assertNotNull(recu.getNumeroRecu());
            assertNotNull(recu.getEntrepriseNom());
            assertNotNull(recu.getMentionLiberatoire());
            assertTrue(recu.getMentionLiberatoire().contains("pour solde de tout compte et règlement définitif"));
            assertEquals("Bon pour solde de tout compte et quittance finale", recu.getFormuleManuscriteObligatoire());
            assertTrue(recu.getNetAPayer().compareTo(BigDecimal.ZERO) >= 0);
            assertNotNull(recu.getNetEnLettres());
        }
    }

    @Test
    @Order(5)
    @DisplayName("STC-05 : REST GET /api/v1/paie/stc/preparer/{idEmploye}")
    void testRestPreparerStc() throws Exception {
        mockMvc.perform(get("/api/v1/paie/stc/preparer/" + testEmployeId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("dateDepart", "2026-03-31")
                        .param("motif", "FIN_CONTRAT"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    @DisplayName("STC-06 : REST POST /api/v1/paie/stc/calculer — Simulation via API")
    void testRestSimulerStc() throws Exception {
        String payload = String.format("""
                {
                    "idEmploye": %d,
                    "dateDepart": "2026-03-31",
                    "motifRupture": "DEMISSION",
                    "simulation": true
                }
                """, testEmployeId);

        MvcResult result = mockMvc.perform(post("/api/v1/paie/stc/calculer")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertEquals("SIMULATION", json.get("statut").asText());
        assertTrue(json.get("netAPayerStc").asDouble() >= 0);
    }

    @Test
    @Order(7)
    @DisplayName("STC-07 : REST GET /api/v1/paie/stc/liste")
    void testRestListerStc() throws Exception {
        mockMvc.perform(get("/api/v1/paie/stc/liste")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
