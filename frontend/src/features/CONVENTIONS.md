# Convention des features frontend

Chaque domaine est autonome sous `features/<domaine>/`.

- `pages/` : uniquement les composants montés par le routeur.
- `components/` : éléments visuels propres au domaine, classés par rôle (`tabs/`, `modals/`, `views/`, `forms/`, `workflow/`, `feedback/`).
- `hooks/` : état serveur, effets et orchestration React propres au domaine.
- `api/` : frontières HTTP du domaine.
- `utils/` : règles ou présentation propres au domaine, sans appel React ni HTTP.

Les utilitaires réellement transversaux restent dans `src/utils/` : dates, monnaie, navigation et icônes. Un fichier de feature ne les réexporte pas : il les importe directement.

Les règles globales de hiérarchie visuelle, de responsive et d’usage des tokens sont définies dans [`frontend/CONVENTIONS.md`](../../CONVENTIONS.md). Ce document complète ces règles avec les conventions d’organisation des features.

## Convention de nommage

- Une route se termine par `Page`.
- Un onglet se termine par `Tab` ou `TabContent` lorsqu'il est injecté dans une page parente.
- Une modale se termine par `Modal`.
- Une vue de données se termine par `Table`, `Grid`, `Timeline` ou `View`.
- Un hook commence par `use`.
- Une frontière HTTP se termine par `Service`.

## Structure de lecture recommandée pour un fichier React

Un composant React doit séparer clairement rendu, état, chargement et actions. Pour les composants complexes, cet ordre facilite la lecture :

1. imports groupés (React, routeur, UI, feature, partagé) ;
2. imports lazy et constantes statiques ;
3. composant principal ;
4. contexte, routeur et hooks métier ;
5. état local avec `useState` ;
6. permissions et données dérivées avec `useMemo` si nécessaire ;
7. chargements et abonnements avec `useEffect` ;
8. actions utilisateur, nommées `handle…` ;
9. rendu JSX ;
10. petits sous-composants locaux, uniquement s'ils ne sont pas réutilisés.

Ces intitulés de section sont facultatifs : les ajouter uniquement lorsqu’ils clarifient un composant conséquent. Ne pas ajouter de commentaires de structure à un composant simple ni découper artificiellement un fichier pour respecter un ordre rigide.
