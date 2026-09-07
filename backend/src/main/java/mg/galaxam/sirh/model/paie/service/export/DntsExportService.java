package mg.galaxam.sirh.model.paie.service.export;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paieconfig.PaieConfigLegale;
import org.springframework.stereotype.Service;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service d'exportation de la Déclaration Nominative Trimestrielle des Salaires (DNTS) pour la CNaPS et le FMFP.
 */
@Service
public class DntsExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }

    public List<DntsRowDto> genererDonneesDnts(Integer idPeriode, Connection c) throws Exception {
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriode, c);
        List<DntsRowDto> rows = new ArrayList<>();

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

        int ordre = 1;
        for (BulletinPaie bp : bulletins) {
            // Exclure les consultants sans affiliation CNaPS
            if ("CONSULTANT".equalsIgnoreCase(bp.getRegimeIntervenant()) || "ISI_5".equalsIgnoreCase(bp.getRegimeIntervenant())) {
                continue;
            }

            List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(bp.getIdBulletin(), c);

            DntsRowDto row = new DntsRowDto();
            row.setNumeroOrdre(ordre++);
            row.setMatricule(bp.getMatricule());
            row.setNumAffiliationCnaps(bp.getNumeroCnaps() != null ? bp.getNumeroCnaps() : "");
            row.setNomPrenom(bp.getNomPrenom());
            row.setRefCin(bp.getCinNumero() != null ? bp.getCinNumero() : "");
            row.setDateEmbauche(bp.getDateEmbauche() != null ? DATE_FMT.format(bp.getDateEmbauche().toLocalDate()) : "");
            row.setDateDebauchage(chercherDateFinContrat(bp.getIdEmploye(), dateDebutPeriode, dateFinPeriode, c));

            BigDecimal brut = bp.getTotalGainsBruts() != null ? bp.getTotalGainsBruts() : BigDecimal.ZERO;
            row.setSalaireBrutReel(brut.setScale(2, RoundingMode.HALF_UP));

            BigDecimal assietteCnaps = BigDecimal.ZERO;
            BigDecimal cnapsSal = BigDecimal.ZERO;
            BigDecimal cnapsPat = BigDecimal.ZERO;
            BigDecimal fmfpPat = BigDecimal.ZERO;
            BigDecimal santeSal = BigDecimal.ZERO;
            BigDecimal santePat = BigDecimal.ZERO;

            for (BulletinPaieLigne l : lignes) {
                String code = l.getCodeRubrique();
                if (code == null) continue;

                if ("COTISATION_CNAPS".equals(code) || "COTISATION_CNAPS_SAL".equals(code) || "COTISATION_CNAPS_PAT".equals(code)) {
                    if (l.getBaseCalcul() != null && l.getBaseCalcul().compareTo(BigDecimal.ZERO) > 0) {
                        assietteCnaps = l.getBaseCalcul();
                    }
                    if (l.getMontantSalarial() != null) cnapsSal = cnapsSal.add(l.getMontantSalarial());
                    if (l.getMontantPatronal() != null) cnapsPat = cnapsPat.add(l.getMontantPatronal());
                } else if ("COTISATION_FMFP".equals(code) || "COTISATION_FMFP_PAT".equals(code)) {
                    if (l.getMontantPatronal() != null) fmfpPat = fmfpPat.add(l.getMontantPatronal());
                } else if ("COTISATION_OSTIE".equals(code) || "COTISATION_OSTIE_SAL".equals(code) || "COTISATION_OSTIE_PAT".equals(code)
                        || "COTISATION_OMSI".equals(code) || "COTISATION_OMSI_SAL".equals(code) || "COTISATION_OMSI_PAT".equals(code)) {
                    if (l.getMontantSalarial() != null) santeSal = santeSal.add(l.getMontantSalarial());
                    if (l.getMontantPatronal() != null) santePat = santePat.add(l.getMontantPatronal());
                }
            }

            row.setAssiettePlafonneeCnaps(assietteCnaps.setScale(2, RoundingMode.HALF_UP));
            row.setCnapsSalarial(cnapsSal.setScale(2, RoundingMode.HALF_UP));
            row.setCnapsPatronal(cnapsPat.setScale(2, RoundingMode.HALF_UP));
            row.setFmfpPatronal(fmfpPat.setScale(2, RoundingMode.HALF_UP));
            row.setSanteSalarial(santeSal.setScale(2, RoundingMode.HALF_UP));
            row.setSantePatronal(santePat.setScale(2, RoundingMode.HALF_UP));

            BigDecimal totalCotis = cnapsSal.add(cnapsPat).add(fmfpPat).add(santeSal).add(santePat);
            row.setTotalCotisations(totalCotis.setScale(2, RoundingMode.HALF_UP));

            rows.add(row);
        }

        return rows;
    }

    public String exporterDntsCsv(Integer idPeriode, Connection c) throws Exception {
        List<DntsRowDto> data = genererDonneesDnts(idPeriode, c);

        String sqlPeriode = "SELECT code, libelle FROM periode_paie WHERE id_periode_paie = ?";
        String codePeriode = "";
        String libellePeriode = "";

        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    codePeriode = rs.getString("code");
                    libellePeriode = rs.getString("libelle");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff'); // BOM UTF-8

        sb.append("CNAPS - CAISSE NATIONALE DE PREVOYANCE SOCIALE & FMFP\n");
        sb.append("DECLARATION NOMINATIVE DES SALAIRES ET DES COTISATIONS\n");
        sb.append("EMPLOYEUR :;GALAXAM LOGISTICS;NIF :;3000019434;PERIODE :;").append(libellePeriode != null ? libellePeriode : codePeriode).append("\n\n");

        sb.append("N° Ordre;Matricule;N° CNaPS;Nom et Prenom;CIN;Date Embauche;Date Debauchage;")
          .append("Salaire Brut Reel;Assiette Plafonnee CNaPS;CNaPS Salarial (1%);CNaPS Patronal (13%);FMFP Patronal (1%);")
          .append("Sante Salarial;Sante Patronal;Total Cotisations\n");

        DecimalFormat df = new DecimalFormat("0.00", DFS);
        df.setGroupingUsed(false);

        BigDecimal totBrut = BigDecimal.ZERO;
        BigDecimal totAssiette = BigDecimal.ZERO;
        BigDecimal totCnapsSal = BigDecimal.ZERO;
        BigDecimal totCnapsPat = BigDecimal.ZERO;
        BigDecimal totFmfpPat = BigDecimal.ZERO;
        BigDecimal totSanteSal = BigDecimal.ZERO;
        BigDecimal totSantePat = BigDecimal.ZERO;
        BigDecimal totGlobal = BigDecimal.ZERO;

        for (DntsRowDto r : data) {
            sb.append(r.getNumeroOrdre()).append(";")
              .append(escape(r.getMatricule())).append(";")
              .append(escape(r.getNumAffiliationCnaps())).append(";")
              .append(escape(r.getNomPrenom())).append(";")
              .append(escape(r.getRefCin())).append(";")
              .append(escape(r.getDateEmbauche())).append(";")
              .append(escape(r.getDateDebauchage())).append(";")
              .append(df.format(r.getSalaireBrutReel())).append(";")
              .append(df.format(r.getAssiettePlafonneeCnaps())).append(";")
              .append(df.format(r.getCnapsSalarial())).append(";")
              .append(df.format(r.getCnapsPatronal())).append(";")
              .append(df.format(r.getFmfpPatronal())).append(";")
              .append(df.format(r.getSanteSalarial())).append(";")
              .append(df.format(r.getSantePatronal())).append(";")
              .append(df.format(r.getTotalCotisations())).append("\n");

            totBrut = totBrut.add(r.getSalaireBrutReel());
            totAssiette = totAssiette.add(r.getAssiettePlafonneeCnaps());
            totCnapsSal = totCnapsSal.add(r.getCnapsSalarial());
            totCnapsPat = totCnapsPat.add(r.getCnapsPatronal());
            totFmfpPat = totFmfpPat.add(r.getFmfpPatronal());
            totSanteSal = totSanteSal.add(r.getSanteSalarial());
            totSantePat = totSantePat.add(r.getSantePatronal());
            totGlobal = totGlobal.add(r.getTotalCotisations());
        }

        sb.append("TOTAL;;;;;;;")
          .append(df.format(totBrut)).append(";")
          .append(df.format(totAssiette)).append(";")
          .append(df.format(totCnapsSal)).append(";")
          .append(df.format(totCnapsPat)).append(";")
          .append(df.format(totFmfpPat)).append(";")
          .append(df.format(totSanteSal)).append(";")
          .append(df.format(totSantePat)).append(";")
          .append(df.format(totGlobal)).append("\n");

        return sb.toString();
    }

    /**
     * Génère la Déclaration Nominative Trimestrielle des Salaires (DNTS CNaPS & FMFP)
     * en agrégeant les 3 mois d'un trimestre donné (T1 = Jan-Mar, T2 = Avr-Juin, T3 = Juil-Sept, T4 = Oct-Déc).
     */
    public List<DntsRowDto> genererDonneesDntsTrimestre(int annee, int trimestre, Connection c) throws Exception {
        if (trimestre < 1 || trimestre > 4) {
            throw new IllegalArgumentException("Le trimestre doit être compris entre 1 et 4.");
        }
        int moisDebut = (trimestre - 1) * 3 + 1;
        int moisFin = moisDebut + 2;

        String sqlPeriodes = "SELECT id_periode_paie FROM periode_paie " +
                "WHERE EXTRACT(YEAR FROM date_debut) = ? " +
                "AND EXTRACT(MONTH FROM date_debut) BETWEEN ? AND ? " +
                "ORDER BY date_debut ASC";

        List<Integer> idsPeriodes = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sqlPeriodes)) {
            ps.setInt(1, annee);
            ps.setInt(2, moisDebut);
            ps.setInt(3, moisFin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    idsPeriodes.add(rs.getInt("id_periode_paie"));
                }
            }
        }

        // Configuration légale pour les taux et le plafond CNaPS du trimestre
        LocalDate dateDebutTrimestre = LocalDate.of(annee, moisDebut, 1);
        PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, dateDebutTrimestre);
        BigDecimal plafondMensuel = (config != null && config.getPlafondCotisationMensuel() != null)
                ? config.getPlafondCotisationMensuel()
                : new BigDecimal("2101440.00");
        BigDecimal tauxCnapsSal = new BigDecimal("0.01");
        BigDecimal tauxCnapsPat = new BigDecimal("0.13");
        BigDecimal tauxFmfpPat = new BigDecimal("0.01");

        // Agréger par matricule sur l'ensemble du trimestre
        Map<String, DntsRowDto> mapSalaries = new LinkedHashMap<>();
        Map<String, Integer> moisPresents = new HashMap<>();
        int ordre = 1;

        for (Integer idPer : idsPeriodes) {
            List<DntsRowDto> moisRows = genererDonneesDnts(idPer, c);
            for (DntsRowDto r : moisRows) {
                String matr = r.getMatricule();
                moisPresents.merge(matr, 1, Integer::sum);

                if (!mapSalaries.containsKey(matr)) {
                    DntsRowDto cumul = new DntsRowDto();
                    cumul.setNumeroOrdre(ordre++);
                    cumul.setMatricule(matr);
                    cumul.setNumAffiliationCnaps(r.getNumAffiliationCnaps());
                    cumul.setNomPrenom(r.getNomPrenom());
                    cumul.setRefCin(r.getRefCin());
                    cumul.setDateEmbauche(r.getDateEmbauche());
                    cumul.setDateDebauchage(r.getDateDebauchage());
                    cumul.setSalaireBrutReel(r.getSalaireBrutReel());
                    cumul.setAssiettePlafonneeCnaps(r.getAssiettePlafonneeCnaps());
                    cumul.setCnapsSalarial(r.getCnapsSalarial());
                    cumul.setCnapsPatronal(r.getCnapsPatronal());
                    cumul.setFmfpPatronal(r.getFmfpPatronal());
                    cumul.setSanteSalarial(r.getSanteSalarial());
                    cumul.setSantePatronal(r.getSantePatronal());
                    cumul.setTotalCotisations(r.getTotalCotisations());
                    mapSalaries.put(matr, cumul);
                } else {
                    DntsRowDto exist = mapSalaries.get(matr);
                    exist.setSalaireBrutReel(exist.getSalaireBrutReel().add(r.getSalaireBrutReel()));
                    exist.setSanteSalarial(exist.getSanteSalarial().add(r.getSanteSalarial()));
                    exist.setSantePatronal(exist.getSantePatronal().add(r.getSantePatronal()));
                    if (r.getDateDebauchage() != null && !r.getDateDebauchage().isBlank()) {
                        exist.setDateDebauchage(r.getDateDebauchage());
                    }
                }
            }
        }

        // Régularisation progressive trimestrielle CNaPS (Loi de Prévoyance Sociale Madagascar) :
        // AssietteTrimestrielle = min(BrutCumulé, nbMoisPresents × PlafondMensuel 8x SME).
        // Évite la sous-déclaration de cotisations lorsque le brut fluctue d'un mois à l'autre dans le même trimestre.
        for (Map.Entry<String, DntsRowDto> entry : mapSalaries.entrySet()) {
            String matr = entry.getKey();
            DntsRowDto row = entry.getValue();
            int nbMois = moisPresents.getOrDefault(matr, 1);
            BigDecimal plafondTrimestriel = plafondMensuel.multiply(BigDecimal.valueOf(nbMois));

            BigDecimal assietteTrim = row.getSalaireBrutReel().min(plafondTrimestriel).setScale(2, RoundingMode.HALF_UP);
            row.setAssiettePlafonneeCnaps(assietteTrim);

            BigDecimal cnapsSal = assietteTrim.multiply(tauxCnapsSal).setScale(2, RoundingMode.HALF_UP);
            BigDecimal cnapsPat = assietteTrim.multiply(tauxCnapsPat).setScale(2, RoundingMode.HALF_UP);
            BigDecimal fmfpPat = row.getSalaireBrutReel().multiply(tauxFmfpPat).setScale(2, RoundingMode.HALF_UP);

            row.setCnapsSalarial(cnapsSal);
            row.setCnapsPatronal(cnapsPat);
            row.setFmfpPatronal(fmfpPat);

            BigDecimal totCotis = cnapsSal.add(cnapsPat).add(fmfpPat)
                    .add(row.getSanteSalarial()).add(row.getSantePatronal());
            row.setTotalCotisations(totCotis.setScale(2, RoundingMode.HALF_UP));
        }

        return new ArrayList<>(mapSalaries.values());
    }

    public String exporterDntsTrimestreCsv(int annee, int trimestre, Connection c) throws Exception {
        List<DntsRowDto> data = genererDonneesDntsTrimestre(annee, trimestre, c);

        StringBuilder sb = new StringBuilder();
        sb.append('\ufeff'); // BOM UTF-8

        sb.append("CNAPS - CAISSE NATIONALE DE PREVOYANCE SOCIALE & FMFP\n");
        sb.append("DECLARATION NOMINATIVE TRIMESTRIELLE DES SALAIRES ET DES COTISATIONS (DNTS)\n");
        sb.append("EMPLOYEUR :;GALAXAM LOGISTICS;NIF :;3000019434;TRIMESTRE :;T").append(trimestre).append(" ").append(annee).append("\n\n");

        sb.append("N° Ordre;Matricule;N° CNaPS;Nom et Prenom;CIN;Date Embauche;Date Debauchage;")
          .append("Salaire Brut Reel;Assiette Plafonnee CNaPS;CNaPS Salarial (1%);CNaPS Patronal (13%);FMFP Patronal (1%);")
          .append("Sante Salarial;Sante Patronal;Total Cotisations\n");

        DecimalFormat df = new DecimalFormat("0.00", DFS);
        df.setGroupingUsed(false);

        BigDecimal totBrut = BigDecimal.ZERO;
        BigDecimal totAssiette = BigDecimal.ZERO;
        BigDecimal totCnapsSal = BigDecimal.ZERO;
        BigDecimal totCnapsPat = BigDecimal.ZERO;
        BigDecimal totFmfpPat = BigDecimal.ZERO;
        BigDecimal totSanteSal = BigDecimal.ZERO;
        BigDecimal totSantePat = BigDecimal.ZERO;
        BigDecimal totGlobal = BigDecimal.ZERO;

        for (DntsRowDto r : data) {
            sb.append(r.getNumeroOrdre()).append(";")
              .append(escape(r.getMatricule())).append(";")
              .append(escape(r.getNumAffiliationCnaps())).append(";")
              .append(escape(r.getNomPrenom())).append(";")
              .append(escape(r.getRefCin())).append(";")
              .append(escape(r.getDateEmbauche())).append(";")
              .append(escape(r.getDateDebauchage())).append(";")
              .append(df.format(r.getSalaireBrutReel())).append(";")
              .append(df.format(r.getAssiettePlafonneeCnaps())).append(";")
              .append(df.format(r.getCnapsSalarial())).append(";")
              .append(df.format(r.getCnapsPatronal())).append(";")
              .append(df.format(r.getFmfpPatronal())).append(";")
              .append(df.format(r.getSanteSalarial())).append(";")
              .append(df.format(r.getSantePatronal())).append(";")
              .append(df.format(r.getTotalCotisations())).append("\n");

            totBrut = totBrut.add(r.getSalaireBrutReel());
            totAssiette = totAssiette.add(r.getAssiettePlafonneeCnaps());
            totCnapsSal = totCnapsSal.add(r.getCnapsSalarial());
            totCnapsPat = totCnapsPat.add(r.getCnapsPatronal());
            totFmfpPat = totFmfpPat.add(r.getFmfpPatronal());
            totSanteSal = totSanteSal.add(r.getSanteSalarial());
            totSantePat = totSantePat.add(r.getSantePatronal());
            totGlobal = totGlobal.add(r.getTotalCotisations());
        }

        sb.append("TOTAL;;;;;;;")
          .append(df.format(totBrut)).append(";")
          .append(df.format(totAssiette)).append(";")
          .append(df.format(totCnapsSal)).append(";")
          .append(df.format(totCnapsPat)).append(";")
          .append(df.format(totFmfpPat)).append(";")
          .append(df.format(totSanteSal)).append(";")
          .append(df.format(totSantePat)).append(";")
          .append(df.format(totGlobal)).append("\n");

        return sb.toString();
    }

    private String chercherDateFinContrat(Integer idEmploye, Date dateDebutPeriode, Date dateFinPeriode, Connection c) {
        String sql = "SELECT ct.date_fin, rsa.code AS statut_rh " +
                     "FROM contrat_travail ct " +
                     "LEFT JOIN employe_affectation_carriere eac ON ct.id_contrat = eac.id_contrat " +
                     "LEFT JOIN ref_statut_rh rsa ON eac.id_statut_rh = rsa.id_statut_rh " +
                     "WHERE ct.id_employe = ? ORDER BY ct.date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDate("date_fin") != null) {
                    Date dateFin = rs.getDate("date_fin");
                    String statutRh = rs.getString("statut_rh");

                    // 1. Échéance future : pas encore débauché
                    if (dateFinPeriode != null && dateFin.after(dateFinPeriode)) {
                        return "";
                    }
                    // 2. Échéance antérieure à la période déclarée :
                    // Si le salarié est encore ACTIF (ex: renouvellement/continuation), ce n'est pas un débauchage.
                    // Si le salarié est non-actif, il s'agit d'un Solde de Tout Compte décalé : afficher la date réelle de cessation.
                    if (dateDebutPeriode != null && dateFin.before(dateDebutPeriode)) {
                        if ("ACTIF".equalsIgnoreCase(statutRh)) {
                            return "";
                        }
                    }
                    return DATE_FMT.format(dateFin.toLocalDate());
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
    }
}
