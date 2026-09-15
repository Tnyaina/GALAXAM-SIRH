import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, AlertTriangle, RotateCcw, CalendarClock, CheckCircle2, Clock, AlertCircle } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import Button from '@/components/ui/forms/Button';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import periodePaieService from '@/services/periodePaieService';

/**
 * DashboardHeader — Backend-driven (Phase 6).
 * La période active et les actions disponibles proviennent exclusivement de
 * /periodes/context (PayrollPeriodContextView), jamais d'une décision React
 * basée sur le statutCode.
 */

/** Résout la couleur du badge selon l'état fourni par le backend. */
function resolveStatutStyle(statutCode) {
    switch (statutCode) {
        case 'A_VALIDER':
        case 'TRANSMIS_DAF':
            return { dot: 'bg-amber-300 animate-pulse', badge: 'bg-amber-400 text-amber-950' };
        case 'EN_COURS':
        case 'CALCULE':
            return { dot: 'bg-emerald-400 animate-pulse', badge: 'bg-emerald-400/90 text-emerald-950' };
        case 'VALIDEE':
        case 'CLOTUREE':
            return { dot: 'bg-sky-300', badge: 'bg-sky-400/90 text-sky-950' };
        default:
            return { dot: 'bg-white/50', badge: 'bg-white/20 text-white' };
    }
}

/** Icône de statut pour le widget paie. */
function StatutIcon({ statutCode, size = 14 }) {
    switch (statutCode) {
        case 'VALIDEE':
        case 'CLOTUREE':
            return <CheckCircle2 size={size} className="text-sky-300" />;
        case 'A_VALIDER':
        case 'TRANSMIS_DAF':
            return <AlertCircle size={size} className="text-amber-300" />;
        default:
            return <Clock size={size} className="text-emerald-300" />;
    }
}

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

    // Contexte backend-driven : périodes + actions disponibles
    const [periodContext, setPeriodContext] = useState(null);
    const [periodes, setPeriodes] = useState([]);

    useEffect(() => {
        let isMounted = true;
        const fetchContext = async () => {
            try {
                // getContext() retourne PayrollPeriodContextView — la période sélectionnée
                // et les availableActions sont déterminés par le backend, pas par React.
                const [context, list] = await Promise.all([
                    periodePaieService.getContext(),
                    periodePaieService.getAllPeriodes()
                ]);
                if (isMounted) {
                    setPeriodContext(context);
                    setPeriodes(Array.isArray(list) ? list : list?.items || []);
                }
            } catch (err) {
                // Silencieux — le dashboard continue de fonctionner sans ce widget
                console.error('[DashboardHeader] Erreur chargement contexte paie:', err);
            }
        };
        fetchContext();
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

    // Période active : provient de periodContext.selectedPeriod (backend-driven),
    // avec fallback robuste sur la liste des périodes si non résolue par date
    const activePeriode = periodContext?.selectedPeriod
        || periodes.find(p => p.statutCode === 'A_VALIDER' || p.statutCode === 'TRANSMIS_DAF' || p.statutCode === 'EN_COURS')
        || (periodes.length > 0 ? periodes[0] : null);

    const activePeriodeId = activePeriode ? (activePeriode.idPeriodePaie ?? activePeriode.id) : null;
    const activePeriodeCode = activePeriode?.code || '';
    const activePeriodeLibelle = activePeriode?.libelle || activePeriode?.label || activePeriodeCode;
    const activePeriodeStatutCode = activePeriode?.statutCode || activePeriode?.state || '';
    const activePeriodeStatutLibelle = activePeriode?.statutLibelle || activePeriodeStatutCode || 'Active';
    const statutStyle = resolveStatutStyle(activePeriodeStatutCode);

    // La route de destination est déterminée par les availableActions du backend.
    // Si EDIT_POINTAGE est disponible → saisie ; si non → validation pour DAF, suivi ou cockpit sinon.
    const periodeRoute = (() => {
        if (!activePeriodeId) return '/pointages?tab=suivi';
        const actions = periodContext?.availableActions || activePeriode?.availableActions || {};
        if (isDaf && actions.SUBMIT?.allowed === true) return '/pointages?tab=validation';
        if (actions.EDIT_POINTAGE?.allowed === true) return '/pointages?tab=saisie';
        return `/paie/periods/${activePeriodeId}/overview`;
    })();

    // Widget rappel périodes de paie : affiche les périodes récentes non clôturées (en excluant l'active si déjà affichée)
    const periodesRappel = periodes
        .filter(p => {
            const pId = p.idPeriodePaie ?? p.id;
            return p.statutCode !== 'CLOTUREE' && (!activePeriodeId || pId !== activePeriodeId);
        })
        .slice(0, 3);

    return (
        <>
            {/* Conteneur unifié Header + Rappel périodes (même pattern vert brand) */}
            <div className="bg-[var(--color-brand-800)] text-white shadow-xs">
                {/* Bannière de bienvenue */}
                <div className="p-3.5 sm:p-4 flex items-center justify-between gap-3">
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
                                    ? ` ${demandesCongeAValiderCount} demande(s) de congé en attente de validation.`
                                    : totalAlertesContrats > 0
                                        ? ` ${totalAlertesContrats} alerte(s) de contrat nécessitent votre attention.`
                                        : 'Tout est à jour dans l\'application SIRH.'}
                            </p>
                        )}

                        {/* Widget période active — Backend-driven (DSH-001) */}
                        {activePeriode && activePeriodeId && (
                            <div className="mt-2.5 flex items-center gap-2">
                                <Link
                                    to={periodeRoute}
                                    className="inline-flex items-center gap-2 px-2.5 py-1 bg-white/10 hover:bg-white/20 border border-white/20 text-xs text-white transition-all group shadow-2xs"
                                    title="Accéder au cockpit de la période active"
                                >
                                    <span className={`inline-block w-2 h-2 rounded-full ${statutStyle.dot}`} />
                                    <span className="font-semibold text-[11px] text-[var(--color-brand-200)] uppercase tracking-wider">Période RH :</span>
                                    <span className="font-bold text-xs text-white font-mono">{activePeriodeLibelle}</span>
                                    <span className={`px-1.5 py-0.2 text-[10px] font-bold uppercase tracking-wider ${statutStyle.badge}`}>
                                        {activePeriodeStatutLibelle}
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

                {/* Barre de rappel des périodes de paie — Même pattern esthétique que le header */}
                {periodesRappel.length > 0 && (
                    <div className="px-3.5 sm:px-4 py-2 bg-black/20 border-t border-white/10 flex items-center gap-3 overflow-x-auto">
                        <div className="flex items-center gap-1.5 shrink-0 text-white">
                            <CalendarClock size={13} className="shrink-0 text-white" />
                            <span className="text-[11px] font-semibold uppercase tracking-wider font-data text-white">
                                Périodes paie :
                            </span>
                        </div>
                        <div className="flex items-center gap-2 flex-wrap">
                            {periodesRappel.map(p => {
                                const pId = p.idPeriodePaie ?? p.id;
                                if (!pId) return null;
                                const style = resolveStatutStyle(p.statutCode || p.state);
                                return (
                                    <Link
                                        key={pId}
                                        to={`/paie/periods/${pId}/overview`}
                                        className="inline-flex items-center gap-2 px-2.5 py-1 bg-white/10 hover:bg-white/20 border border-white/20 text-xs text-white transition-all group shadow-2xs"
                                        title={p.libelle || p.label || p.code}
                                    >
                                        <span className={`inline-block w-1.5 h-1.5 rounded-full ${style.dot}`} />
                                        <span className="font-mono font-bold text-xs text-white">{p.code || p.libelle || p.label}</span>
                                        <span className={`px-1.5 py-0.2 text-[10px] font-bold uppercase tracking-wider ${style.badge}`}>
                                            {p.statutLibelle || p.statutCode || p.state}
                                        </span>
                                        <span className="text-[11px] text-[var(--color-brand-200)] group-hover:translate-x-0.5 transition-transform font-bold">→</span>
                                    </Link>
                                );
                            })}
                        </div>
                    </div>
                )}
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
