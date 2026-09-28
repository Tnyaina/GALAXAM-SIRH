package mg.galaxam.sirh.conge.application;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** Grand récapitulatif DAF : situation du ledger à une date, sans recalcul des droits dans Excel. */
@Service
public class CongeGrandRecapExcelExportService {
    private static final String TEMPLATE = "/templates/annuel/GRAND_RECAP_CONGES_VALORISES.xlsx";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final DataSource dataSource;

    public CongeGrandRecapExcelExportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public byte[] generer(LocalDate dateDebut, LocalDate dateFin) throws Exception {
        validerPeriode(dateDebut, dateFin);
        try (Connection connection = dataSource.getConnection()) {
            return generer(dateDebut, dateFin, connection);
        }
    }

    public byte[] generer(LocalDate dateDebut, LocalDate dateFin, Connection connection) throws Exception {
        validerPeriode(dateDebut, dateFin);
        List<Ligne> lignes = chargerLignes(dateDebut, dateFin, connection);
        try (InputStream input = getClass().getResourceAsStream(TEMPLATE)) {
            if (input == null) throw new IllegalStateException("Canevas de récapitulatif des congés introuvable : " + TEMPLATE);
            try (XSSFWorkbook workbook = new XSSFWorkbook(input)) {
                Sheet sheet = workbook.getSheetAt(0);
                int firstData = 4; // ligne 5 Excel
                int lastData = firstData + lignes.size() - 1;
                int totalRow = Math.max(firstData, lastData + 1);
                Row prototype = sheet.getRow(firstData);
                CellStyle[] styles = new CellStyle[17];
                for (int column = 0; column < styles.length; column++) styles[column] = prototype.getCell(column).getCellStyle();

                // Le canevas prévoit 100 lignes. Adapter sa taille au nombre réel de salariés.
                sheet.shiftRows(104, 106, totalRow - 104);
                for (int index = 0; index < lignes.size(); index++) {
                    Row row = sheet.getRow(firstData + index);
                    if (row == null) row = sheet.createRow(firstData + index);
                    Ligne ligne = lignes.get(index);
                    setText(row, 0, ligne.matricule(), styles[0]);
                    setText(row, 1, ligne.nomComplet(), styles[1]);
                    setText(row, 2, ligne.filiale(), styles[2]);
                    setText(row, 3, ligne.site(), styles[3]);
                    setText(row, 4, ligne.poste(), styles[4]);
                    for (int column = 5; column <= 12; column++) setNumber(row, column, ligne.jours()[column - 5], styles[column]);
                    setNumber(row, 13, ligne.taux(), styles[13]);
                    setNumber(row, 14, ligne.valeur(), styles[14]);
                    setText(row, 15, ligne.periode(), styles[15]);
                    setText(row, 16, ligne.statut(), styles[16]);
                }

                Row total = sheet.getRow(totalRow);
                if (total == null) total = sheet.createRow(totalRow);
                total.getCell(0).setCellValue("TOTAL GÉNÉRAL — " + lignes.size() + " salarié(s)");
                for (int column = 5; column <= 12; column++) {
                    if (lignes.isEmpty()) total.getCell(column).setCellValue(0);
                    else total.getCell(column).setCellFormula("SUM(" + (char) ('A' + column) + "5:"
                            + (char) ('A' + column) + (lastData + 1) + ")");
                }
                long sansTaux = lignes.stream().filter(l -> l.taux() == null && l.jours()[7].signum() != 0).count();
                // Un total en Ariary partiel serait trompeur si un salarié n'a pas de taux historisé.
                if (lignes.isEmpty()) total.getCell(14).setCellValue(0);
                else if (sansTaux == 0) total.getCell(14).setCellFormula("SUM(O5:O" + (lastData + 1) + ")");
                else total.getCell(14).setBlank();
                sheet.getRow(1).getCell(0).setCellValue("Période du " + dateDebut.format(DATE_FORMAT)
                        + " au " + dateFin.format(DATE_FORMAT) + "  |  Situation au " + dateFin.format(DATE_FORMAT)
                        + "  |  " + sansTaux + " solde(s) non nuls sans TCONGE applicable");
                if (sansTaux > 0) sheet.getRow(totalRow + 2).getCell(0).setCellValue(
                        "Valorisation totale indisponible : " + sansTaux + " salarié(s) sans TCONGE applicable à la date de situation."
                                + " Les soldes en jours restent consultables.");
                sheet.createFreezePane(5, 4);
                sheet.setAutoFilter(org.apache.poi.ss.util.CellRangeAddress.valueOf("A4:Q" + Math.max(4, lastData + 1)));
                sheet.setPrintGridlines(false);
                Sheet notice = workbook.getSheetAt(1);
                notice.getRow(4).getCell(1).setCellValue("Solde d'ouverture avant le "
                        + dateDebut.format(DATE_FORMAT) + ", mouvements du " + dateDebut.format(DATE_FORMAT)
                        + " au " + dateFin.format(DATE_FORMAT) + ", situation arrêtée au "
                        + dateFin.format(DATE_FORMAT) + ".");
                workbook.setForceFormulaRecalculation(true);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    private static void validerPeriode(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null || dateFin.isBefore(dateDebut) || dateFin.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La période doit avoir un début, une fin postérieure au début et ne pas dépasser aujourd'hui.");
        }
    }

    private List<Ligne> chargerLignes(LocalDate dateDebut, LocalDate dateFin, Connection connection) throws SQLException {
        String sql = """
                SELECT e.matricule, e.nom, e.prenom,
                       f.raison_sociale AS filiale, s.libelle AS site, fn.libelle AS poste,
                       COALESCE(m.ouverture, 0) AS ouverture,
                       COALESCE(m.acquis, 0) AS acquis,
                       COALESCE(m.bonification, 0) AS bonification,
                       COALESCE(m.ajustement, 0) AS ajustement,
                       COALESCE(m.pris, 0) AS pris,
                       COALESCE(m.expiration, 0) AS expiration,
                       COALESCE(m.annulation, 0) AS annulation,
                       COALESCE(m.solde, 0) AS solde,
                       t.taux_journalier, t.taux_count,
                       p.date_debut AS periode_debut, p.date_fin AS periode_fin, p.statut AS periode_statut
                FROM employe e
                LEFT JOIN LATERAL (
                    SELECT SUM(nb_jours) FILTER (WHERE date_mouvement < ?) AS ouverture,
                           SUM(nb_jours) FILTER (WHERE date_mouvement >= ? AND type_mouvement = 'ACQUISITION') AS acquis,
                           SUM(nb_jours) FILTER (WHERE date_mouvement >= ? AND type_mouvement = 'BONIFICATION') AS bonification,
                           SUM(nb_jours) FILTER (WHERE date_mouvement >= ? AND type_mouvement = 'AJUSTEMENT') AS ajustement,
                           SUM(-nb_jours) FILTER (WHERE date_mouvement >= ? AND type_mouvement = 'PRISE') AS pris,
                           SUM(-nb_jours) FILTER (WHERE date_mouvement >= ? AND type_mouvement = 'EXPIRATION') AS expiration,
                           SUM(nb_jours) FILTER (WHERE date_mouvement >= ? AND type_mouvement = 'ANNULATION') AS annulation,
                           SUM(nb_jours) AS solde
                    FROM mouvement_solde_conge WHERE id_employe = e.id_employe AND date_mouvement <= ?
                ) m ON true
                LEFT JOIN LATERAL (
                    SELECT a.id_site, a.id_fonction, ct.id_filiale
                    FROM employe_affectation_carriere a
                    LEFT JOIN contrat_travail ct ON ct.id_contrat = a.id_contrat
                    WHERE a.id_employe = e.id_employe AND a.date_debut <= ?
                    ORDER BY a.date_debut DESC, a.id_affectation DESC LIMIT 1
                ) a ON true
                LEFT JOIN ref_filiale f ON f.id_filiale = a.id_filiale
                LEFT JOIN ref_site s ON s.id_site = a.id_site
                LEFT JOIN ref_fonction fn ON fn.id_fonction = a.id_fonction
                LEFT JOIN LATERAL (
                    SELECT taux_journalier, COUNT(*) OVER () AS taux_count FROM conge_taux_applicable
                    WHERE id_employe = e.id_employe AND date_effet <= ? AND (date_fin IS NULL OR date_fin > ?)
                    ORDER BY date_effet DESC, id_taux_applicable DESC LIMIT 1
                ) t ON true
                LEFT JOIN LATERAL (
                    SELECT date_debut, date_fin, statut FROM conge_periode_reference
                    WHERE id_employe = e.id_employe AND date_debut <= ? AND date_fin >= ?
                    ORDER BY date_debut DESC, id_periode_reference DESC LIMIT 1
                ) p ON true
                WHERE (e.date_declaration IS NULL OR e.date_declaration <= ?)
                  AND (e.est_actif = true OR EXISTS (
                      SELECT 1 FROM mouvement_solde_conge historic
                      WHERE historic.id_employe = e.id_employe AND historic.date_mouvement <= ?))
                ORDER BY e.matricule
                """;
        List<Ligne> lignes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (int i = 0; i < 7; i++) statement.setDate(parameter++, Date.valueOf(dateDebut));
            for (int i = 0; i < 8; i++) statement.setDate(parameter++, Date.valueOf(dateFin));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("taux_count") > 1) {
                        throw new IllegalStateException("TCONGE_APPLICABLE_AMBIGUOUS: plusieurs taux applicables pour le matricule "
                                + rs.getString("matricule") + " à la date " + dateFin);
                    }
                    BigDecimal ouverture = rs.getBigDecimal("ouverture");
                    BigDecimal acquis = rs.getBigDecimal("acquis");
                    BigDecimal bonification = rs.getBigDecimal("bonification");
                    BigDecimal ajustement = rs.getBigDecimal("ajustement");
                    BigDecimal pris = rs.getBigDecimal("pris");
                    BigDecimal expiration = rs.getBigDecimal("expiration");
                    BigDecimal annulation = rs.getBigDecimal("annulation");
                    BigDecimal solde = rs.getBigDecimal("solde");
                    BigDecimal taux = rs.getBigDecimal("taux_journalier");
                    String periode = rs.getDate("periode_debut") == null ? "—"
                            : rs.getDate("periode_debut").toLocalDate().format(DATE_FORMAT) + " – "
                            + rs.getDate("periode_fin").toLocalDate().format(DATE_FORMAT);
                    lignes.add(new Ligne(rs.getString("matricule"),
                            (rs.getString("nom") + " " + rs.getString("prenom")).trim(),
                            textOrDash(rs.getString("filiale")), textOrDash(rs.getString("site")),
                            textOrDash(rs.getString("poste")),
                            new BigDecimal[]{ouverture, acquis, bonification, ajustement, pris, expiration, annulation, solde},
                            taux, solde.signum() == 0 ? BigDecimal.ZERO
                                    : taux == null ? null : solde.multiply(taux).setScale(2, RoundingMode.HALF_UP),
                            periode, textOrDash(rs.getString("periode_statut"))));
                }
            }
        }
        return lignes;
    }

    private static String textOrDash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static void setText(Row row, int index, String value, CellStyle style) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellStyle(style);
        cell.setCellValue(value);
    }
    private static void setNumber(Row row, int index, BigDecimal value, CellStyle style) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellStyle(style);
        if (value == null) cell.setBlank(); else cell.setCellValue(value.doubleValue());
    }

    private record Ligne(String matricule, String nomComplet, String filiale, String site, String poste,
                         BigDecimal[] jours, BigDecimal taux, BigDecimal valeur, String periode, String statut) {}
}
