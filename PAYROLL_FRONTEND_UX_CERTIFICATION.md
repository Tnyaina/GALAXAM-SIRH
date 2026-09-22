# Certification UX frontend Paie

## Verdict

`PAYROLL_FRONTEND_PREMIUM_UX_PASS`

## Before / After

Avant, certaines vues exposaient des fallbacks réglementaires et reconstruisaient des sous-totaux en React. Le bulletin regroupait fiscalité et cotisations avec des libellés datés en dur.

Après, le workflow conserve le contexte de période, rend la progression backend, distingue blockers et warnings, et affiche la cascade canonique jusqu'au net à payer. Le détail IRSA est entièrement rendu depuis le DTO d'explicabilité.

## Workflow final et routes

Le workflow final utilise sept pages adressables : préparation, population, variables, calcul, contrôle, validation et sorties. La route `/paie` reste l'entrée unique du module dans la navigation globale.

## Pages, modales et drawers

- Pages dédiées pour chaque tâche longue de campagne.
- Drawer bulletin pour l'inspection contextuelle depuis le contrôle.
- Confirmations courtes pour validation et clôture selon la machine d'état backend.
- Formulaires de variables alimentés par les types configurés retournés par l'API.

## Design system et tokens

La refonte réutilise les surfaces, bordures, couleurs sémantiques, ombres et transitions de `frontend/src/index.css`. Les accents décoratifs en gradient des pages Paie ont été remplacés par l'accent Sauge canonique.

## Backend contract

- Aucun barème, minimum, plafond ou taux fiscal/social n'est codé dans React.
- Aucun net, IRSA ou total de cotisations n'est recalculé côté client.
- `explicationFiscalite.detailsTranches` fournit bornes, montant taxé, taux et impôt produit.
- Les actions et états du workflow proviennent du contexte backend.

## Accessibilité et responsive

Le stepper est navigable au clavier et horizontalement sur écran étroit. Les états disposent de textes et icônes, pas seulement de couleurs. Le détail IRSA utilise `aria-expanded`; les actions icon-only possèdent un nom accessible. Le drawer reste adapté aux écrans étroits via le composant `SlideOver` existant.

## Tests et E2E

- Vitest : PASS — 10 fichiers, 74 tests.
- Build Vite : PASS — 2 133 modules transformés.
- Playwright : PASS — 27 tests. Le scénario DAF vérifie revenu net imposable, base IRSA, ouverture du détail fiscal et IRSA dû; il rend les tranches lorsque le DTO les fournit.
- `git diff --check` : PASS.
- Contrôle des styles ajoutés : aucune couleur hexadécimale, valeur `rgb/rgba`, ombre ou rayon arbitraire introduit dans le frontend Paie modifié.
- Contrôle des règles : aucune constante de tranche, taux, minimum fiscal ou diviseur légal dans les pages et composants Paie.

## Known limitations

Les variations M-1 ne sont affichées que lorsque le cockpit backend les fournit. L'historique des exports n'est pas inventé lorsqu'il n'est pas exposé par l'API.

## Screen matrix

La matrice maintenable est définie dans `PAYROLL_FRONTEND_UX_ARCHITECTURE.md`.
