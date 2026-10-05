package mg.galaxam.sirh.paie.application.export;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** Source unique des états trimestriels : trois campagnes mensuelles validées, sans recalcul des cotisations. */
@Service
public class QuarterlySocialDataService {
    public record Employer(Integer id, String name, String nif, String stat, String address, String phone, String email) {}
    public static final class Month {
        public BigDecimal gross = BigDecimal.ZERO;
        public BigDecimal advantage = BigDecimal.ZERO;
        public BigDecimal hours = BigDecimal.ZERO;
        public BigDecimal cnapsBase = BigDecimal.ZERO;
        public BigDecimal cnapsEmployee = BigDecimal.ZERO;
        public BigDecimal cnapsEmployer = BigDecimal.ZERO;
        public BigDecimal fmfpEmployer = BigDecimal.ZERO;
        public BigDecimal ostieBase = BigDecimal.ZERO;
        public BigDecimal ostieEmployee = BigDecimal.ZERO;
        public BigDecimal ostieEmployer = BigDecimal.ZERO;
        public BigDecimal omsiBase = BigDecimal.ZERO;
        public BigDecimal omsiEmployee = BigDecimal.ZERO;
        public BigDecimal omsiEmployer = BigDecimal.ZERO;
        public BigDecimal cap = BigDecimal.ZERO;
    }
    public static final class Person {
        public final int id;
        public String matricule, cnaps, cin, name, firstNames, sex, phone, email, position;
        public LocalDate birth, hired, left;
        public final Month[] months = {new Month(), new Month(), new Month()};
        public Person(int id) { this.id = id; }
        public BigDecimal sum(java.util.function.Function<Month, BigDecimal> getter) {
            return Arrays.stream(months).map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        public boolean hasCnaps() { return sum(m -> m.cnapsEmployee.add(m.cnapsEmployer)).signum() != 0; }
        public boolean hasFmfp() { return sum(m -> m.fmfpEmployer).signum() != 0; }
        public boolean hasOstie() { return sum(m -> m.ostieEmployee.add(m.ostieEmployer)).signum() != 0; }
        public boolean hasOmsi() { return sum(m -> m.omsiEmployee.add(m.omsiEmployer)).signum() != 0; }
    }
    public record Data(int year, int quarter, String[] months, Employer employer, List<Person> people) {
        public String label() { return "T" + quarter + "_" + year; }
        public List<Person> cnaps() { return people.stream().filter(Person::hasCnaps).toList(); }
        public List<Person> fmfp() { return people.stream().filter(Person::hasFmfp).toList(); }
        public List<Person> ostie() { return people.stream().filter(Person::hasOstie).toList(); }
        public List<Person> omsi() { return people.stream().filter(Person::hasOmsi).toList(); }
    }
    private record Period(int id, String code, LocalDate start, LocalDate end) {}

    public Data load(int year, int quarter, Connection connection) throws Exception {
        if (year < 2000 || year > 2100 || quarter < 1 || quarter > 4) throw new IllegalArgumentException("Trimestre invalide.");
        String[] months = new String[3];
        for (int i = 0; i < 3; i++) months[i] = "%04d-%02d".formatted(year, (quarter - 1) * 3 + i + 1);
        Map<String, Period> periods = new LinkedHashMap<>();
        String sql = "SELECT p.id_periode_paie, p.code, p.date_debut, p.date_fin, s.code AS statut " +
                "FROM periode_paie p JOIN ref_statut_periode_paie s ON s.id_statut_periode = p.id_statut_periode " +
                "WHERE p.code IN (?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < 3; i++) ps.setString(i + 1, months[i]);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("statut");
                    if (!"PAIE_VALIDEE".equals(status) && !"CLOTUREE".equals(status)) {
                        throw new IllegalStateException("La paie " + rs.getString("code") + " doit être validée avant la consolidation.");
                    }
                    Period previous = periods.put(rs.getString("code"), new Period(rs.getInt("id_periode_paie"),
                            rs.getString("code"), rs.getDate("date_debut").toLocalDate(),
                            rs.getDate("date_fin").toLocalDate()));
                    if (previous != null) throw new IllegalStateException("Plusieurs campagnes portent le code " + rs.getString("code") + ".");
                }
            }
        }
        for (String month : months) if (!periods.containsKey(month)) {
            throw new IllegalStateException("La période " + month + " est absente. Qualifier ce mois avant l'export trimestriel.");
        }
        Map<Integer, Person> people = new LinkedHashMap<>();
        Employer employer = null;
        for (int index = 0; index < 3; index++) {
            Period period = periods.get(months[index]);
            List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(period.id(), connection);
            if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour " + period.code() + ".");
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(connection, period.end());
            BigDecimal cap = config == null ? BigDecimal.ZERO : zero(config.getPlafondCotisationMensuel());
            for (BulletinPaie b : bulletins) {
                if (!"VALIDE".equals(b.getStatut()) && !"CLOTURE".equals(b.getStatut())) {
                    throw new IllegalStateException("Bulletin non validé dans " + period.code() + ".");
                }
                PersonMetadata metadata = metadata(b.getIdEmploye(), period.start(), period.end(), connection);
                if (metadata.employer() == null || metadata.employer().id() == null) {
                    throw new IllegalStateException("Filiale non renseignée pour le salarié " + b.getMatricule() + ".");
                }
                if (employer != null && !employer.id().equals(metadata.employer().id())) {
                    throw new IllegalStateException("Plusieurs filiales dans ce trimestre : les modèles doivent être produits par employeur.");
                }
                employer = metadata.employer();
                Person person = people.computeIfAbsent(b.getIdEmploye(), Person::new);
                person.matricule = b.getMatricule(); person.cnaps = b.getNumeroCnaps(); person.cin = b.getCinNumero();
                person.name = metadata.name(); person.firstNames = metadata.firstNames(); person.sex = metadata.sex();
                person.birth = metadata.birth(); person.phone = metadata.phone(); person.email = metadata.email();
                person.position = b.getIntitulePoste();
                if (person.hired == null && b.getDateEmbauche() != null) person.hired = b.getDateEmbauche().toLocalDate();
                Month month = person.months[index];
                month.gross = month.gross.add(zero(b.getTotalGainsBruts()));
                month.hours = month.hours.add(zero(b.getHeuresNormales()));
                month.cap = cap;
                BigDecimal[] bases = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                for (BulletinPaieLigne line : BulletinPaieLigne.listerParBulletin(b.getIdBulletin(), connection)) {
                    String code = n(line.getCodeRubrique()).toUpperCase(Locale.ROOT);
                    BigDecimal salariale = zero(line.getMontantSalarial());
                    BigDecimal patronale = zero(line.getMontantPatronal());
                    BigDecimal base = zero(line.getBaseCalcul());
                    if ("GAIN".equals(line.getSens()) && code.contains("AVANTAGE")) month.advantage = month.advantage.add(salariale);
                    if (code.contains("CNAPS")) {
                        month.cnapsEmployee = month.cnapsEmployee.add(salariale);
                        month.cnapsEmployer = month.cnapsEmployer.add(patronale);
                        bases[0] = bases[0].max(base);
                    } else if (code.contains("FMFP")) {
                        month.fmfpEmployer = month.fmfpEmployer.add(patronale);
                    } else if (code.contains("OSTIE")) {
                        month.ostieEmployee = month.ostieEmployee.add(salariale);
                        month.ostieEmployer = month.ostieEmployer.add(patronale);
                        bases[1] = bases[1].max(base);
                    } else if (code.contains("OMSI")) {
                        month.omsiEmployee = month.omsiEmployee.add(salariale);
                        month.omsiEmployer = month.omsiEmployer.add(patronale);
                        bases[2] = bases[2].max(base);
                    }
                }
                month.cnapsBase = month.cnapsBase.add(bases[0]);
                month.ostieBase = month.ostieBase.add(bases[1]);
                month.omsiBase = month.omsiBase.add(bases[2]);
            }
        }
        List<Person> ordered = new ArrayList<>(people.values());
        ordered.sort(Comparator.comparing(p -> n(p.matricule)));
        return new Data(year, quarter, months, employer, ordered);
    }

    private record PersonMetadata(String name, String firstNames, String sex, LocalDate birth,
                                  String phone, String email, Employer employer) {}
    private static PersonMetadata metadata(int employeeId, LocalDate start, LocalDate end,
            Connection c) throws Exception {
        var company = new mg.galaxam.sirh.contrat.application.EmployerIdentityContextResolver()
                .resolve(employeeId, start, end, c);
        String sql = "SELECT nom,prenom,sexe,date_naissance,telephone,email FROM employe WHERE id_employe=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Salarié introuvable : " + employeeId);
                Date birth = rs.getDate("date_naissance");
                Employer employer = new Employer(company.id(), company.name(), company.nif(),
                        company.stat(), company.address(), company.phone(), company.email());
                return new PersonMetadata(rs.getString("nom"), rs.getString("prenom"), rs.getString("sexe"),
                        birth == null ? null : birth.toLocalDate(), rs.getString("telephone"), rs.getString("email"), employer);
            }
        }
    }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String n(String value) { return value == null ? "" : value; }

    // Format complémentaire du même export ; partage les données et la connexion.
    public byte[] render(Data data, String type) throws Exception {
        if ("consolidation".equals(type)) return consolidation(data);
        String path = switch (type) {
            case "cnaps" -> "/templates/trimestriel/DNWEB-MODELE CNAPS.xlsx";
            case "fmfp" -> "/templates/trimestriel/DNS_FMFP.xls";
            case "ostie" -> "/templates/trimestriel/DNS_OSTIE.xlsx";
            case "omsi" -> "/templates/trimestriel/DNS_OMSI.xlsx";
            default -> throw new IllegalArgumentException("Type d'export trimestriel inconnu.");
        };
        List<Person> recipients = switch (type) {
            case "cnaps" -> data.cnaps();
            case "fmfp" -> data.people().stream().filter(p -> p.hasCnaps() || p.hasFmfp()).toList();
            case "ostie" -> data.ostie();
            default -> data.omsi();
        };
        if (recipients.isEmpty()) throw new IllegalStateException("Aucune cotisation " + type.toUpperCase(Locale.ROOT) + " dans ce trimestre.");
        try (InputStream input = getClass().getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Modèle trimestriel introuvable : " + path);
            try (Workbook workbook = WorkbookFactory.create(input); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                switch (type) {
                    case "cnaps" -> cnaps(workbook, data, recipients);
                    case "fmfp" -> fmfp(workbook, data, recipients);
                    case "ostie" -> ostie(workbook, data, recipients);
                    case "omsi" -> omsi(workbook, data, recipients);
                    default -> throw new IllegalArgumentException("Type d'export trimestriel inconnu.");
                }
                workbook.setForceFormulaRecalculation(true);
                workbook.write(output);
                return output.toByteArray();
            }
        }
    }

    private static void cnaps(Workbook book, Data data, List<Person> people) {
        Sheet sheet = book.getSheetAt(0);
        // Le nom d'onglet fourni peut porter l'identifiant CNaPS de l'employeur.
        CellStyle[] styles = styles(sheet.getRow(2), 22);
        for (int row = 2; row <= Math.max(sheet.getLastRowNum(), people.size() + 1); row++) {
            Row target = writableRow(sheet, row, styles);
            if (row - 2 >= people.size()) continue;
            Person p = people.get(row - 2);
            string(target, 0, p.cnaps); string(target, 1, p.cin); string(target, 2, p.name);
            string(target, 3, p.firstNames); string(target, 4, p.matricule);
            date(target, 5, p.hired); date(target, 6, p.left);
            for (int m = 0; m < 3; m++) {
                Month v = p.months[m]; int col = 7 + 4 * m;
                number(target, col, v.gross.subtract(v.advantage)); number(target, col + 1, v.advantage);
                number(target, col + 2, v.hours);
                if (v.cap.signum() > 0) number(target, col + 3, v.cap);
            }
            string(target, 20, p.phone); string(target, 21, p.email);
        }
    }

    private static void fmfp(Workbook book, Data data, List<Person> people) {
        Sheet employer = book.getSheetAt(0);
        // Les numéros d'adhérent et données de paiement restent vides faute de source vérifiée.
        blank(employer, 3, 2); string(employer, 4, 2, data.employer().name());
        string(employer, 5, 2, data.employer().nif()); string(employer, 7, 2, data.employer().address());
        string(employer, 8, 2, data.employer().email());
        string(employer, 11, 2, data.label().replace('_', ' '));
        blank(employer, 12, 2); blank(employer, 13, 2);
        for (int m = 0; m < 3; m++) {
            final int monthIndex = m;
            int col = 2 + m;
            string(employer, 19, col, monthName(data.months()[m]));
            number(employer, 22, col, BigDecimal.valueOf(people.stream().filter(p -> p.months[monthIndex].gross.signum() > 0).count()));
            number(employer, 23, col, sum(people, p -> p.months[monthIndex].gross));
            number(employer, 24, col, sum(people, p -> p.months[monthIndex].cnapsEmployer));
            number(employer, 25, col, sum(people, p -> p.months[monthIndex].cnapsEmployee));
            number(employer, 26, col, sum(people, p -> p.months[monthIndex].fmfpEmployer));
        }
        for (int m = 0; m < 3; m++) {
            Sheet sheet = book.getSheetAt(m + 1);
            int first = 3;
            int capacity = m == 0 ? 2 : 16;
            int extra = Math.max(0, people.size() - capacity);
            if (extra > 0 && m == 0) sheet.shiftRows(first + capacity, sheet.getLastRowNum(), extra, true, false);
            CellStyle[] styles = styles(sheet.getRow(first), 17);
            for (int row = first; row < first + Math.max(capacity, people.size()); row++) {
                Row target = writableRow(sheet, row, styles);
                if (row - first >= people.size()) continue;
                Person p = people.get(row - first); Month v = p.months[m];
                string(target, 0, data.months()[m].replace("-", ""));
                string(target, 1, p.name); string(target, 2, p.firstNames);
                string(target, 3, p.cnaps); string(target, 4, p.matricule);
                date(target, 5, inQuarter(p.hired, data) ? p.hired : null);
                date(target, 6, inQuarter(p.left, data) ? p.left : null);
                number(target, 7, v.gross.subtract(v.advantage)); number(target, 8, v.advantage);
                number(target, 9, v.hours); number(target, 10, v.gross);
                number(target, 11, v.cnapsBase); number(target, 12, v.cnapsEmployer);
                number(target, 13, v.cnapsEmployee); number(target, 14, v.cnapsEmployer.add(v.cnapsEmployee));
                number(target, 15, v.fmfpEmployer); string(target, 16, p.cin);
            }
        }
    }

    private static void ostie(Workbook book, Data data, List<Person> people) {
        Sheet sheet = book.getSheetAt(0);
        string(sheet, 0, 16, data.label().replace('_', ' '));
        blank(sheet, 2, 8); string(sheet, 3, 8, data.employer().name());
        string(sheet, 4, 8, data.employer().address()); string(sheet, 5, 8, data.employer().phone());
        string(sheet, 5, 11, data.employer().email()); string(sheet, 6, 7, data.employer().stat());
        string(sheet, 6, 11, data.employer().nif()); blank(sheet, 9, 8);
        // Conserver le SME et le plafond/formule du modèle officiel ; seuls les champs déclaratifs sont remplis.
        int first = 14, capacity = 10, extra = Math.max(0, people.size() - capacity);
        if (extra > 0) sheet.shiftRows(first + capacity, sheet.getLastRowNum(), extra, true, false);
        CellStyle[] styles = styles(sheet.getRow(first), 18);
        for (int row = first; row < first + Math.max(capacity, people.size()); row++) {
            Row target = writableRow(sheet, row, styles);
            if (row - first >= people.size()) continue;
            Person p = people.get(row - first);
            number(target, 0, BigDecimal.valueOf(row - first + 1));
            string(target, 1, p.matricule); string(target, 2, p.name); string(target, 3, p.firstNames);
            string(target, 4, p.sex); date(target, 5, p.birth); date(target, 6, p.hired);
            date(target, 7, p.left); string(target, 8, p.position);
            string(target, 9, p.cnaps); string(target, 10, p.cin);
            for (int m = 0; m < 3; m++) number(target, 11 + m, p.months[m].gross);
            number(target, 14, p.sum(v -> v.gross)); number(target, 15, p.sum(v -> v.ostieBase));
            number(target, 16, p.sum(v -> v.ostieEmployer)); number(target, 17, p.sum(v -> v.ostieEmployee));
        }
        int totalRow = 24 + extra;
        for (int col = 11; col <= 17; col++) formula(sheet, totalRow, col,
                "SUM(" + CellReference.convertNumToColString(col) + "15:" + CellReference.convertNumToColString(col) + (14 + people.size()) + ")");

        Sheet recap = book.getSheetAt(1);
        blank(recap, 2, 2); string(recap, 3, 2, data.employer().name());
        string(recap, 4, 2, data.employer().phone()); string(recap, 5, 2, data.employer().address());
        string(recap, 6, 2, data.employer().email());
        number(recap, 9, 2, BigDecimal.valueOf(data.quarter()));
        number(recap, 10, 2, BigDecimal.valueOf(data.year()));
        blank(recap, 11, 2); blank(recap, 12, 2);
        for (int m = 0; m < 3; m++) {
            final int monthIndex = m;
            int col = 2 + m;
            string(recap, 19, col, monthName(data.months()[m]));
            number(recap, 22, col, BigDecimal.valueOf(people.stream().filter(p -> p.months[monthIndex].ostieEmployer.add(p.months[monthIndex].ostieEmployee).signum() > 0).count()));
            number(recap, 23, col, sum(people, p -> p.months[monthIndex].gross));
            number(recap, 24, col, sum(people, p -> p.months[monthIndex].ostieBase));
            number(recap, 25, col, sum(people, p -> p.months[monthIndex].ostieEmployer));
            number(recap, 26, col, sum(people, p -> p.months[monthIndex].ostieEmployee));
        }
    }

    private static void omsi(Workbook book, Data data, List<Person> people) {
        Sheet employer = book.getSheetAt(0);
        String templateNif = new DataFormatter().formatCellValue(cell(employer, 5, 2)).trim();
        String companyNif = n(data.employer().nif()).trim();
        if (!templateNif.isEmpty() && !templateNif.equals(companyNif)) {
            throw new IllegalStateException("Le NIF prérempli dans le modèle OMSI ne correspond pas à l'employeur de la campagne.");
        }
        // L'identité et le matricule de l'employeur sont déjà préremplis dans ce modèle.
        fillIfBlank(employer, 3, 2, null); fillIfBlank(employer, 4, 2, data.employer().name());
        fillIfBlank(employer, 5, 2, data.employer().nif()); fillIfBlank(employer, 6, 2, data.employer().address());
        fillIfBlank(employer, 7, 2, data.employer().email());
        string(employer, 10, 2, data.label().replace('_', ' ')); blank(employer, 11, 2);
        // Paramètres et annotations de l'organisme conservés tels que fournis.
        for (int m = 0; m < 3; m++) {
            final int monthIndex = m;
            int col = 2 + m;
            string(employer, 16, col, monthName(data.months()[m]));
            number(employer, 20, col, BigDecimal.valueOf(people.stream().filter(p -> p.months[monthIndex].omsiEmployer.add(p.months[monthIndex].omsiEmployee).signum() > 0).count()));
            number(employer, 21, col, sum(people, p -> p.months[monthIndex].omsiBase));
            number(employer, 22, col, sum(people, p -> p.months[monthIndex].omsiEmployer));
            number(employer, 23, col, sum(people, p -> p.months[monthIndex].omsiEmployee));
        }
        Sheet sheet = book.getSheetAt(1);
        book.setSheetName(1, "OMSI T" + data.quarter() + " " + data.year());
        string(sheet, 1, 9, data.quarter() + "e");
        number(sheet, 1, 11, BigDecimal.valueOf(data.year()));
        fillIfBlank(sheet, 4, 2, data.employer().name()); fillIfBlank(sheet, 5, 2, data.employer().address());
        fillIfBlank(sheet, 6, 2, "Tél " + n(data.employer().phone()));
        // Le numéro d'inscription et le plafond figurent dans le canevas et ne sont pas recalculés ici.
        for (int m = 0; m < 3; m++) string(sheet, 8, 8 + m, monthName(data.months()[m]));
        int first = 10, capacity = 5, extra = Math.max(0, people.size() - capacity);
        if (extra > 0) sheet.shiftRows(first + capacity, 34, extra, true, false);
        CellStyle[] styles = styles(sheet.getRow(first), 15);
        for (int row = first; row < first + Math.max(capacity, people.size()); row++) {
            Row target = writableRow(sheet, row, styles);
            if (row - first >= people.size()) continue;
            Person p = people.get(row - first);
            number(target, 1, BigDecimal.valueOf(row - first + 1));
            string(target, 2, p.name); string(target, 3, p.firstNames);
            string(target, 4, p.matricule); string(target, 5, p.cnaps);
            date(target, 6, inQuarter(p.hired, data) ? p.hired : null);
            date(target, 7, inQuarter(p.left, data) ? p.left : null);
            for (int m = 0; m < 3; m++) number(target, 8 + m, p.months[m].omsiBase);
            number(target, 11, p.sum(v -> v.omsiBase));
            number(target, 12, p.sum(v -> v.omsiEmployer));
            number(target, 13, p.sum(v -> v.omsiEmployee));
            number(target, 14, p.sum(v -> v.omsiEmployer.add(v.omsiEmployee)));
        }
        int totalRow = 15 + extra;
        for (int col = 8; col <= 14; col++) formula(sheet, totalRow, col,
                "SUM(" + CellReference.convertNumToColString(col) + "11:" + CellReference.convertNumToColString(col) + (10 + people.size()) + ")");
    }

    private static byte[] consolidation(Data data) throws Exception {
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet summary = book.createSheet("Synthèse");
            Sheet detail = book.createSheet("Par salarié et mois");
            summary.setDisplayGridlines(false); detail.setDisplayGridlines(false);
            string(summary, 0, 0, "CONSOLIDATION SOCIALE · " + data.label().replace('_', ' '));
            string(summary, 1, 0, data.employer().name() + " · contrôle interne avant préparation des modèles");
            String[] heads = {"Organisme", "Salariés", "Part salarié (Ar)", "Part employeur (Ar)", "Total (Ar)"};
            for (int i = 0; i < heads.length; i++) string(summary, 3, i, heads[i]);
            String[] orgs = {"CNaPS", "FMFP", "OSTIE", "OMSI"};
            List<List<Person>> lists = List.of(data.cnaps(), data.fmfp(), data.ostie(), data.omsi());
            for (int i = 0; i < 4; i++) {
                int row = i + 4;
                string(summary, row, 0, orgs[i]); number(summary, row, 1, BigDecimal.valueOf(lists.get(i).size()));
                BigDecimal employee = BigDecimal.ZERO, employer = BigDecimal.ZERO;
                for (Person p : lists.get(i)) for (Month m : p.months) {
                    employee = employee.add(switch (i) { case 0 -> m.cnapsEmployee; case 2 -> m.ostieEmployee; case 3 -> m.omsiEmployee; default -> BigDecimal.ZERO; });
                    employer = employer.add(switch (i) { case 0 -> m.cnapsEmployer; case 1 -> m.fmfpEmployer; case 2 -> m.ostieEmployer; default -> m.omsiEmployer; });
                }
                number(summary, row, 2, employee); number(summary, row, 3, employer); number(summary, row, 4, employee.add(employer));
            }
            string(summary, 8, 0, "TOTAL");
            for (int col = 2; col <= 4; col++) formula(summary, 8, col, "SUM(" + CellReference.convertNumToColString(col) + "5:" + CellReference.convertNumToColString(col) + "8)");
            for (int col = 0; col < 5; col++) summary.setColumnWidth(col, col == 0 ? 8500 : 6200);

            String[] detailHeads = {"Mois", "Matricule", "Nom", "Prénoms", "N° CNaPS", "Brut", "Heures", "Base CNaPS", "CNaPS salarié", "CNaPS employeur", "FMFP employeur", "Base OSTIE", "OSTIE salarié", "OSTIE employeur", "Base OMSI", "OMSI salarié", "OMSI employeur"};
            for (int i = 0; i < detailHeads.length; i++) string(detail, 2, i, detailHeads[i]);
            int row = 3;
            for (Person p : data.people()) for (int monthIndex = 0; monthIndex < 3; monthIndex++) {
                Month m = p.months[monthIndex];
                if (m.gross.signum() == 0 && m.cnapsEmployee.signum() == 0 && m.cnapsEmployer.signum() == 0 && m.fmfpEmployer.signum() == 0 && m.ostieEmployee.signum() == 0 && m.ostieEmployer.signum() == 0 && m.omsiEmployee.signum() == 0 && m.omsiEmployer.signum() == 0) continue;
                string(detail, row, 0, data.months()[monthIndex]); string(detail, row, 1, p.matricule);
                string(detail, row, 2, p.name); string(detail, row, 3, p.firstNames); string(detail, row, 4, p.cnaps);
                BigDecimal[] values = {m.gross, m.hours, m.cnapsBase, m.cnapsEmployee, m.cnapsEmployer, m.fmfpEmployer,
                        m.ostieBase, m.ostieEmployee, m.ostieEmployer, m.omsiBase, m.omsiEmployee, m.omsiEmployer};
                for (int i = 0; i < values.length; i++) number(detail, row, 5 + i, values[i]);
                row++;
            }
            detail.createFreezePane(5, 3);
            for (int col = 0; col < detailHeads.length; col++) detail.setColumnWidth(col, col == 2 || col == 3 ? 7500 : 4800);
            book.write(output);
            return output.toByteArray();
        }
    }

    private static CellStyle[] styles(Row row, int count) {
        CellStyle[] result = new CellStyle[count];
        if (row != null) for (int col = 0; col < count; col++) { Cell cell = row.getCell(col); if (cell != null) result[col] = cell.getCellStyle(); }
        return result;
    }
    private static Row writableRow(Sheet sheet, int index, CellStyle[] styles) {
        Row row = sheet.getRow(index); if (row == null) row = sheet.createRow(index);
        for (int col = 0; col < styles.length; col++) {
            Cell cell = row.getCell(col); if (cell == null) cell = row.createCell(col);
            cell.setBlank(); if (styles[col] != null) cell.setCellStyle(styles[col]);
        }
        return row;
    }
    private static Cell cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row); if (r == null) r = sheet.createRow(row);
        Cell c = r.getCell(col); return c == null ? r.createCell(col) : c;
    }
    private static void string(Sheet sheet, int row, int col, String value) { if (value != null && !value.isBlank()) cell(sheet, row, col).setCellValue(value); else blank(sheet, row, col); }
    private static void string(Row row, int col, String value) { if (value != null && !value.isBlank()) row.getCell(col).setCellValue(value); }
    private static void number(Sheet sheet, int row, int col, BigDecimal value) { cell(sheet, row, col).setCellValue(value.doubleValue()); }
    private static void number(Row row, int col, BigDecimal value) { row.getCell(col).setCellValue(value.doubleValue()); }
    private static void formula(Sheet sheet, int row, int col, String value) { cell(sheet, row, col).setCellFormula(value); }
    private static void blank(Sheet sheet, int row, int col) { cell(sheet, row, col).setBlank(); }
    private static void fillIfBlank(Sheet sheet, int row, int col, String value) {
        Cell target = cell(sheet, row, col);
        if (target.getCellType() == CellType.BLANK && value != null && !value.isBlank()) target.setCellValue(value);
    }
    private static void date(Row row, int col, LocalDate value) { if (value != null) row.getCell(col).setCellValue(java.sql.Date.valueOf(value)); }
    private static String monthName(String code) { return LocalDate.parse(code + "-01").getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH); }
    private static BigDecimal sum(List<Person> people, Function<Person, BigDecimal> getter) { return people.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static boolean inQuarter(LocalDate value, Data data) {
        return value != null && !value.isBefore(LocalDate.parse(data.months()[0] + "-01"))
                && value.isBefore(LocalDate.parse(data.months()[2] + "-01").plusMonths(1));
    }
}
