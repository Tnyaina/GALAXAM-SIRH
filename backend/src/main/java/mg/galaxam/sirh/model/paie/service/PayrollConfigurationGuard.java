package mg.galaxam.sirh.model.paie.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mg.galaxam.sirh.api.dto.common.BusinessIssue;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;

/** Point unique de traduction d'une configuration de paie absente ou ambiguë. */
final class PayrollConfigurationGuard {

    private static final String ISSUE_CODE = "PAYROLL_CONFIGURATION_MISSING";

    private PayrollConfigurationGuard() {
    }

    static boolean requireFlag(
            ResultSet rs, String column, String configurationType, Integer employeeId) throws SQLException {
        Boolean value = rs.getObject(column, Boolean.class);
        if (value == null) {
            throw missing(configurationType, employeeId, column);
        }
        return value;
    }

    static SirhBusinessException missing(String configurationType, Integer employeeId, String field) {
        Map<String, Object> details = new HashMap<>();
        details.put("configurationType", configurationType);
        details.put("employeeId", employeeId);
        if (field != null) details.put("field", field);
        BusinessIssue issue = new BusinessIssue(
                ISSUE_CODE,
                "La configuration " + configurationType + " doit être renseignée explicitement.",
                details);
        return new SirhBusinessException(
                SirhErrorCode.BLOCKING_CONFIGURATION_ERROR,
                issue.message(),
                details,
                List.of(issue));
    }
}
