# Usage des getters paie historiques

| Élément legacy | Sémantique réelle | Consommateurs modernes | Décision |
|---|---|---|---|
| `BulletinPaie.salaireNetImposable` | `NET_INCOME_BEFORE_INCOME_TAX` (`NETI`) | Aucun calcul fiscal; persistance et compatibilité API seulement | Getter déprécié, migration vers `revenuNetAvantImpot` |
| JSON `salaireNetImposable` | Alias historique de `NETI` | Fallback temporaire dans les trois vues Paie | Préférer `revenuNetImposable`; supprimer lors de la rupture API versionnée |
| SQL `salaire_net_imposable` | Colonne historique `NETI` | `PayrollService` pour compatibilité export/API | Conserver jusqu'à migration de schéma versionnée |

Le moteur ne lit jamais ce getter pour définir le net à payer. `revenuNetImposable`, `assietteIrsa` et `netAPayer` restent trois champs distincts.
