# Conventions globales du frontend GALAXAM

Ce document définit les règles de présentation et d’ergonomie communes aux modules. Les écrans métier les appliquent au moyen des composants partagés de `src/components/ui/` et des tokens de `src/index.css`. Les besoins métier et l’accessibilité priment sur une uniformité purement décorative.

## Hiérarchie visuelle

1. **Page** — utilise le fond global et un espacement régulier. Le titre, le fil d’Ariane et ses actions forment un en-tête de page sans cadre de carte. Ne pas enfermer tout le contenu dans une seule grande carte.
2. **Section** — une surface `Card` ou un panneau équivalent représente un bloc fonctionnel autonome. Quand titre, filtres/actions et données participent à la même tâche, les réunir dans cette surface.
3. **Contenu interne** — utiliser l’espace, les titres secondaires, les séparateurs (`border-t/b`, `divide-*`) ou un fond subtil. Éviter les cartes encastrées décoratives. Une sous-surface encadrée n’est justifiée que si elle représente une unité fonctionnelle distincte (ex. ligne sélectionnable, échéance, document, zone d’aperçu).

Avant d’ajouter une bordure, une ombre ou un fond de surface, vérifier si le parent fournit déjà cette séparation. Une bordure qui porte une fonction reste pertinente : champs, focus/sélection, badges, fenêtres flottantes, tables autonomes ou aperçu imprimable.

## Règles d’usage

4. **Titres** — conserver une échelle stable : titre de page, titre de section, libellé. Employer la casse normale ; réserver les capitales aux petits libellés lorsque cela aide au balayage. Ne pas répéter le nom de l’onglet dans chaque sous-titre.
5. **Actions** — présenter une action principale par zone de tâche ; utiliser les variantes secondaires pour les autres actions et regrouper les actions rares/administratives dans un menu. Garder les actions près du contenu concerné. Le style du bouton doit refléter sa priorité, pas seulement sa fréquence.
6. **Tables** — une seule surface autour du tableau et de sa pagination. Utiliser `<Table bordered={false}>` si le tableau se trouve dans une surface déjà encadrée ; conserver le cadre par défaut pour une table autonome. Les colonnes séparées et lignes de grille sont réservées aux tableaux qui en ont besoin pour la comparaison. Utiliser `density="compact"` seulement pour les tableaux denses ; cette densité s’applique aux cellules via le contexte de `Table`. Les attributs d’accessibilité de la table vont directement sur `Table` (ex. `aria-label`), et chaque colonne triable doit fournir `onSort` ainsi qu’un libellé accessible si son titre visuel est ambigu. Le tri et la pagination restent pilotés par le backend lorsque les données sont paginées ou métier.
7. **Formulaires** — grouper les champs par sujet dans une même surface ; utiliser des sous-titres et séparateurs pour les groupes secondaires. Les erreurs de validation restent au niveau du champ avec `error` et `FieldError`; les erreurs de soumission restent au niveau du formulaire avec `FormError`. Elles ne doivent pas être répétées dans une bannière de page. Les exemples de saisie passent par les `placeholder`/`hint` des composants d’entrée.
8. **États, responsive et densité** — `Alert` sert aux avertissements et informations de page, aux confirmations et aux erreurs qui empêchent de consulter une section ; `FormError` sert aux erreurs d’action dans un formulaire ; `FieldError` sert aux erreurs d’un champ. N’afficher qu’un message au niveau le plus proche capable d’expliquer et de corriger le problème. Les erreurs globales sont annoncées immédiatement et les autres alertes avec une annonce polie. Sur petit écran, faire passer les colonnes en pile, permettre aux barres d’actions de se replier et éviter le défilement horizontal pour les formulaires/actions. Les tables et vues calendaires peuvent défiler horizontalement si la comparaison des colonnes est essentielle ; fournir alors un conteneur borné et un indice visuel si nécessaire.

## Composants et style

- Utiliser `PageHeader`, `Card` (`CardHeader`, `CardBody`, `CardFooter`), `Table`, `Tabs`, `Button`, `Input`, `Select`, `Alert`, `FormError`, `FieldError`, `EmptyState`, `StatCard`, `Badge` et `Pagination` avant de créer un composant équivalent local.
- Employer les variables sémantiques définies dans `src/index.css` (`--color-surface`, `--color-bg-subtle`, `--color-border-subtle`, `--color-fg-muted`, palettes d’état et de marque). Ne pas introduire de couleurs hexadécimales ou classes de couleurs concurrentes dans les vues applicatives.
- Respecter l’identité carrée GALAXAM avec `rounded-none` pour les surfaces et contrôles. Les indicateurs circulaires (points, avatars ou jauges) peuvent utiliser `rounded-full` lorsqu’ils représentent réellement une forme circulaire.
- Les ombres restent discrètes et sont réservées aux surfaces qui doivent se détacher du fond ou être interactives. Les panneaux ordinaires privilégient la bordure subtile.
- L’état actif, l’erreur, l’avertissement et le succès doivent être lisibles par le texte et la structure, pas par la couleur seule.
- Respecter les données et règles renvoyées par le backend : aucun filtrage, calcul, statut ou seuil métier ne doit être inventé dans la présentation frontend.
- **KPI** — employer `StatCard` avec une palette sémantique cohérente (`brand`, `success`, `warning`, `danger`, `info`, `neutral`). Les cartes utilisées comme filtres doivent exposer clairement l’état actif ; la couleur d’accent ne remplace pas le libellé. Dans une carte parente, utiliser `bordered={false}` plutôt que d’ajouter un second cadre.
- **États de chargement** — utiliser les composants `Skeleton*` partagés et préciser `columns` pour `SkeletonTable` afin que le squelette reflète la structure attendue. Dans une carte parente, passer `bordered={false}` pour éviter une bordure doublée.
- **Couleurs** — toute nouvelle couleur d’état doit utiliser une palette/tokens sémantiques de `src/index.css`; si un niveau de teinte déjà utilisé manque au thème, compléter le thème au lieu de dépendre implicitement d’une classe utilitaire non configurée.
- **Sens des couleurs métier** — garder la marque pour les actions et la sélection ; le neutre pour les données ordinaires ; le vert pour une présence ou un traitement positif ; l’ambre pour un élément à vérifier ; le rouge pour une absence injustifiée, une erreur ou un blocage ; le bleu pour une information non bloquante comme le congé. Le violet et l’orange restent des accents secondaires pour les catégories pointage/majoration, avec un libellé explicite. Ne pas utiliser une couleur d’état comme simple décoration ni colorer les KPI ordinaires chacun différemment.
- **Paie et impression** — distinguer les gains des retenues uniquement là où cette comparaison aide à lire les montants. Le bulletin imprimable utilise les tokens `print-*` stables, afin que le mode clair/sombre de l’application n’altère pas le document.

## Vérification lors d’une évolution UI

- Vérifier la page au minimum en largeur bureau et mobile, les états chargement/vide/erreur et les libellés longs.
- Vérifier qu’une seule bordure de surface entoure chaque zone de données, que les actions restent accessibles au clavier et que les contrôles ont des libellés accessibles.
- Réutiliser les tests existants pertinents ; exécuter le lint ciblé, les tests/build adaptés et `git diff --check` selon l’étendue des changements.

Une exception est acceptable lorsqu’elle améliore la compréhension, la comparaison ou l’accessibilité. Elle doit rester locale et ne pas réintroduire une hiérarchie de cadres purement décorative.
