package mg.galaxam.sirh.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Référentiel centralisé des codes et messages d'erreurs métier pour le SIRH GALAXAM.
 */
public enum SirhErrorCode {

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "Les informations fournies sont invalides."),

    DATA_DUPLICATE(
            HttpStatus.CONFLICT,
            "Un enregistrement comportant cette valeur existe déjà."),

    DATA_REFERENCED(
            HttpStatus.CONFLICT,
            "Cet élément est lié à d'autres dossiers actifs."),

    DATA_INTEGRITY_VIOLATION(
            HttpStatus.CONFLICT,
            "L'opération ne respecte pas une contrainte d'intégrité des données."),

    FILE_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "Le fichier sélectionné dépasse la taille autorisée."),

    PARAMETER_FORMAT_INVALID(
            HttpStatus.BAD_REQUEST,
            "Le paramètre fourni est invalide."),

    PAYLOAD_INVALID(
            HttpStatus.BAD_REQUEST,
            "Le format des données transmises est incorrect."),

    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "Vous ne disposez pas des autorisations nécessaires."),

    AUTHENTICATION_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "Votre session n'est plus valide. Veuillez vous reconnecter."),

    PERSISTENCE_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Une erreur est survenue lors de l'enregistrement des données."),

    INTERNAL_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Une anomalie temporaire est survenue lors du traitement."),

    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "La ressource demandée n'existe pas."),

    BUSINESS_STATE_TRANSITION_BLOCKED(
            HttpStatus.CONFLICT,
            "Cette action n'est pas autorisée dans l'état courant."),

    STATE_CONFLICT(
            HttpStatus.CONFLICT,
            "La ressource est verrouillée dans son état courant."),

    PAYROLL_PERIOD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "La période de paie demandée n'existe pas."),

    PAYROLL_PERIOD_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "La période a été modifiée par un autre utilisateur. Rechargez les données."),

    PAYROLL_PERIOD_TRANSITION_BLOCKED(
            HttpStatus.CONFLICT,
            "Cette action n'est pas autorisée dans l'état courant de la période."),

    PAYROLL_PERIOD_REASON_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "Un motif est obligatoire pour cette action."),

    PAYROLL_PERIOD_PREREQUISITE_BLOCKED(
            HttpStatus.CONFLICT,
            "Un prérequis de la période n'est pas satisfait."),

    BLOCKING_CONFIGURATION_ERROR(
            HttpStatus.CONFLICT,
            "Une configuration métier obligatoire est absente ou incomplète."),

    PAYROLL_ALLOWANCE_CONFIGURATION_MISSING(
            HttpStatus.CONFLICT,
            "Une configuration d'indemnité repas ou déplacement est absente pour le contexte demandé."),

    PAYROLL_EMPLOYEE_INACTIVE(
            HttpStatus.CONFLICT,
            "L'employé est inactif ou ne dispose d'aucun contrat ou affectation active sur la période de paie calculée."),

    // =========================================================================
    // POINTAGE & PAIE
    // =========================================================================
    POINTAGE_EXPORT_SNAPSHOT_MISSING(HttpStatus.CONFLICT, "Aucun snapshot pour cette période validée."),
    POINTAGE_EXPORT_SNAPSHOT_MISMATCH(HttpStatus.CONFLICT, "Le journal ne correspond plus à la version figée."),
    POINTAGE_EXPORT_SOURCE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Les données d’indemnités sont indisponibles."),
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

    POINTAGE_PERIOD_CONTEXT_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "Une période de paie ou une date de pointage explicite est obligatoire."),

    POINTAGE_PERIOD_CONTEXT_AMBIGUOUS(
            HttpStatus.CONFLICT,
            "Plusieurs périodes de paie couvrent la date demandée."),

    POINTAGE_HORAIRES_INVALIDES(
            HttpStatus.BAD_REQUEST,
            "L'heure de sortie doit être postérieure à l'heure d'entrée."),

    // =========================================================================
    // CONTRAT & CARRIÈRE
    // =========================================================================
    CONTRACT_ASSIGNMENT_MISSING(HttpStatus.CONFLICT, "L'affectation historique du contrat est introuvable."),
    CONTRACT_TEMPLATE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "Aucun modèle n'est supporté pour ce type de contrat."),
    CONTRACT_DOCUMENT_DATA_MISSING(HttpStatus.UNPROCESSABLE_CONTENT, "Des données obligatoires du contrat sont manquantes."),
    CONTRACT_TEMPLATE_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "Le modèle contractuel contient des variables inconnues ou résiduelles."),

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

    CONGE_POINTAGE_VERROUILLE(
            HttpStatus.CONFLICT,
            "Ce congé ne peut plus être modifié : le pointage de la période concernée est déjà validé."),

    LEAVE_RATE_MISSING(
            HttpStatus.CONFLICT,
            "Aucun taux de congé n'est configuré pour cet employé à cette date. Configurez le taux avant de générer la paie."),

    CYCLE_ACCUMULATORS_UNKNOWN(
            HttpStatus.CONFLICT,
            "Les données antérieures à la reprise sont incomplètes. Le taux de la prochaine période ne peut pas être calculé automatiquement sans arbitrage RH."),

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
            "L'employé demandé n'existe pas."),

    // =========================================================================
    // AJUSTEMENTS DE PAIE (P1-01)
    // =========================================================================
    PAYROLL_ADJUSTMENT_ALREADY_APPROVED(
            HttpStatus.CONFLICT,
            "Cet ajustement est déjà approuvé et ne peut pas être approuvé à nouveau."),

    PAYROLL_ADJUSTMENT_COMMAND_BLOCKED(
            HttpStatus.CONFLICT,
            "Cette commande n'est pas autorisée dans l'état courant de l'ajustement."),

    PAYROLL_ADJUSTMENT_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "L'ajustement a été modifié entre-temps. Rechargez les données."),

    // =========================================================================
    // CONFIGURATION LÉGALE MANQUANTE (P1-03)
    // =========================================================================
    LEGAL_CONFIG_MISSING(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "Configuration légale manquante pour la date demandée. LEGAL_REVIEW_REQUIRED.");

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
