# Conventions d'architecture

## Principe directeur

L'application est **pilotée par le backend** pour les décisions métier. Le serveur possède les règles de calcul, les seuils, les statuts, les transitions, les autorisations et les validations finales. Le frontend consomme les résultats et les actions disponibles renvoyés par l'API. Les contrôles de saisie dans le navigateur améliorent l'ergonomie ; le backend les vérifie de nouveau lors de toute commande.

Les conversions de présentation (format de date, monnaie, durée, libellé et tri d'une liste déjà chargée pour l'affichage) restent possibles dans le frontend. Elles ne doivent pas décider d'un droit, d'un montant de paie, d'un solde, d'une éligibilité ou d'une transition. Pour les listes paginées ou les classements métier, le serveur fournit le tri et le filtre.

## Propriété des domaines

| Responsabilité | Emplacement |
| --- | --- |
| Contrat HTTP, authentification de la requête et DTO | `backend/.../<domaine>/api/` |
| Orchestration des cas d'usage et transactions | `backend/.../<domaine>/application/` |
| Invariants et moteurs de calcul | `backend/.../<domaine>/domain/` |
| Requêtes SQL et stockage | `backend/.../<domaine>/persistence/` |
| Pages et composants propres à une fonction | `frontend/src/features/<fonction>/` |
| Client HTTP commun | `frontend/src/api/` |
| Composants visuels sans règle métier | `frontend/src/components/ui/` |

La structure existante est progressive : tous les domaines Java ne possèdent pas encore les quatre sous-dossiers. Créer un dossier lorsqu'il reçoit une responsabilité réelle ; éviter les dossiers vides et les déplacements massifs sans besoin fonctionnel.

Un domaine propriétaire expose ses données ou ses résultats aux autres domaines par un service ou un contrat explicite. Une modification des tables d'un autre domaine demande une revue de la frontière et de la transaction. `common/` contient seulement des mécanismes transversaux ; une règle RH, de pointage ou de paie reste dans son domaine propriétaire.

## Flux d'une fonctionnalité

1. Identifier le domaine propriétaire et la règle métier dans son README ou son document `backend/docs/boky/`.
2. Définir la réponse API : données, statut, actions disponibles, motifs de blocage et erreurs exploitables par l'interface.
3. Implémenter les invariants côté serveur, puis le cas d'usage et le point d'entrée HTTP.
4. Dans le frontend, placer l'appel dans `features/<fonction>/api/`, l'orchestration React dans `hooks/` si nécessaire, et la présentation dans `pages/` ou `components/`.
5. Ajouter une migration versionnée dans `docker/migrations/` pour toute évolution de base. Les schémas initiaux et migrations livrées restent immuables.
6. Vérifier les tests pertinents, le build et les parcours affectés ; mettre à jour la documentation du domaine si le contrat ou l'invariant change.

## Critères pour améliorer l'arborescence

- Déplacer un fichier vers un domaine lorsque son propriétaire est clair et que ses imports peuvent être mis à jour dans le même changement.
- Extraire un composant partagé après au moins deux usages réels avec le même contrat, sans y importer de logique d'un domaine.
- Garder les DTO d'API distincts des objets de persistance ; éviter qu'un contrôleur fournisse directement une ligne de table comme contrat public.
- Éviter les imports du frontend entre deux `features/` ; remonter une primitive réellement commune dans `src/components/`, `src/hooks/` ou `src/utils/`.
- Préférer une extraction ciblée pour les pages ou services volumineux : séparer chargement, actions et vues selon leur responsabilité, sans découpage artificiel.

Le README racine décrit l'ensemble du produit. Les documents `backend/docs/boky/` décrivent les règles métier ; les README de domaine et `frontend/src/features/CONVENTIONS.md` décrivent la navigation dans le code. Cette convention complète ces sources sans les remplacer.

## README technique de chaque domaine backend

Chaque domaine métier doit avoir **un seul `README.md` technique** à la racine de son dossier Java. Il sert à retrouver rapidement le fonctionnement du code ; `backend/docs/boky/` conserve la description détaillée des règles métier. Rédiger les README progressivement à partir du code vérifié. Le [README Organisation](backend/src/main/java/mg/galaxam/sirh/organisation/README.md) est le premier exemple.

Chaque README contient au minimum, de façon concise :

1. Un schéma Mermaid du parcours principal : entrée HTTP, service d'application, décision métier, persistance et dépendances externes. Indiquer les étapes absentes, sans inventer de couche.
2. Un inventaire de **chaque fichier source** et de ses méthodes importantes : classe et méthode (ou groupe homogène d'accesseurs/CRUD), appelant ou route, usage concret, effet (lecture, calcul, écriture), importance pour le fonctionnement et tests pertinents. Les méthodes répétitives peuvent être regroupées si leur rôle reste explicite.
3. Une distinction explicite entre **règle métier**, **orchestration**, **adaptateur**, **mécanisme partagé** et **simple présentation de données**. « Backend driven » décrit la responsabilité du serveur face au frontend ; ce n'est pas une catégorie de méthode.
4. Les données possédées, les domaines consommateurs, les transactions et les points de contrat API à préserver.

Dans la colonne « importance », utiliser **critique** pour un calcul, une décision, une autorisation ou une écriture dont l'erreur affecte les données métier ; **structurant** pour un cas d'usage ou une frontière API ; **support** pour une lecture, un export ou un mécanisme auxiliaire. Justifier le niveau en une phrase. Mettre à jour le README lors d'un changement de parcours, d'API ou de propriétaire d'une règle, et vérifier les liens vers les méthodes après un renommage.

Les README techniques des domaines déjà traités suivent cette convention. Les README de `organisation`, `admin`, `securite`, `dashboard`, `contrat`, `conge`, `pointage` et `paieconfig` suivent déjà ce format. `common`, `config` et `cron` sont transversaux ou techniques : documenter leurs mécanismes et leurs consommateurs dans leur README, sans les présenter artificiellement comme des domaines métier.

## Lire un parcours dans le code

Pour retrouver le fonctionnement principal, suivre cette chaîne : **page React → service API frontend → contrôleur Java → service d'application → moteur ou politique de domaine → persistance**. Le sens inverse aide à retrouver les écrans concernés. Un service API JavaScript transmet une requête ; il n'est pas le service qui exécute la règle métier.

| Exemple | Point d'entrée frontend | Contrat HTTP | Cas d'usage serveur | Règle métier |
| --- | --- | --- | --- | --- |
| Calcul d'une campagne de paie | `features/payroll/pages/CalculStepPage.jsx` et `features/payroll/api/paieService.js` | `paie/api/PaieController.java` | `paie/application/calculation/PayrollService.java` (`calculerPaiePeriode`) | `paie/domain/engine/PayrollExecutionEngine.java` et moteurs associés |
| Simulation d'une demande de congé | `features/leave/hooks/useDemandeCongeForm.js` et `features/leave/api/congeService.js` | `conge/api/DemandeCongeController.java` | `conge/application/CongeService.java` (`simulerDemande`) | `conge/domain/CongeCalculateurEngine.java` (`simuler`) |
| Calcul des semaines de pointage | `features/attendance/` et son service API | `pointage/api/PointageController.java` (`calculerTout`) | `pointage/application/PointageSyntheseService.java` (`calculerToutesSemainesPeriode`) | `pointage/domain/calculation/AttendanceEngine.java` et `TemporalAttendanceEngine.java` |

### Reconnaître le rôle d'une méthode

- **Commande métier** : change l'état ou lance un calcul officiel ; chercher le contrôleur puis le service `application/` et sa transaction.
- **Évaluation métier** : renvoie une simulation, une capacité d'action ou un motif de blocage ; le backend reste propriétaire de la décision, même sans écriture.
- **Moteur ou politique de domaine** : applique un invariant ou une formule ; chercher `domain/`, même si le service d'application prépare ses entrées.
- **Adaptateur** : traduit une requête HTTP, une réponse, un document ou une ligne SQL ; chercher `api/`, `persistence/` ou un service d'export.
- **Utilitaire de présentation** : formate ou réarrange des données déjà décidées, sans créer de montant, droit, statut ou seuil officiel.

### Points à clarifier progressivement

Le nom `utils/` ne garantit pas qu'un fichier soit neutre. `features/payroll/utils/manualVariableCalculation.js` prépare et valide localement le formulaire d'une rubrique ; la validation et le montant final doivent rester contrôlés par le backend. `features/payroll/utils/netDeductions.js` recompose des sous-totaux à partir des lignes du bulletin : vérifier que ces sous-totaux sont purement informatifs, puis préférer des valeurs API explicites s'ils deviennent des chiffres officiels. `features/attendance/utils/pointageStandardHours.js` calcule un aperçu d'horaire : l'API reste propriétaire des heures retenues. Ces fichiers méritent des noms qui indiquent leur rôle de formulaire ou d'aperçu lors d'un prochain changement ciblé.

Pour les nouveaux fichiers, préférer `*Presentation`, `*Formatter` ou `*FormPayload` lorsqu'il s'agit de l'interface. Réserver `*Policy`, `*Engine` et `*Calculation` aux règles métier du backend, sauf si un nom existant est déjà public et qu'un renommage demande une migration coordonnée.
