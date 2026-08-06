package mg.galaxam.sirh.employe;

import bean.ClassMere;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeCPL extends ClassMere {

    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;
    private String nomComplet;
    private String sexe;
    private Date dateNaissance;
    private Integer age;
    private String lieuNaissance;
    private String nationalite;
    private String pereNomPrenom;
    private String mereNomPrenom;
    private String situationFamiliale;

    // CIN
    private String cinNumero;
    private Date cinDateDelivrance;
    private String cinLieuDelivrance;
    private Date cinDuplicataDate;
    private String cinDuplicataLieu;

    // PERMIS
    private String permisConduireNumero;
    private Date permisConduireDateDelivrance;
    private String permisConduireLieuDelivrance;

    // COORDONNEES
    private String adresse;
    private String ville;
    private String telephone;
    private String email;

    // IDENTIFIANTS SOCIAUX & STATUT
    private String numAffiliationCnaps;
    private String photoUrl;
    private Date dateDeclaration;
    private boolean estActif;
    private Timestamp dateDesactivation;
    private String motifDesactivation;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // CONJOINT SYNTHESE
    private String conjointNom;
    private String conjointPrenom;
    private String conjointNomComplet;
    private String conjointSociete;
    private String conjointFonction;

    // COMPTE BANCAIRE PRINCIPAL SYNTHESE
    private Integer idCompteBanquePrincipal;
    private Integer idBanquePrincipal;
    private String codeBanquePrincipal;
    private String nomBanquePrincipal;
    private String agenceBanquePrincipal;
    private String numCompteRibPrincipal;
    private String titulaireComptePrincipal;

    // METRIQUES
    private Integer nbEnfants;
    private Integer nbEnfantsACharge;
    private Integer nbContactsUrgence;

    public EmployeCPL() {
        this.setNomTable("V_EMPLOYE_CPL");
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
    protected ClassMere inserer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_employe_cpl est en lecture seule.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_employe_cpl est en lecture seule.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La vue v_employe_cpl est en lecture seule.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeCPL rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT * FROM v_employe_cpl WHERE id_employe = ?";
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

    public static List<EmployeCPL> rechercherEmployes(Connection c, String query, Boolean estActif) throws Exception {
        List<EmployeCPL> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM v_employe_cpl WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (estActif != null) {
            sql.append(" AND est_actif = ? ");
            params.add(estActif);
        }

        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(matricule) LIKE ? OR LOWER(nom) LIKE ? OR LOWER(prenom) LIKE ? OR LOWER(cin_numero) LIKE ? OR LOWER(telephone) LIKE ?) ");
            String search = "%" + query.trim().toLowerCase() + "%";
            params.add(search);
            params.add(search);
            params.add(search);
            params.add(search);
            params.add(search);
        }

        sql.append(" ORDER BY nom ASC, prenom ASC");

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Boolean) {
                    ps.setBoolean(i + 1, (Boolean) p);
                } else {
                    ps.setString(i + 1, (String) p);
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    public static List<EmployeCPL> listAll(Connection c) throws Exception {
        return rechercherEmployes(c, null, null);
    }

    private static EmployeCPL mapResultSet(ResultSet rs) throws SQLException {
        EmployeCPL emp = new EmployeCPL();
        emp.setIdEmploye(rs.getInt("id_employe"));
        emp.setMatricule(rs.getString("matricule"));
        emp.setNom(rs.getString("nom"));
        emp.setPrenom(rs.getString("prenom"));
        emp.setNomComplet(rs.getString("nom_complet"));
        emp.setSexe(rs.getString("sexe"));
        emp.setDateNaissance(rs.getDate("date_naissance"));
        emp.setAge((Integer) rs.getObject("age"));
        emp.setLieuNaissance(rs.getString("lieu_naissance"));
        emp.setNationalite(rs.getString("nationalite"));
        emp.setPereNomPrenom(rs.getString("pere_nom_prenom"));
        emp.setMereNomPrenom(rs.getString("mere_nom_prenom"));
        emp.setSituationFamiliale(rs.getString("situation_familiale"));

        emp.setCinNumero(rs.getString("cin_numero"));
        emp.setCinDateDelivrance(rs.getDate("cin_date_delivrance"));
        emp.setCinLieuDelivrance(rs.getString("cin_lieu_delivrance"));
        emp.setCinDuplicataDate(rs.getDate("cin_duplicata_date"));
        emp.setCinDuplicataLieu(rs.getString("cin_duplicata_lieu"));

        emp.setPermisConduireNumero(rs.getString("permis_conduire_numero"));
        emp.setPermisConduireDateDelivrance(rs.getDate("permis_conduire_date_delivrance"));
        emp.setPermisConduireLieuDelivrance(rs.getString("permis_conduire_lieu_delivrance"));

        emp.setAdresse(rs.getString("adresse"));
        emp.setVille(rs.getString("ville"));
        emp.setTelephone(rs.getString("telephone"));
        emp.setEmail(rs.getString("email"));

        emp.setNumAffiliationCnaps(rs.getString("num_affiliation_cnaps"));
        emp.setPhotoUrl(rs.getString("photo_url"));
        emp.setDateDeclaration(rs.getDate("date_declaration"));

        emp.setEstActif(rs.getBoolean("est_actif"));
        emp.setDateDesactivation(rs.getTimestamp("date_desactivation"));
        emp.setMotifDesactivation(rs.getString("motif_desactivation"));
        emp.setCreatedAt(rs.getTimestamp("created_at"));
        emp.setUpdatedAt(rs.getTimestamp("updated_at"));

        emp.setConjointNom(rs.getString("conjoint_nom"));
        emp.setConjointPrenom(rs.getString("conjoint_prenom"));
        emp.setConjointNomComplet(rs.getString("conjoint_nom_complet"));
        emp.setConjointSociete(rs.getString("conjoint_societe"));
        emp.setConjointFonction(rs.getString("conjoint_fonction"));

        emp.setIdCompteBanquePrincipal((Integer) rs.getObject("id_compte_banque_principal"));
        emp.setIdBanquePrincipal((Integer) rs.getObject("id_banque_principal"));
        emp.setCodeBanquePrincipal(rs.getString("code_banque_principal"));
        emp.setNomBanquePrincipal(rs.getString("nom_banque_principal"));
        emp.setAgenceBanquePrincipal(rs.getString("agence_banque_principal"));
        emp.setNumCompteRibPrincipal(rs.getString("num_compte_rib_principal"));
        emp.setTitulaireComptePrincipal(rs.getString("titulaire_compte_principal"));

        emp.setNbEnfants((Integer) rs.getObject("nb_enfants"));
        emp.setNbEnfantsACharge((Integer) rs.getObject("nb_enfants_a_charge"));
        emp.setNbContactsUrgence((Integer) rs.getObject("nb_contacts_urgence"));

        return emp;
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

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }

    public String getSexe() { return sexe; }
    public void setSexe(String sexe) { this.sexe = sexe; }

    public Date getDateNaissance() { return dateNaissance; }
    public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }

    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }

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

    public String getConjointNom() { return conjointNom; }
    public void setConjointNom(String conjointNom) { this.conjointNom = conjointNom; }

    public String getConjointPrenom() { return conjointPrenom; }
    public void setConjointPrenom(String conjointPrenom) { this.conjointPrenom = conjointPrenom; }

    public String getConjointNomComplet() { return conjointNomComplet; }
    public void setConjointNomComplet(String conjointNomComplet) { this.conjointNomComplet = conjointNomComplet; }

    public String getConjointSociete() { return conjointSociete; }
    public void setConjointSociete(String conjointSociete) { this.conjointSociete = conjointSociete; }

    public String getConjointFonction() { return conjointFonction; }
    public void setConjointFonction(String conjointFonction) { this.conjointFonction = conjointFonction; }

    public Integer getIdCompteBanquePrincipal() { return idCompteBanquePrincipal; }
    public void setIdCompteBanquePrincipal(Integer idCompteBanquePrincipal) { this.idCompteBanquePrincipal = idCompteBanquePrincipal; }

    public Integer getIdBanquePrincipal() { return idBanquePrincipal; }
    public void setIdBanquePrincipal(Integer idBanquePrincipal) { this.idBanquePrincipal = idBanquePrincipal; }

    public String getCodeBanquePrincipal() { return codeBanquePrincipal; }
    public void setCodeBanquePrincipal(String codeBanquePrincipal) { this.codeBanquePrincipal = codeBanquePrincipal; }

    public String getNomBanquePrincipal() { return nomBanquePrincipal; }
    public void setNomBanquePrincipal(String nomBanquePrincipal) { this.nomBanquePrincipal = nomBanquePrincipal; }

    public String getAgenceBanquePrincipal() { return agenceBanquePrincipal; }
    public void setAgenceBanquePrincipal(String agenceBanquePrincipal) { this.agenceBanquePrincipal = agenceBanquePrincipal; }

    public String getNumCompteRibPrincipal() { return numCompteRibPrincipal; }
    public void setNumCompteRibPrincipal(String numCompteRibPrincipal) { this.numCompteRibPrincipal = numCompteRibPrincipal; }

    public String getTitulaireComptePrincipal() { return titulaireComptePrincipal; }
    public void setTitulaireComptePrincipal(String titulaireComptePrincipal) { this.titulaireComptePrincipal = titulaireComptePrincipal; }

    public Integer getNbEnfants() { return nbEnfants; }
    public void setNbEnfants(Integer nbEnfants) { this.nbEnfants = nbEnfants; }

    public Integer getNbEnfantsACharge() { return nbEnfantsACharge; }
    public void setNbEnfantsACharge(Integer nbEnfantsACharge) { this.nbEnfantsACharge = nbEnfantsACharge; }

    public Integer getNbContactsUrgence() { return nbContactsUrgence; }
    public void setNbContactsUrgence(Integer nbContactsUrgence) { this.nbContactsUrgence = nbContactsUrgence; }
}
