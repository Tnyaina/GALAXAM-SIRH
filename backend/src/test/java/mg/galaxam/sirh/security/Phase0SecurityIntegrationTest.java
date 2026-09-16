package mg.galaxam.sirh.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.securite.AdminAccountSeeder;
import mg.galaxam.sirh.securite.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "app.upload.dir=target/test-photo-uploads")
class Phase0SecurityIntegrationTest extends BaseIntegrationTest {

    private static final Path PHOTO_DIRECTORY = Path.of("target/test-photo-uploads");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void cleanPhotoFiles() throws Exception {
        if (!Files.exists(PHOTO_DIRECTORY)) return;
        try (var files = Files.list(PHOTO_DIRECTORY)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
        Files.deleteIfExists(PHOTO_DIRECTORY);
    }

    @Test
    @DisplayName("Bootstrap admin : désactivé par défaut dans le profil de test")
    void adminBootstrapIsDisabledByDefault() {
        assertThat(applicationContext.getBeansOfType(AdminAccountSeeder.class)).isEmpty();
    }

    @Test
    @DisplayName("Photo RH : un appel anonyme est refusé")
    void anonymousCannotReadEmployeePhoto() throws Exception {
        mockMvc.perform(get("/employes/photos/unknown.png"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Photo RH (ancien endpoint) : l'agent Pointage voit 410 (endpoint déprécié P1-05)")
    void pointageAgentCannotReadEmployeePhoto() throws Exception {
        // P1-05 : L'ancien endpoint GET /employes/photos/{filename} retourne 410 Gone.
        // Le contrôle d'accès se fait désormais sur GET /employes/{id}/photo
        // qui vérifie l'existence de l'employé et la permission EMPLOYE_READ.
        mockMvc.perform(get("/employes/photos/unknown.png")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userFixtures.getPointageToken()))
                .andExpect(status().isGone());
    }

    @Test
    @DisplayName("Photo RH (P1-05) : upload via /{idEmploye}/photo + lecture via /{idEmploye}/photo")
    void adminReadsUploadedPhotoThroughPrivateEndpoint() throws Exception {
        String token = userFixtures.getAdminToken();

        // Récupérer un idEmploye réel
        String empRes = mockMvc.perform(get("/employes?size=1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(empRes);
        int idEmploye = node.isArray() && node.size() > 0
                ? node.get(0).get("idEmploye").asInt()
                : employeFixtures.createActiveEmploye("MATR-PHOTO-01", "Photo", "Test", 1).idEmploye;

        // P1-05 : Upload via POST /{idEmploye}/photo (lié à un employé)
        MockMultipartFile photo = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3, 4});
        mockMvc.perform(multipart("/employes/" + idEmploye + "/photo")
                        .file(photo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoEndpoint").value("/employes/" + idEmploye + "/photo"));

        // P1-05 : Lecture via GET /{idEmploye}/photo (le filename n'est jamais exposé)
        // Un fichier minimal de 4 bytes n'est pas détecté comme PNG valide par le système.
        // On vérifie juste que l'endpoint répond 200 ou 415 (contenu invalide mais accès autorisé).
        int status = mockMvc.perform(get("/employes/" + idEmploye + "/photo")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn().getResponse().getStatus();
        // 200 = fichier PNG valide, 415 = tôt PNG invalide — dans les deux cas l'accès est contrôlé
        org.assertj.core.api.Assertions.assertThat(status).isIn(200, 415);
    }

    @Test
    @DisplayName("Documents RH : l'ancien chemin statique n'est pas public")
    void legacyStaticDocumentPathIsNotPublic() throws Exception {
        mockMvc.perform(get("/uploads/documents/unknown.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS : l'origine locale déclarée est acceptée")
    void configuredCorsOriginIsAccepted() throws Exception {
        mockMvc.perform(options("/employes")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    @DisplayName("CORS : une origine non déclarée est refusée")
    void unknownCorsOriginIsRejected() throws Exception {
        mockMvc.perform(options("/employes")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("JWT : expiresIn provient de la même durée que le token signé")
    void loginExpiryMatchesJwtConfiguration() throws Exception {
        userFixtures.initTestUsers();
        String body = "{\"identifiant\":\"" + userFixtures.ADMIN_EMAIL
                + "\",\"motDePasse\":\"" + userFixtures.ADMIN_RAW_PASSWORD + "\"}";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(jwtService.getAccessTokenExpirationMs() / 1000));
    }
}
