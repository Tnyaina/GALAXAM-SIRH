// En développement et dans l'installation native, le serveur web relaie /api/.
// Une adresse explicite reste possible pour les déploiements qui en ont besoin.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/';
