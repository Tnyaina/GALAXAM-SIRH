# Architecture Paie

## Frontière Pointage → Paie

Le contrat `PayrollAttendanceInput` est immutable, conserve `snapshotId`, `snapshotVersion`, employé, période et horodatage de gel, et exprime toutes les durées en minutes. `PayrollService` est l'adaptateur de persistance : il charge le cumul gelé et construit ce contrat. `PayrollExecutionEngine` ne connaît ni SQL, ni repository, ni horloge, ni transaction.

```text
snapshot Pointage gelé
  → PayrollAttendanceInput
  → PayrollCalculationInput validé
  → PayrollExecutionEngine
  → PayrollResult
  → contrôle
  → persistance/validation du même résultat
```

Les quantités temporelles et éligibilités appartiennent au Pointage. La valorisation par taux horaire, taux de majoration, cotisations et fiscalité appartient à la Paie. Les exports consomment les résultats persistés et ne sont pas propriétaires d'une formule.

## Pureté et workflow

Le moteur reçoit toutes ses données; il ne sauvegarde rien et ne produit ni statut métier de workflow ni horodatage. Simulation, campagne, savepoints, persistance, obsolescence et validation sont des responsabilités du service applicatif. La validation porte sur le résultat déjà calculé et ne relance pas le moteur.

## Configurations et arrondis

Les coefficients, plafonds, tranches et seuils évolutifs doivent être résolus pour la période calculée. Une absence ou concurrence de configuration est bloquante. Les algorithmes de cotisation et d'impôt restent dans Java. Les montants sont des `BigDecimal`; les politiques d'arrondi doivent être détenues par les calculateurs spécialisés, jamais par le frontend.

## Obsolescence

Le résultat Paie doit conserver l'identifiant et la version du snapshot Pointage ainsi que les versions des configurations déterminantes. Une validation doit refuser un résultat si l'une de ces versions a changé. Le contrat applicatif existe; la persistance complète de ces références dans le bulletin reste requise avant de considérer ce contrôle terminé.
