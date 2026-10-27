package mg.galaxam.sirh.conge.application;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;

/** Contrôle organisationnel des absences sur les affectations réellement applicables. */
public final class CongeContinuiteService {
    private final EmploymentContextResolver emplois = new EmploymentContextResolver();

    public record Segment(LocalDate debut, LocalDate fin, Integer idAffectation,
            Integer idFonction, String nomFonction, Integer idSite, String nomSite,
            String nomDepartement) {}

    public record Collision(int idEmploye, String matricule, String nom, String prenom,
            String photoUrl, int idDemandeConge, String typeConge, String statut,
            LocalDate dateDebut, LocalDate dateFin, boolean demiJournee, String momentDemiJournee,
            LocalDate debutConflit, LocalDate finConflit, String nomFonction, String nomSite) {}

    public record Analyse(boolean actifSurPeriode, List<Segment> segments,
            List<Collision> collisions, int totalEmployesMemePoste) {}

    public Analyse analyser(int employe, LocalDate debut, LocalDate fin, Integer demandeExclue,
            boolean demiJournee, String momentDemiJournee, Connection c) throws SQLException {
        if (debut == null || fin == null || fin.isBefore(debut))
            throw new IllegalArgumentException("Période de congé invalide.");
        List<Segment> segments = new ArrayList<>();
        boolean actif = true;
        var contextesParJour = emplois.resolveContinuityDays(employe, debut, fin, c);
        for (LocalDate jour = debut; !jour.isAfter(fin); jour = jour.plusDays(1)) {
            var contexte = contextesParJour.get(jour);
            if (contexte == null || !contexte.enPoste() || contexte.idAffectation() == null) {
                actif = false;
                continue;
            }
            Segment precedent = segments.isEmpty() ? null : segments.get(segments.size() - 1);
            if (precedent != null && precedent.fin().plusDays(1).equals(jour)
                    && precedent.idAffectation().equals(contexte.idAffectation())) {
                segments.set(segments.size() - 1, new Segment(precedent.debut(), jour,
                        precedent.idAffectation(), precedent.idFonction(), precedent.nomFonction(),
                        precedent.idSite(), precedent.nomSite(), precedent.nomDepartement()));
            } else {
                segments.add(new Segment(jour, jour, contexte.idAffectation(), contexte.idFonction(),
                        contexte.fonctionNom(), contexte.idSite(), contexte.nomSite(), contexte.departementNom()));
            }
        }

        LinkedHashMap<Integer, Collision> collisions = new LinkedHashMap<>();
        int total = 1;
        for (Segment segment : segments) {
            if (segment.idFonction() == null || segment.idSite() == null) continue;
            total = Math.max(total, compterCollegues(segment, c));
            for (Collision collision : chercherCollisions(employe, segment, demandeExclue, c)) {
                if (demiJournee && collision.demiJournee() && debut.equals(fin)
                        && collision.dateDebut().equals(debut) && collision.dateFin().equals(fin)
                        && momentDemiJournee != null && collision.momentDemiJournee() != null
                        && !momentDemiJournee.equalsIgnoreCase(collision.momentDemiJournee())) continue;
                collisions.putIfAbsent(collision.idDemandeConge(), collision);
            }
        }
        return new Analyse(actif, List.copyOf(segments), List.copyOf(collisions.values()), total);
    }

    private int compterCollegues(Segment segment, Connection c) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT a.id_employe) FROM employe_affectation_carriere a "
                + "JOIN employe e ON e.id_employe=a.id_employe AND e.est_actif=TRUE "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh r ON r.id_statut_rh=ct.id_statut_rh AND r.code='ACTIF' "
                + "WHERE a.id_fonction=? AND a.id_site=? "
                + "AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) "
                + "AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, segment.idFonction()); ps.setInt(2, segment.idSite());
            ps.setDate(3, Date.valueOf(segment.fin())); ps.setDate(4, Date.valueOf(segment.debut()));
            ps.setDate(5, Date.valueOf(segment.fin())); ps.setDate(6, Date.valueOf(segment.debut()));
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return Math.max(1, rs.getInt(1)); }
        }
    }

    private List<Collision> chercherCollisions(int employe, Segment segment,
            Integer demandeExclue, Connection c) throws SQLException {
        String sql = "SELECT e.id_employe,e.matricule,e.nom,e.prenom,e.photo_url, "
                + "dc.id_demande_conge,dc.date_debut,dc.date_fin,dc.est_demi_journee,dc.moment_demi_journee, "
                + "t.libelle AS type_conge,s.code AS statut, "
                + "GREATEST(dc.date_debut,a.date_debut,ct.date_debut,?) AS debut_conflit, "
                + "LEAST(dc.date_fin,COALESCE(a.date_fin,?),COALESCE(ct.date_fin,?),?) AS fin_conflit "
                + "FROM demande_conge dc JOIN employe e ON e.id_employe=dc.id_employe "
                + "JOIN employe_affectation_carriere a ON a.id_employe=e.id_employe "
                + "JOIN contrat_travail ct ON ct.id_contrat=a.id_contrat "
                + "JOIN ref_statut_rh r ON r.id_statut_rh=ct.id_statut_rh AND r.code='ACTIF' "
                + "JOIN ref_statut_demande_conge s ON s.id_statut_demande_conge=dc.id_statut_demande_conge "
                + "JOIN ref_type_conge t ON t.id_type_conge=dc.id_type_conge "
                + "WHERE a.id_fonction=? AND a.id_site=? AND e.id_employe<>? "
                + "AND dc.id_demande_conge<>COALESCE(?,-1) "
                + "AND s.code NOT IN ('REFUSE','ANNULE','REJETE') "
                + "AND dc.date_debut<=? AND dc.date_fin>=? "
                + "AND a.date_debut<=? AND (a.date_fin IS NULL OR a.date_fin>=?) "
                + "AND ct.date_debut<=? AND (ct.date_fin IS NULL OR ct.date_fin>=?) "
                + "AND GREATEST(dc.date_debut,a.date_debut,ct.date_debut,?) "
                + "<= LEAST(dc.date_fin,COALESCE(a.date_fin,?),COALESCE(ct.date_fin,?),?) "
                + "ORDER BY dc.date_debut,dc.id_demande_conge";
        List<Collision> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setDate(i++, Date.valueOf(segment.debut()));
            ps.setDate(i++, Date.valueOf(segment.fin()));
            ps.setDate(i++, Date.valueOf(segment.fin()));
            ps.setDate(i++, Date.valueOf(segment.fin()));
            ps.setInt(i++, segment.idFonction()); ps.setInt(i++, segment.idSite());
            ps.setInt(i++, employe); ps.setObject(i++, demandeExclue);
            ps.setDate(i++, Date.valueOf(segment.fin())); ps.setDate(i++, Date.valueOf(segment.debut()));
            ps.setDate(i++, Date.valueOf(segment.fin())); ps.setDate(i++, Date.valueOf(segment.debut()));
            ps.setDate(i++, Date.valueOf(segment.fin())); ps.setDate(i++, Date.valueOf(segment.debut()));
            ps.setDate(i++, Date.valueOf(segment.debut()));
            ps.setDate(i++, Date.valueOf(segment.fin()));
            ps.setDate(i++, Date.valueOf(segment.fin()));
            ps.setDate(i, Date.valueOf(segment.fin()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(new Collision(rs.getInt("id_employe"), rs.getString("matricule"),
                        rs.getString("nom"), rs.getString("prenom"), rs.getString("photo_url"),
                        rs.getInt("id_demande_conge"), rs.getString("type_conge"), rs.getString("statut"),
                        rs.getDate("date_debut").toLocalDate(), rs.getDate("date_fin").toLocalDate(),
                        rs.getBoolean("est_demi_journee"), rs.getString("moment_demi_journee"),
                        rs.getDate("debut_conflit").toLocalDate(), rs.getDate("fin_conflit").toLocalDate(),
                        segment.nomFonction(), segment.nomSite()));
            }
        }
        return result;
    }
}
