package mg.galaxam.sirh.contrat.api.dto;

import java.sql.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonSetter;

public class EmployeRequest {

    private static final String[] DATE_FORMATS = new String[] {
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "d/M/yyyy",
        "yyyy/MM/dd",
        "dd-MM-yyyy"
    };

    public static Date parseFlexibleDate(String val) {
        if (val == null || val.trim().isEmpty() || "-".equals(val.trim()) || "—".equals(val.trim())) {
            return null;
        }
        String s = val.trim();
        if (s.contains("T")) {
            s = s.split("T")[0];
        }
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                sdf.setLenient(false);
                java.util.Date parsed = sdf.parse(s);
                return new Date(parsed.getTime());
            } catch (ParseException ignored) {}
        }
        try {
            return Date.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String matricule;
    private String nom;
    private String prenom;
    private String sexe;
    private Date dateNaissance;
    private String lieuNaissance;
    private String nationalite = "Malgache";
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

    // IDENTIFIANTS SOCIAUX & FICHE
    private String numAffiliationCnaps;
    private String photoUrl;
    private Date dateDeclaration;

    // CONJOINT
    private ConjointRequest conjoint;

    // ENFANTS
    private List<EnfantRequest> enfants;

    // CONTACTS URGENCE
    private List<ContactUrgenceRequest> contactsUrgence;

    // COMPTE BANCAIRE PRINCIPAL
    private CompteBanqueRequest compteBanquePrincipal;

    public static class ConjointRequest {
        private String nom;
        private String prenom;
        private Date dateNaissance;
        private String lieuNaissance;
        private String societe;
        private String fonction;

        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }

        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }

        public Date getDateNaissance() { return dateNaissance; }
        public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }
        @JsonSetter("dateNaissance")
        public void setDateNaissance(String val) { this.dateNaissance = parseFlexibleDate(val); }

        public String getLieuNaissance() { return lieuNaissance; }
        public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

        public String getSociete() { return societe; }
        public void setSociete(String societe) { this.societe = societe; }

        public String getFonction() { return fonction; }
        public void setFonction(String fonction) { this.fonction = fonction; }
    }

    public static class EnfantRequest {
        private String nom;
        private String prenom;
        private String sexe;
        private Date dateNaissance;
        private String lieuNaissance;
        private boolean aCharge = true;
        private boolean estEtudiant = false;
        private Date dateFinScolarite;
        private boolean estHandicape = false;
        private boolean certificatFourni = false;

        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }

        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }

        public String getSexe() { return sexe; }
        public void setSexe(String sexe) { this.sexe = sexe; }

        public Date getDateNaissance() { return dateNaissance; }
        public void setDateNaissance(Date dateNaissance) { this.dateNaissance = dateNaissance; }
        @JsonSetter("dateNaissance")
        public void setDateNaissance(String val) { this.dateNaissance = parseFlexibleDate(val); }

        public String getLieuNaissance() { return lieuNaissance; }
        public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }

        public boolean isACharge() { return aCharge; }
        public void setACharge(boolean aCharge) { this.aCharge = aCharge; }

        public boolean isEstEtudiant() { return estEtudiant; }
        public void setEstEtudiant(boolean estEtudiant) { this.estEtudiant = estEtudiant; }

        public Date getDateFinScolarite() { return dateFinScolarite; }
        public void setDateFinScolarite(Date dateFinScolarite) { this.dateFinScolarite = dateFinScolarite; }
        @JsonSetter("dateFinScolarite")
        public void setDateFinScolarite(String val) { this.dateFinScolarite = parseFlexibleDate(val); }

        public boolean isEstHandicape() { return estHandicape; }
        public void setEstHandicape(boolean estHandicape) { this.estHandicape = estHandicape; }

        public boolean isCertificatFourni() { return certificatFourni; }
        public void setCertificatFourni(boolean certificatFourni) { this.certificatFourni = certificatFourni; }
    }

    public static class ContactUrgenceRequest {
        private String nom;
        private String prenom;
        private String lienParente;
        private String adresse;
        private String telephone;
        private String societe;

        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }

        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }

        public String getLienParente() { return lienParente; }
        public void setLienParente(String lienParente) { this.lienParente = lienParente; }

        public String getAdresse() { return adresse; }
        public void setAdresse(String adresse) { this.adresse = adresse; }

        public String getTelephone() { return telephone; }
        public void setTelephone(String telephone) { this.telephone = telephone; }

        public String getSociete() { return societe; }
        public void setSociete(String societe) { this.societe = societe; }
    }

    public static class CompteBanqueRequest {
        private Integer idBanque;
        private String agence;
        private String numCompteRib;
        private String titulaireCompte;

        public Integer getIdBanque() { return idBanque; }
        public void setIdBanque(Integer idBanque) { this.idBanque = idBanque; }

        public String getAgence() { return agence; }
        public void setAgence(String agence) { this.agence = agence; }

        public String getNumCompteRib() { return numCompteRib; }
        public void setNumCompteRib(String numCompteRib) { this.numCompteRib = numCompteRib; }

        public String getTitulaireCompte() { return titulaireCompte; }
        public void setTitulaireCompte(String titulaireCompte) { this.titulaireCompte = titulaireCompte; }
    }

    public static class DesactivationRequest {
        private String motif;
        public String getMotif() { return motif; }
        public void setMotif(String motif) { this.motif = motif; }
    }

    // Getters and Setters
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
    @JsonSetter("dateNaissance")
    public void setDateNaissance(String val) { this.dateNaissance = parseFlexibleDate(val); }

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
    @JsonSetter("cinDateDelivrance")
    public void setCinDateDelivrance(String val) { this.cinDateDelivrance = parseFlexibleDate(val); }

    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String cinLieuDelivrance) { this.cinLieuDelivrance = cinLieuDelivrance; }

    public Date getCinDuplicataDate() { return cinDuplicataDate; }
    public void setCinDuplicataDate(Date cinDuplicataDate) { this.cinDuplicataDate = cinDuplicataDate; }
    @JsonSetter("cinDuplicataDate")
    public void setCinDuplicataDate(String val) { this.cinDuplicataDate = parseFlexibleDate(val); }

    public String getCinDuplicataLieu() { return cinDuplicataLieu; }
    public void setCinDuplicataLieu(String cinDuplicataLieu) { this.cinDuplicataLieu = cinDuplicataLieu; }

    public String getPermisConduireNumero() { return permisConduireNumero; }
    public void setPermisConduireNumero(String permisConduireNumero) { this.permisConduireNumero = permisConduireNumero; }

    public Date getPermisConduireDateDelivrance() { return permisConduireDateDelivrance; }
    public void setPermisConduireDateDelivrance(Date permisConduireDateDelivrance) { this.permisConduireDateDelivrance = permisConduireDateDelivrance; }
    @JsonSetter("permisConduireDateDelivrance")
    public void setPermisConduireDateDelivrance(String val) { this.permisConduireDateDelivrance = parseFlexibleDate(val); }

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
    @JsonSetter("dateDeclaration")
    public void setDateDeclaration(String val) { this.dateDeclaration = parseFlexibleDate(val); }

    public ConjointRequest getConjoint() { return conjoint; }
    public void setConjoint(ConjointRequest conjoint) { this.conjoint = conjoint; }

    public List<EnfantRequest> getEnfants() { return enfants; }
    public void setEnfants(List<EnfantRequest> enfants) { this.enfants = enfants; }

    public List<ContactUrgenceRequest> getContactsUrgence() { return contactsUrgence; }
    public void setContactsUrgence(List<ContactUrgenceRequest> contactsUrgence) { this.contactsUrgence = contactsUrgence; }

    public CompteBanqueRequest getCompteBanquePrincipal() { return compteBanquePrincipal; }
    public void setCompteBanquePrincipal(CompteBanqueRequest compteBanquePrincipal) { this.compteBanquePrincipal = compteBanquePrincipal; }
}
