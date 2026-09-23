package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContratDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void contratIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("contrat");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("api/dto")));
        assertTrue(Files.isDirectory(domain.resolve("application")));
        assertTrue(Files.isDirectory(domain.resolve("domain")));
        assertTrue(Files.isDirectory(domain.resolve("persistence")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/contrat")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/employe")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/document")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/contrat")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/employe")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/document")));
    }

    @Test
    void contratKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("contrat/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/employes"));
        assertTrue(controllers.contains("/contrats"));
        assertTrue(controllers.contains("/ref-carriere"));
        assertTrue(controllers.contains("/ref-types-documents"));
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception error) {
            throw new IllegalStateException("Lecture impossible: " + path, error);
        }
    }

    private boolean containsJavaFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(path -> path.getFileName().toString().endsWith(".java"));
        } catch (Exception error) {
            throw new IllegalStateException("Inspection impossible: " + directory, error);
        }
    }
}
