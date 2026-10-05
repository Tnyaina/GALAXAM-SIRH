package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.paie.api.PaieRubriqueController;
import mg.galaxam.sirh.paie.api.dto.PaieRubriqueRequest;
import mg.galaxam.sirh.paie.persistence.PaieRubrique;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paieconfig.api.RefTypeMouvementPaieController;
import mg.galaxam.sirh.paieconfig.api.dto.RefTypeMouvementPaieRequest;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeMouvementPaie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/** Recette volontaire sur une copie réelle. Ne doit jamais cibler la base de travail. */
@EnabledIfSystemProperty(named = "payroll.copied-data-test", matches = "true")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/sirh_galaxam_recette_variables_20261001",
    "app.rattrapage.enabled=false", "app.security.bootstrap-admin.enabled=false"
})
@ActiveProfiles("test")
class PayrollVariablesCopiedDataTest {
    @MockitoBean(name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor")
    ScheduledAnnotationBeanPostProcessor scheduling;
    @Autowired mg.galaxam.sirh.paie.api.PaieAjustementController adjustments;
    @Autowired mg.galaxam.sirh.paie.application.workflow.PayrollAdjustmentWorkflowService adjustmentWorkflow;
    @Autowired mg.galaxam.sirh.paie.application.export.BulletinDocumentService bulletinDocuments;
    @Autowired DataSource dataSource;
    @Autowired PayrollService payroll;
    @Autowired PaieRubriqueController rubriques;
    @Autowired RefTypeMouvementPaieController types;
    @Autowired mg.galaxam.sirh.paie.application.export.SafiExportService safi;
    @Autowired mg.galaxam.sirh.paie.application.export.DntsExportService dnts;

    @Test
    void exportsUtilisentIdentiteEtTauxDeLaBase() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertEquals("sirh_galaxam_recette_variables_20261001", scalar(c, "SELECT current_database()"));
            String company = scalar(c, "SELECT raison_sociale FROM ref_filiale WHERE id_filiale=1");
            String nif = scalar(c, "SELECT regexp_replace(nif,'\\s','','g') FROM ref_filiale WHERE id_filiale=1");
            String safiCsv = safi.exporterSafiCsv(1, c);
            String dntsCsv = dnts.exporterDntsCsv(1, c);
            assertTrue(safiCsv.contains(company));
            assertTrue(safiCsv.contains(nif));
            assertTrue(dntsCsv.contains(company));
            assertTrue(dntsCsv.contains(nif));
        }
    }

    @Test
    void noteDeFraisExigeUnePieceEtApparaitDansLeBulletin() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken("recette", "",
                AuthorityUtils.createAuthorityList("ROLE_ADMIN", "PAIE_WRITE", "PAIE_READ"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try (Connection c = dataSource.getConnection()) {
            assertEquals("sirh_galaxam_recette_variables_20261001", scalar(c, "SELECT current_database()"));
        }
        var baseline = calculate(true).getBulletin();
        var request = new mg.galaxam.sirh.paie.api.dto.PaieAjustementRequest();
        request.setIdEmploye(3); request.setIdPeriodePaie(1);
        request.setTypeMouvement("NOTE_DE_FRAIS"); request.setMontant(new BigDecimal("10000"));
        request.setMotif("Facture de mission de recette");
        int id = -1;
        try {
            var created = (java.util.Map<?, ?>) adjustments.creerAjustement(request, authentication).getBody();
            id = ((Number) created.get("idAvance")).intValue();
            final int expenseId = id;
            final int initialVersion = ((Number) created.get("version")).intValue();
            assertThrows(SirhBusinessException.class, () -> adjustmentWorkflow.execute(expenseId,
                    mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAdjustmentCommand.APPROVE,
                    null, null, initialVersion));
            byte[] pdf = "%PDF-1.4\njustificatif de recette".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            adjustments.deposerJustificatifFrais(id,
                    new org.springframework.mock.web.MockMultipartFile("file", "facture.pdf", "application/pdf", pdf), authentication);
            int updatedVersion;
            try (Connection c = dataSource.getConnection(); var ps = c.prepareStatement(
                    "SELECT updated_at FROM paie_avance_acompte WHERE id_avance=?")) {
                ps.setInt(1, id);
                try (var rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    updatedVersion = (int) (rs.getTimestamp(1).getTime() % Integer.MAX_VALUE);
                }
            }
            adjustmentWorkflow.execute(id,
                    mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAdjustmentCommand.APPROVE,
                    null, null, updatedVersion);
            var result = calculate(true);
            var bulletin = result.getBulletin();
            money("10000", bulletin.getNetAvantArrondi().subtract(baseline.getNetAvantArrondi()));
            money("0", bulletin.getTotalGainsBruts().subtract(baseline.getTotalGainsBruts()));
            money("0", bulletin.getTotalCotisationsSalariales().subtract(baseline.getTotalCotisationsSalariales()));
            money("0", bulletin.getIrsaNet().subtract(baseline.getIrsaNet()));
            assertTrue(result.getLignes().stream().anyMatch(l ->
                    "REMBOURSEMENT_NOTE_FRAIS".equals(l.getCodeRubrique())
                            && l.getMontantSalarial().compareTo(new BigDecimal("10000")) == 0));
            var context = new mg.galaxam.sirh.paie.application.export.BulletinDocumentService.Context(
                    "2026-08", "26/07/2026 au 25/08/2026", "Galaxam", "", "", "");
            byte[] xlsx = bulletinDocuments.excel(result, context);
            try (var workbook = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(xlsx))) {
                boolean found = false;
                for (var row : workbook.getSheetAt(0)) {
                    var cell = row.getCell(0);
                    if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING
                            && cell.getStringCellValue().contains("Ajout au net : Remboursement Notes de Frais")) found = true;
                }
                assertTrue(found, "Le bulletin Excel doit présenter la ligne de remboursement.");
            }
            try (var pdfDocument = org.apache.pdfbox.Loader.loadPDF(bulletinDocuments.pdf(result, context))) {
                String pdfText = new org.apache.pdfbox.text.PDFTextStripper().getText(pdfDocument);
                assertTrue(pdfText.contains("Ajout au net"));
                assertTrue(pdfText.contains("Remboursement Notes de Frais"));
            }
        } finally {
            if (id > 0) try (Connection c = dataSource.getConnection(); var ps = c.prepareStatement(
                    "DELETE FROM paie_avance_acompte WHERE id_avance=?")) {
                ps.setInt(1, id); ps.executeUpdate();
            }
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void catalogueConfigurationPagineEtVerrouille() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "recette", "", AuthorityUtils.createAuthorityList("ROLE_ADMIN", "PAIE_WRITE")));
        try {
            var result=(java.util.Map<String,Object>)types.getConfiguration("rubriques","","","","","ordre",1,10,null).getBody();
            var items=(java.util.List<java.util.Map<String,Object>>)result.get("items");
            assertEquals(10,items.size());
            assertTrue((Integer)result.get("totalElements") >= items.size());
            var second=(java.util.Map<String,Object>)types.getConfiguration("rubriques","","","","","ordre",2,10,null).getBody();
            var next=(java.util.List<java.util.Map<String,Object>>)second.get("items");
            assertTrue(next.stream().noneMatch(n -> items.stream().anyMatch(i -> i.get("id").equals(n.get("id")))));
            var searched=(java.util.Map<String,Object>)types.getConfiguration("rubriques","salaire_base","","","","code",1,10,null).getBody();
            var salary=((java.util.List<java.util.Map<String,Object>>)searched.get("items")).get(0);
            assertEquals(false,salary.get("peutModifier"));
            assertEquals(true,salary.get("traitementVerrouille"));
            var selected=(java.util.Map<String,Object>)types.getConfiguration("rubriques","inexistant","","","","ordre",1,10,(Integer)salary.get("id")).getBody();
            assertEquals(0,selected.get("totalElements"));
            assertNotNull(selected.get("selection")); // La fiche ne disparaît pas lors d'un filtre.
            var filtered=(java.util.Map<String,Object>)types.getConfiguration("types","","RETENUE","","actif","ordre",1,10,null).getBody();
            for(var item:(java.util.List<java.util.Map<String,Object>>)filtered.get("items")) {
                assertEquals("RETENUE",item.get("sens")); assertEquals(true,item.get("estActif"));
                assertNotNull(item.get("liaisonVerrouillee"));
            }
            assertThrows(IllegalArgumentException.class,()->types.getConfiguration("rubriques","","","","","ordre",1,500,null));
            String dump=System.getProperty("payroll.configuration-preview");
            if(dump!=null) {
                var full=types.getConfiguration("rubriques","","","","","ordre",1,50,null).getBody();
                var mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
                java.nio.file.Files.writeString(java.nio.file.Path.of(dump),mapper.writeValueAsString(full));
            }
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void troisTraitementsEtProtectionsSurAgentY() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertEquals("sirh_galaxam_recette_variables_20261001", scalar(c, "SELECT current_database()"));
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "recette", "", AuthorityUtils.createAuthorityList("ROLE_ADMIN", "PAIE_WRITE")));
        try {
            var baseline = calculate(true);
            money("411361.01", baseline.getBulletin().getTotalGainsBruts());
            money("8227.22", baseline.getBulletin().getTotalCotisationsSalariales());
            money("399120", baseline.getBulletin().getNetAPayer());
            String suffix = Long.toString(System.nanoTime(), 36).toUpperCase();
            var incoherent = rubrique("R_INVALIDE_" + suffix, false, true);
            incoherent.setEtapeCalcul("GAIN_NET");
            var rejected = assertThrows(SirhBusinessException.class, () -> rubriques.creerRubrique(incoherent));
            assertTrue(rejected.getMessage().contains("hors assiettes"));
            for (String scenario : new String[]{"BRUT", "NET", "RETENUE", "BRUT_EXO", "BRUT_EXO_SOC", "BRUT_CNAPS", "BRUT_SANTE"}) {
                boolean retention = scenario.equals("RETENUE");
                boolean taxable = scenario.equals("BRUT");
                boolean gross = scenario.startsWith("BRUT");
                boolean social = taxable || scenario.equals("BRUT_EXO_SOC");
                boolean cnaps = social || scenario.equals("BRUT_CNAPS");
                boolean health = social || scenario.equals("BRUT_SANTE");
                boolean mixed = cnaps != health;
                var req = rubrique("R_" + scenario + "_" + suffix, retention, taxable);
                req.setEtapeCalcul(retention ? "RETENUE_NET" : gross ? "BRUT" : "GAIN_NET");
                if (gross) req.setCategorie("PRIME");
                req.setSoumisCnaps(cnaps); req.setSoumisSante(health);
                var rubrique = (PaieRubrique) rubriques.creerRubrique(req).getBody();
                assertNotNull(rubrique);
                var typeReq = new RefTypeMouvementPaieRequest();
                typeReq.setCode("V_" + scenario + "_" + suffix);
                typeReq.setLibelle("Recette " + scenario);
                typeReq.setSens(retention ? "RETENUE" : "GAIN");
                typeReq.setCategorie(req.getCategorie());
                typeReq.setIdRubrique(rubrique.getIdRubrique());
                var type = (RefTypeMouvementPaie) types.creerTypeMouvement(typeReq).getBody();
                assertNotNull(type);
                int movement = insertMovement(type);
                try {
                    var result = calculate(true);
                    var b = result.getBulletin();
                    money(gross ? "421361.01" : "411361.01", b.getTotalGainsBruts());
                    money(social ? "8427.22" : mixed ? "8327.22" : "8227.22", b.getTotalCotisationsSalariales());
                    // Assiette 412 900 : 50 000 à 5 % + 12 900 à 10 % = 3 790.
                    money(taxable ? "3790" : "3000", b.getIrsaNet());
                    money(retention ? "389123.79" : taxable ? "408133.79" : social ? "408923.79" : mixed ? "409023.79" : "409123.79", b.getNetAvantArrondi());
                    money(retention ? "389120" : taxable ? "408130" : social ? "408920" : mixed ? "409020" : "409120", b.getNetAPayer());
                    assertNull(b.getIdBulletin(), "La simulation ne persiste pas de bulletin");
                    System.out.printf("RECETTE %s brut=%s cotisations=%s irsa=%s net=%s%n", scenario,
                            b.getTotalGainsBruts(), b.getTotalCotisationsSalariales(), b.getIrsaNet(), b.getNetAPayer());
                    req.setSoumisIrsa(!taxable);
                    var regimeFailure = assertThrows(SirhBusinessException.class,
                            () -> rubriques.modifierRubrique(rubrique.getIdRubrique(), req));
                    assertTrue(regimeFailure.getMessage().contains("historiques"));
                    req.setSoumisIrsa(taxable);
                    req.setDateDebutEffet(LocalDate.of(2026, 9, 1));
                    var dateFailure = assertThrows(SirhBusinessException.class,
                            () -> rubriques.modifierRubrique(rubrique.getIdRubrique(), req));
                    assertTrue(dateFailure.getMessage().contains("historiques"));
                    req.setDateDebutEffet(LocalDate.of(2026,7,26));
                    req.setEtapeCalcul("GAIN_NET".equals(req.getEtapeCalcul()) ? "BRUT" : "GAIN_NET");
                    var stageFailure = assertThrows(SirhBusinessException.class,
                            () -> rubriques.modifierRubrique(rubrique.getIdRubrique(), req));
                    assertTrue(stageFailure.getMessage().contains("historiques"));
                    // Le calcul réel est essayé dans une transaction annulée : contrôle du lien de rubrique.
                    try (Connection c = dataSource.getConnection()) {
                        c.setAutoCommit(false);
                        try {
                            var persisted = payroll.calculerBulletinEmploye(3, 1, false, null, c);
                            var line = persisted.getLignes().stream().filter(l ->
                                    rubrique.getIdRubrique().equals(l.getIdRubrique())).findFirst().orElseThrow();
                            assertEquals(retention ? "RETENUE_NET" : gross ? "BRUT" : "GAIN_NET", line.getEtapeCalcul());
                            try (var ps = c.prepareStatement("SELECT etape_calcul FROM bulletin_paie_ligne WHERE id_ligne=?")) {
                                ps.setInt(1,line.getIdLigne());
                                try(var rs=ps.executeQuery()){assertTrue(rs.next());assertEquals(line.getEtapeCalcul(),rs.getString(1));}
                            }
                            var exported = safi.genererDonneesSafi(1, c).stream()
                                    .filter(row -> "199".equals(row.getMatricule())).findFirst().orElseThrow();
                            money(!taxable && !retention ? "10000" : "0", exported.getIndemnitesNonImposables());
                        } finally { c.rollback(); }
                    }
                    if (retention) {
                        try (Connection c = dataSource.getConnection()) {
                            c.setAutoCommit(false);
                            try (var ps = c.prepareStatement("UPDATE paie_avance_acompte SET montant_impute=3000,solde_restant=7000 WHERE id_avance=?")) {
                                ps.setInt(1,movement); ps.executeUpdate();
                                var partial=payroll.calculerBulletinEmploye(3,1,true,null,c);
                                money("392123.79",partial.getBulletin().getNetAvantArrondi());
                            } finally {c.rollback();}
                        }
                    }
                } finally { cancelMovement(movement); }
            }
            var futureReq = rubrique("R_FUTUR_" + suffix, false, true);
            futureReq.setDateDebutEffet(LocalDate.of(2026, 9, 1));
            var future = (PaieRubrique) rubriques.creerRubrique(futureReq).getBody();
            var futureType = new RefTypeMouvementPaieRequest();
            futureType.setCode("V_FUTUR_" + suffix); futureType.setLibelle("Recette future");
            futureType.setSens("GAIN"); futureType.setCategorie("PRIME"); futureType.setIdRubrique(future.getIdRubrique());
            int movement = insertMovement((RefTypeMouvementPaie) types.creerTypeMouvement(futureType).getBody());
            try {
                var failure = assertThrows(SirhBusinessException.class, () -> calculate(true));
                assertTrue(failure.getMessage().contains("couvre toute la période"));
            }
            finally { cancelMovement(movement); }
            var originalReq = rubrique("R_VERSION_" + suffix, false, true);
            originalReq.setDateDebutEffet(LocalDate.of(2026,6,1));
            var original = (PaieRubrique) rubriques.creerRubrique(originalReq).getBody();
            var versionReq = rubrique("R_VERSION2_" + suffix, false, false);
            versionReq.setCategorie("PRIME"); versionReq.setEtapeCalcul("BRUT");
            versionReq.setDateDebutEffet(LocalDate.of(2026,7,26));
            versionReq.setConditionApplication("ASSUJETTI_IRSA");
            var version = rubriques.creerVersion(original.getIdRubrique(),versionReq).getBody();
            assertEquals(original.getIdRubrique(),version.getIdRubriqueOrigine());
            var versionTypeReq = new RefTypeMouvementPaieRequest();
            versionTypeReq.setCode("V_VERSION_"+suffix); versionTypeReq.setLibelle("Version automatique");
            versionTypeReq.setSens("GAIN"); versionTypeReq.setCategorie("PRIME");
            versionTypeReq.setIdRubrique(original.getIdRubrique());
            int versionMovement = insertMovement((RefTypeMouvementPaie) types.creerTypeMouvement(versionTypeReq).getBody());
            try {
                var versionResult=calculate(true);
                money("409120",versionResult.getBulletin().getNetAPayer());
                assertTrue(versionResult.getLignes().stream().anyMatch(l -> version.getCode().equals(l.getCodeRubrique())));
                try(Connection c=dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    try(var ps=c.prepareStatement("UPDATE ref_type_intervenant SET soumis_irsa=false WHERE id_type_intervenant IN (SELECT id_type_intervenant FROM contrat_travail WHERE id_employe=3)")) {
                        ps.executeUpdate();
                        var incompatible=assertThrows(SirhBusinessException.class,
                                ()->payroll.calculerBulletinEmploye(3,1,true,null,c));
                        assertTrue(incompatible.getMessage().contains("n’est pas applicable"));
                    } finally {c.rollback();}
                }
                var retro= rubrique("R_RETRO_"+suffix,false,false);
                retro.setDateDebutEffet(LocalDate.of(2026,8,1));
                assertThrows(SirhBusinessException.class,()->rubriques.creerVersion(version.getIdRubrique(),retro));
                var nextReq=rubrique("R_VERSION3_"+suffix,false,true);
                nextReq.setDateDebutEffet(LocalDate.of(2026,9,1));
                rubriques.creerVersion(version.getIdRubrique(),nextReq);
                // Le recalcul d’août utilise toujours la version applicable à août.
                money("409120",calculate(true).getBulletin().getNetAPayer());
                try(Connection c=dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    try(var ps=c.prepareStatement("UPDATE paie_rubrique SET date_fin_effet=NULL WHERE id_rubrique=?")) {
                        ps.setInt(1,original.getIdRubrique());
                        assertThrows(java.sql.SQLException.class,ps::executeUpdate);
                    } finally {c.rollback();}
                }
            } finally {cancelMovement(versionMovement);}
            // Le catalogue est réellement lu, pas seulement les créations de ses contrôleurs.
            assertNotNull(types.getCatalogue().getBody());
            for(String method:new String[]{"QUANTITE_TAUX","POURCENTAGE_SALAIRE_BASE"}) {
                var methodReq=rubrique("R_M_"+(method.equals("QUANTITE_TAUX")?"Q":"P")+"_"+suffix,false,false);
                methodReq.setCategorie("PRIME"); methodReq.setEtapeCalcul("BRUT"); methodReq.setMethodeCalcul(method);
                methodReq.setSoumisFmfp(false);
                var methodRule=(PaieRubrique)rubriques.creerRubrique(methodReq).getBody();
                var typeReq=new RefTypeMouvementPaieRequest();
                typeReq.setCode("V_M_"+(method.equals("QUANTITE_TAUX")?"Q":"P")+"_"+suffix);typeReq.setLibelle("Méthode "+method);
                typeReq.setSens("GAIN");typeReq.setCategorie("PRIME");typeReq.setIdRubrique(methodRule.getIdRubrique());
                var methodType=(RefTypeMouvementPaie)types.creerTypeMouvement(typeReq).getBody();
                var request=new mg.galaxam.sirh.paie.api.dto.PaieAjustementRequest();
                request.setIdEmploye(3);request.setIdPeriodePaie(1);request.setIdTypeMouvement(methodType.getIdTypeMouvement());
                request.setMotif("Recette méthode calculée");request.setMontant(new BigDecimal("999999"));
                request.setQuantiteSaisie(new BigDecimal("1.5"));request.setTauxSaisi(new BigDecimal(method.equals("QUANTITE_TAUX")?"2500.125":"10"));
                var body=(java.util.Map<?,?>)adjustments.creerAjustement(request,SecurityContextHolder.getContext().getAuthentication()).getBody();
                int methodMovement=((Number)body.get("idAvance")).intValue();
                String expectedAmount=method.equals("QUANTITE_TAUX")?"3750.19":"35000";
                money(expectedAmount,(BigDecimal)body.get("montant"));
                try {
                    try(Connection c=dataSource.getConnection();var ps=c.prepareStatement("UPDATE paie_avance_acompte SET statut='APPROUVE' WHERE id_avance=?")) {ps.setInt(1,methodMovement);ps.executeUpdate();}
                    var result=calculate(true);
                    var calculated=result.getLignes().stream().filter(l->methodRule.getCode().equals(l.getCodeRubrique())).findFirst().orElseThrow();
                    money(expectedAmount,calculated.getMontantSalarial());assertNotNull(calculated.getCommentaire());
                    methodReq.setMethodeCalcul("MONTANT");
                    assertThrows(SirhBusinessException.class,()->rubriques.modifierRubrique(methodRule.getIdRubrique(),methodReq));
                    try(Connection c=dataSource.getConnection()) {
                        c.setAutoCommit(false);
                        try {
                            var actual=payroll.calculerBulletinEmploye(3,1,false,null,c);
                            var actualLine=actual.getLignes().stream().filter(l->methodRule.getCode().equals(l.getCodeRubrique())).findFirst().orElseThrow();
                            assertEquals(calculated.getCommentaire(),actualLine.getCommentaire());
                            try(var ps=c.prepareStatement("SELECT montant_impute FROM paie_avance_acompte WHERE id_avance=?")) {ps.setInt(1,methodMovement);try(var rs=ps.executeQuery()){rs.next();money(expectedAmount,rs.getBigDecimal(1));}}
                        } finally {c.rollback();}
                    }
                } finally {cancelMovement(methodMovement);}
            }
            money("399120", calculate(true).getBulletin().getNetAPayer());
        } finally { SecurityContextHolder.clearContext(); }
    }

    private PaieRubriqueRequest rubrique(String code, boolean retention, boolean taxable) {
        var r = new PaieRubriqueRequest(); r.setCode(code); r.setLibelle("Recette " + code);
        r.setCategorie(retention ? "RETENUE_DIVERSE" : taxable ? "PRIME" : "INDEMNITE_NON_IMPOSABLE");
        r.setSens(retention ? "RETENUE" : "GAIN");
        r.setSoumisCnaps(taxable); r.setSoumisSante(taxable); r.setSoumisIrsa(taxable);
        r.setEtapeCalcul(retention ? "RETENUE_NET" : taxable ? "BRUT" : "GAIN_NET");
        r.setReferenceReglementaire("Scénario technique de recette uniquement");
        r.setDateDebutEffet(LocalDate.of(2026, 7, 26)); return r;
    }
    private BulletinPaieComplet calculate(boolean simulation) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try { return payroll.calculerBulletinEmploye(3, 1, simulation, null, c); }
            finally { c.rollback(); }
        }
    }
    private int insertMovement(RefTypeMouvementPaie type) throws Exception {
        try (Connection c = dataSource.getConnection(); var ps = c.prepareStatement(
                "INSERT INTO paie_avance_acompte (id_employe,id_periode_paie,type_mouvement,id_type_mouvement,sens,"
                + "montant,montant_initial,solde_restant,motif,statut) VALUES (3,1,?,?,?,10000,10000,10000,'Recette isolée','APPROUVE') RETURNING id_avance")) {
            ps.setString(1,type.getCode()); ps.setInt(2,type.getIdTypeMouvement()); ps.setString(3,type.getSens());
            try(var rs=ps.executeQuery()){rs.next(); return rs.getInt(1);}
        }
    }
    private void cancelMovement(int id) throws Exception {
        try(Connection c=dataSource.getConnection();var ps=c.prepareStatement("UPDATE paie_avance_acompte SET statut='ANNULE' WHERE id_avance=?")) {
            ps.setInt(1,id); ps.executeUpdate();
        }
    }
    private String scalar(Connection c,String sql) throws Exception {
        try(var s=c.createStatement();var rs=s.executeQuery(sql)){rs.next();return rs.getString(1);}
    }
    private void money(String expected, BigDecimal actual) { assertEquals(0,new BigDecimal(expected).compareTo(actual), expected + " != " + actual); }
}
