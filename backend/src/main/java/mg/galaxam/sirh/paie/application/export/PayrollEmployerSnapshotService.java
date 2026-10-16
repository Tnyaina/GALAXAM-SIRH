package mg.galaxam.sirh.paie.application.export;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.contrat.application.EmployerIdentityContextResolver;
import org.springframework.stereotype.Service;

/** Identité employeur historisée sur le bulletin validé, source des réimpressions. */
@Service
public class PayrollEmployerSnapshotService {
    public record Employer(int id, String name, String nif, String stat, String address,
                           String phone, String email, boolean historical) {}

    public void capture(int bulletinId, Connection connection) throws Exception {
        int employeeId;
        LocalDate start;
        LocalDate end;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT b.id_employe,p.date_debut,p.date_fin FROM bulletin_paie b "
                        + "JOIN periode_paie p ON p.id_periode_paie=b.id_periode_paie WHERE b.id_bulletin=?")) {
            ps.setInt(1, bulletinId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Bulletin introuvable : " + bulletinId);
                employeeId = rs.getInt(1);
                start = rs.getDate(2).toLocalDate();
                end = rs.getDate(3).toLocalDate();
            }
        }
        var employer = new EmployerIdentityContextResolver().resolve(employeeId, start, end, connection);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO bulletin_paie_employeur_snapshot "
                        + "(id_bulletin,id_filiale,raison_sociale,nif,stat,adresse_siege,telephone,email) "
                        + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id_bulletin) DO UPDATE SET "
                        + "id_filiale=EXCLUDED.id_filiale,raison_sociale=EXCLUDED.raison_sociale,nif=EXCLUDED.nif, "
                        + "stat=EXCLUDED.stat,adresse_siege=EXCLUDED.adresse_siege,telephone=EXCLUDED.telephone, "
                        + "email=EXCLUDED.email,captured_at=CURRENT_TIMESTAMP")) {
            ps.setInt(1, bulletinId);
            ps.setInt(2, employer.id());
            ps.setString(3, employer.name());
            ps.setString(4, employer.nif());
            ps.setString(5, employer.stat());
            ps.setString(6, employer.address());
            ps.setString(7, employer.phone());
            ps.setString(8, employer.email());
            ps.executeUpdate();
        }
    }

    public Employer find(int bulletinId, Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id_filiale,raison_sociale,nif,stat,adresse_siege,telephone,email "
                        + "FROM bulletin_paie_employeur_snapshot WHERE id_bulletin=?")) {
            ps.setInt(1, bulletinId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Employer(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), true) : null;
            }
        }
    }
}
