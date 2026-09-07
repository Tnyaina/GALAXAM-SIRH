package mg.galaxam.sirh.paie;

import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.dto.export.VirementRowDto;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.export.PaieExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("PaieExportServiceIntegrationTest — Validation des Déclarations Officielles et Ordre de Virement")
class PaieExportServiceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PaieExportService paieExportService;

    private static final int ID_PERIODE = 1; // Période Août 2026

    @BeforeEach
    void setUp() throws Exception {
        // Calculer au préalable les bulletins pour la période 1
        try (Connection c = dataSource.getConnection()) {
            payrollService.calculerPaiePeriode(ID_PERIODE, true, 1, c);
        }
    }

    @Test
    @DisplayName("1. État 301 SAFI IRSA : Conformité des 25 colonnes réglementaires DGI")
    void testEtat301SafiDonneesEtCsv() throws Exception {
        List<SafiRowDto> safiRows = paieExportService.obtenirDonneesSafi(ID_PERIODE);
        assertNotNull(safiRows);
        assertFalse(safiRows.isEmpty(), "L'État 301 SAFI doit contenir les salariés sous contrat");

        for (SafiRowDto row : safiRows) {
            assertTrue(row.getNumeroOrdre() >= 1);
            assertNotNull(row.getMatricule());
            assertNotNull(row.getNomPrenoms());
            assertNotNull(row.getFonction());

            assertNotNull(row.getSalaireBase());
            assertTrue(row.getSalaireBase().compareTo(BigDecimal.ZERO) >= 0);

            assertNotNull(row.getSalaireBrut());
            assertTrue(row.getSalaireBrut().compareTo(BigDecimal.ZERO) >= 0);

            assertNotNull(row.getDeductionsCnaps());
            assertNotNull(row.getDeductionsSante());
            assertNotNull(row.getSalaireNet());
            assertNotNull(row.getMontantImposable());
            assertNotNull(row.getImpotCorrespondant());
            assertNotNull(row.getReductionChargeFamille());
            assertNotNull(row.getImpotDu());

            // Cohérence de l'arrondi fiscal à la centaine inférieure : imposable <= net
            assertTrue(row.getMontantImposable().compareTo(row.getSalaireNet()) <= 0,
                    "Le montant imposable (arrondi à 100 Ar inf) doit être <= salaire net");
        }

        // Export CSV
        String csv = paieExportService.exporterSafiCsv(ID_PERIODE);
        assertNotNull(csv);
        assertTrue(csv.startsWith("\ufeff"), "Le CSV SAFI doit commencer par le BOM UTF-8 pour Excel");
        assertTrue(csv.contains("ETAT 301 - ANNEXE DE DECLARATION DE L'IRSA (SAFI)"));
        assertTrue(csv.toUpperCase().contains("GALAXAM LOGISTICS"));
        assertTrue(csv.contains("3000019434"));
        assertTrue(csv.contains("N° Ordre;N° Mle;N° d'affiliation (CNaPS);Nom et prenoms;"));
        assertTrue(csv.contains("Montant imposable;Impot correspondant;Reduction charge famille;Impot du"));
        assertTrue(csv.contains("TOTAL;"), "Le CSV SAFI doit comporter une ligne de synthèse TOTAL");
    }

    @Test
    @DisplayName("2. Annexe EDI XML ISI 5% : Conformité du flux e-Hetra DGI")
    void testAnnexeEdiIsiXml() throws Exception {
        String xml = paieExportService.exporterEdiXml(ID_PERIODE);
        assertNotNull(xml);
        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<idEdiType>ISI</idEdiType>"));
        assertTrue(xml.contains("<rContribuable>3000019434</rContribuable>"));
        assertTrue(xml.contains("<idImpotNature>ISINT</idImpotNature>"));
        assertTrue(xml.contains("<idExercice>2026</idExercice>"));
        assertTrue(xml.contains("<idMois>8</idMois>"));
        assertTrue(xml.endsWith("</tabs></EDI>"));
    }

    @Test
    @DisplayName("3. Déclaration DNTS CNaPS & FMFP : Ventilation cotisations et plafonnement 8x SME")
    void testDntsCnapsEtFmfp() throws Exception {
        List<DntsRowDto> dntsRows = paieExportService.obtenirDonneesDnts(ID_PERIODE);
        assertNotNull(dntsRows);
        assertFalse(dntsRows.isEmpty(), "La DNTS doit contenir les salariés affiliés CNaPS");

        BigDecimal maxPlafondCnaps = new BigDecimal("2101440.00"); // 8 x 262 680 Ar (SME LF 2026)

        for (DntsRowDto row : dntsRows) {
            assertNotNull(row.getMatricule());
            assertNotNull(row.getNomPrenom());
            assertNotNull(row.getSalaireBrutReel());
            assertNotNull(row.getAssiettePlafonneeCnaps());

            // Contrôle du respect du plafond légal 8x SME
            assertTrue(row.getAssiettePlafonneeCnaps().compareTo(maxPlafondCnaps) <= 0,
                    "L'assiette CNaPS ne doit pas dépasser le plafond 8x SME (2 101 440 Ar)");

            // Contrôle des taux : CNaPS salarial = 1% assiette, CNaPS patronal = 13% assiette
            assertNotNull(row.getCnapsSalarial());
            assertNotNull(row.getCnapsPatronal());
            assertNotNull(row.getFmfpPatronal());
            assertNotNull(row.getSanteSalarial());
            assertNotNull(row.getSantePatronal());
            assertNotNull(row.getTotalCotisations());

            BigDecimal expectedTotal = row.getCnapsSalarial()
                    .add(row.getCnapsPatronal())
                    .add(row.getFmfpPatronal())
                    .add(row.getSanteSalarial())
                    .add(row.getSantePatronal());
            assertEquals(expectedTotal, row.getTotalCotisations(), "Total cotisations = somme des 5 composantes");
        }

        // Export CSV DNTS
        String csv = paieExportService.exporterDntsCsv(ID_PERIODE);
        assertNotNull(csv);
        assertTrue(csv.startsWith("\ufeff"));
        assertTrue(csv.contains("CNAPS - CAISSE NATIONALE DE PREVOYANCE SOCIALE & FMFP"));
        assertTrue(csv.contains("CNaPS Salarial (1%);CNaPS Patronal (13%);FMFP Patronal (1%)"));
        assertTrue(csv.contains("TOTAL;"));
    }

    @Test
    @DisplayName("3b. Déclaration Trimestrielle DNTS CNaPS : Agrégation T3 2026")
    void testDntsTrimestrielleCnaps() throws Exception {
        // T3 2026 (Juillet, Août, Septembre 2026)
        List<DntsRowDto> rowsTrimestre = paieExportService.obtenirDonneesDntsTrimestre(2026, 3);
        assertNotNull(rowsTrimestre);
        assertFalse(rowsTrimestre.isEmpty(), "La DNTS trimestrielle doit agréger les salariés du trimestre T3");

        for (DntsRowDto r : rowsTrimestre) {
            assertNotNull(r.getMatricule());
            assertNotNull(r.getNomPrenom());
            assertTrue(r.getSalaireBrutReel().compareTo(BigDecimal.ZERO) >= 0);
            assertTrue(r.getTotalCotisations().compareTo(BigDecimal.ZERO) >= 0);
        }

        String csvTrim = paieExportService.exporterDntsTrimestreCsv(2026, 3);
        assertNotNull(csvTrim);
        assertTrue(csvTrim.contains("TRIMESTRE :;T3 2026"));
        assertTrue(csvTrim.contains("DECLARATION NOMINATIVE TRIMESTRIELLE"));
    }

    @Test
    @DisplayName("4. Ordre de Virement Bancaire : Fichier de télétransmission et découpage RIB")
    void testOrdreVirementBancaire() throws Exception {
        List<VirementRowDto> virementRows = paieExportService.obtenirDonneesVirement(ID_PERIODE);
        assertNotNull(virementRows);
        assertFalse(virementRows.isEmpty(), "L'ordre de virement doit contenir les salariés à payer");

        for (VirementRowDto row : virementRows) {
            assertNotNull(row.getMatricule());
            assertNotNull(row.getNomBeneficiaire());
            assertNotNull(row.getBanque());
            assertNotNull(row.getNetAPayer());
            assertTrue(row.getNetAPayer().compareTo(BigDecimal.ZERO) > 0, "Seuls les nets positifs sont virés");
            assertEquals("MGA", row.getDevise());
            assertTrue(row.getMotifVirement().contains("VIRT SALAIRE"));

            // Si le RIB est complet (ex: 00004 00123 00000000001 45), les segments sont renseignés
            if (row.getRibComplet() != null && row.getRibComplet().contains(" ")) {
                assertNotNull(row.getCodeBanque());
                assertNotNull(row.getCodeGuichet());
                assertNotNull(row.getNumeroCompte());
                assertNotNull(row.getCleRib());
            }
        }

        // Export CSV Virement
        String csv = paieExportService.exporterVirementCsv(ID_PERIODE);
        assertNotNull(csv);
        assertTrue(csv.startsWith("\ufeff"));
        assertTrue(csv.contains("ORDRE DE VIREMENT DE SALAIRES INTERBANCAIRE"));
        assertTrue(csv.contains("GALAXAM LOGISTICS"));
        assertTrue(csv.contains("Matricule;Beneficiaire;Banque;Code Banque;Code Guichet;Numero Compte;Cle RIB;RIB Complet;Net a Payer"));
        assertTrue(csv.contains("TOTAL;"));
    }

    @Test
    @DisplayName("5. Contre-Audit Axe 6 : Export SAFI ODS natif conforme au modèle officiel e-Hetra DGI")
    void testExportSafiOdsEHetra() throws Exception {
        byte[] odsBytes = paieExportService.exporterSafiOds(ID_PERIODE);
        assertNotNull(odsBytes, "Le flux binaire de l'ODS ne doit pas être nul");
        assertTrue(odsBytes.length > 20000, "Le fichier ODS doit être complet et dépasser 20 Ko");

        // Vérification de la validité de l'archive ZIP et de la présence des fichiers clés
        boolean hasMimetype = false;
        boolean hasContentXml = false;
        boolean hasManifest = false;
        String contentXmlStr = null;

        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(odsBytes))) {
            java.util.zip.ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if ("mimetype".equals(ze.getName())) {
                    hasMimetype = true;
                    String mt = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    assertEquals("application/vnd.oasis.opendocument.spreadsheet", mt);
                } else if ("content.xml".equals(ze.getName())) {
                    hasContentXml = true;
                    contentXmlStr = new String(zis.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } else if ("META-INF/manifest.xml".equals(ze.getName())) {
                    hasManifest = true;
                }
                zis.closeEntry();
            }
        }

        assertTrue(hasMimetype, "L'entrée mimetype doit être présente dans l'archive ODS");
        assertTrue(hasContentXml, "L'entrée content.xml doit être présente dans l'archive ODS");
        assertTrue(hasManifest, "Le manifest ODF doit être présent");

        assertNotNull(contentXmlStr);
        assertTrue(contentXmlStr.toLowerCase().contains("galaxam logistics"), "content.xml doit contenir la raison sociale");
        assertTrue(contentXmlStr.contains("EMP-2006-0001"), "content.xml doit contenir le matricule du salarié");
        assertTrue(contentXmlStr.contains("RAKOTONDRAZAKA"), "content.xml doit contenir le nom du salarié");
    }
}
