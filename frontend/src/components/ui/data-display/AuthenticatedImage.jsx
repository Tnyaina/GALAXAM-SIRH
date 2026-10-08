import { useEffect, useState } from 'react';
import apiClient, { getAccessToken } from '@/api/apiClient';

// Cache mémoire de courte durée : les photos privées évitent les téléchargements
// répétés pendant la navigation, puis leurs URL sont libérées à expiration.
const IMAGE_CACHE_TTL_MS = 5 * 60 * 1000;
const IMAGE_CACHE_MAX_ENTRIES = 100;
const imageCache = new Map();

function getAuthenticatedCacheKey(path) {
    try {
        const payload = getAccessToken()?.split('.')[1];
        if (!payload) return null;
        const decoded = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
        return decoded.sub ? `${decoded.sub}:${path}` : null;
    } catch {
        return null;
    }
}

function getCachedImage(path) {
    const entry = imageCache.get(path);
    if (!entry) return null;
    if (entry.expiresAt <= Date.now()) {
        URL.revokeObjectURL(entry.url);
        imageCache.delete(path);
        return null;
    }
    return entry.url;
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/';

function apiAssetPath(source) {
    if (!source) return null;
    if (source.startsWith('blob:') || source.startsWith('data:')) return source;

    try {
        const apiBase = new URL(API_BASE_URL);
        const asset = new URL(source, apiBase);
        if (asset.origin !== apiBase.origin) return null;

        const apiPrefix = apiBase.pathname.endsWith('/') ? apiBase.pathname : `${apiBase.pathname}/`;
        const relativePath = asset.pathname.startsWith(apiPrefix)
            ? asset.pathname.slice(apiPrefix.length)
            : asset.pathname.replace(/^\/+/, '');
        return `${relativePath}${asset.search}`;
    } catch {
        return null;
    }
}

/** Charge une image privée via le client API afin d'envoyer le JWT et de gérer son renouvellement. */
export default function AuthenticatedImage({ src, fallback = null, onError, ...imageProps }) {
    // État local, effet de chargement authentifié et rendu de repli.
    const [objectUrl, setObjectUrl] = useState(null);
    const [failed, setFailed] = useState(false);

    useEffect(() => {
        let disposed = false;
        let generatedUrl = null;
        const path = apiAssetPath(src);

        setObjectUrl(null);
        setFailed(false);

        if (!path) {
            setFailed(Boolean(src));
            return () => {};
        }
        if (path.startsWith('blob:') || path.startsWith('data:')) {
            setObjectUrl(path);
            return () => {};
        }

        const cacheKey = getAuthenticatedCacheKey(path);
        const cachedUrl = cacheKey ? getCachedImage(cacheKey) : null;
        if (cachedUrl) {
            setObjectUrl(cachedUrl);
            return () => {};
        }

        apiClient.get(path, { responseType: 'blob' })
            .then(({ data }) => {
                if (disposed) return;
                const objectUrl = URL.createObjectURL(data);
                generatedUrl = objectUrl;
                if (cacheKey) {
                    for (const [key, entry] of imageCache) {
                        if (entry.expiresAt <= Date.now()) {
                            URL.revokeObjectURL(entry.url);
                            imageCache.delete(key);
                        }
                    }
                    while (imageCache.size >= IMAGE_CACHE_MAX_ENTRIES) {
                        const oldestKey = imageCache.keys().next().value;
                        URL.revokeObjectURL(imageCache.get(oldestKey).url);
                        imageCache.delete(oldestKey);
                    }
                    imageCache.set(cacheKey, { url: objectUrl, expiresAt: Date.now() + IMAGE_CACHE_TTL_MS });
                    generatedUrl = null;
                }
                setObjectUrl(objectUrl);
            })
            .catch((error) => {
                if (disposed) return;
                setFailed(true);
                onError?.(error);
            });

        return () => {
            disposed = true;
            if (generatedUrl) URL.revokeObjectURL(generatedUrl);
        };
    }, [src, onError]);

    if (!src || failed || !objectUrl) return fallback;
    return <img src={objectUrl} {...imageProps} />;
}
