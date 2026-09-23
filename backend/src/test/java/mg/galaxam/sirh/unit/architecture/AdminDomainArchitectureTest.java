package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void adminIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("admin");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("api/dto")));
        assertTrue(Files.isDirectory(domain.resolve("persistence")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/admin")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/dto/admin")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/admin")));
    }

    @Test
    void adminKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("admin/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/referentiel/menus"));
        assertTrue(controllers.contains("/referentiel/modules"));
        assertTrue(controllers.contains("/referentiel/roles"));
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
