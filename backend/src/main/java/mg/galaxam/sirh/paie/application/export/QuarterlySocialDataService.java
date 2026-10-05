package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
}
