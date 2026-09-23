package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void dashboardIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("dashboard");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("application")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/dashboard")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/dashboard")));
    }

    @Test
    void dashboardKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("dashboard/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/dashboard"));
        assertTrue(controllers.contains("/notifications"));
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
