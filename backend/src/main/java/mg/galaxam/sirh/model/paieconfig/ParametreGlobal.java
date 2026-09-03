package mg.galaxam.sirh.model.paieconfig;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class ParametreGlobal extends ClassMere {

    private Integer idParametre;
    private String codeParametre;
    private String valeur;
    private String description;
    private String categorie;
    private Date dateDebut;
    private Date dateFin;

    public ParametreGlobal() {
        this.setNomTable("PARAMETRE_GLOBAL");
    }

    @Override
    public Integer getTuppleID() {
        return idParametre;
    }

    @Override
    public String getAttributIDName() {
        return "id_parametre";
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO parametre_global (code_parametre, valeur, description, categorie, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id_parametre";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre);
            ps.setString(2, valeur);
            ps.setString(3, description);
            ps.setString(4, categorie);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idParametre = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE parametre_global SET code_parametre = ?, valeur = ?, description = ?, categorie = ?, date_debut = ?, date_fin = ? " +
                     "WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre);
            ps.setString(2, valeur);
            ps.setString(3, description);
            ps.setString(4, categorie);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setInt(7, idParametre);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM parametre_global WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idParametre);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin FROM parametre_global WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ParametreGlobal item = new ParametreGlobal();
                    item.idParametre = rs.getInt("id_parametre");
                    item.codeParametre = rs.getString("code_parametre");
                    item.valeur = rs.getString("valeur");
                    item.description = rs.getString("description");
                    item.categorie = rs.getString("categorie");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<ParametreGlobal> listAll(Connection c) throws Exception {
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin FROM parametre_global ORDER BY categorie ASC, code_parametre ASC";
        List<ParametreGlobal> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ParametreGlobal item = new ParametreGlobal();
                item.idParametre = rs.getInt("id_parametre");
                item.codeParametre = rs.getString("code_parametre");
                item.valeur = rs.getString("valeur");
                item.description = rs.getString("description");
                item.categorie = rs.getString("categorie");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                list.add(item);
            }
        }
        return list;
    }

    public static List<ParametreGlobal> listByCategorie(String categorie, Connection c) throws Exception {
        if (categorie == null || categorie.isBlank()) return listAll(c);
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin FROM parametre_global WHERE UPPER(categorie) = UPPER(?) ORDER BY code_parametre ASC";
        List<ParametreGlobal> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, categorie.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ParametreGlobal item = new ParametreGlobal();
                    item.idParametre = rs.getInt("id_parametre");
                    item.codeParametre = rs.getString("code_parametre");
                    item.valeur = rs.getString("valeur");
                    item.description = rs.getString("description");
                    item.categorie = rs.getString("categorie");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Charge l'ensemble des paramètres globaux actifs sous forme de Map clé-valeur (clé en majuscules).
     */
    public static java.util.Map<String, String> loadActiveMap(Connection c) {
        return loadActiveMapAtDate(new Date(System.currentTimeMillis()), c);
    }

    /**
     * Charge les paramètres actifs à une date de référence spécifique (ex: période de paie passée).
     */
    public static java.util.Map<String, String> loadActiveMapAtDate(Date dateRef, Connection c) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        Date d = (dateRef != null) ? dateRef : new Date(System.currentTimeMillis());
        String sql = "SELECT code_parametre, valeur FROM parametre_global " +
                     "WHERE date_debut <= ? AND (date_fin IS NULL OR date_fin >= ?) " +
                     "ORDER BY date_debut ASC, id_parametre ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, d);
            ps.setDate(2, d);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("code_parametre").toUpperCase(), rs.getString("valeur"));
                }
            }
        } catch (Exception e) {
            System.err.println("[PARAMETRE GLOBAL] Erreur de chargement: " + e.getMessage());
        }
        return map;
    }

    /**
     * Charge les paramètres actifs d'une catégorie spécifique sous forme de Map.
     */
    public static java.util.Map<String, String> loadActiveMapByCategorie(String categorie, Connection c) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String sql = "SELECT code_parametre, valeur FROM parametre_global " +
                     "WHERE UPPER(categorie) = UPPER(?) AND date_debut <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE) " +
                     "ORDER BY date_debut ASC, id_parametre ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, categorie.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("code_parametre").toUpperCase(), rs.getString("valeur"));
                }
            }
        } catch (Exception e) {
            System.err.println("[PARAMETRE GLOBAL] Erreur de chargement catégorie " + categorie + ": " + e.getMessage());
        }
        return map;
    }

    /**
     * Clôture la version précédente d'un paramètre global à la veille de la nouvelle date d'effet.
     */
    public static void cloturerPrecedent(String codeParametre, Date dateDebutNouveau, Connection c) throws Exception {
        if (codeParametre == null || dateDebutNouveau == null) return;
        String closeSql = "UPDATE parametre_global SET date_fin = ? " +
                          "WHERE UPPER(code_parametre) = UPPER(?) " +
                          "AND (date_fin IS NULL OR date_fin >= ?) " +
                          "AND date_debut < ?";
        try (PreparedStatement ps = c.prepareStatement(closeSql)) {
            ps.setDate(1, java.sql.Date.valueOf(dateDebutNouveau.toLocalDate().minusDays(1)));
            ps.setString(2, codeParametre.trim());
            ps.setDate(3, dateDebutNouveau);
            ps.setDate(4, dateDebutNouveau);
            ps.executeUpdate();
        }
    }

    /**
     * Retourne la valeur active d'un paramètre global à ce jour.
     */
    public static String getActiveValue(String codeParametre, Connection c) {
        if (codeParametre == null || codeParametre.isBlank()) return null;
        String sql = "SELECT valeur FROM parametre_global WHERE UPPER(code_parametre) = UPPER(?) " +
                     "AND date_debut <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE) " +
                     "ORDER BY date_debut DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception e) {
            System.err.println("[PARAMETRE GLOBAL] Erreur getActiveValue: " + e.getMessage());
        }
        return null;
    }

    // Helper static getters typés
    public static double getDouble(java.util.Map<String, String> params, String key, double defaultValue) {
        if (params == null || key == null) return defaultValue;
        try {
            String val = params.get(key.toUpperCase());
            return val != null ? Double.parseDouble(val.trim()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(java.util.Map<String, String> params, String key, int defaultValue) {
        if (params == null || key == null) return defaultValue;
        try {
            String val = params.get(key.toUpperCase());
            return val != null ? Integer.parseInt(val.trim()) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String getString(java.util.Map<String, String> params, String key, String defaultValue) {
        if (params == null || key == null) return defaultValue;
        String val = params.get(key.toUpperCase());
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    public static java.time.LocalTime getLocalTime(java.util.Map<String, String> params, String key, java.time.LocalTime defaultValue) {
        if (params == null || key == null) return defaultValue;
        try {
            String val = params.get(key.toUpperCase());
            if (val == null || val.trim().isEmpty()) return defaultValue;
            String s = val.trim();
            if (s.length() == 5) s += ":00";
            return java.time.LocalTime.parse(s);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // Getters / Setters
    public Integer getIdParametre() { return idParametre; }
    public void setIdParametre(Integer idParametre) { this.idParametre = idParametre; }
    public String getCodeParametre() { return codeParametre; }
    public void setCodeParametre(String codeParametre) { this.codeParametre = codeParametre; }
    public String getValeur() { return valeur; }
    public void setValeur(String valeur) { this.valeur = valeur; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
