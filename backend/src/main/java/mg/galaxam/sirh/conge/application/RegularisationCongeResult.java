package mg.galaxam.sirh.conge.application;

/**
 * Résultat structuré et explicite d'une régularisation de congé depuis Pointage.
 *
 * @param demandeId    identifiant de la demande créée (null si aucun droit n'a pu être imputé)
 * @param joursDemandes durée totale d'absence que l'on a tenté de régulariser
 * @param joursCouverts fraction réellement couverte par les droits congés disponibles
 * @param joursRestants fraction restante d'absence non couverte (devenue absence décomptée)
 * @param soldeAvant   solde de congé disponible avant régularisation
 * @param soldeApres   solde de congé résiduel après imputation de la fraction couverte
 */
public record RegularisationCongeResult(
        Integer demandeId,
        double joursDemandes,
        double joursCouverts,
        double joursRestants,
        double soldeAvant,
        double soldeApres
) {}
