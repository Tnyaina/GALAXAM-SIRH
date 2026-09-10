package mg.galaxam.sirh.api.dto.paieconfig;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

/** DTO pour la revalorisation batch du barème IRSA. */
public class IrsaRevalorisationRequest {

    /** Date d'application du nouveau barème (doit être dans le futur). */
    private Date nouvelleDateDebut;

    /** Nouvelles tranches à créer pour ce barème. */
    private List<IrsaRequest> tranches;

    public Date getNouvelleDateDebut() { return nouvelleDateDebut; }
    public void setNouvelleDateDebut(Date nouvelleDateDebut) { this.nouvelleDateDebut = nouvelleDateDebut; }
    public List<IrsaRequest> getTranches() { return tranches; }
    public void setTranches(List<IrsaRequest> tranches) { this.tranches = tranches; }
}
