package mg.galaxam.sirh.conge.application;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
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
import java.util.NavigableMap;
import java.util.TreeMap;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.persistence.MouvementSoldeConge;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Export Excel de l'état individuel des congés.
 * Remplit le canevas interne à partir des écritures du SIRH et de la période de référence du salarié.
 */
@Service
public class CongeAnnuelExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private record MouvementWindow(int moisReference, LocalDate debut, LocalDate situation) {}
    private record MouvementTotals(BigDecimal solde, BigDecimal prises) {}
    private record TauxInterval(LocalDate debut, LocalDate fin, BigDecimal taux) {}
    private record BulletinMois(BigDecimal absences, BigDecimal retenueAbsence,
                               BigDecimal indemniteConge, BigDecimal heuresSupplementaires) {}

    @Autowired
    private DataSource dataSource;
    @Autowired
    private CongeLotValorisationService lots;

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
                // Résoudre d'abord la période pour figer les métadonnées à sa date de situation.
                PeriodeReferenceInfo ref = chargerPeriodeReference(idEmploye, idPeriodeReference, c);
                if (ref == null) {
                    throw new IllegalStateException("Aucune période de référence de congé trouvée pour l'employé " + idEmploye);
                }
                EmployeMetadata emp = chargerMetadataEmploye(idEmploye, ref.dateFin.isAfter(LocalDate.now())
                        ? LocalDate.now() : ref.dateFin, c);
                if (emp == null) {
                    throw new IllegalArgumentException("Employé introuvable avec l'identifiant " + idEmploye);
                }

                // 4. Charger le journal mensuel et les prises pour les 12 mois du cycle
                List<MoisSyntheseData> moisData = charger12MoisReference(idEmploye, ref, c);
                Map<LocalDate, BigDecimal> valeursStockParDate = new HashMap<>();

                // 5. Charger l'historique complet des mouvements pour l'onglet d'audit
                List<MouvementSoldeConge> mouvements = MouvementSoldeConge.listParEmploye(idEmploye, c);
                List<LocalDate> datesTauxHistorique = new ArrayList<>();
                datesTauxHistorique.add(ref.dateDebut);
                for (MouvementSoldeConge mouvement : mouvements) {
                    LocalDate dateMouvement = mouvement.getDateMouvement() != null
                            ? mouvement.getDateMouvement().toLocalDate()
                            : (mouvement.getCreatedAt() != null
                                    ? mouvement.getCreatedAt().toLocalDateTime().toLocalDate() : ref.dateDebut);
                    if (!dateMouvement.isBefore(ref.dateDebut) && !dateMouvement.isAfter(ref.dateFin)) {
                        datesTauxHistorique.add(dateMouvement);
                    }
                }
                Map<LocalDate, BigDecimal> tauxParDate = chargerTauxPourDates(idEmploye, datesTauxHistorique, c);
                ref.tauxJournalier = tauxParDate.get(ref.dateDebut);

                // 6. Remplir la Feuille 1 : État congés salarié
                Sheet sheet1 = workbook.getSheetAt(0);
                remplirEnTeteFeuille1(sheet1, emp, ref);
                remplirTableau12Mois(sheet1, moisData, ref, idEmploye, c, valeursStockParDate);
                sheet1.setColumnWidth(0, 30 * 256);
                sheet1.setColumnWidth(2, 14 * 256); // col C : jours base calcul (formule)
                sheet1.setColumnWidth(17, 18 * 256); // col R : solde × TCONGE estimation
                // Note de bas de page (ligne 20, index 19)
                org.apache.poi.ss.usermodel.Row noteRow = sheet1.getRow(19);
                if (noteRow == null) noteRow = sheet1.createRow(19);
                org.apache.poi.ss.usermodel.Cell noteCell = noteRow.getCell(0);
                if (noteCell == null) noteCell = noteRow.createCell(0);
                noteCell.setCellValue("Données enregistrées dans le SIRH à la date de situation. "
                        + "Col C (verte) = jours base calcul = 'formule' de l'OHATRA DAF. "
                        + "Col R (verte) = Solde × TCONGE (estimation simple pour cross-check DAF). "
                        + "Col Q = valeur FIFO historisée (plus précise). Taux indisponibles = vide.");

                // 7. Remplir la Feuille 2 : Mouvements
                Sheet sheet2 = workbook.getSheetAt(1);
                remplirFeuilleMouvements(sheet2, mouvements, ref, idEmploye, c,
                        valeursStockParDate, tauxParDate);
                setCellString(sheet2, 2, 8, "Valeur du stock après mvt (Ar)");
                Sheet detailLots = workbook.createSheet("Origine des droits");
                var situationLots = lots.situation(idEmploye, ref.dateFin.isAfter(LocalDate.now())
                        ? LocalDate.now() : ref.dateFin, c);
                Row titresLots = detailLots.createRow(0);
                String[] colonnesLots = {"Origine", "ID droit", "Date acquisition", "Jours restants",
                        "Valeur restante", "ID prise", "Jours consommés", "Valeur consommée", "TCONGE historique"};
                for (int i = 0; i < colonnesLots.length; i++) titresLots.createCell(i).setCellValue(colonnesLots[i]);
                int rangLot = 1;
                for (var lot : situationLots.lots()) {
                    Row r = detailLots.createRow(rangLot++);
                    r.createCell(0).setCellValue(lot.origine()); r.createCell(1).setCellValue(lot.idSource());
                    r.createCell(2).setCellValue(lot.dateAcquisition().toString());
                    r.createCell(3).setCellValue(lot.jours().doubleValue());
                    r.createCell(4).setCellValue(lot.valeur().doubleValue());
                }
                for (var prise : situationLots.imputations()) {
                    Row r = detailLots.createRow(rangLot++);
                    r.createCell(0).setCellValue(prise.origine()); r.createCell(1).setCellValue(prise.idSource());
                    if (prise.idDemande() != null) r.createCell(5).setCellValue(prise.idDemande());
                    r.createCell(6).setCellValue(prise.jours().doubleValue());
                    r.createCell(7).setCellValue(prise.valeur().doubleValue());
                    Cell tauxCell = r.createCell(8);
                    tauxCell.setCellValue(prise.tauxHistorique().doubleValue());
                    tauxCell.setCellStyle(tauxStyle(workbook, null));
                }
                Sheet notice = workbook.getSheetAt(2);
                setCellString(notice, 7, 1,
                        "Douze périodes du cycle individuel ; les dates des campagnes de paie sont utilisées lorsqu'elles sont disponibles.");
                setCellString(notice, 8, 1,
                        "Le cumul valorisé des droits TCONGE et la valeur du solde disponible sont deux indicateurs distincts.");
                setCellString(notice, 9, 1,
                        "Les mouvements expliquent les crédits et débits ; la valeur du stock provient de la valorisation FIFO des lots de droits, pas de solde × TCONGE.");

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
            PeriodeReferenceInfo ref = chargerPeriodeReference(idEmploye, idPeriodeReference, c);
            EmployeMetadata emp = chargerMetadataEmploye(idEmploye,
                    ref != null && ref.dateFin.isBefore(LocalDate.now()) ? ref.dateFin : LocalDate.now(), c);
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
        // Ligne 2 (index 1) : En-tête titre déjà dans le canevas
        // Ligne 3 (index 2) : Informations employé
        setCellString(sheet, 2, 0, "Matricule : " + safe(emp.matricule));
        setCellString(sheet, 2, 2, "Nom & prénom : " + safe(emp.nom) + " " + safe(emp.prenom));
        setCellString(sheet, 2, 5, "Filiale : " + safe(emp.filialeNom));
        setCellString(sheet, 2, 7, "Site : " + safe(emp.siteNom));
        setCellString(sheet, 2, 9, "Poste : " + safe(emp.posteNom));
        setCellString(sheet, 2, 12, "Période de référence");

        // Ligne 4 (index 3) : Dates période de référence
        setCellString(sheet, 3, 12, "Du " + ref.dateDebut.format(DATE_FMT));
        setCellString(sheet, 3, 14, "Au " + ref.dateFin.format(DATE_FMT));

        // Ligne 5 (index 4) : Situation d'ouverture & TCONGE
        setCellString(sheet, 4, 0, "Solde ouv. : " + formatJours(ref.soldeOuverture) + " j");
        setCellString(sheet, 4, 2, "Valorisation droits ouverture : " + formatMoney(ref.valoOuverture));
        setCellString(sheet, 4, 4, "TCONGE ouv. : " + formatTaux(ref.tauxJournalier));
        setCellString(sheet, 4, 6, "Mode reprise : " + safe(ref.modeReprise));
        setCellString(sheet, 4, 9, "Statut : " + safe(ref.statut));
        String tClotureText = (ref.tauxCloture != null && ref.tauxCloture.compareTo(BigDecimal.ZERO) > 0)
                ? formatTaux(ref.tauxCloture)
                : "En cours";
        setCellString(sheet, 4, 12, "TCONGE clôture : " + tClotureText);

        // Ligne 6 (index 5) : Numéros de mois (DAF) + rappel TCONGE clôture en fin de ligne
        setCellString(sheet, 5, 0, "N° mois");
        setCellString(sheet, 5, 17, "TCONGE clôture → " + tClotureText);
    }

    /**
     * Remplit les 12 lignes mensuelles du tableau principal.
     * Correspondance colonnes (index 0-based) avec l'OHATRA DAF :
     *   A(0)  = Mois/période        —  DAF: MOIS
     *   B(1)  = Salaire de base     —  DAF: BASE
     *   C(2)  = Jours base calcul   —  DAF: formule  ← REC #1
     *   D(3)  = Absences (j)        —  DAF: NBABS
     *   E(4)  = Retenue absence     —  DAF: ABSENCES (montant)
     *   F(5)  = Congés pris (j)     —  DAF: NBCONGE
     *   G(6)  = TCONGE applicable   —  DAF: TCONGE
     *   H(7)  = Indemnité congé     —  DAF: CONGE
     *   I(8)  = HS rémunérées       —  DAF: HS
     *   J(9)  = Base valo (sal+HS)  —  DAF: base totale  ← REC #2
     *   K(10) = Base activité (j)
     *   L(11) = Droits acquis (j)   —  DAF: nbre droit congés
     *   M(12) = Valorisation acquise—  DAF: R*S/30
     *   N(13) = Cumul droits (j)    —  DAF: cumul NB JR DROIT
     *   O(14) = Cumul valorisation
     *   P(15) = Solde disponible (j)
     *   Q(16) = Valeur stock FIFO
     *   R(17) = Solde × TCONGE (estimation simple DAF)  ← REC #4
     */
    private void remplirTableau12Mois(Sheet sheet, List<MoisSyntheseData> moisData,
            PeriodeReferenceInfo ref, int idEmploye, Connection c,
            Map<LocalDate, BigDecimal> valeursStockParDate) throws SQLException {
        // Rows 7 à 18 (index 6 à 17) dans le nouveau canevas à 18 colonnes
        for (int i = 0; i < 12; i++) {
            int rowIndex = 6 + i;   // ligne 7 Excel = index 6
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            MoisSyntheseData d = (i < moisData.size()) ? moisData.get(i) : null;
            if (d == null) {
                LocalDate mDate = ref.dateDebut.plusMonths(i);
                // REC #6 : label court numéro mois
                setCellString(sheet, rowIndex, 0,
                        String.format("%d — %02d/%d", i + 1, mDate.getMonthValue(), mDate.getYear()));
                continue;
            }

            // REC #6 : Col A — numéro de mois + label court
            setCellString(sheet, rowIndex, 0, d.labelMois);

            // Col B(1) : Salaire de base
            setNumericValue(row, 1, d.salaireBase);

            // REC #1 — Col C(2) : Jours base calcul (= « formule » de l'OHATRA DAF)
            setNumericValue(row, 2, d.baseActiviteJours);

            // Col D(3) : Absences constatées (j)
            setNumericValue(row, 3, d.absencesJours);

            // Col E(4) : Retenue absence (Ar)
            setNumericValue(row, 4, d.retenueAbsence);

            // Col F(5) : Congés pris (j)
            setNumericValue(row, 5, d.congesPrisJours);

            // Col G(6) : TCONGE applicable
            setNumericValue(row, 6, d.tcongeApplicable);

            // Col H(7) : Indemnité congé — montant porté par le bulletin
            setNumericValue(row, 7, d.indemniteConge);

            // Col I(8) : HS rémunérées
            setNumericValue(row, 8, d.hsRemunerees);

            // REC #2 — Col J(9) : Base valorisation (sal+HS) = « base totale » DAF
            setNumericValue(row, 9, d.assietteDroits);

            // Col K(10) : Base activité (j) — déjà affiché en C ; ici c'est le nb jours paie
            // (on peut laisser vide ou afficher les jours ouvrables de la période paie)
            // => on laisse pour éviter la redondance avec col C

            // Col L(11) : Droits acquis (j)  — DAF: nbre droit congés
            setNumericValue(row, 11, d.droitsAcquisJours);

            // Col M(12) : Valorisation acquise (Ar)  — DAF: R*S/30
            setNumericValue(row, 12, d.valorisationAcquise);

            // Col N(13) : Cumul droits (j)  — DAF: cumul NB JR DROIT
            setNumericValue(row, 13, d.cumulDroitsJours);

            // Col O(14) : Cumul valorisation (Ar)
            setNumericValue(row, 14, d.cumulValorisation);

            // Col P(15) : Solde disponible (j) — inclut ajustements, annulations, expirations
            setNumericValue(row, 15, d.soldeDisponible);

            // Col Q(16) : Valeur du stock FIFO (historisée par lots)
            BigDecimal valSolde = d.dateSituation == null ? null
                    : obtenirValeurStock(idEmploye, d.dateSituation, c, valeursStockParDate);
            setNumericValue(row, 16, valSolde);

            // REC #4 — Col R(17) : Solde × TCONGE estimation simple (cross-check DAF)
            if (d.soldeDisponible != null && d.tcongeApplicable != null
                    && d.tcongeApplicable.compareTo(BigDecimal.ZERO) > 0) {
                setNumericValue(row, 17, d.soldeDisponible.multiply(d.tcongeApplicable));
            }
        }

        // ── Ligne TOTAL (index 18 = ligne 19 Excel) ──────────────────────────
        Row total = sheet.getRow(18);
        if (total == null) total = sheet.createRow(18);
        total.getCell(0).setCellValue("TOTAL ENREGISTRÉ / SITUATION");

        MoisSyntheseData latest = null;
        MoisSyntheseData latestJournal = null;
        for (MoisSyntheseData month : moisData) {
            if (month.soldeDisponible != null) latest = month;
            if (month.cumulDroitsJours != null) latestJournal = month;
        }

        // REC #3 : TCONGE clôture affiché en ligne TOTAL (col G)
        BigDecimal tclotureTotal = (ref.tauxCloture != null && ref.tauxCloture.compareTo(BigDecimal.ZERO) > 0)
                ? ref.tauxCloture
                : (latest != null ? latest.tcongeApplicable : null);
        setNumericValue(total, 6, tclotureTotal);

        setNumericValue(total, 13, latestJournal != null ? latestJournal.cumulDroitsJours : null);
        setNumericValue(total, 14, latestJournal != null ? latestJournal.cumulValorisation : null);
        setNumericValue(total, 15, latest != null ? latest.soldeDisponible : ref.soldeOuverture);

        LocalDate dateFinale = ref.dateFin.isAfter(LocalDate.now()) ? LocalDate.now() : ref.dateFin;
        BigDecimal valStockFinal = obtenirValeurStock(idEmploye, dateFinale, c, valeursStockParDate);
        setNumericValue(total, 16, valStockFinal);

        // REC #4 : Solde × TCONGE clôture en col R pour la ligne TOTAL
        BigDecimal soldeFinal = latest != null ? latest.soldeDisponible : ref.soldeOuverture;
        if (soldeFinal != null && tclotureTotal != null && tclotureTotal.compareTo(BigDecimal.ZERO) > 0) {
            setNumericValue(total, 17, soldeFinal.multiply(tclotureTotal));
        }
    }

    private void remplirFeuilleMouvements(Sheet sheet, List<MouvementSoldeConge> mouvements,
                                          PeriodeReferenceInfo ref, int idEmploye, Connection c,
                                          Map<LocalDate, BigDecimal> valeursStockParDate,
                                          Map<LocalDate, BigDecimal> tauxParDate) throws SQLException {
        BigDecimal runningBalance = ref.soldeOuverture != null ? ref.soldeOuverture : BigDecimal.ZERO;

        int rowIndex = 3; // Ligne 4 Excel

        // 1. Ligne initiale : report d'ouverture de la période si disponible
        if (ref.soldeOuverture != null && ref.soldeOuverture.compareTo(BigDecimal.ZERO) != 0) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = createStyledMovementRow(sheet, rowIndex);

            setCellString(sheet, rowIndex, 0, ref.dateDebut.format(DATE_FMT));
            setCellString(sheet, rowIndex, 1, "Solde d'ouverture (report)");
            setCellString(sheet, rowIndex, 2, "PERIODE-" + ref.idPeriodeReference);
            setCellString(sheet, rowIndex, 3, "Report des droits acquis de la période précédente / cutover");

            BigDecimal credit = ref.soldeOuverture.compareTo(BigDecimal.ZERO) > 0 ? ref.soldeOuverture : BigDecimal.ZERO;
            BigDecimal debit = ref.soldeOuverture.compareTo(BigDecimal.ZERO) < 0 ? ref.soldeOuverture.abs() : BigDecimal.ZERO;
            setNumericValue(row, 4, credit);
            setNumericValue(row, 5, debit);
            setNumericValue(row, 6, runningBalance);
            setTauxValue(row, 7, ref.tauxJournalier);
            setNumericValue(row, 8, obtenirValeurStock(idEmploye, ref.dateDebut, c, valeursStockParDate));
            setCellString(sheet, rowIndex, 9, "Système RH (Report)");
            rowIndex++;
        }

        if (mouvements == null || mouvements.isEmpty()) {
            sheet.createFreezePane(0, 3);
            return;
        }

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

        List<LocalDate> datesMouvements = chronologique.stream()
                .map(m -> m.getDateMouvement() != null
                        ? m.getDateMouvement().toLocalDate()
                        : (m.getCreatedAt() != null ? m.getCreatedAt().toLocalDateTime().toLocalDate() : ref.dateDebut))
                .toList();
        for (int mouvementIndex = 0; mouvementIndex < chronologique.size(); mouvementIndex++) {
            MouvementSoldeConge m = chronologique.get(mouvementIndex);
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = createStyledMovementRow(sheet, rowIndex);
            }

            BigDecimal nbJ = m.getNbJours() != null ? m.getNbJours() : BigDecimal.ZERO;
            runningBalance = runningBalance.add(nbJ);

            LocalDate dMvt = datesMouvements.get(mouvementIndex);
            setCellString(sheet, rowIndex, 0, dMvt.format(DATE_FMT));
            setCellString(sheet, rowIndex, 1, formatTypeMouvement(m.getTypeMouvement(), m.getCommentaire()));
            setCellString(sheet, rowIndex, 2, m.getIdDemandeConge() != null ? "#" + m.getIdDemandeConge() : "—");
            setCellString(sheet, rowIndex, 3, safe(m.getCommentaire()));
            setNumericValue(row, 4, nbJ.compareTo(BigDecimal.ZERO) > 0 ? nbJ : BigDecimal.ZERO);
            setNumericValue(row, 5, nbJ.compareTo(BigDecimal.ZERO) < 0 ? nbJ.abs() : BigDecimal.ZERO);
            setNumericValue(row, 6, runningBalance);
            BigDecimal tauxMouvement = tauxParDate.get(dMvt);
            setTauxValue(row, 7, tauxMouvement);
            setNumericValue(row, 8, obtenirValeurStock(idEmploye, dMvt, c, valeursStockParDate));
            setCellString(sheet, rowIndex, 9, m.getCreatedBy() != null ? "Manuel (Admin #" + m.getCreatedBy() + ")" : "Automatique Système");
            rowIndex++;
        }
        sheet.createFreezePane(0, 3);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(2, Math.max(2, rowIndex - 1), 0, 9));
    }

    private Row createStyledMovementRow(Sheet sheet, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        Row prototype = sheet.getRow(3);
        if (prototype != null && prototype != row) {
            for (int column = 0; column < 10; column++) {
                Cell source = prototype.getCell(column);
                if (source != null) row.createCell(column).setCellStyle(source.getCellStyle());
            }
        }
        return row;
    }

    // =========================================================================
    // CHARGEMENT DES DONNÉES DEPUIS POSTGRESQL
    // =========================================================================

    private EmployeMetadata chargerMetadataEmploye(int idEmploye, LocalDate dateSituation, Connection c) throws SQLException {
        String sql = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, " +
                "COALESCE(f.raison_sociale, f_site.raison_sociale) AS filiale_nom, " +
                "s.libelle AS site_nom, fonc.libelle AS intitule_poste " +
                "FROM employe e " +
                "LEFT JOIN LATERAL (SELECT * FROM employe_affectation_carriere a0 " +
                "  WHERE a0.id_employe = e.id_employe AND a0.date_debut <= ? " +
                "  ORDER BY a0.date_debut DESC, a0.id_affectation DESC LIMIT 1) a ON true " +
                "LEFT JOIN contrat_travail ct ON a.id_contrat = ct.id_contrat " +
                "LEFT JOIN ref_filiale f ON ct.id_filiale = f.id_filiale " +
                "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "LEFT JOIN ref_filiale f_site ON s.id_filiale_rattachement = f_site.id_filiale " +
                "LEFT JOIN ref_fonction fonc ON a.id_fonction = fonc.id_fonction " +
                "WHERE e.id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(dateSituation));
            ps.setInt(2, idEmploye);
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
            sql = "SELECT p.id_periode_reference, p.id_employe, p.date_debut, p.date_fin, p.statut, " +
                    "p.taux_calcule_cloture, p.cumul_droits_cloture, p.cumul_valorisation_cloture, " +
                    "p.mode_reprise, p.date_cutover, p.cumul_droits_ouverture, p.cumul_valorisation_ouverture, " +
                    "COALESCE((SELECT SUM(m.nb_jours) FROM mouvement_solde_conge m " +
                    "WHERE m.id_employe=p.id_employe AND m.date_mouvement<p.date_debut),0) AS solde_ouverture " +
                    "FROM conge_periode_reference p WHERE p.id_periode_reference = ? AND p.id_employe = ?";
        } else {
            sql = "SELECT p.id_periode_reference, p.id_employe, p.date_debut, p.date_fin, p.statut, " +
                    "p.taux_calcule_cloture, p.cumul_droits_cloture, p.cumul_valorisation_cloture, " +
                    "p.mode_reprise, p.date_cutover, p.cumul_droits_ouverture, p.cumul_valorisation_ouverture, " +
                    "COALESCE((SELECT SUM(m.nb_jours) FROM mouvement_solde_conge m " +
                    "WHERE m.id_employe=p.id_employe AND m.date_mouvement<p.date_debut),0) AS solde_ouverture " +
                    "FROM conge_periode_reference p WHERE p.id_employe = ? " +
                    "ORDER BY (CASE WHEN p.statut = 'OUVERTE' THEN 0 ELSE 1 END), p.date_debut DESC LIMIT 1";
        }

        PeriodeReferenceInfo info = null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idPeriodeReference != null) {
                ps.setInt(1, idPeriodeReference);
                ps.setInt(2, idEmploye);
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
                    info.soldeOuverture = rs.getBigDecimal("solde_ouverture");
                    info.tauxCloture = rs.getBigDecimal("taux_calcule_cloture");
                    info.modeReprise = rs.getString("mode_reprise");
                    // cumul_droits_ouverture est un compteur TCONGE, pas le solde de congés.
                    info.valoOuverture = rs.getBigDecimal("cumul_valorisation_ouverture");
                }
            }
        }

        if (info == null) {
            return null;
        }

        return info;
    }

    private List<MoisSyntheseData> charger12MoisReference(int idEmploye, PeriodeReferenceInfo ref, Connection c) throws SQLException {
        List<MoisSyntheseData> liste = new ArrayList<>();

        // 1. Charger tout conge_journal_droit pour ce cycle
        String sqlJournal = "SELECT numero_mois_reference, annee, mois, salaire_base, jours_travailles_formule, " +
                "jours_droit_acquis, base_valorisation, valorisation_mensuelle, " +
                "cumul_jours_droit, cumul_valorisation, taux_journalier_conge, j.id_periode_paie, " +
                "pp.date_debut AS paie_date_debut, pp.date_fin AS paie_date_fin " +
                "FROM conge_journal_droit j " +
                "LEFT JOIN periode_paie pp ON pp.id_periode_paie = j.id_periode_paie " +
                "WHERE j.id_employe = ? AND j.id_periode_reference = ? " +
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
                    map.put("paie_date_debut", rs.getDate("paie_date_debut"));
                    map.put("paie_date_fin", rs.getDate("paie_date_fin"));
                    mapJournal.put(num, map);
                }
            }
        }

        // Préparer les dates des mois connus afin de lire l'historique des mouvements une seule fois.
        LocalDate today = LocalDate.now();
        List<MouvementWindow> movementWindows = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int monthNumber = i + 1;
            Map<String, Object> entry = mapJournal.get(monthNumber);
            Date payrollStart = entry != null ? (Date) entry.get("paie_date_debut") : null;
            Date payrollEnd = entry != null ? (Date) entry.get("paie_date_fin") : null;
            LocalDate monthStart = payrollStart != null ? payrollStart.toLocalDate() : ref.dateDebut.plusMonths(i);
            LocalDate monthEnd = payrollEnd != null ? payrollEnd.toLocalDate()
                    : ref.dateDebut.plusMonths(i + 1).minusDays(1);
            if (monthStart.isBefore(ref.dateDebut)) monthStart = ref.dateDebut;
            if (monthEnd.isAfter(ref.dateFin)) monthEnd = ref.dateFin;
            if (!monthStart.isAfter(today)) {
                LocalDate situationDate = monthEnd.isAfter(today) ? today : monthEnd;
                movementWindows.add(new MouvementWindow(monthNumber, monthStart, situationDate));
            }
        }
        Map<Integer, MouvementTotals> movementTotals = chargerCumulsMouvements(idEmploye, movementWindows, c);
        Map<Integer, BigDecimal> ratesByMonth = chargerTauxApplicables(idEmploye, movementWindows, c);
        List<Integer> periodeIds = mapJournal.values().stream()
                .map(entry -> entry.get("id_periode_paie"))
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .distinct()
                .toList();
        Map<Integer, BulletinMois> bulletinsParPeriode = chargerBulletinsParPeriode(idEmploye, periodeIds, c);

        // 2. Dérouler les 12 mois légaux de la période
        for (int i = 0; i < 12; i++) {
            int numMoisRef = i + 1;
            Map<String, Object> jEntry = mapJournal.get(numMoisRef);
            Date dateDebutPaie = jEntry != null ? (Date) jEntry.get("paie_date_debut") : null;
            Date dateFinPaie = jEntry != null ? (Date) jEntry.get("paie_date_fin") : null;
            LocalDate debutM = dateDebutPaie != null ? dateDebutPaie.toLocalDate() : ref.dateDebut.plusMonths(i);
            LocalDate finM = dateFinPaie != null ? dateFinPaie.toLocalDate() : ref.dateDebut.plusMonths(i + 1).minusDays(1);
            if (debutM.isBefore(ref.dateDebut)) debutM = ref.dateDebut;
            if (finM.isAfter(ref.dateFin)) finM = ref.dateFin;

            MoisSyntheseData d = new MoisSyntheseData();
            d.numeroMoisRef = numMoisRef;
            d.labelMois = numMoisRef + " — " + debutM.format(DATE_FMT) + " au " + finM.format(DATE_FMT);
            if (debutM.isAfter(today)) {
                liste.add(d);
                continue;
            }
            LocalDate dateSituation = finM.isAfter(today) ? today : finM;
            d.dateSituation = dateSituation;

            if (jEntry != null) {
                d.salaireBase = (BigDecimal) jEntry.get("salaire_base");
                d.baseActiviteJours = (BigDecimal) jEntry.get("jours_travailles_formule");
                d.droitsAcquisJours = (BigDecimal) jEntry.get("jours_droit_acquis");
                d.assietteDroits = (BigDecimal) jEntry.get("base_valorisation");
                d.valorisationAcquise = (BigDecimal) jEntry.get("valorisation_mensuelle");
                d.cumulDroitsJours = (BigDecimal) jEntry.get("cumul_jours_droit");
                d.cumulValorisation = (BigDecimal) jEntry.get("cumul_valorisation");
            }

            MouvementTotals totals = movementTotals.get(numMoisRef);
            if (totals != null) {
                d.soldeDisponible = totals.solde();
                d.congesPrisJours = totals.prises();
            }
            d.tcongeApplicable = ratesByMonth.get(numMoisRef);

            if (jEntry != null && jEntry.get("id_periode_paie") instanceof Number periodeId) {
                BulletinMois bulletin = bulletinsParPeriode.get(periodeId.intValue());
                if (bulletin != null) {
                    d.absencesJours = bulletin.absences();
                    d.retenueAbsence = bulletin.retenueAbsence();
                    d.indemniteConge = bulletin.indemniteConge();
                    d.hsRemunerees = bulletin.heuresSupplementaires();
                }
            }

            liste.add(d);
        }

        return liste;
    }

    private Map<Integer, BulletinMois> chargerBulletinsParPeriode(int idEmploye,
            List<Integer> periodeIds, Connection c) throws SQLException {
        if (periodeIds.isEmpty()) return Map.of();
        String sql = "WITH derniers AS ("
                + " SELECT DISTINCT ON (bp.id_periode_paie) bp.id_bulletin,bp.id_periode_paie,bp.jours_absence"
                + " FROM bulletin_paie bp WHERE bp.id_employe=? AND bp.id_periode_paie=ANY(?)"
                + " AND bp.statut IN ('VALIDE','CLOTURE','CALCULE')"
                + " ORDER BY bp.id_periode_paie,bp.id_bulletin DESC"
                + ") SELECT d.id_periode_paie,d.jours_absence,"
                + "COALESCE(SUM(l.montant_salarial) FILTER (WHERE l.code_rubrique='RETENUE_ABSENCE'),0) AS ret_abs,"
                + "COALESCE(SUM(l.montant_salarial) FILTER (WHERE l.code_rubrique IN ('INDEMNITE_CONGE','INDEMNITE_CONGE_PAYE')),0) AS ind_conge,"
                + "COALESCE(SUM(l.montant_salarial) FILTER (WHERE l.code_rubrique LIKE 'HS_%' OR l.code_rubrique='HS'"
                + " OR l.categorie IN ('HEURES_SUP','HEURES_SUPPLEMENTAIRES')),0) AS hs_montant"
                + " FROM derniers d LEFT JOIN bulletin_paie_ligne l ON l.id_bulletin=d.id_bulletin"
                + " GROUP BY d.id_periode_paie,d.jours_absence";
        Map<Integer, BulletinMois> result = new HashMap<>();
        java.sql.Array periodeArray = c.createArrayOf("integer", periodeIds.toArray());
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, idEmploye);
            statement.setArray(2, periodeArray);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("id_periode_paie"), new BulletinMois(
                            rs.getBigDecimal("jours_absence"), rs.getBigDecimal("ret_abs"),
                            rs.getBigDecimal("ind_conge"), rs.getBigDecimal("hs_montant")));
                }
            }
        } finally {
            periodeArray.free();
        }
        return result;
    }

    private Map<Integer, MouvementTotals> chargerCumulsMouvements(int idEmploye,
            List<MouvementWindow> windows, Connection c) throws SQLException {
        Map<Integer, MouvementTotals> result = new HashMap<>();
        if (windows.isEmpty()) return result;
        LocalDate latestDate = windows.stream().map(MouvementWindow::situation)
                .max(LocalDate::compareTo).orElseThrow();
        NavigableMap<LocalDate, MouvementTotals> cumulative = new TreeMap<>();
        BigDecimal solde = BigDecimal.ZERO;
        BigDecimal prises = BigDecimal.ZERO;
        String sql = "SELECT date_mouvement, COALESCE(SUM(nb_jours), 0) AS variation_solde, "
                + "COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE'), 0) AS variation_prise "
                + "FROM mouvement_solde_conge WHERE id_employe = ? AND date_mouvement <= ? "
                + "GROUP BY date_mouvement ORDER BY date_mouvement";
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, idEmploye);
            statement.setDate(2, Date.valueOf(latestDate));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    solde = solde.add(rs.getBigDecimal("variation_solde"));
                    prises = prises.add(rs.getBigDecimal("variation_prise"));
                    cumulative.put(rs.getDate("date_mouvement").toLocalDate(), new MouvementTotals(solde, prises));
                }
            }
        }
        for (MouvementWindow window : windows) {
            Map.Entry<LocalDate, MouvementTotals> atSituation = cumulative.floorEntry(window.situation());
            Map.Entry<LocalDate, MouvementTotals> beforeStart = cumulative.floorEntry(window.debut().minusDays(1));
            BigDecimal balance = atSituation == null ? BigDecimal.ZERO : atSituation.getValue().solde();
            BigDecimal takenThrough = atSituation == null || window.situation().isBefore(window.debut())
                    ? BigDecimal.ZERO : atSituation.getValue().prises();
            BigDecimal takenBefore = beforeStart == null || window.situation().isBefore(window.debut())
                    ? BigDecimal.ZERO : beforeStart.getValue().prises();
            result.put(window.moisReference(), new MouvementTotals(balance, takenThrough.subtract(takenBefore)));
        }
        return result;
    }

    private Map<Integer, BigDecimal> chargerTauxApplicables(int idEmploye,
            List<MouvementWindow> windows, Connection c) throws SQLException {
        Map<Integer, BigDecimal> result = new HashMap<>();
        if (windows.isEmpty()) return result;
        LocalDate earliest = windows.stream().map(MouvementWindow::situation)
                .min(LocalDate::compareTo).orElseThrow();
        LocalDate latest = windows.stream().map(MouvementWindow::situation)
                .max(LocalDate::compareTo).orElseThrow();
        List<TauxInterval> rates = new ArrayList<>();
        String sql = "SELECT date_effet, date_fin, taux_journalier FROM conge_taux_applicable "
                + "WHERE id_employe = ? AND date_effet <= ? AND (date_fin IS NULL OR date_fin > ?) "
                + "ORDER BY date_effet DESC, id_taux_applicable DESC";
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, idEmploye);
            statement.setDate(2, Date.valueOf(latest));
            statement.setDate(3, Date.valueOf(earliest));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Date end = rs.getDate("date_fin");
                    rates.add(new TauxInterval(rs.getDate("date_effet").toLocalDate(),
                            end == null ? null : end.toLocalDate(), rs.getBigDecimal("taux_journalier")));
                }
            }
        }
        for (MouvementWindow window : windows) {
            List<TauxInterval> applicable = rates.stream()
                    .filter(rate -> !rate.debut().isAfter(window.situation())
                            && (rate.fin() == null || rate.fin().isAfter(window.situation())))
                    .toList();
            if (applicable.size() > 1) {
                throw new IllegalStateException("TCONGE_APPLICABLE_AMBIGUOUS: plusieurs taux applicables pour l'employé "
                        + idEmploye + " au " + window.situation());
            }
            if (!applicable.isEmpty()) result.put(window.moisReference(), applicable.get(0).taux());
        }
        return result;
    }

    private Map<LocalDate, BigDecimal> chargerTauxPourDates(int idEmploye,
            List<LocalDate> dates, Connection c) throws SQLException {
        if (dates.isEmpty()) return Map.of();
        LocalDate earliest = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate latest = dates.stream().max(LocalDate::compareTo).orElseThrow();
        List<TauxInterval> rates = new ArrayList<>();
        String sql = "SELECT date_effet, date_fin, taux_journalier FROM conge_taux_applicable "
                + "WHERE id_employe = ? AND date_effet <= ? AND (date_fin IS NULL OR date_fin > ?) "
                + "ORDER BY date_effet DESC, id_taux_applicable DESC";
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, idEmploye);
            statement.setDate(2, Date.valueOf(latest));
            statement.setDate(3, Date.valueOf(earliest));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Date end = rs.getDate("date_fin");
                    rates.add(new TauxInterval(rs.getDate("date_effet").toLocalDate(),
                            end == null ? null : end.toLocalDate(), rs.getBigDecimal("taux_journalier")));
                }
            }
        }
        Map<LocalDate, BigDecimal> result = new HashMap<>();
        for (LocalDate date : dates.stream().distinct().toList()) {
            List<TauxInterval> applicable = rates.stream()
                    .filter(rate -> !rate.debut().isAfter(date)
                            && (rate.fin() == null || rate.fin().isAfter(date)))
                    .toList();
            if (applicable.size() > 1) {
                throw new IllegalStateException("TCONGE_APPLICABLE_AMBIGUOUS: plusieurs taux applicables pour l'employé "
                        + idEmploye + " au " + date);
            }
            if (!applicable.isEmpty()) result.put(date, applicable.get(0).taux());
        }
        return result;
    }

    private BigDecimal obtenirValeurStock(int idEmploye, LocalDate date, Connection c,
            Map<LocalDate, BigDecimal> valeursStockParDate) throws SQLException {
        if (!valeursStockParDate.containsKey(date)) {
            valeursStockParDate.put(date, lots.situation(idEmploye, date, c).soldeValorisation());
        }
        return valeursStockParDate.get(date);
    }

    // =========================================================================
    // UTILITAIRES POI & FORMATTAGE
    // =========================================================================

    private void setCellString(Sheet sheet, int rowIndex, int colIndex, String text) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.getCell(colIndex);
        if (cell == null) {
            cell = row.createCell(colIndex);
        }
        cell.setBlank();
        cell.setCellValue(text != null ? text : "");
    }

    private void setNumericValue(Row row, int colIndex, BigDecimal value) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) cell = row.createCell(colIndex);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else cell.setBlank();
    }

    private void setTauxValue(Row row, int colIndex, BigDecimal value) {
        setNumericValue(row, colIndex, value);
        Cell cell = row.getCell(colIndex);
        cell.setCellStyle(tauxStyle(row.getSheet().getWorkbook(), cell.getCellStyle()));
    }

    private CellStyle tauxStyle(org.apache.poi.ss.usermodel.Workbook workbook, CellStyle base) {
        CellStyle style = workbook.createCellStyle();
        if (base != null) style.cloneStyleFrom(base);
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.######"));
        return style;
    }

    private String safe(String s) {
        return s != null ? s.trim() : "—";
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "—";
        return String.format("%,.2f Ar", amount.doubleValue());
    }

    private String formatTaux(BigDecimal taux) {
        if (taux == null) return "—";
        return taux.stripTrailingZeros().toPlainString() + " Ar/j";
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
            case "EXPIRATION" -> "Expiration confirmée après examen";
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
        LocalDate dateSituation;
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
        BigDecimal soldeDisponible;
    }
}
