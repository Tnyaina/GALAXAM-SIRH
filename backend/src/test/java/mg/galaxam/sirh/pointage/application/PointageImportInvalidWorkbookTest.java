package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointageImportInvalidWorkbookTest {
    @Test
    void explainsHowToRecoverWhenUploadIsNotAnXlsxWorkbook() {
        PointageEmployeService employes = mock(PointageEmployeService.class);
        PointageService pointages = mock(PointageService.class);
        FichePointageEmployeDTO fiche = mock(FichePointageEmployeDTO.class);
        when(employes.getFichePointageEmploye(7, null, "periode", 9)).thenReturn(fiche);
        when(fiche.getIdPeriode()).thenReturn(9);
        when(fiche.getPointagesData()).thenReturn(List.of());

        PointageImportService service = new PointageImportService(employes, pointages, null);
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "pointage.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not an XLSX workbook".getBytes());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.preview(7, 9, invalidFile));

        assertTrue(error.getMessage().contains("classeur XLSX valide"));
        assertTrue(error.getMessage().contains("Téléchargez le modèle de pointage"));
    }
}
