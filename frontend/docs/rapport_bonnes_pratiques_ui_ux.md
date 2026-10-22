
# Rapport — Bonnes pratiques UI/UX pour une application web fluide

## 1. Objectif

L'objectif d'une bonne UX n'est pas uniquement de rendre une interface jolie. Il s'agit surtout de réduire :

- l'effort demandé à l'utilisateur ;
- son incertitude ;
- les erreurs ;
- les temps d'attente perçus ;
- les actions inutiles ;
- la frustration.

Une application peut avoir un backend relativement lent et malgré tout sembler rapide si l'interface donne immédiatement un retour clair à l'utilisateur.

---

## 2. Principe fondamental : chaque action doit avoir un feedback

Règle générale :

```text
ACTION → RÉACTION → NOUVEL ÉTAT
```

Exemple :

```text
[ Enregistrer ]
       ↓
[ Enregistrement... ]
       ↓
[ Enregistré ]
```

L'utilisateur ne doit jamais se demander :

> « Est-ce que mon clic a fonctionné ? »

---

# 3. Gestion du chargement

## 3.1 Éviter l'écran vide

### À éviter

```text
┌─────────────────────┐
│                     │
│    Chargement...    │
│                     │
└─────────────────────┘
```

### Préférer : Skeleton Loading

```text
Nom             Département       Statut
████████        ███████████        █████
████████        ███████████        █████
████████        ███████████        █████
```

Le skeleton montre immédiatement la structure de la page.

## 3.2 Loading local

Ne bloquez pas toute l'application lorsqu'une seule partie est en chargement.

### Mauvais

```text
Toute la page → Loading
```

### Bon

```text
Navigation       → utilisable
Filtres          → utilisables
Tableau          → actualisation...
```

Le chargement doit être aussi local que possible.

## 3.3 Pour les opérations longues

Utiliser une progression :

```text
Upload du contrat

██████████████░░░░ 82%

8.2 MB / 10 MB
```

---

# 4. Optimistic UI

L'Optimistic UI consiste à modifier immédiatement l'interface et à synchroniser avec le serveur en arrière-plan.

### Classique

```text
Clic
 ↓
Attente API
 ↓
Réponse
 ↓
Modification UI
```

### Fluide

```text
Clic
 ↓
Modification UI immédiate
 ↓
API en arrière-plan
 ↓
Synchronisation
```

Exemple :

```text
Statut

● Actif

⟳ Synchronisation...
```

Puis :

```text
● Actif

Synchronisé
```

À utiliser notamment pour :

- activation/désactivation ;
- favoris ;
- checkbox ;
- validations simples ;
- changements de paramètres.

Attention : l'optimistic UI doit être accompagnée d'une stratégie de rollback si le serveur échoue.

---

# 5. Éviter les reloads inutiles

Éviter :

```text
Modifier
 ↓
reload()
 ↓
toute la page recharge
```

Préférer :

```text
Modifier
 ↓
mettre à jour uniquement l'élément concerné
```

Exemple après suppression :

```text
Jean
Marie
Paul ← supprimer
```

devient directement :

```text
Jean
Marie
```

Puis :

```text
Employé supprimé
```

---

# 6. Boutons et actions

## 6.1 Désactiver pendant une requête

Éviter les doubles clics :

```text
[ Enregistrer ]
```

devient :

```text
[ Enregistrement... ]
```

pendant la requête.

Il faut également empêcher les doublons côté backend.

## 6.2 Nommer clairement les actions

### Mauvais

```text
[ OK ]
```

### Bon

```text
[ Enregistrer l'employé ]
```

Le bouton doit expliquer ce qu'il va faire.

## 6.3 Hiérarchiser les actions

Une page ne doit pas avoir dix boutons primaires.

Exemple :

```text
Jean Dupont

[ Modifier ]

⋮
  Supprimer
  Archiver
  Exporter
```

Les actions importantes doivent être visuellement prioritaires.

---

# 7. Toast et notifications

Pour les petites confirmations :

```text
Employé créé
```

```text
Modification enregistrée
```

```text
Document supprimé
```

Les notifications peuvent disparaître automatiquement.

Éviter une grosse modal pour une action banale.

---

# 8. Undo plutôt que confirmation

Pour les actions réversibles, privilégier :

```text
Employé supprimé.

[ Annuler ]
```

plutôt que :

```text
Êtes-vous sûr ?

[ Annuler ] [ Supprimer ]
```

Cela réduit le nombre de clics.

Pour les actions réellement destructives, utiliser une confirmation explicite.

---

# 9. Confirmation pour les actions dangereuses

Exemple :

```text
Désactiver cet employé ?

Jean Dupont

Il ne pourra plus être affecté
à de nouvelles opérations.

[ Annuler ] [ Désactiver ]
```

Pour une suppression irréversible, une confirmation plus forte peut être nécessaire.

---

# 10. Validation des formulaires

## 10.1 Validation inline

Éviter d'attendre la soumission pour révéler toutes les erreurs.

Exemple :

```text
Email

[ tojo@gmail ]

Format d'email invalide
```

Puis :

```text
[ tojo@gmail.com ]

Email valide
```

## 10.2 Erreur au bon endroit

L'erreur doit être proche du champ concerné.

Éviter :

```text
Une erreur est survenue
```

Préférer :

```text
CIN

[ 101234567890 ]

Ce numéro CIN existe déjà.
```

## 10.3 Message d'erreur utile

Éviter :

```text
Error 400
Bad Request
```

Préférer :

```text
Impossible d'enregistrer l'employé.

Le numéro CIN existe déjà.

[ Voir l'employé existant ]
```

Une bonne erreur doit idéalement expliquer :

1. ce qui s'est passé ;
2. pourquoi ;
3. comment résoudre le problème.

---

# 11. Préserver les données de l'utilisateur

Si une requête échoue, ne pas vider le formulaire.

Exemple :

```text
Impossible de contacter le serveur.

Vos informations ont été conservées.

[ Réessayer ]
```

La perte de données saisies est l'une des expériences les plus frustrantes.

---

# 12. Recherche

## 12.1 Debounce

Éviter une requête API pour chaque caractère.

Mauvais :

```text
J → API
Je → API
Jea → API
Jean → API
```

Préférer :

```text
Jean
 ↓
attendre ~300 ms
 ↓
API
```

## 12.2 Donner un état clair

Pendant la recherche :

```text
Recherche : Jean

Recherche...
```

Puis :

```text
3 résultats
```

Ou :

```text
Aucun employé trouvé pour « Jeanxyz ».

Essayez un autre nom.
```

---

# 13. Empty States

Une liste vide doit expliquer quoi faire.

### Mauvais

```text
Aucune donnée.
```

### Bon

```text
         [Aucun collaborateur]

       Aucun employé

Commencez par ajouter votre
premier collaborateur.

[ + Ajouter un employé ]
```

Un empty state doit être considéré comme une partie normale de l'interface.

---

# 14. Pagination et filtres

## 14.1 Pagination

Éviter une longue liste de numéros.

Préférer :

```text
←  1  2  3  ...  12  13  →
```

Permettre éventuellement :

```text
20 / page
50 / page
100 / page
```

## 14.2 Préserver les filtres

Si l'utilisateur utilise :

```text
Département : Finance
Statut : Actif
Recherche : Jean
```

puis ouvre une fiche, ces filtres doivent être conservés lorsqu'il revient.

## 14.3 État dans l'URL

Lorsque c'est pertinent :

```text
/employes?statut=actif&departement=finance
```

Avantages :

- refresh conservé ;
- navigation arrière conservée ;
- URL partageable ;
- état plus prévisible.

---

# 15. Navigation

## 15.1 Structure prévisible

Exemple SIRH :

```text
Dashboard

GESTION RH
  Employés
  Contrats
  Documents

TEMPS
  Pointage
  Absences
  Congés

PAIE
  Paie
  Bulletins
```

## 15.2 Breadcrumb

Pour les interfaces profondes :

```text
Employés
  >
Jean Dupont
  >
Contrat
```

## 15.3 Onglets

Pour une fiche employé :

```text
[ Informations ]
[ Contrats ]
[ Documents ]
[ Carrière ]
[ Absences ]
[ Historique ]
```

Cela évite les pages extrêmement longues.

---

# 16. Préserver le contexte utilisateur

Un bon système doit préserver autant que possible :

- filtres ;
- recherche ;
- pagination ;
- onglet actif ;
- position dans la liste ;
- données saisies ;
- état des modales lorsque pertinent.

Exemple :

```text
Employés
Page 3
Département = Finance
Recherche = Jean
```

L'utilisateur ouvre Jean puis revient :

```text
Employés
Page 3
Département = Finance
Recherche = Jean
```

Il n'a pas besoin de recommencer.

---

# 17. Transitions et animations

Les animations doivent aider l'utilisateur à comprendre les changements.

Utiliser généralement des transitions courtes pour :

- dropdown ;
- modal ;
- tooltip ;
- apparition/disparition ;
- changement d'état ;
- déplacement d'éléments.

Une plage de départ raisonnable est souvent :

```text
150–300 ms
```

Éviter d'animer absolument tout.

Une animation doit servir l'expérience, pas ralentir l'utilisateur.

---

# 18. Tooltips

Pour les icônes seules :

```text
[Modifier]
```

Au survol :

```text
Modifier l'employé
```

Les tooltips sont particulièrement utiles pour les actions secondaires.

Pour les actions critiques, privilégier un bouton avec texte explicite :

```text
[ Supprimer ]
```

plutôt que :

```text
[ Supprimer ]
```

---

# 19. Upload de documents

Pour un SIRH, privilégier :

- Drag & Drop ;
- sélection classique ;
- aperçu ;
- nom du fichier ;
- taille ;
- progression ;
- succès/erreur ;
- possibilité d'annuler si possible.

Exemple :

```text
┌─────────────────────────────┐
│                             │
│    Déposez votre document   │
│                             │
│  ou                         │
│                             │
│  [ Choisir un fichier ]     │
│                             │
└─────────────────────────────┘
```

Après sélection :

```text
contrat-jean.pdf

████████████████░░ 82%

[ Annuler ]
```

---

# 20. Autosave et brouillons

Pour les formulaires complexes, il peut être utile d'afficher :

```text
Brouillon sauvegardé
```

ou :

```text
Dernière sauvegarde :
il y a 12 secondes
```

Mais il faut clairement différencier :

- brouillon ;
- données enregistrées ;
- validation définitive.

---

# 21. Prévenir la perte de données

Si l'utilisateur a modifié un formulaire puis tente de partir :

```text
Modifications non enregistrées

Vous allez quitter cette page.
Vos modifications seront perdues.

[ Rester ] [ Quitter ]
```

Cela évite une frustration majeure.

---

# 22. Raccourcis clavier

Pour une application professionnelle :

```text
Ctrl / Cmd + K
```

peut ouvrir une recherche globale.

D'autres raccourcis peuvent être ajoutés pour les utilisateurs avancés.

Ils doivent rester cohérents et ne pas gêner les raccourcis natifs du navigateur.

---

# 23. Recherche globale

Pour un gros SIRH :

```text
Ctrl + K

Rechercher...
```

Exemple :

```text
Jean Dupont

Jean Dupont (Employé)
   Employé

Contrat CDI (Document)
   Contrat

Congé du 15 août (Demande)
   Congé
```

Cela réduit fortement le nombre de navigations nécessaires.

---

# 24. Accessibilité

Une interface moderne doit être utilisable par le plus grand nombre.

Principes importants :

- contraste suffisant ;
- focus clavier visible ;
- navigation clavier ;
- labels explicites ;
- messages d'erreur accessibles ;
- `aria-label` pour les icônes seules ;
- ne pas utiliser uniquement la couleur pour transmettre une information.

Mauvais :

```text
[Actif]
```

Bon :

```text
Actif
```

---

# 25. Responsive Design

Ne pas simplement réduire une interface desktop.

### Desktop

```text
Nom | Département | Contrat | Statut | Actions
```

### Mobile

```text
Jean Dupont
Finance

CDI
● Actif

[ Voir ]
```

Le contenu et les actions doivent être adaptés au contexte.

---

# 26. Touch targets

Les boutons et contrôles tactiles doivent être suffisamment grands et espacés.

Éviter les petites zones difficiles à toucher.

Préférer :

```text
[ Modifier ]
```

à une minuscule icône seule lorsqu'une action est importante.

---

# 27. États réseau

L'utilisateur doit toujours savoir dans quel état se trouve l'application :

```text
Synchronisé
```

```text
⟳ Synchronisation...
```

```text
Hors connexion
```

En cas d'erreur :

```text
Impossible de contacter le serveur.

Vos données n'ont pas été perdues.

[ Réessayer ]
```

---

# 28. Règle d'or : réduire l'incertitude

Une grande partie de la frustration vient de questions sans réponse :

> Est-ce que mon clic a fonctionné ?

> Est-ce que mes données sont sauvegardées ?

> Pourquoi ça charge ?

> Est-ce que mon fichier est en train d'être envoyé ?

> Est-ce que ma demande a été validée ?

> Pourquoi le bouton est désactivé ?

Une bonne UX répond à ces questions directement dans l'interface.

---

# 29. Application au SIRH

Pour ton SIRH, les priorités peuvent être organisées ainsi.

## Niveau 1 — indispensable

- Feedback immédiat ;
- états Loading / Success / Error ;
- Skeleton loading ;
- validation inline ;
- messages d'erreur utiles ;
- boutons désactivés pendant les requêtes ;
- Toast ;
- Empty states ;
- pas de reload inutile.

## Niveau 2 — fluidité

- Optimistic UI ;
- Debounce ;
- mise à jour locale des tableaux ;
- Undo ;
- conservation des filtres ;
- conservation des formulaires ;
- navigation préservant l'état ;
- loading local ;
- transitions courtes.

## Niveau 3 — expérience avancée

- Recherche globale ;
- Command Palette ;
- raccourcis clavier ;
- autosave ;
- brouillons ;
- Drag & Drop ;
- upload avec progression ;
- historique ;
- actions réversibles ;
- fonctionnement adapté au mobile.

---

# 30. Checklist UX avant de considérer une page comme terminée

## Interaction

- [ ] Chaque action possède un feedback.
- [ ] Les doubles clics sont gérés.
- [ ] Les boutons indiquent clairement leur action.
- [ ] Les actions principales sont visuellement prioritaires.
- [ ] Les actions dangereuses demandent une confirmation appropriée.

## Chargement

- [ ] Pas d'écran blanc inutile.
- [ ] Skeleton utilisé lorsque pertinent.
- [ ] Loading local plutôt que global.
- [ ] Progression affichée pour les opérations longues.

## Formulaires

- [ ] Validation proche du champ.
- [ ] Messages d'erreur compréhensibles.
- [ ] Données conservées en cas d'erreur.
- [ ] Perte de données prévenue.
- [ ] Brouillon clairement distingué d'une validation définitive.

## Données

- [ ] Pas de reload complet inutile.
- [ ] Tableaux mis à jour localement.
- [ ] Recherche avec debounce.
- [ ] Empty state utile.
- [ ] Pagination claire.
- [ ] Filtres conservés.

## Navigation

- [ ] Navigation prévisible.
- [ ] Retour arrière cohérent.
- [ ] Contexte utilisateur conservé.
- [ ] Breadcrumb lorsque nécessaire.
- [ ] Onglets utilisés pour structurer les fiches complexes.

## Feedback

- [ ] Success visible.
- [ ] Error visible.
- [ ] Loading visible.
- [ ] Synchronisation visible lorsque nécessaire.
- [ ] Toast utilisé pour les petites confirmations.
- [ ] Undo utilisé lorsque l'action est réversible.

## Accessibilité

- [ ] Contraste suffisant.
- [ ] Navigation clavier.
- [ ] Focus visible.
- [ ] Labels explicites.
- [ ] Icônes importantes accompagnées de texte.
- [ ] L'information ne dépend pas uniquement de la couleur.

## Responsive

- [ ] Interface utilisable sur mobile.
- [ ] Tableaux adaptés aux petits écrans.
- [ ] Boutons suffisamment grands.
- [ ] Navigation adaptée au mobile.
