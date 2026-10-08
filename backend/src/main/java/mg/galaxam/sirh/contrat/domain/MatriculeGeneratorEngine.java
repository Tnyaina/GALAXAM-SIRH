package mg.galaxam.sirh.contrat.domain;


import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Moteur de génération et de validation des matricules employés.
 * Conserve la logique historique séquentielle (ex: 0001, 0042) tout en
 * fournissant des utilitaires pour les formats préfixés (EMP-YYYY-XXXX).
 */
@Component
public class MatriculeGeneratorEngine {

    private static final String PREFIX = "EMP-";
    private static final Pattern PATTERN_MATRICULE_PREFIXE = Pattern.compile("^EMP-(\\d{4})-(\\d{4,6})$");
    private static final Pattern PATTERN_MATRICULE_SEQUENTIEL = Pattern.compile("^\\d{4,}$");
    private static final Pattern PATTERN_SEQUENCE_NUMERIQUE = Pattern.compile("\\d+");
    private static final Pattern PATTERN_SEQUENCE_DANS_TEXTE = Pattern.compile("(\\d+)");

    /**
     * Génère un matricule selon l'ancienne logique historique (ex: 0001, 0042).
     */
    public String genererMatriculeSequentiel(long sequenceCount) {
        long num = Math.max(1, sequenceCount);
        return String.format("%04d", num);
    }

    /**
     * Génère un matricule par défaut (logique historique 4 chiffres).
     */
    public String genererMatriculeParDefaut(long sequenceCount) {
        return genererMatriculeSequentiel(sequenceCount);
    }

    /**
     * Génère un matricule avec préfixe et année (EMP-YYYY-XXXX).
     */
    public String genererMatriculePrefixe(int annee, long sequenceCount) {
        if (annee < 1900 || annee > 2100) {
            annee = LocalDate.now().getYear();
        }
        long num = Math.max(1, sequenceCount);
        return String.format("%s%d-%04d", PREFIX, annee, num);
    }

    /**
     * Vérifie si un matricule respecte l'une des nomenclatures autorisées (séquentiel ou préfixé).
     */
    public boolean estFormatValide(String matricule) {
        if (matricule == null || matricule.trim().isEmpty()) {
            return false;
        }
        String mat = matricule.trim();
        return PATTERN_MATRICULE_SEQUENTIEL.matcher(mat).matches() || PATTERN_MATRICULE_PREFIXE.matcher(mat).matches();
    }

    /**
     * Extrait le numéro de séquence d'un matricule quelconque (séquentiel ou préfixé).
     */
    public Long extraireSequence(String matricule) {
        if (matricule == null || matricule.trim().isEmpty()) return null;
        String mat = matricule.trim();
        Matcher mPref = PATTERN_MATRICULE_PREFIXE.matcher(mat);
        if (mPref.matches()) {
            try {
                return Long.parseLong(mPref.group(2));
            } catch (NumberFormatException ignored) {}
        }
        if (PATTERN_SEQUENCE_NUMERIQUE.matcher(mat).matches()) {
            try {
                return Long.parseLong(mat);
            } catch (NumberFormatException ignored) {}
        }
        Matcher mDigits = PATTERN_SEQUENCE_DANS_TEXTE.matcher(mat);
        Long lastVal = null;
        while (mDigits.find()) {
            try {
                lastVal = Long.parseLong(mDigits.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return lastVal;
    }

    /**
     * Extrait l'année d'un matricule préfixé si présente.
     */
    public Integer extraireAnnee(String matricule) {
        if (matricule == null) return null;
        Matcher m = PATTERN_MATRICULE_PREFIXE.matcher(matricule.trim());
        if (m.matches()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }
}
