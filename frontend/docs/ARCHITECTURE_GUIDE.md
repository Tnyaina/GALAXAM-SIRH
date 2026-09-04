# Guide d'Architecture Front-End : GALAXAM SIRH

Ce document explique l'architecture en 4 couches (Pages / Hooks / Utilitaires Métier / Composants UI) mise en place pour résoudre le problème des "composants monolithiques" et découpler hermétiquement les algorithmes de calculs légaux du rendu graphique.

---

## 1. Schéma Visuel de l'Architecture (4 Couches)

Voici comment les fichiers communiquent entre eux. Le flux de données est **unidirectionnel** : la logique descend vers l'affichage, tandis que les algorithmes purs sont isolés dans des utilitaires sans état.

```mermaid
graph TD
    %% Couche 0 : Utilitaires & Calculs Purs
    U[Utilitaires Métier Purs <br/> ex: congeBusinessRules.js / pointageCalculations.js / carriereTransitionMatrix.js]:::util

    %% Couche 1 : Conteneur (Chef d'orchestre)
    A[Page / Onglet Conteneur <br/> ex: DemandesValidationTab.jsx / SynthesePointagePage.jsx]:::page
  
    %% Couche 2 : Logique Réactive & État
    B[(Hook Logique & API <br/> ex: useDemandesValidation.js / usePointageSynthese.js)]:::hook
  
    %% Couche 3 : Interface Visuelle (Membres purs)
    C[Filtres <br/> ex: ValidationFilters.jsx]:::ui
    D[Tableau/Grille <br/> ex: ValidationTable.jsx]:::ui
    E[Modales <br/> ex: ValidationModals.jsx]:::ui

    %% Relations
    B -- "Délègue calculs math/légaux" --> U
    A -- "Délègue calculs affichage temps réel" --> U
    A -- "1. Appelle le Hook" --> B
    B -. "2. Retourne (données, états, mutations)" .-> A
  
    A -- "3. Passe les données (Props)" --> C
    A -- "3. Passe les données (Props)" --> D
    A -- "3. Passe les données (Props)" --> E
  
    C -- "4. Déclenche une action (onClick)" --> A
    A -- "5. Exécute la fonction du Hook" --> B

    classDef util fill:#8E44AD,stroke:#5B2C6F,stroke-width:2px,color:#fff,font-weight:bold
    classDef page fill:#2E86C1,stroke:#1B4F72,stroke-width:2px,color:#fff,font-weight:bold
    classDef hook fill:#28B463,stroke:#186A3B,stroke-width:2px,color:#fff,font-weight:bold
    classDef ui fill:#E67E22,stroke:#873600,stroke-width:2px,color:#fff,font-weight:bold
```

---

## 2. Qui fait quoi ? (Les 4 Couches)

### 1. Les Utilitaires Métier Purs (`src/utils/...`)

**C'est le moteur mathématique et réglementaire.** Fonctions pures, déterministes, sans aucun état React ni effet de bord (`useState`, `useEffect` proscrits ici).

- **`congeBusinessRules.js`** : Délais de préavis (RG-CON-06), détection des demandes rétroactives, calcul des dates de fin de congés à durée fixe (maternité 98j/119j, événements familiaux), évaluation de suffisance de solde.
- **`pointageCalculations.js`** : Parsing d'heures sécurisé, calcul d'heures effectives matin/après-midi, pause automatique, retards cumulés, détection d'incohérences chronologiques et attribution des codes statuts.
- **`pointageSyntheseUtils.js`** : Agrégations KPIs de semaine, consolidation mensuelle et statut de transmission DAF.
- **`carriereTransitionMatrix.js`** : Détection du contrat source normalisé, matrice d'éligibilité des mouvements RH, verrouillage automatique des types de contrat et statuts.
- **`employeFormValidation.js`** : Validation par étapes du dossier collaborateur, regex emails, formatage ISO strict.

### 2. Le Hook Logique (`src/hooks/...`)

**C'est le cerveau réactif.** Il ne contient aucun code HTML/JSX.

- Il stocke les états réactifs (`useState`) : page actuelle, termes de recherche, filtres actifs.
- Il gère le cycle de vie asynchrone (`axios`, requêtes annulables avec `AbortController`, debouncing 350ms).
- Il consomme les utilitaires purs pour transformer et consolider les données (`useMemo`).
- **Ce qu'il exporte** : Les données prêtes à l'emploi et les fonctions de mutation pour agir dessus.

### 3. Le Conteneur (`src/components/...` ou `src/pages/...`)

**C'est le chef d'orchestre.**

- Il instancie le Hook pour connecter le cerveau et l'API.
- Il assemble les briques visuelles (les sous-composants).
- Il passe les données prêtes du Hook aux briques visuelles via des `props`.

### 4. Les Sous-Composants UI (`src/components/.../sous-dossier/`)

**Ce sont les membres (les bras, les jambes).** Ils sont purs (Pure Components).

- Ils ne font aucun calcul mathématique complexe ni appel API direct.
- Ils ne contiennent que du rendu visuel (JSX, Tailwind).
- Ils reçoivent les données (ex: `demandes={demandes}`) et les affichent.
- Ils préviennent le chef d'orchestre lors des interactions utilisateur (via `onClick`, `onChange`).

---

## 3. Guide Pratique : "Comment faire si je veux..."

### Cas A : Je veux ajouter un nouveau filtre (ex: filtrer par type de congé)

1. **Modifier le Hook Logique (Le Cerveau)**
   - Ouvrez `useDemandesValidation.js`.
   - Ajoutez l'état : `const [activeType, setActiveType] = useState('ALL');`
   - Modifiez `loadData()` pour envoyer ce paramètre à l'API : `if (activeType !== 'ALL') params.type = activeType;`
   - N'oubliez pas de retourner `activeType` et `setActiveType` à la fin du hook.
2. **Modifier le Conteneur (Le Chef d'Orchestre)**
   - Ouvrez `DemandesValidationTab.jsx`.
   - Récupérez vos nouvelles variables depuis le hook : `const { activeType, setActiveType } = useDemandesValidation();`
   - Passez ces variables au composant visuel des filtres : `<ValidationFilters activeType={activeType} setActiveType={setActiveType} ... />`
3. **Modifier le Composant UI (Le Visuel)**
   - Ouvrez `ValidationFilters.jsx`.
   - Ajoutez `activeType` et `setActiveType` dans les props en haut du fichier.
   - Ajoutez le code HTML/Tailwind pour votre menu déroulant de types, et liez `onChange={(e) => setActiveType(e.target.value)}`.

### Cas B : Je veux rajouter une colonne dans le tableau

1. **Modifier uniquement le Composant UI (Le Visuel)**
   - Ouvrez `ValidationTable.jsx`.
   - Ajoutez un `<Th>Nouvelle Colonne</Th>` dans le Header (`<Thead>`).
   - Ajoutez un `<Td>{d.nouvelleDonnee}</Td>` dans le Body (`<Tbody>`).
   - Si la donnée existait déjà dans ce que l'API renvoie, vous n'avez besoin de toucher ni au Hook, ni au Conteneur.

### Cas C : Workflow Asynchrone & Réactivité Globale (Exemple Synthèse Pointage)

1. **Orchestration dans la Page Conteneur (`SynthesePointagePage.jsx`)**
   - La page consomme `usePointageSynthese` et orchestre le calcul global 1-clic, la transmission et l'arbitrage DAF.
   - Après chaque mutation réussie (transmission, validation ou rejet avec motif), la page réactualise la synthèse via `loadSynthese()` et synchronise le semainier.
2. **Événements Globaux de Notification**
   - Lors d'une transition d'état, la page émet l'événement `window.dispatchEvent(new Event('refresh-notifications'))`.
   - Le menu déroulant des notifications intercepte cet événement et met immédiatement à jour son compteur sans nécessiter de rechargement de page.

---

## 4. Pourquoi cette architecture ?

Avant, lorsqu'un fichier faisait 600 lignes, modifier une simple couleur de bouton nécessitait de scroller au milieu des requêtes API, et ajouter un filtre risquait de casser la pagination.

Avec cette architecture :
- Si l'affichage présente un défaut, la modification est localisée dans le dossier UI (`ValidationTable.jsx`).
- Si les données sont incohérentes, la vérification s'effectue dans le Hook (`useDemandesValidation.js`, `usePointageSynthese.js`).
- Le code est modulaire, testable, et robuste face aux régressions.
