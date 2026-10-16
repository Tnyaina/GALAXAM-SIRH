package mg.galaxam.sirh.paie.application;


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
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Résolution canonique et datée de la population d'une campagne de paie. */
@Service
public class PayrollPopulationResolver {

    @Autowired
    private EmploymentContextResolver employmentContextResolver = new EmploymentContextResolver();

    public PayrollPopulation resolve(int periodId, Connection connection) throws SQLException {
        Period period = loadPeriod(periodId, connection);
        String sql = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.est_actif, eac.id_affectation, "
                + "ct.id_contrat, ct.numero_contrat, rtc.libelle AS type_contrat, rtc.code AS type_contrat_code, "
                + "rs.libelle AS nom_site, tri.libelle AS regime, tri.code AS regime_code, "
                + "EXISTS(SELECT 1 FROM paie_stc stc WHERE stc.id_employe = e.id_employe AND stc.id_periode_paie = ? AND stc.statut IN ('VALIDE', 'REGLE')) AS stc_scelle "
                + "FROM employe e "
                + "LEFT JOIN employe_affectation_carriere eac ON eac.id_employe = e.id_employe "
                + "AND eac.date_debut <= ? AND (eac.date_fin IS NULL OR eac.date_fin >= ?) "
                + "LEFT JOIN contrat_travail ct ON ct.id_contrat = eac.id_contrat "
                + "AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?) "
                + "LEFT JOIN ref_type_contrat rtc ON rtc.id_type_contrat = ct.id_type_contrat "
                + "LEFT JOIN ref_site rs ON rs.id_site = eac.id_site "
                + "LEFT JOIN ref_type_intervenant tri ON tri.id_type_intervenant = ct.id_type_intervenant "
                + "WHERE e.est_actif = TRUE ORDER BY e.matricule, eac.date_debut";
        Map<Integer, List<Row>> rowsByEmployee = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, period.id());
            statement.setDate(2, Date.valueOf(period.endDate()));
            statement.setDate(3, Date.valueOf(period.startDate()));
            statement.setDate(4, Date.valueOf(period.endDate()));
            statement.setDate(5, Date.valueOf(period.startDate()));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    int employeeId = result.getInt("id_employe");
                    rowsByEmployee.computeIfAbsent(employeeId, ignored -> new ArrayList<>()).add(new Row(
                            employeeId, result.getString("matricule"),
                            (result.getString("nom") + " " + result.getString("prenom")).trim(),
                            (Integer) result.getObject("id_affectation"), (Integer) result.getObject("id_contrat"),
                            result.getString("numero_contrat"), result.getString("type_contrat"),
                            result.getString("nom_site"), result.getBoolean("est_actif"),
                            result.getString("regime"), result.getString("type_contrat_code"),
                            result.getString("regime_code"),
                            result.getBoolean("stc_scelle")));
                }
            }
        }

        List<PayrollPopulationMember> members = new ArrayList<>();
        for (List<Row> rows : rowsByEmployee.values()) {
            if (rows.stream().noneMatch(row -> row.assignmentId() != null)) {
                members.add(decide(rows));
                continue;
            }
            try {
                var coverage = employmentContextResolver.resolveCoverage(rows.get(0).employeeId(),
                        period.startDate(), period.endDate(), connection);
                Row last = rows.stream().filter(row -> row.assignmentId() != null
                        && row.assignmentId() == coverage.lastAssignmentId()).findFirst().orElseThrow();
                members.add(decide(List.of(last)));
            } catch (RuntimeException error) {
                members.add(member(rows.get(0), "BLOCKED", String.valueOf(error.getMessage()).startsWith("AMBIGUOUS_CONFIGURATION")
                        ? "AMBIGUOUS_ASSIGNMENT" : "MISSING_TEMPORAL_ASSIGNMENT"));
            }
        }
        return new PayrollPopulation(period.id(), period.code(), period.startDate(), period.endDate(), members);
    }

    private PayrollPopulationMember decide(List<Row> rows) {
        Row fallbackRow = rows.get(0);
        List<Row> applicableRows = rows.stream().filter(candidate -> candidate.assignmentId() != null).toList();
        if (applicableRows.isEmpty()) {
            return member(fallbackRow, "EXCLUDED", "NO_APPLICABLE_ASSIGNMENT");
        }
        if (applicableRows.size() > 1) {
            return member(fallbackRow, "BLOCKED", "AMBIGUOUS_ASSIGNMENT");
        }
        Row applicableRow = applicableRows.get(0);
        if (applicableRow.stcScelle()) {
            return member(applicableRow, "EXCLUDED", "STC_ALREADY_SETTLED");
        }
        if (applicableRow.contractId() == null || applicableRow.regime() == null) {
            return member(applicableRow, "BLOCKED", "NO_APPLICABLE_CONTRACT");
        }
        if ("PRESTATION".equalsIgnoreCase(applicableRow.contractTypeCode())
                || "CONSULTANT".equalsIgnoreCase(applicableRow.regimeCode())
                || "PRESTATAIRE".equalsIgnoreCase(applicableRow.regimeCode())
                || "ISI_5".equalsIgnoreCase(applicableRow.regimeCode())) {
            return member(applicableRow, "EXCLUDED", "INDEPENDENT_SERVICE_PROVIDER");
        }
        return member(applicableRow, "INCLUDED", null);
    }

    private PayrollPopulationMember member(Row row, String decision, String reasonCode) {
        return new PayrollPopulationMember(row.employeeId(), row.matricule(), row.employeeName(),
                row.typeContrat(), row.contractNumber(), row.nomSite(),
                row.employeeActive() ? "Actif" : "Inactif", row.regime(), decision, reasonCode);
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
        public PopulationSummary summary() {
            int included = (int) members.stream().filter(member -> "INCLUDED".equals(member.decision())).count();
            int excluded = (int) members.stream().filter(member -> "EXCLUDED".equals(member.decision())).count();
            int blocked = (int) members.stream().filter(member -> "BLOCKED".equals(member.decision())).count();
            return new PopulationSummary(members.size(), included, excluded, blocked);
        }

        public List<Integer> includedEmployeeIds() {
            return members.stream().filter(member -> "INCLUDED".equals(member.decision()))
                    .map(PayrollPopulationMember::employeeId).toList();
        }
    }

    public record PopulationSummary(int evaluated, int included, int excluded, int blocked) {}

    private record Period(int id, String code, LocalDate startDate, LocalDate endDate) {}
    private record Row(int employeeId, String matricule, String employeeName, Integer assignmentId,
                       Integer contractId, String contractNumber, String typeContrat, String nomSite,
                       boolean employeeActive, String regime, String contractTypeCode, String regimeCode,
                       boolean stcScelle) {}
}
