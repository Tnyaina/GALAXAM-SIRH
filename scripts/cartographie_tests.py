#!/usr/bin/env python3
"""
Cartographie exhaustive de la suite de tests legacy GALAXAM.
Scanne backend/src/test/java et extrait pour chaque méthode de test :
- Classe
- Méthode
- Module (POINTAGE, CONGES, PAIE, FISCALITE, SOCIAL, TCONGE, SNAPSHOT, TRANSACTION, EXPORT, API, SECURITY, OTHER)
- Règle protégée
- Frontière protégée
- Bug historique protégé
- Niveau de test (UNIT, INTEGRATION, ARCHITECTURE, ADVERSARIAL, E2E)
- Précision de la protection (HAUTE, MOYENNE, SYSTEMIQUE)
- Futur test de remplacement
- Couverture équivalente/supérieure
- Suppression autorisée (OUI/NON)
"""

import os
import re

TEST_DIR = "backend/src/test/java"
OUTPUT_FILE = "TEST_LEGACY_COVERAGE_MAP.md"

def classify_module(filepath, class_name, method_name, content):
    lower_path = filepath.lower()
    lower_m = method_name.lower()
    lower_c = class_name.lower()
    
    if "tconge" in lower_path or "tconge" in lower_m or "tconge" in lower_c:
        return "TCONGE"
    if "conge" in lower_path or "leave" in lower_path or "conge" in lower_m or "leave" in lower_m:
        return "CONGES"
    if "pointage" in lower_path or "attendance" in lower_path or "calendrier" in lower_path or "pointage" in lower_m:
        return "POINTAGE"
    if "export" in lower_path or "excel" in lower_m or "pdf" in lower_m or "export" in lower_m:
        return "EXPORT"
    if "security" in lower_path or "adversarial" in lower_path or "cors" in lower_m or "jwt" in lower_m:
        return "SECURITY"
    if "transaction" in lower_path or "rollback" in lower_m or "concurren" in lower_m or "idempotenc" in lower_m:
        return "TRANSACTION"
    if "snapshot" in lower_path or "snapshot" in lower_m:
        return "SNAPSHOT"
    if "irsa" in lower_m or "impot" in lower_m or "fiscal" in lower_m or "tax" in lower_m:
        return "FISCALITE"
    if "cnaps" in lower_m or "sante" in lower_m or "ostie" in lower_m or "omsi" in lower_m or "fmfp" in lower_m or "social" in lower_m:
        return "SOCIAL"
    if "api" in lower_path or "controller" in lower_path or "controller" in lower_m:
        return "API"
    if "paie" in lower_path or "payroll" in lower_path or "salaire" in lower_m or "bulletin" in lower_m:
        return "PAIE"
    if "architecture" in lower_path:
        return "OTHER"
    return "OTHER"

def detect_historical_bugs(method_text, display_name):
    combined = (method_text + " " + display_name).lower()
    bugs = []
    if "sme" in combined and ("2026" in combined or "fevrier" in combined or "janvier" in combined or "mars" in combined):
        bugs.append("SME historique & transition mars 2026")
    if "cnaps" in combined and ("plafond" in combined or "8" in combined):
        bugs.append("Plafond CNaPS 8 SME")
    if "irsa" in combined and ("2025" in combined or "2026" in combined or "tranche" in combined):
        bugs.append("Barème progressif IRSA 2025/2026")
    if "20h" in combined or "exoneration" in combined or "01.03.03" in combined:
        bugs.append("Exonération HS 20h CGI Art. 01.03.03-9°")
    if "double" in combined or "anti-double" in combined:
        bugs.append("Anti-double déduction congé/absence")
    if "minute" in combined and "jour" in combined:
        bugs.append("Séparation dimensionnelle minutes vs jours")
    if "ambigu" in combined:
        bugs.append("Détection configuration ambiguë")
    if "manquant" in combined or "missing" in combined:
        bugs.append("Interdiction configuration manquante sans fallback")
    if "double validation" in combined or "idempotenc" in combined:
        bugs.append("Protection double validation / idempotence")
    if "rollback" in combined:
        bugs.append("Rollback transactionnel atomique")
    if "snapshot" in combined and "immuable" in combined:
        bugs.append("Immutabilité du snapshot scellé")
    if "trentieme" in combined or "30" in combined and "prorata" in combined:
        bugs.append("Prorata temporis au 30ème (RG-PAIE-01)")
    if "centaine" in combined or "centaine_inf" in combined:
        bugs.append("Arrondi IRSA centaine inférieure (CENTAINE_INF)")
    if "mround" in combined or "arrondi net" in combined or "multiple de 10" in combined:
        bugs.append("Arrondi final net multiple de 10 (MROUND 10)")
    if "diviseur" in combined or "173.33" in combined:
        bugs.append("Diviseur légal mensuel 173.33h (RG-POINTAGE-01)")
    if not bugs:
        return "Conformité nominale"
    return "; ".join(bugs)

def detect_boundary(method_text, display_name):
    combined = (method_text + " " + display_name).lower()
    boundaries = []
    if "19h59" in combined or "20h01" in combined or "20h" in combined:
        boundaries.append("Plafond 20h HS (19h59 / 20h00 / 20h01 / 22h)")
    if "4000000" in combined or "4 000 000" in combined:
        boundaries.append("Seuil tranche 20% IRSA (4 000 000 Ar)")
    if "350000" in combined or "350 000" in combined or "seuil exo" in combined:
        boundaries.append("Seuil d exonération IRSA (350 000 Ar)")
    if "2000" in combined or "minimum" in combined:
        boundaries.append("Minimum perception IRSA (2 000 Ar)")
    if "40h" in combined or "hebdo" in combined:
        boundaries.append("Seuil hebdomadaire légal 40h")
    if "8h" in combined or "tranche1" in combined:
        boundaries.append("Seuil HS Tranche 1 (8h supp hebdo)")
    if "8 sme" in combined or "plafond cnaps" in combined:
        boundaries.append("Plafond CNaPS 8 x SME")
    if ".004" in combined or ".005" in combined or ".006" in combined or "arrondi" in combined:
        boundaries.append("Frontières d arrondi demi-centime (.004 / .005 / .006)")
    if not boundaries:
        return "Régime nominal"
    return "; ".join(boundaries)

def main():
    records = []
    
    for root, _, files in os.walk(TEST_DIR):
        for f in sorted(files):
            if f.endswith(".java") and not f.endswith("Oracle.java") and not f.endswith("Fixtures.java") and not f.endswith("Cleaner.java"):
                fpath = os.path.join(root, f)
                with open(fpath, "r", encoding="utf-8", errors="ignore") as fh:
                    lines = fh.readlines()
                content = "".join(lines)
                class_match = re.search(r"class\s+(\w+)", content)
                class_name = class_match.group(1) if class_match else f.replace(".java", "")
                
                # Parse methods with @Test
                i = 0
                while i < len(lines):
                    line = lines[i]
                    if "@Test" in line:
                        # accumulate annotations and method signature
                        annotation_block = line
                        j = i + 1
                        while j < len(lines) and not re.search(r"(?:public\s+|protected\s+|private\s+|void\s+)(\w+)\s*\(", lines[j]):
                            annotation_block += " " + lines[j].strip()
                            j += 1
                        if j < len(lines):
                            sig_line = lines[j]
                            m_match = re.search(r"(?:public\s+|protected\s+|private\s+|void\s+)(\w+)\s*\(", sig_line)
                            if m_match:
                                method_name = m_match.group(1)
                                # Extract DisplayName if present
                                disp_match = re.search(r"@DisplayName\(\s*\"([^\"]+)\"", annotation_block)
                                display_name = disp_match.group(1) if disp_match else ""
                                
                                # Extract method body (approximate up to 50 lines)
                                body_lines = lines[j:min(j+50, len(lines))]
                                body_text = "".join(body_lines)
                                
                                module = classify_module(fpath, class_name, method_name, body_text)
                                bug = detect_historical_bugs(body_text + " " + annotation_block, display_name)
                                boundary = detect_boundary(body_text + " " + annotation_block, display_name)
                                
                                level = "INTEGRATION" if "integration" in fpath.lower() else ("E2E" if "workflow" in fpath.lower() else ("ADVERSARIAL" if "adversarial" in fpath.lower() else "UNIT"))
                                precision = "HAUTE" if (level == "UNIT" or "Oracle" in class_name or "Certification" in class_name) else ("SYSTEMIQUE" if level == "E2E" else "MOYENNE")
                                
                                rule_desc = display_name if display_name else method_name
                                rule_desc = rule_desc.replace("|", "/").strip()
                                
                                records.append({
                                    "file": f,
                                    "path": fpath,
                                    "class": class_name,
                                    "method": method_name,
                                    "display": display_name,
                                    "module": module,
                                    "rule": rule_desc,
                                    "boundary": boundary,
                                    "bug": bug,
                                    "level": level,
                                    "precision": precision
                                })
                                i = j
                    i += 1

    print(f"Total test methods indexed: {len(records)}")
    
    # Modules summary
    module_counts = {}
    for r in records:
        m = r["module"]
        module_counts[m] = module_counts.get(m, 0) + 1
        
    print("Module distribution:", module_counts)

    # Write Markdown map
    with open(OUTPUT_FILE, "w", encoding="utf-8") as out:
        out.write("# CARTOGRAPHIE EXHAUSTIVE DE LA SUITE DE TESTS LEGACY GALAXAM\n\n")
        out.write(f"**Date d'audit :** 2026-09-19  \n")
        out.write(f"**Total méthodes recensées :** {len(records)}  \n\n")
        out.write("## 1. Répartition par Module Métier\n\n")
        out.write("| Module | Nombre de tests | Protection Principale |\n")
        out.write("| :--- | :---: | :--- |\n")
        for m, count in sorted(module_counts.items(), key=lambda x: -x[1]):
            out.write(f"| **{m}** | {count} | Règles, frontières et flux critiques du domaine {m} |\n")
        out.write("\n---\n\n")
        
        out.write("## 2. Inventaire Détaillé des Protections & Matrice de Remplacement\n\n")
        out.write("> [!IMPORTANT]\n")
        out.write("> **Règle absolue :** Aucun test ne peut être supprimé sans qu'un test certifiant de remplacement soit explicitement désigné et vérifié.\n")
        out.write("> Si la colonne *Suppression autorisée* est **NON**, le test unitaire est obligatoirement conservé dans la suite finale.\n\n")
        
        out.write("| Classe | Méthode | Module | Règle Protégée | Frontière | Bug Historique | Niveau | Précision | Nouveau Test Remplaçant | Suppression Autorisée |\n")
        out.write("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: |\n")
        
        for r in records:
            # Determine replacement mapping
            mod = r["module"]
            replacement = "NON DÉFINI (CONSERVATION OBLIGATOIRE)"
            allowed = "NON"
            
            if mod == "POINTAGE":
                replacement = "pointage.AttendanceBusinessTest / AttendanceIntegrationTest"
                # Keep high precision unit tests
                allowed = "NON" if r["precision"] == "HAUTE" else "OUI (si couvert par AttendanceIntegrationTest)"
            elif mod == "CONGES" or mod == "TCONGE":
                replacement = "conges.LeaveBusinessTest / LeaveIntegrationTest"
                allowed = "NON" if r["precision"] == "HAUTE" else "OUI (si couvert par LeaveIntegrationTest)"
            elif mod == "FISCALITE" or mod == "SOCIAL" or mod == "PAIE":
                replacement = "paie.PayrollFormulaTest / PayrollIntegrationTest / PayrollGoldenMasterTest"
                allowed = "NON" if r["precision"] == "HAUTE" or "Certification" in r["class"] else "OUI (si couvert par PayrollIntegrationTest)"
            elif mod == "EXPORT":
                replacement = "export.PayrollExportCertificationTest"
                allowed = "NON" if r["precision"] == "HAUTE" else "OUI"
            elif mod == "TRANSACTION" or mod == "SNAPSHOT":
                replacement = "workflow.PayrollTransactionAndResilienceTest"
                allowed = "NON" if r["precision"] == "HAUTE" else "OUI"
            elif mod == "API" or mod == "SECURITY":
                replacement = "workflow.PayrollSecurityAndWorkflowApiTest"
                allowed = "NON" if r["precision"] == "HAUTE" else "OUI"
            else:
                replacement = "Conservation spécifique"
                allowed = "NON"

            out.write(f"| `{r['class']}` | `{r['method']}` | {r['module']} | {r['rule']} | {r['boundary']} | {r['bug']} | {r['level']} | {r['precision']} | {replacement} | **{allowed}** |\n")

    print(f"Cartographie générée avec succès dans {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
