
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
- **Police** : `Inter` (texte courant) + `JetBrains Mono` (données type
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

Toutes les couleurs "en dur" ci-dessous existent déjà dans les composants du
projet. Elles sont aussi disponibles comme tokens Tailwind (`--color-*`) dans
`index.css` — préfère les tokens dans le nouveau code, mais les valeurs hex
restent la source de vérité tant que les composants `ui/` ne sont pas migrés.

### Marque — bleu Galaxam (`brand`)

Utilisé pour : focus des champs, liens, accent principal.

| Token         | Hex         | Usage                                                           |
| ------------- | ----------- | --------------------------------------------------------------- |
| `brand-500` | `#1A82B1` | Référence — focus`Input`, liens ("Mot de passe oublié ?") |
| `brand-600` | `#156d95` | Hover sur`brand-500`                                          |

### Info / secondaire (`info`) — bleu Tailwind standard

Utilisé pour : `Badge` primary, `Alert` info, `Toggle`.

| Token        | Hex         | Usage                             |
| ------------ | ----------- | --------------------------------- |
| `info-100` | `#dbeafe` | Fond badge primary                |
| `info-600` | `#2563EB` | Texte badge primary, toggle actif |

### Neutres — `ink` (texte, bordures, fonds)

| Token       | Hex         | Usage                                                    |
| ----------- | ----------- | -------------------------------------------------------- |
| `ink-800` | `#2d2d2d` | Texte principal, bouton`primary`/`secondary`         |
| `ink-700` | `#3a3a3a` | Hover bouton secondary                                   |
| `ink-400` | `#888888` | Texte secondaire / icônes discrètes                    |
| `ink-300` | `#aaaaaa` | Placeholder, hover de bordure                            |
| `ink-200` | `#d0d0d0` | Bordure par défaut des`Input`                         |
| `ink-150` | `#e5e5e5` | Bordure des`Card` / pages système                     |
| `ink-100` | `#f0f0f0` | Hover fond bouton`ghost`                               |
| `ink-50`  | `#f5f5f5` | Fond désactivé, fond de page (`bg-[#f7f7f7]` proche) |

### Statuts sémantiques

| Statut  | Couleur principale      | Hover/foncé | Où c'est utilisé                                                               |
| ------- | ----------------------- | ------------ | -------------------------------------------------------------------------------- |
| Succès | `#27ae60`             | `#1e8449`  | `Button variant="success"`, `Alert type="success"` (emerald Tailwind)        |
| Danger  | `#c0392b`             | `#a93226`  | `Button variant="danger"`, erreurs de formulaire, `StatusPage tone="danger"` |
| Warning | `#d97706` (amber-600) | `#b45309`  | `Badge variant="warning"`, `StatusPage tone="warning"`                       |

⚠️ **Incohérence connue à ne pas "corriger" sans le signaler** : `Button variant="success"` utilise un vert custom (`#27ae60`) alors que `Alert`/`Badge`
utilisent l'émeraude Tailwind par défaut. C'est déjà comme ça dans le projet —
garder cette distinction plutôt que d'unifier silencieusement.

---

## 4. Typographie

| Élément                      | Classe                                                  | Détail                |
| ------------------------------ | ------------------------------------------------------- | ---------------------- |
| `h1`                         | `text-2xl font-bold tracking-tight`                   | Titres de page         |
| `h2`                         | `text-xl font-bold tracking-tight`                    | Sections               |
| Titre de carte (`CardTitle`) | `text-base font-semibold` (`size` prop pour varier) |                        |
| Label de champ (`Input`)     | `text-xs font-bold uppercase tracking-wider`          | Toujours en majuscules |
| Texte de bouton                | `text-[0.75em] font-semibold uppercase tracking-wide` | Toujours en majuscules |
| Texte courant                  | `text-sm text-[#2d2d2d]`                              |                        |
| Texte secondaire/hint          | `text-xs text-[#888]` ou `text-[#999]`              |                        |

---

## 5. Composants existants (`@/components/ui/`)

Ne jamais recréer un bouton/input/carte "à la main" dans une page — toujours
importer ces composants.

| Composant                                                       | Fichier         | Points clés                                                                                                                                                        |
| --------------------------------------------------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `Button`                                                      | `Button.jsx`  | variants`primary/secondary/outline/ghost/danger/success` · sizes `xs/sm/md/lg` · props `loading`, `iconLeft`, `iconRight` · toujours carré, uppercase |
| `Input` (+ `Textarea`, `Select`, `Checkbox`, `Radio`) | `Input.jsx`   | `border-2`, focus `border-[#1A82B1]`, props `label`, `error`, `hint`, `icon`, `iconRight`                                                             |
| `Card` (+ `CardHeader/Title/Subtitle/Body/Footer/Image`)    | `Card.jsx`    | `shadow` (`none/sm/md/lg`), `bordered`, `hover`, `padding` — pas de radius                                                                               |
| `Alert`                                                       | `Alert.jsx`   | types`success/warning/error/info`, icônes Lucide assorties                                                                                                       |
| `Badge`                                                       | `Badge.jsx`   | variants de couleur +`dot`, `rounded` (à utiliser seulement si besoin explicite d'un badge arrondi)                                                            |
| `Toggle` (+ `Checkbox`)                                     | `Toggle.jsx`  | switch arrondi (exception autorisée), accent`#2563EB`                                                                                                            |
| `Loading`                                                     | `Loading.jsx` | `type` (`spinner/dots`), `color`, `display` (`inline/pill/overlay`)                                                                                       |

## 6. Composants système (`@/components/system/`)

| Composant          | Rôle                                                                                                                                                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `StatusPage.jsx` | Socle commun pour toutes les pages d'erreur/statut. Props :`code`, `icon` (composant Lucide, pas un élément), `title`, `description`, `tone` (`neutral/danger/warning/info`), `actionLabel`, `actionHref`. |

Pages qui l'utilisent aujourd'hui : `NotFound` (404), `Unauthorized` (401),
`ServerError` (500), `Maintenance` (503), `NoConnection` (hors ligne).
**Pour toute nouvelle page d'erreur** → passer par `StatusPage`, ne pas
dupliquer son JSX.

---

## 7. Conventions de mise en page

- Fond de page : `bg-[#f7f7f7]`
- Conteneur centré : `min-h-screen flex items-center justify-center px-4`
- Carte de contenu : `bg-white border border-[#e5e5e5] shadow-sm`, padding
  généreux (`p-8` à `p-10`)
- Largeur de formulaire/carte isolée : `max-w-sm` (compact) à `max-w-md`
  (standard) — ne pas dépasser `max-w-lg` pour un formulaire simple
- Logo : toujours dans la carte en haut, séparé du contenu par
  `border-t border-[#e5e5e5]`

---

## 8. Règles ergonomiques (accessibilité / UX)

- Le focus clavier (`:focus-visible`) ne doit **jamais** être supprimé
- Cible tactile minimale de 44px sur les éléments cliquables
- Toujours un état `loading` explicite sur les actions asynchrones
  (`Button loading` ou `Loading` en overlay)
- Toujours un retour d'erreur visible via `Alert`, jamais de `alert()` JS
  natif ni de texte d'erreur non stylé
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

## 10. Dette technique connue (ne pas s'étonner, ne pas "corriger" tout seul)

- Les composants `ui/` utilisent encore des couleurs Tailwind arbitraires
  (`bg-[#2d2d2d]`) plutôt que les variables CSS de `index.css`
  (`var(--color-ink-800)`). Migration possible composant par composant, à
  faire à la demande.
- Le mode sombre (`.dark`) est prêt côté `index.css` (variables de surface)
  mais **aucun composant `ui/` ne le consomme encore** — ajouter des variantes
  `dark:` ou passer aux variables CSS est nécessaire avant d'activer le toggle
  sombre en production.
