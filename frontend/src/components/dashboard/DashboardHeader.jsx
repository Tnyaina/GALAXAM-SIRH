import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, AlertTriangle, RotateCcw } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import Button from '@/components/ui/forms/Button';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import periodePaieService from '@/services/periodePaieService';
import { getTodayDateString } from '@/utils/dateUtils';

export default function DashboardHeader({
    loading,
    error,
    refresh,
    lastRefresh,
    demandesCongeAValiderCount = 0,
    totalAlertesContrats = 0
}) {
    const { user, hasPermission } = useAuth();
    const isDaf = hasPermission('POINTAGE_VALIDATE');
    const toast = useToast();
    const [isOnline, setIsOnline] = useState(navigator.onLine);
    const [activePeriode, setActivePeriode] = useState(null);

    useEffect(() => {
        let isMounted = true;
        const fetchPeriode = async () => {
            try {
                const list = await periodePaieService.getAllPeriodes();
                if (isMounted && list && list.length > 0) {
                    const todayStr = getTodayDateString();
                    const found = list.find(p => p.statutCode === 'A_VALIDER' || p.statutCode === 'TRANSMIS_DAF')
                        || list.find(p => p.statutCode === 'EN_COURS')
                        || list.find(p => p.dateDebut <= todayStr && p.dateFin >= todayStr)
                        || list[0];
                    setActivePeriode(found);
                }
            } catch (err) {
                console.error('[DashboardHeader] Erreur période active:', err);
            }
        };
        fetchPeriode();
        return () => { isMounted = false; };
    }, [lastRefresh]);

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
            <div className="bg-[var(--color-brand-800)] text-white p-3.5 sm:p-4 flex items-center justify-between gap-3 shadow-xs">
                <div>
                    <p className="text-[var(--color-brand-200)] text-[11px] font-medium mb-0.5 uppercase tracking-wider font-data">
                        {now.toLocaleDateString('fr-FR', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' }).replace(/\s+/g, ' ')}
                    </p>
                    <h2 className="text-base sm:text-lg font-extrabold tracking-tight mb-0.5">
                        {greeting}, {user?.identifiant || 'utilisateur'}
                    </h2>
                    {loading ? (
                        <Skeleton className="h-3.5 w-64 bg-white/20 mt-1" />
                    ) : (
                        <p className="text-[var(--color-brand-100)] text-xs">
                            {demandesCongeAValiderCount > 0
                                ? ` ${demandesCongeAValiderCount} demande(s) de congé en attente de validation DAF/RH.`
                                : totalAlertesContrats > 0
                                    ? ` ${totalAlertesContrats} alerte(s) de contrat nécessitent votre attention.`
                                    : 'Tout est à jour dans l\'application SIRH.'}
                        </p>
                    )}

                    {/* Indicateur de période RH active (DSH-001) */}
                    {activePeriode && (
                        <div className="mt-2.5 flex items-center gap-2">
                            <Link
                                to={activePeriode.statutCode === 'A_VALIDER' && isDaf ? '/pointages?tab=validation' : '/pointages?tab=suivi'}
                                className="inline-flex items-center gap-2 px-2.5 py-1 bg-white/10 hover:bg-white/20 border border-white/20 text-xs text-white transition-all group shadow-2xs"
                                title="Accéder au cockpit de la période active"
                            >
                                <span className={`inline-block w-2 h-2 rounded-full ${
                                    activePeriode.statutCode === 'A_VALIDER' ? 'bg-amber-300 animate-pulse' : 'bg-emerald-400 animate-pulse'
                                }`} />
                                <span className="font-semibold text-[11px] text-[var(--color-brand-200)] uppercase tracking-wider">Période RH :</span>
                                <span className="font-bold text-xs text-white font-mono">{activePeriode.libelle || activePeriode.code}</span>
                                <span className={`px-1.5 py-0.2 text-[10px] font-bold uppercase tracking-wider ${
                                    activePeriode.statutCode === 'A_VALIDER' 
                                        ? 'bg-amber-400 text-amber-950 font-extrabold' 
                                        : activePeriode.statutCode === 'EN_COURS'
                                        ? 'bg-emerald-400/90 text-emerald-950 font-bold'
                                        : 'bg-white/20 text-white'
                                }`}>
                                    {activePeriode.statutLibelle || activePeriode.statutCode || 'Active'}
                                </span>
                                <span className="text-[11px] text-[var(--color-brand-200)] group-hover:translate-x-0.5 transition-transform font-bold">→</span>
                            </Link>
                        </div>
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
