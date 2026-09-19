package mg.galaxam.sirh.api.dto.document;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DocumentRhDTO {

    public static class EntrepriseInfoDTO {
        private String nom;
        private String raisonSociale;
        private String adresse;
        private String codePostal;
        private String ville;
        private String pays;
        private String telephone;
        private String email;
        private String siteWeb;
        private String nif;
        private String stat;
        private String rcs;
        private String logoUrl;

        // Getters & Setters
        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }
        public String getRaisonSociale() { return raisonSociale; }
        public void setRaisonSociale(String raisonSociale) { this.raisonSociale = raisonSociale; }
        public String getAdresse() { return adresse; }
        public void setAdresse(String adresse) { this.adresse = adresse; }
        public String getCodePostal() { return codePostal; }
        public void setCodePostal(String codePostal) { this.codePostal = codePostal; }
        public String getVille() { return ville; }
        public void setVille(String ville) { this.ville = ville; }
        public String getPays() { return pays; }
        public void setPays(String pays) { this.pays = pays; }
        public String getTelephone() { return telephone; }
        public void setTelephone(String telephone) { this.telephone = telephone; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getSiteWeb() { return siteWeb; }
        public void setSiteWeb(String siteWeb) { this.siteWeb = siteWeb; }
        public String getNif() { return nif; }
        public void setNif(String nif) { this.nif = nif; }
        public String getStat() { return stat; }
        public void setStat(String stat) { this.stat = stat; }
        public String getRcs() { return rcs; }
        public void setRcs(String rcs) { this.rcs = rcs; }
        public String getLogoUrl() { return logoUrl; }
        public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    }

    public static class EmployeDocumentDTO {
        private Integer idEmploye;
        private String matricule;
        private String civilite; // Madame / Monsieur
        private String nom;
        private String prenom;
        private String nomComplet;
        private LocalDate dateNaissance;
        private String lieuNaissance;
        private String nationalite;
        private String numeroCin;
        private LocalDate dateDelivranceCin;
        private String lieuDelivranceCin;
        private String numeroCnaps;
        private String affiliationMedicale;
        private String adresse;
        private String ville;

        // Getters & Setters
        public Integer getIdEmploye() { return idEmploye; }
        public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }
        public String getMatricule() { return matricule; }
        public void setMatricule(String matricule) { this.matricule = matricule; }
        public String getCivilite() { return civilite; }
        public void setCivilite(String civilite) { this.civilite = civilite; }
        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }
        public String getPrenom() { return prenom; }
        public void setPrenom(String prenom) { this.prenom = prenom; }
        public String getNomComplet() { return nomComplet; }
        public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
        public LocalDate getDateNaissance() { return dateNaissance; }
        public void setDateNaissance(LocalDate dateNaissance) { this.dateNaissance = dateNaissance; }
        public String getLieuNaissance() { return lieuNaissance; }
        public void setLieuNaissance(String lieuNaissance) { this.lieuNaissance = lieuNaissance; }
        public String getNationalite() { return nationalite; }
        public void setNationalite(String nationalite) { this.nationalite = nationalite; }
        public String getNumeroCin() { return numeroCin; }
        public void setNumeroCin(String numeroCin) { this.numeroCin = numeroCin; }
        public LocalDate getDateDelivranceCin() { return dateDelivranceCin; }
        public void setDateDelivranceCin(LocalDate dateDelivranceCin) { this.dateDelivranceCin = dateDelivranceCin; }
        public String getLieuDelivranceCin() { return lieuDelivranceCin; }
        public void setLieuDelivranceCin(String lieuDelivranceCin) { this.lieuDelivranceCin = lieuDelivranceCin; }
        public String getNumeroCnaps() { return numeroCnaps; }
        public void setNumeroCnaps(String numeroCnaps) { this.numeroCnaps = numeroCnaps; }
        public String getAffiliationMedicale() { return affiliationMedicale; }
        public void setAffiliationMedicale(String affiliationMedicale) { this.affiliationMedicale = affiliationMedicale; }
        public String getAdresse() { return adresse; }
        public void setAdresse(String adresse) { this.adresse = adresse; }
        public String getVille() { return ville; }
        public void setVille(String ville) { this.ville = ville; }
    }

    public static class PosteHistoriqueDTO {
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private String dateDebutTexte;
        private String dateFinTexte;
        private String libelleFonction;
        private String categorieProfessionnelle;
        private String departement;
        private String site;
        private String descriptionMissions;

        // Getters & Setters
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
        public String getDateDebutTexte() { return dateDebutTexte; }
        public void setDateDebutTexte(String dateDebutTexte) { this.dateDebutTexte = dateDebutTexte; }
        public String getDateFinTexte() { return dateFinTexte; }
        public void setDateFinTexte(String dateFinTexte) { this.dateFinTexte = dateFinTexte; }
        public String getLibelleFonction() { return libelleFonction; }
        public void setLibelleFonction(String libelleFonction) { this.libelleFonction = libelleFonction; }
        public String getCategorieProfessionnelle() { return categorieProfessionnelle; }
        public void setCategorieProfessionnelle(String categorieProfessionnelle) { this.categorieProfessionnelle = categorieProfessionnelle; }
        public String getDepartement() { return departement; }
        public void setDepartement(String departement) { this.departement = departement; }
        public String getSite() { return site; }
        public void setSite(String site) { this.site = site; }
        public String getDescriptionMissions() { return descriptionMissions; }
        public void setDescriptionMissions(String descriptionMissions) { this.descriptionMissions = descriptionMissions; }
    }

    public static class SignataireDTO {
        private String nom;
        private String fonction;
        private String ville;
        private LocalDate dateEmission;
        private String dateEmissionTexte;

        // Getters & Setters
        public String getNom() { return nom; }
        public void setNom(String nom) { this.nom = nom; }
        public String getFonction() { return fonction; }
        public void setFonction(String fonction) { this.fonction = fonction; }
        public String getVille() { return ville; }
        public void setVille(String ville) { this.ville = ville; }
        public LocalDate getDateEmission() { return dateEmission; }
        public void setDateEmission(LocalDate dateEmission) { this.dateEmission = dateEmission; }
        public String getDateEmissionTexte() { return dateEmissionTexte; }
        public void setDateEmissionTexte(String dateEmissionTexte) { this.dateEmissionTexte = dateEmissionTexte; }
    }

    public static class AttestationTravailDTO {
        private String reference;
        private EntrepriseInfoDTO entreprise;
        private EmployeDocumentDTO employe;
        private SignataireDTO signataire;
        private LocalDate dateEntree;
        private String dateEntreeTexte;
        private String typeContrat; // ex: contrat de travail à durée indéterminée
        private String libelleFonction;
        private String categorieProfessionnelle;
        private String departement;
        private String site;
        private String superieurHierarchique;
        private String tempsTravail; // plein / partiel
        private Integer heuresHebdomadaires; // 40
        private BigDecimal salaireBase;
        private String salaireBaseTexte;
        private boolean inclureSalaire;
        private String typeAnciennete; // 'contrat' | 'globale'
        private String ancienneteTexte;

        // Getters & Setters
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public EntrepriseInfoDTO getEntreprise() { return entreprise; }
        public void setEntreprise(EntrepriseInfoDTO entreprise) { this.entreprise = entreprise; }
        public EmployeDocumentDTO getEmploye() { return employe; }
        public void setEmploye(EmployeDocumentDTO employe) { this.employe = employe; }
        public SignataireDTO getSignataire() { return signataire; }
        public void setSignataire(SignataireDTO signataire) { this.signataire = signataire; }
        public LocalDate getDateEntree() { return dateEntree; }
        public void setDateEntree(LocalDate dateEntree) { this.dateEntree = dateEntree; }
        public String getDateEntreeTexte() { return dateEntreeTexte; }
        public void setDateEntreeTexte(String dateEntreeTexte) { this.dateEntreeTexte = dateEntreeTexte; }
        public String getTypeContrat() { return typeContrat; }
        public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }
        public String getLibelleFonction() { return libelleFonction; }
        public void setLibelleFonction(String libelleFonction) { this.libelleFonction = libelleFonction; }
        public String getCategorieProfessionnelle() { return categorieProfessionnelle; }
        public void setCategorieProfessionnelle(String categorieProfessionnelle) { this.categorieProfessionnelle = categorieProfessionnelle; }
        public String getDepartement() { return departement; }
        public void setDepartement(String departement) { this.departement = departement; }
        public String getSite() { return site; }
        public void setSite(String site) { this.site = site; }
        public String getSuperieurHierarchique() { return superieurHierarchique; }
        public void setSuperieurHierarchique(String superieurHierarchique) { this.superieurHierarchique = superieurHierarchique; }
        public String getTempsTravail() { return tempsTravail; }
        public void setTempsTravail(String tempsTravail) { this.tempsTravail = tempsTravail; }
        public Integer getHeuresHebdomadaires() { return heuresHebdomadaires; }
        public void setHeuresHebdomadaires(Integer heuresHebdomadaires) { this.heuresHebdomadaires = heuresHebdomadaires; }
        public BigDecimal getSalaireBase() { return salaireBase; }
        public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }
        public String getSalaireBaseTexte() { return salaireBaseTexte; }
        public void setSalaireBaseTexte(String salaireBaseTexte) { this.salaireBaseTexte = salaireBaseTexte; }
        public boolean isInclureSalaire() { return inclureSalaire; }
        public void setInclureSalaire(boolean inclureSalaire) { this.inclureSalaire = inclureSalaire; }
        public String getTypeAnciennete() { return typeAnciennete; }
        public void setTypeAnciennete(String typeAnciennete) { this.typeAnciennete = typeAnciennete; }
        public String getAncienneteTexte() { return ancienneteTexte; }
        public void setAncienneteTexte(String ancienneteTexte) { this.ancienneteTexte = ancienneteTexte; }
    }

    public static class CertificatTravailDTO {
        private String reference;
        private EntrepriseInfoDTO entreprise;
        private EmployeDocumentDTO employe;
        private SignataireDTO signataire;
        private LocalDate dateEntree;
        private String dateEntreeTexte;
        private LocalDate dateSortie;
        private String dateSortieTexte;
        private String dureeTotaleTexte;
        private String typeContrat;
        private String dernierPoste;
        private String derniereCategorie;
        private String dernierDepartement;
        private String dernierSite;
        private String mentionLiberatoire;
        private List<PosteHistoriqueDTO> postesOccupes = new ArrayList<>();

        // Getters & Setters
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public EntrepriseInfoDTO getEntreprise() { return entreprise; }
        public void setEntreprise(EntrepriseInfoDTO entreprise) { this.entreprise = entreprise; }
        public EmployeDocumentDTO getEmploye() { return employe; }
        public void setEmploye(EmployeDocumentDTO employe) { this.employe = employe; }
        public SignataireDTO getSignataire() { return signataire; }
        public void setSignataire(SignataireDTO signataire) { this.signataire = signataire; }
        public LocalDate getDateEntree() { return dateEntree; }
        public void setDateEntree(LocalDate dateEntree) { this.dateEntree = dateEntree; }
        public String getDateEntreeTexte() { return dateEntreeTexte; }
        public void setDateEntreeTexte(String dateEntreeTexte) { this.dateEntreeTexte = dateEntreeTexte; }
        public LocalDate getDateSortie() { return dateSortie; }
        public void setDateSortie(LocalDate dateSortie) { this.dateSortie = dateSortie; }
        public String getDateSortieTexte() { return dateSortieTexte; }
        public void setDateSortieTexte(String dateSortieTexte) { this.dateSortieTexte = dateSortieTexte; }
        public String getDureeTotaleTexte() { return dureeTotaleTexte; }
        public void setDureeTotaleTexte(String dureeTotaleTexte) { this.dureeTotaleTexte = dureeTotaleTexte; }
        public String getTypeContrat() { return typeContrat; }
        public void setTypeContrat(String typeContrat) { this.typeContrat = typeContrat; }
        public String getDernierPoste() { return dernierPoste; }
        public void setDernierPoste(String dernierPoste) { this.dernierPoste = dernierPoste; }
        public String getDerniereCategorie() { return derniereCategorie; }
        public void setDerniereCategorie(String derniereCategorie) { this.derniereCategorie = derniereCategorie; }
        public String getDernierDepartement() { return dernierDepartement; }
        public void setDernierDepartement(String dernierDepartement) { this.dernierDepartement = dernierDepartement; }
        public String getDernierSite() { return dernierSite; }
        public void setDernierSite(String dernierSite) { this.dernierSite = dernierSite; }
        public String getMentionLiberatoire() { return mentionLiberatoire; }
        public void setMentionLiberatoire(String mentionLiberatoire) { this.mentionLiberatoire = mentionLiberatoire; }
        public List<PosteHistoriqueDTO> getPostesOccupes() { return postesOccupes; }
        public void setPostesOccupes(List<PosteHistoriqueDTO> postesOccupes) { this.postesOccupes = postesOccupes; }
    }
}
