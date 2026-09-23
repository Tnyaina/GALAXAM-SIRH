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

## 3. Palette de couleurs (Sauge Apaisant & Gris Neutres)

La palette repose sur un équilibre technique entre le **Vert Sauge Organique (#5C7A52, #2A3626)** pour la marque/structure et les **Gris Sauge désaturés (`ink`)** pour la clarté et l'ergonomie documentaire sans fatigue oculaire.

### 1. Marque & Accent Principal — Sauge (`brand`)

Utilisé pour les boutons primaires, accents, focus, CTA et liens. Volontairement plus saturé/vert que la famille ink (texte/bordures) pour qu'un élément interactif ne se confonde jamais avec du texte neutre.

| Token | Hex | Rôle / Usage |
| :--- | :--- | :--- |
| `brand-950` | `#14150f` | Fond ultra-sombre |
| `brand-900` | `#1c2318` | Conteneurs sombres profonds |
| `brand-800` | `#2a3626` | Fond sidebar / cartes sombres |
| `brand-700` | `#384b32` | Bordures sidebar / accents sombres |
| `brand-600` | `#47603f` | Survol sombre / Accent fort |
| `brand-500` | `#5c7a52` | **Accent principal** (CTA, focus, état actif, liens) |
| `brand-hover` | `#47603f` | Survol bouton principal / liens |
| `brand-400` | `#7a9c68` | Accent secondaire haute visibilité (WCAG AA >= 4.5:1) |
| `brand-300` | `#9fbe8c` | Accent intermédiaire doux |
| `brand-200` | `#c7d9bc` | Bordure d'accent / fond doux |
| `brand-100` | `#edf2e8` | Arrière-plan doux |
| `brand-50` | `#fafaf9` | Fond d'accent très clair |

### 2. Neutres — `ink` (Surfaces, texte, bordures)

Famille de neutres "Gris Sauge" désaturés à dessein, calibrée pour le contraste clair/sombre.

| Token | Hex | Usage |
| :--- | :--- | :--- |
| `ink-950` | `#14150f` | Fond mode sombre profond |
| `ink-900` | `#1c2318` | Conteneurs sombres / Modales |
| `ink-800` | `#262922` | Surfaces sombres / Hover lignes |
| `ink-700` | `#333630` | Bordures sombres |
| `ink-600` | `#454840` | Éléments d'interface discrets |
| `ink-500` | `#5e6258` | Texte secondaire / icônes (Contraste > 7:1) |
| `ink-400` | `#7e8277` | Muted / placeholders / bordures subtiles (WCAG AA >= 4.5:1) |
| `ink-350` | `#9ca096` | Hint / Disabled |
| `ink-200` | `#d7d9d2` | **Bordure par défaut** |
| `ink-150` | `#e6e7e1` | **Bordure des cartes / Fond doux (`--color-bg-subtle`)** |
| `ink-100` | `#f1f1ed` | Hover doux / surfaces secondaires |
| `ink-50` | `#fafaf9` | **Fond de page clair par défaut (`--color-bg`)** |

### 3. Statuts sémantiques, Alertes & Mises en valeur

| Statut / Domaine | Jetons / Hex | Usage |
| :--- | :--- | :--- |
| **Succès (`success`)** | `success-500` (`#16a34a`), `success-400` (`#4ade80`), `success-600` (`#15803d`) | `Alert type="success"`, badges validés, validations |
| **Danger / Erreur (`danger`)** | `danger-500` (`#dc2626`), `danger-400` (`#f87171`), `danger-200` (`#fecaca`), `danger-600` (`#b91c1c`) | `Button variant="danger"`, erreurs de formulaire, alertes critiques |
| **Warning / Vigilance (`warning`)** | `warning-500` (`#d97706`), `warning-400` (`#f59e0b`), `warning-200` (`#fef08a`), `warning-100` (`#fef9c3`), `warning-50` (`#fefce8`), `warning-700` (`#92400e`) | Badges warning, alertes de conformité et vigilance (ambre équilibré) |
| **Info (`info`)** | `info-500` (`#3730a3`), `info-600` (`#312e81`), `info-700` (`#1e1b4b`), `info-100` (`#e0e7ff`), `info-50` (`#f0f3ff`) | Notifications d'information, badges informatifs |
| **Congés & Planification (`sky`)** | `sky-500` (`#0284c7`), `sky-600` (`#0369a1`), `sky-700` (`#075985`), `sky-100` (`#e0f2fe`), `sky-50` (`#f0f9ff`) | Badges congés, part salariale cotisations, planification |
| **Heures de nuit / Majorations (`purple`)** | `purple-500` (`#7e22ce`), `purple-600` (`#6b21a8`), `purple-700` (`#581c87`), `purple-100` (`#f3e8ff`), `purple-50` (`#faf5ff`) | Heures de nuit, primes spécifiques |
| **Horaires décalés & Indemnités (`orange`)** | `orange-500` (`#ea580c`), `orange-600` (`#c2410c`), `orange-700` (`#9a3412`), `orange-100` (`#ffedd5`), `orange-50` (`#fff7ed`) | Plages crépusculaires, indemnités panier/transport spécifiques |

---

## 4. Surfaces sémantiques & Variables CSS

Toujours privilégier les variables sémantiques du thème :

| Variable | Mode Clair (Light) | Mode Sombre (`.dark`) | Description / Rôle |
| :--- | :--- | :--- | :--- |
| `--color-bg` | `#fafaf9` (`ink-50`) | `#14150f` | Fond d'écran global |
| `--color-bg-subtle` | `#f1f1ed` (`ink-100`) | `#1c2318` | Fond de conteneurs discrets / en-têtes |
| `--color-surface` | `#ffffff` | `#262922` | Fond des cartes, tables, modales |
| `--color-surface-hover`| `#eeefe9` | `#333630` | Hover sur lignes et cartes |
| `--color-fg` | `#1c2318` | `#f1f1ed` | Texte principal (Contraste high) |
| `--color-fg-muted` | `#5e6258` | `#c7cbbf` | Texte secondaire / sous-titres (WCAG AA > 7:1) |
| `--color-fg-subtle`| `#7e8277` | `#9ca096` | Placeholders, légendes, hints (WCAG AA >= 4.5:1) |
| `--color-border` | `#d7d9d2` | `#3a3d35` | Bordures principales |
| `--color-border-subtle`| `#e6e7e1` | `#2e312a` | Séparateurs, bordures douces |
| `--color-accent` | `#5c7a52` (`brand-500`) | `#9bc98a` | Liens, accents, focus actif |
| `--color-sidebar-bg` | `#2a3626` | `#14150f` | Fond de navigation latérale |
| `--color-sidebar-fg` | `#d7d9d2` | `#d7d9d2` | Texte / icônes sidebar |

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
| `Button` | `forms/` | [`Button.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/forms/Button.jsx) | Variants : `primary/secondary/outline/ghost/danger/success` · sizes : `xs/sm/md/lg` · angles droits, uppercase, zones tactiles min-h `40px` à `48px`. |
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
| `EmptyState` | `data-display/` | [`EmptyState.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/data-display/EmptyState.jsx) | Placeholder visuel pour les listes ou tables vides. |
| `Alert` | `feedback/` | [`Alert.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Alert.jsx) | Types : `success/warning/error/info`, icônes assorties. |
| `Loading` | `feedback/` | [`Loading.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Loading.jsx) | Spinner ou dots avec gestion overlay / inline. |
| `Skeleton` (+ `SkeletonTable`, etc.) | `feedback/` | [`Skeleton.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Skeleton.jsx) | Squelettes de chargement pour cards, tables et stats. |
| `Toast` (+ hook `useToast`) | `feedback/` | [`Toast.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/feedback/Toast.jsx) | Système de notifications temporaires. |
| `Stepper` | `navigation/` | [`Stepper.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Stepper.jsx) | Visualisation des étapes d'un processus multi-étapes. |
| `Tabs` | `navigation/` | [`Tabs.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Tabs.jsx) | Onglets de navigation horizontaux. |
| `Breadcrumb` | `navigation/` | [`Breadcrumb.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Breadcrumb.jsx) | Fil d'Ariane pour la navigation contextuelle. |
| `Pagination` | `navigation/` | [`Pagination.jsx`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/ui/navigation/Pagination.jsx) | Contrôles de pagination avec cibles tactiles `min-w-[40px] min-h-[40px]`. |
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

## 8. Normes d'Accessibilité WCAG AA (Lighthouse)

Tout nouveau composant ou modification UI **doit strictement respecter** les directives d'accessibilité suivantes :

1. **Contraste des Couleurs (WCAG AA 1.4.3)** :
   - Le texte normal (body, hints, placeholders) doit avoir un ratio de contraste d'au moins **4.5:1** avec son fond.
   - Utiliser `--color-fg` (`#0d1b2a`), `--color-fg-muted` (`#334862`) ou `--color-fg-subtle` (`#4a617c`) en mode clair.
   - Ne jamais utiliser un texte avec une couleur plus claire que `--color-fg-subtle` sur fond blanc/gris clair.

2. **Cibles Tactiles et Espacement (WCAG 2.5.5 / 2.5.8)** :
   - Les éléments interactifs (boutons, icônes cliquables, pagination, sélecteurs) doivent mesurer au moins **44px x 44px** (ou au minimum `min-w-[40px] min-h-[40px]`).
   - Prévoir un espacement suffisant (`gap-2` ou `gap-1.5` minimum) entre deux cibles interactives adjacentes.

3. **Repère Principal & Navigation (`<main id="main-content">`)** :
   - Toute page (Layout authentifié, Login, StatusPage/Erreurs) **doit obligatoirement inclure un conteneur `<main id="main-content" tabIndex="-1">`**.
   - Inclure le lien d'évitement accessible (`skip-link`) "Aller au contenu principal" au sommet du layout.

4. **Formulaires & Attributs HTML** :
   - Dans les fichiers HTML statiques (ex. `index.html`), utiliser l'attribut standard `class="..."` et non `className="..."`.
   - Fournir un `aria-label` explicite pour tous les boutons d'icônes sans texte.

---

## 9. Checklist avant validation

- [ ] Les couleurs proviennent strictly de `index.css` via les tokens ou classes Tailwind (`brand-*`, `ink-*`, etc.).
- [ ] Le ratio de contraste des textes respecte la norme **WCAG AA (>= 4.5:1)** (`--color-fg-subtle` >= `#4a617c`).
- [ ] Les cibles d'interaction tactiles (boutons, pagination, header) mesurent au moins **44px** (ou `min-w-[40px] min-h-[40px]`).
- [ ] La page inclut un repère principal sémantique `<main id="main-content">`.
- [ ] Aucun arrondi injustifié (`rounded-none` par défaut).
- [ ] Réutilisation systématique des composants de `@/components/ui/`.
- [ ] Typographie mono (`.font-data`) appliquée pour les codes logistiques, montants et dates.
- [ ] Focus accessible et gestion du mode sombre via classes Tailwind (`dark:`) ou variables CSS.

---

## 10. Règles d'Ergonomie Visuelle & Sémantique (Best Practices B2B)

Pour maintenir une interface logique, lisible et exempte de fatigue visuelle :

### 1. Distinction stricte : Onboarding / Recommandation vs Alerte de Danger
* **Interdiction absolue** : Ne jamais utiliser le statut `Warning` (fond jaune/ambre ou triangle de danger `AlertTriangle`) pour une recommandation, un guidage d'onboarding ou une "prochaine étape recommandée" (ex. *« Configurer le contrat »*). Cela génère du stress et induit l'utilisateur en erreur sur une prétendue panne.
* **Standard d'Onboarding** : Utiliser la **Carte d'Orientation** :
  * Fond neutre de surface `bg-[var(--color-surface)]` ou doux `bg-[var(--color-bg-subtle)]`.
  * Bordure gauche bleue marque `border-l-4 border-l-[var(--color-brand-500)]`.
  * Icône d'orientation positive (`Compass`, `Sparkles` ou `ArrowRight`).
  * Bouton d'action primaire avec flèche incitative (`iconRight={<ArrowRight />}`).

### 2. Conformité RH & Vigilance feutrée
* Réserver `warning` strictement aux cas de non-conformité réelle (documents obligatoires manquants, anomalies de pointage, contrats expirant sous peu).
* Préférer des encarts de conformité élégants : badge discret avec pastille (`Badge variant="warning" dot`), titre net en majuscules, et puces d'actions interactives (`Upload`, `Plus`) stylisées avec fond doux plutôt que de grosses alertes agressives.

### 3. Densité et Navigation des Tableaux de Données
* **Élimination du défilement horizontal** : Les tableaux doivent tenir sur un écran desktop standard sans forcer un scroll horizontal. Consolider les informations associées au lieu de multiplier les colonnes (ex : fusionner Poste et Structure en une seule colonne `Fonction & Affectation` avec Poste en gras et Structure en sous-titre).
* **Lignes interactives cliquables** : Rendre la ligne entière cliquable (`Tr onClick`) pour ouvrir la fiche détaillée d'un collaborateur, en isolant les boutons d'actions interactifs via `e.stopPropagation()`.
* **Standardisation des boutons d'actions** : Pas de gros boutons texte colorés dans les cellules de table. Utiliser le groupe d'icônes standardisé `[Edit2]` (Modifier) et `[Power]` (Activer / Désactiver) avec infobulles contextuelles explicites.

### 4. Non-redondance dans les Empty States
* Lorsqu'une table ou liste est vide, **masquer le bouton d'action secondaire dans l'en-tête** de section. L'action principale doit être centrée au sein de l'`EmptyState` pour créer un point d'action focal unique et éviter tout sentiment de duplication.