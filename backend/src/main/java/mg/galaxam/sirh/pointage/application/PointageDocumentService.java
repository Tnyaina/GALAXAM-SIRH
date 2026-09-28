package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import mg.galaxam.sirh.common.exception.*;

@Service
public class PointageDocumentService {
    private final DataSource dataSource;
    private final PointageEmployeService employeService;
    public PointageDocumentService(DataSource dataSource, PointageEmployeService employeService) {
        this.dataSource = dataSource; this.employeService = employeService;
    }
    public PointageDocumentData preparer(int employeeId, String date, String period, Integer periodId) throws SQLException {
        var fiche = employeService.getFichePointageEmploye(employeeId, date, "periode", periodId);
        var data = PointageDocumentData.from(fiche);
        boolean frozen = fiche.getIdPeriode() != null && fiche.getStatutPeriode() != null
                && Set.of("VALIDEE", "PAIE_VALIDEE", "CLOTUREE").contains(fiche.getStatutPeriode());
        try (Connection c = dataSource.getConnection()) {
            if (!frozen) return withAdjustments(data, readAdjustments(c, fiche, null));
            String sql = "SELECT * FROM pointage_cumul_periode WHERE id_employe = ? AND id_periode_paie = ? "
                    + "AND statut_snapshot IN ('GELE','CONSOMME') ORDER BY version_snapshot DESC LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, employeeId); ps.setInt(2, fiche.getIdPeriode());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SirhBusinessException(SirhErrorCode.POINTAGE_EXPORT_SNAPSHOT_MISSING);
                    int id = rs.getInt("id_cumul"), version = rs.getInt("version_snapshot");
                    verifyJournal(c, fiche, id);
                    String[] columns = {"minutes_deficit_hebdo", "minutes_hs30_hebdo", "minutes_hs50_hebdo", "minutes_ajustement_periode", "minutes_solde_periode", "minutes_absence_finale", "minutes_hs30_finales"};
                    List<BigDecimal> balance = new ArrayList<>();
                    for (String column : columns) balance.add(PointageDocumentData.hours((Integer)rs.getObject(column)));
                    Integer final50 = (Integer)rs.getObject("minutes_hs50_finales");
                    balance.add(final50 == null ? rs.getBigDecimal("heures_hs_50") : PointageDocumentData.hours(final50));
                    balance.add(rs.getBigDecimal("heures_nuit_total")); balance.add(rs.getBigDecimal("heures_dimanche_total")); balance.add(rs.getBigDecimal("heures_ferie_total"));
                    var amounts = new LinkedHashMap<String, PointageDocumentData.Indemnite>();
                    for (var allowance : data.indemnites()) amounts.put(allowance.code(), new PointageDocumentData.Indemnite(allowance.code(), allowance.libelle(), allowance.periodicite(), allowance.quantite(), BigDecimal.ZERO));
                    try (PreparedStatement ap = c.prepareStatement("SELECT code, libelle, montant FROM pointage_snapshot_indemnite WHERE id_cumul = ? ORDER BY code")) {
                        ap.setInt(1,id);
                        try (ResultSet ar = ap.executeQuery()) {
                            while (ar.next()) {
                                String code = ar.getString("code"); var old = amounts.get(code);
                                amounts.put(code, new PointageDocumentData.Indemnite(code, ar.getString("libelle"), old == null ? null : old.periodicite(), old == null ? null : old.quantite(), ar.getBigDecimal("montant")));
                            }
                        }
                    }
                    return new PointageDocumentData(fiche, data.edition(), "SYNTHÈSE FIGÉE v" + version + " · " + fiche.getStatutPeriode(), version,
                            List.copyOf(amounts.values()), readAdjustments(c, fiche, id), balance);
                }
            }
        }
    }
    private PointageDocumentData withAdjustments(PointageDocumentData d, List<PointageDocumentData.Ajustement> adjustments) {
        return new PointageDocumentData(d.fiche(),d.edition(),d.provenance(),d.versionSnapshot(),d.indemnites(),adjustments,d.bilan());
    }
    private List<PointageDocumentData.Ajustement> readAdjustments(Connection c, FichePointageEmployeDTO f, Integer snapshotId) throws SQLException {
        if (f.getIdPeriode() == null) return List.of();
        String sql = snapshotId == null
                ? "SELECT a.minutes_signees, a.motif, a.date_saisie, u.identifiant, a.statut FROM pointage_ajustement_periode a LEFT JOIN utilisateur u ON u.id_utilisateur = a.saisi_par WHERE a.id_employe = ? AND a.id_periode_paie = ? ORDER BY a.id_ajustement"
                : "SELECT s.minutes_signees, s.motif, a.date_saisie, u.identifiant, 'VALIDE' AS statut FROM pointage_snapshot_ajustement s JOIN pointage_ajustement_periode a ON a.id_ajustement = s.id_ajustement LEFT JOIN utilisateur u ON u.id_utilisateur = a.saisi_par WHERE s.id_cumul = ? ORDER BY s.id_ajustement";
        var result = new ArrayList<PointageDocumentData.Ajustement>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (snapshotId != null) ps.setInt(1, snapshotId); else { ps.setInt(1,f.getIdEmploye()); ps.setInt(2,f.getIdPeriode()); }
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(new PointageDocumentData.Ajustement(rs.getInt(1),rs.getString(2),rs.getTimestamp(3).toLocalDateTime().toString(),rs.getString(4),rs.getString(5))); }
        }
        return result;
    }
    private void verifyJournal(Connection c, FichePointageEmployeDTO fiche, int snapshotId) throws SQLException {
        var versions = new HashMap<Integer,Integer>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id_pointage, version_pointage FROM pointage_snapshot_jour WHERE id_cumul = ?")) {
            ps.setInt(1,snapshotId); try (ResultSet rs=ps.executeQuery()) { while(rs.next()) versions.put(rs.getInt(1),rs.getInt(2)); }
        }
        Set<Integer> seen = new HashSet<>();
        for (var day : PointageDocumentData.days(fiche)) {
            if (day.getIdPointage() != null && day.getIdPointage() > 0) {
                seen.add(day.getIdPointage());
                if (!Objects.equals(day.getVersion(),versions.get(day.getIdPointage()))) throw new SirhBusinessException(SirhErrorCode.POINTAGE_EXPORT_SNAPSHOT_MISMATCH);
            }
        }
        if (!seen.equals(versions.keySet())) throw new SirhBusinessException(SirhErrorCode.POINTAGE_EXPORT_SNAPSHOT_MISMATCH);
    }
}
