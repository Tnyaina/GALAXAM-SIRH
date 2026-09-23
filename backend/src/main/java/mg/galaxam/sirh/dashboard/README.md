# Domaine Dashboard & Notifications

Ce dossier regroupe le tableau de bord RH consolidé et les alertes/notifications système.

- `api/` : contrôleurs REST (`DashboardController`, `NotificationController`)
- `application/` : services d'agrégation d'indicateurs et d'alertes (`DashboardService`, `NotificationRhService`)

Les routes publiques restent sous `/dashboard/*` et `/notifications/*`.
