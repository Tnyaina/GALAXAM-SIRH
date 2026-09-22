import { useEffect, useMemo, useState } from 'react';
import { NetworkStatusContext } from '@/context/networkStatus';

/**
 * Centralise l'état réseau de l'application.
 * `online` décrit la connectivité navigateur ; `serverReachable` est confirmé
 * par les réponses API réelles (et non seulement par navigator.onLine).
 */
export function NetworkStatusProvider({ children }) {
    const [online, setOnline] = useState(() => navigator.onLine);
    const [serverReachable, setServerReachable] = useState(() => navigator.onLine);

    useEffect(() => {
        const handleOnline = () => {
            setOnline(true);
            // L'accès internet ne garantit pas que l'API est déjà disponible.
            // La première réponse API réussie confirmera le rétablissement.
        };
        const handleOffline = () => {
            setOnline(false);
            setServerReachable(false);
        };
        const handleApiStatus = (event) => {
            const reachable = event.detail?.reachable === true;
            setServerReachable(reachable);
            if (reachable) setOnline(true);
        };

        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);
        window.addEventListener('api-network-status', handleApiStatus);
        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
            window.removeEventListener('api-network-status', handleApiStatus);
        };
    }, []);

    const value = useMemo(() => ({ online, serverReachable }), [online, serverReachable]);
    return <NetworkStatusContext.Provider value={value}>{children}</NetworkStatusContext.Provider>;
}
