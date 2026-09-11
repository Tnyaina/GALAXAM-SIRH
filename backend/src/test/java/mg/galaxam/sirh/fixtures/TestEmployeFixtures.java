package mg.galaxam.sirh.fixtures;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestEmployeFixtures {

    @Autowired
    private DataSource dataSource;

    public static class CreatedEmploye {
        public int idEmploye;
        public String matricule;
        public String nom;
        public String prenom;
        public int idContrat;
        public int idAffectation;
        public int idSite;
    }

    public CreatedEmploye createActiveEmploye(String matricule, String nom, String prenom, int idSite) {
        return createActiveEmploye(matricule, nom, prenom, idSite, LocalDate.now().minusYears(1));
    }

    public CreatedEmploye createActiveEmploye(String matricule, String nom, String prenom, int idSite, LocalDate dateEmbauche) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                CreatedEmploye emp = new CreatedEmploye();
                emp.matricule = matricule;
                emp.nom = nom;
                emp.prenom = prenom;
                emp.idSite = idSite;

                // 1. Employe
                String sqlEmp = "INSERT INTO employe (" +
                        "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                        "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif) " +
                        "VALUES (?, ?, ?, 'M', '1990-01-01', 'Antananarivo', ?, '2010-01-01', 'Antananarivo', 'Lot II Test', '0340000000', ?, true) " +
                        "RETURNING id_employe";
                try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
                    ps.setString(1, matricule);
                    ps.setString(2, nom);
                    ps.setString(3, prenom);
                    // unique 12 digits CIN derived from matricule/time
                    String cin = String.format("%012d", Math.abs((matricule + nom).hashCode()) % 1000000000000L);
                    ps.setString(4, cin);
                    ps.setString(5, matricule.toLowerCase() + "@galaxam.com");
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            emp.idEmploye = rs.getInt(1);
                        }
                    }
                }

                // 2. Contrat CDI ACTIF
                String sqlCt = "INSERT INTO contrat_travail (" +
                        "id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, numero_contrat, date_debut, comptabilise_effectif) " +
                        "VALUES (?, 1, (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'), " +
                        "(SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'), " +
                        "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), ?, ?, true) " +
                        "RETURNING id_contrat";
                try (PreparedStatement ps = c.prepareStatement(sqlCt)) {
                    ps.setInt(1, emp.idEmploye);
                    ps.setString(2, "CT-" + matricule);
                    ps.setDate(3, Date.valueOf(dateEmbauche));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            emp.idContrat = rs.getInt(1);
                        }
                    }
                }

                // 3. Affectation Carriere ACTIF
                String sqlAff = "INSERT INTO employe_affectation_carriere (" +
                        "id_employe, id_contrat, id_site, id_statut_rh, id_motif_mouvement, " +
                        "id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, " +
                        "date_debut, salaire_base) " +
                        "VALUES (?, ?, ?, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), " +
                        "(SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'EMBAUCHE'), " +
                        "(SELECT id_departement FROM ref_departement LIMIT 1), " +
                        "(SELECT id_service FROM ref_service LIMIT 1), " +
                        "(SELECT id_fonction FROM ref_fonction LIMIT 1), " +
                        "(SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle LIMIT 1), " +
                        "(SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1), " +
                        "?, 500000) " +
                        "RETURNING id_affectation";
                try (PreparedStatement ps = c.prepareStatement(sqlAff)) {
                    ps.setInt(1, emp.idEmploye);
                    ps.setInt(2, emp.idContrat);
                    ps.setInt(3, idSite);
                    ps.setDate(4, Date.valueOf(dateEmbauche));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            emp.idAffectation = rs.getInt(1);
                        }
                    }
                }

                c.commit();
                return emp;
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur création employé test : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int createEmployeSansContrat(String nom, String prenom) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String matricule = "EMP-" + System.currentTimeMillis() % 100000;
                String cin = String.format("%012d", Math.abs((matricule + nom).hashCode()) % 1000000000000L);
                String sqlEmp = "INSERT INTO employe (" +
                        "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                        "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone, email, est_actif) " +
                        "VALUES (?, ?, ?, 'M', '1990-01-01', 'Antananarivo', ?, '2010-01-01', 'Antananarivo', 'Lot II Test', '0340000000', ?, true) " +
                        "RETURNING id_employe";
                int empId = 0;
                try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
                    ps.setString(1, matricule);
                    ps.setString(2, nom);
                    ps.setString(3, prenom);
                    ps.setString(4, cin);
                    ps.setString(5, matricule.toLowerCase() + "@galaxam.com");
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) empId = rs.getInt(1);
                    }
                }
                c.commit();
                return empId;
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur création employé sans contrat : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public int createPeriodePaie(String code, LocalDate debut, LocalDate fin, String statutCode) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                        "VALUES (?, ?, ?, ?, (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?)) " +
                        "RETURNING id_periode_paie";
                int idPeriode = 0;
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, code);
                    ps.setString(2, "Période " + code);
                    ps.setDate(3, Date.valueOf(debut));
                    ps.setDate(4, Date.valueOf(fin));
                    ps.setString(5, statutCode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idPeriode = rs.getInt(1);
                        }
                    }
                }
                c.commit();
                return idPeriode;
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur création période test : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void crediterSoldeConge(int idEmploye, double jours) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "INSERT INTO mouvement_solde_conge (" +
                        "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                        "VALUES (?, 'ACQUISITION', ?, CURRENT_DATE, 'Crédit initial test')";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setInt(1, idEmploye);
                    ps.setBigDecimal(2, BigDecimal.valueOf(jours));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur crédit solde congé : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
