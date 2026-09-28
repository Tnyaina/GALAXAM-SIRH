package mg.galaxam.sirh.contrat.application;

import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.contrat.api.dto.ContratTravailDocumentDTO;
import org.springframework.core.env.Environment;
import mg.galaxam.sirh.contrat.persistence.RefTypeDocument;
import mg.galaxam.sirh.contrat.persistence.WorkflowRh;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.regex.Pattern;


import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.AttestationTravailDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.CertificatTravailDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.EmployeDocumentDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.EntrepriseInfoDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.PosteHistoriqueDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.SignataireDTO;
import mg.galaxam.sirh.contrat.persistence.ContratTravail;
import mg.galaxam.sirh.contrat.persistence.EmployeAffectationCarriere;
import mg.galaxam.sirh.contrat.persistence.Employe;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DocumentRhService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRhService.class);
    private static final DateTimeFormatter FMT_FR = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter FMT_COURT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public DocumentRhService() {
    }

    public static String formatDateFr(LocalDate d) {
        if (d == null) return "—";
        return d.format(FMT_FR);
    }

    public static String calculerDureeTexte(LocalDate debut, LocalDate fin) {
        if (debut == null) return "—";
        LocalDate refFin = fin != null ? fin : LocalDate.now();
        if (refFin.isBefore(debut)) return "—";

        Period p = Period.between(debut, refFin);
        int ans = p.getYears();
        int mois = p.getMonths();
        int jours = p.getDays();

        StringBuilder sb = new StringBuilder();
        if (ans > 0) {
            sb.append(ans).append(ans > 1 ? " ans" : " an");
        }
        if (mois > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(mois).append(" mois");
        }
        if (ans == 0 && mois == 0) {
            sb.append(jours).append(jours > 1 ? " jours" : " jour");
        }
        return sb.toString();
    }

    private static final Map<String, String> CONTRACT_TEMPLATES = Map.of(
            "CDI", "Contrat_CDI_modele.docx", "CDD", "Contrat_CDD_modele.docx",
            "STAGE", "Contrat_STAGE_modele.docx", "PRESTATION", "Contrat_PRESTATION_modele.docx",
            "TEMPORAIRE", "Contrat_TEMPORAIRE_modele.docx");
    // Présentation de la donnée source uniquement ; aucune formule de paie.
    private static final Map<String, String> REMUNERATION_UNITS = Map.of(
            "MENSUEL", "par mois", "HORAIRE", "par heure", "JOURNALIER", "par jour");
    private static final Map<String, String> MANUAL_CONTRACT_FIELDS = Map.of(
            "{{SIGNATAIRE_NOM}}", "[Nom du signataire à compléter avant signature]",
            "{{SIGNATAIRE_FONCTION}}", "[Fonction du signataire à compléter avant signature]",
            "{{SIGNATAIRE_VILLE}}", "[Ville de signature à compléter avant signature]");
    private static final Pattern CONTRACT_VARIABLE = Pattern.compile("\\{\\{[^{}]+}}");

    @Autowired
    private Environment environment;

    public static String templateContrat(String code) {
        String template = code == null ? null : CONTRACT_TEMPLATES.get(code);
        if (template == null) throw new SirhBusinessException(
                SirhErrorCode.CONTRACT_TEMPLATE_UNSUPPORTED,
                "Type de contrat non supporté : " + code);
        return template;
    }

    private String configurationContrat(String scope, String key) {
        return environment == null ? null : environment.getProperty("app.documents.contrats." + scope + "." + key);
    }

    public ContratTravailDocumentDTO preparerContrat(Integer idContrat, Connection c) throws Exception {
        ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
        if (contrat == null) throw new SirhBusinessException(
                SirhErrorCode.CONTRAT_NON_TROUVE);
        Employe emp = new Employe().rechercherParId(contrat.getIdEmploye(), c);
        if (emp == null) throw new SirhBusinessException(
                SirhErrorCode.EMPLOYE_NON_TROUVE);
        String code = valeurReference("ref_type_contrat", "id_type_contrat", "code", contrat.getIdTypeContrat(), c);
        String template = templateContrat(code);
        // Un contrat peut avoir plusieurs affectations après mutations. Le document initial utilise
        // celle créée avec le contrat, jamais la dernière affectation ni l'affectation active.
        EmployeAffectationCarriere aff = null;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT a.id_affectation FROM employe_affectation_carriere a " +
                "JOIN ref_statut_rh s ON s.id_statut_rh = a.id_statut_rh " +
                "WHERE a.id_contrat = ? AND s.code <> 'ANNULE' ORDER BY a.date_debut, a.id_affectation LIMIT 1")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) aff = new EmployeAffectationCarriere().rechercherParId(rs.getInt(1), c);
            }
        }
        if (aff == null || !contrat.getIdEmploye().equals(aff.getIdEmploye())) {
            throw new SirhBusinessException(
                    SirhErrorCode.CONTRACT_ASSIGNMENT_MISSING);
        }
        Map<String, String> vars = new LinkedHashMap<>();
        putContrat(vars, "CONTRAT_NUMERO", contrat.getNumeroContrat());
        putContrat(vars, "CONTRAT_TYPE", code);
        putContrat(vars, "CONTRAT_DATE_DEBUT", dateContrat(contrat.getDateDebut()));
        putContrat(vars, "CONTRAT_DATE_FIN", contrat.getDateFin() == null && "CDI".equals(code) ? "Durée indéterminée" : dateContrat(contrat.getDateFin()));
        putContrat(vars, "CONTRAT_DATE_SIGNATURE", dateContrat(contrat.getDateSignature()));
        putContrat(vars, "CONTRAT_DUREE_ESSAI", contrat.getDureeEssaiMois() == null ? (contrat.getDateFinEssai() == null ? "Aucune période d’essai prévue" : null) : (contrat.getDureeEssaiMois() == 0 ? "Aucune période d’essai prévue" : contrat.getDureeEssaiMois() + " mois"));
        putContrat(vars, "CONTRAT_DATE_FIN_ESSAI", dateContrat(contrat.getDateFinEssai()));
        putContrat(vars, "CONTRAT_DESCRIPTION", contrat.getDescription());
        putContrat(vars, "CONTRAT_STATUT", valeurReference("ref_statut_rh", "id_statut_rh", "code", contrat.getIdStatutRh(), c));
        EmployeDocumentDTO employee = mapperEmployeDocument(emp);
        putContrat(vars, "EMPLOYE_NOM_COMPLET", emp.getNom() == null || emp.getNom().isBlank() ? null : employee.getNomComplet());
        putContrat(vars, "EMPLOYE_MATRICULE", emp.getMatricule());
        putContrat(vars, "EMPLOYE_CIVILITE", employee.getCivilite());
        putContrat(vars, "EMPLOYE_DATE_NAISSANCE", dateContrat(emp.getDateNaissance()));
        putContrat(vars, "EMPLOYE_LIEU_NAISSANCE", emp.getLieuNaissance());
        putContrat(vars, "EMPLOYE_CIN", emp.getCinNumero());
        putContrat(vars, "EMPLOYE_CIN_DELIVRANCE", emp.getCinDateDelivrance() == null || emp.getCinLieuDelivrance() == null ? null :
                "Délivrée le " + dateContrat(emp.getCinDateDelivrance()) + " à " + emp.getCinLieuDelivrance());
        putContrat(vars, "EMPLOYE_ADRESSE", emp.getAdresse());
        putContrat(vars, "POSTE_FONCTION", valeurReference("ref_fonction", "id_fonction", "libelle", aff.getIdFonction(), c));
        putContrat(vars, "POSTE_SITE", valeurReference("ref_site", "id_site", "libelle", aff.getIdSite(), c));
        putContrat(vars, "POSTE_DEPARTEMENT", valeurReference("ref_departement", "id_departement", "libelle", aff.getIdDepartement(), c));
        putContrat(vars, "POSTE_SERVICE", valeurReference("ref_service", "id_service", "libelle", aff.getIdService(), c));
        putContrat(vars, "POSTE_CATEGORIE", valeurReference("ref_categorie_professionnelle", "id_categorie_professionnelle", "libelle", aff.getIdCategorieProfessionnelle(), c));
        putContrat(vars, "POSTE_SUPERIEUR", valeurReference("employe", "id_employe", "concat_ws(' ', nom, prenom)", aff.getIdResponsableHierarchique(), c));
        putContrat(vars, "REMUNERATION_BASE", aff.getSalaireBase() == null ? null : String.format(Locale.FRENCH, "%,.2f Ar", aff.getSalaireBase()));
        putContrat(vars, "REMUNERATION_MODE", valeurReference("ref_mode_calcul_paie", "id_mode_calcul_paie", "libelle", aff.getIdModeCalculPaie(), c));
        if ("TEMPORAIRE".equals(code)) {
            String modeCode = valeurReference("ref_mode_calcul_paie", "id_mode_calcul_paie", "code", aff.getIdModeCalculPaie(), c);
            String unite = modeCode == null ? null : REMUNERATION_UNITS.get(modeCode);
            putContrat(vars, "TEMPORAIRE_UNITE_REMUNERATION", unite);
            putContrat(vars, "TEMPORAIRE_MOTIF", contrat.getDescription());
            if (unite != null && aff.getSalaireBase() != null) {
                putContrat(vars, "REMUNERATION_BASE", vars.get("{{REMUNERATION_BASE}}") + " " + unite);
            }
        }
        putContrat(vars, "ENTREPRISE_NOM", valeurReference("ref_filiale", "id_filiale", "raison_sociale", contrat.getIdFiliale(), c));
        putContrat(vars, "ENTREPRISE_NIF", valeurReference("ref_filiale", "id_filiale", "nif", contrat.getIdFiliale(), c));
        putContrat(vars, "ENTREPRISE_STAT", valeurReference("ref_filiale", "id_filiale", "stat", contrat.getIdFiliale(), c));
        putContrat(vars, "ENTREPRISE_ADRESSE", valeurReference("ref_filiale", "id_filiale", "adresse_siege", contrat.getIdFiliale(), c));
        putContrat(vars, "ENTREPRISE_RCS", valeurReference("ref_filiale", "id_filiale", "rcs", contrat.getIdFiliale(), c));
        String filialeScope = "filiales." + contrat.getIdFiliale();
        for (String key : List.of("SIGNATAIRE_NOM", "SIGNATAIRE_FONCTION", "SIGNATAIRE_VILLE")) {
            putContrat(vars, key, configurationContrat(filialeScope, key));
        }
        Map<String, String> daf = rechercherDafSignataire(contrat.getIdFiliale(), c);
        for (String key : List.of("SIGNATAIRE_NOM", "SIGNATAIRE_FONCTION")) {
            if (daf.containsKey(key)) putContrat(vars, key, daf.get(key));
        }
        // La date de signature reste à renseigner à la signature ; aucune date fictive.
        putContrat(vars, "SIGNATAIRE_DATE", contrat.getDateSignature() == null ? "À renseigner lors de la signature" : dateContrat(contrat.getDateSignature()));
        if (!"PRESTATION".equals(code)) {
            putContrat(vars, "TEMPS_TRAVAIL", resoudreTempsTravailContrat(contrat, c));
        }
        for (String key : List.of("STAGE_ETABLISSEMENT", "STAGE_REFERENT_ETABLISSEMENT", "STAGE_GRATIFICATION", "PRESTATION_MONTANT", "PRESTATION_MODALITES_PAIEMENT")) {
            putContrat(vars, key, configurationContrat("contrats." + idContrat, key));
        }
        return preparerApercuContrat(contrat, aff, employee, code, template, vars);
    }

    /** Une DAF active de la filiale, sans confondre compte administrateur et personne. */
    Map<String, String> rechercherDafSignataire(Integer idFiliale, Connection c) throws Exception {
        String sql = "SELECT DISTINCT e.id_employe, concat_ws(' ', e.nom, e.prenom) AS nom, f.libelle AS fonction " +
                "FROM employe e JOIN employe_affectation_carriere a ON a.id_employe = e.id_employe " +
                "JOIN ref_fonction f ON f.id_fonction = a.id_fonction " +
                "JOIN ref_statut_rh s ON s.id_statut_rh = a.id_statut_rh " +
                "JOIN contrat_travail ct ON ct.id_contrat = a.id_contrat " +
                "JOIN ref_statut_rh sc ON sc.id_statut_rh = ct.id_statut_rh " +
                "WHERE f.code_fonction = 'DAF' AND s.code = 'ACTIF' AND sc.code = 'ACTIF' " +
                "AND ct.id_filiale = ? AND a.date_debut <= CURRENT_DATE " +
                "AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idFiliale);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Map.of();
                String nom = rs.getString("nom"), fonction = rs.getString("fonction");
                if (rs.next()) {
                    log.warn("Plusieurs DAF actives pour la filiale {} : signataire automatique indéterminé", idFiliale);
                    return Map.of();
                }
                Map<String, String> values = new HashMap<>();
                if (nom != null && !nom.isBlank()) values.put("SIGNATAIRE_NOM", nom);
                if (fonction != null && !fonction.isBlank()) values.put("SIGNATAIRE_FONCTION", fonction);
                return values;
            }
        }
    }

    private static void putContrat(Map<String, String> vars, String name, String value) {
        vars.put("{{" + name + "}}", value == null ? "" : value.trim());
    }

    private String resoudreTempsTravailContrat(ContratTravail contrat, Connection c) throws Exception {
        if (contrat.getDateDebut() == null) {
            throw new SirhBusinessException(SirhErrorCode.CONTRACT_DOCUMENT_DATA_MISSING,
                    "Date de début absente : durée hebdomadaire de référence indéterminée.");
        }
        var config = mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale.findActiveAt(
                c, contrat.getDateDebut().toLocalDate());
        if (config == null || config.getDureeHebdoLegale() == null
                || config.getDureeHebdoLegale().signum() <= 0) {
            throw new SirhBusinessException(SirhErrorCode.LEGAL_CONFIG_MISSING,
                    "Durée hebdomadaire de référence absente pour le " + contrat.getDateDebut() + ".");
        }
        return config.getDureeHebdoLegale().stripTrailingZeros().toPlainString() + " heures par semaine";
    }

    private static String dateContrat(java.sql.Date date) {
        return date == null ? null : formatDateFr(date.toLocalDate());
    }

    private String valeurReference(String table, String idColumn, String valueColumn, Integer id, Connection c) throws Exception {
        if (id == null) return null;
        // Identifiants exclusivement internes, jamais fournis par un client.
        try (PreparedStatement ps = c.prepareStatement("SELECT " + valueColumn + " FROM " + table + " WHERE " + idColumn + " = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        }
    }

    private ContratTravailDocumentDTO preparerApercuContrat(
            ContratTravail contrat, EmployeAffectationCarriere aff, EmployeDocumentDTO emp,
            String code, String template, Map<String, String> vars) throws Exception {
        List<String> missing = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> paragraphs = new ArrayList<>();
        try (InputStream input = loadTemplateStream(template)) {
            if (input == null) throw new FileNotFoundException("Modèle contractuel introuvable : " + template);
            try (XWPFDocument doc = new XWPFDocument(input)) {
                retirerMentionsContractuellesInutiles(doc, vars);
                for (XWPFParagraph p : paragraphesDocument(doc)) {
                    var matcher = CONTRACT_VARIABLE.matcher(p.getText());
                    while (matcher.find()) {
                        String key = matcher.group();
                        if (!vars.containsKey(key)) throw erreurTemplate(key);
                        if (vars.get(key).isBlank()) {
                            // Champs facultatifs selon le schéma : absences visibles, sans valeurs inventées.
                            if (MANUAL_CONTRACT_FIELDS.containsKey(key)) {
                                vars.put(key, MANUAL_CONTRACT_FIELDS.get(key));
                                warnings.add(key);
                            } else if (Set.of("{{ENTREPRISE_RCS}}", "{{POSTE_SERVICE}}", "{{POSTE_SUPERIEUR}}", "{{STAGE_REFERENT_ETABLISSEMENT}}", "{{TEMPORAIRE_MOTIF}}").contains(key)) {
                                vars.put(key, "Non renseigné — à vérifier par la DAF/RH");
                                warnings.add(key);
                            } else if (!missing.contains(key)) missing.add(key);
                        }
                    }
                    String text = p.getText();
                    for (var entry : vars.entrySet()) text = text.replace(entry.getKey(), entry.getValue().isBlank() ? "[Donnée manquante : " + entry.getKey().replace("{{", "").replace("}}", "") + "]" : entry.getValue());
                    paragraphs.add(text);
                }
            }
        }
        return new ContratTravailDocumentDTO(
                contrat.getIdContrat(), contrat.getIdEmploye(), contrat.getNumeroContrat(), code,
                vars.get("{{CONTRAT_STATUT}}"), template, contrat, aff, emp,
                Collections.unmodifiableMap(new LinkedHashMap<>(vars)), List.copyOf(missing), List.copyOf(warnings), List.copyOf(paragraphs));
    }

    public void genererContratDocx(ContratTravailDocumentDTO dto, OutputStream os) throws Exception {
        if (!dto.isGenerationPossible()) throw new SirhBusinessException(
                SirhErrorCode.CONTRACT_DOCUMENT_DATA_MISSING,
                "Données obligatoires manquantes : " + String.join(", ", dto.donneesManquantes()),
                Map.of("variables", dto.donneesManquantes()), List.of());
        try (InputStream input = loadTemplateStream(templateContrat(dto.codeTypeContrat()))) {
            if (input == null) throw new FileNotFoundException("Modèle contractuel introuvable.");
            try (XWPFDocument doc = new XWPFDocument(input)) {
                retirerMentionsContractuellesInutiles(doc, dto.variables());
                for (XWPFParagraph p : paragraphesDocument(doc)) {
                    var matcher = CONTRACT_VARIABLE.matcher(p.getText());
                    while (matcher.find()) {
                        String key = matcher.group();
                        if (!dto.variables().containsKey(key)) throw erreurTemplate(key);
                        if (dto.variables().get(key) == null || dto.variables().get(key).isBlank()) {
                            throw new SirhBusinessException(
                                    SirhErrorCode.CONTRACT_DOCUMENT_DATA_MISSING, "Donnée manquante : " + key);
                        }
                    }
                    remplacerDansParagraphe(p, dto.variables());
                    if (p.getText().contains("{{") || p.getText().contains("}}")) throw erreurTemplate(p.getText());
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                doc.write(output);
                // Vérifie aussi les parties XML non exposées comme paragraphes POI (ex. zones de texte).
                try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
                    java.util.zip.ZipEntry entry;
                    while ((entry = zip.getNextEntry()) != null) {
                        if (entry.getName().startsWith("word/") && entry.getName().endsWith(".xml")) {
                            String text = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replaceAll("<[^>]+>", "");
                            if (text.contains("{{") || text.contains("}}")) throw erreurTemplate(entry.getName());
                        }
                    }
                }
                os.write(output.toByteArray());
                log.info("DOCX contrat généré : id={}, type={}", dto.idContrat(), dto.codeTypeContrat());
            }
        }
    }

    private void retirerMentionsContractuellesInutiles(XWPFDocument doc, Map<String, String> variables) {
        // Supprimer la ligne facultative RCS lorsqu'aucun numéro vérifié n'est renseigné.
        boolean rcsAbsent = variables.getOrDefault("{{ENTREPRISE_RCS}}", "").isBlank();
        for (XWPFParagraph paragraph : paragraphesDocument(doc)) {
            String text = paragraph.getText();
            if ((rcsAbsent && text.contains("{{ENTREPRISE_RCS}}")) || text.contains("{{POSTE_SUPERIEUR}}")) {
                String retained = Arrays.stream(text.split("\\R", -1))
                        .filter(line -> !(rcsAbsent && line.contains("{{ENTREPRISE_RCS}}")) && !line.contains("{{POSTE_SUPERIEUR}}"))
                        .collect(java.util.stream.Collectors.joining("\n"));
                int position = doc.getPosOfParagraph(paragraph);
                if (retained.isBlank() && position >= 0) doc.removeBodyElement(position);
                else if (retained.isBlank() && paragraph.getBody() instanceof XWPFTableCell cell)
                    cell.removeParagraph(cell.getParagraphs().indexOf(paragraph));
                else remplacerTexteParagrapheComplet(paragraph, retained);
            }
        }
    }

    private static SirhBusinessException erreurTemplate(String key) {
        return new SirhBusinessException(
                SirhErrorCode.CONTRACT_TEMPLATE_INVALID, "Variable inconnue ou résiduelle : " + key);
    }

    private List<XWPFParagraph> paragraphesDocument(XWPFDocument doc) {
        List<XWPFParagraph> result = new ArrayList<>();
        collecterParagraphes(doc.getBodyElements(), result);
        for (XWPFHeader header : doc.getHeaderList()) collecterParagraphes(header.getBodyElements(), result);
        for (XWPFFooter footer : doc.getFooterList()) collecterParagraphes(footer.getBodyElements(), result);
        for (XWPFFootnote note : doc.getFootnotes()) collecterParagraphes(note.getBodyElements(), result);
        for (XWPFEndnote note : doc.getEndnotes()) collecterParagraphes(note.getBodyElements(), result);
        return result;
    }

    private void collecterParagraphes(List<IBodyElement> elements, List<XWPFParagraph> result) {
        for (IBodyElement element : elements) {
            if (element instanceof XWPFParagraph p) result.add(p);
            else if (element instanceof XWPFTable table) {
                for (XWPFTableRow row : table.getRows()) for (XWPFTableCell cell : row.getTableCells()) collecterParagraphes(cell.getBodyElements(), result);
            }
        }
    }

    public static void validerLiaisonsDocument(Integer idEmploye, Integer idTypeDocument, Integer idContrat, Integer idWorkflow, Connection c) throws Exception {
        var type = new RefTypeDocument().rechercherParId(idTypeDocument, c);
        if (type != null && type.isContratTravail() && idContrat == null) {
            throw new SirhBusinessException(
                    SirhErrorCode.VALIDATION_ERROR, "Le contrat est obligatoire pour un contrat de travail signé.");
        }
        if (idContrat != null) {
            ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
            if (contrat == null || !Objects.equals(idEmploye, contrat.getIdEmploye())) {
                throw new SirhBusinessException(
                        SirhErrorCode.VALIDATION_ERROR, "Le contrat ne correspond pas à l'employé.");
            }
        }
        if (idWorkflow != null) {
            var workflow = new WorkflowRh().rechercherParId(idWorkflow, c);
            if (workflow == null || !Objects.equals(idEmploye, workflow.getIdEmploye()) ||
                    (idContrat != null && !Objects.equals(idContrat, workflow.getIdContratSource()) && !Objects.equals(idContrat, workflow.getIdContratCible()))) {
                throw new SirhBusinessException(
                        SirhErrorCode.VALIDATION_ERROR, "Le workflow ne correspond pas à l'employé et au contrat.");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. PRÉPARATION ATTESTATION DE TRAVAIL
    // ─────────────────────────────────────────────────────────────────────────
    public AttestationTravailDTO preparerAttestation(Integer idEmploye, String typeAnciennete, boolean inclureSalaire, Connection c) throws Exception {
        Employe emp = new Employe().rechercherParId(idEmploye, c);
        if (emp == null) {
            throw new IllegalArgumentException("Employé introuvable (ID: " + idEmploye + ")");
        }

        ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
        if (contratActif == null) {
            // Rechercher le dernier contrat
            String sqlLast = "SELECT id_contrat FROM contrat_travail WHERE id_employe = ? ORDER BY date_debut DESC LIMIT 1";
            try (PreparedStatement ps = c.prepareStatement(sqlLast)) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        contratActif = new ContratTravail().rechercherParId(rs.getInt("id_contrat"), c);
                    }
                }
            }
        }

        EmployeAffectationCarriere affectation = EmployeAffectationCarriere.rechercherActiveParEmploye(idEmploye, c);

        AttestationTravailDTO dto = new AttestationTravailDTO();
        dto.setReference("ATT-" + (contratActif != null ? contratActif.getNumeroContrat() : ("EMP-" + idEmploye)));
        dto.setTypeAnciennete("globale".equalsIgnoreCase(typeAnciennete) ? "globale" : "contrat");
        dto.setInclureSalaire(inclureSalaire);

        // Entreprise / Filiale
        dto.setEntreprise(chargerEntrepriseInfo(contratActif != null ? contratActif.getIdFiliale() : null, affectation != null ? affectation.getIdSite() : null, c));

        // Employé
        EmployeDocumentDTO empDto = mapperEmployeDocument(emp);
        dto.setEmploye(empDto);

        // Date d'entrée
        LocalDate dateDebutContrat = contratActif != null && contratActif.getDateDebut() != null
                ? contratActif.getDateDebut().toLocalDate() : LocalDate.now();

        LocalDate datePremiereEmbauche = dateDebutContrat;
        String sqlChaine = "SELECT MIN(date_debut) FROM contrat_travail WHERE id_employe = ? AND id_statut_rh != (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE')";
        try (PreparedStatement ps = c.prepareStatement(sqlChaine)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDate(1) != null) {
                    datePremiereEmbauche = rs.getDate(1).toLocalDate();
                }
            }
        }

        LocalDate dateEntreeEffective = "globale".equalsIgnoreCase(typeAnciennete) ? datePremiereEmbauche : dateDebutContrat;
        dto.setDateEntree(dateEntreeEffective);
        dto.setDateEntreeTexte(formatDateFr(dateEntreeEffective));
        dto.setAncienneteTexte(calculerDureeTexte(dateEntreeEffective, LocalDate.now()));

        // Type contrat
        String typeContratLib = "durée indéterminée";
        if (contratActif != null && contratActif.getIdTypeContrat() != null) {
            String sqlTc = "SELECT libelle FROM ref_type_contrat WHERE id_type_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlTc)) {
                ps.setInt(1, contratActif.getIdTypeContrat());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String tc = rs.getString("libelle");
                        if (tc != null && tc.toUpperCase().contains("DÉTERMINÉE") && !tc.toUpperCase().contains("INDÉTERMINÉE")) {
                            typeContratLib = "durée déterminée";
                        }
                    }
                }
            }
        }
        dto.setTypeContrat(typeContratLib);

        // Fonction & Organisation
        enrichirPosteOrganisation(dto, affectation, c);

        // Signataire RH
        dto.setSignataire(resoudreSignataire(dto.getEntreprise().getVille(), c));

        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. PRÉPARATION CERTIFICAT DE TRAVAIL
    // ─────────────────────────────────────────────────────────────────────────
    public CertificatTravailDTO preparerCertificat(Integer idContrat, String typeAnciennete, Connection c) throws Exception {
        ContratTravail contrat = new ContratTravail().rechercherParId(idContrat, c);
        if (contrat == null) {
            throw new IllegalArgumentException("Contrat de travail introuvable (ID: " + idContrat + ")");
        }

        Employe emp = new Employe().rechercherParId(contrat.getIdEmploye(), c);
        if (emp == null) {
            throw new IllegalArgumentException("Employé introuvable pour ce contrat.");
        }

        CertificatTravailDTO dto = new CertificatTravailDTO();
        dto.setReference("CERT-" + (contrat.getNumeroContrat() != null ? contrat.getNumeroContrat() : ("CTR-" + idContrat)));

        // Entreprise
        dto.setEntreprise(chargerEntrepriseInfo(contrat.getIdFiliale(), null, c));

        // Employé
        dto.setEmploye(mapperEmployeDocument(emp));

        // Dates et durée
        LocalDate dateDebutContrat = contrat.getDateDebut() != null ? contrat.getDateDebut().toLocalDate() : LocalDate.now();
        LocalDate dateFinContrat = contrat.getDateFin() != null ? contrat.getDateFin().toLocalDate() : LocalDate.now();

        LocalDate dateEntreeInitiale = dateDebutContrat;
        if ("globale".equalsIgnoreCase(typeAnciennete)) {
            String sqlChaine = "SELECT MIN(date_debut) FROM contrat_travail WHERE id_employe = ? AND id_statut_rh != (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE')";
            try (PreparedStatement ps = c.prepareStatement(sqlChaine)) {
                ps.setInt(1, emp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getDate(1) != null) {
                        dateEntreeInitiale = rs.getDate(1).toLocalDate();
                    }
                }
            }
        }

        dto.setDateEntree(dateEntreeInitiale);
        dto.setDateEntreeTexte(formatDateFr(dateEntreeInitiale));
        dto.setDateSortie(dateFinContrat);
        dto.setDateSortieTexte(formatDateFr(dateFinContrat));
        dto.setDureeTotaleTexte(calculerDureeTexte(dateEntreeInitiale, dateFinContrat));

        // Type contrat
        String typeContratLib = "durée indéterminée";
        if (contrat.getIdTypeContrat() != null) {
            String sqlTc = "SELECT libelle FROM ref_type_contrat WHERE id_type_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlTc)) {
                ps.setInt(1, contrat.getIdTypeContrat());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String tc = rs.getString("libelle");
                        if (tc != null && tc.toUpperCase().contains("DÉTERMINÉE") && !tc.toUpperCase().contains("INDÉTERMINÉE")) {
                            typeContratLib = "durée déterminée";
                        }
                    }
                }
            }
        }
        dto.setTypeContrat(typeContratLib);

        // Formule libératoire légale obligatoire
        dto.setMentionLiberatoire("Nous confirmons que le/la salarié(e) quitte l'entreprise libre de tout engagement et de toute obligation envers la société.");

        // Postes successivement occupés
        List<PosteHistoriqueDTO> postes = chargerPostesSuccessifs(emp.getIdEmploye(), c);
        dto.setPostesOccupes(postes);

        // Dernier poste occupé
        if (!postes.isEmpty()) {
            PosteHistoriqueDTO dernier = postes.get(postes.size() - 1);
            dto.setDernierPoste(dernier.getLibelleFonction());
            dto.setDerniereCategorie(dernier.getCategorieProfessionnelle());
            dto.setDernierDepartement(dernier.getDepartement());
            dto.setDernierSite(dernier.getSite());
        } else {
            dto.setDernierPoste(null);
            dto.setDerniereCategorie(null);
            dto.setDernierDepartement(null);
            dto.setDernierSite(null);
        }

        // Signataire
        dto.setSignataire(resoudreSignataire(dto.getEntreprise().getVille(), c));

        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. GÉNÉRATION DES FICHIERS WORD .DOCX (Apache POI)
    // ─────────────────────────────────────────────────────────────────────────
    public void genererAttestationDocx(AttestationTravailDTO dto, OutputStream os) throws Exception {
        InputStream is = loadTemplateStream("Attestation_de_travail_modele.docx");
        if (is == null) {
            throw new FileNotFoundException("Modèle Attestation_de_travail_modele.docx introuvable.");
        }

        try (XWPFDocument doc = new XWPFDocument(is)) {
            Map<String, String> map = new HashMap<>();
            EntrepriseInfoDTO ent = dto.getEntreprise();
            EmployeDocumentDTO emp = dto.getEmploye();
            SignataireDTO sig = dto.getSignataire();

            map.put("[NOM DE L'ENTREPRISE]", ent.getNom() != null ? ent.getNom().toUpperCase() : "GALAXAM LOGISTICS");
            map.put("[Nom de l'entreprise]", ent.getNom() != null ? ent.getNom() : "GALAXAM LOGISTICS");
            map.put("[Adresse complète]", ent.getAdresse() != null ? ent.getAdresse() : "");
            map.put("[Adresse complète du siège social]", ent.getAdresse() != null ? ent.getAdresse() : "");
            map.put("[Code postal, Ville]", (ent.getCodePostal() != null ? ent.getCodePostal() + " " : "") + (ent.getVille() != null ? ent.getVille() : ""));
            map.put("[Pays]", ent.getPays() != null ? ent.getPays() : "");
            map.put("[Téléphone]", ent.getTelephone() != null ? ent.getTelephone() : "");
            map.put("[Adresse e-mail]", ent.getEmail() != null ? ent.getEmail() : "");
            map.put("[Site web]", ent.getSiteWeb() != null ? ent.getSiteWeb() : "");
            map.put("[LOGO]", "");
            map.put("[NIF]", ent.getNif() != null ? ent.getNif() : "—");
            map.put("[STAT]", ent.getStat() != null ? ent.getStat() : "—");
            map.put("[RCS]", "");

            map.put("[N° de référence]", dto.getReference());
            map.put("[Ville]", sig.getVille() != null ? sig.getVille() : "");
            map.put("[JJ/MM/AAAA]", sig.getDateEmissionTexte());
            map.put("[Nom et prénom du représentant]", sig.getNom());
            map.put("[Fonction — ex. Directeur des Ressources Humaines]", sig.getFonction());
            map.put("[Nom du signataire]", sig.getNom());
            map.put("[Fonction du signataire]", sig.getFonction());

            // Nettoyage de la date de signature pour éviter ", le JJ/MM/AAAA" si la ville est vide
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Ville]") && txt.contains("[JJ/MM/AAAA]")) {
                    String dateLine = (sig.getVille() != null && !sig.getVille().isBlank())
                            ? sig.getVille().trim() + ", le " + sig.getDateEmissionTexte()
                            : "Le " + sig.getDateEmissionTexte();
                    remplacerTexteParagrapheComplet(p, dateLine);
                    break;
                }
            }

            // Nettoyage de la ligne légale NIF / STAT / RCS dans l'en-tête (sans RCS)
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("NIF : [NIF]") || txt.contains("STAT : [STAT]")) {
                    StringBuilder sbId = new StringBuilder();
                    if (ent.getNif() != null && !ent.getNif().isBlank()) {
                        sbId.append("NIF : ").append(ent.getNif());
                    }
                    if (ent.getStat() != null && !ent.getStat().isBlank()) {
                        if (sbId.length() > 0) sbId.append(" - ");
                        sbId.append("STAT : ").append(ent.getStat());
                    }
                    remplacerTexteParagrapheComplet(p, sbId.toString());
                    break;
                }
            }

            // Nettoyage et formatage strict de la ligne Tél / Email / Site web
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Téléphone]") || txt.contains("[Adresse e-mail]") || (txt.startsWith("Tél.") && txt.contains("·"))) {
                    StringBuilder sbContact = new StringBuilder();
                    String tel = (ent.getTelephone() != null && !ent.getTelephone().isBlank()) ? ent.getTelephone() : "+261 32 12 131 01";
                    String mail = (ent.getEmail() != null && !ent.getEmail().isBlank()) ? ent.getEmail() : "daf@galaxam.com";
                    sbContact.append("Tél. : ").append(tel).append(" · Email : ").append(mail);
                    if (ent.getSiteWeb() != null && !ent.getSiteWeb().isBlank()) {
                        sbContact.append(" · ").append(ent.getSiteWeb());
                    }
                    remplacerTexteParagrapheComplet(p, sbContact.toString());
                    break;
                }
            }

            // Remplacement dynamique du paragraphe d'introduction légale (sans RCS inventé)
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("immatriculée sous le numéro au RCS") || (txt.contains("[RCS]") && txt.contains("[NIF]"))) {
                    StringBuilder sbIntro = new StringBuilder();
                    sbIntro.append("Je soussigné(e), ").append(sig.getNom())
                           .append(", agissant en qualité de ").append(sig.getFonction())
                           .append(" de la société ").append(ent.getNom());
                    if (ent.getAdresse() != null && !ent.getAdresse().isBlank()) {
                        sbIntro.append(", dont le siège social est situé ").append(ent.getAdresse());
                    }
                    if (ent.getNif() != null || ent.getStat() != null) {
                        sbIntro.append(", sous le ");
                        if (ent.getNif() != null && !ent.getNif().isBlank()) {
                            sbIntro.append("NIF n° ").append(ent.getNif());
                        }
                        if (ent.getNif() != null && ent.getStat() != null) {
                            sbIntro.append(" et le ");
                        }
                        if (ent.getStat() != null && !ent.getStat().isBlank()) {
                            sbIntro.append("STAT n° ").append(ent.getStat());
                        }
                    }
                    sbIntro.append(", atteste par la présente que :");
                    remplacerTexteParagrapheComplet(p, sbIntro.toString());
                    break;
                }
            }

            // Remplacement dynamique du paragraphe d'état civil (sans valeurs inventées ni clauses vides)
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Nom et prénom du salarié]") && txt.contains("[Date d'entrée]")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Madame/Monsieur ").append(emp.getNomComplet());
                    boolean hasDetails = false;
                    if (emp.getDateNaissance() != null) {
                        sb.append(", né(e) le ").append(emp.getDateNaissance().format(FMT_COURT));
                        hasDetails = true;
                    }
                    if (emp.getLieuNaissance() != null && !emp.getLieuNaissance().isBlank()) {
                        sb.append(" à ").append(emp.getLieuNaissance());
                        hasDetails = true;
                    }
                    if (emp.getNationalite() != null && !emp.getNationalite().isBlank()) {
                        sb.append(", de nationalité ").append(emp.getNationalite());
                        hasDetails = true;
                    }
                    if (emp.getNumeroCin() != null && !emp.getNumeroCin().isBlank()) {
                        sb.append(", titulaire de la pièce d'identité n° ").append(emp.getNumeroCin());
                        hasDetails = true;
                    }
                    if (emp.getAdresse() != null && !emp.getAdresse().isBlank()) {
                        sb.append(", demeurant ").append(emp.getAdresse());
                        hasDetails = true;
                    }
                    if (hasDetails) {
                        sb.append(", est employé(e)");
                    } else {
                        sb.append(" est employé(e)");
                    }
                    sb.append(" au sein de notre entreprise depuis le ").append(dto.getDateEntreeTexte())
                      .append(", dans le cadre d'un contrat de travail à ").append(dto.getTypeContrat()).append(".");

                    remplacerTexteParagrapheComplet(p, sb.toString());
                    break;
                }
            }

            // Remplacement dynamique du paragraphe de poste (sans département ou supérieur inventé)
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Intitulé du poste]") && txt.contains("[plein / partiel]")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Il/Elle occupe actuellement le poste de ").append(dto.getLibelleFonction() != null ? dto.getLibelleFonction() : "Salarié(e)");
                    if (dto.getCategorieProfessionnelle() != null && !dto.getCategorieProfessionnelle().isBlank()) {
                        sb.append(" (").append(dto.getCategorieProfessionnelle()).append(")");
                    }
                    if (dto.getDepartement() != null && !dto.getDepartement().isBlank()) {
                        sb.append(" au sein du département ").append(dto.getDepartement());
                    }
                    if (dto.getSuperieurHierarchique() != null && !dto.getSuperieurHierarchique().isBlank()) {
                        sb.append(", sous la responsabilité de ").append(dto.getSuperieurHierarchique());
                    }
                    sb.append(". Il/Elle exerce ses fonctions à temps ").append(dto.getTempsTravail())
                      .append(", pour une durée hebdomadaire de ").append(dto.getHeuresHebdomadaires()).append(" heures.");

                    remplacerTexteParagrapheComplet(p, sb.toString());
                    break;
                }
            }

            remplacerVariables(doc, map);

            // Mention du salaire si demandée
            if (dto.isInclureSalaire() && dto.getSalaireBaseTexte() != null) {
                XWPFParagraph p = doc.createParagraph();
                p.setSpacingBefore(120);
                XWPFRun r = p.createRun();
                r.setFontFamily("Calibri");
                r.setFontSize(10.5);
                r.setText("Rémunération de base : " + dto.getSalaireBaseTexte() + " Ar par mois (salaire mensuel brut contractuel).");
            }

            // Nettoyage complet des pieds de page de modèle (suppression des mentions confidentielles / page numbers orphelins)
            for (XWPFFooter footer : doc.getFooterList()) {
                for (XWPFParagraph p : footer.getParagraphs()) {
                    for (int r = p.getRuns().size() - 1; r >= 0; r--) {
                        p.removeRun(r);
                    }
                }
            }

            doc.write(os);
        }
    }

    public void genererCertificatDocx(CertificatTravailDTO dto, OutputStream os) throws Exception {
        InputStream is = loadTemplateStream("Certificat_de_travail_modele.docx");
        if (is == null) {
            throw new FileNotFoundException("Modèle Certificat_de_travail_modele.docx introuvable.");
        }

        try (XWPFDocument doc = new XWPFDocument(is)) {
            Map<String, String> map = new HashMap<>();
            EntrepriseInfoDTO ent = dto.getEntreprise();
            EmployeDocumentDTO emp = dto.getEmploye();
            SignataireDTO sig = dto.getSignataire();

            map.put("[NOM DE L'ENTREPRISE]", ent.getNom() != null ? ent.getNom().toUpperCase() : "GALAXAM LOGISTICS");
            map.put("[Nom de l'entreprise]", ent.getNom() != null ? ent.getNom() : "GALAXAM LOGISTICS");
            map.put("[Adresse complète]", ent.getAdresse() != null ? ent.getAdresse() : "");
            map.put("[Adresse complète du siège social]", ent.getAdresse() != null ? ent.getAdresse() : "");
            map.put("[Code postal, Ville]", (ent.getCodePostal() != null ? ent.getCodePostal() + " " : "") + (ent.getVille() != null ? ent.getVille() : ""));
            map.put("[Pays]", ent.getPays() != null ? ent.getPays() : "");
            map.put("[Téléphone]", ent.getTelephone() != null ? ent.getTelephone() : "");
            map.put("[Adresse e-mail]", ent.getEmail() != null ? ent.getEmail() : "");
            map.put("[Site web]", ent.getSiteWeb() != null ? ent.getSiteWeb() : "");
            map.put("[LOGO]", "");
            map.put("[NIF]", ent.getNif() != null ? ent.getNif() : "—");
            map.put("[STAT]", ent.getStat() != null ? ent.getStat() : "—");
            map.put("[RCS]", "");

            map.put("[N° de référence]", dto.getReference());
            map.put("[Ville]", sig.getVille() != null ? sig.getVille() : "");
            map.put("[JJ/MM/AAAA]", sig.getDateEmissionTexte());
            map.put("[Nom et prénom du représentant]", sig.getNom());
            map.put("[Fonction — ex. Directeur des Ressources Humaines]", sig.getFonction());
            map.put("[Nom du signataire]", sig.getNom());
            map.put("[Fonction du signataire]", sig.getFonction());

            // Nettoyage de la date de signature pour éviter ", le JJ/MM/AAAA" si la ville est vide
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Ville]") && txt.contains("[JJ/MM/AAAA]")) {
                    String dateLine = (sig.getVille() != null && !sig.getVille().isBlank())
                            ? sig.getVille().trim() + ", le " + sig.getDateEmissionTexte()
                            : "Le " + sig.getDateEmissionTexte();
                    remplacerTexteParagrapheComplet(p, dateLine);
                    break;
                }
            }

            // Nettoyage de la ligne légale NIF / STAT / RCS dans l'en-tête (sans RCS)
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("NIF : [NIF]") || txt.contains("STAT : [STAT]")) {
                    StringBuilder sbId = new StringBuilder();
                    if (ent.getNif() != null && !ent.getNif().isBlank()) {
                        sbId.append("NIF : ").append(ent.getNif());
                    }
                    if (ent.getStat() != null && !ent.getStat().isBlank()) {
                        if (sbId.length() > 0) sbId.append(" - ");
                        sbId.append("STAT : ").append(ent.getStat());
                    }
                    remplacerTexteParagrapheComplet(p, sbId.toString());
                    break;
                }
            }

            // Nettoyage et formatage strict de la ligne Tél / Email / Site web
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Téléphone]") || txt.contains("[Adresse e-mail]") || (txt.startsWith("Tél.") && txt.contains("·"))) {
                    StringBuilder sbContact = new StringBuilder();
                    String tel = (ent.getTelephone() != null && !ent.getTelephone().isBlank()) ? ent.getTelephone() : "+261 32 12 131 01";
                    String mail = (ent.getEmail() != null && !ent.getEmail().isBlank()) ? ent.getEmail() : "daf@galaxam.com";
                    sbContact.append("Tél. : ").append(tel).append(" · Email : ").append(mail);
                    if (ent.getSiteWeb() != null && !ent.getSiteWeb().isBlank()) {
                        sbContact.append(" · ").append(ent.getSiteWeb());
                    }
                    remplacerTexteParagrapheComplet(p, sbContact.toString());
                    break;
                }
            }

            // Remplacement dynamique du paragraphe d'introduction légale (sans RCS inventé)
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("immatriculée sous le numéro au RCS") || (txt.contains("[RCS]") && txt.contains("[NIF]"))) {
                    StringBuilder sbIntro = new StringBuilder();
                    sbIntro.append("Je soussigné(e), ").append(sig.getNom())
                           .append(", agissant en qualité de ").append(sig.getFonction())
                           .append(" de la société ").append(ent.getNom());
                    if (ent.getAdresse() != null && !ent.getAdresse().isBlank()) {
                        sbIntro.append(", dont le siège social est situé ").append(ent.getAdresse());
                    }
                    if (ent.getNif() != null || ent.getStat() != null) {
                        sbIntro.append(", sous le ");
                        if (ent.getNif() != null && !ent.getNif().isBlank()) {
                            sbIntro.append("NIF n° ").append(ent.getNif());
                        }
                        if (ent.getNif() != null && ent.getStat() != null) {
                            sbIntro.append(" et le ");
                        }
                        if (ent.getStat() != null && !ent.getStat().isBlank()) {
                            sbIntro.append("STAT n° ").append(ent.getStat());
                        }
                    }
                    sbIntro.append(", certifie par la présente que :");
                    remplacerTexteParagrapheComplet(p, sbIntro.toString());
                    break;
                }
            }

            // Paragraphe identité et durée
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Nom et prénom du salarié]") && txt.contains("[Date de sortie]")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Madame/Monsieur ").append(emp.getNomComplet());
                    boolean hasDetails = false;
                    if (emp.getDateNaissance() != null) {
                        sb.append(", né(e) le ").append(emp.getDateNaissance().format(FMT_COURT));
                        hasDetails = true;
                    }
                    if (emp.getLieuNaissance() != null && !emp.getLieuNaissance().isBlank()) {
                        sb.append(" à ").append(emp.getLieuNaissance());
                        hasDetails = true;
                    }
                    if (emp.getAdresse() != null && !emp.getAdresse().isBlank()) {
                        sb.append(", demeurant ").append(emp.getAdresse());
                        hasDetails = true;
                    }
                    if (hasDetails) {
                        sb.append(", a été employé(e)");
                    } else {
                        sb.append(" a été employé(e)");
                    }
                    sb.append(" au sein de notre entreprise du ").append(dto.getDateEntreeTexte())
                      .append(" au ").append(dto.getDateSortieTexte())
                      .append(", soit une durée totale de ").append(dto.getDureeTotaleTexte())
                      .append(", dans le cadre d'un contrat de travail à ").append(dto.getTypeContrat()).append(".");

                    remplacerTexteParagrapheComplet(p, sb.toString());
                    break;
                }
            }

            // Paragraphe départ
            for (XWPFParagraph p : doc.getParagraphs()) {
                String txt = p.getText();
                if (txt.contains("[Dernier intitulé de poste]")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Au moment de son départ, Madame/Monsieur ").append(emp.getNomComplet())
                      .append(" occupait le poste de ").append(dto.getDernierPoste() != null ? dto.getDernierPoste() : "Collaborateur");
                    if (dto.getDerniereCategorie() != null && !dto.getDerniereCategorie().isBlank()) {
                        sb.append(", classé au niveau ").append(dto.getDerniereCategorie());
                    }
                    if (dto.getDernierDepartement() != null && !dto.getDernierDepartement().isBlank()) {
                        sb.append(", au sein du département ").append(dto.getDernierDepartement());
                    }
                    sb.append(".");
                    remplacerTexteParagrapheComplet(p, sb.toString());
                    break;
                }
            }

            map.put("[Date de fin de contrat]", dto.getDateSortieTexte());
            remplacerVariables(doc, map);

            // Remplacement dynamique des postes occupés
            injecterPostesOccupes(doc, dto.getPostesOccupes());

            // Nettoyage complet des pieds de page de modèle (suppression des mentions confidentielles / page numbers orphelins)
            for (XWPFFooter footer : doc.getFooterList()) {
                for (XWPFParagraph p : footer.getParagraphs()) {
                    for (int r = p.getRuns().size() - 1; r >= 0; r--) {
                        p.removeRun(r);
                    }
                }
            }

            doc.write(os);
        }
    }

    private void remplacerTexteParagrapheComplet(XWPFParagraph p, String nouveauTexte) {
        int runsCount = p.getRuns().size();
        for (int i = runsCount - 1; i > 0; i--) {
            p.removeRun(i);
        }
        if (!p.getRuns().isEmpty()) {
            XWPFRun r = p.getRuns().get(0);
            r.setText(nouveauTexte, 0);
            r.setFontFamily("Calibri");
        } else {
            XWPFRun r = p.createRun();
            r.setText(nouveauTexte);
            r.setFontFamily("Calibri");
        }
    }

    private void injecterPostesOccupes(XWPFDocument doc, List<PosteHistoriqueDTO> postes) {
        if (postes == null || postes.isEmpty()) return;

        // Trouver le paragraphe qui contient le modèle de liste de postes
        for (int i = 0; i < doc.getParagraphs().size(); i++) {
            XWPFParagraph p = doc.getParagraphs().get(i);
            String text = p.getText();
            if (text.contains("Date début") && text.contains("Date fin") && text.contains("Intitulé du poste")) {
                // Remplacer ce paragraphe par les vraies lignes de postes
                p.getRuns().forEach(r -> r.setText("", 0));
                
                // Formater la première ligne
                PosteHistoriqueDTO premier = postes.get(0);
                p.createRun().setText(formaterLignePoste(premier));

                // Ajouter des paragraphes pour les autres postes
                for (int j = 1; j < postes.size(); j++) {
                    XWPFParagraph nextP = doc.createParagraph();
                    nextP.createRun().setText(formaterLignePoste(postes.get(j)));
                }
                break;
            }
        }
    }

    private String formaterLignePoste(PosteHistoriqueDTO p) {
        StringBuilder sb = new StringBuilder();
        sb.append("—  Du ").append(p.getDateDebutTexte()).append(" au ").append(p.getDateFinTexte())
          .append(" : ").append(p.getLibelleFonction() != null ? p.getLibelleFonction() : "Salarié(e)");
        if (p.getCategorieProfessionnelle() != null && !p.getCategorieProfessionnelle().isBlank()) {
            sb.append(" (").append(p.getCategorieProfessionnelle()).append(")");
        }
        if (p.getDepartement() != null && !p.getDepartement().isBlank()) {
            sb.append(" — Département ").append(p.getDepartement());
        }
        if (p.getSite() != null && !p.getSite().isBlank()) {
            sb.append(" (").append(p.getSite()).append(")");
        }
        return sb.toString();
    }

    private void remplacerVariables(XWPFDocument doc, Map<String, String> map) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            remplacerDansParagraphe(p, map);
        }
        for (XWPFTable tbl : doc.getTables()) {
            for (XWPFTableRow row : tbl.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        remplacerDansParagraphe(p, map);
                    }
                }
            }
        }
        for (XWPFFooter ftr : doc.getFooterList()) {
            for (XWPFParagraph p : ftr.getParagraphs()) {
                remplacerDansParagraphe(p, map);
            }
        }
        for (XWPFHeader hdr : doc.getHeaderList()) {
            for (XWPFParagraph p : hdr.getParagraphs()) {
                remplacerDansParagraphe(p, map);
            }
        }
    }

    private void remplacerDansParagraphe(XWPFParagraph p, Map<String, String> map) {
        String pText = p.getText();
        boolean needsReplacement = false;
        for (String k : map.keySet()) {
            if (pText.contains(k)) {
                needsReplacement = true;
                break;
            }
        }
        if (!needsReplacement) return;

        // Approche robuste : concaténer le texte du paragraphe, remplacer, puis réaffecter au run 0
        String replaced = pText;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (replaced.contains(entry.getKey())) {
                replaced = replaced.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
        }

        // Effacer les runs existants et définir le texte remplacé
        int runsCount = p.getRuns().size();
        for (int i = runsCount - 1; i > 0; i--) {
            p.removeRun(i);
        }
        if (!p.getRuns().isEmpty()) {
            p.getRuns().get(0).setText(replaced, 0);
        } else {
            p.createRun().setText(replaced);
        }
    }

    private InputStream loadTemplateStream(String filename) {
        // 1. Essayer dans classpath
        try {
            ClassPathResource cpr = new ClassPathResource("templates/documents/" + filename);
            if (cpr.exists()) {
                return cpr.getInputStream();
            }
        } catch (Exception ignored) {}

        // 2. Essayer dans le filesystem projet
        Path p = Paths.get("/Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/model/documents/" + filename);
        if (Files.exists(p)) {
            try {
                return Files.newInputStream(p);
            } catch (Exception e) {
                log.error("Erreur lecture template filesystem {}", p, e);
            }
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS DB & MAPPERS
    // ─────────────────────────────────────────────────────────────────────────
    private EmployeDocumentDTO mapperEmployeDocument(Employe emp) {
        EmployeDocumentDTO d = new EmployeDocumentDTO();
        d.setIdEmploye(emp.getIdEmploye());
        d.setMatricule(emp.getMatricule());
        d.setCivilite("F".equalsIgnoreCase(emp.getSexe()) ? "Madame" : "Monsieur");
        d.setNom(emp.getNom());
        d.setPrenom(emp.getPrenom() != null ? emp.getPrenom() : "");
        d.setNomComplet((emp.getNom() + " " + (emp.getPrenom() != null ? emp.getPrenom() : "")).trim());
        if (emp.getDateNaissance() != null) d.setDateNaissance(emp.getDateNaissance().toLocalDate());
        d.setLieuNaissance(emp.getLieuNaissance() != null && !emp.getLieuNaissance().isBlank() ? emp.getLieuNaissance().trim() : null);
        d.setNationalite(emp.getNationalite() != null && !emp.getNationalite().isBlank() ? emp.getNationalite().trim() : null);
        d.setNumeroCin(emp.getCinNumero() != null && !emp.getCinNumero().isBlank() ? emp.getCinNumero().trim() : null);
        if (emp.getCinDateDelivrance() != null) d.setDateDelivranceCin(emp.getCinDateDelivrance().toLocalDate());
        d.setLieuDelivranceCin(emp.getCinLieuDelivrance());
        d.setNumeroCnaps(emp.getNumAffiliationCnaps() != null && !emp.getNumAffiliationCnaps().isBlank() ? emp.getNumAffiliationCnaps().trim() : null);
        d.setAffiliationMedicale(null);
        d.setAdresse(emp.getAdresse() != null && !emp.getAdresse().isBlank() ? emp.getAdresse().trim() : null);
        d.setVille(emp.getVille() != null && !emp.getVille().isBlank() ? emp.getVille().trim() : null);
        return d;
    }

    private EntrepriseInfoDTO chargerEntrepriseInfo(Integer idFiliale, Integer idSite, Connection c) {
        EntrepriseInfoDTO ent = new EntrepriseInfoDTO();
        ent.setNom("GALAXAM LOGISTICS");
        ent.setRaisonSociale("GALAXAM LOGISTICS");
        ent.setAdresse(null);
        ent.setCodePostal(null);
        ent.setVille(null);
        ent.setPays(null);
        ent.setTelephone("+261 32 12 131 01");
        ent.setEmail("daf@galaxam.com");
        ent.setSiteWeb(null);
        ent.setNif(null);
        ent.setStat(null);
        ent.setRcs(null);

        String sql = idFiliale != null
                ? "SELECT raison_sociale, nif, stat FROM ref_filiale WHERE id_filiale = ?"
                : "SELECT raison_sociale, nif, stat FROM ref_filiale WHERE est_actif = TRUE ORDER BY id_filiale ASC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idFiliale != null) {
                ps.setInt(1, idFiliale);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("raison_sociale") != null) {
                        ent.setNom(rs.getString("raison_sociale"));
                        ent.setRaisonSociale(rs.getString("raison_sociale"));
                    }
                    if (rs.getString("nif") != null) ent.setNif(rs.getString("nif"));
                    if (rs.getString("stat") != null) ent.setStat(rs.getString("stat"));
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de charger la filiale en base", e);
        }

        if (idSite != null) {
            try (PreparedStatement ps = c.prepareStatement("SELECT libelle FROM ref_site WHERE id_site = ?")) {
                ps.setInt(1, idSite);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String lib = rs.getString("libelle");
                        if (lib != null && !lib.isBlank()) {
                            if (lib.contains("-")) {
                                ent.setVille(lib.split("-")[0].trim());
                            } else {
                                ent.setVille(lib.trim());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return ent;
    }

    private void enrichirPosteOrganisation(AttestationTravailDTO dto, EmployeAffectationCarriere aff, Connection c) {
        dto.setLibelleFonction(null);
        dto.setCategorieProfessionnelle(null);
        dto.setDepartement(null);
        dto.setSite(null);
        dto.setSuperieurHierarchique(null);
        dto.setTempsTravail("plein");
        dto.setHeuresHebdomadaires(40);

        if (aff != null) {
            if (aff.getSalaireBase() != null) {
                dto.setSalaireBase(aff.getSalaireBase());
                dto.setSalaireBaseTexte(String.format(Locale.FRENCH, "%,.0f", aff.getSalaireBase()));
            }

            if (aff.getIdFonction() != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT libelle FROM ref_fonction WHERE id_fonction = ?")) {
                    ps.setInt(1, aff.getIdFonction());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dto.setLibelleFonction(rs.getString("libelle"));
                    }
                } catch (Exception ignored) {}
            }

            if (aff.getIdDepartement() != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT libelle FROM ref_departement WHERE id_departement = ?")) {
                    ps.setInt(1, aff.getIdDepartement());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dto.setDepartement(rs.getString("libelle"));
                    }
                } catch (Exception ignored) {}
            }

            if (aff.getIdSite() != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT libelle FROM ref_site WHERE id_site = ?")) {
                    ps.setInt(1, aff.getIdSite());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dto.setSite(rs.getString("libelle"));
                    }
                } catch (Exception ignored) {}
            }

            if (aff.getIdCategorieProfessionnelle() != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT libelle FROM ref_categorie_professionnelle WHERE id_categorie_professionnelle = ?")) {
                    ps.setInt(1, aff.getIdCategorieProfessionnelle());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) dto.setCategorieProfessionnelle(rs.getString("libelle"));
                    }
                } catch (Exception ignored) {}
            }

            if (aff.getIdResponsableHierarchique() != null) {
                try (PreparedStatement ps = c.prepareStatement("SELECT nom, prenom FROM employe WHERE id_employe = ?")) {
                    ps.setInt(1, aff.getIdResponsableHierarchique());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String nom = rs.getString("nom");
                            String prenom = rs.getString("prenom");
                            dto.setSuperieurHierarchique((nom + " " + (prenom != null ? prenom : "")).trim());
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private List<PosteHistoriqueDTO> chargerPostesSuccessifs(Integer idEmploye, Connection c) {
        List<PosteHistoriqueDTO> list = new ArrayList<>();
        String sql = "SELECT a.date_debut, a.date_fin, " +
                "f.libelle AS fonction, " +
                "d.libelle AS departement, " +
                "s.libelle AS site, " +
                "cp.libelle AS categorie, a.description " +
                "FROM employe_affectation_carriere a " +
                "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
                "WHERE a.id_employe = ? " +
                "  AND a.id_statut_rh != (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ANNULE') " +
                "ORDER BY a.date_debut ASC";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PosteHistoriqueDTO p = new PosteHistoriqueDTO();
                    if (rs.getDate("date_debut") != null) {
                        p.setDateDebut(rs.getDate("date_debut").toLocalDate());
                        p.setDateDebutTexte(formatDateFr(p.getDateDebut()));
                    }
                    if (rs.getDate("date_fin") != null) {
                        p.setDateFin(rs.getDate("date_fin").toLocalDate());
                        p.setDateFinTexte(formatDateFr(p.getDateFin()));
                    } else {
                        p.setDateFinTexte("ce jour");
                    }
                    p.setLibelleFonction(rs.getString("fonction"));
                    p.setDepartement(rs.getString("departement"));
                    p.setSite(rs.getString("site"));
                    p.setCategorieProfessionnelle(rs.getString("categorie"));
                    p.setDescriptionMissions(rs.getString("description"));
                    list.add(p);
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de charger l'historique des postes pour employé {}", idEmploye, e);
        }
        return list;
    }

    private SignataireDTO resoudreSignataire(String ville, Connection c) {
        SignataireDTO s = new SignataireDTO();
        s.setNom("Direction des Ressources Humaines");
        s.setFonction("Directeur des Ressources Humaines");
        s.setVille(ville != null && !ville.isBlank() ? ville.trim() : null);
        s.setDateEmission(LocalDate.now());
        s.setDateEmissionTexte(formatDateFr(LocalDate.now()));
        return s;
    }
}
