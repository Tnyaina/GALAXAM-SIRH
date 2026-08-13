import { useState, useRef, useEffect } from 'react';
import { Bell, CheckCheck, X, ChevronRight, AlertTriangle, Clock, FileText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '@/hooks/useDashboard';

const PRIORITE_CONFIG = {
    CRITIQUE: {
        chip: 'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)]',
        icon: AlertTriangle,
        text: 'text-[var(--color-fg)]',
        label: 'Critique',
        labelColor: 'text-[var(--color-danger-500)]',
    },
    HAUTE: {
        chip: 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-500)]',
        icon: Clock,
        text: 'text-[var(--color-fg)]',
        label: 'Prioritaire',
        labelColor: 'text-[var(--color-warning-500)]',
    },
    NORMALE: {
        chip: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)]',
        icon: FileText,
        text: 'text-[var(--color-fg)]',
        label: null,
        labelColor: '',
    },
    INFO: {
        chip: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]',
        icon: FileText,
        text: 'text-[var(--color-fg-muted)]',
        label: null,
        labelColor: '',
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

export default function NotificationBell() {
    const [open, setOpen] = useState(false);
    const panelRef = useRef(null);
    const btnRef = useRef(null);
    const navigate = useNavigate();
    const { notifications, totalNonLues, loading, marquerToutesLues, marquerUne } = useNotifications();

    useEffect(() => {
        if (!open) return;
        const handler = (e) => {
            if (
                panelRef.current && !panelRef.current.contains(e.target) &&
                btnRef.current && !btnRef.current.contains(e.target)
            ) {
                setOpen(false);
            }
        };
        document.addEventListener('mousedown', handler);
        return () => document.removeEventListener('mousedown', handler);
    }, [open]);

    const handleNotifClick = (notif) => {
        marquerUne(notif.idNotification);
        if (notif.lien) {
            navigate(notif.lien);
        }
        setOpen(false);
    };

    return (
        <div className="relative">
            <button
                ref={btnRef}
                id="btn-notifications"
                onClick={() => setOpen(v => !v)}
                className="relative p-2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors"
                aria-label="Notifications RH"
                aria-expanded={open}
            >
                <Bell size={19} strokeWidth={1.75} />
                {totalNonLues > 0 && (
                    <span className="absolute top-1 right-1 min-w-[16px] h-[16px] flex items-center justify-center text-[10px] font-semibold leading-none text-white bg-[var(--color-danger-500)] rounded-full px-1 ring-2 ring-[var(--color-surface)] font-data">
                        {totalNonLues > 99 ? '99+' : totalNonLues}
                    </span>
                )}
            </button>

            {open && (
                <div
                    ref={panelRef}
                    role="menu"
                    className="absolute right-0 top-full mt-2 w-[380px] max-h-[520px] flex flex-col z-50
                               bg-[var(--color-surface)] border border-[var(--color-border)]
                               shadow-lg ring-1 ring-black/5 rounded-none overflow-hidden"
                >
                    <div className="px-4 py-3 border-b border-[var(--color-border-subtle)] flex items-center justify-between shrink-0">
                        <div className="flex items-center gap-2">
                            <span className="text-[13px] font-semibold text-[var(--color-fg)] tracking-tight">
                                Alertes RH
                            </span>
                            {totalNonLues > 0 && (
                                <span className="text-[11px] font-medium text-[var(--color-fg-muted)] font-data">
                                    {totalNonLues} non lue{totalNonLues > 1 ? 's' : ''}
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-1">
                            {totalNonLues > 0 && (
                                <button
                                    onClick={marquerToutesLues}
                                    className="flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] font-medium px-1.5 py-1 rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors"
                                    title="Tout marquer comme lu"
                                >
                                    <CheckCheck size={13} strokeWidth={2} />
                                    Tout lire
                                </button>
                            )}
                            <button
                                onClick={() => setOpen(false)}
                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors"
                                aria-label="Fermer"
                            >
                                <X size={14} strokeWidth={2} />
                            </button>
                        </div>
                    </div>

                    <div className="flex-1 overflow-y-auto">
                        {loading && (
                            <div className="divide-y divide-[var(--color-border-subtle)]">
                                {[0, 1, 2].map((i) => (
                                    <div key={i} className="px-4 py-3 flex items-start gap-3">
                                        <div className="w-7 h-7 rounded-none bg-[var(--color-bg-subtle)] shrink-0 animate-pulse" />
                                        <div className="flex-1 min-w-0 space-y-2 pt-0.5">
                                            <div className="h-2.5 w-3/5 rounded-none bg-[var(--color-bg-subtle)] animate-pulse" />
                                            <div className="h-2 w-full rounded-none bg-[var(--color-bg-subtle)] animate-pulse" />
                                            <div className="h-2 w-1/3 rounded-none bg-[var(--color-bg-subtle)] animate-pulse" />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {!loading && notifications.length === 0 && (
                            <div className="p-8 text-center">
                                <div className="w-11 h-11 bg-[var(--color-bg-subtle)] rounded-full flex items-center justify-center mx-auto mb-3">
                                    <CheckCheck size={18} strokeWidth={1.75} className="text-[var(--color-fg-subtle)]" />
                                </div>
                                <p className="text-sm font-semibold text-[var(--color-fg)]">Aucune alerte en cours</p>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1">Toutes les actions RH sont à jour.</p>
                            </div>
                        )}

                        {!loading && notifications.map((notif) => {
                            const cfg = PRIORITE_CONFIG[notif.priorite] || PRIORITE_CONFIG.NORMALE;
                            const Icon = cfg.icon;
                            const nonLue = !notif.dateLecture;
                            return (
                                <button
                                    key={notif.idNotification}
                                    onClick={() => handleNotifClick(notif)}
                                    className={`w-full text-left px-4 py-3 border-b border-[var(--color-border-subtle)] last:border-b-0
                                                hover:bg-[var(--color-surface-hover)] group transition-colors rounded-none
                                                ${nonLue ? 'bg-[var(--color-brand-500)]/[0.04]' : ''}`}
                                >
                                    <div className="flex items-start gap-3">
                                        <div className={`w-7 h-7 rounded-none flex items-center justify-center shrink-0 ${cfg.chip}`}>
                                            <Icon size={14} strokeWidth={2} />
                                        </div>
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-1.5">
                                                {cfg.label && (
                                                    <span className={`text-[10px] font-semibold uppercase tracking-wide ${cfg.labelColor}`}>
                                                        {cfg.label}
                                                    </span>
                                                )}
                                                <p className={`text-[13px] font-semibold leading-tight truncate ${cfg.text}`}>
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
                                            {nonLue && <span className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-500)]" />}
                                            <ChevronRight size={13} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] transition-colors" />
                                        </div>
                                    </div>
                                </button>
                            );
                        })}
                    </div>

                    {notifications.length > 0 && (
                        <div className="px-4 py-2.5 border-t border-[var(--color-border-subtle)] shrink-0">
                            <button
                                onClick={() => { navigate('/'); setOpen(false); }}
                                className="flex items-center gap-1 text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] font-medium transition-colors rounded-none"
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