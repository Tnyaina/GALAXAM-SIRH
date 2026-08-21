import { useState, useEffect } from 'react';
import { RefreshCw, AlertTriangle, RotateCcw } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import Button from '@/components/ui/forms/Button';
import { Skeleton } from '@/components/ui/feedback/Skeleton';

export default function DashboardHeader({ 
    loading, 
    error, 
    refresh, 
    lastRefresh, 
    demandesCongeAValiderCount = 0, 
    totalAlertesContrats = 0 
}) {
    const { user } = useAuth();
    const toast = useToast();
    const [isOnline, setIsOnline] = useState(navigator.onLine);

    const now = new Date();
    const greeting =
        now.getHours() < 12 ? 'Bonjour' :
            now.getHours() < 18 ? 'Bon après-midi' : 'Bonsoir';

    useEffect(() => {
        const handleOnline = () => {
            setIsOnline(true);
            toast({
                title: 'Connexion rétablie',
                message: 'Vous êtes à nouveau connecté. Actualisation des données...',
                type: 'success',
                duration: 3000
            });
            refresh();
        };

        const handleOffline = () => {
            setIsOnline(false);
        };

        window.addEventListener('online', handleOnline);
        window.addEventListener('offline', handleOffline);

        return () => {
            window.removeEventListener('online', handleOnline);
            window.removeEventListener('offline', handleOffline);
        };
    }, [refresh, toast]);

    const handleRefresh = async () => {
        await refresh();
        toast({
            title: 'Actualisation réussie',
            message: 'Les données du dashboard sont à jour.',
            type: 'success',
            duration: 3000
        });
    };

    return (
        <>
            {/* Bannière de bienvenue */}
            <div className="bg-[var(--color-brand-800)] text-white p-6 flex items-center justify-between gap-4 shadow-md">
                <div>
                    <p className="text-[var(--color-brand-200)] text-xs font-medium mb-1 uppercase tracking-wider font-data">
                        {now.toLocaleDateString('fr-FR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
                    </p>
                    <h2 className="text-xl font-extrabold tracking-tight mb-1">
                        {greeting}, {user?.identifiant || 'utilisateur'}
                    </h2>
                    {loading ? (
                        <Skeleton className="h-4 w-64 bg-white/20 mt-1" />
                    ) : (
                        <p className="text-[var(--color-brand-100)] text-xs">
                            {demandesCongeAValiderCount > 0
                                ? ` ${demandesCongeAValiderCount} demande(s) de congé en attente de validation DAF/RH.`
                                : totalAlertesContrats > 0
                                    ? `⚠️ ${totalAlertesContrats} alerte(s) de contrat nécessitent votre attention.`
                                    : 'Tout est à jour dans l\'application SIRH.'}
                        </p>
                    )}
                </div>
                <div className="flex flex-col items-end gap-2">
                    <Button
                        onClick={handleRefresh}
                        loading={loading}
                        variant="ghost"
                        size="sm"
                        className="bg-white/15 hover:bg-white/25 text-white border-transparent"
                        icon={<RefreshCw size={14} />}
                    >
                        Actualiser
                    </Button>
                    {lastRefresh && (
                        <p className="text-[10px] text-[var(--color-brand-200)] font-data">
                            Dernière MAJ : {lastRefresh.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
                        </p>
                    )}
                </div>
            </div>

            {/* Message offline */}
            {!isOnline && (
                <div role="alert" className="bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 px-5 py-4 text-xs text-[var(--color-warning-500)] flex items-center gap-3">
                    <AlertTriangle size={16} className="shrink-0" />
                    <span className="font-semibold">Connexion perdue. Affichage des dernières données en cache.</span>
                </div>
            )}

            {/* Message d'erreur */}
            {error && (
                <div role="alert" className="bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 px-5 py-4 text-xs text-[var(--color-danger-500)] flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                        <AlertTriangle size={16} className="shrink-0" />
                        <span>{typeof error === 'string' ? error : 'Impossible de charger le dashboard.'}</span>
                    </div>
                    <Button
                        onClick={refresh}
                        variant="ghost"
                        size="sm"
                        loading={loading}
                        className="text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/30 shrink-0"
                        icon={<RotateCcw size={14} />}
                    >
                        Réessayer
                    </Button>
                </div>
            )}
        </>
    );
}
