import { useState, useRef, useEffect, memo, useCallback } from 'react';
import { Bell, CheckCheck, X, ChevronRight, AlertTriangle, Clock, FileText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '@/hooks/useDashboard';
import Badge from '@/components/ui/Badge';
import { Skeleton } from '@/components/ui/Skeleton';
import { Tooltip } from '@/components/ui/ExtraComponents';

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

function fmtRelative(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now - d;
    const diffH = Math.floor(diffMs / 3600000);
    if (diffH < 1) return 'Il y a quelques minutes';
    if (diffH < 24) return `Il y a ${diffH}h`;
    const diffJ = Math.floor(diffH / 24);
    return `Il y a ${diffJ} jour(s)`;
}

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
                        ${nonLue ? 'bg-[var(--color-brand-500)]/[0.04]' : ''}`}
        >
            <div className="flex items-start gap-3">
                <div className="w-7 h-7 rounded-none flex items-center justify-center shrink-0 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                    <Icon size={14} className="text-[var(--color-fg-muted)]" strokeWidth={2} />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1.5">
                        {cfg.label && (
                            <Badge variant={cfg.variant} size="sm">
                                {cfg.label}
                            </Badge>
                        )}
                        <p className="text-[13px] font-semibold leading-tight truncate text-[var(--color-fg)]">
                            {notif.titre}
                        </p>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1 leading-snug line-clamp-2">
                        {notif.message}
                    </p>
                    <p className="text-[11px] text-[var(--color-fg-subtle)] mt-1.5 font-data">
                        {fmtRelative(notif.dateCreation)}
                    </p>
                </div>
                <div className="flex flex-col items-end gap-1.5 shrink-0 pt-0.5">
                    {nonLue && <span className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-500)]" aria-hidden="true" />}
                    <ChevronRight size={13} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] transition-colors" />
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
        if (notif.lien) {
            navigate(notif.lien);
        }
        setOpen(false);
    }, [marquerUne, navigate]);

    return (
        <div className="relative flex items-center">
            <Tooltip content="Alertes & Notifications RH" position="bottom">
                <button
                    ref={btnRef}
                    id="btn-notifications"
                    type="button"
                    onClick={() => setOpen((v) => !v)}
                    className="relative flex items-center justify-center w-8 h-8 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                    aria-label="Notifications RH"
                    aria-expanded={open}
                    aria-haspopup="true"
                >
                    <Bell size={18} strokeWidth={1.75} />
                    {totalNonLues > 0 && (
                        <span className="absolute -top-0.5 -right-0.5 min-w-[15px] h-[15px] flex items-center justify-center text-[9px] font-bold leading-none text-white bg-[var(--color-danger-500)] rounded-full px-1 ring-2 ring-[var(--color-surface)] font-data">
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
                    className="absolute right-0 top-full mt-2 w-[380px] max-h-[520px] flex flex-col z-50
                               bg-[var(--color-surface)] border border-[var(--color-border)]
                               shadow-xl rounded-none overflow-hidden"
                >
                    {/* Header blanc unifié */}
                    <div className="px-4 py-3 border-b border-[var(--color-border-subtle)] flex items-center justify-between shrink-0 bg-[var(--color-surface)]">
                        <div className="flex items-center gap-2">
                            <span className="text-[13px] font-bold text-[var(--color-fg)] tracking-tight uppercase">
                                Alertes RH
                            </span>
                            {totalNonLues > 0 && (
                                <Badge variant="warning" size="sm">
                                    {totalNonLues} non lue{totalNonLues > 1 ? 's' : ''}
                                </Badge>
                            )}
                        </div>
                        <div className="flex items-center gap-1">
                            {totalNonLues > 0 && (
                                <button
                                    type="button"
                                    onClick={marquerToutesLues}
                                    className="flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] font-medium px-1.5 py-1 rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                                    title="Tout marquer comme lu"
                                >
                                    <CheckCheck size={13} strokeWidth={2} />
                                    Tout lire
                                </button>
                            )}
                            <button
                                type="button"
                                onClick={() => setOpen(false)}
                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                                aria-label="Fermer le panneau"
                            >
                                <X size={14} strokeWidth={2} />
                            </button>
                        </div>
                    </div>

                    {/* Liste des notifications */}
                    <div className="flex-1 overflow-y-auto bg-[var(--color-surface)]">
                        {loading && <SkeletonNotifList />}

                        {!loading && notifications.length === 0 && (
                            <div className="p-8 text-center">
                                <div className="w-11 h-11 bg-[var(--color-bg-subtle)] rounded-full flex items-center justify-center mx-auto mb-3">
                                    <CheckCheck size={18} strokeWidth={1.75} className="text-[var(--color-success-500)]" />
                                </div>
                                <p className="text-sm font-bold text-[var(--color-fg)]">Aucune alerte en cours</p>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1">Toutes les actions RH sont à jour.</p>
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
                                className="flex items-center gap-1 text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] font-semibold transition-colors rounded-none cursor-pointer"
                            >
                                Voir toutes les alertes sur le dashboard
                                <ChevronRight size={12} strokeWidth={2.5} />
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}