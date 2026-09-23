# Carte des duplications

| Concept | Implémentations observées | Consommateurs | Canonique | Action |
| --- | --- | --- | --- | --- |
| API périodes de paie | `api/pointage/periodePaieService.js`, alias `services/periodePaieService.js` | 7 pages/hooks/composants | `api/pointage/periodePaieService.js` | Alias supprimé, imports migrés |
| Export pointage frontend | `utils/excelExportUtils.js` | aucun | API/export backend `PointageExcelExportService` | Fichier mort supprimé |
| KPI pointage frontend | `utils/pointageSyntheseUtils.js` | aucun | vues calculées backend | Fichier mort supprimé |
| Montants UI | `moneyUtils.js`, wrappers locaux `formatAr` | pages paie | `moneyUtils.js` | Wrappers locaux non publics conservés seulement comme adaptation de vue ; ne pas exporter une seconde API |
| Dates UI | `dateUtils.js`, alias locaux de composants | pages/composants | `dateUtils.js` | Même règle : aucun nouvel utilitaire concurrent |
| URLs historiques | routes canoniques et redirections | bookmarks inconnus | routes pluriel `/pointages`, `/paie/periodes`, `/admin/*` | Conserver jusqu'à preuve de non-usage externe |

Les classes `IrsaCalculator`, `IsiCalculator`, `SocialContributionCalculator`, `OvertimePayCalculator` et `MoneyRoundingPolicy` ne sont pas classées comme doublons : leurs lois, entrées et invariants diffèrent.
