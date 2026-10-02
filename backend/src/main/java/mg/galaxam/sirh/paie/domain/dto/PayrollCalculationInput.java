package mg.galaxam.sirh.paie.domain.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.paie.persistence.PaieAvanceAcompte;
import mg.galaxam.sirh.paie.persistence.PaiePretEcheance;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;
import mg.galaxam.sirh.paie.domain.engine.SpecialPremiumCollisionPolicy;
import mg.galaxam.sirh.paie.domain.engine.TemporalRateResolver;

/**
 * Objet d'entrée complet et immuable pour le calcul déterministe d'un bulletin de paie.
 */
public class PayrollCalculationInput {

    private PayrollAttendanceInput attendanceInput;

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
    private String nomSite;
    private String codeSite;
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
    private Integer idConfigLegale;
    private String typeBulletin = "MENSUEL";
    private String codePeriode;
    private Date dateDebutPeriode;
    private Date dateFinPeriode;
    private Date dateCalcul = new Date(System.currentTimeMillis());
    private BigDecimal salaireBaseContractuel = BigDecimal.ZERO;
    private BigDecimal tauxHoraire;

    // 3. Pointage scellé & Décompte temps
    private BigDecimal nbJoursOuvrables;
    private BigDecimal nbJoursPresents = BigDecimal.ZERO;
    private BigDecimal nbJoursAbsence = BigDecimal.ZERO;
    private BigDecimal nbJoursConges = BigDecimal.ZERO;
    private BigDecimal joursSousContrat;
    private BigDecimal totalHeuresTravaillees = BigDecimal.ZERO;
    private BigDecimal heuresNormales = BigDecimal.ZERO;
    private BigDecimal heuresHs30 = BigDecimal.ZERO;
    private BigDecimal heuresHs50 = BigDecimal.ZERO;
    private BigDecimal heuresNuit = BigDecimal.ZERO;
    private BigDecimal heuresDimanche = BigDecimal.ZERO;
    private BigDecimal heuresFeriees = BigDecimal.ZERO;
    private int minutesRetard = 0;

    // 4. Primes & Ajustements opérationnels
    private BigDecimal primePerformanceBase = BigDecimal.ZERO;
    private BigDecimal primeAssiduiteForfait = BigDecimal.ZERO;
    private BigDecimal primeExceptionnelle = BigDecimal.ZERO;
    private String motifPrimeExceptionnelle;
    private BigDecimal regularisationPaie = BigDecimal.ZERO; // Régularisation congés/paie mois antérieur
    private String motifRegularisationPaie;
    private BigDecimal autimp = BigDecimal.ZERO; // Autres primes/gains imposables
    private BigDecimal extraDeplacement = BigDecimal.ZERO; // Indemnité extra déplacement (non imposable)

    // 5. Indemnités non imposables (Repas, Transport, Déplacement)
    private BigDecimal indemniteTransport; // Forfait site proratisé
    private BigDecimal forfaitTransportSite = BigDecimal.ZERO;
    private BigDecimal indemniteRepas;     // Repas par jour présent
    private BigDecimal forfaitRepasSite = BigDecimal.ZERO;
    private BigDecimal remboursementNotesFrais = BigDecimal.ZERO;

    // 6. Fiscalité & Déductions familiales
    private int nbEnfantsEligibles = 0;
    private BigDecimal smeNational;
    private BigDecimal plafondCotisationMensuel;
    private BigDecimal plafondAvanceEmployeur = new BigDecimal("0.10");
    private BigDecimal quotiteCessible = new BigDecimal("0.33333333");
    private BigDecimal minimumPerceptionIrsa;
    private BigDecimal deductionParEnfant;
    /** @deprecated La tranche IRSA à 0 % est portée uniquement par {@code tranchesIrsa}. */
    @Deprecated
    private BigDecimal seuilExonerationIrsa;
    private List<IrsaCalculator.Tranche> tranchesIrsa = new ArrayList<>();
    private BigDecimal tauxIsi;
    // Majorations résolues par l'orchestrateur depuis ref_taux_majoration.
    private BigDecimal majorationHs30;
    private BigDecimal majorationHs50;
    private BigDecimal majorationNuit;
    private BigDecimal majorationDimanche;
    private BigDecimal majorationFerie;

    // 6.1 Exonération fiscale des heures supplémentaires (CGI Art. 01.03.03-9°)
    private boolean exonerationHsIrsaActive;
    private Boolean categorieEligibleExonerationHs;
    private boolean appliquerExonerationHs = false;
    private BigDecimal plafondMensuelHsExonerees;

    public boolean isExonerationHsIrsaActive() { return exonerationHsIrsaActive; }
    public void setExonerationHsIrsaActive(boolean v) { this.exonerationHsIrsaActive = v; }
    public Boolean getCategorieEligibleExonerationHs() { return categorieEligibleExonerationHs; }
    public void setCategorieEligibleExonerationHs(Boolean v) { this.categorieEligibleExonerationHs = v; }

    public boolean isAppliquerExonerationHs() {
        if (!exonerationHsIrsaActive) {
            return false;
        }
        if (plafondMensuelHsExonerees == null || plafondMensuelHsExonerees.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        if (categorieEligibleExonerationHs != null) {
            return categorieEligibleExonerationHs;
        }
        return appliquerExonerationHs;
    }
    public void setAppliquerExonerationHs(boolean v) { this.appliquerExonerationHs = v; }
    public BigDecimal getPlafondMensuelHsExonerees() { return plafondMensuelHsExonerees; }
    public void setPlafondMensuelHsExonerees(BigDecimal v) { this.plafondMensuelHsExonerees = v; }

    public BigDecimal getMajorationHs30() { return majorationHs30; }
    public void setMajorationHs30(BigDecimal v) { this.majorationHs30 = v; }
    public BigDecimal getMajorationHs50() { return majorationHs50; }
    public void setMajorationHs50(BigDecimal v) { this.majorationHs50 = v; }
    public BigDecimal getMajorationNuit() { return majorationNuit; }
    public void setMajorationNuit(BigDecimal v) { this.majorationNuit = v; }
    public BigDecimal getMajorationDimanche() { return majorationDimanche; }
    public void setMajorationDimanche(BigDecimal v) { this.majorationDimanche = v; }
    public BigDecimal getMajorationFerie() { return majorationFerie; }
    public void setMajorationFerie(BigDecimal v) { this.majorationFerie = v; }

    // 7. Cotisations sociales (Taux légaux et régionaux)
    private String codeSanteOrgane = "OSTIE";
    private BigDecimal tauxCnapsSalarial;
    private BigDecimal tauxCnapsPatronal;
    private BigDecimal tauxSanteSalarial;
    private BigDecimal tauxSantePatronal;
    private BigDecimal tauxFmfpPatronal;

    /** Représentation financière exacte utilisée par les calculateurs modernes. */
    public BigDecimal getTauxCnapsSalarialDecimal() { return tauxCnapsSalarial; }
    public BigDecimal getTauxCnapsPatronalDecimal() { return tauxCnapsPatronal; }
    public BigDecimal getTauxSanteSalarialDecimal() { return tauxSanteSalarial; }
    public BigDecimal getTauxSantePatronalDecimal() { return tauxSantePatronal; }
    public BigDecimal getTauxFmfpPatronalDecimal() { return tauxFmfpPatronal; }

    // 7.1 Régime fiscal et social (piloté par ref_type_intervenant)
    private boolean soumisCnaps = true;
    private boolean soumisSante = true;
    private boolean soumisIrsa = true;
    private boolean soumisIsi5 = false;

    // 8. Trésorerie & Prêts
    private List<PaieAvanceAcompte> avancesAcomptes = new ArrayList<>();
    private List<Integer> idsMouvementsGains = new ArrayList<>();
    private List<GainVariable> gainsVariablesDynamiques = new ArrayList<>();
    private PaiePretEcheance echeancePretActive;

    public static class GainVariable {
        private Integer idAvance;
        private String code;
        private String codeRubrique;
        private String etapeCalcul;
        private String libelle;
        private BigDecimal montant;
        private String categorie = "PRIME";
        private boolean imposable = true;
        private boolean soumisCotisation = true;
        private Boolean soumisSante;
        private Boolean soumisFmfp;
        private String explicationCalcul;
        private BigDecimal baseCalcul, quantite, taux;

        public GainVariable() {}
        public GainVariable(Integer idAvance, String code, String libelle, BigDecimal montant,
                            String categorie, boolean imposable, boolean soumisCotisation) {
            this.idAvance = idAvance;
            this.code = code;
            this.libelle = libelle;
            this.montant = montant;
            this.categorie = categorie;
            this.imposable = imposable;
            this.soumisCotisation = soumisCotisation;
        }

        public Integer getIdAvance() { return idAvance; }
        public void setIdAvance(Integer idAvance) { this.idAvance = idAvance; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getCodeRubrique() { return codeRubrique != null ? codeRubrique : code; }
        public void setCodeRubrique(String codeRubrique) { this.codeRubrique = codeRubrique; }
        public String getEtapeCalcul() { return etapeCalcul != null ? etapeCalcul : (imposable ? "BRUT" : "GAIN_NET"); }
        public void setEtapeCalcul(String etapeCalcul) {
            if (etapeCalcul != null && !"BRUT".equals(etapeCalcul) && !"GAIN_NET".equals(etapeCalcul)) {
                throw new IllegalArgumentException("Étape de gain manuel invalide : " + etapeCalcul);
            }
            this.etapeCalcul = etapeCalcul;
        }
        public boolean isDansBrut() { return "BRUT".equals(getEtapeCalcul()); }
        public String getLibelle() { return libelle; }
        public void setLibelle(String libelle) { this.libelle = libelle; }
        public BigDecimal getMontant() { return montant; }
        public void setMontant(BigDecimal montant) { this.montant = montant; }
        public String getCategorie() { return categorie; }
        public void setCategorie(String categorie) { this.categorie = categorie; }
        public boolean isImposable() { return imposable; }
        public void setImposable(boolean imposable) { this.imposable = imposable; }
        public boolean isSoumisCotisation() { return soumisCotisation; }
        public void setSoumisCotisation(boolean soumisCotisation) { this.soumisCotisation = soumisCotisation; }
        public boolean isSoumisFmfp() { return soumisFmfp != null ? soumisFmfp : soumisCotisation; }
        public void setSoumisFmfp(boolean v) { soumisFmfp=v; }
        public BigDecimal getBaseCalcul() { return baseCalcul != null ? baseCalcul : montant; }
        public void setBaseCalcul(BigDecimal v) { baseCalcul=v; }
        public BigDecimal getQuantite() { return quantite; }
        public void setQuantite(BigDecimal v) { quantite=v; }
        public BigDecimal getTaux() { return taux; }
        public void setTaux(BigDecimal v) { taux=v; }
        public String getExplicationCalcul() { return explicationCalcul; }
        public void setExplicationCalcul(String v) { explicationCalcul=v; }
        public boolean isSoumisSante() { return soumisSante != null ? soumisSante : soumisCotisation; }
        public void setSoumisSante(boolean soumisSante) { this.soumisSante = soumisSante; }
    }

    public List<GainVariable> getGainsVariablesDynamiques() {
        return gainsVariablesDynamiques;
    }
    public void setGainsVariablesDynamiques(List<GainVariable> gainsVariablesDynamiques) {
        this.gainsVariablesDynamiques = gainsVariablesDynamiques;
    }
    public void ajouterGainVariable(GainVariable g) {
        if (this.gainsVariablesDynamiques == null) {
            this.gainsVariablesDynamiques = new ArrayList<>();
        }
        this.gainsVariablesDynamiques.add(g);
    }

    // 9. Options & Flags
    private boolean entreeSortieEnCoursDeMois = false;
    private boolean calculerPrimeAncienneteAuto = false;
    private boolean simulation = true;
    private BigDecimal nbHeuresAbsence = BigDecimal.ZERO;
    private BigDecimal tauxJournalierConge; // TCONGE dynamique (valorisation grand-livre congés)
    private BigDecimal indemniteCongeLots; // Somme des jours pris × taux historique de chaque lot FIFO
    private String modeCalculAbsence = "HEURES_REELLES"; // "HEURES_REELLES" (défaut opposable) ou "TRENTIEME"
    private boolean estPlafonneSante = true; // Plafonnement OSTIE / OMSI à 8x SME
    private boolean indemniteRepasImposable = false;

    // 10. Paramètres configurables (seuils et durées)
    private BigDecimal diviseurMensuelHeures = PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES;
    private BigDecimal heuresJournalieresLegales = new BigDecimal("8.00");
    private int seuilRetardAssiduiteMinutes = 45;
    private boolean reglePrimeAssiduiteActive = true;
    private boolean regleMalusPerformanceActive = true;
    private int seuilRetardMalusPerformanceMinutes = 70;
    private BigDecimal seuilCongesMalusPerformanceJours = BigDecimal.ONE;
    private BigDecimal tauxMalusPerformance = new BigDecimal("0.25");

    // Constructeur par défaut
    public PayrollCalculationInput() {}

    public PayrollAttendanceInput getAttendanceInput() { return attendanceInput; }

    /** Point d'entrée unique du Pointage : les anciens champs ne sont plus alimentés directement par SQL. */
    public void setAttendanceInput(PayrollAttendanceInput value) {
        if (value == null) throw new IllegalArgumentException("Le snapshot Pointage est obligatoire.");
        if (idEmploye != null && value.employeeId() != idEmploye) {
            throw new IllegalArgumentException("Le snapshot Pointage appartient à un autre employé.");
        }
        if (idPeriodePaie != null && value.payrollPeriodId() != idPeriodePaie) {
            throw new IllegalArgumentException("Le snapshot Pointage appartient à une autre période.");
        }
        this.attendanceInput = value;
        this.nbJoursOuvrables = value.workingDays();
        this.nbJoursPresents = value.presentDays();
        this.nbJoursAbsence = value.absenceDays();
        // Phase 2A-3B : NBCONGE financier est possédé exclusivement par CongeService.calculerNbJoursCongeValorises
        // et injecté via PayrollService.preparerInput. Pointage n'alimente plus nbJoursConges.
        // value.leaveDays() est purement conservé dans attendanceInput comme métrique de snapshot (DISPLAY_ONLY).
        this.totalHeuresTravaillees = PayrollAttendanceInput.minutesToHours(value.workedMinutes());
        this.heuresNormales = this.totalHeuresTravaillees;
        this.heuresHs30 = PayrollAttendanceInput.minutesToHours(value.overtime30Minutes());
        this.heuresHs50 = PayrollAttendanceInput.minutesToHours(value.overtime50Minutes());
        this.heuresNuit = PayrollAttendanceInput.minutesToHours(value.nightMinutes());
        this.heuresDimanche = PayrollAttendanceInput.minutesToHours(value.sundayMinutes());
        this.heuresFeriees = PayrollAttendanceInput.minutesToHours(value.holidayMinutes());
        this.nbHeuresAbsence = PayrollAttendanceInput.minutesToHours(value.unpaidAbsenceMinutes());
        this.minutesRetard = value.lateMinutes();
        this.indemniteRepas = value.mealAmount();
        this.indemniteTransport = value.travelAmount();
    }

    /**
     * Calcule ou retourne le diviseur mensuel d'heures effectif (issu de paie_config_legale ou constante canonique).
     */
    public BigDecimal getDiviseurMensuelHeuresEffectif() {
        if (diviseurMensuelHeures != null && diviseurMensuelHeures.compareTo(BigDecimal.ZERO) > 0) {
            return diviseurMensuelHeures;
        }
        return PayrollExecutionEngine.DIVISEUR_HEURES_MENSUELLES;
    }

    /**
     * Calcule ou retourne le taux horaire de référence à pleine précision (sans setScale prématuré).
     */
    public BigDecimal getTauxHorairePlein() {
        if (tauxHoraire != null && tauxHoraire.compareTo(BigDecimal.ZERO) > 0) {
            return tauxHoraire;
        }
        if (salaireBaseContractuel != null && salaireBaseContractuel.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diviseur = getDiviseurMensuelHeuresEffectif();
            return salaireBaseContractuel.divide(diviseur, java.math.MathContext.DECIMAL128);
        }
        return BigDecimal.ZERO;
    }

    /**
     * Calcule ou retourne le taux horaire de référence (Base / 173.33 ou diviseur configurable).
     */
    public BigDecimal getTauxHoraireEffectif() {
        if (tauxHoraire != null && tauxHoraire.compareTo(BigDecimal.ZERO) > 0) {
            return tauxHoraire;
        }
        if (salaireBaseContractuel != null && salaireBaseContractuel.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diviseur = getDiviseurMensuelHeuresEffectif();
            return salaireBaseContractuel.divide(diviseur, 8, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getSalaireBasePlein() {
        return salaireBaseContractuel != null ? salaireBaseContractuel : BigDecimal.ZERO;
    }

    /**
     * Calcule ou retourne le taux journalier de référence (Base / 30).
     */
    public BigDecimal getTauxJournalierTrentieme() {
        if (salaireBaseContractuel != null && salaireBaseContractuel.compareTo(BigDecimal.ZERO) > 0) {
            return salaireBaseContractuel.divide(new BigDecimal("30.0"), 8, RoundingMode.HALF_UP);
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
    public BigDecimal getIndemniteCongeLots() { return indemniteCongeLots; }
    public void setIndemniteCongeLots(BigDecimal indemniteCongeLots) { this.indemniteCongeLots = indemniteCongeLots; }

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

    public BigDecimal getJoursSousContrat() { return joursSousContrat; }
    public void setJoursSousContrat(BigDecimal joursSousContrat) { this.joursSousContrat = joursSousContrat; }

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

    public BigDecimal getHeuresDimanche() { return heuresDimanche; }
    public void setHeuresDimanche(BigDecimal heuresDimanche) { this.heuresDimanche = heuresDimanche; }

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

    public BigDecimal getRegularisationPaie() { return regularisationPaie; }
    public void setRegularisationPaie(BigDecimal regularisationPaie) { this.regularisationPaie = regularisationPaie; }

    public String getMotifRegularisationPaie() { return motifRegularisationPaie; }
    public void setMotifRegularisationPaie(String motifRegularisationPaie) { this.motifRegularisationPaie = motifRegularisationPaie; }

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
    public BigDecimal getPlafondCotisationMensuel() { return plafondCotisationMensuel; }
    public void setPlafondCotisationMensuel(BigDecimal plafondCotisationMensuel) { this.plafondCotisationMensuel = plafondCotisationMensuel; }
    public BigDecimal getPlafondAvanceEmployeur() { return plafondAvanceEmployeur; }
    public void setPlafondAvanceEmployeur(BigDecimal plafondAvanceEmployeur) { this.plafondAvanceEmployeur = plafondAvanceEmployeur; }
    public BigDecimal getQuotiteCessible() { return quotiteCessible; }
    public void setQuotiteCessible(BigDecimal quotiteCessible) { this.quotiteCessible = quotiteCessible; }

    public BigDecimal getMinimumPerceptionIrsa() { return minimumPerceptionIrsa; }
    public void setMinimumPerceptionIrsa(BigDecimal minimumPerceptionIrsa) { this.minimumPerceptionIrsa = minimumPerceptionIrsa; }

    public BigDecimal getDeductionParEnfant() { return deductionParEnfant; }
    public void setDeductionParEnfant(BigDecimal deductionParEnfant) { this.deductionParEnfant = deductionParEnfant; }

    public BigDecimal getSeuilExonerationIrsa() { return seuilExonerationIrsa; }
    public void setSeuilExonerationIrsa(BigDecimal seuilExonerationIrsa) { this.seuilExonerationIrsa = seuilExonerationIrsa; }

    public List<IrsaCalculator.Tranche> getTranchesIrsa() { return tranchesIrsa; }
    public void setTranchesIrsa(List<IrsaCalculator.Tranche> tranches) {
        this.tranchesIrsa = new ArrayList<>();
        if (tranches == null) return;
        this.tranchesIrsa.addAll(tranches);
    }

    public BigDecimal getTauxIsi() { return tauxIsi; }
    public void setTauxIsi(BigDecimal tauxIsi) { this.tauxIsi = tauxIsi; }

    public void setTauxCnapsSalarial(BigDecimal v) { this.tauxCnapsSalarial = v; }

    public void setTauxCnapsPatronal(BigDecimal v) { this.tauxCnapsPatronal = v; }

    public void setTauxSanteSalarial(BigDecimal v) { this.tauxSanteSalarial = v; }

    public void setTauxSantePatronal(BigDecimal v) { this.tauxSantePatronal = v; }

    public void setTauxFmfpPatronal(BigDecimal v) { this.tauxFmfpPatronal = v; }

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

    public String getCodeSanteOrgane() { return codeSanteOrgane; }
    public void setCodeSanteOrgane(String codeSanteOrgane) { this.codeSanteOrgane = codeSanteOrgane; }

    public BigDecimal getForfaitTransportSite() { return forfaitTransportSite; }
    public void setForfaitTransportSite(BigDecimal forfaitTransportSite) { this.forfaitTransportSite = forfaitTransportSite; }

    public BigDecimal getForfaitRepasSite() { return forfaitRepasSite; }
    public void setForfaitRepasSite(BigDecimal forfaitRepasSite) { this.forfaitRepasSite = forfaitRepasSite; }

    public BigDecimal getHeuresJournalieresLegales() { return heuresJournalieresLegales; }
    public void setHeuresJournalieresLegales(BigDecimal heuresJournalieresLegales) { this.heuresJournalieresLegales = heuresJournalieresLegales; }

    public int getSeuilRetardAssiduiteMinutes() { return seuilRetardAssiduiteMinutes; }
    public boolean isReglePrimeAssiduiteActive() { return reglePrimeAssiduiteActive; }
    public void setReglePrimeAssiduiteActive(boolean active) { this.reglePrimeAssiduiteActive = active; }
    public boolean isRegleMalusPerformanceActive() { return regleMalusPerformanceActive; }
    public void setRegleMalusPerformanceActive(boolean active) { this.regleMalusPerformanceActive = active; }
    public void setSeuilRetardAssiduiteMinutes(int seuilRetardAssiduiteMinutes) { this.seuilRetardAssiduiteMinutes = seuilRetardAssiduiteMinutes; }

    public int getSeuilRetardMalusPerformanceMinutes() { return seuilRetardMalusPerformanceMinutes; }
    public void setSeuilRetardMalusPerformanceMinutes(int seuilRetardMalusPerformanceMinutes) { this.seuilRetardMalusPerformanceMinutes = seuilRetardMalusPerformanceMinutes; }

    public BigDecimal getSeuilCongesMalusPerformanceJours() { return seuilCongesMalusPerformanceJours; }
    public void setSeuilCongesMalusPerformanceJours(BigDecimal seuilCongesMalusPerformanceJours) { this.seuilCongesMalusPerformanceJours = seuilCongesMalusPerformanceJours; }

    public BigDecimal getTauxMalusPerformance() { return tauxMalusPerformance; }
    public void setTauxMalusPerformance(BigDecimal tauxMalusPerformance) { this.tauxMalusPerformance = tauxMalusPerformance; }

    public Integer getIdConfigLegale() { return idConfigLegale; }
    public void setIdConfigLegale(Integer idConfigLegale) { this.idConfigLegale = idConfigLegale; }

    public String getTypeBulletin() { return typeBulletin; }
    public void setTypeBulletin(String typeBulletin) { this.typeBulletin = typeBulletin; }

    public BigDecimal getDiviseurMensuelHeures() { return diviseurMensuelHeures; }
    public void setDiviseurMensuelHeures(BigDecimal diviseurMensuelHeures) { this.diviseurMensuelHeures = diviseurMensuelHeures; }

    private TemporalRateResolver temporalRateResolver;
    private SpecialPremiumCollisionPolicy specialPremiumCollisionPolicy;

    public TemporalRateResolver getTemporalRateResolver() { return temporalRateResolver; }
    public void setTemporalRateResolver(TemporalRateResolver temporalRateResolver) { this.temporalRateResolver = temporalRateResolver; }

    public SpecialPremiumCollisionPolicy getSpecialPremiumCollisionPolicy() { return specialPremiumCollisionPolicy; }
    public void setSpecialPremiumCollisionPolicy(SpecialPremiumCollisionPolicy specialPremiumCollisionPolicy) { this.specialPremiumCollisionPolicy = specialPremiumCollisionPolicy; }
}
