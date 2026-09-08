package mg.galaxam.sirh.model.conge.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.RefStatutDemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.conge.VSoldeCongeEmploye;
import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import mg.galaxam.sirh.model.dashboard.service.NotificationRhService;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.springframework.stereotype.Service;

@Service
public class CongeService {

    private final CongeCalculateurEngine calculateurEngine;
    private final CalendrierOuvrableEngine calendrierOuvrableEngine;

    @org.springframework.beans.factory.annotation.Autowired
    public CongeService(CongeCalculateurEngine calculateurEngine, CalendrierOuvrableEngine calendrierOuvrableEngine) {
        this.calculateurEngine = calculateurEngine;
        this.calendrierOuvrableEngine = calendrierOuvrableEngine != null ? calendrierOuvrableEngine : new CalendrierOuvrableEngine();
    }

    public CongeService(CongeCalculateurEngine calculateurEngine) {
        this(calculateurEngine, new CalendrierOuvrableEngine());
    }

    public BigDecimal simulerNombreJours(Date dateDebut, Date dateFin, boolean estDemiJournee) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires pour la simulation.");
        }
        return calculateurEngine.calculerNombreJours(dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee);
    }

    public DemandeConge creerDemande(DemandeConge d, Integer currentUserId, Connection c) throws Exception {
        if (d.getIdEmploye() == null) {
            throw new IllegalArgumentException("L'identifiant de l'employé est obligatoire.");
        }
        if (d.getIdTypeConge() == null) {
            throw new IllegalArgumentException("Le type de congé est obligatoire.");
        }
        if (d.getDateDebut() == null || d.getDateFin() == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }

        RefStatutDemandeConge enAttente = RefStatutDemandeConge.rechercherParCode("EN_ATTENTE", c);
        if (enAttente == null) {
            throw new IllegalStateException("Statut EN_ATTENTE non trouvé dans le référentiel.");
        }
        d.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
        d.setSaisiPar(currentUserId);

        // Bascule automatique Arrêt Maladie si > 30 jours (Art. 13)
        RefTypeConge typeConge = RefTypeConge.rechercherParId(d.getIdTypeConge(), c);
        Date originalDateFin = d.getDateFin();
        BigDecimal totalJoursOrigine = calculateurEngine.calculerNombreJours(d.getDateDebut().toLocalDate(), originalDateFin.toLocalDate(),
                d.isEstDemiJournee());

        if (typeConge != null && "MALADIE".equals(typeConge.getCode())
                && calculateurEngine.doitScinderArretMaladie(totalJoursOrigine)) {
            Date dateFinT1 = Date.valueOf(d.getDateDebut().toLocalDate().plusDays(29));
            d.setDateFin(dateFinT1);
            d.controler(c);
            d.save(c);

            if (typeConge.getIdTypeCongeBascule() != null) {
                DemandeConge d2 = new DemandeConge();
                d2.setIdEmploye(d.getIdEmploye());
                d2.setIdTypeConge(typeConge.getIdTypeCongeBascule());
                d2.setIdStatutDemandeConge(enAttente.getIdStatutDemandeConge());
                d2.setDateDebut(Date.valueOf(dateFinT1.toLocalDate().plusDays(1)));
                d2.setDateFin(originalDateFin);
                d2.setJustificatifFourni(d.isJustificatifFourni());
                d2.setCommentaire(
                        "(Bascule automatique Art. 13) " + (d.getCommentaire() != null ? d.getCommentaire() : ""));
                d2.setSaisiPar(d.getSaisiPar());
                d2.controler(c);
                d2.save(c);
            }

            NotificationRhService.creer(
                    c,
                    "MALADIE_PLAFOND_EPISODE_ATTEINT",
                    "HAUTE",
                    "Bascule Automatique Arrêt Maladie (> 30j)",
                    "L'arrêt maladie continu (" + totalJoursOrigine
                            + "j) a été automatiquement scindé : 30 jours en maintien de salaire et la suite en Maladie Sans Solde (CNaPS).",
                    "/conges/demandes/employe/" + d.getIdEmploye(),
                    d.getIdEmploye());
        } else {
            d.controler(c);
            d.save(c);

            NotificationRhService.creer(
                    c,
                    "CONGE_EN_ATTENTE",
                    "NORMALE",
                    "Nouvelle demande de congé",
                    "Une nouvelle demande de congé (" + (typeConge != null ? typeConge.getLibelle() : "Absence")
                            + ") a été enregistrée et est en attente de validation.",
                    "/conges",
                    d.getIdEmploye());
        }

        return d;
    }

    public DemandeConge validerDemande(int idDemande, Integer currentUserId, String commentaireValidation, Connection c)
            throws Exception {
        DemandeConge d = new DemandeConge().rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        d.valider(currentUserId, c);
        return d;
    }

    public DemandeConge refuserDemande(int idDemande, Integer currentUserId, String motifRefus, Connection c)
            throws Exception {
        DemandeConge d = new DemandeConge().rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        d.refuser(currentUserId, motifRefus, c);
        return d;
    }

    public DemandeConge annulerDemande(int idDemande, Integer currentUserId, String motifAnnulation, Connection c)
            throws Exception {
        DemandeConge d = new DemandeConge().rechercherParId(idDemande, c);
        if (d == null) {
            throw new IllegalArgumentException("Demande de congé introuvable (ID: " + idDemande + ").");
        }

        d.annuler(currentUserId, motifAnnulation, c);
        return d;
    }

    public java.util.Map<String, Object> verifierConflitsDemande(Integer idEmploye, Date dateDebut, Date dateFin,
            boolean estDemiJournee, String momentDemiJournee, Integer idDemandeExclue, Connection c) throws Exception {
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }

        // 1. Décompte de base
        BigDecimal nbJours = calculateurEngine.calculerNombreJours(dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee);
        res.put("nbJoursDecomptes", nbJours);
        res.put("dateDebut", dateDebut);
        res.put("dateFin", dateFin);
        res.put("estDemiJournee", estDemiJournee);
        res.put("momentDemiJournee", momentDemiJournee);

        // 2. Décompte calendaire détaillé (ouvrés, week-ends, fériés) via le moteur canonique
        java.time.LocalDate cur = dateDebut.toLocalDate();
        java.time.LocalDate end = dateFin.toLocalDate();
        int nbWeekend = 0;
        int nbOuvres = 0;
        int nbFeries = 0;

        java.util.Set<java.time.LocalDate> feries = calendrierOuvrableEngine.getDatesFeriesEtChomeesEmploye(cur, end, idEmploye, c);
        while (!cur.isAfter(end)) {
            java.time.DayOfWeek dow = cur.getDayOfWeek();
            boolean isWe = (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY);
            boolean isFerie = feries.contains(cur);

            if (isFerie) {
                nbFeries++;
            }
            if (isWe) {
                nbWeekend++;
            } else if (!isFerie) {
                nbOuvres++;
            }
            cur = cur.plusDays(1);
        }
        res.put("nbJoursOuvres", nbOuvres);
        res.put("nbJoursFeries", nbFeries);
        res.put("nbJoursWeekEnd", nbWeekend);

        if (idEmploye == null || idEmploye <= 0) {
            res.put("collisionPersonnelle", false);
            res.put("collisionsPersonnelles", java.util.List.of());
            res.put("conflitPoste", false);
            res.put("colleguesAbsents", java.util.List.of());
            return res;
        }

        // 3. Collisions personnelles (autres congés de l'employé)
        java.util.List<java.util.Map<String, Object>> colPerso = new java.util.ArrayList<>();
        String sqlOverlap = "SELECT dc.id_demande_conge, dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.moment_demi_journee, " +
                "tc.libelle AS libelle_conge, sc.code AS code_statut " +
                "FROM demande_conge dc " +
                "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "WHERE dc.id_employe = ? " +
                "AND dc.id_demande_conge != COALESCE(?, -1) " +
                "AND sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                "AND dc.date_debut <= ? AND dc.date_fin >= ?";
        try (PreparedStatement psO = c.prepareStatement(sqlOverlap)) {
            psO.setInt(1, idEmploye);
            psO.setObject(2, idDemandeExclue != null ? idDemandeExclue : -1);
            psO.setDate(3, dateFin);
            psO.setDate(4, dateDebut);
            try (ResultSet rsO = psO.executeQuery()) {
                while (rsO.next()) {
                    boolean otherDemi = rsO.getBoolean("est_demi_journee");
                    String otherMoment = rsO.getString("moment_demi_journee");
                    if (estDemiJournee && otherDemi && dateDebut.equals(dateFin) &&
                            rsO.getDate("date_debut").equals(dateDebut) &&
                            momentDemiJournee != null && otherMoment != null &&
                            !momentDemiJournee.equalsIgnoreCase(otherMoment)) {
                        continue;
                    }
                    java.util.Map<String, Object> cp = new java.util.HashMap<>();
                    cp.put("idDemandeConge", rsO.getInt("id_demande_conge"));
                    cp.put("typeConge", rsO.getString("libelle_conge"));
                    cp.put("dateDebut", rsO.getDate("date_debut"));
                    cp.put("dateFin", rsO.getDate("date_fin"));
                    cp.put("statut", rsO.getString("code_statut"));
                    colPerso.add(cp);
                }
            }
        }
        res.put("collisionPersonnelle", !colPerso.isEmpty());
        res.put("collisionsPersonnelles", colPerso);

        // 4. Détection du poste, département et site de l'employé
        String sqlPoste = "SELECT a.id_fonction, f.libelle AS nom_fonction, a.id_departement, d.libelle AS nom_departement, a.id_site, s.libelle AS nom_site " +
                "FROM employe_affectation_carriere a " +
                "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "WHERE a.id_employe = ? AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "ORDER BY a.date_debut DESC LIMIT 1";
        Integer idFonction = null;
        Integer idSite = null;
        String nomFonction = null;
        String nomDepartement = null;
        String nomSite = null;
        try (PreparedStatement psP = c.prepareStatement(sqlPoste)) {
            psP.setInt(1, idEmploye);
            psP.setDate(2, dateFin);
            psP.setDate(3, dateDebut);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    idFonction = (Integer) rsP.getObject("id_fonction");
                    idSite = (Integer) rsP.getObject("id_site");
                    nomFonction = rsP.getString("nom_fonction");
                    nomDepartement = rsP.getString("nom_departement");
                    nomSite = rsP.getString("nom_site");
                }
            }
        }
        res.put("idFonction", idFonction);
        res.put("idSite", idSite);
        res.put("nomFonction", nomFonction);
        res.put("nomDepartement", nomDepartement);
        res.put("nomSite", nomSite);

        // 5. Recherche des collègues du même poste et même site
        java.util.List<java.util.Map<String, Object>> colleguesAbsents = new java.util.ArrayList<>();
        int totalEmployesMemePoste = 1;
        if (idFonction != null) {
            String sqlCount = "SELECT COUNT(DISTINCT a.id_employe) AS cnt " +
                    "FROM employe_affectation_carriere a " +
                    "JOIN employe e ON a.id_employe = e.id_employe " +
                    "WHERE a.id_fonction = ? " +
                    (idSite != null && idSite > 0 ? "AND a.id_site = ? " : "") +
                    "AND e.est_actif = true " +
                    "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?)";
            try (PreparedStatement psCnt = c.prepareStatement(sqlCount)) {
                int pIdx = 1;
                psCnt.setInt(pIdx++, idFonction);
                if (idSite != null && idSite > 0) psCnt.setInt(pIdx++, idSite);
                psCnt.setDate(pIdx++, dateFin);
                psCnt.setDate(pIdx++, dateDebut);
                try (ResultSet rsCnt = psCnt.executeQuery()) {
                    if (rsCnt.next()) totalEmployesMemePoste = Math.max(1, rsCnt.getInt("cnt"));
                }
            }

            String sqlCollegues = "SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                    "dc.id_demande_conge, dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.moment_demi_journee, " +
                    "tc.libelle AS libelle_conge, sc.code AS code_statut " +
                    "FROM demande_conge dc " +
                    "JOIN employe e ON dc.id_employe = e.id_employe " +
                    "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                    "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                    "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                    "WHERE a.id_fonction = ? " +
                    (idSite != null && idSite > 0 ? "AND a.id_site = ? " : "") +
                    "AND dc.id_employe != ? " +
                    "AND dc.id_demande_conge != COALESCE(?, -1) " +
                    "AND sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "AND dc.date_debut <= ? AND dc.date_fin >= ? " +
                    "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                    "ORDER BY dc.date_debut ASC";
            try (PreparedStatement psC = c.prepareStatement(sqlCollegues)) {
                int pIdx = 1;
                psC.setInt(pIdx++, idFonction);
                if (idSite != null && idSite > 0) psC.setInt(pIdx++, idSite);
                psC.setInt(pIdx++, idEmploye);
                psC.setObject(pIdx++, idDemandeExclue != null ? idDemandeExclue : -1);
                psC.setDate(pIdx++, dateFin);
                psC.setDate(pIdx++, dateDebut);
                psC.setDate(pIdx++, dateFin);
                psC.setDate(pIdx++, dateDebut);
                try (ResultSet rsC = psC.executeQuery()) {
                    while (rsC.next()) {
                        boolean cDemi = rsC.getBoolean("est_demi_journee");
                        String cMoment = rsC.getString("moment_demi_journee");
                        Date cDeb = rsC.getDate("date_debut");
                        Date cFin = rsC.getDate("date_fin");

                        // Exclusion : demi-journées complémentaires sur la même date
                        if (estDemiJournee && cDemi && dateDebut.equals(dateFin) && cDeb.equals(cFin) && dateDebut.equals(cDeb)) {
                            if (momentDemiJournee != null && cMoment != null && !momentDemiJournee.equalsIgnoreCase(cMoment)) {
                                continue;
                            }
                        }

                        java.util.Map<String, Object> col = new java.util.HashMap<>();
                        col.put("idEmploye", rsC.getInt("id_employe"));
                        col.put("matricule", rsC.getString("matricule"));
                        col.put("nom", rsC.getString("nom"));
                        col.put("prenom", rsC.getString("prenom"));
                        col.put("photoUrl", rsC.getString("photo_url"));
                        col.put("idDemandeConge", rsC.getInt("id_demande_conge"));
                        col.put("typeConge", rsC.getString("libelle_conge"));
                        col.put("dateDebut", cDeb);
                        col.put("dateFin", cFin);
                        col.put("statut", rsC.getString("code_statut"));
                        colleguesAbsents.add(col);
                    }
                }
            }
        }

        boolean conflitPoste = !colleguesAbsents.isEmpty();
        res.put("totalEmployesPoste", totalEmployesMemePoste);
        res.put("colleguesAbsents", colleguesAbsents);
        res.put("conflitPoste", conflitPoste);
        if (conflitPoste) {
            String siteInfo = (nomSite != null && !nomSite.isEmpty()) ? " sur le site " + nomSite : "";
            res.put("messageAvertissement", colleguesAbsents.size() + " collaborateur(s) au même poste (" +
                    nomFonction + siteInfo + ") sont déjà absents sur cette période.");
        } else {
            res.put("messageAvertissement", null);
        }

        // 6. Solde actuel de congés du collaborateur
        VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
        BigDecimal soldeDisponible = (solde != null && solde.getSoldeActuel() != null)
                ? solde.getSoldeActuel()
                : BigDecimal.ZERO;
        res.put("soldeDisponible", soldeDisponible);

        return res;
    }

    public java.util.Map<String, Object> getPlanningCollaborateurs(
            Integer annee, Integer mois, java.time.LocalDate dateDebut, java.time.LocalDate dateFin,
            Integer idSite, Integer idDepartement,
            String search, String filterMode,
            Integer page, Integer size,
            Connection c) throws Exception {

        java.time.LocalDate start;
        java.time.LocalDate end;
        if (dateDebut != null && dateFin != null) {
            start = dateDebut;
            end = dateFin;
        } else {
            int y = (annee != null && annee > 2000) ? annee : java.time.LocalDate.now().getYear();
            int m = (mois != null && mois >= 1 && mois <= 12) ? mois : java.time.LocalDate.now().getMonthValue();
            start = java.time.LocalDate.of(y, m, 1);
            end = start.withDayOfMonth(start.lengthOfMonth());
        }

        Date dStart = Date.valueOf(start);
        Date dEnd = Date.valueOf(end);

        int curPage = (page != null && page >= 1) ? page : 1;
        int pageSize = (size != null && size >= 1 && size <= 100) ? size : 10;
        int offset = (curPage - 1) * pageSize;

        boolean hasSearch = (search != null && !search.trim().isEmpty());
        String searchPattern = hasSearch ? "%" + search.trim().toLowerCase() + "%" : null;
        boolean onlyAbsents = "ABSENTS".equalsIgnoreCase(filterMode);

        // 1. Décompte total des collaborateurs éligibles selon les filtres
        StringBuilder sqlCount = new StringBuilder(
                "WITH base_emp AS (" +
                "  SELECT DISTINCT ON (e.id_employe) e.id_employe, e.nom, e.prenom, e.matricule, f.libelle AS fonction, d.libelle AS departement " +
                "  FROM employe e " +
                "  JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                "      AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "  LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "  LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "  WHERE e.est_actif = true "
        );

        if (idSite != null && idSite > 0) sqlCount.append("AND a.id_site = ? ");
        if (idDepartement != null && idDepartement > 0) sqlCount.append("AND a.id_departement = ? ");
        if (hasSearch) {
            sqlCount.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ? OR LOWER(COALESCE(f.libelle, '')) LIKE ? OR LOWER(COALESCE(d.libelle, '')) LIKE ?) ");
        }
        if (onlyAbsents) {
            sqlCount.append("AND EXISTS (" +
                    "  SELECT 1 FROM demande_conge dc_sub " +
                    "  JOIN ref_statut_demande_conge sc_sub ON dc_sub.id_statut_demande_conge = sc_sub.id_statut_demande_conge " +
                    "  WHERE dc_sub.id_employe = e.id_employe " +
                    "    AND sc_sub.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "    AND dc_sub.date_debut <= ? AND dc_sub.date_fin >= ?" +
                    ") ");
        }
        sqlCount.append("  ORDER BY e.id_employe, a.date_debut DESC" +
                ") SELECT COUNT(*) AS total FROM base_emp");

        int totalCount = 0;
        try (PreparedStatement psCnt = c.prepareStatement(sqlCount.toString())) {
            int p = 1;
            psCnt.setDate(p++, dEnd);
            psCnt.setDate(p++, dStart);
            if (idSite != null && idSite > 0) psCnt.setInt(p++, idSite);
            if (idDepartement != null && idDepartement > 0) psCnt.setInt(p++, idDepartement);
            if (hasSearch) {
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
                psCnt.setString(p++, searchPattern);
            }
            if (onlyAbsents) {
                psCnt.setDate(p++, dEnd);
                psCnt.setDate(p++, dStart);
            }
            try (ResultSet rsCnt = psCnt.executeQuery()) {
                if (rsCnt.next()) totalCount = rsCnt.getInt("total");
            }
        }

        // 2. Récupérer les employés pour la page courante
        StringBuilder sqlEmp = new StringBuilder(
                "WITH base_emp AS (" +
                "  SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                "         f.id_fonction, f.libelle AS fonction, d.id_departement, d.libelle AS nom_departement, s.id_site, s.libelle AS site_nom " +
                "  FROM employe e " +
                "  JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                "      AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "  LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "  LEFT JOIN ref_departement d ON a.id_departement = d.id_departement " +
                "  LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "  WHERE e.est_actif = true "
        );

        if (idSite != null && idSite > 0) sqlEmp.append("AND a.id_site = ? ");
        if (idDepartement != null && idDepartement > 0) sqlEmp.append("AND a.id_departement = ? ");
        if (hasSearch) {
            sqlEmp.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ? OR LOWER(COALESCE(f.libelle, '')) LIKE ? OR LOWER(COALESCE(d.libelle, '')) LIKE ?) ");
        }
        if (onlyAbsents) {
            sqlEmp.append("AND EXISTS (" +
                    "  SELECT 1 FROM demande_conge dc_sub " +
                    "  JOIN ref_statut_demande_conge sc_sub ON dc_sub.id_statut_demande_conge = sc_sub.id_statut_demande_conge " +
                    "  WHERE dc_sub.id_employe = e.id_employe " +
                    "    AND sc_sub.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "    AND dc_sub.date_debut <= ? AND dc_sub.date_fin >= ?" +
                    ") ");
        }
        sqlEmp.append("  ORDER BY e.id_employe, a.date_debut DESC" +
                ") SELECT * FROM base_emp ORDER BY nom ASC, prenom ASC LIMIT ? OFFSET ?");

        java.util.List<java.util.Map<String, Object>> employes = new java.util.ArrayList<>();
        java.util.Map<Integer, java.util.Map<String, Object>> empMap = new java.util.LinkedHashMap<>();

        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            int p = 1;
            psE.setDate(p++, dEnd);
            psE.setDate(p++, dStart);
            if (idSite != null && idSite > 0) psE.setInt(p++, idSite);
            if (idDepartement != null && idDepartement > 0) psE.setInt(p++, idDepartement);
            if (hasSearch) {
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
                psE.setString(p++, searchPattern);
            }
            if (onlyAbsents) {
                psE.setDate(p++, dEnd);
                psE.setDate(p++, dStart);
            }
            psE.setInt(p++, pageSize);
            psE.setInt(p++, offset);

            try (ResultSet rsE = psE.executeQuery()) {
                while (rsE.next()) {
                    int idEmp = rsE.getInt("id_employe");
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("idEmploye", idEmp);
                    map.put("matricule", rsE.getString("matricule"));
                    map.put("nom", rsE.getString("nom"));
                    map.put("prenom", rsE.getString("prenom"));
                    map.put("nomComplet", rsE.getString("prenom") + " " + rsE.getString("nom"));
                    map.put("photoUrl", rsE.getString("photo_url"));
                    map.put("idFonction", rsE.getObject("id_fonction"));
                    map.put("fonction", rsE.getString("fonction"));
                    map.put("departement", rsE.getString("nom_departement"));
                    map.put("site", rsE.getString("site_nom"));
                    map.put("demandes", new java.util.ArrayList<java.util.Map<String, Object>>());
                    empMap.put(idEmp, map);
                    employes.add(map);
                }
            }
        }

        // 3. Charger les demandes des employés de cette page
        int totalDemandes = 0;
        java.util.Set<Integer> employesAvecConge = new java.util.HashSet<>();

        if (!empMap.isEmpty()) {
            StringBuilder sqlDemandes = new StringBuilder(
                    "SELECT dc.id_demande_conge, dc.id_employe, dc.date_debut, dc.date_fin, " +
                    "dc.nb_jours_decomptes, dc.est_demi_journee, dc.moment_demi_journee, " +
                    "tc.code AS code_type_conge, tc.libelle AS type_conge, sc.code AS code_statut " +
                    "FROM demande_conge dc " +
                    "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                    "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                    "WHERE sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "AND dc.date_debut <= ? AND dc.date_fin >= ? " +
                    "AND dc.id_employe IN ("
            );

            int countEmps = 0;
            for (Integer id : empMap.keySet()) {
                if (countEmps > 0) sqlDemandes.append(",");
                sqlDemandes.append("?");
                countEmps++;
            }
            sqlDemandes.append(") ORDER BY dc.date_debut ASC");

            try (PreparedStatement psD = c.prepareStatement(sqlDemandes.toString())) {
                int p = 1;
                psD.setDate(p++, dEnd);
                psD.setDate(p++, dStart);
                for (Integer id : empMap.keySet()) {
                    psD.setInt(p++, id);
                }
                try (ResultSet rsD = psD.executeQuery()) {
                    while (rsD.next()) {
                        int idEmp = rsD.getInt("id_employe");
                        java.util.Map<String, Object> emp = empMap.get(idEmp);
                        if (emp != null) {
                            java.util.Map<String, Object> item = new java.util.HashMap<>();
                            item.put("idDemandeConge", rsD.getInt("id_demande_conge"));
                            item.put("dateDebut", rsD.getDate("date_debut"));
                            item.put("dateFin", rsD.getDate("date_fin"));
                            item.put("nbJoursDecomptes", rsD.getBigDecimal("nb_jours_decomptes"));
                            item.put("estDemiJournee", rsD.getBoolean("est_demi_journee"));
                            item.put("momentDemiJournee", rsD.getString("moment_demi_journee"));
                            String codeType = rsD.getString("code_type_conge");
                            item.put("codeTypeConge", codeType);
                            item.put("typeConge", rsD.getString("type_conge"));
                            String color = switch (codeType != null ? codeType : "") {
                                case "ANNUEL" -> "#47603f";
                                case "MALADIE", "MALADIE_SANS_SOLDE" -> "#dc2626";
                                case "MATERNITE" -> "#7e22ce";
                                case "PATERNITE" -> "#6b21a8";
                                case "EXCEPTIONNEL" -> "#ea580c";
                                default -> "#5e6258";
                            };
                            item.put("couleur", color);
                            item.put("statut", rsD.getString("code_statut"));
                            @SuppressWarnings("unchecked")
                            java.util.List<java.util.Map<String, Object>> list = (java.util.List<java.util.Map<String, Object>>) emp.get("demandes");
                            list.add(item);
                            employesAvecConge.add(idEmp);
                            totalDemandes++;
                        }
                    }
                }
            }
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("dateDebut", start.toString());
        result.put("dateFin", end.toString());
        result.put("total", totalCount);
        result.put("totalCollaborateurs", totalCount);
        result.put("nbCollaborateursAbsents", employesAvecConge.size());
        result.put("totalDemandes", totalDemandes);
        result.put("page", curPage);
        result.put("size", pageSize);
        result.put("totalPages", totalPages);
        result.put("employes", employes);

        return result;
    }

    public java.util.Map<String, Object> getPlanningCollaborateurs(
            Integer annee, Integer mois, java.time.LocalDate dateDebut, java.time.LocalDate dateFin,
            Integer idSite, Integer idDepartement, Connection c) throws Exception {
        return getPlanningCollaborateurs(annee, mois, dateDebut, dateFin, idSite, idDepartement, null, "TOUS", 1, 1000, c);
    }
}
