package mg.galaxam.sirh.pointage.domain;

import java.util.Locale;

/**
 * Statuts canoniques du cycle de vie des périodes de paie et de pointage.
 * Centralise les codes et leurs libellés officiels après suppression de ref_statut_periode_paie.
 */
public enum PayrollPeriodStatus {
    BROUILLON("Période créée, non ouverte"),
    EN_COURS("Saisie en cours"),
    A_VALIDER("Terminée, en attente de validation"),
    TRANSMIS_DAF("Transmise pour arbitrage"),
    VALIDEE("Pointage Scellé & Figé — Prêt pour calcul Paie"),
    PAIE_VALIDEE("Paie Validée & Approuvée — Prête pour virement et clôture"),
    CLOTUREE("Clôturée — archivée, immuable");

    private final String libelle;

    PayrollPeriodStatus(String libelle) {
        this.libelle = libelle;
    }

    public String getLibelle() {
        return libelle;
    }

    /**
     * Retourne le libellé officiel correspondant à un code de statut.
     * Si le code est inconnu ou null, renvoie une valeur par défaut cohérente.
     */
    public static String getLibelle(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        try {
            return PayrollPeriodStatus.valueOf(code.trim().toUpperCase(Locale.ROOT)).getLibelle();
        } catch (IllegalArgumentException e) {
            return code;
        }
    }

    /**
     * Vérifie si le code correspond à un statut canonique valide.
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        try {
            PayrollPeriodStatus.valueOf(code.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
