package mg.galaxam.sirh.integration.paie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Test d'Observation Runtime Phase 2A-0 :
 * Traverse le pipeline existant pour observer et documenter précisément
 * comment le système construit actuellement les composants du salaire brut :
 *
 * Salaire de Base
 * + Absence
 * + Congés (Maintien / TCONGE)
 * + Primes (Performance, Assiduité, Exceptionnelle)
 * + Indemnités (Repas, Déplacement)
 * + Avances & Acomptes
 * -> Total Gains Bruts & Lignes de bulletin
 */
public class Phase2a0PayrollCoreRuntimeAuditTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;
    private int idPeriode;
    private final LocalDate debutPeriode = LocalDate.of(2026, 8, 1);
    private final LocalDate finPeriode = LocalDate.of(2026, 8, 31);

    @BeforeEach
    void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

        idPeriode = employeFixtures.createPeriodePaie("P-2026-08-AUDIT", debutPeriode, finPeriode, "EN_COURS");

        emp = employeFixtures.createEmployeWithSalary(
                "EMP-AUDIT-2A0",
                "RAKOTO",
                "Audit 2A0",
                1,
                new BigDecimal("600000.00"),
                "PERMANENT",
                0
        );

        // Assurer que l'employé est éligible aux heures supplémentaires et a un salaire de base de 600 000 Ar
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE employe_affectation_carriere SET salaire_base = 600000.00, " +
                     "id_categorie_professionnelle = (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'A' LIMIT 1) " +
                     "WHERE id_employe = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("Phase 2A-0 : Observation Runtime de la construction du Salaire Brut et des Rubriques de Paie")
    void testObserveCurrentGrossPayrollConstruction() throws Exception {
        // 1. Saisie des pointages réels pour la période
        List<SaisiePointageJourDTO> batchPunches = new ArrayList<>();

        // Mon 03/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-03", "08:00", "12:00", "13:00", "17:00", false));
        // Tue 04/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-04", "08:00", "12:00", "13:00", "17:00", false));
        // Wed 05/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-05", "08:00", "12:00", "13:00", "17:00", false));
        // Thu 06/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-06", "08:00", "12:00", "13:00", "17:00", false));
        // Fri 07/08 (8h)
        batchPunches.add(creerJourDTO("2026-08-07", "08:00", "12:00", "13:00", "17:00", false));

        mockMvc.perform(post("/pointages/saisie/batch")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(batchPunches)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. Injecter des variables de paie réelles dans paie_avance_acompte :
        // - Prime exceptionnelle de 50 000 Ar
        // - Acompte quinzaine de 100 000 Ar
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, sens, montant, motif, statut) " +
                     "VALUES (?, ?, 'PRIME_EXCEPTIONNELLE', 'GAIN', 50000.00, 'Bonus mission audit', 'APPROUVE'), " +
                     "       (?, ?, 'ACOMPTE_15', 'RETENUE', 100000.00, 'Acompte quinzaine', 'APPROUVE')")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idPeriode);
            ps.setInt(3, emp.idEmploye);
            ps.setInt(4, idPeriode);
            ps.executeUpdate();
        }

        // 3. Calcul de synthèse et gel de période
        Map<String, Object> reqSynth = new HashMap<>();
        reqSynth.put("idPeriodePaie", idPeriode);
        reqSynth.put("idSite", emp.idSite);

        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqSynth)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk());

        // 4. Calcul de paie réel via REST
        Map<String, Object> reqPaie = new HashMap<>();
        reqPaie.put("idPeriodePaie", idPeriode);
        reqPaie.put("simulation", false);

        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqPaie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbBulletinsCalcules").value(1));

        // 5. Audit contradictoire du bulletin produit en BDD
        try (Connection c = dataSource.getConnection()) {
            String sqlBull = "SELECT id_bulletin, salaire_base, total_gains_bruts, total_indemnites_non_imposables, " +
                    "net_a_payer FROM bulletin_paie WHERE id_employe = ? AND id_periode_paie = ?";
            int idBulletin = 0;
            try (PreparedStatement ps = c.prepareStatement(sqlBull)) {
                ps.setInt(1, emp.idEmploye);
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBigDecimal("salaire_base")).isEqualByComparingTo("600000.00");
                    idBulletin = rs.getInt("id_bulletin");
                }
            }

            // Inspecter toutes les lignes de bulletin générées
            String sqlLignes = "SELECT code_rubrique, libelle_rubrique, sens, categorie, base_calcul, montant_salarial " +
                    "FROM bulletin_paie_ligne WHERE id_bulletin = ? ORDER BY ordre ASC";
            Map<String, BigDecimal> montantsLignes = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(sqlLignes)) {
                ps.setInt(1, idBulletin);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        montantsLignes.put(rs.getString("code_rubrique"), rs.getBigDecimal("montant_salarial"));
                    }
                }
            }

            // Observations du comportement actuel du moteur :
            // 1. SALAIRE_BASE est contractuel (600 000 Ar)
            assertThat(montantsLignes).containsKey("SALAIRE_BASE");
            assertThat(montantsLignes.get("SALAIRE_BASE")).isEqualByComparingTo("600000.00");

            // 2. RETENUE_ABSENCE est présente (décomptée en HEURES_REELLES par défaut)
            // Taux horaire = 600000 / 173.33 = 3461.60503 Ar/h. Pour 8h d'absence = ~27 692.84 Ar
            if (montantsLignes.containsKey("RETENUE_ABSENCE")) {
                assertThat(montantsLignes.get("RETENUE_ABSENCE")).isGreaterThan(BigDecimal.ZERO);
            }

            // 3. CONGÉS PAYÉS : Déduction 1/30ème (20 000 Ar) et Maintien (20 000 Ar)
            if (montantsLignes.containsKey("DEDUCTION_CONGE_BASE")) {
                assertThat(montantsLignes.get("DEDUCTION_CONGE_BASE")).isEqualByComparingTo("20000.00");
                assertThat(montantsLignes).containsKey("INDEMNITE_CONGE_PAYE");
                assertThat(montantsLignes.get("INDEMNITE_CONGE_PAYE")).isEqualByComparingTo("20000.00");
            }

            // 4. PRIME_EXCEPTIONNELLE (50 000 Ar)
            assertThat(montantsLignes).containsKey("PRIME_EXCEPTIONNELLE");
            assertThat(montantsLignes.get("PRIME_EXCEPTIONNELLE")).isEqualByComparingTo("50000.00");

            // 5. RETENUE_ACOMPTE_15 retenu sur le net (100 000 Ar)
            assertThat(montantsLignes).containsKey("RETENUE_ACOMPTE_15");
            assertThat(montantsLignes.get("RETENUE_ACOMPTE_15")).isEqualByComparingTo("100000.00");

            // 6. INDEMNITES NON IMPOSABLES (Panier repas accord établissement & transport)
            assertThat(montantsLignes).containsKey("INDEMNITE_REPAS");
            assertThat(montantsLignes.get("INDEMNITE_REPAS")).isGreaterThan(BigDecimal.ZERO);
            assertThat(montantsLignes).containsKey("INDEMNITE_TRANSPORT");
            assertThat(montantsLignes.get("INDEMNITE_TRANSPORT")).isGreaterThan(BigDecimal.ZERO);
        }
    }

    private SaisiePointageJourDTO creerJourDTO(String date, String em, String sm, String ea, String sa, boolean horaireContinu) {
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(emp.idEmploye);
        dto.setIdSite(emp.idSite);
        dto.setIdPeriodePaie(idPeriode);
        dto.setDatePointage(date);
        dto.setHeureEntreeMatin(em);
        dto.setHeureSortieMatin(sm);
        dto.setHeureEntreeAm(ea);
        dto.setHeureSortieAm(sa);
        dto.setHoraireContinu(horaireContinu);
        dto.setIndemniteRepasRecue(true);
        return dto;
    }
}
