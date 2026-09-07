package mg.galaxam.sirh.model.paie.service.export;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.PaieHonorairePrestation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service de génération de l'Annexe EDI ISI (Impôt Synthétique Intermittent 5%).
 * Produit le flux XML officiel e-Hetra pour la Direction Générale des Impôts de Madagascar.
 */
@Service
public class EdiIsiExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Génère le document XML EDI ISI pour les consultants et prestataires assujettis à l'ISI 5%.
     */
    public String exporterEdiXml(Integer idPeriode, Connection c) throws Exception {
        // 1. Informations de la période et du déclarant (GALAXAM LOGISTICS)
        String sqlPeriode = "SELECT pp.date_fin, rf.nif " +
                "FROM periode_paie pp " +
                "LEFT JOIN ref_filiale rf ON rf.id_filiale = 1 " +
                "WHERE pp.id_periode_paie = ?";

        LocalDate dateFin = LocalDate.now();
        String nifContribuable = "3000019434";

        try (PreparedStatement ps = c.prepareStatement(sqlPeriode)) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    if (rs.getDate("date_fin") != null) {
                        dateFin = rs.getDate("date_fin").toLocalDate();
                    }
                    if (rs.getString("nif") != null) {
                        nifContribuable = rs.getString("nif");
                    }
                }
            }
        }

        int mois = dateFin.getMonthValue();
        int exercice = dateFin.getYear();

        // 2. Récupérer les honoraires et prestations externes sous régime ISI (Axe 1 du Contre-Audit)
        List<PaieHonorairePrestation> honoraires = PaieHonorairePrestation.listerParPeriode(idPeriode, c);

        // Fallback de compatibilité : récupérer d'éventuels bulletins résiduels sous régime ISI
        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriode, c);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<EDI>");
        sb.append("<infos>");
        sb.append("<idEdiType>ISI</idEdiType>");
        sb.append("<rContribuable>").append(escapeXml(nifContribuable)).append("</rContribuable>");
        sb.append("<idImpotNature>ISINT</idImpotNature>");
        sb.append("<idMois>").append(mois).append("</idMois>");
        sb.append("<idExercice>").append(exercice).append("</idExercice>");
        sb.append("</infos>");
        sb.append("<tabs><tab><data>");

        // Lignes issues des honoraires légaux isolés (paie_honoraire_prestation)
        for (PaieHonorairePrestation h : honoraires) {
            long brut = h.getMontantBrut() != null ? h.getMontantBrut().longValue() : 0L;
            long isi = h.getMontantRetenueIsi() != null ? h.getMontantRetenueIsi().longValue() : 0L;
            String datePaiement = h.getDatePaiement() != null ? DATE_FMT.format(h.getDatePaiement().toLocalDate()) : DATE_FMT.format(dateFin);

            sb.append("<line>");
            sb.append("<k><id>texte_1</id><o>").append(escapeXml(h.getNomComplet())).append("</o></k>");
            sb.append("<k><id>texte_2</id><o>").append(escapeXml(h.getCinOuNif())).append("</o></k>");
            sb.append("<k><id>texte_3</id><o>").append(escapeXml(h.getIntitulePrestation())).append("</o></k>");
            sb.append("<k><id>texte_4</id><o>").append(escapeXml(h.getNatureService() != null ? h.getNatureService() : "SERVICE SYSTEMATIQUE")).append("</o></k>");
            sb.append("<k><id>date_1</id><o>").append(datePaiement).append("</o></k>");
            sb.append("<k><id>montant_1</id><o>").append(brut).append("</o></k>");
            sb.append("<k><id>montant_2</id><o>").append(isi).append("</o></k>");
            sb.append("<k><id>texte_5</id><o>").append(escapeXml(h.getAdresse() != null ? h.getAdresse() : "ANTANANARIVO")).append("</o></k>");
            sb.append("<k><id>texte_6</id><o></o></k>");
            sb.append("<k><id>texte_7</id><o>").append(escapeXml(h.getCommune() != null ? h.getCommune() : "ANTANANARIVO RENIVOHITRA C.U")).append("</o></k>");
            sb.append("<k><id>texte_8</id><o>").append(escapeXml(h.getRegion() != null ? h.getRegion() : "ANALAMANGA")).append("</o></k>");
            sb.append("<k><id>texte_9</id><o>").append(escapeXml(h.getDistrict() != null ? h.getDistrict() : "ANTANANARIVO RENIVOHITRA")).append("</o></k>");
            sb.append("<k><id>texte_10</id><o>").append(escapeXml(h.getProvince() != null ? h.getProvince() : "ANTANANARIVO")).append("</o></k>");
            sb.append("</line>");
        }

        // Lignes issues d'anciens bulletins résiduels sous régime ISI (si non encore migrés)
        for (BulletinPaie bp : bulletins) {
            boolean isConsultant = "CONSULTANT".equalsIgnoreCase(bp.getRegimeIntervenant()) ||
                                   "ISI_5".equalsIgnoreCase(bp.getRegimeIntervenant()) ||
                                   (bp.getRetenueIsi5() != null && bp.getRetenueIsi5().compareTo(BigDecimal.ZERO) > 0);

            if (!isConsultant) {
                continue;
            }

            // Éviter le doublon si déjà présent dans honoraires
            boolean dejaPresent = honoraires.stream().anyMatch(h -> h.getIdIntervenant() != null && h.getIdIntervenant().equals(bp.getIdEmploye()));
            if (dejaPresent) continue;

            String adresse = "ANTANANARIVO";
            String commune = "ANTANANARIVO RENIVOHITRA C.U";
            String region = "ANALAMANGA";
            String district = "ANTANANARIVO RENIVOHITRA";
            String province = "ANTANANARIVO";

            String sqlEmp = "SELECT adresse, ville FROM employe WHERE id_employe = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlEmp)) {
                ps.setInt(1, bp.getIdEmploye());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String adr = rs.getString("adresse");
                        String vil = rs.getString("ville");
                        if (adr != null && !adr.isBlank()) adresse = adr;
                        if (vil != null && !vil.isBlank()) commune = vil;
                    }
                }
            }

            long brut = bp.getTotalGainsBruts() != null ? bp.getTotalGainsBruts().longValue() : 0L;
            long isi = bp.getRetenueIsi5() != null ? bp.getRetenueIsi5().longValue() : 0L;
            String datePaiement = DATE_FMT.format(dateFin);

            sb.append("<line>");
            sb.append("<k><id>texte_1</id><o>").append(escapeXml(bp.getNomPrenom())).append("</o></k>");
            sb.append("<k><id>texte_2</id><o>").append(escapeXml(bp.getCinNumero() != null ? bp.getCinNumero() : "")).append("</o></k>");
            sb.append("<k><id>texte_3</id><o>").append(escapeXml(bp.getIntitulePoste())).append("</o></k>");
            sb.append("<k><id>texte_4</id><o>SERVICE SYSTEMATIQUE</o></k>");
            sb.append("<k><id>date_1</id><o>").append(datePaiement).append("</o></k>");
            sb.append("<k><id>montant_1</id><o>").append(brut).append("</o></k>");
            sb.append("<k><id>montant_2</id><o>").append(isi).append("</o></k>");
            sb.append("<k><id>texte_5</id><o>").append(escapeXml(adresse)).append("</o></k>");
            sb.append("<k><id>texte_6</id><o></o></k>");
            sb.append("<k><id>texte_7</id><o>").append(escapeXml(commune)).append("</o></k>");
            sb.append("<k><id>texte_8</id><o>").append(escapeXml(region)).append("</o></k>");
            sb.append("<k><id>texte_9</id><o>").append(escapeXml(district)).append("</o></k>");
            sb.append("<k><id>texte_10</id><o>").append(escapeXml(province)).append("</o></k>");
            sb.append("</line>");
        }

        sb.append("</data></tab></tabs>");
        sb.append("</EDI>");

        return sb.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
