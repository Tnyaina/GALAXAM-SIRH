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
    @DisplayName("Photo RH : l'agent Pointage sans EMPLOYE_READ est refusé")
    void pointageAgentCannotReadEmployeePhoto() throws Exception {
        mockMvc.perform(get("/employes/photos/unknown.png")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userFixtures.getPointageToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Photo RH : upload et lecture autorisés utilisent un cache privé")
    void adminReadsUploadedPhotoThroughPrivateEndpoint() throws Exception {
        String token = userFixtures.getAdminToken();
        MockMultipartFile photo = new MockMultipartFile(
                "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, new byte[] {1, 2, 3, 4});

        String response = mockMvc.perform(multipart("/employes/upload-photo")
                        .file(photo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").value(org.hamcrest.Matchers.startsWith("/employes/photos/")))
                .andReturn().getResponse().getContentAsString();

        String photoUrl = objectMapper.readTree(response).get("photoUrl").asText();
        mockMvc.perform(get(photoUrl).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("private")));
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
