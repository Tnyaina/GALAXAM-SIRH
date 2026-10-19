package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.api.dto.PaieAjustementRequest;
import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAction;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodUserContext;
import mg.galaxam.sirh.paie.application.workflow.PayrollPeriodWorkflowService;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeMouvementPaie;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Unique owner of payroll adjustment creation and its transactional validations. */
@Service
public class PayrollAdjustmentCreationService {
    private final DataSource dataSource;
    private final PayrollPeriodWorkflowService periodWorkflowService;
    private final ManualVariableAmountService manualVariableAmountService;

    public PayrollAdjustmentCreationService(DataSource dataSource,
            PayrollPeriodWorkflowService periodWorkflowService,
            ManualVariableAmountService manualVariableAmountService) {
        this.dataSource = dataSource;
        this.periodWorkflowService = periodWorkflowService;
        this.manualVariableAmountService = manualVariableAmountService;
    }

    public Map<String, Object> create(PaieAjustementRequest req, Authentication authentication, int actorId) throws Exception {
        Integer userId = actorId;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Résolution du type de mouvement
                RefTypeMouvementPaie typeMvt = null;
                if (req.getIdTypeMouvement() != null) {
                    typeMvt = RefTypeMouvementPaie.trouverParId(req.getIdTypeMouvement(), c);
                } else if (req.getTypeMouvement() != null && !req.getTypeMouvement().isBlank()) {
                    typeMvt = RefTypeMouvementPaie.trouverParCode(req.getTypeMouvement().trim(), c);
                }

                if (typeMvt == null || !typeMvt.isEstActif()) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Le type de mouvement doit être actif et configuré dans le référentiel de paie.");
                }
                if (!typeMvt.isSaisieManuelleAutorisee()) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Ce type appartient à un owner automatique ou à un domaine non saisissable en Paie.");
                }
                if (typeMvt.isPeriodeObligatoire() && req.getIdPeriodePaie() == null) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "La période de paie est obligatoire pour ce type de mouvement.");
                }
                if (typeMvt.isMotifObligatoire() && (req.getMotif() == null || req.getMotif().isBlank())) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Le motif ou la source est obligatoire pour ce type de mouvement.");
                }
                if (req.getIdPeriodePaie() != null) {
                    periodWorkflowService.requirePayrollAction(req.getIdPeriodePaie(), PayrollAction.ADD_VARIABLE,
                            PayrollPeriodUserContext.from(authentication));
                }

                if (req.getIdPeriodePaie() != null && req.getIdEmploye() != null) {
                    String sqlCouv = "SELECT 1 FROM employe_affectation_carriere eac " +
                            "JOIN contrat_travail ct ON ct.id_contrat = eac.id_contrat " +
                            "JOIN periode_paie p ON p.id_periode_paie = ? " +
                            "WHERE eac.id_employe = ? " +
                            "AND eac.date_debut <= p.date_fin AND (eac.date_fin IS NULL OR eac.date_fin >= p.date_debut) "
                            +
                            "AND ct.date_debut <= p.date_fin AND (ct.date_fin IS NULL OR ct.date_fin >= p.date_debut) "
                            +
                            "LIMIT 1";
                    try (PreparedStatement psCouv = c.prepareStatement(sqlCouv)) {
                        psCouv.setInt(1, req.getIdPeriodePaie());
                        psCouv.setInt(2, req.getIdEmploye());
                        try (ResultSet rsCouv = psCouv.executeQuery()) {
                            if (!rsCouv.next()) {
                                throw new SirhBusinessException(SirhErrorCode.PAYROLL_EMPLOYEE_INACTIVE,
                                        "L'employé ne dispose d'aucun contrat ou affectation active sur la période de paie sélectionnée.");
                            }
                        }
                    }
                }

                String codeType = typeMvt.getCode();
                Integer cutoverCorrectionId = null;
                BigDecimal cutoverAmount = null;
                if ("REGULARISATION_CONGE".equals(codeType)) {
                    if (req.getIdEmploye() == null) throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Le salarié est obligatoire pour une régularisation Congés.");
                    String motif = req.getMotif() != null ? req.getMotif() : "";
                    var reference = java.util.regex.Pattern.compile("(?:^|\\s)CORRECTION_CUTOVER#(\\d+)(?:\\s|$)")
                            .matcher(motif);
                    if (!reference.find()) throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                            "Régularisation Congés : indiquez CORRECTION_CUTOVER#<id> dans le motif.");
                    cutoverCorrectionId = Integer.parseInt(reference.group(1));
                    try (PreparedStatement preuve = c.prepareStatement(
                            "SELECT regularisation_a_examiner FROM conge_cutover_correction "
                                    + "WHERE id_correction=? AND id_employe=?")) {
                        preuve.setInt(1, cutoverCorrectionId);
                        preuve.setInt(2, req.getIdEmploye());
                        try (ResultSet rs = preuve.executeQuery()) {
                            if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                    "La correction de reliquat citée n'appartient pas au salarié.");
                            cutoverAmount = rs.getBigDecimal(1);
                        }
                    }
                    if (cutoverAmount == null || cutoverAmount.signum() <= 0) {
                        throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                "Cette correction ne constitue pas un complément positif. Un écart négatif exige un traitement distinct.");
                    }
                    if (req.getMontant() != null && req.getMontant().compareTo(cutoverAmount) != 0) {
                        throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                "Le montant doit correspondre exactement à l'écart calculé par la correction de reliquat.");
                    }
                }
                String sens = typeMvt.getSens();
                Integer idType = typeMvt.getIdTypeMouvement();

                BigDecimal resolvedAmount=req.getMontant();
                if ("VARIABLE_DAF".equals(typeMvt.getNature())) {
                    resolvedAmount = manualVariableAmountService.calculate(req, typeMvt, c).montant();
                }
                if (cutoverAmount != null) resolvedAmount = cutoverAmount;
                if(resolvedAmount==null || resolvedAmount.signum()<=0)
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,"Le montant doit être positif.");
                PaieAvanceAcompte entity = new PaieAvanceAcompte();
                entity.setIdEmploye(req.getIdEmploye());
                entity.setIdPeriodePaie(req.getIdPeriodePaie());
                entity.setIdTypeMouvement(idType);
                entity.setTypeMouvement(codeType);
                entity.setSens(sens);
                entity.setMontant(resolvedAmount);
                entity.setQuantiteSaisie(req.getQuantiteSaisie()); entity.setTauxSaisi(req.getTauxSaisi());
                entity.setMontantInitial(resolvedAmount);
                entity.setMontantImpute(java.math.BigDecimal.ZERO);
                entity.setSoldeRestant(resolvedAmount);
                entity.setProvenance(typeMvt.getProvenanceDefaut());
                entity.setDateDemande(req.getDateDemande() != null ? req.getDateDemande()
                        : new java.sql.Date(System.currentTimeMillis()));
                entity.setMotif(req.getMotif());
                entity.setModeReglement(req.getModeReglement() != null ? req.getModeReglement() : "VIREMENT");
                // P1-01 : Le statut initial est toujours EN_ATTENTE — le client ne décide pas.
                // L'approbation passe par POST /{id}/commands avec commande APPROVE.
                entity.setStatut("EN_ATTENTE");
                entity.setCreePar(userId);

                entity.save(c);
                if (cutoverCorrectionId != null) {
                    try (PreparedStatement link = c.prepareStatement(
                            "UPDATE paie_avance_acompte SET id_conge_cutover_correction=? WHERE id_avance=?")) {
                        link.setInt(1, cutoverCorrectionId);
                        link.setInt(2, entity.getIdAvance());
                        link.executeUpdate();
                    } catch (SQLException duplicate) {
                        if ("23505".equals(duplicate.getSQLState())) {
                            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                                    "Cette correction de reliquat est déjà liée à une régularisation de paie.");
                        }
                        throw duplicate;
                    }
                }
                AuditLog.audit(c, "CREATION", "paie_avance_acompte", String.valueOf(entity.getIdAvance()), null,
                        entity);

                int version = 1;
                try (PreparedStatement psVer = c
                        .prepareStatement("SELECT updated_at FROM paie_avance_acompte WHERE id_avance = ?")) {
                    psVer.setInt(1, entity.getIdAvance());
                    try (ResultSet rsVer = psVer.executeQuery()) {
                        if (rsVer.next()) {
                            Timestamp ts = rsVer.getTimestamp("updated_at");
                            if (ts != null) {
                                version = (int) (ts.getTime() % Integer.MAX_VALUE);
                            }
                        }
                    }
                }

                c.commit();

                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "Ajustement créé avec succès.");
                resp.put("idAvance", entity.getIdAvance());
                resp.put("typeMouvement", codeType);
                resp.put("sens", sens);
                resp.put("montant", entity.getMontant());
                if (cutoverCorrectionId != null) resp.put("idCongeCutoverCorrection", cutoverCorrectionId);
                resp.put("statut", entity.getStatut());
                resp.put("version", version);

                return resp;
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            }
        }
    }
}
