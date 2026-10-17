# Registre des Migrations Incrémentales BDD

`26-10-12-sp65-retire-seuil-heures-semaine-compensation.sql` clôt et
désactive l'ancien seuil hebdomadaire, remplacé par
`paie_config_legale.duree_hebdo_legale`. La ligne reste conservée pour l'audit.

`26-10-12-sp66-centralise-exoneration-hs-et-conges.sql` clôt les paramètres
globaux historiques d'exonération HS, désormais pilotés uniquement par
`paie_config_legale`, et retire le diviseur de valorisation des congés inutilisé.
La valeur TCONGE reste la référence d'indemnité ; la retenue de base reste au
1/30e du salaire.

`26-10-16-sp74-affectation-integrity.sql` vérifie d'abord les incohérences entre
salarié et contrat, puis entre service et département. La migration échoue avec
les identifiants d'affectation concernés sans modifier les données, avant
d'ajouter les clés uniques et étrangères composées. Le même DDL est intégré aux
schémas canoniques 02 et 03 pour les nouvelles installations.

`26-10-16-sp76-parametre-global-validite-temporelle.sql` diagnostique les dates
inversées et les périodes qui se chevauchent pour un même code avant d'ajouter
le contrôle de dates et l'exclusion temporelle, sans corriger de données. Le DDL
est également intégré à `01_schema_core_securite.sql` pour les nouvelles
installations. Sur une base existante, appliquer uniquement après vérification
de l'état de la migration et des diagnostics ; le rôle doit pouvoir modifier
`parametre_global` et, si nécessaire, installer `btree_gist`.

`26-10-16-sp77-scheduler-ownership.sql` ajoute un jeton de possession et une
échéance de bail aux exécutions RUNNING, en attribuant un bail de 30 minutes aux
lignes historiques. La migration ajoute aussi une unicité salarié/année aux
mouvements de bonification après diagnostic des doublons. Arrêter toutes les
anciennes instances de l'application avant migration puis déployer le nouveau
code : l'ancien code ne vérifie pas le jeton et pourrait contourner la clôture
par possession. Toute bonification en doublon bloque la migration avec les
salariés et années concernés ; ne pas supprimer ou fusionner ces mouvements sans
arbitrage métier.
La migration a été vérifiée sous PostgreSQL 15 ; `gen_random_uuid()` est fourni
par PostgreSQL 13 et versions ultérieures. Le rôle d'application de la migration
doit posséder les deux tables concernées, ou disposer des droits équivalents
pour `ALTER TABLE` et `CREATE INDEX`.

`26-10-16-sp75-stc-reglement-securite.sql` renforce le journal des règlements
STC : montant nul autorisé, annulation tracée, clé d'idempotence et unicité des
références actives par STC. Une même clé d'idempotence ne doit pas être réutilisée
pour une requête différente. Vérifier les définitions déjà présentes avant
application, car le script remplace le contrôle de montant et ajoute des index.

`26-10-16-sp78-dossier-sortie-cloture.sql` ajoute le motif de clôture et les
champs de traçabilité d'une éventuelle dérogation de règlement. Ces colonnes
doivent être disponibles avant le déploiement du code qui les lit/écrit. SP78
est actuellement un script non commité dans la branche de travail ; son statut
de livraison doit être confirmé avant de le traiter comme migration publiée.

Diagnostic préalable des éventuels doublons bloquants :

```sql
SELECT id_employe,
       EXTRACT(YEAR FROM date_mouvement)::INTEGER AS annee,
       array_agg(id_mouvement ORDER BY id_mouvement) AS mouvements,
       COUNT(*) AS nombre
FROM mouvement_solde_conge
WHERE type_mouvement = 'BONIFICATION'
GROUP BY id_employe, EXTRACT(YEAR FROM date_mouvement)
HAVING COUNT(*) > 1
ORDER BY id_employe, annee;
```

`26-10-11-sp64-prestataires-menu.sql` ajoute « Prestataires indépendants »
dans le menu Paie pour les rôles qui disposent de la lecture Paie. Appliquer
après SP63 sur une base existante ; le schéma canonique contient ce menu.

`26-10-08-sp62-isi-2026.sql` désactive le taux et la rubrique ISI, remet les
types d'intervenants en cohérence avec la loi de finances 2026 et ajoute le
type `CONSULTANT_SALARIE`. Les anciennes lignes de paie restent intactes.

`26-10-08-sp63-prestataires-independants.sql` crée les dossiers prestataires,
leurs contrats et leurs factures, sans retenue ISI ni lien obligatoire avec un
employé ou une période de paie.

`26-10-08-sp60-stc-motifs-rupture.sql` ajoute les motifs détaillés du STC
pour les bases existantes. Le référentiel canonique `02_schema_referentiels_rh.sql`
les contient pour les nouvelles installations.

`26-10-08-sp61-stc-reglement.sql` ajoute la preuve de paiement du STC,
distincte de la validation du calcul. Les colonnes figurent dans le schéma
canonique `06_schema_paie.sql`.

`26-10-13-sp69-stc-snapshot-source.sql` fige le contrat et l'origine du pointage
sur le STC validé. À appliquer avant de valider un STC avec cette version.

`26-10-13-sp70-stc-rapprochement-bulletin.sql` conserve le bulletin mensuel final,
son net, le paiement réel attesté et le solde inclus dans le STC. Appliquer après SP69.

`26-10-03-sp55-conge-decompte-feries-date.sql` fixe la règle Galaxam actuelle
`INCLUS` dans un paramètre obligatoire et versionnable par date d'effet.
Une version `EXCLUS` future changera le décompte à partir de chaque jour concerné,
sans modifier les demandes déjà validées. Exécuter uniquement sur
`sirh_galaxam_test` après contrôle de `current_database()`.

`26-10-04-sp56-conge-decompte-feries-desactivable.sql` rend la règle
`CONGE_DECOMPTE_FERIES` désactivable par version future. Une version inactive
bloque les calculs concernés avec `CONFIGURATION_DISABLED` ; aucune ancienne
valeur n'est reprise implicitement. Exécuter uniquement sur
`sirh_galaxam_test` après contrôle de `current_database()`.

`26-10-03-sp54-menu-controle-droits.sql` renomme le sous-menu existant
`CONGES_PERIODES` en « Contrôle des droits ». Aucun objet métier ni droit
d'accès n'est créé. Vérifier `current_database()` avant exécution ; le script
a été appliqué uniquement à `sirh_galaxam_test` pendant ce chantier.

Le script `26-10-03-sp53-conge-valorisation-mensuelle-correction.sql` ajoute
l'historique des rectifications DAF de valorisation mensuelle. Vérifier
`current_database()` avant toute exécution ; les essais et migrations de ce
chantier sont réservés à `sirh_galaxam_test`. Son modèle est intégré aux
schémas canoniques 04 et 06 (clé étrangère vers le journal après sa création).

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
| `26-10-01-sp48-hs-imposables-par-defaut.sql` | Exonération HS inactive par défaut | `01_schema_core_securite.sql`, `02_schema_referentiels_rh.sql` |
| `26-10-02-sp49-configuration-paie-menu.sql` | Sous-menu de configuration paie | `01_schema_core_securite.sql` |
| `26-10-02-sp49-limites-legales-retenues.sql` | Plafond avance employeur légal | `02_schema_referentiels_rh.sql` |
| `26-10-02-sp50-diviseur-conge-obligatoire.sql` | Diviseur jours calendaires congé obligatoire | `01_schema_core_securite.sql` |
| `26-10-02-sp51-alertes-contrat-essai-echus.sql` | Types d'alertes contrats et essais échus non régularisés | `03_schema_employes_contrats.sql` |
| `26-10-03-sp52-transitions-temporelles-et-menu-conges.sql` | Exclusions des chevauchements légaux, tarifs de site et références congés ; navigation des cycles | `01_schema_core_securite.sql`, `02_schema_referentiels_rh.sql`, `04_schema_conges.sql`, `05_schema_pointage.sql` |
| `26-10-03-sp53-conge-valorisation-mensuelle-correction.sql` | Historique des rectifications DAF valorisation mensuelle | `04_schema_conges.sql`, `06_schema_paie.sql` |
| `26-10-03-sp54-menu-controle-droits.sql` | Renommage sous-menu Contrôle des droits | `01_schema_core_securite.sql` |
| `26-10-03-sp55-conge-decompte-feries-date.sql` | Règle datée `INCLUS`/`EXCLUS` des jours fériés débitant le solde acquis | `01_schema_core_securite.sql` |
| `26-10-04-sp55-pointage-periodes-sans-chevauchement.sql` | Guard anti-chevauchement des campagnes de paie | `05_schema_pointage.sql` |
| `26-10-04-sp56-conge-decompte-feries-desactivable.sql` | Désactivation explicite de la règle fériés par version future | `01_schema_core_securite.sql` |
| `26-10-06-sp57-agent-pointage-acces-fiche-employe.sql` | Habilitation temporaire agent pointage fiche employé | `01_schema_core_securite.sql` |
| `26-10-07-sp58-agent-pointage-sans-fiche-employe.sql` | Rétablissement périmètre agent pointage sans fiche employé | `01_schema_core_securite.sql` |
| `26-10-08-sp59-security-user-read-indexes.sql` | Index des recherches case-insensitive de comptes et des refresh tokens | `01_schema_core_securite.sql` |
| `26-10-12-sp67-activation-par-regle-metier.sql` | Rend les paramètres membres des règles groupées basculables par action métier atomique | `01_schema_core_securite.sql` |
| `26-10-16-sp74-affectation-integrity.sql` | Cohérence contrat/salarié et service/département par clés composées | `02_schema_referentiels_rh.sql`, `03_schema_employes_contrats.sql` |
| `26-10-16-sp75-stc-reglement-securite.sql` | Annulation tracée, montant >= 0 et unicité des références de règlements STC actifs | `06_schema_paie.sql` |
| `26-10-16-sp76-parametre-global-validite-temporelle.sql` | Dates valides et exclusion des chevauchements par code paramètre | `01_schema_core_securite.sql` |
| `26-10-16-sp77-scheduler-ownership.sql` | Possession des tentatives scheduler et unicité des bonifications annuelles | `01_schema_core_securite.sql`, `04_schema_conges.sql` |
| `26-10-16-sp78-dossier-sortie-cloture.sql` | Motif de clôture, dérogation et justification de dérogation pour dossier de sortie | `06_schema_paie.sql` |
| `26-10-16-sp79-stc-reglement-idempotence.sql` | Clé d'idempotence technique et index d'unicité partiel pour requêtes répétées de règlement STC | `06_schema_paie.sql` |
| `26-10-16-sp80-pointage-jour-periode-integrite.sql` | Contrainte et index de cohérence entre date de pointage et période de rattachement | `05_schema_pointage.sql` |
| `26-10-16-sp81-derogation-decision-gouvernance.sql` | Autorité, référence de protocole et date d'acte pour clôture par dérogation avec solde restant | `06_schema_paie.sql` |
| `26-10-17-sp82-menu-dossiers-sortie.sql` | Entrée de menu dédiée aux dossiers de sortie pour les rôles ayant `PAIE_READ` | `01_schema_core_securite.sql` |

### Remarques Opérationnelles
- Pour toute nouvelle base de données : exécuter **uniquement les 6 scripts canoniques** `01` à `06`. Ils créent 91 tables et 9 vues cohérentes.
- Les scripts du dossier `migrations/` sont destinés uniquement à la mise à niveau de bases déjà déployées antérieurement ; vérifier l'état de chaque migration avant de la rejouer.
