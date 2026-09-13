* [ ] 

# Propriété des règles métier — 2026-09-13

| Règle               | Source donnée                     | Résolveur                              | Calculateur canonique            | Consommateurs                | Implémentations historiques                                                    | État           |
| -------------------- | ---------------------------------- | --------------------------------------- | -------------------------------- | ---------------------------- | ------------------------------------------------------------------------------- | --------------- |
| Durée journée      | badges/corrections + horaire daté | contexte Pointage                       | `DailyAttendanceCalculator`    | saisie, anomalies, synthèse | conversions DTO décimales                                                      | ACTIF_CANONIQUE |
| Retard/nuit/statut   | contexte journalier                | contexte Pointage                       | `DailyAttendanceCalculator`    | services Pointage            | wrappers historiques                                                            | ACTIF_CANONIQUE |
| HS qualification     | `DailyAttendanceResult`          | contexte hebdomadaire                   | `WeeklyAttendanceCalculator`   | période/snapshot            | aucun chemin moderne identifié                                                 | ACTIF_CANONIQUE |
| HS valorisation      | minutes HS + taux datés           | `RefTauxMajoration`                   | `OvertimePayCalculator`        | Paie                         | ancienne formule retirée du moteur                                             | ACTIF_CANONIQUE |
| Cotisations sociales | taux/configuration légale datée  | `PayrollService`                      | `SocialContributionCalculator` | Paie, STC                    | aucune formule active hors calculateur                                          | ACTIF_CANONIQUE |
| IRSA                 | tranches datées                   | `IrsaBracketResolver`                 | `IrsaCalculator`               | Paie, STC                    | aucun loader concurrent                                                         | ACTIF_CANONIQUE |
| ISI                  | taux daté du contexte             | résolveur Paie/STC (absence bloquante) | `IsiCalculator`                | Paie, STC                    | aucun calcul ISI parallèle actif                                               | ACTIF_CANONIQUE |
| Jours ouvrables      | période + calendrier              | `CalendrierOuvrableEngine`            | calendrier central               | Pointage/snapshot            | aucun fallback métier`22`                                                    | ACTIF_CANONIQUE |
| Arrondis monétaires | règle par nature                  | contexte calcul                         | `MoneyRoundingPolicy`          | moteurs et calculateurs Paie/STC | aucun arrondi monétaire générique concurrent ; arrondis fiscaux propres aux calculateurs légaux, formatage propre aux exports | ACTIF_CANONIQUE |
| Snapshot Pointage    | résultats période                | service snapshot                        | tables versionnées              | Paie/validation              | aucun écrasement silencieux                                                    | ACTIF_CANONIQUE |

Les conversions horaires décimales restantes sont des frontières de présentation
ou de persistance legacy. Elles ne sont pas consommées par les calculateurs.
