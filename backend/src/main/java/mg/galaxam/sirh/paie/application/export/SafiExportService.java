package mg.galaxam.sirh.paie.application.export;


import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private String chercherDateFinContrat(Integer idEmploye, Date dateDebutPeriode, Date dateFinPeriode, Connection c) {
        // 1. Détection prioritaire via Solde de Tout Compte (indispensable pour les CDI dont ct.date_fin est NULL)
        String sqlStc = "SELECT date_depart FROM paie_stc WHERE id_employe = ? ORDER BY date_depart DESC LIMIT 1";
        try (PreparedStatement psStc = c.prepareStatement(sqlStc)) {
            psStc.setInt(1, idEmploye);
            try (ResultSet rsStc = psStc.executeQuery()) {
                if (rsStc.next() && rsStc.getDate("date_depart") != null) {
                    Date dateDepart = rsStc.getDate("date_depart");
                    if (dateDebutPeriode != null && dateFinPeriode != null) {
                        if (!dateDepart.before(dateDebutPeriode) && !dateDepart.after(dateFinPeriode)) {
                            return DATE_FMT.format(dateDepart.toLocalDate());
                        }
                    } else {
                        return DATE_FMT.format(dateDepart.toLocalDate());
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Recherche standard sur contrat_travail (échéances CDD)
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

                    // Échéance future : pas encore débauché
                    if (dateFinPeriode != null && dateFin.after(dateFinPeriode)) {
                        return "";
                    }
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
