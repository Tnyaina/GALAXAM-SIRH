Utilisation de render

structure Git : main production, develop code local -> feature sprint etc -> PR vers develop , staging (apartir de develop)

## 🔒 Sécurité et Indexation (SEO Technique)

L'application étant un intranet privé, l'indexation par les moteurs de recherche est explicitement bloquée via la balise `<meta name="robots" content="noindex, nofollow" />` dans `frontend/index.html`.
Cette règle empêche la fuite de données RH sensibles sur le web.
*   **Note sur les audits Lighthouse :** Le score "SEO" de Lighthouse signalera systématiquement une erreur *"Page is blocked from indexing"*. Il s'agit d'un **faux positif attendu** pour une application interne. Les autres bonnes pratiques (Titres, Méta descriptions, Viewport) sont toutefois respectées pour maintenir l'accessibilité.