package mg.galaxam.sirh.conge.application;

import org.springframework.security.access.AccessDeniedException;

/** Règle d'autorisation et de conservation de la surcharge de rémunération d'une demande. */
public final class RemunerationCongePolicy {

    private RemunerationCongePolicy() {}

    public static Boolean resolve(Boolean existingValue, Boolean requestedValue,
                                  boolean resetToTypeDefault, boolean administrator) {
        if (requestedValue != null && resetToTypeDefault) {
            throw new IllegalArgumentException(
                    "Choisissez une rémunération explicite ou le défaut du type, pas les deux.");
        }
        if ((requestedValue != null || resetToTypeDefault) && !administrator) {
            throw new AccessDeniedException(
                    "Seul un administrateur peut définir la rémunération d'une demande de congé.");
        }
        if (resetToTypeDefault) return null;
        return requestedValue != null ? requestedValue : existingValue;
    }
}
