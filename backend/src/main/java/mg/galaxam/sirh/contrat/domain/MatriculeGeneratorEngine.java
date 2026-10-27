package mg.galaxam.sirh.contrat.domain;

import org.springframework.stereotype.Component;

/**
 * Moteur de génération des matricules employés.
 * Conserve la logique historique séquentielle sur 4 chiffres (ex: 0001, 0042).
 */
@Component
public class MatriculeGeneratorEngine {

    /**
     * Génère un matricule selon la logique séquentielle sur 4 chiffres (ex: 0001, 0042).
     */
    public String genererMatriculeSequentiel(long sequenceCount) {
        long num = Math.max(1, sequenceCount);
        return String.format("%04d", num);
    }

    /**
     * Génère un matricule par défaut (alias de la logique séquentielle).
     */
    public String genererMatriculeParDefaut(long sequenceCount) {
        return genererMatriculeSequentiel(sequenceCount);
    }
}
