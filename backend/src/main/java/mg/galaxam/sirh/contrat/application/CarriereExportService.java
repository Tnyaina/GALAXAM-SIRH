package mg.galaxam.sirh.contrat.application;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.stereotype.Service;

/**
 * Service dédié aux exports du registre des contrats et carrières (CSV, Excel).
 * Découple le formatage et le streaming de données hors des contrôleurs REST.
 */
@Service
public class CarriereExportService {

    /**
     * Génère l'export CSV officiel du registre des contrats.
     */
    public void exportContratsCsv(OutputStream outputStream, Connection c) throws SQLException {
        String sql = "SELECT e.matricule, e.nom, e.prenom, ct.numero_contrat, " +
                "rtc.libelle AS type_contrat, rsc.libelle AS statut_contrat, " +
                "ct.date_debut, ct.date_fin, ct.date_fin_essai, " +
                "rs.libelle AS site, rd.libelle AS departement, rf.libelle AS fonction " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "LEFT JOIN LATERAL (" +
                "  SELECT id_site, id_departement, id_fonction FROM employe_affectation_carriere " +
                "  WHERE id_contrat = ct.id_contrat ORDER BY id_affectation DESC LIMIT 1" +
                ") eac ON TRUE " +
                "LEFT JOIN ref_site rs ON eac.id_site = rs.id_site " +
                "LEFT JOIN ref_departement rd ON eac.id_departement = rd.id_departement " +
                "LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction " +
                "ORDER BY e.matricule ASC, ct.date_debut DESC";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // BOM UTF-8 pour Excel
            writer.write('\ufeff');
            writer.println("Matricule;Nom;Prénom;N° Contrat;Type Contrat;Statut;Date Début;Date Fin;Date Fin Essai;Site;Département;Fonction");

            while (rs.next()) {
                writer.printf("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s%n",
                        clean(rs.getString("matricule")),
                        clean(rs.getString("nom")),
                        clean(rs.getString("prenom")),
                        clean(rs.getString("numero_contrat")),
                        clean(rs.getString("type_contrat")),
                        clean(rs.getString("statut_contrat")),
                        clean(rs.getString("date_debut")),
                        clean(rs.getString("date_fin")),
                        clean(rs.getString("date_fin_essai")),
                        clean(rs.getString("site")),
                        clean(rs.getString("departement")),
                        clean(rs.getString("fonction")));
            }
            writer.flush();
        }
    }

    private String clean(String val) {
        if (val == null) return "";
        return val.replace(";", ",").replace("\n", " ").replace("\r", "");
    }
}
