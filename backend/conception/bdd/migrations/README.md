# Registre des Migrations Incrémentales BDD

> **IMPORTANT :**
> L'ensemble des modifications apportées par ces scripts incrémentaux a été **intégralement fusionné et consolidé** dans les **6 scripts canoniques principaux** situés dans `backend/conception/bdd/` :
> 1. `01_schema_core_securite.sql`
> 2. `02_schema_referentiels_rh.sql`
> 3. `03_schema_employes_contrats.sql`
> 4. `04_schema_conges.sql`
> 5. `05_schema_pointage.sql`
> 6. `06_schema_paie.sql`

## Règle de Déploiement / Initialisation

- Pour toute nouvelle installation (développement, recette, staging, production), **exécuter uniquement les 6 scripts canoniques dans l'ordre de 01 à 06**.
- **Aucune exécution des scripts de ce dossier `migrations/` n'est nécessaire** après le passage des 6 scripts principaux.
- Les scripts ci-dessous sont conservés pour l'historique et sont 100% idempotents (clauses `IF NOT EXISTS`, `ON CONFLICT`, et `WHERE NOT EXISTS`).

## Détail des fusions réalisées

| Script Incrémental | Domaine | Intégré dans |
| :--- | :--- | :--- |
| `26-09-19-sp12-parametrage-exoneration-hs.sql` | Exonération fiscale HS (CGI 01.03.03-9°) & Éligibilité | `01_schema_core_securite.sql` (paramètres globaux), `02_schema_referentiels_rh.sql` (catégories & config légale) |
| `26-09-19-sp9-conge-cutover-snapshot.sql` | Modes de reprise congés (Cutover, Snapshots) & Source `REPRISE_CUTOVER` | `04_schema_conges.sql` (`conge_periode_reference` & `conge_taux_applicable`) |
| `26-09-21-sp13-certification-irsa.sql` | Millésime IRSA 6 tranches (Jan-Fév 2026), bornes contiguës & traçabilité légale | `02_schema_referentiels_rh.sql` (`paie_config_legale`), `06_schema_paie.sql` (`bareme_irsa`, `ref_taux_majoration`) |
