package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.PaieAvanceAcompte;
import mg.galaxam.sirh.model.paie.PaiePret;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.PaieRubrique;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("PaieBddModelTest — Validation du Socle BDD & Entités Backend du Module Paie")
class PaieBddModelTest {

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void nettoyerBulletinsTest() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE bulletin_paie SET statut = 'ROUVERT', motif_reouverture = 'Nettoyage unitaire test' " +
                    "WHERE id_periode_paie = 1 AND id_employe = 1 AND statut = 'CLOTURE'")) {
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM bulletin_paie WHERE id_periode_paie = 1 AND id_employe = 1")) {
                ps.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("1. Vérification du catalogue paie_rubrique et des rubriques fondamentales")
    void testPaieRubriqueCatalogue() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<PaieRubrique> rubriques = PaieRubrique.listerTous(c);
            assertNotNull(rubriques);
            assertTrue(rubriques.size() >= 30, "Le catalogue doit contenir au moins 30 rubriques de paie");

            // Vérification de rubriques clés
            PaieRubrique salBase = PaieRubrique.trouverParCode("SALAIRE_BASE", c);
            assertNotNull(salBase);
            assertEquals("GAIN", salBase.getSens());
            assertTrue(salBase.isSoumisCnaps());
            assertTrue(salBase.isSoumisIrsa());

            PaieRubrique cnapsSal = PaieRubrique.trouverParCode("COTISATION_CNAPS_SAL", c);
            assertNotNull(cnapsSal);
            assertEquals("RETENUE", cnapsSal.getSens());
            assertEquals("COTISATION_SALARIALE", cnapsSal.getCategorie());

            PaieRubrique irsaNet = PaieRubrique.trouverParCode("IMPOT_IRSA_NET", c);
            assertNotNull(irsaNet);
            assertEquals("RETENUE", irsaNet.getSens());

            PaieRubrique repas = PaieRubrique.trouverParCode("INDEMNITE_REPAS", c);
            assertNotNull(repas);
            assertEquals("INDEMNITE_NON_IMPOSABLE", repas.getCategorie());
            assertFalse(repas.isSoumisIrsa(), "L'indemnité repas doit être exonérée d'IRSA");

            PaieRubrique arrondi = PaieRubrique.trouverParCode("AJUSTEMENT_ARRONDI", c);
            assertNotNull(arrondi, "La rubrique d'ajustement arrondi doit exister dans le catalogue");
            assertEquals("NET", arrondi.getCategorie());
            assertEquals("NEUTRE", arrondi.getSens());

            // Test création / modification / suppression d'une rubrique temporaire
            c.setAutoCommit(false);
            try {
                PaieRubrique custom = new PaieRubrique();
                custom.setCode("TEST_PRIME_BONUS");
                custom.setLibelle("Prime Spéciale Test");
                custom.setCategorie("PRIME");
                custom.setTypeValeur("MONTANT");
                custom.setSens("GAIN");
                custom.setSoumisCnaps(true);
                custom.setSoumisSante(true);
                custom.setSoumisIrsa(true);
                custom.setOrdreAffichage(999);
                custom.setEstActif(true);
                custom.setDescription("Prime de test unitaire");

                custom.save(c);
                assertNotNull(custom.getIdRubrique());

                PaieRubrique relu = PaieRubrique.trouverParCode("TEST_PRIME_BONUS", c);
                assertNotNull(relu);
                assertEquals("Prime Spéciale Test", relu.getLibelle());

                relu.setLibelle("Prime Modifiée");
                relu.update(c);

                relu.delete(c);
                assertNull(PaieRubrique.trouverParCode("TEST_PRIME_BONUS", c));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("2. Vérification de la présence de la cotisation patronale FMFP (1%)")
    void testFmfpCotisationExiste() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String sql = "SELECT code, libelle, taux_patronal, taux_salarial, est_actif FROM ref_type_cotisation WHERE code = 'FMFP'";
            try (PreparedStatement ps = c.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "La cotisation FMFP doit exister dans ref_type_cotisation");
                assertEquals(new BigDecimal("1.00"), rs.getBigDecimal("taux_patronal"));
                assertEquals(new BigDecimal("0.00"), rs.getBigDecimal("taux_salarial"));
                assertTrue(rs.getBoolean("est_actif"));
            }
        }
    }

    @Test
    @DisplayName("3. Règle d'arrondi monétaire malgache à la dizaine d'Ariary")
    void testArrondiMonetaireDizaineAriary() {
        assertEquals(new BigDecimal("450120.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("450123.45")));
        assertEquals(new BigDecimal("450130.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("450127.00")));
        assertEquals(new BigDecimal("450130.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("450125.00")));
        assertEquals(new BigDecimal("450120.00"), BulletinPaie.arrondirDizaineAriary(new BigDecimal("450120.00")));
        assertEquals(BigDecimal.ZERO, BulletinPaie.arrondirDizaineAriary(null));
    }

    @Test
    @DisplayName("4. Garde-fou légal de quotité cessible (Art. 68 Code du Travail max 1/3)")
    void testQuotiteCessibleMaxUnTiers() {
        BigDecimal net = new BigDecimal("1500000.00");
        BigDecimal maxCessible = PaieAvanceAcompte.calculerQuotiteCessibleMax(net);
        assertEquals(new BigDecimal("500000.00"), maxCessible);

        // Retenue inférieure au tiers : conforme
        assertTrue(PaieAvanceAcompte.isQuotiteCessibleRespectee(net, new BigDecimal("350000.00")));
        // Retenue exactement égale au tiers : conforme
        assertTrue(PaieAvanceAcompte.isQuotiteCessibleRespectee(net, new BigDecimal("500000.00")));
        // Retenue excédant le tiers : violation bloquante
        assertFalse(PaieAvanceAcompte.isQuotiteCessibleRespectee(net, new BigDecimal("500001.00")));
        assertFalse(PaieAvanceAcompte.isQuotiteCessibleRespectee(net, new BigDecimal("750000.00")));
    }

    @Test
    @DisplayName("5. CRUD complet d'un Bulletin de Paie avec Snapshot et Lignes ventilées")
    void testCrudBulletinPaieEtLignes() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Création Bulletin
                BulletinPaie bp = new BulletinPaie();
                bp.setIdPeriodePaie(1);
                bp.setIdEmploye(1);
                bp.setReferenceBulletin("BP-TEST-2026-08-EMP001");
                bp.setMatricule("EMP001");
                bp.setNomPrenom("RAKOTO Jean");
                bp.setCinNumero("101234567890");
                bp.setCinDateDelivrance(Date.valueOf("2015-05-10"));
                bp.setCinLieuDelivrance("Antananarivo");
                bp.setNumeroCnaps("123456789");
                bp.setIntitulePoste("Développeur Senior");
                bp.setNomDepartement("Informatique");
                bp.setNomSite("Antananarivo - Siège");
                bp.setRegimeIntervenant("PERMANENT");
                bp.setCategorieProfessionnelle("CADRE");
                bp.setModeCalculPaie("MENSUEL");
                bp.setDateEmbauche(Date.valueOf("2020-01-15"));
                bp.setAncienneteMois(79);
                bp.setNbEnfantsCharge(2);
                bp.setModePaiement("VIREMENT");
                bp.setBanqueNom("BNI MADAGASCAR");
                bp.setBanqueRib("00005 00001 12345678901 25");

                bp.setHeuresNormales(new BigDecimal("173.33"));
                bp.setJoursOuvrables(new BigDecimal("22.0"));
                bp.setJoursTravailles(new BigDecimal("22.0"));
                bp.setSalaireBase(new BigDecimal("2500000.00"));
                bp.setTauxHoraire(new BigDecimal("14423.0769"));
                bp.setTotalGainsBruts(new BigDecimal("2500000.00"));

                bp.setTotalCotisationsSalariales(new BigDecimal("46014.40")); // CNaPS 1% plafonné (21 014.40) + OSTIE 1% (25 000)
                bp.setTotalCotisationsPatronales(new BigDecimal("423187.20")); // CNaPS 13% plafonné (273 187.20) + OSTIE 5% (125 000) + FMFP 1% (25 000)
                bp.setSalaireNetImposable(new BigDecimal("2453985.60"));
                bp.setBaseIrsaArrondie(new BigDecimal("2453900.00"));
                bp.setIrsaBrut(new BigDecimal("395780.00"));
                bp.setReductionEnfants(new BigDecimal("4000.00")); // 2 enfants x 2 000 Ar
                bp.setIrsaNet(new BigDecimal("391780.00"));

                bp.setTotalIndemnitesNonImposables(new BigDecimal("44000.00")); // Repas 22j x 2000 Ar
                bp.setTotalRetenuesAcomptePret(new BigDecimal("300000.00")); // Acompte
                BigDecimal netAvantArrondi = new BigDecimal("1806205.60");
                bp.setNetAvantArrondi(netAvantArrondi);
                bp.setNetAPayer(BulletinPaie.arrondirDizaineAriary(netAvantArrondi)); // 1 806 210.00 Ar
                bp.setCoutGlobalEmployeur(new BigDecimal("2967187.20"));
                bp.setStatut("CALCULE");

                bp.save(c);
                assertNotNull(bp.getIdBulletin(), "L'idBulletin généré doit être renseigné");

                // 2. Ajout de lignes ventilées
                BulletinPaieLigne l1 = new BulletinPaieLigne();
                l1.setIdBulletin(bp.getIdBulletin());
                l1.setCodeRubrique("SALAIRE_BASE");
                l1.setLibelleRubrique("Salaire de Base");
                l1.setSens("GAIN");
                l1.setCategorie("SALAIRE_BASE");
                l1.setBaseCalcul(new BigDecimal("2500000.00"));
                l1.setMontantSalarial(new BigDecimal("2500000.00"));
                l1.setOrdre(10);
                l1.save(c);
                assertNotNull(l1.getIdLigne());

                BulletinPaieLigne l2 = new BulletinPaieLigne();
                l2.setIdBulletin(bp.getIdBulletin());
                l2.setCodeRubrique("COTISATION_CNAPS_SAL");
                l2.setLibelleRubrique("Cotisation CNaPS Salariale (1%)");
                l2.setSens("RETENUE");
                l2.setCategorie("COTISATION_SALARIALE");
                l2.setBaseCalcul(new BigDecimal("2101440.00")); // Plafond 8x SME
                l2.setTauxSalarial(new BigDecimal("1.00"));
                l2.setMontantSalarial(new BigDecimal("21014.40"));
                l2.setOrdre(200);
                l2.save(c);

                // 3. Relecture
                BulletinPaie relu = BulletinPaie.trouverParReference("BP-TEST-2026-08-EMP001", c);
                assertNotNull(relu);
                assertEquals("EMP001", relu.getMatricule());
                assertEquals("RAKOTO Jean", relu.getNomPrenom());
                assertEquals("CADRE", relu.getCategorieProfessionnelle());
                assertEquals(new BigDecimal("2500000.00"), relu.getSalaireBase());
                assertEquals(new BigDecimal("1806210.00"), relu.getNetAPayer());

                List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(bp.getIdBulletin(), c);
                assertEquals(2, lignes.size());
                assertEquals("SALAIRE_BASE", lignes.get(0).getCodeRubrique());
                assertEquals("COTISATION_CNAPS_SAL", lignes.get(1).getCodeRubrique());

                // 4. Mise à jour statut
                relu.setStatut("VALIDE");
                relu.update(c);

                BulletinPaie reluValide = BulletinPaie.trouverParReference("BP-TEST-2026-08-EMP001", c);
                assertEquals("VALIDE", reluValide.getStatut());

            } finally {
                c.rollback(); // Restauration de l'état initial
            }
        }
    }

    @Test
    @DisplayName("6. Trigger d'immutabilité PostgreSQL : Blocage strict des modifications post-clôture")
    void testTriggerImmutabilitePostCloture() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Création d'un bulletin et d'une ligne au statut CALCULE
                BulletinPaie bp = new BulletinPaie();
                bp.setIdPeriodePaie(1);
                bp.setIdEmploye(1);
                bp.setReferenceBulletin("BP-TEST-CLOTURE-001");
                bp.setMatricule("EMP001");
                bp.setNomPrenom("RAKOTO Test Cloture");
                bp.setIntitulePoste("Chauffeur");
                bp.setNomSite("Toamasina - Agence");
                bp.setRegimeIntervenant("PERMANENT");
                bp.setModeCalculPaie("MENSUEL");
                bp.setSalaireBase(new BigDecimal("800000.00"));
                bp.setTauxHoraire(new BigDecimal("4615.38"));
                bp.setStatut("CALCULE");
                bp.save(c);

                BulletinPaieLigne ligne = new BulletinPaieLigne();
                ligne.setIdBulletin(bp.getIdBulletin());
                ligne.setCodeRubrique("SALAIRE_BASE");
                ligne.setLibelleRubrique("Salaire de Base");
                ligne.setSens("GAIN");
                ligne.setCategorie("SALAIRE_BASE");
                ligne.setMontantSalarial(new BigDecimal("800000.00"));
                ligne.save(c);

                // 2. Passage au statut CLOTURE (transition autorisée)
                bp.setStatut("CLOTURE");
                bp.update(c);

                // 3. Tentative de modification du bulletin clôturé -> Doit lever une exception par le trigger
                bp.setSalaireBase(new BigDecimal("999999.00"));
                SQLException exBulletin = assertThrows(SQLException.class, () -> bp.update(c),
                        "La modification d'un bulletin clôturé doit être rejetée par le trigger PostgreSQL");
                assertTrue(exBulletin.getMessage().contains("strictement immuable") || exBulletin.getMessage().contains("interdite"),
                        "Le message d'erreur doit mentionner l'immutabilité : " + exBulletin.getMessage());

            } finally {
                c.rollback(); // Rollback systématique pour ne pas polluer la base
            }
        }
    }

    @Test
    @DisplayName("7. Gestion des mouvements de trésorerie (PaieAvanceAcompte)")
    void testCrudPaieAvanceAcompte() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PaieAvanceAcompte avance = new PaieAvanceAcompte();
                avance.setIdEmploye(1);
                avance.setIdPeriodePaie(1);
                avance.setTypeMouvement("ACOMPTE_15");
                avance.setSens("RETENUE");
                avance.setMontant(new BigDecimal("250000.00"));
                avance.setDateDemande(Date.valueOf(LocalDate.now()));
                avance.setMotif("Acompte quinzaine du 15");
                avance.setModeReglement("VIREMENT");
                avance.setStatut("EN_ATTENTE");

                avance.save(c);
                assertNotNull(avance.getIdAvance());

                List<PaieAvanceAcompte> list = PaieAvanceAcompte.listerParEmployeEtPeriode(1, 1, c);
                assertFalse(list.isEmpty());

                PaieAvanceAcompte relu = list.get(list.size() - 1);
                assertEquals("ACOMPTE_15", relu.getTypeMouvement());
                assertEquals(new BigDecimal("250000.00"), relu.getMontant());

                relu.setStatut("APPROUVE");
                relu.setDateAccord(Date.valueOf(LocalDate.now()));
                relu.update(c);

                relu.delete(c);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("8. Gestion des prêts d'entreprise (PaiePret & PaiePretEcheance) et amortissement")
    void testCrudPaiePretEtEcheancier() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Création d'un prêt de 1 200 000 Ar sur 6 mois (200 000 Ar / mois)
                PaiePret pret = new PaiePret();
                pret.setIdEmploye(1);
                pret.setReferencePret("PRET-TEST-2026-001");
                pret.setMontantTotal(new BigDecimal("1200000.00"));
                pret.setMontantMensualite(new BigDecimal("200000.00"));
                pret.setNbEcheances(6);
                pret.setDateAccord(Date.valueOf("2026-08-01"));
                pret.setDateDeblocage(Date.valueOf("2026-08-05"));
                pret.setDatePremiereEcheance(Date.valueOf("2026-08-25"));
                pret.setCapitalRembourse(BigDecimal.ZERO);
                pret.setCapitalRestantDu(new BigDecimal("1200000.00"));
                pret.setStatut("EN_COURS");
                pret.setMotif("Prêt équipement personnel");

                pret.enregistrerAvecEcheancier(c);
                assertNotNull(pret.getIdPret(), "L'id du prêt doit être généré");

                // 2. Vérification de l'échéancier généré
                List<PaiePretEcheance> echeances = PaiePretEcheance.listerParPret(pret.getIdPret(), c);
                assertEquals(6, echeances.size(), "6 échéances doivent avoir été générées");

                PaiePretEcheance ech1 = echeances.get(0);
                assertEquals(1, ech1.getNumeroEcheance());
                assertEquals(new BigDecimal("200000.00"), ech1.getMontantEcheance());
                assertEquals(new BigDecimal("1000000.00"), ech1.getCapitalRestantApres());
                assertEquals("A_ECHOIR", ech1.getStatut());

                PaiePretEcheance ech6 = echeances.get(5);
                assertEquals(6, ech6.getNumeroEcheance());
                assertEquals(new BigDecimal("200000.00"), ech6.getMontantEcheance());
                assertEquals(BigDecimal.ZERO.setScale(2), ech6.getCapitalRestantApres());

                // 3. Prochaine échéance à échoir
                PaiePretEcheance prochaine = PaiePretEcheance.trouverProchaineAEchoir(pret.getIdPret(), c);
                assertNotNull(prochaine);
                assertEquals(1, prochaine.getNumeroEcheance());

                // 4. Simulation du prélèvement en paie de l'échéance 1
                prochaine.preleverEnPaie(null, 1, c);

                PaiePretEcheance reluEch1 = PaiePretEcheance.trouverParId(prochaine.getIdEcheance(), c);
                assertNotNull(reluEch1);
                assertEquals("PRELEVEE_EN_PAIE", reluEch1.getStatut());
                assertNotNull(reluEch1.getDatePrelevement());

                // 5. Vérification de l'impact sur le capital restant dû du prêt parent
                PaiePret pretRelu = PaiePret.trouverParId(pret.getIdPret(), c);
                assertNotNull(pretRelu);
                assertEquals(new BigDecimal("200000.00"), pretRelu.getCapitalRembourse());
                assertEquals(new BigDecimal("1000000.00"), pretRelu.getCapitalRestantDu());
                assertEquals("EN_COURS", pretRelu.getStatut());

                // 6. Test Contre-Audit Axe 6 : Calcul du capital restant total pour STC et solde anticipé
                BigDecimal totalRestant = PaiePret.calculerCapitalRestantTotal(1, c);
                assertEquals(new BigDecimal("1000000.00"), totalRestant, "Le capital restant total en cours doit être de 1 000 000 Ar");

                // Clôture anticipée lors du Solde de Tout Compte (STC)
                BigDecimal totalSoldeStc = PaiePret.solderPretsPourStc(1, null, c);
                assertEquals(new BigDecimal("1000000.00"), totalSoldeStc, "Le montant imputé sur le STC doit être de 1 000 000 Ar");

                // Vérifier que le prêt est désormais SOLDE et capital restant = 0
                PaiePret pretSolde = PaiePret.trouverParId(pret.getIdPret(), c);
                assertNotNull(pretSolde);
                assertEquals(new BigDecimal("1200000.00"), pretSolde.getCapitalRembourse());
                assertEquals(BigDecimal.ZERO.setScale(2), pretSolde.getCapitalRestantDu());
                assertEquals("SOLDE", pretSolde.getStatut(), "Le prêt doit être marqué SOLDE une fois soldé par le STC");

                // Vérifier que les échéances 2 à 6 sont passées en REMBOURSEE_ANTICIPEE
                List<PaiePretEcheance> echsApresStc = PaiePretEcheance.listerParPret(pret.getIdPret(), c);
                for (int i = 1; i < echsApresStc.size(); i++) {
                    PaiePretEcheance echI = echsApresStc.get(i);
                    assertEquals("REMBOURSEE_ANTICIPEE", echI.getStatut(), "L'échéance résiduelle doit être REMBOURSEE_ANTICIPEE");
                    assertNull(echI.getIdBulletin());
                    assertEquals(BigDecimal.ZERO.setScale(2), echI.getMontantRestantEcheance());
                }

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("9. Contre-audit : Réouverture exceptionnelle tracée d'un bulletin clôturé (Trigger & Audit)")
    void testReouvertureExceptionnelleBulletinCloture() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Création d'un bulletin calculé puis clôturé
                BulletinPaie bp = new BulletinPaie();
                bp.setIdPeriodePaie(1);
                bp.setIdEmploye(1);
                bp.setReferenceBulletin("BP-TEST-REOUVERTURE-001");
                bp.setMatricule("EMP001");
                bp.setNomPrenom("RAKOTO Test Reouverture");
                bp.setIntitulePoste("Mécanicien");
                bp.setNomSite("Toamasina - Agence");
                bp.setRegimeIntervenant("PERMANENT");
                bp.setModeCalculPaie("MENSUEL");
                bp.setSalaireBase(new BigDecimal("900000.00"));
                bp.setTauxHoraire(new BigDecimal("5192.31"));
                bp.setStatut("CALCULE");
                bp.save(c);

                bp.setStatut("CLOTURE");
                bp.update(c);

                // 2. Rejet si motif trop court ou vide (validation applicative Java)
                assertThrows(IllegalArgumentException.class, () -> bp.rouvrirBulletin("bad", 1, c),
                        "La réouverture doit exiger un motif explicite d'au moins 5 caractères");

                // 3. Rejet si tentative de modification directe en SQL d'un bulletin clôturé (Trigger PostgreSQL)
                java.sql.Savepoint sp = c.setSavepoint();
                bp.setSalaireBase(new BigDecimal("950000.00"));
                SQLException exTrigger = assertThrows(SQLException.class, () -> bp.update(c),
                        "Une modification directe sur un bulletin clôturé doit être rejetée par le trigger");
                assertTrue(exTrigger.getMessage().contains("clôturé et strictement immuable") || exTrigger.getMessage().contains("interdite"),
                        "Le trigger doit refuser la modification : " + exTrigger.getMessage());
                c.rollback(sp); // Rétablir la transaction après l'erreur provoquée

                // 4. Réouverture d'urgence tracée avec motif valide
                String motif = "Correction suite à régularisation d'heures supplémentaires validée DAF";
                bp.rouvrirBulletin(motif, 1, c);

                // Vérification en base
                BulletinPaie relu = BulletinPaie.trouverParReference("BP-TEST-REOUVERTURE-001", c);
                assertNotNull(relu);
                assertEquals("ROUVERT", relu.getStatut());
                assertEquals(motif, relu.getMotifReouverture());
                assertEquals(1, relu.getRouvertPar());
                assertNotNull(relu.getDateReouverture());

                // 5. Vérification qu'un bulletin clôturé ne peut JAMAIS être supprimé (DELETE bloqué par trigger)
                bp.setStatut("CLOTURE");
                bp.update(c);
                java.sql.Savepoint spDelete = c.setSavepoint();
                SQLException exDelete = assertThrows(SQLException.class, () -> {
                    try (PreparedStatement ps = c.prepareStatement("DELETE FROM bulletin_paie WHERE id_bulletin = ?")) {
                        ps.setInt(1, bp.getIdBulletin());
                        ps.executeUpdate();
                    }
                }, "La suppression d'un bulletin clôturé doit être formellement rejetée par le trigger");
                assertTrue(exDelete.getMessage().contains("Suppression impossible d'un bulletin clôturé") || exDelete.getMessage().contains("interdite"));
                c.rollback(spDelete);

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("10. Contre-audit : Prélèvement partiel d'échéance de prêt (quotité cessible) et mise à jour du reliquat")
    void testPrelevementPartielEcheancePret() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Création d'un prêt de 600 000 Ar en 3 mensualités de 200 000 Ar
                PaiePret pret = new PaiePret();
                pret.setIdEmploye(1);
                pret.setReferencePret("PRET-TEST-PARTIEL-001");
                pret.setMontantTotal(new BigDecimal("600000.00"));
                pret.setMontantMensualite(new BigDecimal("200000.00"));
                pret.setNbEcheances(3);
                pret.setDateAccord(Date.valueOf("2026-09-01"));
                pret.setDateDeblocage(Date.valueOf("2026-09-05"));
                pret.setDatePremiereEcheance(Date.valueOf("2026-09-25"));
                pret.setCapitalRembourse(BigDecimal.ZERO);
                pret.setCapitalRestantDu(new BigDecimal("600000.00"));
                pret.setStatut("EN_COURS");
                pret.setMotif("Prêt secours social");
                pret.enregistrerAvecEcheancier(c);

                List<PaiePretEcheance> echeances = PaiePretEcheance.listerParPret(pret.getIdPret(), c);
                assertEquals(3, echeances.size());

                PaiePretEcheance ech1 = echeances.get(0);
                assertEquals(1, ech1.getNumeroEcheance());
                assertEquals(new BigDecimal("200000.00"), ech1.getMontantEcheance());
                assertEquals("A_ECHOIR", ech1.getStatut());

                // 2. Prélèvement partiel : salaire net insuffisant (quotité 1/3 légale Art. 68), prélèvement de 120 000 Ar au lieu de 200 000 Ar
                ech1.preleverEnPaie(new BigDecimal("120000.00"), null, 1, c);

                PaiePretEcheance ech1Relu = PaiePretEcheance.trouverParId(ech1.getIdEcheance(), c);
                assertNotNull(ech1Relu);
                assertEquals("PRELEVEE_PARTIELLEMENT", ech1Relu.getStatut());
                assertEquals(new BigDecimal("120000.00"), ech1Relu.getMontantPreleve());
                assertEquals(new BigDecimal("80000.00"), ech1Relu.getMontantRestantEcheance());

                // Vérification du prêt parent : capital restant dû déduit de 120 000 Ar (reste 480 000 Ar)
                PaiePret pretRelu = PaiePret.trouverParId(pret.getIdPret(), c);
                assertNotNull(pretRelu);
                assertEquals(new BigDecimal("120000.00"), pretRelu.getCapitalRembourse());
                assertEquals(new BigDecimal("480000.00"), pretRelu.getCapitalRestantDu());
                assertEquals("EN_COURS", pretRelu.getStatut());

                // 3. Prélèvement du reliquat (80 000 Ar) sur la période suivante
                ech1Relu.preleverEnPaie(new BigDecimal("80000.00"), null, 1, c);
                PaiePretEcheance ech1Final = PaiePretEcheance.trouverParId(ech1.getIdEcheance(), c);
                assertNotNull(ech1Final);
                assertEquals("PRELEVEE_EN_PAIE", ech1Final.getStatut());
                assertEquals(new BigDecimal("200000.00"), ech1Final.getMontantPreleve());
                assertEquals(BigDecimal.ZERO.setScale(2), ech1Final.getMontantRestantEcheance());

                // Vérification finale sur le prêt : capital remboursé = 200 000 Ar, capital restant = 400 000 Ar
                PaiePret pretApresSoldeEch1 = PaiePret.trouverParId(pret.getIdPret(), c);
                assertNotNull(pretApresSoldeEch1);
                assertEquals(new BigDecimal("200000.00"), pretApresSoldeEch1.getCapitalRembourse());
                assertEquals(new BigDecimal("400000.00"), pretApresSoldeEch1.getCapitalRestantDu());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("13. Art. 68 Code du Travail : Distinction du dixième (10%) avance employeur vs tiers (33.33%) quotité cessible")
    void testArt68AvanceEmployeurVsQuotiteCessible() {
        BigDecimal netDisponible = new BigDecimal("1000000.00"); // 1 000 000 Ar

        // 1. Plafond avance employeur (1/10e = 100 000 Ar)
        BigDecimal maxAvance = PaieAvanceAcompte.calculerAvanceEmployeurMax(netDisponible);
        assertEquals(new BigDecimal("100000.00"), maxAvance);

        assertTrue(PaieAvanceAcompte.isAvanceEmployeurRespectee(netDisponible, new BigDecimal("100000.00")));
        assertTrue(PaieAvanceAcompte.isAvanceEmployeurRespectee(netDisponible, new BigDecimal("50000.00")));
        assertFalse(PaieAvanceAcompte.isAvanceEmployeurRespectee(netDisponible, new BigDecimal("100001.00")));
        assertFalse(PaieAvanceAcompte.isAvanceEmployeurRespectee(netDisponible, new BigDecimal("250000.00")));

        // 2. Plafond quotité cessible générale (1/3 = 333 333.33 Ar)
        BigDecimal maxQuotite = PaieAvanceAcompte.calculerQuotiteCessibleMax(netDisponible);
        assertEquals(new BigDecimal("333333.33"), maxQuotite);

        // Une retenue de 250 000 Ar respecte la quotité cessible générale mais violerait le 1/10e avance employeur
        assertTrue(PaieAvanceAcompte.isQuotiteCessibleRespectee(netDisponible, new BigDecimal("250000.00")));
        assertFalse(PaieAvanceAcompte.isAvanceEmployeurRespectee(netDisponible, new BigDecimal("250000.00")));

        // Cas limites nuls
        assertFalse(PaieAvanceAcompte.isAvanceEmployeurRespectee(BigDecimal.ZERO, new BigDecimal("50000.00")));
        assertTrue(PaieAvanceAcompte.isAvanceEmployeurRespectee(netDisponible, BigDecimal.ZERO));
    }
}
