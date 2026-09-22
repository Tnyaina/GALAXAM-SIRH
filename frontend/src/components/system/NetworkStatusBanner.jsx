import { useEffect, useRef, useState } from 'react';
import { CloudOff, Wifi } from 'lucide-react';
import { useNetworkStatus } from '@/context/networkStatus';

/** Affiche une information unique, quel que soit l'écran actuellement ouvert. */
export default function NetworkStatusBanner() {
    const { online, serverReachable } = useNetworkStatus();
    const wasUnavailable = useRef(false);
    const [showRestored, setShowRestored] = useState(false);
    const unavailable = !online || !serverReachable;

    useEffect(() => {
        if (unavailable) {
            wasUnavailable.current = true;
            setShowRestored(false);
            return undefined;
        }
        if (!wasUnavailable.current) return undefined;
        wasUnavailable.current = false;
        setShowRestored(true);
        const timeout = window.setTimeout(() => setShowRestored(false), 5000);
        return () => window.clearTimeout(timeout);
    }, [unavailable]);

    if (unavailable) {
        return (
            <div role="alert" className="fixed inset-x-0 bottom-0 z-[100] flex items-center justify-center gap-2 border-t border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/15 px-4 py-2.5 text-xs font-medium text-[var(--color-fg)] shadow-lg">
                <CloudOff size={16} className="text-[var(--color-warning-600)]" />
                <span>{online ? 'Serveur momentanément inaccessible. Les modifications ne sont pas envoyées.' : 'Vous êtes hors ligne. Les modifications ne sont pas envoyées.'}</span>
            </div>
        );
    }
    if (showRestored) {
        return (
            <div role="status" className="fixed inset-x-0 bottom-0 z-[100] flex items-center justify-center gap-2 border-t border-[var(--color-success-500)]/40 bg-[var(--color-success-500)]/15 px-4 py-2.5 text-xs font-medium text-[var(--color-fg)] shadow-lg">
                <Wifi size={16} className="text-[var(--color-success-600)]" />
                <span>Connexion au serveur rétablie. Actualisez les données avant de reprendre une action.</span>
            </div>
        );
    }
    return null;
}
