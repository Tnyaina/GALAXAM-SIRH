# GALAXAM SIRH — UI/UX V3

## Frontend Resilience & React Performance

### Objectif

Une application React professionnelle doit être rapide **et** résiliente : elle doit continuer à fonctionner lorsque le token expire, qu'une requête échoue, que le réseau disparaît ou que plusieurs réponses arrivent dans le mauvais ordre.

---

## 1. Frontend Resilience

### Refresh JWT automatique

Problème constaté :

```text
Token expire → API 401 → erreur → F5 → ça fonctionne
```

Architecture recommandée :

```text
API → 401 → Refresh Token → nouveau Access Token
                       ↓
                rejouer la requête
```

Le frontend doit aussi gérer les cas où le refresh échoue : session invalide → logout propre.

### Refresh Lock

Si plusieurs requêtes obtiennent `401` en même temps, ne jamais lancer plusieurs refresh :

```js
let refreshPromise = null;

function refreshTokenOnce() {
  if (!refreshPromise) {
    refreshPromise = refreshToken()
      .finally(() => refreshPromise = null);
  }
  return refreshPromise;
}
```

Toutes les requêtes attendent le même refresh.

### Retry

Après un refresh réussi, rejouer **une seule fois** la requête originale. Éviter les boucles infinies.

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

Utiliser un backoff :

```text
tentative 1
↓ 500 ms
tentative 2
↓ 1 s
tentative 3
↓
échec
```

Gérer aussi :

```js
window.addEventListener("offline", ...);
window.addEventListener("online", ...);
```

UX :

```text
⚠ Connexion perdue
```

puis :

```text
✓ Connexion rétablie
```

---

## 6. Error Boundary

Une erreur dans un composant ne doit pas forcément provoquer un écran blanc.

Afficher :

```text
⚠ Cette section n'a pas pu être affichée.

[ Réessayer ]
```

---

## 7. Double soumission

Après clic :

```text
[ Enregistrer ]
      ↓
[ ⏳ Enregistrement... ]
```

Désactiver le bouton et, pour les opérations sensibles, prévoir aussi l'idempotence côté backend.

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

| Métrique | Avant | Après |
|---|---:|---:|
| Requêtes Dashboard | 109 | 16 |
| Ressources Dashboard | 12,5 Mo | 430 Ko |
| JS fiche employé | 155 Ko | 29,1 Ko |
| JS sécurité | 64 Ko | 5,5 Ko |
| DOMContentLoaded | 186 ms | 54 ms |
| Load | 272 ms | 134 ms |

Ces chiffres doivent être remesurés après chaque évolution importante.

---

# 15. Priorités GALAXAM

### P0

1. Refresh JWT automatique
2. Interceptor 401
3. Refresh Lock
4. Retry contrôlé
5. Logout propre
6. Protection double soumission
7. Gestion globale des erreurs

### P1

8. Server state/cache
9. Invalidation
10. AbortController
11. Race conditions
12. Error Boundary
13. Offline/reconnexion
14. Retry avec backoff

### P2

15. Virtualisation
16. Batch API pointage
17. Synchronisation multi-onglets
18. Optimistic UI
19. Prefetch
20. Profiling React

### P3

21. React.memo
22. useMemo
23. useCallback
24. Micro-optimisations

---

# 16. Checklist

## Auth

- [ ] Refresh automatique
- [ ] Interceptor 401
- [ ] Refresh Lock
- [ ] Retry contrôlé
- [ ] Logout propre
- [ ] Session cohérente entre onglets

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
