package mg.galaxam.sirh.conge.application;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.conge.domain.CongeExpirationExamenDTO;
import mg.galaxam.sirh.conge.domain.PurgeFifoEngine;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import org.springframework.stereotype.Service;

@Service
public class CongeExpirationExamenService {
    private final PurgeFifoEngine purgeFifoEngine;

    public CongeExpirationExamenService(PurgeFifoEngine purgeFifoEngine) {
        this.purgeFifoEngine = purgeFifoEngine;
    }

    public CongeExpirationExamenDTO obtenir(int idEmploye, LocalDate dateReference, Connection c) throws Exception {
        LocalDate reference = dateReference != null ? dateReference : LocalDate.now();
        int delaiMois = delaiPrescription(c);
        String sql = "SELECT decision, motif, date_prochaine_revue FROM conge_expiration_examen "
                + "WHERE id_employe = ? ORDER BY created_at DESC, id_examen DESC LIMIT 1";
        String decision = null;
        String motif = null;
        LocalDate prochaineDate = null;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    decision = rs.getString("decision");
                    motif = rs.getString("motif");
                    Date prochaine = rs.getDate("date_prochaine_revue");
                    prochaineDate = prochaine != null ? prochaine.toLocalDate() : null;
                }
            }
        }

        Snapshot snapshot = chargerSnapshot(idEmploye, reference, delaiMois, c);
        return new CongeExpirationExamenDTO(idEmploye, reference, delaiMois, reference.minusMonths(delaiMois),
                BigDecimal.valueOf(snapshot.jours()).setScale(2, java.math.RoundingMode.HALF_UP),
                snapshot.plusAncien(), snapshot.droitsCandidats(), decision, motif, prochaineDate);
    }

    public CongeExpirationExamenDTO reporter(int idEmploye, LocalDate prochaineDate, String motif,
            Integer idUtilisateur, Connection c) throws Exception {
        validerMotif(motif);
        if (prochaineDate == null || !prochaineDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La prochaine date d'examen doit être postérieure à aujourd'hui.");
        }
        validerDecisionnaire(idUtilisateur);
        verrouillerEmploye(idEmploye, c);
        Snapshot snapshot = chargerSnapshot(idEmploye, LocalDate.now(), delaiPrescription(c), c);
        if (snapshot.jours() <= 0) throw new IllegalStateException("Aucun droit n'est actuellement à examiner.");
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_expiration_examen "
                + "(id_employe, decision, jours_examines, motif, date_prochaine_revue, decide_par) "
                + "VALUES (?, 'REPORTE', ?, ?, ?, ?)")) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, BigDecimal.valueOf(snapshot.jours()).setScale(2, java.math.RoundingMode.HALF_UP));
            ps.setString(3, motif.trim());
            ps.setDate(4, Date.valueOf(prochaineDate));
            if (idUtilisateur == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, idUtilisateur);
            ps.executeUpdate();
        }
        return obtenir(idEmploye, LocalDate.now(), c);
    }

    public CongeExpirationExamenDTO confirmerExpiration(int idEmploye, String motif, boolean planificationVerifiee,
            Integer idUtilisateur, Connection c) throws Exception {
        validerMotif(motif);
        if (!planificationVerifiee) {
            throw new IllegalArgumentException("La vérification de la planification des congés doit être confirmée.");
        }
        validerDecisionnaire(idUtilisateur);
        verrouillerEmploye(idEmploye, c);
        LocalDate reference = LocalDate.now();
        int delaiMois = delaiPrescription(c);
        Snapshot snapshot = chargerSnapshot(idEmploye, reference, delaiMois, c);
        if (snapshot.jours() <= 0) throw new IllegalStateException("Aucun droit n'est actuellement à examiner.");

        int idMouvement;
        String commentaire = "Expiration confirmée après examen DAF — droits acquis avant le "
                + reference.minusMonths(delaiMois) + " — Motif : " + motif.trim();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by) "
                + "VALUES (?, 'EXPIRATION', ?, ?, ?, ?) RETURNING id_mouvement")) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, BigDecimal.valueOf(snapshot.jours()).setScale(2, java.math.RoundingMode.HALF_UP).negate());
            ps.setDate(3, Date.valueOf(reference));
            ps.setString(4, commentaire);
            if (idUtilisateur == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, idUtilisateur);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("Le mouvement d'expiration n'a pas été créé.");
                idMouvement = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_expiration_examen "
                + "(id_employe, decision, jours_examines, motif, id_mouvement_expiration, decide_par, planification_verifiee) "
                + "VALUES (?, 'EXPIRE', ?, ?, ?, ?, TRUE)")) {
            ps.setInt(1, idEmploye);
            ps.setBigDecimal(2, BigDecimal.valueOf(snapshot.jours()).setScale(2, java.math.RoundingMode.HALF_UP));
            ps.setString(3, motif.trim());
            ps.setInt(4, idMouvement);
            if (idUtilisateur == null) ps.setNull(5, java.sql.Types.INTEGER); else ps.setInt(5, idUtilisateur);
            ps.executeUpdate();
        }
        return obtenir(idEmploye, reference, c);
    }

    private Snapshot chargerSnapshot(int idEmploye, LocalDate reference, int delaiMois, Connection c) throws Exception {
        List<PurgeFifoEngine.MouvementLedger> mouvements = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id_mouvement, type_mouvement, nb_jours, date_mouvement, commentaire "
                + ", id_demande_conge FROM mouvement_solde_conge WHERE id_employe = ? ORDER BY date_mouvement, id_mouvement")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BigDecimal joursBd = rs.getBigDecimal("nb_jours");
                    double jours = joursBd == null ? 0 : joursBd.doubleValue();
                    Date dateSql = rs.getDate("date_mouvement");
                    LocalDate date = dateSql == null ? reference : dateSql.toLocalDate();
                    int idDemandeValue = rs.getInt("id_demande_conge");
                    Integer idDemande = rs.wasNull() ? null : idDemandeValue;
                    mouvements.add(new PurgeFifoEngine.MouvementLedger(rs.getInt("id_mouvement"),
                            rs.getString("type_mouvement"), jours, date, idDemande, rs.getString("commentaire")));
                }
            }
        }
        LocalDate seuil = reference.minusMonths(delaiMois);
        List<PurgeFifoEngine.DroitDisponible> droits = purgeFifoEngine.calculerDroitsDisponibles(
                mouvements, reference, delaiMois);
        LocalDate plusAncien = null;
        List<CongeExpirationExamenDTO.DroitCandidat> candidats = new ArrayList<>();
        double jours = 0;
        for (PurgeFifoEngine.DroitDisponible droit : droits) {
            if (droit.dateAcquisition() == null || !droit.dateAcquisition().isBefore(seuil) || droit.joursRestants() <= 0.001) continue;
            if (plusAncien == null) plusAncien = droit.dateAcquisition();
            jours += droit.joursRestants();
            candidats.add(new CongeExpirationExamenDTO.DroitCandidat(droit.dateAcquisition(),
                    droit.dateAcquisition().plusMonths(delaiMois),
                    BigDecimal.valueOf(droit.joursRestants()).setScale(2, java.math.RoundingMode.HALF_UP)));
            }
        return new Snapshot(Math.round(jours * 100.0) / 100.0, plusAncien, List.copyOf(candidats));
    }

    private int delaiPrescription(Connection c) throws Exception {
        var params = ParametreGlobal.loadActiveMap(c);
        return ParametreGlobal.requirePositiveInt(params, "DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS");
    }

    private void verrouillerEmploye(int idEmploye, Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Employé introuvable.");
            }
        }
    }

    private void validerMotif(String motif) {
        if (motif == null || motif.trim().length() < 10) {
            throw new IllegalArgumentException("Un motif explicatif d'au moins 10 caractères est obligatoire.");
        }
    }

    private void validerDecisionnaire(Integer idUtilisateur) {
        if (idUtilisateur == null) throw new IllegalStateException("Impossible d'identifier l'utilisateur qui prend cette décision.");
    }

    private record Snapshot(double jours, LocalDate plusAncien,
            List<CongeExpirationExamenDTO.DroitCandidat> droitsCandidats) {}
}
