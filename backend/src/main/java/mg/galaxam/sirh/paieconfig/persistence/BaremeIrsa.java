package mg.galaxam.sirh.paieconfig.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class BaremeIrsa extends ClassMere {

    private Integer idTranche;
    private Integer idConfigLegale;
    private Integer numeroTranche;
    private BigDecimal trancheMin;
    private BigDecimal trancheMax;
    private BigDecimal taux;
    private Date dateDebut;
    private Date dateFin;

    public BaremeIrsa() {
        this.setNomTable("BAREME_IRSA");
    }

    @Override
    public Integer getTuppleID() {
        return idTranche;
    }

    @Override
    public String getAttributIDName() {
        return "id_tranche";
    }

    public void valider() {
        if (numeroTranche == null || numeroTranche < 1) {
            throw new IllegalArgumentException("Le numéro de tranche doit être supérieur ou égal à 1.");
        }
        if (trancheMin == null || trancheMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Le montant minimum de la tranche ne peut pas être négatif.");
        }
        if (trancheMax != null && trancheMax.compareTo(trancheMin) <= 0) {
            throw new IllegalArgumentException("Le montant maximum (" + trancheMax + " Ar) doit être strictement supérieur au montant minimum (" + trancheMin + " Ar).");
        }
        if (taux == null || taux.compareTo(BigDecimal.ZERO) < 0 || taux.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Le taux IRSA doit être compris entre 0% et 100% (valeur fournie : " + taux + "%).");
        }
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début d'application est obligatoire.");
        }
        if (dateFin != null && dateFin.before(dateDebut)) {
            throw new IllegalArgumentException("La date de fin (" + dateFin + ") ne peut pas être antérieure à la date de début (" + dateDebut + ").");
        }
    }

    /**
     * Vérifie la continuité du barème POUR LA MÊMME PÉRIODE seulement.
     * Deux tranches de périodes différentes (ex : LF_2024 et LF_2026) ne se chevauchent pas.
     */
    public void validerContinuite(Connection c) throws Exception {
        valider();
        if (c == null) return;
        List<BaremeIrsa> existingList = listAll(c);
        for (BaremeIrsa other : existingList) {
            if (this.idTranche != null && this.idTranche.equals(other.getIdTranche())) {
                continue; // Skip self when modifying
            }

            // ------ NOUVEAU : ne comparer qu'avec les tranches de la même période ------
            boolean samePeriod = java.util.Objects.equals(
                this.dateDebut != null ? this.dateDebut.toString() : null,
                other.getDateDebut() != null ? other.getDateDebut().toString() : null
            ) && java.util.Objects.equals(
                this.dateFin != null ? this.dateFin.toString() : null,
                other.getDateFin() != null ? other.getDateFin().toString() : null
            );
            if (!samePeriod) continue;
            // -----------------------------------------------------------------------

            // Check duplicate numeroTranche
            if (this.numeroTranche.equals(other.getNumeroTranche())) {
                throw new IllegalArgumentException("Une tranche avec le numéro " + this.numeroTranche + " existe déjà.");
            }

            // Check overlap between intervals [trancheMin, trancheMax]
            BigDecimal minA = this.trancheMin;
            BigDecimal maxA = this.trancheMax;
            BigDecimal minB = other.getTrancheMin();
            BigDecimal maxB = other.getTrancheMax();

            boolean overlaps = false;
            if (maxA == null && maxB == null) {
                overlaps = true; // Deux tranches ouvertes vers l'infini
            } else if (maxA == null) {
                if (minA.compareTo(maxB) < 0) overlaps = true;
            } else if (maxB == null) {
                if (minB.compareTo(maxA) < 0) overlaps = true;
            } else {
                BigDecimal startMax = minA.max(minB);
                BigDecimal endMin = maxA.min(maxB);
                if (startMax.compareTo(endMin) < 0) overlaps = true;
            }

            if (overlaps) {
                throw new IllegalArgumentException("Chevauchement de barème détecté : l'intervalle [" +
                        minA.toPlainString() + " Ar - " + (maxA != null ? maxA.toPlainString() + " Ar" : "Illimité") +
                        "] chevauche la tranche existante #" + other.getNumeroTranche() + " [" +
                        minB.toPlainString() + " Ar - " + (maxB != null ? maxB.toPlainString() + " Ar" : "Illimité") + "].");
            }
        }
    }

    /**
     * Revalorisation du barème IRSA :
     * 1. Ferme les tranches de la période en vigueur (date_fin = nouvelleDateDebut - 1 jour)
     * 2. Crée les nouvelles tranches avec la nouvelleDateDebut et sans date de fin.
     * Tout dans la même transaction fournie en paramètre.
     */
    public static void revaloriser(
            Connection c,
            Date nouvelleDateDebut,
            List<BaremeIrsa> nouvellesTranches) throws Exception {

        if (nouvelleDateDebut == null) {
            throw new IllegalArgumentException("La date d'application est obligatoire.");
        }
        if (nouvellesTranches == null || nouvellesTranches.isEmpty()) {
            throw new IllegalArgumentException("Le nouveau barème doit contenir au moins une tranche.");
        }

        Date today = new Date(System.currentTimeMillis());
        if (!nouvelleDateDebut.after(today)) {
            throw new IllegalArgumentException("La date d'application doit être postérieure à aujourd'hui.");
        }

        // --- 1. Identifier les tranches EN VIGUEUR (date_fin IS NULL ou date_fin >= today) ---
        String sqlActives = "UPDATE bareme_irsa SET date_fin = ? WHERE (date_fin IS NULL OR date_fin >= ?) AND date_debut <= ?";
        java.sql.Date veille = new java.sql.Date(nouvelleDateDebut.getTime() - 86_400_000L);
        try (PreparedStatement ps = c.prepareStatement(sqlActives)) {
            ps.setDate(1, veille);
            ps.setDate(2, today);
            ps.setDate(3, today);
            ps.executeUpdate();
        }

        // --- 2. Créer les nouvelles tranches ---
        for (BaremeIrsa t : nouvellesTranches) {
            t.setDateDebut(nouvelleDateDebut);
            t.setDateFin(null); // En vigueur indéfiniment
            t.setIdTranche(null); // Forcer INSERT
            t.save(c); // validerContinuite interne
        }
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        validerContinuite(c);
        String sql = "INSERT INTO bareme_irsa (id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_tranche";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idConfigLegale != null) ps.setInt(1, idConfigLegale);
            else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setInt(2, numeroTranche);
            ps.setBigDecimal(3, trancheMin);
            ps.setBigDecimal(4, trancheMax);
            ps.setBigDecimal(5, taux);
            ps.setDate(6, dateDebut);
            ps.setDate(7, dateFin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    this.idTranche = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        validerContinuite(c);
        String sql = "UPDATE bareme_irsa SET id_config_legale = ?, numero_tranche = ?, tranche_min = ?, tranche_max = ?, taux = ?, " +
                     "date_debut = ?, date_fin = ? WHERE id_tranche = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (idConfigLegale != null) ps.setInt(1, idConfigLegale);
            else ps.setNull(1, java.sql.Types.INTEGER);
            ps.setInt(2, numeroTranche);
            ps.setBigDecimal(3, trancheMin);
            ps.setBigDecimal(4, trancheMax);
            ps.setBigDecimal(5, taux);
            ps.setDate(6, dateDebut);
            ps.setDate(7, dateFin);
            ps.setInt(8, idTranche);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM bareme_irsa WHERE id_tranche = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idTranche);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank()) return null;
        return rechercherParId(Integer.parseInt(id), c);
    }

    public ClassMere rechercherParId(Integer id, Connection c) throws Exception {
        String sql = "SELECT id_tranche, id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin FROM bareme_irsa WHERE id_tranche = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BaremeIrsa item = new BaremeIrsa();
                    item.idTranche = rs.getInt("id_tranche");
                    int cfg = rs.getInt("id_config_legale");
                    item.idConfigLegale = rs.wasNull() ? null : cfg;
                    item.numeroTranche = rs.getInt("numero_tranche");
                    item.trancheMin = rs.getBigDecimal("tranche_min");
                    item.trancheMax = rs.getBigDecimal("tranche_max");
                    item.taux = rs.getBigDecimal("taux");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    return item;
                }
            }
        }
        return null;
    }

    public static List<BaremeIrsa> listAll(Connection c) throws Exception {
        String sql = "SELECT id_tranche, id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin FROM bareme_irsa ORDER BY date_debut DESC, numero_tranche ASC";
        List<BaremeIrsa> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BaremeIrsa item = new BaremeIrsa();
                item.idTranche = rs.getInt("id_tranche");
                int cfg = rs.getInt("id_config_legale");
                item.idConfigLegale = rs.wasNull() ? null : cfg;
                item.numeroTranche = rs.getInt("numero_tranche");
                item.trancheMin = rs.getBigDecimal("tranche_min");
                item.trancheMax = rs.getBigDecimal("tranche_max");
                item.taux = rs.getBigDecimal("taux");
                item.dateDebut = rs.getDate("date_debut");
                item.dateFin = rs.getDate("date_fin");
                list.add(item);
            }
        }
        return list;
    }

    public static List<BaremeIrsa> listByConfigLegale(int idConfigLegale, Connection c) throws Exception {
        String sql = "SELECT id_tranche, id_config_legale, numero_tranche, tranche_min, tranche_max, taux, date_debut, date_fin FROM bareme_irsa WHERE id_config_legale = ? ORDER BY numero_tranche ASC";
        List<BaremeIrsa> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idConfigLegale);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BaremeIrsa item = new BaremeIrsa();
                    item.idTranche = rs.getInt("id_tranche");
                    item.idConfigLegale = idConfigLegale;
                    item.numeroTranche = rs.getInt("numero_tranche");
                    item.trancheMin = rs.getBigDecimal("tranche_min");
                    item.trancheMax = rs.getBigDecimal("tranche_max");
                    item.taux = rs.getBigDecimal("taux");
                    item.dateDebut = rs.getDate("date_debut");
                    item.dateFin = rs.getDate("date_fin");
                    list.add(item);
                }
            }
        }
        return list;
    }

    // Getters / Setters
    public Integer getIdTranche() { return idTranche; }
    public void setIdTranche(Integer idTranche) { this.idTranche = idTranche; }
    public Integer getIdConfigLegale() { return idConfigLegale; }
    public void setIdConfigLegale(Integer idConfigLegale) { this.idConfigLegale = idConfigLegale; }
    public Integer getNumeroTranche() { return numeroTranche; }
    public void setNumeroTranche(Integer numeroTranche) { this.numeroTranche = numeroTranche; }
    public BigDecimal getTrancheMin() { return trancheMin; }
    public void setTrancheMin(BigDecimal trancheMin) { this.trancheMin = trancheMin; }
    public BigDecimal getTrancheMax() { return trancheMax; }
    public void setTrancheMax(BigDecimal trancheMax) { this.trancheMax = trancheMax; }
    public BigDecimal getTaux() { return taux; }
    public void setTaux(BigDecimal taux) { this.taux = taux; }
    public Date getDateDebut() { return dateDebut; }
    public void setDateDebut(Date dateDebut) { this.dateDebut = dateDebut; }
    public Date getDateFin() { return dateFin; }
    public void setDateFin(Date dateFin) { this.dateFin = dateFin; }
}
