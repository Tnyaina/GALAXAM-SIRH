// Vite remplace cette valeur au build. Le prototype desktop utilise /api/
// sur son serveur local, qui relaie les requêtes vers le backend Docker.
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/';
