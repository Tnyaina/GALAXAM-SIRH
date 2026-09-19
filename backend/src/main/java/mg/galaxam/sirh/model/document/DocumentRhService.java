package mg.galaxam.sirh.model.document;

import mg.galaxam.sirh.api.dto.document.DocumentRhDTO;
import mg.galaxam.sirh.api.dto.document.DocumentRhDTO.*;
import mg.galaxam.sirh.model.contrat.ContratTravail;
import mg.galaxam.sirh.model.contrat.EmployeAffectationCarriere;
import mg.galaxam.sirh.model.employe.Employe;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
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

    private final DataSource dataSource;

    public DocumentRhService(DataSource dataSource) {
        this.dataSource = dataSource;
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
                XWPFParagraph currentP = p;
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
        ent.setTelephone(null);
        ent.setEmail(null);
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
