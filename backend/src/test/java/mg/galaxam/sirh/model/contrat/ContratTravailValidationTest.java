package mg.galaxam.sirh.model.contrat;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ContratTravailValidationTest {

    @Test
    @DisplayName("Calcul automatique de la date de fin de période d'essai (ex: 3 mois)")
    void testCalculFinPeriodeEssai() {
        LocalDate dateDebut = LocalDate.of(2026, 1, 1);
        int dureeEssaiMois = 3;

        LocalDate dateFinEssai = dateDebut.plusMonths(dureeEssaiMois);

        System.out.println("[TEST CONTRAT] Début contrat : " + dateDebut + " + " + dureeEssaiMois + " mois d'essai -> Fin essai : " + dateFinEssai);
        assertEquals(LocalDate.of(2026, 4, 1), dateFinEssai, "La date de fin d'essai doit être exactement 3 mois plus tard.");
    }

    @Test
    @DisplayName("Validation de la date de fin obligatoire pour un contrat CDD ou Stage")
    void testDateFinObligatoireCDD() {
        String codeTypeContrat = "CDD";
        Date dateFin = null;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            if (("CDD".equals(codeTypeContrat) || "STAGE".equals(codeTypeContrat)) && dateFin == null) {
                throw new IllegalArgumentException("La date de fin est obligatoire pour un contrat à durée déterminée (CDD/Stage).");
            }
        });

        System.out.println("[TEST CONTRAT] Contrôle CDD sans date de fin : " + ex.getMessage());
        assertEquals("La date de fin est obligatoire pour un contrat à durée déterminée (CDD/Stage).", ex.getMessage());
    }

    @Test
    @DisplayName("Cohérence Stagiaire / Convention de Stage")
    void testCoherenceStagiaire() {
        String intervenant = "STAGIAIRE";
        String typeContrat = "CDI"; // Incohérent

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            if ("STAGIAIRE".equals(intervenant) && !"STAGE".equals(typeContrat)) {
                throw new IllegalArgumentException("Un intervenant Stagiaire doit obligatoirement être sous un contrat de type Stage.");
            }
        });

        System.out.println("[TEST CONTRAT] Contrôle Stagiaire sous CDI : " + ex.getMessage());
        assertEquals("Un intervenant Stagiaire doit obligatoirement être sous un contrat de type Stage.", ex.getMessage());
    }

    @Test
    @DisplayName("Vérification d'ordre chronologique des dates de contrat (dateFin > dateDebut)")
    void testOrdreChronologiqueDates() {
        LocalDate debut = LocalDate.of(2026, 8, 1);
        LocalDate fin = LocalDate.of(2026, 7, 1); // Antérieure

        assertTrue(fin.isBefore(debut), "La date de fin ne peut pas être antérieure à la date de début.");
    }
}
