package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaieConfigDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void paieconfigIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("paieconfig");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("api/dto")));
        assertTrue(Files.isDirectory(domain.resolve("persistence")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/paieconfig")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/dto/paieconfig")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/paieconfig")));
    }

    @Test
    void paieconfigKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("paieconfig/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/referentiel/calendrier-ferie"));
        assertTrue(controllers.contains("/referentiel/cotisations"));
        assertTrue(controllers.contains("/referentiel/indemnites"));
        assertTrue(controllers.contains("/referentiel/bareme-irsa"));
        assertTrue(controllers.contains("/paie/config-legale"));
        assertTrue(controllers.contains("/referentiel/parametres-globaux"));
        assertTrue(controllers.contains("/referentiel/types-mouvement-paie"));
        assertTrue(controllers.contains("/referentiel/taux-majoration"));
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
