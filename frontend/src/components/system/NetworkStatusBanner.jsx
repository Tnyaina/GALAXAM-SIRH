import { useEffect, useRef, useState } from 'react';
import { CloudOff, Wifi, WifiOff, RefreshCw, ChevronDown, ChevronUp } from 'lucide-react';
import { useNetworkStatus } from '@/context/networkStatus';

/**
 * NetworkStatusBanner — Bannière globale de résilience réseau et connectivité.
 * Avertit l'utilisateur sans bloquer son interface et permet de retester la liaison.
 */
export default function NetworkStatusBanner() {
    const { online, serverReachable, isChecking, checkConnectivity } = useNetworkStatus();
    const wasUnavailable = useRef(false);
    const [showRestored, setShowRestored] = useState(false);
    const [isMinimized, setIsMinimized] = useState(false);
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
        if (isMinimized) {
            return (
                <button
                    type="button"
                    onClick={() => setIsMinimized(false)}
                    className="fixed bottom-3 right-3 z-[100] flex items-center gap-1.5 px-3 py-1.5 bg-[var(--color-danger-600)] text-white text-xs font-semibold shadow-xl border border-[var(--color-danger-700)] rounded-none cursor-pointer animate-pulse"
                    title="Connexion réseau indisponible - Cliquer pour ouvrir le détail"
                >
                    <WifiOff size={14} />
                    <span>Hors ligne</span>
                    <ChevronUp size={12} />
                </button>
            );
        }

        const isNetworkOffline = !online;

        return (
            <aside
                role="alert"
                aria-live="assertive"
                className="fixed inset-x-0 bottom-0 z-[100] border-t border-[var(--color-warning-500)]/50 bg-[var(--color-surface)] shadow-2xl backdrop-blur-md px-4 py-2.5 sm:px-6"
            >
                <div className="mx-auto max-w-7xl flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between text-xs">
                    <div className="flex items-start sm:items-center gap-2.5">
                        <div className="w-7 h-7 flex items-center justify-center bg-[var(--color-warning-500)]/15 text-[var(--color-warning-600)] shrink-0 rounded-none border border-[var(--color-warning-500)]/30">
                            {isNetworkOffline ? <WifiOff size={15} /> : <CloudOff size={15} />}
                        </div>
                        <div>
                            <div className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <span>{isNetworkOffline ? 'Connexion Internet interrompue' : 'Serveur Galaxam inaccessible'}</span>
                                <span className="inline-flex items-center px-1.5 py-0.5 bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] text-[10px] font-semibold border border-[var(--color-danger-500)]/20 uppercase tracking-wider">
                                    Mode Dégradé
                                </span>
                            </div>
                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                {isNetworkOffline
                                    ? "Vous êtes actuellement hors ligne. Vos modifications saisies sur cet écran sont protégées en mémoire locale."
                                    : "La liaison avec le serveur distant est temporairement perdue. Vos saisies restent en mémoire."}
                            </p>
                        </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                        <button
                            type="button"
                            onClick={() => checkConnectivity()}
                            disabled={isChecking}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] text-white text-xs font-medium rounded-none transition-colors cursor-pointer disabled:opacity-50"
                        >
                            <RefreshCw size={13} className={isChecking ? 'animate-spin' : ''} />
                            <span>{isChecking ? 'Vérification…' : 'Tester la connexion'}</span>
                        </button>

                        <button
                            type="button"
                            onClick={() => setIsMinimized(true)}
                            aria-label="Réduire la bannière de statut réseau"
                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                        >
                            <ChevronDown size={15} />
                        </button>
                    </div>
                </div>
            </aside>
        );
    }

    if (showRestored) {
        return (
            <aside
                role="status"
                aria-live="polite"
                className="fixed inset-x-0 bottom-0 z-[100] border-t border-[var(--color-success-500)]/40 bg-[var(--color-surface)] shadow-lg px-4 py-2.5 sm:px-6"
            >
                <div className="mx-auto max-w-7xl flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2.5">
                        <div className="w-7 h-7 flex items-center justify-center bg-[var(--color-success-500)]/15 text-[var(--color-success-600)] shrink-0 rounded-none border border-[var(--color-success-500)]/30">
                            <Wifi size={15} />
                        </div>
                        <div>
                            <span className="font-bold text-[var(--color-fg)]">Connexion rétablie</span>
                            <span className="text-[11px] text-[var(--color-fg-muted)] ml-2 hidden sm:inline">
                                Les échanges avec le serveur Galaxam sont à nouveau opérationnels.
                            </span>
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={() => setShowRestored(false)}
                        className="text-[11px] text-[var(--color-brand-600)] hover:underline font-semibold cursor-pointer"
                    >
                        Masquer
                    </button>
                </div>
            </aside>
        );
    }

    return null;
}

