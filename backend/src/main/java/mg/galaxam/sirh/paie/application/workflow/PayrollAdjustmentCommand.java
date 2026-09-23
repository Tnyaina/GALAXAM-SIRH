package mg.galaxam.sirh.paie.application.workflow;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;

/**
 * Commandes métier explicites pour les ajustements de paie (primes, acomptes, retenues).
 * Le client demande une commande ; le backend décide si la transition est possible.
 */
public enum PayrollAdjustmentCommand {
    /** Approuver l'ajustement : EN_ATTENTE → APPROUVE */
    APPROVE,
    /** Rejeter l'ajustement : EN_ATTENTE → REJETE */
    REJECT,
    /** Annuler l'ajustement : EN_ATTENTE ou APPROUVE → ANNULE */
    CANCEL
}
