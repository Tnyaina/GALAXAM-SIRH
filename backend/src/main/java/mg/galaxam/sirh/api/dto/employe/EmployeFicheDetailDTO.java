package mg.galaxam.sirh.api.dto.employe;

import java.util.List;
import mg.galaxam.sirh.model.employe.EmployeCPL;
import mg.galaxam.sirh.model.employe.EmployeCompteBanque;
import mg.galaxam.sirh.model.employe.EmployeConjoint;
import mg.galaxam.sirh.model.employe.EmployeContactUrgence;
import mg.galaxam.sirh.model.employe.EmployeEnfant;

public class EmployeFicheDetailDTO {

    private EmployeCPL employe;
    private EmployeConjoint conjoint;
    private List<EmployeEnfant> enfants;
    private List<EmployeContactUrgence> contactsUrgence;
    private List<EmployeCompteBanque> comptesBancaires;

    public EmployeFicheDetailDTO() {}

    public EmployeFicheDetailDTO(EmployeCPL employe,
                                EmployeConjoint conjoint,
                                List<EmployeEnfant> enfants,
                                List<EmployeContactUrgence> contactsUrgence,
                                List<EmployeCompteBanque> comptesBancaires) {
        this.employe = employe;
        this.conjoint = conjoint;
        this.enfants = enfants;
        this.contactsUrgence = contactsUrgence;
        this.comptesBancaires = comptesBancaires;
    }

    public EmployeCPL getEmploye() { return employe; }
    public void setEmploye(EmployeCPL employe) { this.employe = employe; }

    public EmployeConjoint getConjoint() { return conjoint; }
    public void setConjoint(EmployeConjoint conjoint) { this.conjoint = conjoint; }

    public List<EmployeEnfant> getEnfants() { return enfants; }
    public void setEnfants(List<EmployeEnfant> enfants) { this.enfants = enfants; }

    public List<EmployeContactUrgence> getContactsUrgence() { return contactsUrgence; }
    public void setContactsUrgence(List<EmployeContactUrgence> contactsUrgence) { this.contactsUrgence = contactsUrgence; }

    public List<EmployeCompteBanque> getComptesBancaires() { return comptesBancaires; }
    public void setComptesBancaires(List<EmployeCompteBanque> comptesBancaires) { this.comptesBancaires = comptesBancaires; }
}
