# rg.md — Guide de référence du Design System Galaxam Logistics

> **But de ce fichier** : donner à une IA (ou à un nouveau dev) tout le contexte
> nécessaire pour créer/modifier une page ou un composant **sans casser
> l'uniformité visuelle** du projet. Toujours lire ce fichier avant de générer
> du CSS/JSX pour Galaxam Logistics.

---

## 1. Fondations techniques

- **Tailwind CSS v4** (syntaxe CSS-first, `@import "tailwindcss";`)
- Fichier de base : `src/index.css` — contient les tokens `@theme`, les
  variables de surface (`:root` / `.dark`) et les règles `@layer base`.
- **Mode sombre** : piloté par une classe `.dark` sur `<html>` (pas de
  `prefers-color-scheme`), via `@custom-variant dark (&:where(.dark, .dark *));`
- **Police** : `Inter` / `system-ui` (texte courant) + `JetBrains Mono` (données type
  numéros de suivi, codes, dates → classe `.font-data` ou `<code>`).

---

## 2. Règle d'or : PAS D'ARRONDI

L'identité visuelle Galaxam est **anguleuse**. Sauf exception listée
ci-dessous, tout élément est carré.

| Autorisé                                                                                                                  | Interdit                                        |
| -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| `rounded-none` (par défaut, souvent implicite)                                                                          | `rounded-lg`, `rounded-xl`, `rounded-2xl` |
| `rounded-full` **uniquement** pour : switch (`Toggle`), pastille de statut (`Badge dot`), spinner, avatar rond | `rounded-md` sur boutons/cards/inputs         |
| `border-2` net plutôt qu'une ombre floue pour délimiter                                                                | coins arrondis "par défaut du navigateur"      |

Si tu génères un nouveau composant et que tu hésites → **carré**.

---

## 3. Palette de couleurs

> **Migration & Réharmonisation UI/UX** : La couleur de marque principale est le
> **Terracotta / Brique chaud (`#b85235`)**. Ce choix offre une élégance
> visuelle naturelle en synergie avec le fond de page papier chaud (`#f5f3ee`),
> tout en conservant un contraste ergonomique élevé.
>
> Les surfaces sombres (comme la Sidebar) s'appuient sur un neutre **Ardoise
> fumée (`#171b18`)**, évitant le noir pur pour un rendu doux et professionnel.

Toutes les couleurs de l'application sont représentées par des tokens Tailwind (`--color-*`) dans `index.css`.

### Marque — Terracotta Galaxam (`brand`)

Utilisé pour : focus des champs, liens, accent principal, CTA.

| Token         | Hex         | Usage                                                           |
| ------------- | ----------- | --------------------------------------------------------------- |
| `brand-500`   | `#b85235`   | Référence — focus `Input`, CTA, liens, badge actif              |
| `brand-hover` | `#c95c3e`   | Hover sur bouton `primary`                                      |
| `brand-600`   | `#9e3e26`   | Hover sur `brand-500` (texte / liens)                            |

### Ambre — accent secondaire (`warning`)

Utilisé pour : `Badge` warning, `Alert type="warning"`, `StatusPage tone="warning"`, indicateurs d'attention.

| Token          | Hex         | Usage                     |
| -------------- | ----------- | -------------------------- |
| `warning-100` | `#f5e7d6`   | Fond badge warning         |
| `warning-500` | `#b96a21`   | Référence — texte, icônes |
| `warning-600` | `#9a551a`   | Hover / bordure            |

### Info / secondaire (`info`) — violet-prune

Utilisé pour : `Badge` primary/info, `Alert type="info"`, `Toggle`.

| Token        | Hex         | Usage                             |
| ------------ | ----------- | --------------------------------- |
| `info-100`   | `#e4d8ea`   | Fond badge primary                |
| `info-600`   | `#593d67`   | Texte badge primary, toggle actif |

### Neutres — `ink` (texte, bordures, fonds)

Famille de neutres teinté chaud (dominante beige/ardoise), assortie au fond papier.

| Token       | Hex         | Usage                                                              |
| ----------- | ----------- | ------------------------------------------------------------------ |
| `ink-900`   | `#171b18`   | Fond mode sombre / fond Sidebar très profond                       |
| `ink-800`   | `#212622`   | Texte principal, bouton `primary`/`secondary`, fond Sidebar        |
| `ink-700`   | `#333a35`   | Hover bouton secondary, bordures de surfaces sombres               |
| `ink-500`   | `#66726a`   | Texte secondaire / icônes discrètes                                |
| `ink-350`   | `#9faaa2`   | Placeholder, hint `Input`                                          |
| `ink-200`   | `#e0dcd3`   | Bordure par défaut des `Input`                                     |
| `ink-150`   | `#eae6dc`   | Bordure des `Card` / pages système                                 |
| `ink-100`   | `#f2efe6`   | Hover fond bouton `ghost`                                          |
| `ink-50`    | `#f5f3ee`   | **Fond de page par défaut** (papier chaud chaleureux)              |

### Statuts sémantiques

| Statut  | Couleur principale | Hover/foncé | Où c'est utilisé                                                                    |
| ------- | ------------------- | ------------ | -------------------------------------------------------------------------------------- |
| Succès | `#3e7a5c`          | `#2f5f46`    | `Button variant="success"`, `Alert type="success"` — vert désormais unifié partout    |
| Danger  | `#b4483b`          | `#953a2f`    | `Button variant="danger"`, erreurs de formulaire, `StatusPage tone="danger"`          |
| Warning | `#b96a21`          | `#9a551a`    | `Badge variant="warning"`, `StatusPage tone="warning"` (= ambre ci-dessus)             |

---

## 4. Typographie

| Élément                      | Classe                                                  | Détail                |
| ------------------------------ | ------------------------------------------------------- | ---------------------- |
| `h1`                         | `text-2xl font-bold tracking-tight`                   | Titres de page         |
| `h2`                         | `text-xl font-bold tracking-tight`                    | Sections               |
| Titre de carte (`CardTitle`) | `text-base font-semibold` (`size` prop pour varier) |                        |
| Label de champ (`Input`)     | `text-xs font-bold uppercase tracking-wider`          | Toujours en majuscules |
| Texte de bouton                | `text-[0.75em] font-semibold uppercase tracking-wide` | Toujours en majuscules |
| Texte courant                  | `text-sm text-[var(--color-fg)]`                        |                        |
| Texte secondaire/hint          | `text-xs text-[var(--color-fg-muted)]`                  |                        |

---

## 5. Composants existants (`@/components/ui/`)

Ne jamais recréer un bouton/input/carte "à la main" dans une page — toujours
importer ces composants.

| Composant                                                       | Fichier         | Points clés                                                                                                                                                        |
| --------------------------------------------------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Button`                                                      | `Button.jsx`    | variants `primary/secondary/outline/ghost/danger/success` · sizes `xs/sm/md/lg` · props `loading`, `iconLeft`, `iconRight` · toujours carré, uppercase             |
| `Input` (+ `Textarea`, `Select`, `Checkbox`, `Radio`)           | `Input.jsx`     | `border-2`, focus `border-[var(--color-brand-500)]`, props `label`, `error`, `hint`, `icon`, `iconRight`                                                            |
| `Card` (+ `CardHeader/Title/Subtitle/Body/Footer/Image`)      | `Card.jsx`      | `shadow` (`none/sm/md/lg`), `bordered`, `hover`, `padding` — pas de radius                                                                                         |
| `Alert`                                                       | `Alert.jsx`     | types `success/warning/error/info`, icônes Lucide assorties                                                                                                       |
| `Badge`                                                       | `Badge.jsx`     | variants de couleur + `dot`, `rounded` (à utiliser seulement si besoin explicite d'un badge arrondi)                                                               |
| `Toggle` (+ `Checkbox`)                                       | `Toggle.jsx`    | switch arrondi (exception autorisée), accent `var(--color-info-600)`                                                                                               |
| `Loading`                                                     | `Loading.jsx`   | `type` (`spinner/dots`), `color`, `display` (`inline/pill/overlay`)                                                                                                 |

---

## 6. Composants système (`@/components/system/`)

| Composant          | Rôle                                                                                                                                                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `StatusPage.jsx`   | Socle commun pour toutes les pages d'erreur/statut. Props : `code`, `icon` (composant Lucide, pas un élément), `title`, `description`, `tone` (`neutral/danger/warning/info`), `actionLabel`, `actionHref`. |

Pages qui l'utilisent aujourd'hui : `NotFound` (404), `Unauthorized` (401),
`ServerError` (500), `Maintenance` (503), `NoConnection` (hors ligne).
**Pour toute nouvelle page d'erreur** → passer par `StatusPage`, ne pas
dupliquer son JSX.

---

## 7. Conventions de mise en page

- Fond de page : `bg-[var(--color-bg)]` (`#f5f3ee`)
- Conteneur centré : `min-h-screen flex items-center justify-center px-4`
- Carte de contenu : `bg-white border border-[var(--color-border-subtle)] shadow-sm`, padding généreux (`p-8` à `p-10`)
- Largeur de formulaire/carte isolée : `max-w-sm` (compact) à `max-w-md` (standard) — ne pas dépasser `max-w-lg` pour un formulaire simple
- Logo : toujours dans la carte en haut, séparé du contenu par `border-t border-[var(--color-border-subtle)]`

---

## 8. Règles ergonomiques (accessibilité / UX)

- Le focus clavier (`:focus-visible`) ne doit **jamais** être supprimé
- Cible tactile minimale de 44px sur les éléments cliquables
- Toujours un état `loading` explicite sur les actions asynchrones (`Button loading` ou `Loading` en overlay)
- Toujours un retour d'erreur visible via `Alert`, jamais de `alert()` JS natif ni de texte d'erreur non stylé
- Respecter `prefers-reduced-motion` (déjà géré dans `index.css`)

---

## 9. Checklist avant de créer/modifier une page

- [ ] Les couleurs viennent de la palette (section 3), pas de couleur inventée
- [ ] Pas d'arrondi sauf exception listée (section 2)
- [ ] Réutilisation des composants `ui/` existants plutôt qu'un HTML brut
- [ ] Boutons/labels en majuscules (cohérence avec le reste de l'app)
- [ ] Focus visible + labels accessibles sur tout élément interactif
- [ ] Si c'est une page d'erreur/statut → passer par `StatusPage`

---

## 10. Conventions de maintenance du Design System

- Utiliser les variables CSS sémantiques (`var(--color-brand-500)`, `var(--color-bg)`, `var(--color-sidebar-bg)`) ou les utilitaires Tailwind associés plutôt que des valeurs hexadécimales codées en dur dans les composants.
- Pour ajouter un nouveau rôle de surface (ex: fond de modal, carte spéciale), le déclarer d'abord dans `:root` et `.dark` de `index.css`.