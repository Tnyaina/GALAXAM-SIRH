package mg.galaxam.sirh.employe;

import java.sql.Date;
import java.time.LocalDate;
import mg.galaxam.sirh.model.employe.EmployeEnfant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmployeEnfant — Tests unitaires de la règle fiscale des 21 ans / déduction IRSA (CGI Art. 01.03.14)")
class EmployeEnfantTest {

    private final LocalDate datePaie = LocalDate.of(2026, 5, 31);

    @Nested
    @DisplayName("1. Règle des enfants mineurs (< 21 ans)")
    class MineursTest {

        @Test
        @DisplayName("Enfant de 10 ans à charge : Éligible d'office")
        void enfantJeuneEligible() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setNom("RAZAFY");
            enf.setPrenom("Soa");
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2016, 5, 15))); // 10 ans
            enf.setACharge(true);

            assertEquals(10, enf.getAge(datePaie));
            assertTrue(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Enfant de 20 ans et 11 mois à charge : Éligible")
        void enfant20AnsEligible() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2005, 6, 1))); // Aura 21 ans le 01/06/2026
            enf.setACharge(true);

            assertEquals(20, enf.getAge(datePaie));
            assertTrue(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Enfant de 10 ans mais PAS à charge : Non éligible")
        void enfantNonAChargeNonEligible() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2016, 5, 15)));
            enf.setACharge(false);

            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }
    }

    @Nested
    @DisplayName("2. Enfant atteignant ou dépassant 21 ans non-étudiant")
    class VingtUnAnsNonEtudiantTest {

        @Test
        @DisplayName("Enfant ayant eu 21 ans avant la date de paie : Exclu de l'IRSA")
        void enfant21AnsNonEtudiantExclu() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2005, 5, 10))); // 21 ans révolus le 10/05/2026
            enf.setACharge(true);
            enf.setEstEtudiant(false);

            assertEquals(21, enf.getAge(datePaie));
            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Enfant de 22 ans non étudiant : Exclu")
        void enfant22AnsExclu() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2004, 1, 1))); // 22 ans
            enf.setACharge(true);
            enf.setEstEtudiant(false);

            assertEquals(22, enf.getAge(datePaie));
            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }
    }

    @Nested
    @DisplayName("3. Règle étudiant majeur (21 à moins de 25 ans)")
    class EtudiantMajeurTest {

        @Test
        @DisplayName("Étudiant de 22 ans avec certificat valide : Éligible")
        void etudiant22AnsAvecCertificatEligible() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2004, 1, 1))); // 22 ans
            enf.setACharge(true);
            enf.setEstEtudiant(true);
            enf.setCertificatFourni(true);
            enf.setDateFinScolarite(Date.valueOf(LocalDate.of(2026, 6, 30))); // Certificat valide jusqu'en juin 2026

            assertTrue(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Étudiant de 22 ans mais SANS certificat fourni : Exclu")
        void etudiantSansCertificatExclu() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2004, 1, 1)));
            enf.setACharge(true);
            enf.setEstEtudiant(true);
            enf.setCertificatFourni(false); // Pas de certificat
            enf.setDateFinScolarite(Date.valueOf(LocalDate.of(2026, 6, 30)));

            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Étudiant de 22 ans dont le certificat est expiré : Exclu")
        void etudiantCertificatExpireExclu() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2004, 1, 1)));
            enf.setACharge(true);
            enf.setEstEtudiant(true);
            enf.setCertificatFourni(true);
            enf.setDateFinScolarite(Date.valueOf(LocalDate.of(2026, 4, 30))); // Expiré en avril (paie de mai)

            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Étudiant ayant atteint 25 ans révolus : Exclu de plein droit")
        void etudiant25AnsRevolusExclu() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(2001, 5, 1))); // 25 ans révolus le 01/05/2026
            enf.setACharge(true);
            enf.setEstEtudiant(true);
            enf.setCertificatFourni(true);
            enf.setDateFinScolarite(Date.valueOf(LocalDate.of(2026, 12, 31)));

            assertEquals(25, enf.getAge(datePaie));
            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }
    }

    @Nested
    @DisplayName("4. Enfant en situation de handicap (sans limite d'âge)")
    class HandicapTest {

        @Test
        @DisplayName("Enfant handicapé de 28 ans à charge : Éligible sans limite d'âge")
        void enfantHandicapeAdulteEligible() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(1998, 1, 1))); // 28 ans
            enf.setACharge(true);
            enf.setEstHandicape(true);

            assertEquals(28, enf.getAge(datePaie));
            assertTrue(enf.isEligibleReductionIrsa(datePaie));
        }

        @Test
        @DisplayName("Enfant handicapé non à charge : Exclu")
        void enfantHandicapeNonAChargeExclu() {
            EmployeEnfant enf = new EmployeEnfant();
            enf.setDateNaissance(Date.valueOf(LocalDate.of(1998, 1, 1)));
            enf.setACharge(false);
            enf.setEstHandicape(true);

            assertFalse(enf.isEligibleReductionIrsa(datePaie));
        }
    }
}
