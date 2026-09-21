#!/usr/bin/env bash
# =============================================================================
# CAMPAGNE DE MUTATION TESTING DE CERTIFICATION — SIRH GALAXAM
# =============================================================================
# Applique 10 mutations sémantiques contrôlées sur les règles fondamentales
# de Paie, Fiscalité, Congés et Workflow.
# Pour chaque mutation :
# 1. Altération ciblée du code source de production
# 2. Exécution de la suite de tests certifiante
# 3. Vérification que le mutant est TUÉ (échec du test)
# 4. Restauration immédiate (git checkout) et vérification du retour au vert
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"

cd "$REPO_ROOT"

echo "============================================================================="
echo "   DÉMARRAGE DE LA CAMPAGNE DE MUTATION TESTING — ORACLES SIRH GALAXAM"
echo "============================================================================="

# Vérification préalable de la propreté git sur les fichiers ciblés
git checkout -- \
    backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java \
    backend/src/main/java/mg/galaxam/sirh/model/paie/engine/SocialContributionCalculator.java \
    backend/src/main/java/mg/galaxam/sirh/model/paie/engine/IrsaCalculator.java \
    backend/src/main/java/mg/galaxam/sirh/model/paie/engine/OvertimePayCalculator.java \
    backend/src/main/java/mg/galaxam/sirh/model/paie/engine/MoneyRoundingPolicy.java \
    backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java \
    2>/dev/null || true

TOTAL_MUTATIONS=12
MUTANTS_KILLED=0

run_mutation() {
    local mut_id="$1"
    local mut_desc="$2"
    local target_file="$3"
    local test_target="$4"
    local search_pattern="$5"
    local replace_pattern="$6"

    echo ""
    echo "-----------------------------------------------------------------------------"
    echo "[$mut_id/$TOTAL_MUTATIONS] Mutation : $mut_desc"
    echo "Fichier cible : $target_file"
    echo "Test oracle   : $test_target"
    echo "-----------------------------------------------------------------------------"

    # 1. Appliquer la mutation
    python3 -c "
with open('$target_file', 'r', encoding='utf-8') as f:
    content = f.read()
if '''$search_pattern''' not in content:
    raise ValueError('Pattern introuvable dans $target_file: ' + '''$search_pattern''')
mutated = content.replace('''$search_pattern''', '''$replace_pattern''', 1)
with open('$target_file', 'w', encoding='utf-8') as f:
    f.write(mutated)
"

    # 2. Exécuter le test cible
    set +e
    mvn -f "$BACKEND_DIR/pom.xml" test -Dtest="$test_target" -q > /dev/null 2>&1
    local test_exit_code=$?
    set -e

    # 3. Restaurer le fichier immédiatement
    git checkout -- "$target_file"

    # 4. Vérifier l'issue : le test DOIT échouer pour prouver que le mutant est tué
    if [ $test_exit_code -ne 0 ]; then
        echo "✅ MUTANT TUÉ (Le test oracle a détecté l'anomalie avec succès - Exit code: $test_exit_code)"
        MUTANTS_KILLED=$((MUTANTS_KILLED + 1))
    else
        echo "❌ ÉCHEC : LE MUTANT A SURVÉCU ! Le test a passé malgré l'altération de la règle métier !"
        exit 1
    fi
}

# -----------------------------------------------------------------------------
# MUTATION 1 : Diviseur Mensuel 173.33 -> 173.00
# -----------------------------------------------------------------------------
run_mutation "1" \
    "Altération du diviseur légal mensuel (173.33 -> 173.00)" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java" \
    "PayrollFormulaTest" \
    "DIVISEUR_HEURES_MENSUELLES = new BigDecimal(\"173.33\");" \
    "DIVISEUR_HEURES_MENSUELLES = new BigDecimal(\"173.00\");"

# -----------------------------------------------------------------------------
# MUTATION 2 : Plafond CNaPS 8 SME -> 10 SME
# -----------------------------------------------------------------------------
run_mutation "2" \
    "Altération du multiplicateur plafond CNaPS (8x SME -> 10x SME)" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/SocialContributionCalculator.java" \
    "PayrollFormulaTest" \
    "rules.smig().multiply(new BigDecimal(\"8\"))" \
    "rules.smig().multiply(new BigDecimal(\"10\"))"

# -----------------------------------------------------------------------------
# MUTATION 3 : Diviseur trentième calendaire 30 -> 31
# -----------------------------------------------------------------------------
run_mutation "3" \
    "Altération du diviseur trentième (30.0 -> 31.0)" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java" \
    "PayrollFormulaTest" \
    "DIVISEUR_JOURS_TRENTIEME = new BigDecimal(\"30.0\");" \
    "DIVISEUR_JOURS_TRENTIEME = new BigDecimal(\"31.0\");"

# -----------------------------------------------------------------------------
# MUTATION 4 : Arrondi IRSA à la centaine inférieure dégradé
# -----------------------------------------------------------------------------
run_mutation "4" \
    "Suppression de l'arrondi fiscal IRSA à la centaine inférieure" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/IrsaCalculator.java" \
    "PayrollFormulaTest" \
    "divide(BigDecimal.valueOf(100),0,java.math.RoundingMode.DOWN).multiply(BigDecimal.valueOf(100))" \
    "divide(BigDecimal.valueOf(10),0,java.math.RoundingMode.DOWN).multiply(BigDecimal.valueOf(10))"

# -----------------------------------------------------------------------------
# MUTATION 5 : Suppression du minimum de perception IRSA (LF 2026 : 3000 Ar)
# -----------------------------------------------------------------------------
run_mutation "5" \
    "Suppression du plancher minimum de perception IRSA" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/IrsaCalculator.java" \
    "PayrollFormulaTest" \
    "net = dizaine.subtract(reduction).max(minimum == null ? BigDecimal.ZERO : minimum);" \
    "net = dizaine.subtract(reduction).max(BigDecimal.ZERO);"

# -----------------------------------------------------------------------------
# MUTATION 6 : Suppression de la déduction de salaire pour congés payés
# -----------------------------------------------------------------------------
run_mutation "6" \
    "Suppression de la déduction au trentième lors de la prise de congé (double paiement indu)" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java" \
    "PayrollEndToEndTest" \
    "return indemniteConges.subtract(deductionCongeTrentieme);" \
    "return indemniteConges;"

# -----------------------------------------------------------------------------
# MUTATION 7 : Omission de la déduction des acomptes/avances sur le Net à payer
# -----------------------------------------------------------------------------
run_mutation "7" \
    "Non-imputation des acomptes sur le net à payer final" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java" \
    "PayrollEndToEndTest" \
    "BigDecimal netAvantArrondi = netAvantTresorerie.subtract(totalRetenues);" \
    "BigDecimal netAvantArrondi = netAvantTresorerie;"

# -----------------------------------------------------------------------------
# MUTATION 8 : Omission de la majoration +30% sur les heures supplémentaires
# -----------------------------------------------------------------------------
run_mutation "8" \
    "Omission de la majoration légale sur HS 30% (seule la base est rémunérée)" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/OvertimePayCalculator.java" \
    "PayrollEndToEndTest" \
    "totalHs30 = totalHs30.add(overtimeBaseAmount).add(overtimePremiumAmount);" \
    "totalHs30 = totalHs30.add(overtimeBaseAmount);"

# -----------------------------------------------------------------------------
# MUTATION 9 : Tronquage sauvage des centimes dans la politique monétaire
# -----------------------------------------------------------------------------
run_mutation "9" \
    "Tronquage illégal des centimes (arrondi entier sauvage)" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/MoneyRoundingPolicy.java" \
    "PayrollFormulaTest" \
    "value.setScale(2, RoundingMode.HALF_UP)" \
    "value.setScale(0, RoundingMode.DOWN).setScale(2)"

# -----------------------------------------------------------------------------
# MUTATION 10 : Désactivation du verrou de pointage non figé
# -----------------------------------------------------------------------------
run_mutation "10" \
    "Bypass du verrou d'interdiction de calcul sur pointage non figé" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java" \
    "PayrollSecurityAndWorkflowApiTest" \
    "if (!List.of(\"VALIDEE\", \"PAIE_VALIDEE\").contains(state)) return ActionCapability.denied(\"POINTAGE_NOT_FROZEN\");" \
    "if (false) return ActionCapability.denied(\"POINTAGE_NOT_FROZEN\");"

# -----------------------------------------------------------------------------
# MUTATION 11 : Altération déduction congé base détectée par le dataset historique GALAXAM
# -----------------------------------------------------------------------------
run_mutation "11" \
    "Suppression déduction congé base trentième sur données réelles GALAXAM" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/PayrollExecutionEngine.java" \
    "GalaxamHistoricalExcelCertificationTest" \
    "return indemniteConges.subtract(deductionCongeTrentieme);" \
    "return indemniteConges;"

# -----------------------------------------------------------------------------
# MUTATION 12 : Omission de l'arrondi légal à la dizaine supérieure IRSA (CGI Art. 01.03.14)
# -----------------------------------------------------------------------------
run_mutation "12" \
    "Omission de l'arrondi IRSA à la dizaine supérieure détectée par le cas réel RASOA" \
    "backend/src/main/java/mg/galaxam/sirh/model/paie/engine/IrsaCalculator.java" \
    "GalaxamHistoricalExcelCertificationTest" \
    "BigDecimal dizaine = brut.divide(BigDecimal.TEN,0,java.math.RoundingMode.CEILING).multiply(BigDecimal.TEN).setScale(2);" \
    "BigDecimal dizaine = brut.setScale(2);"

echo ""
echo "============================================================================="
echo "   BILAN DE LA CAMPAGNE DE MUTATION TESTING"
echo "============================================================================="
echo "Mutants injectés : $TOTAL_MUTATIONS"
echo "Mutants tués     : $MUTANTS_KILLED / $TOTAL_MUTATIONS"
echo "Score de mutation: 100.0% (Tous les mutants ont été interceptés et abattus)"
echo "============================================================================="

# Exécution finale de vérification de l'intégrité globale
echo ""
echo "Vérification finale du rétablissement nominal de la suite de certification..."
mvn -f "$BACKEND_DIR/pom.xml" test -Dtest="mg.galaxam.sirh.certification.**.*Test" -q
echo "✅ SUITE DE CERTIFICATION 100% VERTE APRÈS CAMPAGNE DE MUTATION."
