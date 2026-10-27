import { useEffect, useMemo, useState, useCallback, useRef } from 'react';
import { NetworkStatusContext } from '@/context/networkStatus';
import { probeBackendConnectivity } from '@/api/apiClient';

/**
 * Centralise l'état réseau de l'application et la résilience face aux pannes.
 * - `online` : connectivité physique du navigateur (navigator.onLine).
 * - `serverReachable` : validé par des réponses API concrètes du serveur Galaxam.
 * - `isChecking` : sonde de connectivité active en cours d'exécution.
 * - `checkConnectivity` : fonction manuelle ou automatique de vérification de liaison.
 */
export function NetworkStatusProvider({ children }) {
    const [online, setOnline] = useState(() => (typeof navigator !== 'undefined' ? navigator.onLine : true));
    const [serverReachable, setServerReachable] = useState(() => (typeof navigator !== 'undefined' ? navigator.onLine : true));
    const [isChecking, setIsChecking] = useState(false);
    const [lastCheckedAt, setLastCheckedAt] = useState(null);

    const isCheckingRef = useRef(false);

    const checkConnectivity = useCallback(async () => {
        if (isCheckingRef.current) return serverReachable;
        isCheckingRef.current = true;
        setIsChecking(true);
        try {
            const reachable = await probeBackendConnectivity();
            setServerReachable(reachable);
            if (reachable) {
                setOnline(true);
            }
            setLastCheckedAt(new Date());
            return reachable;
        } finally {
            isCheckingRef.current = false;
            setIsChecking(false);
        }
    }, [serverReachable]);

    useEffect(() => {
        const handleOnline = () => {
            setOnline(true);
            // Dès que le navigateur retrouve le réseau, vérifier activement l'API
            checkConnectivity();
        };

        const handleOffline = () => {
            setOnline(false);
            setServerReachable(false);
            setLastCheckedAt(new Date());
        };

        const handleApiStatus = (event) => {
            const reachable = event.detail?.reachable === true;
            setServerReachable(reachable);
            if (reachable) {
                setOnline(true);
            }
            setLastCheckedAt(new Date());
        };

        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);
        window.addEventListener('api-network-status', handleApiStatus);

        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
            window.removeEventListener('api-network-status', handleApiStatus);
        };
    }, [checkConnectivity]);

    // Sonde périodique en arrière-plan lorsque le serveur est injoignable pour détecter son rétablissement
    useEffect(() => {
        if (serverReachable) return undefined;

        const interval = setInterval(() => {
            if (navigator.onLine) {
                checkConnectivity();
            }
        }, 15000); // Teste toutes les 15 secondes

        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible' && navigator.onLine) {
                checkConnectivity();
            }
        };

        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            clearInterval(interval);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [serverReachable, checkConnectivity]);

    const value = useMemo(() => ({
        online,
        serverReachable,
        isChecking,
        lastCheckedAt,
        checkConnectivity,
    }), [online, serverReachable, isChecking, lastCheckedAt, checkConnectivity]);

    return <NetworkStatusContext.Provider value={value}>{children}</NetworkStatusContext.Provider>;
}

