# Convention des features frontend

Chaque domaine est autonome sous `features/<domaine>/`.

- `pages/` : uniquement les composants montés par le routeur.
- `components/` : éléments visuels propres au domaine, classés par rôle (`tabs/`, `modals/`, `views/`, `forms/`, `workflow/`, `feedback/`).
- `hooks/` : état serveur, effets et orchestration React propres au domaine.
- `api/` : frontières HTTP du domaine.
- `utils/` : règles ou présentation propres au domaine, sans appel React ni HTTP.

Les utilitaires réellement transversaux restent dans `src/utils/` : dates, monnaie, navigation et icônes. Un fichier de feature ne les réexporte pas : il les importe directement.

## Convention de nommage

- Une route se termine par `Page`.
- Un onglet se termine par `Tab` ou `TabContent` lorsqu'il est injecté dans une page parente.
- Une modale se termine par `Modal`.
- Une vue de données se termine par `Table`, `Grid`, `Timeline` ou `View`.
- Un hook commence par `use`.
- Une frontière HTTP se termine par `Service`.

## Structure interne obligatoire d'un fichier React

Un composant React ne mélange pas les responsabilités. Son ordre est toujours :

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

Les commentaires de section utilisent exactement les intitulés `// État local`, `// Données dérivées`, `// Effets`, `// Actions` et `// Rendu`. Ils sont omis lorsque la section n'existe pas. Une page complexe peut ainsi être lue de haut en bas sans chercher où se trouve une action ou un chargement.
