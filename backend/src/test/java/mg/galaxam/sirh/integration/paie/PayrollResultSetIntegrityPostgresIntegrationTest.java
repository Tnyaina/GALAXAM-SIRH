package mg.galaxam.sirh.integration.paie;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.paieconfig.RefTypeMouvementPaie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SPRINT UX PAIE V1 FULL V3 — TEST D'INTÉGRITÉ POSTGRESQL DES RESULTSETS & NON-RÉGRESSION 'nature'
 * (Sections 6, 7, 28 & 29 du cahier des charges de certification).
 *
 * Vérifie contre la base PostgreSQL réelle :
 * 1. Présence de la colonne 'nature' et des colonnes de gouvernance dans ref_type_mouvement_paie
 * 2. Succès du mapping complet de RefTypeMouvementPaie.listerActifs et listerTous sans PSQLException
 * 3. Présence des colonnes 'montant_initial', 'montant_impute', 'solde_restant', 'provenance' dans paie_avance_acompte
 * 4. Succès de la projection SELECT paa.* dans PaieAjustementController
 * 5. Disponibilité des endpoints REST associés
 */
@DisplayName("Certification PostgreSQL — Intégrité des ResultSets Paie & Non-régression 'nature'")
public class PayrollResultSetIntegrityPostgresIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("29.1 Non-régression 'nature' : SELECT * FROM ref_type_mouvement_paie mappe 'nature' sans PSQLException")
    void testRefTypeMouvementPaieNatureColumnMapping() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // 1. Test direct de la requête SQL native
            String sql = "SELECT * FROM ref_type_mouvement_paie WHERE est_actif = true";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Au moins un type de mouvement doit être configuré en base");
                
                // Vérification explicite que la colonne 'nature' existe et est non nulle
                assertDoesNotThrow(() -> {
                    String nature = rs.getString("nature");
                    assertNotNull(nature, "La colonne 'nature' ne doit pas être null");
                    assertFalse(nature.trim().isEmpty(), "La colonne 'nature' ne doit pas être vide");
                }, "L'accès à rs.getString('nature') ne doit pas lever de PSQLException");

                // Vérification des autres colonnes ajoutées par sp14
                assertDoesNotThrow(() -> {
                    rs.getString("owner_code");
                    rs.getBoolean("saisie_manuelle_autorisee");
                    rs.getBoolean("periode_obligatoire");
                    rs.getBoolean("motif_obligatoire");
                    rs.getString("provenance_defaut");
                }, "Toutes les colonnes de gouvernance sp14 doivent exister dans le ResultSet");
            }

            // 2. Test via le modèle DAO RefTypeMouvementPaie
            List<RefTypeMouvementPaie> actifs = RefTypeMouvementPaie.listerActifs(conn);
            assertNotNull(actifs, "La liste des types actifs ne doit pas être nulle");
            assertFalse(actifs.isEmpty(), "La liste des types actifs ne doit pas être vide");

            for (RefTypeMouvementPaie mvt : actifs) {
                assertNotNull(mvt.getCode(), "Le code du type de mouvement ne doit pas être nul");
                assertNotNull(mvt.getNature(), "La nature ne doit pas être nulle pour " + mvt.getCode());
                assertNotNull(mvt.getOwnerCode(), "L'owner code ne doit pas être nul pour " + mvt.getCode());
                assertNotNull(mvt.getProvenanceDefaut(), "La provenance par défaut ne doit pas être nulle pour " + mvt.getCode());
            }
        }
    }

    @Test
    @DisplayName("28.1 Intégrité ResultSet paie_avance_acompte : colonnes montant_initial, montant_impute, solde_restant, provenance")
    void testPaieAvanceAcompteColumnsExistInResultSet() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM paie_avance_acompte LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                // Même si la table est vide dans ce test, les métadonnées de colonnes doivent être vérifiées
                var meta = rs.getMetaData();
                boolean hasNatureOrInitial = false;
                boolean hasImpute = false;
                boolean hasSolde = false;
                boolean hasProvenance = false;

                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String col = meta.getColumnName(i).toLowerCase();
                    if ("montant_initial".equals(col)) hasNatureOrInitial = true;
                    if ("montant_impute".equals(col)) hasImpute = true;
                    if ("solde_restant".equals(col)) hasSolde = true;
                    if ("provenance".equals(col)) hasProvenance = true;
                }

                assertTrue(hasNatureOrInitial, "La colonne 'montant_initial' doit exister dans paie_avance_acompte");
                assertTrue(hasImpute, "La colonne 'montant_impute' doit exister dans paie_avance_acompte");
                assertTrue(hasSolde, "La colonne 'solde_restant' doit exister dans paie_avance_acompte");
                assertTrue(hasProvenance, "La colonne 'provenance' doit exister dans paie_avance_acompte");
            }
        }
    }

    @Test
    @DisplayName("28.2 Endpoint API /referentiel/types-mouvement-paie retourne 200 OK avec le champ nature renseigné")
    void testApiTypesMouvementPaieReturnsNature() throws Exception {
        mockMvc.perform(get("/referentiel/types-mouvement-paie")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].nature").exists())
                .andExpect(jsonPath("$[0].ownerCode").exists())
                .andExpect(jsonPath("$[0].saisieManuelleAutorisee").exists());
    }

    @Test
    @DisplayName("28.3 Endpoint API /paie/ajustements retourne 200 OK sans erreur de mapping SQL")
    void testApiPaieAjustementsReturnsOk() throws Exception {
        mockMvc.perform(get("/paie/ajustements")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("28.4 Vérification des sous-menus PAIE_MODULE dans PostgreSQL")
    void testPaieSubmenusInDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT id_menu, libelle, href FROM ref_menu WHERE id_pere = 'PAIE_MODULE' ORDER BY rang";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "PAIE_CENTRE doit exister");
                assertEquals("PAIE_CENTRE", rs.getString("id_menu"));
                assertEquals("/paie", rs.getString("href"));

                assertTrue(rs.next(), "PAIE_CAMPAGNE doit exister");
                assertEquals("PAIE_CAMPAGNE", rs.getString("id_menu"));
                assertEquals("/paie/periodes", rs.getString("href"));

                assertTrue(rs.next(), "PAIE_ACOMPTES doit exister");
                assertEquals("PAIE_ACOMPTES", rs.getString("id_menu"));
                assertEquals("/paie/acomptes-avances", rs.getString("href"));

                assertTrue(rs.next(), "PAIE_HISTORIQUE doit exister");
                assertEquals("PAIE_HISTORIQUE", rs.getString("id_menu"));
                assertEquals("/paie/historique", rs.getString("href"));

                assertTrue(rs.next(), "PAIE_DECLARATIONS doit exister");
                assertEquals("PAIE_DECLARATIONS", rs.getString("id_menu"));
                assertEquals("/paie/declarations", rs.getString("href"));

                assertTrue(rs.next(), "PAIE_PARAMETRES doit exister");
                assertEquals("PAIE_PARAMETRES", rs.getString("id_menu"));
                assertEquals("/admin/parametres?module=paie", rs.getString("href"));
            }
        }
    }

    @Test
    @DisplayName("28.5 Vérification de l'intégrité des colonnes de pointage_cumul_periode (heures_dimanche, heures_ferie)")
    void testPointageCumulColumnsIntegrity() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT total_heures_travaillees, heures_hs_30, heures_hs_50, heures_nuit_total, "
                       + "heures_dimanche_total, heures_ferie_total, heures_absence_retenues, total_minutes_retard, "
                       + "nb_jours_ouvrables, nb_jours_presents, nb_jours_absence, nb_jours_conge, "
                       + "nb_jours_repas, montant_repas, nb_jours_deplacement, montant_deplacement, gele_at "
                       + "FROM pointage_cumul_periode LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                // Doit s'exécuter sans PSQLException "Le nom de colonne X n'a pas été trouvé"
                ResultSetMetaData meta = rs.getMetaData();
                assertNotNull(meta);
                assertEquals(17, meta.getColumnCount());
            }
        }
    }

    @Test
    @DisplayName("28.6 Endpoint API /paie/periode/101/cockpit retourne 200 OK")
    void testCockpit101() throws Exception {
        mockMvc.perform(get("/paie/periode/101/cockpit")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());
    }
}
