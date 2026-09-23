# Architecture cible

## Règle d'emplacement

Le paquet ou dossier d'un domaine est son propriétaire. Un élément transverse ne va dans `common`, `config`, `api` ou `components/ui` que s'il a des consommateurs dans plusieurs domaines et n'embarque pas de règle métier.

```text
backend/src/main/java/mg/galaxam/sirh/
  api/{controller,dto}/<domaine>     frontière HTTP et contrats publics
  model/<domaine>/{service,engine,dto,workflow}
  securite/                          authentification et autorisation
  common/                            erreur et transaction réellement transverses
  config/                            infrastructure Spring

frontend/src/
  pages/<domaine>/                   route, chargement et orchestration
  components/<domaine>/              UI métier réutilisable dans le domaine
  hooks/<domaine>/                   état/composition réutilisable
  api/<domaine>/                     appels HTTP canoniques
  components/ui/                     UI sans règle métier
  utils/                             une utilité canonique par responsabilité
```

## Conventions

- Un contrôleur appelle le service ou workflow propriétaire ; aucun calcul métier n'est dupliqué dans React.
- Un `engine` est conservé lorsqu'il a un invariant autonome et un test utile ; il n'est pas créé pour une simple délégation.
- Les DTO HTTP restent sous `api/dto/<domaine>`. Les objets de calcul internes restent dans le domaine.
- Les tables sont définies dans l'un des six schémas canoniques, par domaine. Une évolution productive est ajoutée sous `backend/conception/bdd/migrations/`, jamais en réécrivant un historique déjà déployé.
- Les tests vivent sous le domaine correspondant ; `unit/architecture` vérifie les frontières et `integration` couvre PostgreSQL/API.

## Repères rapides

| Modification | Emplacement canonique |
| --- | --- |
| Calcul IRSA | `backend/src/main/java/mg/galaxam/sirh/model/paie/engine/IrsaCalculator.java` |
| Workflow paie | `backend/src/main/java/mg/galaxam/sirh/model/paie/workflow/PayrollPeriodWorkflowService.java` |
| Tableau pointage | `frontend/src/pages/pointage/GestionPointagesPage.jsx` puis `components/pointage` |
| Périodes de paie côté React | `frontend/src/api/pointage/periodePaieService.js` |
| Monnaie/date UI | `frontend/src/utils/moneyUtils.js`, `frontend/src/utils/dateUtils.js` |
| Contrat/carrière | `backend/.../model/contrat` et `frontend/src/components/carriere` |
