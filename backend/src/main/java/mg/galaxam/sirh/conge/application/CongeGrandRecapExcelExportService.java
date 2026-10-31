package mg.galaxam.sirh.conge.application;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
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
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
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
    private final CongeLotValorisationService lots;
    private final EmploymentContextResolver employmentContextResolver;

    public CongeGrandRecapExcelExportService(DataSource dataSource, CongeLotValorisationService lots,
                                             EmploymentContextResolver employmentContextResolver) {
        this.dataSource = dataSource;
        this.lots = lots;
        this.employmentContextResolver = employmentContextResolver;
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
                CellStyle tauxStyle = workbook.createCellStyle();
                tauxStyle.cloneStyleFrom(styles[13]);
                tauxStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.######"));

                // Le canevas prévoit 100 lignes. Adapter sa taille au nombre réel de salariés.
                sheet.shiftRows(104, 106, totalRow - 104);
                for (int index = 0; index < lignes.size(); index++) {
                    Row row = sheet.getRow(firstData + index);
                    if (row == null) row = sheet.createRow(firstData + index);
                    Ligne ligne = lignes.get(index);
                    // Colonnes A-E : identité
                    setText(row, 0, ligne.matricule(), styles[0]);
                    setText(row, 1, ligne.nomComplet(), styles[1]);
                    setText(row, 2, ligne.filiale(), styles[2]);
                    setText(row, 3, ligne.site(), styles[3]);
                    setText(row, 4, ligne.poste(), styles[4]);
                    // Colonnes F-M (5-12) : jours mouvements
                    for (int column = 5; column <= 12; column++) setNumber(row, column, ligne.jours()[column - 5], styles[Math.min(column, styles.length - 1)]);
                    // Col N(13) : TCONGE applicable
                    setNumber(row, 13, ligne.taux(), tauxStyle);
                    // Col O(14) : Valeur du solde (FIFO historisée)
                    setNumber(row, 14, ligne.valeur(), styles[Math.min(14, styles.length - 1)]);
                    // REC #5 — Col P(15) : Cumul valorisation acquise sur la période
                    setNumber(row, 15, ligne.cumulValoAcquise(), styles[Math.min(15, styles.length - 1)]);
                    // REC #4/#7 — Col Q(16) : Solde × TCONGE (estimation simple DAF)
                    BigDecimal soldeXTaux = (ligne.jours()[7] != null && ligne.taux() != null
                            && ligne.taux().compareTo(BigDecimal.ZERO) > 0)
                            ? ligne.jours()[7].multiply(ligne.taux()) : null;
                    setNumber(row, 16, soldeXTaux, styles[Math.min(16, styles.length - 1)]);
                    // Col R(17) : Période de référence
                    setText(row, 17, ligne.periode(), styles[Math.min(17, styles.length - 1)]);
                    // Col S(18) : Statut période
                    setText(row, 18, ligne.statut(), styles[Math.min(18, styles.length - 1)]);
                }

                Row total = sheet.getRow(totalRow);
                if (total == null) total = sheet.createRow(totalRow);
                Cell c0 = total.getCell(0);
                if (c0 == null) c0 = total.createCell(0);
                c0.setCellValue("TOTAL GÉNÉRAL — " + lignes.size() + " salarié(s)");
                // Colonnes jours F-M (5-12) : SUM
                for (int column = 5; column <= 12; column++) {
                    Cell c = total.getCell(column);
                    if (c == null) c = total.createCell(column);
                    if (lignes.isEmpty()) c.setCellValue(0);
                    else c.setCellFormula("SUM(" + (char) ('A' + column) + "5:"
                            + (char) ('A' + column) + (lastData + 1) + ")");
                }
                // REC #7 : TCONGE — pas de moyenne globale significative, libellé explicite
                Cell tcongeTotal = total.getCell(13);
                if (tcongeTotal == null) tcongeTotal = total.createCell(13);
                tcongeTotal.setCellValue("— (voir détail)");
                // Col O : valeur stock FIFO total
                Cell oTotal = total.getCell(14);
                if (oTotal == null) oTotal = total.createCell(14);
                if (lignes.isEmpty()) oTotal.setCellValue(0);
                else oTotal.setCellFormula("SUM(O5:O" + (lastData + 1) + ")");
                // REC #5 — Col P : cumul valo acquise total
                Cell pTotal = total.getCell(15);
                if (pTotal == null) pTotal = total.createCell(15);
                if (lignes.isEmpty()) pTotal.setCellValue(0);
                else pTotal.setCellFormula("SUM(P5:P" + (lastData + 1) + ")");
                // REC #4 — Col Q : solde×TCONGE total
                Cell qTotal = total.getCell(16);
                if (qTotal == null) qTotal = total.createCell(16);
                if (lignes.isEmpty()) qTotal.setCellValue(0);
                else qTotal.setCellFormula("SUM(Q5:Q" + (lastData + 1) + ")");

                Row subTitleRow = sheet.getRow(1);
                if (subTitleRow == null) subTitleRow = sheet.createRow(1);
                Cell subTitleCell = subTitleRow.getCell(0);
                if (subTitleCell == null) subTitleCell = subTitleRow.createCell(0);
                subTitleCell.setBlank();
                subTitleCell.setCellValue("Période du " + dateDebut.format(DATE_FORMAT)
                        + " au " + dateFin.format(DATE_FORMAT) + "  |  Situation au " + dateFin.format(DATE_FORMAT)
                        + "  |  Solde valorisé selon les lots historiques (col O) + estimation Solde×TCONGE (col Q)");
                sheet.createFreezePane(5, 4);
                sheet.setAutoFilter(org.apache.poi.ss.util.CellRangeAddress.valueOf("A3:S" + Math.max(3, lastData + 1)));
                sheet.setPrintGridlines(false);
                Sheet notice = workbook.getSheetAt(1);
                Row r6 = notice.getRow(6);
                if (r6 == null) r6 = notice.createRow(6);
                Cell c6_1 = r6.getCell(1);
                if (c6_1 == null) c6_1 = r6.createCell(1);
                c6_1.setBlank();
                c6_1.setCellValue(
                        "Col O = valeur FIFO historisée (lots restants après prises). Col P = cumul valo acquise sur la période. Col Q = Solde × TCONGE (estimation simple pour cross-check DAF).");
                Row r4 = notice.getRow(4);
                if (r4 == null) r4 = notice.createRow(4);
                Cell c4_1 = r4.getCell(1);
                if (c4_1 == null) c4_1 = r4.createCell(1);
                c4_1.setBlank();
                c4_1.setCellValue("Solde d'ouverture avant le "
                        + dateDebut.format(DATE_FORMAT) + ", mouvements du " + dateDebut.format(DATE_FORMAT)
                        + " au " + dateFin.format(DATE_FORMAT) + ", situation arrêtée au "
                        + dateFin.format(DATE_FORMAT) + ".");
                Sheet ventilation = workbook.createSheet("Droits et consommations");
                Row enteteLots = ventilation.createRow(0);
                String[] titres = {"Matricule", "Salarié", "Origine", "ID droit", "Date", "Jours restants",
                        "Valeur restante (Ar)", "ID prise", "Jours consommés", "Valeur consommée (Ar)", "TCONGE historique (Ar/j)"};
                for (int i = 0; i < titres.length; i++) enteteLots.createCell(i).setCellValue(titres[i]);
                int ligneLot = 1;
                for (Ligne ligne : lignes) {
                    // chargerLignes a déjà calculé la situation FIFO pour la valeur de la colonne O.
                    // Réutiliser ce même instantané évite de relire et recalculer tout le ledger.
                    var situation = ligne.situation();
                    for (var lot : situation.lots()) {
                        Row row = ventilation.createRow(ligneLot++);
                        row.createCell(0).setCellValue(ligne.matricule());
                        row.createCell(1).setCellValue(ligne.nomComplet());
                        row.createCell(2).setCellValue(lot.origine());
                        row.createCell(3).setCellValue(lot.idSource());
                        row.createCell(4).setCellValue(lot.dateAcquisition().toString());
                        row.createCell(5).setCellValue(lot.jours().doubleValue());
                        row.createCell(6).setCellValue(lot.valeur().doubleValue());
                    }
                    for (var prise : situation.imputations()) {
                        Row row = ventilation.createRow(ligneLot++);
                        row.createCell(0).setCellValue(ligne.matricule());
                        row.createCell(1).setCellValue(ligne.nomComplet());
                        row.createCell(2).setCellValue(prise.origine());
                        row.createCell(3).setCellValue(prise.idSource());
                        if (prise.idDemande() != null) row.createCell(7).setCellValue(prise.idDemande());
                        row.createCell(8).setCellValue(prise.jours().doubleValue());
                        row.createCell(9).setCellValue(prise.valeur().doubleValue());
                        Cell tauxCell = row.createCell(10);
                        tauxCell.setCellValue(prise.tauxHistorique().doubleValue());
                        tauxCell.setCellStyle(tauxStyle);
                    }
                }
                for (int i = 0; i < titres.length; i++) ventilation.autoSizeColumn(i);
                // Le modèle métier FIFO n'est plus nécessaire : toutes ses données sont
                // maintenant matérialisées dans les deux feuilles du classeur. Libérer ces
                // objets avant la sérialisation évite de les garder en mémoire en parallèle
                // avec le buffer XLSX final.
                lignes.clear();
                workbook.setForceFormulaRecalculation(true);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    private static void validerPeriode(LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut == null || dateFin == null || dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("La période doit avoir une date de début et une date de fin valide postérieure ou égale au début.");
        }
    }

    private List<Ligne> chargerLignes(LocalDate dateDebut, LocalDate dateFin, Connection connection) throws SQLException {
        String sql = """
                SELECT e.id_employe, e.matricule, e.nom, e.prenom,
                       COALESCE(m.ouverture, 0) AS ouverture,
                       COALESCE(m.acquis, 0) AS acquis,
                       COALESCE(m.bonification, 0) AS bonification,
                       COALESCE(m.ajustement, 0) AS ajustement,
                       COALESCE(m.pris, 0) AS pris,
                       COALESCE(m.expiration, 0) AS expiration,
                       COALESCE(m.annulation, 0) AS annulation,
                       COALESCE(m.solde, 0) AS solde,
                       t.taux_journalier, t.taux_count,
                       p.date_debut AS periode_debut, p.date_fin AS periode_fin, p.statut AS periode_statut,
                       p.periode_count,
                       COALESCE(jd.cumul_valo, 0) AS cumul_valo_acquise
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
                    SELECT taux_journalier, COUNT(*) OVER () AS taux_count FROM conge_taux_applicable
                    WHERE id_employe = e.id_employe AND date_effet <= ? AND (date_fin IS NULL OR date_fin > ?)
                    ORDER BY date_effet DESC, id_taux_applicable DESC LIMIT 1
                ) t ON true
                LEFT JOIN LATERAL (
                    SELECT date_debut, date_fin, statut, COUNT(*) OVER () AS periode_count
                    FROM conge_periode_reference
                    WHERE id_employe = e.id_employe AND date_debut <= ? AND date_fin >= ?
                    ORDER BY date_debut DESC, id_periode_reference DESC LIMIT 1
                ) p ON true
                LEFT JOIN LATERAL (
                    SELECT COALESCE(SUM(valorisation_mensuelle), 0) AS cumul_valo
                    FROM conge_journal_droit
                    WHERE id_employe = e.id_employe
                      AND EXISTS (
                        SELECT 1 FROM periode_paie pp
                        WHERE pp.id_periode_paie = conge_journal_droit.id_periode_paie
                          AND pp.date_debut >= ? AND pp.date_fin <= ?
                      )
                ) jd ON true
                WHERE (e.date_declaration IS NULL OR e.date_declaration <= ?)
                  AND (e.est_actif = true OR EXISTS (
                      SELECT 1 FROM mouvement_solde_conge historic
                      WHERE historic.id_employe = e.id_employe AND historic.date_mouvement <= ?))
                ORDER BY e.matricule
                """;
        List<RawLigne> rawRows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (int i = 0; i < 7; i++) statement.setDate(parameter++, Date.valueOf(dateDebut));
            for (int i = 0; i < 7; i++) statement.setDate(parameter++, Date.valueOf(dateFin));
            // Paramètres supplémentaires pour jd (cumul valo acquise sur la période) : pp.date_debut >= ? AND pp.date_fin <= ?
            statement.setDate(parameter++, Date.valueOf(dateDebut));
            statement.setDate(parameter++, Date.valueOf(dateFin));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("taux_count") > 1) {
                        throw new IllegalStateException("TCONGE_APPLICABLE_AMBIGUOUS: plusieurs taux applicables pour le matricule "
                                + rs.getString("matricule") + " à la date " + dateFin);
                    }
                    if (rs.getInt("periode_count") > 1) {
                        throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: plusieurs périodes de référence congés pour le matricule "
                                + rs.getString("matricule") + " à la date " + dateFin);
                    }
                    java.sql.Date periodeDebut = rs.getDate("periode_debut");
                    java.sql.Date periodeFin = rs.getDate("periode_fin");
                    rawRows.add(new RawLigne(rs.getInt("id_employe"), rs.getString("matricule"),
                            rs.getString("nom"), rs.getString("prenom"),
                            rs.getBigDecimal("ouverture"), rs.getBigDecimal("acquis"),
                            rs.getBigDecimal("bonification"), rs.getBigDecimal("ajustement"),
                            rs.getBigDecimal("pris"), rs.getBigDecimal("expiration"),
                            rs.getBigDecimal("annulation"), rs.getBigDecimal("solde"),
                            rs.getBigDecimal("taux_journalier"), rs.getBigDecimal("cumul_valo_acquise"),
                            periodeDebut == null ? null : periodeDebut.toLocalDate(),
                            periodeFin == null ? null : periodeFin.toLocalDate(), rs.getString("periode_statut")));
                }
            }
        }
        List<Integer> employeeIds = rawRows.stream().map(RawLigne::idEmploye).toList();
        var situations = lots.situations(employeeIds, dateFin, connection);
        var labels = employmentContextResolver.resolveLabels(employeeIds, dateFin, connection);
        List<Ligne> lignes = new ArrayList<>(rawRows.size());
        for (RawLigne raw : rawRows) {
            var situation = situations.get(raw.idEmploye());
            if (situation == null) {
                throw new IllegalStateException("CONGE_LEDGER_SITUATION_MISSING: employé " + raw.idEmploye());
            }
            var contexte = labels.get(raw.idEmploye());
            String periode = raw.periodeDebut() == null ? "—"
                    : raw.periodeDebut().format(DATE_FORMAT) + " – " + raw.periodeFin().format(DATE_FORMAT);
            lignes.add(new Ligne(raw.idEmploye(), raw.matricule(),
                    (raw.nom() + " " + raw.prenom()).trim(),
                    contexte == null ? "—" : textOrDash(contexte.filialeNom()),
                    contexte == null ? "—" : textOrDash(contexte.nomSite()),
                    contexte == null ? "—" : textOrDash(contexte.fonctionNom()),
                    new BigDecimal[]{raw.ouverture(), raw.acquis(), raw.bonification(), raw.ajustement(),
                            raw.pris(), raw.expiration(), raw.annulation(), raw.solde()},
                    raw.taux(), situation.soldeValorisation(), raw.cumulValoAcquise(), situation,
                    periode, textOrDash(raw.periodeStatut())));
        }
        return lignes;
    }

    private static String textOrDash(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static void setText(Row row, int index, String value, CellStyle style) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellStyle(style);
        cell.setBlank();
        cell.setCellValue(value != null ? value : "");
    }
    private static void setNumber(Row row, int index, BigDecimal value, CellStyle style) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellStyle(style);
        if (value == null) cell.setBlank(); else cell.setCellValue(value.doubleValue());
    }

    private record RawLigne(int idEmploye, String matricule, String nom, String prenom,
                            BigDecimal ouverture, BigDecimal acquis, BigDecimal bonification, BigDecimal ajustement,
                            BigDecimal pris, BigDecimal expiration, BigDecimal annulation, BigDecimal solde,
                            BigDecimal taux, BigDecimal cumulValoAcquise, LocalDate periodeDebut,
                            LocalDate periodeFin, String periodeStatut) {}

    private record Ligne(int idEmploye, String matricule, String nomComplet, String filiale, String site, String poste,
                         BigDecimal[] jours, BigDecimal taux, BigDecimal valeur, BigDecimal cumulValoAcquise,
                         mg.galaxam.sirh.conge.domain.CongeLotValorisationEngine.Situation situation,
                         String periode, String statut) {}
}
