import { useEffect, useState } from 'react';
import apiClient from '@/api/apiClient';

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

        apiClient.get(path, { responseType: 'blob' })
            .then(({ data }) => {
                if (disposed) return;
                generatedUrl = URL.createObjectURL(data);
                setObjectUrl(generatedUrl);
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
