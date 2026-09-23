package mg.galaxam.sirh.integration.employe;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.common.dto.PageDTO;
import mg.galaxam.sirh.contrat.persistence.EmployeCPL;
import mg.galaxam.sirh.contrat.application.EmployeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EmployePaginationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private EmployeService employeService;

    @BeforeEach
    void preparePopulation() throws Exception {
        databaseCleaner.cleanTransactionalData();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO employe (
                        matricule, nom, prenom, sexe, date_naissance, lieu_naissance,
                        cin_numero, cin_date_delivrance, cin_lieu_delivrance,
                        adresse, telephone, date_declaration, est_actif)
                    SELECT 'LOAD-' || LPAD(g::text, 5, '0'), 'Nom' || g, 'Prenom', 'M'::sexe_enum,
                           DATE '1990-01-01', 'Antananarivo', 'CIN-LOAD-' || g,
                           DATE '2010-01-01', 'Antananarivo', 'Adresse test',
                           '03' || LPAD(g::text, 8, '0'), DATE '2020-01-01', TRUE
                    FROM generate_series(1, 10001) g
                    """);
        }
    }

    @Test
    @DisplayName("10 001 salariés restent accessibles sans plafond frontend")
    void allEmployeesRemainReachableThroughServerPages() throws Exception {
        PageDTO<EmployeCPL> first = employeService.getEmployes(
                "LOAD-", true, null, null, null, "MATRICULE", "ASC", 1, 100);
        PageDTO<EmployeCPL> last = employeService.getEmployes(
                "LOAD-", true, null, null, null, "MATRICULE", "ASC", 101, 100);

        assertThat(first.getTotalElements()).isEqualTo(10_001);
        assertThat(first.getTotalPages()).isEqualTo(101);
        assertThat(first.getContent()).hasSize(100);
        assertThat(last.getContent()).hasSize(1);
        assertThat(last.getContent().get(0).getMatricule()).isEqualTo("LOAD-10001");
    }
}
