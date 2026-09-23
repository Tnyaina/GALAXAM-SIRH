package mg.galaxam.sirh.integration.conge;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.paie.application.StcService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

public class CongeServiceIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye emp;

    @Autowired
    private CongeService congeService;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        // Embauché il y a 2 ans pour valider l'ancienneté légale (Art. 84 : 12 mois)
        LocalDate dateEmbauche = LocalDate.now().minusYears(2);
        emp = employeFixtures.createActiveEmploye("EMP020", "RAVOAVIHASINA", "Fanja", 1, dateEmbauche);
    }

    private int resolveTypeCongeId(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("Type de congé non trouvé : " + code);
    }

    private double getSoldeActuel(int idEmploye) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = ?")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getDouble("solde_actuel");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 0.0;
    }

    @Test
    @DisplayName("API Congé : Demande nominale et validation FIFO via endpoints REST")
    public void testCreerEtValiderDemande_FIFO() throws Exception {
        // 1. Créditer 20 jours de congés
        employeFixtures.crediterSoldeConge(emp.idEmploye, 20.0);

        // 2. Créer une demande de 5 jours de congé annuel via POST /conges/demandes
        LocalDate debut = LocalDate.now().plusDays(20);
        LocalDate fin = debut.plusDays(4); // 5 jours calendaires

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());
        req.put("commentaire", "Congé annuel d'été");

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(resStr);
        int idDemande = json.get("idDemandeConge").asInt();
        assertTrue(idDemande > 0);

        // Vérifier statut EN_ATTENTE en base
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT s.code FROM demande_conge dc JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge WHERE dc.id_demande_conge = ?")) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_ATTENTE", rs.getString("code"));
            }
        }

        // 3. Valider la demande via POST /conges/demandes/{id}/valider
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // 4. Vérifier la création d'un mouvement de type PRISE dans mouvement_solde_conge
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ? AND id_demande_conge = ?")) {
            ps.setInt(1, emp.idEmploye);
            ps.setInt(2, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Un mouvement de prise doit avoir été enregistré pour cette demande");
                assertEquals("PRISE", rs.getString("type_mouvement"));
                assertEquals(-5.0, rs.getDouble("nb_jours"), 0.01, "La prise de congé doit être un mouvement négatif (-5j)");
            }
        }
    }

    @Test
    @DisplayName("API Congé : Scission automatique arrêt maladie > 30j (maintien + sans solde) via REST")
    public void testScissionAutomatiqueArretMaladie() throws Exception {
        LocalDate debut = LocalDate.now().plusDays(2);
        LocalDate fin = debut.plusDays(44); // 45 jours calendaires (> 30j)

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("MALADIE"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());
        req.put("justificatifFourni", true);
        req.put("commentaire", "Hospitalisation et convalescence");

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated());

        // Vérifier qu'il existe 2 demandes en base pour cet employé :
        // 1ère demande : MALADIE de 30 jours (du debut au debut + 29 jours)
        // 2ème demande : MALADIE_SANS_SOLDE pour le reliquat (15 jours)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT dc.id_demande_conge, t.code, dc.date_debut, dc.date_fin, dc.nb_jours_decomptes " +
                     "FROM demande_conge dc " +
                     "JOIN ref_type_conge t ON dc.id_type_conge = t.id_type_conge " +
                     "WHERE dc.id_employe = ? ORDER BY dc.date_debut ASC")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "La 1ère tranche (30j) doit exister");
                assertEquals("MALADIE", rs.getString("code"));
                assertEquals(30.0, rs.getDouble("nb_jours_decomptes"), 0.01);

                assertTrue(rs.next(), "La 2ème tranche (15j sans solde CNaPS) doit avoir été scindée");
                assertEquals("MALADIE_SANS_SOLDE", rs.getString("code"));
                assertEquals(15.0, rs.getDouble("nb_jours_decomptes"), 0.01);
            }
        }
    }

    @Test
    @DisplayName("API Congé : Refus d'une demande avec motif via POST /conges/demandes/{id}/refuser")
    public void testRefuserDemandeConge() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 10.0);

        LocalDate debut = LocalDate.now().plusDays(20);
        LocalDate fin = debut.plusDays(2);

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resStr).get("idDemandeConge").asInt();

        String motifRefus = "Nécessité de service impérieuse";
        Map<String, String> refusBody = new HashMap<>();
        refusBody.put("motifRefus", motifRefus);

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/refuser")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(refusBody)))
                .andExpect(status().isOk());

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT s.code, dc.motif_refus FROM demande_conge dc " +
                     "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                     "WHERE dc.id_demande_conge = ?")) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("REFUSE", rs.getString("code"));
                assertEquals(motifRefus, rs.getString("motif_refus"));
            }
        }
    }

    @Test
    @DisplayName("RG-CONGE-04 : Débit immédiat du solde à la validation (avant = 10, après = 7)")
    public void testDebitImmediatValidation() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 10.0);
        assertEquals(10.0, getSoldeActuel(emp.idEmploye), 0.01);

        LocalDate debut = LocalDate.now().plusDays(10);
        LocalDate fin = debut.plusDays(2); // 3 jours calendaires

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resStr).get("idDemandeConge").asInt();

        // Avant validation : solde = 10
        assertEquals(10.0, getSoldeActuel(emp.idEmploye), 0.01);

        // Validation
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // Après validation : mouvement PRISE = -3, solde = 7
        assertEquals(7.0, getSoldeActuel(emp.idEmploye), 0.01);
    }

    @Test
    @DisplayName("RG-CONGE-03 : Jour férié inclus dans le congé -> aucun mouvement de restitution, solde débité intégralement")
    public void testJourFerieInclus_AucuneRestitution() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 10.0);

        LocalDate debut = LocalDate.now().plusDays(15);
        LocalDate fin = debut.plusDays(4); // 5 jours calendaires

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resStr).get("idDemandeConge").asInt();

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // Solde = 5.0 (et NON 6.0)
        assertEquals(5.0, getSoldeActuel(emp.idEmploye), 0.01);

        // Vérifier absence de restitution automatique
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement IN ('ANNULATION', 'BONIFICATION', 'AJUSTEMENT')")) {
            ps.setInt(1, emp.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Aucun mouvement de restitution automatique lié aux fériés");
            }
        }
    }

    @Test
    @DisplayName("Modification non-destructive d'une demande validée : 5j -> 3j via AJUSTEMENT +2j sans violation unicité")
    public void testModificationDemandeValidee_PreserveAuditEtNonDestructif() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 20.0);

        LocalDate debut = LocalDate.now().plusDays(30);
        LocalDate fin = debut.plusDays(4); // 5 jours calendaires

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resStr).get("idDemandeConge").asInt();

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        assertEquals(15.0, getSoldeActuel(emp.idEmploye), 0.01);

        // Raccourcir à 3 jours (du debut au debut + 2 jours)
        LocalDate nouvelleFin = debut.plusDays(2);
        Map<String, Object> modifReq = new HashMap<>();
        modifReq.put("idEmploye", emp.idEmploye);
        modifReq.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        modifReq.put("dateDebut", debut.toString());
        modifReq.put("dateFin", nouvelleFin.toString());
        modifReq.put("commentaire", "Raccourcissement de 5j à 3j");

        mockMvc.perform(put("/conges/demandes/" + idDemande)
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(modifReq)))
                .andExpect(status().isOk());

        // Solde = 20 - 3 = 17.0
        assertEquals(17.0, getSoldeActuel(emp.idEmploye), 0.01);

        // Vérifier les mouvements
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ? ORDER BY id_mouvement ASC")) {
            ps.setInt(1, idDemande);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PRISE", rs.getString("type_mouvement"));
                assertEquals(-5.0, rs.getDouble("nb_jours"), 0.01);

                assertTrue(rs.next());
                assertEquals("AJUSTEMENT", rs.getString("type_mouvement"));
                assertEquals(2.0, rs.getDouble("nb_jours"), 0.01);
            }
        }
    }

    @Test
    @DisplayName("Sécurité Période Clôturée : Interdiction d'annuler ou modifier un congé sur une période scellée")
    public void testInterdictionAnnulationOuModification_SurPeriodePaieCloturee() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 15.0);

        LocalDate debut = LocalDate.now().plusMonths(3).withDayOfMonth(10);
        LocalDate fin = debut.plusDays(4);

        Map<String, Object> req = new HashMap<>();
        req.put("idEmploye", emp.idEmploye);
        req.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        req.put("dateDebut", debut.toString());
        req.put("dateFin", fin.toString());

        String resStr = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resStr).get("idDemandeConge").asInt();

        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk());

        // Créer une période de paie CLOTUREE couvrant ces dates
        LocalDate debutMois = debut.withDayOfMonth(1);
        LocalDate finMois = debut.withDayOfMonth(debut.lengthOfMonth());
        try (Connection c = dataSource.getConnection();
             PreparedStatement psP = c.prepareStatement(
                     "INSERT INTO periode_paie (id_statut_periode, code, libelle, date_debut, date_fin) " +
                     "VALUES ((SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'CLOTUREE'), ?, 'Période Test Clôturée', ?, ?)")) {
            psP.setString(1, "TEST-" + debut.getYear() + "-" + debut.getMonthValue());
            psP.setDate(2, Date.valueOf(debutMois));
            psP.setDate(3, Date.valueOf(finMois));
            psP.executeUpdate();
        }

        // 1. Tentative d'annulation -> Rejetée (4xx)
        Map<String, String> cancelReq = new HashMap<>();
        cancelReq.put("motifAnnulation", "Annulation tardive");
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/annuler")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(cancelReq)))
                .andExpect(status().is4xxClientError());

        // 2. Tentative de modification -> Rejetée (4xx)
        Map<String, Object> modifReq = new HashMap<>();
        modifReq.put("idEmploye", emp.idEmploye);
        modifReq.put("idTypeConge", resolveTypeCongeId("ANNUEL"));
        modifReq.put("dateDebut", debut.toString());
        modifReq.put("dateFin", debut.plusDays(2).toString());

        mockMvc.perform(put("/conges/demandes/" + idDemande)
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(modifReq)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Grand-Livre Canonique : Acquisition unifiée et idempotente (0 abs = 2.5j, 3 abs = 2.25j)")
    public void testAcquisitionCanoniqueEtIdempotence() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Première acquisition sans absence -> 2.5 jours
            BigDecimal acq1 = congeService.enregistrerAcquisitionMensuelle(emp.idEmploye, 2026, 11, BigDecimal.ZERO, null, c);
            assertEquals(0, acq1.compareTo(new BigDecimal("2.50")));

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), SUM(nb_jours) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' AND EXTRACT(MONTH FROM date_mouvement) = 11")) {
                ps.setInt(1, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Exactement 1 ligne d'acquisition");
                    assertEquals(2.5, rs.getDouble(2), 0.01);
                }
            }

            // 2. Rejeu immédiat (idempotence) -> doit retourner 2.5j sans doubler
            BigDecimal acq2 = congeService.enregistrerAcquisitionMensuelle(emp.idEmploye, 2026, 11, BigDecimal.ZERO, null, c);
            assertEquals(0, acq2.compareTo(new BigDecimal("2.50")));

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), SUM(nb_jours) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' AND EXTRACT(MONTH FROM date_mouvement) = 11")) {
                ps.setInt(1, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Toujours exactement 1 seule ligne après rejeu");
                    assertEquals(2.5, rs.getDouble(2), 0.01);
                }
            }

            // 3. Appel ultérieur avec 3 jours d'absence injustifiée -> ajustement canonique à 2.25j
            BigDecimal acq3 = congeService.enregistrerAcquisitionMensuelle(emp.idEmploye, 2026, 11, new BigDecimal("3.0"), null, c);
            assertEquals(0, acq3.compareTo(new BigDecimal("2.25")));

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*), SUM(nb_jours) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' AND EXTRACT(MONTH FROM date_mouvement) = 11")) {
                ps.setInt(1, emp.idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1), "Toujours 1 seule ligne mise à jour");
                    assertEquals(2.25, rs.getDouble(2), 0.01);
                }
            }
        }
    }

    @Test
    @DisplayName("RG-CONGE-05 : Multi-périodes, week-end et férié inclus dans NBCONGE sans double comptage")
    public void testNbJoursCongeValorises_MultiPeriodesEtFerie() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 30.0);

        // Congé du 28/10/2026 au 04/11/2026 (8 jours calendaires complets)
        // Inclut week-end (31/10-01/11) et férié Toussaint (01/11)
        LocalDate debut = LocalDate.of(2026, 10, 28);
        LocalDate fin = LocalDate.of(2026, 11, 4);

        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            mg.galaxam.sirh.conge.persistence.DemandeConge d = new mg.galaxam.sirh.conge.persistence.DemandeConge();
            d.setIdEmploye(emp.idEmploye);
            d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
            d.setDateDebut(Date.valueOf(debut));
            d.setDateFin(Date.valueOf(fin));
            d.setCommentaire("Congé chevauchant deux périodes de paie");
            congeService.creerDemande(d, 1, c);
            idDemande = d.getIdDemandeConge();
            congeService.validerDemande(idDemande, 1, "Validation DAF", c);
        }

        try (Connection c = dataSource.getConnection()) {
            // 1. Grand-livre : un seul mouvement de prise de -8 jours
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT type_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ?")) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("PRISE", rs.getString("type_mouvement"));
                    assertEquals(-8.0, rs.getDouble("nb_jours"), 0.01);
                }
            }

            // 2. Valorisation Paie P1 (Octobre : 28/10 -> 31/10 = 4 jours)
            LocalDate p1Deb = LocalDate.of(2026, 10, 1);
            LocalDate p1Fin = LocalDate.of(2026, 10, 31);
            BigDecimal nbP1 = congeService.calculerNbJoursCongeValorises(emp.idEmploye, p1Deb, p1Fin, c);
            assertEquals(0, nbP1.compareTo(new BigDecimal("4.0")), "P1 doit valoriser 4 jours calendaires");

            // 3. Valorisation Paie P2 (Novembre : 01/11 -> 04/11 = 4 jours, dont 01/11 férié)
            LocalDate p2Deb = LocalDate.of(2026, 11, 1);
            LocalDate p2Fin = LocalDate.of(2026, 11, 30);
            BigDecimal nbP2 = congeService.calculerNbJoursCongeValorises(emp.idEmploye, p2Deb, p2Fin, c);
            assertEquals(0, nbP2.compareTo(new BigDecimal("4.0")), "P2 doit valoriser 4 jours calendaires avec férié inclus");

            // 4. Somme P1 + P2 = 8 jours (totalité des jours débités sans chevauchement)
            assertEquals(0, nbP1.add(nbP2).compareTo(new BigDecimal("8.0")));
        }
    }

    @Test
    @DisplayName("RG-CONGE-05 : Indépendance Pointage vs NBCONGE (Vendredi-Dimanche = 3j valorisés vs 8h attendues)")
    public void testPointageVsNbConge_IndependanceDesMetriques() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 15.0);

        // Congé Vendredi 16 Octobre -> Dimanche 18 Octobre 2026
        LocalDate ven = LocalDate.of(2026, 10, 16);
        LocalDate dim = LocalDate.of(2026, 10, 18);

        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            mg.galaxam.sirh.conge.persistence.DemandeConge d = new mg.galaxam.sirh.conge.persistence.DemandeConge();
            d.setIdEmploye(emp.idEmploye);
            d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
            d.setDateDebut(Date.valueOf(ven));
            d.setDateFin(Date.valueOf(dim));
            d.setCommentaire("Week-end prolongé");
            congeService.creerDemande(d, 1, c);
            idDemande = d.getIdDemandeConge();
            congeService.validerDemande(idDemande, 1, "Validation", c);

            // Grand-livre : 3 jours débités
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ?")) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(-3.0, rs.getDouble("nb_jours"), 0.01);
                }
            }

            // Valorisation Paie : NBCONGE = 3.0 jours calendaires
            LocalDate pDeb = LocalDate.of(2026, 10, 1);
            LocalDate pFin = LocalDate.of(2026, 10, 31);
            BigDecimal nbConge = congeService.calculerNbJoursCongeValorises(emp.idEmploye, pDeb, pFin, c);
            assertEquals(0, nbConge.compareTo(new BigDecimal("3.0")));
        }
    }

    @Test
    @DisplayName("RG-CONGE-05 : Demi-journée validée -> PRISE -0.5j -> NBCONGE 0.5j")
    public void testDemiJournee_Valorisation() throws Exception {
        employeFixtures.crediterSoldeConge(emp.idEmploye, 10.0);

        LocalDate jour = LocalDate.of(2026, 10, 15);
        int idDemande;
        try (Connection c = dataSource.getConnection()) {
            mg.galaxam.sirh.conge.persistence.DemandeConge d = new mg.galaxam.sirh.conge.persistence.DemandeConge();
            d.setIdEmploye(emp.idEmploye);
            d.setIdTypeConge(resolveTypeCongeId("ANNUEL"));
            d.setDateDebut(Date.valueOf(jour));
            d.setDateFin(Date.valueOf(jour));
            d.setEstDemiJournee(true);
            d.setMomentDemiJournee("MATIN");
            d.setCommentaire("Demi-journée matin");
            congeService.creerDemande(d, 1, c);
            idDemande = d.getIdDemandeConge();
            congeService.validerDemande(idDemande, 1, "Validation", c);

            // Débit de -0.5
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_demande_conge = ?")) {
                ps.setInt(1, idDemande);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(-0.5, rs.getDouble("nb_jours"), 0.01);
                }
            }

            // Valorisation : NBCONGE = 0.5
            BigDecimal nbConge = congeService.calculerNbJoursCongeValorises(
                    emp.idEmploye, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), c);
            assertEquals(0, nbConge.compareTo(new BigDecimal("0.5")));
        }
    }

    @Test
    @DisplayName("TCONGE : Non-régression, fallback Base/30 et absence stricte de future leakage")
    public void testTcongeNonRegressionEtResolutionHistorique() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal base = new BigDecimal("600000.00");
            LocalDate dateRef = LocalDate.of(2026, 10, 1);

            // 1. Sans historique : Fallback Base / 30.0 = 20 000.00 Ar
            BigDecimal tauxFallback = StcService.getTauxJournalierApplicable(emp.idEmploye, dateRef, base, c);
            assertEquals(0, tauxFallback.compareTo(new BigDecimal("20000.00")));

            // 2. Insertion historique conge_journal_droit pour Septembre 2026 (mois 9) -> Taux = 25 000.00 Ar
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO conge_journal_droit (id_employe, annee, mois, salaire_base, jours_travailles_formule, " +
                    "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, cumul_valorisation, taux_journalier_conge) " +
                    "VALUES (?, 2026, 9, 600000.00, 30.0, 2.5, 600000.00, 50000.00, 2.5, 62500.00, 25000.00)")) {
                ps.setInt(1, emp.idEmploye);
                ps.executeUpdate();
            }

            // Résolution au 01/10/2026 -> Doit retourner 25 000.00 Ar (dernier taux historique avant le 01/10)
            BigDecimal tauxHist = StcService.getTauxJournalierApplicable(emp.idEmploye, dateRef, base, c);
            assertEquals(0, tauxHist.compareTo(new BigDecimal("25000.00")));

            // 3. Insertion future en Novembre 2026 (mois 11) -> Taux = 35 000.00 Ar
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO conge_journal_droit (id_employe, annee, mois, salaire_base, jours_travailles_formule, " +
                    "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, cumul_valorisation, taux_journalier_conge) " +
                    "VALUES (?, 2026, 11, 700000.00, 30.0, 2.5, 700000.00, 58333.33, 5.0, 175000.00, 35000.00)")) {
                ps.setInt(1, emp.idEmploye);
                ps.executeUpdate();
            }

            // Résolution au 01/10/2026 -> Strictement aucune fuite future, reste 25 000.00 Ar
            BigDecimal tauxSansFuite = StcService.getTauxJournalierApplicable(emp.idEmploye, dateRef, base, c);
            assertEquals(0, tauxSansFuite.compareTo(new BigDecimal("25000.00")));

            // Résolution au 01/12/2026 -> Doit maintenant voir Novembre (35 000.00 Ar)
            BigDecimal tauxFutur = StcService.getTauxJournalierApplicable(emp.idEmploye, LocalDate.of(2026, 12, 1), base, c);
            assertEquals(0, tauxFutur.compareTo(new BigDecimal("35000.00")));
        }
    }
}
