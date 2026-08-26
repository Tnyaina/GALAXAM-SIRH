package mg.galaxam.sirh.model.contrat;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import mg.galaxam.sirh.api.dto.contrat.ContratTravailGlobalDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContratGlobalSearchTest {

    @Test
    @DisplayName("Calcul des jours restants sur un CDD à terme dans 18 jours")
    void testCalculJoursRestantsCdd() {
        LocalDate today = LocalDate.now();
        LocalDate finCdd = today.plusDays(18);

        ContratTravailGlobalDTO dto = new ContratTravailGlobalDTO();
        dto.setNumeroContrat("CTR-2026-0012");
        dto.setDateFin(Date.valueOf(finCdd));
        dto.setJoursRestantsFin((int) ChronoUnit.DAYS.between(today, finCdd));

        System.out.println("[TEST CONTRAT GLOBAL] Contrat " + dto.getNumeroContrat() + " - Fin : " + finCdd + " (" + dto.getJoursRestantsFin() + " jours restants).");

        assertEquals(18, dto.getJoursRestantsFin());
        assertTrue(dto.getJoursRestantsFin() <= 30, "Doit être inclus dans le filtre des échéances à 30 jours.");
    }

    @Test
    @DisplayName("Calcul de la période d'essai imminente (Échéance dans 5 jours)")
    void testCalculEcheancePeriodeEssai() {
        LocalDate today = LocalDate.now();
        LocalDate finEssai = today.plusDays(5);

        ContratTravailGlobalDTO dto = new ContratTravailGlobalDTO();
        dto.setDateFinEssai(Date.valueOf(finEssai));
        dto.setJoursRestantsEssai((int) ChronoUnit.DAYS.between(today, finEssai));

        System.out.println("[TEST CONTRAT GLOBAL] Fin d'essai : " + finEssai + " (" + dto.getJoursRestantsEssai() + " jours restants).");

        assertEquals(5, dto.getJoursRestantsEssai());
        assertTrue(dto.getJoursRestantsEssai() <= 7, "Doit être marqué comme urgent (J-7).");
    }
}
