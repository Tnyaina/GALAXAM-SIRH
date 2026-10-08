package mg.galaxam.sirh.paieconfig.persistence;

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
    private Boolean estActif = true;
    private Boolean estDesactivable = false;

    public ParametreGlobal() {
        this.setNomTable("PARAMETRE_GLOBAL");
    }

    /** Effet métier fourni à l'interface avec l'état du paramètre. */
    public String getEffetDesactivation() {
        if (!Boolean.TRUE.equals(estDesactivable) || codeParametre == null) return null;
        return switch (codeParametre.toUpperCase()) {
            case "REDUCTION_DEPLACT_REPAS_MIDI" ->
                    "La réduction de l'indemnité de déplacement ne s'applique plus.";
            case "PLAFOND_CUMUL_SOLDE_CONGE_JOURS" ->
                    "Les acquisitions et bonifications de congés ne sont plus plafonnées par cette règle.";
            case "PLAFOND_SOLDE_CONGE_NEGATIF" ->
                    "Les demandes de congé créant un solde négatif sont refusées.";
            case "SEUIL_ALERTE_SOLDE_CONGE_FAIBLE" ->
                    "L'alerte de solde de congé faible n'est plus émise.";
            case "SEUIL_ANCIENNETE_BONIFICATION_ANS", "PALIER_ANNEES_BONIFICATION",
                    "JOURS_BONIFICATION_PAR_PALIER" ->
                    "La bonification d'ancienneté n'est plus calculée.";
            case "SEUIL_RETARD_ASSIDUITE_MIN", "MONTANT_PRIME_ASSIDUITE" ->
                    "La prime d'assiduité n'est plus accordée par le calcul de paie.";
            case "SEUIL_RETARD_PENALITE_PP_MIN", "SEUIL_CONGES_PENALITE_PP_JOURS", "TAUX_PENALITE_PP" ->
                    "La pénalité retard/congé sur la prime de performance n'est plus appliquée.";
            case "EXONERATION_HS_IRSA_ACTIVE", "PLAFOND_MENSUEL_HS_EXONEREES" ->
                    "L'exonération IRSA des heures supplémentaires n'est plus appliquée.";
            case "CONGE_DECOMPTE_FERIES" ->
                    "Le traitement des jours fériés dans les congés débitant le solde est désactivé ; sans version active, le calcul des congés concernés est bloqué.";
            case "SEUIL_2ND_REPAS_1", "MONTANT_2ND_REPAS_1" ->
                    "Le deuxième repas de la première tranche n'est plus accordé.";
            case "SEUIL_2ND_REPAS_2", "MONTANT_2ND_REPAS_2" ->
                    "Le deuxième repas majoré n'est plus accordé ; la première tranche reste applicable si elle est active.";
            default -> null;
        };
    }

    public boolean isHistoriqueNonConsomme() {
        if (codeParametre == null) return false;
        return switch (codeParametre.toUpperCase()) {
            case "SEUIL_HEURES_SEMAINE_COMPENSATION",
                 "EXONERATION_HS_IRSA_ACTIVE",
                 "PLAFOND_MENSUEL_HS_EXONEREES" -> true;
            default -> false;
        };
    }

    public String getStatutTemporel() {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (dateDebut == null) return "NON_DATE";
        if (dateDebut.toLocalDate().isAfter(today)) return "PROGRAMME";
        if (dateFin != null && dateFin.toLocalDate().isBefore(today)) return "ARCHIVE";
        return "EN_VIGUEUR";
    }

    public boolean isPeutBasculer() {
        return !"CONGE_DECOMPTE_FERIES".equalsIgnoreCase(codeParametre)
                && Boolean.TRUE.equals(estDesactivable) && "EN_VIGUEUR".equals(getStatutTemporel()) && getEffetDesactivation() != null;
    }

    public boolean isPeutVersionner() { return !"PROGRAMME".equals(getStatutTemporel()); }
    public boolean isPeutModifier() { return true; }

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
        String sql = "INSERT INTO parametre_global (code_parametre, valeur, description, categorie, date_debut, date_fin, est_actif, est_desactivable) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_parametre";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre);
            ps.setString(2, valeur);
            ps.setString(3, description);
            ps.setString(4, categorie);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setBoolean(7, estActif != null ? estActif : true);
            ps.setBoolean(8, estDesactivable != null ? estDesactivable : false);

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
        String sql = "UPDATE parametre_global SET code_parametre = ?, valeur = ?, description = ?, categorie = ?, date_debut = ?, date_fin = ?, est_actif = ?, est_desactivable = ? " +
                     "WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, codeParametre);
            ps.setString(2, valeur);
            ps.setString(3, description);
            ps.setString(4, categorie);
            ps.setDate(5, dateDebut);
            ps.setDate(6, dateFin);
            ps.setBoolean(7, estActif != null ? estActif : true);
            ps.setBoolean(8, estDesactivable != null ? estDesactivable : false);
            ps.setInt(9, idParametre);
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
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin, " +
                     "COALESCE(est_actif, TRUE) AS est_actif, COALESCE(est_desactivable, FALSE) AS est_desactivable " +
                     "FROM parametre_global WHERE id_parametre = ?";
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
                    item.estActif = rs.getBoolean("est_actif");
                    item.estDesactivable = rs.getBoolean("est_desactivable");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<ParametreGlobal> listAll(Connection c) throws Exception {
        return lister(c, null, false);
    }

    /** Filtre les paramètres par catégorie avec la comparaison exacte déjà utilisée par l'API. */
    public static List<ParametreGlobal> listByCategorieExact(String categorie, Connection c) throws Exception {
        if (categorie == null || categorie.isBlank()) return listAll(c);
        return lister(c, categorie, true);
    }

    private static List<ParametreGlobal> lister(Connection c, String categorie, boolean exact) throws Exception {
        String sql = "SELECT id_parametre, code_parametre, valeur, description, categorie, date_debut, date_fin, " +
                     "COALESCE(est_actif, TRUE) AS est_actif, COALESCE(est_desactivable, FALSE) AS est_desactivable " +
                     "FROM parametre_global";
        if (categorie != null) sql += exact ? " WHERE categorie = ?" : " WHERE UPPER(categorie) = UPPER(?)";
        sql += categorie == null ? " ORDER BY categorie ASC, code_parametre ASC" : " ORDER BY code_parametre ASC";
        List<ParametreGlobal> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (categorie != null) ps.setString(1, exact ? categorie : categorie.trim());
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
                    item.estActif = rs.getBoolean("est_actif");
                    item.estDesactivable = rs.getBoolean("est_desactivable");
                    list.add(item);
                }
            }
        }
        return list;
    }

    public static List<ParametreGlobal> listByCategorie(String categorie, Connection c) throws Exception {
        if (categorie == null || categorie.isBlank()) return listAll(c);
        return lister(c, categorie, false);
    }

    /**
     * Représente l'état complet d'un paramètre (valeur, actif/inactif, désactivable).
     */
    public static class ParametreEtat {
        private final String codeParametre;
        private final String valeur;
        private final boolean estActif;
        private final boolean estDesactivable;

        public ParametreEtat(String codeParametre, String valeur, boolean estActif, boolean estDesactivable) {
            this.codeParametre = codeParametre;
            this.valeur = valeur;
            this.estActif = estActif;
            this.estDesactivable = estDesactivable;
        }

        public String getCodeParametre() { return codeParametre; }
        public String getValeur() { return valeur; }
        public boolean isEstActif() { return estActif; }
        public boolean isEstDesactivable() { return estDesactivable; }
    }

    /** Résultat triétat afin qu'une coupure volontaire ne soit jamais confondue avec une absence. */
    public enum ResolutionStatus { ACTIVE, DISABLED, MISSING }

    public record ParametreResolution(ResolutionStatus status, String valeur) {
        public boolean isActive() { return status == ResolutionStatus.ACTIVE; }
        public boolean isDisabled() { return status == ResolutionStatus.DISABLED; }
        public boolean isMissing() { return status == ResolutionStatus.MISSING; }
    }

    public static ParametreResolution resolve(
            java.util.Map<String, ParametreEtat> etatMap, String code) {
        if (etatMap == null || code == null) {
            return new ParametreResolution(ResolutionStatus.MISSING, null);
        }
        ParametreEtat parameter = etatMap.get(code.trim().toUpperCase());
        if (parameter == null) {
            return new ParametreResolution(ResolutionStatus.MISSING, null);
        }
        return new ParametreResolution(
                parameter.isEstActif() ? ResolutionStatus.ACTIVE : ResolutionStatus.DISABLED,
                parameter.getValeur());
    }

    /**
     * Charge l'état complet de tous les paramètres en vigueur à ce jour.
     */
    /**
     * Charge les paramètres <b>à la date courante du système</b>.
     * Uniquement pour les contextes d'action en temps réel (validation métier, affichage dashboard).
     * Pour tout calcul historique ou de campagne, utiliser {@link #loadEtatMapAtDate(Date, Connection)}.
     */
    public static java.util.Map<String, ParametreEtat> loadEtatMap(Connection c) {
        return loadEtatMapAtDate(new Date(System.currentTimeMillis()), c);
    }

    /**
     * Charge l'état complet des paramètres à une date donnée.
     */
    private static final class DatedParameterMap<V> extends java.util.HashMap<String, V> {
        final Date date;
        final java.util.Map<String, java.util.List<String>> intervals = new java.util.HashMap<>();
        DatedParameterMap(Date date) {
            this.date = date;
        }
        DatedParameterMap(DatedParameterMap<?> source) {
            this.date = source.date;
            this.intervals.putAll(source.intervals);
        }
        String missing(String key) { return " au " + date + "; versions=" + intervals.getOrDefault(key.toUpperCase(), java.util.List.of()); }
    }

    private static String temporalContext(java.util.Map<?, ?> map, String key) {
        return map instanceof DatedParameterMap<?> dated ? dated.missing(key) : "";
    }

    public static java.util.Map<String, ParametreEtat> loadEtatMapAtDate(Date dateRef, Connection c) {
        if (dateRef == null) throw new IllegalArgumentException("La date métier des paramètres globaux est obligatoire.");
        Date d = dateRef;
        DatedParameterMap<ParametreEtat> map = new DatedParameterMap<>(d);
        String sql = "SELECT code_parametre, date_debut, date_fin, valeur, " +
                     "COALESCE(est_actif, TRUE) AS est_actif, COALESCE(est_desactivable, FALSE) AS est_desactivable " +
                     "FROM parametre_global ORDER BY date_debut ASC, id_parametre ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code_parametre").toUpperCase();
                    Date debut = rs.getDate("date_debut");
                    Date fin = rs.getDate("date_fin");
                    map.intervals.computeIfAbsent(code, key -> new java.util.ArrayList<>())
                            .add("[" + debut + ", " + fin + "]");
                    if (debut == null || debut.after(d) || (fin != null && fin.before(d))) continue;
                    if (map.containsKey(code)) throw new IllegalStateException(
                            "AMBIGUOUS_CONFIGURATION: paramètre " + code + " au " + d);
                    map.put(code, new ParametreEtat(
                        code,
                        rs.getString("valeur"),
                        rs.getBoolean("est_actif"),
                        rs.getBoolean("est_desactivable")
                    ));
                }
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Impossible de charger l'état des paramètres au " + d + ".", e);
        }

        return map;
    }

    /** Projette les paramètres actifs depuis un état daté déjà chargé, sans nouvelle lecture SQL. */
    public static java.util.Map<String, String> activeValues(
            java.util.Map<String, ParametreEtat> states) {
        if (states == null) return java.util.Map.of();
        java.util.Map<String, String> values = states instanceof DatedParameterMap<?> dated
                ? new DatedParameterMap<String>(dated) : new java.util.HashMap<>();
        states.forEach((code, state) -> {
            if (state.isEstActif()) values.put(code, state.getValeur());
        });
        return values;
    }

    /**
     * Charge les paramètres actifs (valeur uniquement) sous forme de Map clé-valeur.
     * Tout paramètre désactivé (est_actif = FALSE) est exclu.
     */
    /**
     * Charge les paramètres actifs <b>à la date courante du système</b>.
     * Réservé aux contrôles opérationnels en temps réel (prescription de solde, seuils de validation).
     * Pour tout calcul portant sur une date historique, utiliser {@link #loadActiveMapAtDate(Date, Connection)}.
     */
    public static java.util.Map<String, String> loadActiveMap(Connection c) {
        return loadActiveMapAtDate(new Date(System.currentTimeMillis()), c);
    }

    public static java.util.Map<String, String> loadActiveMapAtDate(Date dateRef, Connection c) {
        if (dateRef == null) {
            throw new IllegalArgumentException("La date métier des paramètres globaux est obligatoire.");
        }
        return activeValues(loadEtatMapAtDate(dateRef, c));
    }

    public static java.util.Map<String, String> loadActiveMapByCategorie(String categorie, Connection c) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        String sql = "SELECT code_parametre, valeur FROM parametre_global " +
                     "WHERE UPPER(categorie) = UPPER(?) AND date_debut <= CURRENT_DATE AND (date_fin IS NULL OR date_fin >= CURRENT_DATE) " +
                     "AND COALESCE(est_actif, TRUE) = TRUE " +
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
     * Vérifie si un paramètre est actif en distinguant INTROUVABLE (repli fallback) d'INACTIF (coupure volontaire).
     * @param defaultIfMissing Fallback technique si la clé n'existe pas en base
     */
    public static boolean isActif(java.util.Map<String, ParametreEtat> etatMap, String code, boolean defaultIfMissing) {
        if (etatMap == null || code == null) return defaultIfMissing;
        ParametreEtat p = etatMap.get(code.toUpperCase());
        if (p == null) return defaultIfMissing; // INTROUVABLE -> repli technique
        return p.isEstActif();                  // TROUVÉ -> respecte le choix explicite actif/inactif
    }

    /**
     * Récupère un double en distinguant le cas 'INTROUVABLE' (fallback technique) du cas 'INACTIF' (coupure).
     */
    public static double getDouble(java.util.Map<String, ParametreEtat> etatMap, String code, double fallbackIfMissing, double valueWhenInactive) {
        if (etatMap == null || code == null) return fallbackIfMissing;
        ParametreEtat p = etatMap.get(code.toUpperCase());
        if (p == null) return fallbackIfMissing;       // Absent en base -> fallback technique
        if (!p.isEstActif()) return valueWhenInactive; // Volontairement inactif -> coupure
        try {
            return Double.parseDouble(p.getValeur());
        } catch (Exception e) {
            return fallbackIfMissing;
        }
    }

    /**
     * Récupère un int en distinguant le cas 'INTROUVABLE' du cas 'INACTIF'.
     */
    public static int getInt(java.util.Map<String, ParametreEtat> etatMap, String code, int fallbackIfMissing, int valueWhenInactive) {
        if (etatMap == null || code == null) return fallbackIfMissing;
        ParametreEtat p = etatMap.get(code.toUpperCase());
        if (p == null) return fallbackIfMissing;
        if (!p.isEstActif()) return valueWhenInactive;
        try {
            return Integer.parseInt(p.getValeur());
        } catch (Exception e) {
            return fallbackIfMissing;
        }
    }

    /**
     * Récupère un String en distinguant le cas 'INTROUVABLE' du cas 'INACTIF'.
     */
    public static String getString(java.util.Map<String, ParametreEtat> etatMap, String code, String fallbackIfMissing, String valueWhenInactive) {
        if (etatMap == null || code == null) return fallbackIfMissing;
        ParametreEtat p = etatMap.get(code.toUpperCase());
        if (p == null) return fallbackIfMissing;
        if (!p.isEstActif()) return valueWhenInactive;
        return p.getValeur();
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
     * Annule une version PROGRAMMÉE (date_debut > aujourd'hui) :
     * 1. Vérifie que la version n'est pas encore en vigueur.
     * 2. Réouvre le date_fin de la version précédente (la remet à NULL).
     * 3. Supprime la version programmée.
     * La transaction est gérée par l'appelant.
     */
    public static void annulerProgramme(int idParametre, Connection c) throws Exception {
        ParametreGlobal item = new ParametreGlobal();
        ParametreGlobal cible = (ParametreGlobal) item.rechercherParId(idParametre, c);
        if (cible == null) {
            throw new IllegalArgumentException("Paramètre global introuvable : " + idParametre);
        }
        java.time.LocalDate today = java.time.LocalDate.now();
        if (cible.getDateDebut() == null || !cible.getDateDebut().toLocalDate().isAfter(today)) {
            throw new IllegalStateException(
                "Seule une version PROGRAMMÉE (date_debut dans le futur) peut être annulée. "
                + "Ce paramètre est déjà en vigueur ou archivé.");
        }
        java.sql.Date veille = java.sql.Date.valueOf(cible.getDateDebut().toLocalDate().minusDays(1));
        String reopenSql = "UPDATE parametre_global SET date_fin = NULL "
                + "WHERE UPPER(code_parametre) = UPPER(?) AND id_parametre != ? AND date_fin = ?";
        try (PreparedStatement ps = c.prepareStatement(reopenSql)) {
            ps.setString(1, cible.getCodeParametre());
            ps.setInt(2, idParametre);
            ps.setDate(3, veille);
            int reopened = ps.executeUpdate();
            if (reopened == 0) {
                String fallbackReopen = "UPDATE parametre_global SET date_fin = NULL "
                        + "WHERE id_parametre = ("
                        + "  SELECT id_parametre FROM parametre_global "
                        + "  WHERE UPPER(code_parametre) = UPPER(?) AND id_parametre != ? AND date_debut < ? "
                        + "  ORDER BY date_debut DESC LIMIT 1"
                        + ")";
                try (PreparedStatement ps2 = c.prepareStatement(fallbackReopen)) {
                    ps2.setString(1, cible.getCodeParametre());
                    ps2.setInt(2, idParametre);
                    ps2.setDate(3, cible.getDateDebut());
                    ps2.executeUpdate();
                }
            }
        }
        String deleteSql = "DELETE FROM parametre_global WHERE id_parametre = ?";
        try (PreparedStatement ps = c.prepareStatement(deleteSql)) {
            ps.setInt(1, idParametre);
            ps.executeUpdate();
        }
    }

    /**
     * Retourne la valeur active d'un paramètre global à ce jour.
     */
    /**
     * Retourne la valeur active d'un paramètre global <b>à la date du jour</b>.
     * Usage exclusif : affichage UI, vérifications administratives en temps réel.
     * Ne jamais utiliser dans un moteur de calcul daté (paie, STC, congés) :
     * utiliser {@link #loadEtatMapAtDate(Date, Connection)} avec la date métier concernée.
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

    public static java.math.BigDecimal getBigDecimal(java.util.Map<String, String> params, String key,
                                                      java.math.BigDecimal defaultValue) {
        if (params == null || key == null) return defaultValue;
        String value = params.get(key.toUpperCase());
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return new java.math.BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static java.math.BigDecimal getBigDecimal(java.util.Map<String, ParametreEtat> params, String code,
                                                      java.math.BigDecimal defaultValue,
                                                      java.math.BigDecimal valueWhenInactive) {
        if (params == null || code == null) return defaultValue;
        ParametreEtat parameter = params.get(code.toUpperCase());
        if (parameter == null) return defaultValue;
        if (!parameter.isEstActif()) return valueWhenInactive;
        try {
            return new java.math.BigDecimal(parameter.getValeur().trim());
        } catch (RuntimeException e) {
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

    /**
     * Résolution stricte d'un entier strictement positif sans fallback silencieux.
     * Lève des erreurs explicites en cas d'absence ou de valeur non valide.
     */
    public static int requirePositiveInt(java.util.Map<String, String> params, String key) {
        if (params == null || key == null) {
            throw new IllegalStateException("TCONGE_REFERENCE_DURATION_MISSING: paramètre " + key + " non chargé.");
        }
        String val = params.get(key.toUpperCase());
        if (val == null || val.trim().isEmpty()) {
            throw new IllegalStateException("TCONGE_REFERENCE_DURATION_MISSING: le paramètre obligatoire " + key + " est introuvable ou vide" + temporalContext(params, key) + ".");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("TCONGE_REFERENCE_DURATION_INVALID: la valeur du paramètre " + key + " ('" + val + "') n'est pas un entier valide.", e);
        }
        if (parsed <= 0) {
            throw new IllegalStateException("TCONGE_REFERENCE_DURATION_INVALID: la valeur du paramètre " + key + " doit être strictement positive (obtenu: " + parsed + ").");
        }
        return parsed;
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
    public Boolean getEstActif() { return estActif != null ? estActif : true; }
    public void setEstActif(Boolean estActif) { this.estActif = estActif; }
    public Boolean getEstDesactivable() { return estDesactivable != null ? estDesactivable : false; }
    public void setEstDesactivable(Boolean estDesactivable) { this.estDesactivable = estDesactivable; }
}
