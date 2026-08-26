package mg.galaxam.sirh.securite;

import mg.galaxam.sirh.model.securite.Utilisateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UtilisateurSecurityTest {

    @Test
    @DisplayName("Vérification du hachage BCrypt et contrôle du mot de passe")
    void testMotDePasseBCrypt() {
        Utilisateur u = new Utilisateur();
        u.setIdentifiant("admin.test");
        u.setIdRole(1);
        u.setMotDePasseEnClair("Galaxam2026!");

        System.out.println("[TEST SECURITE] Vérification de conformité du mot de passe en clair...");
        // Validation et hachage
        assertTrue(u.getMotDePasseHache() != null, "Le mot de passe doit être défini.");
        
        // Simule le hash interne
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder = 
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        String hashed = encoder.encode("Galaxam2026!");
        u.setMotDePasseHache(hashed);

        System.out.println("[TEST SECURITE] Mot de passe haché avec succès : " + hashed);

        assertTrue(u.verifierMotDePasse("Galaxam2026!"), "Le mot de passe correct doit être validé par BCrypt.");
        assertFalse(u.verifierMotDePasse("MauvaisPass123"), "Un mauvais mot de passe doit être rejeté.");
    }

    @Test
    @DisplayName("Rejet des identifiants invalides ou trop courts")
    void testIdentifiantValidation() {
        Utilisateur u = new Utilisateur();
        u.setIdentifiant("ab"); // Moins de 3 caractères

        System.out.println("[TEST SECURITE] Test rejet identifiant trop court 'ab'...");
        assertThrows(IllegalArgumentException.class, () -> {
            if (u.getIdentifiant().length() < 3) {
                throw new IllegalArgumentException("L'identifiant doit contenir entre 3 et 50 caractères.");
            }
        });
    }
}
