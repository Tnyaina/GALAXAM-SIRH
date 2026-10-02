import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { RefreshCw, AlertTriangle, RotateCcw, CalendarClock } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import Button from '@/components/ui/forms/Button';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';

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
            return { dot: 'bg-[var(--color-warning-400)] animate-pulse', badge: 'bg-[var(--color-warning-400)] text-[var(--color-warning-700)]' };
        case 'EN_COURS':
        case 'CALCULE':
            return { dot: 'bg-[var(--color-success-400)] animate-pulse', badge: 'bg-[var(--color-success-500)] text-[var(--color-on-color)]' };
        case 'VALIDEE':
        case 'CLOTUREE':
            return { dot: 'bg-[var(--color-info-200)]', badge: 'bg-[var(--color-info-500)] text-[var(--color-on-color)]' };
        default:
            return { dot: 'bg-[var(--color-surface)]/50', badge: 'bg-[var(--color-surface)]/20 text-[var(--color-on-color)]' };
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
    // Contexte
    const { user, hasPermission } = useAuth();
    const toast = useToast();

    // État local
    const [isOnline, setIsOnline] = useState(navigator.onLine);
    const [periodContext, setPeriodContext] = useState(null);
    const [periodes, setPeriodes] = useState([]);

    // Données dérivées : message de bienvenue et période active fournie par le backend.
    const now = new Date();
    const greeting =
        now.getHours() < 12 ? 'Bonjour' :
            now.getHours() < 18 ? 'Bon après-midi' : 'Bonsoir';

    // Effets
    // Charge le contexte de période sans déduire son statut côté navigateur.
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

    // Surveille la connectivité afin d'actualiser le tableau de bord au retour du réseau.
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

    // Actions utilisateur
    const handleRefresh = async () => {
        await refresh();
        toast({
            title: 'Actualisation réussie',
            message: 'Les données du dashboard sont à jour.',
            type: 'success',
            duration: 3000
        });
    };

    // Données dérivées de navigation
    // P1-02 : Période active = exclusivement periodContext.selectedPeriod fourni par le backend.
    // Aucun fallback local (periodes.find, periodes[0]) : le backend est le seul décisionnaire.
    // Si selectedPeriod est null → l'absence de widget indique explicitement qu'aucune période n'est active.
    const activePeriode = periodContext?.selectedPeriod ?? null;

    const activePeriodeId = activePeriode ? (activePeriode.idPeriodePaie ?? activePeriode.id) : null;
    const activePeriodeCode = activePeriode?.code || '';
    const activePeriodeLibelle = activePeriode?.libelle || activePeriode?.label || activePeriodeCode;
    const activePeriodeStatutCode = activePeriode?.statutCode || activePeriode?.state || '';
    const activePeriodeStatutLibelle = activePeriode?.statutLibelle || activePeriodeStatutCode || 'Active';
    const statutStyle = resolveStatutStyle(activePeriodeStatutCode);

    // P1-02 : La route de destination est fournie par le backend via periodContext.nextStep.destination
    // ou via availableActions. Le frontend ne reconstruit pas de logique conditionnelle par rôle/statut.
    const peutAccederPaie = hasPermission('PAIE_READ');
    const peutAccederPointage = hasPermission('POINTAGE_READ')
        && ['ROLE_ADMIN', 'ROLE_AGENT_POINTAGE'].includes(user?.role);
    const routePeriode = (periodeId, destinationWorkflow = null) => {
        if (!periodeId) return null;
        if (peutAccederPaie) return destinationWorkflow || `/paie?periodId=${periodeId}`;
        if (peutAccederPointage) return `/pointages?tab=suivi&periode=${periodeId}`;
        return null;
    };

    const periodeRoute = (() => {
        if (!activePeriodeId) return null; // pas de navigation sans période active
        if (!peutAccederPaie) return routePeriode(activePeriodeId);
        // Priorité 1 : nextStep calculé par le backend selon rôle + statut
        if (periodContext?.nextStep?.destination) return periodContext.nextStep.destination;
        // Priorité 2 : action EDIT_POINTAGE disponible selon backend
        const actions = periodContext?.availableActions || activePeriode?.availableActions || {};
        if (actions.EDIT_POINTAGE?.allowed === true) return `/pointages?tab=saisie&periode=${activePeriodeId}`;
        // Priorité 3 : vue de suivi de la période
        return routePeriode(activePeriodeId);
    })();

    // Widget rappel périodes de paie : affiche les périodes récentes non clôturées (en excluant l'active si déjà affichée)
    const periodesRappel = periodes
        .filter(p => {
            const pId = p.idPeriodePaie ?? p.id;
            return p.statutCode !== 'CLOTUREE' && (!activePeriodeId || pId !== activePeriodeId);
        })
        .slice(0, 3);

    const activePeriodeChipContent = (
        <>
            <span className={`inline-block w-2 h-2 rounded-full ${statutStyle.dot}`} />
            <span className="font-semibold text-[11px] text-[var(--color-brand-200)] uppercase tracking-wider">Période RH :</span>
            <span className="font-bold text-xs text-[var(--color-on-color)] font-mono">{activePeriodeLibelle}</span>
            <span className={`px-1.5 py-0.2 text-[10px] font-bold uppercase tracking-wider ${statutStyle.badge}`}>
                {activePeriodeStatutLibelle}
            </span>
            {periodeRoute && <span className="text-[11px] text-[var(--color-brand-200)] group-hover:translate-x-0.5 transition-transform font-bold">→</span>}
        </>
    );

    // Rendu
    return (
        <>
            {/* Conteneur unifié Header + Rappel périodes (même pattern vert brand) */}
            <div className="bg-[var(--color-brand-800)] text-[var(--color-on-color)] shadow-xs">
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
                            <Skeleton className="h-3.5 w-64 bg-[var(--color-surface)]/20 mt-1" />
                        ) : (
                            <p className="text-[var(--color-brand-100)] text-xs">
                                {demandesCongeAValiderCount > 0
                                    ? ` ${demandesCongeAValiderCount} demande(s) de congé en attente de validation.`
                                    : totalAlertesContrats > 0
                                        ? ` ${totalAlertesContrats} alerte(s) de contrat nécessitent votre attention.`
                                        : 'Tout est à jour dans l\'application GRH.'}
                            </p>
                        )}

                        {/* Widget période active — Backend-driven (DSH-001) */}
                        {activePeriode && activePeriodeId && (
                            <div className="mt-2.5 flex items-center gap-2">
                                {periodeRoute ? <Link
                                    to={periodeRoute}
                                    className="inline-flex items-center gap-2 px-2.5 py-1 bg-[var(--color-surface)]/10 hover:bg-[var(--color-surface)]/20 border border-[var(--color-on-color)]/20 text-xs text-[var(--color-on-color)] transition-all group shadow-2xs"
                                    title="Accéder au cockpit de la période active"
                                >
                                    {activePeriodeChipContent}
                                </Link> : <span
                                    className="inline-flex items-center gap-2 px-2.5 py-1 bg-[var(--color-surface)]/10 border border-[var(--color-on-color)]/20 text-xs text-[var(--color-on-color)]"
                                    title="Aucun accès disponible pour cette période"
                                >{activePeriodeChipContent}</span>}
                            </div>
                        )}
                    </div>
                    <div className="flex flex-col items-end gap-2">
                        <Button
                            onClick={handleRefresh}
                            loading={loading}
                            variant="ghost"
                            size="sm"
                            className="bg-[var(--color-surface)]/15 hover:bg-[var(--color-surface)]/25 text-[var(--color-on-color)] border-transparent"
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
                    <div className="px-3.5 sm:px-4 py-2 bg-[var(--color-overlay)]/20 border-t border-[var(--color-on-color)]/10 flex items-center gap-3 overflow-x-auto">
                        <div className="flex items-center gap-1.5 shrink-0 text-[var(--color-on-color)]">
                            <CalendarClock size={13} className="shrink-0 text-[var(--color-on-color)]" />
                            <span className="text-[11px] font-semibold uppercase tracking-wider font-data text-[var(--color-on-color)]">
                                Périodes paie :
                            </span>
                        </div>
                        <div className="flex items-center gap-2 flex-wrap">
                            {periodesRappel.map(p => {
                                const pId = p.idPeriodePaie ?? p.id;
                                if (!pId) return null;
                                const style = resolveStatutStyle(p.statutCode || p.state);
                                const destination = routePeriode(pId);
                                const content = (
                                    <>
                                        <span className={`inline-block w-1.5 h-1.5 rounded-full ${style.dot}`} />
                                        <span className="font-mono font-bold text-xs text-[var(--color-on-color)]">{p.code || p.libelle || p.label}</span>
                                        <span className={`px-1.5 py-0.2 text-[10px] font-bold uppercase tracking-wider ${style.badge}`}>
                                            {p.statutLibelle || p.statutCode || p.state}
                                        </span>
                                        {destination && <span className="text-[11px] text-[var(--color-brand-200)] group-hover:translate-x-0.5 transition-transform font-bold">→</span>}
                                    </>
                                );
                                return (
                                    destination ? <Link
                                        key={pId}
                                        to={destination}
                                        className="inline-flex items-center gap-2 px-2.5 py-1 bg-[var(--color-surface)]/10 hover:bg-[var(--color-surface)]/20 border border-[var(--color-on-color)]/20 text-xs text-[var(--color-on-color)] transition-all group shadow-2xs"
                                        title={p.libelle || p.label || p.code}
                                    >
                                        {content}
                                    </Link>
                                        : <span
                                            key={pId}
                                            className="inline-flex items-center gap-2 px-2.5 py-1 bg-[var(--color-surface)]/10 border border-[var(--color-on-color)]/20 text-xs text-[var(--color-on-color)]"
                                            title="Aucun accès disponible pour cette période"
                                        >{content}</span>
                                );
                            })}
                        </div>
                    </div>
                )}
            </div>

            {/* Message offline */}
            {!isOnline && (
                <div role="status" aria-live="polite" className="bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 px-5 py-4 text-xs text-[var(--color-warning-500)] flex items-center gap-3">
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
