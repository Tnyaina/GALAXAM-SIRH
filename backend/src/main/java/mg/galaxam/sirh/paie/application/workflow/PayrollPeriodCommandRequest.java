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

import jakarta.validation.constraints.NotNull;

public class PayrollPeriodCommandRequest {

    @NotNull(message = "La version de la période est obligatoire.")
    private Integer version;
    private String motif;
    private Integer idSite;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getMotif() {
        return motif;
    }

    public void setMotif(String motif) {
        this.motif = motif;
    }

    public Integer getIdSite() {
        return idSite;
    }

    public void setIdSite(Integer idSite) {
        this.idSite = idSite;
    }
}
