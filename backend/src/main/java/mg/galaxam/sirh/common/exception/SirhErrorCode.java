package mg.galaxam.sirh.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Référentiel centralisé des codes et messages d'erreurs métier pour le SIRH GALAXAM.
 */
public enum SirhErrorCode {

    // =========================================================================
    // POINTAGE & PAIE
    // =========================================================================
    POINTAGE_DATE_FUTURE(
            HttpStatus.CONFLICT,
            "Action interdite : Impossible d'enregistrer un pointage pour une date future. La saisie doit s'effectuer le jour même ou rétroactivement."),

    POINTAGE_PERIODE_VERROUILLEE(
            HttpStatus.CONFLICT,
            "Impossible de modifier les pointages : la période de paie est fermée, validée ou en attente de validation."),

    POINTAGE_CONFLIT_OCC(
            HttpStatus.CONFLICT,
            "Conflit d'accès concurrent : ces pointages ont été modifiés par un autre utilisateur ou validés entre-temps. Veuillez recharger la grille."),

    POINTAGE_SITE_NON_AFFECTE(
            HttpStatus.CONFLICT,
            "L'employé n'est pas rattaché au site sélectionné pour cette date."),

    POINTAGE_EMPLOYE_INACTIF(
            HttpStatus.CONFLICT,
            "L'employé est inactif ou ne dispose d'aucun contrat actif pour la date sélectionnée."),

    POINTAGE_HORAIRES_INVALIDES(
            HttpStatus.BAD_REQUEST,
            "L'heure de sortie doit être postérieure à l'heure d'entrée."),

    // =========================================================================
    // CONTRAT & CARRIÈRE
    // =========================================================================
    CONTRAT_ACTIF_EXISTANT(
            HttpStatus.BAD_REQUEST,
            "L'employé possède déjà un contrat ACTIF. Utilisez un workflow de transition (Mutation, Promotion, Renouvellement, etc.)."),

    CONTRAT_BROUILLON_EXISTANT(
            HttpStatus.BAD_REQUEST,
            "L'employé possède déjà un workflow BROUILLON en cours. Veuillez le valider, modifier ou annuler avant d'en créer un nouveau."),

    CONTRAT_CDD_DUREE_MAX_DEPASSEE(
            HttpStatus.BAD_REQUEST,
            "Chaque CDD, renouvellement compris, ne peut excéder 24 mois selon le Code du travail malgache."),

    CONTRAT_CDD_DUREE_MIN_NON_ATTEINTE(
            HttpStatus.BAD_REQUEST,
            "Un CDD doit avoir une durée minimale d'un mois selon le Code du travail malgache."),

    CONTRAT_CDD_MAX_RENOUVELLEMENTS(
            HttpStatus.BAD_REQUEST,
            "Le CDD a déjà atteint le nombre maximal de renouvellements autorisés (2). Au-delà, le contrat doit être requalifié en CDI."),

    CONTRAT_SOURCE_MANQUANT(
            HttpStatus.BAD_REQUEST,
            "Cette opération de transition de carrière requiert un contrat source valide."),

    CONTRAT_TRANSITION_INTERDITE(
            HttpStatus.BAD_REQUEST,
            "Cette transition de carrière n'est pas autorisée selon la matrice des règles RH."),

    CONTRAT_PERIODE_ESSAI_INTERDITE(
            HttpStatus.BAD_REQUEST,
            "Une période d'essai ne peut pas être appliquée sur un avenant interne ou une transformation de contrat."),

    CONTRAT_NON_TROUVE(
            HttpStatus.NOT_FOUND,
            "Le contrat de travail demandé n'existe pas."),

    // =========================================================================
    // CONGÉS & ABSENCES
    // =========================================================================
    CONGE_DATES_INVALIDES(
            HttpStatus.BAD_REQUEST,
            "La date de fin ne peut pas être antérieure à la date de début du congé."),

    CONGE_SOLDE_INSUFFISANT(
            HttpStatus.BAD_REQUEST,
            "Solde de congé insuffisant. Une dérogation RH de solde négatif / anticipation est requise."),

    CONGE_PREAVIS_INSUFFISANT(
            HttpStatus.BAD_REQUEST,
            "Le préavis minimum requis pour cette demande n'est pas respecté. Une dérogation RH de préavis est requise."),

    CONGE_ANCIENNETE_INSUFFISANTE(
            HttpStatus.BAD_REQUEST,
            "L'employé ne justifie pas de l'ancienneté requise (12 mois de service effectif selon l'Art. 85 du Code du Travail). Dérogation RH requise."),

    CONGE_CHEVAUCHEMENT_DETECTE(
            HttpStatus.BAD_REQUEST,
            "Chevauchement détecté : l'employé dispose déjà d'une demande de congé active couvrant cette période."),

    CONGE_DEMI_JOURNEE_INVALIDE(
            HttpStatus.BAD_REQUEST,
            "Une demande de congé en demi-journée doit concerner la même journée (date début = date fin) avec indication MATIN ou APRES_MIDI."),

    CONGE_MOTIF_REFUS_REQUIS(
            HttpStatus.BAD_REQUEST,
            "Un motif de refus explicite est obligatoire lors du rejet d'une demande de congé."),

    CONGE_NON_TROUVE(
            HttpStatus.NOT_FOUND,
            "La demande de congé demandée n'existe pas."),

    // =========================================================================
    // EMPLOYÉ & RÉFÉRENTIELS
    // =========================================================================
    EMPLOYE_CIN_DOUBLON(
            HttpStatus.CONFLICT,
            "Ce numéro de CIN est déjà enregistré pour un autre employé."),

    EMPLOYE_CIN_INVALIDE(
            HttpStatus.BAD_REQUEST,
            "Le numéro de CIN doit comporter exactement 12 chiffres."),

    EMPLOYE_MATRICULE_DOUBLON(
            HttpStatus.CONFLICT,
            "Le matricule généré existe déjà pour un autre employé."),

    EMPLOYE_NON_TROUVE(
            HttpStatus.NOT_FOUND,
            "L'employé demandé n'existe pas.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    SirhErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
