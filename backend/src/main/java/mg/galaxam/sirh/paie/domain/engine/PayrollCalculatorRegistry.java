package mg.galaxam.sirh.paie.domain.engine;

import java.util.Set;
import java.util.List;

/** Catalogue des calculateurs spécialisés. Leurs paramètres restent dans leur domaine propriétaire. */
public final class PayrollCalculatorRegistry {
    private PayrollCalculatorRegistry() {}
    private static final Set<String> LEAVE=Set.of("RETENUE_CONGE","INDEMNITE_CONGE","DEDUCTION_CONGE_BASE","INDEMNITE_CONGE_PAYE",
            "INDEMNITE_COMPENSATRICE_CONGE","INDEMNITE_PREAVIS","INDEMNITE_LICENCIEMENT");
    private static final Set<String> PREMIUM=Set.of("PRIME_PERFORMANCE","PRIME_ASSIDUITE","PRIME_ANCIENNETE");
    private static final Set<String> CASH=Set.of("RETENUE_ACOMPTE_15","RETENUE_AVANCE_SPECIALE","RETENUE_PRET","RETENUE_ECHEANCE_PRET","RETENUE_CALL_PERSO");
    public record Capability(String niveau,String calculateur,String phase,String source,String arrondi) {}
    /** Ordre des paliers effectivement exécutés par PayrollExecutionEngine. */
    public record Phase(String code, String libelle, String description) {}
    public static List<Phase> phases() {
        return List.of(
                new Phase("BRUT", "Brut et gains", "Salaire, présence, congés, heures et gains bruts"),
                new Phase("COTISATIONS", "Cotisations sociales", "Cotisations calculées sur leurs assiettes"),
                new Phase("NET_IMPOSABLE", "Net imposable", "Résultat du brut et des cotisations salariales"),
                new Phase("IMPOT", "Fiscalité", "Impôt calculé selon le régime applicable"),
                new Phase("GAIN_NET", "Ajouts au net", "Gains intervenant après la fiscalité"),
                new Phase("RETENUE_NET", "Retenues sur le net", "Acomptes, avances, prêts et retenues nettes"),
                new Phase("NET_FINAL", "Net à payer", "Résultat final et arrondi monétaire"));
    }
    public static Capability describe(String code, String category, boolean fixed, String stage) {
        if("REMBOURSEMENT_NOTE_FRAIS".equals(code)) return new Capability("SEMI_CONFIGURABLE","NOTES_DE_FRAIS","GAIN_NET","Notes de frais approuvées avec justificatif","Deux décimales");
        if(LEAVE.contains(code)) return new Capability("SYSTEM","CONGES_ET_RUPTURE","BRUT","Congés validés, taux historique et contrat","Calculateur métier");
        if(PREMIUM.contains(code)) return new Capability("SEMI_CONFIGURABLE","PRIMES_METIER","BRUT","Forfaits et paramètres dédiés","Calculateur métier");
        if(CASH.contains(code)) return new Capability("SYSTEM","TRESORERIE","RETENUE_NET","Mouvements approuvés et échéances","Deux décimales et plafonds dédiés");
        if("IMPOT".equals(category)) return new Capability("SEMI_CONFIGURABLE","FISCALITE","IMPOT","Barème daté et profil fiscal","Politique fiscale dédiée");
        if(category!=null && category.startsWith("COTISATION")) return new Capability("SEMI_CONFIGURABLE","COTISATIONS","COTISATIONS","Taux datés, assiettes et profil social","Deux décimales et plafonds dédiés");
        if("HEURES_SUP".equals(category)) return new Capability("SEMI_CONFIGURABLE","HEURES_MAJOREES","BRUT","Pointage scellé et taux datés","Calculateur métier");
        if("SALAIRE_BASE".equals(code) || "RETENUE_ABSENCE".equals(code)) return new Capability("SYSTEM","SALAIRE_PRESENCE","BRUT","Contrat et pointage scellé","Calculateur métier");
        if("AJUSTEMENT_ARRONDI".equals(code) || "NET".equals(category)) return new Capability("SYSTEM","NET_FINAL","NET_FINAL","Résultat des étapes précédentes","Politique de règlement dédiée");
        if(fixed || !Set.of("PRIME","INDEMNITE_IMPOSABLE","INDEMNITE_NON_IMPOSABLE","RETENUE_DIVERSE").contains(category==null?"":category))
            return new Capability("SYSTEM","METIER","DOMAINE","Domaine propriétaire","Calculateur métier");
        return new Capability("CONFIGURABLE","VARIABLE_MANUELLE",stage,"Saisie approuvée et règle versionnée","Politique de la rubrique");
    }
}
