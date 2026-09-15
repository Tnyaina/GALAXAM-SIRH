package mg.galaxam.sirh.unit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorContractArchitectureTest {

    @Test
    void controllersDoNotBuildErrorResponses() throws Exception {
        Path controllers = Path.of("src/main/java/mg/galaxam/sirh/api/controller");
        List<String> violations = new ArrayList<>();

        try (var files = Files.walk(controllers)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (source.contains("put(\"error\"")
                        || source.contains("createErrorResponse")
                        || source.matches("(?s).*ResponseEntity\\.(badRequest|notFound|internalServerError)\\(\\).*")) {
                    violations.add(file.toString());
                }
                for (String status : List.of("BAD_REQUEST", "NOT_FOUND", "CONFLICT",
                        "INTERNAL_SERVER_ERROR", "FORBIDDEN", "UNAUTHORIZED")) {
                    if (source.contains("ResponseEntity.status(HttpStatus." + status + ")")) {
                        violations.add(file + " -> " + status);
                    }
                }
            }
        }

        assertThat(violations)
                .as("Toute erreur contrôleur doit être déléguée au GlobalExceptionHandler")
                .isEmpty();
    }

    @Test
    void frontendDoesNotParseTechnicalErrorMessages() throws Exception {
        String source = Files.readString(Path.of("../frontend/src/api/apiClient.js")).toLowerCase();
        assertThat(source).doesNotContain(
                "duplicate key",
                "unique constraint",
                "foreign key",
                "nullpointerexception",
                "sqlexception",
                "java.lang");
    }
}
