package mg.galaxam.sirh.fixtures;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TestDatabaseCleaner {

    @Autowired
    private DataSource dataSource;

    public void cleanTransactionalData() {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("TRUNCATE TABLE pointage_indemnite_jour CASCADE;");
                s.execute("TRUNCATE TABLE pointage_jour CASCADE;");
                s.execute("TRUNCATE TABLE pointage_semaine_calcule CASCADE;");
                s.execute("TRUNCATE TABLE pointage_cumul_indemnite CASCADE;");
                s.execute("TRUNCATE TABLE pointage_cumul_periode CASCADE;");
                s.execute("TRUNCATE TABLE demande_conge CASCADE;");
                s.execute("TRUNCATE TABLE mouvement_solde_conge CASCADE;");
                s.execute("TRUNCATE TABLE conge_journal_droit CASCADE;");
                s.execute("TRUNCATE TABLE workflow_rh CASCADE;");
                s.execute("TRUNCATE TABLE contrat_periode_essai CASCADE;");
                s.execute("TRUNCATE TABLE employe_affectation_carriere CASCADE;");
                s.execute("TRUNCATE TABLE contrat_travail CASCADE;");
                s.execute("TRUNCATE TABLE employe_enfant CASCADE;");
                s.execute("TRUNCATE TABLE employe_conjoint CASCADE;");
                s.execute("TRUNCATE TABLE employe_contact_urgence CASCADE;");
                s.execute("TRUNCATE TABLE employe_compte_banque CASCADE;");
                s.execute("TRUNCATE TABLE employe_indemnite_derogation CASCADE;");
                s.execute("TRUNCATE TABLE document_rh CASCADE;");
                s.execute("TRUNCATE TABLE employe CASCADE;");
                s.execute("TRUNCATE TABLE notification_rh CASCADE;");
                s.execute("TRUNCATE TABLE journal_connexion CASCADE;");
                s.execute("TRUNCATE TABLE audit_log CASCADE;");
                s.execute("TRUNCATE TABLE periode_paie CASCADE;");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur lors du nettoyage de la base de test : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
