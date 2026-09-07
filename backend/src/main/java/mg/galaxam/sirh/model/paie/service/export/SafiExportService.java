package mg.galaxam.sirh.model.paie.service.export;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service d'exportation de l'État 301 SAFI IRSA (25 colonnes réglementaires DGI Madagascar).
 * Conforme au barème de la Loi de Finances et aux directives de téléversement e-Hetra.
 */
@Service
public class SafiExportService {

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

        int ordre = 1;
        for (BulletinPaie bp : bulletins) {
            // Filtrer les prestataires / consultants sous régime ISI (qui vont sur l'EDI ISI)
            if ("CONSULTANT".equalsIgnoreCase(bp.getRegimeIntervenant()) || "ISI_5".equalsIgnoreCase(bp.getRegimeIntervenant())) {
                continue;
            }

            List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(bp.getIdBulletin(), c);

            SafiRowDto row = new SafiRowDto();
            row.setNumeroOrdre(ordre++);
            row.setMatricule(bp.getMatricule());
            row.setAffiliationCnaps(bp.getNumeroCnaps() != null ? bp.getNumeroCnaps() : "");
            row.setNomPrenoms(bp.getNomPrenom());
            row.setCin(bp.getCinNumero() != null ? bp.getCinNumero() : "");
            row.setDateEmbauche(bp.getDateEmbauche() != null ? DATE_FMT.format(bp.getDateEmbauche().toLocalDate()) : "");
            row.setDateDebauchage(chercherDateFinContrat(bp.getIdEmploye(), c));
            row.setFonction(bp.getIntitulePoste());

            // Ventilation par rubriques
            BigDecimal base = BigDecimal.ZERO;
            BigDecimal indemImpos = BigDecimal.ZERO;
            BigDecimal indemNonImpos = BigDecimal.ZERO;
            BigDecimal hs = BigDecimal.ZERO;
            BigDecimal primes = BigDecimal.ZERO;
            BigDecimal repas = BigDecimal.ZERO;
            BigDecimal cnaps = BigDecimal.ZERO;
            BigDecimal sante = BigDecimal.ZERO;

            for (BulletinPaieLigne l : lignes) {
                String code = l.getCodeRubrique();
                BigDecimal mnt = l.getMontantSalarial() != null ? l.getMontantSalarial() : BigDecimal.ZERO;

                switch (code) {
                    case "SALAIRE_BASE":
                    case "RETENUE_ABSENCE":
                    case "INDEMNITE_CONGE_PAYE":
                        base = base.add(mnt);
                        break;
                    case "HS_130":
                    case "HS_150":
                    case "HEURES_NUIT":
                        hs = hs.add(mnt);
                        break;
                    case "PRIME_ASSIDUITE":
                    case "PRIME_PERFORMANCE":
                    case "PRIME_EXCEPTIONNELLE":
                        primes = primes.add(mnt);
                        break;
                    case "INDEMNITE_TRANSPORT":
                        indemNonImpos = indemNonImpos.add(mnt);
                        break;
                    case "INDEMNITE_REPAS":
                        repas = repas.add(mnt);
                        break;
                    case "COTISATION_CNAPS":
                        cnaps = cnaps.add(mnt);
                        break;
                    case "COTISATION_OSTIE":
                    case "COTISATION_OMSI":
                        sante = sante.add(mnt);
                        break;
                    default:
                        break;
                }
            }

            if (base.compareTo(BigDecimal.ZERO) < 0) {
                base = BigDecimal.ZERO;
            }

            row.setSalaireBase(base.setScale(2, RoundingMode.HALF_UP));
            row.setIndemnitesImposables(indemImpos.setScale(2, RoundingMode.HALF_UP));
            row.setIndemnitesNonImposables(indemNonImpos.setScale(2, RoundingMode.HALF_UP));
            row.setAvantagesNatureImposables(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            row.setAvantagesNatureExoneres(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            row.setHeuresSupplementaires(hs.setScale(2, RoundingMode.HALF_UP));
            row.setPrimesEtGratifications(primes.setScale(2, RoundingMode.HALF_UP));
            row.setAutres(repas.setScale(2, RoundingMode.HALF_UP));

            // Salaire brut (Q = I + J + L + N + O + P)
            BigDecimal brut = bp.getTotalGainsBruts() != null ? bp.getTotalGainsBruts() : BigDecimal.ZERO;
            row.setSalaireBrut(brut.setScale(2, RoundingMode.HALF_UP));

            row.setDeductionsCnaps(cnaps.setScale(2, RoundingMode.HALF_UP));
            row.setDeductionsSante(sante.setScale(2, RoundingMode.HALF_UP));

            // Salaire Net (T = Q - R - S)
            BigDecimal netAvantImpot = brut.subtract(cnaps).subtract(sante);
            if (netAvantImpot.compareTo(BigDecimal.ZERO) < 0) {
                netAvantImpot = BigDecimal.ZERO;
            }
            row.setSalaireNet(netAvantImpot.setScale(2, RoundingMode.HALF_UP));

            row.setAutreDeduction(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

            // Montant imposable (V = 100 * floor(T / 100))
            BigDecimal imposable = bp.getBaseIrsaArrondie() != null ? bp.getBaseIrsaArrondie() : BigDecimal.ZERO;
            row.setMontantImposable(imposable.setScale(2, RoundingMode.HALF_UP));

            // Impôt correspondant (W = IRSA progressif brut)
            BigDecimal impCorresp = bp.getIrsaBrut() != null ? bp.getIrsaBrut() : BigDecimal.ZERO;
            row.setImpotCorrespondant(impCorresp.setScale(2, RoundingMode.HALF_UP));

            // Réduction charge de famille (X = 2 000 * enfants)
            BigDecimal redEnf = bp.getReductionEnfants() != null ? bp.getReductionEnfants() : BigDecimal.ZERO;
            row.setReductionChargeFamille(redEnf.setScale(2, RoundingMode.HALF_UP));

            // Impôt dû (Y = IRSA net)
            BigDecimal impDu = bp.getIrsaNet() != null ? bp.getIrsaNet() : BigDecimal.ZERO;
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
        String sqlEntete = "SELECT pp.code AS code_periode, pp.libelle AS libelle_periode, " +
                "pp.date_debut, pp.date_fin, " +
                "rf.raison_sociale, rf.nif, rf.stat " +
                "FROM periode_paie pp " +
                "LEFT JOIN ref_filiale rf ON rf.id_filiale = 1 " +
                "WHERE pp.id_periode_paie = ?";

        String codePeriode = "";
        String libellePeriode = "";
        String raisonSociale = "GALAXAM LOGISTICS";
        String nif = "3000019434";

        try (PreparedStatement ps = c.prepareStatement(sqlEntete)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    codePeriode = rs.getString("code_periode");
                    libellePeriode = rs.getString("libelle_periode");
                    if (rs.getString("raison_sociale") != null) raisonSociale = rs.getString("raison_sociale");
                    if (rs.getString("nif") != null) nif = rs.getString("nif");
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

    private String chercherDateFinContrat(Integer idEmploye, Connection c) {
        String sql = "SELECT date_fin FROM contrat_travail WHERE id_employe = ? ORDER BY date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getDate("date_fin") != null) {
                    return DATE_FMT.format(rs.getDate("date_fin").toLocalDate());
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
