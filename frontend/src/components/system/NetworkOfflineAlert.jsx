import { useState } from 'react';
import { WifiOff, CloudOff, RefreshCw, AlertCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { useNetworkStatus } from '@/context/networkStatus';

/**
 * NetworkOfflineAlert — Bloc d'alerte contextuel pour les zones dont le chargement
 * ou la validation échoue en raison d'une coupure réseau ou d'un serveur injoignable.
 */
export default function NetworkOfflineAlert({
    title,
    message,
    onRetry,
    compact = false,
    className = '',
}) {
    const { online, serverReachable, isChecking, checkConnectivity } = useNetworkStatus();
    const [retrying, setRetrying] = useState(false);

    const isOffline = !online;
    const defaultTitle = isOffline
        ? 'Connexion réseau interrompue'
        : !serverReachable
            ? 'Serveur momentanément indisponible'
            : 'Erreur de communication réseau';

    const defaultMessage = isOffline
        ? 'Impossible de charger ou transmettre les informations. Veuillez vérifier votre connexion Internet.'
        : !serverReachable
            ? 'La passerelle de service Galaxam ne répond pas. Nous tentons de rétablir la communication.'
            : 'Une interruption réseau temporaire a empêché le traitement de cette requête.';

    const handleRetry = async () => {
        setRetrying(true);
        try {
            await checkConnectivity();
            if (onRetry) {
                await onRetry();
            }
        } finally {
            setRetrying(false);
        }
    };

    if (compact) {
        return (
            <div
                role="alert"
                className={`flex items-center justify-between gap-3 p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs rounded-none ${className}`}
            >
                <div className="flex items-center gap-2">
                    {isOffline ? (
                        <WifiOff size={15} className="text-[var(--color-warning-600)] shrink-0" />
                    ) : (
                        <CloudOff size={15} className="text-[var(--color-warning-600)] shrink-0" />
                    )}
                    <span className="text-[var(--color-fg)] font-medium">
                        {message || defaultTitle}
                    </span>
                </div>
                {onRetry && (
                    <Button
                        type="button"
                        size="xs"
                        variant="outline"
                        onClick={handleRetry}
                        disabled={isChecking || retrying}
                        iconLeft={<RefreshCw size={12} className={isChecking || retrying ? 'animate-spin' : ''} />}
                    >
                        {isChecking || retrying ? 'Vérification…' : 'Réessayer'}
                    </Button>
                )}
            </div>
        );
    }

    return (
        <div
            role="alert"
            className={`border border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10 p-4 sm:p-5 rounded-none flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 ${className}`}
        >
            <div className="flex items-start gap-3">
                <div className="w-9 h-9 flex items-center justify-center bg-[var(--color-warning-500)]/20 text-[var(--color-warning-600)] border border-[var(--color-warning-500)]/40 rounded-none shrink-0">
                    {isOffline ? <WifiOff size={18} /> : !serverReachable ? <CloudOff size={18} /> : <AlertCircle size={18} />}
                </div>
                <div>
                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                        {title || defaultTitle}
                    </h4>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1 max-w-xl">
                        {message || defaultMessage}
                    </p>
                </div>
            </div>

            <div className="flex items-center gap-2 shrink-0">
                <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    onClick={handleRetry}
                    disabled={isChecking || retrying}
                    iconLeft={<RefreshCw size={14} className={isChecking || retrying ? 'animate-spin' : ''} />}
                >
                    {isChecking || retrying ? 'Vérification…' : onRetry ? 'Réessayer le chargement' : 'Tester la connexion'}
                </Button>
            </div>
        </div>
    );
}
