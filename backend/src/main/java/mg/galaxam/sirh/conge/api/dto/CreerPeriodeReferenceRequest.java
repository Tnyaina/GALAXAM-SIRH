package mg.galaxam.sirh.conge.api.dto;

import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonFormat;

public class CreerPeriodeReferenceRequest {

    private Integer idEmploye;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    public CreerPeriodeReferenceRequest() {
    }

    public CreerPeriodeReferenceRequest(Integer idEmploye, LocalDate dateDebut, LocalDate dateFin) {
        this.idEmploye = idEmploye;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
    }

    public Integer getIdEmploye() {
        return idEmploye;
    }

    public void setIdEmploye(Integer idEmploye) {
        this.idEmploye = idEmploye;
    }

    public LocalDate getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(LocalDate dateDebut) {
        this.dateDebut = dateDebut;
    }

    public LocalDate getDateFin() {
        return dateFin;
    }

    public void setDateFin(LocalDate dateFin) {
        this.dateFin = dateFin;
    }
}
