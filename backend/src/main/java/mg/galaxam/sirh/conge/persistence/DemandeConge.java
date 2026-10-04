package mg.galaxam.sirh.conge.persistence;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import mg.galaxam.sirh.contrat.persistence.ContratTravail;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;

public class DemandeConge extends ClassMere {

    private Integer idDemandeConge;
    private Integer idEmploye;
    private Integer idTypeConge;
    private Integer idStatutDemandeConge;
    private Integer idMotifEvenementFamilial;

    private Date dateDebut;
    private Date dateFin;
    private Date dateDemande;

    private BigDecimal nbJoursDecomptes;

    private boolean derogationSoldeNegatif = false;
    private boolean derogationDelaiPreavis = false;
    private boolean derogationContinuiteService = false;
    private boolean extensionPathologiqueMaternite = false;

    private boolean estDemiJournee = false;
    private String momentDemiJournee; // MATIN ou APRES_MIDI

    private boolean justificatifFourni = false;
    private Boolean estRemunere;
    private String commentaire;

    private Integer saisiPar;
    private Integer validePar;
    private Timestamp dateValidation;
    private String motifRefus;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public DemandeConge() {
        this.setNomTable("DEMANDE_CONGE");
        this.dateDemande = new Date(System.currentTimeMillis());
    }

    @Override
    public Integer getTuppleID() {
        return this.idDemandeConge;
    }

    @Override
    public String getAttributIDName() {
        return "id_demande_conge";
    }

    private static final mg.galaxam.sirh.conge.domain.CongeCalculateurEngine CONGE_ENGINE =
            new mg.galaxam.sirh.conge.domain.CongeCalculateurEngine();

    /**
     * Règle de décompte en jours calendaires stricts (Code du Travail malgache Art. 85, 88).
     * Déléguée à CongeCalculateurEngine (source unique de vérité).
     */
    public static BigDecimal calculerNombreJoursDecomptes(Date dateDebut, Date dateFin) {
        return calculerNombreJoursDecomptes(dateDebut, dateFin, false);
    }

    public static BigDecimal calculerNombreJoursDecomptes(Date dateDebut, Date dateFin, boolean estDemiJournee) {
        if (dateDebut == null || dateFin == null)
            return BigDecimal.ZERO;
        return CONGE_ENGINE.calculerNombreJours(dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee);
    }

    @Override
    public void controler(Connection c) throws Exception {
        if (idEmploye == null) {
            throw new IllegalArgumentException("L'employé est obligatoire.");
        }
        if (idTypeConge == null) {
            throw new IllegalArgumentException("Le type de congé est obligatoire.");
        }
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début du congé est obligatoire.");
        }
        if (dateFin == null) {
            throw new IllegalArgumentException("La date de fin du congé est obligatoire.");
        }
        if (dateFin.before(dateDebut)) {
            throw new IllegalArgumentException("La date de fin ne peut pas être antérieure à la date de début.");
        }

        if (estDemiJournee) {
            if (!dateDebut.equals(dateFin)) {
                throw new IllegalArgumentException(
                        "Une demande de congé en demi-journée doit concerner la même journée (date début = date fin).");
            }
            if (momentDemiJournee == null || (!"MATIN".equalsIgnoreCase(momentDemiJournee)
                    && !"APRES_MIDI".equalsIgnoreCase(momentDemiJournee))) {
                throw new IllegalArgumentException(
                        "Veuillez préciser le moment de la demi-journée ('MATIN' ou 'APRES_MIDI').");
            }
            this.momentDemiJournee = momentDemiJournee.toUpperCase();
        } else {
            this.momentDemiJournee = null;
        }

        // Résoudre le type avant le décompte : seuls les congés débitant les droits acquis
        // suivent la politique datée des jours fériés.
        RefTypeConge typeConge = RefTypeConge.rechercherParId(idTypeConge, c);
        if (typeConge == null) {
            throw new IllegalArgumentException("Le type de congé spécifié n'existe pas.");
        }

        // 1. Décompte daté (sauf régularisation partielle explicite)
        boolean isRegularisation = this.commentaire != null && this.commentaire.startsWith("[REGULARISATION_POINTAGE]");
        if (!isRegularisation || this.nbJoursDecomptes == null || this.nbJoursDecomptes.compareTo(BigDecimal.ZERO) <= 0) {
            this.nbJoursDecomptes = typeConge.isDecompteSoldeAcquis()
                    ? mg.galaxam.sirh.conge.application.CongeDecompteCalendaireResolver.calculer(
                            idEmploye, dateDebut.toLocalDate(), dateFin.toLocalDate(), estDemiJournee, c)
                    : calculerNombreJoursDecomptes(dateDebut, dateFin, estDemiJournee);
        }

        // 2.bis Contrôle du solde disponible pour les types de congés entamant le solde acquis (ex: ANNUEL)
        if (typeConge.isDecompteSoldeAcquis()) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            BigDecimal soldeDisponible = (solde != null && solde.getSoldeActuel() != null)
                    ? solde.getSoldeActuel()
                    : BigDecimal.ZERO;
            BigDecimal joursDejaImputes = BigDecimal.ZERO;
            if (this.idDemandeConge != null) {
                try (PreparedStatement psM = c.prepareStatement(
                        "SELECT COALESCE(SUM(-nb_jours), 0) FROM mouvement_solde_conge WHERE id_demande_conge = ?")) {
                    psM.setInt(1, this.idDemandeConge);
                    try (ResultSet rsM = psM.executeQuery()) {
                        if (rsM.next()) joursDejaImputes = rsM.getBigDecimal(1);
                    }
                }
            }
            BigDecimal nouveauSolde = CONGE_ENGINE.calculerSoldeProjete(
                    soldeDisponible.add(joursDejaImputes), nbJoursDecomptes, true);
            if (nouveauSolde.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "Solde de congé insuffisant (" + soldeDisponible.add(joursDejaImputes) + "j disponibles pour " + nbJoursDecomptes
                                + "j demandés). La prise par anticipation est désactivée.");
            }
        }

        // Contrat, affectation et continuité résolus sur chaque jour de la demande.
        var continuite = new mg.galaxam.sirh.conge.application.CongeContinuiteService()
                .analyser(idEmploye, dateDebut.toLocalDate(), dateFin.toLocalDate(),
                        idDemandeConge, estDemiJournee, momentDemiJournee, c);
        if (!continuite.actifSurPeriode()) {
            throw new IllegalArgumentException("L'employé ne dispose pas d'un contrat actif et d'une affectation "
                    + "sur chaque jour du congé demandé (du " + dateDebut + " au " + dateFin + ").");
        }

        // 3. Contrôle justificatif obligatoire
        // Assouplissement: On ne bloque plus la validation car le justificatif peut
        // être fourni au retour
        // if (typeConge.isExigeJustificatif() && !justificatifFourni &&
        // ("VALIDE".equals(getStatutCode(c)))) {
        // throw new IllegalArgumentException("Un justificatif obligatoire (ex:
        // certificat médical, acte) doit être fourni pour valider ce congé.");
        // }

        // 4. Contrôle d'ancienneté minimale (ancienneté effective cumulée sans les interruptions)
        if (typeConge.getExigeAncienneteMinMois() != null && typeConge.getExigeAncienneteMinMois() > 0) {
            ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
            if (contratActif == null || contratActif.getDateDebut() == null) {
                throw new IllegalArgumentException(
                        "Aucun contrat de travail actif trouvé pour vérifier l'ancienneté requise.");
            }

            java.util.Map<String, Object> ancDetaillee = ContratTravail.calculerAncienneteDetaillee(contratActif.getIdContrat(), c);
            int moisAnciennete = ancDetaillee.get("ancienneteEffectiveMois") != null
                    ? ((Number) ancDetaillee.get("ancienneteEffectiveMois")).intValue()
                    : 0;
            String texteAnciennete = ancDetaillee.get("ancienneteEffectiveTexte") != null
                    ? (String) ancDetaillee.get("ancienneteEffectiveTexte")
                    : (moisAnciennete + " mois");

            if (moisAnciennete < typeConge.getExigeAncienneteMinMois()) {
                throw new IllegalArgumentException(
                        "L'ancienneté requise pour le congé " + typeConge.getLibelle() + " est de " +
                                typeConge.getExigeAncienneteMinMois() + " mois (" + texteAnciennete
                                + " constatés).");
            }
        }

        // 5. Contrôle du délai de préavis (si le type de congé est soumis à préavis)
        if (typeConge.isSoumisDelaiPreavis() && !derogationDelaiPreavis) {
            LocalDate dDemande = (dateDemande != null) ? dateDemande.toLocalDate() : LocalDate.now();
            LocalDate dDebut = dateDebut.toLocalDate();
            long joursPreavis = ChronoUnit.DAYS.between(dDemande, dDebut);

            int seuilLong = getParamGlobalInt("SEUIL_JOURS_PREAVIS_LONG", c);
            int delaiCourt = getParamGlobalInt("DELAI_PREAVIS_COURT_JOURS", c);
            int delaiLong = getParamGlobalInt("DELAI_PREAVIS_LONG_JOURS", c);

            int preavisRequis = CONGE_ENGINE.calculerPreavisRequis(
                    nbJoursDecomptes, true, seuilLong, delaiCourt, delaiLong);

            if (joursPreavis < preavisRequis) {
                throw new IllegalArgumentException("Le préavis minimum requis pour cette demande est de "
                        + preavisRequis + " jours (" +
                        joursPreavis + " jours constatés). Vous devez solliciter une dérogation RH de préavis.");
            }
        }

        // 5bis. Jouissance du congé payé après douze mois de service effectif (Code du travail 2024, art. 130).
        if ("ANNUEL".equals(typeConge.getCode())) {
            String sqlEmbauche = "SELECT MIN(date_debut) AS date_embauche FROM contrat_travail WHERE id_employe = ?";
            try (PreparedStatement psEmb = c.prepareStatement(sqlEmbauche)) {
                psEmb.setInt(1, idEmploye);
                try (ResultSet rsEmb = psEmb.executeQuery()) {
                    if (rsEmb.next()) {
                        Date dEmb = rsEmb.getDate("date_embauche");
                        if (dEmb != null) {
                            LocalDate hireDate = dEmb.toLocalDate();
                            LocalDate startDate = dateDebut.toLocalDate();
                            long moisService = ChronoUnit.MONTHS.between(hireDate, startDate);
                            if (moisService < 12) {
                                throw new IllegalArgumentException("L'employé ne justifie que de " + moisService +
                                        " mois de service effectif (12 mois requis par l'article 130 du Code du travail 2024).");
                            }
                        }
                    }
                }
            }
        }

        // 6. Contrôle Maternité (98 jours standard, ou 119 jours si extension
        // pathologique)
        if ("MATERNITE".equals(typeConge.getCode())) {
            int dureeMaxStd = getParamGlobalInt("DUREE_MAX_MATERNITE_STANDARD_JOURS", c);
            int dureeMaxPatho = getParamGlobalInt("DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS", c);
            int maxPlafond = extensionPathologiqueMaternite ? dureeMaxPatho : dureeMaxStd;

            if (nbJoursDecomptes.compareTo(BigDecimal.valueOf(maxPlafond)) > 0) {
                throw new IllegalArgumentException("La durée maximale autorisée pour un congé maternité est de " +
                        maxPlafond + " jours (" + nbJoursDecomptes + "j demandés)." +
                        (!extensionPathologiqueMaternite
                                ? " Activez l'extension pathologique si un justificatif médical le justifie (119j max)."
                                : ""));
            }
        }

        // 7. Contrôle du pool annuel configuré en BDD.
        // VALIDE = consommé, EN_ATTENTE = réservé ; le contrôle porte sur le total engagé.
        if (typeConge.getIdPoolCongeAnnuel() != null) {
            int annee = dateDebut.toLocalDate().getYear();
            RefPoolCongeAnnuel.verrouillerQuota(idEmploye, c);
            RefPoolCongeAnnuel.Consommation consommation = RefPoolCongeAnnuel.rechercherConsommation(
                    idEmploye, typeConge.getIdPoolCongeAnnuel(), annee, c);
            if (consommation == null || consommation.getPlafond() == null) {
                throw new IllegalStateException("Configuration du pool annuel introuvable.");
            }
            BigDecimal engagesActuels = consommation.getJoursEngages();
            BigDecimal joursDejaPrisEnCompte = BigDecimal.ZERO;
            if (this.idDemandeConge != null) {
                String sqlOld = "SELECT dc.nb_jours_decomptes FROM demande_conge dc " +
                        "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                        "JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                        "WHERE dc.id_demande_conge = ? AND tc.id_pool_conge_annuel = ? " +
                        "AND EXTRACT(year FROM dc.date_debut) = ? " +
                        "AND sc.code IN ('VALIDE', 'EN_ATTENTE')";
                try (PreparedStatement psOld = c.prepareStatement(sqlOld)) {
                    psOld.setInt(1, this.idDemandeConge);
                    psOld.setInt(2, typeConge.getIdPoolCongeAnnuel());
                    psOld.setInt(3, annee);
                    try (ResultSet rsOld = psOld.executeQuery()) {
                        if (rsOld.next()) {
                            joursDejaPrisEnCompte = rsOld.getBigDecimal(1);
                            if (joursDejaPrisEnCompte == null) joursDejaPrisEnCompte = BigDecimal.ZERO;
                        }
                    }
                }
            }
            BigDecimal engagesHorsDemandeCourante = engagesActuels.subtract(joursDejaPrisEnCompte);
            BigDecimal plafond = consommation.getPlafond();
            BigDecimal totalApresDemande = engagesHorsDemandeCourante.add(nbJoursDecomptes);
            if (totalApresDemande.compareTo(plafond) > 0) {
                throw new IllegalArgumentException(
                        "Le quota du pool annuel des permissions exceptionnelles (" + plafond + " jours/an) est dépassé pour l'année "
                                + annee + ". (Engagé hors demande courante: " + engagesHorsDemandeCourante
                                + "j, Demandé: " + nbJoursDecomptes
                                + "j). Dérogation RH requise.");
            }
        }

        // 8. Contrôle de non-chevauchement des dates avec une autre demande active du même salarié
        String sqlOverlap = "SELECT dc.id_demande_conge, dc.date_debut, dc.date_fin, dc.est_demi_journee, dc.moment_demi_journee, "
                +
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
            psO.setObject(2, idDemandeConge != null ? idDemandeConge : -1);
            psO.setDate(3, dateFin);
            psO.setDate(4, dateDebut);
            try (ResultSet rsOverlap = psO.executeQuery()) {
                while (rsOverlap.next()) {
                    boolean otherDemi = rsOverlap.getBoolean("est_demi_journee");
                    String otherMoment = rsOverlap.getString("moment_demi_journee");
                    if (estDemiJournee && otherDemi && dateDebut.equals(dateFin) &&
                            rsOverlap.getDate("date_debut").equals(dateDebut) &&
                            momentDemiJournee != null && otherMoment != null &&
                            !momentDemiJournee.equalsIgnoreCase(otherMoment)) {
                        continue;
                    }
                    throw new IllegalArgumentException("Chevauchement détecté : l'employé a déjà une demande de congé ("
                            +
                            rsOverlap.getString("libelle_conge") + " du " + rsOverlap.getDate("date_debut") + " au " +
                            rsOverlap.getDate("date_fin") + " - Statut: " + rsOverlap.getString("code_statut") +
                            ") couvrant cette période.");
                }
            }
        }

        // 9. Une collision de poste/site réelle sur l'un des segments exige une dérogation.
        if (!derogationContinuiteService && !continuite.collisions().isEmpty()) {
            var collision = continuite.collisions().get(0);
            throw new IllegalArgumentException("Conflit de continuité de service détecté : "
                    + collision.prenom() + " " + collision.nom() + " (même poste : "
                    + collision.nomFonction() + " sur le site " + collision.nomSite() + ") est déjà en congé "
                    + "du " + collision.debutConflit() + " au " + collision.finConflit()
                    + ". Une dérogation expresse de continuité de service est requise.");
        }
    }

    public String getStatutCode(Connection c) throws Exception {
        if (idStatutDemandeConge == null)
            return "BROUILLON";
        RefStatutDemandeConge st = RefStatutDemandeConge.rechercherParId(idStatutDemandeConge, c);
        return st != null ? st.getCode() : "BROUILLON";
    }

    private int getParamGlobalInt(String code, Connection c) {
        LocalDate dateMetier = code.startsWith("DUREE_MAX_MATERNITE")
                ? dateDebut.toLocalDate() : (dateDemande != null ? dateDemande.toLocalDate() : LocalDate.now());
        return ParametreGlobal.requirePositiveInt(ParametreGlobal.loadActiveMapAtDate(Date.valueOf(dateMetier), c), code);
    }

    @Override
    protected ClassMere inserer(Connection c) throws Exception {
        String sql = "INSERT INTO demande_conge (" +
                "id_employe, id_type_conge, id_statut_demande_conge, id_motif_evenement_familial, " +
                "date_debut, date_fin, date_demande, nb_jours_decomptes, " +
                "derogation_solde_negatif, derogation_delai_preavis, derogation_continuite_service, extension_pathologique_maternite, " +
                "est_demi_journee, moment_demi_journee, " +
                "est_remunere, justificatif_fourni, commentaire, saisi_par" +
                ") VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_DATE), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            ps.setInt(i++, idEmploye);
            ps.setInt(i++, idTypeConge);
            ps.setInt(i++, idStatutDemandeConge);
            setNullableInt(ps, i++, idMotifEvenementFamilial);
            ps.setDate(i++, dateDebut);
            ps.setDate(i++, dateFin);
            ps.setDate(i++, dateDemande);
            ps.setBigDecimal(i++, nbJoursDecomptes);
            ps.setBoolean(i++, derogationSoldeNegatif);
            ps.setBoolean(i++, derogationDelaiPreavis);
            ps.setBoolean(i++, derogationContinuiteService);
            ps.setBoolean(i++, extensionPathologiqueMaternite);
            ps.setBoolean(i++, estDemiJournee);
            setNullableString(ps, i++, momentDemiJournee);
            setNullableBoolean(ps, i++, estRemunere);
            ps.setBoolean(i++, justificatifFourni);
            setNullableString(ps, i++, commentaire);
            setNullableInt(ps, i++, saisiPar);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    this.idDemandeConge = rs.getInt(1);
                }
            }
        }
        return this;
    }

    @Override
    protected ClassMere modifier(Connection c) throws Exception {
        String sql = "UPDATE demande_conge SET " +
                "id_type_conge = ?, id_statut_demande_conge = ?, id_motif_evenement_familial = ?, " +
                "date_debut = ?, date_fin = ?, date_demande = ?, nb_jours_decomptes = ?, " +
                "derogation_solde_negatif = ?, derogation_delai_preavis = ?, derogation_continuite_service = ?, extension_pathologique_maternite = ?, " +
                "est_demi_journee = ?, moment_demi_journee = ?, " +
                "est_remunere = ?, justificatif_fourni = ?, commentaire = ?, valide_par = ?, date_validation = ?, motif_refus = ?, " +
                "updated_at = CURRENT_TIMESTAMP " +
                "WHERE id_demande_conge = ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, idTypeConge);
            ps.setInt(i++, idStatutDemandeConge);
            setNullableInt(ps, i++, idMotifEvenementFamilial);
            ps.setDate(i++, dateDebut);
            ps.setDate(i++, dateFin);
            ps.setDate(i++, dateDemande);
            ps.setBigDecimal(i++, nbJoursDecomptes);
            ps.setBoolean(i++, derogationSoldeNegatif);
            ps.setBoolean(i++, derogationDelaiPreavis);
            ps.setBoolean(i++, derogationContinuiteService);
            ps.setBoolean(i++, extensionPathologiqueMaternite);
            ps.setBoolean(i++, estDemiJournee);
            setNullableString(ps, i++, momentDemiJournee);
            setNullableBoolean(ps, i++, estRemunere);
            ps.setBoolean(i++, justificatifFourni);
            setNullableString(ps, i++, commentaire);
            setNullableInt(ps, i++, validePar);
            setNullableTimestamp(ps, i++, dateValidation);
            setNullableString(ps, i++, motifRefus);
            ps.setInt(i++, idDemandeConge);
            ps.executeUpdate();
        }
        return this;
    }

    @Override
    protected void supprimer(Connection c) throws Exception {
        String sql = "DELETE FROM demande_conge WHERE id_demande_conge = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idDemandeConge);
            ps.executeUpdate();
        }
    }

    @Override
    public ClassMere rechercherParId(String id, Connection c) throws Exception {
        if (id == null || id.isBlank())
            return null;
        return DemandeConge.rechercherParId(Integer.parseInt(id), c);
    }

    public static DemandeConge rechercherParId(Integer id, Connection c) throws Exception {
        if (id == null)
            return null;
        String sql = "SELECT * FROM demande_conge WHERE id_demande_conge = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    public static List<DemandeConge> listParEmploye(Integer idEmploye, Connection c) throws Exception {
        List<DemandeConge> list = new ArrayList<>();
        if (idEmploye == null)
            return list;
        String sql = "SELECT * FROM demande_conge WHERE id_employe = ? ORDER BY date_debut DESC, created_at DESC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    private static DemandeConge mapResultSet(ResultSet rs) throws Exception {
        DemandeConge obj = new DemandeConge();
        obj.setIdDemandeConge(rs.getInt("id_demande_conge"));
        obj.setIdEmploye(rs.getInt("id_employe"));
        obj.setIdTypeConge(rs.getInt("id_type_conge"));
        obj.setIdStatutDemandeConge(rs.getInt("id_statut_demande_conge"));
        obj.setIdMotifEvenementFamilial(getNullableIntFromRs(rs, "id_motif_evenement_familial"));
        obj.setDateDebut(rs.getDate("date_debut"));
        obj.setDateFin(rs.getDate("date_fin"));
        obj.setDateDemande(rs.getDate("date_demande"));
        obj.setNbJoursDecomptes(rs.getBigDecimal("nb_jours_decomptes"));
        obj.setDerogationSoldeNegatif(rs.getBoolean("derogation_solde_negatif"));
        obj.setDerogationDelaiPreavis(rs.getBoolean("derogation_delai_preavis"));
        try {
            obj.setDerogationContinuiteService(rs.getBoolean("derogation_continuite_service"));
        } catch (Exception ignored) {}
        obj.setExtensionPathologiqueMaternite(rs.getBoolean("extension_pathologique_maternite"));
        obj.setEstDemiJournee(rs.getBoolean("est_demi_journee"));
        obj.setMomentDemiJournee(rs.getString("moment_demi_journee"));
        obj.setJustificatifFourni(rs.getBoolean("justificatif_fourni"));
        try {
            if (rs.getObject("est_remunere") != null) {
                obj.setEstRemunere(rs.getBoolean("est_remunere"));
            }
        } catch (Exception ignored) {}
        obj.setCommentaire(rs.getString("commentaire"));
        obj.setSaisiPar(getNullableIntFromRs(rs, "saisi_par"));
        obj.setValidePar(getNullableIntFromRs(rs, "valide_par"));
        obj.setDateValidation(rs.getTimestamp("date_validation"));
        obj.setMotifRefus(rs.getString("motif_refus"));
        obj.setCreatedAt(rs.getTimestamp("created_at"));
        obj.setUpdatedAt(rs.getTimestamp("updated_at"));
        return obj;
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws Exception {
        if (value != null)
            ps.setInt(index, value);
        else
            ps.setNull(index, Types.INTEGER);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws Exception {
        if (value != null && !value.isBlank())
            ps.setString(index, value.trim());
        else
            ps.setNull(index, Types.VARCHAR);
    }

    private static void setNullableTimestamp(PreparedStatement ps, int index, Timestamp value) throws Exception {
        if (value != null)
            ps.setTimestamp(index, value);
        else
            ps.setNull(index, Types.TIMESTAMP);
    }

    private static void setNullableBoolean(PreparedStatement ps, int index, Boolean value) throws Exception {
        if (value != null)
            ps.setBoolean(index, value);
        else
            ps.setNull(index, Types.BOOLEAN);
    }

    private static Integer getNullableIntFromRs(ResultSet rs, String colName) throws Exception {
        int val = rs.getInt(colName);
        return rs.wasNull() ? null : val;
    }

    // Getters / Setters
    public Integer getIdDemandeConge() {
        return idDemandeConge;
    }

    public void setIdDemandeConge(Integer idDemandeConge) {
        this.idDemandeConge = idDemandeConge;
    }

    public Integer getIdEmploye() {
        return idEmploye;
    }

    public void setIdEmploye(Integer idEmploye) {
        this.idEmploye = idEmploye;
    }

    public Integer getIdTypeConge() {
        return idTypeConge;
    }

    public void setIdTypeConge(Integer idTypeConge) {
        this.idTypeConge = idTypeConge;
    }

    public Integer getIdStatutDemandeConge() {
        return idStatutDemandeConge;
    }

    public void setIdStatutDemandeConge(Integer idStatutDemandeConge) {
        this.idStatutDemandeConge = idStatutDemandeConge;
    }

    public Integer getIdMotifEvenementFamilial() {
        return idMotifEvenementFamilial;
    }

    public void setIdMotifEvenementFamilial(Integer idMotifEvenementFamilial) {
        this.idMotifEvenementFamilial = idMotifEvenementFamilial;
    }

    public Date getDateDebut() {
        return dateDebut;
    }

    public void setDateDebut(Date dateDebut) {
        this.dateDebut = dateDebut;
    }

    public Date getDateFin() {
        return dateFin;
    }

    public void setDateFin(Date dateFin) {
        this.dateFin = dateFin;
    }

    public Date getDateDemande() {
        return dateDemande;
    }

    public void setDateDemande(Date dateDemande) {
        this.dateDemande = dateDemande;
    }

    public BigDecimal getNbJoursDecomptes() {
        return nbJoursDecomptes;
    }

    public void setNbJoursDecomptes(BigDecimal nbJoursDecomptes) {
        this.nbJoursDecomptes = nbJoursDecomptes;
    }

    public boolean isDerogationSoldeNegatif() {
        return derogationSoldeNegatif;
    }

    public void setDerogationSoldeNegatif(boolean derogationSoldeNegatif) {
        this.derogationSoldeNegatif = derogationSoldeNegatif;
    }

    public boolean isDerogationDelaiPreavis() {
        return derogationDelaiPreavis;
    }

    public void setDerogationDelaiPreavis(boolean derogationDelaiPreavis) {
        this.derogationDelaiPreavis = derogationDelaiPreavis;
    }

    public boolean isDerogationContinuiteService() {
        return derogationContinuiteService;
    }

    public void setDerogationContinuiteService(boolean derogationContinuiteService) {
        this.derogationContinuiteService = derogationContinuiteService;
    }

    public boolean isExtensionPathologiqueMaternite() {
        return extensionPathologiqueMaternite;
    }

    public void setExtensionPathologiqueMaternite(boolean extensionPathologiqueMaternite) {
        this.extensionPathologiqueMaternite = extensionPathologiqueMaternite;
    }

    public boolean isEstDemiJournee() {
        return estDemiJournee;
    }

    public void setEstDemiJournee(boolean estDemiJournee) {
        this.estDemiJournee = estDemiJournee;
    }

    public String getMomentDemiJournee() {
        return momentDemiJournee;
    }

    public void setMomentDemiJournee(String momentDemiJournee) {
        this.momentDemiJournee = momentDemiJournee;
    }

    public boolean isJustificatifFourni() {
        return justificatifFourni;
    }

    public void setJustificatifFourni(boolean justificatifFourni) {
        this.justificatifFourni = justificatifFourni;
    }

    public String getCommentaire() {
        return commentaire;
    }

    public void setCommentaire(String commentaire) {
        this.commentaire = commentaire;
    }

    public Integer getSaisiPar() {
        return saisiPar;
    }

    public void setSaisiPar(Integer saisiPar) {
        this.saisiPar = saisiPar;
    }

    public Integer getValidePar() {
        return validePar;
    }

    public void setValidePar(Integer validePar) {
        this.validePar = validePar;
    }

    public Timestamp getDateValidation() {
        return dateValidation;
    }

    public void setDateValidation(Timestamp dateValidation) {
        this.dateValidation = dateValidation;
    }

    public String getMotifRefus() {
        return motifRefus;
    }

    public void setMotifRefus(String motifRefus) {
        this.motifRefus = motifRefus;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getEstRemunere() {
        return estRemunere;
    }

    public void setEstRemunere(Boolean estRemunere) {
        this.estRemunere = estRemunere;
    }
}
