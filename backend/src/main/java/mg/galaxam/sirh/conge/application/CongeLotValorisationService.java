package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Collection;
import java.util.Set;
import java.util.LinkedHashSet;
import mg.galaxam.sirh.conge.domain.CongeLotValorisationEngine;
import org.springframework.stereotype.Service;

/** Lit les valeurs d'origine : cutover, journal mensuel et taux historisés. */
@Service
public class CongeLotValorisationService {
    private final CongeLotValorisationEngine engine = new CongeLotValorisationEngine();
    private record SourceMovement(int id, String type, BigDecimal days, LocalDate date, Integer requestId,
                                  String comment, BigDecimal value) {}
    private record JournalEntry(BigDecimal days, BigDecimal value) {}
    private record HistoricalRate(LocalDate start, LocalDate end, BigDecimal value) {}
    private static final class HistoricalRateCursor {
        private final List<HistoricalRate> rates;
        private final List<HistoricalRate> active = new ArrayList<>();
        private int next;

        HistoricalRateCursor(List<HistoricalRate> rates) {
            this.rates = rates;
        }

        List<HistoricalRate> at(LocalDate date) {
            while (next < rates.size() && !rates.get(next).start().isAfter(date)) {
                active.add(rates.get(next++));
            }
            active.removeIf(rate -> rate.end() != null && !rate.end().isAfter(date));
            return active;
        }
    }

    public record CorrectionCutover(int idCorrection, BigDecimal ancienReliquatJours, BigDecimal nouveauReliquatJours,
            BigDecimal ancienneValeur, BigDecimal nouvelleValeur, BigDecimal regularisationAExaminer) {}

    /** Corrige seulement le reliquat de reprise. L'écart sur les prises antérieures reste à examiner en paie. */
    public CorrectionCutover corrigerReliquatCutover(int employe, BigDecimal nouveauxJours,
            BigDecimal nouvelleValeur, String motif, int auteur, Connection c) throws SQLException {
        return corrigerReliquatCutover(employe, nouveauxJours, nouvelleValeur, motif, auteur, LocalDate.now(), c);
    }

    public CorrectionCutover corrigerReliquatCutover(int employe, BigDecimal nouveauxJours,
            BigDecimal nouvelleValeur, String motif, int auteur, LocalDate dateEffet, Connection c) throws SQLException {
        if (nouveauxJours == null || nouvelleValeur == null || nouveauxJours.signum() < 0
                || nouvelleValeur.signum() < 0 || motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Reliquat, valeur et motif DAF obligatoires.");
        }
        LocalDate dateMetier = dateEffet != null ? dateEffet : LocalDate.now();
        try (PreparedStatement verrou = c.prepareStatement("SELECT id_mouvement "
                + "FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = 'AJUSTEMENT' "
                + "AND (commentaire LIKE '[SOLDE_INITIAL]%' OR commentaire LIKE 'Reprise cutover - Solde initial%') "
                + "FOR UPDATE")) {
            verrou.setInt(1, employe);
            try (ResultSet rs = verrou.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("CONGE_CUTOVER_SOURCE_MISSING");
                if (rs.next()) throw new IllegalStateException("CONGE_CUTOVER_SOURCE_AMBIGUOUS");
            }
        }
        var situation = situation(employe, dateMetier, c);
        int reprise = -1;
        BigDecimal anciensJours = BigDecimal.ZERO;
        BigDecimal ancienneValeur = BigDecimal.ZERO;
        for (var lot : situation.lots()) {
            if ("REPRISE_CUTOVER".equals(lot.origine())) {
                reprise = lot.idSource(); anciensJours = lot.jours(); ancienneValeur = lot.valeur();
                break;
            }
        }
        if (reprise < 0) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id_mouvement FROM mouvement_solde_conge "
                    + "WHERE id_employe = ? AND type_mouvement = 'AJUSTEMENT' "
                    + "AND (commentaire LIKE '[SOLDE_INITIAL]%' OR commentaire LIKE 'Reprise cutover - Solde initial%') "
                    + "ORDER BY id_mouvement LIMIT 1")) {
                ps.setInt(1, employe);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) reprise = rs.getInt(1); }
            }
        }
        if (reprise < 0) throw new IllegalStateException("CONGE_CUTOVER_SOURCE_MISSING");
        BigDecimal ancienTaux;
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE((SELECT nouveau_taux "
                + "FROM conge_cutover_correction WHERE id_mouvement_reprise = ? "
                + "ORDER BY id_correction DESC LIMIT 1), m.valeur_source / NULLIF(m.nb_jours, 0)) "
                + "FROM mouvement_solde_conge m WHERE m.id_mouvement = ?")) {
            ps.setInt(1, reprise); ps.setInt(2, reprise);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getBigDecimal(1) == null) {
                    throw new IllegalStateException("CONGE_CUTOVER_TCONGE_MISSING: " + reprise);
                }
                ancienTaux = rs.getBigDecimal(1).setScale(6, RoundingMode.HALF_UP);
            }
        }
        BigDecimal nouveauTaux = nouveauxJours.signum() == 0 ? BigDecimal.ZERO
                : nouvelleValeur.divide(nouveauxJours, 6, RoundingMode.HALF_UP);
        final int sourceReprise = reprise;
        BigDecimal consommes = situation.imputations().stream()
                .filter(i -> i.idSource() == sourceReprise && i.idDemande() != null)
                .map(CongeLotValorisationEngine.Imputation::jours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ecart = consommes.multiply(nouveauTaux.subtract(ancienTaux))
                .setScale(2, RoundingMode.HALF_UP);
        int ajustement;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by) "
                + "VALUES (?, 'AJUSTEMENT', ?, ?, ?, ?) RETURNING id_mouvement")) {
            ps.setInt(1, employe); ps.setBigDecimal(2, nouveauxJours.subtract(anciensJours));
            ps.setDate(3, Date.valueOf(dateMetier));
            ps.setString(4, "Correction DAF du reliquat de reprise : " + motif.trim()); ps.setInt(5, auteur);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); ajustement = rs.getInt(1); }
        }
        int idCorrection;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_cutover_correction "
                + "(id_employe, id_mouvement_reprise, id_mouvement_ajustement, ancien_reliquat_jours, "
                + "nouveau_reliquat_jours, ancien_reliquat_valeur, nouveau_reliquat_valeur, "
                + "ancien_taux, nouveau_taux, regularisation_a_examiner, motif, corrige_par) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_correction")) {
            ps.setInt(1, employe); ps.setInt(2, reprise); ps.setInt(3, ajustement);
            ps.setBigDecimal(4, anciensJours); ps.setBigDecimal(5, nouveauxJours);
            ps.setBigDecimal(6, ancienneValeur); ps.setBigDecimal(7, nouvelleValeur);
            ps.setBigDecimal(8, ancienTaux); ps.setBigDecimal(9, nouveauTaux);
            ps.setBigDecimal(10, ecart); ps.setString(11, motif.trim()); ps.setInt(12, auteur);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); idCorrection = rs.getInt(1); }
        }
        return new CorrectionCutover(idCorrection, anciensJours, nouveauxJours, ancienneValeur, nouvelleValeur, ecart);
    }

    public CongeLotValorisationEngine.Situation situation(int idEmploye, LocalDate date, Connection c)
            throws SQLException {
        return situations(List.of(idEmploye), date, c).get(idEmploye);
    }

    /** Calcule plusieurs situations FIFO avec des lectures groupées par date de situation. */
    public Map<Integer, CongeLotValorisationEngine.Situation> situations(Collection<Integer> employeeIds,
            LocalDate date, Connection c) throws SQLException {
        if (date == null) throw new IllegalArgumentException("La date de situation est obligatoire.");
        Set<Integer> ids = new LinkedHashSet<>();
        if (employeeIds != null) employeeIds.stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        if (ids.isEmpty()) return Map.of();
        Map<Integer, List<SourceMovement>> sourcesByEmployee = new LinkedHashMap<>();
        ids.forEach(id -> sourcesByEmployee.put(id, new ArrayList<>()));
        java.sql.Array employeeArray = c.createArrayOf("integer", ids.toArray());
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.id_mouvement, m.type_mouvement, m.nb_jours, m.date_mouvement, "
                        + "m.id_employe, m.id_demande_conge, m.commentaire, m.valeur_source, cc.id_mouvement_reprise, "
                        + "cc.nouveau_reliquat_valeur FROM mouvement_solde_conge m "
                        + "LEFT JOIN conge_cutover_correction cc ON cc.id_mouvement_ajustement = m.id_mouvement "
                        + "WHERE m.id_employe = ANY (?) AND m.date_mouvement <= ? "
                        + "ORDER BY m.id_employe, m.date_mouvement, m.id_mouvement")) {
            ps.setArray(1, employeeArray);
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id_mouvement");
                    String type = rs.getString("type_mouvement");
                    BigDecimal jours = rs.getBigDecimal("nb_jours");
                    LocalDate dateMouvement = rs.getDate("date_mouvement").toLocalDate();
                    int demande = rs.getInt("id_demande_conge");
                    Integer idDemande = rs.wasNull() ? null : demande;
                    String commentaire = rs.getString("commentaire");
                    BigDecimal valeur = rs.getBigDecimal("valeur_source");
                    int reprise = rs.getInt("id_mouvement_reprise");
                    if (!rs.wasNull()) {
                        type = "CORRECTION_CUTOVER";
                        idDemande = reprise;
                        valeur = rs.getBigDecimal("nouveau_reliquat_valeur");
                    }
                    sourcesByEmployee.get(rs.getInt("id_employe")).add(
                            new SourceMovement(id, type, jours, dateMouvement, idDemande, commentaire, valeur));
                }
            }
        } finally {
            employeeArray.free();
        }
        boolean needsJournal = sourcesByEmployee.values().stream().flatMap(Collection::stream)
                .anyMatch(source -> needsFallback(source, "ACQUISITION"));
        boolean needsRates = sourcesByEmployee.values().stream().flatMap(Collection::stream)
                .anyMatch(source -> needsFallback(source, "BONIFICATION") || needsFallback(source, "AJUSTEMENT"));
        Map<Integer, Map<YearMonth, List<JournalEntry>>> journals = needsJournal
                ? loadJournalEntries(ids, date, c) : Map.of();
        Map<Integer, List<HistoricalRate>> rates = needsRates ? loadHistoricalRates(ids, date, c) : Map.of();
        Map<Integer, CongeLotValorisationEngine.Situation> result = new LinkedHashMap<>();
        for (Integer employeeId : ids) {
            List<SourceMovement> sources = sourcesByEmployee.get(employeeId);
            HistoricalRateCursor rateCursor = needsRates
                    ? new HistoricalRateCursor(rates.getOrDefault(employeeId, List.of())) : null;
            List<CongeLotValorisationEngine.Mouvement> mouvements = new ArrayList<>(sources.size());
            for (SourceMovement source : sources) {
                BigDecimal value = source.value();
                if (needsFallback(source, "ACQUISITION")) {
                    YearMonth month = YearMonth.from(source.date());
                    List<JournalEntry> entries = journals.getOrDefault(employeeId, Map.of()).getOrDefault(month, List.of());
                    if (entries.isEmpty()) throw new IllegalStateException(
                            "CONGE_ACQUISITION_VALORISATION_MISSING: mouvement " + source.id());
                    if (entries.size() > 1) throw new IllegalStateException(
                            "CONGE_ACQUISITION_JOURNAL_AMBIGUOUS: plusieurs lignes journal pour le même mois (mouvement " + source.id() + ")");
                    JournalEntry entry = entries.get(0);
                    if (entry.days() == null || entry.value() == null
                            || entry.days().setScale(2, RoundingMode.HALF_UP).compareTo(source.days()) != 0)
                        throw new IllegalStateException("CONGE_ACQUISITION_JOURNAL_MISMATCH: mouvement " + source.id());
                    value = entry.value();
                } else if (needsFallback(source, "BONIFICATION") || needsFallback(source, "AJUSTEMENT")) {
                    List<HistoricalRate> applicable = rateCursor.at(source.date());
                    if (applicable.isEmpty()) throw new IllegalStateException(
                            "CONGE_LOT_TCONGE_MISSING: mouvement " + source.id() + " au " + source.date());
                    if (applicable.size() > 1) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: CONGE_LOT_TCONGE_AMBIGUOUS: mouvement " + source.id() + " au " + source.date());
                    value = source.days().multiply(applicable.get(0).value()).setScale(2, RoundingMode.HALF_UP);
                }
                mouvements.add(new CongeLotValorisationEngine.Mouvement(source.id(), source.type(), source.days(),
                        source.date(), source.requestId(), source.comment(), value));
            }
            result.put(employeeId, engine.calculer(mouvements));
        }
        return result;
    }

    private static boolean needsFallback(SourceMovement source, String type) {
        boolean restitutionDemande = "AJUSTEMENT".equals(source.type()) && source.requestId() != null
                && source.comment() != null && source.comment().startsWith("Ajustement de solde suite modification durée");
        return source.value() == null && source.days().signum() > 0 && !restitutionDemande
                && type.equals(source.type());
    }

    private static Map<Integer, Map<YearMonth, List<JournalEntry>>> loadJournalEntries(Collection<Integer> employees, LocalDate date,
            Connection c) throws SQLException {
        Map<Integer, Map<YearMonth, List<JournalEntry>>> entries = new HashMap<>();
        java.sql.Array employeeArray = c.createArrayOf("integer", employees.toArray());
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe,annee,mois,jours_droit_acquis,valorisation_mensuelle "
                + "FROM conge_journal_droit WHERE id_employe=ANY(?) AND (annee < ? OR (annee = ? AND mois <= ?))")) {
            ps.setArray(1, employeeArray); ps.setInt(2, date.getYear()); ps.setInt(3, date.getYear()); ps.setInt(4, date.getMonthValue());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entries.computeIfAbsent(rs.getInt("id_employe"), ignored -> new HashMap<>())
                        .computeIfAbsent(YearMonth.of(rs.getInt("annee"), rs.getInt("mois")), ignored -> new ArrayList<>())
                        .add(new JournalEntry(rs.getBigDecimal("jours_droit_acquis"), rs.getBigDecimal("valorisation_mensuelle")));
            }
        } finally { employeeArray.free(); }
        return entries;
    }

    private static Map<Integer, List<HistoricalRate>> loadHistoricalRates(Collection<Integer> employees, LocalDate date, Connection c)
            throws SQLException {
        Map<Integer, List<HistoricalRate>> rates = new HashMap<>();
        java.sql.Array employeeArray = c.createArrayOf("integer", employees.toArray());
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe,date_effet,date_fin,taux_journalier "
                + "FROM conge_taux_applicable WHERE id_employe=ANY(?) AND date_effet<=? ORDER BY id_employe,date_effet,id_taux_applicable")) {
            ps.setArray(1, employeeArray); ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rates.computeIfAbsent(rs.getInt("id_employe"), ignored -> new ArrayList<>())
                        .add(new HistoricalRate(rs.getDate("date_effet").toLocalDate(),
                                rs.getDate("date_fin") == null ? null : rs.getDate("date_fin").toLocalDate(),
                                rs.getBigDecimal("taux_journalier")));
            }
        } finally { employeeArray.free(); }
        return rates;
    }

    /** Ventile une prise sur ses lots, y compris quand elle chevauche deux périodes de paie. */
    public BigDecimal valorisationPrisesSurPeriode(int idEmploye, LocalDate debut, LocalDate fin,
            Connection c) throws SQLException {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new IllegalArgumentException("La période de paie des congés est invalide.");
        }
        Map<Integer, LinkedHashMap<Integer, CongeLotValorisationEngine.Imputation>> allocations = new HashMap<>();
        for (var trace : situation(idEmploye, fin, c).imputations()) {
            if (trace.idDemande() == null) continue;
            var sources = allocations.computeIfAbsent(trace.idDemande(), ignored -> new LinkedHashMap<>());
            var precedent = sources.get(trace.idSource());
            if (precedent == null) sources.put(trace.idSource(), trace);
            else sources.put(trace.idSource(), new CongeLotValorisationEngine.Imputation(
                    precedent.idMouvement(), trace.idDemande(), trace.idSource(), trace.origine(),
                    precedent.jours().add(trace.jours()), precedent.valeur().add(trace.valeur()),
                    trace.tauxHistorique()));
        }
        BigDecimal total = BigDecimal.ZERO;
        String sql = "SELECT d.id_demande_conge, d.date_debut, d.date_fin, d.est_demi_journee "
                + "FROM demande_conge d JOIN ref_statut_demande_conge s ON s.id_statut_demande_conge = d.id_statut_demande_conge "
                + "JOIN ref_type_conge t ON t.id_type_conge = d.id_type_conge "
                + "WHERE d.id_employe = ? AND s.code = 'VALIDE' AND t.decompte_solde_acquis = TRUE "
                + "AND d.date_debut <= ? AND d.date_fin >= ? ORDER BY d.date_debut, d.id_demande_conge";
        CongeDecompteCalendaireResolver.Session decompte = CongeDecompteCalendaireResolver.session(idEmploye, c);
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(fin));
            ps.setDate(3, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int demande = rs.getInt("id_demande_conge");
                    LocalDate debutConge = rs.getDate("date_debut").toLocalDate();
                    LocalDate finConge = rs.getDate("date_fin").toLocalDate();
                    boolean demi = rs.getBoolean("est_demi_journee");
                    LocalDate debutIntersection = debutConge.isBefore(debut) ? debut : debutConge;
                    LocalDate finIntersection = finConge.isAfter(fin) ? fin : finConge;
                    // Les offsets FIFO suivent les jours réellement débités, y compris une
                    // éventuelle exclusion datée des jours fériés au changement d'année.
                    BigDecimal ignorer = debutIntersection.isAfter(debutConge)
                            ? decompte.calculer(debutConge, debutIntersection.minusDays(1), demi)
                            : BigDecimal.ZERO;
                    BigDecimal prendre = decompte.calculer(debutIntersection, finIntersection, demi);
                    List<CongeLotValorisationEngine.Imputation> lots = new ArrayList<>(
                            allocations.getOrDefault(demande, new LinkedHashMap<>()).values());
                    for (var lot : lots) {
                        if (prendre.signum() == 0) break;
                        if (ignorer.compareTo(lot.jours()) >= 0) {
                            ignorer = ignorer.subtract(lot.jours());
                            continue;
                        }
                        BigDecimal disponible = lot.jours().subtract(ignorer);
                        BigDecimal jours = prendre.min(disponible);
                        BigDecimal valeur = ignorer.signum() == 0 && jours.compareTo(lot.jours()) == 0
                                ? lot.valeur() : jours.multiply(lot.tauxHistorique())
                                        .setScale(2, RoundingMode.HALF_UP);
                        total = total.add(valeur);
                        prendre = prendre.subtract(jours);
                        ignorer = BigDecimal.ZERO;
                    }
                    if (prendre.signum() > 0) {
                        throw new IllegalStateException("CONGE_LOT_IMPUTATION_MISSING: demande " + demande);
                    }
                }
            }
        }
        return total;
    }

}
