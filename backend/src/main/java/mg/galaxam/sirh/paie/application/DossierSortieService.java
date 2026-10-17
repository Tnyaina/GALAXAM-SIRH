package mg.galaxam.sirh.paie.application;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.contrat.domain.AncienneteEngine;
import mg.galaxam.sirh.paie.api.dto.stc.ChecklistItemDTO;
import mg.galaxam.sirh.paie.api.dto.stc.ChecklistItemDTO.Niveau;
import mg.galaxam.sirh.paie.api.dto.stc.ChecklistItemDTO.SourceNorme;
import mg.galaxam.sirh.paie.api.dto.stc.ChecklistItemDTO.StatutCheck;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieDTO;
import mg.galaxam.sirh.paie.api.dto.stc.DossierSortieSaveRequest;
import mg.galaxam.sirh.paie.domain.PayrollBusinessTime;
import mg.galaxam.sirh.paie.persistence.PaieDossierSortie;
import mg.galaxam.sirh.paie.persistence.PaiePret;
import mg.galaxam.sirh.paie.persistence.PaieStc;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import mg.galaxam.sirh.paie.api.dto.stc.ScellementStcRequest;
import mg.galaxam.sirh.paie.api.dto.stc.SimulationStcResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcReglementRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcSituationReglementDTO;
import mg.galaxam.sirh.paie.api.dto.stc.AnnulationReglementRequest;
import java.sql.Timestamp;
import mg.galaxam.sirh.contrat.persistence.Employe;
import mg.galaxam.sirh.paie.api.dto.stc.ClotureDossierRequest;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.securite.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service d'orchestration pour le dossier de sortie du salarié.
 * Gère la persistance préparatoire, la synchronisation en lecture seule,
 * la checklist dynamique (Loi Malgache 2024 vs Règles GALAXAM vs Technique SI),
 * la simulation déterministe et le scellement transactionnel du STC.
 */
@Service
public class DossierSortieService {

    private static final Logger log = LoggerFactory.getLogger(DossierSortieService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final StcService stcService;
    private final AncienneteEngine ancienneteEngine = new AncienneteEngine();
    private final mg.galaxam.sirh.conge.application.CongeLotValorisationService lotsConges =
            new mg.galaxam.sirh.conge.application.CongeLotValorisationService();

    public DossierSortieService(StcService stcService) {
        this.stcService = stcService;
    }

    /**
     * Crée ou met à jour un dossier préparatoire de sortie.
     * Ne modifie aucun pointage, aucun solde de congés et n'émet aucun bulletin.
     */
    public DossierSortieDTO sauvegarderDossier(DossierSortieSaveRequest req, Integer userId, Connection c) throws Exception {
        if (req.getIdEmploye() == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "L'identifiant de l'employé est obligatoire.");
        }
        if (req.getDateDepart() == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La date de départ effectif est obligatoire.");
        }

        // Vérification de l'existence de l'employé
        String sqlEmp = "SELECT 1 FROM employe WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
            ps.setInt(1, req.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND,
                            "Employé introuvable pour l'identifiant " + req.getIdEmploye());
                }
            }
        }

        // Protection des états : interdiction formelle de forcer les statuts scellés/clôturés par API
        if (req.getStatutDossier() != null && List.of("VALIDE", "REGLE", "CLOTURE").contains(req.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Le statut " + req.getStatutDossier() + " ne peut pas être forcé via l'API préparatoire. Les statuts VALIDE, REGLE et CLOTURE sont réservés au calcul financier scellé et aux règlements constatés.");
        }

        PaieDossierSortie entity = null;
        if (req.getIdDossierSortie() != null) {
            entity = PaieDossierSortie.trouverParId(req.getIdDossierSortie(), c);
        }
        if (entity == null) {
            entity = PaieDossierSortie.trouverParEmployeEtDate(req.getIdEmploye(), req.getDateDepart(), c);
        }
        if (entity == null) {
            entity = new PaieDossierSortie();
            entity.setIdEmploye(req.getIdEmploye());
            entity.setCreePar(userId);
        }

        // On ne permet la modification que si le dossier n'est pas encore scellé, clôturé ou annulé
        if (entity.getStatutDossier() != null && List.of("CLOTURE", "ANNULE", "VALIDE", "REGLE").contains(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Ce dossier de sortie est " + entity.getStatutDossier() + " et ne peut plus être modifié.");
        }

        // Verrouillage optimiste applicatif si la version est spécifiée
        if (entity.getIdDossierSortie() != null && req.getVersion() != null
                && entity.getVersion() != null && !entity.getVersion().equals(req.getVersion())) {
            throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                    "Conflit de concurrence : le dossier a été modifié par un autre utilisateur (version attendue : "
                    + req.getVersion() + ", version courante : " + entity.getVersion() + "). Veuillez recharger le dossier.");
        }

        entity.setIdContrat(req.getIdContrat());
        entity.setDateDepart(req.getDateDepart());
        entity.setMotifRupture(req.getMotifRupture() != null ? req.getMotifRupture() : "DEMISSION");

        // Statut préparatoire autorisé (EN_PREPARATION, PRET_A_VALIDER, ANNULE)
        if (req.getStatutDossier() != null) {
            entity.setStatutDossier(req.getStatutDossier());
        } else if (entity.getStatutDossier() == null) {
            entity.setStatutDossier("EN_PREPARATION");
        }

        if (req.getDonneesPreparation() != null) {
            entity.setDonneesPreparation(objectMapper.writeValueAsString(req.getDonneesPreparation()));
        }
        entity.setDecisionPret(req.getDecisionPret());
        entity.setJustificationDecisionPret(req.getJustificationDecisionPret());
        entity.setMontantPretRetenu(req.getMontantPretRetenu() != null ? req.getMontantPretRetenu() : BigDecimal.ZERO);

        if (req.getCertificatDelivre() != null) {
            entity.setCertificatDelivre(req.getCertificatDelivre());
            entity.setDateDelivranceCertificat(req.getDateDelivranceCertificat());
            entity.setModeRemiseCertificat(req.getModeRemiseCertificat());
        }
        if (req.getRecuStcRemis() != null) {
            entity.setRecuStcRemis(req.getRecuStcRemis());
            entity.setDateRemiseRecu(req.getDateRemiseRecu());
        }

        entity.enregistrer(c);
        return assemblerDossierDTO(entity, c);
    }

    /**
     * Récupère un dossier de sortie complet avec sa checklist dynamique et ses données sources en lecture.
     */
    public DossierSortieDTO obtenirDossier(Integer idDossier, Connection c) throws Exception {
        PaieDossierSortie entity = PaieDossierSortie.trouverParId(idDossier, c);
        if (entity == null) return null;
        return assemblerDossierDTO(entity, c);
    }

    /**
     * Récupère le dernier dossier de sortie d'un salarié ou prépare une vue pour un nouveau départ.
     */
    public DossierSortieDTO obtenirDossierParEmploye(Integer idEmploye, Date dateDepart, String motif, Connection c) throws Exception {
        if (idEmploye == null) throw new IllegalArgumentException("idEmploye obligatoire.");
        Date dateRef = dateDepart != null ? dateDepart : Date.valueOf(PayrollBusinessTime.today());
        PaieDossierSortie entity = PaieDossierSortie.trouverParEmployeEtDate(idEmploye, dateRef, c);
        if (entity == null) {
            // Vue préparatoire virtuelle (non persistée tant que la DAF n'a pas cliqué sur Sauvegarder)
            entity = new PaieDossierSortie();
            entity.setIdEmploye(idEmploye);
            entity.setDateDepart(dateRef);
            entity.setMotifRupture(motif != null ? motif : "DEMISSION");
            entity.setStatutDossier("EN_PREPARATION");
        }
        return assemblerDossierDTO(entity, c);
    }

    /**
     * Liste tous les dossiers de sortie enregistrés.
     */
    public List<DossierSortieDTO> listerDossiers(Connection c) throws Exception {
        List<PaieDossierSortie> entities = PaieDossierSortie.listerTous(c);
        List<DossierSortieDTO> dtos = new ArrayList<>();
        for (PaieDossierSortie e : entities) {
            dtos.add(assemblerDossierDTO(e, c));
        }
        return dtos;
    }

    /**
     * Enregistre la délivrance du certificat de travail (Art. 50 Code du travail malgache 2024).
     * Accessible dès la cessation effective, indépendamment du règlement du STC.
     */
    public void enregistrerDelivranceCertificat(Integer idDossier, Date dateDelivrance, String modeRemise, Integer userId, Connection c) throws Exception {
        PaieDossierSortie entity = PaieDossierSortie.trouverParId(idDossier, c);
        if (entity == null) throw new IllegalArgumentException("Dossier de sortie introuvable : " + idDossier);
        if ("CLOTURE".equalsIgnoreCase(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Ce dossier de sortie est clôturé et ne peut plus être modifié.");
        }
        if (entity.getIdContrat() == null) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "La cessation effective du contrat doit être enregistrée avant la remise du certificat.");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT ct.date_fin, s.code FROM contrat_travail ct " +
                "JOIN ref_statut_rh s ON s.id_statut_rh = ct.id_statut_rh WHERE ct.id_contrat = ?")) {
            ps.setInt(1, entity.getIdContrat());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getDate(1) == null
                        || rs.getDate(1).toLocalDate().isAfter(PayrollBusinessTime.today())
                        || !List.of("ROMPU", "CLOTURE", "TERMINE").contains(rs.getString(2))) {
                    throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                            "La cessation effective du contrat doit être enregistrée avant la remise du certificat.");
                }
            }
        }
        entity.setCertificatDelivre(true);
        entity.setDateDelivranceCertificat(dateDelivrance != null ? dateDelivrance : Date.valueOf(PayrollBusinessTime.today()));
        entity.setModeRemiseCertificat(modeRemise != null ? modeRemise : "MAIN_PROPRE");
        entity.enregistrer(c);
    }

    /**
     * Enregistre un versement de règlement STC directement depuis le dossier de sortie scellé.
     */
    public StcSituationReglementDTO enregistrerReglementDepuisDossier(Integer idDossier, StcReglementRequest req, Integer userId, Connection c) throws Exception {
        PaieDossierSortie dossier = PaieDossierSortie.trouverParId(idDossier, c);
        if (dossier == null) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Dossier de sortie introuvable : " + idDossier);
        }
        if (dossier.getIdStc() == null) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Aucun STC n'a encore été scellé pour ce dossier de sortie. Le scellement financier est obligatoire avant tout règlement.");
        }
        return stcService.enregistrerReglement(dossier.getIdStc(), req, userId, c);
    }

    /**
     * Récupère la situation financière détaillée des règlements pour un dossier de sortie.
     */
    public StcSituationReglementDTO obtenirSituationReglementsDepuisDossier(Integer idDossier, Connection c) throws Exception {
        PaieDossierSortie dossier = PaieDossierSortie.trouverParId(idDossier, c);
        if (dossier == null) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Dossier de sortie introuvable : " + idDossier);
        }
        if (dossier.getIdStc() == null) {
            return null;
        }
        return stcService.obtenirSituationReglements(dossier.getIdStc(), c);
    }

    /**
     * Annule une ligne de versement depuis le dossier de sortie.
     */
    public StcSituationReglementDTO annulerReglementDepuisDossier(Integer idDossier, Integer idReglement, AnnulationReglementRequest req, Integer userId, Connection c) throws Exception {
        PaieDossierSortie dossier = PaieDossierSortie.trouverParId(idDossier, c);
        if (dossier == null) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Dossier de sortie introuvable : " + idDossier);
        }
        if (dossier.getIdStc() == null) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, "Aucun STC lié à ce dossier.");
        }
        return stcService.annulerReglement(dossier.getIdStc(), idReglement, req, userId, c);
    }

    // =========================================================================
    // PHASE 3.2 : CLÔTURE ADMINISTRATIVE DU DOSSIER DE SORTIE & CYCLE DE VIE RH
    // =========================================================================

    /**
     * Clôture administrativement le dossier de sortie du salarié.
     * Opération transactionnelle et auditée respectant strictement la séparation entre :
     * 1. Cessation effective du contrat (autonome du règlement).
     * 2. Règlement financier du STC (vérifié ou dérogé).
     * 3. Clôture administrative du dossier (irréversible et protégée).
     * 4. Statut du salarié (préservation des contrats continus / futurs confirmés).
     */
    public DossierSortieDTO cloturerDossier(Integer idDossier, ClotureDossierRequest req, Integer userId, Connection c) throws Exception {
        if (idDossier == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "L'identifiant du dossier est obligatoire.");
        }

        // 1. Verrouillage transactionnel pessimiste du dossier
        PaieDossierSortie entity = PaieDossierSortie.trouverParIdPourMiseAJour(idDossier, c);
        if (entity == null) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Dossier de sortie introuvable : " + idDossier);
        }

        // 2. Contrôle de transition d'état et rejet de double clôture
        if ("CLOTURE".equalsIgnoreCase(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Ce dossier de sortie est déjà clôturé (clôture prononcée le " + entity.getDateCloture() + "). Une seconde clôture est rejetée.");
        }
        if ("ANNULE".equalsIgnoreCase(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Impossible de clôturer administrativement un dossier annulé.");
        }
        if (entity.getIdStc() == null || List.of("EN_PREPARATION", "PRET_A_VALIDER").contains(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Le STC doit être préalablement scellé et validé avant de pouvoir procéder à la clôture administrative du dossier (statut actuel : " + entity.getStatutDossier() + ").");
        }

        // 3. Vérification du règlement financier et dérogation formelle
        StcSituationReglementDTO situation = stcService.obtenirSituationReglements(entity.getIdStc(), c);
        boolean estSolde = situation != null && situation.isEstIntegralementRegle();
        boolean derogation = req != null && req.isDerogationReglementIncomplet();

        if (!estSolde && !derogation) {
            BigDecimal soldeRestant = situation != null ? situation.getResteAPayer() : BigDecimal.ZERO;
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Clôture administrative refusée : le solde de tout compte n'est pas intégralement réglé (reste à payer : "
                    + soldeRestant + " Ar). Pour clôturer avec un solde restant dû, une dérogation formelle motivée est obligatoirement requise.");
        }

        if (!estSolde && derogation) {
            if (req.getJustificationDerogation() == null || req.getJustificationDerogation().trim().isBlank()) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Clôture par dérogation rejetée : la justification formelle de la dérogation financière est obligatoire en cas de règlement incomplet.");
            }
            if (req.getAutoriteDecisionDerogation() == null || req.getAutoriteDecisionDerogation().trim().isBlank()) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Clôture par dérogation rejetée : l'autorité décisionnaire ayant approuvé la dérogation (ex: DIRECTION_GENERALE, DAF) est obligatoire.");
            }
            if (req.getReferenceDecisionDerogation() == null || req.getReferenceDecisionDerogation().trim().isBlank()) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Clôture par dérogation rejetée : la référence formelle de la décision ou du protocole de dérogation est obligatoire.");
            }
            if (req.getDateDecisionDerogation() == null) {
                throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                        "Clôture par dérogation rejetée : la date de la décision formelle de dérogation est obligatoire.");
            }
        }

        // 4. Vérification de la cessation effective préalable du contrat rattaché (Responsabilité RH canonique)
        if (entity.getIdContrat() != null) {
            String sqlCheckStatut = "SELECT ct.numero_contrat, s.code AS statut_code, ct.date_fin " +
                    "FROM contrat_travail ct " +
                    "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh " +
                    "WHERE ct.id_contrat = ?";
            try (PreparedStatement psStat = c.prepareStatement(sqlCheckStatut)) {
                psStat.setInt(1, entity.getIdContrat());
                try (ResultSet rsStat = psStat.executeQuery()) {
                    if (rsStat.next()) {
                        String numCt = rsStat.getString("numero_contrat");
                        String codeStatut = rsStat.getString("statut_code");
                        Date dateFinCt = rsStat.getDate("date_fin");

                        if ("ACTIF".equalsIgnoreCase(codeStatut)) {
                            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                    "Clôture impossible : le contrat associé (N° " + numCt + ") est toujours à l'état ACTIF. " +
                                    "La cessation effective du contrat doit préalablement être formalisée et enregistrée par le workflow RH canonique (WorkflowRhService) ou la clôture de contrat.");
                        }

                        if (!List.of("ROMPU", "CLOTURE", "TERMINE").contains(codeStatut != null ? codeStatut.toUpperCase() : "")) {
                            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                    "Clôture impossible : le statut du contrat associé (N° " + numCt + ", statut: " + codeStatut + ") " +
                                    "n'est pas conforme pour une clôture de dossier de sortie (attendu : ROMPU ou CLOTURE).");
                        }

                        if (dateFinCt == null) {
                            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                                    "Clôture impossible : le contrat associé (N° " + numCt + ") ne comporte aucune date de fin effective enregistrée. " +
                                    "Veuillez régulariser la fin de contrat dans le module Carrière.");
                        }
                    }
                }
            }
        }

        // 5. Contrôle des formalités administratives et checklist
        DossierSortieDTO currentDto = assemblerDossierDTO(entity, c);

        // Décision obligatoire sur prêt en cours
        if (currentDto.getCapitalRestantPrets() != null && currentDto.getCapitalRestantPrets().signum() > 0) {
            if (entity.getDecisionPret() == null || entity.getDecisionPret().trim().isBlank()) {
                throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                        "Clôture impossible : une créance de prêt d'entreprise subsiste (" + currentDto.getCapitalRestantPrets()
                        + " Ar) et aucune décision formelle d'arbitrage n'a été consignée.");
            }
        }

        // Contrôle des points bloquants de la checklist
        boolean aControlesBloquants = currentDto.getChecklist().stream()
                .anyMatch(it -> it.getNiveau() == Niveau.BLOQUANT && it.getStatut() == StatutCheck.NON_CONFORME);
        if (aControlesBloquants) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Clôture impossible : des formalités ou contrôles obligatoires bloquants non conformes subsistent sur ce dossier.");
        }

        // 6. Gestion du statut du salarié (préservation stricte de la continuité contractuelle et des contrats futurs confirmés)
        boolean aAutreContratActif = false;
        String sqlAutreActif = "SELECT COUNT(*) FROM contrat_travail c " +
                "JOIN ref_statut_rh s ON c.id_statut_rh = s.id_statut_rh " +
                "WHERE c.id_employe = ? AND s.code = 'ACTIF' AND c.id_contrat <> ?";
        try (PreparedStatement ps = c.prepareStatement(sqlAutreActif)) {
            ps.setInt(1, entity.getIdEmploye());
            ps.setInt(2, entity.getIdContrat() != null ? entity.getIdContrat() : 0);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) aAutreContratActif = true;
            }
        }

        // Continuité contractuelle confirmée : uniquement contrat futur au statut 'PLANIFIE'
        // (A_SIGNER et BROUILLON ne constituent PAS une continuité contractuelle confirmée)
        boolean aContratFuturConfirme = false;
        String sqlFutur = "SELECT COUNT(*) FROM contrat_travail c " +
                "JOIN ref_statut_rh s ON c.id_statut_rh = s.id_statut_rh " +
                "WHERE c.id_employe = ? AND c.id_contrat <> ? " +
                "AND s.code = 'PLANIFIE' AND c.date_debut >= ?";
        try (PreparedStatement ps = c.prepareStatement(sqlFutur)) {
            ps.setInt(1, entity.getIdEmploye());
            ps.setInt(2, entity.getIdContrat() != null ? entity.getIdContrat() : 0);
            ps.setDate(3, entity.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) aContratFuturConfirme = true;
            }
        }

        // Workflow RH confirmé : uniquement les transitions validées au statut 'PLANIFIE' à date d'effet future
        // (les brouillons et demandes en attente ne sont pas confirmés)
        boolean aWorkflowFutur = false;
        String sqlWf = "SELECT COUNT(*) FROM workflow_rh w " +
                "JOIN ref_statut_rh s ON w.id_statut_rh = s.id_statut_rh " +
                "WHERE w.id_employe = ? AND s.code = 'PLANIFIE' AND w.date_effet >= ?";
        try (PreparedStatement ps = c.prepareStatement(sqlWf)) {
            ps.setInt(1, entity.getIdEmploye());
            ps.setDate(2, entity.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) aWorkflowFutur = true;
            }
        }

        // Désactivation uniquement en l'absence totale d'autre engagement actif ou confirmé
        if (!aAutreContratActif && !aContratFuturConfirme && !aWorkflowFutur) {
            String motifDesact = determinerMotifDesactivation(entity.getMotifRupture());
            Employe emp = new Employe().rechercherParId(entity.getIdEmploye(), c);
            if (emp != null && emp.isEstActif()) {
                try {
                    emp.desactiver(motifDesact, c);
                } catch (Exception e) {
                    String sqlDesact = "UPDATE employe SET est_actif = FALSE, date_desactivation = CURRENT_TIMESTAMP, " +
                            "motif_desactivation = ?::motif_desactivation_enum, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?";
                    try (PreparedStatement ps = c.prepareStatement(sqlDesact)) {
                        ps.setString(1, motifDesact);
                        ps.setInt(2, entity.getIdEmploye());
                        ps.executeUpdate();
                    }
                }
            }
        }

        // 7. Enregistrement transactionnel de la clôture
        entity.setStatutDossier("CLOTURE");
        entity.setCloturePar(userId);
        entity.setDateCloture(new Timestamp(System.currentTimeMillis()));
        entity.setMotifCloture(req != null && req.getMotifCloture() != null && !req.getMotifCloture().isBlank()
                ? req.getMotifCloture().trim() : "Clôture administrative conforme");
        entity.setDerogationReglement(derogation);
        entity.setJustificationDerogation(derogation ? req.getJustificationDerogation().trim() : null);
        entity.setAutoriteDecisionDerogation(derogation ? req.getAutoriteDecisionDerogation().trim() : null);
        entity.setReferenceDecisionDerogation(derogation ? req.getReferenceDecisionDerogation().trim() : null);
        entity.setDateDecisionDerogation(derogation ? req.getDateDecisionDerogation() : null);
        entity.modifier(c);

        // 8. Journalisation d'audit formelle
        String actionAudit = derogation ? "CLOTURE_AVEC_DEROGATION_REGLEMENT" : "CLOTURE_ADMINISTRATIVE";
        String detailDerog = derogation
                ? "Dérogation autorisée par " + entity.getAutoriteDecisionDerogation()
                + " (Réf: " + entity.getReferenceDecisionDerogation() + ", Date: " + entity.getDateDecisionDerogation() + ")"
                : null;
        AuditLog.audit(c, actionAudit, "paie_dossier_sortie",
                String.valueOf(entity.getIdDossierSortie()), detailDerog, entity);

        return assemblerDossierDTO(entity, c);
    }

    private String determinerMotifDesactivation(String motifRupture) {
        if (motifRupture == null) return "Demission";
        String upper = motifRupture.toUpperCase();
        if (upper.contains("DEMISSION")) return "Demission";
        if (upper.contains("LICENCIEMENT")) return "Licenciement";
        if (upper.contains("FIN") || upper.contains("TERME") || upper.contains("CDD")) return "Fin_contrat";
        if (upper.contains("RETRAITE")) return "Retraite";
        if (upper.contains("DECES")) return "Deces";
        return "Autre";
    }

    private static Integer resolveStatut(String code, Connection c) throws Exception {
        String sql = "SELECT id_statut_rh FROM ref_statut_rh WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    // =========================================================================
    // PHASE 2 : SIMULATION DÉTERMINISTE & SCELLEMENT TRANSACTIONNEL DU STC
    // =========================================================================

    /**
     * Simule le calcul complet du STC en réutilisant StcService.
     * Produit un décompte financier détaillé, capture un snapshot des données métiers
     * et génère une référence de simulation vérifiable liée au résultat exact.
     */
    public SimulationStcResultDTO simulerStcDepuisDossier(Integer idDossier, Connection c) throws Exception {
        PaieDossierSortie entity = PaieDossierSortie.trouverParId(idDossier, c);
        if (entity == null) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Dossier de sortie introuvable : " + idDossier);
        }
        if (entity.getStatutDossier() != null && List.of("ANNULE", "CLOTURE").contains(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Impossible de simuler un dossier dans le statut " + entity.getStatutDossier());
        }

        // 1. Assemblage des données fraîches du dossier
        DossierSortieDTO dto = assemblerDossierDTO(entity, c);

        // 2. Construction de la requête de calcul STC
        StcCalculRequest calcReq = construireCalculRequest(entity, dto, true);

        // 3. Calcul de simulation via StcService existant
        StcCalculResultDTO simRes = stcService.simulerStc(calcReq, c);

        // 4. Capture du snapshot métier déterministe
        Map<String, Object> snapshot = capturerSnapshotMetier(entity, dto, simRes);

        // 5. Génération de la référence vérifiable
        String referenceSimulation = genererReferenceSimulation(entity.getIdDossierSortie(), snapshot, simRes);

        // 6. Sauvegarde de la référence et du snapshot dans le dossier préparatoire
        entity.setReferenceSimulation(referenceSimulation);
        entity.setSnapshotSimulation(objectMapper.writeValueAsString(snapshot));
        entity.modifier(c);

        return new SimulationStcResultDTO(referenceSimulation, entity.getVersion(), simRes, snapshot);
    }

    /**
     * Scelle transactionnellement le STC après confirmation explicite de la simulation.
     * Exécute un verrouillage optimiste, contrôle la fraîcheur de tous les champs métiers réellement
     * utilisés, valide juridiquement le STC et lie le résultat au dossier de sortie.
     */
    public DossierSortieDTO scellerDossierEtStc(Integer idDossier, ScellementStcRequest req, Integer userId, Connection c) throws Exception {
        if (req == null) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La requête de scellement est obligatoire.");
        }
        if (req.getReferenceSimulation() == null || req.getReferenceSimulation().isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La référence de la simulation financière est obligatoire.");
        }
        if (req.getVersion() == null || req.getVersion() <= 0) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "La version du dossier est obligatoire pour le verrouillage optimiste.");
        }

        // 1. Verrouillage pessimiste de la ligne pour garantir la cohérence transactionnelle
        PaieDossierSortie entity = PaieDossierSortie.trouverParIdPourMiseAJour(idDossier, c);
        if (entity == null) {
            throw new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, "Dossier de sortie introuvable : " + idDossier);
        }

        // 2. Protection contre le double-clic et la double émission
        if (List.of("VALIDE", "REGLE", "CLOTURE", "ANNULE").contains(entity.getStatutDossier())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Ce dossier de sortie a déjà été scellé (statut " + entity.getStatutDossier() + "). Le scellement ne peut pas être réexécuté.");
        }

        // 3. Contrôle optimiste de version du dossier
        if (!req.getVersion().equals(entity.getVersion())) {
            throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                    "Conflit de version sur le dossier (version courante : " + entity.getVersion()
                            + ", version fournie : " + req.getVersion() + "). Veuillez recharger le dossier.");
        }

        // 4. Contrôle de correspondance stricte de la référence de simulation
        if (entity.getReferenceSimulation() == null || !entity.getReferenceSimulation().equals(req.getReferenceSimulation())) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "La référence de simulation fournie (" + req.getReferenceSimulation()
                            + ") ne correspond pas à la dernière simulation active du dossier ("
                            + (entity.getReferenceSimulation() != null ? entity.getReferenceSimulation() : "aucune") + "). "
                            + "Veuillez relancer la simulation avant de sceller.");
        }

        if (entity.getSnapshotSimulation() == null || entity.getSnapshotSimulation().isBlank()) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Aucun snapshot financier associé à la simulation courante.");
        }

        Map<String, Object> snapshot = objectMapper.readValue(entity.getSnapshotSimulation(), new TypeReference<Map<String, Object>>() {});

        // 5. Contrôle de fraîcheur transactionnel couvrant les champs métier réellement utilisés
        DossierSortieDTO currentDto = assemblerDossierDTO(entity, c);
        verifierFraicheurMetier(snapshot, currentDto);

        // 6. Contrôle bloquant de conformité de la checklist
        boolean aControlesBloquants = currentDto.getChecklist().stream()
                .anyMatch(it -> it.getNiveau() == Niveau.BLOQUANT && it.getStatut() == StatutCheck.NON_CONFORME);
        if (aControlesBloquants) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Le scellement est impossible : des contrôles bloquants non conformes subsistent sur ce dossier.");
        }

        // 7. Scellement officiel via StcService
        StcCalculRequest calcReq = construireCalculRequest(entity, currentDto, false);
        if (req.getCommentaire() != null && !req.getCommentaire().isBlank()) {
            calcReq.setCommentaire(req.getCommentaire());
        }
        StcCalculResultDTO sealRes = stcService.validerStc(calcReq, userId, c);

        // 8. Vérification stricte du résultat financier scellé vs résultat simulé
        BigDecimal montantSimule = new BigDecimal(snapshot.get("netAPayerStc").toString());
        if (sealRes.getNetAPayerStc().compareTo(montantSimule) != 0) {
            throw new SirhBusinessException(SirhErrorCode.DATA_INTEGRITY_VIOLATION,
                    "Anomalie financière : le montant scellé (" + sealRes.getNetAPayerStc()
                            + " Ar) diffère du montant simulé vérifié (" + montantSimule + " Ar). La transaction est annulée.");
        }

        // 9. Mise à jour transactionnelle du dossier préparatoire
        entity.setIdStc(sealRes.getIdStc());
        entity.setStatutDossier("VALIDE");
        entity.modifier(c);

        return assemblerDossierDTO(entity, c);
    }

    private StcCalculRequest construireCalculRequest(PaieDossierSortie entity, DossierSortieDTO dto, boolean isSimulation) {
        StcCalculRequest calcReq = new StcCalculRequest();
        calcReq.setIdEmploye(entity.getIdEmploye());
        calcReq.setDateDepart(entity.getDateDepart());
        calcReq.setMotifRupture(entity.getMotifRupture());
        calcReq.setSimulation(isSimulation);

        // Phase 2 : Aucune retenue automatique sur prêt autorisée (apurement désactivé)
        calcReq.setApurerPretDansStc(false);
        calcReq.setSoldePretsADeduire(BigDecimal.ZERO);
        calcReq.setAutresDeductions(BigDecimal.ZERO);
        calcReq.setMotifAutresDeductions(null);

        // Extraction des paramètres saisis lors de la préparation
        if (entity.getDonneesPreparation() != null && !entity.getDonneesPreparation().isBlank()) {
            try {
                Map<String, Object> prep = objectMapper.readValue(entity.getDonneesPreparation(), new TypeReference<Map<String, Object>>() {});
                if (prep.get("joursTravaillesDernierMois") != null && !prep.get("joursTravaillesDernierMois").toString().isBlank()) {
                    calcReq.setJoursTravaillesDernierMois(new BigDecimal(prep.get("joursTravaillesDernierMois").toString()));
                }
                if (prep.get("justificationSalaireDernierMois") != null) {
                    calcReq.setJustificationSalaireDernierMois(prep.get("justificationSalaireDernierMois").toString());
                }
                if (prep.get("montantBulletinDejaPaye") != null && !prep.get("montantBulletinDejaPaye").toString().isBlank()) {
                    calcReq.setMontantBulletinDejaPaye(new BigDecimal(prep.get("montantBulletinDejaPaye").toString()));
                }
                if (prep.get("datePaiementBulletin") != null && !prep.get("datePaiementBulletin").toString().isBlank()) {
                    calcReq.setDatePaiementBulletin(Date.valueOf(prep.get("datePaiementBulletin").toString()));
                }
                if (prep.get("referencePaiementBulletin") != null) {
                    calcReq.setReferencePaiementBulletin(prep.get("referencePaiementBulletin").toString());
                }
                if (prep.get("soldeConges") != null && !prep.get("soldeConges").toString().isBlank()) {
                    calcReq.setSoldeConges(new BigDecimal(prep.get("soldeConges").toString()));
                }
                if (prep.get("moisPreavis") != null && !prep.get("moisPreavis").toString().isBlank()) {
                    calcReq.setMoisPreavis(new BigDecimal(prep.get("moisPreavis").toString()));
                }
                if (prep.get("indemnitePreavis") != null && !prep.get("indemnitePreavis").toString().isBlank()) {
                    calcReq.setIndemnitePreavis(new BigDecimal(prep.get("indemnitePreavis").toString()));
                }
                if (prep.get("indemniteLicenciement") != null && !prep.get("indemniteLicenciement").toString().isBlank()) {
                    calcReq.setIndemniteLicenciement(new BigDecimal(prep.get("indemniteLicenciement").toString()));
                }
                if (prep.get("remunerationReferenceLicenciement") != null && !prep.get("remunerationReferenceLicenciement").toString().isBlank()) {
                    calcReq.setRemunerationReferenceLicenciement(new BigDecimal(prep.get("remunerationReferenceLicenciement").toString()));
                }
                if (prep.get("justificationIndemnites") != null) {
                    calcReq.setJustificationIndemnites(prep.get("justificationIndemnites").toString());
                }
                if (prep.get("autresIndemnites") != null && !prep.get("autresIndemnites").toString().isBlank()) {
                    calcReq.setAutresIndemnites(new BigDecimal(prep.get("autresIndemnites").toString()));
                }
                if (prep.get("motifAutresIndemnites") != null) {
                    calcReq.setMotifAutresIndemnites(prep.get("motifAutresIndemnites").toString());
                }
                if (prep.get("modeReglement") != null && !prep.get("modeReglement").toString().isBlank()) {
                    calcReq.setModeReglement(prep.get("modeReglement").toString());
                }
                if (prep.get("commentaire") != null) {
                    calcReq.setCommentaire(prep.get("commentaire").toString());
                }
            } catch (Exception e) {
                log.warn("Erreur désérialisation donneesPreparation dossier {}: {}", entity.getIdDossierSortie(), e.getMessage());
            }
        }
        return calcReq;
    }

    private Map<String, Object> capturerSnapshotMetier(PaieDossierSortie entity, DossierSortieDTO dto, StcCalculResultDTO simRes) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("idDossierSortie", entity.getIdDossierSortie());
        map.put("idEmploye", entity.getIdEmploye());
        map.put("dateDepart", entity.getDateDepart() != null ? entity.getDateDepart().toString() : "");
        map.put("motifRupture", entity.getMotifRupture());
        map.put("versionDossier", entity.getVersion());

        map.put("idContrat", dto.getIdContrat());
        map.put("salaireBase", dto.getSalaireBase() != null ? dto.getSalaireBase().toPlainString() : "0");
        map.put("sourcePointage", dto.getSourcePointage() != null ? dto.getSourcePointage() : "");
        map.put("joursPointageReels", dto.getJoursPointageReels() != null ? dto.getJoursPointageReels().toPlainString() : "0");
        map.put("totalHeuresPointage", dto.getTotalHeuresPointage() != null ? dto.getTotalHeuresPointage().toPlainString() : "0");
        map.put("pointageGele", dto.isPointageGele());
        map.put("soldeConges", dto.getSoldeCongesRestant() != null ? dto.getSoldeCongesRestant().toPlainString() : "0");
        map.put("idBulletinMensuel", dto.getIdBulletinMensuel());
        map.put("statutBulletinMensuel", dto.getStatutBulletinMensuel());
        map.put("netBulletinMensuel", dto.getNetBulletinMensuel() != null ? dto.getNetBulletinMensuel().toPlainString() : "0");
        map.put("soldeAvances", dto.getAvancesEnAttente() != null ? dto.getAvancesEnAttente().toPlainString() : "0");
        map.put("capitalRestantPrets", dto.getCapitalRestantPrets() != null ? dto.getCapitalRestantPrets().toPlainString() : "0");

        map.put("totalBrutStc", simRes.getTotalBrutStc() != null ? simRes.getTotalBrutStc().toPlainString() : "0");
        map.put("totalCotisationsSociales", simRes.getTotalCotisationsSociales() != null ? simRes.getTotalCotisationsSociales().toPlainString() : "0");
        map.put("irsaNet", simRes.getIrsaNet() != null ? simRes.getIrsaNet().toPlainString() : "0");
        map.put("joursSousContrat", dto.getJoursSousContrat() != null ? dto.getJoursSousContrat().toPlainString() : "0");
        map.put("soldeAvancesDeduit", simRes.getSoldeAvancesDeduit() != null ? simRes.getSoldeAvancesDeduit().toPlainString() : "0");
        map.put("soldePretsDeduit", "0");
        map.put("soldeBulletinARegler", simRes.getSoldeBulletinARegler() != null ? simRes.getSoldeBulletinARegler().toPlainString() : "0");
        map.put("netAPayerStc", simRes.getNetAPayerStc() != null ? simRes.getNetAPayerStc().toPlainString() : "0");
        return map;
    }

    private String genererReferenceSimulation(Integer idDossier, Map<String, Object> snapshot, StcCalculResultDTO simRes) {
        try {
            String data = idDossier + "|"
                    + snapshot.get("idEmploye") + "|"
                    + snapshot.get("dateDepart") + "|"
                    + snapshot.get("salaireBase") + "|"
                    + snapshot.get("joursSousContrat") + "|"
                    + snapshot.get("soldeConges") + "|"
                    + snapshot.get("joursPointageReels") + "|"
                    + snapshot.get("totalHeuresPointage") + "|"
                    + snapshot.get("netBulletinMensuel") + "|"
                    + snapshot.get("soldeAvances") + "|"
                    + snapshot.get("netAPayerStc") + "|"
                    + System.currentTimeMillis();
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02X", hash[i]));
            }
            return "SIM-STC-" + idDossier + "-" + hex.toString();
        } catch (Exception e) {
            return "SIM-STC-" + idDossier + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }

    private void verifierFraicheurMetier(Map<String, Object> snap, DossierSortieDTO current) {
        // 1. Salaire de base
        BigDecimal snapSal = new BigDecimal(snap.getOrDefault("salaireBase", "0").toString());
        BigDecimal curSal = current.getSalaireBase() != null ? current.getSalaireBase() : BigDecimal.ZERO;
        if (curSal.compareTo(snapSal) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le salaire de base a changé (simulé : " + snapSal + ", actuel : " + curSal + "). Relancez la simulation.");
        }

        // 1b. Jours sous contrat
        BigDecimal snapJoursContrat = new BigDecimal(snap.getOrDefault("joursSousContrat", "0").toString());
        BigDecimal curJoursContrat = current.getJoursSousContrat() != null ? current.getJoursSousContrat() : BigDecimal.ZERO;
        if (curJoursContrat.compareTo(snapJoursContrat) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : les jours sous contrat ont changé (simulé : " + snapJoursContrat + ", actuel : " + curJoursContrat + "). Relancez la simulation.");
        }

        // 2. Contrat
        Object snapContrat = snap.get("idContrat");
        Integer curContrat = current.getIdContrat();
        String snapContratStr = snapContrat != null ? snapContrat.toString() : null;
        String curContratStr = curContrat != null ? curContrat.toString() : null;
        if (!Objects.equals(snapContratStr, curContratStr)) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le contrat de référence a changé. Relancez la simulation.");
        }

        // 3. Pointage
        String snapSrcPtg = (String) snap.getOrDefault("sourcePointage", "");
        String curSrcPtg = current.getSourcePointage() != null ? current.getSourcePointage() : "";
        if (!snapSrcPtg.equals(curSrcPtg)) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : la source de pointage a changé (" + snapSrcPtg + " -> " + curSrcPtg + "). Relancez la simulation.");
        }

        BigDecimal snapJoursPtg = new BigDecimal(snap.getOrDefault("joursPointageReels", "0").toString());
        BigDecimal curJoursPtg = current.getJoursPointageReels() != null ? current.getJoursPointageReels() : BigDecimal.ZERO;
        if (curJoursPtg.compareTo(snapJoursPtg) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : les jours de pointage réels ont changé (simulé : " + snapJoursPtg + ", actuel : " + curJoursPtg + "). Relancez la simulation.");
        }

        BigDecimal snapHeuresPtg = new BigDecimal(snap.getOrDefault("totalHeuresPointage", "0").toString());
        BigDecimal curHeuresPtg = current.getTotalHeuresPointage() != null ? current.getTotalHeuresPointage() : BigDecimal.ZERO;
        if (curHeuresPtg.compareTo(snapHeuresPtg) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le total des heures pointées a changé (simulé : " + snapHeuresPtg + ", actuel : " + curHeuresPtg + "). Relancez la simulation.");
        }

        boolean snapPtgGele = Boolean.parseBoolean(snap.getOrDefault("pointageGele", "false").toString());
        if (current.isPointageGele() != snapPtgGele) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le statut du gel de pointage a changé. Relancez la simulation.");
        }

        // 4. Congés
        BigDecimal snapSoldeCg = new BigDecimal(snap.getOrDefault("soldeConges", "0").toString());
        BigDecimal curSoldeCg = current.getSoldeCongesRestant() != null ? current.getSoldeCongesRestant() : BigDecimal.ZERO;
        if (curSoldeCg.compareTo(snapSoldeCg) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le solde de congés a changé (simulé : " + snapSoldeCg + ", actuel : " + curSoldeCg + "). Relancez la simulation.");
        }

        // 5. Bulletin mensuel
        Object snapIdBp = snap.get("idBulletinMensuel");
        Integer curIdBp = current.getIdBulletinMensuel();
        String snapIdBpStr = snapIdBp != null ? snapIdBp.toString() : null;
        String curIdBpStr = curIdBp != null ? curIdBp.toString() : null;
        if (!Objects.equals(snapIdBpStr, curIdBpStr)) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le bulletin mensuel associé a changé. Relancez la simulation.");
        }

        BigDecimal snapNetBp = new BigDecimal(snap.getOrDefault("netBulletinMensuel", "0").toString());
        BigDecimal curNetBp = current.getNetBulletinMensuel() != null ? current.getNetBulletinMensuel() : BigDecimal.ZERO;
        if (curNetBp.compareTo(snapNetBp) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le net du bulletin mensuel a changé (simulé : " + snapNetBp + ", actuel : " + curNetBp + "). Relancez la simulation.");
        }

        // 6. Acomptes et avances
        BigDecimal snapAv = new BigDecimal(snap.getOrDefault("soldeAvances", "0").toString());
        BigDecimal curAv = current.getAvancesEnAttente() != null ? current.getAvancesEnAttente() : BigDecimal.ZERO;
        if (curAv.compareTo(snapAv) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le solde des acomptes versés a changé (simulé : " + snapAv + ", actuel : " + curAv + "). Relancez la simulation.");
        }

        // 7. Prêts
        BigDecimal snapPret = new BigDecimal(snap.getOrDefault("capitalRestantPrets", "0").toString());
        BigDecimal curPret = current.getCapitalRestantPrets() != null ? current.getCapitalRestantPrets() : BigDecimal.ZERO;
        if (curPret.compareTo(snapPret) != 0) {
            throw new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED,
                    "Contrôle de fraîcheur échoué : le capital restant des prêts a changé (simulé : " + snapPret + ", actuel : " + curPret + "). Relancez la simulation.");
        }
    }

    // =========================================================================
    // ASSEMBLAGE DTO & SYNCHRONISATION EN LECTURE DES SOURCES
    // =========================================================================

    private DossierSortieDTO assemblerDossierDTO(PaieDossierSortie entity, Connection c) throws Exception {
        DossierSortieDTO dto = new DossierSortieDTO();
        dto.setIdDossierSortie(entity.getIdDossierSortie());
        dto.setIdEmploye(entity.getIdEmploye());
        dto.setDateDepart(entity.getDateDepart());
        dto.setMotifRupture(entity.getMotifRupture());
        dto.setStatutDossier(entity.getStatutDossier());
        dto.setDecisionPret(entity.getDecisionPret());
        dto.setJustificationDecisionPret(entity.getJustificationDecisionPret());
        dto.setMontantPretRetenu(entity.getMontantPretRetenu());
        dto.setCertificatDelivre(Boolean.TRUE.equals(entity.getCertificatDelivre()));
        dto.setDateDelivranceCertificat(entity.getDateDelivranceCertificat());
        dto.setModeRemiseCertificat(entity.getModeRemiseCertificat());
        dto.setRecuStcRemis(Boolean.TRUE.equals(entity.getRecuStcRemis()));
        dto.setDateRemiseRecu(entity.getDateRemiseRecu());
        dto.setIdStc(entity.getIdStc());
        dto.setCreePar(entity.getCreePar());
        dto.setCloturePar(entity.getCloturePar());
        dto.setDateCloture(entity.getDateCloture());
        dto.setMotifCloture(entity.getMotifCloture());
        dto.setDerogationReglement(Boolean.TRUE.equals(entity.getDerogationReglement()));
        dto.setJustificationDerogation(entity.getJustificationDerogation());
        dto.setAutoriteDecisionDerogation(entity.getAutoriteDecisionDerogation());
        dto.setReferenceDecisionDerogation(entity.getReferenceDecisionDerogation());
        dto.setDateDecisionDerogation(entity.getDateDecisionDerogation());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setVersion(entity.getVersion() != null ? entity.getVersion() : 1);
        dto.setReferenceSimulation(entity.getReferenceSimulation());

        if (entity.getDonneesPreparation() != null && !entity.getDonneesPreparation().isBlank()) {
            try {
                dto.setDonneesPreparation(objectMapper.readValue(entity.getDonneesPreparation(),
                        new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {}
        }

        // 1. Synchronisation Emploi / Carrière (en lecture seule)
        mg.galaxam.sirh.contrat.domain.EmploymentContext emploi = null;
        try {
            emploi = new EmploymentContextResolver().resolveContext(entity.getIdEmploye(), entity.getDateDepart().toLocalDate(), c);
        } catch (Exception e) {
            log.warn("[DossierSortie] Contexte d'emploi non résolu ou multiple pour employé {}: {}", entity.getIdEmploye(), e.getMessage());
        }
        if (emploi != null) {
            dto.setIdContrat(emploi.idContrat());
            dto.setMatricule(emploi.matricule());
            dto.setNomComplet(emploi.nomComplet());
            dto.setIntitulePoste(emploi.fonctionNom() != null ? emploi.fonctionNom() : "Collaborateur");
            dto.setDepartement(emploi.departementNom());
            dto.setSite(emploi.nomSite());
            dto.setCodeSite(emploi.codeSite());
            dto.setTypeContrat(emploi.typeContratCode());
            dto.setDateDebutContrat(emploi.dateDebutContrat() != null ? Date.valueOf(emploi.dateDebutContrat()) : null);
            dto.setDateFinContrat(emploi.dateFinContrat() != null ? Date.valueOf(emploi.dateFinContrat()) : null);
            dto.setSalaireBase(emploi.salaireBase());

            Date dateEmbauche = emploi.dateEmbaucheInitiale() != null ? Date.valueOf(emploi.dateEmbaucheInitiale()) : null;
            dto.setDateEmbauche(dateEmbauche);

            // Ancienneté
            var bilan = ancienneteEngine.calculerAnciennete(entity.getIdEmploye(), entity.getDateDepart().toLocalDate(), c);
            if (bilan != null) {
                dto.setAncienneteAnnees(bilan.ansEffectifs());
                dto.setAncienneteMois(bilan.moisEffectifs());
            }

            // Taux horaire
            if (dto.getSalaireBase() != null && dto.getSalaireBase().signum() > 0) {
                PaieConfigLegale cfg = PaieConfigLegale.findActiveAt(c, entity.getDateDepart().toLocalDate());
                BigDecimal diviseur = (cfg != null && cfg.getDiviseurMensuelHeures() != null)
                        ? cfg.getDiviseurMensuelHeures() : new BigDecimal("173.3333");
                dto.setTauxHoraire(dto.getSalaireBase().divide(diviseur, 4, RoundingMode.HALF_UP));
            }
        }

        // État administratif du contrat ciblé, distinct de la date de fin et de l'état financier du STC.
        if (entity.getIdContrat() != null) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT s.code FROM contrat_travail ct JOIN ref_statut_rh s ON s.id_statut_rh = ct.id_statut_rh WHERE ct.id_contrat = ?")) {
                ps.setInt(1, entity.getIdContrat());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) dto.setStatutContrat(rs.getString(1));
                }
            }
        }

        // Fallback résilient si la résolution unique du contexte d'emploi a échoué (ex: contrats multiples ou réembauche)
        if (entity.getIdContrat() != null && dto.getDateFinContrat() == null) {
            String sqlCtFallback = "SELECT c.date_debut, c.date_fin, t.code AS type_contrat " +
                    "FROM contrat_travail c " +
                    "LEFT JOIN ref_type_contrat t ON t.id_type_contrat = c.id_type_contrat " +
                    "WHERE c.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlCtFallback)) {
                ps.setInt(1, entity.getIdContrat());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        if (dto.getDateDebutContrat() == null && rs.getDate("date_debut") != null) {
                            dto.setDateDebutContrat(rs.getDate("date_debut"));
                        }
                        if (rs.getDate("date_fin") != null) {
                            dto.setDateFinContrat(rs.getDate("date_fin"));
                        }
                        if (dto.getTypeContrat() == null && rs.getString("type_contrat") != null) {
                            dto.setTypeContrat(rs.getString("type_contrat"));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (dto.getMatricule() == null && entity.getIdEmploye() != null) {
            String sqlEmp = "SELECT matricule, nom, prenom FROM employe WHERE id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
                ps.setInt(1, entity.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dto.setMatricule(rs.getString("matricule"));
                        dto.setNomComplet((rs.getString("nom") + " " + (rs.getString("prenom") != null ? rs.getString("prenom") : "")).trim());
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Synchronisation Pointage (en lecture seule, sans geler ni modifier)
        collecterPointageEnLecture(dto, c);

        // 3. Synchronisation Congés (en lecture seule)
        collecterCongesEnLecture(dto, c);

        // 4. Synchronisation Paie & Trésorerie (en lecture seule)
        collecterPaieEtTresorerieEnLecture(dto, c);

        // 5. Informations sur le STC lié et sa situation de règlement
        if (entity.getIdStc() != null) {
            PaieStc stc = PaieStc.trouverParId(entity.getIdStc(), c);
            if (stc != null) {
                dto.setNumeroRecu(stc.getNumeroRecu());
                dto.setNetAPayerStc(stc.getNetAPayerStc());
                dto.setDateReglement(stc.getDateReglement());
                dto.setReferenceReglement(stc.getReferenceReglement());

                StcSituationReglementDTO sit = stcService.obtenirSituationReglements(entity.getIdStc(), c);
                if (sit != null) {
                    dto.setMontantTotalRegle(sit.getTotalRegle());
                    dto.setSoldeRestantAPayer(sit.getResteAPayer());
                    dto.setEstIntegralementRegle(sit.isEstIntegralementRegle());
                    dto.setReglements(sit.getReglements());
                }
            }
        }

        // 6. Évaluation de la Checklist Métier Dynamique
        dto.setChecklist(evaluerChecklistDynamique(dto));
        dto.setPretAValider(dto.getChecklist().stream().noneMatch(it -> it.getNiveau() == Niveau.BLOQUANT && it.getStatut() == StatutCheck.NON_CONFORME));

        return dto;
    }

    private void collecterPointageEnLecture(DossierSortieDTO dto, Connection c) throws Exception {
        LocalDate dDep = dto.getDateDepart().toLocalDate();
        LocalDate dDebutMois = dDep.withDayOfMonth(1);

        String sqlPer = "SELECT id_periode_paie, date_debut, date_fin FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
        Integer idPeriode = null;
        LocalDate dDebutPlage = dDebutMois;
        LocalDate dFinPeriode = null;
        try (PreparedStatement ps = c.prepareStatement(sqlPer)) {
            ps.setDate(1, dto.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idPeriode = rs.getInt("id_periode_paie");
                    if (rs.getDate("date_debut") != null) {
                        dDebutPlage = rs.getDate("date_debut").toLocalDate();
                    }
                    if (rs.getDate("date_fin") != null) {
                        dFinPeriode = rs.getDate("date_fin").toLocalDate();
                    }
                }
            }
        }

        // Résolution de la couverture contrat réelle via EmploymentContextResolver
        var empResolver = new mg.galaxam.sirh.contrat.application.EmploymentContextResolver();
        mg.galaxam.sirh.contrat.application.EmploymentContextResolver.Coverage coverage = null;
        try {
            coverage = empResolver.resolveCoverage(dto.getIdEmploye(), dDebutPlage, dDep, c);
        } catch (Exception ignored) {}

        LocalDate debutCouverture = coverage != null ? coverage.firstDay() : dDebutPlage;
        LocalDate finCouverture = coverage != null ? coverage.lastDay() : dDep;

        boolean isPeriodeComplete = (dFinPeriode != null)
                && !debutCouverture.isAfter(dDebutPlage)
                && !dDep.isBefore(dFinPeriode);

        int joursSousContrat;
        if (isPeriodeComplete) {
            joursSousContrat = 30;
        } else if (coverage != null) {
            joursSousContrat = Math.min(coverage.days(), 30);
        } else {
            joursSousContrat = Math.min((int) java.time.temporal.ChronoUnit.DAYS.between(dDebutPlage, dDep) + 1, 30);
        }
        dto.setJoursSousContrat(new BigDecimal(Math.max(0, joursSousContrat)));

        // Vérification snapshot existant et compteurs consolidés de la période
        if (idPeriode != null) {
            String sqlSnap = "SELECT nb_jours_ouvrables, nb_jours_presents, nb_jours_conge, " +
                    "COALESCE(minutes_absence_finale, 0) AS min_abs_fin, heures_absence_retenues " +
                    "FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? " +
                    "AND statut_snapshot IN ('GELE', 'CONSOMME') ORDER BY version_snapshot DESC LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sqlSnap)) {
                ps.setInt(1, dto.getIdEmploye());
                ps.setInt(2, idPeriode);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        dto.setPointageGele(true);
                        dto.setJoursOuvresPeriode(rs.getInt("nb_jours_ouvrables"));
                        dto.setJoursCongesPeriode(rs.getInt("nb_jours_conge"));
                        int minFin = rs.getInt("min_abs_fin");
                        BigDecimal hAbs = rs.getBigDecimal("heures_absence_retenues");
                        if (minFin > 0) {
                            dto.setHeuresAbsenceRetenues(BigDecimal.valueOf(minFin).divide(new BigDecimal("60"), 2, RoundingMode.HALF_UP));
                        } else if (hAbs != null) {
                            dto.setHeuresAbsenceRetenues(hAbs);
                        } else {
                            dto.setHeuresAbsenceRetenues(BigDecimal.ZERO);
                        }
                    }
                }
            }
        }

        // Lecture des heures pointées réelles sur [debutCouverture, finCouverture]
        String sqlPtg = "SELECT COUNT(*) AS total_lignes, " +
                "COUNT(*) FILTER (WHERE r.decompte_absence = FALSE AND (pj.total_heures > 0 OR pj.heures_matin > 0 OR pj.heures_am > 0)) AS jours_travailles_reels, " +
                "COUNT(*) FILTER (WHERE r.code = 'CONGE') AS jours_conges, " +
                "COALESCE(SUM(pj.total_heures), 0) AS total_heures " +
                "FROM pointage_jour pj " +
                "JOIN ref_statut_pointage_jour r ON r.id_statut_jour = pj.id_statut_jour " +
                "WHERE pj.id_employe = ? AND pj.date_pointage >= ? AND pj.date_pointage <= ?";
        try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
            ps.setInt(1, dto.getIdEmploye());
            ps.setDate(2, Date.valueOf(debutCouverture));
            ps.setDate(3, Date.valueOf(finCouverture));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt("total_lignes") > 0) {
                    dto.setSourcePointage("POINTAGE_REEL");
                    dto.setJoursPointageReels(new BigDecimal(rs.getInt("jours_travailles_reels")));
                    dto.setTotalHeuresPointage(rs.getBigDecimal("total_heures"));
                    if (dto.getJoursCongesPeriode() == null) {
                        dto.setJoursCongesPeriode(rs.getInt("jours_conges"));
                    }
                } else {
                    dto.setSourcePointage("ESTIMATION_CALENDAIRE");
                    dto.setJoursPointageReels(BigDecimal.ZERO);
                    dto.setTotalHeuresPointage(BigDecimal.ZERO);
                }
            }
        }

        if (dto.getJoursOuvresPeriode() == null && dDebutPlage != null) {
            LocalDate finCalc = dFinPeriode != null ? dFinPeriode : finCouverture;
            int countOuvres = 0;
            LocalDate cur = dDebutPlage;
            while (!cur.isAfter(finCalc)) {
                if (cur.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && cur.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                    countOuvres++;
                }
                cur = cur.plusDays(1);
            }
            dto.setJoursOuvresPeriode(countOuvres);
        }
        if (dto.getHeuresAbsenceRetenues() == null) {
            dto.setHeuresAbsenceRetenues(BigDecimal.ZERO);
        }
        if (dto.getJoursCongesPeriode() == null) {
            dto.setJoursCongesPeriode(0);
        }
    }

    private void collecterCongesEnLecture(DossierSortieDTO dto, Connection c) throws Exception {
        VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(dto.getIdEmploye(), c);
        BigDecimal soldeJours = (solde != null && solde.getSoldeActuel() != null) ? solde.getSoldeActuel() : BigDecimal.ZERO;
        dto.setSoldeCongesRestant(soldeJours);

        if (soldeJours.signum() > 0) {
            try {
                var sit = lotsConges.situation(dto.getIdEmploye(), dto.getDateDepart().toLocalDate(), c);
                dto.setIccpEstimee(sit.soldeValorisation());
                dto.setTauxJournalierConge(sit.soldeValorisation().divide(soldeJours, 4, RoundingMode.HALF_UP));
            } catch (Exception e) {
                log.warn("[DossierSortie] Erreur calcul valorisation congés: {}", e.getMessage());
                dto.setIccpEstimee(BigDecimal.ZERO);
                dto.setTauxJournalierConge(BigDecimal.ZERO);
            }
        } else {
            dto.setIccpEstimee(BigDecimal.ZERO);
            dto.setTauxJournalierConge(BigDecimal.ZERO);
        }

        // Demandes de congés en attente
        String sqlDem = "SELECT COUNT(*) FROM demande_conge d " +
                "JOIN ref_statut_demande_conge s ON s.id_statut_demande_conge = d.id_statut_demande_conge " +
                "WHERE d.id_employe = ? AND s.code = 'EN_ATTENTE'";
        try (PreparedStatement ps = c.prepareStatement(sqlDem)) {
            ps.setInt(1, dto.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dto.setDemandesCongesEnAttente(rs.getInt(1));
            }
        }
    }

    private void collecterPaieEtTresorerieEnLecture(DossierSortieDTO dto, Connection c) throws Exception {
        // Bulletin mensuel existant
        String sqlBp = "SELECT b.id_bulletin, b.statut, b.net_a_payer FROM bulletin_paie b " +
                "JOIN periode_paie p ON p.id_periode_paie = b.id_periode_paie " +
                "WHERE b.id_employe = ? AND ? BETWEEN p.date_debut AND p.date_fin " +
                "AND COALESCE(b.type_bulletin, 'MENSUEL') = 'MENSUEL' AND b.statut <> 'ANNULE' LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlBp)) {
            ps.setInt(1, dto.getIdEmploye());
            ps.setDate(2, dto.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto.setIdBulletinMensuel(rs.getInt("id_bulletin"));
                    dto.setStatutBulletinMensuel(rs.getString("statut"));
                    dto.setNetBulletinMensuel(rs.getBigDecimal("net_a_payer"));
                    dto.setSoldeBulletinARegler(rs.getBigDecimal("net_a_payer"));
                }
            }
        }

        // Acomptes & Avances approuvés versés non imputés
        String sqlAv = "SELECT COALESCE(SUM(a.solde_restant), 0) FROM paie_avance_acompte a " +
                "JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                "WHERE a.id_employe = ? AND t.categorie IN ('ACOMPTE','AVANCE') " +
                "AND a.versement_confirme_at IS NOT NULL AND a.statut = 'APPROUVE'";
        try (PreparedStatement ps = c.prepareStatement(sqlAv)) {
            ps.setInt(1, dto.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dto.setAvancesEnAttente(rs.getBigDecimal(1));
            }
        }

        // Capital restant des prêts
        dto.setCapitalRestantPrets(PaiePret.calculerCapitalRestantTotal(dto.getIdEmploye(), c));

        // Variables de paie en attente sur la période
        String sqlVar = "SELECT COUNT(*) FROM paie_avance_acompte a " +
                "LEFT JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                "JOIN periode_paie p ON p.id_periode_paie = a.id_periode_paie " +
                "WHERE a.id_employe = ? AND ? BETWEEN p.date_debut AND p.date_fin " +
                "AND a.statut IN ('EN_ATTENTE', 'APPROUVE') " +
                "AND COALESCE(t.categorie, '') NOT IN ('ACOMPTE', 'AVANCE')";
        try (PreparedStatement ps = c.prepareStatement(sqlVar)) {
            ps.setInt(1, dto.getIdEmploye());
            ps.setDate(2, dto.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) dto.setPrimesOuVariablesEnAttente(rs.getInt(1));
            }
        }
    }

    // =========================================================================
    // CHECKLIST DYNAMIQUE HIÉRARCHISÉE (Loi 2024 vs GALAXAM vs Technique SI)
    // =========================================================================

    private List<ChecklistItemDTO> evaluerChecklistDynamique(DossierSortieDTO d) {
        List<ChecklistItemDTO> list = new ArrayList<>();
        LocalDate today = PayrollBusinessTime.today();
        LocalDate dDep = d.getDateDepart().toLocalDate();

        // 1. L-01 : Date de départ future (Loi Malgache 2024 / Libération des droits)
        boolean future = dDep.isAfter(today);
        list.add(new ChecklistItemDTO(
                "L-01_DATE_DEPART",
                "Échéance légale de départ",
                Niveau.BLOQUANT,
                SourceNorme.LEGAL_MADAGASCAR_2024,
                future ? StatutCheck.NON_CONFORME : StatutCheck.CONFORME,
                future ? "La date de départ effective (" + dDep + ") est future. Le STC définitif ne peut être émis qu'à la cessation réelle."
                       : "Date de départ atteinte ou passée (" + dDep + ").",
                future ? "Conserver le dossier en préparation jusqu'au jour du départ." : "Validation autorisée.",
                null
        ));

        // 2. L-02 : Concordance de la rupture contractuelle (Loi 2024-004)
        boolean dateFinConforme = d.getDateFinContrat() != null && d.getDateFinContrat().toLocalDate().equals(dDep);
        list.add(new ChecklistItemDTO(
                "L-02_CONCORDANCE_CONTRAT",
                "Clôture du contrat de travail (Art. 18-35)",
                Niveau.BLOQUANT,
                SourceNorme.LEGAL_MADAGASCAR_2024,
                dateFinConforme ? StatutCheck.CONFORME : StatutCheck.NON_CONFORME,
                dateFinConforme ? "Le contrat se termine à la date exacte du départ (" + dDep + ")."
                                : "La date de fin du contrat (" + d.getDateFinContrat() + ") ne correspond pas à la date de départ (" + dDep + ").",
                dateFinConforme ? "Situation contractuelle en règle." : "Mettre à jour la rupture dans le module Carrière.",
                "carriere"
        ));

        // 3. L-03 : Certificat de travail (Art. 50 Code du travail malgache 2024)
        list.add(new ChecklistItemDTO(
                "L-03_CERTIFICAT_TRAVAIL",
                "Certificat de travail (Art. 50 Code 2024)",
                Niveau.INFO,
                SourceNorme.LEGAL_MADAGASCAR_2024,
                d.isCertificatDelivre() ? StatutCheck.CONFORME : StatutCheck.A_VERIFIER,
                d.isCertificatDelivre() ? "Certificat délivré le " + d.getDateDelivranceCertificat() + " (" + d.getModeRemiseCertificat() + ")."
                                       : "Certificat à délivrer obligatoirement dès la cessation du contrat (Art. 50).",
                d.isCertificatDelivre() ? "Obligation légale satisfaite." : "Télécharger et remettre le certificat au salarié.",
                "documents"
        ));

        // 4. G-01 : Décision formelle sur le prêt actif (Règle GALAXAM)
        boolean hasPret = d.getCapitalRestantPrets() != null && d.getCapitalRestantPrets().signum() > 0;
        boolean hasDecisionPret = d.getDecisionPret() != null && !d.getDecisionPret().isBlank();
        list.add(new ChecklistItemDTO(
                "G-01_DECISION_PRET",
                "Créance de prêt d'entreprise en cours",
                hasPret ? Niveau.BLOQUANT : Niveau.INFO,
                SourceNorme.REGLE_INTERNE_GALAXAM,
                (!hasPret || hasDecisionPret) ? StatutCheck.CONFORME : StatutCheck.NON_CONFORME,
                hasPret ? (hasDecisionPret ? "Décision enregistrée : " + d.getDecisionPret() + " (Capital restant : " + d.getCapitalRestantPrets() + " Ar)."
                                           : "Prêt actif détecté (" + d.getCapitalRestantPrets() + " Ar) : une décision formelle est requise (aucun prélèvement sauvage).")
                        : "Aucun prêt d'entreprise en cours.",
                hasPret && !hasDecisionPret ? "Renseigner l'option dans l'onglet Décision Prêt (maintien échéancier, chèque externe, ou accord signé)." : null,
                null
        ));

        // 5. G-02 : Variables ou primes en attente (Règle GALAXAM)
        boolean hasVariables = d.getPrimesOuVariablesEnAttente() > 0;
        list.add(new ChecklistItemDTO(
                "G-02_VARIABLES_EN_ATTENTE",
                "Variables et primes en cours de traitement",
                hasVariables ? Niveau.BLOQUANT : Niveau.INFO,
                SourceNorme.REGLE_INTERNE_GALAXAM,
                hasVariables ? StatutCheck.NON_CONFORME : StatutCheck.CONFORME,
                hasVariables ? d.getPrimesOuVariablesEnAttente() + " variable(s) ou prime(s) en attente d'arbitrage sur cette période."
                             : "Toutes les variables de la période sont traitées.",
                hasVariables ? "Approuver ou rejeter les primes dans le module Ajustements avant de sceller le STC." : null,
                "paie-ajustements"
        ));

        // 6. G-03 : Justification des jours si pointage absent (Règle GALAXAM)
        boolean sansPointage = !"POINTAGE_REEL".equals(d.getSourcePointage());
        list.add(new ChecklistItemDTO(
                "G-03_JUSTIFICATION_POINTAGE",
                "Contrôle de présence finale",
                sansPointage ? Niveau.AVERTISSEMENT : Niveau.INFO,
                SourceNorme.REGLE_INTERNE_GALAXAM,
                sansPointage ? StatutCheck.A_VERIFIER : StatutCheck.CONFORME,
                sansPointage ? "Aucun pointage biométrique détecté : prorata calculé au trentième (" + d.getJoursSousContrat() + " j). Justification requise pour validation."
                             : "Pointage réel présent : " + d.getJoursPointageReels() + " j (" + d.getTotalHeuresPointage() + "h).",
                sansPointage ? "Saisir la justification des jours rémunérés dans le décompte." : null,
                "pointage"
        ));

        // 7. T-01 : Snapshot de pointage final (Technique SI)
        boolean pointageNonGele = "POINTAGE_REEL".equals(d.getSourcePointage()) && !d.isPointageGele();
        list.add(new ChecklistItemDTO(
                "T-01_SNAPSHOT_POINTAGE",
                "Pointage mensuel de la période",
                pointageNonGele ? Niveau.AVERTISSEMENT : Niveau.INFO,
                SourceNorme.TECHNIQUE_SI,
                pointageNonGele ? StatutCheck.A_VERIFIER : StatutCheck.CONFORME,
                pointageNonGele ? "Pointage réel synchronisé jusqu'au départ (" + d.getJoursPointageReels() + " j). La campagne mensuelle globale n'est pas encore gelée (départ en cours de cycle)."
                                : "Pointage conforme pour l'établissement du décompte.",
                pointageNonGele ? "Vérifier que les pointages journaliers jusqu'au départ sont bien validés." : null,
                "pointage"
        ));

        // 8. W-01 : Demandes de congés en attente (Avertissement)
        boolean hasCongesEnAttente = d.getDemandesCongesEnAttente() > 0;
        list.add(new ChecklistItemDTO(
                "W-01_CONGES_EN_ATTENTE",
                "Demandes de congés non arbitrées",
                Niveau.AVERTISSEMENT,
                SourceNorme.REGLE_INTERNE_GALAXAM,
                hasCongesEnAttente ? StatutCheck.A_VERIFIER : StatutCheck.CONFORME,
                hasCongesEnAttente ? d.getDemandesCongesEnAttente() + " demande(s) de congé en attente de décision. Elles peuvent modifier le solde ICCP."
                                   : "Aucune demande de congé en suspens. Solde actuel : " + d.getSoldeCongesRestant() + " j.",
                hasCongesEnAttente ? "Valider ou refuser les demandes de congé dans le module Congés." : null,
                "conges"
        ));

        // 9. W-02 : Solde de congés négatif (Avertissement)
        boolean soldeNegatif = d.getSoldeCongesRestant() != null && d.getSoldeCongesRestant().signum() < 0;
        list.add(new ChecklistItemDTO(
                "W-02_SOLDE_CONGE_NEGATIF",
                "Congés pris par anticipation",
                Niveau.AVERTISSEMENT,
                SourceNorme.LEGAL_MADAGASCAR_2024,
                soldeNegatif ? StatutCheck.A_VERIFIER : StatutCheck.CONFORME,
                soldeNegatif ? "Le salarié a un solde de congés négatif (" + d.getSoldeCongesRestant() + " j). Une retenue automatique aveugle est proscrite sans examen RH."
                             : "Solde de congés positif ou nul (" + d.getSoldeCongesRestant() + " j, valorisation estimée : " + d.getIccpEstimee() + " Ar).",
                soldeNegatif ? "Examiner si une retenue est contractuellement et légalement autorisée." : null,
                "conges"
        ));

        return list;
    }
}
