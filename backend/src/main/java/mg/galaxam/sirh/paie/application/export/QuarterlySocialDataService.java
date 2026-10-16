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
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/** Source unique des états trimestriels : trois campagnes mensuelles validées, sans recalcul des cotisations. */
@Service
public class QuarterlySocialDataService {
    public record Employer(Integer id, String name, String nif, String stat, String address, String phone, String email) {}
    public static final class OrganismeAmounts {
        public BigDecimal base = BigDecimal.ZERO;
        public BigDecimal baseNonPlafonnee = BigDecimal.ZERO;
        public BigDecimal employee = BigDecimal.ZERO;
        public BigDecimal employer = BigDecimal.ZERO;
        public BigDecimal total() { return employee.add(employer); }
    }
    public static final class Month {
        public BigDecimal gross = BigDecimal.ZERO;
        public BigDecimal advantage = BigDecimal.ZERO;
        public BigDecimal hours = BigDecimal.ZERO;
        public BigDecimal cnapsBaseNonPlafonnee = BigDecimal.ZERO;
        public BigDecimal cnapsBase = BigDecimal.ZERO;
        public BigDecimal cnapsEmployee = BigDecimal.ZERO;
        public BigDecimal cnapsEmployer = BigDecimal.ZERO;
        public BigDecimal fmfpBaseNonPlafonnee = BigDecimal.ZERO;
        public BigDecimal fmfpBase = BigDecimal.ZERO;
        public BigDecimal fmfpEmployer = BigDecimal.ZERO;
        public BigDecimal ostieBaseNonPlafonnee = BigDecimal.ZERO;
        public BigDecimal ostieBase = BigDecimal.ZERO;
        public BigDecimal ostieEmployee = BigDecimal.ZERO;
        public BigDecimal ostieEmployer = BigDecimal.ZERO;
        public BigDecimal omsiBaseNonPlafonnee = BigDecimal.ZERO;
        public BigDecimal omsiBase = BigDecimal.ZERO;
        public BigDecimal omsiEmployee = BigDecimal.ZERO;
        public BigDecimal omsiEmployer = BigDecimal.ZERO;
        public BigDecimal cap = BigDecimal.ZERO;
        public final Map<String, OrganismeAmounts> organismes = new LinkedHashMap<>();

        public OrganismeAmounts getOrganisme(String org) {
            return organismes.computeIfAbsent(org, k -> new OrganismeAmounts());
        }
        public BigDecimal employeeFor(String org) {
            OrganismeAmounts o = organismes.get(org);
            return o == null ? BigDecimal.ZERO : o.employee;
        }
        public BigDecimal employerFor(String org) {
            OrganismeAmounts o = organismes.get(org);
            return o == null ? BigDecimal.ZERO : o.employer;
        }
        public BigDecimal baseFor(String org) {
            OrganismeAmounts o = organismes.get(org);
            return o == null ? BigDecimal.ZERO : o.base;
        }
        public BigDecimal baseNonPlafonneeFor(String org) {
            OrganismeAmounts o = organismes.get(org);
            return o == null ? BigDecimal.ZERO : o.baseNonPlafonnee;
        }
        public boolean hasOrg(String org) {
            OrganismeAmounts o = organismes.get(org);
            return o != null && o.total().signum() != 0;
        }
    }
    public static BigDecimal cnapsNonPlafonnee(Month m) {
        if (m.cnapsBaseNonPlafonnee.signum() > 0) return m.cnapsBaseNonPlafonnee;
        if (m.cnapsBase.signum() > 0) return m.cnapsBase;
        return m.gross;
    }
    public static BigDecimal ostieNonPlafonnee(Month m) {
        if (m.ostieBaseNonPlafonnee.signum() > 0) return m.ostieBaseNonPlafonnee;
        if (m.ostieBase.signum() > 0) return m.ostieBase;
        return m.gross;
    }
    public static BigDecimal omsiNonPlafonnee(Month m) {
        if (m.omsiBaseNonPlafonnee.signum() > 0) return m.omsiBaseNonPlafonnee;
        if (m.omsiBase.signum() > 0) return m.omsiBase;
        return m.gross;
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
        public boolean hasOrganisme(String org) {
            return sum(m -> {
                OrganismeAmounts o = m.organismes.get(org);
                return o == null ? BigDecimal.ZERO : o.total();
            }).signum() != 0;
        }
    }
    public record Data(int year, int quarter, String[] months, Employer employer, List<Person> people,
                       List<String> distinctOrganismes, boolean hasUnconfigured) {
        public Data(int year, int quarter, String[] months, Employer employer, List<Person> people) {
            this(year, quarter, months, employer, people, computeDistinctOrganismes(people), computeHasUnconfigured(people));
        }
        public String label() { return "T" + quarter + "_" + year; }
        public List<Person> cnaps() { return people.stream().filter(Person::hasCnaps).toList(); }
        public List<Person> fmfp() { return people.stream().filter(Person::hasFmfp).toList(); }
        public List<Person> ostie() { return people.stream().filter(Person::hasOstie).toList(); }
        public List<Person> omsi() { return people.stream().filter(Person::hasOmsi).toList(); }
        public List<Person> forOrganisme(String org) {
            return people.stream().filter(p -> p.hasOrganisme(org)).toList();
        }
    }

    public static List<String> computeDistinctOrganismes(List<Person> people) {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (Person p : people) {
            for (Month m : p.months) {
                for (Map.Entry<String, OrganismeAmounts> e : m.organismes.entrySet()) {
                    if (e.getValue().total().signum() != 0 || e.getValue().base.signum() != 0) {
                        set.add(e.getKey());
                    }
                }
            }
        }
        List<String> list = new ArrayList<>(set);
        list.sort((a, b) -> {
            if (a.equals(b)) return 0;
            if (a.equals(EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME)) return 1;
            if (b.equals(EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME)) return -1;
            if ("CNaPS".equalsIgnoreCase(a)) return -1;
            if ("CNaPS".equalsIgnoreCase(b)) return 1;
            if ("FMFP".equalsIgnoreCase(a)) return -1;
            if ("FMFP".equalsIgnoreCase(b)) return 1;
            return a.compareToIgnoreCase(b);
        });
        return list;
    }

    public static boolean computeHasUnconfigured(List<Person> people) {
        return people.stream().anyMatch(p -> p.hasOrganisme(EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME));
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
                    PayrollExportEligibility.requireFinalPeriod(status, rs.getString("code"));
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
        Map<String, String> registeredOrganismes = loadOrganismes(connection);
        Map<Integer, Person> people = new LinkedHashMap<>();
        Employer employer = null;
        for (int index = 0; index < 3; index++) {
            Period period = periods.get(months[index]);
            List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(period.id(), connection);
            if (bulletins.isEmpty()) throw new IllegalStateException("Aucun bulletin pour " + period.code() + ".");
            Map<Integer, List<BulletinPaieLigne>> lignesParBulletin = BulletinPaieLigne.listerParBulletins(
                    bulletins.stream().map(BulletinPaie::getIdBulletin).toList(), connection);
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(connection, period.end());
            BigDecimal cap = config == null ? BigDecimal.ZERO : zero(config.getPlafondCotisationMensuel());
            for (BulletinPaie b : bulletins) {
                PayrollExportEligibility.requireFinalBulletin(b.getStatut(), period.code());
                PersonMetadata metadata = metadata(b.getIdBulletin(), b.getIdEmploye(), period.start(), period.end(), connection);
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
                List<BulletinPaieLigne> lines = lignesParBulletin.getOrDefault(b.getIdBulletin(), List.of());
                BigDecimal nonCotisableGains = lines.stream()
                        .filter(l -> "GAIN".equals(l.getSens()) && !l.isSoumisCotisation())
                        .map(l -> zero(l.getMontantSalarial()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal uncappedSocialBase = zero(b.getTotalGainsBruts()).subtract(nonCotisableGains).max(BigDecimal.ZERO);

                BigDecimal[] bases = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                Map<String, BigDecimal> orgBases = new LinkedHashMap<>();
                for (BulletinPaieLigne line : lines) {
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
                        bases[3] = bases[3].max(base);
                    } else if (code.contains("OSTIE")) {
                        month.ostieEmployee = month.ostieEmployee.add(salariale);
                        month.ostieEmployer = month.ostieEmployer.add(patronale);
                        bases[1] = bases[1].max(base);
                    } else if (code.contains("OMSI")) {
                        month.omsiEmployee = month.omsiEmployee.add(salariale);
                        month.omsiEmployer = month.omsiEmployer.add(patronale);
                        bases[2] = bases[2].max(base);
                    }
                    if (isSocial(line) && (salariale.signum() != 0 || patronale.signum() != 0)) {
                        String org = EtatSocialMensuelExportService.resolveOrganisme(line, registeredOrganismes);
                        OrganismeAmounts amounts = month.organismes.computeIfAbsent(org, ignored -> new OrganismeAmounts());
                        amounts.employee = amounts.employee.add(salariale);
                        amounts.employer = amounts.employer.add(patronale);
                        BigDecimal currentBase = orgBases.getOrDefault(org, BigDecimal.ZERO);
                        orgBases.put(org, currentBase.max(base));
                    }
                }
                if (bases[0].signum() > 0 || month.cnapsEmployee.add(month.cnapsEmployer).signum() > 0) {
                    month.cnapsBase = month.cnapsBase.add(bases[0]);
                    month.cnapsBaseNonPlafonnee = month.cnapsBaseNonPlafonnee.add(uncappedSocialBase.max(bases[0]));
                }
                if (bases[3].signum() > 0 || month.fmfpEmployer.signum() > 0) {
                    month.fmfpBase = month.fmfpBase.add(bases[3]);
                    month.fmfpBaseNonPlafonnee = month.fmfpBaseNonPlafonnee.add(uncappedSocialBase.max(bases[3]));
                }
                if (bases[1].signum() > 0 || month.ostieEmployee.add(month.ostieEmployer).signum() > 0) {
                    month.ostieBase = month.ostieBase.add(bases[1]);
                    month.ostieBaseNonPlafonnee = month.ostieBaseNonPlafonnee.add(uncappedSocialBase.max(bases[1]));
                }
                if (bases[2].signum() > 0 || month.omsiEmployee.add(month.omsiEmployer).signum() > 0) {
                    month.omsiBase = month.omsiBase.add(bases[2]);
                    month.omsiBaseNonPlafonnee = month.omsiBaseNonPlafonnee.add(uncappedSocialBase.max(bases[2]));
                }
                for (Map.Entry<String, OrganismeAmounts> entry : month.organismes.entrySet()) {
                    String org = entry.getKey();
                    OrganismeAmounts amounts = entry.getValue();
                    BigDecimal maxBase = orgBases.getOrDefault(org, BigDecimal.ZERO);
                    if (maxBase.signum() > 0 || amounts.total().signum() > 0) {
                        amounts.base = amounts.base.add(maxBase);
                        amounts.baseNonPlafonnee = amounts.baseNonPlafonnee.add(uncappedSocialBase.max(maxBase));
                    }
                }
            }
        }
        List<Person> ordered = new ArrayList<>(people.values());
        ordered.sort(Comparator.comparing(p -> n(p.matricule)));
        return new Data(year, quarter, months, employer, ordered);
    }

    private record PersonMetadata(String name, String firstNames, String sex, LocalDate birth,
                                  String phone, String email, Employer employer) {}
    private static PersonMetadata metadata(int bulletinId, int employeeId, LocalDate start, LocalDate end,
            Connection c) throws Exception {
        var snapshot = new PayrollEmployerSnapshotService().find(bulletinId, c);
        var company = snapshot == null
                ? new mg.galaxam.sirh.contrat.application.EmployerIdentityContextResolver()
                        .resolve(employeeId, start, end, c) : null;
        String sql = "SELECT nom,prenom,sexe,date_naissance,telephone,email FROM employe WHERE id_employe=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Salarié introuvable : " + employeeId);
                Date birth = rs.getDate("date_naissance");
                Employer employer = snapshot != null
                        ? new Employer(snapshot.id(), snapshot.name(), snapshot.nif(), snapshot.stat(),
                                snapshot.address(), snapshot.phone(), snapshot.email())
                        : new Employer(company.id(), company.name(), company.nif(), company.stat(),
                                company.address(), company.phone(), company.email());
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
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
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
                BigDecimal declaredSalary = cnapsNonPlafonnee(v).subtract(v.advantage);
                number(target, col, declaredSalary); number(target, col + 1, v.advantage);
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
            number(employer, 22, col, BigDecimal.valueOf(people.stream().filter(p -> cnapsNonPlafonnee(p.months[monthIndex]).signum() > 0 || p.months[monthIndex].fmfpEmployer.signum() > 0).count()));
            number(employer, 23, col, sum(people, p -> cnapsNonPlafonnee(p.months[monthIndex])));
            number(employer, 24, col, sum(people, p -> p.months[monthIndex].cnapsEmployer));
            number(employer, 25, col, sum(people, p -> p.months[monthIndex].cnapsEmployee));
            number(employer, 26, col, sum(people, p -> p.months[monthIndex].fmfpEmployer));
        }
        formula(employer, 23, 5, "SUM(C24:E24)");
        formula(employer, 24, 5, "SUM(C25:E25)");
        formula(employer, 25, 5, "SUM(C26:E26)");
        formula(employer, 26, 5, "SUM(C27:E27)");
        formula(employer, 27, 5, "SUM(F25:F27)");
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
                BigDecimal declaredSalary = cnapsNonPlafonnee(v);
                string(target, 0, data.months()[m].replace("-", ""));
                string(target, 1, p.name); string(target, 2, p.firstNames);
                string(target, 3, p.cnaps); string(target, 4, p.matricule);
                date(target, 5, inQuarter(p.hired, data) ? p.hired : null);
                date(target, 6, inQuarter(p.left, data) ? p.left : null);
                number(target, 7, declaredSalary.subtract(v.advantage)); number(target, 8, v.advantage);
                number(target, 9, v.hours); number(target, 10, declaredSalary);
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
            for (int m = 0; m < 3; m++) number(target, 11 + m, ostieNonPlafonnee(p.months[m]));
            number(target, 14, p.sum(QuarterlySocialDataService::ostieNonPlafonnee)); number(target, 15, p.sum(v -> v.ostieBase));
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
            number(recap, 23, col, sum(people, p -> ostieNonPlafonnee(p.months[monthIndex])));
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
            number(employer, 21, col, sum(people, p -> omsiNonPlafonnee(p.months[monthIndex])));
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
            for (int m = 0; m < 3; m++) number(target, 8 + m, omsiNonPlafonnee(p.months[m]));
            number(target, 11, p.sum(QuarterlySocialDataService::omsiNonPlafonnee));
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
            byte[] navy = new byte[]{7, 50, 74};                          // #07324A --color-brand-900
            byte[] totalBg = new byte[]{(byte)225, (byte)237, (byte)242}; // #E1EDF2 --color-brand-100

            // Styles
            Font titleFont = book.createFont(); titleFont.setBold(true); titleFont.setFontHeightInPoints((short) 14);
            CellStyle titleStyle = book.createCellStyle(); titleStyle.setFont(titleFont);

            Font mutedFont = book.createFont(); mutedFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            CellStyle mutedStyle = book.createCellStyle(); mutedStyle.setFont(mutedFont);

            Font warnFont = book.createFont(); warnFont.setColor(IndexedColors.DARK_RED.getIndex()); warnFont.setBold(true);
            CellStyle warningStyle = book.createCellStyle(); warningStyle.setFont(warnFont);

            Font headFont = book.createFont(); headFont.setBold(true); headFont.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle headStyle = book.createCellStyle(); headStyle.setFont(headFont);
            headStyle.setFillForegroundColor(new XSSFColor(navy, null)); headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headStyle.setBorderBottom(BorderStyle.THIN); headStyle.setBorderTop(BorderStyle.THIN);
            headStyle.setBorderLeft(BorderStyle.THIN); headStyle.setBorderRight(BorderStyle.THIN);

            Font totalFont = book.createFont(); totalFont.setBold(true);
            XSSFCellStyle totalStyle = book.createCellStyle(); totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(new XSSFColor(totalBg, null)); totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setBorderBottom(BorderStyle.THIN); totalStyle.setBorderTop(BorderStyle.THIN);
            totalStyle.setBorderLeft(BorderStyle.THIN); totalStyle.setBorderRight(BorderStyle.THIN);
            XSSFCellStyle totalMoney = book.createCellStyle(); totalMoney.cloneStyleFrom(totalStyle);
            totalMoney.setDataFormat(book.createDataFormat().getFormat("#,##0.00")); totalMoney.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle textStyle = book.createCellStyle();
            textStyle.setBorderBottom(BorderStyle.THIN); textStyle.setBorderTop(BorderStyle.THIN);
            textStyle.setBorderLeft(BorderStyle.THIN); textStyle.setBorderRight(BorderStyle.THIN);
            textStyle.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            textStyle.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            textStyle.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            textStyle.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());

            CellStyle moneyStyle = book.createCellStyle(); moneyStyle.cloneStyleFrom(textStyle);
            moneyStyle.setDataFormat(book.createDataFormat().getFormat("#,##0.00")); moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            Sheet summary = book.createSheet("Synthèse");
            Sheet detail = book.createSheet("Par salarié et mois");
            summary.setDisplayGridlines(false); detail.setDisplayGridlines(false);
            cell(summary, 0, 0, titleStyle);
            string(summary, 0, 0, "CONSOLIDATION SOCIALE · " + data.label().replace('_', ' '));
            cell(summary, 1, 0, mutedStyle);
            string(summary, 1, 0, data.employer().name() + " · contrôle interne trimestriel multi-organismes");

            int summaryHeaderRow = 3;
            if (data.hasUnconfigured()) {
                cell(summary, 2, 0, warningStyle);
                string(summary, 2, 0, "⚠️ AVERTISSEMENT : Des cotisations sans organisme configuré ont été détectées et regroupées sous '" + EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME + "'.");
                summaryHeaderRow = 4;
            }

            String[] heads = {"Organisme", "Salariés", "Part salarié (Ar)", "Part employeur (Ar)", "Total (Ar)"};
            for (int i = 0; i < heads.length; i++) {
                Cell c = cell(summary, summaryHeaderRow, i, headStyle);
                c.setCellValue(heads[i]);
            }
            summary.getRow(summaryHeaderRow).setHeightInPoints(26);

            List<String> distinctOrgs = data.distinctOrganismes();
            int curRow = summaryHeaderRow + 1;
            for (String org : distinctOrgs) {
                List<Person> orgPeople = data.forOrganisme(org);
                BigDecimal employee = BigDecimal.ZERO;
                BigDecimal employer = BigDecimal.ZERO;
                for (Person p : orgPeople) {
                    for (Month m : p.months) {
                        OrganismeAmounts oa = m.organismes.get(org);
                        if (oa != null) {
                            employee = employee.add(oa.employee);
                            employer = employer.add(oa.employer);
                        }
                    }
                }
                Cell c0 = cell(summary, curRow, 0, textStyle); c0.setCellValue(org);
                Cell c1 = cell(summary, curRow, 1, textStyle); c1.setCellValue(orgPeople.size());
                Cell c2 = cell(summary, curRow, 2, moneyStyle); c2.setCellValue(employee.doubleValue());
                Cell c3 = cell(summary, curRow, 3, moneyStyle); c3.setCellValue(employer.doubleValue());
                Cell c4 = cell(summary, curRow, 4, moneyStyle); c4.setCellValue(employee.add(employer).doubleValue());
                curRow++;
            }

            Cell tot0 = cell(summary, curRow, 0, totalStyle); tot0.setCellValue("TOTAL");
            Cell tot1 = cell(summary, curRow, 1, totalStyle); tot1.setCellValue("");
            for (int col = 2; col <= 4; col++) {
                Cell tc = cell(summary, curRow, col, totalMoney);
                if (distinctOrgs.isEmpty()) {
                    tc.setCellValue(0);
                } else {
                    tc.setCellFormula("SUM(" + CellReference.convertNumToColString(col) + (summaryHeaderRow + 2) + ":" + CellReference.convertNumToColString(col) + curRow + ")");
                }
            }

            cell(summary, curRow + 2, 0, mutedStyle);
            string(summary, curRow + 2, 0, "Source : cotisations constatées sur les bulletins validés ou clôturés des 3 mois.");
            cell(summary, curRow + 3, 0, mutedStyle);
            string(summary, curRow + 3, 0, "Un salarié affilié à plusieurs organismes est comptabilisé pour chaque organisme correspondant.");
            if (data.hasUnconfigured()) {
                cell(summary, curRow + 4, 0, warningStyle);
                string(summary, curRow + 4, 0, "Lignes sans organisme configuré détectées : vérifier la configuration des types et sites de cotisation.");
            }
            for (int col = 0; col < 5; col++) summary.setColumnWidth(col, col == 0 ? 8500 : 6200);

            // DETAIL SHEET
            cell(detail, 0, 0, titleStyle);
            string(detail, 0, 0, "DÉTAIL PAR SALARIÉ ET PAR MOIS · " + data.label().replace('_', ' '));
            int detailHeaderRow = 2;
            if (data.hasUnconfigured()) {
                cell(detail, 1, 0, warningStyle);
                string(detail, 1, 0, "⚠️ AVERTISSEMENT : Des colonnes comportent des montants sous '" + EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME + "'.");
                detailHeaderRow = 3;
            }

            List<String> detailHeadList = new ArrayList<>();
            detailHeadList.addAll(List.of("Mois", "Matricule", "Nom", "Prénoms", "N° CNaPS", "Brut", "Heures"));
            for (String org : distinctOrgs) {
                detailHeadList.add("Base " + org + " non plaf.");
                detailHeadList.add("Base " + org + " plaf.");
                detailHeadList.add(org + " salarié");
                detailHeadList.add(org + " employeur");
            }

            for (int i = 0; i < detailHeadList.size(); i++) {
                Cell dc = cell(detail, detailHeaderRow, i, headStyle);
                dc.setCellValue(detailHeadList.get(i));
            }
            detail.getRow(detailHeaderRow).setHeightInPoints(26);

            int dRow = detailHeaderRow + 1;
            for (Person p : data.people()) {
                for (int monthIndex = 0; monthIndex < 3; monthIndex++) {
                    Month m = p.months[monthIndex];
                    boolean hasAnyOrg = distinctOrgs.stream().anyMatch(org -> {
                        OrganismeAmounts oa = m.organismes.get(org);
                        return oa != null && oa.total().signum() != 0;
                    });
                    if (m.gross.signum() == 0 && !hasAnyOrg) continue;

                    Cell c0 = cell(detail, dRow, 0, textStyle); c0.setCellValue(data.months()[monthIndex]);
                    Cell c1 = cell(detail, dRow, 1, textStyle); c1.setCellValue(p.matricule);
                    Cell c2 = cell(detail, dRow, 2, textStyle); c2.setCellValue(p.name);
                    Cell c3 = cell(detail, dRow, 3, textStyle); c3.setCellValue(p.firstNames);
                    Cell c4 = cell(detail, dRow, 4, textStyle); c4.setCellValue(p.cnaps);

                    cell(detail, dRow, 5, moneyStyle).setCellValue(m.gross.doubleValue());
                    cell(detail, dRow, 6, moneyStyle).setCellValue(m.hours.doubleValue());

                    int colIdx = 7;
                    for (String org : distinctOrgs) {
                        OrganismeAmounts oa = m.organismes.get(org);
                        BigDecimal baseNp = oa != null ? oa.baseNonPlafonnee : BigDecimal.ZERO;
                        BigDecimal basePl = oa != null ? oa.base : BigDecimal.ZERO;
                        BigDecimal sal = oa != null ? oa.employee : BigDecimal.ZERO;
                        BigDecimal pat = oa != null ? oa.employer : BigDecimal.ZERO;

                        cell(detail, dRow, colIdx++, moneyStyle).setCellValue(baseNp.doubleValue());
                        cell(detail, dRow, colIdx++, moneyStyle).setCellValue(basePl.doubleValue());
                        cell(detail, dRow, colIdx++, moneyStyle).setCellValue(sal.doubleValue());
                        cell(detail, dRow, colIdx++, moneyStyle).setCellValue(pat.doubleValue());
                    }
                    dRow++;
                }
            }

            Cell dTotLabel = cell(detail, dRow, 3, totalStyle); dTotLabel.setCellValue("TOTAL");
            for (int col = 0; col < detailHeadList.size(); col++) {
                if (col == 3) continue;
                Cell c = cell(detail, dRow, col);
                if (col < 5) {
                    c.setCellStyle(totalStyle);
                    c.setCellValue("");
                } else {
                    c.setCellStyle(totalMoney);
                    if (dRow == detailHeaderRow + 1) {
                        c.setCellValue(0);
                    } else {
                        String letter = CellReference.convertNumToColString(col);
                        c.setCellFormula("SUM(" + letter + (detailHeaderRow + 2) + ":" + letter + dRow + ")");
                    }
                }
            }

            detail.createFreezePane(5, detailHeaderRow + 1);
            for (int col = 0; col < detailHeadList.size(); col++) {
                int width = switch (col) {
                    case 0 -> 3800;
                    case 1 -> 3500;
                    case 2, 3 -> 7500;
                    case 4 -> 4200;
                    case 5 -> 5000;
                    case 6 -> 3500;
                    default -> 4800;
                };
                detail.setColumnWidth(col, width);
            }

            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(output);
            return output.toByteArray();
        }
    }

    public static boolean isSocial(BulletinPaieLigne line) {
        String code = n(line.getCodeRubrique()).toUpperCase(Locale.ROOT);
        String category = n(line.getCategorie()).toUpperCase(Locale.ROOT);
        return code.startsWith("COTISATION_") || category.startsWith("COTISATION_");
    }

    public static Map<String, String> loadOrganismes(Connection c) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        String sql = "SELECT code, libelle FROM ref_organisme_social";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("code");
                String upper = code.toUpperCase(Locale.ROOT);
                String display = "CNAPS".equalsIgnoreCase(code) ? "CNaPS" : code;
                map.put(upper, display);
            }
        }
        return map;
    }

    private static Cell cell(Sheet sheet, int row, int col, CellStyle style) {
        Cell c = cell(sheet, row, col);
        c.setCellStyle(style);
        return c;
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
