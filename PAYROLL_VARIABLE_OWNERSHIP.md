# Ownership des variables de paie

## Verdict

`PAYROLL_VARIABLE_OWNERSHIP_PASS`

Les blockers techniques d'ownership sont fermés. Les variables de campagne sont définies par un type configuré en base, rattachées à une période, motivées, protégées par la capacité backend `ADD_VARIABLE` et séparées des éléments automatiques. Les montants d'indemnités repas/déplacement sont lus en base sans fallback monétaire.

La règle de montant de la prime d'assiduité reste `BUSINESS_RULE_UNCERTAIN`. Elle ne bloque pas l'ownership : le type est retiré de la saisie manuelle tant qu'un forfait daté n'est pas défini.

## Décisions appliquées

| Concept | Owner | Décision runtime |
|---|---|---|
| Prime exceptionnelle | DAF / Paie | Variable ponctuelle manuelle, période et motif obligatoires |
| Prime performance | DAF / Paie puis moteur | Le montant attribué est conservé comme base ; le montant payé résulte du prorata moteur |
| Prime assiduité | Système / Pointage | Non saisissable ; éligibilité automatique ; forfait daté encore à définir |
| Note de frais | Futur domaine Frais | Compatibilité historique conservée, création manuelle ordinaire interdite |
| Call personnel | DAF / Paie | Retenue nette manuelle motivée, provenance configurée `VARIABLE_DAF` ; import non implémenté |
| Acompte | Trésorerie | Création, approbation, rattachement période et imputation unique |
| Avance spéciale | Trésorerie | Retenue plafonnée avec montant initial, montant imputé et solde restant persistés |
| Repas / déplacement ordinaire | Pointage | Automatiques depuis configuration et snapshot ; jamais variables manuelles |
| Extra déplacement | DAF / Paie | Exception motivée uniquement |
| Régularisation congé | Congés / Paie | Motif obligatoire devant identifier la correction ou période source |

## Modèle configuré

`ref_type_mouvement_paie` porte la nature, l'owner, l'autorisation de saisie manuelle, les obligations de période et de motif, la provenance par défaut et la rubrique canonique.

`paie_avance_acompte` porte `montant_initial`, `montant_impute`, `solde_restant`, `provenance` et la période. Les inserts legacy sont normalisés par trigger à partir du montant fourni ; aucune valeur monétaire n'est créée par le trigger.

## Garde-fous backend

- Création : type actif, manuel, période et motif selon configuration DB.
- Modification de statut et suppression : contrôle de `ADD_VARIABLE` sur la période.
- Après bulletin `CALCULE`, après `PAIE_VALIDEE` ou après `CLOTUREE` : mutation interdite.
- Un mouvement sans période n'est plus listé ni consommé par toutes les campagnes futures.
- Le sens et la provenance viennent du référentiel, jamais du client.
- Les codes automatiques ou inconnus (`HS`, repas, cotisations, IRSA, échéance prêt, net) sont refusés.
- La fiche Employé reste en lecture seule pour les mouvements de campagne.

## Cascade des owners

| Domaine | Donnée possédée | Consommation Paie |
|---|---|---|
| Contrat / carrière | salaire, dates, catégorie, site | lecture datée |
| Pointage | présence, absence, HS, repas, déplacement | snapshot gelé |
| Congés | jours, déduction, indemnité TCONGE | données validées |
| DAF / Paie | primes et retenues exceptionnelles | mouvement approuvé |
| Trésorerie | acompte, avance, solde | mouvement approuvé puis imputation |
| Prêt | capital et échéancier | prochaine échéance active |
| Social / fiscal | paramètres datés | calculateurs canoniques |
| Moteur Paie | transformations, cascade et net | bulletin et exports |

## Prime performance

Le moteur reçoit séparément le montant attribué (`primePerformanceBase`) et produit le montant payé (`primePerformance`). Le test de certification vérifie qu'une attribution de 100 000 Ar peut produire 50 000 Ar après transformation sans écraser la base attribuée.

## Avance et acompte

L'acompte et l'avance utilisent les états existants `EN_ATTENTE`, `APPROUVE`, `REJETE`, `ANNULE`, `IMPUTE_PAIE`. Une imputation partielle d'avance laisse le mouvement `APPROUVE` et conserve son `solde_restant`. Une imputation complète passe à `IMPUTE_PAIE`. Une réouverture restaure les compteurs du mouvement lié au bulletin annulé.

Le reliquat est identifiable mais sa réaffectation à une période ultérieure demeure une action du domaine Trésorerie ; aucun échéancier de prêt n'est réutilisé.

## Indemnités automatiques

Les montants `MONTANT_REPAS_MIDI`, `MONTANT_2ND_REPAS_1`, `MONTANT_2ND_REPAS_2` et `REDUCTION_DEPLACT_REPAS_MIDI` sont résolus depuis `parametre_global`. Une configuration active absente produit `PAYROLL_ALLOWANCE_CONFIGURATION_MISSING`. Aucun montant de secours n'est codé dans les services Pointage.

## Dette métier explicite

### `BUSINESS_RULE_UNCERTAIN` — forfait d'assiduité

- Question : quel forfait daté doit être versé à un salarié éligible ?
- Comportement actuel : le Pointage détermine l'éligibilité ; la saisie mensuelle arbitraire est interdite.
- Source disponible : seuils d'éligibilité configurés en base.
- Impact : aucune prime d'assiduité n'est créée tant que le forfait n'est pas configuré.
- Décision nécessaire : valeur/règle datée et population applicable.

### Dette non bloquante — lien structuré de régularisation congé

Le modèle n'a pas encore de clé dédiée vers un bulletin ou un événement de correction. Le motif obligatoire porte temporairement la référence métier ; aucun faux lien n'a été créé.

## Tests de preuve

- `PaieAjustementControllerIntegrationTest` : période obligatoire, catalogue contextuel, types automatiques refusés, verrouillage/validation/clôture, workflow d'approbation.
- `PayrollExecutionEngineTest` : distinction montant attribué/payé et imputation partielle d'avance.
- `PointageSyntheseServiceIntegrationTest` : configuration d'indemnité absente explicitement rejetée.
- Suites existantes : snapshots Pointage vers Paie, acompte unique, prêts, réouverture et campagnes E2E.

## Prêt pour le Sprint UX 2

Le modèle d'ownership est stabilisé et backend-driven. Le redesign peut consommer le catalogue contextuel sans recréer de règles dans React.

`READY_FOR_SPRINT_UX_2_PAYROLL_PREMIUM_WORKFLOW_REDESIGN`
