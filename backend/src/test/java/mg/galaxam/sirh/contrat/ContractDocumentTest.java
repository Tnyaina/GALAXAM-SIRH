package mg.galaxam.sirh.contrat;

import mg.galaxam.sirh.contrat.application.DocumentRhService;
import mg.galaxam.sirh.contrat.api.dto.*;
import mg.galaxam.sirh.contrat.persistence.*;
import mg.galaxam.sirh.common.exception.*;
import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContractDocumentTest {
    DocumentRhService service;
    MockEnvironment env;
    Map<String, Object> contract, employee, assignment;
    String type = "CDI";
    String mode = "MENSUEL";
    BigDecimal dureeHebdoReference = new BigDecimal("40.00");
    String adresseSiege = "3e étage, Tour Forello, Tanjombato";
    String rcs;
    String initialAssignmentQuery;

    @BeforeEach void setup() {
        service = new DocumentRhService(); env = new MockEnvironment();
        ReflectionTestUtils.setField(service, "environment", env);
        contract = new HashMap<>(Map.of("id_contrat", 42, "id_employe", 7, "id_filiale", 3, "id_type_contrat", 1,
                "id_statut_rh", 1, "numero_contrat", "CTR-2026-0042", "date_debut", java.sql.Date.valueOf("2026-10-01"), "duree_essai_mois", 0));
        contract.put("description", "Missions du contrat initial");
        employee = new HashMap<>(Map.of("id_employe", 7, "nom", "Rakoto", "prenom", "Jean", "matricule", "EMP-7", "sexe", "M",
                "date_naissance", java.sql.Date.valueOf("1990-01-01"), "lieu_naissance", "Toamasina", "adresse", "Adresse employé",
                "cin_numero", "123456789012", "cin_date_delivrance", java.sql.Date.valueOf("2010-01-01")));
        employee.put("cin_lieu_delivrance", "Toamasina");
        assignment = new HashMap<>(Map.of("id_affectation", 10, "id_contrat", 42, "id_employe", 7, "id_fonction", 1,
                "id_site", 1, "id_departement", 1, "id_categorie_professionnelle", 1, "id_mode_calcul_paie", 1, "salaire_base", new BigDecimal("950000")));
        for (String key : List.of("ENTREPRISE_ADRESSE", "SIGNATAIRE_NOM", "SIGNATAIRE_FONCTION", "SIGNATAIRE_VILLE"))
            env.withProperty("app.documents.contrats.filiales.3." + key, "Valeur validée " + key);
        for (String key : List.of("TEMPS_TRAVAIL", "STAGE_ETABLISSEMENT", "STAGE_GRATIFICATION", "PRESTATION_MONTANT", "PRESTATION_MODALITES_PAIEMENT"))
            env.withProperty("app.documents.contrats.contrats.42." + key, "Valeur validée " + key);
    }

    ResultSet row(Map<String, Object> values) throws Exception {
        var lastWasNull = new java.util.concurrent.atomic.AtomicBoolean();
        ResultSet rs = mock(ResultSet.class, invocation -> {
            String method = invocation.getMethod().getName();
            if (method.equals("wasNull")) return lastWasNull.get();
            if (method.startsWith("get") && invocation.getArguments().length > 0) {
                Object value = values.get(String.valueOf((Object) invocation.getArgument(0)));
                lastWasNull.set(value == null);
                if (method.equals("getInt")) return value == null ? 0 : ((Number)value).intValue();
                if (method.equals("getBoolean")) return value != null && Boolean.TRUE.equals(value);
                return value;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        when(rs.next()).thenReturn(!values.isEmpty(), false); return rs;
    }

    @Test void dafActiveSuppliesCompanySignatory() throws Exception {
        Connection c = db();
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet dafRow = row(Map.of("nom", "Rakoto Marie", "fonction", "DAF"));
        when(ps.executeQuery()).thenReturn(dafRow);
        when(c.prepareStatement(startsWith("SELECT DISTINCT e.id_employe"))).thenReturn(ps);
        var prepared = service.preparerContrat(42, c);
        assertEquals("Rakoto Marie", prepared.variables().get("{{SIGNATAIRE_NOM}}"));
        assertEquals("DAF", prepared.variables().get("{{SIGNATAIRE_FONCTION}}"));
        verify(ps).setInt(1, (Integer) contract.get("id_filiale"));
    }

    @Test void ambiguousDafDoesNotSelectAnArbitraryPerson() throws Exception {
        Connection c = db();
        PreparedStatement ps = mock(PreparedStatement.class);
        ResultSet rs = row(Map.of("nom", "Une personne", "fonction", "DAF"));
        when(rs.next()).thenReturn(true, true, false);
        when(ps.executeQuery()).thenReturn(rs);
        when(c.prepareStatement(startsWith("SELECT DISTINCT e.id_employe"))).thenReturn(ps);
        env.setProperty("app.documents.contrats.filiales.1.SIGNATAIRE_NOM", "");
        var result = service.preparerContrat(42, c);
        assertNotEquals("Une personne", result.variables().get("{{SIGNATAIRE_NOM}}"));
    }

    Connection db() throws Exception {
        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0); Map<String, Object> values;
            if (sql.startsWith("SELECT * FROM contrat_travail")) values = contract;
            else if (sql.startsWith("INSERT INTO document_rh")) values = Map.of("1",12);
            else if (sql.startsWith("SELECT d.*")) values = Map.of("id_document",12,"id_employe",7,"id_type_document",6,"id_contrat",42,"id_workflow",8,"numero_contrat","CTR-2026-0042","contrat_type_code","CDI","type_document_libelle","Contrat de travail");
            else if (sql.startsWith("SELECT * FROM employe_affectation_carriere")) values = assignment;
            else if (sql.startsWith("SELECT * FROM employe ")) values = employee;
            else if (sql.startsWith("SELECT a.id_affectation")) { initialAssignmentQuery = sql; values = assignment.isEmpty() ? Map.of() : Map.of("1", 10); }
            else if (sql.contains("FROM ref_type_contrat")) values = Map.of("1", type);
            else if (sql.startsWith("SELECT id_statut_rh")) values = Map.of("1", sql.contains("ACTIF") ? 4 : sql.contains("A_SIGNER") ? 3 : 2);
            else if (sql.contains("FROM ref_statut_rh")) values = Map.of("1", "BROUILLON");
            else if (sql.contains("FROM ref_type_document")) values = Map.of("id_type_document", 6, "libelle", "Contrat de travail", "actif", true);
            else if (sql.startsWith("SELECT * FROM workflow_rh")) values = Map.of("id_workflow", 8, "id_employe", 7, "id_contrat_cible", 42);
            else if (sql.contains("FROM ref_mode_calcul_paie")) values = Map.of("1", sql.startsWith("SELECT code ") ? mode : "Libellé de paie modifiable");
            else if (sql.contains("FROM paie_config_legale")) values = dureeHebdoReference == null
                    ? Map.of() : Map.of("duree_hebdo_legale", dureeHebdoReference);
            else if (sql.contains("FROM ref_filiale")) values = sql.startsWith("SELECT adresse_siege ")
                    ? (adresseSiege == null ? Map.of() : Map.of("1", adresseSiege))
                    : sql.startsWith("SELECT rcs ") ? (rcs == null ? Map.of() : Map.of("1", rcs))
                    : Map.of("1", "Entreprise validée");
            else values = Map.of("1", "Valeur historique");
            PreparedStatement ps = mock(PreparedStatement.class); ResultSet rs = row(values); when(ps.executeQuery()).thenReturn(rs); return ps;
        });
        return c;
    }

    @ParameterizedTest @ValueSource(strings={"CDI", "CDD", "STAGE", "PRESTATION", "TEMPORAIRE"})
    void prepareDraftAndGenerateValidDocx(String code) throws Exception {
        type = code; if (!"CDI".equals(code)) contract.put("date_fin", java.sql.Date.valueOf("2027-03-31"));
        var dto = service.preparerContrat(42, db());
        assertEquals(code, dto.codeTypeContrat()); assertEquals("BROUILLON", dto.statut());
        assertEquals("Contrat_" + code + "_modele.docx", dto.template()); assertTrue(dto.isGenerationPossible(), dto.donneesManquantes().toString());
        if (!"PRESTATION".equals(code)) assertEquals("40 heures par semaine", dto.variables().get("{{TEMPS_TRAVAIL}}"));
        assertEquals(42, dto.affectation().getIdContrat());
        assertTrue(initialAssignmentQuery.contains("ORDER BY a.date_debut, a.id_affectation"));
        assertEquals("Valeur historique", dto.variables().get("{{POSTE_FONCTION}}"));
        assertFalse(dto.paragraphes().stream().anyMatch(p -> p.contains("{{")));
        ByteArrayOutputStream out = new ByteArrayOutputStream(); service.genererContratDocx(dto, out);
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(out.toByteArray()))) {
            String text = doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("", String::concat);
            assertTrue(text.contains("Rakoto Jean")); assertTrue(text.contains("CTR-2026-0042"));
            assertEquals("Title", doc.getParagraphs().stream().filter(p -> p.getText().startsWith("Contrat de") || p.getText().startsWith("Convention de")).findFirst().orElseThrow().getStyle());
            assertFalse(doc.getAllPictures().isEmpty(), "Le logo doit être conservé à la génération");
            assertTrue(text.contains("Canevas interne")); assertFalse(text.contains("{{")); assertFalse(text.contains("}}"));
            assertTrue(text.contains("["), "Les zones de clauses restent dans le canevas");
        }
    }
    @ParameterizedTest @ValueSource(strings={"HORAIRE", "JOURNALIER", "MENSUEL"})
    void temporaryRemunerationUsesStoredAmountAndModeCode(String modeCode) throws Exception {
        type="TEMPORAIRE"; mode=modeCode; contract.put("date_fin",java.sql.Date.valueOf("2027-01-31"));
        assignment.put("salaire_base",new BigDecimal("12500.50"));
        String unit=Map.of("HORAIRE","par heure","JOURNALIER","par jour","MENSUEL","par mois").get(modeCode);
        var dto=service.preparerContrat(42,db());
        assertEquals(unit,dto.variables().get("{{TEMPORAIRE_UNITE_REMUNERATION}}"));
        assertTrue(dto.variables().get("{{REMUNERATION_BASE}}").contains("12"));
        assertTrue(dto.variables().get("{{REMUNERATION_BASE}}").endsWith("500,50 Ar " + unit));
        assertEquals(new BigDecimal("12500.50"),dto.affectation().getSalaireBase());
        assertEquals("Missions du contrat initial",dto.variables().get("{{TEMPORAIRE_MOTIF}}"));
        var output=new ByteArrayOutputStream();service.genererContratDocx(dto,output);
        try(var doc=new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            String text=doc.getParagraphs().stream().map(XWPFParagraph::getText).reduce("",String::concat);
            assertTrue(text.contains(unit)); assertFalse(text.contains("{{"));
        }
    }
    @Test void temporaryMissingMotifIsExplicitUntilRhDefinesRequirement() throws Exception {
        type="TEMPORAIRE";contract.put("date_fin",java.sql.Date.valueOf("2027-01-31"));contract.remove("description");
        var dto=service.preparerContrat(42,db());assertTrue(dto.isGenerationPossible());
        assertTrue(dto.avertissements().contains("{{TEMPORAIRE_MOTIF}}"));
        assertEquals("Non renseigné — à vérifier avant validation",dto.variables().get("{{TEMPORAIRE_MOTIF}}"));
    }
    @Test void temporaryMissingEndDateBlocksGenerationAndDomainValidation() throws Exception {
        type="TEMPORAIRE";var dto=service.preparerContrat(42,db());assertTrue(dto.donneesManquantes().contains("{{CONTRAT_DATE_FIN}}"));
        assertThrows(SirhBusinessException.class,()->service.genererContratDocx(dto,new ByteArrayOutputStream()));
        var validator=new mg.galaxam.sirh.contrat.domain.ContratValidationEngine();
        assertThrows(IllegalArgumentException.class,()->validator.validerDatesContrat(type,java.sql.Date.valueOf("2026-10-01"),null,0));
        assertDoesNotThrow(()->validator.validerDatesContrat(type,java.sql.Date.valueOf("2026-10-01"),java.sql.Date.valueOf("2026-10-31"),0));
    }
    @Test void temporaryUnknownRemunerationModeDoesNotDefaultToMonthly() throws Exception {
        type="TEMPORAIRE";mode="FORFAIT_INCONNU";contract.put("date_fin",java.sql.Date.valueOf("2027-01-31"));
        assertTrue(service.preparerContrat(42,db()).donneesManquantes().contains("{{TEMPORAIRE_UNITE_REMUNERATION}}"));
    }
    @Test void signedTemporaryUsesSameUploadMechanism(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        type="TEMPORAIRE";uploadPersistsContractAndReturnsLinkedDocument(directory);
    }
    @Test void noTrialSelectedIsNotMissingAndExcludedFieldsAreNotPrinted() throws Exception {
        contract.remove("duree_essai_mois");
        var dto=service.preparerContrat(42,db());
        assertTrue(dto.isGenerationPossible());
        assertEquals("Aucune période d’essai prévue",dto.variables().get("{{CONTRAT_DUREE_ESSAI}}"));
        assertFalse(dto.avertissements().contains("{{ENTREPRISE_RCS}}"));
        assertFalse(dto.avertissements().contains("{{POSTE_SUPERIEUR}}"));
        assertFalse(dto.paragraphes().stream().anyMatch(text->text.startsWith("RCS :") || text.contains("Responsable hiérarchique")));
    }
    @Test void unknownContract() throws Exception { contract.clear(); assertEquals(SirhErrorCode.CONTRAT_NON_TROUVE, assertThrows(SirhBusinessException.class, () -> service.preparerContrat(99, db())).getErrorCode()); }
    @Test void unsupportedType() throws Exception { type="INTERIM_NON_SUPPORTE"; assertEquals(SirhErrorCode.CONTRACT_TEMPLATE_UNSUPPORTED, assertThrows(SirhBusinessException.class, () -> service.preparerContrat(42, db())).getErrorCode()); }
    @Test void missingAssignmentNeverUsesCurrentOne() throws Exception { assignment.clear(); assertEquals(SirhErrorCode.CONTRACT_ASSIGNMENT_MISSING, assertThrows(SirhBusinessException.class, () -> service.preparerContrat(42, db())).getErrorCode()); }
    @Test void missingRequiredDataBlocksDownloadButKeepsPreview() throws Exception {
        adresseSiege = null;
        var dto=service.preparerContrat(42, db()); assertFalse(dto.isGenerationPossible());
        assertTrue(dto.donneesManquantes().contains("{{ENTREPRISE_ADRESSE}}"));
        assertEquals(SirhErrorCode.CONTRACT_DOCUMENT_DATA_MISSING, assertThrows(SirhBusinessException.class, () -> service.genererContratDocx(dto,new ByteArrayOutputStream())).getErrorCode());
    }
    @Test void companyAddressComesFromContractFilialeNotExternalConfiguration() throws Exception {
        env.setProperty("app.documents.contrats.filiales.3.ENTREPRISE_ADRESSE", "Ancienne configuration");
        var dto = service.preparerContrat(42, db());
        assertEquals(adresseSiege, dto.variables().get("{{ENTREPRISE_ADRESSE}}"));
    }
    @Test void rcsIsOmittedWhenUnknownAndPrintedWhenAvailable() throws Exception {
        var without = service.preparerContrat(42, db());
        assertFalse(without.paragraphes().stream().anyMatch(line -> line.contains("RCS :")));
        var output = new ByteArrayOutputStream();
        service.genererContratDocx(without, output);
        try (var doc = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertFalse(doc.getTables().get(0).getRow(0).getCell(1).getText().contains("RCS :"));
        }
        rcs = "RCS vérifié";
        var with = service.preparerContrat(42, db());
        assertTrue(with.paragraphes().stream().anyMatch(line -> line.contains("RCS : RCS vérifié")));
        output.reset();
        service.genererContratDocx(with, output);
        try (var doc = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            assertTrue(doc.getTables().get(0).getRow(0).getCell(1).getText().contains("RCS : RCS vérifié"));
        }
    }
    @Test void manualCompletionKeepsDraftDocxAvailableWithoutInventingValues() throws Exception {
        for (String key : List.of("SIGNATAIRE_NOM", "SIGNATAIRE_FONCTION", "SIGNATAIRE_VILLE"))
            env.setProperty("app.documents.contrats.filiales.3." + key, "");
        env.setProperty("app.documents.contrats.contrats.42.TEMPS_TRAVAIL", "");
        var draft = service.preparerContrat(42, db());
        assertTrue(draft.isGenerationPossible(), draft.donneesManquantes().toString());
        assertEquals(3, draft.avertissements().stream()
                .filter(key -> Set.of("{{SIGNATAIRE_NOM}}", "{{SIGNATAIRE_FONCTION}}", "{{SIGNATAIRE_VILLE}}").contains(key)).count());
        assertEquals("[Nom du signataire à compléter avant signature]", draft.variables().get("{{SIGNATAIRE_NOM}}"));
        assertEquals("40 heures par semaine", draft.variables().get("{{TEMPS_TRAVAIL}}"));
        var output = new ByteArrayOutputStream();
        service.genererContratDocx(draft, output);
        assertTrue(output.size() > 0);
    }
    @Test void timeReferenceIgnoresOldContractPropertyAndReadsEffectiveConfiguration() throws Exception {
        env.setProperty("app.documents.contrats.contrats.42.TEMPS_TRAVAIL", "35 heures par semaine");
        dureeHebdoReference = new BigDecimal("39.50");
        var dto = service.preparerContrat(42, db());
        assertEquals("39.5 heures par semaine", dto.variables().get("{{TEMPS_TRAVAIL}}"));
    }
    @Test void missingEffectiveTimeConfigurationFailsExplicitly() throws Exception {
        dureeHebdoReference = null;
        assertEquals(SirhErrorCode.LEGAL_CONFIG_MISSING,
                assertThrows(SirhBusinessException.class, () -> service.preparerContrat(42, db())).getErrorCode());
    }
    @Test void missingEmployeeIdentityBlocksDocx() throws Exception { employee.remove("cin_numero"); assertTrue(service.preparerContrat(42, db()).donneesManquantes().contains("{{EMPLOYE_CIN}}")); }
    @Test void unknownAndResidualVariablesFail() throws Exception {
        var dto=service.preparerContrat(42, db()); var vars=new HashMap<>(dto.variables()); vars.remove("{{EMPLOYE_CIN}}");
        var bad=new ContratTravailDocumentDTO(dto.idContrat(),dto.idEmploye(),dto.numeroContrat(),dto.codeTypeContrat(),dto.statut(),dto.template(),dto.contrat(),dto.affectation(),dto.employe(),vars,List.of(),List.of(),List.of());
        assertEquals(SirhErrorCode.CONTRACT_TEMPLATE_INVALID, assertThrows(SirhBusinessException.class, () -> service.genererContratDocx(bad,new ByteArrayOutputStream())).getErrorCode());
        vars.put("{{EMPLOYE_CIN}}", "{{INCONNU}}");
        assertEquals(SirhErrorCode.CONTRACT_TEMPLATE_INVALID, assertThrows(SirhBusinessException.class, () -> service.genererContratDocx(bad,new ByteArrayOutputStream())).getErrorCode());
    }
    @Test void signedUploadRequiresContractAndMatchingOwner() throws Exception {
        assertThrows(SirhBusinessException.class, () -> DocumentRhService.validerLiaisonsDocument(7,6,null,null,db()));
        assertThrows(SirhBusinessException.class, () -> DocumentRhService.validerLiaisonsDocument(9,6,42,null,db()));
        assertDoesNotThrow(() -> DocumentRhService.validerLiaisonsDocument(7,6,42,8,db()));
        assertThrows(SirhBusinessException.class, () -> DocumentRhService.validerLiaisonsDocument(9,6,42,8,db()));
    }
    @Test void documentLinksAreMappedAndPersisted() throws Exception {
        var values=new HashMap<String,Object>(Map.of("id_document",12,"id_employe",7,"id_type_document",6,"id_contrat",42,"id_workflow",8,"taille_octet",100L));
        var doc=DocumentRh.mapResultSet(row(values)); assertEquals(42,doc.getIdContrat()); assertEquals(8,doc.getIdWorkflow());
        values.put("numero_contrat","CTR-2026-0042"); values.put("contrat_type_code","CDD"); values.put("type_document_libelle","Contrat de travail");
        var view=DocumentRhCPL.mapResultSet(row(values)); assertTrue(view.isContratSigne()); assertEquals("CDD",view.getCodeTypeContrat());
        Connection c=db(); doc.setNomOriginal("signe.pdf"); doc.setCheminFichier("/uploads/documents/signe.pdf"); doc.update(c);
        doc.save(c);
        var captor=org.mockito.ArgumentCaptor.forClass(String.class); verify(c,atLeastOnce()).prepareStatement(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(sql -> sql.startsWith("UPDATE document_rh") && sql.contains("id_contrat = ?") && sql.contains("id_workflow = ?")));
    }
    @Test void futureHiringUsesExistingPlanningWithoutSignedDocument() throws Exception {
        var workflow=new WorkflowRh(); workflow.setIdWorkflow(8); workflow.setIdEmploye(7); workflow.setIdContratCible(42); workflow.setIdAffectationCible(10);
        workflow.setDateEffet(java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(15)));
        Connection c=db();
        ReflectionTestUtils.invokeMethod(new mg.galaxam.sirh.contrat.application.WorkflowRhService(),"validerEmbaucheInitiale",workflow,null,1,c);
        var sql=org.mockito.ArgumentCaptor.forClass(String.class); verify(c,atLeastOnce()).prepareStatement(sql.capture());
        assertTrue(sql.getAllValues().stream().anyMatch(query->query.startsWith("UPDATE workflow_rh SET id_statut_rh = ?")));
        assertTrue(sql.getAllValues().stream().anyMatch(query->query.startsWith("UPDATE contrat_travail SET id_statut_rh = ?")));
        assertFalse(sql.getAllValues().stream().anyMatch(query->query.contains("UPDATE employe SET est_actif") || query.contains("UPDATE employe_affectation_carriere")));
        assertFalse(sql.getAllValues().stream().anyMatch(query->query.contains("document_rh")));
    }
    @Test void immediateHiringKeepsActivationWithoutSignedDocument() throws Exception {
        var workflow=new WorkflowRh(); workflow.setIdWorkflow(8); workflow.setIdEmploye(7); workflow.setIdContratCible(42); workflow.setIdAffectationCible(10);
        workflow.setDateEffet(java.sql.Date.valueOf(java.time.LocalDate.now()));
        Connection c=db();
        ReflectionTestUtils.invokeMethod(new mg.galaxam.sirh.contrat.application.WorkflowRhService(),"validerEmbaucheInitiale",workflow,null,1,c);
        var sql=org.mockito.ArgumentCaptor.forClass(String.class); verify(c,atLeastOnce()).prepareStatement(sql.capture());
        assertTrue(sql.getAllValues().stream().anyMatch(query->query.startsWith("UPDATE employe SET est_actif = TRUE")));
        assertTrue(sql.getAllValues().stream().anyMatch(query->query.startsWith("UPDATE employe_affectation_carriere")));
        assertFalse(sql.getAllValues().stream().anyMatch(query->query.contains("document_rh")));
    }
    @Test void apiSerializationIncludesGenerationAndSignedFlags() throws Exception {
        String json=new tools.jackson.databind.ObjectMapper().writeValueAsString(service.preparerContrat(42,db()));
        assertTrue(json.contains("\"generationPossible\":true"));
        var typeDoc=new RefTypeDocument(); typeDoc.setLibelle("Contrat de travail");
        assertTrue(new tools.jackson.databind.ObjectMapper().writeValueAsString(typeDoc).contains("\"contratTravail\":true"));
    }
    @Test void uploadPersistsContractAndReturnsLinkedDocument(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var controller=new mg.galaxam.sirh.contrat.api.DocumentRhController();
        var ds=mock(javax.sql.DataSource.class); Connection c=db(); when(ds.getConnection()).thenReturn(c);
        ReflectionTestUtils.setField(controller,"dataSource",ds); ReflectionTestUtils.setField(controller,"docUploadDir",directory.toString());
        var result=controller.uploadDocument(7,new org.springframework.mock.web.MockMultipartFile("file","signe.pdf","application/pdf",new byte[]{1,2,3}),6,null,null,42,8,null);
        assertEquals(201,result.getStatusCode().value()); var document=(DocumentRhCPL)result.getBody();
        assertEquals(42,document.getIdContrat()); assertEquals(8,document.getIdWorkflow()); assertTrue(document.isContratSigne());
        try(var files=java.nio.file.Files.list(directory)){assertEquals(1,files.count());} verify(c).commit();
    }
    @Test void failedUploadRollsBackAndRemovesPhysicalFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var controller=new mg.galaxam.sirh.contrat.api.DocumentRhController(); var ds=mock(javax.sql.DataSource.class); Connection c=db(); when(ds.getConnection()).thenReturn(c);
        ReflectionTestUtils.setField(controller,"dataSource",ds); ReflectionTestUtils.setField(controller,"docUploadDir",directory.toString());
        doThrow(new SQLException("Insert failure")).when(c).prepareStatement(startsWith("INSERT INTO document_rh"));
        assertThrows(SirhBusinessException.class,()->controller.uploadDocument(7,new org.springframework.mock.web.MockMultipartFile("file","signe.pdf","application/pdf",new byte[]{1}),6,null,null,42,null,null));
        verify(c).rollback(); try(var files=java.nio.file.Files.list(directory)){assertEquals(0,files.count());}
    }
    @Test void splitRunsAndNestedTablesAreReplaced() throws Exception {
        XWPFDocument doc=new XWPFDocument(); var paragraph=doc.createParagraph(); paragraph.createRun().setText("{{EMPLOYE_"); paragraph.createRun().setText("NOM_COMPLET}}");
        var table=doc.createTable(); table.getRow(0).getCell(0).setText("{{EMPLOYE_NOM_COMPLET}}");
        ReflectionTestUtils.invokeMethod(service,"remplacerVariables",doc,Map.of("{{EMPLOYE_NOM_COMPLET}}","Nom réel"));
        assertEquals("Nom réel",paragraph.getText()); assertEquals("Nom réel",table.getRow(0).getCell(0).getText().trim()); doc.close();
    }
    @Test void attestationAndCertificateStillGenerate() throws Exception {
        var emp=new DocumentRhDTO.EmployeDocumentDTO(); emp.setNomComplet("Rakoto Jean"); emp.setMatricule("EMP-7");
        var ent=new DocumentRhDTO.EntrepriseInfoDTO(); ent.setNom("Entreprise validée");
        var sig=new DocumentRhDTO.SignataireDTO(); sig.setNom("Représentant validé"); sig.setFonction("Fonction validée"); sig.setDateEmissionTexte("27 septembre 2026");
        var att=new DocumentRhDTO.AttestationTravailDTO(); att.setEntreprise(ent); att.setEmploye(emp); att.setSignataire(sig); att.setReference("ATT-7"); att.setDateEntreeTexte("1 octobre 2020"); att.setTypeContrat("durée indéterminée");
        var cert=new DocumentRhDTO.CertificatTravailDTO(); cert.setEntreprise(ent); cert.setEmploye(emp); cert.setSignataire(sig); cert.setReference("CERT-7"); cert.setDateEntreeTexte("1 octobre 2020"); cert.setDateSortieTexte("27 septembre 2026"); cert.setDureeTotaleTexte("6 ans"); cert.setTypeContrat("durée indéterminée"); cert.setPostesOccupes(List.of());
        for (boolean attestation : List.of(true,false)) { ByteArrayOutputStream out=new ByteArrayOutputStream(); if(attestation)service.genererAttestationDocx(att,out);else service.genererCertificatDocx(cert,out);
            try(XWPFDocument doc=new XWPFDocument(new ByteArrayInputStream(out.toByteArray()))) { assertTrue(doc.getParagraphs().stream().anyMatch(p->p.getText().contains("Rakoto Jean"))); }
        }
    }
}
