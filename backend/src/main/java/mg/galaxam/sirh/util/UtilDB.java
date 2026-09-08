package mg.galaxam.sirh.util;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Utilitaire de gestion des connexions SQL via le pool HikariCP managé par Spring Boot.
 * 
 * Note d'architecture :
 * Privilégier l'injection de dépendance directe de DataSource dans les Services/Controllers.
 * Cette classe fournit un point d'accès statique contrôlé pour les entités ou méthodes utilitaires legacy.
 */
@Component
public class UtilDB {
    
    private static DataSource dataSource;

    public UtilDB(DataSource dataSource) {
        UtilDB.dataSource = dataSource;
    }

    /**
     * Obtient une connexion active depuis le pool HikariCP.
     * @return Connection active
     * @throws SQLException si le pool est épuisé ou inaccessible
     */
    public static Connection GetConn() throws Exception {
        if (dataSource == null) {
            throw new IllegalStateException("UtilDB n'a pas été initialisé avec une DataSource Spring.");
        }
        return dataSource.getConnection();
    }
}
