package mg.galaxam.sirh.model.conge;

import bean.ClassMere;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mg.galaxam.sirh.model.contrat.ContratTravail;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;

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

    private static final mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine CONGE_ENGINE =
            new mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine();

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

        // 1. Calcul dynamique du décompte en jours calendaires stricts
        this.nbJoursDecomptes = calculerNombreJoursDecomptes(dateDebut, dateFin, estDemiJournee);

        // 2. Chargement du type de congé
        RefTypeConge typeConge = RefTypeConge.rechercherParId(idTypeConge, c);
        if (typeConge == null) {
            throw new IllegalArgumentException("Le type de congé spécifié n'existe pas.");
        }

        // 2.bis Contrôle de l'existence d'un contrat de travail actif couvrant toute la période de congé
        String sqlContrat = "SELECT ct.id_contrat FROM contrat_travail ct " +
                "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "WHERE ct.id_employe = ? AND rsc.code = 'ACTIF' " +
                "AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?)";
        try (PreparedStatement psCt = c.prepareStatement(sqlContrat)) {
            psCt.setInt(1, idEmploye);
            psCt.setDate(2, dateDebut);
            psCt.setDate(3, dateFin);
            try (ResultSet rsCt = psCt.executeQuery()) {
                if (!rsCt.next()) {
                    throw new IllegalArgumentException(
                            "L'employé ne dispose d'aucun contrat actif couvrant l'intégralité de la période de congé demandée (du "
                                    + dateDebut + " au " + dateFin + ").");
                }
            }
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
            if (!derogationSoldeNegatif) {
                ContratTravail contratActif = ContratTravail.rechercherActifParEmploye(idEmploye, c);
                if (contratActif == null || contratActif.getDateDebut() == null) {
                    throw new IllegalArgumentException(
                            "Aucun contrat de travail actif trouvé pour vérifier l'ancienneté requise. Dérogation RH requise.");
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
                                    + " constatés). Dérogation RH requise.");
                }
            }
        }

        // 5. Contrôle du délai de préavis (si le type de congé est soumis à préavis)
        if (typeConge.isSoumisDelaiPreavis() && !derogationDelaiPreavis) {
            LocalDate dDemande = (dateDemande != null) ? dateDemande.toLocalDate() : LocalDate.now();
            LocalDate dDebut = dateDebut.toLocalDate();
            long joursPreavis = ChronoUnit.DAYS.between(dDemande, dDebut);

            int seuilLong = getParamGlobalInt("SEUIL_JOURS_PREAVIS_LONG", 3, c);
            int delaiCourt = getParamGlobalInt("DELAI_PREAVIS_COURT_JOURS", 2, c);
            int delaiLong = getParamGlobalInt("DELAI_PREAVIS_LONG_JOURS", 15, c);

            int preavisRequis = (nbJoursDecomptes.compareTo(BigDecimal.valueOf(seuilLong)) > 0) ? delaiLong
                    : delaiCourt;

            if (joursPreavis < preavisRequis) {
                throw new IllegalArgumentException("Le préavis minimum requis pour cette demande est de "
                        + preavisRequis + " jours (" +
                        joursPreavis + " jours constatés). Vous devez solliciter une dérogation RH de préavis.");
            }
        }

        // 5bis. Contrôle de l'ancienneté minimale (Art. 85 du Code du Travail : 12 mois de service effectif)
        if ("ANNUEL".equals(typeConge.getCode()) && !derogationSoldeNegatif) {
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
                                        " mois de service effectif (12 mois requis par l'Art. 85 du Code du Travail). " +
                                        "Une dérogation RH pour prise par anticipation est requise.");
                            }
                        }
                    }
                }
            }
        }

        // 6. Contrôle Maternité (98 jours standard, ou 119 jours si extension
        // pathologique)
        if ("MATERNITE".equals(typeConge.getCode())) {
            int dureeMaxStd = getParamGlobalInt("DUREE_MAX_MATERNITE_STANDARD_JOURS", 98, c);
            int dureeMaxPatho = getParamGlobalInt("DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS", 119, c);
            int maxPlafond = extensionPathologiqueMaternite ? dureeMaxPatho : dureeMaxStd;

            if (nbJoursDecomptes.compareTo(BigDecimal.valueOf(maxPlafond)) > 0) {
                throw new IllegalArgumentException("La durée maximale autorisée pour un congé maternité est de " +
                        maxPlafond + " jours (" + nbJoursDecomptes + "j demandés)." +
                        (!extensionPathologiqueMaternite
                                ? " Activez l'extension pathologique si un justificatif médical le justifie (119j max)."
                                : ""));
            }
        }

        // 7. Contrôle Pool Annuel Partagé 10j/an (Art. 88 : EXCEPTIONNEL + PATERNITE)
        if (typeConge.getIdPoolCongeAnnuel() != null) {
            int annee = dateDebut.toLocalDate().getYear();
            List<VPoolCongeAnnuelConsommation> pools = VPoolCongeAnnuelConsommation.listParEmployeEtAnnee(idEmploye,
                    annee, c);
            BigDecimal consommeActuel = BigDecimal.ZERO;
            for (VPoolCongeAnnuelConsommation p : pools) {
                if (typeConge.getIdPoolCongeAnnuel().equals(p.getIdPool()) && p.getJoursConsommes() != null) {
                    consommeActuel = consommeActuel.add(p.getJoursConsommes());
                }
            }
            BigDecimal totalApresDemande = consommeActuel.add(nbJoursDecomptes);
            if (totalApresDemande.compareTo(BigDecimal.valueOf(10)) > 0 && !derogationSoldeNegatif) {
                throw new IllegalArgumentException(
                        "Le quota du pool annuel des permissions exceptionnelles (10 jours/an Art. 88) est dépassé pour l'année "
                                +
                                annee + ". (Consommé: " + consommeActuel + "j, Demandé: " + nbJoursDecomptes
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

        // 9. Contrôle de Continuité de Service (anti-collision de poste sur le même site d'affectation)
        String sqlPoste = "SELECT a.id_fonction, a.id_site, f.libelle AS nom_fonction, s.libelle AS nom_site " +
                "FROM employe_affectation_carriere a " +
                "JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
                "WHERE a.id_employe = ? AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                "ORDER BY a.date_debut DESC LIMIT 1";
        Integer idFonction = null;
        Integer idSite = null;
        String nomFonction = null;
        String nomSite = null;
        try (PreparedStatement psP = c.prepareStatement(sqlPoste)) {
            psP.setInt(1, idEmploye);
            psP.setDate(2, dateFin);
            psP.setDate(3, dateDebut);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    idFonction = rsP.getInt("id_fonction");
                    idSite = rsP.getInt("id_site");
                    nomFonction = rsP.getString("nom_fonction");
                    nomSite = rsP.getString("nom_site");
                }
            }
        }

        if (idFonction != null) {
            String sqlCollegues = "SELECT e.nom, e.prenom, dc.date_debut, dc.date_fin, " +
                    "dc.est_demi_journee, dc.moment_demi_journee, sc.code AS code_statut " +
                    "FROM demande_conge dc " +
                    "JOIN employe e ON dc.id_employe = e.id_employe " +
                    "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                    "JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                    "WHERE a.id_fonction = ? " +
                    (idSite != null && idSite > 0 ? "AND a.id_site = ? " : "") +
                    "AND dc.id_employe != ? " +
                    "AND dc.id_demande_conge != COALESCE(?, -1) " +
                    "AND sc.code NOT IN ('REFUSE', 'ANNULE', 'REJETE') " +
                    "AND dc.date_debut <= ? AND dc.date_fin >= ? " +
                    "AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?)";
            try (PreparedStatement psC = c.prepareStatement(sqlCollegues)) {
                int pIdx = 1;
                psC.setInt(pIdx++, idFonction);
                if (idSite != null && idSite > 0) {
                    psC.setInt(pIdx++, idSite);
                }
                psC.setInt(pIdx++, idEmploye);
                psC.setObject(pIdx++, idDemandeConge != null ? idDemandeConge : -1);
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
                        if (this.estDemiJournee && cDemi && dateDebut.equals(dateFin) && cDeb.equals(cFin) && dateDebut.equals(cDeb)) {
                            if (this.momentDemiJournee != null && cMoment != null &&
                                    !this.momentDemiJournee.equalsIgnoreCase(cMoment)) {
                                continue;
                            }
                        }

                        if (!this.derogationContinuiteService) {
                            String collegueNom = rsC.getString("prenom") + " " + rsC.getString("nom");
                            String siteInfo = (nomSite != null && !nomSite.isEmpty()) ? " sur le site " + nomSite : "";
                            throw new IllegalArgumentException("Conflit de continuité de service détecté : " +
                                    collegueNom + " (même poste : " + nomFonction + siteInfo + ") est déjà en congé du " +
                                    cDeb + " au " + cFin + ". Une dérogation expresse de continuité de service est requise.");
                        }
                    }
                }
            }
        }
    }

    public String getStatutCode(Connection c) throws Exception {
        if (idStatutDemandeConge == null)
            return "BROUILLON";
        RefStatutDemandeConge st = RefStatutDemandeConge.rechercherParId(idStatutDemandeConge, c);
        return st != null ? st.getCode() : "BROUILLON";
    }

    /**
     * Valide la demande de congé, vérifie les soldes et génère le mouvement PRISE
     * si applicable.
     */
    public DemandeConge valider(Integer idValideur, Connection c) throws Exception {
        if (this.idDemandeConge == null) {
            throw new IllegalArgumentException("ID de demande de congé obligatoire pour la validation.");
        }

        // 1. Verrouillage pessimiste strict de la demande pour sérialiser les validations concurrentes
        String sqlLock = "SELECT s.code, dc.id_statut_demande_conge FROM demande_conge dc " +
                         "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                         "WHERE dc.id_demande_conge = ? FOR UPDATE";
        try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
            psLock.setInt(1, this.idDemandeConge);
            try (ResultSet rsLock = psLock.executeQuery()) {
                if (!rsLock.next()) {
                    throw new IllegalArgumentException("Demande de congé introuvable (ID: " + this.idDemandeConge + ").");
                }
                String statutCourant = rsLock.getString("code");
                this.idStatutDemandeConge = rsLock.getInt("id_statut_demande_conge");
                if (!"EN_ATTENTE".equalsIgnoreCase(statutCourant) && !"BROUILLON".equalsIgnoreCase(statutCourant)) {
                    throw new IllegalStateException("Seule une demande au statut EN_ATTENTE ou BROUILLON peut être validée (Statut actuel : " + statutCourant + ").");
                }
            }
        }

        // 1.bis Verrouillage pessimiste du salarié pour sérialiser le décompte de solde face à d'autres demandes concurrentes du même salarié
        if (this.idEmploye != null) {
            try (PreparedStatement psLockEmp = c.prepareStatement("SELECT id_employe FROM employe WHERE id_employe = ? FOR UPDATE")) {
                psLockEmp.setInt(1, this.idEmploye);
                try (ResultSet rsEmp = psLockEmp.executeQuery()) {
                    if (!rsEmp.next()) {
                        throw new IllegalArgumentException("Employé introuvable pour la validation du congé (ID: " + this.idEmploye + ").");
                    }
                }
            }
        }

        // Re-contrôle strict de toutes les règles métier et de la couverture contractuelle avant validation
        this.controler(c);

        RefTypeConge typeConge = RefTypeConge.rechercherParId(idTypeConge, c);
        if (typeConge == null) {
            throw new IllegalArgumentException("Type de congé non valide.");
        }

        // Si le type de congé entame le solde acquis (ex: ANNUEL), vérifier le solde et
        // générer le mouvement PRISE
        if (typeConge.isDecompteSoldeAcquis()) {
            VSoldeCongeEmploye solde = VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c);
            BigDecimal soldeDisponible = solde != null && solde.getSoldeActuel() != null ? solde.getSoldeActuel()
                    : BigDecimal.ZERO;
            BigDecimal nouveauSolde = soldeDisponible.subtract(nbJoursDecomptes);

            if (nouveauSolde.compareTo(BigDecimal.ZERO) < 0) {
                if (!derogationSoldeNegatif) {
                    throw new IllegalArgumentException(
                            "Solde de congé insuffisant (" + soldeDisponible + "j disponibles pour " + nbJoursDecomptes
                                    + "j demandés). Dérogation RH de solde négatif requise.");
                }
                int plafondNegatif = getParamGlobalInt("PLAFOND_SOLDE_CONGE_NEGATIF", -10, c);
                if (nouveauSolde.compareTo(BigDecimal.valueOf(plafondNegatif)) < 0) {
                    throw new IllegalArgumentException("Le solde négatif maximal d'anticipation autorisé est de "
                            + plafondNegatif + " jours (" + nouveauSolde + "j calculés).");
                }
            }

            // Génération du mouvement de solde PRISE (débit)
            MouvementSoldeConge prise = new MouvementSoldeConge();
            prise.setIdEmploye(idEmploye);
            prise.setTypeMouvement("PRISE");
            prise.setNbJours(nbJoursDecomptes.negate());
            prise.setIdDemandeConge(idDemandeConge);
            prise.setCommentaire("Prise de congé " + typeConge.getLibelle() + " (Demande #" + idDemandeConge + ")");
            prise.setCreatedBy(idValideur);
            try {
                prise.save(c);
            } catch (SQLException ePrise) {
                if (ePrise.getMessage() != null && (ePrise.getMessage().toLowerCase().contains("duplicate") || ePrise.getMessage().toLowerCase().contains("unique") || ePrise.getMessage().contains("idx_uniq_mouvement_prise_demande"))) {
                    throw new IllegalStateException("Conflit de validation concurrente : cette demande a déjà généré un mouvement de solde.");
                }
                throw ePrise;
            }

            // Trigger alerte SOLDE_CONGE_NEGATIF ou SOLDE_CONGE_FAIBLE
            int seuilFaible = getParamGlobalInt("SEUIL_ALERTE_SOLDE_CONGE_FAIBLE", 3, c);
            if (nouveauSolde.compareTo(BigDecimal.ZERO) < 0) {
                creerNotificationRh("SOLDE_CONGE_NEGATIF", "HAUTE", "Solde Négatif sous Dérogation",
                        "Le solde de congé de l'employé est passé en négatif (" + nouveauSolde
                                + "j) suite à une dérogation RH.",
                        "/conges/soldes", idEmploye, c);
            } else if (nouveauSolde.compareTo(BigDecimal.valueOf(seuilFaible)) <= 0) {
                creerNotificationRh("SOLDE_CONGE_FAIBLE", "NORMALE", "Solde de Congé Faible",
                        "Le solde de congé disponible est faible (" + nouveauSolde + "j restants).",
                        "/conges/soldes", idEmploye, c);
            }
        }

        // Trigger alerte POOL_EXCEPTIONNEL_EPUISE
        if (typeConge.getIdPoolCongeAnnuel() != null) {
            int annee = dateDebut.toLocalDate().getYear();
            List<VPoolCongeAnnuelConsommation> pools = VPoolCongeAnnuelConsommation.listParEmployeEtAnnee(idEmploye,
                    annee, c);
            BigDecimal consomme = BigDecimal.ZERO;
            for (VPoolCongeAnnuelConsommation p : pools) {
                if (typeConge.getIdPoolCongeAnnuel().equals(p.getIdPool()) && p.getJoursConsommes() != null) {
                    consomme = consomme.add(p.getJoursConsommes());
                }
            }
            if (consomme.compareTo(BigDecimal.valueOf(10)) >= 0) {
                creerNotificationRh("POOL_EXCEPTIONNEL_EPUISE", "HAUTE", "Quota Pool 10j/an Atteint",
                        "Le quota annuel de 10 jours de permissions exceptionnelles (Art. 88) pour l'année " + annee
                                + " est épuisé pour cet employé.",
                        "/conges/soldes", idEmploye, c);
            }
        }

        RefStatutDemandeConge statutValide = RefStatutDemandeConge.rechercherParCode("VALIDE", c);
        if (statutValide == null) {
            throw new IllegalStateException("Statut VALIDE non trouvé dans le référentiel.");
        }
        this.idStatutDemandeConge = statutValide.getIdStatutDemandeConge();
        this.validePar = idValideur;
        this.dateValidation = new Timestamp(System.currentTimeMillis());

        return (DemandeConge) this.update(c);
    }

    /**
     * Refuse la demande de congé avec un motif de refus explicite.
     */
    public DemandeConge refuser(Integer idValideur, String motifRefus, Connection c) throws Exception {
        String statutCourant = getStatutCode(c);
        if (!"EN_ATTENTE".equals(statutCourant)) {
            throw new IllegalStateException("Seule une demande en attente de validation peut être refusée.");
        }
        if (motifRefus == null || motifRefus.isBlank()) {
            throw new IllegalArgumentException("Le motif de refus est obligatoire.");
        }

        RefStatutDemandeConge statutRefuse = RefStatutDemandeConge.rechercherParCode("REFUSE", c);
        if (statutRefuse == null) {
            throw new IllegalStateException("Statut REFUSE non trouvé dans le référentiel.");
        }
        this.idStatutDemandeConge = statutRefuse.getIdStatutDemandeConge();
        this.validePar = idValideur;
        this.dateValidation = new Timestamp(System.currentTimeMillis());
        this.motifRefus = motifRefus.trim();

        return (DemandeConge) this.update(c);
    }

    /**
     * Annule une demande de congé. Si elle était validée et imputait le solde,
     * génère le mouvement ANNULATION (crédit).
     */
    public DemandeConge annuler(Integer idUtilisateur, Connection c) throws Exception {
        if (this.idDemandeConge == null) {
            throw new IllegalArgumentException("ID de demande de congé obligatoire pour l'annulation.");
        }

        String sqlLock = "SELECT s.code, dc.id_statut_demande_conge FROM demande_conge dc " +
                         "JOIN ref_statut_demande_conge s ON dc.id_statut_demande_conge = s.id_statut_demande_conge " +
                         "WHERE dc.id_demande_conge = ? FOR UPDATE";
        String statutCourant;
        try (PreparedStatement psLock = c.prepareStatement(sqlLock)) {
            psLock.setInt(1, this.idDemandeConge);
            try (ResultSet rsLock = psLock.executeQuery()) {
                if (!rsLock.next()) {
                    throw new IllegalArgumentException("Demande de congé introuvable (ID: " + this.idDemandeConge + ").");
                }
                statutCourant = rsLock.getString("code");
                this.idStatutDemandeConge = rsLock.getInt("id_statut_demande_conge");
                if ("ANNULE".equalsIgnoreCase(statutCourant) || "REFUSE".equalsIgnoreCase(statutCourant)) {
                    throw new IllegalStateException("Une demande de congé déjà refusée ou annulée ne peut plus être annulée.");
                }
            }
        }

        RefTypeConge typeConge = RefTypeConge.rechercherParId(idTypeConge, c);

        // Si la demande était VALIDÉE et imputait le solde acquis, générer un mouvement
        // de restitution (ANNULATION)
        if ("VALIDE".equals(statutCourant) && typeConge != null && typeConge.isDecompteSoldeAcquis()) {
            MouvementSoldeConge annulation = new MouvementSoldeConge();
            annulation.setIdEmploye(idEmploye);
            annulation.setTypeMouvement("ANNULATION");
            annulation.setNbJours(nbJoursDecomptes); // Restitution créditeur (+)
            annulation.setIdDemandeConge(idDemandeConge);
            annulation.setCommentaire("Restitution suite à l'annulation de la demande #" + idDemandeConge);
            annulation.setCreatedBy(idUtilisateur);
            annulation.save(c);
        }

        RefStatutDemandeConge statutAnnule = RefStatutDemandeConge.rechercherParCode("ANNULE", c);
        if (statutAnnule == null) {
            throw new IllegalStateException("Statut ANNULE non trouvé dans le référentiel.");
        }
        this.idStatutDemandeConge = statutAnnule.getIdStatutDemandeConge();

        return (DemandeConge) this.update(c);
    }

    /**
     * @deprecated Utiliser {@link mg.galaxam.sirh.model.dashboard.service.NotificationRhService#creer} via CongeService.
     * Cette méthode statique délègue directement à {@link mg.galaxam.sirh.model.dashboard.service.NotificationRhService#creer}.
     */
    @Deprecated
    public static void creerNotificationRh(String typeAlerte, String priorite, String titre, String message,
            String lien, Integer idEmploye, Connection c) {
        mg.galaxam.sirh.model.dashboard.service.NotificationRhService.creer(c, typeAlerte, priorite, titre, message, lien, idEmploye);
    }

    private int getParamGlobalInt(String code, int defaultValue, Connection c) {
        try {
            Map<String, String> map = ParametreGlobal.loadActiveMap(c);
            return ParametreGlobal.getInt(map, code, defaultValue);
        } catch (Exception ignored) {
        }
        return defaultValue;
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
        return rechercherParId(Integer.parseInt(id), c);
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
