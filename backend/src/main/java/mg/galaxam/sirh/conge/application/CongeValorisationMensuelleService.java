package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Rectification exceptionnelle de la valeur d'un lot mensuel encore intact. */
@Service
public class CongeValorisationMensuelleService {
    @Autowired private CongeLotValorisationService lots;

    public record Mois(int idJournal, int annee, int mois, BigDecimal jours,
            BigDecimal valeur, BigDecimal cumul, String statutReference) {}

    public List<Mois> lister(int reference, Connection c) throws SQLException {
        List<Mois> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT j.id_journal,j.annee,j.mois,j.jours_droit_acquis,"
                + "j.valorisation_mensuelle,j.cumul_valorisation,r.statut FROM conge_journal_droit j "
                + "JOIN conge_periode_reference r ON r.id_periode_reference=j.id_periode_reference "
                + "WHERE j.id_periode_reference=? ORDER BY j.annee,j.mois")) {
            ps.setInt(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Mois(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getString(7)));
            }
        }
        return result;
    }

    public record Historique(int idCorrection, int idJournal, BigDecimal ancienneValeur,
            BigDecimal nouvelleValeur, String motif, int corrigePar, java.sql.Timestamp dateCorrection) {}

    public List<Historique> historique(int reference, Connection c) throws SQLException {
        List<Historique> result = new ArrayList<>();
        try (PreparedStatement ps=c.prepareStatement("SELECT x.id_correction,x.id_journal,x.ancienne_valeur,"
                + "x.nouvelle_valeur,x.motif,x.corrige_par,x.date_correction "
                + "FROM conge_valorisation_mensuelle_correction x "
                + "JOIN conge_journal_droit j ON j.id_journal=x.id_journal "
                + "WHERE j.id_periode_reference=? ORDER BY x.date_correction DESC,x.id_correction DESC")) {
            ps.setInt(1,reference);
            try(ResultSet rs=ps.executeQuery()) {
                while(rs.next()) result.add(new Historique(rs.getInt(1),rs.getInt(2),
                        rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getString(5),rs.getInt(6),rs.getTimestamp(7)));
            }
        }
        return result;
    }

    public Map<String, Object> corriger(int reference, int journal, BigDecimal nouvelleValeur,
            String motif, int auteur, Connection c) throws SQLException {
        if (nouvelleValeur == null || nouvelleValeur.signum() < 0 || motif == null || motif.isBlank())
            throw new IllegalArgumentException("Valeur positive ou nulle et motif DAF obligatoires.");
        BigDecimal ancienne, jours;
        int employe, annee, mois;
        Date acquisition;
        try (PreparedStatement ps = c.prepareStatement("SELECT j.id_employe,j.annee,j.mois,j.jours_droit_acquis,"
                + "j.valorisation_mensuelle,p.date_fin,r.statut FROM conge_journal_droit j "
                + "JOIN conge_periode_reference r ON r.id_periode_reference=j.id_periode_reference "
                + "JOIN periode_paie p ON p.id_periode_paie=j.id_periode_paie "
                + "WHERE j.id_journal=? AND j.id_periode_reference=? FOR UPDATE OF j,r")) {
            ps.setInt(1, journal); ps.setInt(2, reference);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("CONGE_JOURNAL_MISSING: " + journal);
                if (!"OUVERTE".equals(rs.getString(7)))
                    throw new IllegalStateException("CONGE_REFERENCE_CLOSED: rectification mensuelle à régulariser séparément");
                employe=rs.getInt(1); annee=rs.getInt(2); mois=rs.getInt(3);
                jours=rs.getBigDecimal(4); ancienne=rs.getBigDecimal(5); acquisition=rs.getDate(6);
            }
        }
        nouvelleValeur = nouvelleValeur.setScale(2, RoundingMode.HALF_UP);
        if (ancienne.compareTo(nouvelleValeur) == 0)
            throw new IllegalArgumentException("La nouvelle valorisation est identique à l'ancienne.");
        int mouvement = -1;
        BigDecimal valeurLot = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT id_mouvement,nb_jours,valeur_source "
                + "FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION' "
                + "AND date_mouvement=? FOR UPDATE")) {
            ps.setInt(1, employe); ps.setDate(2, acquisition);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("CONGE_ACQUISITION_MISSING: " + acquisition);
                mouvement=rs.getInt(1);
                if (rs.getBigDecimal(2).setScale(2, RoundingMode.HALF_UP)
                        .compareTo(jours.setScale(2, RoundingMode.HALF_UP)) != 0)
                    throw new IllegalStateException("CONGE_ACQUISITION_JOURNAL_MISMATCH");
                valeurLot=rs.getBigDecimal(3);
                if (rs.next()) throw new IllegalStateException("AMBIGUOUS_CONFIGURATION: acquisition " + acquisition);
            }
        }
        if (valeurLot == null || valeurLot.compareTo(ancienne) != 0)
            throw new IllegalStateException("CONGE_ACQUISITION_VALUE_MISMATCH: lot et journal diffèrent");
        // Un lot déjà consommé a pu alimenter une indemnité de congé ou un STC figé.
        int source = mouvement;
        java.time.LocalDate dateSituation = acquisition.toLocalDate();
        try (PreparedStatement ps=c.prepareStatement("SELECT MAX(date_mouvement) FROM mouvement_solde_conge WHERE id_employe=?")) {
            ps.setInt(1,employe);
            try(ResultSet rs=ps.executeQuery()) {
                rs.next(); Date derniere=rs.getDate(1);
                if(derniere!=null && derniere.toLocalDate().isAfter(dateSituation))
                    dateSituation=derniere.toLocalDate();
            }
        }
        boolean utilise = lots.situation(employe, dateSituation, c).imputations().stream()
                .anyMatch(i -> i.idSource() == source);
        if (utilise) throw new IllegalStateException("CONGE_LOT_ALREADY_USED: régularisation DAF distincte requise");
        try (PreparedStatement ps = c.prepareStatement("UPDATE conge_journal_droit SET valorisation_mensuelle=? WHERE id_journal=?")) {
            ps.setBigDecimal(1, nouvelleValeur); ps.setInt(2, journal); ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE mouvement_solde_conge SET valeur_source=? WHERE id_mouvement=?")) {
            ps.setBigDecimal(1, nouvelleValeur); ps.setInt(2, mouvement); ps.executeUpdate();
        }
        BigDecimal cumulJours=BigDecimal.ZERO, cumulValeur=BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(cumul_droits_ouverture,0),"
                + "COALESCE(cumul_valorisation_ouverture,0) FROM conge_periode_reference "
                + "WHERE id_periode_reference=?")) {
            ps.setInt(1, reference);
            try (ResultSet rs=ps.executeQuery()) { rs.next(); cumulJours=rs.getBigDecimal(1); cumulValeur=rs.getBigDecimal(2); }
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id_journal,jours_droit_acquis,valorisation_mensuelle "
                + "FROM conge_journal_droit WHERE id_periode_reference=? ORDER BY annee,mois FOR UPDATE")) {
            ps.setInt(1, reference);
            try (ResultSet rs=ps.executeQuery()) {
                while (rs.next()) {
                    cumulJours=cumulJours.add(rs.getBigDecimal(2));
                    cumulValeur=cumulValeur.add(rs.getBigDecimal(3));
                    BigDecimal taux=cumulJours.signum()==0 ? BigDecimal.ZERO
                            : cumulValeur.divide(cumulJours,2,RoundingMode.HALF_UP);
                    try (PreparedStatement upd=c.prepareStatement("UPDATE conge_journal_droit SET "
                            + "cumul_jours_droit=?,cumul_valorisation=?,taux_journalier_conge=? WHERE id_journal=?")) {
                        upd.setBigDecimal(1,cumulJours); upd.setBigDecimal(2,cumulValeur);
                        upd.setBigDecimal(3,taux); upd.setInt(4,rs.getInt(1)); upd.executeUpdate();
                    }
                }
            }
        }
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO conge_valorisation_mensuelle_correction "
                + "(id_journal,id_employe,id_mouvement_acquisition,ancienne_valeur,nouvelle_valeur,motif,corrige_par) "
                + "VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1,journal); ps.setInt(2,employe); ps.setInt(3,mouvement); ps.setBigDecimal(4,ancienne);
            ps.setBigDecimal(5,nouvelleValeur); ps.setString(6,motif.trim()); ps.setInt(7,auteur); ps.executeUpdate();
        }
        return Map.of("idJournal",journal,"annee",annee,"mois",mois,
                "ancienneValeur",ancienne,"nouvelleValeur",nouvelleValeur,
                "cumulValorisation",cumulValeur);
    }
}
