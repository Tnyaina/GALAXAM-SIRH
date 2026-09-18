package mg.galaxam.sirh.model.conge.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Moteur mathématique d'imputation des congés et de purge triennale selon la règle canonique RG-CONGE-06.
 *
 * Règles d'imputation canoniques (Phase 2A-3D) :
 * - PRIORITÉ 1 : Stock historique repris dont l'âge réel est inconnu ([SOLDE_INITIAL]).
 * - PRIORITÉ 2 : Acquisitions datées connues (ACQUISITION, BONIFICATION).
 * - À l'intérieur des acquisitions datées : FIFO chronologique strict (droit connu le plus ancien consommé en premier).
 * - Compensation préalable en cas de solde initial négatif de migration.
 */
@Component
public class PurgeFifoEngine {

    public static class DroitDate {
        private final Integer idMouvement;
        private final LocalDate dateMouvement;
        private final double nbJoursInitial;
        private double nbJoursConsommes = 0.0;
        private double nbJoursRestants;

        public DroitDate(Integer idMouvement, LocalDate dateMouvement, double nbJoursInitial) {
            this.idMouvement = idMouvement;
            this.dateMouvement = dateMouvement;
            this.nbJoursInitial = Math.round(nbJoursInitial * 100.0) / 100.0;
            this.nbJoursRestants = this.nbJoursInitial;
        }

        public Integer getIdMouvement() { return idMouvement; }
        public LocalDate getDateMouvement() { return dateMouvement; }
        public double getNbJoursInitial() { return nbJoursInitial; }
        public double getNbJoursConsommes() { return nbJoursConsommes; }
        public double getNbJoursRestants() { return nbJoursRestants; }

        public void consommer(double quantite) {
            double q = Math.round(quantite * 100.0) / 100.0;
            this.nbJoursConsommes = Math.round((this.nbJoursConsommes + q) * 100.0) / 100.0;
            this.nbJoursRestants = Math.max(0.0, Math.round((this.nbJoursInitial - this.nbJoursConsommes) * 100.0) / 100.0);
        }
    }

    public static class ImputationResult {
        private final double stockHistoriqueInitial;
        private final double stockHistoriqueRestant;
        private final double totalConsommationNette;
        private final double consommationImputeeSurHistorique;
        private final double consommationImputeeSurDroitsDates;
        private final double totalDroitsDatesInitiaux;
        private final double totalDroitsDatesRestants;
        private final double soldeTotalDisponible;
        private final List<DroitDate> detailsDroitsDates;

        public ImputationResult(
                double stockHistoriqueInitial,
                double stockHistoriqueRestant,
                double totalConsommationNette,
                double consommationImputeeSurHistorique,
                double consommationImputeeSurDroitsDates,
                double totalDroitsDatesInitiaux,
                double totalDroitsDatesRestants,
                double soldeTotalDisponible,
                List<DroitDate> detailsDroitsDates) {
            this.stockHistoriqueInitial = stockHistoriqueInitial;
            this.stockHistoriqueRestant = stockHistoriqueRestant;
            this.totalConsommationNette = totalConsommationNette;
            this.consommationImputeeSurHistorique = consommationImputeeSurHistorique;
            this.consommationImputeeSurDroitsDates = consommationImputeeSurDroitsDates;
            this.totalDroitsDatesInitiaux = totalDroitsDatesInitiaux;
            this.totalDroitsDatesRestants = totalDroitsDatesRestants;
            this.soldeTotalDisponible = soldeTotalDisponible;
            this.detailsDroitsDates = detailsDroitsDates;
        }

        public double getStockHistoriqueInitial() { return stockHistoriqueInitial; }
        public double getStockHistoriqueRestant() { return stockHistoriqueRestant; }
        public double getTotalConsommationNette() { return totalConsommationNette; }
        public double getConsommationImputeeSurHistorique() { return consommationImputeeSurHistorique; }
        public double getConsommationImputeeSurDroitsDates() { return consommationImputeeSurDroitsDates; }
        public double getTotalDroitsDatesInitiaux() { return totalDroitsDatesInitiaux; }
        public double getTotalDroitsDatesRestants() { return totalDroitsDatesRestants; }
        public double getSoldeTotalDisponible() { return soldeTotalDisponible; }
        public List<DroitDate> getDetailsDroitsDates() { return detailsDroitsDates; }
    }

    /**
     * Effectue l'imputation canonique d'une consommation de congé selon RG-CONGE-06.
     *
     * @param stockHistoriqueInitial Stock historique à ancienneté inconnue ([SOLDE_INITIAL])
     * @param droitsDates            Acquisitions datées connues ordonnées chronologiquement
     * @param consommationNette      Total des prises nettes (PRISE - ANNULATION + ajustements négatifs)
     * @return Résultat structuré d'imputation sans altération des données d'origine
     */
    public ImputationResult calculerImputation(
            double stockHistoriqueInitial,
            List<DroitDate> droitsDates,
            double consommationNette) {

        double cNette = Math.max(0.0, Math.round(consommationNette * 100.0) / 100.0);
        double histRestant;
        double consommeSurHist;
        double aConsommerSurDates;

        List<DroitDate> copies = new ArrayList<>();
        if (droitsDates != null) {
            for (DroitDate dd : droitsDates) {
                copies.add(new DroitDate(dd.getIdMouvement(), dd.getDateMouvement(), dd.getNbJoursInitial()));
            }
        }

        if (stockHistoriqueInitial >= 0) {
            // RG-CONGE-06 : PRIORITÉ 1 -> Consommation sur le stock historique d'âge inconnu
            consommeSurHist = Math.min(stockHistoriqueInitial, cNette);
            histRestant = Math.round((stockHistoriqueInitial - consommeSurHist) * 100.0) / 100.0;
            aConsommerSurDates = Math.round((cNette - consommeSurHist) * 100.0) / 100.0;
        } else {
            // Cas de solde initial négatif (dette de cutover à compenser d'abord par les acquisitions)
            consommeSurHist = 0.0;
            histRestant = stockHistoriqueInitial;
            double detteACompenser = -stockHistoriqueInitial;

            for (DroitDate dd : copies) {
                double compense = Math.min(dd.getNbJoursRestants(), detteACompenser);
                dd.consommer(compense);
                detteACompenser = Math.round((detteACompenser - compense) * 100.0) / 100.0;
                if (detteACompenser <= 0.0001) break;
            }
            aConsommerSurDates = cNette;
        }

        // RG-CONGE-06 : PRIORITÉ 2 -> Consommation sur les acquisitions datées selon FIFO chronologique
        double consommeSurDates = 0.0;
        for (DroitDate dd : copies) {
            if (aConsommerSurDates <= 0.0001) break;
            double dispo = dd.getNbJoursRestants();
            if (dispo > 0.0001) {
                double prise = Math.min(dispo, aConsommerSurDates);
                dd.consommer(prise);
                consommeSurDates = Math.round((consommeSurDates + prise) * 100.0) / 100.0;
                aConsommerSurDates = Math.round((aConsommerSurDates - prise) * 100.0) / 100.0;
            }
        }

        double totalDroitsDatesInitiaux = 0.0;
        double totalDroitsDatesRestants = 0.0;
        for (DroitDate dd : copies) {
            totalDroitsDatesInitiaux += dd.getNbJoursInitial();
            totalDroitsDatesRestants += dd.getNbJoursRestants();
        }
        totalDroitsDatesInitiaux = Math.round(totalDroitsDatesInitiaux * 100.0) / 100.0;
        totalDroitsDatesRestants = Math.round(totalDroitsDatesRestants * 100.0) / 100.0;

        double soldeTotal;
        if (stockHistoriqueInitial >= 0) {
            soldeTotal = Math.round((histRestant + totalDroitsDatesRestants) * 100.0) / 100.0;
        } else {
            soldeTotal = Math.round((stockHistoriqueInitial + totalDroitsDatesInitiaux - cNette) * 100.0) / 100.0;
        }

        return new ImputationResult(
                stockHistoriqueInitial,
                histRestant,
                cNette,
                consommeSurHist,
                consommeSurDates,
                totalDroitsDatesInitiaux,
                totalDroitsDatesRestants,
                soldeTotal,
                copies
        );
    }

    /**
     * Calcule la purge FIFO exacte pour les droits datés prescrits encore disponibles.
     * Ne purge JAMAIS le stock historique et ne purge JAMAIS une fraction datée déjà consommée.
     */
    public double calculerJoursAExpierFifo(
            ImputationResult imputation,
            LocalDate seuilPrescription,
            double totalDejaExpire) {
        if (imputation == null || imputation.getDetailsDroitsDates() == null || seuilPrescription == null) {
            return 0.0;
        }

        double totalPrescritDisponible = 0.0;
        for (DroitDate dd : imputation.getDetailsDroitsDates()) {
            if (dd.getDateMouvement() != null && dd.getDateMouvement().isBefore(seuilPrescription)) {
                totalPrescritDisponible += dd.getNbJoursRestants();
            }
        }
        totalPrescritDisponible = Math.round(totalPrescritDisponible * 100.0) / 100.0;

        double aExpirer = Math.max(0.0, totalPrescritDisponible - Math.max(0.0, totalDejaExpire));
        return Math.round(aExpirer * 100.0) / 100.0;
    }

    /**
     * Méthode mathématique historique conservée pour rétro-compatibilité unitaire.
     */
    public double calculerJoursAExpier(double totalAcquisAnciens, double totalPris, double totalDejaExpire) {
        if (totalAcquisAnciens <= 0.0001) {
            return 0.0;
        }
        double consommeSurAnciens = Math.min(totalAcquisAnciens, Math.max(0.0, totalPris));
        double anciensNonConsommes = Math.max(0.0, totalAcquisAnciens - consommeSurAnciens);
        double joursAExpirer = Math.max(0.0, anciensNonConsommes - Math.max(0.0, totalDejaExpire));
        return Math.round(joursAExpirer * 100.0) / 100.0;
    }
}
