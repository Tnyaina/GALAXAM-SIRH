package mg.galaxam.sirh.paie.application;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.paie.domain.PayrollBusinessTime;
import mg.galaxam.sirh.paie.persistence.PaieDossierSortie;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import org.springframework.stereotype.Service;

/** Parcours anticipé du bulletin mensuel final d'un salarié sorti. */
@Service
public class DeparturePayrollService {
    private final DataSource dataSource;
    private final PointageSyntheseService pointage;
    private final PayrollService payroll;

    public DeparturePayrollService(DataSource dataSource, PointageSyntheseService pointage, PayrollService payroll) {
        this.dataSource = dataSource;
        this.pointage = pointage;
        this.payroll = payroll;
    }

    public record Situation(int periodeId, int employeId, int contratId, LocalDate dateDepart,
                            String statutPeriode, boolean pointageGele, Integer bulletinId,
                            String statutBulletin) {}

    public Situation situation(int dossierId) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return charger(dossierId, c);
        }
    }

    public Situation gelerPointage(int dossierId, Integer userId) throws Exception {
        return transaction(dossierId, (state, c) -> {
            verifierDepart(state, c);
            if (state.bulletinId() != null && "VALIDE".equals(state.statutBulletin()))
                return state;
            if (!state.pointageGele()) pointage.gelerEmploye(state.periodeId(), state.employeId(), userId, c);
            return charger(dossierId, c);
        });
    }

    public Situation calculerBulletin(int dossierId, Integer userId) throws Exception {
        return transaction(dossierId, (state, c) -> {
            verifierDepart(state, c);
            if (!state.pointageGele()) throw new IllegalStateException(
                    "POINTAGE_NOT_FROZEN: gelez le pointage individuel avant le calcul.");
            if ("VALIDE".equals(state.statutBulletin()) || "CLOTURE".equals(state.statutBulletin()))
                return state;
            payroll.calculerBulletinEmploye(state.employeId(), state.periodeId(), false, userId, c);
            return charger(dossierId, c);
        });
    }

    public Situation validerBulletin(int dossierId, Integer userId) throws Exception {
        return transaction(dossierId, (state, c) -> {
            verifierDepart(state, c);
            if (!state.pointageGele()) throw new IllegalStateException(
                    "POINTAGE_NOT_FROZEN: le pointage final doit être gelé.");
            if ("VALIDE".equals(state.statutBulletin()) || "CLOTURE".equals(state.statutBulletin()))
                return state;
            payroll.validerBulletinSortie(state.periodeId(), state.employeId(), userId, c);
            return charger(dossierId, c);
        });
    }

    private Situation transaction(int dossierId, Action action) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Sérialise les actions de sortie et la validation de la campagne.
                Situation state = charger(dossierId, c);
                try (PreparedStatement lock = c.prepareStatement(
                        "SELECT id_periode_paie FROM periode_paie WHERE id_periode_paie=? FOR UPDATE")) {
                    lock.setInt(1, state.periodeId());
                    lock.executeQuery().close();
                }
                Situation result = action.run(charger(dossierId, c), c);
                c.commit();
                return result;
            } catch (Exception failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    private Situation charger(int dossierId, Connection c) throws Exception {
        PaieDossierSortie dossier = PaieDossierSortie.trouverParId(dossierId, c);
        if (dossier == null || dossier.getDateDepart() == null || dossier.getIdEmploye() == null
                || dossier.getIdContrat() == null)
            throw new IllegalStateException("DOSSIER_SORTIE_INCOMPLET: enregistrez la date de départ.");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT p.id_periode_paie,s.code FROM periode_paie p "
                + "JOIN ref_statut_periode_paie s ON s.id_statut_periode=p.id_statut_periode "
                + "WHERE ? BETWEEN p.date_debut AND p.date_fin")) {
            ps.setDate(1, dossier.getDateDepart());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "PERIODE_PAIE_MANQUANTE: aucune campagne ne couvre la date de départ.");
                int periodeId = rs.getInt(1);
                String statutPeriode = rs.getString(2);
                if (rs.next()) throw new IllegalStateException(
                        "PERIODE_PAIE_AMBIGUE: plusieurs campagnes couvrent la date de départ.");
                boolean gele;
                try (PreparedStatement snapshot = c.prepareStatement(
                        "SELECT 1 FROM pointage_cumul_periode WHERE id_periode_paie=? AND id_employe=? "
                        + "AND statut_snapshot IN ('GELE','CONSOMME') LIMIT 1")) {
                    snapshot.setInt(1, periodeId);
                    snapshot.setInt(2, dossier.getIdEmploye());
                    try (ResultSet row = snapshot.executeQuery()) { gele = row.next(); }
                }
                Integer bulletinId = null;
                String statutBulletin = null;
                try (PreparedStatement bulletin = c.prepareStatement(
                        "SELECT id_bulletin,statut FROM bulletin_paie WHERE id_periode_paie=? AND id_employe=? "
                        + "AND COALESCE(type_bulletin,'MENSUEL')='MENSUEL' ORDER BY id_bulletin DESC LIMIT 1")) {
                    bulletin.setInt(1, periodeId);
                    bulletin.setInt(2, dossier.getIdEmploye());
                    try (ResultSet row = bulletin.executeQuery()) {
                        if (row.next()) { bulletinId = row.getInt(1); statutBulletin = row.getString(2); }
                    }
                }
                return new Situation(periodeId, dossier.getIdEmploye(), dossier.getIdContrat(),
                        dossier.getDateDepart().toLocalDate(),
                        statutPeriode, gele, bulletinId, statutBulletin);
            }
        }
    }

    private void verifierDepart(Situation state, Connection c) throws Exception {
        if (state.dateDepart().isAfter(PayrollBusinessTime.today()))
            throw new IllegalStateException("DEPART_NON_EFFECTIF: attendez la date effective de sortie.");
        if ("BROUILLON".equals(state.statutPeriode()))
            throw new IllegalStateException("PERIODE_NON_OUVERTE: ouvrez la campagne de paie avant le gel individuel.");
        if ("PAIE_VALIDEE".equals(state.statutPeriode()) || "CLOTUREE".equals(state.statutPeriode()))
            throw new IllegalStateException("PERIODE_DEJA_CLOTUREE: utilisez le parcours normal de la campagne.");
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM contrat_travail ct JOIN employe_affectation_carriere a "
                + "ON a.id_contrat=ct.id_contrat AND a.id_employe=ct.id_employe "
                + "WHERE ct.id_employe=? AND ct.id_contrat=? AND ct.date_fin=? AND a.date_fin=? "
                + "AND ct.date_debut<=? AND a.date_debut<=? LIMIT 1")) {
            ps.setInt(1, state.employeId());
            ps.setInt(2, state.contratId());
            for (int i = 3; i <= 6; i++) ps.setDate(i, Date.valueOf(state.dateDepart()));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException(
                        "DEPART_CONTRAT_NON_CLOTURE: terminez le contrat et l'affectation à la date de départ.");
            }
        }
    }

    @FunctionalInterface private interface Action {
        Situation run(Situation state, Connection c) throws Exception;
    }
}
