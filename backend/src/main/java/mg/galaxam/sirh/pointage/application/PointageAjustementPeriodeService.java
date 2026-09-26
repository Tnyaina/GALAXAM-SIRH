package mg.galaxam.sirh.pointage.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Décisions DAF sur le solde horaire de période, sans altération des badges. */
@Service
public class PointageAjustementPeriodeService {
    @Autowired private DataSource dataSource;

    public record Ajustement(long id, int minutesSignees, String motif, String statut,
                             Integer saisiPar, Integer validePar, String dateSaisie) {}

    public List<Ajustement> lister(int periodeId, int employeId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return lister(periodeId, employeId, c);
        }
    }

    public List<Ajustement> lister(int periodeId, int employeId, Connection c) throws SQLException {
        List<Ajustement> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id_ajustement, minutes_signees, motif, statut, saisi_par, valide_par, date_saisie " +
                "FROM pointage_ajustement_periode WHERE id_periode_paie = ? AND id_employe = ? " +
                "ORDER BY id_ajustement")) {
            ps.setInt(1, periodeId);
            ps.setInt(2, employeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Ajustement(rs.getLong(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                            (Integer) rs.getObject(5), (Integer) rs.getObject(6), rs.getTimestamp(7).toString()));
                }
            }
        }
        return result;
    }

    public int totalMinutesValidees(int periodeId, int employeId, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(minutes_signees), 0) FROM pointage_ajustement_periode " +
                "WHERE id_periode_paie = ? AND id_employe = ? AND statut = 'VALIDE'")) {
            ps.setInt(1, periodeId);
            ps.setInt(2, employeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public long creer(int periodeId, int employeId, int minutesSignees, String motif, Integer auteurId)
            throws SQLException {
        if (auteurId == null) throw new IllegalStateException("Auteur DAF non identifié.");
        if (minutesSignees == 0 || Math.abs((long) minutesSignees) > 60L * 24L * 31L) {
            throw new IllegalArgumentException("La durée signée doit être non nulle et tenir dans la période.");
        }
        if (motif == null || motif.trim().length() < 5) {
            throw new IllegalArgumentException("Un motif de cinq caractères minimum est obligatoire.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                verrouillerPeriodeModifiable(periodeId, c);
                String sql = "INSERT INTO pointage_ajustement_periode " +
                        "(id_periode_paie, id_employe, minutes_signees, motif, saisi_par, valide_par) " +
                        "SELECT ?, e.id_employe, ?, ?, ?, ? FROM employe e " +
                        "WHERE e.id_employe = ? AND EXISTS (" +
                        "SELECT 1 FROM pointage_jour j WHERE j.id_employe = e.id_employe " +
                        "AND j.id_periode_paie = ?) RETURNING id_ajustement";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, periodeId);
                    ps.setInt(2, minutesSignees);
                    ps.setString(3, motif.trim());
                    ps.setInt(4, auteurId);
                    ps.setInt(5, auteurId);
                    ps.setInt(6, employeId);
                    ps.setInt(7, periodeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Aucun pointage dans cette période pour cet employé : ajustement impossible.");
                        long id = rs.getLong(1);
                        c.commit();
                        return id;
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                c.rollback();
                throw exception;
            }
        }
    }

    public void annuler(int periodeId, int employeId, long ajustementId, String motif, Integer auteurId)
            throws SQLException {
        if (auteurId == null) throw new IllegalStateException("Auteur DAF non identifié.");
        if (motif == null || motif.trim().length() < 5) {
            throw new IllegalArgumentException("Un motif d'annulation de cinq caractères minimum est obligatoire.");
        }
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                verrouillerPeriodeModifiable(periodeId, c);
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_ajustement_periode a SET statut = 'ANNULE', annule_par = ?, " +
                        "date_annulation = CURRENT_TIMESTAMP, motif_annulation = ? " +
                        "WHERE a.id_ajustement = ? AND a.id_employe = ? AND a.id_periode_paie = ? " +
                        "AND a.statut = 'VALIDE'")) {
                    ps.setInt(1, auteurId);
                    ps.setString(2, motif.trim());
                    ps.setLong(3, ajustementId);
                    ps.setInt(4, employeId);
                    ps.setInt(5, periodeId);
                    if (ps.executeUpdate() != 1) throw new IllegalStateException("Ajustement introuvable ou période figée.");
                }
                c.commit();
            } catch (SQLException | RuntimeException exception) {
                c.rollback();
                throw exception;
            }
        }
    }

    private void verrouillerPeriodeModifiable(int periodeId, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT s.code FROM periode_paie p " +
                "JOIN ref_statut_periode_paie s ON s.id_statut_periode = p.id_statut_periode " +
                "WHERE p.id_periode_paie = ? FOR UPDATE OF p")) {
            ps.setInt(1, periodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !List.of("EN_COURS", "A_VALIDER", "TRANSMIS_DAF").contains(rs.getString(1))) {
                    throw new IllegalStateException("Période figée ou introuvable : ajustement impossible.");
                }
            }
        }
    }

    public void figerProvenance(int snapshotId, int periodeId, int employeId, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pointage_snapshot_ajustement (id_cumul, id_ajustement, minutes_signees, motif) " +
                "SELECT ?, id_ajustement, minutes_signees, motif FROM pointage_ajustement_periode " +
                "WHERE id_periode_paie = ? AND id_employe = ? AND statut = 'VALIDE'")) {
            ps.setInt(1, snapshotId);
            ps.setInt(2, periodeId);
            ps.setInt(3, employeId);
            ps.executeUpdate();
        }
    }
}
