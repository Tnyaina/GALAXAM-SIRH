package mg.galaxam.sirh.integration.conge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.paie.application.PayrollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2A-4C — Tests d'intégration des périodes de référence bornées (max 12 mois) KAJY.
 * Prouve la suppression définitive du cumul tous temps infini et l'interdiction de tout 13ème mois.
 */
@DisplayName("Phase 2A-4C : Périodes de référence bornées et anti-cumul infini")
public class TcongeBoundedReferenceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private PayrollService payrollService;

    private int testEmployeId;

    @BeforeEach
    public void setup() {
        databaseCleaner.cleanTransactionalData();
        testEmployeId = employeFixtures.createActiveEmploye("EMP-TCONGE-REF", "RAKOTO", "Andry", 1).idEmploye;
    }

    private int creerPeriodePaieEtBulletin(int annee, int mois, BigDecimal base, BigDecimal hs, Connection c) throws Exception {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin = debut.plusMonths(1).minusDays(1);
        String code = "P-" + annee + "-" + String.format("%02d", mois);

        int idPer = employeFixtures.createPeriodePaie(code, debut, fin, "EN_COURS");

        // Créer un bulletin paye pour cette période
        String sqlBull = "INSERT INTO bulletin_paie (" +
                "id_periode_paie, id_employe, reference_bulletin, matricule, nom_prenom, intitule_poste, " +
                "nom_site, regime_intervenant, salaire_base, taux_horaire, statut" +
                ") VALUES (?, ?, ?, 'EMP-TCONGE-REF', 'RAKOTO Andry', 'Agent', 'TNR', 'SALARIE_STANDARD', ?, ?, 'VALIDE') " +
                "RETURNING id_bulletin";
        int idBull;
        try (PreparedStatement ps = c.prepareStatement(sqlBull)) {
            ps.setInt(1, idPer);
            ps.setInt(2, testEmployeId);
            ps.setString(3, "B-" + code);
            ps.setBigDecimal(4, base);
            ps.setBigDecimal(5, base.divide(new BigDecimal("173.33"), 4, RoundingMode.HALF_UP));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idBull = rs.getInt(1);
            }
        }

        if (hs != null && hs.compareTo(BigDecimal.ZERO) > 0) {
            String sqlLigne = "INSERT INTO bulletin_paie_ligne (" +
                    "id_bulletin, code_rubrique, libelle_rubrique, categorie, sens, montant_salarial" +
                    ") VALUES (?, 'HS_HEBDO', 'Heures Supplémentaires', 'HEURES_SUP', 'GAIN', ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlLigne)) {
                ps.setInt(1, idBull);
                ps.setBigDecimal(2, hs);
                ps.executeUpdate();
            }
        }

        return idPer;
    }

    @Test
    @DisplayName("shouldThrowExceptionWhenNoReferencePeriodIsConfigured")
    void shouldThrowExceptionWhenNoReferencePeriodIsConfigured() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> congeTauxApplicableService.resoudrePeriodeReferenceStrict(testEmployeId, LocalDate.of(2027, 1, 1), c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_REFERENCE_PERIOD_MISSING");
        }
    }

    @Test
    @DisplayName("shouldAccumulateBounded12MonthsAndForbid13thMonthInSameReference")
    void shouldAccumulateBounded12MonthsAndForbid13thMonthInSameReference() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Création explicite de la référence A (12 mois : 01/01/2027 au 31/12/2027)
            LocalDate debutRef = LocalDate.of(2027, 1, 1);
            LocalDate finRef = LocalDate.of(2027, 12, 31);
            int idRef = congeTauxApplicableService.creerPeriodeReference(testEmployeId, debutRef, finRef, 1, c);
            assertThat(idRef).isPositive();

            // 2. Traitement des 12 mois de paie successifs
            BigDecimal base = new BigDecimal("300000.00");
            BigDecimal hs = new BigDecimal("10000.00"); // Base totale = 310 000 Ar

            for (int mois = 1; mois <= 12; mois++) {
                int idPer = creerPeriodePaieEtBulletin(2027, mois, base, hs, c);
                payrollService.cloturerDonneesPaie(idPer, 1, c);

                // Vérifier dans conge_journal_droit
                String sqlVerif = "SELECT numero_mois_reference, cumul_jours_droit, cumul_valorisation, taux_journalier_conge " +
                        "FROM conge_journal_droit WHERE id_employe = ? AND annee = 2027 AND mois = ?";
                try (PreparedStatement ps = c.prepareStatement(sqlVerif)) {
                    ps.setInt(1, testEmployeId);
                    ps.setInt(2, mois);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt("numero_mois_reference")).isEqualTo(mois);

                        BigDecimal attenduDroits = new BigDecimal("2.5").multiply(BigDecimal.valueOf(mois));
                        assertThat(rs.getBigDecimal("cumul_jours_droit")).isEqualByComparingTo(attenduDroits);
                    }
                }
            }

            // 3. Vérification de la clôture de la référence A au 12ème mois
            String sqlRef = "SELECT statut, taux_calcule_cloture, cumul_droits_cloture, cumul_valorisation_cloture " +
                    "FROM conge_periode_reference WHERE id_periode_reference = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlRef)) {
                ps.setInt(1, idRef);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("statut")).isEqualTo("CLOTUREE");
                    assertThat(rs.getBigDecimal("cumul_droits_cloture")).isEqualByComparingTo("30.0000");
                    assertThat(rs.getBigDecimal("taux_calcule_cloture")).isNotNull();
                    // TCONGE calculé = 310 000 / 30 = 10 333.33 Ar/j
                    assertThat(rs.getBigDecimal("taux_calcule_cloture")).isEqualByComparingTo("10333.33");
                }
            }

            // 4. Invariant : Tenter d'insérer un 13ème mois rattaché à cette même référence lève TCONGE_REFERENCE_CAPACITY_EXCEEDED
            // (Par exemple en créant une période de paie en Janvier 2028 alors que la référence est configurée jusqu'à Décembre 2027)
            // On élargit exceptionnellement la date_fin de la référence en BDD pour tester la barrière des 12 mois
            try (PreparedStatement psExtend = c.prepareStatement("UPDATE conge_periode_reference SET date_fin = DATE '2028-02-28', statut = 'OUVERTE' WHERE id_periode_reference = ?")) {
                psExtend.setInt(1, idRef);
                psExtend.executeUpdate();
            }

            int idPer13 = creerPeriodePaieEtBulletin(2028, 1, base, hs, c);
            assertThatThrownBy(() -> payrollService.cloturerDonneesPaie(idPer13, 1, c))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_REFERENCE_CAPACITY_EXCEEDED");
        }
    }

    @Test
    @DisplayName("shouldResetCumulForSecondReferenceWithoutInfiniteAccumulation")
    void shouldResetCumulForSecondReferenceWithoutInfiniteAccumulation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Référence A : 2027 (12 mois)
            int idRefA = congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 1, c
            );
            // Simuler la clôture de la référence A (30j de droits cumulés, 3 000 000 Ar valorisés)
            congeTauxApplicableService.cloturerPeriodeReference(
                    idRefA, new BigDecimal("30.0000"), new BigDecimal("3000000.00"), new BigDecimal("10000.00"), c
            );

            // Insertion manuelle d'un journal au mois 12 de 2027
            String sqlJ2027 = "INSERT INTO conge_journal_droit (" +
                    "id_employe, annee, mois, salaire_base, jours_travailles_formule, jours_droit_acquis, " +
                    "base_valorisation, valorisation_mensuelle, cumul_jours_droit, cumul_valorisation, taux_journalier_conge, " +
                    "id_periode_reference, numero_mois_reference" +
                    ") VALUES (?, 2027, 12, 300000, 30, 2.5, 300000, 25000, 30.0, 300000.0, 10000.0, ?, 12)";
            try (PreparedStatement ps = c.prepareStatement(sqlJ2027)) {
                ps.setInt(1, testEmployeId);
                ps.setInt(2, idRefA);
                ps.executeUpdate();
            }

            // Référence B : 2028 (Nouvelle référence explicitement créée)
            int idRefB = congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31), 1, c
            );

            // Traitement du Mois 1 de la Référence B (Janvier 2028)
            int idPerB1 = creerPeriodePaieEtBulletin(2028, 1, new BigDecimal("300000.00"), BigDecimal.ZERO, c);
            payrollService.cloturerDonneesPaie(idPerB1, 1, c);

            // Preuve ANTI-CUMUL INFINI : Le Mois 1 de Référence B doit avoir des cumuls PROPRES à sa référence
            String sqlCheckB = "SELECT numero_mois_reference, cumul_jours_droit, cumul_valorisation " +
                    "FROM conge_journal_droit WHERE id_employe = ? AND annee = 2028 AND mois = 1";
            try (PreparedStatement ps = c.prepareStatement(sqlCheckB)) {
                ps.setInt(1, testEmployeId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt("numero_mois_reference")).isEqualTo(1); // Mois 1 de la référence
                    // Invariant : Le cumul repart à 2.5j et NON à 32.5j !
                    assertThat(rs.getBigDecimal("cumul_jours_droit")).isEqualByComparingTo("2.5000");
                    // La valorisation repart à 25 000 Ar et NON à 325 000 Ar !
                    assertThat(rs.getBigDecimal("cumul_valorisation")).isEqualByComparingTo("25000.00");
                }
            }
        }
    }
}
