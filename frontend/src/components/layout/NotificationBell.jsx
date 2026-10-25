import { useState, useRef, useEffect, memo, useCallback } from 'react';
import { Bell, CheckCheck, X, ChevronRight, AlertTriangle, Clock, FileText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '@/features/dashboard/hooks/useDashboard';
import Badge from '@/components/ui/data-display/Badge';
import { fmtRelative } from '@/utils/dateUtils';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { Tooltip } from '@/components/ui/overlays/Tooltip';

const PRIORITE_CONFIG = {
    CRITIQUE: {
        variant: 'danger',
        icon: AlertTriangle,
        label: 'Critique',
    },
    HAUTE: {
        variant: 'warning',
        icon: Clock,
        label: 'Prioritaire',
    },
    NORMALE: {
        variant: 'primary',
        icon: FileText,
        label: null,
    },
    INFO: {
        variant: 'default',
        icon: FileText,
        label: null,
    },
};

const NotifRow = memo(function NotifRow({ notif, onClick }) {
    const cfg = PRIORITE_CONFIG[notif.priorite] || PRIORITE_CONFIG.NORMALE;
    const Icon = cfg.icon;
    const nonLue = !notif.dateLecture;

    return (
        <button
            type="button"
            role="menuitem"
            onClick={() => onClick(notif)}
            className={`w-full text-left px-4 py-3 border-b border-[var(--color-border-subtle)] last:border-b-0
                        hover:bg-[var(--color-surface-hover)] group transition-colors rounded-none cursor-pointer
                        ${nonLue ? 'bg-[var(--color-brand-500)]/[0.03] border-l-2 border-l-[var(--color-brand-500)]' : 'border-l-2 border-l-transparent'}`}
        >
            <div className="flex items-start gap-3">
                <div className={`w-8 h-8 rounded-none flex items-center justify-center shrink-0 border ${
                    notif.priorite === 'CRITIQUE' || notif.priorite === 'HAUTE'
                        ? 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30 text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]'
                        : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]'
                }`}>
                    <Icon size={15} strokeWidth={2} />
                </div>
                
                <div className="flex-1 min-w-0">
                    <div className="flex flex-col items-start gap-1.5 mb-1.5">
                        {cfg.label && (
                            <Badge variant={cfg.variant} size="sm">
                                {cfg.label}
                            </Badge>
                        )}
                        <p className="w-full min-w-0 text-[13px] font-semibold leading-snug text-[var(--color-fg)] line-clamp-2 text-left">
                            {notif.titre}
                        </p>
                    </div>
                    
                    <p className="text-xs text-[var(--color-fg-muted)] leading-normal line-clamp-2 text-left">
                        {notif.message}
                    </p>
                    
                    <p className="text-[11px] text-[var(--color-fg-subtle)] mt-1.5 font-data">
                        {fmtRelative(notif.dateCreation)}
                    </p>
                </div>

                <div className="flex items-center self-center shrink-0 text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] group-hover:translate-x-0.5 transition-all">
                    <ChevronRight size={15} />
                </div>
            </div>
        </button>
    );
});

function SkeletonNotifList() {
    return (
        <div className="divide-y divide-[var(--color-border-subtle)]">
            {[0, 1, 2].map((i) => (
                <div key={i} className="px-4 py-3 flex items-start gap-3">
                    <Skeleton className="w-7 h-7 rounded-none shrink-0" />
                    <div className="flex-1 min-w-0 space-y-2 pt-0.5">
                        <Skeleton className="h-3 w-3/5" />
                        <Skeleton className="h-2.5 w-full" />
                        <Skeleton className="h-2 w-1/3" />
                    </div>
                </div>
            ))}
        </div>
    );
}

export default function NotificationBell() {
    // État local, références et actions du panneau de notifications.
    const [open, setOpen] = useState(false);
    const panelRef = useRef(null);
    const btnRef = useRef(null);
    const navigate = useNavigate();
    const { notifications, totalNonLues, loading, marquerToutesLues, marquerUne } = useNotifications();

    useEffect(() => {
        if (!open) return;
        
        const handleMousedown = (e) => {
            if (
                panelRef.current && !panelRef.current.contains(e.target) &&
                btnRef.current && !btnRef.current.contains(e.target)
            ) {
                setOpen(false);
            }
        };

        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                setOpen(false);
                btnRef.current?.focus();
            }
        };

        document.addEventListener('mousedown', handleMousedown);
        document.addEventListener('keydown', handleKeyDown);
        return () => {
            document.removeEventListener('mousedown', handleMousedown);
            document.removeEventListener('keydown', handleKeyDown);
        };
    }, [open]);

    const handleNotifClick = useCallback((notif) => {
        marquerUne(notif.idNotification);
        let link = notif.lien;
        
        // Redirection de secours si ancien lien /supervision
        if (link === '/supervision' || link?.startsWith('/supervision')) {
            link = notif.idEmployeConcerne ? `/employes/${notif.idEmployeConcerne}?tab=carriere` : '/carrieres';
        } else if (!link || link === '/conges') {
            // Si aucun lien précis n'est fourni ou s'il s'agit du lien générique /conges
            const titreMsg = ((notif.titre || '') + ' ' + (notif.message || '')).toLowerCase();
            if (titreMsg.includes('expiration') || titreMsg.includes('solde')) {
                link = '/conges?tab=planning';
            } else if (titreMsg.includes('demande') || titreMsg.includes('validation')) {
                link = '/conges?tab=demandes';
            } else {
                link = '/conges';
            }
        }
        
        if (link) {
            navigate(link);
        }
        setOpen(false);
    }, [marquerUne, navigate]);

    return (
        <div className="relative flex items-center">
            <Tooltip content="Alertes & Notifications" position="bottom">
                <button
                    ref={btnRef}
                    id="btn-notifications"
                    type="button"
                    onClick={() => setOpen((v) => !v)}
                    className="relative flex items-center justify-center min-w-[40px] min-h-[40px] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded-none hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                    aria-label="Notifications"
                    aria-expanded={open}
                    aria-haspopup="true"
                >
                    <Bell size={16} strokeWidth={1.75} />
                    {totalNonLues > 0 && (
                        <span className="absolute top-0.5 right-0.5 min-w-[15px] h-[15px] flex items-center justify-center text-[8.5px] font-bold leading-none text-[var(--color-on-color)] bg-[var(--color-danger-500)] rounded-none px-1 ring-1 ring-[var(--color-surface)] font-data">
                            {totalNonLues > 99 ? '99+' : totalNonLues}
                        </span>
                    )}
                </button>
            </Tooltip>

            {open && (
                <div
                    ref={panelRef}
                    role="menu"
                    aria-orientation="vertical"
                    className="fixed right-3 top-12 mt-2 w-[calc(100vw-1.5rem)] max-h-[min(70vh,560px)] sm:absolute sm:right-0 sm:top-full sm:w-[min(30rem,calc(100vw-3rem))] sm:max-w-[calc(100vw-3rem)] flex flex-col z-50
                               bg-[var(--color-surface)] border border-[var(--color-border)]
                               shadow-xl rounded-none overflow-hidden"
                >
                    <div className="px-4 py-3 border-b border-[var(--color-border-subtle)] shrink-0 bg-[var(--color-surface)]">
                        <div className="flex items-center justify-between gap-3">
                            <span className="text-sm font-bold text-[var(--color-fg)]">
                                Alertes et notifications
                            </span>
                            <button
                                type="button"
                                onClick={() => setOpen(false)}
                                className="min-w-[40px] min-h-[40px] shrink-0 flex items-center justify-center text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded-none hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                                aria-label="Fermer le panneau"
                            >
                                <X size={16} strokeWidth={2} />
                            </button>
                        </div>
                        <div className="flex items-center justify-between gap-3 mt-1">
                            <span className="text-xs text-[var(--color-fg-muted)]">
                                {totalNonLues > 0 ? `${totalNonLues} non lue${totalNonLues > 1 ? 's' : ''}` : 'Tout est lu'}
                            </span>
                            {totalNonLues > 0 && (
                                <button
                                    type="button"
                                    onClick={marquerToutesLues}
                                    className="flex items-center gap-1.5 text-xs text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-medium py-1 rounded-none cursor-pointer"
                                    title="Tout marquer comme lu"
                                >
                                    <CheckCheck size={14} strokeWidth={2} />
                                    Tout marquer comme lu
                                </button>
                            )}
                        </div>
                    </div>

                    {/* Liste des notifications */}
                    <div className="flex-1 overflow-y-auto bg-[var(--color-surface)]">
                        {loading && <SkeletonNotifList />}

                        {!loading && notifications.length === 0 && (
                            <div className="p-8 text-center">
                                <div className="w-11 h-11 bg-[var(--color-bg-subtle)] rounded-none flex items-center justify-center mx-auto mb-3">
                                    <CheckCheck size={18} strokeWidth={1.75} className="text-[var(--color-success-500)]" />
                                </div>
                                <p className="text-sm font-bold text-[var(--color-fg)]">Aucune alerte en cours</p>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1">Toutes les actions sont à jour.</p>
                            </div>
                        )}

                        {!loading && notifications.map((notif) => (
                            <NotifRow
                                key={notif.idNotification}
                                notif={notif}
                                onClick={handleNotifClick}
                            />
                        ))}
                    </div>

                    {/* Footer blanc unifié */}
                    {notifications.length > 0 && (
                        <div className="px-4 py-2.5 border-t border-[var(--color-border-subtle)] shrink-0 bg-[var(--color-surface)]">
                            <button
                                type="button"
                                onClick={() => { navigate('/dashboard'); setOpen(false); }}
                                className="flex items-center gap-1 text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] font-semibold  rounded-none cursor-pointer"
                            >
                                Voir toutes les alertes
                                <ChevronRight size={12} strokeWidth={2.5} />
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
