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

public enum PayrollPeriodCommand {
    OPEN,
    SUBMIT,
    APPROVE,
    REJECT,
    CLOSE,
    REOPEN
}
