package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.CertificatTravailDTO;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcPreparationDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.persistence.PaiePret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test d'intégration validant le cycle complet du STC :
 * 1. Détection du pointage réel vs estimation calendaire
 * 2. Vérification des préavis et indemnités saisis par le gestionnaire
 * 3. Conservation du prêt comme créance distincte
 * 4. Remise à zéro stricte du solde de congés après versement de l'ICCP
 * 5. Disponibilité du Certificat de Travail officiel
 */
@SpringBootTest
@ActiveProfiles("test")
class StcLegalWorkflowIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private StcService stcService;
    @Autowired private CongeTauxApplicableService congeTauxApplicableService;
    @Autowired private DocumentRhService documentRhService;

    @Test
    @DisplayName("STC : le bulletin mensuel validé est rapproché des seuls paiements attestés")
    void rapprocheBulletinMensuelEtPaiementReel() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (Statement migration = c.createStatement()) {
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_contrat INT REFERENCES contrat_travail(id_contrat)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS source_pointage VARCHAR(30)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_bulletin_mensuel INT REFERENCES bulletin_paie(id_bulletin)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS net_bulletin_mensuel NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS montant_bulletin_deja_paye NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS solde_bulletin_a_regler NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS date_paiement_bulletin DATE");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS reference_paiement_bulletin VARCHAR(255)");
                }
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_stc_rapprochement_" + System.currentTimeMillis());
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-STC-RAPP", "Rabe", "Lina");
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2026, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2026, 1, 1), new BigDecimal("300000"));
                Date depart = Date.valueOf("2026-10-10");
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, depart);
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }
                int periodeId;
                try (PreparedStatement psSel = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE '2026-10-01' BETWEEN date_debut AND date_fin LIMIT 1")) {
                    try (ResultSet rsSel = psSel.executeQuery()) {
                        if (rsSel.next()) {
                            periodeId = rsSel.getInt(1);
                        } else {
                            try (PreparedStatement ps = c.prepareStatement(
                                    "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) "
                                    + "VALUES (?, 'Octobre STC rapprochement', '2026-10-01', '2026-10-31', 1) RETURNING id_periode_paie")) {
                                ps.setString(1, "P-" + empId + "-" + (System.currentTimeMillis() % 10000));
                                try (ResultSet rs = ps.executeQuery()) { rs.next(); periodeId = rs.getInt(1); }
                            }
                        }
                    }
                }
                int snapshotId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_cumul_periode (id_employe, id_periode_paie, nb_jours_ouvrables, "
                        + "nb_jours_presents, total_heures_travaillees) VALUES (?, ?, 10, 8, 64) RETURNING id_cumul")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); snapshotId = rs.getInt(1); }
                }
                int bulletinId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bulletin_paie (id_periode_paie, id_employe, id_snapshot_pointage, "
                        + "type_bulletin, reference_bulletin, matricule, nom_prenom, intitule_poste, nom_site, "
                        + "regime_intervenant, salaire_base, taux_horaire, net_a_payer, statut) "
                        + "VALUES (?, ?, ?, 'MENSUEL', ?, 'EMP-STC-RAPP', 'Rabe Lina', 'Salariée', "
                        + "'Antananarivo', 'PERMANENT', 300000, 1730, 300000, 'VALIDE') RETURNING id_bulletin")) {
                    ps.setInt(1, periodeId);
                    ps.setInt(2, empId);
                    ps.setInt(3, snapshotId);
                    ps.setString(4, "MENS-STC-RAPP-" + empId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); bulletinId = rs.getInt(1); }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE bulletin_paie SET total_gains_bruts=300000, total_cotisations_salariales=6000, "
                        + "salaire_net_imposable=294000, irsa_net=3000 WHERE id_bulletin=?")) {
                    ps.setInt(1, bulletinId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bulletin_paie_ligne (id_bulletin, code_rubrique, libelle_rubrique, "
                        + "sens, categorie, base_calcul, montant_salarial, montant_patronal) VALUES "
                        + "(?, 'COTISATION_CNAPS_SAL', 'CNaPS', 'RETENUE', 'COTISATION_SALARIALE', 300000, 3000, 0), "
                        + "(?, 'COTISATION_CNAPS_PAT', 'CNaPS employeur', 'PATRONAL', 'COTISATION_PATRONALE', 300000, 0, 39000), "
                        + "(?, 'COTISATION_OSTIE_SAL', 'Santé', 'RETENUE', 'COTISATION_SALARIALE', 300000, 3000, 0), "
                        + "(?, 'COTISATION_OSTIE_PAT', 'Santé employeur', 'PATRONAL', 'COTISATION_PATRONALE', 300000, 0, 15000), "
                        + "(?, 'COTISATION_FMFP_PAT', 'FMFP', 'PATRONAL', 'COTISATION_PATRONALE', 300000, 0, 3000)")) {
                    for (int i = 1; i <= 5; i++) ps.setInt(i, bulletinId);
                    ps.executeUpdate();
                }
                StcCalculRequest req = new StcCalculRequest();
                req.setIdEmploye(empId);
                req.setIdPeriodePaie(periodeId);
                req.setDateDepart(depart);
                req.setMotifRupture("DEMISSION");
                req.setIndemnitePreavis(new BigDecimal("1000"));
                req.setMoisPreavis(BigDecimal.ONE);
                req.setJustificationIndemnites("Décision RH test de calcul différentiel");
                req.setMontantBulletinDejaPaye(new BigDecimal("200000"));
                req.setDatePaiementBulletin(depart);
                req.setReferencePaiementBulletin("VIR-PAIE-RAPP-200000");
                StcCalculResultDTO valide = stcService.validerStc(req, adminId, c);
                assertEquals(bulletinId, valide.getIdBulletinMensuel());
                assertEquals(0, BigDecimal.ZERO.compareTo(valide.getSalaireProrata()));
                assertEquals(0, new BigDecimal("100000").compareTo(valide.getSoldeBulletinARegler()));
                assertEquals(0, new BigDecimal("10").compareTo(valide.getCotisationCnaps()));
                StcRecuDTO recu = stcService.obtenirRecuStc(valide.getIdStc(), c);
                assertTrue(recu.isEmployeurHistoriqueDisponible());
                String employeurValide = recu.getEntrepriseNom();
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE ref_filiale SET raison_sociale = ? WHERE id_filiale = "
                                + "(SELECT id_filiale FROM bulletin_paie_employeur_snapshot WHERE id_bulletin = ?)")) {
                    ps.setString(1, "Nom modifié après STC");
                    ps.setInt(2, valide.getIdBulletin());
                    ps.executeUpdate();
                }
                assertEquals(employeurValide, stcService.obtenirRecuStc(valide.getIdStc(), c).getEntrepriseNom());
                assertEquals(bulletinId, recu.getIdBulletinMensuel());
                assertEquals(0, new BigDecimal("200000").compareTo(recu.getMontantBulletinDejaPaye()));
                assertEquals("VIR-PAIE-RAPP-200000", recu.getReferencePaiementBulletin());
                assertEquals(0, new BigDecimal("100000").compareTo(recu.getSoldeBulletinARegler()));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("STC : pointage, droits calculés, prêt distinct et reçu figé")
    void testWorkflowCompletStcLoisEtCongeZero() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // La base de test partagée n'exécute pas automatiquement les migrations SQL.
                try (Statement migration = c.createStatement()) {
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_contrat INT REFERENCES contrat_travail(id_contrat)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS source_pointage VARCHAR(30)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_bulletin_mensuel INT REFERENCES bulletin_paie(id_bulletin)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS net_bulletin_mensuel NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS montant_bulletin_deja_paye NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS solde_bulletin_a_regler NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS date_paiement_bulletin DATE");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS reference_paiement_bulletin VARCHAR(255)");
                }
                // 1. Utilisateur DAF / RH
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "rh_stc_tester_" + System.currentTimeMillis());

                // 2. Création d'un employé Cadre (ancienneté 4 ans = 48 mois)
                int empId = CongeTestFixtureHelper.createEmploye(c, "EMP-STC-01", "Rasoa", "Marie");

                // Contrat CDI & Affectation carrière CADRE (salaire 3 000 000 Ar, embauche 01/10/2022)
                int contratId = CongeTestFixtureHelper.createContratActif(c, empId, LocalDate.of(2022, 10, 1));
                try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET numero_contrat = 'CT-STC-01' WHERE id_contrat = ?")) {
                    ps.setInt(1, contratId);
                    ps.executeUpdate();
                }

                int affectationId = CongeTestFixtureHelper.createAffectation(c, empId, contratId, LocalDate.of(2022, 10, 1), new BigDecimal("3000000.00"));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET id_categorie_professionnelle = (SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle WHERE code = 'CADRE' LIMIT 1) " +
                        "WHERE id_affectation = ?")) {
                    ps.setInt(1, affectationId);
                    ps.executeUpdate();
                }

                // Période de paie active (Octobre 2026)
                LocalDate dateDepart = LocalDate.of(2026, 10, 10); // Départ effectif au 10 octobre 2026 (<= today)
                int periodeId;
                try (PreparedStatement psSel = c.prepareStatement("SELECT id_periode_paie FROM periode_paie WHERE '2026-10-01' BETWEEN date_debut AND date_fin LIMIT 1")) {
                    try (ResultSet rsSel = psSel.executeQuery()) {
                        if (rsSel.next()) {
                            periodeId = rsSel.getInt(1);
                        } else {
                            try (PreparedStatement ps = c.prepareStatement(
                                    "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                                    "VALUES ('2026-10-STC', 'Octobre 2026 STC Test', '2026-10-01', '2026-10-31', 1) RETURNING id_periode_paie")) {
                                try (ResultSet rs = ps.executeQuery()) {
                                    rs.next();
                                    periodeId = rs.getInt(1);
                                }
                            }
                        }
                    }
                }

                // Pointage partiel : trois jours saisis pour dix jours sous contrat.
                int idStatutPresent = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT' LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idStatutPresent = rs.getInt(1);
                }

                int idSite = 1;
                try (PreparedStatement ps = c.prepareStatement("SELECT id_site FROM ref_site LIMIT 1");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) idSite = rs.getInt(1);
                }

                for (int day = 1; day <= 3; day++) {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, total_heures) " +
                            "VALUES (?, ?, ?, ?, ?, 8.00)")) {
                        ps.setInt(1, empId);
                        ps.setInt(2, periodeId);
                        ps.setInt(3, idSite);
                        ps.setDate(4, Date.valueOf(LocalDate.of(2026, 10, day)));
                        ps.setInt(5, idStatutPresent);
                        ps.executeUpdate();
                    }
                }

                // 4. Crédit de congés : 15 jours acquis via Cutover initial
                var cutover = new CongeTauxApplicableService.CutoverOpeningRequest();
                cutover.setIdEmploye(empId);
                cutover.setDateCutover(LocalDate.of(2026, 10, 1));
                cutover.setDateDebutReference(LocalDate.of(2026, 1, 1));
                cutover.setReferenceArrete("ARRETE-STC-TEST");
                cutover.setSoldeJours(new BigDecimal("15.00"));
                cutover.setValeurSoldeOuverture(new BigDecimal("1500000.00"));
                cutover.setTauxJournalierApplicable(new BigDecimal("100000.00"));
                cutover.setCumulDroitsOuverture(BigDecimal.ZERO);
                cutover.setCumulValorisationOuverture(BigDecimal.ZERO);
                cutover.setCommentaire("Stock initial congés pour STC test");
                congeTauxApplicableService.enregistrerCutoverSnapshot(cutover, adminId, c);

                VSoldeCongeEmploye soldeInitial = VSoldeCongeEmploye.rechercherParEmploye(empId, c);
                assertNotNull(soldeInitial);
                assertEquals(0, new BigDecimal("15.00").compareTo(soldeInitial.getSoldeActuel()), "L'employé doit avoir 15j de congés");

                // 5. Prêt entreprise accordé : Capital restant 500 000 Ar
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) " +
                        "VALUES (?, 'PRET-STC-01', 1000000.00, 100000.00, 10, '2026-01-01', 500000.00, 500000.00, 'EN_COURS', 'Prêt équipement test')")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                // =====================================================================
                // ÉTAPE 1 : PRÉPARATION DU STC & VÉRIFICATIONS LÉGALES
                // =====================================================================
                StcPreparationDTO prep = stcService.preparerStc(empId, Date.valueOf(dateDepart), "LICENCIEMENT", c);
                assertNotNull(prep);
                assertEquals(contratId, prep.getIdContrat(), "L'idContrat doit être correctement résolu");

                // A. Détection du pointage réel
                assertEquals("POINTAGE_REEL", prep.getSourcePointage(), "Le pointage réel doit être détecté");
                assertEquals(0, new BigDecimal("10.0").compareTo(prep.getJoursTravaillesEstimes()), "Le pointage partiel ne réduit pas les jours sous contrat");
                assertEquals(0, new BigDecimal("3.0").compareTo(prep.getJoursPointageReels()));
                assertEquals(0, new BigDecimal("24.00").compareTo(prep.getTotalHeuresPointage()));
                assertFalse(prep.isPointageGele());
                assertTrue(prep.getControlesApplication().stream().anyMatch(v -> v.contains("Pointage final non gelé")));

                // Aucune indemnité ne découle du seul libellé de catégorie.
                assertEquals(0, BigDecimal.ZERO.compareTo(prep.getMoisPreavisSuggere()));
                assertEquals(0, BigDecimal.ZERO.compareTo(prep.getIndemnitePreavisEstimee()));
                assertEquals(0, BigDecimal.ZERO.compareTo(prep.getIndemniteLicenciementEstimee()));

                // D. Capital prêt détecté
                assertEquals(0, new BigDecimal("500000.00").compareTo(prep.getCapitalRestantPrets()), "Capital restant du prêt doit être de 500 000 Ar");

                // =====================================================================
                // ÉTAPE 2 : SIMULATION DU STC AVEC APUREMENT DU PRÊT
                // =====================================================================
                StcCalculRequest req = new StcCalculRequest();
                req.setIdEmploye(empId);
                req.setIdPeriodePaie(periodeId);
                req.setDateDepart(Date.valueOf(dateDepart));
                req.setMotifRupture("LICENCIEMENT");
                req.setSimulation(true);
                req.setModeReglement("VIREMENT");
                req.setJoursTravaillesDernierMois(new BigDecimal("10.0"));
                req.setMoisPreavis(new BigDecimal("3.0"));
                req.setIndemnitePreavis(BigDecimal.ZERO);
                req.setIndemniteLicenciement(BigDecimal.ZERO);
                req.setJustificationIndemnites("Licenciement économique autorisé");
                req.setRemunerationReferenceLicenciement(new BigDecimal("3000000.00"));
                req.setApurerPretDansStc(true);
                assertThrows(IllegalArgumentException.class, () -> stcService.simulerStc(req, c));
                req.setApurerPretDansStc(false);

                StcCalculResultDTO simRes = stcService.simulerStc(req, c);
                assertNotNull(simRes);
                assertEquals("SIMULATION", simRes.getStatut());
                assertEquals(contratId, simRes.getIdContrat());
                assertEquals("POINTAGE_REEL", simRes.getSourcePointage());
                assertEquals(0, BigDecimal.ZERO.compareTo(simRes.getSoldePretsDeduit()), "Le prêt reste distinct du STC");
                assertTrue(simRes.getNetAPayerStc().compareTo(BigDecimal.ZERO) > 0);

                // =====================================================================
                // ÉTAPE 3 : VALIDATION DÉFINITIVE DU STC
                // =====================================================================
                req.setSimulation(false);
                assertThrows(IllegalArgumentException.class, () -> stcService.validerStc(req, adminId, c),
                        "Une simulation ne doit pas autoriser la validation d'un contrat encore ouvert");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_fin = ? WHERE id_contrat = ?")) {
                    ps.setDate(1, Date.valueOf(dateDepart));
                    ps.setInt(2, contratId);
                    ps.executeUpdate();
                }
                assertThrows(IllegalStateException.class, () -> stcService.validerStc(req, adminId, c),
                        "Un pointage saisi mais non gelé ne doit pas être accepté");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_cumul_periode (id_employe, id_periode_paie, nb_jours_ouvrables, "
                        + "nb_jours_presents, total_heures_travaillees) VALUES (?, ?, 10, 3, 24)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_jour SET montant_total_indemnites = 5000 "
                        + "WHERE id_employe = ? AND date_pointage = DATE '2026-10-01'")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                assertThrows(IllegalStateException.class, () -> stcService.validerStc(req, adminId, c),
                        "Les variables du pointage non intégrées doivent bloquer la validation");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_jour SET montant_total_indemnites = 0 "
                        + "WHERE id_employe = ? AND date_pointage = DATE '2026-10-01'")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM pointage_jour WHERE id_employe = ? AND id_periode_paie = ?")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.executeUpdate();
                }
                assertThrows(IllegalStateException.class, () -> stcService.validerStc(req, adminId, c),
                        "Sans pointage, le salaire final doit être justifié");
                req.setJustificationSalaireDernierMois("Pointage indisponible, jours sous contrat vérifiés par RH");
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, sens, "
                        + "montant, montant_initial, solde_restant, motif, statut) "
                        + "VALUES (?, ?, 'PRIME_EXCEPTIONNELLE', 'GAIN', 10000, 10000, 10000, "
                        + "'Prime finale à intégrer', 'EN_ATTENTE')")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.executeUpdate();
                }
                assertTrue(stcService.preparerStc(empId, Date.valueOf(dateDepart), "LICENCIEMENT", c)
                        .getControlesApplication().stream().anyMatch(v -> v.contains("Variables de paie présentes")));
                assertThrows(IllegalStateException.class, () -> stcService.validerStc(req, adminId, c),
                        "Une prime encore en attente ne doit pas disparaître du STC");
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM paie_avance_acompte WHERE id_employe = ? AND id_periode_paie = ? "
                        + "AND type_mouvement = 'PRIME_EXCEPTIONNELLE'")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, "
                        + "id_statut_jour, total_heures) VALUES (?, ?, ?, DATE '2026-10-01', ?, 8.00)")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, idSite);
                    ps.setInt(4, idStatutPresent);
                    ps.executeUpdate();
                }
                StcCalculResultDTO valRes = stcService.validerStc(req, adminId, c);
                assertNotNull(valRes);
                assertEquals("VALIDE", valRes.getStatut());
                assertNotNull(valRes.getIdStc());
                assertNotNull(valRes.getIdBulletin());

                // =====================================================================
                // VÉRIFICATION CLÉ 1 : LE SOLDE DE CONGÉS EST STRICTEMENT REMIS À 0.00
                // =====================================================================
                VSoldeCongeEmploye soldeFinal = VSoldeCongeEmploye.rechercherParEmploye(empId, c);
                assertNotNull(soldeFinal);
                assertEquals(0, BigDecimal.ZERO.compareTo(soldeFinal.getSoldeActuel()),
                        "LE SOLDE DE CONGÉS DOIT ÊTRE STRICTEMENT DE 0.00 APRÈS VALIDATION DU STC (LIQUIDATION ICCP)");

                // =====================================================================
                // VÉRIFICATION CLÉ 2 : LE PRÊT D'ENTREPRISE EST SOLDÉ
                // =====================================================================
                BigDecimal capRestantApres = PaiePret.calculerCapitalRestantTotal(empId, c);
                assertEquals(0, new BigDecimal("500000.00").compareTo(capRestantApres),
                        "Le prêt reste une créance distincte");

                // =====================================================================
                // VÉRIFICATION CLÉ 3 : LE REÇU OFFICIEL ET LE CERTIFICAT DE TRAVAIL
                // =====================================================================
                StcRecuDTO recu = stcService.obtenirRecuStc(valRes.getIdStc(), c);
                assertNotNull(recu);
                assertEquals(contratId, recu.getIdContrat());
                assertEquals("POINTAGE_REEL", recu.getSourcePointage());
                assertEquals("LICENCIEMENT", recu.getMotifRupture());
                assertEquals("Rasoa Marie", recu.getNomComplet());

                // Certificat de travail officiel, généré depuis le contrat courant.
                CertificatTravailDTO certDto = documentRhService.preparerCertificat(contratId, "contrat", c);
                assertNotNull(certDto);
                assertEquals("EMP-STC-01", certDto.getEmploye().getMatricule());
                assertEquals("Rasoa Marie", certDto.getEmploye().getNomComplet());
                assertNotNull(certDto.getDateSortie());
                try (PreparedStatement ps = c.prepareStatement("SELECT id_bulletin FROM paie_stc WHERE id_stc = ?")) {
                    ps.setInt(1, valRes.getIdStc());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(valRes.getIdBulletin(), rs.getObject(1));
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT nom_prenom FROM bulletin_paie WHERE id_bulletin = ?")) {
                    ps.setInt(1, valRes.getIdBulletin());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Rasoa Marie", rs.getString(1));
                    }
                }
                // Un bulletin mensuel validé reste distinct du paiement. La simulation
                // ne reprend que son net non encore réglé, sans repayer son brut.
                int bulletinMensuelId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bulletin_paie (id_periode_paie, id_employe, id_snapshot_pointage, "
                        + "type_bulletin, reference_bulletin, matricule, nom_prenom, intitule_poste, "
                        + "nom_site, regime_intervenant, mode_calcul_paie, salaire_base, taux_horaire, "
                        + "net_a_payer, statut) "
                        + "SELECT id_periode_paie, id_employe, "
                        + "(SELECT id_cumul FROM pointage_cumul_periode WHERE id_employe = ? "
                        + "AND id_periode_paie = ? LIMIT 1), 'MENSUEL', 'MENS-STC-TEST-' || ?, "
                        + "matricule, nom_prenom, intitule_poste, nom_site, regime_intervenant, "
                        + "mode_calcul_paie, salaire_base, taux_horaire, 300000, 'VALIDE' "
                        + "FROM bulletin_paie WHERE id_bulletin = ? RETURNING id_bulletin")) {
                    ps.setInt(1, empId);
                    ps.setInt(2, periodeId);
                    ps.setInt(3, empId);
                    ps.setInt(4, valRes.getIdBulletin());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        bulletinMensuelId = rs.getInt(1);
                    }
                }
                req.setMontantBulletinDejaPaye(new BigDecimal("200000"));
                assertThrows(IllegalArgumentException.class, () -> stcService.simulerStc(req, c),
                        "Un montant déjà payé exige sa preuve datée");
                req.setDatePaiementBulletin(Date.valueOf(dateDepart));
                req.setReferencePaiementBulletin("VIR-TEST-200000");
                StcCalculResultDTO rapprochement = stcService.simulerStc(req, c);
                assertEquals(bulletinMensuelId, rapprochement.getIdBulletinMensuel());
                assertEquals(0, BigDecimal.ZERO.compareTo(rapprochement.getSalaireProrata()));
                assertEquals(0, new BigDecimal("100000").compareTo(rapprochement.getSoldeBulletinARegler()));
                assertEquals(0, new BigDecimal("100000").compareTo(rapprochement.getNetAPayerStc()));
                req.setMontantBulletinDejaPaye(new BigDecimal("300001"));
                assertThrows(IllegalArgumentException.class, () -> stcService.simulerStc(req, c));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe SET nom = 'Nom modifié' WHERE id_employe = ?")) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT nom_prenom FROM bulletin_paie WHERE id_bulletin = ?")) {
                    ps.setInt(1, valRes.getIdBulletin());
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Rasoa Marie", rs.getString(1));
                    }
                }
                assertEquals("Rasoa Marie", stcService.obtenirRecuStc(valRes.getIdStc(), c).getNomComplet(),
                        "Le reçu validé conserve l'identité du bulletin figé");

            } finally {
                c.rollback(); // Isolation stricte du test
            }
        }
    }
}
