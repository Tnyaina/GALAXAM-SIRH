import json
import sys

def main():
    with open('target/excel_audit_raw.json', 'r', encoding='utf-8') as f:
        data = json.load(f)

    md = []
    md.append("# CARTOGRAPHIE DES SOURCES EXCEL HISTORIQUES — SIRH GALAXAM")
    md.append("> Document d'inventaire technique exhaustif généré dans le cadre de la Phase 1 de certification.")
    md.append("> Analyse structurelle, dimensionnelle, typologique et sémantique des 3 classeurs historiques.")
    md.append("")

    for wb in data:
        fname = wb['file']
        fformat = wb['format']
        names = wb['defined_names'] if wb['defined_names'] else "Aucune"
        md.append(f"## 📁 Fichier : `{fname}` ({fformat})")
        md.append(f"- **Plages nommées (Named Ranges)** : `{names}`")
        md.append(f"- **Nombre d'onglets (Sheets)** : `{len(wb['sheets'])}`")
        md.append("")
        for s in wb['sheets']:
            md.append(f"### Onglet : `{s['name']}`")
            md.append(f"- **Dimensions max** : {s['max_row']} lignes × {s['max_col']} colonnes")
            md.append(f"- **Plage réellement utilisée** : Lignes `{s['used_row_range']}` | Colonnes `{s['used_col_range']}` ({s['used_rows_count']} lignes occupées, {s['used_cols_count']} colonnes occupées)")
            md.append(f"- **Cellules fusionnées ({s['merged_cells_count']})** : `{s['merged_cells_sample']}`")
            if s.get('hidden_rows'):
                md.append(f"- **Lignes masquées** : `{s['hidden_rows']}`")
            if s.get('hidden_cols'):
                md.append(f"- **Colonnes masquées** : `{s['hidden_cols']}`")
            
            st = s['stats']
            md.append("- **Typologie des cellules** :")
            md.append(f"  - Numériques : **{st.get('numeric', 0)}**")
            md.append(f"  - Chaînes de caractères : **{st.get('string', 0)}**")
            md.append(f"  - Formules : **{st.get('formula', 0)}**")
            md.append(f"  - Dates : **{st.get('date', 0)}**")
            md.append(f"  - Booléens : **{st.get('boolean', 0)}**")
            md.append(f"  - Vides (dans la zone) : **{st.get('blank', 0)}**")
            
            if s.get('headers'):
                md.append("- **En-têtes détectés** :")
                for h in s['headers']:
                    vals_str = ' | '.join([f"`{v}`" for v in h['values'][:10]])
                    md.append(f"  - *Ligne {h['row']}* : {vals_str}")
                    
            if s.get('formulas_sample'):
                md.append(f"- **Échantillon de formules significatives ({s.get('total_formulas_count', 0)} formules distinctes)** :")
                for fitem in s['formulas_sample'][:10]:
                    md.append(f"  - `{fitem['cell']}` : `{fitem['formula']}` (Valeur en cache : `{fitem['cached']}`)")
            md.append("")
        md.append("---")
        md.append("")

    with open('GALAXAM_EXCEL_SOURCE_MAP.md', 'w', encoding='utf-8') as out:
        out.write('\n'.join(md))
    print("GALAXAM_EXCEL_SOURCE_MAP.md écrit avec succès.")

if __name__ == '__main__':
    main()
