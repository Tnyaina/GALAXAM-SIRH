package mg.galaxam.sirh.conge.application;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service d'export officiel Excel de l'État Annuel des Congés par Personne.
 * Utilise le modèle réglementaire /templates/annuel/ETAT_CONGES_PAR_PERSONNE.xlsx
 * sans recalcul arbitraire, en respectant la période de référence légale de 12 mois.
 */
@Service
public class CongeAnnuelExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(CongeAnnuelExcelExportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    public byte[] genererEtatCongesIndividuel(int idEmploye, Integer idPeriodeReference) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return genererEtatCongesIndividuel(idEmploye, idPeriodeReference, c);
        }
    }

    public byte[] genererEtatCongesIndividuel(int idEmploye, Integer idPeriodeReference, Connection c) throws Exception {
        // 1. Charger le classeur modèle
        try (InputStream is = getClass().getResourceAsStream("/templates/annuel/ETAT_CONGES_PAR_PERSONNE.xlsx")) {
            if (is == null) {
                throw new IllegalStateException("Le modèle Excel /templates/annuel/ETAT_CONGES_PAR_PERSONNE.xlsx est introuvable.");
            }
            try (XSSFWorkbook workbook = new XSSFWorkbook(is)) {
                // 2. Récupérer les métadonnées employé
                EmployeMetadata emp = chargerMetadataEmploye(idEmploye, c);
                if (emp == null) {
                    throw new IllegalArgumentException("Employé introuvable avec l'identifiant " + idEmploye);
                }

                // 3. Résoudre la période de référence
                PeriodeReferenceInfo ref = chargerPeriodeReference(idEmploye, idPeriodeReference, c);
                if (ref == null) {
                    throw new IllegalStateException("Aucune période de référence de congé trouvée pour l'employé " + idEmploye);
                }

                // 4. Charger le journal mensuel et les prises pour les 12 mois du cycle
                List<MoisSyntheseData> moisData = charger12MoisReference(idEmploye, ref, c);

                // 5. Charger l'historique complet des mouvements pour l'onglet d'audit
                List<MouvementSoldeConge> mouvements = MouvementSoldeConge.listParEmploye(idEmploye, c);

                // 6. Remplir la Feuille 1 : État congés salarié
                Sheet sheet1 = workbook.getSheetAt(0);
                remplirEnTeteFeuille1(sheet1, emp, ref);
                remplirTableau12Mois(sheet1, moisData, ref);

                // 7. Remplir la Feuille 2 : Mouvements
                Sheet sheet2 = workbook.getSheetAt(1);
                remplirFeuilleMouvements(sheet2, mouvements, ref);

                // 8. Forcer le recalcul des formules natives (=SUM(B8:B19), etc.)
                workbook.setForceFormulaRecalculation(true);

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                workbook.write(bos);
                return bos.toByteArray();
            }
        }
    }

    public String obtenirNomFichierExport(int idEmploye, Integer idPeriodeReference) {
        try (Connection c = dataSource.getConnection()) {
            EmployeMetadata emp = chargerMetadataEmploye(idEmploye, c);
            PeriodeReferenceInfo ref = chargerPeriodeReference(idEmploye, idPeriodeReference, c);
            String matricule = emp != null && emp.matricule != null ? emp.matricule : "EMP" + idEmploye;
            String dDebut = ref != null ? ref.dateDebut.toString() : LocalDate.now().getYear() + "-01-01";
            String dFin = ref != null ? ref.dateFin.toString() : LocalDate.now().getYear() + "-12-31";
            return String.format("ETAT_CONGES_%s_%s_%s.xlsx", matricule.replaceAll("[^a-zA-Z0-9_-]", ""), dDebut, dFin);
        } catch (Exception e) {
            return String.format("ETAT_CONGES_EMP%d.xlsx", idEmploye);
        }
    }

    public int resoudreEmployeDePeriode(int idPeriodeReference) throws SQLException {
        String sql = "SELECT id_employe FROM conge_periode_reference WHERE id_periode_reference = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriodeReference);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id_employe");
                }
            }
        }
        throw new IllegalArgumentException("Période de référence introuvable : " + idPeriodeReference);
    }

    // =========================================================================
    // REMPLISSAGE DES FEUILLES DU CLASSEUR
    // =========================================================================

    private void remplirEnTeteFeuille1(Sheet sheet, EmployeMetadata emp, PeriodeReferenceInfo ref) {
        // Row 3 (index 2) : Informations employé
        setCellString(sheet, 2, 0, "Matricule : " + safe(emp.matricule));
        setCellString(sheet, 2, 2, "Nom & prénom : " + safe(emp.nom) + " " + safe(emp.prenom));
        setCellString(sheet, 2, 5, "Filiale : " + safe(emp.filialeNom));
        setCellString(sheet, 2, 7, "Site : " + safe(emp.siteNom));
        setCellString(sheet, 2, 9, "Poste : " + safe(emp.posteNom));
        setCellString(sheet, 2, 12, "Période de référence");

        // Row 4 (index 3) : Dates période de référence
        setCellString(sheet, 3, 12, "Du " + ref.dateDebut.format(DATE_FMT));
        setCellString(sheet, 3, 14, "Au " + ref.dateFin.format(DATE_FMT));

        // Row 5 (index 4) : Situation d'ouverture & TCONGE
        setCellString(sheet, 4, 0, "Solde ouv. : " + formatJours(ref.soldeOuverture) + " j");
        setCellString(sheet, 4, 2, "Valorisation ouv. : " + formatMoney(ref.valoOuverture));
        setCellString(sheet, 4, 4, "TCONGE ouv. : " + formatMoney(ref.tauxJournalier));
        setCellString(sheet, 4, 6, "Mode reprise : " + safe(ref.modeReprise));
        setCellString(sheet, 4, 9, "Statut : " + safe(ref.statut));
        String tClotureText = (ref.tauxCloture != null && ref.tauxCloture.compareTo(BigDecimal.ZERO) > 0)
                ? formatMoney(ref.tauxCloture)
                : "En cours";
        setCellString(sheet, 4, 12, "TCONGE clôture : " + tClotureText);
    }

    private void remplirTableau12Mois(Sheet sheet, List<MoisSyntheseData> moisData, PeriodeReferenceInfo ref) {
        BigDecimal soldeCourant = ref.soldeOuverture != null ? ref.soldeOuverture : BigDecimal.ZERO;
        BigDecimal tauxJournalierCourant = ref.tauxJournalier != null ? ref.tauxJournalier : BigDecimal.ZERO;

        // Rows 8 à 19 (index 7 à 18) dans le canevas
        for (int i = 0; i < 12; i++) {
            int rowIndex = 7 + i;
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            MoisSyntheseData d = (i < moisData.size()) ? moisData.get(i) : null;
            if (d == null) {
                LocalDate mDate = ref.dateDebut.plusMonths(i);
                setCellString(sheet, rowIndex, 0, String.format("%02d/%d", mDate.getMonthValue(), mDate.getYear()));
                continue;
            }

            // Col A: Mois / période
            setCellString(sheet, rowIndex, 0, d.labelMois);

            // Col B: Salaire de base
            setNumericValue(row, 1, d.salaireBase);

            // Col C: Absence constatée (j)
            setNumericValue(row, 2, d.absencesJours);

            // Col D: Retenue absence (Ar)
            setNumericValue(row, 3, d.retenueAbsence);

            // Col E: Congés pris (j)
            setNumericValue(row, 4, d.congesPrisJours);

            // Taux applicable ce mois-ci
            if (d.tcongeApplicable != null && d.tcongeApplicable.compareTo(BigDecimal.ZERO) > 0) {
                tauxJournalierCourant = d.tcongeApplicable;
            }

            // Col F: TCONGE applicable
            setNumericValue(row, 5, tauxJournalierCourant);

            // Col G: Indemnité congé (Ar)
            BigDecimal indemnite = d.indemniteConge;
            if ((indemnite == null || indemnite.compareTo(BigDecimal.ZERO) == 0) && d.congesPrisJours != null && d.congesPrisJours.compareTo(BigDecimal.ZERO) > 0) {
                indemnite = d.congesPrisJours.multiply(tauxJournalierCourant).setScale(2, RoundingMode.HALF_UP);
            }
            setNumericValue(row, 6, indemnite);

            // Col H: HS rémunérées
            setNumericValue(row, 7, d.hsRemunerees);

            // Col I: Assiette droits congé
            setNumericValue(row, 8, d.assietteDroits);

            // Col J: Base activité (j)
            setNumericValue(row, 9, d.baseActiviteJours);

            // Col K: Droits acquis (j)
            setNumericValue(row, 10, d.droitsAcquisJours);

            // Col L: Valorisation acquise (Ar)
            setNumericValue(row, 11, d.valorisationAcquise);

            // Col M: Cumul droits (j)
            setNumericValue(row, 12, d.cumulDroitsJours);

            // Col N: Cumul valorisation (Ar)
            setNumericValue(row, 13, d.cumulValorisation);

            // Évolution du solde disponible
            if (d.droitsAcquisJours != null) {
                soldeCourant = soldeCourant.add(d.droitsAcquisJours);
            }
            if (d.congesPrisJours != null) {
                soldeCourant = soldeCourant.subtract(d.congesPrisJours);
            }

            // Col O: Solde disponible (j)
            setNumericValue(row, 14, soldeCourant);

            // Col P: Valeur du solde (Ar)
            BigDecimal valSolde = soldeCourant.multiply(tauxJournalierCourant).setScale(2, RoundingMode.HALF_UP);
            setNumericValue(row, 15, valSolde);
        }
    }

    private void remplirFeuilleMouvements(Sheet sheet, List<MouvementSoldeConge> mouvements, PeriodeReferenceInfo ref) {
        BigDecimal runningBalance = ref.soldeOuverture != null ? ref.soldeOuverture : BigDecimal.ZERO;
        BigDecimal taux = ref.tauxJournalier != null ? ref.tauxJournalier : BigDecimal.ZERO;

        int rowIndex = 3; // Ligne 4 Excel

        // 1. Ligne initiale : report d'ouverture de la période si disponible
        if (ref.soldeOuverture != null && ref.soldeOuverture.compareTo(BigDecimal.ZERO) != 0) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);

            setCellString(sheet, rowIndex, 0, ref.dateDebut.format(DATE_FMT));
            setCellString(sheet, rowIndex, 1, "Solde d'ouverture (report)");
            setCellString(sheet, rowIndex, 2, "PERIODE-" + ref.idPeriodeReference);
            setCellString(sheet, rowIndex, 3, "Report des droits acquis de la période précédente / cutover");

            BigDecimal credit = ref.soldeOuverture.compareTo(BigDecimal.ZERO) > 0 ? ref.soldeOuverture : BigDecimal.ZERO;
            BigDecimal debit = ref.soldeOuverture.compareTo(BigDecimal.ZERO) < 0 ? ref.soldeOuverture.abs() : BigDecimal.ZERO;
            setNumericValue(row, 4, credit);
            setNumericValue(row, 5, debit);
            setNumericValue(row, 6, runningBalance);
            setNumericValue(row, 7, taux);
            setNumericValue(row, 8, runningBalance.multiply(taux).setScale(2, RoundingMode.HALF_UP));
            setCellString(sheet, rowIndex, 9, "Système RH (Report)");
            rowIndex++;
        }

        if (mouvements == null || mouvements.isEmpty()) return;

        // Filtrer sur la période de référence et trier chronologiquement ascendant
        List<MouvementSoldeConge> chronologique = new ArrayList<>();
        for (MouvementSoldeConge m : mouvements) {
            LocalDate dMvt = m.getDateMouvement() != null
                    ? m.getDateMouvement().toLocalDate()
                    : (m.getCreatedAt() != null ? m.getCreatedAt().toLocalDateTime().toLocalDate() : ref.dateDebut);
            if (!dMvt.isBefore(ref.dateDebut) && !dMvt.isAfter(ref.dateFin)) {
                chronologique.add(m);
            }
        }

        chronologique.sort((a, b) -> {
            Date da = a.getDateMouvement() != null ? a.getDateMouvement() : new Date(a.getCreatedAt() != null ? a.getCreatedAt().getTime() : 0);
            Date db = b.getDateMouvement() != null ? b.getDateMouvement() : new Date(b.getCreatedAt() != null ? b.getCreatedAt().getTime() : 0);
            int comp = da.compareTo(db);
            if (comp != 0) return comp;
            return Integer.compare(a.getIdMouvement() != null ? a.getIdMouvement() : 0, b.getIdMouvement() != null ? b.getIdMouvement() : 0);
        });

        for (MouvementSoldeConge m : chronologique) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            BigDecimal nbJ = m.getNbJours() != null ? m.getNbJours() : BigDecimal.ZERO;
            runningBalance = runningBalance.add(nbJ);

            LocalDate dMvt = m.getDateMouvement() != null
                    ? m.getDateMouvement().toLocalDate()
                    : (m.getCreatedAt() != null ? m.getCreatedAt().toLocalDateTime().toLocalDate() : ref.dateDebut);
            setCellString(sheet, rowIndex, 0, dMvt.format(DATE_FMT));
            setCellString(sheet, rowIndex, 1, formatTypeMouvement(m.getTypeMouvement(), m.getCommentaire()));
            setCellString(sheet, rowIndex, 2, m.getIdDemandeConge() != null ? "#" + m.getIdDemandeConge() : "—");
            setCellString(sheet, rowIndex, 3, safe(m.getCommentaire()));
            setNumericValue(row, 4, nbJ.compareTo(BigDecimal.ZERO) > 0 ? nbJ : BigDecimal.ZERO);
            setNumericValue(row, 5, nbJ.compareTo(BigDecimal.ZERO) < 0 ? nbJ.abs() : BigDecimal.ZERO);
            setNumericValue(row, 6, runningBalance);
            setNumericValue(row, 7, taux);
            setNumericValue(row, 8, runningBalance.multiply(taux).setScale(2, RoundingMode.HALF_UP));
            setCellString(sheet, rowIndex, 9, m.getCreatedBy() != null ? "Manuel (Admin #" + m.getCreatedBy() + ")" : "Automatique Système");
            rowIndex++;
        }
    }

    // =========================================================================
    // CHARGEMENT DES DONNÉES DEPUIS POSTGRESQL
    // =========================================================================

    private EmployeMetadata chargerMetadataEmploye(int idEmploye, Connection c) throws SQLException {
        String sql = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, " +
                "COALESCE(f.raison_sociale, f_site.raison_sociale, 'GALAXAM LOGISTICS') AS filiale_nom, " +
                "COALESCE(s.libelle, 'Siège Antananarivo') AS site_nom, " +
                "COALESCE(fonc.libelle, 'Collaborateur') AS intitule_poste " +
                "FROM employe e " +
                "LEFT JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                "LEFT JOIN contrat_travail ct ON a.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_filiale f ON ct.id_filiale = f.id_filiale " +
                "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "LEFT JOIN ref_filiale f_site ON s.id_filiale_rattachement = f_site.id_filiale " +
                "LEFT JOIN ref_fonction fonc ON a.id_fonction = fonc.id_fonction " +
                "WHERE e.id_employe = ? " +
                "ORDER BY a.date_debut DESC NULLS LAST LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EmployeMetadata(
                            rs.getInt("id_employe"),
                            rs.getString("matricule"),
                            rs.getString("nom"),
                            rs.getString("prenom"),
                            rs.getString("filiale_nom"),
                            rs.getString("site_nom"),
                            rs.getString("intitule_poste")
                    );
                }
            }
        }
        return null;
    }

    private PeriodeReferenceInfo chargerPeriodeReference(int idEmploye, Integer idPeriodeReference, Connection c) throws SQLException {
        String sql;
        if (idPeriodeReference != null) {
            sql = "SELECT id_periode_reference, id_employe, date_debut, date_fin, statut, " +
                    "taux_calcule_cloture, cumul_droits_cloture, cumul_valorisation_cloture, " +
                    "mode_reprise, date_cutover, cumul_droits_ouverture, cumul_valorisation_ouverture " +
                    "FROM conge_periode_reference WHERE id_periode_reference = ?";
        } else {
            sql = "SELECT id_periode_reference, id_employe, date_debut, date_fin, statut, " +
                    "taux_calcule_cloture, cumul_droits_cloture, cumul_valorisation_cloture, " +
                    "mode_reprise, date_cutover, cumul_droits_ouverture, cumul_valorisation_ouverture " +
                    "FROM conge_periode_reference WHERE id_employe = ? " +
                    "ORDER BY (CASE WHEN statut = 'OUVERTE' THEN 0 ELSE 1 END), date_debut DESC LIMIT 1";
        }

        PeriodeReferenceInfo info = null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idPeriodeReference != null) {
                ps.setInt(1, idPeriodeReference);
            } else {
                ps.setInt(1, idEmploye);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info = new PeriodeReferenceInfo();
                    info.idPeriodeReference = rs.getInt("id_periode_reference");
                    info.dateDebut = rs.getDate("date_debut").toLocalDate();
                    info.dateFin = rs.getDate("date_fin").toLocalDate();
                    info.statut = rs.getString("statut");
                    info.tauxCloture = rs.getBigDecimal("taux_calcule_cloture");
                    info.modeReprise = rs.getString("mode_reprise");
                    info.soldeOuverture = rs.getBigDecimal("cumul_droits_ouverture");
                    info.valoOuverture = rs.getBigDecimal("cumul_valorisation_ouverture");
                }
            }
        }

        if (info == null) {
            return null;
        }

        // Taux applicable actif ou d'ouverture
        String sqlTaux = "SELECT taux_journalier FROM conge_taux_applicable " +
                "WHERE id_employe = ? AND date_effet <= ? AND (date_fin IS NULL OR date_fin >= ?) " +
                "ORDER BY date_effet DESC LIMIT 1";
        try (PreparedStatement psTaux = c.prepareStatement(sqlTaux)) {
            psTaux.setInt(1, idEmploye);
            psTaux.setDate(2, Date.valueOf(info.dateDebut));
            psTaux.setDate(3, Date.valueOf(info.dateDebut));
            try (ResultSet rsT = psTaux.executeQuery()) {
                if (rsT.next()) {
                    info.tauxJournalier = rsT.getBigDecimal("taux_journalier");
                }
            }
        }
        if (info.tauxJournalier == null) {
            // Fallback salaire base / 30 depuis l'affectation courante
            String sqlSal = "SELECT salaire_base FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY date_debut DESC LIMIT 1";
            try (PreparedStatement psS = c.prepareStatement(sqlSal)) {
                psS.setInt(1, idEmploye);
                try (ResultSet rsS = psS.executeQuery()) {
                    if (rsS.next()) {
                        BigDecimal sal = rsS.getBigDecimal("salaire_base");
                        if (sal != null && sal.compareTo(BigDecimal.ZERO) > 0) {
                            info.tauxJournalier = sal.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
                        }
                    }
                }
            }
        }
        if (info.tauxJournalier == null) {
            info.tauxJournalier = BigDecimal.ZERO;
        }

        return info;
    }

    private List<MoisSyntheseData> charger12MoisReference(int idEmploye, PeriodeReferenceInfo ref, Connection c) throws SQLException {
        List<MoisSyntheseData> liste = new ArrayList<>();

        // 1. Charger tout conge_journal_droit pour ce cycle
        String sqlJournal = "SELECT numero_mois_reference, annee, mois, salaire_base, jours_travailles_formule, " +
                "jours_droit_acquis, base_valorisation, valorisation_mensuelle, " +
                "cumul_jours_droit, cumul_valorisation, taux_journalier_conge, id_periode_paie " +
                "FROM conge_journal_droit " +
                "WHERE id_employe = ? AND id_periode_reference = ? " +
                "ORDER BY numero_mois_reference ASC";
        Map<Integer, Map<String, Object>> mapJournal = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sqlJournal)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, ref.idPeriodeReference);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int num = rs.getInt("numero_mois_reference");
                    Map<String, Object> map = new HashMap<>();
                    map.put("salaire_base", rs.getBigDecimal("salaire_base"));
                    map.put("jours_travailles_formule", rs.getBigDecimal("jours_travailles_formule"));
                    map.put("jours_droit_acquis", rs.getBigDecimal("jours_droit_acquis"));
                    map.put("base_valorisation", rs.getBigDecimal("base_valorisation"));
                    map.put("valorisation_mensuelle", rs.getBigDecimal("valorisation_mensuelle"));
                    map.put("cumul_jours_droit", rs.getBigDecimal("cumul_jours_droit"));
                    map.put("cumul_valorisation", rs.getBigDecimal("cumul_valorisation"));
                    map.put("taux_journalier_conge", rs.getBigDecimal("taux_journalier_conge"));
                    map.put("id_periode_paie", rs.getObject("id_periode_paie"));
                    mapJournal.put(num, map);
                }
            }
        }

        // 2. Dérouler les 12 mois légaux de la période
        for (int i = 0; i < 12; i++) {
            int numMoisRef = i + 1;
            LocalDate dateMois = ref.dateDebut.plusMonths(i);
            int annee = dateMois.getYear();
            int mois = dateMois.getMonthValue();
            String labelMois = String.format("%02d/%d", mois, annee);

            MoisSyntheseData d = new MoisSyntheseData();
            d.numeroMoisRef = numMoisRef;
            d.labelMois = labelMois;

            Map<String, Object> jEntry = mapJournal.get(numMoisRef);
            if (jEntry != null) {
                d.salaireBase = (BigDecimal) jEntry.get("salaire_base");
                d.baseActiviteJours = (BigDecimal) jEntry.get("jours_travailles_formule");
                d.droitsAcquisJours = (BigDecimal) jEntry.get("jours_droit_acquis");
                d.assietteDroits = (BigDecimal) jEntry.get("base_valorisation");
                d.valorisationAcquise = (BigDecimal) jEntry.get("valorisation_mensuelle");
                d.cumulDroitsJours = (BigDecimal) jEntry.get("cumul_jours_droit");
                d.cumulValorisation = (BigDecimal) jEntry.get("cumul_valorisation");
                d.tcongeApplicable = (BigDecimal) jEntry.get("taux_journalier_conge");
            }

            // Prises de congés enregistrées dans mouvement_solde_conge pour ce mois
            LocalDate debutM = LocalDate.of(annee, mois, 1);
            LocalDate finM = debutM.plusMonths(1).minusDays(1);
            String sqlPrises = "SELECT COALESCE(SUM(ABS(nb_jours)), 0) FROM mouvement_solde_conge " +
                    "WHERE id_employe = ? AND type_mouvement = 'PRISE' " +
                    "  AND date_mouvement >= ? AND date_mouvement <= ?";
            try (PreparedStatement psP = c.prepareStatement(sqlPrises)) {
                psP.setInt(1, idEmploye);
                psP.setDate(2, Date.valueOf(debutM));
                psP.setDate(3, Date.valueOf(finM));
                try (ResultSet rsP = psP.executeQuery()) {
                    if (rsP.next()) {
                        d.congesPrisJours = rsP.getBigDecimal(1);
                    }
                }
            }

            // Bulletins de paie du mois pour absences et HS
            String sqlBp = "SELECT bp.jours_absence, " +
                    "(SELECT COALESCE(SUM(bpl.montant_salarial), 0) FROM bulletin_paie_ligne bpl WHERE bpl.id_bulletin = bp.id_bulletin AND bpl.code_rubrique = 'RET_ABS') AS ret_abs, " +
                    "(SELECT COALESCE(SUM(bpl.montant_salarial), 0) FROM bulletin_paie_ligne bpl WHERE bpl.id_bulletin = bp.id_bulletin AND bpl.code_rubrique = 'CONGE_PAYE') AS ind_conge, " +
                    "(SELECT COALESCE(SUM(bpl.montant_salarial), 0) FROM bulletin_paie_ligne bpl WHERE bpl.id_bulletin = bp.id_bulletin AND bpl.code_rubrique LIKE 'HS_%') AS hs_montant " +
                    "FROM bulletin_paie bp " +
                    "JOIN periode_paie pp ON bp.id_periode_paie = pp.id_periode_paie " +
                    "WHERE bp.id_employe = ? AND EXTRACT(YEAR FROM pp.date_debut) = ? AND EXTRACT(MONTH FROM pp.date_debut) = ? " +
                    "  AND bp.statut IN ('VALIDE', 'CLOTURE', 'CALCULE') " +
                    "ORDER BY bp.id_bulletin DESC LIMIT 1";
            try (PreparedStatement psBp = c.prepareStatement(sqlBp)) {
                psBp.setInt(1, idEmploye);
                psBp.setInt(2, annee);
                psBp.setInt(3, mois);
                try (ResultSet rsBp = psBp.executeQuery()) {
                    if (rsBp.next()) {
                        d.absencesJours = rsBp.getBigDecimal("jours_absence");
                        d.retenueAbsence = rsBp.getBigDecimal("ret_abs");
                        BigDecimal indC = rsBp.getBigDecimal("ind_conge");
                        if (indC != null && indC.compareTo(BigDecimal.ZERO) > 0) {
                            d.indemniteConge = indC;
                        }
                        d.hsRemunerees = rsBp.getBigDecimal("hs_montant");
                    }
                }
            }

            liste.add(d);
        }

        return liste;
    }

    // =========================================================================
    // UTILITAIRES POI & FORMATTAGE
    // =========================================================================

    private void setCellString(Sheet sheet, int rowIndex, int colIndex, String text) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        cell.setCellValue(text != null ? text : "");
    }

    private void setNumericValue(Row row, int colIndex, BigDecimal value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue(0.0);
        }
    }

    private String safe(String s) {
        return s != null ? s.trim() : "—";
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0 Ar";
        return String.format("%,.2f Ar", amount.doubleValue());
    }

    private String formatJours(BigDecimal j) {
        if (j == null) return "0.00";
        return String.format("%.2f", j.doubleValue());
    }

    private String formatTypeMouvement(String type, String commentaire) {
        if ("AJUSTEMENT".equals(type) && commentaire != null && commentaire.startsWith("[SOLDE_INITIAL]")) {
            return "Solde initial (reprise)";
        }
        if (type == null) return "—";
        return switch (type) {
            case "ACQUISITION" -> "Acquisition mensuelle";
            case "BONIFICATION" -> "Bonification ancienneté";
            case "PRISE" -> "Prise de congé";
            case "AJUSTEMENT" -> "Ajustement manuel RH";
            case "ANNULATION" -> "Annulation demande";
            case "EXPIRATION" -> "Expiration triennale";
            default -> type;
        };
    }

    // Classes internes DTO locales
    public static class EmployeMetadata {
        final int idEmploye;
        final String matricule;
        final String nom;
        final String prenom;
        final String filialeNom;
        final String siteNom;
        final String posteNom;

        public EmployeMetadata(int idEmploye, String matricule, String nom, String prenom, String filialeNom, String siteNom, String posteNom) {
            this.idEmploye = idEmploye;
            this.matricule = matricule;
            this.nom = nom;
            this.prenom = prenom;
            this.filialeNom = filialeNom;
            this.siteNom = siteNom;
            this.posteNom = posteNom;
        }
    }

    public static class PeriodeReferenceInfo {
        int idPeriodeReference;
        LocalDate dateDebut;
        LocalDate dateFin;
        String statut;
        BigDecimal tauxCloture;
        String modeReprise;
        BigDecimal soldeOuverture;
        BigDecimal valoOuverture;
        BigDecimal tauxJournalier;
    }

    public static class MoisSyntheseData {
        int numeroMoisRef;
        String labelMois;
        BigDecimal salaireBase;
        BigDecimal absencesJours;
        BigDecimal retenueAbsence;
        BigDecimal congesPrisJours;
        BigDecimal tcongeApplicable;
        BigDecimal indemniteConge;
        BigDecimal hsRemunerees;
        BigDecimal assietteDroits;
        BigDecimal baseActiviteJours;
        BigDecimal droitsAcquisJours;
        BigDecimal valorisationAcquise;
        BigDecimal cumulDroitsJours;
        BigDecimal cumulValorisation;
    }
}
