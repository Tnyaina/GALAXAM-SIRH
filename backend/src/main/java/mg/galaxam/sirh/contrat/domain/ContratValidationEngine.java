package mg.galaxam.sirh.contrat.domain;


import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Moteur de validation des règles métier de gestion des contrats RH.
 * Encapsule les contraintes légales du Code du Travail :
 * - Types de contrats et CDD : Loi n° 2024-014, Art. 23 à 25 & 28 (Loi n° 2003-044, Art. 8 à 10).
 * - Période d'essai : Loi n° 2024-014, Art. 52 à 54 (Loi n° 2003-044, Art. 35 et 36).
 */
@Component
public class ContratValidationEngine {

    /** Types de contrats exigeant légalement une date de fin déterminée */
    private static final Set<String> TYPES_AVEC_DATE_FIN_OBLIGATOIRE = Set.of("CDD", "STAGE", "PRESTATION", "CONSULTANT", "INTERIM", "TEMPORAIRE");

    /** Durée maximale d'une période d'essai initiale en mois (selon la convention/catégorie, max 6 mois, Loi 2024-014 Art. 52-54) */
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
}
