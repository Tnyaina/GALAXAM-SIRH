package mg.galaxam.sirh.contrat.persistence;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class Employe extends ClassMere {

    private record ActiveEmployment(boolean contractExists, String contractNumber, boolean assignmentExists) {}

    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String sexe; // 'M', 'F'
    private Date dateNaissance;
    private String lieuNaissance;
    private String nationalite = "Malgache";
    private String pereNomPrenom;
    private String mereNomPrenom;
    private String situationFamiliale; // 'Celibataire', 'Marie', 'Divorce', 'Veuf'

    // CIN
    private String cinNumero;
    private Date cinDateDelivrance;
    private String cinLieuDelivrance;
    private Date cinDuplicataDate;
    private String cinDuplicataLieu;

    // Permis
    private String permisConduireNumero;
    private Date permisConduireDateDelivrance;
    private String permisConduireLieuDelivrance;

    // Coordonnées
    private String adresse;
    private String ville;
    private String telephone;
    private String email;

    // Identifiants sociaux & Fiche
    private String numAffiliationCnaps;
    private String photoUrl;
    private Date dateDeclaration;

    // Statut & Soft delete
    private boolean estActif = true;
    private Timestamp dateDesactivation;
    private String motifDesactivation;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Employe() {
        this.setNomTable("EMPLOYE");
    }

    public Employe(Employe source) {
        this();
        if (source == null) return;
        idEmploye = source.idEmploye;
        matricule = source.matricule;
        nom = source.nom;
        prenom = source.prenom;
        sexe = source.sexe;
        dateNaissance = source.dateNaissance;
        lieuNaissance = source.lieuNaissance;
        nationalite = source.nationalite;
        pereNomPrenom = source.pereNomPrenom;
        mereNomPrenom = source.mereNomPrenom;
        situationFamiliale = source.situationFamiliale;
        cinNumero = source.cinNumero;
        cinDateDelivrance = source.cinDateDelivrance;
        cinLieuDelivrance = source.cinLieuDelivrance;
        cinDuplicataDate = source.cinDuplicataDate;
        cinDuplicataLieu = source.cinDuplicataLieu;
        permisConduireNumero = source.permisConduireNumero;
        permisConduireDateDelivrance = source.permisConduireDateDelivrance;
        permisConduireLieuDelivrance = source.permisConduireLieuDelivrance;
        adresse = source.adresse;
        ville = source.ville;
        telephone = source.telephone;
        email = source.email;
        numAffiliationCnaps = source.numAffiliationCnaps;
        photoUrl = source.photoUrl;
        dateDeclaration = source.dateDeclaration;
        estActif = source.estActif;
        dateDesactivation = source.dateDesactivation;
        motifDesactivation = source.motifDesactivation;
        createdAt = source.createdAt;
        updatedAt = source.updatedAt;
    }

    @Override
    public Integer getTuppleID() {
        return this.idEmploye;
    }

    @Override
    public String getAttributIDName() {
        return "id_employe";
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (matricule == null || matricule.isBlank()) {
            throw new IllegalArgumentException("Le matricule est obligatoire.");
        }
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom est obligatoire.");
        }
        if (prenom == null || prenom.isBlank()) {
            throw new IllegalArgumentException("Le prénom est obligatoire.");
        }
        if (sexe == null || sexe.isBlank()) {
            throw new IllegalArgumentException("Le sexe est obligatoire (M/F).");
        }
        if (dateNaissance == null) {
            throw new IllegalArgumentException("La date de naissance est obligatoire.");
        }
        if (lieuNaissance == null || lieuNaissance.isBlank()) {
            throw new IllegalArgumentException("Le lieu de naissance est obligatoire.");
        }
        if (cinNumero == null || cinNumero.isBlank()) {
            throw new IllegalArgumentException("Le numéro de CIN est obligatoire.");
        }
        if (cinDateDelivrance == null) {
            throw new IllegalArgumentException("La date de délivrance de la CIN est obligatoire.");
        }
        if (cinLieuDelivrance == null || cinLieuDelivrance.isBlank()) {
            throw new IllegalArgumentException("Le lieu de délivrance de la CIN est obligatoire.");
        }
        if (adresse == null || adresse.isBlank()) {
            throw new IllegalArgumentException("L'adresse est obligatoire.");
        }
        if (telephone == null || telephone.isBlank()) {
            throw new IllegalArgumentException("Le numéro de téléphone est obligatoire.");
        }

        this.matricule = this.matricule.trim().toUpperCase();
        this.nom = this.nom.trim().toUpperCase();
        this.cinNumero = this.cinNumero.trim();

        Employe existantMatricule = rechercherParMatricule(this.matricule, c);
        if (existantMatricule != null && !existantMatricule.getIdEmploye().equals(this.idEmploye)) {
            throw new IllegalArgumentException("Ce matricule est déjà attribué.");
        }

        Employe existantCin = rechercherParCin(this.cinNumero, c);
        if (existantCin != null && !existantCin.getIdEmploye().equals(this.idEmploye)) {
            throw new IllegalArgumentException("Ce numéro de CIN est déjà enregistré pour un autre employé.");
        }
    }

    private void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.trim().isEmpty()) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value.trim());
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO employe (" +
                "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, nationalite, pere_nom_prenom, mere_nom_prenom, situation_familiale, " +
                "cin_numero, cin_date_delivrance, cin_lieu_delivrance, cin_duplicata_date, cin_duplicata_lieu, " +
                "permis_conduire_numero, permis_conduire_date_delivrance, permis_conduire_lieu_delivrance, " +
                "adresse, ville, telephone, email, num_affiliation_cnaps, photo_url, date_declaration, est_actif" +
                ") VALUES (?, ?, ?, ?::sexe_enum, ?, ?, ?, ?, ?, ?::situation_familiale_enum, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setString(i++, matricule);
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setString(i++, sexe);
            ps.setDate(i++, dateNaissance);
            setNullableString(ps, i++, lieuNaissance);
            setNullableString(ps, i++, nationalite);
            setNullableString(ps, i++, pereNomPrenom);
            setNullableString(ps, i++, mereNomPrenom);
            setNullableString(ps, i++, situationFamiliale);

            setNullableString(ps, i++, cinNumero);
            ps.setDate(i++, cinDateDelivrance);
            setNullableString(ps, i++, cinLieuDelivrance);
            ps.setDate(i++, cinDuplicataDate);
            setNullableString(ps, i++, cinDuplicataLieu);

            setNullableString(ps, i++, permisConduireNumero);
            ps.setDate(i++, permisConduireDateDelivrance);
            setNullableString(ps, i++, permisConduireLieuDelivrance);

            setNullableString(ps, i++, adresse);
            setNullableString(ps, i++, ville);
            setNullableString(ps, i++, telephone);
            setNullableString(ps, i++, email);
            setNullableString(ps, i++, numAffiliationCnaps);
            setNullableString(ps, i++, photoUrl);
            ps.setDate(i++, dateDeclaration);
            ps.setBoolean(i++, estActif);

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idEmploye = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE employe SET " +
                "matricule = ?, nom = ?, prenom = ?, sexe = ?::sexe_enum, date_naissance = ?, lieu_naissance = ?, nationalite = ?, pere_nom_prenom = ?, mere_nom_prenom = ?, situation_familiale = ?::situation_familiale_enum, " +
                "cin_numero = ?, cin_date_delivrance = ?, cin_lieu_delivrance = ?, cin_duplicata_date = ?, cin_duplicata_lieu = ?, " +
                "permis_conduire_numero = ?, permis_conduire_date_delivrance = ?, permis_conduire_lieu_delivrance = ?, " +
                "adresse = ?, ville = ?, telephone = ?, email = ?, num_affiliation_cnaps = ?, photo_url = ?, date_declaration = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_employe = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, matricule);
            ps.setString(i++, nom);
            ps.setString(i++, prenom);
            ps.setString(i++, sexe);
            ps.setDate(i++, dateNaissance);
            setNullableString(ps, i++, lieuNaissance);
            setNullableString(ps, i++, nationalite);
            setNullableString(ps, i++, pereNomPrenom);
            setNullableString(ps, i++, mereNomPrenom);
            setNullableString(ps, i++, situationFamiliale);

            setNullableString(ps, i++, cinNumero);
            ps.setDate(i++, cinDateDelivrance);
            setNullableString(ps, i++, cinLieuDelivrance);
            ps.setDate(i++, cinDuplicataDate);
            setNullableString(ps, i++, cinDuplicataLieu);

            setNullableString(ps, i++, permisConduireNumero);
            ps.setDate(i++, permisConduireDateDelivrance);
            setNullableString(ps, i++, permisConduireLieuDelivrance);

            setNullableString(ps, i++, adresse);
            setNullableString(ps, i++, ville);
            setNullableString(ps, i++, telephone);
            setNullableString(ps, i++, email);
            setNullableString(ps, i++, numAffiliationCnaps);
            setNullableString(ps, i++, photoUrl);
            ps.setDate(i++, dateDeclaration);

            ps.setInt(i++, idEmploye);

            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM employe WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }
    }

    public void desactiver(String motif, Connection c) throws Exception {
        ActiveEmployment activeEmployment = readActiveEmployment(c);
        // 1. Contrôle : Interdiction de désactiver directement un employé avec un contrat actif
        if (activeEmployment.contractExists()) {
            throw new IllegalStateException("Impossible de désactiver directement un employé ayant un contrat de travail ACTIF (N° "
                    + activeEmployment.contractNumber() + "). Veuillez utiliser le workflow RH de sortie (/workflows/transition).");
        }

        // 2. Contrôle : Interdiction de désactiver directement un employé avec une affectation active
        if (activeEmployment.assignmentExists()) {
            throw new IllegalStateException("Impossible de désactiver directement un employé ayant une affectation de carrière ACTIVE. Veuillez utiliser le workflow RH de sortie (/workflows/transition).");
        }

        String validMotif = (motif != null && !motif.isBlank()) ? motif : "Autre";
        String sql = "UPDATE employe SET est_actif = FALSE, date_desactivation = CURRENT_TIMESTAMP, motif_desactivation = ?::motif_desactivation_enum, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, validMotif);
            ps.setInt(2, idEmploye);
            ps.executeUpdate();
        }
        this.estActif = false;
        this.motifDesactivation = validMotif;
    }

    public void reactiver(Connection c) throws Exception {
        ActiveEmployment activeEmployment = readActiveEmployment(c);
        // 1. Contrôle : Pour être réactivé directement, l'employé doit disposer d'un contrat de travail ACTIF valide
        if (!activeEmployment.contractExists()) {
            throw new IllegalStateException("Impossible de réactiver directement cet employé : aucun contrat de travail ACTIF trouvé. Veuillez utiliser le workflow d'embauche ou de réintégration.");
        }

        // 2. Contrôle : L'employé doit également disposer d'une affectation de carrière ACTIVE valide
        if (!activeEmployment.assignmentExists()) {
            throw new IllegalStateException("Impossible de réactiver directement cet employé : aucune affectation de carrière ACTIVE trouvée. Veuillez utiliser le workflow de transition/réintégration pour lui assigner un poste et un site.");
        }

        String sql = "UPDATE employe SET est_actif = TRUE, date_desactivation = NULL, motif_desactivation = NULL, updated_at = CURRENT_TIMESTAMP WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }
        this.estActif = true;
        this.dateDesactivation = null;
        this.motifDesactivation = null;
    }

    private ActiveEmployment readActiveEmployment(Connection c) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM contrat_travail ct "
                + "JOIN ref_statut_rh status ON status.id_statut_rh=ct.id_statut_rh "
                + "WHERE ct.id_employe=? AND status.code='ACTIF') AS contrat_actif, "
                + "(SELECT ct.numero_contrat FROM contrat_travail ct "
                + "JOIN ref_statut_rh status ON status.id_statut_rh=ct.id_statut_rh "
                + "WHERE ct.id_employe=? AND status.code='ACTIF' LIMIT 1) AS numero_contrat, "
                + "EXISTS (SELECT 1 FROM employe_affectation_carriere a "
                + "JOIN ref_statut_rh status ON status.id_statut_rh=a.id_statut_rh "
                + "WHERE a.id_employe=? AND status.code='ACTIF') AS affectation_active";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idEmploye);
            ps.setInt(3, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Impossible de vérifier le contexte actif de l'employé " + idEmploye);
                return new ActiveEmployment(rs.getBoolean("contrat_actif"), rs.getString("numero_contrat"),
                        rs.getBoolean("affectation_active"));
            }
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public Employe rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM employe WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static Employe rechercherParMatricule(String matricule, Connection c) throws Exception {
        String sql = "SELECT * FROM employe WHERE matricule = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, matricule);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static Employe rechercherParCin(String cin, Connection c) throws Exception {
        String sql = "SELECT * FROM employe WHERE cin_numero = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cin);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<Employe> listAll(Connection c) throws Exception {
        List<Employe> list = new ArrayList<>();
        String sql = "SELECT * FROM employe ORDER BY nom, prenom ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        }
        return list;
    }

    public static Employe mapResultSet(ResultSet rs) throws SQLException {
        Employe e = new Employe();
        e.setIdEmploye(rs.getInt("id_employe"));
        e.setMatricule(rs.getString("matricule"));
        e.setNom(rs.getString("nom"));
        e.setPrenom(rs.getString("prenom"));
        e.setSexe(rs.getString("sexe"));
        e.setDateNaissance(rs.getDate("date_naissance"));
        e.setLieuNaissance(rs.getString("lieu_naissance"));
        e.setNationalite(rs.getString("nationalite"));
        e.setPereNomPrenom(rs.getString("pere_nom_prenom"));
        e.setMereNomPrenom(rs.getString("mere_nom_prenom"));
        e.setSituationFamiliale(rs.getString("situation_familiale"));

        e.setCinNumero(rs.getString("cin_numero"));
        e.setCinDateDelivrance(rs.getDate("cin_date_delivrance"));
        e.setCinLieuDelivrance(rs.getString("cin_lieu_delivrance"));
        e.setCinDuplicataDate(rs.getDate("cin_duplicata_date"));
        e.setCinDuplicataLieu(rs.getString("cin_duplicata_lieu"));

        e.setPermisConduireNumero(rs.getString("permis_conduire_numero"));
        e.setPermisConduireDateDelivrance(rs.getDate("permis_conduire_date_delivrance"));
        e.setPermisConduireLieuDelivrance(rs.getString("permis_conduire_lieu_delivrance"));

        e.setAdresse(rs.getString("adresse"));
        e.setVille(rs.getString("ville"));
        e.setTelephone(rs.getString("telephone"));
        e.setEmail(rs.getString("email"));

        e.setNumAffiliationCnaps(rs.getString("num_affiliation_cnaps"));
        e.setPhotoUrl(rs.getString("photo_url"));
        e.setDateDeclaration(rs.getDate("date_declaration"));

        e.setEstActif(rs.getBoolean("est_actif"));
        e.setDateDesactivation(rs.getTimestamp("date_desactivation"));
        e.setMotifDesactivation(rs.getString("motif_desactivation"));

        e.setCreatedAt(rs.getTimestamp("created_at"));
        e.setUpdatedAt(rs.getTimestamp("updated_at"));
        return e;
    }

    // Getters and Setters
    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public Date getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }

    public String getLieuNaissance() { return lieuNaissance; }
    public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

    public String getNationalite() { return nationalite; }
    public void setNationalite(String nationalite) { this.nationalite = nationalite; }

    public String getPereNomPrenom() { return pereNomPrenom; }
    public void setPereNomPrenom(String pereNomPrenom) { this.pereNomPrenom = pereNomPrenom; }

    public String getMereNomPrenom() { return mereNomPrenom; }
    public void setMereNomPrenom(String mereNomPrenom) { this.mereNomPrenom = mereNomPrenom; }

    public String getSituationFamiliale() { return situationFamiliale; }
    public void setSituationFamiliale(String situationFamiliale) { this.situationFamiliale = situationFamiliale; }

    public String getCinNumero() { return cinNumero; }
    public void setCinNumero(String cinNumero) { this.cinNumero = cinNumero; }

    public Date getCinDateDelivrance() { return cinDateDelivrance; }
    public void setCinDateDelivrance(Date cinDateDelivrance) { this.cinDateDelivrance = cinDateDelivrance; }

    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String cinLieuDelivrance) { this.cinLieuDelivrance = cinLieuDelivrance; }

    public Date getCinDuplicataDate() { return cinDuplicataDate; }
    public void setCinDuplicataDate(Date cinDuplicataDate) { this.cinDuplicataDate = cinDuplicataDate; }

    public String getCinDuplicataLieu() { return cinDuplicataLieu; }
    public void setCinDuplicataLieu(String cinDuplicataLieu) { this.cinDuplicataLieu = cinDuplicataLieu; }

    public String getPermisConduireNumero() { return permisConduireNumero; }
    public void setPermisConduireNumero(String permisConduireNumero) { this.permisConduireNumero = permisConduireNumero; }

    public Date getPermisConduireDateDelivrance() { return permisConduireDateDelivrance; }
    public void setPermisConduireDateDelivrance(Date permisConduireDateDelivrance) { this.permisConduireDateDelivrance = permisConduireDateDelivrance; }

    public String getPermisConduireLieuDelivrance() { return permisConduireLieuDelivrance; }
    public void setPermisConduireLieuDelivrance(String permisConduireLieuDelivrance) { this.permisConduireLieuDelivrance = permisConduireLieuDelivrance; }

    public String getAdresse() { return adresse; }
    public void setAdresse(String adresse) { this.adresse = adresse; }

    public String getVille() { return ville; }
    public void setVille(String ville) { this.ville = ville; }

    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNumAffiliationCnaps() { return numAffiliationCnaps; }
    public void setNumAffiliationCnaps(String numAffiliationCnaps) { this.numAffiliationCnaps = numAffiliationCnaps; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public Date getDateDeclaration() { return dateDeclaration; }
    public void setDateDeclaration(Date dateDeclaration) { this.dateDeclaration = dateDeclaration; }

    public boolean isEstActif() { return estActif; }
    public void setEstActif(boolean estActif) { this.estActif = estActif; }

    public Timestamp getDateDesactivation() { return dateDesactivation; }
    public void setDateDesactivation(Timestamp dateDesactivation) { this.dateDesactivation = dateDesactivation; }

    public String getMotifDesactivation() { return motifDesactivation; }
    public void setMotifDesactivation(String motifDesactivation) { this.motifDesactivation = motifDesactivation; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
