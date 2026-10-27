package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Lecture de gestion commune aux classeurs, exclusivement à partir des bulletins enregistrés. */
public final class PayrollWorkbookAnalysis {
    private PayrollWorkbookAnalysis() {}
    public enum Kind { PAIE, SOCIAL, IRSA }
    record Contribution(BigDecimal employee, BigDecimal employer) {
        BigDecimal total() { return employee.add(employer); }
    }
    record Entry(String period, int bulletin, int employee, String reference, String status,
                 String company, String nif, BigDecimal gross, BigDecimal net, BigDecimal cost,
                 BigDecimal taxBase, BigDecimal irsa, BigDecimal employeeSocial, BigDecimal employerSocial,
                 Map<String, Contribution> organisms, boolean socialMismatch) {}
    record Data(List<String> expected, Set<String> included, List<Entry> entries) {}
    record Metric(String label, Function<Entry, BigDecimal> value) {}
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    public static byte[] monthly(byte[] bytes, Kind kind, int periodId, Connection c) throws Exception {
        try (var ps = c.prepareStatement("SELECT code FROM periode_paie WHERE id_periode_paie=?")) {
            ps.setInt(1, periodId);
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Période introuvable.");
                return enrich(bytes, kind, List.of(rs.getString(1)), 1, c);
            }
        }
    }
    public static byte[] quarterly(byte[] bytes, int year, int quarter, Connection c) throws Exception {
        return enrich(bytes, Kind.SOCIAL, months(year, (quarter - 1) * 3 + 1, 3), 3, c);
    }
    public static byte[] annual(byte[] bytes, int year, Connection c) throws Exception {
        return enrich(bytes, Kind.PAIE, months(year, 1, 12), 12, c);
    }
    private static List<String> months(int year, int first, int count) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(YearMonth.of(year, first).plusMonths(i).toString());
        return result;
    }
    private static byte[] enrich(byte[] bytes, Kind kind, List<String> periods, int shift, Connection c) throws Exception {
        Data current = load(periods, kind, c);
        List<String> preceding = periods.stream().map(p -> YearMonth.parse(p).minusMonths(shift).toString()).toList();
        Data previous;
        String unavailable = null;
        try { previous = load(preceding, kind, c); }
        catch (IllegalStateException ex) {
            previous = new Data(preceding, Set.of(), List.of());
            unavailable = "Historique précédent incomplet ou incohérent : comparaison indisponible.";
        }
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            render(book, kind, current, previous, unavailable);
            book.write(out);
            return out.toByteArray();
        }
    }
    static Data load(List<String> expected, Kind kind, Connection c) throws Exception {
        Map<Integer, String> periods = new LinkedHashMap<>();
        String marks = String.join(",", Collections.nCopies(expected.size(), "?"));
        try (var ps = c.prepareStatement("SELECT id_periode_paie,code,statut FROM periode_paie WHERE code IN (" + marks + ") ORDER BY code")) {
            for (int i = 0; i < expected.size(); i++) ps.setString(i + 1, expected.get(i));
            try (var rs = ps.executeQuery()) {
                Set<String> seen = new HashSet<>();
                while (rs.next()) {
                    if (!seen.add(rs.getString(2))) throw new IllegalStateException("Plusieurs campagnes pour " + rs.getString(2));
                    if (PayrollExportEligibility.isFinalPeriod(rs.getString(3))) periods.put(rs.getInt(1), rs.getString(2));
                }
            }
        }
        List<Entry> entries = new ArrayList<>();
        Set<String> included = new TreeSet<>();
        Map<String, String> organisms = EtatSocialMensuelExportService.loadOrganismes(c);
        for (var period : periods.entrySet()) {
            List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(period.getKey(), c);
            if (bulletins.isEmpty()) continue;
            PayrollExportEligibility.requireFinalBulletins(bulletins, period.getValue());
            included.add(period.getValue());
            var companies = PayrollExportContextResolver.companiesByBulletin(period.getKey(), c);
            var lines = BulletinPaieLigne.listerParBulletins(bulletins.stream().map(BulletinPaie::getIdBulletin).toList(), c);
            for (BulletinPaie b : bulletins) {
                if (kind == Kind.IRSA && !SafiExportService.isIrsaEmployee(b)) continue;
                var company = companies.get(b.getIdBulletin());
                if (company == null) throw new IllegalStateException("Filiale absente du bulletin " + b.getReferenceBulletin());
                Map<String, Contribution> contributions = new TreeMap<>();
                for (var line : lines.getOrDefault(b.getIdBulletin(), List.of())) {
                    if (!EtatSocialMensuelExportService.isSocial(line)) continue;
                    String org = EtatSocialMensuelExportService.resolveOrganisme(line, organisms);
                    Contribution prior = contributions.getOrDefault(org, new Contribution(ZERO, ZERO));
                    contributions.put(org, new Contribution(prior.employee.add(z(line.getMontantSalarial())),
                            prior.employer.add(z(line.getMontantPatronal()))));
                }
                BigDecimal employee = contributions.values().stream().map(Contribution::employee).reduce(ZERO, BigDecimal::add);
                BigDecimal employer = contributions.values().stream().map(Contribution::employer).reduce(ZERO, BigDecimal::add);
                boolean mismatch = employee.subtract(z(b.getTotalCotisationsSalariales())).abs().compareTo(new BigDecimal("0.01")) > 0
                        || employer.subtract(z(b.getTotalCotisationsPatronales())).abs().compareTo(new BigDecimal("0.01")) > 0;
                entries.add(new Entry(period.getValue(), b.getIdBulletin(), b.getIdEmploye(), b.getReferenceBulletin(), b.getStatut(),
                        company.name(), company.nif(), z(b.getTotalGainsBruts()), z(b.getNetAPayer()), z(b.getCoutGlobalEmployeur()),
                        z(b.getBaseIrsaArrondie()), z(b.getIrsaNet()), employee, employer, contributions, mismatch));
            }
        }
        return new Data(expected, included, entries);
    }
    static String comparisonReason(Data now, Data before) {
        if (now.included.size() != now.expected.size()) return "Période partielle : comparaison globale indisponible.";
        if (before.included.isEmpty() || before.entries.isEmpty()) return "Pas de période de comparaison disponible (reprise ou historique absent).";
        if (before.included.size() != before.expected.size()) return "Historique précédent partiel : comparaison globale indisponible.";
        if (!employers(now.entries).equals(employers(before.entries))) return "Périmètre des filiales différent : comparaison globale indisponible.";
        return null;
    }
    private static Set<String> employers(List<Entry> entries) {
        Set<String> result = new TreeSet<>();
        entries.forEach(e -> result.add(e.company + "|" + e.nif));
        return result;
    }
    static BigDecimal rate(BigDecimal current, BigDecimal previous) {
        return previous.signum() == 0 ? null : current.subtract(previous).divide(previous.abs(), 8, RoundingMode.HALF_UP);
    }
    static void render(Workbook book, Kind kind, Data now, Data before, String unavailable) throws Exception {
        Style s = new Style(book);
        String reason = unavailable != null ? unavailable : comparisonReason(now, before);
        boolean comparable = reason == null;
        List<Metric> metrics = metrics(kind);
        Sheet pilot = book.createSheet(PayrollSheetNames.unique(book, "Vue de pilotage"));
        book.setSheetOrder(pilot.getSheetName(), 0);
        title(pilot, "VUE DE PILOTAGE · " + kind, s);
        note(pilot, 1, String.join(" / ", now.expected) + " · " + now.included.size() + "/" + now.expected.size()
                + " mois inclus · montants en ariary", s);
        note(pilot, 2, comparable ? "Comparaison : " + String.join(" / ", before.expected)
                + " · mêmes filiales, effectifs pouvant varier." : reason, s);
        header(pilot, 4, s, "Indicateur", "Période", "Précédente", "Écart (Ar)", "Évolution", "Lecture");
        int row = 5;
        for (Metric metric : metrics) {
            BigDecimal value = sum(now.entries, metric.value), prior = sum(before.entries, metric.value);
            text(pilot, row, 0, metric.label, s.body);
            number(pilot, row, 1, value, s.money);
            if (comparable) {
                number(pilot, row, 2, prior, s.money);
                number(pilot, row, 3, value.subtract(prior), s.money);
                BigDecimal rate = rate(value, prior);
                if (rate != null) number(pilot, row, 4, rate, s.percent);
                text(pilot, row, 5, prior.signum() == 0 ? "Base précédente nulle : % non calculable"
                        : value.compareTo(prior) > 0 ? "En hausse" : value.compareTo(prior) < 0 ? "En baisse" : "Stable", s.body);
            } else text(pilot, row, 5, "Montant constaté", s.body);
            row++;
        }
        row += 2;
        note(pilot, row++, distinct(now.entries) + " salariés distincts · " + now.entries.size() + " bulletins · "
                + employers(now.entries).size() + " filiales. Un salarié peut avoir plusieurs bulletins.", s);
        if (comparable) {
            Set<Integer> currentIds = employeeIds(now.entries), previousIds = employeeIds(before.entries);
            long added = currentIds.stream().filter(id -> !previousIds.contains(id)).count();
            long absent = previousIds.stream().filter(id -> !currentIds.contains(id)).count();
            note(pilot, row++, added + " salariés présents uniquement sur la période courante ; " + absent
                    + " uniquement sur la précédente. Cela ne prouve pas une embauche ou un départ.", s);
        }
        if (comparable) {
            String top = largestCompanyChange(now.entries, before.entries, metrics.get(metrics.size() - 1).value);
            if (top != null) note(pilot, row++, "Plus fort écart constaté par filiale : " + top + ". Consulter son détail avant de conclure sur la cause.", s);
        }
        note(pilot, row++, "À lire : une hausse peut venir des effectifs, rémunérations ou variables. Les écarts constatés ne suffisent pas à en attribuer la cause.", s);
        if (kind == Kind.SOCIAL) note(pilot, row++, "Part salarié = retenue sur salaire ; part employeur = charge patronale. Le total des deux représente les cotisations, pas le coût salarial total.", s);
        else if (kind == Kind.IRSA) note(pilot, row++, "Périmètre : salariés retenus dans l'état IRSA. Les montants proviennent des bulletins ; aucun impôt n'est recalculé ici.", s);
        header(pilot, ++row, s, "Point de contrôle", "Nombre", "", "", "", "Action suggérée");
        row++;
        control(pilot, row++, "Nets à payer négatifs", now.entries.stream().filter(e -> e.net.signum() < 0).count(), "Vérifier les retenues et régularisations", s);
        control(pilot, row++, "Organisme non configuré", now.entries.stream().filter(e -> e.organisms.containsKey(EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME)).count(), "Compléter le rattachement de cotisation", s);
        control(pilot, row++, "Écarts lignes / bulletin", now.entries.stream().filter(Entry::socialMismatch).count(), "Rapprocher les cotisations dans les détails", s);
        control(pilot, row++, "Identité employeur incomplète", now.entries.stream().filter(e -> e.nif == null || e.nif.isBlank()).count(), "Renseigner le NIF de la filiale", s);
        note(pilot, row++, "Les nombres de contrôle portent sur les bulletins. Zéro anomalie détectée ne constitue pas une validation comptable ou une preuve de paiement.", s);
        note(pilot, row, "Parcours de lecture : vue de pilotage → analyse des filiales / organismes → détails existants → traçabilité. Les mois non inclus ne sont jamais assimilés à zéro.", s);
        Sheet distribution = book.createSheet(PayrollSheetNames.unique(book, "Analyse filiales"));
        title(distribution, "RÉPARTITION PAR FILIALE", s);
        Metric primary = metrics.get(metrics.size() - 1);
        note(distribution, 1, "Indicateur : " + primary.label + " · salariés distincts par filiale ; ne pas additionner les effectifs en cas de mobilité.", s);
        header(distribution, 3, s, "Filiale", "Salariés", "Montant (Ar)", "Part du total", "", "Périmètre");
        Map<String, List<Entry>> groups = new TreeMap<>();
        now.entries.forEach(e -> groups.computeIfAbsent(e.company, k -> new ArrayList<>()).add(e));
        row = 4;
        BigDecimal total = sum(now.entries, primary.value);
        for (var group : groups.entrySet()) {
            text(distribution, row, 0, group.getKey(), s.body);
            number(distribution, row, 1, BigDecimal.valueOf(distinct(group.getValue())), s.integer);
            BigDecimal amount = sum(group.getValue(), primary.value);
            number(distribution, row, 2, amount, s.money);
            if (total.signum() != 0) number(distribution, row, 3, amount.divide(total, 8, RoundingMode.HALF_UP), s.percent);
            text(distribution, row++, 5, "Données des bulletins inclus", s.body);
        }
        if (kind == Kind.SOCIAL) {
            row += 2;
            header(distribution, row++, s, "Organisme", "Salariés", "Part salarié", "Part employeur", "Total (Ar)", "Lecture");
            Set<String> orgs = new TreeSet<>(); now.entries.forEach(e -> orgs.addAll(e.organisms.keySet()));
            for (String org : orgs) {
                List<Entry> eligible = now.entries.stream().filter(e -> e.organisms.containsKey(org) && e.organisms.get(org).total().signum() != 0).toList();
                BigDecimal employee = sum(eligible, e -> e.organisms.get(org).employee), employer = sum(eligible, e -> e.organisms.get(org).employer);
                text(distribution, row, 0, org, s.body);
                number(distribution, row, 1, BigDecimal.valueOf(distinct(eligible)), s.integer);
                number(distribution, row, 2, employee, s.money); number(distribution, row, 3, employer, s.money);
                number(distribution, row, 4, employee.add(employer), s.money);
                text(distribution, row++, 5, "Cotisations constatées", s.body);
            }
        }
        Sheet series = book.createSheet(PayrollSheetNames.unique(book, "Évolution et couverture"));
        title(series, "ÉVOLUTION ET COUVERTURE", s);
        note(series, 1, "Les mois absents ou non validés restent vides. Les effectifs sont des salariés distincts, pas des équivalents temps plein.", s);
        header(series, 3, s, "Mois", "Couverture", "Salariés", primary.label, "Écart comparable", "Lecture");
        row = 4;
        List<String> timeline = new ArrayList<>();
        if (now.expected.size() == 1) timeline.addAll(before.expected);
        timeline.addAll(now.expected);
        for (String period : timeline) {
            Data data = now.expected.contains(period) ? now : before;
            text(series, row, 0, period, s.body);
            boolean present = data.included.contains(period);
            text(series, row, 1, present ? "Inclus" : "Non inclus", s.body);
            if (present) {
                List<Entry> entries = data.entries.stream().filter(e -> e.period.equals(period)).toList();
                number(series, row, 2, BigDecimal.valueOf(distinct(entries)), s.integer);
                number(series, row, 3, sum(entries, primary.value), s.money);
                if (now.expected.contains(period)) {
                    int index = now.expected.indexOf(period);
                    String reference = index < before.expected.size() ? before.expected.get(index) : null;
                    if (reference != null && before.included.contains(reference)) {
                        List<Entry> referenceEntries = before.entries.stream().filter(e -> e.period.equals(reference)).toList();
                        if (employers(entries).equals(employers(referenceEntries))) {
                            number(series, row, 4, sum(entries, primary.value).subtract(sum(referenceEntries, primary.value)), s.money);
                            text(series, row, 5, "Écart avec " + reference, s.body);
                        } else text(series, row, 5, "Filiales différentes de " + reference, s.body);
                    } else text(series, row, 5, "Historique équivalent absent", s.body);
                } else text(series, row, 5, "Référence précédente", s.body);
            } else text(series, row, 5, "Aucune extrapolation", s.body);
            row++;
        }
        archive(book, now, before, reason, s);
        finish(book);
    }
    private static String largestCompanyChange(List<Entry> current, List<Entry> previous,
            Function<Entry, BigDecimal> metric) {
        Set<String> names = new TreeSet<>();
        current.forEach(e -> names.add(e.company));
        previous.forEach(e -> names.add(e.company));
        String selected = null;
        BigDecimal largest = ZERO;
        BigDecimal selectedDelta = ZERO;
        for (String name : names) {
            BigDecimal delta = sum(current.stream().filter(e -> e.company.equals(name)).toList(), metric)
                    .subtract(sum(previous.stream().filter(e -> e.company.equals(name)).toList(), metric));
            if (selected == null || delta.abs().compareTo(largest) > 0) {
                selected = name; largest = delta.abs(); selectedDelta = delta;
            }
        }
        return selected == null ? null : selected + " (" + selectedDelta.toPlainString() + " Ar)";
    }

    private static List<Metric> metrics(Kind kind) {
        return switch (kind) {
            case SOCIAL -> List.of(new Metric("Part salariale", Entry::employeeSocial), new Metric("Part patronale", Entry::employerSocial),
                    new Metric("Cotisations totales", e -> e.employeeSocial.add(e.employerSocial)));
            case IRSA -> List.of(new Metric("Assiette IRSA cumulée", Entry::taxBase), new Metric("IRSA retenu", Entry::irsa));
            case PAIE -> List.of(new Metric("Salaire brut", Entry::gross), new Metric("Net à payer", Entry::net),
                    new Metric("Cotisations patronales", Entry::employerSocial), new Metric("Coût employeur", Entry::cost));
        };
    }
    private static void archive(Workbook book, Data now, Data before, String reason, Style s) throws Exception {
        Sheet sheet = book.createSheet(PayrollSheetNames.unique(book, "Traçabilité"));
        title(sheet, "TRAÇABILITÉ DE L'EXPORT", s);
        note(sheet, 1, "Généré le " + ZonedDateTime.now(ZoneId.of("Indian/Antananarivo")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss XXX")) + " · version de présentation 1", s);
        note(sheet, 2, "Périodes incluses : " + String.join(", ", now.included) + " · comparaison : " + (reason == null ? String.join(", ", before.included) : reason), s);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        now.entries.stream().sorted(Comparator.comparing(Entry::period).thenComparingInt(Entry::bulletin))
                .forEach(entry -> digest.update((entry + "\n").getBytes(StandardCharsets.UTF_8)));
        String hash = HexFormat.of().formatHex(digest.digest());
        note(sheet, 3, "Empreinte SHA-256 des références et agrégats utilisés : " + hash, s);
        note(sheet, 4, "Conserver ce fichier pour archiver cette extraction. Une nouvelle génération peut refléter des corrections ultérieures. Les détails des bulletins restent les justificatifs de calcul.", s);
        header(sheet, 6, s, "Mois", "ID bulletin", "Référence", "Statut", "Filiale", "Contrôle social");
        int row = 7;
        for (Entry entry : now.entries) {
            text(sheet, row, 0, entry.period, s.body); number(sheet, row, 1, BigDecimal.valueOf(entry.bulletin), s.integer);
            text(sheet, row, 2, entry.reference, s.body); text(sheet, row, 3, entry.status, s.body);
            text(sheet, row, 4, entry.company, s.body); text(sheet, row++, 5, entry.socialMismatch ? "À rapprocher" : "Lignes concordantes", s.body);
        }
    }
    static void finish(Workbook book) {
        for (Sheet sheet : book) {
            sheet.createFreezePane(0, 0);
            sheet.setDisplayGridlines(false);
            sheet.setAutobreaks(true);
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) (sheet.getSheetName().equals("Vue de pilotage") ? 1 : 0));
            int lastColumn = 0;
            for (Row row : sheet) lastColumn = Math.max(lastColumn, row.getLastCellNum() - 1);
            sheet.getPrintSetup().setPaperSize(lastColumn > 14 ? PrintSetup.A3_PAPERSIZE : PrintSetup.A4_PAPERSIZE);
            if (sheet.getLastRowNum() >= 0 && sheet.getPhysicalNumberOfRows() > 0)
                book.setPrintArea(book.getSheetIndex(sheet), 0, lastColumn, 0, sheet.getLastRowNum());
        }
        book.setActiveSheet(0);
        book.setSelectedTab(0);
    }
    private static void control(Sheet sheet, int row, String label, long count, String action, Style s) {
        text(sheet, row, 0, label, s.body); number(sheet, row, 1, BigDecimal.valueOf(count), s.integer);
        text(sheet, row, 5, count == 0 ? "Aucun cas détecté" : action, s.body);
    }
    private static BigDecimal z(BigDecimal value) { return value == null ? ZERO : value; }
    private static BigDecimal sum(List<Entry> entries, Function<Entry, BigDecimal> metric) {
        return entries.stream().map(metric).reduce(ZERO, BigDecimal::add);
    }
    private static Set<Integer> employeeIds(List<Entry> entries) {
        Set<Integer> ids = new HashSet<>(); entries.forEach(e -> ids.add(e.employee)); return ids;
    }
    private static int distinct(List<Entry> entries) { return employeeIds(entries).size(); }
    private static Cell cell(Sheet sheet, int row, int col, CellStyle style) {
        Row r = sheet.getRow(row); if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col); if (c == null) c = r.createCell(col); c.setCellStyle(style); return c;
    }
    private static void text(Sheet sheet, int row, int col, String value, CellStyle style) { cell(sheet, row, col, style).setCellValue(value == null ? "" : value); }
    private static void number(Sheet sheet, int row, int col, BigDecimal value, CellStyle style) { cell(sheet, row, col, style).setCellValue(value.doubleValue()); }
    private static void title(Sheet sheet, String value, Style s) {
        note(sheet, 0, value, s); sheet.getRow(0).getCell(0).setCellStyle(s.title); sheet.getRow(0).setHeightInPoints(32);
        sheet.setDefaultRowHeightInPoints(30);
        int[] widths = {30, 18, 23, 23, 19, 40};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
        sheet.setFitToPage(true); sheet.getPrintSetup().setLandscape(true); sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
        sheet.getPrintSetup().setFitWidth((short) 1); sheet.getPrintSetup().setFitHeight((short) 0);
    }
    private static void note(Sheet sheet, int row, String value, Style s) {
        text(sheet, row, 0, value, s.body); sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 5)); sheet.getRow(row).setHeightInPoints(32);
    }
    private static void header(Sheet sheet, int row, Style s, String... labels) {
        for (int col = 0; col < labels.length; col++) text(sheet, row, col, labels[col], s.header);
        sheet.getRow(row).setHeightInPoints(32);
    }
    private static final class Style {
        final CellStyle title, header, body, money, percent, integer;
        Style(Workbook book) {
            Font font = book.createFont(); font.setFontName("Calibri"); font.setFontHeightInPoints((short) 11);
            Font white = book.createFont(); white.setFontName("Calibri"); white.setBold(true); white.setColor(IndexedColors.WHITE.getIndex());
            body = book.createCellStyle(); body.setFont(font); body.setWrapText(true); body.setVerticalAlignment(VerticalAlignment.CENTER);
            header = book.createCellStyle(); header.cloneStyleFrom(body); header.setFont(white); header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex()); header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title = book.createCellStyle(); title.cloneStyleFrom(header);
            money = book.createCellStyle(); money.cloneStyleFrom(body); money.setDataFormat(book.createDataFormat().getFormat("#,##0.00;[Red](#,##0.00)"));
            percent = book.createCellStyle(); percent.cloneStyleFrom(body); percent.setDataFormat(book.createDataFormat().getFormat("+0.0%;-0.0%;0.0%"));
            integer = book.createCellStyle(); integer.cloneStyleFrom(body); integer.setDataFormat(book.createDataFormat().getFormat("0"));
        }
    }
}
