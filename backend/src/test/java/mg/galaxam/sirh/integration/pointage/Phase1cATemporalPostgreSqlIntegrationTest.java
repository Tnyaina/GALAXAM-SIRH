package mg.galaxam.sirh.integration.pointage;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.model.pointage.calculation.DaySpecialType;
import mg.galaxam.sirh.model.pointage.calculation.NightWorkType;
import mg.galaxam.sirh.model.pointage.calculation.OvertimeTier;
import mg.galaxam.sirh.model.pointage.calculation.QualifiedTimeBucket;
import mg.galaxam.sirh.model.pointage.service.PointageSyntheseService;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Phase1cATemporalPostgreSqlIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private DataSource dataSource;

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        LocalDate dEmb = LocalDate.of(2026, 1, 1);
        emp = employeFixtures.createActiveEmploye("EMP_1CA", "NIGHTSHIFT", "Jean", 1, dEmb);

        LocalDate deb = LocalDate.of(2026, 9, 1);
        LocalDate fin = LocalDate.of(2026, 9, 30);
        idPeriode = employeFixtures.createPeriodePaie("P-2026-09", deb, fin, "EN_COURS");
    }

    @Test
    @DisplayName("Section 49: Pipeline complet PostgreSQL : pointage 21:00->05:00 -> WorkInterval -> partition -> projection -> pointage_snapshot_bucket -> reload")
    void testCrossMidnightFullPipelinePostgreSql() throws Exception {
        LocalDate datePtg = LocalDate.of(2026, 9, 17); // jeudi

        // 1. Insérer un pointage cross-midnight en BDD (21:00 -> 05:00, horaire continu de nuit)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, " +
                     "heure_entree_matin, heure_sortie_am, total_heures, horaire_continu_jour, id_statut_jour) " +
                     "VALUES (?, ?, ?, ?, '21:00', '05:00', 8.0, TRUE, 1)")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            ps.setInt(3, emp.idSite);
            ps.setDate(4, java.sql.Date.valueOf(datePtg));
            ps.executeUpdate();
        }

        // 2. Geler la période pour créer le snapshot versionné
        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.gelerPeriode(idPeriode, 1, c);
        }

        // 3. Vérifier la persistance dans pointage_cumul_periode et pointage_snapshot_bucket
        int idCumul;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_cumul, statut_snapshot, version_snapshot FROM pointage_cumul_periode " +
                     "WHERE id_employe = ? AND id_periode_paie = ? AND statut_snapshot = 'GELE'")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                idCumul = rs.getInt("id_cumul");
                assertThat(rs.getString("statut_snapshot")).isEqualTo("GELE");
                assertThat(rs.getInt("version_snapshot")).isGreaterThanOrEqualTo(1);
            }
        }

        // 4. Recharger les buckets depuis pointage_snapshot_bucket
        List<QualifiedTimeBucket> buckets;
        try (Connection c = dataSource.getConnection()) {
            buckets = pointageSyntheseService.getSnapshotBuckets(idCumul, c);
        }

        assertThat(buckets).isNotNull();
        assertThat(buckets).hasSize(3);

        // Segment 1 : 17/09 21:00 -> 22:00 (60 min, DAY)
        QualifiedTimeBucket b1 = buckets.get(0);
        assertThat(b1.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 21, 0));
        assertThat(b1.end()).isEqualTo(LocalDateTime.of(2026, 9, 17, 22, 0));
        assertThat(b1.minutes()).isEqualTo(60);
        assertThat(b1.nightType()).isEqualTo(NightWorkType.NONE);
        assertThat(b1.dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(b1.overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(b1.payrollPeriodId()).isEqualTo(idPeriode);

        // Segment 2 : 17/09 22:00 -> 18/09 00:00 (120 min, NIGHT)
        QualifiedTimeBucket b2 = buckets.get(1);
        assertThat(b2.start()).isEqualTo(LocalDateTime.of(2026, 9, 17, 22, 0));
        assertThat(b2.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 0, 0));
        assertThat(b2.minutes()).isEqualTo(120);
        assertThat(b2.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b2.dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(b2.overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(b2.payrollPeriodId()).isEqualTo(idPeriode);

        // Segment 3 : 18/09 00:00 -> 18/09 05:00 (300 min, NIGHT le lendemain)
        QualifiedTimeBucket b3 = buckets.get(2);
        assertThat(b3.start()).isEqualTo(LocalDateTime.of(2026, 9, 18, 0, 0));
        assertThat(b3.end()).isEqualTo(LocalDateTime.of(2026, 9, 18, 5, 0));
        assertThat(b3.minutes()).isEqualTo(300);
        assertThat(b3.nightType()).isEqualTo(NightWorkType.NIGHT_UNCLASSIFIED);
        assertThat(b3.dayType()).isEqualTo(DaySpecialType.STANDARD);
        assertThat(b3.overtimeTier()).isEqualTo(OvertimeTier.REGULAR);
        assertThat(b3.payrollPeriodId()).isEqualTo(idPeriode);

        // Total = 480 min
        long totalMins = buckets.stream().mapToLong(QualifiedTimeBucket::minutes).sum();
        assertThat(totalMins).isEqualTo(480);

        // 5. Vérifier la consommation par le contrat PayrollAttendanceInput
        PayrollCalculationInput input;
        try (Connection c = dataSource.getConnection()) {
            input = payrollService.preparerInput(emp.idEmploye, idPeriode, false, c);
        }
        assertThat(input.getAttendanceInput()).isNotNull();
        assertThat(input.getAttendanceInput().qualifiedTimeBuckets()).hasSize(3);
        assertThat(input.getAttendanceInput().qualifiedTimeBuckets().get(0).minutes()).isEqualTo(60);
        assertThat(input.getAttendanceInput().qualifiedTimeBuckets().get(1).minutes()).isEqualTo(120);
        assertThat(input.getAttendanceInput().qualifiedTimeBuckets().get(2).minutes()).isEqualTo(300);
    }

    @Test
    @DisplayName("Section 19: Versionnement réel des snapshots V1/V2 - Immutabilité stricte et traçabilité")
    void testSnapshotVersioningV1V2PostgreSql() throws Exception {
        LocalDate datePtg = LocalDate.of(2026, 9, 17);

        // 1. Créer pointage initial : 21:00 -> 05:00 (8h, 480 min)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, " +
                     "heure_entree_matin, heure_sortie_am, total_heures, horaire_continu_jour, id_statut_jour) " +
                     "VALUES (?, ?, ?, ?, '21:00', '05:00', 8.0, TRUE, 1)")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            ps.setInt(3, emp.idSite);
            ps.setDate(4, java.sql.Date.valueOf(datePtg));
            ps.executeUpdate();
        }

        // 2. Calculer & Geler -> V1
        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.gelerPeriode(idPeriode, 1, c);
        }

        // 3. Récupérer snapshot V1 + buckets V1
        int idCumulV1;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_cumul, statut_snapshot, version_snapshot FROM pointage_cumul_periode " +
                     "WHERE id_employe = ? AND id_periode_paie = ? AND version_snapshot = 1")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                idCumulV1 = rs.getInt("id_cumul");
                assertThat(rs.getString("statut_snapshot")).isEqualTo("GELE");
            }
        }

        List<QualifiedTimeBucket> bucketsV1;
        try (Connection c = dataSource.getConnection()) {
            bucketsV1 = pointageSyntheseService.getSnapshotBuckets(idCumulV1, c);
        }
        assertThat(bucketsV1).hasSize(3);
        long totalV1 = bucketsV1.stream().mapToLong(QualifiedTimeBucket::minutes).sum();
        assertThat(totalV1).isEqualTo(480);

        // 4. Rouvrir (dégeler) la période
        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.degelerPeriode(idPeriode, c);
        }

        // Vérifier que V1 est maintenant marquée REMPLACE
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT statut_snapshot FROM pointage_cumul_periode WHERE id_cumul = ?")) {
            ps.setInt(1, idCumulV1);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("statut_snapshot")).isEqualTo("REMPLACE");
            }
        }

        // 5. Modifier le pointage : changer de 21:00->05:00 à 21:00->06:00 (9h, 540 min)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE pointage_jour SET heure_sortie_am = '06:00', total_heures = 9.0 " +
                     "WHERE id_employe = ? AND id_periode_paie = ? AND date_pointage = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            ps.setDate(3, java.sql.Date.valueOf(datePtg));
            int updated = ps.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }

        // 6. Recalculer & Geler -> V2
        try (Connection c = dataSource.getConnection()) {
            pointageSyntheseService.gelerPeriode(idPeriode, 1, c);
        }

        // 7. Récupérer snapshot V2 + buckets V2
        int idCumulV2;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_cumul, statut_snapshot, version_snapshot, remplace_id_cumul FROM pointage_cumul_periode " +
                     "WHERE id_employe = ? AND id_periode_paie = ? AND version_snapshot = 2")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                idCumulV2 = rs.getInt("id_cumul");
                assertThat(rs.getString("statut_snapshot")).isEqualTo("GELE");
                assertThat(rs.getInt("remplace_id_cumul")).isEqualTo(idCumulV1);
            }
        }

        List<QualifiedTimeBucket> bucketsV2;
        try (Connection c = dataSource.getConnection()) {
            bucketsV2 = pointageSyntheseService.getSnapshotBuckets(idCumulV2, c);
        }
        assertThat(bucketsV2).hasSize(4); // 21h-22h (60), 22h-00h (120), 00h-05h (300), 05h-06h (60)
        long totalV2 = bucketsV2.stream().mapToLong(QualifiedTimeBucket::minutes).sum();
        assertThat(totalV2).isEqualTo(540);

        // 8. VERIFICATION D'IMMUTABILITE STRICTE :
        // V1 existe toujours, V1 != V2
        assertThat(idCumulV1).isNotEqualTo(idCumulV2);

        // Buckets V1 existent toujours et sont STRICTEMENT INCHANGÉS
        List<QualifiedTimeBucket> bucketsV1Reloaded;
        try (Connection c = dataSource.getConnection()) {
            bucketsV1Reloaded = pointageSyntheseService.getSnapshotBuckets(idCumulV1, c);
        }
        assertThat(bucketsV1Reloaded).hasSize(3);
        long totalV1Reloaded = bucketsV1Reloaded.stream().mapToLong(QualifiedTimeBucket::minutes).sum();
        assertThat(totalV1Reloaded).isEqualTo(480);
        assertThat(bucketsV1Reloaded).isEqualTo(bucketsV1);
    }
}
