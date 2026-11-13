package mg.galaxam.sirh.contrat.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.application.EmployeService;
import mg.galaxam.sirh.contrat.application.WorkflowRhService;
import mg.galaxam.sirh.contrat.api.dto.TransitionCarriereRequest.EmbaucheInitialeRequest;
import mg.galaxam.sirh.securite.auth.CustomUserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;

/** Import initial des dossiers employés, avec embauche activable si complète. */
@RestController
@RequestMapping("/employes/import")
@PreAuthorize("(hasAuthority('EMPLOYE_WRITE') and hasAuthority('PAIE_WRITE')) or hasAuthority('ROLE_ADMIN')")
public class EmployeImportController {
    private static final String MOCK_TEXT = "A COMPLETER PAR DAF";
    private static final Set<String> DEPLACEMENT_EXCLUS_INITIAUX = Set.of("1", "35", "55", "135");
    private final EmployeService employeService;
    private final WorkflowRhService workflowRhService;
    private final DataSource dataSource;
    private final ObjectMapper mapper;

    public EmployeImportController(EmployeService employeService, WorkflowRhService workflowRhService, DataSource dataSource, ObjectMapper mapper) {
        this.employeService = employeService;
        this.workflowRhService = workflowRhService;
        this.dataSource = dataSource;
        this.mapper = mapper;
    }

    @PostMapping("/preview")
    public List<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty() || file.getSize() > 5_000_000 ||
                file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("Classeur XLSX requis (5 Mo maximum).");
        }
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.FRANCE);
            Row header = sheet.getRow(1);
            if (header == null || !"Mle".equalsIgnoreCase(value(header, 1, formatter))
                    || !"CIN".equalsIgnoreCase(value(header, 6, formatter))) {
                throw new IllegalArgumentException("Colonnes du classeur non reconnues (Mle et CIN attendus en ligne 2).");
            }
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, Object> current = null;
            Set<String> matricules = new HashSet<>();
            for (int rowNumber = 2; rowNumber <= sheet.getLastRowNum(); rowNumber++) {
                Row row = sheet.getRow(rowNumber);
                if (row == null) continue;
                String matricule = value(row, 1, formatter);
                if (!matricule.isBlank()) {
                    if (!matricules.add(matricule)) throw new IllegalArgumentException("Matricule répété : " + matricule);
                    Map<String, Object> employee = new LinkedHashMap<>();
                    employee.put("matricule", matricule);
                    employee.put("exclureIndemniteDeplacement", DEPLACEMENT_EXCLUS_INITIAUX.contains(matricule));
                    employee.put("sexe", value(row, 2, formatter));
                    employee.put("nom", value(row, 3, formatter));
                    employee.put("prenom", value(row, 4, formatter));
                    employee.put("cinNumero", value(row, 6, formatter));
                    List<String> mockFields = new ArrayList<>();
                    for (Map.Entry<String, Integer> required : Map.of(
                            "dateNaissance", 17, "lieuNaissance", 18, "cinDateDelivrance", 7,
                            "cinLieuDelivrance", 8, "adresse", 10, "telephone", 11).entrySet()) {
                        if (value(row, required.getValue(), formatter).isBlank()) mockFields.add(required.getKey());
                    }
                    employee.put("mockFields", mockFields);
                    employee.put("dateNaissance", date(row, 17, formatter, "1980-01-01"));
                    employee.put("lieuNaissance", fallback(value(row, 18, formatter), MOCK_TEXT));
                    employee.put("cinDateDelivrance", date(row, 7, formatter, "2000-01-01"));
                    employee.put("cinLieuDelivrance", fallback(value(row, 8, formatter), MOCK_TEXT));
                    employee.put("cinDuplicataDate", date(row, 9, formatter, ""));
                    employee.put("adresse", fallback(value(row, 10, formatter), MOCK_TEXT));
                    String phone = value(row, 11, formatter);
                    List<String> phones = splitPhones(phone);
                    employee.put("telephone", phones.isEmpty() ? "00000000" : phones.get(0));
                    employee.put("telephonesSecondaires", phones.stream().skip(1).toList());
                    employee.put("numAffiliationCnaps", value(row, 16, formatter).replaceAll("\\s+", ""));
                    Map<String, String> embauche = new LinkedHashMap<>();
                    embauche.put("dateDebut", date(row, 5, formatter, ""));
                    embauche.put("dateSignature", date(row, 5, formatter, ""));
                    embauche.put("typeContrat", value(row, 19, formatter));
                    embauche.put("typeIntervenant", "Salarié permanent");
                    embauche.put("site", switch (value(row, 21, formatter).toUpperCase(Locale.ROOT)) {
                        case "TANA" -> "TNR";
                        case "TAMATAVE" -> "TMM";
                        default -> "";
                    });
                    embauche.put("filiale", "GLX");
                    embauche.put("departement", "Logistiques");
                    embauche.put("service", "");
                    embauche.put("fonction", "");
                    embauche.put("serviceSource", value(row, 22, formatter));
                    embauche.put("fonctionSource", value(row, 23, formatter));
                    embauche.put("categorie", fallback(value(row, 24, formatter), "AGENT"));
                    embauche.put("modeCalculPaie", value(row, 25, formatter));
                    embauche.put("salaireBase", value(row, 37, formatter));
                    embauche.put("categorieAConfirmer", value(row, 24, formatter).isBlank() ? "true" : "false");
                    employee.put("embauche", embauche);
                    employee.put("enfants", new ArrayList<Map<String, Object>>());
                    employee.put("nombreEnfantsDeclare", value(row, 12, formatter));
                    employee.put("contactsUrgence", new ArrayList<Map<String, Object>>());
                    String emergency = value(row, 15, formatter);
                    if (!emergency.isBlank()) {
                        List<String> emergencyPhones = splitPhones(emergency);
                        ((List<Map<String, Object>>) employee.get("contactsUrgence")).add(new LinkedHashMap<>(Map.of(
                                "nom", MOCK_TEXT, "prenom", MOCK_TEXT, "telephone", emergencyPhones.get(0),
                                "telephonesSecondaires", emergencyPhones.stream().skip(1).toList())));
                    }
                    employee.put("banques", new ArrayList<Map<String, String>>());
                    employee.put("comptePrincipalIndex", 0);
                    result.add(employee);
                    current = employee;
                }
                if (current == null) continue;
                String childName = value(row, 14, formatter);
                String childDob = date(row, 13, formatter, "");
                if (!childName.isBlank() || !childDob.isBlank()) {
                    String[] parts = childName.trim().split("\\s+", 2);
                    ((List<Map<String, Object>>) current.get("enfants")).add(new LinkedHashMap<>(Map.of(
                            "nom", parts.length > 0 ? parts[0] : "", "prenom", parts.length > 1 ? parts[1] : "",
                            "dateNaissance", childDob, "aCharge", true)));
                }
                String bankCode = value(row, 33, formatter);
                if (!bankCode.isBlank()) {
                    ((List<Map<String, String>>) current.get("banques")).add(new LinkedHashMap<>(Map.of(
                            "nomBanque", value(row, 32, formatter), "codeBanque", bankCode,
                            "codeGuichet", value(row, 34, formatter), "numeroCompte", value(row, 35, formatter),
                            "cleRib", value(row, 36, formatter))));
                }
            }
            if (result.isEmpty() || result.size() > 500) throw new IllegalArgumentException("Nombre de salariés invalide.");
            return result;
        }
    }

    @PostMapping("/commit")
    public ResponseEntity<Map<String, Object>> commit(@RequestBody List<JsonNode> rows) throws Exception {
        return commitRows(rows, false);
    }

    @PostMapping("/commit-with-contracts")
    @PreAuthorize("(hasAuthority('EMPLOYE_WRITE') and hasAuthority('PAIE_WRITE') and hasAuthority('EMPLOYE_VALIDATE')) or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Map<String, Object>> commitWithContracts(@RequestBody List<JsonNode> rows) throws Exception {
        return commitRows(rows, true);
    }

    private ResponseEntity<Map<String, Object>> commitRows(List<JsonNode> rows, boolean withContracts) throws Exception {
        if (rows == null || rows.isEmpty() || rows.size() > 500) throw new IllegalArgumentException("Lot vide ou trop volumineux.");
        Set<String> seen = new HashSet<>();
        List<Map<String, String>> corrections = new ArrayList<>();
        List<String> created = new ArrayList<>();
        Map<String, Integer> employeeIds = new LinkedHashMap<>();
        Set<String> exclusionsDeplacement = new HashSet<>();
        // Prévalidation complète avant toute écriture.
        List<EmployeRequest> requests = new ArrayList<>();
        Map<String, EmbaucheInitialeRequest> embauches = new HashMap<>();
        try (Connection c = dataSource.getConnection()) {
            for (JsonNode row : rows) {
                String matricule = row.path("matricule").asText("").trim();
                if (matricule.isEmpty() || !seen.add(matricule)) throw new IllegalArgumentException("Matricule vide ou répété : " + matricule);
                if (row.path("exclureIndemniteDeplacement").asBoolean(false)) exclusionsDeplacement.add(matricule);
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM employe WHERE matricule = ? OR cin_numero = ?")) {
                    ps.setString(1, matricule); ps.setString(2, row.path("cinNumero").asText());
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) throw new IllegalArgumentException("Salarié déjà présent : " + matricule); }
                }
                Map<String, Object> payload = new HashMap<>();
                for (String field : List.of("matricule", "nom", "prenom", "sexe", "dateNaissance", "lieuNaissance",
                        "cinNumero", "cinDateDelivrance", "cinLieuDelivrance", "cinDuplicataDate", "adresse", "telephone", "numAffiliationCnaps")) {
                    if (row.hasNonNull(field)) payload.put(field, row.get(field).asText());
                }
                payload.put("telephonesSecondaires", phoneArray(row.path("telephonesSecondaires")));
                List<Map<String, Object>> children = new ArrayList<>();
                for (JsonNode child : row.path("enfants")) {
                    children.add(Map.of("nom", child.path("nom").asText(), "prenom", child.path("prenom").asText(),
                            "dateNaissance", child.path("dateNaissance").asText(),
                            "aCharge", child.path("aCharge").asBoolean(true)));
                }
                payload.put("enfants", children);
                List<Map<String, Object>> contacts = new ArrayList<>();
                for (JsonNode contact : row.path("contactsUrgence")) {
                    contacts.add(Map.of("nom", contact.path("nom").asText(), "prenom", contact.path("prenom").asText(),
                            "telephone", contact.path("telephone").asText(),
                            "telephonesSecondaires", phoneArray(contact.path("telephonesSecondaires"))));
                }
                payload.put("contactsUrgence", contacts);
                JsonNode banks = row.path("banques");
                if (banks.isArray() && !banks.isEmpty()) {
                    int index = row.path("comptePrincipalIndex").asInt(0);
                    if (index < 0 || index >= banks.size()) throw new IllegalArgumentException("Compte bancaire principal invalide : " + matricule);
                    JsonNode bank = banks.get(index);
                    try (PreparedStatement ps = c.prepareStatement("SELECT id_banque FROM ref_banque WHERE code_banque = ? AND est_actif = TRUE")) {
                        ps.setString(1, bank.path("codeBanque").asText());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Banque absente du référentiel : " + bank.path("codeBanque").asText());
                            String rib = bank.path("codeBanque").asText() + bank.path("codeGuichet").asText()
                                    + bank.path("numeroCompte").asText() + bank.path("cleRib").asText();
                            payload.put("compteBanquePrincipal", Map.of("idBanque", rs.getInt(1), "numCompteRib", rib,
                                    "codeGuichet", bank.path("codeGuichet").asText(),
                                    "numeroCompte", bank.path("numeroCompte").asText(),
                                    "cleRib", bank.path("cleRib").asText()));
                        }
                    }
                }
                EmployeRequest request = mapper.convertValue(payload, EmployeRequest.class);
                requests.add(request);
                if (withContracts) embauches.put(matricule, parseEmbauche(row.path("embauche"), matricule));
                for (String field : List.of("dateNaissance", "lieuNaissance", "cinDateDelivrance", "cinLieuDelivrance", "adresse", "telephone")) {
                    String val = row.path(field).asText("");
                    if (containsText(row.path("mockFields"), field) &&
                            (val.equals(MOCK_TEXT) || val.equals("00000000") ||
                                    (field.equals("dateNaissance") && val.equals("1980-01-01")) ||
                                    (field.equals("cinDateDelivrance") && val.equals("2000-01-01")))) {
                        corrections.add(Map.of("matricule", matricule, "champ", field, "valeurProvisoire", val));
                    }
                }
                for (JsonNode contact : row.path("contactsUrgence")) {
                    for (String field : List.of("nom", "prenom")) if (MOCK_TEXT.equals(contact.path(field).asText()))
                        corrections.add(Map.of("matricule", matricule, "champ", "contactUrgence." + field, "valeurProvisoire", MOCK_TEXT));
                    if (contact.path("telephonesSecondaires").isArray() && !contact.path("telephonesSecondaires").isEmpty())
                        corrections.add(Map.of("matricule", matricule, "champ", "contactUrgence.numerosAConfirmer",
                                "valeurProvisoire", contact.path("telephone").asText() + "/" + contact.path("telephonesSecondaires").get(0).asText()));
                }
                if (exclusionsDeplacement.contains(matricule)) {
                    corrections.add(Map.of("matricule", matricule, "champ", "exclusionIndemniteDeplacement",
                            "valeurProvisoire", "OUI - A VERIFIER PAR DAF"));
                }
            }
        }
        if (withContracts) {
            Integer userId = currentUserId();
            employeService.creerEmployesInitiaux(requests, exclusionsDeplacement, (employe, c) -> {
                employeeIds.put(employe.getMatricule(), employe.getIdEmploye());
                EmbaucheInitialeRequest embauche = embauches.get(employe.getMatricule());
                var workflow = workflowRhService.creerBrouillonEmbauche(employe.getIdEmploye(), embauche, userId, c);
                workflowRhService.validerWorkflow(workflow.getIdWorkflow(), null, userId, c);
            });
        } else {
            employeService.creerEmployesInitiaux(requests, exclusionsDeplacement).forEach(employe ->
                    employeeIds.put(employe.getMatricule(), employe.getIdEmploye()));
        }
        for (EmployeRequest request : requests) created.add(request.getMatricule());
        return ResponseEntity.ok(Map.of("created", created, "employeeIds", employeeIds, "corrections", corrections,
                "exclusionsDeplacement", exclusionsDeplacement.stream().sorted().toList(),
                "contratsActives", withContracts ? created.size() : 0));
    }

    private EmbaucheInitialeRequest parseEmbauche(JsonNode row, String matricule) {
        if (!row.isObject()) throw new IllegalArgumentException("Embauche absente pour le matricule " + matricule);
        Map<String, Object> values = new HashMap<>();
        for (String key : List.of("idFiliale", "idTypeContrat", "idTypeIntervenant", "dateDebut", "dateFin",
                "dateSignature", "motifCddCode", "dureeEssaiMois", "dateFinEssai", "comptabiliseEffectif",
                "numeroContrat", "idSite", "idDepartement", "idService", "idFonction",
                "idResponsableHierarchique", "idCategorieProfessionnelle", "idModeCalculPaie", "salaireBase")) {
            if (row.hasNonNull(key) && !row.get(key).asText().isBlank()) values.put(key, row.get(key).isBoolean() ? row.get(key).asBoolean() : row.get(key).asText());
        }
        EmbaucheInitialeRequest req = mapper.convertValue(values, EmbaucheInitialeRequest.class);
        if (req.getDateDebut() == null || req.getIdFiliale() == null || req.getIdTypeContrat() == null
                || req.getIdTypeIntervenant() == null || req.getIdSite() == null || req.getIdDepartement() == null
                || req.getIdService() == null || req.getIdFonction() == null || req.getIdCategorieProfessionnelle() == null
                || req.getIdModeCalculPaie() == null || req.getSalaireBase() == null
                || req.getSalaireBase().signum() <= 0) {
            throw new IllegalArgumentException("Embauche incomplète pour le matricule " + matricule);
        }
        if (req.getDateDebut().toLocalDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Date d'embauche future pour le matricule " + matricule + " : activation immédiate impossible.");
        }
        req.setDateEffet(req.getDateDebut());
        req.setCodeTypeEvenement("EMBAUCHE_INITIALE");
        return req;
    }

    private Integer currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) return details.getIdUtilisateur();
        return null;
    }

    private static String value(Row row, int index, DataFormatter formatter) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return formatter.formatCellValue(cell).trim();
    }
    private static String fallback(String value, String replacement) { return value.isBlank() ? replacement : value; }
    private static List<String> splitPhones(String raw) {
        return java.util.Arrays.stream(raw.split("/"))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }
    private static List<String> phoneArray(JsonNode node) {
        List<String> phones = new ArrayList<>();
        if (node.isArray()) for (JsonNode value : node) if (!value.asText().isBlank()) phones.add(value.asText().trim());
        return phones;
    }
    private static boolean containsText(JsonNode array, String value) {
        for (JsonNode item : array) if (value.equals(item.asText())) return true;
        return false;
    }
    private static String date(Row row, int index, DataFormatter formatter, String missing) {
        Cell cell = row.getCell(index);
        if (cell == null || value(row, index, formatter).isBlank()) return missing;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().toLocalDate().toString();
        String raw = value(row, index, formatter).trim();
        if (raw.matches("\\d{4}-\\d{2}-\\d{2}.*")) return raw.substring(0, 10);
        String normalized = Normalizer.normalize(raw.toLowerCase(Locale.FRANCE), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim();
        String[] parts = normalized.split(" ");
        if (parts.length == 3) {
            List<String> months = List.of("janvier", "fevrier", "mars", "avril", "mai", "juin", "juillet", "aout", "septembre", "octobre", "novembre", "decembre");
            int month = months.indexOf(parts[1]);
            if (month >= 0) return LocalDate.of(Integer.parseInt(parts[2]), month + 1, Integer.parseInt(parts[0])).toString();
        }
        throw new IllegalArgumentException("Date non reconnue à la ligne " + (row.getRowNum() + 1) + " : " + raw);
    }
}
