package mg.galaxam.sirh.contrat;

import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.*;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CertificatDocxRenderTest {
    @Test
    void certificatSansVilleConserveReferenceEtNettoieEntete() throws Exception {
        EntrepriseInfoDTO entreprise = new EntrepriseInfoDTO();
        entreprise.setNom("Galaxam Logistics");
        entreprise.setNif("3 000 019 43 4");
        entreprise.setStat("52 296 11 2001 0 00056");
        entreprise.setTelephone("+261 32 12 131 01");
        entreprise.setEmail("daf@galaxam.com");

        EmployeDocumentDTO employe = new EmployeDocumentDTO();
        employe.setNomComplet("AGENT STC");
        employe.setDateNaissance(LocalDate.of(1990, 3, 12));
        employe.setLieuNaissance("Antananarivo");
        employe.setAdresse("Hahahaha");

        SignataireDTO signataire = new SignataireDTO();
        signataire.setNom("Direction des Ressources Humaines");
        signataire.setFonction("Directeur des Ressources Humaines");
        signataire.setDateEmissionTexte("18 octobre 2026");

        PosteHistoriqueDTO poste = new PosteHistoriqueDTO();
        poste.setDateDebutTexte("1 février 2020");
        poste.setDateFinTexte("25 août 2026");
        poste.setLibelleFonction("Assistante Admin");
        poste.setCategorieProfessionnelle("Agent");
        poste.setDepartement("Logistics");

        CertificatTravailDTO certificat = new CertificatTravailDTO();
        certificat.setReference("CERT-CTR-2026-00001");
        certificat.setEntreprise(entreprise);
        certificat.setEmploye(employe);
        certificat.setSignataire(signataire);
        certificat.setDateEntreeTexte("1 février 2020");
        certificat.setDateSortieTexte("25 août 2026");
        certificat.setDureeTotaleTexte("6 ans 6 mois");
        certificat.setTypeContrat("durée indéterminée");
        certificat.setDernierPoste("Assistante Admin");
        certificat.setDerniereCategorie("Agent");
        certificat.setDernierDepartement("Logistics");
        certificat.setPostesOccupes(List.of(poste));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new DocumentRhService().genererCertificatDocx(certificat, output);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            String corps = document.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            String entete = document.getTables().stream().flatMap(t -> t.getRows().stream())
                    .flatMap(r -> r.getTableCells().stream()).flatMap(c -> c.getParagraphs().stream())
                    .map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            assertTrue(corps.contains("Référence : CERT-CTR-2026-00001 · Le 18 octobre 2026"));
            assertFalse(corps.contains(", le 18 octobre 2026"));
            assertFalse(corps.contains("[Date début]"));
            assertTrue(entete.contains("NIF : 3 000 019 43 4"));
            assertFalse(entete.contains("·  ·"));
        }
        if (Boolean.getBoolean("certificat.render")) {
            Path chemin = Path.of("target", "certificat-preview.docx");
            Files.write(chemin, output.toByteArray());
        }
    }
}
