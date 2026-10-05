package mg.galaxam.sirh.paie.application.export;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Service d'exportation de l'État 301 SAFI IRSA (25 colonnes réglementaires DGI Madagascar).
 * Conforme au barème de la Loi de Finances et aux directives de téléversement e-Hetra.
 */
@Service
public class SafiExportService {

    private static final String TABLE_NS = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String OFFICE_NS = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String TEXT_NS = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }

    /**
     * Génère la liste des 25 colonnes SAFI pour tous les salariés d'une période donnée.
     */
    public List<SafiRowDto> genererDonneesSafi(Integer idPeriode, Connection c) throws Exception {
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriode, c);
        List<SafiRowDto> rows = new ArrayList<>();

        Date dateDebutPeriode = null;
        Date dateFinPeriode = null;
        String sqlPeriodeDate = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlPeriodeDate)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dateDebutPeriode = rs.getDate("date_debut");
                    dateFinPeriode = rs.getDate("date_fin");
                }
            }
        }

        // Regroupement par employé pour garantir 1 ligne unique par salarié (Obligation État 301 SAFI DGI)
        Map<Integer, List<BulletinPaie>> bulletinsParEmploye = new LinkedHashMap<>();
        for (BulletinPaie bp : bulletins) {
            if ("CONSULTANT".equalsIgnoreCase(bp.getRegimeIntervenant()) 
                    || "TEMPORAIRE".equalsIgnoreCase(bp.getRegimeIntervenant())
                    || "ISI_5".equalsIgnoreCase(bp.getRegimeIntervenant())
                    || (bp.getRetenueIsi5() != null && bp.getRetenueIsi5().compareTo(BigDecimal.ZERO) > 0)) {
                continue;
            }
            bulletinsParEmploye.computeIfAbsent(bp.getIdEmploye(), k -> new ArrayList<>()).add(bp);
        }

        int ordre = 1;
        for (Map.Entry<Integer, List<BulletinPaie>> entry : bulletinsParEmploye.entrySet()) {
            List<BulletinPaie> empBulletins = entry.getValue();
            BulletinPaie bpPrincipal = empBulletins.stream()
                    .filter(b -> "STC".equalsIgnoreCase(b.getTypeBulletin()))
                    .findFirst()
                    .orElse(empBulletins.get(empBulletins.size() - 1));

            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal totalBrutCumule = BigDecimal.ZERO;
            BigDecimal totalBaseIrsaCumule = BigDecimal.ZERO;
            BigDecimal totalIrsaBrutCumule = BigDecimal.ZERO;
            BigDecimal totalIrsaNetCumule = BigDecimal.ZERO;
            BigDecimal maxReductionEnfants = BigDecimal.ZERO;

            for (BulletinPaie b : empBulletins) {
                lignes.addAll(BulletinPaieLigne.listerParBulletin(b.getIdBulletin(), c));
                if (b.getTotalGainsBruts() != null) totalBrutCumule = totalBrutCumule.add(b.getTotalGainsBruts());
                if (b.getBaseIrsaArrondie() != null) totalBaseIrsaCumule = totalBaseIrsaCumule.add(b.getBaseIrsaArrondie());
                if (b.getIrsaBrut() != null) totalIrsaBrutCumule = totalIrsaBrutCumule.add(b.getIrsaBrut());
                if (b.getIrsaNet() != null) totalIrsaNetCumule = totalIrsaNetCumule.add(b.getIrsaNet());
                if (b.getReductionEnfants() != null) maxReductionEnfants = maxReductionEnfants.max(b.getReductionEnfants());
            }

            SafiRowDto row = new SafiRowDto();
            row.setNumeroOrdre(ordre++);
            row.setMatricule(bpPrincipal.getMatricule());
            row.setAffiliationCnaps(bpPrincipal.getNumeroCnaps() != null ? bpPrincipal.getNumeroCnaps() : "");
            row.setNomPrenoms(bpPrincipal.getNomPrenom());
            row.setCin(bpPrincipal.getCinNumero() != null ? bpPrincipal.getCinNumero() : "");
            row.setDateEmbauche(bpPrincipal.getDateEmbauche() != null ? DATE_FMT.format(bpPrincipal.getDateEmbauche().toLocalDate()) : "");
            row.setDateDebauchage(chercherDateFinContrat(bpPrincipal.getIdEmploye(), dateDebutPeriode, dateFinPeriode, c));
            row.setFonction(bpPrincipal.getIntitulePoste());

            // Ventilation par rubriques
            BigDecimal base = BigDecimal.ZERO;
            BigDecimal indemImpos = BigDecimal.ZERO;
            BigDecimal indemNonImpos = BigDecimal.ZERO;
            BigDecimal hs = BigDecimal.ZERO;
            BigDecimal primes = BigDecimal.ZERO;
            BigDecimal cnaps = BigDecimal.ZERO;
            BigDecimal sante = BigDecimal.ZERO;

            for (BulletinPaieLigne l : lignes) {
                String code = l.getCodeRubrique();
                if (code == null) continue;
                String cat = l.getCategorie() != null ? l.getCategorie().toUpperCase() : "";
                BigDecimal mnt = l.getMontantSalarial() != null ? l.getMontantSalarial() : BigDecimal.ZERO;
                if (mnt.compareTo(BigDecimal.ZERO) == 0) continue;

                // Les variables manuelles portent leur assiette effectivement appliquée,
                // indépendamment du nom de leur famille et de leur place dans le brut.
                if (l.getEtapeCalcul() != null && "GAIN".equals(l.getSens()) && !l.isImposable()) {
                    indemNonImpos = indemNonImpos.add(mnt);
                    continue;
                }

                // 1. Salaire de base et congés (Base - Colonne I)
                if ("SALAIRE_BASE".equals(cat) || code.startsWith("SALAIRE_BASE") || code.startsWith("SALAIRE_PRORATA")
                        || "INDEMNITE_CONGE".equals(code) || "INDEMNITE_CONGE_PAYE".equals(code) || "INDEMNITE_COMPENSATRICE_CONGE".equals(code)) {
                    base = base.add(mnt);
                } else if (code.startsWith("RETENUE_ABSENCE") || code.startsWith("RETENUE_CONGE")) {
                    base = base.subtract(mnt);
                }
                // 2. Heures supplémentaires et majorations (HS - Colonne L)
                else if ("HEURES_SUP".equals(cat) || code.startsWith("HEURES_SUP") || code.startsWith("HS_") || code.startsWith("MAJORATION_")) {
                    hs = hs.add(mnt);
                }
                // 3. Indemnité compensatrice de préavis (Indemnités imposables - Colonne J, et non Base)
                else if ("INDEMNITE_PREAVIS".equals(code)) {
                    indemImpos = indemImpos.add(mnt);
                }
                // 4. Primes et gratifications (Primes - Colonne K)
                else if ("PRIME".equals(cat) || code.startsWith("PRIME_") || "AUTIMP".equals(code)) {
                    primes = primes.add(mnt);
                }
                // 5. Indemnités imposables diverses (Indemnités imposables - Colonne J)
                else if ("INDEMNITE_IMPOSABLE".equals(cat)) {
                    indemImpos = indemImpos.add(mnt);
                }
                // 6. Indemnités non imposables / exonérées (Indemnités non imposables - Colonne N)
                else if ("INDEMNITE_NON_IMPOSABLE".equals(cat) || "INDEMNITE_LICENCIEMENT".equals(code)) {
                    indemNonImpos = indemNonImpos.add(mnt);
                }
                // 7. Cotisations sociales salariales (CNaPS et Santé)
                else if ("COTISATION_SALARIALE".equals(cat) || code.contains("CNAPS") || code.contains("OSTIE") || code.contains("OMSI") || code.contains("SMIE") || code.contains("AMIT")) {
                    if (code.contains("CNAPS")) {
                        cnaps = cnaps.add(mnt);
                    } else {
                        sante = sante.add(mnt);
                    }
                }
                // 8. Fallback dynamique pour tout gain non classé
                else if ("GAIN".equalsIgnoreCase(l.getSens())) {
                    if (l.isImposable()) {
                        primes = primes.add(mnt);
                    } else {
                        indemNonImpos = indemNonImpos.add(mnt);
                    }
                }
            }

            if (base.compareTo(BigDecimal.ZERO) < 0) {
                base = BigDecimal.ZERO;
            }

            row.setIndemnitesImposables(indemImpos.setScale(2, RoundingMode.HALF_UP));
            row.setIndemnitesNonImposables(indemNonImpos.setScale(2, RoundingMode.HALF_UP));
            row.setAvantagesNatureImposables(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            row.setAvantagesNatureExoneres(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            row.setHeuresSupplementaires(hs.setScale(2, RoundingMode.HALF_UP));
            // Règle DGI SAFI : La colonne P ("Autres Repas") est automatiquement ajoutée au salaire brut imposable (Q)
            // dans la formule officielle e-Hetra (Q = I + J + L + N + O + P). L'indemnité de panier exonérée (accord d'établissement)
            // doit figurer en colonne K (Indemnités Non Imposables) pour ne pas être taxée à tort à l'IRSA.
            row.setAutres(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            // Salaire brut (Q = I + J + L + N + O + P)
            BigDecimal brut = totalBrutCumule;
            // Sécurisation de la cohérence arithmétique e-Hetra DGI
            BigDecimal sommeComposantes = base.add(indemImpos).add(hs).add(primes);
            BigDecimal delta = brut.subtract(sommeComposantes);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                if (primes.add(delta).compareTo(BigDecimal.ZERO) >= 0) {
                    primes = primes.add(delta);
                } else {
                    base = base.add(delta);
                    if (base.compareTo(BigDecimal.ZERO) < 0) {
                        base = BigDecimal.ZERO;
                    }
                }
            }
            row.setSalaireBase(base.setScale(2, RoundingMode.HALF_UP));
            row.setPrimesEtGratifications(primes.setScale(2, RoundingMode.HALF_UP));

            // Recalcul strict de Q = I + J + L + N + O + P pour garantir une égalité à 100% sans écart d'arrondi
            BigDecimal brutStrict = row.getSalaireBase()
                    .add(row.getIndemnitesImposables())
                    .add(row.getAvantagesNatureImposables())
                    .add(row.getHeuresSupplementaires())
                    .add(row.getPrimesEtGratifications())
                    .add(row.getAutres());
            row.setSalaireBrut(brutStrict.setScale(2, RoundingMode.HALF_UP));

            row.setDeductionsCnaps(cnaps.setScale(2, RoundingMode.HALF_UP));
            row.setDeductionsSante(sante.setScale(2, RoundingMode.HALF_UP));

            // Salaire Net (T = Q - R - S)
            BigDecimal netAvantImpot = brutStrict.subtract(cnaps).subtract(sante);
            if (netAvantImpot.compareTo(BigDecimal.ZERO) < 0) {
                netAvantImpot = BigDecimal.ZERO;
            }
            row.setSalaireNet(netAvantImpot.setScale(2, RoundingMode.HALF_UP));

            row.setAutreDeduction(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            // Montant imposable (V = 100 * floor(T / 100))
            BigDecimal imposable = totalBaseIrsaCumule;
            row.setMontantImposable(imposable.setScale(2, RoundingMode.HALF_UP));

            // Impôt correspondant (W = IRSA progressif brut)
            BigDecimal impCorresp = totalIrsaBrutCumule;
            row.setImpotCorrespondant(impCorresp.setScale(2, RoundingMode.HALF_UP));

            // Réduction charge de famille (X = 2 000 * enfants)
            BigDecimal redEnf = maxReductionEnfants;
            row.setReductionChargeFamille(redEnf.setScale(2, RoundingMode.HALF_UP));

            // Impôt dû (Y = IRSA net)
            BigDecimal impDu = totalIrsaNetCumule;
            row.setImpotDu(impDu.setScale(2, RoundingMode.HALF_UP));

            rows.add(row);
        }

        return rows;
    }

    /**
     * Exporte l'État 301 SAFI au format CSV (compatible Excel avec BOM UTF-8 et séparateur point-virgule).
     */
    public String exporterSafiCsv(Integer idPeriode, Connection c) throws Exception {
        List<SafiRowDto> data = genererDonneesSafi(idPeriode, c);

        // Informations d'en-tête de la société
        String sqlEntete = "SELECT code AS code_periode, libelle AS libelle_periode FROM periode_paie WHERE id_periode_paie = ?";

        String codePeriode = "";
        String libellePeriode = "";
        var company = PayrollExportContextResolver.forPeriod(idPeriode, c);
        String raisonSociale = company.name();
        String nif = company.nifCompact();

        try (PreparedStatement ps = c.prepareStatement(sqlEntete)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    codePeriode = rs.getString("code_periode");
                    libellePeriode = rs.getString("libelle_periode");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        // BOM UTF-8
        sb.append('\ufeff');

        // En-tête officiel DGI
        sb.append("REPOBLIKAN'I MADAGASIKARA;Tanindrazana - Fahafahana - Fandrosoana\n");
        sb.append("MINISTERE DE L'ECONOMIE ET DES FINANCES\n");
        sb.append("DIRECTION GENERALE DES IMPOTS\n");
        sb.append("DIRECTION DES GRANDES ENTREPRISES\n");
        sb.append("ETAT 301 - ANNEXE DE DECLARATION DE L'IRSA (SAFI)\n");
        sb.append("NOM OU RAISON SOCIALE :;").append(raisonSociale).append("\n");
        sb.append("NIF :;").append(nif).append(";Periode :;").append(libellePeriode != null ? libellePeriode : codePeriode).append("\n\n");

        // Ligne des 25 colonnes réglementaires
        sb.append("N° Ordre;N° Mle;N° d'affiliation (CNaPS);Nom et prenoms;CIN / Carte de resident;Date d'embauche;Date de debauchage;Fonction;")
          .append("Salaire de base;Indemnites imposables;Indemnites non imposables;Avantages en nature imposables;Avantages en nature exoneres;")
          .append("Heures supplementaires;Primes et gratifications;Autres;Salaire brut;CNaPS;OSTIE / OMSI;Salaire net;Autre deduction;")
          .append("Montant imposable;Impot correspondant;Reduction charge famille;Impot du\n");

        DecimalFormat df = new DecimalFormat("0.00", DFS);
        df.setGroupingUsed(false);

        // Cumuls pour la ligne TOTAL
        BigDecimal totBase = BigDecimal.ZERO;
        BigDecimal totIndemImp = BigDecimal.ZERO;
        BigDecimal totIndemNonImp = BigDecimal.ZERO;
        BigDecimal totAvNatImp = BigDecimal.ZERO;
        BigDecimal totAvNatExo = BigDecimal.ZERO;
        BigDecimal totHs = BigDecimal.ZERO;
        BigDecimal totPrimes = BigDecimal.ZERO;
        BigDecimal totAutres = BigDecimal.ZERO;
        BigDecimal totBrut = BigDecimal.ZERO;
        BigDecimal totCnaps = BigDecimal.ZERO;
        BigDecimal totSante = BigDecimal.ZERO;
        BigDecimal totNet = BigDecimal.ZERO;
        BigDecimal totAutreDed = BigDecimal.ZERO;
        BigDecimal totImposable = BigDecimal.ZERO;
        BigDecimal totImpotCorresp = BigDecimal.ZERO;
        BigDecimal totRedFamille = BigDecimal.ZERO;
        BigDecimal totImpotDu = BigDecimal.ZERO;

        for (SafiRowDto r : data) {
            sb.append(r.getNumeroOrdre()).append(";")
              .append(escape(r.getMatricule())).append(";")
              .append(escape(r.getAffiliationCnaps())).append(";")
              .append(escape(r.getNomPrenoms())).append(";")
              .append(escape(r.getCin())).append(";")
              .append(escape(r.getDateEmbauche())).append(";")
              .append(escape(r.getDateDebauchage())).append(";")
              .append(escape(r.getFonction())).append(";")
              .append(df.format(r.getSalaireBase())).append(";")
              .append(df.format(r.getIndemnitesImposables())).append(";")
              .append(df.format(r.getIndemnitesNonImposables())).append(";")
              .append(df.format(r.getAvantagesNatureImposables())).append(";")
              .append(df.format(r.getAvantagesNatureExoneres())).append(";")
              .append(df.format(r.getHeuresSupplementaires())).append(";")
              .append(df.format(r.getPrimesEtGratifications())).append(";")
              .append(df.format(r.getAutres())).append(";")
              .append(df.format(r.getSalaireBrut())).append(";")
              .append(df.format(r.getDeductionsCnaps())).append(";")
              .append(df.format(r.getDeductionsSante())).append(";")
              .append(df.format(r.getSalaireNet())).append(";")
              .append(df.format(r.getAutreDeduction())).append(";")
              .append(df.format(r.getMontantImposable())).append(";")
              .append(df.format(r.getImpotCorrespondant())).append(";")
              .append(df.format(r.getReductionChargeFamille())).append(";")
              .append(df.format(r.getImpotDu())).append("\n");

            totBase = totBase.add(r.getSalaireBase());
            totIndemImp = totIndemImp.add(r.getIndemnitesImposables());
            totIndemNonImp = totIndemNonImp.add(r.getIndemnitesNonImposables());
            totAvNatImp = totAvNatImp.add(r.getAvantagesNatureImposables());
            totAvNatExo = totAvNatExo.add(r.getAvantagesNatureExoneres());
            totHs = totHs.add(r.getHeuresSupplementaires());
            totPrimes = totPrimes.add(r.getPrimesEtGratifications());
            totAutres = totAutres.add(r.getAutres());
            totBrut = totBrut.add(r.getSalaireBrut());
            totCnaps = totCnaps.add(r.getDeductionsCnaps());
            totSante = totSante.add(r.getDeductionsSante());
            totNet = totNet.add(r.getSalaireNet());
            totAutreDed = totAutreDed.add(r.getAutreDeduction());
            totImposable = totImposable.add(r.getMontantImposable());
            totImpotCorresp = totImpotCorresp.add(r.getImpotCorrespondant());
            totRedFamille = totRedFamille.add(r.getReductionChargeFamille());
            totImpotDu = totImpotDu.add(r.getImpotDu());
        }

        // Ligne de synthèse TOTAL
        sb.append("TOTAL;;;;;;;;")
          .append(df.format(totBase)).append(";")
          .append(df.format(totIndemImp)).append(";")
          .append(df.format(totIndemNonImp)).append(";")
          .append(df.format(totAvNatImp)).append(";")
          .append(df.format(totAvNatExo)).append(";")
          .append(df.format(totHs)).append(";")
          .append(df.format(totPrimes)).append(";")
          .append(df.format(totAutres)).append(";")
          .append(df.format(totBrut)).append(";")
          .append(df.format(totCnaps)).append(";")
          .append(df.format(totSante)).append(";")
          .append(df.format(totNet)).append(";")
          .append(df.format(totAutreDed)).append(";")
          .append(df.format(totImposable)).append(";")
          .append(df.format(totImpotCorresp)).append(";")
          .append(df.format(totRedFamille)).append(";")
          .append(df.format(totImpotDu)).append("\n");

        return sb.toString();
    }

    private String chercherDateFinContrat(Integer idEmploye, Date dateDebutPeriode,
            Date dateFinPeriode, Connection c) throws Exception {
        if (dateDebutPeriode == null || dateFinPeriode == null)
            throw new IllegalStateException("Bornes de campagne manquantes pour l'export.");
        LocalDate departure = PayrollExportContextResolver.departureForPeriod(idEmploye,
                dateDebutPeriode.toLocalDate(), dateFinPeriode.toLocalDate(), c);
        return departure == null ? "" : DATE_FMT.format(departure);
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
    }

    // Format complémentaire du même export ; partage les données et la connexion.
    /**
     * Génère le fichier .ods complet à partir de la période de paie spécifiée.
     */
    public byte[] genererSafiOds(Integer idPeriode, Connection c) throws Exception {
        // 1. Récupération des 25 colonnes réglementaires calculées
        List<SafiRowDto> donnees = genererDonneesSafi(idPeriode, c);

        String sqlEntete = "SELECT libelle AS libelle_periode FROM periode_paie WHERE id_periode_paie = ?";

        String libellePeriode = "PÉRIODE";
        var company = PayrollExportContextResolver.forPeriod(idPeriode, c);
        String raisonSociale = company.name();
        String nif = company.nifCompact();
        String adresse = company.address() == null ? "" : company.address();

        try (PreparedStatement ps = c.prepareStatement(sqlEntete)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getString("libelle_periode") != null) libellePeriode = rs.getString("libelle_periode");
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
