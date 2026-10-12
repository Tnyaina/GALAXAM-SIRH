package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Lecture seule du modèle Pointage. L'enregistrement reste dans PointageService. */
@Service
public class PointageImportService {
    private static final List<String> FIXED = List.of("Date", "Entrée AM", "Sortie AM", "Entrée PM", "Sortie PM");
    private static final List<String> LEGACY_FIXED = List.of("Date", "Entrée matin", "Sortie matin", "Entrée après-midi", "Sortie finale");
    private static final String EVENT_COLUMN = "Événement";
    private static final String LEGACY_EVENT_COLUMN = "Code";
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private final PointageEmployeService employes;
    private final PointageService pointages;
    private final DataSource dataSource;

    public record ImportLine(int ligne, String date, String eventCode, String decision, boolean selectedByDefault,
                             boolean conflict, List<String> errors, List<String> warnings,
                             SaisiePointageJourDTO proposed, PointageJourDTO existing,
                             List<Map<String, Object>> allowances, Map<String, Object> allowanceOverrides) {}
    public record ImportPreview(List<ImportLine> lines, List<String> columns) {}

    public PointageImportService(PointageEmployeService employes, PointageService pointages, DataSource dataSource) {
        this.employes = employes;
        this.pointages = pointages;
        this.dataSource = dataSource;
    }

    private FichePointageEmployeDTO sheet(int employeeId, int periodId) {
        FichePointageEmployeDTO fiche = employes.getFichePointageEmploye(employeeId, null, "periode", periodId);
        if (fiche.getIdPeriode() == null || fiche.getPointagesData() == null)
            throw new IllegalArgumentException("Employé ou période de pointage introuvable.");
        return fiche;
    }

    @SuppressWarnings("unchecked")
    private DayContext context(int employeeId, PointageJourDTO day) throws Exception {
        if (day.getIdSite() == null || day.getIdSite() <= 0)
            throw new IllegalArgumentException("Site de pointage introuvable pour le " + day.getDateComplete());
        Map<String, Object> grid = pointages.getGrilleSaisie(day.getIdSite(), day.getDateComplete());
        List<Map<String, Object>> allowances = (List<Map<String, Object>>) grid.getOrDefault("siteIndemnites", List.of());
        List<SaisiePointageJourDTO> people = (List<SaisiePointageJourDTO>) grid.getOrDefault("grilleData", List.of());
        SaisiePointageJourDTO employee = people.stream()
                .filter(person -> Objects.equals(person.getIdEmploye(), employeeId)).findFirst().orElse(null);
        if (employee == null) throw new IllegalArgumentException("Employé non présent sur le site au " + day.getDateComplete());
        return new DayContext(employee, allowances);
    }

    private record DayContext(SaisiePointageJourDTO employee, List<Map<String, Object>> allowances) {}

    public byte[] template(int employeeId, int periodId) throws Exception {
        FichePointageEmployeDTO fiche = sheet(employeeId, periodId);
        SortedMap<String, Boolean> union = new TreeMap<>();
        Set<Integer> sites = new HashSet<>();
        fiche.getPointagesData().forEach(day -> { if (day.getIdSite() != null) sites.add(day.getIdSite()); });
        if (!sites.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(sites.size(), "?"));
            String sql = "SELECT DISTINCT i.code, COALESCE(i.fractionnable, FALSE) AS fractionnable "
                    + "FROM site_indemnite_config c JOIN ref_indemnite i ON i.id_indemnite = c.id_indemnite "
                    + "WHERE c.id_site IN (" + placeholders + ") AND c.est_actif = TRUE AND i.est_actif = TRUE "
                    + "AND c.periodicite <> 'FIXE_MENSUEL' AND c.date_debut <= ? "
                    + "AND (c.date_fin IS NULL OR c.date_fin >= ?)";
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameter = 1;
                for (Integer site : sites) statement.setInt(parameter++, site);
                statement.setDate(parameter++, java.sql.Date.valueOf(fiche.getDateFin()));
                statement.setDate(parameter, java.sql.Date.valueOf(fiche.getDateDebut()));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) union.put(result.getString("code").toUpperCase(Locale.ROOT), result.getBoolean("fractionnable"));
                }
            }
        }
        List<String> columns = columns(union);
        try (Workbook book = new XSSFWorkbook(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Sheet tab = book.createSheet("Pointage");
            Sheet metadata = book.createSheet("_Import");
            metadata.createRow(0).createCell(0).setCellValue(employeeId);
            metadata.createRow(1).createCell(0).setCellValue(periodId);
            book.setSheetHidden(book.getSheetIndex(metadata), true);
            Row header = tab.createRow(0);
            for (int i = 0; i < columns.size(); i++) header.createCell(i).setCellValue(columns.get(i));
            Sheet help = book.createSheet("Aide");
            help.createRow(0).createCell(0).setCellValue("Événement : CONGÉ = congé à régulariser ; ABSENCE = absence à traiter ; vide = aucun signalement.");
            help.createRow(1).createCell(0).setCellValue("Un seul CONGÉ au début suffit. La date de reprise se choisit dans l'aperçu.");
            help.createRow(2).createCell(0).setCellValue("Indemnités : vide = proposition du site ; 0 = refus ; 1 = accordée ; 0,5 = demi-journée.");
            help.setColumnWidth(0, 25000);
            org.apache.poi.ss.usermodel.DataValidationHelper validationHelper = tab.getDataValidationHelper();
            org.apache.poi.ss.usermodel.DataValidationConstraint codeList = validationHelper
                    .createExplicitListConstraint(new String[]{"CONGÉ", "ABSENCE"});
            org.apache.poi.ss.usermodel.DataValidation codeValidation = validationHelper.createValidation(codeList,
                    new org.apache.poi.ss.util.CellRangeAddressList(1, Math.max(1, fiche.getPointagesData().size()), FIXED.size(), FIXED.size()));
            codeValidation.setShowErrorBox(true);
            tab.addValidationData(codeValidation);
            CellStyle dateStyle = book.createCellStyle();
            dateStyle.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            int rowIndex = 1;
            for (PointageJourDTO day : fiche.getPointagesData()) {
                Row row = tab.createRow(rowIndex++);
                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(java.sql.Date.valueOf(LocalDate.parse(day.getDateComplete())));
                dateCell.setCellStyle(dateStyle);
            }
            tab.createFreezePane(0, 1);
            tab.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rowIndex - 1, 0, columns.size() - 1));
            for (int i = 0; i < columns.size(); i++) tab.setColumnWidth(i, i == 0 ? 4200 : 5500);
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    public ImportPreview preview(int employeeId, int periodId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("Fichier XLSX vide ou supérieur à 2 Mo.");
        FichePointageEmployeDTO fiche = sheet(employeeId, periodId);
        Map<String, PointageJourDTO> days = new HashMap<>();
        fiche.getPointagesData().forEach(day -> days.put(day.getDateComplete(), day));
        List<ImportLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        LocalDate previousLeaveMarker = null;
        String leaveMarkerStart = null;
        try (InputStream fileStream = file.getInputStream(); Workbook book = WorkbookFactory.create(fileStream)) {
            Sheet metadata = book.getSheet("_Import");
            if (metadata == null || metadata.getRow(0) == null || metadata.getRow(1) == null
                    || metadata.getRow(0).getCell(0) == null || metadata.getRow(1).getCell(0) == null
                    || (int) metadata.getRow(0).getCell(0).getNumericCellValue() != employeeId
                    || (int) metadata.getRow(1).getCell(0).getNumericCellValue() != periodId)
                throw new IllegalArgumentException("Ce modèle appartient à un autre collaborateur ou à une autre période.");
            Sheet tab = book.getSheet("Pointage");
            if (tab == null) throw new IllegalArgumentException("La feuille Pointage est introuvable.");
            Row header = tab.getRow(0);
            if (header == null) throw new IllegalArgumentException("En-tête du modèle absent.");
            List<String> headers = new ArrayList<>();
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            if (header.getLastCellNum() > 100) throw new IllegalArgumentException("Le modèle dépasse 100 colonnes.");
            for (int col = 0; col < header.getLastCellNum(); col++)
                headers.add(formatter.formatCellValue(header.getCell(col)).trim());
            if (headers.size() < FIXED.size()
                    || (!headers.subList(0, FIXED.size()).equals(FIXED)
                    && !headers.subList(0, LEGACY_FIXED.size()).equals(LEGACY_FIXED)))
                throw new IllegalArgumentException("Les colonnes fixes du modèle Pointage ont été modifiées.");
            if (new HashSet<>(headers).size() != headers.size())
                throw new IllegalArgumentException("Le modèle contient des colonnes en double.");
            int allowanceStart = headers.size() > FIXED.size()
                    && (EVENT_COLUMN.equalsIgnoreCase(headers.get(FIXED.size()))
                    || LEGACY_EVENT_COLUMN.equalsIgnoreCase(headers.get(FIXED.size())))
                    ? FIXED.size() + 1 : FIXED.size();
            if (tab.getLastRowNum() > 400) throw new IllegalArgumentException("Le modèle dépasse 400 lignes.");
            for (int index = 1; index <= tab.getLastRowNum(); index++) {
                Row source = tab.getRow(index);
                if (source == null) continue;
                List<String> errors = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                String date = dateText(source.getCell(0), formatter);
                if (date.isBlank()) continue;
                PointageJourDTO existing = days.get(date);
                if (existing == null) errors.add("Date hors de la période sélectionnée");
                if (!seen.add(date)) errors.add("Date présente plusieurs fois dans le fichier");
                String[] hours = new String[4];
                for (int i = 0; i < 4; i++) {
                    try { hours[i] = timeText(source.getCell(i + 1), formatter); }
                    catch (IllegalArgumentException error) { errors.add(error.getMessage()); hours[i] = null; }
                }
                boolean hasHours = Arrays.stream(hours).anyMatch(Objects::nonNull);
                String eventValue = allowanceStart > FIXED.size()
                        ? formatter.formatCellValue(source.getCell(FIXED.size())).trim().toUpperCase(Locale.ROOT) : "";
                String eventCode = switch (eventValue) {
                    case "CONGÉ", "CONGE", "C" -> "C";
                    case "ABSENCE", "A" -> "A";
                    default -> eventValue;
                };
                if (!eventCode.isEmpty() && !eventCode.equals("C") && !eventCode.equals("A"))
                    errors.add("Événement inconnu : choisir CONGÉ, ABSENCE ou laisser vide");
                boolean hasAllowanceInput = false;
                for (int col = allowanceStart; col < headers.size(); col++) {
                    if (!formatter.formatCellValue(source.getCell(col)).isBlank()) {
                        hasAllowanceInput = true;
                        break;
                    }
                }
                boolean repeatedLeaveMarker = false;
                if (eventCode.equals("C") && !hasHours) {
                    try {
                        LocalDate leaveDay = LocalDate.parse(date);
                        repeatedLeaveMarker = previousLeaveMarker != null
                                && previousLeaveMarker.plusDays(1).equals(leaveDay);
                        if (!repeatedLeaveMarker) leaveMarkerStart = date;
                        previousLeaveMarker = leaveDay;
                    } catch (Exception ignored) { previousLeaveMarker = null; }
                } else {
                    previousLeaveMarker = null;
                }
                if (repeatedLeaveMarker) {
                    if (hasAllowanceInput) errors.add("CONGÉ répété avec indemnités : conserver CONGÉ sur le premier jour et examiner cette indemnité séparément");
                    else warnings.add("CONGÉ répété : couvert par le signalement du " + leaveMarkerStart);
                }
                boolean continuous = hours[0] != null && hours[3] != null && hours[1] == null && hours[2] == null;
                boolean morningOnly = hours[0] != null && hours[1] != null && hours[2] == null && hours[3] == null;
                boolean afternoonOnly = hours[0] == null && hours[1] == null && hours[2] != null && hours[3] != null;
                boolean complete = Arrays.stream(hours).allMatch(Objects::nonNull);
                if (hasHours && !continuous && !morningOnly && !afternoonOnly && !complete)
                    errors.add("Pointage incomplet : corriger les heures du matin ou de l'après-midi");
                if (complete && !eventCode.isEmpty()) errors.add("Un événement CONGÉ/ABSENCE est incompatible avec une journée entièrement pointée");
                if (continuous) warnings.add("Journée continue détectée");
                if (morningOnly) warnings.add("Après-midi à régulariser");
                if (afternoonOnly) warnings.add("Matin à régulariser");
                if (eventCode.equals("C")) warnings.add(morningOnly || afternoonOnly
                        ? "Demi-journée de congé signalée : confirmer dans la régularisation"
                        : "Congé signalé : choisir la date de reprise dans la régularisation");
                if (eventCode.equals("A")) warnings.add("Absence signalée : choisir le traitement dans la régularisation");
                if (existing != null && eventCode.equals("A") && (existing.isWeekend()
                        || "FERIE_CHOME".equals(existing.getStatut())))
                    errors.add("Absence sur un repos ou un férié chômé : examiner la date dans la grille");
                boolean conflict = existing != null && existing.getIdPointage() != null;
                if (conflict && hasHours) warnings.add("Pointage déjà enregistré : comparer les deux versions");
                if (existing != null && existing.isCongeApprouve() && hasHours)
                    errors.add("Congé approuvé : régulariser la journée avant toute saisie d'heures");
                if (existing != null && hasHours && ("CONGE".equals(existing.getStatut())
                        || "CONGE_PAYE".equals(existing.getStatut())))
                    errors.add("Congé déjà posé : utiliser la régularisation avant de modifier cette journée");
                if (existing != null && !eventCode.isEmpty() && ("CONGE".equals(existing.getStatut())
                        || "CONGE_PAYE".equals(existing.getStatut())))
                    errors.add("Congé déjà posé : aucun nouveau signalement nécessaire");
                SaisiePointageJourDTO proposed = null;
                List<Map<String, Object>> availableAllowances = List.of();
                Map<String, Object> allowanceOverrides = new LinkedHashMap<>();
                if (existing != null && errors.isEmpty() && !repeatedLeaveMarker
                        && (hasHours || hasAllowanceInput || !eventCode.isEmpty())) {
                    DayContext dayContext = context(employeeId, existing);
                    proposed = dayContext.employee();
                    availableAllowances = dayContext.allowances();
                    Map<String, Object> selections = allowances(source, headers, allowanceStart, availableAllowances, proposed,
                            hours, continuous, formatter, errors, allowanceOverrides);
                    proposed.setDynamicIndemnites(selections);
                    if (!hasHours && hasAllowanceInput && eventCode.isEmpty()
                            && !"CONGE".equals(existing.getStatut()) && !"CONGE_PAYE".equals(existing.getStatut()))
                        errors.add("Indemnités sans horaires : indiquer C ou A pour examiner la journée");
                    if (hasHours && errors.isEmpty()) {
                        proposed.setHeureEntreeMatin(hours[0]); proposed.setHeureSortieMatin(hours[1]);
                        proposed.setHeureEntreeAm(hours[2]); proposed.setHeureSortieAm(hours[3]);
                        proposed.setHoraireContinu(continuous);
                        proposed.setDynamicIndemnites(selections);
                        proposed.setCodeStatut("PRESENT");
                        proposed.setIdPeriodePaie(periodId);
                        try {
                            proposed = pointages.previsualiserPointage(proposed);
                            if (proposed.isHasValidationError()) errors.addAll(proposed.getValidationMessages());
                        } catch (Exception error) {
                            errors.add(error.getMessage() == null ? "Pointage invalide" : error.getMessage());
                        }
                    }
                }
                String decision = !errors.isEmpty() ? "ERROR" : repeatedLeaveMarker ? "IGNORE" : hasHours ? "IMPORT"
                        : !eventCode.isEmpty() ? "SIGNAL"
                        : hasAllowanceInput ? "IMPORT" : "IGNORE";
                if (!hasHours && existing != null && !existing.isWeekend()
                        && !"FERIE_CHOME".equals(existing.getStatut())
                        && !"CONGE".equals(existing.getStatut())) warnings.add("Non saisi : décider dans la grille");
                lines.add(new ImportLine(index + 1, date, eventCode, decision,
                        ("IMPORT".equals(decision) || "SIGNAL".equals(decision)) && !conflict, conflict,
                        errors, warnings, proposed, existing, availableAllowances, allowanceOverrides));
            }
            return new ImportPreview(lines, headers);
        } catch (org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException error) {
            throw new IllegalArgumentException("Le fichier doit être au format XLSX.", error);
        }
    }

    private static List<String> columns(SortedMap<String, Boolean> allowances) {
        List<String> result = new ArrayList<>(FIXED);
        result.add(EVENT_COLUMN);
        allowances.forEach((code, split) -> {
            if (split) { result.add(code + " matin"); result.add(code + " après-midi"); }
            else result.add(code);
        });
        return result;
    }

    private static String dateText(Cell cell, DataFormatter formatter) {
        if (cell == null) return "";
        if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        String raw = formatter.formatCellValue(cell).trim();
        try { return LocalDate.parse(raw).toString(); }
        catch (Exception ignored) { return raw; }
    }

    private static String timeText(Cell cell, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.NUMERIC && cell.getNumericCellValue() >= 0
                && cell.getNumericCellValue() < 1) {
            long minutes = Math.round(cell.getNumericCellValue() * 1440);
            if (minutes >= 1440) throw new IllegalArgumentException("Heure invalide : fin de journée hors plage");
            return LocalTime.of((int) (minutes / 60), (int) (minutes % 60)).toString();
        }
        String value = formatter.formatCellValue(cell).trim().replace('h', ':');
        if (value.isEmpty()) return null;
        if (value.matches("\\d{4}")) value = value.substring(0, 2) + ":" + value.substring(2);
        try { return LocalTime.parse(value, DateTimeFormatter.ofPattern("H:mm")).toString(); }
        catch (Exception error) { throw new IllegalArgumentException("Heure invalide : " + value); }
    }

    private static Map<String, Object> allowances(Row source, List<String> headers, int allowanceStart,
            List<Map<String, Object>> configured, SaisiePointageJourDTO employee,
            String[] hours, boolean continuous, DataFormatter formatter, List<String> errors,
            Map<String, Object> overrides) {
        Map<String, Object> selections = new LinkedHashMap<>();
        Map<String, Object> defaults = employee.getDynamicIndemnites() == null ? Map.of() : employee.getDynamicIndemnites();
        Set<String> validHeaders = new HashSet<>();
        boolean morning = hours[0] != null || hours[1] != null;
        boolean afternoon = continuous || hours[2] != null || hours[3] != null;
        for (Map<String, Object> allowance : configured) {
            String code = String.valueOf(allowance.get("code")).toUpperCase(Locale.ROOT);
            String id = String.valueOf(allowance.get("idIndemnite"));
            boolean split = Boolean.TRUE.equals(allowance.get("fractionnable"));
            @SuppressWarnings("unchecked") Map<String, Object> current = defaults.get(id) instanceof Map<?, ?> map
                    ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
            boolean suggested = current.get("motifSuggestion") == null;
            if (split) {
                String colMorning = code + " matin", colAfternoon = code + " après-midi";
                validHeaders.add(colMorning); validHeaders.add(colAfternoon);
                Boolean m = allowanceCell(source, headers, colMorning, formatter, errors, true);
                Boolean a = allowanceCell(source, headers, colAfternoon, formatter, errors, true);
                if (m != null || a != null) {
                    Map<String, Object> selected = new LinkedHashMap<>();
                    if (m != null) selected.put("matin", m);
                    if (a != null) selected.put("am", a);
                    overrides.put(id, selected);
                }
                boolean grantMorning = m == null ? suggested && morning : m;
                boolean grantAfternoon = a == null ? suggested && afternoon : a;
                current.put("matin", grantMorning); current.put("am", grantAfternoon);
                current.put("accordee", grantMorning || grantAfternoon);
                current.put("fractionJour", (grantMorning ? 0.5 : 0) + (grantAfternoon ? 0.5 : 0));
            } else {
                validHeaders.add(code);
                Boolean selected = allowanceCell(source, headers, code, formatter, errors, false);
                if (selected != null) overrides.put(id, Map.of("accordee", selected));
                boolean grant = selected == null ? suggested && (morning || afternoon) : selected;
                current.put("accordee", grant); current.put("fractionJour", grant ? 1 : 0);
            }
            selections.put(id, current);
        }
        for (int i = allowanceStart; i < headers.size(); i++) {
            String header = headers.get(i);
            if (!validHeaders.contains(header) && !formatter.formatCellValue(source.getCell(i)).isBlank())
                errors.add("Indemnité " + header + " non active à cette date");
        }
        return selections;
    }

    private static Boolean allowanceCell(Row row, List<String> headers, String heading,
            DataFormatter formatter, List<String> errors, boolean split) {
        int index = headers.indexOf(heading);
        if (index < 0) return null;
        String text = formatter.formatCellValue(row.getCell(index)).trim().replace(',', '.');
        if (text.isBlank()) return null;
        if (text.equals("0")) return false;
        if (split && text.equals("0.5") || !split && text.equals("1")) return true;
        errors.add(heading + " : saisir " + (split ? "0 ou 0,5" : "0 ou 1"));
        return null;
    }
}
