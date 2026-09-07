package mg.galaxam.sirh.employe;

import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.employe.EmployeEnfant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("EmployeEnfantIntegrationTest — Test d'intégration persistance PostgreSQL de employe_enfant")
class EmployeEnfantIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Insertion, relecture et mise à jour des nouveaux attributs de employe_enfant")
    void testCrudEmployeEnfant() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Insertion
                EmployeEnfant enf = new EmployeEnfant();
                enf.setIdEmploye(1);
                enf.setNom("TEST-RAKOTO");
                enf.setPrenom("Junior");
                enf.setSexe("M");
                enf.setDateNaissance(Date.valueOf(LocalDate.of(2004, 3, 15)));
                enf.setLieuNaissance("Antananarivo");
                enf.setACharge(true);
                enf.setEstEtudiant(true);
                enf.setDateFinScolarite(Date.valueOf(LocalDate.of(2027, 6, 30)));
                enf.setEstHandicape(false);
                enf.setCertificatFourni(true);

                enf.save(c);
                assertNotNull(enf.getIdEnfant(), "L'id généré doit être renseigné");

                // 2. Relecture
                EmployeEnfant relu = new EmployeEnfant().rechercherParId(enf.getIdEnfant(), c);
                assertNotNull(relu);
                assertEquals("TEST-RAKOTO", relu.getNom());
                assertTrue(relu.isEstEtudiant());
                assertTrue(relu.isCertificatFourni());
                assertFalse(relu.isEstHandicape());
                assertEquals(Date.valueOf(LocalDate.of(2027, 6, 30)), relu.getDateFinScolarite());
                assertTrue(relu.isEligibleReductionIrsa(LocalDate.of(2026, 8, 31)));

                // 3. Mise à jour
                relu.setEstHandicape(true);
                relu.setCertificatFourni(false);
                relu.update(c);

                EmployeEnfant reluApresUpdate = new EmployeEnfant().rechercherParId(enf.getIdEnfant(), c);
                assertTrue(reluApresUpdate.isEstHandicape());
                assertFalse(reluApresUpdate.isCertificatFourni());
                assertTrue(reluApresUpdate.isEligibleReductionIrsa(LocalDate.of(2026, 8, 31)), "Même sans certificat, le statut handicapé confère l'éligibilité");

                // 4. Liste par employe
                List<EmployeEnfant> list = EmployeEnfant.listByEmployeId(1, c);
                assertTrue(list.stream().anyMatch(e -> e.getIdEnfant().equals(enf.getIdEnfant())));

            } finally {
                c.rollback();
            }
        }
    }
}
