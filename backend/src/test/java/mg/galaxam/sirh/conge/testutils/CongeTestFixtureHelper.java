package mg.galaxam.sirh.conge.testutils;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;

public final class CongeTestFixtureHelper {

    private CongeTestFixtureHelper() {}

    public static void cleanDatabase(Connection c) throws SQLException {
        if (!"sirh_galaxam_test".equals(c.getCatalog())) {
            throw new SQLException("Nettoyage des fixtures interdit hors de sirh_galaxam_test.");
        }
        try (Statement st = c.createStatement()) {
            st.execute("DELETE FROM pointage_snapshot_jour");
            st.execute("DELETE FROM pointage_snapshot_indemnite");
            st.execute("DELETE FROM pointage_snapshot_bucket");
            st.execute("DELETE FROM pointage_cumul_periode");
            st.execute("DELETE FROM pointage_jour");
            st.execute("DELETE FROM bulletin_paie_ligne");
            st.execute("DELETE FROM bulletin_paie");
            st.execute("DELETE FROM paie_stc");
            st.execute("DELETE FROM conge_taux_applicable");
            st.execute("DELETE FROM conge_journal_droit");
            st.execute("DELETE FROM conge_periode_reference");
            st.execute("DELETE FROM conge_expiration_examen");
            st.execute("DELETE FROM conge_cutover_correction");
            st.execute("DELETE FROM mouvement_solde_conge");
            st.execute("DELETE FROM demande_conge");
            st.execute("DELETE FROM periode_paie");
            st.execute("DELETE FROM workflow_rh");
            st.execute("DELETE FROM employe_affectation_carriere");
            st.execute("DELETE FROM contrat_travail");
            st.execute("DELETE FROM notification_rh");
            st.execute("DELETE FROM utilisateur");
            st.execute("DELETE FROM employe");
            st.execute("INSERT INTO parametre_global (code_parametre, categorie, valeur, description, date_debut, est_actif, est_desactivable) " +
                    "SELECT 'DIVISEUR_JOURS_CALENDAIRE_CONGE', 'CONGE', '30', 'Fixture de calcul des droits', DATE '2001-07-01', TRUE, FALSE " +
                    "WHERE NOT EXISTS (SELECT 1 FROM parametre_global WHERE code_parametre = 'DIVISEUR_JOURS_CALENDAIRE_CONGE')");
        }
    }

    public static int createAdminUser(Connection c, String identifiant) throws SQLException {
        String sql = "INSERT INTO utilisateur (identifiant, mot_de_passe_hache, id_role) " +
                "VALUES (?, '$2a$10$abcdefghijklmnopqrstuvwxyz1234567890abcdefghijklmn', 1) " +
                "RETURNING id_utilisateur";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, identifiant);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer l'utilisateur test: " + identifiant);
    }

    public static int createEmploye(Connection c, String matricule, String nom, String prenom) throws SQLException {
        String sql = "INSERT INTO employe (" +
                "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone" +
                ") VALUES (?, ?, ?, 'M', '1990-01-15', 'Antananarivo', '101000000001', '2010-01-15', 'TNR', 'Rue Test 101', '0340000000') " +
                "RETURNING id_employe";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, matricule);
            ps.setString(2, nom);
            ps.setString(3, prenom);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer l'employé test: " + matricule);
    }

    public static int createContratActif(Connection c, int idEmploye, LocalDate dateDebut) throws SQLException {
        String sql = "INSERT INTO contrat_travail (" +
                "id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, date_debut" +
                ") VALUES (?, 1, 1, 1, 4, ?) RETURNING id_contrat";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(dateDebut));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer le contrat test pour employé: " + idEmploye);
    }

    public static int createAffectation(Connection c, int idEmploye, int idContrat, LocalDate dateDebut, BigDecimal salaireBase) throws SQLException {
        String sql = "INSERT INTO employe_affectation_carriere (" +
                "id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, " +
                "id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, " +
                "salaire_base, date_debut" +
                ") VALUES (?, ?, 4, 1, 1, 1, 1, 3, 1, 1, ?, ?) RETURNING id_affectation";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idContrat);
            ps.setBigDecimal(3, salaireBase);
            ps.setDate(4, Date.valueOf(dateDebut));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer l'affectation test pour employé: " + idEmploye);
    }

    public static int createPeriodePaie(Connection c, String code, LocalDate debut, LocalDate fin, String codeStatut) throws SQLException {
        int idStatut = getStatutPeriodePaieId(c, codeStatut);
        String sql = "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode, version) " +
                "VALUES (?, ?, ?, ?, ?, 1) RETURNING id_periode_paie";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, "Période " + code);
            ps.setDate(3, Date.valueOf(debut));
            ps.setDate(4, Date.valueOf(fin));
            ps.setInt(5, idStatut);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new SQLException("Impossible de créer la période de paie: " + code);
    }

    public static void crediterSolde(Connection c, int idEmploye, double jours, String commentaire) throws SQLException {
        String sql = "INSERT INTO mouvement_solde_conge (" +
                "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire" +
                ") VALUES (?, 'ACQUISITION', ?, CURRENT_DATE, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, BigDecimal.valueOf(jours));
            ps.setString(3, commentaire);
            ps.executeUpdate();
        }
    }

    public static void crediterSoldeAvecDate(Connection c, int idEmploye, double jours, LocalDate dateMvt, String commentaire) throws SQLException {
        String sql = "INSERT INTO mouvement_solde_conge (" +
                "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire" +
                ") VALUES (?, 'ACQUISITION', ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, BigDecimal.valueOf(jours));
            ps.setDate(3, Date.valueOf(dateMvt));
            ps.setString(4, commentaire);
            ps.executeUpdate();
        }
    }

    public static void creerTcongeApplicable(Connection c, int idEmploye, BigDecimal taux, LocalDate dateEffet, LocalDate dateFin, String provenance) throws SQLException {
        String sql = "INSERT INTO conge_taux_applicable (" +
                "id_employe, taux_journalier, date_effet, date_fin, source_provenance, commentaire" +
                ") VALUES (?, ?, ?, ?, ?, 'Test TCONGE fixture')";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, taux);
            ps.setDate(3, Date.valueOf(dateEffet));
            if (dateFin != null) {
                ps.setDate(4, Date.valueOf(dateFin));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setString(5, provenance);
            ps.executeUpdate();
        }
    }

    public static int getTypeCongeId(Connection c, String code) throws SQLException {
        String sql = "SELECT id_type_conge FROM ref_type_conge WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalArgumentException("Type congé introuvable pour code: " + code);
    }

    public static int getMotifEvenementFamilialId(Connection c, String code) throws SQLException {
        String sql = "SELECT id_motif FROM ref_motif_evenement_familial WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalArgumentException("Motif familial introuvable pour code: " + code);
    }

    public static int getStatutDemandeCongeId(Connection c, String code) throws SQLException {
        String sql = "SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalArgumentException("Statut demande congé introuvable pour code: " + code);
    }

    public static int getStatutPeriodePaieId(Connection c, String code) throws SQLException {
        String sql = "SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new IllegalArgumentException("Statut période paie introuvable pour code: " + code);
    }
}
