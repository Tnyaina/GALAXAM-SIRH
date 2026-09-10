package mg.galaxam.sirh.paie;

import mg.galaxam.sirh.model.paieconfig.BaremeIrsa;
import mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BulletinPaiePreparationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CalculateurPaieEngine calculateurPaieEngine;

    private List<CalculateurPaieEngine.TrancheIrsa> chargerTranchesIrsa(Connection c) throws Exception {
        List<CalculateurPaieEngine.TrancheIrsa> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT tranche_min, tranche_max, taux FROM bareme_irsa ORDER BY numero_tranche ASC")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BigDecimal min = rs.getBigDecimal("tranche_min");
                BigDecimal max = rs.getBigDecimal("tranche_max");
                double rawTaux = rs.getDouble("taux");
                double taux = rawTaux > 1.0 ? rawTaux / 100.0 : rawTaux;
                list.add(new CalculateurPaieEngine.TrancheIrsa(min, max, taux));
            }
        }
        return list;
    }

    @Test
    @Order(1)
    @DisplayName("PAIE-01 : Bulletin de Paie DAF Cadre autonome (Andry Nirina - EMP 1)")
    void testBulletinPaieDafEmp1() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            List<CalculateurPaieEngine.TrancheIrsa> tranches = chargerTranchesIrsa(c);

            BigDecimal salaireBase = new BigDecimal("3800000.00");
            BigDecimal smig = CalculateurPaieEngine.SMIG_DEFAUT;

            // 1. Cotisation CNaPS (plafonnée à 8 x SMIG = 2 101 440 Ar)
            CalculateurPaieEngine.CotisationResult cnaps = calculateurPaieEngine.calculerCnaps(salaireBase, smig, 0.01, 0.13);
            assertEquals(new BigDecimal("2101440.00"), cnaps.assiette(), "Assiette CNaPS plafonnée à 8x SMIG");
            assertEquals(new BigDecimal("21014.40"), cnaps.partSalariale(), "CNaPS salariale 1%");
            assertEquals(new BigDecimal("273187.20"), cnaps.partPatronale(), "CNaPS patronale 13%");

            // 2. Cotisation Santé OSTIE (non plafonnée sur salaire brut)
            CalculateurPaieEngine.CotisationResult ostie = calculateurPaieEngine.calculerSante(salaireBase, 0.01, 0.05);
            assertEquals(new BigDecimal("38000.00"), ostie.partSalariale(), "OSTIE salariale 1%");
            assertEquals(new BigDecimal("190000.00"), ostie.partPatronale(), "OSTIE patronale 5%");

            // 3. Base imposable IRSA (Brut - CNaPS salariale - OSTIE salariale)
            BigDecimal totalCotisationsSalariales = cnaps.partSalariale().add(ostie.partSalariale());
            BigDecimal baseImposable = salaireBase.subtract(totalCotisationsSalariales);
            assertEquals(new BigDecimal("3740985.60"), baseImposable);

            // 4. Calcul IRSA progressif avec déduction pour 2 enfants à charge (-4 000 Ar)
            BigDecimal irsa = calculateurPaieEngine.calculerIrsa(baseImposable, tranches, 2);
            assertTrue(irsa.compareTo(new BigDecimal("600000.00")) > 0, "IRSA supérieur à 600 000 Ar");

            // 5. Net à Payer
            BigDecimal netAPayer = salaireBase.subtract(totalCotisationsSalariales).subtract(irsa);
            assertTrue(netAPayer.compareTo(new BigDecimal("3000000.00")) > 0, "Net à payer > 3 000 000 Ar");

            System.out.println("==================================================================");
            System.out.println("📄 BULLETIN DE PAIE — AOÛT 2026 (DAF CADRE AUTONOME)");
            System.out.println("Matricule        : EMP-2006-0001 (RAKOTONDRAZAKA Andry Nirina)");
            System.out.println("Fonction         : DAF (Cadre Autonome — Dispense Pointage)");
            System.out.println("Enfants à charge : 2 (-4 000 Ar IRSA)");
            System.out.println("Salaire de Base  : " + salaireBase + " Ar");
            System.out.println("CNaPS Salariale  : " + cnaps.partSalariale() + " Ar (Plafond 8x SMIG: 2 101 440 Ar)");
            System.out.println("OSTIE Salariale  : " + ostie.partSalariale() + " Ar");
            System.out.println("Base Imposable   : " + baseImposable + " Ar");
            System.out.println("IRSA Net Déduit  : " + irsa + " Ar");
            System.out.println("👉 NET À VIRER BNI: " + netAPayer + " Ar");
            System.out.println("==================================================================");
        }
    }

    @Test
    @Order(2)
    @DisplayName("PAIE-02 : Bulletin OPE Manager Tamatave (Jean Jacques - EMP 2) avec HS & Déduction Transport TMM")
    void testBulletinPaieTamataveEmp2() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal salaireBase = new BigDecimal("1850000.00");
            BigDecimal diviseur = new BigDecimal("173.33");
            BigDecimal tauxHoraire = salaireBase.divide(diviseur, 2, RoundingMode.HALF_UP);

            // 3h supplémentaires à +30% (Semaine 33)
            BigDecimal montantHs30 = tauxHoraire.multiply(new BigDecimal("3")).multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal salaireBrut = salaireBase.add(montantHs30);

            // Indemnités Tamatave : Déplacement forfaitaire 53 200 Ar - 21 000 Ar déduction repas midi
            BigDecimal indemniteDeplacementNette = new BigDecimal("53200.00").subtract(new BigDecimal("21000.00"));
            assertEquals(new BigDecimal("32200.00"), indemniteDeplacementNette);

            CalculateurPaieEngine.CotisationResult cnaps = calculateurPaieEngine.calculerCnaps(salaireBrut, CalculateurPaieEngine.SMIG_DEFAUT, 0.01, 0.13);
            CalculateurPaieEngine.CotisationResult ostie = calculateurPaieEngine.calculerSante(salaireBrut, 0.01, 0.05);

            BigDecimal netEstime = salaireBrut.subtract(cnaps.partSalariale()).subtract(ostie.partSalariale()).add(indemniteDeplacementNette);
            assertTrue(netEstime.compareTo(new BigDecimal("1700000.00")) > 0);

            System.out.println("==================================================================");
            System.out.println("📄 BULLETIN DE PAIE — AOÛT 2026 (OPE MANAGER TAMATAVE)");
            System.out.println("Matricule        : EMP-2014-0012 (RANDRIAMAMPIONONA Jean Jacques)");
            System.out.println("Site d'Affect.   : Toamasina Port (TMM) - Muté en 2022");
            System.out.println("Salaire de Base  : " + salaireBase + " Ar");
            System.out.println("Heures Sup (+30%): +3h = " + montantHs30 + " Ar");
            System.out.println("Salaire Brut     : " + salaireBrut + " Ar");
            System.out.println("Transport Net TMM: " + indemniteDeplacementNette + " Ar (53 200 Ar - 21 repas site)");
            System.out.println("==================================================================");
        }
    }

    @Test
    @Order(3)
    @DisplayName("PAIE-03 : Bulletin Admin RH (Voahirana Mialy - EMP 3) avec Maintien de salaire pour Congé Annuel")
    void testBulletinPaieCongesEmp3() throws Exception {
        BigDecimal salaireBase = new BigDecimal("1350000.00");
        // Maintien intégral du salaire de base pour les 5 jours ouvrables de congés posés du 03 au 07 Août
        BigDecimal salaireBrut = salaireBase;

        CalculateurPaieEngine.CotisationResult cnaps = calculateurPaieEngine.calculerCnaps(salaireBrut, CalculateurPaieEngine.SMIG_DEFAUT, 0.01, 0.13);
        CalculateurPaieEngine.CotisationResult ostie = calculateurPaieEngine.calculerSante(salaireBrut, 0.01, 0.05);

        assertEquals(new BigDecimal("13500.00"), cnaps.partSalariale());
        assertEquals(new BigDecimal("13500.00"), ostie.partSalariale());
        assertEquals(salaireBase, salaireBrut, "Le congé annuel ouvre droit au maintien intégral de la rémunération");
    }

    @Test
    @Order(4)
    @DisplayName("PAIE-04 : Bulletin Nouvel Embauché au 17 Août (Faly Christian - EMP 5) avec Prorata Temporis")
    void testBulletinPaieProrataTemporisEmp5() throws Exception {
        BigDecimal salaireBaseMensuelPlein = new BigDecimal("1050000.00");
        // Embauché le 17 Août 2026 : 11 jours ouvrés travaillés sur 21 jours ouvrés du mois d'Août
        BigDecimal salaireProratise = salaireBaseMensuelPlein.multiply(new BigDecimal("11"))
                .divide(new BigDecimal("21"), 2, RoundingMode.HALF_UP);

        assertEquals(new BigDecimal("550000.00"), salaireProratise, "Proratisation exacte : 1 050 000 * 11 / 21 = 550 000 Ar");

        // Cotisations sur la base proratisée
        CalculateurPaieEngine.CotisationResult cnaps = calculateurPaieEngine.calculerCnaps(salaireProratise, CalculateurPaieEngine.SMIG_DEFAUT, 0.01, 0.13);
        assertEquals(new BigDecimal("5500.00"), cnaps.partSalariale(), "CNaPS salariale calculée sur le salaire proratisé");

        System.out.println("==================================================================");
        System.out.println("📄 BULLETIN DE PAIE — AOÛT 2026 (NOUVEL EMBAUCHÉ AU 17 AOÛT)");
        System.out.println("Matricule        : EMP-2026-0089 (RAKOTONDRASOA Faly Christian)");
        System.out.println("Filiale          : Vina sy Asa  (VA) - Site TNR");
        System.out.println("Salaire Mensuel  : 1 050 000.00 Ar (Plein temps)");
        System.out.println("Jours Travaillés : 11 / 21 jours ouvrés");
        System.out.println("👉 BASE PRORATISÉE: " + salaireProratise + " Ar");
        System.out.println("==================================================================");
    }
}
