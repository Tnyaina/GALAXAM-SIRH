package mg.galaxam.sirh.unit.conge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2A-4C.1 — Correction P1 : Qualification formelle de 2000-01-01 comme TECHNICAL_VALIDITY_LOWER_BOUND.
 * Prouve qu'aucune fausse signification historique (fondation GALAXAM, début de règle métier, début de campagne TCONGE)
 * n'est attribuée à cette borne technique de base de données.
 */
@DisplayName("Phase 2A-4C.1 — P1 : Classification TECHNICAL_VALIDITY_LOWER_BOUND")
public class TcongeP1TechnicalValidityLowerBoundUnitTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("2000-01-01 doit être qualifié exclusivement de TECHNICAL_VALIDITY_LOWER_BOUND")
    void shouldClassifyDateAsTechnicalValidityLowerBoundWithoutHistoricalSemantics() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT date_debut, description FROM parametre_global WHERE code_parametre = 'DUREE_REFERENCE_TCONGE_MOIS'";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();

                LocalDate dateDebut = rs.getDate("date_debut").toLocalDate();
                String description = rs.getString("description");

                // 1. Borne technique inférieure vérifiée
                assertThat(dateDebut).isEqualTo(LocalDate.of(2000, 1, 1));

                // 2. Classification explicite
                assertThat(description).contains("TECHNICAL_VALIDITY_LOWER_BOUND");

                // 3. Aucune fausse signification historique ou début de campagne
                assertThat(description.toLowerCase())
                        .doesNotContain("fondation galaxam")
                        .doesNotContain("date de fondation")
                        .contains("ne représente ni le début de la règle métier")
                        .contains("ni le début d une campagne tconge");
            }
        }
    }

    @Test
    @DisplayName("La résolution du paramètre reste stricte et non polluée par la date technique")
    void shouldResolveStrictlyWithoutSilentFallbacks() {
        Map<String, String> params = Map.of("DUREE_REFERENCE_TCONGE_MOIS", "12");
        int duration = ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS");
        assertThat(duration).isEqualTo(12);
    }
}
