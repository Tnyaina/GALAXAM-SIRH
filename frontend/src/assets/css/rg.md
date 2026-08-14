# rg.md — Guide de référence du Design System Galaxam Logistics

> **But de ce fichier** : donner à une IA (ou à un nouveau dev) tout le contexte
> nécessaire pour créer/modifier une page ou un composant **sans casser
> l'uniformité visuelle** du projet. Toujours lire ce fichier avant de générer
> du CSS/JSX pour Galaxam Logistics.

---

## 1. Fondations techniques

- **Tailwind CSS v4** (syntaxe CSS-first, `@import "tailwindcss";`)
- Fichier de base : `src/index.css` — contient les tokens `@theme`, les variables de surface (`:root` / `.dark`), les règles `@layer base` et l'impression RH.
- **Mode sombre** : piloté par une classe `.dark` sur `<html>`, via `@custom-variant dark (&:where(.dark, .dark *));`.
- **Police** : `system-ui` / sans-serif (texte courant) + mono / monospace (données type numéros de suivi, matricules, codes, dates → classe `.font-data` ou `<code>`).

---

## 2. Règle d'or : PAS D'ARRONDI

L'identité visuelle Galaxam est **anguleuse et technique**. Sauf exception explicite, tout élément est carré.

| Autorisé | Interdit |
| :--- | :--- |
| `rounded-none` (par défaut, souvent implicite / `var(--radius-none)`) | `rounded-lg`, `rounded-xl`, `rounded-2xl` |
| `rounded-full` **uniquement** pour : switch (`Toggle`), pastille de statut (`Badge dot`), spinner, avatar rond | `rounded-md` sur boutons/cards/inputs (sauf token strict `radius-sm` 2px / `radius-md` 4px défini en thème) |
| `border` ou `border-2` net plutôt qu'une ombre floue pour délimiter | coins arrondis "par défaut du navigateur" |

Si tu génères un nouveau composant et que tu hésites → **carré (`rounded-none`)**.

---

## 3. Palette de couleurs (Deep Navy & Slate Blue)

La palette repose sur un équilibre technique entre le **Bleu Nuit / Deep Navy** pour la structure et le **Bleu Acier Moyen (Slate Blue)** pour l'accent et l'interactivité.

### 1. Marque & Accent Principal — Bleu Acier (`brand`)

Utilisé pour les boutons primaires, accents, focus, CTA et liens.

| Token | Hex | Rôle / Usage |
| :--- | :--- | :--- |
| `brand-900` | `#0d1b2a` | Fond ultra-sombre / Ink 950 |
| `brand-800` | `#1b263b` | Fond sidebar / cartes sombres |
| `brand-500` | `#415a77` | **Accent principal** (CTA, focus, état actif, liens) |
| `brand-hover` | `#334862` | Hover sur bouton principal / liens |
| `brand-300` | `#778da9` | Accent secondaire / Badges soft / Muted |
| `brand-50` | `#f4f6f8` | Fond d'accent très clair |

### 2. Neutres — `ink` (Surfaces, texte, bordures)

Famille de neutres "Bleu-Gris" calibrée pour le contraste clair/sombre.

| Token | Hex | Usage |
| :--- | :--- | :--- |
| `ink-950` | `#0d1b2a` | Fond mode sombre profond |
| `ink-900` | `#1b263b` | Sidebar & conteneurs sombres |
| `ink-800` | `#25334a` | Texte principal mode clair / boutons neutres |
| `ink-700` | `#35475e` | Titres / bordures sombres |
| `ink-600` | `#415a77` | Titres secondaires |
| `ink-500` | `#5d7393` | Texte secondaire / icônes |
| `ink-400` | `#778da9` | Muted / placeholders / bordures subtiles |
| `ink-350` | `#9ab0c7` | Hint / Disabled |
| `ink-200` | `#c9d5e3` | **Bordure par défaut** |
| `ink-150` | `#e0e1dd` | **Bordure des cartes / Fond doux (`--color-bg-subtle`)** |
| `ink-100` | `#f0f2f5` | Hover doux / surfaces secondaires |
| `ink-50` | `#f7f8f9` | **Fond de page clair par défaut (`--color-bg`)** |

### 3. Statuts sémantiques & Alertes

| Statut | Jetons / Hex | Usage |
| :--- | :--- | :--- |
| **Succès** | `success-500` (`#16a34a`), `success-400` (`#4ade80`), `success-600` (`#15803d`) | `Alert type="success"`, badges validés, validations |
| **Danger / Erreur** | `danger-500` (`#dc2626`), `danger-400` (`#f87171`), `danger-200` (`#fecaca`) | `Button variant="danger"`, erreurs de formulaire, alertes critiques |
| **Warning (Ambre)** | `warning-500` (`#d97706`), `warning-100` (`#fef3c7`), `warning-600` (`#b45309`) | Badges warning, alertes d'attention, alertes stock/délais |
| **Info (Indigo)** | `info-500` (`#4f46e5`), `info-100` (`#e0e7ff`), `info-600` (`#4338ca`) | Badges informatifs, notifications d'information |

---

## 4. Surfaces sémantiques & Variables CSS

Toujours privilégier les variables sémantiques du thème :

| Variable | Mode Clair (Light) | Mode Sombre (`.dark`) | Description / Rôle |
| :--- | :--- | :--- | :--- |
| `--color-bg` | `#f7f8f9` (`ink-50`) | `#0d1b2a` | Fond d'écran global |
| `--color-bg-subtle` | `#e0e1dd` | `#162335` | Fond de conteneurs discrets |
| `--color-surface` | `#ffffff` | `#1b263b` | Fond des cartes, tables, modales |
| `--color-surface-hover`| `#f0f2f5` | `#24334d` | Hover sur lignes et cartes |
| `--color-fg` | `#0d1b2a` | `#e0e1dd` | Texte principal |
| `--color-fg-muted` | `#415a77` | `#9ab0c7` | Texte secondaire / sous-titres |
| `--color-fg-subtle`| `#778da9` | `#778da9` | Placeholders, légendes, hints |
| `--color-border` | `#c9d5e3` | `#2a3a50` | Bordures principales |
| `--color-border-subtle`| `#e0e1dd` | `#1e2c40` | Séparateurs, bordures douces |
| `--color-accent` | `#415a77` | `#778da9` | Liens, accents, focus actif |
| `--color-sidebar-bg` | `#1b263b` | `#070e17` | Fond de navigation latérale |
| `--color-sidebar-fg` | `#778da9` | `#778da9` | Texte / icônes sidebar |

---

## 5. Typographie

| Élément | Classe / Règle | Détail |
| :--- | :--- | :--- |
| `h1` | `text-2xl font-bold tracking-tight` | Titres de page |
| `h2` | `text-xl font-bold tracking-tight` | Titres de section |
| `h3` | `text-lg font-semibold tracking-tight` | Sous-sections / Titres de bloc |
| `h4` | `text-base font-semibold` | Titres de cartes / modules |
| `small`, `.text-caption` | `text-xs text-[var(--color-fg-muted)]` | Légendes et mentions |
| Données / Chiffres | `.font-data`, `code`, `kbd` | Police mono avec `tabular-nums` |
| Label de champ (`Input`) | `text-xs font-bold uppercase tracking-wider` | Toujours en majuscules |
| Texte de bouton | `text-[0.75em] font-semibold uppercase tracking-wide` | Toujours en majuscules |

---

## 6. Composants existants (`@/components/ui/`)

Ne jamais recréer un bouton, input ou carte "from scratch". Les composants sont organisés par rôle :

| Composant | Sous-dossier | Fichier | Points clés |
| :--- | :--- | :--- | :--- |
| `Button` | `forms/` | [`Button.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/Button.jsx) | Variants : `primary/secondary/outline/ghost/danger/success` · sizes : `xs/sm/md/lg` · angles droits, uppercase. |
| `Input` (+ `Textarea`, `Select`, `Checkbox`, `Radio`) | `forms/` | [`Input.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/Input.jsx) | Focus visible via `var(--color-focus-ring)`. Labels uppercase. |
| `Toggle` | `forms/` | [`Toggle.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/Toggle.jsx) | Switch arrondi (exception de radius autorisée). |
| `DatePicker` | `forms/` | [`DatePicker.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/DatePicker.jsx) | Sélecteur de date personnalisé. |
| `FileUpload` | `forms/` | [`FileUpload.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/FileUpload.jsx) | Zone de dépôt de document avec limitation de taille. |
| `ChipInput` | `forms/` | [`ChipInput.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/ChipInput.jsx) | Saisie de tags / puces avec suppression. |
| `Card` (+ `CardHeader/Title/Body/Footer`) | `data-display/` | [`Card.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/Card.jsx) | Fond `var(--color-surface)`, bordure `var(--color-border-subtle)`. |
| `Badge` | `data-display/` | [`Badge.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/Badge.jsx) | Variants statut / teintes de la palette + option `dot` ronde. |
| `Avatar` | `data-display/` | [`Avatar.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/Avatar.jsx) | Avatar d'utilisateur avec initiales ou image (arrondi autorisé). |
| `Chart` (+ `LineTrendChart`, etc.) | `data-display/` | [`Chart.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/Chart.jsx) | Graphiques de tendances pour les statistiques et tableaux de bord. |
| `StatCard` | `data-display/` | [`StatCard.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/StatCard.jsx) | Affichage de chiffres clés, de variations et d'icônes. |
| `Table` (+ `Thead`, `Th`, `Tbody`, `Tr`, `Td`) | `data-display/` | [`Table.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/Table.jsx) | Tableaux de données formatés, interlignes nets et fond de survol. |
| `AccordionItem` | `data-display/` | [`Accordion.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/Accordion.jsx) | Conteneur d'informations repliable. |
| `EmpyState` | `data-display/` | [`EmpyState.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/EmpyState.jsx) | Placeholder visuel pour les listes ou tables vides. |
| `Alert` | `feedback/` | [`Alert.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Alert.jsx) | Types : `success/warning/error/info`, icônes assorties. |
| `Loading` | `feedback/` | [`Loading.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Loading.jsx) | Spinner ou dots avec gestion overlay / inline. |
| `Skeleton` (+ `SkeletonTable`, etc.) | `feedback/` | [`Skeleton.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Skeleton.jsx) | Squelettes de chargement pour cards, tables et stats. |
| `Toast` (+ hook `useToast`) | `feedback/` | [`Toast.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Toast.jsx) | Système de notifications temporaires. |
| `Stepper` | `navigation/` | [`Stepper.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Stepper.jsx) | Visualisation des étapes d'un processus multi-étapes. |
| `Tabs` | `navigation/` | [`Tabs.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Tabs.jsx) | Onglets de navigation horizontaux. |
| `Breadcrumb` | `navigation/` | [`Breadcrumb.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Breadcrumb.jsx) | Fil d'Ariane pour la navigation contextuelle. |
| `Pagination` | `navigation/` | [`Pagination.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Pagination.jsx) | Contrôles de pagination pour les longs tableaux de données. |
| `ConfirmDialog` | `overlays/` | [`ConfirmDialog.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/overlays/ConfirmDialog.jsx) | Modale de confirmation d'action. |
| `SlideOver` | `overlays/` | [`SlideOver.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/overlays/SlideOver.jsx) | Panneau coulissant latéral (Drawer). |
| `Tooltip` | `overlays/` | [`Tooltip.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/overlays/Tooltip.jsx) | Info-bulle directionnelle (top/bottom/left/right) au survol. |
| `DropdownMenu` | `overlays/` | [`DropdownMenu.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/overlays/DropdownMenu.jsx) | Menu contextuel déroulant. |
| `Modal` | `overlays/` | [`Modal.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/overlays/Modal.jsx) | Boîte de dialogue modale générique et accessible. |

---

## 7. Composants système & Impression RH

- **Pages d'erreur/statut** : Utiliser impérativement `@/components/system/StatusPage.jsx` (`code`, `icon`, `title`, `description`, `tone`, `actionLabel`, `actionHref`).
- **Impression RH (`@media print`)** :
  - Utiliser le portail `.certificat-print-portal` pour les bulletins, attestations et fiches de paie.
  - Masquer l'UI de navigation via la classe `.print:hidden`.

---

## 8. Checklist avant validation

- [ ] Les couleurs proviennent strictement de `index.css` via les tokens ou classes Tailwind (`brand-*`, `ink-*`, etc.).
- [ ] Aucun arrondi injustifié (`rounded-none` par défaut).
- [ ] Réutilisation systématique des composants de `@/components/ui/`.
- [ ] Typographie mono (`.font-data`) appliquée pour les codes logistiques, montants et dates.
- [ ] Focus accessible et gestion du mode sombre via classes Tailwind (`dark:`) ou variables CSS.