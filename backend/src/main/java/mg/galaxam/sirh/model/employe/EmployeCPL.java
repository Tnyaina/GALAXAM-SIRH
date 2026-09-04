package mg.galaxam.sirh.model.employe;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    // AFFECTATION CARRIERE & CONTRAT ACTIF (SYNTHESE)
    private Integer idContratActif;
    private String numeroContratActif;
    private String codeStatutContrat;
    private String statutContrat;
    private Integer idSite;
    private String nomSite;
    private Integer idDepartement;
    private String nomDepartement;
    private Integer idService;
    private String nomService;
    private Integer idFonction;
    private String nomFonction;
    private Integer idFiliale;
    private String nomFiliale;
    private Date dateEmbauche;

    // ANCIENNETE CALCULEE (BACKEND-DRIVEN)
    private Double ancienneteEffectiveAnnees;
    private Integer ancienneteEffectiveMois;
    private String ancienneteEffectiveTexte;
    private String ancienneteContratTexte;
    private Integer nombreContratsChaine;

    // ÉCHÉANCES & ALERTES CONTRAT (BACKEND-DRIVEN)
    private Date dateFinContrat;
    private Date dateFinEssai;
    private String typeContrat;
    private Integer joursRestantsFinContrat;
    private Integer joursRestantsEssai;
    private String alerteContratTexte;
    private String alerteContratNiveau;

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
        throw new UnsupportedOperationException("L'insertion directe dans une vue n'est pas autorisée.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("La modification directe d'une vue n'est pas autorisée.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("La suppression directe d'une vue n'est pas autorisée.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public EmployeCPL rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT e.*, eac.id_site, rs.libelle AS nom_site, eac.id_departement, rd.libelle AS nom_departement, " +
                "eac.id_service, rserv.libelle AS nom_service, eac.id_fonction, rf.libelle AS nom_fonction, " +
                "ct.id_contrat AS id_contrat_actif, ct.numero_contrat AS numero_contrat_actif, " +
                "rsc.code AS code_statut_contrat, rsc.libelle AS statut_contrat, " +
                "ct.id_filiale, rfil.raison_sociale AS nom_filiale, ct.date_debut AS date_embauche, " +
                "ct.date_fin AS date_fin_contrat, ct.date_fin_essai AS date_fin_essai, rtc.libelle AS type_contrat " +
                "FROM v_employe_cpl e " +
                "LEFT JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe AND eac.id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1), 1) " +
                "LEFT JOIN ref_site rs ON eac.id_site = rs.id_site " +
                "LEFT JOIN ref_departement rd ON eac.id_departement = rd.id_departement " +
                "LEFT JOIN ref_service rserv ON eac.id_service = rserv.id_service " +
                "LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "LEFT JOIN ref_filiale rfil ON ct.id_filiale = rfil.id_filiale " +
                "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE e.id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    EmployeCPL emp = mapResultSet(rs);
                    if (emp.getIdContratActif() != null && emp.getIdContratActif() > 0) {
                        var anc = mg.galaxam.sirh.model.contrat.ContratTravail.calculerAncienneteDetaillee(emp.getIdContratActif(), c);
                        emp.setAncienneteEffectiveTexte((String) anc.get("ancienneteEffectiveTexte"));
                        emp.setAncienneteContratTexte((String) anc.get("ancienneteContratTexte"));
                        if (anc.get("ancienneteEffectiveAnnees") instanceof Number) {
                            emp.setAncienneteEffectiveAnnees(((Number) anc.get("ancienneteEffectiveAnnees")).doubleValue());
                        }
                        if (anc.get("ancienneteEffectiveMois") instanceof Number) {
                            emp.setAncienneteEffectiveMois(((Number) anc.get("ancienneteEffectiveMois")).intValue());
                        }
                        if (anc.get("nombreContratsChaine") instanceof Number) {
                            emp.setNombreContratsChaine(((Number) anc.get("nombreContratsChaine")).intValue());
                        }
                    }
                    return emp;
                }
            }
        }
        return null;
    }

    public static long compterEmployes(Connection c, String query, Boolean estActif) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT COUNT(e.id_employe) FROM v_employe_cpl e WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (estActif != null) {
            sql.append(" AND e.est_actif = ? ");
            params.add(estActif);
        }

        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(e.matricule) LIKE ? OR LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.cin_numero) LIKE ? OR LOWER(e.telephone) LIKE ?) ");
            String search = "%" + query.trim().toLowerCase() + "%";
            params.add(search);
            params.add(search);
            params.add(search);
            params.add(search);
            params.add(search);
        }

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
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0;
    }

    public static List<EmployeCPL> rechercherEmployes(Connection c, String query, Boolean estActif, Integer limit, Integer offset) throws Exception {
        List<EmployeCPL> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT e.*, eac.id_site, rs.libelle AS nom_site, eac.id_departement, rd.libelle AS nom_departement, " +
                "eac.id_service, rserv.libelle AS nom_service, eac.id_fonction, rf.libelle AS nom_fonction, " +
                "ct.id_contrat AS id_contrat_actif, ct.numero_contrat AS numero_contrat_actif, " +
                "rsc.code AS code_statut_contrat, rsc.libelle AS statut_contrat, " +
                "ct.id_filiale, rfil.raison_sociale AS nom_filiale, ct.date_debut AS date_embauche, " +
                "ct.date_fin AS date_fin_contrat, ct.date_fin_essai AS date_fin_essai, rtc.libelle AS type_contrat " +
                "FROM v_employe_cpl e " +
                "LEFT JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe AND eac.id_statut_rh = COALESCE((SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1), 1) " +
                "LEFT JOIN ref_site rs ON eac.id_site = rs.id_site " +
                "LEFT JOIN ref_departement rd ON eac.id_departement = rd.id_departement " +
                "LEFT JOIN ref_service rserv ON eac.id_service = rserv.id_service " +
                "LEFT JOIN ref_fonction rf ON eac.id_fonction = rf.id_fonction " +
                "LEFT JOIN contrat_travail ct ON eac.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "LEFT JOIN ref_filiale rfil ON ct.id_filiale = rfil.id_filiale " +
                "LEFT JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (estActif != null) {
            sql.append(" AND e.est_actif = ? ");
            params.add(estActif);
        }

        if (query != null && !query.isBlank()) {
            sql.append(" AND (LOWER(e.matricule) LIKE ? OR LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.cin_numero) LIKE ? OR LOWER(e.telephone) LIKE ?) ");
            String search = "%" + query.trim().toLowerCase() + "%";
            params.add(search);
            params.add(search);
            params.add(search);
            params.add(search);
            params.add(search);
        }

        sql.append(" ORDER BY e.nom ASC, e.prenom ASC");
        
        if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }
        if (offset != null) {
            sql.append(" OFFSET ?");
            params.add(offset);
        }

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Boolean) {
                    ps.setBoolean(i + 1, (Boolean) p);
                } else if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
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
        return rechercherEmployes(c, null, null, null, null);
    }

    private static String getNullableStringFromRs(ResultSet rs, String colName) {
        try {
            return rs.getString(colName);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getNullableIntFromRs(ResultSet rs, String colName) {
        try {
            Object obj = rs.getObject(colName);
            if (obj != null && obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Date getNullableDateFromRs(ResultSet rs, String colName) {
        try {
            return rs.getDate(colName);
        } catch (Exception e) {
            return null;
        }
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

        // Affectation & Contrat
        emp.setIdContratActif(getNullableIntFromRs(rs, "id_contrat_actif"));
        emp.setNumeroContratActif(getNullableStringFromRs(rs, "numero_contrat_actif"));
        emp.setCodeStatutContrat(getNullableStringFromRs(rs, "code_statut_contrat"));
        emp.setStatutContrat(getNullableStringFromRs(rs, "statut_contrat"));
        emp.setIdSite(getNullableIntFromRs(rs, "id_site"));
        emp.setNomSite(getNullableStringFromRs(rs, "nom_site"));
        emp.setIdDepartement(getNullableIntFromRs(rs, "id_departement"));
        emp.setNomDepartement(getNullableStringFromRs(rs, "nom_departement"));
        emp.setIdService(getNullableIntFromRs(rs, "id_service"));
        emp.setNomService(getNullableStringFromRs(rs, "nom_service"));
        emp.setIdFonction(getNullableIntFromRs(rs, "id_fonction"));
        emp.setNomFonction(getNullableStringFromRs(rs, "nom_fonction"));
        emp.setIdFiliale(getNullableIntFromRs(rs, "id_filiale"));
        emp.setNomFiliale(getNullableStringFromRs(rs, "nom_filiale"));
        emp.setDateEmbauche(getNullableDateFromRs(rs, "date_embauche"));

        emp.setDateFinContrat(getNullableDateFromRs(rs, "date_fin_contrat"));
        emp.setDateFinEssai(getNullableDateFromRs(rs, "date_fin_essai"));
        emp.setTypeContrat(getNullableStringFromRs(rs, "type_contrat"));

        // Calculs des alertes d'échéance contractuelle Backend-Driven
        java.time.LocalDate today = java.time.LocalDate.now();
        if (emp.getDateFinContrat() != null) {
            java.time.LocalDate dFin = emp.getDateFinContrat().toLocalDate();
            long diff = java.time.temporal.ChronoUnit.DAYS.between(today, dFin);
            emp.setJoursRestantsFinContrat((int) diff);
            if (diff < 0) {
                emp.setAlerteContratTexte("Contrat échu (J" + diff + ")");
                emp.setAlerteContratNiveau("DANGER");
            } else if (diff == 0) {
                emp.setAlerteContratTexte("Fin de contrat aujourd'hui");
                emp.setAlerteContratNiveau("DANGER");
            } else if (diff <= 7) {
                emp.setAlerteContratTexte("Fin " + (emp.getTypeContrat() != null ? emp.getTypeContrat() : "contrat") + " : J-" + diff + " (Urgent)");
                emp.setAlerteContratNiveau("DANGER");
            } else if (diff <= 30) {
                emp.setAlerteContratTexte("Fin " + (emp.getTypeContrat() != null ? emp.getTypeContrat() : "contrat") + " : J-" + diff);
                emp.setAlerteContratNiveau("WARNING");
            } else if (diff <= 60) {
                emp.setAlerteContratTexte("Fin " + (emp.getTypeContrat() != null ? emp.getTypeContrat() : "contrat") + " : J-" + diff);
                emp.setAlerteContratNiveau("INFO");
            }
        }

        if (emp.getDateFinEssai() != null && emp.getAlerteContratTexte() == null) {
            java.time.LocalDate dEssai = emp.getDateFinEssai().toLocalDate();
            long diff = java.time.temporal.ChronoUnit.DAYS.between(today, dEssai);
            emp.setJoursRestantsEssai((int) diff);
            if (diff < 0) {
                emp.setAlerteContratTexte("Période d'essai échue (J" + diff + ")");
                emp.setAlerteContratNiveau("DANGER");
            } else if (diff == 0) {
                emp.setAlerteContratTexte("Dernier jour d'essai");
                emp.setAlerteContratNiveau("WARNING");
            } else if (diff <= 15) {
                emp.setAlerteContratTexte("Fin d'essai : J-" + diff);
                emp.setAlerteContratNiveau("WARNING");
            }
        }

        return emp;
    }

    public Integer getIdContratActif() { return idContratActif; }
    public void setIdContratActif(Integer idContratActif) { this.idContratActif = idContratActif; }

    public String getNumeroContratActif() { return numeroContratActif; }
    public void setNumeroContratActif(String numeroContratActif) { this.numeroContratActif = numeroContratActif; }

    public String getCodeStatutContrat() { return codeStatutContrat; }
    public void setCodeStatutContrat(String codeStatutContrat) { this.codeStatutContrat = codeStatutContrat; }

    public String getStatutContrat() { return statutContrat; }
    public void setStatutContrat(String statutContrat) { this.statutContrat = statutContrat; }

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

    public Integer getIdSite() { return idSite; }
    public void setIdSite(Integer idSite) { this.idSite = idSite; }

    public String getNomSite() { return nomSite; }
    public void setNomSite(String nomSite) { this.nomSite = nomSite; }

    public Integer getIdDepartement() { return idDepartement; }
    public void setIdDepartement(Integer idDepartement) { this.idDepartement = idDepartement; }

    public String getNomDepartement() { return nomDepartement; }
    public void setNomDepartement(String nomDepartement) { this.nomDepartement = nomDepartement; }

    public Integer getIdService() { return idService; }
    public void setIdService(Integer idService) { this.idService = idService; }

    public String getNomService() { return nomService; }
    public void setNomService(String nomService) { this.nomService = nomService; }

    public Integer getIdFonction() { return idFonction; }
    public void setIdFonction(Integer idFonction) { this.idFonction = idFonction; }

    public String getNomFonction() { return nomFonction; }
    public void setNomFonction(String nomFonction) { this.nomFonction = nomFonction; }

    public Integer getIdFiliale() { return idFiliale; }
    public void setIdFiliale(Integer idFiliale) { this.idFiliale = idFiliale; }

    public String getNomFiliale() { return nomFiliale; }
    public void setNomFiliale(String nomFiliale) { this.nomFiliale = nomFiliale; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public Double getAncienneteEffectiveAnnees() { return ancienneteEffectiveAnnees; }
    public void setAncienneteEffectiveAnnees(Double ancienneteEffectiveAnnees) { this.ancienneteEffectiveAnnees = ancienneteEffectiveAnnees; }

    public Integer getAncienneteEffectiveMois() { return ancienneteEffectiveMois; }
    public void setAncienneteEffectiveMois(Integer ancienneteEffectiveMois) { this.ancienneteEffectiveMois = ancienneteEffectiveMois; }

    public String getAncienneteEffectiveTexte() { return ancienneteEffectiveTexte; }
    public void setAncienneteEffectiveTexte(String ancienneteEffectiveTexte) { this.ancienneteEffectiveTexte = ancienneteEffectiveTexte; }

    public String getAncienneteContratTexte() { return ancienneteContratTexte; }
    public void setAncienneteContratTexte(String ancienneteContratTexte) { this.ancienneteContratTexte = ancienneteContratTexte; }

    public Integer getNombreContratsChaine() { return nombreContratsChaine; }
    public void setNombreContratsChaine(Integer nombreContratsChaine) { this.nombreContratsChaine = nombreContratsChaine; }

    public Date getDateFinContrat() { return dateFinContrat; }
    public void setDateFinContrat(Date dateFinContrat) { this.dateFinContrat = dateFinContrat; }

    public Date getDateFinEssai() { return dateFinEssai; }
    public void setDateFinEssai(Date dateFinEssai) { this.dateFinEssai = dateFinEssai; }

    public String getTypeContrat() { return typeContrat; }
    public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }

    public Integer getJoursRestantsFinContrat() { return joursRestantsFinContrat; }
    public void setJoursRestantsFinContrat(Integer joursRestantsFinContrat) { this.joursRestantsFinContrat = joursRestantsFinContrat; }

    public Integer getJoursRestantsEssai() { return joursRestantsEssai; }
    public void setJoursRestantsEssai(Integer joursRestantsEssai) { this.joursRestantsEssai = joursRestantsEssai; }

    public String getAlerteContratTexte() { return alerteContratTexte; }
    public void setAlerteContratTexte(String alerteContratTexte) { this.alerteContratTexte = alerteContratTexte; }

    public String getAlerteContratNiveau() { return alerteContratNiveau; }
    public void setAlerteContratNiveau(String alerteContratNiveau) { this.alerteContratNiveau = alerteContratNiveau; }
}
