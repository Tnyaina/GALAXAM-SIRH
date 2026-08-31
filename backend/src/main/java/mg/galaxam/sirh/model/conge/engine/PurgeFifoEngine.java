package mg.galaxam.sirh.model.conge.engine;

import org.springframework.stereotype.Component;

/**
 * Moteur mathématique de purge triennale des droits aux congés selon la règle FIFO stricte.
 * Réf. Code du Travail Malagasy (Loi n° 2003-044, Art. 85 : prescription des droits à 36 mois).
 */
@Component
public class PurgeFifoEngine {

    /**
     * Calcule le nombre exact de jours acquis prescrits à purger aujourd'hui.
     *
     * @param totalAcquisAnciens Droits acquis il y a plus de 36 mois
     * @param totalPris          Total des congés effectivement pris par le salarié
     * @param totalDejaExpire    Total des jours déjà expirés lors de purges précédentes
     * @return Nombre de jours à expirer lors de cette exécution (>= 0)
     */
    public double calculerJoursAExpier(double totalAcquisAnciens, double totalPris, double totalDejaExpire) {
        if (totalAcquisAnciens <= 0.0001) {
            return 0.0;
        }

        // Règle FIFO : les congés pris consomment en priorité absolue les droits les plus anciens
        double consommeSurAnciens = Math.min(totalAcquisAnciens, Math.max(0.0, totalPris));
        double anciensNonConsommes = Math.max(0.0, totalAcquisAnciens - consommeSurAnciens);

        // Déduction de ce qui a déjà été purgé par le passé
        double joursAExpirer = Math.max(0.0, anciensNonConsommes - Math.max(0.0, totalDejaExpire));

        return Math.round(joursAExpirer * 100.0) / 100.0;
    }
}
