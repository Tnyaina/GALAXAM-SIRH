package utilitaire;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

@Component
public class UtilDB {
    
    private static DataSource dataSource;

    // Spring injecte automatiquement la DataSource ici au démarrage
    public UtilDB(DataSource dataSource) {
        UtilDB.dataSource = dataSource;
    }

    // Méthode statique que tes entités ou tes contrôleurs appelleront
    public static Connection GetConn() throws Exception {
        return dataSource.getConnection(); // Demande une connexion au pool HikariCP de Spring
    }
}
