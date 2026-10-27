# GALAXAM SIRH — UI/UX V3

## Frontend Resilience & React Performance

### Objectif

Le runtime React gère le renouvellement des accès, certaines erreurs HTTP et la détection de disponibilité du backend. Ces mécanismes améliorent la reprise des requêtes, mais ne fournissent ni mode hors ligne avec file d'attente, ni reprise automatique des écritures.

---

## 1. Frontend Resilience

### Renouvellement et reprise d'authentification

Le client conserve l'access token en mémoire. `AuthContext` programme un renouvellement silencieux environ 90 secondes avant l'expiration et retente au retour sur un onglet visible si le jeton est proche de l'expiration. L'intercepteur de requête vérifie aussi si le jeton expire dans moins de 10 secondes et lance un refresh proactif.

`refreshTokenOnce` partage une promesse de renouvellement entre les appels concurrents de la même instance JavaScript. Lorsque l'API Web Locks est disponible, il utilise aussi le verrou navigateur `galaxam-auth-refresh` ; sans cette API, aucun verrou inter-onglets n'est assuré. Le refresh appelle `/auth/refresh` avec les cookies. En cas d'échec de `refreshTokenOnce`, le client efface son access token et émet `auth-expired`. Le renouvellement planifié directement par `AuthContext` efface l'utilisateur et le jeton en cas de réponse 401 ou 403 ; une erreur réseau ou serveur différente ne déclenche pas ce nettoyage immédiat.

Si une requête API protégée reçoit un HTTP 401 alors qu'un access token est présent en mémoire, l'intercepteur tente un refresh et rejoue cette requête au plus une fois. Les routes de connexion, refresh et déconnexion ne passent pas par cette reprise. Si aucun access token n'est présent, le client signale l'expiration de session et ne lance pas un refresh depuis cet intercepteur.

### Nouvelle tentative réseau

Les nouvelles tentatives réseau automatiques sont limitées à `GET`, `HEAD` et `OPTIONS`, au maximum deux fois, pour les erreurs réseau transitoires (absence de réponse, `ERR_NETWORK`), timeout et statuts 502, 503 ou 504. Les annulations explicites ne sont pas retentées. Le délai augmente avec un aléa. Les autres méthodes ne sont pas automatiquement rejouées par cette politique afin d'éviter de répéter des écritures.

---

## 2. Problèmes React fréquents

### Mutation directe

Mauvais :

```js
employees.push(employee);
setEmployees(employees);
```

Bon :

```js
setEmployees(prev => [...prev, employee]);
```

### useEffect

Éviter les dépendances qui provoquent des boucles :

```jsx
useEffect(() => {
  loadEmployees();
}, [employees]);
```

si `loadEmployees()` modifie `employees`.

---

## 3. Server State

Séparer :

**UI state** : modal, onglet, sélection, formulaire temporaire.

**Server state** : employés, contrats, congés, permissions, dashboard.

Le server state bénéficie de :

- cache ;
- stale time ;
- retry ;
- invalidation ;
- synchronisation ;
- gestion loading/error.

TanStack Query est une option adaptée.

### Invalidation

```text
POST /employees
      ↓
success
      ↓
invalidate employees
      ↓
refetch
      ↓
UI synchronisée
```

Sinon le backend peut contenir une donnée que le frontend n'affiche pas encore.

---

## 4. Race Conditions

Exemple recherche :

```text
"Je" → requête A
"Jean" → requête B
"Jean D" → requête C
```

Si A répond après C, A peut écraser les résultats.

Solutions :

- AbortController ;
- cancellation ;
- request ID ;
- query keys ;
- server-state manager.

```js
const controller = new AbortController();

fetch(url, { signal: controller.signal });

controller.abort();
```

---

## 5. Retry et réseau

Le client implémente un backoff pour les seules requêtes idempotentes admissibles décrites plus haut. Il ne retente pas automatiquement les mutations. Le contexte réseau écoute les événements `online`/`offline`, suit les réponses API et sonde le backend toutes les 15 secondes lorsqu'il est marqué inaccessible (si le navigateur indique être en ligne). Cela signale l'état de connexion ; aucune requête d'écriture n'est mise en file ni rejouée après reconnexion.

Le délai du retry est calculé par la politique du client (750 ms × 2ⁿ, avec jusqu'à 250 ms d'aléa, plafonné à 3 s) :

```text
tentative 1
↓ délai croissant avec aléa
tentative 2
↓ au plus 2 nouvelles tentatives
↓
échec
```

UX :

```text
Connexion perdue
```

puis :

```text
Connexion rétablie
```

---

## 6. Error Boundary

Une erreur dans un composant ne doit pas forcément provoquer un écran blanc.

Afficher :

```text
Cette section n'a pas pu être affichée.

[ Réessayer ]
```

---

## 7. Double soumission & Sécurité des formulaires (Anti-Martèlement)

Dans tout le projet GALAXAM SIRH, **toute possibilité de double soumission (double-click ou requêtes parallèles concurrentes) est strictement proscrite**.

### 1. Norme au niveau du Composant UI (`<Button />`)

Tous les formulaires du projet doivent impérativement utiliser le composant réutilisable `@/components/ui/forms/Button.jsx`. Ce composant applique une triple barrière de sécurité :

- **Interception physique DOM (`handleClick`)** : Si `loading` ou `disabled` est vrai, l'événement de clic est stoppé de manière synchrone (`e.preventDefault()`, `e.stopPropagation()`).
- **Blocage CSS Browser** : La classe `pointer-events-none disabled:pointer-events-none` est appliquée dynamiquement pour empêcher le navigateur de relayer tout clic supplémentaire.
- **Feedback Visuel Animé** : L'icône du bouton est automatiquement remplacée par un spinner animé (`animate-spin`), avec opacité réduite (`opacity-50`) et curseur `not-allowed`.

### 2. Standard de code synchrone obligatoire pour tout Formulaire / Modale

Tout gestionnaire de soumission de formulaire (création, modification, suppression, activation/désactivation) **doit obligatoirement** suivre ce patron de conception :

```javascript
const handleSave = async (e) => {
  e.preventDefault();
  if (submitting || loading) return; // Clause de garde synchrone immédiate (Anti-Double Submit)

  setSubmitting(true);
  try {
    await service.saveData(formData);
    toast({ title: 'Succès', message: 'Enregistrement réussi.', type: 'success' });
    onClose();
  } catch (err) {
    setError(err.response?.data?.message || 'Erreur lors de l\'enregistrement.');
  } finally {
    setSubmitting(false); // Déverrouillage uniquement après la fin de la promesse HTTP
  }
};
```

### 3. Obligation sur les Modales et Formulaires

Chaque composant de type Modal (`FormModal`) doit :

1. Déclarer et recevoir la propriété `submitting` (ou `loading`).
2. Transmettre cette propriété au bouton de soumission `<Button type="submit" loading={submitting}>`.

---

# React Performance

## 8. Code splitting

Déjà appliqué :

```js
const Dashboard = lazy(() => import("./Dashboard"));
```

Ne charger que le code nécessaire.

## 9. Lazy loading granulaire

Lazy-loader aussi :

- onglets lourds ;
- modales ;
- graphiques ;
- éditeurs ;
- composants rarement utilisés.

## 10. React.memo

À utiliser uniquement après mesure :

```jsx
const EmployeeRow = React.memo(EmployeeRowComponent);
```

## 11. useMemo / useCallback

Ne pas les utiliser systématiquement. Mesurer d'abord le coût du render.

## 12. Virtualisation

Pour 500+ lignes :

```text
500 données
   ↓
virtualisation
   ↓
~15 éléments DOM visibles
```

Très pertinent pour les tables RH et le pointage.

---

# 13. Pointage GALAXAM

100 employés × 5 jours × entrée/sortie = **1000 cellules**.

Éviter :

```text
1000 cellules → 1000 requêtes
```

Préférer :

```text
édition locale
↓
debounce/autosave
↓
PATCH /pointages/batch
↓
synchronisation
```

Ajouter navigation clavier, validation locale et virtualisation.

---

# 14. Optimisations déjà réalisées

- Lazy loading ;
- code splitting ;
- Vite ;
- ES2022 ;
- CSS split/minification ;
- GZIP ;
- cache HTTP ;
- images lazy ;
- fonts système ;
- preview production.

Mesures de contexte :

| Métrique            |   Avant |  Après |
| -------------------- | ------: | ------: |
| Requêtes Dashboard  |     109 |      16 |
| Ressources Dashboard | 12,5 Mo |  430 Ko |
| JS fiche employé    |  155 Ko | 29,1 Ko |
| JS sécurité        |   64 Ko |  5,5 Ko |
| DOMContentLoaded     |  186 ms |   54 ms |
| Load                 |  272 ms |  134 ms |

Ces chiffres doivent être remesurés après chaque évolution importante.

---

# 15. Priorités GALAXAM

### État des mécanismes d'authentification et de reprise

Les mécanismes de renouvellement proactif, de reprise après un 401, de verrou local au runtime et de retry limité aux lectures idempotentes sont déjà implémentés (voir §1 et §5). Les éléments ci-dessous restent des pistes de travail, pas des fonctionnalités garanties par le code actuel.

### P1

8. Gestion centralisée du server state/cache
9. Invalidation structurée du cache
10. Annulation cohérente des requêtes par `AbortController`
11. Traitement applicatif des réponses arrivant dans le désordre
12. Error Boundary et parcours de reprise
13. Mode hors ligne avec mise en file des écritures, si requis

### P2

14. Virtualisation
15. Batch API pointage
16. Synchronisation complète de la session entre onglets
17. Optimistic UI
18. Prefetch
19. Profiling React

### P3

20. React.memo
21. useMemo
22. useCallback
23. Micro-optimisations

---

# 16. Checklist

## Auth

- [x] Renouvellement proactif : timer avant expiration, refresh à la reprise d'activité et vérification au départ d'une requête.
- [x] Intercepteur 401 : refresh puis reprise unique de la requête si un access token est présent en mémoire.
- [x] Verrou de refresh partagé dans une instance ; Web Locks entre onglets lorsque l'API navigateur est disponible.
- [x] Retry limité : au plus deux nouvelles tentatives pour GET/HEAD/OPTIONS et erreurs réseau transitoires documentées au §5.
- [x] Expiration : retrait de l'utilisateur et du jeton local sur événement d'expiration ; appel serveur de déconnexion tenté par l'action explicite de déconnexion.
- [ ] Synchronisation complète de l'état de session entre onglets : non garantie ; Web Locks ne synchronise pas à lui seul l'état React/localStorage.

## React

- [ ] Pas de mutation directe
- [ ] useEffect maîtrisés
- [ ] Pas de boucle de fetch
- [ ] UI state séparé du server state
- [ ] Cache défini
- [ ] Invalidation
- [ ] Race conditions
- [ ] AbortController

## Performance

- [ ] Routes lazy
- [ ] Composants lourds lazy
- [ ] Bundle analysé
- [ ] Images optimisées
- [ ] Fonts optimisées
- [ ] Compression
- [ ] Cache HTTP
- [ ] Virtualisation
- [ ] Batch API
- [ ] Requêtes inutiles supprimées

## UX

- [ ] Loading clair
- [ ] Skeleton pertinent
- [ ] Feedback immédiat
- [ ] Empty states
- [ ] Erreurs compréhensibles
- [ ] Retry disponible
- [ ] Données saisies conservées
- [ ] Pas de F5 nécessaire pendant une session normale

---

## Conclusion

Le prochain niveau de GALAXAM SIRH est de passer d'une application simplement **rapide** à une application **rapide + fluide + cohérente + récupérable + fiable**.

L'utilisateur ne doit jamais avoir besoin de comprendre JWT, `401`, cache ou race condition. Il doit simplement pouvoir continuer son travail.
