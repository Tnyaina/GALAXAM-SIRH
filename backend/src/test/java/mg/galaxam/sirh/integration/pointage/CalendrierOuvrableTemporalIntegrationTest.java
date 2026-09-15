package mg.galaxam.sirh.integration.pointage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CalendrierOuvrableTemporalIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CalendrierOuvrableEngine engine;

    private CreatedEmploye employe;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();
        employe = employeFixtures.createActiveEmploye(
                "EMP-TEMP-CAL", "Rakoto", "Temporal", 1, LocalDate.of(2025, 1, 1));
    }

    @Test
    void resoutLeSiteAvantLeJourEtApresLeChangement() throws Exception {
        ajouterAffectationSite2(LocalDate.of(2026, 1, 15));
        terminerAffectationInitiale(LocalDate.of(2026, 1, 14));

        try (Connection c = dataSource.getConnection()) {
            assertThat(engine.resolveSiteAt(employe.idEmploye, LocalDate.of(2026, 1, 14), c)).isEqualTo(1);
            assertThat(engine.resolveSiteAt(employe.idEmploye, LocalDate.of(2026, 1, 15), c)).isEqualTo(2);
            assertThat(engine.resolveSiteAt(employe.idEmploye, LocalDate.of(2026, 1, 16), c)).isEqualTo(2);
        }
    }

    @Test
    void bloqueUnTrouDeValidite() throws Exception {
        ajouterAffectationSite2(LocalDate.of(2026, 1, 16));
        terminerAffectationInitiale(LocalDate.of(2026, 1, 14));

        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> engine.resolveSiteAt(
                    employe.idEmploye, LocalDate.of(2026, 1, 15), c))
                    .isInstanceOf(SirhBusinessException.class)
                    .satisfies(error -> assertThat(((SirhBusinessException) error).getIssues().get(0).code())
                            .isEqualTo("AFFECTATION_GAP"));
        }
    }

    @Test
    void bloqueUnChevauchement() throws Exception {
        ajouterAffectationSite2(LocalDate.of(2026, 1, 15));
        terminerAffectationInitiale(LocalDate.of(2026, 1, 16));

        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> engine.resolveSiteAt(
                    employe.idEmploye, LocalDate.of(2026, 1, 15), c))
                    .isInstanceOf(SirhBusinessException.class)
                    .satisfies(error -> assertThat(((SirhBusinessException) error).getIssues().get(0).code())
                            .isEqualTo("AFFECTATION_OVERLAP"));
        }
    }

    private void terminerAffectationInitiale(LocalDate dateFin) throws Exception {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "UPDATE employe_affectation_carriere SET date_fin = ? WHERE id_affectation = ?")) {
            ps.setDate(1, Date.valueOf(dateFin));
            ps.setInt(2, employe.idAffectation);
            ps.executeUpdate();
        }
    }

    private void ajouterAffectationSite2(LocalDate dateDebut) throws Exception {
        String sql = "INSERT INTO employe_affectation_carriere "
                + "(id_employe,id_contrat,id_statut_rh,id_motif_mouvement,id_site,id_departement,id_service,"
                + "id_fonction,id_responsable_hierarchique,id_categorie_professionnelle,id_mode_calcul_paie,"
                + "salaire_base,date_debut,date_fin,description,commentaire) "
                + "SELECT id_employe,id_contrat,id_statut_rh,id_motif_mouvement,2,id_departement,id_service,"
                + "id_fonction,id_responsable_hierarchique,id_categorie_professionnelle,id_mode_calcul_paie,"
                + "salaire_base,?,NULL,'Mutation temporelle test',commentaire "
                + "FROM employe_affectation_carriere WHERE id_affectation = ?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(dateDebut));
            ps.setInt(2, employe.idAffectation);
            ps.executeUpdate();
        }
    }
}
