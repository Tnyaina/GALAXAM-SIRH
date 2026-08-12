import { useState, useRef, useEffect } from 'react';
import { Bell, CheckCheck, X, ChevronRight, AlertTriangle, Clock, FileText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '@/hooks/useDashboard';

const PRIORITE_CONFIG = {
    CRITIQUE: {
        chip: 'bg-danger-500/10 text-danger-500',
        icon: AlertTriangle,
        text: 'text-ink-800 dark:text-ink-100',
        label: 'Critique',
        labelColor: 'text-danger-600 dark:text-danger-400',
    },
    HAUTE: {
        chip: 'bg-warning-500/10 text-warning-500',
        icon: Clock,
        text: 'text-ink-800 dark:text-ink-100',
        label: 'Prioritaire',
        labelColor: 'text-warning-600 dark:text-warning-400',
    },
    NORMALE: {
        chip: 'bg-brand-500/10 text-brand-500',
        icon: FileText,
        text: 'text-ink-800 dark:text-ink-100',
        label: null,
        labelColor: '',
    },
    INFO: {
        chip: 'bg-ink-200/60 dark:bg-ink-700/60 text-ink-400',
        icon: FileText,
        text: 'text-ink-700 dark:text-ink-200',
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

    // Fermer le panel en cliquant à l'extérieur
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
            {/* Bouton Cloche */}
            <button
                ref={btnRef}
                id="btn-notifications"
                onClick={() => setOpen(v => !v)}
                className="relative p-2 text-ink-400 hover:text-ink-700 dark:hover:text-ink-100 rounded-md hover:bg-ink-100 dark:hover:bg-ink-800"
                aria-label="Notifications RH"
                aria-expanded={open}
            >
                <Bell size={19} strokeWidth={1.75} />
                {totalNonLues > 0 && (
                    <span className="absolute top-1 right-1 min-w-[16px] h-[16px] flex items-center justify-center text-[10px] font-semibold leading-none text-white bg-danger-500 rounded-full px-1 ring-2 ring-white dark:ring-ink-900">
                        {totalNonLues > 99 ? '99+' : totalNonLues}
                    </span>
                )}
            </button>

            {/* Panel dropdown */}
            {open && (
                <div
                    ref={panelRef}
                    role="menu"
                    className="absolute right-0 top-full mt-2 w-[380px] max-h-[520px] flex flex-col z-50
                               bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700
                               shadow-lg ring-1 ring-black/5 rounded-lg overflow-hidden"
                >
                    {/* Header du panel */}
                    <div className="px-4 py-3 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between shrink-0">
                        <div className="flex items-center gap-2">
                            <span className="text-[13px] font-semibold text-ink-800 dark:text-white tracking-tight">
                                Alertes RH
                            </span>
                            {totalNonLues > 0 && (
                                <span className="text-[11px] font-medium text-ink-500 dark:text-ink-400">
                                    {totalNonLues} non lue{totalNonLues > 1 ? 's' : ''}
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-1">
                            {totalNonLues > 0 && (
                                <button
                                    onClick={marquerToutesLues}
                                    className="flex items-center gap-1 text-[11px] text-ink-500 hover:text-brand-600 dark:text-ink-400 dark:hover:text-brand-400 font-medium px-1.5 py-1 rounded hover:bg-ink-100 dark:hover:bg-ink-800"
                                    title="Tout marquer comme lu"
                                >
                                    <CheckCheck size={13} strokeWidth={2} />
                                    Tout lire
                                </button>
                            )}
                            <button
                                onClick={() => setOpen(false)}
                                className="p-1 text-ink-400 hover:text-ink-700 dark:hover:text-ink-200 rounded hover:bg-ink-100 dark:hover:bg-ink-800"
                                aria-label="Fermer"
                            >
                                <X size={14} strokeWidth={2} />
                            </button>
                        </div>
                    </div>

                    {/* Corps du panel */}
                    <div className="flex-1 overflow-y-auto">
                        {loading && (
                            <div className="divide-y divide-ink-100 dark:divide-ink-800">
                                {[0, 1, 2].map((i) => (
                                    <div key={i} className="px-4 py-3 flex items-start gap-3">
                                        <div className="w-7 h-7 rounded-md bg-ink-100 dark:bg-ink-800 shrink-0" />
                                        <div className="flex-1 min-w-0 space-y-2 pt-0.5">
                                            <div className="h-2.5 w-3/5 rounded-full bg-ink-100 dark:bg-ink-800" />
                                            <div className="h-2 w-full rounded-full bg-ink-100 dark:bg-ink-800" />
                                            <div className="h-2 w-1/3 rounded-full bg-ink-100 dark:bg-ink-800" />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}

                        {!loading && notifications.length === 0 && (
                            <div className="p-8 text-center">
                                <div className="w-11 h-11 bg-ink-100 dark:bg-ink-800 rounded-full flex items-center justify-center mx-auto mb-3">
                                    <CheckCheck size={18} strokeWidth={1.75} className="text-ink-400" />
                                </div>
                                <p className="text-sm font-semibold text-ink-700 dark:text-ink-200">Aucune alerte en cours</p>
                                <p className="text-xs text-ink-400 mt-1">Toutes les actions RH sont à jour.</p>
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
                                    className={`w-full text-left px-4 py-3 border-b border-ink-100 dark:border-ink-800 last:border-b-0
                                                hover:bg-ink-50 dark:hover:bg-ink-800/50 group
                                                ${nonLue ? 'bg-brand-500/[0.03] dark:bg-brand-500/[0.04]' : ''}`}
                                >
                                    <div className="flex items-start gap-3">
                                        <div className={`w-7 h-7 rounded-md flex items-center justify-center shrink-0 ${cfg.chip}`}>
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
                                            <p className="text-xs text-ink-500 dark:text-ink-400 mt-1 leading-snug line-clamp-2">
                                                {notif.message}
                                            </p>
                                            <p className="text-[11px] text-ink-400 mt-1.5">
                                                {fmtRelative(notif.dateCreation)}
                                            </p>
                                        </div>
                                        <div className="flex flex-col items-end gap-1.5 shrink-0 pt-0.5">
                                            {nonLue && <span className="w-1.5 h-1.5 rounded-full bg-brand-500" />}
                                            <ChevronRight size={13} className="text-ink-300 dark:text-ink-600 group-hover:text-brand-500" />
                                        </div>
                                    </div>
                                </button>
                            );
                        })}
                    </div>

                    {/* Footer */}
                    {notifications.length > 0 && (
                        <div className="px-4 py-2.5 border-t border-ink-150 dark:border-ink-700 shrink-0">
                            <button
                                onClick={() => { navigate('/'); setOpen(false); }}
                                className="flex items-center gap-1 text-xs text-ink-600 hover:text-brand-600 dark:text-ink-300 dark:hover:text-brand-400 font-medium"
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