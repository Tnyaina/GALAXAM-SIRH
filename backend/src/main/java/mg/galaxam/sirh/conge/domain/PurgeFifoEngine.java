package mg.galaxam.sirh.conge.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Moteur mathématique d'imputation FIFO des droits de congé et de calcul des reliquats disponibles.
 *
 * Règles d'imputation canoniques (Phase 2A-3D) :
 * - PRIORITÉ 1 : Stock historique repris dont l'âge réel est inconnu ([SOLDE_INITIAL]).
 * - PRIORITÉ 2 : Acquisitions datées connues (ACQUISITION, BONIFICATION).
 * - À l'intérieur des acquisitions datées : FIFO chronologique strict (droit connu le plus ancien consommé en premier).
 * - Compensation préalable en cas de solde initial négatif de migration.
 */
@Component
public class PurgeFifoEngine {

    /** Reconnaît aussi les reprises enregistrées avant l'unification du marqueur. */
    public static boolean estStockInitial(String commentaire) {
        return commentaire != null && (commentaire.startsWith("[SOLDE_INITIAL]")
                || commentaire.startsWith("Reprise cutover - Solde initial au "));
    }

    public record MouvementLedger(int id, String type, double jours, LocalDate date,
            Integer idDemande, String commentaire) {}
    public record DroitDisponible(int idMouvement, LocalDate dateAcquisition, double joursRestants) {}

    /** Rejoue le grand-livre dans l'ordre réel des mouvements pour déterminer les lots encore disponibles. */
    public List<DroitDisponible> calculerDroitsDisponibles(List<MouvementLedger> mouvements,
            LocalDate dateReference, int delaiPrescriptionMois) {
        if (mouvements == null || dateReference == null || delaiPrescriptionMois <= 0) return List.of();
        List<MouvementLedger> ordonnes = mouvements.stream()
                .filter(m -> m.date() == null || !m.date().isAfter(dateReference))
                .sorted(java.util.Comparator.comparing(MouvementLedger::date,
                        java.util.Comparator.nullsFirst(LocalDate::compareTo)).thenComparingInt(MouvementLedger::id))
                .toList();
        List<LotMutable> lots = new ArrayList<>();
        java.util.Map<Integer, List<Allocation>> consommationsDemande = new java.util.HashMap<>();
        double historique = 0;
        double ajustementsPositifs = 0;
        double deficitNonDate = 0;

        for (MouvementLedger mouvement : ordonnes) {
            String type = mouvement.type() == null ? "" : mouvement.type().toUpperCase();
            double jours = mouvement.jours();
            if ("AJUSTEMENT".equals(type) && estStockInitial(mouvement.commentaire())) {
                historique += jours;
            } else if ("ACQUISITION".equals(type) || "BONIFICATION".equals(type)) {
                double disponible = Math.max(0, jours);
                if (historique < 0 && disponible > 0) {
                    double compense = Math.min(-historique, disponible);
                    historique += compense;
                    disponible -= compense;
                }
                double compenseDeficit = Math.min(deficitNonDate, disponible);
                deficitNonDate -= compenseDeficit;
                disponible -= compenseDeficit;
                if (disponible > 0) lots.add(new LotMutable(mouvement.id(), mouvement.date(), disponible));
            } else if ("PRISE".equals(type) || ("AJUSTEMENT".equals(type) && jours < 0)) {
                double demande = Math.abs(jours);
                Consommation consommation = consommer(demande, lots, historique, ajustementsPositifs);
                List<Allocation> allocations = consommation.allocations();
                deficitNonDate += consommation.nonImpute();
                double consommeHistorique = allocations.stream().filter(a -> a.type().equals("HISTORIQUE")).mapToDouble(Allocation::jours).sum();
                double consommeAjustements = allocations.stream().filter(a -> a.type().equals("AJUSTEMENT")).mapToDouble(Allocation::jours).sum();
                historique -= consommeHistorique;
                ajustementsPositifs = Math.max(0, ajustementsPositifs - consommeAjustements);
                if ("PRISE".equals(type) && mouvement.idDemande() != null) {
                    consommationsDemande.computeIfAbsent(mouvement.idDemande(), ignored -> new ArrayList<>()).addAll(allocations);
                }
            } else if ("AJUSTEMENT".equals(type) && jours > 0) {
                double compenseDeficit = Math.min(deficitNonDate, jours);
                deficitNonDate -= compenseDeficit;
                ajustementsPositifs += jours - compenseDeficit;
            } else if ("ANNULATION".equals(type)) {
                List<Allocation> allocations = mouvement.idDemande() == null ? null : consommationsDemande.remove(mouvement.idDemande());
                if (allocations == null || allocations.isEmpty()) {
                    double credit = Math.max(0, jours);
                    double compenseDeficit = Math.min(deficitNonDate, credit);
                    deficitNonDate -= compenseDeficit;
                    ajustementsPositifs += credit - compenseDeficit;
                } else {
                    double aRestaurer = Math.max(0, jours);
                    for (Allocation allocation : allocations) {
                        if (aRestaurer <= 0.001) break;
                        double credit = Math.min(allocation.jours(), aRestaurer);
                        if (allocation.type().equals("HISTORIQUE")) historique += credit;
                        else if (allocation.type().equals("AJUSTEMENT")) ajustementsPositifs += credit;
                        else {
                            for (LotMutable lot : lots) {
                                if (lot.id == allocation.idLot()) { lot.reste += credit; break; }
                            }
                        }
                        aRestaurer -= credit;
                    }
                    double compenseDeficit = Math.min(deficitNonDate, aRestaurer);
                    deficitNonDate -= compenseDeficit;
                    ajustementsPositifs += aRestaurer - compenseDeficit;
                }
            } else if ("EXPIRATION".equals(type)) {
                LocalDate seuil = seuilExpiration(mouvement, delaiPrescriptionMois);
                expirer(Math.abs(jours), seuil, lots);
            }
        }

        return lots.stream().filter(l -> l.reste > 0.001 && l.date != null)
                .map(l -> new DroitDisponible(l.id, l.date, arrondir(l.reste)))
                .toList();
    }

    private Consommation consommer(double jours, List<LotMutable> lots, double historique, double ajustements) {
        List<Allocation> allocations = new ArrayList<>();
        double restant = jours;
        double surHistorique = Math.min(Math.max(0, historique), restant);
        if (surHistorique > 0) { allocations.add(new Allocation("HISTORIQUE", null, surHistorique)); restant -= surHistorique; }
        double surAjustements = Math.min(Math.max(0, ajustements), restant);
        if (surAjustements > 0) { allocations.add(new Allocation("AJUSTEMENT", null, surAjustements)); restant -= surAjustements; }
        for (LotMutable lot : lots) {
            if (restant <= 0.001) break;
            double prise = Math.min(lot.reste, restant);
            if (prise > 0) {
                lot.reste = Math.max(0, lot.reste - prise);
                allocations.add(new Allocation("LOT", lot.id, prise));
                restant -= prise;
            }
        }
        return new Consommation(allocations, Math.max(0, restant));
    }

    private void expirer(double jours, LocalDate seuil, List<LotMutable> lots) {
        if (seuil == null) return;
        double restant = jours;
        for (LotMutable lot : lots) {
            if (restant <= 0.001) break;
            if (lot.date == null || !lot.date.isBefore(seuil)) continue;
            double expire = Math.min(lot.reste, restant);
            lot.reste = Math.max(0, lot.reste - expire);
            restant -= expire;
        }
    }

    private LocalDate seuilExpiration(MouvementLedger mouvement, int delaiMois) {
        String commentaire = mouvement.commentaire();
        if (commentaire != null) {
            java.util.regex.Matcher dateMatcher = java.util.regex.Pattern.compile("avant le (\\d{4}-\\d{2}-\\d{2})").matcher(commentaire);
            java.util.regex.Matcher moisMatcher = java.util.regex.Pattern.compile("\\+(\\d+) mois").matcher(commentaire);
            try {
                if (dateMatcher.find()) return LocalDate.parse(dateMatcher.group(1));
                if (moisMatcher.find()) return mouvement.date().minusMonths(Integer.parseInt(moisMatcher.group(1)));
            } catch (RuntimeException ignored) { /* seuil courant de repli */ }
        }
        return mouvement.date() == null ? null : mouvement.date().minusMonths(delaiMois);
    }

    private double arrondir(double valeur) { return Math.round(valeur * 100.0) / 100.0; }
    private record Allocation(String type, Integer idLot, double jours) {}
    private record Consommation(List<Allocation> allocations, double nonImpute) {}
    private static final class LotMutable {
        final int id;
        final LocalDate date;
        double reste;
        LotMutable(int id, LocalDate date, double reste) { this.id = id; this.date = date; this.reste = reste; }
    }


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
     * Calcule le reliquat FIFO des droits datés à une échéance donnée.
     * N'inclut pas le stock historique d'ancienneté inconnue ni les fractions datées déjà consommées.
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
