package mg.galaxam.sirh.paie.application.export;


import mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Service d'exportation de la Déclaration IRSA SAFI au format OpenDocument Spreadsheet (.ods)
 * strictement conforme au modèle officiel DGI Madagascar ("modele declaration iRSA SAFI.ods")
 * pour le téléversement direct sur le portail e-Hetra.
 */
@Service
public class SafiOdsExportService {

    private static final String TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";

    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }

    private final SafiExportService safiExportService;

    public SafiOdsExportService(SafiExportService safiExportService) {
        this.safiExportService = safiExportService;
    }

    /**
     * Génère le fichier .ods complet à partir de la période de paie spécifiée.
     */
    public byte[] genererSafiOds(Integer idPeriode, Connection c) throws Exception {
        // 1. Récupération des 25 colonnes réglementaires calculées
        List<SafiRowDto> donnees = safiExportService.genererDonneesSafi(idPeriode, c);

        String sqlEntete = "SELECT pp.code AS code_periode, pp.libelle AS libelle_periode, " +
                "COALESCE(rf.raison_sociale, rf_def.raison_sociale) AS raison_sociale, " +
                "COALESCE(rf.nif, rf_def.nif) AS nif " +
                "FROM periode_paie pp " +
                "LEFT JOIN LATERAL ( " +
                "    SELECT rf1.raison_sociale, rf1.nif " +
                "    FROM bulletin_paie bp " +
                "    JOIN employe_affectation_carriere eac ON bp.id_employe = eac.id_employe " +
                "    JOIN ref_site s ON eac.id_site = s.id_site " +
                "    JOIN ref_filiale rf1 ON s.id_filiale_rattachement = rf1.id_filiale " +
                "    WHERE bp.id_periode_paie = pp.id_periode_paie " +
                "    LIMIT 1 " +
                ") rf ON TRUE " +
                "LEFT JOIN LATERAL ( " +
                "    SELECT rf2.raison_sociale, rf2.nif " +
                "    FROM ref_filiale rf2 " +
                "    WHERE rf2.est_actif = TRUE " +
                "    ORDER BY rf2.id_filiale ASC " +
                "    LIMIT 1 " +
                ") rf_def ON TRUE " +
                "WHERE pp.id_periode_paie = ?";

        String libellePeriode = "PÉRIODE";
        String raisonSociale = "GALAXAM LOGISTICS";
        String nif = "3000019434";
        String adresse = "TOUR FORELLO TANJOMBATO 3È ETAGE (BUREAU)";

        try (PreparedStatement ps = c.prepareStatement(sqlEntete)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("libelle_periode") != null) libellePeriode = rs.getString("libelle_periode");
                    if (rs.getString("raison_sociale") != null) raisonSociale = rs.getString("raison_sociale");
                    if (rs.getString("nif") != null) nif = rs.getString("nif");
                }
            }
        }

        // 3. Chargement du template .ods officiel
        byte[] templateBytes = chargerTemplateOds();

        // 4. Traitement et injection dans content.xml
        return injecterDonneesDansOds(templateBytes, donnees, raisonSociale, nif, libellePeriode, adresse);
    }

    private byte[] chargerTemplateOds() throws Exception {
        // Essai via le classpath Spring Boot
        try (InputStream is = getClass().getResourceAsStream("/templates/modele_declaration_irsa_safi.ods")) {
            if (is != null) {
                return is.readAllBytes();
            }
        }

        // Essai via fichier local relatif
        File[] candidates = new File[] {
                new File("model/modele declaration iRSA SAFI.ods"),
                new File("../model/modele declaration iRSA SAFI.ods"),
                new File("backend/src/main/resources/templates/modele_declaration_irsa_safi.ods")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try (InputStream fis = new FileInputStream(f)) {
                    return fis.readAllBytes();
                }
            }
        }

        throw new IllegalStateException("Le modèle officiel 'modele declaration iRSA SAFI.ods' est introuvable.");
    }

    private byte[] injecterDonneesDansOds(byte[] templateZipBytes, List<SafiRowDto> data,
                                         String raisonSociale, String nif, String libellePeriode, String adresse) throws Exception {
        byte[] originalContentXml = null;
        List<ZipEntryData> otherEntries = new ArrayList<>();
        ZipEntryData mimetypeEntry = null;

        // Lecture de tous les éléments du ZIP d'origine
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(templateZipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                byte[] bytes = zis.readAllBytes();
                if ("mimetype".equals(entry.getName())) {
                    mimetypeEntry = new ZipEntryData(entry.getName(), bytes, entry.getMethod());
                } else if ("content.xml".equals(entry.getName())) {
                    originalContentXml = bytes;
                } else {
                    otherEntries.add(new ZipEntryData(entry.getName(), bytes, entry.getMethod()));
                }
                zis.closeEntry();
            }
        }

        if (originalContentXml == null) {
            throw new IllegalStateException("Le fichier content.xml est manquant dans l'archive ODS.");
        }

        // Modification DOM de content.xml
        byte[] modifiedContentXml = modifierContentXml(originalContentXml, data, raisonSociale, nif, libellePeriode, adresse);

        // Reconstruction de l'archive ZIP ODS
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Règle OASIS ODF : 'mimetype' doit être la 1ère entrée, stockée sans compression (STORED)
            if (mimetypeEntry != null) {
                ZipEntry mEntry = new ZipEntry("mimetype");
                mEntry.setMethod(ZipEntry.STORED);
                mEntry.setSize(mimetypeEntry.data.length);
                mEntry.setCompressedSize(mimetypeEntry.data.length);
                CRC32 crc = new CRC32();
                crc.update(mimetypeEntry.data);
                mEntry.setCrc(crc.getValue());
                zos.putNextEntry(mEntry);
                zos.write(mimetypeEntry.data);
                zos.closeEntry();
            }

            // Écriture des autres fichiers inchangés (styles.xml, meta.xml, manifest.xml, etc.)
            for (ZipEntryData other : otherEntries) {
                ZipEntry ze = new ZipEntry(other.name);
                ze.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(ze);
                zos.write(other.data);
                zos.closeEntry();
            }

            // Écriture du nouveau content.xml
            ZipEntry contentEntry = new ZipEntry("content.xml");
            contentEntry.setMethod(ZipEntry.DEFLATED);
            zos.putNextEntry(contentEntry);
            zos.write(modifiedContentXml);
            zos.closeEntry();
        }

        return baos.toByteArray();
    }

    private byte[] modifierContentXml(byte[] xmlBytes, List<SafiRowDto> data,
                                     String raisonSociale, String nif, String libellePeriode, String adresse) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xmlBytes));

        // Récupération de la table du modèle
        NodeList tables = doc.getElementsByTagNameNS(TABLE_NS, "table");
        if (tables.getLength() == 0) {
            throw new IllegalStateException("Aucune balise table:table trouvée dans content.xml");
        }
        Element tableElem = (Element) tables.item(0);

        // Récupération de toutes les lignes table-row
        NodeList rowNodes = tableElem.getElementsByTagNameNS(TABLE_NS, "table-row");
        List<Element> rows = new ArrayList<>();
        for (int i = 0; i < rowNodes.getLength(); i++) {
            rows.add((Element) rowNodes.item(i));
        }

        // Remplacement des métadonnées d'en-tête (Lignes 7, 8, 9)
        remplacerTexteDansLigne(doc, tableElem, "GALAXAM LOGISTICS", raisonSociale);
        remplacerTexteDansLigne(doc, tableElem, "3000019434", nif);
        remplacerTexteDansLigne(doc, tableElem, "JIONA 2026", libellePeriode);
        remplacerTexteDansLigne(doc, tableElem, "TOUR FORELLO TANJOMBATO 3È ETAGE (BUREAU)", adresse);

        // Les lignes 14 à 53 du modèle correspondent aux 40 lignes de données pré-formatées
        // La ligne 54 correspond à la ligne des totaux
        int startDataIdx = 14;
        int endDataIdx = Math.min(53, rows.size() - 2);

        Element totalRow = (rows.size() > 54) ? rows.get(54) : null;

        // Suppression des anciennes lignes template de données (de 53 à 14)
        for (int i = endDataIdx; i >= startDataIdx; i--) {
            tableElem.removeChild(rows.get(i));
        }

        // Génération et insertion des nouvelles lignes de salariés
        DecimalFormat df = new DecimalFormat("0.00", DFS);
        df.setGroupingUsed(true);

        int nbLignes = Math.max(data.size(), 40); // Conserver au minimum 40 lignes pour garder la grille d'origine

        for (int i = 0; i < nbLignes; i++) {
            Element newRow = doc.createElementNS(TABLE_NS, "table:table-row");
            newRow.setAttributeNS(TABLE_NS, "table:style-name", "ro3");

            if (i < data.size()) {
                SafiRowDto rowDto = data.get(i);
                construireLigneSalarie(doc, newRow, rowDto, df);
            } else {
                construireLigneVide(doc, newRow, i + 1);
            }

            if (totalRow != null) {
                tableElem.insertBefore(newRow, totalRow);
            } else {
                tableElem.appendChild(newRow);
            }
        }

        // Sérialisation DOM vers byte[]
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    private void remplacerTexteDansLigne(Document doc, Element root, String oldText, String newText) {
        if (newText == null || newText.trim().isEmpty() || oldText.equals(newText)) return;
        NodeList pNodes = root.getElementsByTagNameNS(TEXT_NS, "p");
        for (int i = 0; i < pNodes.getLength(); i++) {
            Node p = pNodes.item(i);
            if (p.getTextContent() != null && p.getTextContent().contains(oldText)) {
                p.setTextContent(p.getTextContent().replace(oldText, newText));
            }
        }
    }

    private void construireLigneSalarie(Document doc, Element row, SafiRowDto d, DecimalFormat df) {
        // Col A (0) : N° Ordre
        row.appendChild(createTextCell(doc, "ce2", String.valueOf(d.getNumeroOrdre())));
        // Col B (1) : N° Mle
        row.appendChild(createTextCell(doc, "ce12", d.getMatricule()));
        // Col C (2) : N° Affiliation CNaPS
        row.appendChild(createTextCell(doc, "ce18", d.getAffiliationCnaps()));
        // Col D (3) : Nom et Prénoms
        row.appendChild(createTextCell(doc, "ce24", d.getNomPrenoms()));
        // Col E (4) : CIN / Carte résident
        row.appendChild(createTextCell(doc, "ce18", d.getCin()));
        // Col F (5) : Date d'embauche
        row.appendChild(createTextCell(doc, "ce39", d.getDateEmbauche()));
        // Col G (6) : Date de débauchage
        row.appendChild(createTextCell(doc, "ce45", d.getDateDebauchage()));
        // Col H (7) : Fonction
        row.appendChild(createTextCell(doc, "ce51", d.getFonction()));

        // Col I (8) : Salaire de base
        row.appendChild(createNumericCell(doc, "ce58", d.getSalaireBase(), df));
        // Col J (9) : Indemnités imposables
        row.appendChild(createNumericCell(doc, "ce58", d.getIndemnitesImposables(), df));
        // Col K (10) : Indemnités non imposables
        row.appendChild(createNumericCell(doc, "ce58", d.getIndemnitesNonImposables(), df));
        // Col L (11) : Avantages en nature imposables
        row.appendChild(createNumericCell(doc, "ce58", d.getAvantagesNatureImposables(), df));
        // Col M (12) : Avantages en nature exonérés
        row.appendChild(createNumericCell(doc, "ce58", d.getAvantagesNatureExoneres(), df));
        // Col N (13) : Heures supplémentaires
        row.appendChild(createNumericCell(doc, "ce58", d.getHeuresSupplementaires(), df));
        // Col O (14) : Primes et gratifications
        row.appendChild(createNumericCell(doc, "ce58", d.getPrimesEtGratifications(), df));
        // Col P (15) : Autres (Repas, etc.)
        row.appendChild(createNumericCell(doc, "ce58", d.getAutres(), df));

        // Col Q (16) : Salaire brut
        row.appendChild(createNumericCell(doc, "ce70", d.getSalaireBrut(), df));
        // Col R (17) : Déduction CNaPS
        row.appendChild(createNumericCell(doc, "ce77", d.getDeductionsCnaps(), df));
        // Col S (18) : Déduction OSTIE / OMSI
        row.appendChild(createNumericCell(doc, "ce58", d.getDeductionsSante(), df));
        // Col T (19) : Salaire net
        row.appendChild(createNumericCell(doc, "ce70", d.getSalaireNet(), df));
        // Col U (20) : Autre déduction
        row.appendChild(createNumericCell(doc, "ce70", d.getAutreDeduction(), df));
        // Col V (21) : Montant imposable
        row.appendChild(createNumericCell(doc, "ce70", d.getMontantImposable(), df));
        // Col W (22) : Impôt correspondant
        row.appendChild(createNumericCell(doc, "ce86", d.getImpotCorrespondant(), df));
        // Col X (23) : Réduction charge de famille
        row.appendChild(createNumericCell(doc, "ce70", d.getReductionChargeFamille(), df));
        // Col Y (24) : Impôt dû
        row.appendChild(createNumericCell(doc, "ce90", d.getImpotDu(), df));

        // Cellule répétée pour remplir la largeur de la feuille
        Element endCell = doc.createElementNS(TABLE_NS, "table:table-cell");
        endCell.setAttributeNS(TABLE_NS, "table:number-columns-repeated", "993");
        row.appendChild(endCell);
    }

    private void construireLigneVide(Document doc, Element row, int numeroOrdre) {
        row.appendChild(createTextCell(doc, "ce2", String.valueOf(numeroOrdre)));
        row.appendChild(createTextCell(doc, "ce12", ""));
        row.appendChild(createTextCell(doc, "ce18", ""));
        row.appendChild(createTextCell(doc, "ce24", ""));
        row.appendChild(createTextCell(doc, "ce18", ""));
        row.appendChild(createTextCell(doc, "ce39", ""));
        row.appendChild(createTextCell(doc, "ce45", ""));
        row.appendChild(createTextCell(doc, "ce51", ""));

        // 17 cellules numériques à zéro
        for (int c = 8; c <= 24; c++) {
            Element numCell = doc.createElementNS(TABLE_NS, "table:table-cell");
            numCell.setAttributeNS(TABLE_NS, "table:style-name", "ce58");
            row.appendChild(numCell);
        }

        Element endCell = doc.createElementNS(TABLE_NS, "table:table-cell");
        endCell.setAttributeNS(TABLE_NS, "table:number-columns-repeated", "993");
        row.appendChild(endCell);
    }

    private Element createTextCell(Document doc, String styleName, String text) {
        Element cell = doc.createElementNS(TABLE_NS, "table:table-cell");
        if (styleName != null) {
            cell.setAttributeNS(TABLE_NS, "table:style-name", styleName);
        }
        cell.setAttributeNS(OFFICE_NS, "office:value-type", "string");
        Element p = doc.createElementNS(TEXT_NS, "text:p");
        p.setTextContent(text != null ? text : "");
        cell.appendChild(p);
        return cell;
    }

    private Element createNumericCell(Document doc, String styleName, BigDecimal value, DecimalFormat df) {
        Element cell = doc.createElementNS(TABLE_NS, "table:table-cell");
        if (styleName != null) {
            cell.setAttributeNS(TABLE_NS, "table:style-name", styleName);
        }
        cell.setAttributeNS(OFFICE_NS, "office:value-type", "float");
        BigDecimal v = value != null ? value : BigDecimal.ZERO;
        cell.setAttributeNS(OFFICE_NS, "office:value", v.setScale(2, RoundingMode.HALF_UP).toPlainString());
        Element p = doc.createElementNS(TEXT_NS, "text:p");
        p.setTextContent(df.format(v));
        cell.appendChild(p);
        return cell;
    }

    private static class ZipEntryData {
        String name;
        byte[] data;

        ZipEntryData(String name, byte[] data, int method) {
            this.name = name;
            this.data = data;
        }
    }
}
