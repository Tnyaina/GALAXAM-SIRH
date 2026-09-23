package mg.galaxam.sirh.unit.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OrganisationDomainArchitectureTest {

    private final Path sourceRoot = Path.of("src/main/java/mg/galaxam/sirh");

    @Test
    void organisationIsOwnedByOneTopLevelDomain() {
        Path domain = sourceRoot.resolve("organisation");

        assertTrue(Files.isDirectory(domain.resolve("api")));
        assertTrue(Files.isDirectory(domain.resolve("api/dto")));
        assertTrue(Files.isDirectory(domain.resolve("persistence")));

        assertFalse(containsJavaFiles(sourceRoot.resolve("api/controller/organisation")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("api/dto/organisation")));
        assertFalse(containsJavaFiles(sourceRoot.resolve("model/organisation")));
    }

    @Test
    void organisationKeepsItsPublicHttpContract() throws Exception {
        String controllers = Files.walk(sourceRoot.resolve("organisation/api"))
                .filter(path -> path.getFileName().toString().endsWith("Controller.java"))
                .map(this::read)
                .reduce("", String::concat);

        assertTrue(controllers.contains("/referentiel/filiales"));
        assertTrue(controllers.contains("/referentiel/sites"));
        assertTrue(controllers.contains("/referentiel/departements"));
        assertTrue(controllers.contains("/referentiel/services"));
        assertTrue(controllers.contains("/referentiel/fonctions"));
        assertTrue(controllers.contains("/referentiel/types-contrat"));
        assertTrue(controllers.contains("/referentiel/types-intervenant"));
        assertTrue(controllers.contains("/referentiel/banques"));
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
