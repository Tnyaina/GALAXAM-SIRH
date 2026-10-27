package mg.galaxam.sirh.securite;

import bean.ClassMere;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

public class JournalConnexion extends ClassMere {

    private Integer idJournal;
    private Integer idUtilisateur;
    private Timestamp dateHeure;
    private String statut; // ex: "SUCCES", "ECHEC_MDP", "COMPTE_DESACTIVE"
    private String adresseIp;
    private String userAgent;

    public JournalConnexion() {
        this.setNomTable("JOURNAL_CONNEXION");
    }

    @Override
    public Integer getTuppleID() { return this.idJournal; }

    @Override
    public String getAttributIDName() { return "id_journal"; }

    @Override
    public void controler(Connection c) throws Exception {
        if (idUtilisateur == null) throw new IllegalArgumentException("L'utilisateur est obligatoire.");
        if (statut == null || statut.isBlank()) throw new IllegalArgumentException("Le statut est obligatoire.");
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO journal_connexion (id_utilisateur, statut, adresse_ip, user_agent) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idUtilisateur);
            ps.setString(2, statut);
            ps.setString(3, adresseIp);
            ps.setString(4, userAgent);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) this.idJournal = rs.getInt(1);
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        // Un journal de connexion ne devrait normalement pas être modifié
        throw new UnsupportedOperationException("Un journal de connexion ne peut pas être modifié.");
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM journal_connexion WHERE id_journal=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idJournal);
            ps.executeUpdate();
        }
    }

    @Override
    protected ClassMere rechercherParId(String idRecherche, Connection c) throws Exception {
        String sql = "SELECT * FROM journal_connexion WHERE id_journal=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(idRecherche));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    JournalConnexion j = new JournalConnexion();
                    j.idJournal = rs.getInt("id_journal");
                    j.idUtilisateur = rs.getInt("id_utilisateur");
                    j.dateHeure = rs.getTimestamp("date_heure");
                    j.statut = rs.getString("statut");
                    j.adresseIp = rs.getString("adresse_ip");
                    j.userAgent = rs.getString("user_agent");
                    return j;
                }
            }
        }
        return null;
    }

    // Getters & Setters
    public Integer getIdJournal() { return idJournal; }
    public Integer getIdUtilisateur() { return idUtilisateur; }
    public void setIdUtilisateur(Integer idUtilisateur) { this.idUtilisateur = idUtilisateur; }
    public Timestamp getDateHeure() { return dateHeure; }
    public void setDateHeure(Timestamp dateHeure) { this.dateHeure = dateHeure; }
    public String getStatut() { return statut; }
    public void setStatut(String statut) { this.statut = statut; }
    public String getAdresseIp() { return adresseIp; }
    public void setAdresseIp(String adresseIp) { this.adresseIp = adresseIp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
}
