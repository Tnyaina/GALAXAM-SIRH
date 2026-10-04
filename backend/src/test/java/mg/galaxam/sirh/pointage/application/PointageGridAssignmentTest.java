package mg.galaxam.sirh.pointage.application;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PointageGridAssignmentTest {
    @Test void grilleNeProlongePasUneAncienneAffectation() {
        var ancienne = assignment(1, "ACTIF", "2025-01-01", "2025-12-31");
        assertNull(PointageService.trouverAffectationApplicable(List.of(ancienne), LocalDate.of(2026, 1, 1)));
        assertSame(ancienne, PointageService.trouverAffectationApplicable(List.of(ancienne),
                LocalDate.of(2025, 12, 31)));
    }

    @Test void grilleRefuseChevauchementEtIgnoreBrouillon() {
        var effective = assignment(1, "HISTORISE", "2025-12-01", "2026-01-31");
        var brouillon = assignment(2, "BROUILLON", "2026-01-01", null);
        var concurrente = assignment(3, "ACTIF", "2026-01-01", null);
        LocalDate jour = LocalDate.of(2026, 1, 25);
        assertSame(effective, PointageService.trouverAffectationApplicable(List.of(effective, brouillon), jour));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> PointageService.trouverAffectationApplicable(List.of(effective, concurrente), jour))
                .getMessage().contains("AMBIGUOUS_CONFIGURATION"));
    }

    private static PointageService.AffCandidat assignment(int id, String statut, String debut, String fin) {
        var value = new PointageService.AffCandidat();
        value.idAffectation = id;
        value.codeStatut = statut;
        value.dateDebut = LocalDate.parse(debut);
        value.dateFin = fin == null ? null : LocalDate.parse(fin);
        return value;
    }
}
