package mg.galaxam.sirh.paie.api.dto;

/** Statut métier d'une phase, avec un libellé et une tonalité sémantique pour l'interface. */
public record PayrollPhaseStatus(String code, String label, String tone) {
}
