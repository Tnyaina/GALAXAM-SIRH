package mg.galaxam.sirh.paie.api.dto.stc;

import java.util.Map;

/**
 * Résultat complet d'une simulation STC lancée depuis le dossier de sortie.
 * Contient le décompte financier détaillé généré par StcService,
 * ainsi que la référence de simulation vérifiable et l'empreinte métier des données sources.
 */
public class SimulationStcResultDTO {

    private String referenceSimulation;
    private Integer version;
    private StcCalculResultDTO decompte;
    private Map<String, Object> snapshotData;

    public SimulationStcResultDTO() {}

    public SimulationStcResultDTO(String referenceSimulation, Integer version, StcCalculResultDTO decompte, Map<String, Object> snapshotData) {
        this.referenceSimulation = referenceSimulation;
        this.version = version;
        this.decompte = decompte;
        this.snapshotData = snapshotData;
    }

    public String getReferenceSimulation() {
        return referenceSimulation;
    }

    public void setReferenceSimulation(String referenceSimulation) {
        this.referenceSimulation = referenceSimulation;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public StcCalculResultDTO getDecompte() {
        return decompte;
    }

    public void setDecompte(StcCalculResultDTO decompte) {
        this.decompte = decompte;
    }

    public Map<String, Object> getSnapshotData() {
        return snapshotData;
    }

    public void setSnapshotData(Map<String, Object> snapshotData) {
        this.snapshotData = snapshotData;
    }
}
