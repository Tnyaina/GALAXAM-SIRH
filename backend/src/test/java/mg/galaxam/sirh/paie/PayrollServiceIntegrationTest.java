package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import mg.galaxam.sirh.model.paie.PaieHonorairePrestation;
import mg.galaxam.sirh.model.paie.PaiePret;
import mg.galaxam.sirh.model.paie.PaiePretEcheance;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("PayrollServiceIntegrationTest — Validation de l'Orchestration et Persistance de Paie")
class PayrollServiceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollService payrollService;

    @Test
    @DisplayName("1. Calcul individuel d'un bulletin en mode simulation pour le DAF (EMP 1 - TNR - 3 800 000 Ar)")
    void testCalculIndividuelSimulationDaf() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                BulletinPaieComplet complet = payrollService.calculerBulletinEmploye(1, 1, true, 1, c);
                assertNotNull(complet);
                BulletinPaie bp = complet.getBulletin();
                assertNotNull(bp.getIdBulletin());

                assertEquals("EMP-2006-0001", bp.getMatricule());
                assertEquals("CALCULE", bp.getStatut());
                assertEquals(new BigDecimal("3800000.00"), bp.getSalaireBase());
                assertEquals(new BigDecimal("3800000.00"), bp.getTotalGainsBruts());

                // Cotisation CNaPS salariale (1% plafonnée à 8x SME = 21 014.40 Ar)
                assertEquals(new BigDecimal("21014.40"), complet.getMontantLigneSalariale("COTISATION_CNAPS_SAL"));

                // Cotisation OSTIE salariale (1% plafonnée à 8x SME = 21 014.40 Ar - Contre-Audit Axe 5)
                assertEquals(new BigDecimal("21014.40"), complet.getMontantLigneSalariale("COTISATION_OSTIE_SAL"));

                // FMFP patronale (1% sur 3 800 000 = 38 000 Ar)
                assertEquals(new BigDecimal("38000.00"), complet.getMontantLignePatronale("COTISATION_FMFP_PAT"));

                // Net imposable = 3 800 000 - 21 014.40 - 21 014.40 = 3 757 971.20 Ar
                assertEquals(new BigDecimal("3757971.20"), bp.getSalaireNetImposable());

                // Net à payer positif et arrondi à la dizaine
                assertTrue(bp.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);
                long netLong = bp.getNetAPayer().setScale(0, RoundingMode.HALF_UP).longValue();
                assertEquals(0, netLong % 10, "Le net à payer doit être un multiple exact de 10 Ariary");

                // Vérification de la persistance en base
                List<BulletinPaieLigne> lignesBdd = BulletinPaieLigne.listerParBulletin(bp.getIdBulletin(), c);
                assertFalse(lignesBdd.isEmpty());
                assertTrue(lignesBdd.size() >= 8, "Le bulletin doit comporter au moins 8 lignes détaillées");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("2. Idempotence du calcul en simulation (recalcul sans création de doublon)")
    void testIdempotenceRecalculSimulation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Premier calcul
                BulletinPaieComplet c1 = payrollService.calculerBulletinEmploye(1, 1, true, 1, c);
                Integer idBulletin1 = c1.getBulletin().getIdBulletin();
                assertNotNull(idBulletin1);

                // Second calcul pour le même employé et la même période
                BulletinPaieComplet c2 = payrollService.calculerBulletinEmploye(1, 1, true, 1, c);
                Integer idBulletin2 = c2.getBulletin().getIdBulletin();

                assertEquals(idBulletin1, idBulletin2, "Le recalcul doit réutiliser le même enregistrement bulletin");

                // Vérifier qu'il n'y a pas de doublons dans bulletin_paie
                List<BulletinPaie> tous = BulletinPaie.listerParPeriode(1, c);
                long countEmp1 = tous.stream().filter(b -> b.getIdEmploye().equals(1)).count();
                assertEquals(1, countEmp1, "Un seul bulletin doit exister par couple (employe, periode)");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("3. Calcul collectif de paie pour les 5 salariés de la période 1 (Août 2026)")
    void testCalculCollectifPeriode() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                PayrollBatchResult batch = payrollService.calculerPaiePeriode(1, true, 1, c);
                assertNotNull(batch);

                assertTrue(batch.getNbBulletinsCalcules() >= 5, "Au moins 5 salariés actifs doivent avoir été calculés");
                assertTrue(batch.getTotalMasseSalarialeBrute().compareTo(BigDecimal.ZERO) > 0);
                assertTrue(batch.getTotalNetAPayer().compareTo(BigDecimal.ZERO) > 0);
                assertTrue(batch.getTotalChargesPatronales().compareTo(BigDecimal.ZERO) > 0);
                assertTrue(batch.getTotalCoutGlobal().compareTo(batch.getTotalMasseSalarialeBrute()) > 0);

                assertTrue(batch.getReferencesBulletins().size() >= 5);

                // Vérification en base
                List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(1, c);
                assertTrue(bulletins.size() >= 5);

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("4. Cycle complet avec acompte et prêt : simulation vs validation réelle")
    void testCycleAcomptePretSimulationVsValidation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Création d'un acompte du 15 de 200 000 Ar pour l'employé 3
                PaieAvanceAcompte avance = new PaieAvanceAcompte();
                avance.setIdEmploye(3);
                avance.setIdPeriodePaie(1);
                avance.setTypeMouvement("ACOMPTE_15");
                avance.setSens("RETENUE");
                avance.setMontant(new BigDecimal("200000.00"));
                avance.setDateDemande(Date.valueOf("2026-08-15"));
                avance.setMotif("Acompte quinzaine");
                avance.setStatut("APPROUVE");
                avance.save(c);

                // 2. Création d'un prêt de 600 000 Ar en 3 mensualités de 200 000 Ar
                PaiePret pret = new PaiePret();
                pret.setIdEmploye(3);
                pret.setReferencePret("PRET-INT-003");
                pret.setMontantTotal(new BigDecimal("600000.00"));
                pret.setMontantMensualite(new BigDecimal("200000.00"));
                pret.setNbEcheances(3);
                pret.setDateAccord(Date.valueOf("2026-08-01"));
                pret.setDateDeblocage(Date.valueOf("2026-08-05"));
                pret.setDatePremiereEcheance(Date.valueOf("2026-08-25"));
                pret.setCapitalRembourse(BigDecimal.ZERO);
                pret.setCapitalRestantDu(new BigDecimal("600000.00"));
                pret.setStatut("EN_COURS");
                pret.setMotif("Prêt personnel");
                pret.enregistrerAvecEcheancier(c);

                // 3. Calcul en mode SIMULATION (simulation = true)
                BulletinPaieComplet simu = payrollService.calculerBulletinEmploye(3, 1, true, 1, c);
                assertNotNull(simu);
                assertEquals("CALCULE", simu.getBulletin().getStatut());

                // Vérifier que l'acompte est toujours en APPROUVE en base (idempotence)
                PaieAvanceAcompte acompteApresSimu = PaieAvanceAcompte.trouverParId(avance.getIdAvance(), c);
                assertEquals("APPROUVE", acompteApresSimu.getStatut());

                // Vérifier que l'échéance de prêt est toujours A_ECHOIR
                PaiePretEcheance ech1ApresSimu = PaiePretEcheance.trouverProchaineAEchoir(pret.getIdPret(), c);
                assertNotNull(ech1ApresSimu);
                assertEquals("A_ECHOIR", ech1ApresSimu.getStatut());

                // 4. Calcul en mode VALIDATION RÉELLE (simulation = false)
                BulletinPaieComplet valide = payrollService.calculerBulletinEmploye(3, 1, false, 1, c);
                assertNotNull(valide);
                assertEquals("VALIDE", valide.getBulletin().getStatut());

                // Vérifier que l'acompte est passé à IMPUTE_PAIE
                PaieAvanceAcompte acompteApresValidation = PaieAvanceAcompte.trouverParId(avance.getIdAvance(), c);
                assertEquals("IMPUTE_PAIE", acompteApresValidation.getStatut());
                assertEquals(valide.getBulletin().getIdBulletin(), acompteApresValidation.getIdBulletin());

                // Vérifier que l'échéance de prêt a été débitée
                PaiePretEcheance ech1ApresValidation = PaiePretEcheance.trouverParId(ech1ApresSimu.getIdEcheance(), c);
                assertTrue("PRELEVEE_EN_PAIE".equals(ech1ApresValidation.getStatut())
                        || "PRELEVEE_PARTIELLEMENT".equals(ech1ApresValidation.getStatut()));

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("5. Clôture de période de paie : passage à CLOTURE et verrou d'immutabilité")
    void testCloturePeriodeEtVerrouImmutabilite() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Calcul et validation
                payrollService.calculerPaiePeriode(1, false, 1, c);

                // 2. Clôture formelle
                payrollService.cloturerPaiePeriode(1, 1, c);

                // Vérification des statuts
                List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(1, c);
                for (BulletinPaie b : bulletins) {
                    assertEquals("CLOTURE", b.getStatut());
                    assertNotNull(b.getDateCloture());
                }

                // 3. Tentative de recalcul d'un bulletin clôturé -> Rejet
                IllegalStateException exRecalcul = assertThrows(IllegalStateException.class, () ->
                        payrollService.calculerBulletinEmploye(1, 1, true, 1, c));
                assertTrue(exRecalcul.getMessage().contains("clôturé"));

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("6. Contre-Audit Axe 1 : Isolement des honoraires consultants (paie_honoraire_prestation)")
    void testIsolementHonorairesConsultants() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Création d'un bordereau d'honoraire pour un consultant
                PaieHonorairePrestation hono = new PaieHonorairePrestation();
                hono.setIdPeriodePaie(1);
                hono.setIdIntervenant(1);
                hono.setNomComplet("Dr RAZAFY Hery (Consultant IT)");
                hono.setCinOuNif("101999888777");
                hono.setIntitulePrestation("Audit Cybersécurité et Architecture Cloud");
                hono.setMontantBrut(new BigDecimal("2500000.00"));
                hono.setTauxIsi(new BigDecimal("5.00"));
                hono.setDatePrestation(Date.valueOf("2026-08-31"));
                hono.save(c);

                assertNotNull(hono.getIdPrestation());
                // Retenue ISI 5% = 2 500 000 x 5% = 125 000 Ar
                assertEquals(new BigDecimal("125000.00"), hono.getMontantRetenueIsi());
                // Net payé = 2 500 000 - 125 000 = 2 375 000 Ar
                assertEquals(new BigDecimal("2375000.00"), hono.getMontantNetPaye());

                // Vérification de la persistance et listing par période
                List<PaieHonorairePrestation> liste = PaieHonorairePrestation.listerParPeriode(1, c);
                assertFalse(liste.isEmpty());
                assertTrue(liste.stream().anyMatch(h -> h.getNomComplet().contains("RAZAFY")));

            } finally {
                c.rollback();
            }
        }
    }
}
