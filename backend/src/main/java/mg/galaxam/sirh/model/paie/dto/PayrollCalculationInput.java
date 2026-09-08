package mg.galaxam.sirh.model.paie.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.TrancheIrsa;

/**
 * Objet d'entrée complet et immuable pour le calcul déterministe d'un bulletin de paie.
 */
public class PayrollCalculationInput {

    // 1. Employé & Carrière
    private Integer idEmploye;
    private String matricule;
    private String nomPrenom;
    private String cinNumero;
    private Date cinDateDelivrance;
    private String cinLieuDelivrance;
    private String numeroCnaps;
    private String intitulePoste;
    private String nomDepartement;
    private String nomSite = "Antananarivo - Siège";
    private String codeSite = "TNR";
    private String nomFiliale = "GALAXAM LOGISTICS";
    private String regimeIntervenant = "PERMANENT"; // PERMANENT, CONSULTANT, TEMPORAIRE, STAGIAIRE
    private String categorieProfessionnelle = "AGENT"; // CADRE, AGENT, NON_CADRE
    private String modeCalculPaie = "MENSUEL"; // MENSUEL, HORAIRE, JOURNALIER
    private Date dateEmbauche;
    private Date dateDepart;
    private int ancienneteMois;
    private String modePaiement = "VIREMENT";
    private String banqueNom;
    private String banqueRib;
    private String compteNumero;

    // 2. Période & Rémunération de base
    private Integer idPeriodePaie;
    private String codePeriode;
    private Date dateDebutPeriode;
    private Date dateFinPeriode;
    private Date dateCalcul = new Date(System.currentTimeMillis());
    private BigDecimal salaireBaseContractuel = BigDecimal.ZERO;
    private BigDecimal tauxHoraire;

    // 3. Pointage scellé & Décompte temps
    private BigDecimal nbJoursOuvrables = new BigDecimal("22.0");
    private BigDecimal nbJoursPresents = BigDecimal.ZERO;
    private BigDecimal nbJoursAbsence = BigDecimal.ZERO;
    private BigDecimal nbJoursConges = BigDecimal.ZERO;
    private BigDecimal totalHeuresTravaillees = BigDecimal.ZERO;
    private BigDecimal heuresNormales = BigDecimal.ZERO;
    private BigDecimal heuresHs30 = BigDecimal.ZERO;
    private BigDecimal heuresHs50 = BigDecimal.ZERO;
    private BigDecimal heuresNuit = BigDecimal.ZERO;
    private BigDecimal heuresFeriees = BigDecimal.ZERO;
    private int minutesRetard = 0;

    // 4. Primes & Ajustements opérationnels
    private BigDecimal primePerformanceBase = BigDecimal.ZERO;
    private BigDecimal primeAssiduiteForfait = BigDecimal.ZERO;
    private BigDecimal primeExceptionnelle = BigDecimal.ZERO;
    private String motifPrimeExceptionnelle;
    private BigDecimal autimp = BigDecimal.ZERO; // Autres primes/gains imposables
    private BigDecimal extraDeplacement = BigDecimal.ZERO; // Indemnité extra déplacement (non imposable)

    // 5. Indemnités non imposables (Repas, Transport, Déplacement)
    private BigDecimal indemniteTransport; // Forfait site proratisé (TNR 31 200, TMM 53 200)
    private BigDecimal indemniteRepas;     // 2 000 Ar par jour présent complet TNR
    private BigDecimal remboursementNotesFrais = BigDecimal.ZERO;

    // 6. Fiscalité & Déductions familiales
    private int nbEnfantsEligibles = 0;
    private BigDecimal smeNational = new BigDecimal("262680.00");
    private BigDecimal minimumPerceptionIrsa = new BigDecimal("3000.00");
    private BigDecimal deductionParEnfant = new BigDecimal("2000.00");
    private List<TrancheIrsa> tranchesIrsa = new ArrayList<>();
    private BigDecimal tauxIsi = new BigDecimal("5.00");

    // 7. Cotisations sociales (Taux légaux et régionaux)
    private double tauxCnapsSalarial = 0.01;
    private double tauxCnapsPatronal = 0.13;
    private double tauxSanteSalarial = 0.01; // OSTIE 1% (TNR) ou OMSI 1.5% (TMM)
    private double tauxSantePatronal = 0.05; // OSTIE 5% (TNR) ou OMSI 5.5% (TMM)
    private double tauxFmfpPatronal = 0.01;  // FMFP 1% patronal

    // 7.1 Régime fiscal et social (piloté par ref_type_intervenant)
    private boolean soumisCnaps = true;
    private boolean soumisSante = true;
    private boolean soumisIrsa = true;
    private boolean soumisIsi5 = false;

    // 8. Trésorerie & Prêts
    private List<PaieAvanceAcompte> avancesAcomptes = new ArrayList<>();
    private List<Integer> idsMouvementsGains = new ArrayList<>();
    private PaiePretEcheance echeancePretActive;

    // 9. Options & Flags
    private boolean entreeSortieEnCoursDeMois = false;
    private boolean calculerPrimeAncienneteAuto = false;
    private boolean simulation = true;
    private BigDecimal nbHeuresAbsence = BigDecimal.ZERO;
    private BigDecimal tauxJournalierConge; // TCONGE dynamique (valorisation grand-livre congés)
    private String modeCalculAbsence = "HEURES_REELLES"; // "HEURES_REELLES" (défaut opposable) ou "TRENTIEME"
    private boolean estPlafonneSante = true; // Plafonnement OSTIE / OMSI à 8x SME
    private boolean indemniteRepasImposable = false;

    // Constructeur par défaut
    public PayrollCalculationInput() {}

    /**
     * Calcule ou retourne le taux horaire de référence (Base / 173.3333).
     */
    public BigDecimal getTauxHoraireEffectif() {
        if (tauxHoraire != null && tauxHoraire.compareTo(BigDecimal.ZERO) > 0) {
            return tauxHoraire;
        }
        if (salaireBaseContractuel != null && salaireBaseContractuel.compareTo(BigDecimal.ZERO) > 0) {
            return salaireBaseContractuel.divide(new BigDecimal("173.3333"), 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calcule ou retourne le taux journalier de référence (Base / 30).
     */
    public BigDecimal getTauxJournalierTrentieme() {
        if (salaireBaseContractuel != null && salaireBaseContractuel.compareTo(BigDecimal.ZERO) > 0) {
            return salaireBaseContractuel.divide(new BigDecimal("30.0"), 4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Retourne le taux journalier de congé effectif (TCONGE dynamique si renseigné, sinon fallback sur Base / 30).
     */
    public BigDecimal getTauxJournalierCongeEffectif() {
        if (tauxJournalierConge != null && tauxJournalierConge.compareTo(BigDecimal.ZERO) > 0) {
            return tauxJournalierConge;
        }
        return getTauxJournalierTrentieme();
    }

    public BigDecimal getTauxJournalierConge() { return tauxJournalierConge; }
    public void setTauxJournalierConge(BigDecimal tauxJournalierConge) { this.tauxJournalierConge = tauxJournalierConge; }

    // Getters & Setters
    public Integer getIdEmploye() { return idEmploye; }
    public void setIdEmploye(Integer idEmploye) { this.idEmploye = idEmploye; }

    public String getMatricule() { return matricule; }
    public void setMatricule(String matricule) { this.matricule = matricule; }

    public String getNomPrenom() { return nomPrenom; }
    public void setNomPrenom(String nomPrenom) { this.nomPrenom = nomPrenom; }

    public String getCinNumero() { return cinNumero; }
    public void setCinNumero(String cinNumero) { this.cinNumero = cinNumero; }

    public Date getCinDateDelivrance() { return cinDateDelivrance; }
    public void setCinDateDelivrance(Date cinDateDelivrance) { this.cinDateDelivrance = cinDateDelivrance; }

    public String getCinLieuDelivrance() { return cinLieuDelivrance; }
    public void setCinLieuDelivrance(String cinLieuDelivrance) { this.cinLieuDelivrance = cinLieuDelivrance; }

    public String getNumeroCnaps() { return numeroCnaps; }
    public void setNumeroCnaps(String numeroCnaps) { this.numeroCnaps = numeroCnaps; }

    public String getIntitulePoste() { return intitulePoste; }
    public void setIntitulePoste(String intitulePoste) { this.intitulePoste = intitulePoste; }

    public String getNomDepartement() { return nomDepartement; }
    public void setNomDepartement(String nomDepartement) { this.nomDepartement = nomDepartement; }

    public String getNomSite() { return nomSite; }
    public void setNomSite(String nomSite) { this.nomSite = nomSite; }

    public String getCodeSite() { return codeSite; }
    public void setCodeSite(String codeSite) { this.codeSite = codeSite; }

    public String getNomFiliale() { return nomFiliale; }
    public void setNomFiliale(String nomFiliale) { this.nomFiliale = nomFiliale; }

    public String getRegimeIntervenant() { return regimeIntervenant; }
    public void setRegimeIntervenant(String regimeIntervenant) {
        this.regimeIntervenant = regimeIntervenant;
        if (regimeIntervenant != null) {
            switch (regimeIntervenant.trim().toUpperCase()) {
                case "CONSULTANT":
                    this.soumisCnaps = false;
                    this.soumisSante = false;
                    this.soumisIrsa = false;
                    this.soumisIsi5 = true;
                    break;
                case "TEMPORAIRE":
                    this.soumisCnaps = false;
                    this.soumisSante = false;
                    this.soumisIrsa = false;
                    this.soumisIsi5 = true;
                    break;
                case "STAGIAIRE":
                    this.soumisCnaps = false;
                    this.soumisSante = false;
                    this.soumisIrsa = false;
                    this.soumisIsi5 = false;
                    break;
                case "PERMANENT":
                default:
                    this.soumisCnaps = true;
                    this.soumisSante = true;
                    this.soumisIrsa = true;
                    this.soumisIsi5 = false;
                    break;
            }
        }
    }

    public String getCategorieProfessionnelle() { return categorieProfessionnelle; }
    public void setCategorieProfessionnelle(String categorieProfessionnelle) { this.categorieProfessionnelle = categorieProfessionnelle; }

    public String getModeCalculPaie() { return modeCalculPaie; }
    public void setModeCalculPaie(String modeCalculPaie) { this.modeCalculPaie = modeCalculPaie; }

    public Date getDateEmbauche() { return dateEmbauche; }
    public void setDateEmbauche(Date dateEmbauche) { this.dateEmbauche = dateEmbauche; }

    public Date getDateDepart() { return dateDepart; }
    public void setDateDepart(Date dateDepart) { this.dateDepart = dateDepart; }

    public int getAncienneteMois() { return ancienneteMois; }
    public void setAncienneteMois(int ancienneteMois) { this.ancienneteMois = ancienneteMois; }

    public String getModePaiement() { return modePaiement; }
    public void setModePaiement(String modePaiement) { this.modePaiement = modePaiement; }

    public String getBanqueNom() { return banqueNom; }
    public void setBanqueNom(String banqueNom) { this.banqueNom = banqueNom; }

    public String getBanqueRib() { return banqueRib; }
    public void setBanqueRib(String banqueRib) { this.banqueRib = banqueRib; }

    public String getCompteNumero() { return compteNumero; }
    public void setCompteNumero(String compteNumero) { this.compteNumero = compteNumero; }

    public Integer getIdPeriodePaie() { return idPeriodePaie; }
    public void setIdPeriodePaie(Integer idPeriodePaie) { this.idPeriodePaie = idPeriodePaie; }

    public String getCodePeriode() { return codePeriode; }
    public void setCodePeriode(String codePeriode) { this.codePeriode = codePeriode; }

    public Date getDateDebutPeriode() { return dateDebutPeriode; }
    public void setDateDebutPeriode(Date dateDebutPeriode) { this.dateDebutPeriode = dateDebutPeriode; }

    public Date getDateFinPeriode() { return dateFinPeriode; }
    public void setDateFinPeriode(Date dateFinPeriode) { this.dateFinPeriode = dateFinPeriode; }

    public Date getDateCalcul() { return dateCalcul; }
    public void setDateCalcul(Date dateCalcul) { this.dateCalcul = dateCalcul; }

    public BigDecimal getSalaireBaseContractuel() { return salaireBaseContractuel; }
    public void setSalaireBaseContractuel(BigDecimal salaireBaseContractuel) { this.salaireBaseContractuel = salaireBaseContractuel; }

    public BigDecimal getTauxHoraire() { return tauxHoraire; }
    public void setTauxHoraire(BigDecimal tauxHoraire) { this.tauxHoraire = tauxHoraire; }

    public BigDecimal getNbJoursOuvrables() { return nbJoursOuvrables; }
    public void setNbJoursOuvrables(BigDecimal nbJoursOuvrables) { this.nbJoursOuvrables = nbJoursOuvrables; }

    public BigDecimal getNbJoursPresents() { return nbJoursPresents; }
    public void setNbJoursPresents(BigDecimal nbJoursPresents) { this.nbJoursPresents = nbJoursPresents; }

    public BigDecimal getNbJoursAbsence() { return nbJoursAbsence; }
    public void setNbJoursAbsence(BigDecimal nbJoursAbsence) { this.nbJoursAbsence = nbJoursAbsence; }

    public BigDecimal getNbJoursConges() { return nbJoursConges; }
    public void setNbJoursConges(BigDecimal nbJoursConges) { this.nbJoursConges = nbJoursConges; }

    public BigDecimal getTotalHeuresTravaillees() { return totalHeuresTravaillees; }
    public void setTotalHeuresTravaillees(BigDecimal totalHeuresTravaillees) { this.totalHeuresTravaillees = totalHeuresTravaillees; }

    public BigDecimal getHeuresNormales() { return heuresNormales; }
    public void setHeuresNormales(BigDecimal heuresNormales) { this.heuresNormales = heuresNormales; }

    public BigDecimal getHeuresHs30() { return heuresHs30; }
    public void setHeuresHs30(BigDecimal heuresHs30) { this.heuresHs30 = heuresHs30; }

    public BigDecimal getHeuresHs50() { return heuresHs50; }
    public void setHeuresHs50(BigDecimal heuresHs50) { this.heuresHs50 = heuresHs50; }

    public BigDecimal getHeuresNuit() { return heuresNuit; }
    public void setHeuresNuit(BigDecimal heuresNuit) { this.heuresNuit = heuresNuit; }

    public BigDecimal getHeuresFeriees() { return heuresFeriees; }
    public void setHeuresFeriees(BigDecimal heuresFeriees) { this.heuresFeriees = heuresFeriees; }

    public int getMinutesRetard() { return minutesRetard; }
    public void setMinutesRetard(int minutesRetard) { this.minutesRetard = minutesRetard; }

    public BigDecimal getPrimePerformanceBase() { return primePerformanceBase; }
    public void setPrimePerformanceBase(BigDecimal primePerformanceBase) { this.primePerformanceBase = primePerformanceBase; }

    public BigDecimal getPrimeAssiduiteForfait() { return primeAssiduiteForfait; }
    public void setPrimeAssiduiteForfait(BigDecimal primeAssiduiteForfait) { this.primeAssiduiteForfait = primeAssiduiteForfait; }

    public BigDecimal getPrimeExceptionnelle() { return primeExceptionnelle; }
    public void setPrimeExceptionnelle(BigDecimal primeExceptionnelle) { this.primeExceptionnelle = primeExceptionnelle; }

    public String getMotifPrimeExceptionnelle() { return motifPrimeExceptionnelle; }
    public void setMotifPrimeExceptionnelle(String motifPrimeExceptionnelle) { this.motifPrimeExceptionnelle = motifPrimeExceptionnelle; }

    public BigDecimal getAutimp() { return autimp; }
    public void setAutimp(BigDecimal autimp) { this.autimp = autimp; }

    public BigDecimal getExtraDeplacement() { return extraDeplacement; }
    public void setExtraDeplacement(BigDecimal extraDeplacement) { this.extraDeplacement = extraDeplacement; }

    public BigDecimal getIndemniteTransport() { return indemniteTransport; }
    public void setIndemniteTransport(BigDecimal indemniteTransport) { this.indemniteTransport = indemniteTransport; }

    public BigDecimal getIndemniteRepas() { return indemniteRepas; }
    public void setIndemniteRepas(BigDecimal indemniteRepas) { this.indemniteRepas = indemniteRepas; }

    public BigDecimal getRemboursementNotesFrais() { return remboursementNotesFrais; }
    public void setRemboursementNotesFrais(BigDecimal remboursementNotesFrais) { this.remboursementNotesFrais = remboursementNotesFrais; }

    public int getNbEnfantsEligibles() { return nbEnfantsEligibles; }
    public void setNbEnfantsEligibles(int nbEnfantsEligibles) { this.nbEnfantsEligibles = nbEnfantsEligibles; }

    public BigDecimal getSmeNational() { return smeNational; }
    public void setSmeNational(BigDecimal smeNational) { this.smeNational = smeNational; }

    public BigDecimal getMinimumPerceptionIrsa() { return minimumPerceptionIrsa; }
    public void setMinimumPerceptionIrsa(BigDecimal minimumPerceptionIrsa) { this.minimumPerceptionIrsa = minimumPerceptionIrsa; }

    public BigDecimal getDeductionParEnfant() { return deductionParEnfant; }
    public void setDeductionParEnfant(BigDecimal deductionParEnfant) { this.deductionParEnfant = deductionParEnfant; }

    public List<TrancheIrsa> getTranchesIrsa() { return tranchesIrsa; }
    public void setTranchesIrsa(List<TrancheIrsa> tranchesIrsa) { this.tranchesIrsa = tranchesIrsa; }

    public BigDecimal getTauxIsi() { return tauxIsi; }
    public void setTauxIsi(BigDecimal tauxIsi) { this.tauxIsi = tauxIsi; }

    public double getTauxCnapsSalarial() { return tauxCnapsSalarial; }
    public void setTauxCnapsSalarial(double tauxCnapsSalarial) { this.tauxCnapsSalarial = tauxCnapsSalarial; }

    public double getTauxCnapsPatronal() { return tauxCnapsPatronal; }
    public void setTauxCnapsPatronal(double tauxCnapsPatronal) { this.tauxCnapsPatronal = tauxCnapsPatronal; }

    public double getTauxSanteSalarial() { return tauxSanteSalarial; }
    public void setTauxSanteSalarial(double tauxSanteSalarial) { this.tauxSanteSalarial = tauxSanteSalarial; }

    public double getTauxSantePatronal() { return tauxSantePatronal; }
    public void setTauxSantePatronal(double tauxSantePatronal) { this.tauxSantePatronal = tauxSantePatronal; }

    public double getTauxFmfpPatronal() { return tauxFmfpPatronal; }
    public void setTauxFmfpPatronal(double tauxFmfpPatronal) { this.tauxFmfpPatronal = tauxFmfpPatronal; }

    public List<PaieAvanceAcompte> getAvancesAcomptes() { return avancesAcomptes; }
    public void setAvancesAcomptes(List<PaieAvanceAcompte> avancesAcomptes) { this.avancesAcomptes = avancesAcomptes; }

    public List<Integer> getIdsMouvementsGains() { return idsMouvementsGains; }
    public void setIdsMouvementsGains(List<Integer> idsMouvementsGains) { this.idsMouvementsGains = idsMouvementsGains; }

    public PaiePretEcheance getEcheancePretActive() { return echeancePretActive; }
    public void setEcheancePretActive(PaiePretEcheance echeancePretActive) { this.echeancePretActive = echeancePretActive; }

    public boolean isEntreeSortieEnCoursDeMois() { return entreeSortieEnCoursDeMois; }
    public void setEntreeSortieEnCoursDeMois(boolean entreeSortieEnCoursDeMois) { this.entreeSortieEnCoursDeMois = entreeSortieEnCoursDeMois; }

    public boolean isCalculerPrimeAncienneteAuto() { return calculerPrimeAncienneteAuto; }
    public void setCalculerPrimeAncienneteAuto(boolean calculerPrimeAncienneteAuto) { this.calculerPrimeAncienneteAuto = calculerPrimeAncienneteAuto; }

    public boolean isSimulation() { return simulation; }
    public void setSimulation(boolean simulation) { this.simulation = simulation; }

    public BigDecimal getNbHeuresAbsence() { return nbHeuresAbsence; }
    public void setNbHeuresAbsence(BigDecimal nbHeuresAbsence) { this.nbHeuresAbsence = nbHeuresAbsence; }

    public String getModeCalculAbsence() { return modeCalculAbsence; }
    public void setModeCalculAbsence(String modeCalculAbsence) { this.modeCalculAbsence = modeCalculAbsence; }

    public boolean isEstPlafonneSante() { return estPlafonneSante; }
    public void setEstPlafonneSante(boolean estPlafonneSante) { this.estPlafonneSante = estPlafonneSante; }

    public boolean isIndemniteRepasImposable() { return indemniteRepasImposable; }
    public void setIndemniteRepasImposable(boolean indemniteRepasImposable) { this.indemniteRepasImposable = indemniteRepasImposable; }

    public boolean isSoumisCnaps() { return soumisCnaps; }
    public void setSoumisCnaps(boolean soumisCnaps) { this.soumisCnaps = soumisCnaps; }

    public boolean isSoumisSante() { return soumisSante; }
    public void setSoumisSante(boolean soumisSante) { this.soumisSante = soumisSante; }

    public boolean isSoumisIrsa() { return soumisIrsa; }
    public void setSoumisIrsa(boolean soumisIrsa) { this.soumisIrsa = soumisIrsa; }

    public boolean isSoumisIsi5() { return soumisIsi5; }
    public void setSoumisIsi5(boolean soumisIsi5) { this.soumisIsi5 = soumisIsi5; }
}
