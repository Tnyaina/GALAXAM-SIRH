# Domaine Admin

Ce dossier est le point d'entrée unique du backend pour l'administration des profils, rôles, modules et menus.

- `api/` : contrôleurs REST (`MenuController`, `ModuleController`, `RoleController`)
- `api/dto/` : contrats d'entrée/sortie HTTP (`RoleRequest`, `MenuRequest`, etc.)
- `persistence/` : modèles Active Record et SQL JDBC explicite (`RefRole`, `RefModule`, `RefMenu`, `RoleMenu`, `RolePermission`)
- `application/` : cas d'usage transactionnels
- `domain/` : règles d'habilitation et invariants métier

Les routes publiques restent sous `/admin/*`. Le déplacement de paquet ne modifie pas le contrat du frontend.
