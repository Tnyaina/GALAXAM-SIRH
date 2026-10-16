package mg.galaxam.sirh.contrat.domain;


import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Source unique des règles métier de transition de carrière.
 *
 * Le Validator est volontairement la source de vérité backend afin de préserver
 * les consommateurs existants qui l'appellent déjà depuis WorkflowRhService.
 *
 * La matrice est portée par le backend. Les seuils numériques sont lus depuis
 * parametre_global afin de rester configurables sans nouvelle table.
 */
public final class CarriereTransitionValidator {

    private CarriereTransitionValidator() {}

    public record ContraintesEvenement(
            List<String> typesContratAutorises,
            List<String> typesIntervenantAutorises,
            boolean autorisePeriodeEssai) {}

    private static final Map<String, Set<String>> EVENEMENTS_AUTORISES_PAR_SOURCE = Map.of(
            "STAGE", Set.of(
                    "EMBAUCHE_APRES_STAGE",
                    "FIN_CONTRAT", "RUPTURE_ESSAI", "DEMISSION"),
            "CDD", Set.of(
                    "TRANSFORMATION_CDI", "RENOUVELLEMENT_CDD", "REPRISE_ANCIENNETE",
                    "MUTATION", "PROMOTION", "REVALORISATION", "REORGANISATION",
                    "CONFIRMATION_ESSAI", "RENOUVELLEMENT_ESSAI", "RUPTURE_ESSAI",
                    "FIN_CONTRAT", "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE", "DEPART_RETRAITE"),
            "CDI", Set.of(
                    "REPRISE_ANCIENNETE", "MUTATION", "PROMOTION", "REVALORISATION",
                    "REORGANISATION", "REPRISE_CONSULTANT",
                    "CONFIRMATION_ESSAI", "RENOUVELLEMENT_ESSAI", "RUPTURE_ESSAI",
                    "DEMISSION", "LICENCIEMENT", "RUPTURE_CONVENTIONNELLE", "DEPART_RETRAITE", "FIN_CONTRAT"),
            "PRESTATION", Set.of(
                    "TRANSFORMATION_CDI",
                    "FIN_CONTRAT", "RUPTURE_CONVENTIONNELLE"),
            "TEMPORAIRE", Set.of(
                    "TRANSFORMATION_CDI",
                    "FIN_CONTRAT", "RUPTURE_CONVENTIONNELLE"));

    private static final Map<String, ContraintesEvenement> CONTRAINTES_PAR_EVENEMENT = Map.ofEntries(
            Map.entry("EMBAUCHE_APRES_STAGE", new ContraintesEvenement(List.of("CDI", "CDD"), List.of("PERMANENT"), true)),
            Map.entry("TRANSFORMATION_CDI", new ContraintesEvenement(List.of("CDI"), List.of("PERMANENT"), false)),
            Map.entry("RENOUVELLEMENT_CDD", new ContraintesEvenement(List.of("CDD"), List.of("PERMANENT"), false)),
            Map.entry("REPRISE_CONSULTANT", new ContraintesEvenement(List.of("PRESTATION"), List.of("CONSULTANT"), false)),
            Map.entry("CONFIRMATION_ESSAI", new ContraintesEvenement(List.of("CDD", "CDI"), List.of(), false)),
            Map.entry("RENOUVELLEMENT_ESSAI", new ContraintesEvenement(List.of("CDD", "CDI"), List.of(), false)),
            Map.entry("RUPTURE_ESSAI", new ContraintesEvenement(List.of(), List.of(), false)),
            Map.entry("DEMISSION", new ContraintesEvenement(List.of(), List.of(), false)),
            Map.entry("LICENCIEMENT", new ContraintesEvenement(List.of(), List.of(), false)),
            Map.entry("RUPTURE_CONVENTIONNELLE", new ContraintesEvenement(List.of(), List.of(), false)),
            Map.entry("DEPART_RETRAITE", new ContraintesEvenement(List.of(), List.of(), false)),
            Map.entry("FIN_CONTRAT", new ContraintesEvenement(List.of(), List.of(), false)));

    /**
     * Compatibilité avec les appels existants qui ne disposent pas de connexion.
     * La validation structurelle reste centralisée ici.
     */
    public static void validerTransition(
            String sourceCode,
            String eventCode,
            String targetCode,
            String targetIntervenant,
            Integer dureeEssaiMois) {
        validerMatrice(sourceCode, eventCode, targetCode, targetIntervenant, dureeEssaiMois);
    }

    /**
     * Validation complète avec contexte DB pour les règles dynamiques
     * (période d'essai active, doublons, compteurs de renouvellement, plafonds CDD).
     */
    public static void validerTransition(
            String sourceCode,
            String eventCode,
            String targetCode,
            String targetIntervenant,
            Integer dureeEssaiMois,
            Integer idContratReference,
            Date dateDebutCible,
            Date dateFinCible,
            Connection c) throws Exception {

        validerMatrice(sourceCode, eventCode, targetCode, targetIntervenant, dureeEssaiMois);

        String event = normalize(eventCode);

        if ("RENOUVELLEMENT_CDD".equals(event)) {
            validerRenouvellementCDD(idContratReference, dateDebutCible, dateFinCible, c);
        } else if ("CONFIRMATION_ESSAI".equals(event)) {
            validerConfirmationEssai(idContratReference, c);
        } else if ("RENOUVELLEMENT_ESSAI".equals(event)) {
            validerRenouvellementEssai(idContratReference, dateDebutCible, dateFinCible, c);
        } else if ("RUPTURE_ESSAI".equals(event)) {
            validerRuptureEssai(idContratReference, c);
        } else if ("CDD".equals(normalize(targetCode)) && dateDebutCible != null && dateFinCible != null) {
            validerDureeContratCDD(dateDebutCible, dateFinCible, c);
        }
    }

    private static void validerMatrice(
            String sourceCode,
            String eventCode,
            String targetCode,
            String targetIntervenant,
            Integer dureeEssaiMois) {

        if (eventCode == null || eventCode.isBlank()) {
            throw new IllegalArgumentException("Le type d'événement RH est obligatoire.");
        }

        String source = normalize(sourceCode);
        String event = normalize(eventCode);

        if ("STAGE".equals(source) && Set.of("MUTATION", "PROMOTION", "REVALORISATION", "REORGANISATION", "TRANSFORMATION_CDI", "RENOUVELLEMENT_CDD").contains(event)) {
            throw new IllegalArgumentException(
                    "Un collaborateur sous convention de Stage ne peut pas faire l'objet d'un avenant interne. " +
                    "Veuillez procéder à un 'Passage Stage vers CDD/CDI' ou enregistrer une fin de stage.");
        }

        Set<String> autorises = EVENEMENTS_AUTORISES_PAR_SOURCE.get(source);
        if (autorises != null && !autorises.contains(event)) {
            throw new IllegalArgumentException(genererMessageIneligibilite(source, event));
        }

        ContraintesEvenement regle = CONTRAINTES_PAR_EVENEMENT.get(event);
        if (regle == null) {
            return;
        }

        if (targetCode != null && !targetCode.isBlank()) {
            String cible = normalize(targetCode);
            if (!regle.typesContratAutorises().isEmpty() && !regle.typesContratAutorises().contains(cible)) {
                throw new IllegalArgumentException(
                        "Pour l'événement '" + event + "', le type de contrat cible doit être parmi "
                                + regle.typesContratAutorises() + " (reçu: " + cible + ").");
            }
        }

        if (targetIntervenant != null && !targetIntervenant.isBlank()
                && !regle.typesIntervenantAutorises().isEmpty()) {
            String intervenant = normalize(targetIntervenant);
            if (!regle.typesIntervenantAutorises().contains(intervenant)) {
                throw new IllegalArgumentException(
                        "Pour l'événement '" + event + "', le régime d'intervenant cible doit être parmi "
                                + regle.typesIntervenantAutorises() + " (reçu: " + intervenant + ").");
            }
        }

        if (!regle.autorisePeriodeEssai() && dureeEssaiMois != null && dureeEssaiMois > 0
                && !"CONFIRMATION_ESSAI".equals(event) && !"RENOUVELLEMENT_ESSAI".equals(event)) {
            throw new IllegalArgumentException(
                    "Une nouvelle période d'essai est interdite pour l'événement '" + event + "'.");
        }
    }

    private static void validerConfirmationEssai(Integer idContrat, Connection c) throws Exception {
        exigerContrat(idContrat, "La confirmation de la période d'essai nécessite un contrat cible.");
        if (!periodeEssaiEnCours(idContrat, c)) {
            throw new IllegalStateException(
                    "Aucune période d'essai EN_COURS trouvée pour ce contrat. Impossible de confirmer une période déjà terminée ou inexistante.");
        }
        if (workflowExiste(idContrat, "CONFIRMATION_ESSAI", c)) {
            throw new IllegalStateException(
                    "La période d'essai de ce contrat a déjà été confirmée ou une confirmation est déjà planifiée.");
        }
    }

    private static void validerRuptureEssai(Integer idContrat, Connection c) throws Exception {
        exigerContrat(idContrat, "La rupture de la période d'essai nécessite un contrat de référence.");
        if (!periodeEssaiEnCours(idContrat, c)) {
            throw new IllegalStateException(
                    "Aucune période d'essai EN_COURS trouvée pour ce contrat. Impossible de rompre une période déjà terminée ou inexistante.");
        }
    }

    private static void validerRenouvellementEssai(
            Integer idContrat, Date dateDecision, Date nouvelleDateFin, Connection c) throws Exception {
        exigerContrat(idContrat, "Le renouvellement de la période d'essai nécessite un contrat cible.");
        if (!periodeEssaiEnCours(idContrat, c)) {
            throw new IllegalStateException(
                    "Aucune période d'essai EN_COURS trouvée pour ce contrat. Impossible de renouveler une période déjà terminée, confirmée ou inexistante.");
        }

        LocalDate refDate = dateDecision != null ? dateDecision.toLocalDate() : LocalDate.now();
        int maxRenouvellements = resolveParametreGlobalInt("MAX_RENOUVELLEMENTS_ESSAI", refDate, 1, c);
        Date finEssaiEnCours = null;
        String sql = "SELECT COUNT(*), MAX(date_fin_prevue) FROM contrat_periode_essai " +
                "WHERE id_contrat = ? AND numero_periode > 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= maxRenouvellements) {
                    throw new IllegalArgumentException(
                            "Le nombre maximal de renouvellements de la période d'essai (" + maxRenouvellements
                                    + ") a déjà été atteint. Pour modifier cette limite, ajustez le paramètre MAX_RENOUVELLEMENTS_ESSAI.");
                }
            }
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT date_fin_prevue FROM contrat_periode_essai WHERE id_contrat = ? AND statut = 'EN_COURS' " +
                "ORDER BY numero_periode DESC LIMIT 1")) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) finEssaiEnCours = rs.getDate(1);
            }
        }
        if (dateDecision == null || nouvelleDateFin == null) {
            throw new IllegalArgumentException(
                    "La date de décision et la nouvelle date de fin sont obligatoires pour renouveler une période d'essai.");
        }
        if (finEssaiEnCours == null || dateDecision.after(finEssaiEnCours)) {
            throw new IllegalArgumentException(
                    "Le renouvellement doit être décidé au plus tard à la date de fin prévue de la période d'essai en cours.");
        }
        
        if (!nouvelleDateFin.after(finEssaiEnCours)) {
            throw new IllegalArgumentException(
                    "La nouvelle date de fin d'essai doit être postérieure à la fin prévue de la période en cours.");
        }

        if (workflowExiste(idContrat, "CONFIRMATION_ESSAI", c)
                || workflowExiste(idContrat, "RENOUVELLEMENT_ESSAI", c)) {
            throw new IllegalStateException(
                    "Une confirmation ou un renouvellement de la période d'essai est déjà planifié ou appliqué pour ce contrat.");
        }
    }

    public static void validerDureeContratCDD(Date dateDebut, Date dateFin, Connection c) throws Exception {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException(
                    "Les dates de début et de fin sont obligatoires pour un contrat CDD.");
        }
        if (!dateFin.after(dateDebut)) {
            throw new IllegalArgumentException("La date de fin du CDD doit être postérieure à sa date de début.");
        }
        if (dateFin.toLocalDate().isBefore(dateDebut.toLocalDate().plusMonths(1))) {
            throw new IllegalArgumentException(
                    "Un CDD doit avoir une durée minimale d'un mois selon le Code du travail malgache.");
        }
        LocalDate refDate = dateDebut.toLocalDate();
        int maxDureeIndividuelle = resolveParametreGlobalInt("DUREE_MAX_CONTRAT_CDD_MOIS", refDate, 24, c);
        if (dateFin.toLocalDate().isAfter(dateDebut.toLocalDate().plusMonths(maxDureeIndividuelle))) {
            throw new IllegalArgumentException(
                    "Chaque CDD, renouvellement compris, ne peut excéder " + maxDureeIndividuelle
                            + " mois (" + (maxDureeIndividuelle / 12) + " ans) selon la réglementation en vigueur.");
        }
    }

    private static void validerRenouvellementCDD(
            Integer idContrat,
            Date dateDebut,
            Date dateFin,
            Connection c) throws Exception {

        exigerContrat(idContrat, "Le renouvellement de CDD nécessite un contrat source.");
        validerDureeContratCDD(dateDebut, dateFin, c);

        LocalDate refDate = dateDebut.toLocalDate();
        int maxRenouvellements = resolveParametreGlobalInt("NB_MAX_RENOUVELLEMENTS_CDD", refDate, 2, c);
        int nbExistants = compterRenouvellementsCDD(idContrat, c);
        if (nbExistants >= maxRenouvellements) {
            throw new IllegalArgumentException(
                    "Le CDD a déjà atteint le nombre maximal de renouvellements autorisés ("
                            + maxRenouvellements + "). Au-delà, le contrat doit être requalifié en CDI.");
        }

        int maxMoisCumule = resolveParametreGlobalInt("DUREE_MAX_CDD_MOIS", refDate, 72, c);
        double dureeCumuleeMois = calculerDureeCumuleeCDD(idContrat, dateDebut, dateFin, c);
        if (dureeCumuleeMois > maxMoisCumule) {
            throw new IllegalArgumentException(
                    "La durée totale cumulée des CDD (" + String.format("%.1f", dureeCumuleeMois)
                            + " mois) dépasse le plafond configuré de " + maxMoisCumule
                            + " mois. Le contrat doit être requalifié en CDI.");
        }
    }

    private static boolean periodeEssaiEnCours(Integer idContrat, Connection c) throws Exception {
        String sql = "SELECT COUNT(*) FROM contrat_travail ct "
                + "LEFT JOIN contrat_periode_essai pe ON pe.id_contrat = ct.id_contrat AND pe.statut = 'EN_COURS' "
                + "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh "
                + "WHERE ct.id_contrat = ? AND s.code IN ('ACTIF', 'BROUILLON', 'PLANIFIE') "
                + "AND (pe.id_periode_essai IS NOT NULL OR ct.date_fin_essai IS NOT NULL)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean workflowExiste(Integer idContrat, String eventCode, Connection c) throws Exception {
        String sql = "SELECT COUNT(*) FROM workflow_rh w " +
                "JOIN ref_statut_rh s ON w.id_statut_rh = s.id_statut_rh " +
                "JOIN ref_type_evenement_rh ev ON w.id_type_evenement_rh = ev.id_type_evenement_rh " +
                "WHERE ev.code = ? AND s.code IN ('ACTIF','PLANIFIE') " +
                "AND (w.id_contrat_cible = ? OR w.id_contrat_source = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, eventCode);
            ps.setInt(2, idContrat);
            ps.setInt(3, idContrat);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static int compterRenouvellementsCDD(Integer idContrat, Connection c) throws Exception {
        int count = 0;
        Integer currentId = idContrat;
        while (currentId != null) {
            String sql = "SELECT ct.id_contrat, ct.id_contrat_precedent, tc.code, tc_prec.code AS code_prec " +
                    "FROM contrat_travail ct " +
                    "JOIN ref_type_contrat tc ON ct.id_type_contrat = tc.id_type_contrat " +
                    "LEFT JOIN contrat_travail ct_prec ON ct.id_contrat_precedent = ct_prec.id_contrat " +
                    "LEFT JOIN ref_type_contrat tc_prec ON ct_prec.id_type_contrat = tc_prec.id_type_contrat " +
                    "WHERE ct.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, currentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) break;
                    String code = rs.getString("code");
                    String codePrec = rs.getString("code_prec");
                    Integer precedent = rs.getObject("id_contrat_precedent") != null
                            ? rs.getInt("id_contrat_precedent") : null;

                    if (!"CDD".equals(normalize(code))) break;

                    if ("CDD".equals(normalize(codePrec))) {
                        count++;
                        currentId = precedent;
                    } else {
                        // Le précédent n'est pas un CDD (ex: stage, prestation ou aucun contrat) -> c'est le CDD initial
                        break;
                    }
                }
            }
        }
        return count;
    }

    private static double calculerDureeCumuleeCDD(
            Integer idContrat,
            Date nouvelleDateDebut,
            Date nouvelleDateFin,
            Connection c) throws Exception {
        double totalJours = 0;
        if (nouvelleDateDebut != null && nouvelleDateFin != null) {
            totalJours += Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                    nouvelleDateDebut.toLocalDate(), nouvelleDateFin.toLocalDate().plusDays(1)));
        }

        Integer currentId = idContrat;
        while (currentId != null) {
            String sql = "SELECT ct.date_debut, ct.date_fin, ct.id_contrat_precedent, tc.code " +
                    "FROM contrat_travail ct JOIN ref_type_contrat tc ON ct.id_type_contrat = tc.id_type_contrat " +
                    "WHERE ct.id_contrat = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, currentId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) break;
                    String code = rs.getString("code");
                    Date debut = rs.getDate("date_debut");
                    Date fin = rs.getDate("date_fin");
                    Integer precedent = rs.getObject("id_contrat_precedent") != null
                            ? rs.getInt("id_contrat_precedent") : null;
                    if ("CDD".equals(normalize(code)) && debut != null && fin != null) {
                        totalJours += Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(
                                debut.toLocalDate(), fin.toLocalDate().plusDays(1)));
                    }
                    currentId = precedent;
                }
            }
        }
        return totalJours / 30.4375;
    }

    public static int resolveParametreGlobalInt(String code, int defaultValue, Connection c) throws Exception {
        return resolveParametreGlobalInt(code, LocalDate.now(), defaultValue, c);
    }

    public static int resolveParametreGlobalInt(String code, LocalDate dateRef, int defaultValue, Connection c) throws Exception {
        LocalDate target = dateRef != null ? dateRef : LocalDate.now();
        var resolution = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal
                .resolveAtDate(code, Date.valueOf(target), c);
        if (!resolution.isActive() || resolution.valeur() == null || resolution.valeur().isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(resolution.valeur().trim());
    }

    private static void exigerContrat(Integer idContrat, String message) {
        if (idContrat == null) throw new IllegalArgumentException(message);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String genererMessageIneligibilite(String source, String evenement) {
        return switch (evenement) {
            case "EMBAUCHE_APRES_STAGE" ->
                    "L'événement 'Passage Stage vers CDD/CDI' est réservé exclusivement aux collaborateurs sous convention de Stage (contrat actuel: " + source + ").";
            case "TRANSFORMATION_CDI" ->
                    "La transformation en CDI requiert un contrat source compatible avec cette transition (contrat actuel: " + source + ").";
            case "RENOUVELLEMENT_CDD" ->
                    "Le renouvellement de CDD requiert un contrat source de type CDD (contrat actuel: " + source + ").";
            case "CONFIRMATION_ESSAI", "RENOUVELLEMENT_ESSAI", "RUPTURE_ESSAI" ->
                    "L'événement '" + evenement + "' nécessite un contrat avec période d'essai de type CDD ou CDI (contrat actuel: " + source + ").";
            case "REPRISE_CONSULTANT" ->
                    "Le passage en consultant indépendant requiert un contrat source compatible avec cette transition (contrat actuel: " + source + ").";
            case "MUTATION", "PROMOTION", "REVALORISATION", "REORGANISATION" -> {
                if ("PRESTATION".equals(source) || "TEMPORAIRE".equals(source)) {
                    yield "Les intervenants sous contrat '" + source + "' ne peuvent pas faire l'objet d'avenants de carrière interne de salarié (risque de requalification légale). Veuillez procéder à une embauche en CDI.";
                }
                yield "L'événement '" + evenement + "' n'est pas autorisé pour un contrat source de type '" + source + "'.";
            }
            default ->
                    "L'événement '" + evenement + "' n'est pas autorisé pour un contrat source de type '" + source + "'.";
        };
    }
}
