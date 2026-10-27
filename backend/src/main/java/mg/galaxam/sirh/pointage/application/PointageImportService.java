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
import java.text.Normalizer;
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
    private static final int TEMPLATE_HEADER_ROW = 5;
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
            setMetadataText(metadata, 2, fiche.getNomComplet() != null ? fiche.getNomComplet()
                    : String.join(" ", Arrays.asList(fiche.getPrenom(), fiche.getNom()).stream()
                            .filter(Objects::nonNull).toList()).trim());
            setMetadataText(metadata, 3, fiche.getMatricule());
            setMetadataText(metadata, 4, fiche.getSiteNom());
            setMetadataText(metadata, 5, fiche.getLibellePeriode());
            setMetadataText(metadata, 6, fiche.getDateDebut());
            setMetadataText(metadata, 7, fiche.getDateFin());
            book.setSheetHidden(book.getSheetIndex(metadata), true);

            int lastColumn = columns.size() - 1;
            Font titleFont = book.createFont();
            titleFont.setBold(true);
            titleFont.setColor(IndexedColors.WHITE.getIndex());
            titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = book.createCellStyle();
            titleStyle.setFont(titleFont);
            titleStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Row title = tab.createRow(0);
            title.setHeightInPoints(27);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("MODÈLE DE POINTAGE — IMPORT PAR COLLABORATEUR");
            titleCell.setCellStyle(titleStyle);
            tab.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, lastColumn));

            Font labelFont = book.createFont();
            labelFont.setBold(true);
            CellStyle labelStyle = book.createCellStyle();
            labelStyle.setFont(labelFont);
            labelStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            labelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            labelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            CellStyle valueStyle = book.createCellStyle();
            valueStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            createInfoCell(tab, 1, 0, "Collaborateur", labelStyle);
            createInfoCell(tab, 1, 1, titleName(fiche), valueStyle);
            if (lastColumn > 1) tab.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 1, lastColumn));

            createInfoCell(tab, 2, 0, "Matricule", labelStyle);
            createInfoCell(tab, 2, 1, fiche.getMatricule() == null ? "—" : fiche.getMatricule(), valueStyle);
            createInfoCell(tab, 2, 2, "Site", labelStyle);
            createInfoCell(tab, 2, 3, fiche.getSiteNom() == null ? "—" : fiche.getSiteNom(), valueStyle);
            if (lastColumn > 3) tab.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(2, 2, 3, lastColumn));

            createInfoCell(tab, 3, 0, "Période", labelStyle);
            createInfoCell(tab, 3, 1, fiche.getLibellePeriode() == null ? "Période " + periodId : fiche.getLibellePeriode(), valueStyle);
            createInfoCell(tab, 3, 2, "Du", labelStyle);
            createInfoCell(tab, 3, 3, fiche.getDateDebut(), valueStyle);
            createInfoCell(tab, 3, 4, "Au", labelStyle);
            createInfoCell(tab, 3, 5, fiche.getDateFin(), valueStyle);

            Font instructionFont = book.createFont();
            instructionFont.setItalic(true);
            instructionFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            CellStyle instructionStyle = book.createCellStyle();
            instructionStyle.setFont(instructionFont);
            instructionStyle.setWrapText(true);
            instructionStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            Row instruction = tab.createRow(4);
            Cell instructionCell = instruction.createCell(0);
            instructionCell.setCellValue("Vérifiez l’employé et la période ci-dessus. Saisissez uniquement les horaires et événements dans les lignes ci-dessous; ne modifiez pas les en-têtes.");
            instructionCell.setCellStyle(instructionStyle);
            tab.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(4, 4, 0, lastColumn));
            instruction.setHeightInPoints(30);

            Row header = tab.createRow(TEMPLATE_HEADER_ROW);
            CellStyle headerStyle = book.createCellStyle();
            Font headerFont = book.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }
            Sheet help = book.createSheet("Aide");
            help.createRow(0).createCell(0).setCellValue("Événement : CONGÉ = congé à régulariser ; ABSENCE = absence à traiter ; vide = aucun signalement.");
            help.createRow(1).createCell(0).setCellValue("Un seul CONGÉ au début suffit. La date de reprise se choisit lors de la régularisation.");
            help.createRow(2).createCell(0).setCellValue("Indemnités : vide = proposition du site ; 0 = refus ; 0,5 = demi-indemnité ; 1 = indemnité entière. Pour une indemnité fractionnable, le créneau de la demi-indemnité suit les heures saisies (matin si les deux demi-journées sont travaillées).");
            help.setColumnWidth(0, 25000);
            org.apache.poi.ss.usermodel.DataValidationHelper validationHelper = tab.getDataValidationHelper();
            org.apache.poi.ss.usermodel.DataValidationConstraint codeList = validationHelper
                    .createExplicitListConstraint(new String[]{"CONGÉ", "ABSENCE"});
            org.apache.poi.ss.usermodel.DataValidation codeValidation = validationHelper.createValidation(codeList,
                    new org.apache.poi.ss.util.CellRangeAddressList(TEMPLATE_HEADER_ROW + 1,
                            TEMPLATE_HEADER_ROW + Math.max(1, fiche.getPointagesData().size()), FIXED.size(), FIXED.size()));
            codeValidation.setShowErrorBox(true);
            tab.addValidationData(codeValidation);
            CellStyle dateStyle = book.createCellStyle();
            dateStyle.setDataFormat(book.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            int rowIndex = TEMPLATE_HEADER_ROW + 1;
            for (PointageJourDTO day : fiche.getPointagesData()) {
                Row row = tab.createRow(rowIndex++);
                Cell dateCell = row.createCell(0);
                dateCell.setCellValue(java.sql.Date.valueOf(LocalDate.parse(day.getDateComplete())));
                dateCell.setCellStyle(dateStyle);
            }
            tab.createFreezePane(0, TEMPLATE_HEADER_ROW + 1);
            tab.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(TEMPLATE_HEADER_ROW, rowIndex - 1, 0, lastColumn));
            for (int i = 0; i < columns.size(); i++) tab.setColumnWidth(i, i == 0 ? 4200 : 5500);
            tab.setZoom(90);
            book.setActiveSheet(book.getSheetIndex(tab));
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    public String templateFilename(int employeeId, int periodId) {
        FichePointageEmployeDTO fiche = sheet(employeeId, periodId);
        return "Pointage_" + filenamePart(titleName(fiche)) + "_" + filenamePart(fiche.getMatricule())
                + "_periode_" + periodId + "_" + fiche.getDateDebut() + "_" + fiche.getDateFin() + ".xlsx";
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
            Integer modelEmployeeId = metadataInteger(metadata, 0);
            Integer modelPeriodId = metadataInteger(metadata, 1);
            if (modelEmployeeId == null || modelPeriodId == null)
                throw new IllegalArgumentException("Ce fichier ne contient pas les informations d’identification du modèle Pointage. Téléchargez un modèle depuis la fiche de l’employé.");
            if (modelEmployeeId != employeeId || modelPeriodId != periodId) {
                String fileEmployee = metadataText(metadata, 2);
                String fileMatricule = metadataText(metadata, 3);
                String fileSite = metadataText(metadata, 4);
                String filePeriod = metadataText(metadata, 5);
                String fileStart = metadataText(metadata, 6);
                String fileEnd = metadataText(metadata, 7);
                String currentEmployee = titleName(fiche);
                String currentPeriod = fiche.getLibellePeriode() == null ? "Période " + periodId : fiche.getLibellePeriode();
                String fileIdentity = "collaborateur " + (fileEmployee.isBlank() ? "#" + modelEmployeeId : fileEmployee)
                        + (fileMatricule.isBlank() ? "" : " (" + fileMatricule + ")")
                        + ", période " + (filePeriod.isBlank() ? "#" + modelPeriodId : filePeriod)
                        + (fileStart.isBlank() || fileEnd.isBlank() ? "" : " (" + fileStart + " au " + fileEnd + ")")
                        + (fileSite.isBlank() ? "" : ", site " + fileSite);
                String selectedIdentity = "collaborateur " + currentEmployee
                        + (fiche.getMatricule() == null || fiche.getMatricule().isBlank() ? "" : " (" + fiche.getMatricule() + ")")
                        + ", période " + currentPeriod + " (" + fiche.getDateDebut() + " au " + fiche.getDateFin() + ")";
                throw new IllegalArgumentException("Ce fichier correspond au " + fileIdentity + ", alors que la sélection actuelle est le "
                        + selectedIdentity + ". Téléchargez le modèle depuis cette fiche et cette période.");
            }
            Sheet tab = book.getSheet("Pointage");
            if (tab == null) throw new IllegalArgumentException("La feuille Pointage est introuvable.");
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            int headerRowIndex = findHeaderRow(tab, formatter);
            Row header = headerRowIndex < 0 ? null : tab.getRow(headerRowIndex);
            if (header == null) throw new IllegalArgumentException("En-tête du modèle absent.");
            List<String> headers = new ArrayList<>();
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
            if (tab.getLastRowNum() - headerRowIndex > 400) throw new IllegalArgumentException("Le modèle dépasse 400 lignes.");
            for (int index = headerRowIndex + 1; index <= tab.getLastRowNum(); index++) {
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
                    proposed.setInclureIndemnitesCalculeesPreview(true);
                    int workingDays = availableAllowances.stream()
                            .map(item -> item.get("nbJoursOuvrablesPeriode"))
                            .filter(Number.class::isInstance).map(Number.class::cast)
                            .mapToInt(Number::intValue).findFirst().orElse(0);
                    boolean nonWorked = !hasHours && !eventCode.isEmpty()
                            || "CONGE".equals(existing.getStatut())
                            || "CONGE_PAYE".equals(existing.getStatut())
                            || "FERIE_CHOME".equals(existing.getStatut())
                            || "REPOS".equals(existing.getStatut());
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
                    if (!hasHours && !eventCode.isEmpty() && errors.isEmpty()) {
                        proposed.setIndemnitesCalculeesPreview(pointages.previsualiserIndemnites(
                                selections, proposed.getIdSite(), date, workingDays, nonWorked));
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
            // Une ligne supprimée du modèle doit rester visible et pouvoir être complétée.
            for (PointageJourDTO day : fiche.getPointagesData()) {
                if (seen.contains(day.getDateComplete())) continue;
                boolean rest = day.isWeekend() || "FERIE_CHOME".equals(day.getStatut());
                SaisiePointageJourDTO proposal = null;
                List<Map<String, Object>> availableAllowances = List.of();
                if (!rest && day.getIdSite() != null && day.getIdSite() > 0) {
                    DayContext dayContext = context(employeeId, day);
                    proposal = dayContext.employee();
                    availableAllowances = dayContext.allowances();
                }
                lines.add(new ImportLine(-(lines.size() + 1), day.getDateComplete(), "",
                        rest ? "IGNORE" : "MISSING", false, day.getIdPointage() != null,
                        List.of(), List.of(rest ? "Repos sans ligne dans le fichier" : "Date absente du fichier : compléter ou laisser non saisie"),
                        proposal, day, availableAllowances, Map.of()));
            }
            lines.sort(Comparator.comparing(ImportLine::date).thenComparingInt(ImportLine::ligne));
            return new ImportPreview(lines, headers);
        } catch (org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException
                 | org.apache.poi.openxml4j.exceptions.InvalidFormatException
                 | java.io.IOException error) {
            throw new IllegalArgumentException("Le fichier n'est pas un classeur XLSX valide ou n'a pas pu être lu. Téléchargez le modèle de pointage puis réessayez.", error);
        }
    }

    private static String titleName(FichePointageEmployeDTO fiche) {
        if (fiche.getNomComplet() != null && !fiche.getNomComplet().isBlank()) return fiche.getNomComplet().trim();
        return String.join(" ", Arrays.asList(fiche.getPrenom(), fiche.getNom()).stream()
                .filter(value -> value != null && !value.isBlank()).toList()).trim();
    }

    private static String filenamePart(String value) {
        if (value == null || value.isBlank()) return "inconnu";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String safe = normalized.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return safe.isBlank() ? "inconnu" : safe;
    }

    private static void createInfoCell(Sheet sheet, int rowIndex, int columnIndex, String value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(value == null ? "—" : value);
        cell.setCellStyle(style);
    }

    private static void setMetadataText(Sheet metadata, int rowIndex, String value) {
        if (value == null) return;
        metadata.createRow(rowIndex).createCell(0).setCellValue(value);
    }

    private static Integer metadataInteger(Sheet metadata, int rowIndex) {
        if (metadata == null || metadata.getRow(rowIndex) == null || metadata.getRow(rowIndex).getCell(0) == null) return null;
        Cell cell = metadata.getRow(rowIndex).getCell(0);
        try {
            return cell.getCellType() == CellType.NUMERIC ? (int) cell.getNumericCellValue()
                    : Integer.valueOf(cell.getStringCellValue().trim());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String metadataText(Sheet metadata, int rowIndex) {
        if (metadata == null || metadata.getRow(rowIndex) == null || metadata.getRow(rowIndex).getCell(0) == null) return "";
        return new DataFormatter(Locale.FRANCE).formatCellValue(metadata.getRow(rowIndex).getCell(0)).trim();
    }

    private static int findHeaderRow(Sheet sheet, DataFormatter formatter) {
        int lastCandidate = Math.min(sheet.getLastRowNum(), 12);
        for (int rowIndex = 0; rowIndex <= lastCandidate; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || row.getLastCellNum() < FIXED.size() || row.getLastCellNum() > 100) continue;
            List<String> values = new ArrayList<>();
            for (int column = 0; column < row.getLastCellNum(); column++)
                values.add(formatter.formatCellValue(row.getCell(column)).trim());
            if (values.size() >= FIXED.size()
                    && (values.subList(0, FIXED.size()).equals(FIXED)
                    || values.subList(0, LEGACY_FIXED.size()).equals(LEGACY_FIXED))) return rowIndex;
        }
        return -1;
    }

    private static List<String> columns(SortedMap<String, Boolean> allowances) {
        List<String> result = new ArrayList<>(FIXED);
        result.add(EVENT_COLUMN);
        allowances.keySet().forEach(result::add);
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
            boolean suggested = current.get("motifSuggestion") == null
                    && Boolean.TRUE.equals(allowance.get("cocheParDefaut"));
            if (split) {
                validHeaders.add(code);
                Double fraction = allowanceFractionCell(source, headers, code, formatter, errors);
                boolean grantMorning;
                boolean grantAfternoon;
                if (fraction != null) {
                    if (fraction >= 1.0) {
                        grantMorning = true;
                        grantAfternoon = true;
                    } else if (fraction == 0.5) {
                        // Une demi-indemnité suit le seul créneau travaillé ; journée complète => matin par convention.
                        grantMorning = morning;
                        grantAfternoon = !morning && afternoon;
                    } else {
                        grantMorning = false;
                        grantAfternoon = false;
                    }
                    overrides.put(id, Map.of("matin", grantMorning, "am", grantAfternoon));
                } else {
                    grantMorning = suggested && morning;
                    grantAfternoon = suggested && afternoon;
                }
                current.put("matin", grantMorning); current.put("am", grantAfternoon);
                current.put("accordee", grantMorning || grantAfternoon);
                current.put("fractionJour", (grantMorning ? 0.5 : 0) + (grantAfternoon ? 0.5 : 0));
            } else {
                validHeaders.add(code);
                Boolean selected = allowanceCell(source, headers, code, formatter, errors);
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

    private static Double allowanceFractionCell(Row row, List<String> headers, String heading,
            DataFormatter formatter, List<String> errors) {
        int index = headers.indexOf(heading);
        if (index < 0) return null;
        String text = formatter.formatCellValue(row.getCell(index)).trim().replace(',', '.');
        if (text.isBlank()) return null;
        if (text.equals("0") || text.equals("0.5") || text.equals("1")) return Double.valueOf(text);
        errors.add(heading + " : saisir 0, 0,5 ou 1");
        return null;
    }

    private static Boolean allowanceCell(Row row, List<String> headers, String heading,
            DataFormatter formatter, List<String> errors) {
        int index = headers.indexOf(heading);
        if (index < 0) return null;
        String text = formatter.formatCellValue(row.getCell(index)).trim();
        if (text.isBlank()) return null;
        if (text.equals("0")) return false;
        if (text.equals("1")) return true;
        errors.add(heading + " : saisir 0 ou 1");
        return null;
    }
}
