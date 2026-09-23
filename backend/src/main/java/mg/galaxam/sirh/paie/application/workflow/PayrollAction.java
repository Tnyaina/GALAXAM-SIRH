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

/** Actions de l'assistant Paie qui ne sont pas des transitions d'état de période. */
public enum PayrollAction {
    SIMULATE,
    CALCULATE,
    VALIDATE,
    ADD_VARIABLE,
    GENERATE_OUTPUT
}
