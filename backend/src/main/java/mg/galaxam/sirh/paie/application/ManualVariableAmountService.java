package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.api.dto.PaieAjustementRequest;
import mg.galaxam.sirh.paie.domain.engine.ManualPayrollRules;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeMouvementPaie;
import org.springframework.stereotype.Service;

/** Même résolution datée et même arrondi pour l'aperçu et l'enregistrement d'une variable. */
@Service
public class ManualVariableAmountService {
    private final PayrollService payrollService;

    public ManualVariableAmountService(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    public ManualPayrollRules.AmountResult calculate(PaieAjustementRequest request,
            RefTypeMouvementPaie type, Connection connection) throws Exception {
        if (type == null || !type.isEstActif() || !type.isSaisieManuelleAutorisee()
                || !"VARIABLE_DAF".equals(type.getNature())
                || type.getIdRubrique() == null || request.getIdPeriodePaie() == null
                || request.getIdEmploye() == null) {
            throw new SirhBusinessException(SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                    "Salarié, période et rubrique manuelle active sont obligatoires.");
        }
        LocalDate start;
        LocalDate end;
        try (var statement = connection.prepareStatement(
                "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
            statement.setInt(1, request.getIdPeriodePaie());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, "Période inconnue.");
                }
                start = rows.getDate(1).toLocalDate();
                end = rows.getDate(2).toLocalDate();
            }
        }
        var rule = ManualRubriqueResolver.resolve(connection, type.getIdRubrique(), start, end);
        if (!rule.isEstActif()) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR,
                    "Cette version est désactivée pour la nouvelle saisie.");
        }
        BigDecimal salary = null;
        if ("POURCENTAGE_SALAIRE_BASE".equals(rule.getMethodeCalcul())) {
            var input = payrollService.preparerInput(request.getIdEmploye(), request.getIdPeriodePaie(), true, connection);
            salary = "MENSUEL".equalsIgnoreCase(input.getModeCalculPaie())
                    ? input.getSalaireBaseCampagne() : input.getSalaireBaseNonMensuel();
        }
        try {
            return ManualPayrollRules.calculateAmount(rule.getMethodeCalcul(), rule.getPolitiqueArrondi(),
                    request.getMontant(), request.getQuantiteSaisie(), request.getTauxSaisi(), salary);
        } catch (IllegalArgumentException error) {
            throw new SirhBusinessException(SirhErrorCode.VALIDATION_ERROR, error.getMessage());
        }
    }
}
