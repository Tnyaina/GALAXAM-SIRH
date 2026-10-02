package mg.galaxam.sirh.paie.application.export;


import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.domain.dto.export.VirementRowDto;
import org.springframework.stereotype.Service;

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

/**
 * Service d'exportation de l'ordre de virement bancaire interbancaire.
 * Génère le fichier de télétransmission conforme aux standards des banques malgaches (BNI, BMOI, BOA, SG).
 */
@Service
public class VirementBancaireExportService {

    private static final DecimalFormatSymbols DFS = new DecimalFormatSymbols(Locale.FRENCH);
    static {
        DFS.setGroupingSeparator(' ');
        DFS.setDecimalSeparator(',');
    }

    public List<VirementRowDto> genererDonneesVirement(Integer idPeriode, Connection c) throws Exception {
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriode, c);
        List<VirementRowDto> rows = new ArrayList<>();

        String codePeriode = "";
        String sqlPeriode = "SELECT code FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) codePeriode = rs.getString("code");
            }
        }

        int ordre = 1;
        for (BulletinPaie bp : bulletins) {
            // Filtrer strictement les règlements par virement bancaire (exclure espèces, chèques)
            if (bp.getModePaiement() != null && !"VIREMENT".equalsIgnoreCase(bp.getModePaiement())) {
                continue;
            }

            BigDecimal net = bp.getNetAPayer() != null ? bp.getNetAPayer() : BigDecimal.ZERO;
            if (net.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // Pas de virement si net nul ou négatif
            }

            VirementRowDto row = new VirementRowDto();
            row.setNumeroOrdre(ordre++);
            row.setMatricule(bp.getMatricule());
            row.setNomBeneficiaire(bp.getNomPrenom());
            row.setBanque(bp.getBanqueNom() != null ? bp.getBanqueNom() : "Non spécifié");

            String rib = bp.getBanqueRib() != null ? bp.getBanqueRib().trim() : "";
            row.setRibComplet(rib);

            // Découpage du RIB malgache normalisé (Ex: 00004 00123 00000000001 45)
            String[] segments = rib.split("\\s+");
            if (segments.length >= 4) {
                row.setCodeBanque(segments[0]);
                row.setCodeGuichet(segments[1]);
                row.setNumeroCompte(segments[2]);
                row.setCleRib(segments[3]);
            } else {
                row.setCodeBanque("");
                row.setCodeGuichet("");
                row.setNumeroCompte(rib);
                row.setCleRib("");
            }

            row.setNetAPayer(net.setScale(2, RoundingMode.HALF_UP));
            row.setDevise("MGA");
            row.setReferencePaiement(bp.getReferenceBulletin());
            String motifType = "STC".equalsIgnoreCase(bp.getTypeBulletin()) ? "STC" : "SALAIRE";
            row.setMotifVirement("VIRT " + motifType + " " + codePeriode + " " + bp.getMatricule());

            rows.add(row);
        }

        return rows;
    }

    public String exporterVirementCsv(Integer idPeriode, Connection c) throws Exception {
        List<VirementRowDto> data = genererDonneesVirement(idPeriode, c);
        var company = PayrollExportCompanyResolver.forPeriod(idPeriode, c);
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

        sb.append("ORDRE DE VIREMENT DE SALAIRES INTERBANCAIRE\n");
        sb.append("EMETTEUR :;").append(escape(company.name())).append("\n");
        sb.append("PERIODE :;").append(libellePeriode != null ? libellePeriode : codePeriode).append("\n\n");

        sb.append("N° Ordre;Matricule;Beneficiaire;Banque;Code Banque;Code Guichet;Numero Compte;Cle RIB;RIB Complet;Net a Payer;Devise;Reference;Motif\n");

        DecimalFormat df = new DecimalFormat("0.00", DFS);
        df.setGroupingUsed(false);

        BigDecimal totalVirement = BigDecimal.ZERO;

        for (VirementRowDto r : data) {
            sb.append(r.getNumeroOrdre()).append(";")
              .append(escape(r.getMatricule())).append(";")
              .append(escape(r.getNomBeneficiaire())).append(";")
              .append(escape(r.getBanque())).append(";")
              .append(escape(r.getCodeBanque())).append(";")
              .append(escape(r.getCodeGuichet())).append(";")
              .append(escape(r.getNumeroCompte())).append(";")
              .append(escape(r.getCleRib())).append(";")
              .append(escape(r.getRibComplet())).append(";")
              .append(df.format(r.getNetAPayer())).append(";")
              .append(r.getDevise()).append(";")
              .append(escape(r.getReferencePaiement())).append(";")
              .append(escape(r.getMotifVirement())).append("\n");

            totalVirement = totalVirement.add(r.getNetAPayer());
        }

        sb.append("TOTAL;;;;;;;;")
          .append(df.format(totalVirement)).append(";MGA;;\n");

        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace(";", ",");
    }
}
