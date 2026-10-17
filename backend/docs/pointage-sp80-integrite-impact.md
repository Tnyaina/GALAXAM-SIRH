# Pointage SP80 — décision et impact

## Décision sur les colonnes

| Colonne | Nature et source | Consommateurs directs | Décision |
| --- | --- | --- | --- |
| `pointage_jour.montant_total_indemnites` | Total opérationnel des lignes accordées de `pointage_indemnite_jour`, recalculé par `PointageService`; des tests STC l'écrivent directement | `PointageService`, `PointageEmployeService`, `PointageAnomalieService`, `StcService`, `StcLegalWorkflowIntegrationTest` | Conserver temporairement. Sa suppression modifierait le STC en cours et son test; vérifier aussi les écarts historiques et les règles d'arrondi avant migration. |
| `pointage_jour.heures_matin`, `heures_am`, `total_heures`, `heures_nuit` | Résultats de saisie et de calcul datés, parfois corrigés; les horaires seuls ne figent pas les paramètres et décisions applicables au jour | Services Pointage, `CongeService`, `DossierSortieService`, `StcService`, tests Paie/STC | Conserver. Recalcul rétroactif et impact STC non démontrés sûrs. |
| `pointage_cumul_periode.heures_hs_30`, `heures_hs_50` | Volumes bruts figés au gel | `PointageSyntheseService`, `PayrollCalculationContextAssembler`, documents et tests | Conserver. Fallback historique de la paie. |
| `minutes_hs30_hebdo`, `minutes_hs50_hebdo` | Résultats hebdomadaires avant solde de période | Synthèse, documents, contrôle des buckets au gel | Conserver. Prouvent la ventilation initiale. |
| `minutes_hs30_finales`, `minutes_hs50_finales`, `minutes_solde_periode` | Décision finale après déficit et ajustement DAF | Synthèse, documents, `PayrollCalculationContextAssembler`, tests Paie | Conserver. Résultat figé distinct des volumes bruts. |

Les autres valeurs candidates (`minutes_retard`, `montant_second_repas`, `heure_entree_matin_effective`) ont des consommateurs Pointage/Paie/STC ou capturent une qualification historique. Aucun retrait n'est engagé. Les 15 tables et les quatre snapshots restent inchangés.

## Invariant livré

Les parcours de production créent les périodes avec des bornes définitives. `PointageService` vérifie déjà la date et la période; `PointageSyntheseService` insère aussi des congés directement. SP80 refuse toute journée hors des bornes inclusives et toute modification effective des bornes après création. Le contrôle SQL protège également les imports, les corrections SQL directes et les autres clients JDBC. Le verrou `FOR SHARE` sur la période protège la lecture des bornes pendant l'insertion; un verrou FK `FOR KEY SHARE` seul ne protège pas les colonnes non clés.

La migration verrouille les deux tables, dénombre les incohérences et échoue en donnant au plus 20 identifiants. Elle ne réaffecte aucune journée. Avant déploiement, exécuter le diagnostic en lecture seule sur la base cible, résoudre les écarts avec décision métier, arrêter les anciennes écritures puis appliquer SP80. Déployer l'application ensuite. Les périodes, snapshots et bulletins historiques ne sont pas réécrits.

## Dépendances et tests

- Module 03 : `EmploymentContextResolver` et les affectations/site déterminent le contexte de chaque journée; aucune API ou formule modifiée.
- Module 04 : `CongeService` annule des liens de congé; `PointageSyntheseService` matérialise les congés directement et doit fournir une période couvrant la date.
- Module 06 : `PayrollCalculationContextAssembler`, `PayrollService`, `DossierSortieService` et `StcService` lisent journées ou snapshots; aucune formule ni référence de bulletin modifiée.
- Modules 01/02 : les acteurs, permissions, scheduler, horaires, sites et paramètres ne changent pas. Les erreurs SQL de contrainte doivent être rendues lisibles par le traitement d'erreurs REST si elles remontent d'un chemin direct.
- Tests à adapter : `CongeTestFixtureHelper` réécrit des bornes existantes; `PointagePhase6IntegrationTest` modifie `date_fin` et crée une campagne septembre qui peut chevaucher la campagne décalée; `DossierSortieIntegrationTest` décale temporairement des bornes. Chacun doit obtenir une période de test isolée sans déplacer une période historique.

La suppression du total d'indemnités et des durées journalières est **différée pour dépendance STC et historique**. Elle exigerait comparaison des résultats avant/après sur indemnités multiples, fractions, arrondis, corrections et bulletins gelés; aucun gain sûr n'est établi aujourd'hui.

## Vérification du chantier

Sur PostgreSQL 15 isolé, les six schémas canoniques s'initialisent. Le script
`pointage_sp80_integrity.sql` valide les bornes, les refus d'insertion et de
déplacement, l'immuabilité et l'écriture directe de congé. Une migration sur
une journée volontairement incohérente échoue avant le DDL; la même migration
s'applique après retrait explicite de cette donnée de la seule base de test.
Deux connexions ont confirmé que la modification de bornes attend la fin de
l'insertion concurrente puis échoue avec SQLSTATE `23514`.

La suite complète sur base Docker isolée reste rouge : 534 tests, 1 échec et
26 erreurs. Les erreurs SP80 proviennent de fixtures modifiant des bornes
existantes (`CongeTestFixtureHelper`, `DossierSortieIntegrationTest`) ; les
erreurs Phase 6 sont dues à une campagne de septembre qui chevauche une autre
campagne laissée par une suite précédente. Un échec sur `ParametreGlobal`
est indépendant de SP80. Les tests ciblés Pointage, Congés et moteurs passent.
Une seconde suite complète n'apporterait pas une preuve de reproductibilité
tant que ces fixtures restent couplées; isoler les périodes par test et relancer
deux fois avant de déclarer ce chantier livrable.
