package mg.galaxam.sirh.model.conge.engine;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO canonique représentant la situation complète d'un salarié pour le module Congés (Correction 1 & Section 21).
 * Découpe formellement :
 * A. Solde consommable (jours disponibles)
 * B. Taux applicable actif (TCONGE as-of)
 * C. Période de référence et accumulateurs du cycle
 */
public class CongeSituationEmployeDTO {

    private int idEmploye;
    private String matricule;
    private String nom;
    private String prenom;

    // A. Stock Consommable
    private BigDecimal soldeJours;
    private BigDecimal totalAcquis;
    private BigDecimal totalPris;
    private LocalDate derniereAcquisition;
    private BigDecimal salaireBase;

    // B. TCONGE Actif Applicable
    private TauxApplicableDTO tauxApplicable;

    // C. Période de Référence & Accumulateurs
    private PeriodeReferenceDTO periodeReference;

    public static class TauxApplicableDTO {
        private BigDecimal montant;
        private LocalDate dateEffet;
        private LocalDate dateFin;
        private String source;

        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }
        public LocalDate getDateEffet() { return dateEffet; }
        public void setDateEffet(LocalDate dateEffet) { this.dateEffet = dateEffet; }
        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
    }

    public static class PeriodeReferenceDTO {
        private Integer idPeriodeReference;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private String statut;
        private String modeReprise;
        private LocalDate dateCutover;
        private boolean openingComplete;
        private BigDecimal cumulDroitsOuverture;
        private BigDecimal cumulValorisationOuverture;
        private BigDecimal cumulDroitsPostCutover;
        private BigDecimal cumulValorisationPostCutover;
        private BigDecimal cumulDroitsTotal;
        private BigDecimal cumulValorisationTotal;
        private BigDecimal tauxProjection;
        private boolean clotureAutomatiquePossible;

        public Integer getIdPeriodeReference() { return idPeriodeReference; }
        public void setIdPeriodeReference(Integer idPeriodeReference) { this.idPeriodeReference = idPeriodeReference; }
        public LocalDate getDateDebut() { return dateDebut; }
        public void setDateDebut(LocalDate dateDebut) { this.dateDebut = dateDebut; }
        public LocalDate getDateFin() { return dateFin; }
        public void setDateFin(LocalDate dateFin) { this.dateFin = dateFin; }
        public String getStatut() { return statut; }
        public void setStatut(String statut) { this.statut = statut; }
        public String getModeReprise() { return modeReprise; }
        public void setModeReprise(String modeReprise) { this.modeReprise = modeReprise; }
        public LocalDate getDateCutover() { return dateCutover; }
        public void setDateCutover(LocalDate dateCutover) { this.dateCutover = dateCutover; }
        public boolean isOpeningComplete() { return openingComplete; }
        public void setOpeningComplete(boolean openingComplete) { this.openingComplete = openingComplete; }
        public BigDecimal getCumulDroitsOuverture() { return cumulDroitsOuverture; }
        public void setCumulDroitsOuverture(BigDecimal cumulDroitsOuverture) { this.cumulDroitsOuverture = cumulDroitsOuverture; }
        public BigDecimal getCumulValorisationOuverture() { return cumulValorisationOuverture; }
        public void setCumulValorisationOuverture(BigDecimal cumulValorisationOuverture) { this.cumulValorisationOuverture = cumulValorisationOuverture; }
        public BigDecimal getCumulDroitsPostCutover() { return cumulDroitsPostCutover; }
        public void setCumulDroitsPostCutover(BigDecimal cumulDroitsPostCutover) { this.cumulDroitsPostCutover = cumulDroitsPostCutover; }
        public BigDecimal getCumulValorisationPostCutover() { return cumulValorisationPostCutover; }
        public void setCumulValorisationPostCutover(BigDecimal cumulValorisationPostCutover) { this.cumulValorisationPostCutover = cumulValorisationPostCutover; }
        public BigDecimal getCumulDroitsTotal() { return cumulDroitsTotal; }
        public void setCumulDroitsTotal(BigDecimal cumulDroitsTotal) { this.cumulDroitsTotal = cumulDroitsTotal; }
        public BigDecimal getCumulValorisationTotal() { return cumulValorisationTotal; }
        public void setCumulValorisationTotal(BigDecimal cumulValorisationTotal) { this.cumulValorisationTotal = cumulValorisationTotal; }
        public BigDecimal getTauxProjection() { return tauxProjection; }
        public void setTauxProjection(BigDecimal tauxProjection) { this.tauxProjection = tauxProjection; }
        public boolean isClotureAutomatiquePossible() { return clotureAutomatiquePossible; }
        public void setClotureAutomatiquePossible(boolean clotureAutomatiquePossible) { this.clotureAutomatiquePossible = clotureAutomatiquePossible; }
    }

    public int getIdEmploye() { return idEmploye; }
    public void setIdEmploye(int idEmploye) { this.idEmploye = idEmploye; }
    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getPrenom() { return prenom; }
    public void setPrenom(String prenom) { this.prenom = prenom; }
    public BigDecimal getSoldeJours() { return soldeJours; }
    public void setSoldeJours(BigDecimal soldeJours) { this.soldeJours = soldeJours; }
    public BigDecimal getTotalAcquis() { return totalAcquis; }
    public void setTotalAcquis(BigDecimal totalAcquis) { this.totalAcquis = totalAcquis; }
    public BigDecimal getTotalPris() { return totalPris; }
    public void setTotalPris(BigDecimal totalPris) { this.totalPris = totalPris; }
    public LocalDate getDerniereAcquisition() { return derniereAcquisition; }
    public void setDerniereAcquisition(LocalDate derniereAcquisition) { this.derniereAcquisition = derniereAcquisition; }
    public BigDecimal getSalaireBase() { return salaireBase; }
    public void setSalaireBase(BigDecimal salaireBase) { this.salaireBase = salaireBase; }
    public TauxApplicableDTO getTauxApplicable() { return tauxApplicable; }
    public void setTauxApplicable(TauxApplicableDTO tauxApplicable) { this.tauxApplicable = tauxApplicable; }
    public PeriodeReferenceDTO getPeriodeReference() { return periodeReference; }
    public void setPeriodeReference(PeriodeReferenceDTO periodeReference) { this.periodeReference = periodeReference; }
}
