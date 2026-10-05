package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import mg.galaxam.sirh.conge.domain.CongeLotValorisationEngine;
import org.springframework.stereotype.Service;

/** Lit les valeurs d'origine : cutover, journal mensuel et taux historisés. */
@Service
public class CongeLotValorisationService {
    private final CongeLotValorisationEngine engine = new CongeLotValorisationEngine();

    public record CorrectionCutover(BigDecimal ancienReliquatJours, BigDecimal nouveauReliquatJours,
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
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_cutover_correction "
                + "(id_employe, id_mouvement_reprise, id_mouvement_ajustement, ancien_reliquat_jours, "
                + "nouveau_reliquat_jours, ancien_reliquat_valeur, nouveau_reliquat_valeur, "
                + "ancien_taux, nouveau_taux, regularisation_a_examiner, motif, corrige_par) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, employe); ps.setInt(2, reprise); ps.setInt(3, ajustement);
            ps.setBigDecimal(4, anciensJours); ps.setBigDecimal(5, nouveauxJours);
            ps.setBigDecimal(6, ancienneValeur); ps.setBigDecimal(7, nouvelleValeur);
            ps.setBigDecimal(8, ancienTaux); ps.setBigDecimal(9, nouveauTaux);
            ps.setBigDecimal(10, ecart); ps.setString(11, motif.trim()); ps.setInt(12, auteur);
            ps.executeUpdate();
        }
        return new CorrectionCutover(anciensJours, nouveauxJours, ancienneValeur, nouvelleValeur, ecart);
    }

    public CongeLotValorisationEngine.Situation situation(int idEmploye, LocalDate date, Connection c)
            throws SQLException {
        if (date == null) throw new IllegalArgumentException("La date de situation est obligatoire.");
        List<CongeLotValorisationEngine.Mouvement> mouvements = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT m.id_mouvement, m.type_mouvement, m.nb_jours, m.date_mouvement, "
                        + "m.id_demande_conge, m.commentaire, m.valeur_source, cc.id_mouvement_reprise, "
                        + "cc.nouveau_reliquat_valeur FROM mouvement_solde_conge m "
                        + "LEFT JOIN conge_cutover_correction cc ON cc.id_mouvement_ajustement = m.id_mouvement "
                        + "WHERE m.id_employe = ? AND m.date_mouvement <= ? "
                        + "ORDER BY m.date_mouvement, m.id_mouvement")) {
            ps.setInt(1, idEmploye);
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
                    boolean restitutionDemande = "AJUSTEMENT".equals(type) && idDemande != null
                            && commentaire != null && commentaire.startsWith("Ajustement de solde suite modification durée");
                    if (valeur == null && jours.signum() > 0 && !restitutionDemande
                            && ("ACQUISITION".equals(type) || "BONIFICATION".equals(type)
                                    || "AJUSTEMENT".equals(type))) {
                        if ("ACQUISITION".equals(type)) {
                            valeur = valeurAcquisition(idEmploye, id, dateMouvement, jours, c);
                        } else {
                            BigDecimal taux = tauxHistorique(idEmploye, id, dateMouvement, c);
                            valeur = jours.multiply(taux).setScale(2, RoundingMode.HALF_UP);
                        }
                    }
                    mouvements.add(new CongeLotValorisationEngine.Mouvement(id, type, jours,
                            dateMouvement, idDemande, commentaire, valeur));
                }
            }
        }
        return engine.calculer(mouvements);
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
                            ? CongeDecompteCalendaireResolver.calculer(idEmploye, debutConge,
                                    debutIntersection.minusDays(1), demi, c)
                            : BigDecimal.ZERO;
                    BigDecimal prendre = CongeDecompteCalendaireResolver.calculer(idEmploye,
                            debutIntersection, finIntersection, demi, c);
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

    private BigDecimal valeurAcquisition(int employe, int mouvement, LocalDate date,
            BigDecimal jours, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT jours_droit_acquis, valorisation_mensuelle FROM conge_journal_droit "
                        + "WHERE id_employe = ? AND annee = ? AND mois = ?")) {
            ps.setInt(1, employe);
            ps.setInt(2, date.getYear());
            ps.setInt(3, date.getMonthValue());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "CONGE_ACQUISITION_VALORISATION_MISSING: mouvement " + mouvement);
                BigDecimal joursJournal = rs.getBigDecimal("jours_droit_acquis");
                BigDecimal valeur = rs.getBigDecimal("valorisation_mensuelle");
                if (joursJournal == null || valeur == null ||
                        joursJournal.setScale(2, RoundingMode.HALF_UP).compareTo(jours) != 0) {
                    throw new IllegalStateException("CONGE_ACQUISITION_JOURNAL_MISMATCH: mouvement " + mouvement);
                }
                if (rs.next()) throw new IllegalStateException(
                        "CONGE_ACQUISITION_JOURNAL_AMBIGUOUS: plusieurs lignes journal pour le même mois (mouvement " + mouvement + ")");
                return valeur;
            }
        }
    }

    private BigDecimal tauxHistorique(int employe, int mouvement, LocalDate date, Connection c)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT taux_journalier FROM conge_taux_applicable WHERE id_employe = ? "
                        + "AND date_effet <= ? AND (date_fin IS NULL OR date_fin > ?)")) {
            ps.setInt(1, employe);
            ps.setDate(2, Date.valueOf(date));
            ps.setDate(3, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "CONGE_LOT_TCONGE_MISSING: mouvement " + mouvement + " au " + date);
                BigDecimal taux = rs.getBigDecimal(1);
                if (rs.next()) throw new IllegalStateException(
                        "AMBIGUOUS_CONFIGURATION: CONGE_LOT_TCONGE_AMBIGUOUS: mouvement " + mouvement + " au " + date);
                return taux;
            }
        }
    }
}
