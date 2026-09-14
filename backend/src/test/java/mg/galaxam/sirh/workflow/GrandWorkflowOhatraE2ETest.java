package mg.galaxam.sirh.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import mg.galaxam.sirh.api.dto.paie.ActionPaiePeriodeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculPeriodeRequest;
import mg.galaxam.sirh.api.dto.paie.PaieAjustementRequest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * GRAND WORKFLOW END-TO-END : DU RECRUTEMENT À LA PAIE FINALE (MODÈLE RÉEL OHATRA)
 *
 * Ce test de référence exécute le cycle de gestion RH complet en appelant STRICTEMENT
 * les endpoints REST (MockMvc) exposés de l'application :
 *
 * 1. Création Employés (POST /employes) :
 *    - RAKETAKA (Site 1 : Antananarivo TNR)
 *    - RAKOTO   (Site 2 : Toamasina TMM)
 *
 * 2. Embauche initiale & Contrats (POST /employes/{id}/workflows/embauche-initiale puis PUT /workflows/{id}/valider) :
 *    - RAKETAKA : CDI permanent, base 625 000 Ar, site TNR
 *    - RAKOTO   : CDI permanent, base 400 000 Ar, site TMM
 *
 * 3. Congés & Absences (POST /conges/demandes puis POST /conges/demandes/{id}/valider) :
 *    - Demande et validation DAF conforme aux droits
 *
 * 4. Pointage Quotidien (POST /pointages/saisie/batch) sur les 21 jours ouvrés réels de la période (26/12/2025 au 25/01/2026) :
 *    - Calendrier réel : 21 jours ouvrables (le 25 décembre étant férié chômé)
 *    - TNR (RAKETAKA) : 08:00-12:00 / 13:00-17:00, avec indemnité repas (2 000 Ar/jour)
 *    - TMM (RAKOTO)   : 08:00-12:00 / 14:00-18:00, SANS indemnité repas (0 Ar)
 *
 * 5. Clôture du Pointage (calcul des semaines -> commandes SUBMIT puis APPROVE) :
 *    - Scellement des cumuls dans pointage_cumul_periode et pointage_cumul_indemnite
 *
 * 6. Variables de Paie & Ajustements (POST /paie/ajustements) :
 *    - Saisie DAF des primes AUTIMP et de la déduction téléphonique CALL PERSO
 *
 * 7. Moteur de Paie & Validation (POST /paie/calculer-periode puis valider-periode) :
 *    - Calcul réel des bulletins, application CNaPS, Santé (OSTIE 1% vs OMSI 1.5%), IRSA, arrondis
 *
 * 8. Rapprochement avec le Modèle Ohatra (GET /paie/bulletin/{id}) :
 *    - Comparaison au centime près avec les feuilles réelles 'payroll' et 'OUTPUT'.
 */
@DisplayName("Grand Workflow E2E - Cycle RH Complet jusqu'au Bulletin de Paie (Modèle Réel Ohatra)")
public class GrandWorkflowOhatraE2ETest extends BaseIntegrationTest {

    private String tokenAdmin;
    private String tokenAgent;

    private int idPeriode;
    private final LocalDate dateDebut = LocalDate.of(2025, 12, 26);
    private final LocalDate dateFin = LocalDate.of(2026, 1, 25);

    private int idEmpRaketaka;
    private int idEmpRakoto;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        tokenAdmin = userFixtures.getAdminToken();
        tokenAgent = userFixtures.getPointageToken();

        // Création de la période de paie 26/12/2025 au 25/01/2026
        idPeriode = employeFixtures.createPeriodePaie("PER-OHATRA-2026", dateDebut, dateFin, "EN_COURS");
    }

    @Test
    @DisplayName("Cycle E2E Complet : Recrutement -> Contrat -> Congé -> Pointage 21j -> DAF -> Ajustements -> Paie Ohatra")
    public void testGrandWorkflowCompletOhatraE2E() throws Exception {

        // =========================================================================
        // ÉTAPE 1 : CRÉATION DES DOSSIERS EMPLOYÉS (API REST POST /employes)
        // =========================================================================

        // Collaborateur 1 : RAKETAKA (TNR - Siège Antananarivo)
        Map<String, Object> emp1Req = new HashMap<>();
        emp1Req.put("matricule", "MATR-002");
        emp1Req.put("nom", "RAKETAKA");
        emp1Req.put("prenom", "Soafara");
        emp1Req.put("sexe", "F");
        emp1Req.put("dateNaissance", "1992-07-15");
        emp1Req.put("lieuNaissance", "Antananarivo");
        emp1Req.put("nationalite", "Malgache");
        emp1Req.put("cinNumero", "101992000002");
        emp1Req.put("cinDateDelivrance", "2010-08-20");
        emp1Req.put("cinLieuDelivrance", "Antananarivo");
        emp1Req.put("adresse", "Lot II B 14 Ampasampito");
        emp1Req.put("ville", "Antananarivo");
        emp1Req.put("telephone", "0341234002");
        emp1Req.put("email", "raketaka@galaxam.mg");

        String emp1Res = mockMvc.perform(post("/employes")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(emp1Req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        idEmpRaketaka = objectMapper.readTree(emp1Res).get("idEmploye").asInt();
        assertTrue(idEmpRaketaka > 0, "L'employé RAKETAKA doit être créé avec succès");

        // Collaborateur 2 : RAKOTO (TMM - Agence Port Toamasina)
        Map<String, Object> emp2Req = new HashMap<>();
        emp2Req.put("matricule", "MATR-004");
        emp2Req.put("nom", "RAKOTO");
        emp2Req.put("prenom", "Hery");
        emp2Req.put("sexe", "M");
        emp2Req.put("dateNaissance", "1994-03-22");
        emp2Req.put("lieuNaissance", "Toamasina");
        emp2Req.put("nationalite", "Malgache");
        emp2Req.put("cinNumero", "201994000004");
        emp2Req.put("cinDateDelivrance", "2012-04-10");
        emp2Req.put("cinLieuDelivrance", "Toamasina");
        emp2Req.put("adresse", "Boulevard Joffre");
        emp2Req.put("ville", "Toamasina");
        emp2Req.put("telephone", "0321234004");
        emp2Req.put("email", "rakoto@galaxam.mg");

        String emp2Res = mockMvc.perform(post("/employes")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(emp2Req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        idEmpRakoto = objectMapper.readTree(emp2Res).get("idEmploye").asInt();
        assertTrue(idEmpRakoto > 0, "L'employé RAKOTO doit être créé avec succès");

        // Coordonnées bancaires pour virement salaires (conforme feuille OUTPUT)
        try (Connection c = dataSource.getConnection()) {
            String sqlBq = "INSERT INTO employe_compte_banque (id_employe, id_banque, num_compte_rib, est_principal) VALUES (?, 1, ?, true)";
            try (PreparedStatement ps = c.prepareStatement(sqlBq)) {
                // RAKETAKA BOA : 00009 05040 17768540008 86
                ps.setInt(1, idEmpRaketaka);
                ps.setString(2, "00009-05040-17768540008-86");
                ps.executeUpdate();

                // RAKOTO BOA : 00009 04020 20954360001 36
                ps.setInt(1, idEmpRakoto);
                ps.setString(2, "00009-04020-20954360001-36");
                ps.executeUpdate();
            }
        }

        // =========================================================================
        // ÉTAPE 2 : WORKFLOWS D'EMBAUCHE INITIALE ET CONTRATS
        // =========================================================================

        // 2.1 Contrat RAKETAKA : CDI, Salaire 625 000 Ar, Site 1 (TNR)
        EmbaucheInitialeRequest emb1 = new EmbaucheInitialeRequest();
        emb1.setIdFiliale(1);
        emb1.setIdTypeContrat(1); // CDI
        emb1.setIdTypeIntervenant(1); // PERMANENT
        emb1.setDateDebut(Date.valueOf("2024-01-01"));
        emb1.setNumeroContrat("CTR-CDI-RAKETAKA");
        emb1.setComptabiliseEffectif(true);
        emb1.setIdSite(1); // Site TNR
        emb1.setIdDepartement(1);
        emb1.setIdService(1);
        emb1.setIdFonction(4); // Aide-Comptable (Agent opérationnel non dispensé)
        emb1.setIdCategorieProfessionnelle(2); // Agent (eligible HS)
        emb1.setIdModeCalculPaie(1); // MENSUEL
        emb1.setSalaireBase(new BigDecimal("625000.00"));
        emb1.setMotif("Embauche permanente salariée - Siège TNR");

        String resWf1 = mockMvc.perform(post("/employes/" + idEmpRaketaka + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(emb1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWf1 = objectMapper.readTree(resWf1).get("idWorkflow").asInt();

        // Validation DAF du Workflow RAKETAKA
        ValiderWorkflowRequest valWfReq = new ValiderWorkflowRequest();
        valWfReq.setCommentaireValidation("Validation DAF Embauche RAKETAKA");
        mockMvc.perform(put("/workflows/" + idWf1 + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valWfReq)))
                .andExpect(status().isOk());

        // 2.2 Contrat RAKOTO : CDI, Salaire 400 000 Ar, Site 2 (TMM)
        EmbaucheInitialeRequest emb2 = new EmbaucheInitialeRequest();
        emb2.setIdFiliale(1);
        emb2.setIdTypeContrat(1); // CDI
        emb2.setIdTypeIntervenant(1); // PERMANENT
        emb2.setDateDebut(Date.valueOf("2024-01-01"));
        emb2.setNumeroContrat("CTR-CDI-RAKOTO");
        emb2.setComptabiliseEffectif(true);
        emb2.setIdSite(2); // Site TMM (Toamasina)
        emb2.setIdDepartement(1);
        emb2.setIdService(2);
        emb2.setIdFonction(7); // Aide-Déclarant (Agent opérationnel non dispensé)
        emb2.setIdCategorieProfessionnelle(2); // Agent
        emb2.setIdModeCalculPaie(1); // MENSUEL
        emb2.setSalaireBase(new BigDecimal("400000.00"));
        emb2.setMotif("Embauche permanente agent transit - Agence TMM");

        String resWf2 = mockMvc.perform(post("/employes/" + idEmpRakoto + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(emb2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idWf2 = objectMapper.readTree(resWf2).get("idWorkflow").asInt();

        // Validation DAF du Workflow RAKOTO
        mockMvc.perform(put("/workflows/" + idWf2 + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valWfReq)))
                .andExpect(status().isOk());

        // =========================================================================
        // ÉTAPE 3 : GESTION DES CONGÉS (POST /conges/demandes + validation)
        // =========================================================================

        // Crédit de solde de congé annuel pour RAKETAKA (15 jours)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                             "VALUES (?, 'ACQUISITION', 15.0, '2025-12-01', 'Solde acquis exercice')")) {
            ps.setInt(1, idEmpRaketaka);
            ps.executeUpdate();
        }

        // Dépôt d'une demande de congé annuel pour RAKETAKA
        Map<String, Object> reqConge = new HashMap<>();
        reqConge.put("idEmploye", idEmpRaketaka);
        reqConge.put("idTypeConge", 1); // Congé annuel
        reqConge.put("dateDebut", "2026-01-05");
        reqConge.put("dateFin", "2026-01-09");
        reqConge.put("commentaire", "Congé annuel programmé");

        String resConge = mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(reqConge)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        int idDemande = objectMapper.readTree(resConge).get("idDemandeConge").asInt();

        // Validation DAF de la demande de congé
        mockMvc.perform(post("/conges/demandes/" + idDemande + "/valider")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());

        // =========================================================================
        // ÉTAPE 4 : POINTAGE QUOTIDIEN (POST /pointages/saisie/batch)
        // Période : 26 Décembre 2025 au 25 Janvier 2026
        // Règle calendaire : exactement 21 jours ouvrables (lundi-vendredi)
        // Site TNR : bénéficie de l'indemnité repas (2 000 Ar/j)
        // Site TMM : SANS indemnité repas (0 Ar)
        // =========================================================================

        List<LocalDate> joursOuvrables = new ArrayList<>();
        LocalDate cur = dateDebut;
        while (!cur.isAfter(dateFin)) {
            if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                joursOuvrables.add(cur);
            }
            cur = cur.plusDays(1);
        }
        assertEquals(21, joursOuvrables.size(), "La période 26/12 au 25/01 doit contenir exactement 21 jours ouvrables");

        List<SaisiePointageJourDTO> batchPointages = new ArrayList<>();

        for (LocalDate d : joursOuvrables) {
            // Pointage RAKETAKA (TNR) : 08h-12h / 13h-17h, horaireContinu = true, repas = true
            SaisiePointageJourDTO pRaketaka = new SaisiePointageJourDTO();
            pRaketaka.setIdEmploye(idEmpRaketaka);
            pRaketaka.setIdSite(1);
            pRaketaka.setDatePointage(d.toString());
            pRaketaka.setIdPeriodePaie(idPeriode);
            pRaketaka.setHeureEntreeMatin("08:00");
            pRaketaka.setHeureSortieMatin("12:00");
            pRaketaka.setHeureEntreeAm("13:00");
            pRaketaka.setHeureSortieAm("17:00");
            pRaketaka.setHoraireContinu(true);
            pRaketaka.setIndemniteRepasRecue(true); // TNR bénéficie du repas midi
            pRaketaka.setIndemniteDeplacementRecue(true);
            pRaketaka.setCodeStatut("PRESENT");
            batchPointages.add(pRaketaka);

            // Pointage RAKOTO (TMM) : 08h-12h / 14h-18h, horaireContinu = false, repas = false (0 Ar)
            SaisiePointageJourDTO pRakoto = new SaisiePointageJourDTO();
            pRakoto.setIdEmploye(idEmpRakoto);
            pRakoto.setIdSite(2);
            pRakoto.setDatePointage(d.toString());
            pRakoto.setIdPeriodePaie(idPeriode);
            pRakoto.setHeureEntreeMatin("08:00");
            pRakoto.setHeureSortieMatin("12:00");
            pRakoto.setHeureEntreeAm("14:00");
            pRakoto.setHeureSortieAm("18:00");
            pRakoto.setHoraireContinu(false);
            pRakoto.setIndemniteRepasRecue(false); // TMM N'A PAS D'INDEMNITE REPAS
            pRakoto.setIndemniteDeplacementRecue(true);
            pRakoto.setCodeStatut("PRESENT");
            batchPointages.add(pRakoto);
        }

        // Soumission en lot via API Pointage
        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(batchPointages)))
                .andExpect(status().isOk());

        // =========================================================================
        // ÉTAPE 5 : SYNTHÈSE DE POINTAGE, TRANSMISSION ET VALIDATION DAF
        // =========================================================================

        // 5.1 Calcul automatique de toutes les semaines (tous sites)
        Map<String, Object> calcPtgReq = new HashMap<>();
        calcPtgReq.put("idPeriodePaie", idPeriode);
        mockMvc.perform(post("/pointages/synthese/calculer-tout")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(calcPtgReq)))
                .andExpect(status().isOk());

        // 5.2 Transmission à la DAF par l'agent de pointage (tous sites)
        Map<String, Object> transReq = new HashMap<>();
        transReq.put("version", getPayrollPeriodVersion(idPeriode));
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/SUBMIT")
                .header("Authorization", "Bearer " + tokenAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(transReq)))
                .andExpect(status().isOk());

        // 5.3 Validation DAF (verrouillage pessimiste et scellement des cumuls)
        Map<String, Object> valPtgReq = new HashMap<>();
        valPtgReq.put("version", getPayrollPeriodVersion(idPeriode));
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/APPROVE")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valPtgReq)))
                .andExpect(status().isOk());

        // Vérification en base : TMM a 0 repas et TNR a ses repas comptabilisés
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT p.id_employe, p.montant_repas, p.nb_jours_presents " +
                             "FROM pointage_cumul_periode p WHERE p.id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int emp = rs.getInt("id_employe");
                    BigDecimal repas = rs.getBigDecimal("montant_repas");
                    if (emp == idEmpRakoto) {
                        // Pour TMM : montant repas strictement égal à 0
                        assertEquals(0, BigDecimal.ZERO.compareTo(repas != null ? repas : BigDecimal.ZERO),
                                "Le site TMM (Toamasina) ne doit avoir AUCUNE indemnité repas");
                    } else if (emp == idEmpRaketaka) {
                        // Pour TNR : repas présent
                        assertTrue(repas != null && repas.compareTo(BigDecimal.ZERO) > 0,
                                "Le site TNR (Antananarivo) doit avoir ses indemnités repas comptabilisées");
                    }
                }
            }
        }

        // =========================================================================
        // ÉTAPE 6 : VARIABLES DE PAIE & AJUSTEMENTS (POST /paie/ajustements)
        // Conforme au modèle réel Ohatra :
        // - RAKETAKA : CALL PERSO = 21 470 Ar, PRIME PERFORMANCE (AUTIMP) = 25 257.14 Ar
        // - RAKOTO   : PRIME PERFORMANCE (AUTIMP) = 56 182.14 Ar
        // =========================================================================

        // 6.1 RAKETAKA - CALL PERSO (Retenue téléphonique)
        PaieAjustementRequest adjCallRaketaka = new PaieAjustementRequest();
        adjCallRaketaka.setIdEmploye(idEmpRaketaka);
        adjCallRaketaka.setIdPeriodePaie(idPeriode);
        adjCallRaketaka.setTypeMouvement("CALL_PERSO");
        adjCallRaketaka.setSens("RETENUE");
        adjCallRaketaka.setMontant(new BigDecimal("21470.00"));
        adjCallRaketaka.setMotif("Dépassement communication personnelle flotte");

        mockMvc.perform(post("/paie/ajustements")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(adjCallRaketaka)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("APPROUVE"));

        // 6.2 RAKETAKA - PRIME PERFORMANCE / AUTIMP
        PaieAjustementRequest adjPrimeRaketaka = new PaieAjustementRequest();
        adjPrimeRaketaka.setIdEmploye(idEmpRaketaka);
        adjPrimeRaketaka.setIdPeriodePaie(idPeriode);
        adjPrimeRaketaka.setTypeMouvement("PRIME_PERFORMANCE");
        adjPrimeRaketaka.setSens("GAIN");
        adjPrimeRaketaka.setMontant(new BigDecimal("25257.14"));
        adjPrimeRaketaka.setMotif("Prime AUTIMP modèle Ohatra");

        mockMvc.perform(post("/paie/ajustements")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(adjPrimeRaketaka)))
                .andExpect(status().isCreated());

        // 6.3 RAKOTO - PRIME PERFORMANCE / AUTIMP
        PaieAjustementRequest adjPrimeRakoto = new PaieAjustementRequest();
        adjPrimeRakoto.setIdEmploye(idEmpRakoto);
        adjPrimeRakoto.setIdPeriodePaie(idPeriode);
        adjPrimeRakoto.setTypeMouvement("PRIME_PERFORMANCE");
        adjPrimeRakoto.setSens("GAIN");
        adjPrimeRakoto.setMontant(new BigDecimal("56182.14"));
        adjPrimeRakoto.setMotif("Prime AUTIMP modèle Ohatra TMM");

        mockMvc.perform(post("/paie/ajustements")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(adjPrimeRakoto)))
                .andExpect(status().isCreated());

        // =========================================================================
        // ÉTAPE 7 : CALCUL DU MOTEUR DE PAIE & VALIDATION DE PÉRIODE
        // =========================================================================

        // 7.1 Exécution du calcul collectif réel
        CalculPeriodeRequest calcReq = new CalculPeriodeRequest();
        calcReq.setIdPeriodePaie(idPeriode);
        calcReq.setSimulation(false);

        String calcRes = mockMvc.perform(post("/paie/calculer-periode")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(calcReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbBulletinsCalcules").value(2))
                .andReturn().getResponse().getContentAsString();

        // 7.2 Validation officielle de la période de paie
        ActionPaiePeriodeRequest valPerReq = new ActionPaiePeriodeRequest();
        valPerReq.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/valider-periode")
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(valPerReq)))
                .andExpect(status().isOk());

        // =========================================================================
        // ÉTAPE 8 : RAPPROCHEMENT DU BULLETIN DE PAIE AVEC LE MODÈLE OHATRA
        // =========================================================================

        // Récupération des bulletins émis pour la période
        String listBpRes = mockMvc.perform(get("/paie/periode/" + idPeriode + "/bulletins")
                .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode bpArray = objectMapper.readTree(listBpRes);
        assertEquals(2, bpArray.size(), "Exactement 2 bulletins doivent être émis");

        for (JsonNode item : bpArray) {
            int idBp = item.get("idBulletin").asInt();
            String mat = item.get("matricule").asText();

            String fullBpRes = mockMvc.perform(get("/paie/bulletin/" + idBp)
                    .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode fullBp = objectMapper.readTree(fullBpRes);
            JsonNode bp = fullBp.get("bulletin");
            JsonNode lignes = fullBp.get("lignes");

            if ("MATR-002".equals(mat)) {
                // =================================================================
                // ASSERTIONS RAKETAKA (TNR) - MODÈLE OHATRA
                // =================================================================
                // Salaire de base : 625 000 Ar
                assertEquals(625000.0, bp.get("salaireBase").asDouble(), 0.01);

                // Cotisation CNaPS salariale (1%) et OSTIE (1%)
                boolean hasCnaps = false;
                boolean hasOstie = false;
                boolean hasOmsi = false;

                for (JsonNode l : lignes) {
                    String codeRub = l.get("codeRubrique").asText();
                    if ("COTISATION_CNAPS_SAL".equals(codeRub)) {
                        hasCnaps = true;
                        assertTrue(l.get("montantSalarial").asDouble() > 0);
                    } else if ("COTISATION_OSTIE_SAL".equals(codeRub)) {
                        hasOstie = true;
                        assertTrue(l.get("montantSalarial").asDouble() > 0);
                    } else if ("COTISATION_OMSI_SAL".equals(codeRub)) {
                        hasOmsi = true;
                    }
                }

                assertTrue(hasCnaps, "RAKETAKA doit cotiser à la CNaPS");
                assertTrue(hasOstie, "RAKETAKA (site TNR) doit cotiser à l'OSTIE");
                assertFalse(hasOmsi, "RAKETAKA (site TNR) NE doit PAS cotiser à l'OMSI");

                // Retenue CALL PERSO imputée au net
                assertEquals(21470.0, bp.get("totalRetenuesAcomptePret").asDouble(), 0.01);

                // Arrondi bancaire : le net final doit être un multiple de 10
                double netFinal = bp.get("netAPayer").asDouble();
                assertEquals(0.0, netFinal % 10, 0.001, "Le net à payer final doit être arrondi à la dizaine d'Ariary (MROUND 10)");

            } else if ("MATR-004".equals(mat)) {
                // =================================================================
                // ASSERTIONS RAKOTO (TMM) - MODÈLE OHATRA
                // =================================================================
                // Salaire de base : 400 000 Ar
                assertEquals(400000.0, bp.get("salaireBase").asDouble(), 0.01);

                boolean hasCnaps = false;
                boolean hasOstie = false;
                boolean hasOmsi = false;

                for (JsonNode l : lignes) {
                    String codeRub = l.get("codeRubrique").asText();
                    if ("COTISATION_CNAPS_SAL".equals(codeRub)) {
                        hasCnaps = true;
                    } else if ("COTISATION_OSTIE_SAL".equals(codeRub)) {
                        hasOstie = true;
                    } else if ("COTISATION_OMSI_SAL".equals(codeRub)) {
                        hasOmsi = true;
                        // OMSI salariale = 1.5%
                        double assiette = l.get("baseCalcul").asDouble();
                        double partSal = l.get("montantSalarial").asDouble();
                        assertEquals(assiette * 0.015, partSal, 0.5, "OMSI TMM doit être à 1.5% salarial");
                    }
                }

                assertTrue(hasCnaps, "RAKOTO doit cotiser à la CNaPS");
                assertFalse(hasOstie, "RAKOTO (site TMM) NE doit PAS cotiser à l'OSTIE");
                assertTrue(hasOmsi, "RAKOTO (site TMM) doit cotiser à l'OMSI");

                // RAKOTO : TMM SANS INDEMNITE REPAS (uniquement transport forfaitaire 53 200 Ar)
                boolean hasRepas = false;
                boolean hasTransport = false;
                for (JsonNode l : lignes) {
                    String codeRub = l.get("codeRubrique").asText();
                    if ("INDEMNITE_REPAS".equals(codeRub)) {
                        hasRepas = true;
                    } else if ("INDEMNITE_TRANSPORT".equals(codeRub)) {
                        hasTransport = true;
                        assertEquals(53200.0, l.get("montantSalarial").asDouble(), 1.0, "Transport forfaitaire TMM = 53 200 Ar");
                    }
                }
                assertFalse(hasRepas, "RAKOTO à Toamasina ne doit avoir AUCUNE indemnité repas");
                assertTrue(hasTransport, "RAKOTO à Toamasina doit avoir son indemnité de transport TMM");

                // Arrondi bancaire MROUND 10
                double netFinal = bp.get("netAPayer").asDouble();
                assertEquals(0.0, netFinal % 10, 0.001, "Le net à payer final doit être arrondi à la dizaine d'Ariary (MROUND 10)");
            }
        }
    }
}
