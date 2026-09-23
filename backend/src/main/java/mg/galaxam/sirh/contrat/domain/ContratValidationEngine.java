package mg.galaxam.sirh.contrat.domain;

import mg.galaxam.sirh.contrat.persistence.*;
import mg.galaxam.sirh.contrat.api.dto.*;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Moteur de validation des règles métier de gestion des contrats et transitions de carrière RH.
 * Encapsule les contraintes légales du Code du Travail (CDI, CDD, Périodes d'essai, Transitions).
 */
@Component
public class ContratValidationEngine {

    /** Types de contrats exigeant légalement une date de fin déterminée */
    private static final Set<String> TYPES_AVEC_DATE_FIN_OBLIGATOIRE = Set.of("CDD", "STAGE", "PRESTATION", "CONSULTANT", "INTERIM");

    /** Durée maximale d'une période d'essai initiale en mois (selon la convention/catégorie, max 6 mois) */
    public static final int DUREE_MAX_ESSAI_MOIS = 6;

    /**
     * Valide la cohérence des dates contractuelles selon le type de contrat.
     */
    public void validerDatesContrat(String codeTypeContrat, Date dateDebut, Date dateFin, Integer dureeEssaiMois) {
        List<String> erreurs = new ArrayList<>();

        if (dateDebut == null) {
            erreurs.add("La date de début de contrat est obligatoire.");
        }

        if (codeTypeContrat != null) {
            String codeUpper = codeTypeContrat.toUpperCase();
            if (TYPES_AVEC_DATE_FIN_OBLIGATOIRE.contains(codeUpper) && dateFin == null) {
                erreurs.add("La date de fin est obligatoire pour un contrat de type " + codeUpper + ".");
            }
        }

        if (dateDebut != null && dateFin != null) {
            if (dateFin.before(dateDebut)) {
                erreurs.add("La date de fin de contrat (" + dateFin + ") ne peut pas être antérieure à la date de début (" + dateDebut + ").");
            }
        }

        if (dureeEssaiMois != null) {
            if (dureeEssaiMois < 0 || dureeEssaiMois > DUREE_MAX_ESSAI_MOIS) {
                erreurs.add("La durée de période d'essai doit être comprise entre 0 et " + DUREE_MAX_ESSAI_MOIS + " mois (reçu : " + dureeEssaiMois + ").");
            }
        }

        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join(" | ", erreurs));
        }
    }

    /**
     * Calcule la date de fin prévisionnelle d'une période d'essai.
     */
    public LocalDate calculerFinPeriodeEssai(LocalDate dateDebut, int dureeMois) {
        if (dateDebut == null || dureeMois <= 0) return null;
        return dateDebut.plusMonths(dureeMois).minusDays(1);
    }

    /**
     * Valide l'éligibilité d'une transition de carrière.
     */
    public void validerTransitionCarriere(String codeEvenement, boolean hasContratActif, String motifSortie) {
        if (codeEvenement == null || codeEvenement.isBlank()) {
            throw new IllegalArgumentException("Le type d'événement de transition RH est obligatoire.");
        }

        String code = codeEvenement.toUpperCase();
        if (Set.of("MUTATION", "PROMOTION", "REVALORISATION", "RENOUVELLEMENT_CDD", "CONFIRMATION_ESSAI").contains(code)) {
            if (!hasContratActif) {
                throw new IllegalArgumentException("L'événement " + code + " requiert un contrat en cours actif.");
            }
        }

        if (Set.of("RUPTURE_ESSAI", "DEPART_RETRAITE", "FIN_CONTRAT", "DEMISSION", "LICENCIEMENT").contains(code)) {
            if (motifSortie == null || motifSortie.isBlank()) {
                throw new IllegalArgumentException("Un motif de sortie explicite est obligatoire pour l'événement " + code + ".");
            }
        }
    }

    /**
     * Valide la légitimité d'un changement de statut de workflow.
     */
    public boolean estTransitionStatutAutorisee(String statutActuel, String statutCible) {
        if (statutActuel == null || statutCible == null) return false;
        String cur = statutActuel.toUpperCase();
        String target = statutCible.toUpperCase();

        if (cur.equals(target)) return true;

        return switch (cur) {
            case "BROUILLON" -> Set.of("EN_ATTENTE", "VALIDE", "ANNULE", "REFUSE").contains(target);
            case "EN_ATTENTE" -> Set.of("VALIDE", "REFUSE", "ANNULE").contains(target);
            case "VALIDE" -> Set.of("ACTIF", "PLANIFIE", "ANNULE").contains(target);
            case "PLANIFIE" -> Set.of("ACTIF", "ANNULE").contains(target);
            case "ACTIF" -> Set.of("CLOTURE", "ROMPU", "TERMINE").contains(target);
            case "CLOTURE", "ROMPU", "TERMINE", "ANNULE", "REFUSE" -> false; // États terminaux
            default -> false;
        };
    }
}
