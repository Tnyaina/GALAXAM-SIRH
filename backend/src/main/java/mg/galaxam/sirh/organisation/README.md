# Domaine organisation

Ce dossier est le point d'entrée unique du backend pour la structure de l'entreprise.

- `api/` : contrôleurs REST et contrats d'entrée ;
- `persistence/` : modèles Active Record et SQL JDBC explicite ;
- `application/` : emplacement réservé aux cas d'usage transactionnels lorsqu'ils seront extraits des contrôleurs ;
- `domain/` : emplacement réservé aux futures règles métier indépendantes de JDBC.

Les routes publiques restent sous `/referentiel/*`. Le déplacement de paquet ne modifie donc pas le contrat du frontend.

Les classes de `persistence/` conservent volontairement `Connection`, `PreparedStatement` et `ResultSet`. Aucun ORM n'est introduit.
