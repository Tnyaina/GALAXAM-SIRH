package mg.galaxam.sirh.contrat.domain;


import java.sql.Date;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest.DesactivationRequest;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import org.springframework.stereotype.Component;

/**
 * Moteur de validation métier des salariés et dossiers RH.
 * Encapsule 100% des règles légales (Code du Travail Malagasy) et de cohérence RH.
 */
@Component
public class EmployeValidationEngine {

    /**
     * Âge minimum légal d'accès à l'emploi (Loi n° 2003-044, Art. 100 : 16 ans révolus).
     * Modifiable rapidement si la politique RH évolue.
     */
    public static final int AGE_MINIMUM_LEGAL = 16;

    private static final Pattern PATTERN_EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PATTERN_CIN = Pattern.compile("^[0-9]{12}$");
    private static final Pattern PATTERN_TELEPHONE = Pattern.compile("^[+]?[0-9\\s.-]{8,20}$");

    /**
     * Valide l'intégrité métier complète d'une demande de création ou de modification de salarié.
     * @param req Données de la requête
     * @throws IllegalArgumentException si une règle métier est enfreinte
     */
    public void validerEmploye(EmployeRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("La requête employé ne peut pas être nulle.");
        }

        List<String> erreurs = new ArrayList<>();

        // 1. Identité de base
        if (req.getNom() == null || req.getNom().trim().isEmpty()) {
            erreurs.add("Le nom de famille est obligatoire.");
        }
        if (req.getPrenom() == null || req.getPrenom().trim().isEmpty()) {
            erreurs.add("Le prénom est obligatoire.");
        }
        if (req.getSexe() == null || req.getSexe().trim().isEmpty()) {
            erreurs.add("Le sexe est obligatoire (M ou F).");
        }

        // 2. Date de naissance & Âge minimum légal
        LocalDate dateNaiss = toLocalDate(req.getDateNaissance());
        if (dateNaiss == null) {
            erreurs.add("La date de naissance est obligatoire.");
        } else {
            validerAgeLegal(dateNaiss, LocalDate.now(), AGE_MINIMUM_LEGAL, erreurs);
        }

        // 3. CIN (Carte d'Identité Nationale)
        LocalDate dateCin = toLocalDate(req.getCinDateDelivrance());
        if (req.getCinNumero() == null || req.getCinNumero().trim().isEmpty()) {
            erreurs.add("Le numéro de CIN est obligatoire.");
        } else {
            validerCinFormatEtDate(req.getCinNumero(), dateCin, dateNaiss, erreurs);
        }

        // 4. Coordonnées (Adresse, Téléphone, Email)
        if (req.getAdresse() == null || req.getAdresse().trim().isEmpty()) {
            erreurs.add("L'adresse de résidence est obligatoire.");
        }
        if (req.getTelephone() == null || req.getTelephone().trim().isEmpty()) {
            erreurs.add("Le numéro de téléphone est obligatoire.");
        } else if (!PATTERN_TELEPHONE.matcher(req.getTelephone().trim()).matches()) {
            erreurs.add("Le format du numéro de téléphone est invalide (" + req.getTelephone() + ").");
        }

        if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
            if (!PATTERN_EMAIL.matcher(req.getEmail().trim()).matches()) {
                erreurs.add("L'adresse email saisie est invalide (" + req.getEmail() + ").");
            }
        }

        // 5. Compte bancaire (si renseigné)
        if (req.getCompteBanquePrincipal() != null) {
            validerCompteBancaire(req.getCompteBanquePrincipal(), erreurs);
        }

        // 6. Conjoint & Enfants
        if (req.getEnfants() != null) {
            for (int i = 0; i < req.getEnfants().size(); i++) {
                EmployeRequest.EnfantRequest enf = req.getEnfants().get(i);
                if (enf.getNom() == null || enf.getNom().trim().isEmpty()) {
                    erreurs.add("L'enfant n°" + (i + 1) + " doit avoir un nom.");
                }
                LocalDate dateNaissEnf = toLocalDate(enf.getDateNaissance());
                if (dateNaissEnf != null && dateNaissEnf.isAfter(LocalDate.now())) {
                    erreurs.add("La date de naissance de l'enfant n°" + (i + 1) + " ne peut pas être dans le futur.");
                }
                LocalDate dateFinScol = toLocalDate(enf.getDateFinScolarite());
                if (dateFinScol != null && dateNaissEnf != null && dateFinScol.isBefore(dateNaissEnf)) {
                    erreurs.add("La date de fin de scolarité de l'enfant n°" + (i + 1) + " ne peut pas précéder sa date de naissance.");
                }
            }
        }

        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join(" | ", erreurs));
        }
    }

    /**
     * Contrôle de l'âge légal (16 ans révolus).
     */
    public void validerAgeLegal(LocalDate dateNaissance, LocalDate referenceDate, int ageMin, List<String> erreurs) {
        if (dateNaissance.isAfter(referenceDate)) {
            erreurs.add("La date de naissance ne peut pas être postérieure à aujourd'hui.");
            return;
        }
        int age = Period.between(dateNaissance, referenceDate).getYears();
        if (age < ageMin) {
            erreurs.add("L'employé doit avoir au minimum " + ageMin + " ans révolus (âge calculé : " + age + " ans).");
        }
    }

    /**
     * Contrôle du format de CIN (12 chiffres à Madagascar) et de la cohérence de sa date de délivrance.
     */
    public void validerCinFormatEtDate(String cinNumero, LocalDate cinDateDelivrance, LocalDate dateNaissance, List<String> erreurs) {
        String cleanCin = cinNumero.replaceAll("\\s+", "");
        if (!PATTERN_CIN.matcher(cleanCin).matches()) {
            erreurs.add("Le numéro de CIN doit comporter exactement 12 chiffres (reçu : " + cinNumero + ").");
        }
        if (cinDateDelivrance != null) {
            if (cinDateDelivrance.isAfter(LocalDate.now())) {
                erreurs.add("La date de délivrance du CIN ne peut pas être dans le futur.");
            }
            if (dateNaissance != null) {
                int ageDelivrance = Period.between(dateNaissance, cinDateDelivrance).getYears();
                if (ageDelivrance < AGE_MINIMUM_LEGAL) {
                    erreurs.add("La date de délivrance du CIN (" + cinDateDelivrance + ") est incompatible avec la date de naissance (délivré avant l'âge légal de " + AGE_MINIMUM_LEGAL + " ans).");
                }
            }
        }
    }

    /**
     * Contrôle des données du compte bancaire.
     */
    public void validerCompteBancaire(EmployeRequest.CompteBanqueRequest cb, List<String> erreurs) {
        if (cb.getIdBanque() == null && (cb.getNumCompteRib() != null && !cb.getNumCompteRib().trim().isEmpty())) {
            erreurs.add("La banque doit être sélectionnée si un numéro de compte est renseigné.");
        }
        if (cb.getIdBanque() != null && (cb.getNumCompteRib() == null || cb.getNumCompteRib().trim().isEmpty())) {
            erreurs.add("Le numéro de compte RIB est obligatoire si une banque est sélectionnée.");
        }
    }

    /**
     * Contrôle de la demande de désactivation/départ d'un collaborateur.
     */
    public void validerDesactivation(DesactivationRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("La demande de désactivation ne peut pas être nulle.");
        }
        if (req.getMotif() == null || req.getMotif().trim().isEmpty()) {
            throw new IllegalArgumentException("Le motif de désactivation est obligatoire.");
        }
    }

    private LocalDate toLocalDate(Date sqlDate) {
        return sqlDate != null ? sqlDate.toLocalDate() : null;
    }
}
