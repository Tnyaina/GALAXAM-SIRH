package mg.galaxam.sirh.conge.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rejoue le grand-livre des jours avec la valeur historique de chaque droit. */
public class CongeLotValorisationEngine {
    public record Mouvement(int id, String type, BigDecimal jours, LocalDate date,
            Integer idDemande, String commentaire, BigDecimal valeurSource) {}
    public record Imputation(int idMouvement, Integer idDemande, int idSource, String origine,
            BigDecimal jours, BigDecimal valeur, BigDecimal tauxHistorique) {}
    public record LotRestant(int idSource, String origine, LocalDate dateAcquisition,
            BigDecimal jours, BigDecimal valeur) {}
    public record Situation(BigDecimal soldeJours, BigDecimal soldeValorisation,
            List<LotRestant> lots, List<Imputation> imputations, BigDecimal joursSansLot) {}

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String REPRISE = "REPRISE_CUTOVER";
    private static final String AJUSTEMENT = "AJUSTEMENT";

    public Situation calculer(List<Mouvement> mouvements) {
        List<Mouvement> ordonnes = mouvements.stream()
                .sorted(Comparator.comparing(Mouvement::date).thenComparingInt(Mouvement::id)).toList();
        List<Lot> lots = new ArrayList<>();
        List<Imputation> traces = new ArrayList<>();
        Map<Integer, List<Imputation>> prisesParDemande = new HashMap<>();
        Map<Integer, Anticipation> anticipations = new java.util.LinkedHashMap<>();
        BigDecimal soldeJours = ZERO;
        BigDecimal joursSansLot = ZERO;

        for (Mouvement mouvement : ordonnes) {
            BigDecimal jours = mouvement.jours();
            soldeJours = soldeJours.add(jours);
            String type = mouvement.type();
            if ("CORRECTION_CUTOVER".equals(type)) {
                Lot reprise = lots.stream().filter(l -> l.id == mouvement.idDemande()
                        && REPRISE.equals(l.origine)).findFirst().orElseThrow(() ->
                        new IllegalStateException("CONGE_CUTOVER_SOURCE_MISSING: " + mouvement.id()));
                BigDecimal nouveauReliquat = reprise.joursRestants.add(jours);
                if (nouveauReliquat.signum() < 0 || mouvement.valeurSource() == null
                        || mouvement.valeurSource().signum() < 0) {
                    throw new IllegalStateException("CONGE_CUTOVER_RELIQUAT_INVALID: " + mouvement.id());
                }
                reprise.joursRestants = nouveauReliquat;
                reprise.valeurRestante = mouvement.valeurSource();
                reprise.tauxFutur = nouveauReliquat.signum() == 0 ? ZERO
                        : mouvement.valeurSource().divide(nouveauReliquat, 8, RoundingMode.HALF_UP);
                continue;
            }
            boolean initial = "AJUSTEMENT".equals(type)
                    && PurgeFifoEngine.estStockInitial(mouvement.commentaire());
            if ("AJUSTEMENT".equals(type) && jours.signum() > 0
                    && mouvement.idDemande() != null && mouvement.commentaire() != null
                    && mouvement.commentaire().startsWith("Ajustement de solde suite modification durée")) {
                List<Imputation> prises = prisesParDemande.get(mouvement.idDemande());
                if (prises == null) throw new IllegalStateException("CONGE_AJUSTEMENT_SOURCE_MISSING: " + mouvement.id());
                BigDecimal restant = jours;
                for (int index = prises.size() - 1; index >= 0 && restant.signum() > 0; index--) {
                    Imputation prise = prises.get(index);
                    BigDecimal quantite = restant.min(prise.jours());
                    Lot lot = lots.stream().filter(l -> l.id == prise.idSource()).findFirst().orElseThrow();
                    BigDecimal valeur = quantite.compareTo(prise.jours()) == 0 ? prise.valeur()
                            : quantite.multiply(prise.tauxHistorique()).setScale(2, RoundingMode.HALF_UP);
                    lot.joursRestants = lot.joursRestants.add(quantite);
                    lot.valeurRestante = lot.valeurRestante.add(valeur);
                    traces.add(new Imputation(mouvement.id(), mouvement.idDemande(), lot.id,
                            lot.origine, quantite.negate(), valeur.negate(), prise.tauxHistorique()));
                    if (quantite.compareTo(prise.jours()) == 0) prises.remove(index);
                    else prises.set(index, new Imputation(prise.idMouvement(), prise.idDemande(),
                            prise.idSource(), prise.origine(), prise.jours().subtract(quantite),
                            prise.valeur().subtract(valeur), prise.tauxHistorique()));
                    restant = restant.subtract(quantite);
                }
                if (restant.signum() > 0) throw new IllegalStateException("CONGE_AJUSTEMENT_EXCESSIF: " + mouvement.id());
            } else if (initial || "ACQUISITION".equals(type) || "BONIFICATION".equals(type)
                    || ("AJUSTEMENT".equals(type) && jours.signum() > 0)) {
                if (jours.signum() > 0) {
                    if (mouvement.valeurSource() == null) {
                        throw new IllegalStateException("CONGE_LOT_VALORISATION_MISSING: mouvement " + mouvement.id());
                    }
                    String origine = initial ? REPRISE : type;
                    Lot credit = new Lot(mouvement.id(), origine, mouvement.date(), jours,
                            mouvement.valeurSource().setScale(2, RoundingMode.HALF_UP));
                    lots.add(credit);
                    for (Anticipation anticipation : anticipations.values()) {
                        if (credit.joursRestants.signum() == 0) break;
                        BigDecimal couverts = credit.joursRestants.min(anticipation.restant);
                        if (couverts.signum() == 0) continue;
                        BigDecimal valeurCouverte = couverts.compareTo(credit.joursRestants) == 0
                                ? credit.valeurRestante : couverts.multiply(credit.taux())
                                        .setScale(2, RoundingMode.HALF_UP);
                        credit.joursRestants = credit.joursRestants.subtract(couverts);
                        credit.valeurRestante = credit.valeurRestante.subtract(valeurCouverte);
                        anticipation.restant = anticipation.restant.subtract(couverts);
                        anticipation.couvertures.add(new Imputation(mouvement.id(), null, credit.id,
                                "COUVERTURE_ANTICIPATION", couverts, valeurCouverte, credit.taux()));
                        joursSansLot = joursSansLot.subtract(couverts);
                        traces.add(anticipation.couvertures.get(anticipation.couvertures.size() - 1));
                    }
                }
            } else if ("PRISE".equals(type) || ("AJUSTEMENT".equals(type) && jours.signum() < 0)) {
                BigDecimal restant = jours.abs();
                List<Imputation> prises = new ArrayList<>();
                for (Lot lot : lots.stream().sorted(Comparator.comparingInt(Lot::priorite)
                        .thenComparing(l -> l.date).thenComparingInt(l -> l.id)).toList()) {
                    if (restant.signum() == 0) break;
                    BigDecimal quantite = restant.min(lot.joursRestants);
                    if (quantite.signum() <= 0) continue;
                    BigDecimal valeur = quantite.compareTo(lot.joursRestants) == 0
                            ? lot.valeurRestante
                            : quantite.multiply(lot.taux()).setScale(2, RoundingMode.HALF_UP);
                    lot.joursRestants = lot.joursRestants.subtract(quantite);
                    lot.valeurRestante = lot.valeurRestante.subtract(valeur);
                    Imputation trace = new Imputation(mouvement.id(), mouvement.idDemande(),
                            lot.id, lot.origine, quantite, valeur, lot.taux());
                    prises.add(trace);
                    traces.add(trace);
                    restant = restant.subtract(quantite);
                }
                if (restant.signum() > 0 && "PRISE".equals(type)) {
                    if (mouvement.valeurSource() == null) {
                        throw new IllegalStateException("CONGE_ANTICIPATION_TCONGE_MISSING: mouvement " + mouvement.id());
                    }
                    BigDecimal valeurAnticipee = restant.multiply(mouvement.valeurSource())
                            .setScale(2, RoundingMode.HALF_UP);
                    Imputation anticipation = new Imputation(mouvement.id(), mouvement.idDemande(),
                            -mouvement.id(), "ANTICIPATION", restant, valeurAnticipee,
                            mouvement.valeurSource());
                    prises.add(anticipation);
                    traces.add(anticipation);
                    anticipations.put(-mouvement.id(), new Anticipation(restant, mouvement.valeurSource()));
                }
                joursSansLot = joursSansLot.add(restant);
                if ("PRISE".equals(type) && mouvement.idDemande() != null) {
                    prisesParDemande.computeIfAbsent(mouvement.idDemande(), ignored -> new ArrayList<>()).addAll(prises);
                }
            } else if ("ANNULATION".equals(type)) {
                List<Imputation> prises = prisesParDemande.remove(mouvement.idDemande());
                if (prises == null) {
                    throw new IllegalStateException("CONGE_ANNULATION_SOURCE_MISSING: mouvement " + mouvement.id());
                }
                for (Imputation prise : prises) {
                    if ("ANTICIPATION".equals(prise.origine())) {
                        Anticipation anticipation = anticipations.remove(prise.idSource());
                        if (anticipation == null) throw new IllegalStateException(
                                "CONGE_ANTICIPATION_SOURCE_MISSING: " + mouvement.id());
                        joursSansLot = joursSansLot.subtract(anticipation.restant);
                        for (Imputation couverture : anticipation.couvertures) {
                            Lot lotCouvert = lots.stream().filter(l -> l.id == couverture.idSource()).findFirst().orElseThrow();
                            lotCouvert.joursRestants = lotCouvert.joursRestants.add(couverture.jours());
                            lotCouvert.valeurRestante = lotCouvert.valeurRestante.add(couverture.valeur());
                            traces.add(new Imputation(mouvement.id(), null, lotCouvert.id,
                                    "ANNULATION_COUVERTURE_ANTICIPATION", couverture.jours().negate(),
                                    couverture.valeur().negate(), couverture.tauxHistorique()));
                        }
                        traces.add(new Imputation(mouvement.id(), mouvement.idDemande(),
                                prise.idSource(), prise.origine(), prise.jours().negate(),
                                prise.valeur().negate(), prise.tauxHistorique()));
                        continue;
                    }
                    Lot lot = lots.stream().filter(l -> l.id == prise.idSource()).findFirst().orElseThrow();
                    lot.joursRestants = lot.joursRestants.add(prise.jours());
                    lot.valeurRestante = lot.valeurRestante.add(prise.valeur());
                    traces.add(new Imputation(mouvement.id(), mouvement.idDemande(), lot.id,
                            lot.origine, prise.jours().negate(), prise.valeur().negate(), lot.taux()));
                }
            } else if ("EXPIRATION".equals(type)) {
                BigDecimal restant = jours.abs();
                LocalDate seuil = seuilExpiration(mouvement.commentaire(), mouvement.date());
                if (seuil == null) throw new IllegalStateException("CONGE_EXPIRATION_SEUIL_MISSING: " + mouvement.id());
                for (Lot lot : lots.stream().filter(l -> !REPRISE.equals(l.origine)
                        && l.date.isBefore(seuil)).sorted(Comparator.comparing((Lot l) -> l.date)
                                .thenComparingInt(l -> l.id)).toList()) {
                    BigDecimal quantite = restant.min(lot.joursRestants);
                    if (quantite.signum() <= 0) continue;
                    BigDecimal valeur = quantite.compareTo(lot.joursRestants) == 0
                            ? lot.valeurRestante : quantite.multiply(lot.taux()).setScale(2, RoundingMode.HALF_UP);
                    lot.joursRestants = lot.joursRestants.subtract(quantite);
                    lot.valeurRestante = lot.valeurRestante.subtract(valeur);
                    traces.add(new Imputation(mouvement.id(), null, lot.id, lot.origine,
                            quantite, valeur, lot.taux()));
                    restant = restant.subtract(quantite);
                    if (restant.signum() == 0) break;
                }
                if (restant.signum() > 0) throw new IllegalStateException("CONGE_EXPIRATION_LOTS_INSUFFISANTS: " + mouvement.id());
            }
        }
        BigDecimal valeur = lots.stream().map(l -> l.valeurRestante).reduce(ZERO, BigDecimal::add);
        for (Anticipation anticipation : anticipations.values()) {
            valeur = valeur.subtract(anticipation.restant.multiply(anticipation.taux)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        List<LotRestant> restants = lots.stream().filter(l -> l.joursRestants.signum() > 0)
                .map(l -> new LotRestant(l.id, l.origine, l.date, l.joursRestants, l.valeurRestante)).toList();
        return new Situation(soldeJours, valeur, restants, List.copyOf(traces), joursSansLot);
    }

    private static LocalDate seuilExpiration(String commentaire, LocalDate date) {
        if (commentaire == null) return null;
        var matcher = java.util.regex.Pattern.compile("avant le (\\d{4}-\\d{2}-\\d{2})").matcher(commentaire);
        if (matcher.find()) return LocalDate.parse(matcher.group(1));
        var mois = java.util.regex.Pattern.compile("\\+(\\d+) mois").matcher(commentaire);
        return mois.find() ? date.minusMonths(Integer.parseInt(mois.group(1))) : null;
    }

    private static final class Lot {
        final int id;
        final String origine;
        final LocalDate date;
        final BigDecimal joursInitiaux;
        final BigDecimal valeurInitiale;
        BigDecimal joursRestants;
        BigDecimal valeurRestante;
        BigDecimal tauxFutur;

        Lot(int id, String origine, LocalDate date, BigDecimal jours, BigDecimal valeur) {
            this.id = id;
            this.origine = origine;
            this.date = date;
            this.joursInitiaux = jours;
            this.valeurInitiale = valeur;
            this.joursRestants = jours;
            this.valeurRestante = valeur;
        }

        int priorite() { return REPRISE.equals(origine) ? 0 : AJUSTEMENT.equals(origine) ? 1 : 2; }
        BigDecimal taux() { return tauxFutur != null ? tauxFutur
                : valeurInitiale.divide(joursInitiaux, 8, RoundingMode.HALF_UP); }
    }

    private static final class Anticipation {
        BigDecimal restant;
        final BigDecimal taux;
        final List<Imputation> couvertures = new ArrayList<>();
        Anticipation(BigDecimal restant, BigDecimal taux) {
            this.restant = restant;
            this.taux = taux;
        }
    }
}
