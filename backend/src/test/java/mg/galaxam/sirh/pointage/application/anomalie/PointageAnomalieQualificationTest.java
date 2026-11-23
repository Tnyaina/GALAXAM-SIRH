package mg.galaxam.sirh.pointage.application.anomalie;

import static org.junit.jupiter.api.Assertions.*;

import mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO;
import org.junit.jupiter.api.Test;
import java.sql.Date;
import java.util.List;

class PointageAnomalieQualificationTest {
    @Test
    void detectionConserveLesCriteresEtLesFiltresDeLaPeriode() {
        var requete = PointageAnomalieDetection.construireRequete(
                4, 18, "2026-08-03", "Raso");
        assertTrue(requete.sql().contains("st.code = 'ANOMALIE_SAISIE'"));
        assertTrue(requete.sql().contains("st.code = 'DEMI_JOURNEE'"));
        assertTrue(requete.sql().contains("p.minutes_retard > 0"));
        assertTrue(requete.sql().contains("EXTRACT(DOW FROM p.date_pointage) NOT IN (0, 6)"));
        assertEquals(List.of(4, 18, Date.valueOf("2026-08-03"), "%raso%", "%raso%", "%raso%"),
                requete.parametres());
    }

    @Test
    void saisieIncompleteBloqueLaTransmission() {
        PointageAnomalieDTO dto = nouvelleJournee("ANOMALIE_SAISIE");
        PointageAnomalieQualification.qualifier(dto, false, false, false);
        assertEquals("INCOMPLET", dto.getTypeAnomalie());
        assertEquals("CRITIQUE", dto.getSeverite());
        assertTrue(dto.isBloquantTransmission());
        assertEquals("todo", dto.getStatutAnomalie());
    }

    @Test
    void absenceNonJustifieeResteBloquanteJusquaSaConfirmation() {
        PointageAnomalieDTO dto = nouvelleJournee("ABSENCE_NON_JUSTIFIEE");
        PointageAnomalieQualification.qualifier(dto, false, false, false);
        assertEquals("ABSENCE", dto.getTypeAnomalie());
        assertEquals("CRITIQUE", dto.getSeverite());
        assertTrue(dto.isBloquantTransmission());

        PointageAnomalieQualification.qualifier(dto, true, false, false);
        assertEquals("FAIBLE", dto.getSeverite());
        assertFalse(dto.isBloquantTransmission());
        assertEquals("done", dto.getStatutAnomalie());
    }

    @Test
    void demiJourneeMotiveeEtRetardNeBloquePasLaTransmission() {
        PointageAnomalieDTO demi = nouvelleJournee("DEMI_JOURNEE");
        demi.setMotifAjustement("Confirmée");
        PointageAnomalieQualification.qualifier(demi, false, false, false);
        assertEquals("FAIBLE", demi.getSeverite());
        assertEquals("done", demi.getStatutAnomalie());
        assertFalse(demi.isBloquantTransmission());

        PointageAnomalieDTO retard = nouvelleJournee("PRESENT");
        retard.setMinutesRetard(15);
        PointageAnomalieQualification.qualifier(retard, false, false, false);
        assertEquals("RETARD", retard.getTypeAnomalie());
        assertEquals("MOYENNE", retard.getSeverite());
        assertFalse(retard.isBloquantTransmission());
    }

    private static PointageAnomalieDTO nouvelleJournee(String statut) {
        PointageAnomalieDTO dto = new PointageAnomalieDTO();
        dto.setStatutCode(statut);
        dto.setHeureEntreeMatin("");
        dto.setHeureSortieMatin("");
        dto.setHeureEntreeAm("");
        dto.setHeureSortieAm("");
        return dto;
    }
}
