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
- Les scripts ci-dessous sont conservés pour la mise à niveau des bases existantes. Ne pas les rejouer sans vérifier leur état d'application : tous ne sont pas entièrement idempotents.

## Détail des fusions réalisées

Le script `26-09-25-sp28-pointage-ajustement-periode.sql` ajoute le journal des
ajustements horaires, les minutes finales du snapshot et la précision des
colonnes horaires pour les bases déjà initialisées. Son schéma est également
intégré à `05_schema_pointage.sql` pour les nouvelles installations.

Le script `26-09-25-sp29-paie-durees-minutes.sql` élargit les durées et le taux
horaire du bulletin pour conserver la précision du rapprochement de pointage.
Il est intégré à `06_schema_paie.sql` pour les nouvelles installations.

Le script `26-09-25-sp30-majorations-manquantes.sql` complète, sans remplacer
les taux déjà configurés, les cinq majorations des trois versions légales
présentes dans le schéma canonique. Il sert aux bases existantes dont les
référentiels de majoration n'ont pas été initialisés.

Le script `26-09-25-sp31-bareme-irsa-dedoublonnage.sql` retire uniquement les
doubles tranches IRSA historiques dont la borne basse est identique ou décalée
de 0,01 Ar par rapport à la tranche contiguë canonique. Une variante
incompatible fait échouer l'index unique et demande un arbitrage explicite.

| Script Incrémental | Domaine | Intégré dans |
| :--- | :--- | :--- |
| `26-09-19-sp12-parametrage-exoneration-hs.sql` | Exonération fiscale HS (CGI 01.03.03-9°) & Éligibilité | `01_schema_core_securite.sql`, `02_schema_referentiels_rh.sql` |
| `26-09-19-sp9-conge-cutover-snapshot.sql` | Modes de reprise congés (Cutover, Snapshots) & Source `REPRISE_CUTOVER` | `04_schema_conges.sql` |
| `26-09-21-sp13-certification-irsa.sql` | Millésime IRSA 6 tranches (Jan-Fév 2026), bornes contiguës & traçabilité légale | `02_schema_referentiels_rh.sql`, `06_schema_paie.sql` |
| `26-09-21-sp14-payroll-ownership-hardening.sql` | Gouvernance types mouvements paie & trigger auto-imputation | `06_schema_paie.sql` |
| `26-09-22-sp15-paie-menu-sidebar-contextuel.sql` | Sous-menus canoniques module paie | `01_schema_core_securite.sql` |
| `26-09-22-sp16-pointage-cumul-heures-dimanche-ferie.sql` | Heures dimanche & fériés scellées | `05_schema_pointage.sql` |
| `26-09-22-sp17-pointage-role-validation-menu.sql` | Habilitation menu validation rôle agent pointage | `01_schema_core_securite.sql` |
| `26-09-22-sp17-ref-periode-chomee-runtime-contract.sql` | Alignement contrat runtime périodes chômées | `02_schema_referentiels_rh.sql` |
| `26-09-22-sp18-scheduler-execution-journal.sql` | Table de journalisation des exécutions planifiées | `01_schema_core_securite.sql` |
| `26-09-23-sp19-tconge-technical-validity-label.sql` | Précision borne technique paramètre TCONGE | `01_schema_core_securite.sql` |
| `26-09-23-sp20-conge-pool-consomme-reserve.sql` | Vue consommation vs réservation pool annuel congés | `04_schema_conges.sql` |
| `26-09-23-sp21-paternite-hors-pool-permissions.sql` | Découplage paternité du pool permissions familiales | `04_schema_conges.sql` |
| `26-09-23-sp22-pointage-remove-unused-summaries.sql` | Suppression tables résumés pointage obsolètes | `05_schema_pointage.sql` |
| `26-09-24-sp23-pointage-drop-weekly-cache.sql` | Suppression cache semaine recalculé à la volée | `05_schema_pointage.sql` |
| `26-09-24-sp24-pointage-allowances-generic.sql` | Snapshot générique des indemnités de pointage | `02_schema_referentiels_rh.sql`, `05_schema_pointage.sql` |
| `26-09-24-sp25-pointage-arrival-floor.sql` | Heure entrée effective & statut tolérance | `05_schema_pointage.sql` |
| `26-09-24-sp26-pointage-snapshot-capture-effective-entry.sql` | Traçabilité entrée effective dans snapshot jour | `05_schema_pointage.sql` |
| `26-09-24-sp27-ref-site-plancher-arrivee.sql` | Unification plancher arrivée dans ref_site & fractions congés | `02_schema_referentiels_rh.sql`, `04_schema_conges.sql` |
| `26-09-25-sp28-pointage-ajustement-periode.sql` | Table ajustement DAF & minutes réconciliation | `05_schema_pointage.sql` |
| `26-09-25-sp29-paie-durees-minutes.sql` | Précision minutes et taux horaire bulletin | `06_schema_paie.sql` |
| `26-09-25-sp30-majorations-manquantes.sql` | Référentiel majorations LF2024/2026 | `06_schema_paie.sql` |
| `26-09-25-sp31-bareme-irsa-dedoublonnage.sql` | Index unique et dédoublonnage tranches IRSA | `06_schema_paie.sql` |
| `26-09-28-sp32-pointage-solde-global.sql` | Rapprochement global HS50 finales de période | `05_schema_pointage.sql` |
| `26-09-28-sp33-pointage-palier-snapshot.sql` | Historisation du palier légal HS30 dans le snapshot | `05_schema_pointage.sql` |
| `26-09-28-sp34-identite-filiales.sql` | Adresse du siège, RCS, téléphone et courriel filiales | `02_schema_referentiels_rh.sql` |
| `26-09-28-sp35-paie-acomptes-avances.sql` | Confirmation explicite versements & table imputation bulletin | `06_schema_paie.sql` |
| `26-09-28-sp36-conge-expiration-examen-daf.sql` | Traçabilité des décisions DAF sur les droits candidats à prescription | `04_schema_conges.sql` |
| `26-09-28-sp37-plafond-cumul-conges-optionnel.sql` | Plafond de cumul désactivable sans valeur de repli | `01_schema_core_securite.sql` |
| `26-09-28-sp38-seuil-hebdomadaire-non-debrayable.sql` | Retrait de la bascule du seuil hebdomadaire historique non consommé | `01_schema_core_securite.sql` |
| `26-09-28-sp39-second-repas-actif.sql` | Rétablit la désactivation des tranches de second repas après sp38 déjà appliqué | `01_schema_core_securite.sql` |
| `26-09-28-sp40-second-repas-pointage.sql` | Trace le deuxième repas automatique dans le pointage, sans nouvelle indemnité | `05_schema_pointage.sql` |
| `26-09-29-sp41-pointage-repos.sql` | Ajoute le statut journalier REPOS aux bases existantes | `05_schema_pointage.sql` |
| `26-09-30-sp42-paie-precision-montants.sql` | Élargit les montants et taux horaires de paie pour les valeurs élevées en ariary | `06_schema_paie.sql` |
| `26-10-01-sp44-rubriques-variables-gouvernance.sql` | Ajoute la référence et la date d'effet aux rubriques, corrige le lien de la retenue exceptionnelle après contrôle des mouvements historiques | `06_schema_paie.sql` |

| `26-10-01-sp45-etape-variables-manuelles.sql` | Sépare l’étape des assiettes et conserve l’étape exécutée sur les lignes nouvelles | `06_schema_paie.sql` |

| `26-10-01-sp46-validite-conditions-rubriques.sql` | Ajoute validité, filiation et condition des rubriques manuelles ; interdit les chevauchements | `06_schema_paie.sql` |

| `26-10-01-sp47-methodes-rubriques.sql` | Méthodes manuelles, arrondi, assiette FMFP séparée et précision des paramètres de ligne ; aucun ajout de table | `06_schema_paie.sql` |

### Remarques Opérationnelles
- Pour toute nouvelle base de données : exécuter **uniquement les 6 scripts canoniques** `01` à `06`. Ils créent 81 tables et 9 vues cohérentes.
- Les scripts du dossier `migrations/` sont destinés uniquement à la mise à niveau de bases déjà déployées antérieurement ; vérifier l'état de chaque migration avant de la rejouer.

- `26-10-01-sp48-hs-imposables-par-defaut.sql` : désactive la bascule globale d'exonération HS sur les bases existantes ; valeur par défaut des nouvelles configurations légales à FALSE. Aucun recalcul de bulletin.
