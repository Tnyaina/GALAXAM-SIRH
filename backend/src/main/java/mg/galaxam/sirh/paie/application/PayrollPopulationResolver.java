package mg.galaxam.sirh.paie.application;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.paie.api.dto.PayrollPopulationMember;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import org.springframework.stereotype.Service;

/** Résolution canonique et datée de la population d'une campagne de paie. */
@Service
public class PayrollPopulationResolver {

    public PayrollPopulation resolve(int periodId, Connection connection) throws SQLException {
        Period period = loadPeriod(periodId, connection);
        String sql = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, eac.id_affectation, "
                + "ct.id_contrat, ct.numero_contrat, tri.code AS regime "
                + "FROM employe e "
                + "LEFT JOIN employe_affectation_carriere eac ON eac.id_employe = e.id_employe "
                + "AND eac.date_debut <= ? AND (eac.date_fin IS NULL OR eac.date_fin >= ?) "
                + "LEFT JOIN contrat_travail ct ON ct.id_contrat = eac.id_contrat "
                + "AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?) "
                + "LEFT JOIN ref_type_intervenant tri ON tri.id_type_intervenant = ct.id_type_intervenant "
                + "WHERE e.est_actif = TRUE ORDER BY e.matricule, eac.date_debut";
        Map<Integer, List<Row>> rowsByEmployee = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(period.endDate()));
            statement.setDate(2, Date.valueOf(period.startDate()));
            statement.setDate(3, Date.valueOf(period.endDate()));
            statement.setDate(4, Date.valueOf(period.startDate()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int employeeId = result.getInt("id_employe");
                    rowsByEmployee.computeIfAbsent(employeeId, ignored -> new ArrayList<>()).add(new Row(
                            employeeId, result.getString("matricule"),
                            (result.getString("nom") + " " + result.getString("prenom")).trim(),
                            (Integer) result.getObject("id_affectation"), (Integer) result.getObject("id_contrat"),
                            result.getString("numero_contrat"),
                            result.getString("regime")));
                }
            }
        }

        List<PayrollPopulationMember> members = rowsByEmployee.values().stream()
                .map(this::decide)
                .toList();
        return new PayrollPopulation(period.id(), period.code(), period.startDate(), period.endDate(), members);
    }

    private PayrollPopulationMember decide(List<Row> rows) {
        Row row = rows.get(0);
        long applicableAssignments = rows.stream().filter(candidate -> candidate.assignmentId() != null).count();
        if (applicableAssignments == 0) {
            return member(row, "EXCLUDED", "NO_APPLICABLE_ASSIGNMENT");
        }
        if (applicableAssignments > 1) {
            return member(row, "BLOCKED", "AMBIGUOUS_ASSIGNMENT");
        }
        if (row.contractId() == null || row.regime() == null) {
            return member(row, "BLOCKED", "NO_APPLICABLE_CONTRACT");
        }
        return member(row, "INCLUDED", null);
    }

    private PayrollPopulationMember member(Row row, String decision, String reasonCode) {
        return new PayrollPopulationMember(row.employeeId(), row.matricule(), row.employeeName(),
                row.contractNumber(), row.regime(), decision, reasonCode);
    }

    private Period loadPeriod(int periodId, Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT code, date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?")) {
            statement.setInt(1, periodId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SirhBusinessException(SirhErrorCode.PAYROLL_PERIOD_NOT_FOUND,
                            "Période de paie introuvable : " + periodId + ".");
                }
                return new Period(periodId, result.getString("code"),
                        result.getDate("date_debut").toLocalDate(), result.getDate("date_fin").toLocalDate());
            }
        }
    }

    public record PayrollPopulation(
            int periodId, String periodCode, LocalDate startDate, LocalDate endDate,
            List<PayrollPopulationMember> members) {
        public List<Integer> includedEmployeeIds() {
            return members.stream().filter(member -> "INCLUDED".equals(member.decision()))
                    .map(PayrollPopulationMember::employeeId).toList();
        }
    }

    private record Period(int id, String code, LocalDate startDate, LocalDate endDate) {}
    private record Row(int employeeId, String matricule, String employeeName, Integer assignmentId,
                       Integer contractId, String contractNumber, String regime) {}
}
