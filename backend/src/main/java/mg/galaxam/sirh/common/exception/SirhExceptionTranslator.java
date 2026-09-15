package mg.galaxam.sirh.common.exception;

/** Point unique de propagation : conserve les erreurs déjà typées et masque les détails techniques. */
public final class SirhExceptionTranslator {
    private SirhExceptionTranslator() {
    }

    public static RuntimeException propagate(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new SirhBusinessException(SirhErrorCode.INTERNAL_ERROR);
    }
}
