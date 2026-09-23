package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PointageDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void pointageIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("pointage");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("api/dto")));
        assertTrue(Files.isDirectory(domain.resolve("application")));
        assertTrue(Files.isDirectory(domain.resolve("domain")));
        assertTrue(Files.isDirectory(domain.resolve("domain/calculation")));
        assertTrue(Files.isDirectory(domain.resolve("domain/engine")));
        assertTrue(Files.isDirectory(domain.resolve("domain/mapper")));
        assertTrue(Files.isDirectory(domain.resolve("persistence")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/pointage")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/dto/pointage")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/pointage")));
    }

    @Test
    void pointageKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("pointage/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/periodes"));
        assertTrue(controllers.contains("/pointages/anomalies"));
        assertTrue(controllers.contains("/pointages"));
        assertTrue(controllers.contains("/pointages/parametres"));
        assertTrue(controllers.contains("/pointages/saisie"));
        assertTrue(controllers.contains("/pointages/synthese"));
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
