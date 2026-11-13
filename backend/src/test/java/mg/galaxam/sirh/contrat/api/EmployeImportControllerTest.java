package mg.galaxam.sirh.contrat.api;

import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.application.EmployeService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmployeImportControllerTest {
    @Test
    void previewRattacheEnfantEtDeuxiemeBanqueAuSalariePrecedent() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            sheet.createRow(0);
            var header = sheet.createRow(1);
            header.createCell(1).setCellValue("Mle");
            header.createCell(6).setCellValue("CIN");
            var employee = sheet.createRow(2);
            employee.createCell(1).setCellValue("35");
            employee.createCell(2).setCellValue("F");
            employee.createCell(3).setCellValue("Nom");
            employee.createCell(4).setCellValue("Prénom");
            employee.createCell(6).setCellValue("123456789012");
            employee.createCell(11).setCellValue("0340000000/0330000000");
            employee.createCell(32).setCellValue("BMOI");
            employee.createCell(33).setCellValue("00004");
            employee.createCell(34).setCellValue("00009");
            employee.createCell(35).setCellValue("00000000001");
            employee.createCell(36).setCellValue("01");
            employee.createCell(15).setCellValue("0341111111/0332222222");
            var supplement = sheet.createRow(3);
            supplement.createCell(13).setCellValue("01 janvier 2015");
            supplement.createCell(14).setCellValue("ENFANT Prénom");
            supplement.createCell(32).setCellValue("MCB");
            supplement.createCell(33).setCellValue("00006");
            supplement.createCell(34).setCellValue("00006");
            supplement.createCell(35).setCellValue("00000000002");
            supplement.createCell(36).setCellValue("02");
            var stream = new ByteArrayOutputStream(); workbook.write(stream);
            var file = new MockMultipartFile("file", "employees.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", stream.toByteArray());
            var preview = new EmployeImportController(null, null, null, new ObjectMapper()).preview(file);
            assertEquals(1, preview.size());
            assertEquals("35", preview.get(0).get("matricule"));
            assertEquals(true, preview.get(0).get("exclureIndemniteDeplacement"));
            assertEquals("0340000000", preview.get(0).get("telephone"));
            assertEquals(List.of("0330000000"), preview.get(0).get("telephonesSecondaires"));
            var contact = (Map<?, ?>) ((List<?>) preview.get(0).get("contactsUrgence")).get(0);
            assertEquals("0341111111", contact.get("telephone"));
            assertEquals(List.of("0332222222"), contact.get("telephonesSecondaires"));
            assertEquals(1, ((List<?>) preview.get(0).get("enfants")).size());
            assertEquals(true, ((Map<?, ?>) ((List<?>) preview.get(0).get("enfants")).get(0)).get("aCharge"));
            assertEquals(2, ((List<?>) preview.get(0).get("banques")).size());
            assertEquals("00006", ((Map<?, ?>) ((List<?>) preview.get(0).get("banques")).get(1)).get("codeGuichet"));
            assertEquals("02", ((Map<?, ?>) ((List<?>) preview.get(0).get("banques")).get(1)).get("cleRib"));
            assertEquals(0, preview.get(0).get("comptePrincipalIndex"));
        }
    }

    @Test
    void datesDesRequetesSontConverties() {
        var payload = Map.of("dateNaissance", "1980-01-01", "cinDateDelivrance", "2000-01-01");
        var request = new ObjectMapper().convertValue(payload, EmployeRequest.class);
        assertEquals("1980-01-01", request.getDateNaissance().toString());
        assertEquals("2000-01-01", request.getCinDateDelivrance().toString());
    }

    @Test
    void commitUtiliseBanquePrincipaleEtRetourneSeulementLesMocksRestants() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var duplicateQuery = mock(PreparedStatement.class);
        var bankQuery = mock(PreparedStatement.class);
        var duplicates = mock(ResultSet.class);
        var bank = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT 1 FROM employe WHERE matricule = ? OR cin_numero = ?")).thenReturn(duplicateQuery);
        when(connection.prepareStatement("SELECT id_banque FROM ref_banque WHERE code_banque = ? AND est_actif = TRUE")).thenReturn(bankQuery);
        when(duplicateQuery.executeQuery()).thenReturn(duplicates);
        when(bankQuery.executeQuery()).thenReturn(bank);
        when(bank.next()).thenReturn(true);
        when(bank.getInt(1)).thenReturn(4);
        var service = mock(EmployeService.class);
        var mapper = new ObjectMapper();
        var input = Map.ofEntries(
                Map.entry("matricule", "35"), Map.entry("nom", "Nom"), Map.entry("prenom", "Prénom"),
                Map.entry("sexe", "F"), Map.entry("cinNumero", "123456789012"),
                Map.entry("dateNaissance", "1980-01-01"), Map.entry("lieuNaissance", "Antananarivo"),
                Map.entry("cinDateDelivrance", "2000-01-01"), Map.entry("cinLieuDelivrance", "Antananarivo"),
                Map.entry("adresse", "Adresse"), Map.entry("telephone", "00000000"),
                Map.entry("telephonesSecondaires", List.of("0330000000")),
                Map.entry("mockFields", List.of("dateNaissance", "telephone")),
                Map.entry("exclureIndemniteDeplacement", true),
                Map.entry("enfants", List.of()), Map.entry("contactsUrgence", List.of(Map.of(
                        "nom", "A COMPLETER PAR DAF", "prenom", "Nom confirmé", "telephone", "0340000000",
                        "telephonesSecondaires", List.of("0320000000")))),
                Map.entry("banques", List.of(Map.of("codeBanque", "00004", "codeGuichet", "00009", "numeroCompte", "00000000001", "cleRib", "01"),
                        Map.of("codeBanque", "00006", "codeGuichet", "00006", "numeroCompte", "00000000002", "cleRib", "02"))),
                Map.entry("comptePrincipalIndex", 0));
        var response = new EmployeImportController(service, null, dataSource, mapper)
                .commit(List.of(mapper.valueToTree(input))).getBody();
        assertNotNull(response);
        assertEquals(5, ((List<?>) response.get("corrections")).size());
        assertEquals(List.of("35"), response.get("exclusionsDeplacement"));
        verify(bankQuery).setString(1, "00004");
        verify(service).creerEmployesInitiaux(org.mockito.ArgumentMatchers.argThat(requests ->
                requests.size() == 1 && requests.get(0).getCompteBanquePrincipal().getIdBanque() == 4
                        && requests.get(0).getTelephonesSecondaires().equals(List.of("0330000000"))
                        && requests.get(0).getContactsUrgence().get(0).getTelephonesSecondaires().equals(List.of("0320000000"))
                        && "00004000090000000000101".equals(requests.get(0).getCompteBanquePrincipal().getNumCompteRib())
                        && "00009".equals(requests.get(0).getCompteBanquePrincipal().getCodeGuichet())
                        && "00000000001".equals(requests.get(0).getCompteBanquePrincipal().getNumeroCompte())
                        && "01".equals(requests.get(0).getCompteBanquePrincipal().getCleRib())),
                org.mockito.ArgumentMatchers.eq(java.util.Set.of("35")));
    }
}
