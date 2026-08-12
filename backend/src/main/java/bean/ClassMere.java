package bean;

import java.sql.Connection;

/**
 * Classe mère de toutes les entités métier (pattern Active Record, pur JDBC).
 *
 * Philosophie :
 *   - AUCUNE reflection, aucune magie : chaque entité fille écrit elle-même
 *     ses requêtes SQL (INSERT/UPDATE/DELETE/SELECT), comme dans du JDBC classique.
 *   - ClassMere ne fait qu'orchestrer : contrôle métier -> exécution du CRUD
 *     propre à l'entité. Les id techniques sont auto-incrémentés par la base.
 *   - On construit/modifie ses objets librement en mémoire (setters), et on ne
 *     touche la base que lorsqu'on appelle explicitement save()/update()/delete().
 *   - Toutes les méthodes prennent une Connection en paramètre : le commit/rollback
 *     reste entièrement piloté par l'appelant (servlet/contrôleur), jamais ici.
 */
public abstract class ClassMere {

    private String nomTable;

    // ------------------------------------------------------------------
    // Nom de table
    // ------------------------------------------------------------------

    public String getNomTable() {
        return nomTable;
    }

    public void setNomTable(String nomTable) {
        this.nomTable = nomTable;
    }

    // ------------------------------------------------------------------
    // Identification du tuple - à implémenter par chaque entité fille
    // ------------------------------------------------------------------

    /**
     * Valeur courante de la clé primaire (id technique, auto-increment côté base).
     * Utile pour savoir si l'objet a déjà été persisté, faire des comparaisons,
     * du logging, etc. Reste null tant que save() n'a pas été appelé.
     */
    public abstract Integer getTuppleID();

    /**
     * Colonne de liaison avec une éventuelle classe fille (relation maître/détail),
     * ex: "idEmploye" dans une table Contrat. Retourne null si l'entité n'a pas de fille.
     */
    public String getLiaisonFille() {
        return null;
    }

    /** Nom qualifié de la classe fille associée (relation maître/détail). */
    public String getNomClasseFille() {
        return null;
    }

    public abstract String getAttributIDName();

    /**
     * Point d'extension pour les règles de contrôle métier, appelé
     * automatiquement avant save()/update().
     * Par défaut : aucun contrôle. À surcharger si nécessaire.
     */
    public void controler(Connection c) throws Exception {
        // rien par défaut
    }

    // ------------------------------------------------------------------
    // CRUD - à implémenter par chaque entité fille (SQL explicite, sans reflection)
    // ------------------------------------------------------------------

    /**
     * Exécute l'INSERT propre à l'entité (colonnes/valeurs écrites à la main).
     * Appelée uniquement par save(), jamais directement par l'appelant.
     */
    protected abstract ClassMere inserer(Connection c) throws Exception;

    /**
     * Exécute l'UPDATE propre à l'entité (généralement "WHERE ID = ?").
     * Appelée uniquement par update().
     */
    protected abstract ClassMere modifier(Connection c) throws Exception;

    /**
     * Exécute le DELETE propre à l'entité.
     * Appelée uniquement par delete().
     */
    protected abstract void supprimer(Connection c) throws Exception;

    /**
     * Exécute le SELECT par clé primaire et retourne une instance hydratée
     * (ou null si rien trouvé). Appelée uniquement par findById().
     */
    protected abstract ClassMere rechercherParId(String id, Connection c) throws Exception;

    // ------------------------------------------------------------------
    // Points d'entrée publics : contrôle métier + persistance
    // ------------------------------------------------------------------

    /**
     * Crée le tuple en base (contrôle, puis insère).
     * L'id technique est auto-incrémenté par la base : c'est à inserer()
     * de le récupérer après l'INSERT (via les generated keys) et de
     * l'affecter à l'objet.
     */
    public final ClassMere save(Connection c) throws Exception {
        controler(c);
        return inserer(c);
    }

    /** Met à jour le tuple en base (contrôle, puis update). */
    public final ClassMere update(Connection c) throws Exception {
        controler(c);
        return modifier(c);
    }

    /** Supprime le tuple en base. */
    public final void delete(Connection c) throws Exception {
        supprimer(c);
    }

    /** Recherche un tuple par sa clé primaire. */
    public final ClassMere findById(String id, Connection c) throws Exception {
        return rechercherParId(id, c);
    }
}
