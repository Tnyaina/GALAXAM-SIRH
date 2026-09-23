package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SecuriteDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void securiteIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("securite");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("api/dto")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/securite")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/dto/securite")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/securite")));
    }

    @Test
    void securiteKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("securite/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/auth"));
        assertTrue(controllers.contains("/utilisateurs"));
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
