package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.common.dto.ActionCapability;

public class VDemandeCongeCpl extends ClassMere {

    private Integer idDemandeConge;
    private Integer idEmploye;
    private String matricule;
    private String nom;
    private String prenom;

    private Integer idTypeConge;
    private String codeTypeConge;
    private String libelleTypeConge;
    private boolean estPaye;
    private Boolean estRemunere;
    private boolean suspendAcquisitionSolde;

    private Integer idMotifEvenementFamilial;
    private String codeMotifEvenement;
    private String libelleMotifEvenement;

    private Integer idStatutDemandeConge;
    private String codeStatutDemande;
    private String libelleStatutDemande;

    private Date dateDebut;
    private Date dateFin;
    private Date dateDemande;
    private BigDecimal nbJoursDecomptes;

    private boolean derogationSoldeNegatif;
    private boolean derogationDelaiPreavis;
    private boolean extensionPathologiqueMaternite;
    private boolean estDemiJournee;
    private String momentDemiJournee;
    private boolean justificatifFourni;
    private String commentaire;

    private Integer saisiPar;
    private String saisiParIdentifiant;
    private Integer validePar;
    private String valideParIdentifiant;
    private Timestamp dateValidation;
    private String motifRefus;

    private Timestamp createdAt;
    private Timestamp updatedAt;
    private Map<String, ActionCapability> availableActions = Map.of();

    public VDemandeCongeCpl() {
        this.setNomTable("V_DEMANDE_CONGE_CPL");
    }

    @Override
    public Integer getTuppleID() {
        return this.idDemandeConge;
    }

    @Override
    public String getAttributIDName() {
        return "id_demande_conge";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        throw new UnsupportedOperationException("Vue en lecture seule.");
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        throw new UnsupportedOperationException("Vue en lecture seule.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        throw new UnsupportedOperationException("Vue en lecture seule.");
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public static VDemandeCongeCpl rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null) return null;
        String sql = "SELECT * FROM v_demande_conge_cpl WHERE id_demande_conge = ?";
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

    public static long countParEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return 0;
        String sql = "SELECT COUNT(*) FROM v_demande_conge_cpl WHERE id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    public static List<VDemandeCongeCpl> listParEmploye(Integer idEmploye, Connection c, Integer limit, Integer offset) throws Exception {
        List<VDemandeCongeCpl> list = new ArrayList<>();
        if (idEmploye == null) return list;
        
        StringBuilder sql = new StringBuilder("SELECT * FROM v_demande_conge_cpl WHERE id_employe = ? ORDER BY date_debut DESC, created_at DESC");
        List<Object> params = new ArrayList<>();
        params.add(idEmploye);
        
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
                if (params.get(i) instanceof Integer) {
                    ps.setInt(i + 1, (Integer) params.get(i));
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

    public static long countAll(Connection c, String codeStatut, String keyword, Integer idTypeConge) throws Exception {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM v_demande_conge_cpl WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (codeStatut != null && !codeStatut.isBlank()) {
            sql.append(" AND code_statut_demande = ? ");
            params.add(codeStatut.trim().toUpperCase());
        }

        if (idTypeConge != null && idTypeConge > 0) {
            sql.append(" AND id_type_conge = ? ");
            params.add(idTypeConge);
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(nom) LIKE ? OR LOWER(prenom) LIKE ? OR LOWER(matricule) LIKE ? OR LOWER(libelle_type_conge) LIKE ?) ");
            String search = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 4; i++) params.add(search);
        }

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else {
                    ps.setString(i + 1, (String) p);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return 0;
    }

    public static long countAll(Connection c, String codeStatut, String keyword) throws Exception {
        return countAll(c, codeStatut, keyword, null);
    }

    public static List<VDemandeCongeCpl> listAll(Connection c, String codeStatut, String keyword, Integer idTypeConge, Integer limit, Integer offset) throws Exception {
        List<VDemandeCongeCpl> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM v_demande_conge_cpl WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (codeStatut != null && !codeStatut.isBlank()) {
            sql.append(" AND code_statut_demande = ? ");
            params.add(codeStatut.trim().toUpperCase());
        }

        if (idTypeConge != null && idTypeConge > 0) {
            sql.append(" AND id_type_conge = ? ");
            params.add(idTypeConge);
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(nom) LIKE ? OR LOWER(prenom) LIKE ? OR LOWER(matricule) LIKE ? OR LOWER(libelle_type_conge) LIKE ?) ");
            String search = "%" + keyword.trim().toLowerCase() + "%";
            for (int i = 0; i < 4; i++) params.add(search);
        }

        sql.append(" ORDER BY date_debut DESC, created_at DESC");

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
                if (p instanceof Integer) {
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

    public static List<VDemandeCongeCpl> listAll(Connection c, String codeStatut, String keyword, Integer limit, Integer offset) throws Exception {
        return listAll(c, codeStatut, keyword, null, limit, offset);
    }

    private static VDemandeCongeCpl mapResultSet(ResultSet rs) throws Exception {
        VDemandeCongeCpl obj = new VDemandeCongeCpl();
        obj.setIdDemandeConge(rs.getInt("id_demande_conge"));
        obj.setIdEmploye(rs.getInt("id_employe"));
        obj.setMatricule(rs.getString("matricule"));
        obj.setNom(rs.getString("nom"));
        obj.setPrenom(rs.getString("prenom"));

        obj.setIdTypeConge(rs.getInt("id_type_conge"));
        obj.setCodeTypeConge(rs.getString("code_type_conge"));
        obj.setLibelleTypeConge(rs.getString("libelle_type_conge"));
        obj.setEstPaye(rs.getBoolean("est_paye"));
        try {
            if (rs.getObject("est_remunere") != null) {
                obj.setEstRemunere(rs.getBoolean("est_remunere"));
            }
        } catch (Exception ignored) {}
        obj.setSuspendAcquisitionSolde(rs.getBoolean("suspend_acquisition_solde"));

        obj.setIdMotifEvenementFamilial(getNullableIntFromRs(rs, "id_motif_evenement_familial"));
        obj.setCodeMotifEvenement(rs.getString("code_motif_evenement"));
        obj.setLibelleMotifEvenement(rs.getString("libelle_motif_evenement"));

        obj.setIdStatutDemandeConge(rs.getInt("id_statut_demande_conge"));
        obj.setCodeStatutDemande(rs.getString("code_statut_demande"));
        obj.setLibelleStatutDemande(rs.getString("libelle_statut_demande"));

        obj.setDateDebut(rs.getDate("date_debut"));
        obj.setDateFin(rs.getDate("date_fin"));
        obj.setDateDemande(rs.getDate("date_demande"));
        obj.setNbJoursDecomptes(rs.getBigDecimal("nb_jours_decomptes"));

        obj.setDerogationSoldeNegatif(rs.getBoolean("derogation_solde_negatif"));
        obj.setDerogationDelaiPreavis(rs.getBoolean("derogation_delai_preavis"));
        obj.setExtensionPathologiqueMaternite(rs.getBoolean("extension_pathologique_maternite"));
        obj.setEstDemiJournee(rs.getBoolean("est_demi_journee"));
        obj.setMomentDemiJournee(rs.getString("moment_demi_journee"));
        obj.setJustificatifFourni(rs.getBoolean("justificatif_fourni"));
        obj.setCommentaire(rs.getString("commentaire"));

        obj.setSaisiPar(getNullableIntFromRs(rs, "saisi_par"));
        obj.setSaisiParIdentifiant(rs.getString("saisi_par_identifiant"));
        obj.setValidePar(getNullableIntFromRs(rs, "valide_par"));
        obj.setValideParIdentifiant(rs.getString("valide_par_identifiant"));
        obj.setDateValidation(rs.getTimestamp("date_validation"));
        obj.setMotifRefus(rs.getString("motif_refus"));

        obj.setCreatedAt(rs.getTimestamp("created_at"));
        obj.setUpdatedAt(rs.getTimestamp("updated_at"));
        return obj;
    }

    private static Integer getNullableIntFromRs(ResultSet rs, String colName) throws Exception {
        int val = rs.getInt(colName);
        return rs.wasNull() ? null : val;
    }

    // Getters / Setters
    public Integer getIdDemandeConge() { return idDemandeConge; }
    public void setIdDemandeConge(Integer idDemandeConge) { this.idDemandeConge = idDemandeConge; }

    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }

    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }

    public Integer getIdTypeConge() { return idTypeConge; }
    public void setIdTypeConge(Integer idTypeConge) { this.idTypeConge = idTypeConge; }

    public String getCodeTypeConge() { return codeTypeConge; }
    public void setCodeTypeConge(String codeTypeConge) { this.codeTypeConge = codeTypeConge; }

    public String getLibelleTypeConge() { return libelleTypeConge; }
    public void setLibelleTypeConge(String libelleTypeConge) { this.libelleTypeConge = libelleTypeConge; }

    public boolean isEstPaye() { return estPaye; }
    public void setEstPaye(boolean estPaye) { this.estPaye = estPaye; }

    public boolean isSuspendAcquisitionSolde() { return suspendAcquisitionSolde; }
    public void setSuspendAcquisitionSolde(boolean suspendAcquisitionSolde) { this.suspendAcquisitionSolde = suspendAcquisitionSolde; }

    public Integer getIdMotifEvenementFamilial() { return idMotifEvenementFamilial; }
    public void setIdMotifEvenementFamilial(Integer idMotifEvenementFamilial) { this.idMotifEvenementFamilial = idMotifEvenementFamilial; }

    public String getCodeMotifEvenement() { return codeMotifEvenement; }
    public void setCodeMotifEvenement(String codeMotifEvenement) { this.codeMotifEvenement = codeMotifEvenement; }

    public String getLibelleMotifEvenement() { return libelleMotifEvenement; }
    public void setLibelleMotifEvenement(String libelleMotifEvenement) { this.libelleMotifEvenement = libelleMotifEvenement; }

    public Integer getIdStatutDemandeConge() { return idStatutDemandeConge; }
    public void setIdStatutDemandeConge(Integer idStatutDemandeConge) { this.idStatutDemandeConge = idStatutDemandeConge; }

    public String getCodeStatutDemande() { return codeStatutDemande; }
    public void setCodeStatutDemande(String codeStatutDemande) { this.codeStatutDemande = codeStatutDemande; }

    public String getLibelleStatutDemande() { return libelleStatutDemande; }
    public void setLibelleStatutDemande(String libelleStatutDemande) { this.libelleStatutDemande = libelleStatutDemande; }

    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }

    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }

    public Date getDateDemande() { return dateDemande; }
    public void setDateDemande(Date dateDemande) { this.dateDemande = dateDemande; }

    public BigDecimal getNbJoursDecomptes() { return nbJoursDecomptes; }
    public void setNbJoursDecomptes(BigDecimal nbJoursDecomptes) { this.nbJoursDecomptes = nbJoursDecomptes; }

    public boolean isDerogationSoldeNegatif() { return derogationSoldeNegatif; }
    public void setDerogationSoldeNegatif(boolean derogationSoldeNegatif) { this.derogationSoldeNegatif = derogationSoldeNegatif; }

    public boolean isDerogationDelaiPreavis() { return derogationDelaiPreavis; }
    public void setDerogationDelaiPreavis(boolean derogationDelaiPreavis) { this.derogationDelaiPreavis = derogationDelaiPreavis; }

    public boolean isExtensionPathologiqueMaternite() { return extensionPathologiqueMaternite; }
    public void setExtensionPathologiqueMaternite(boolean extensionPathologiqueMaternite) { this.extensionPathologiqueMaternite = extensionPathologiqueMaternite; }

    public boolean isEstDemiJournee() { return estDemiJournee; }
    public void setEstDemiJournee(boolean estDemiJournee) { this.estDemiJournee = estDemiJournee; }

    public String getMomentDemiJournee() { return momentDemiJournee; }
    public void setMomentDemiJournee(String momentDemiJournee) { this.momentDemiJournee = momentDemiJournee; }

    public boolean isJustificatifFourni() { return justificatifFourni; }
    public void setJustificatifFourni(boolean justificatifFourni) { this.justificatifFourni = justificatifFourni; }

    public String getCommentaire() { return commentaire; }
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }

    public Integer getSaisiPar() { return saisiPar; }
    public void setSaisiPar(Integer saisiPar) { this.saisiPar = saisiPar; }

    public String getSaisiParIdentifiant() { return saisiParIdentifiant; }
    public void setSaisiParIdentifiant(String saisiParIdentifiant) { this.saisiParIdentifiant = saisiParIdentifiant; }

    public Integer getValidePar() { return validePar; }
    public void setValidePar(Integer validePar) { this.validePar = validePar; }

    public String getValideParIdentifiant() { return valideParIdentifiant; }
    public void setValideParIdentifiant(String valideParIdentifiant) { this.valideParIdentifiant = valideParIdentifiant; }

    public Timestamp getDateValidation() { return dateValidation; }
    public void setDateValidation(Timestamp dateValidation) { this.dateValidation = dateValidation; }

    public String getMotifRefus() { return motifRefus; }
    public void setMotifRefus(String motifRefus) { this.motifRefus = motifRefus; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getEstRemunere() { return estRemunere; }
    public void setEstRemunere(Boolean estRemunere) { this.estRemunere = estRemunere; }


    /**
     * Indique si cette demande est dans un état validé.
     * Sérialisé en JSON par Jackson — consommé directement par le frontend (backend-driven, Phase 6).
     * Évite que le frontend recalcule l'état depuis codeStatutDemande.
     */
    @com.fasterxml.jackson.annotation.JsonProperty("isValide")
    public boolean isValide() {
        return "VALIDE".equalsIgnoreCase(codeStatutDemande);
    }

    /** Décisions calculées par CongeService avec l'état et les permissions courantes. */
    public Map<String, ActionCapability> getAvailableActions() {
        return availableActions;
    }

    public void setAvailableActions(Map<String, ActionCapability> availableActions) {
        this.availableActions = availableActions != null ? Map.copyOf(availableActions) : Map.of();
    }
}
