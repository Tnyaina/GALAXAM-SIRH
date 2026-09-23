package mg.galaxam.sirh.paie.domain.engine;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
/** Calcul ISI pur à partir du taux résolu (en pourcentage). */
public final class IsiCalculator {
  public BigDecimal calculate(BigDecimal base, BigDecimal tauxPourcent) {
    if (base == null || tauxPourcent == null) throw new IllegalStateException("ISI_CONFIGURATION_MISSING");
    return base.multiply(tauxPourcent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
  }
}
