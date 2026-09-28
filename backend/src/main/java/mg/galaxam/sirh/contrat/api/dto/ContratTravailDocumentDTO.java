package mg.galaxam.sirh.contrat.api.dto;

import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.contrat.persistence.ContratTravail;
import mg.galaxam.sirh.contrat.persistence.EmployeAffectationCarriere;

/** Aperçu du canevas interne, issu exclusivement du contrat et de son affectation initiale. */
public record ContratTravailDocumentDTO(
        Integer idContrat, Integer idEmploye, String numeroContrat, String codeTypeContrat,
        String statut, String template, ContratTravail contrat, EmployeAffectationCarriere affectation,
        DocumentRhDTO.EmployeDocumentDTO employe, Map<String, String> variables,
        List<String> donneesManquantes, List<String> avertissements, List<String> paragraphes) {
    public boolean isGenerationPossible() { return donneesManquantes.isEmpty(); }
}
