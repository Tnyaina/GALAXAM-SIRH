package mg.galaxam.sirh.pointage.application.anomalie;

import mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO;

/** Qualifie une journée déjà sélectionnée : type, gravité, blocage et état de traitement. */
public final class PointageAnomalieQualification {
    private PointageAnomalieQualification() {}

    public static void qualifier(PointageAnomalieDTO dto, boolean dateAjustementPresente,
            boolean demandeLiee, boolean demandeValidee) {
        String codeStatut = dto.getStatutCode();
        boolean aMotif = dto.getMotifAjustement() != null && !dto.getMotifAjustement().trim().isEmpty();
        boolean absenceReglee = dateAjustementPresente || demandeLiee || demandeValidee;

        boolean entreeMatin = aUneHeure(dto.getHeureEntreeMatin());
        boolean sortieMatin = aUneHeure(dto.getHeureSortieMatin());
        boolean entreeApresMidi = aUneHeure(dto.getHeureEntreeAm());
        boolean sortieApresMidi = aUneHeure(dto.getHeureSortieAm());
        boolean matinComplet = entreeMatin && sortieMatin && !entreeApresMidi && !sortieApresMidi;
        boolean apresMidiComplet = !entreeMatin && !sortieMatin && entreeApresMidi && sortieApresMidi;
        boolean estDemiJournee = "DEMI_JOURNEE".equals(codeStatut) || matinComplet || apresMidiComplet;

        if ("ANOMALIE_SAISIE".equals(codeStatut)) {
            dto.setTypeAnomalie("INCOMPLET");
            dto.setDescriptionAnomalie(estDemiJournee
                    ? "Une seule plage travaillée : qualifier la demi-journée dans la régularisation."
                    : "Pointage incomplet : vérification et régularisation nécessaires.");
            dto.setSeverite("CRITIQUE");
            dto.setBloquantTransmission(true);
            dto.setStatutAnomalie("todo");
        } else if (estDemiJournee) {
            dto.setTypeAnomalie("DEMI_JOURNEE");
            dto.setDescriptionAnomalie(aMotif 
                    ? ("Demi-journée validée : " + dto.getMotifAjustement()) 
                    : "Demi-journée travaillée : motif de régularisation conseillé.");
            dto.setSeverite(aMotif ? "FAIBLE" : "MOYENNE");
            dto.setBloquantTransmission(false);
            dto.setStatutAnomalie(aMotif ? "done" : "todo");
        } else if ("ABSENCE_NON_JUSTIFIEE".equals(codeStatut)) {
            dto.setTypeAnomalie("ABSENCE");
            if (absenceReglee) {
                dto.setDescriptionAnomalie("Absence non justifiée confirmée" +
                        (aMotif ? " : " + dto.getMotifAjustement() : "."));
                dto.setSeverite("FAIBLE");
            } else if (dto.getDemandeEnAttenteLibelle() != null && !dto.getDemandeEnAttenteLibelle().isBlank()) {
                dto.setDescriptionAnomalie("Absence : demande de " + dto.getDemandeEnAttenteLibelle() + " en attente de validation.");
                dto.setSeverite("MOYENNE");
            } else if (!aMotif) {
                dto.setDescriptionAnomalie("Absence non justifiée (non régularisée sur jour ouvré).");
                dto.setSeverite("CRITIQUE");
            } else {
                dto.setDescriptionAnomalie("Absence non justifiée avec proposition : " + dto.getMotifAjustement());
                dto.setSeverite("HAUTE");
            }
            dto.setBloquantTransmission(!absenceReglee);
            dto.setStatutAnomalie(absenceReglee ? "done" : "todo");
        } else if ("ABSENCE_JUSTIFIEE".equals(codeStatut)) {
            dto.setTypeAnomalie("ABSENCE");
            dto.setDescriptionAnomalie(absenceReglee
                    ? "Absence justifiée confirmée : " + dto.getMotifAjustement()
                    : "Absence justifiée à vérifier : " + dto.getMotifAjustement());
            dto.setSeverite(absenceReglee ? "FAIBLE" : "MOYENNE");
            dto.setBloquantTransmission(!absenceReglee);
            dto.setStatutAnomalie(absenceReglee ? "done" : "todo");
        } else if (dto.getMinutesRetard() != null && dto.getMinutesRetard() > 0) {
            dto.setTypeAnomalie("RETARD");
            dto.setDescriptionAnomalie(aMotif
                    ? "Retard de " + dto.getMinutesRetard() + " min régularisé : " + dto.getMotifAjustement()
                    : "Retard de " + dto.getMinutesRetard() + " min à vérifier : corriger les horaires s'ils sont erronés, sinon confirmer le pointage réel.");
            dto.setSeverite("MOYENNE");
            dto.setBloquantTransmission(false);
            dto.setStatutAnomalie(aMotif ? "done" : "todo");
        } else {
            dto.setTypeAnomalie("AUTRE");
            dto.setDescriptionAnomalie("Pointage régularisé.");
            dto.setSeverite("FAIBLE");
            dto.setBloquantTransmission(false);
            dto.setStatutAnomalie(aMotif ? "done" : "todo");
        }
    }

    private static boolean aUneHeure(String heure) {
        return heure != null && !heure.isEmpty();
    }
}
