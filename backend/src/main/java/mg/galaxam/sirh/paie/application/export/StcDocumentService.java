package mg.galaxam.sirh.paie.application.export;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.paie.persistence.PaieDossierSortie;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.CertificatTravailDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Documents de sortie : lectures seulement, sans recalcul ni mutation du STC. */
@Service
public class StcDocumentService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final ObjectMapper json = new ObjectMapper();

    @Autowired(required = false)
    private DocumentRhService documentRhService;

    public StcDocumentService() {}

    public StcDocumentService(DocumentRhService documentRhService) {
        this.documentRhService = documentRhService;
    }

    public enum Type { DECOMPTE, RECU, CERTIFICAT, BORDEREAU }

    public byte[] pdf(Integer idDossier, Type type, Connection c) throws Exception {
        PaieDossierSortie dossier = dossier(idDossier, c);
        return switch (type) {
            case DECOMPTE -> financier(dossier, false, c);
            case RECU -> financier(dossier, true, c);
            case CERTIFICAT -> render("CERTIFICAT DE TRAVAIL", certificatLines(dossier, c));
            case BORDEREAU -> bordereau(dossier, c);
        };
    }

    public byte[] certificatDocx(Integer idDossier, Connection c) throws Exception {
        PaieDossierSortie dossier = dossier(idDossier, c);
        if (documentRhService != null && dossier.getIdContrat() != null) {
            CertificatTravailDTO dto = documentRhService.preparerCertificat(dossier.getIdContrat(), "globale", c);
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                documentRhService.genererCertificatDocx(dto, out);
                return out.toByteArray();
            }
        }
        List<String> lines = certificatLines(dossier, c);
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph heading = document.createParagraph();
            heading.createRun().setText("CERTIFICAT DE TRAVAIL");
            for (String line : lines) document.createParagraph().createRun().setText(line);
            document.write(out);
            return out.toByteArray();
        }
    }

    public byte[] justificatif(Integer idDossier, Integer idReglement, Connection c) throws Exception {
        PaieDossierSortie dossier = dossier(idDossier, c);
        if (dossier.getIdStc() == null) throw blocked("Aucun STC scellé pour ce dossier.");
        String sql = "SELECT r.id_reglement,r.reference_reglement,r.date_reglement,r.created_at,r.mode_reglement,"
                + "r.montant_regle,r.est_annule,r.date_annulation,r.motif_annulation,s.net_a_payer_stc,s.numero_recu,"
                + "b.raison_sociale,bp.nom_prenom,bp.matricule FROM paie_stc_reglement r JOIN paie_stc s ON s.id_stc=r.id_stc "
                + "LEFT JOIN bulletin_paie_employeur_snapshot b ON b.id_bulletin=s.id_bulletin "
                + "LEFT JOIN bulletin_paie bp ON bp.id_bulletin=s.id_bulletin "
                + "WHERE r.id_reglement=? AND r.id_stc=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idReglement);
            ps.setInt(2, dossier.getIdStc());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw missing("Règlement introuvable pour ce dossier.");
                String employeur = rs.getString("raison_sociale");
                if (employeur == null) throw blocked("Identité historique de l'employeur indisponible.");
                Timestamp enregistrement = rs.getTimestamp("created_at");
                BigDecimal cumul = cumulAuMoment(dossier.getIdStc(), idReglement, enregistrement, c);
                BigDecimal net = rs.getBigDecimal("net_a_payer_stc");
                List<String> lines = new ArrayList<>();
                lines.add("## IDENTIFICATION");
                lines.add("Employeur : " + employeur);
                lines.add("Salarié : " + safe(rs.getString("nom_prenom")) + " (" + safe(rs.getString("matricule")) + ")");
                lines.add("Référence STC : " + safe(rs.getString("numero_recu")));
                lines.add("## RÈGLEMENT ENREGISTRÉ");
                String referenceReglement = rs.getString("reference_reglement");
                lines.add("Date du versement : " + date(rs.getDate("date_reglement").toLocalDate()));
                lines.add("Mode de règlement : " + modeReglement(rs.getString("mode_reglement")));
                lines.add("Référence de transaction : " + (referenceReglement == null
                        || referenceReglement.isBlank() || referenceReglement.equalsIgnoreCase("non")
                        ? "Non renseignée" : referenceReglement));
                lines.add("N° d'écriture : " + idReglement);
                lines.add("## MONTANT DU RÈGLEMENT");
                lines.add("!! " + money(rs.getBigDecimal("montant_regle")));
                lines.add("## SITUATION DU SOLDE");
                lines.add("Cumul des versements à cette date : " + money(cumul));
                lines.add("Reste à payer à cette date : " + money(net.subtract(cumul).max(BigDecimal.ZERO)));
                lines.add("%% Cette quittance récapitule le règlement inscrit au dossier STC à la date indiquée. Elle ne remplace pas un justificatif bancaire ni un reçu signé par le salarié. Les versements postérieurs ne figurent pas dans ce cumul.");
                if (rs.getBoolean("est_annule")) {
                    lines.add("## ÉCRITURE ANNULÉE");
                    lines.add("ÉCRITURE ANNULÉE le " + rs.getTimestamp("date_annulation"));
                    lines.add("Motif : " + rs.getString("motif_annulation"));
                    lines.add("%% Cette quittance ne constate plus un paiement actif.");
                } else {
                    lines.add("## Validation de l'employeur");
                    lines.add("%% Nom, qualité, date et signature : __________________________________________");
                }
                return renderStructured("QUITTANCE DE RÈGLEMENT STC",
                        "Document de constatation du règlement enregistré - modèle à valider", lines, employeur);
            }
        }
    }

    private BigDecimal cumulAuMoment(int idStc, int idReglement, Timestamp instant, Connection c) throws Exception {
        String sql = "SELECT COALESCE(SUM(montant_regle),0) FROM paie_stc_reglement "
                + "WHERE id_stc=? AND (created_at < ? OR (created_at = ? AND id_reglement <= ?)) "
                + "AND (date_annulation IS NULL OR date_annulation > ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idStc); ps.setTimestamp(2, instant); ps.setTimestamp(3, instant);
            ps.setInt(4, idReglement); ps.setTimestamp(5, instant);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1); }
        }
    }

    private byte[] financier(PaieDossierSortie dossier, boolean recu, Connection c) throws Exception {
        if (dossier.getIdStc() == null) throw blocked("Aucun STC scellé pour ce dossier.");
        String sql = "SELECT s.*,b.nom_prenom,b.matricule,e.raison_sociale FROM paie_stc s "
                + "LEFT JOIN bulletin_paie b ON b.id_bulletin=s.id_bulletin "
                + "LEFT JOIN bulletin_paie_employeur_snapshot e ON e.id_bulletin=s.id_bulletin "
                + "WHERE s.id_stc=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, dossier.getIdStc());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !List.of("VALIDE", "REGLE").contains(rs.getString("statut")))
                    throw blocked("Le STC doit être scellé avant l'édition du document.");
                if (rs.getString("nom_prenom") == null || rs.getString("raison_sociale") == null)
                    throw blocked("Identité historique salarié ou employeur manquante pour ce STC.");
                List<String> lines = new ArrayList<>();
                lines.add("## Identification");
                lines.add("Employeur : " + rs.getString("raison_sociale"));
                lines.add("Salarié : " + rs.getString("nom_prenom") + " (" + rs.getString("matricule") + ")");
                lines.add("Référence STC : " + rs.getString("numero_recu"));
                lines.add("Date de scellement : " + date(rs.getTimestamp("date_validation").toLocalDateTime().toLocalDate()));
                lines.add("## Éléments bruts");
                lines.add("Salaire proratisé : " + money(rs.getBigDecimal("salaire_prorata")));
                lines.add("Congés payés : " + money(rs.getBigDecimal("indemnite_conges_payes")));
                lines.add("Préavis : " + money(rs.getBigDecimal("indemnite_preavis")));
                lines.add("Licenciement : " + money(rs.getBigDecimal("indemnite_licenciement")));
                lines.add("Autres indemnités : " + money(rs.getBigDecimal("autres_indemnites")));
                if (rs.getString("motif_autres_indemnites") != null)
                    lines.add("Motif des autres indemnités : " + rs.getString("motif_autres_indemnites"));
                lines.add("Total brut scellé : " + money(rs.getBigDecimal("total_brut_stc")));
                lines.add("## Retenues et déductions");
                lines.add("Cotisations sociales : " + money(rs.getBigDecimal("total_cotisations_sociales")));
                lines.add("IRSA : " + money(rs.getBigDecimal("total_irsa")));
                lines.add("Avances déduites : " + money(rs.getBigDecimal("solde_avances_deduit")));
                lines.add("Autres déductions : " + money(rs.getBigDecimal("autres_deductions")));
                if (rs.getString("motif_autres_deductions") != null)
                    lines.add("Motif des autres déductions : " + rs.getString("motif_autres_deductions"));
                lines.add("## Synthèse du STC");
                lines.add("Solde du bulletin mensuel : " + money(rs.getBigDecimal("solde_bulletin_a_regler")));
                lines.add("NET À PAYER STC SCELLÉ : " + money(rs.getBigDecimal("net_a_payer_stc")));
                if (recu) {
                    BigDecimal verse = totalActif(dossier.getIdStc(), c);
                    BigDecimal reste = rs.getBigDecimal("net_a_payer_stc").subtract(verse).max(BigDecimal.ZERO);
                    lines.add("## Situation des règlements au " + date(LocalDate.now()));
                    lines.add("Versements enregistrés à l'édition : " + money(verse));
                    lines.add("Reste à payer à l'édition : " + money(reste));
                    lines.add("## Mentions");
                    lines.add("Les montants affichés proviennent du STC scellé ; ils ne sont pas recalculés.");
                    lines.add("État récapitulatif à la date d'édition ; aucune remise ni signature n'est constatée.");
                    lines.add("Délai légal de dénonciation : deux (02) mois suivant sa délivrance (Loi n° 2024-014).");
                } else {
                    lines.add("## Mentions");
                    lines.add("Les montants affichés proviennent du STC scellé ; ils ne sont pas recalculés.");
                    lines.add("Ce décompte ne constitue pas une preuve de paiement.");
                }
                return renderStructured(recu ? "REÇU POUR SOLDE DE TOUT COMPTE - PROJET" :
                        "DÉCOMPTE DE LIQUIDATION DU SOLDE DE TOUT COMPTE", lines, rs.getString("raison_sociale"));
            }
        }
    }

    private BigDecimal totalActif(int idStc, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(montant_regle),0) FROM paie_stc_reglement WHERE id_stc=? AND est_annule=FALSE")) {
            ps.setInt(1, idStc);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1); }
        }
    }

    private List<String> certificatLines(PaieDossierSortie dossier, Connection c) throws Exception {
        if (dossier.getIdContrat() == null || dossier.getDateDepart() == null)
            throw blocked("Contrat et date de départ requis pour le certificat.");
        String sql = "SELECT e.nom,e.prenom,ct.date_debut,ct.date_fin,ct.numero_contrat,sr.code AS statut_contrat,"
                + "COALESCE(es.raison_sociale,f.raison_sociale) AS employeur "
                + "FROM contrat_travail ct JOIN employe e ON e.id_employe=ct.id_employe "
                + "JOIN ref_statut_rh sr ON sr.id_statut_rh=ct.id_statut_rh "
                + "JOIN ref_filiale f ON f.id_filiale=ct.id_filiale "
                + "LEFT JOIN paie_stc s ON s.id_stc=? "
                + "LEFT JOIN bulletin_paie_employeur_snapshot es ON es.id_bulletin=s.id_bulletin "
                + "WHERE ct.id_contrat=? AND ct.id_employe=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, dossier.getIdStc()); ps.setInt(2, dossier.getIdContrat());
            ps.setInt(3, dossier.getIdEmploye());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !List.of("ROMPU", "CLOTURE").contains(rs.getString("statut_contrat"))
                        || rs.getDate("date_fin") == null
                        || rs.getDate("date_fin").after(dossier.getDateDepart()))
                    throw blocked("La cessation effective du contrat n'est pas enregistrée.");
                List<String> lines = new ArrayList<>();
                lines.add("Employeur : " + rs.getString("employeur"));
                lines.add("Salarié : " + rs.getString("nom") + " " + rs.getString("prenom"));
                lines.add("Entrée : " + date(rs.getDate("date_debut").toLocalDate()));
                lines.add("Sortie : " + date(rs.getDate("date_fin").toLocalDate()));
                lines.add("Emplois successifs et périodes :");
                String jobs = "SELECT a.date_debut,a.date_fin,f.libelle AS fonction,cp.libelle AS categorie "
                        + "FROM employe_affectation_carriere a LEFT JOIN ref_fonction f ON f.id_fonction=a.id_fonction "
                        + "LEFT JOIN ref_categorie_professionnelle cp ON cp.id_categorie_professionnelle=a.id_categorie_professionnelle "
                        + "WHERE a.id_contrat=? AND a.date_debut<=? ORDER BY a.date_debut";
                try (PreparedStatement jp = c.prepareStatement(jobs)) {
                    jp.setInt(1, dossier.getIdContrat()); jp.setDate(2, rs.getDate("date_fin"));
                    try (ResultSet jr = jp.executeQuery()) {
                        while (jr.next()) {
                            LocalDate fin = jr.getDate("date_fin") == null ? rs.getDate("date_fin").toLocalDate()
                                    : jr.getDate("date_fin").toLocalDate();
                            if (fin.isAfter(rs.getDate("date_fin").toLocalDate())) fin = rs.getDate("date_fin").toLocalDate();
                            lines.add(date(jr.getDate("date_debut").toLocalDate()) + " au " + date(fin)
                                    + " : " + safe(jr.getString("fonction")) + " / " + safe(jr.getString("categorie")));
                        }
                    }
                }
                if (lines.size() == 5) throw blocked("Historique des emplois indisponible pour le certificat.");
                return lines;
            }
        }
    }

    private byte[] bordereau(PaieDossierSortie dossier, Connection c) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("Dossier de sortie : " + dossier.getIdDossierSortie());
        lines.add("Date de départ enregistrée : " + date(dossier.getDateDepart().toLocalDate()));
        lines.add("Certificat délivré : " + Boolean.TRUE.equals(dossier.getCertificatDelivre()));
        if (Boolean.TRUE.equals(dossier.getCertificatDelivre()))
            lines.add("Date : " + dossier.getDateDelivranceCertificat() + " ; mode : " + safe(dossier.getModeRemiseCertificat()));
        lines.add("Reçu STC remis : " + Boolean.TRUE.equals(dossier.getRecuStcRemis()));
        if (Boolean.TRUE.equals(dossier.getRecuStcRemis())) lines.add("Date : " + dossier.getDateRemiseRecu());
        lines.add("La génération de ce document ne constate aucune remise ni signature.");
        return render("BORDEREAU DE REMISE - ÉTAT ENREGISTRÉ", lines);
    }

    private PaieDossierSortie dossier(Integer id, Connection c) throws Exception {
        PaieDossierSortie d = PaieDossierSortie.trouverParId(id, c);
        if (d == null) throw missing("Dossier de sortie introuvable : " + id);
        return d;
    }

    private static String safe(String value) { return value == null ? "Non renseigné" : value; }
    private static String modeReglement(String value) {
        if (value == null) return "Non renseigné";
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "VIREMENT" -> "Virement";
            case "CHEQUE" -> "Chèque";
            case "ESPECES" -> "Espèces";
            default -> value;
        };
    }
    private static String money(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value == null ? BigDecimal.ZERO : value) + " Ar";
    }
    private static String date(LocalDate value) { return value.format(DATE); }
    private static SirhBusinessException missing(String message) { return new SirhBusinessException(SirhErrorCode.RESOURCE_NOT_FOUND, message); }
    private static SirhBusinessException blocked(String message) { return new SirhBusinessException(SirhErrorCode.BUSINESS_STATE_TRANSITION_BLOCKED, message); }

    private byte[] renderStructured(String title, List<String> lines) throws Exception {
        return renderStructured(title, null, lines);
    }

    private byte[] renderStructured(String title, List<String> lines, String nomSociete) throws Exception {
        return renderStructured(title, null, lines, nomSociete);
    }

    private byte[] renderStructured(String title, String subtitle, List<String> lines) throws Exception {
        return renderStructured(title, subtitle, lines, null);
    }

    private byte[] renderStructured(String title, String subtitle, List<String> lines, String nomSociete) throws Exception {
        String company = (nomSociete != null && !nomSociete.isBlank()) ? nomSociete : "GALAXAM LOGISTICS";
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPageContentStream stream = null;
            float y = 0;
            String reference = lines.stream()
                    .filter(line -> line.startsWith("Référence STC : "))
                    .map(line -> line.substring("Référence STC : ".length()))
                    .findFirst().orElse("Non renseignée");
            try {
                for (String line : lines) {
                    boolean heading = line.startsWith("## ");
                    boolean featuredAmount = line.startsWith("!! ");
                    boolean paragraph = line.startsWith("%% ");
                    String content = featuredAmount || paragraph ? line.substring(3) : line;
                    int separator = heading || featuredAmount || paragraph ? -1 : line.indexOf(':');
                    String label = separator >= 0 ? line.substring(0, separator).trim() : line;
                    String value = separator >= 0 ? line.substring(separator + 1).trim() : "";
                    boolean prominent = label.startsWith("NET À PAYER") || label.startsWith("Total brut scellé")
                            || label.startsWith("Montant enregistré") || label.startsWith("Reste à payer");
                    boolean cancelled = label.startsWith("ÉCRITURE ANNULÉE");
                    int labelLines = wrappedLines(label, 220, 10, prominent).size();
                    int valueLines = value.isEmpty() ? 0 : wrappedLines(value, 244, prominent ? 12 : 11, prominent).size();
                    int proseLines = separator < 0 && !heading && !featuredAmount
                            ? wrappedLines(content, 500, 10, cancelled).size() : 0;
                    float height = heading ? (subtitle == null ? 24 : 38)
                            : featuredAmount ? 55
                            : paragraph ? proseLines * 15 + (subtitle == null ? 22 : 28)
                            : separator >= 0 ? Math.max(labelLines, valueLines) * 14 + (subtitle == null ? 4 : 10)
                            : proseLines * 14 + 4;
                    if (stream == null || y - height - (heading ? 28 : 0) < 76) {
                        if (stream != null) stream.close();
                        PDPage page = new PDPage(PDRectangle.A4);
                        doc.addPage(page);
                        stream = new PDPageContentStream(doc, page);
                        y = structuredHeader(stream, title, subtitle, reference, company);
                    }
                    if (heading) {
                        stream.setNonStrokingColor(0.04f, 0.23f, 0.32f);
                        drawWrapped(stream, line.substring(3), 44, y - (subtitle == null ? 16 : 23), 507,
                                subtitle == null ? 11 : 12, true);
                        y -= height;
                        continue;
                    }
                    if (featuredAmount) {
                        stream.setNonStrokingColor(0.04f, 0.23f, 0.32f);
                        drawWrapped(stream, content, 44, y - 25, 507, 23, true);
                        y -= height;
                        continue;
                    }
                    if (paragraph) {
                        stream.setNonStrokingColor(0.38f, 0.44f, 0.48f);
                        drawWrapped(stream, content, 44, y - 14, 500, 10, false);
                        y -= height;
                        continue;
                    }
                    if (prominent || cancelled) {
                        stream.setNonStrokingColor(cancelled ? 1f : 0.96f, cancelled ? 0.95f : 0.97f, cancelled ? 0.95f : 0.98f);
                        stream.addRect(44, y - height + 1, 507, height);
                        stream.fill();
                    }
                    stream.setNonStrokingColor(cancelled ? 0.62f : 0.27f, cancelled ? 0.10f : 0.33f, cancelled ? 0.10f : 0.37f);
                    if (separator >= 0) {
                        drawWrapped(stream, label, 52, y - 14, 220, 10, false);
                        stream.setNonStrokingColor(cancelled ? 0.62f : 0.09f, cancelled ? 0.10f : 0.14f, cancelled ? 0.10f : 0.18f);
                        if (value.endsWith(" Ar") && valueLines == 1) {
                            drawRight(stream, value, 543, y - 14, prominent ? 12 : 11, prominent);
                        } else {
                            drawWrapped(stream, value, 300, y - 14, 244, prominent ? 12 : 11, prominent);
                        }
                    } else {
                        drawWrapped(stream, line, 52, y - 13, 490, 10, cancelled);
                    }
                    y -= height;
                    stream.setStrokingColor(0.88f, 0.91f, 0.93f);
                    stream.moveTo(44, y);
                    stream.lineTo(551, y);
                    stream.stroke();
                }
            } finally {
                if (stream != null) stream.close();
            }
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                try (PDPageContentStream footer = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    footer.setStrokingColor(0.83f, 0.87f, 0.89f);
                    footer.moveTo(44, 58);
                    footer.lineTo(551, 58);
                    footer.stroke();
                    footer.setNonStrokingColor(0.42f, 0.48f, 0.52f);
                    drawWrapped(footer, company + "  |  " + reference + "  |  Édité le " + date(LocalDate.now()),
                            44, 43, 440, 8, false);
                    drawRight(footer, "Page " + (i + 1) + " / " + doc.getNumberOfPages(), 551, 43, 8, false);
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private float structuredHeader(PDPageContentStream stream, String title, String subtitle, String reference, String company) throws Exception {
        stream.setNonStrokingColor(0.04f, 0.23f, 0.32f);
        drawWrapped(stream, company, 44, 804, 507, 11, true);
        float titleY = subtitle == null ? 776 : 744;
        drawWrapped(stream, title, 44, titleY, 507, subtitle == null ? 16 : 19, true);
        float referenceY = titleY - wrappedLines(title, 507, subtitle == null ? 16 : 19, true).size() * 21 - 2;
        stream.setNonStrokingColor(0.42f, 0.48f, 0.52f);
        if (subtitle != null) {
            drawWrapped(stream, subtitle, 44, referenceY, 507, 10, false);
            return referenceY - 48;
        }
        drawWrapped(stream, "Référence du dossier : " + reference, 44, referenceY, 507, 9, false);
        return referenceY - 30;
    }

    private void drawRight(PDPageContentStream stream, String value, float right, float y,
                           int size, boolean bold) throws Exception {
        String normalized = normalizedPdfText(value);
        PDType1Font font = bold ? BOLD : REGULAR;
        float width = font.getStringWidth(normalized) * size / 1000f;
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(right - width, y);
        stream.showText(normalized);
        stream.endText();
    }

    private String normalizedPdfText(String value) {
        return safe(value).replace("œ", "oe").replace("Œ", "OE")
                .replace("’", "'").replace("–", "-").replace("—", "-")
                .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", " ").trim();
    }

    private List<String> wrappedLines(String value, float width, int size, boolean bold) throws Exception {
        String normalized = normalizedPdfText(value);
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        PDType1Font font = bold ? BOLD : REGULAR;
        for (String word : normalized.split("\\s+")) {
            if (font.getStringWidth(word) * size / 1000f > width) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                for (char character : word.toCharArray()) {
                    String nextPart = current.toString() + character;
                    if (!current.isEmpty() && font.getStringWidth(nextPart) * size / 1000f > width) {
                        result.add(current.toString());
                        current.setLength(0);
                    }
                    current.append(character);
                }
                continue;
            }
            String next = current.isEmpty() ? word : current + " " + word;
            if (!current.isEmpty() && font.getStringWidth(next) * size / 1000f > width) {
                result.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(next);
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result.isEmpty() ? List.of("") : result;
    }

    private void drawWrapped(PDPageContentStream stream, String value, float x, float y,
                             float width, int size, boolean bold) throws Exception {
        for (String line : wrappedLines(value, width, size, bold)) {
            stream.beginText();
            stream.setFont(bold ? BOLD : REGULAR, size);
            stream.newLineAtOffset(x, y);
            stream.showText(line);
            stream.endText();
            y -= 15;
        }
    }

    byte[] render(String title, List<String> lines) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4); doc.addPage(page);
            PDPageContentStream stream = new PDPageContentStream(doc, page);
            float y = header(stream, title);
            for (String line : lines) {
                if (y < 85) {
                    stream.close(); page = new PDPage(PDRectangle.A4); doc.addPage(page);
                    stream = new PDPageContentStream(doc, page); y = header(stream, title + " - suite");
                }
                boolean emphasis = line.startsWith("NET ") || line.startsWith("Reste à payer")
                        || line.startsWith("ÉCRITURE ANNULÉE");
                if (emphasis) {
                    stream.setNonStrokingColor(0xF2 / 255f, 0xF7 / 255f, 0xF9 / 255f);
                    stream.addRect(36, y - 12, 523, 30); stream.fill();
                }
                stream.setNonStrokingColor(0x18 / 255f, 0x24 / 255f, 0x2D / 255f);
                y = write(stream, line, 44, y, emphasis ? 11 : 10, emphasis) - 8;
            }
            stream.close(); doc.save(out); return out.toByteArray();
        }
    }

    private float header(PDPageContentStream stream, String title) throws Exception {
        stream.setNonStrokingColor(0x07 / 255f, 0x32 / 255f, 0x4A / 255f);
        stream.addRect(0, 754, 595, 88); stream.fill();
        stream.setNonStrokingColor(1, 1, 1);
        write(stream, "GALAXAM SIRH", 44, 810, 11, true);
        write(stream, title, 44, 780, 12, true);
        stream.setNonStrokingColor(0x18 / 255f, 0x24 / 255f, 0x2D / 255f);
        return 728;
    }

    private float write(PDPageContentStream stream, String value, float x, float y, int size, boolean bold) throws Exception {
        String normalized = java.text.Normalizer.normalize(safe(value), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^\\x20-\\x7E]", " ");
        int max = bold ? 68 : 95;
        for (int offset = 0; offset < normalized.length(); offset += max) {
            stream.beginText(); stream.setFont(bold ? BOLD : REGULAR, size);
            stream.newLineAtOffset(x, y); stream.showText(normalized.substring(offset, Math.min(offset + max, normalized.length())));
            stream.endText(); y -= size + 4;
        }
        return y;
    }
}
