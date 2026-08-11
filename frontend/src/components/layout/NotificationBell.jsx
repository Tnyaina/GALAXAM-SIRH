import { useState, useRef, useEffect } from 'react';
import { Bell, CheckCheck, X, ChevronRight, AlertTriangle, Clock, FileText } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useNotifications } from '@/hooks/useDashboard';

const PRIORITE_CONFIG = {
    CRITIQUE: {
        bg: 'bg-danger-500/10 border-l-2 border-danger-500',
        badge: 'bg-danger-500',
        icon: AlertTriangle,
        iconColor: 'text-danger-500',
        text: 'text-danger-700 dark:text-danger-400',
    },
    HAUTE: {
        bg: 'bg-warning-500/10 border-l-2 border-warning-500',
        badge: 'bg-warning-500',
        icon: Clock,
        iconColor: 'text-warning-500',
        text: 'text-warning-700 dark:text-warning-400',
    },
    NORMALE: {
        bg: 'bg-ink-50 dark:bg-ink-800/40 border-l-2 border-brand-400',
        badge: 'bg-brand-500',
        icon: FileText,
        iconColor: 'text-brand-500',
        text: 'text-ink-700 dark:text-ink-300',
    },
    INFO: {
        bg: 'bg-ink-50 dark:bg-ink-800/20 border-l-2 border-ink-300',
        badge: 'bg-ink-400',
        icon: FileText,
        iconColor: 'text-ink-400',
        text: 'text-ink-600 dark:text-ink-400',
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
                className="relative p-2 text-ink-400 hover:text-ink-700 dark:hover:text-ink-100 transition-colors rounded-sm hover:bg-ink-100 dark:hover:bg-ink-800"
                aria-label="Notifications RH"
            >
                <Bell size={20} />
                {totalNonLues > 0 && (
                    <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] flex items-center justify-center text-[10px] font-bold text-white bg-danger-500 rounded-full px-1 shadow-sm">
                        {totalNonLues > 99 ? '99+' : totalNonLues}
                    </span>
                )}
            </button>

            {/* Panel dropdown */}
            {open && (
                <div
                    ref={panelRef}
                    className="absolute right-0 top-full mt-2 w-[400px] max-h-[520px] flex flex-col z-50
                               bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700
                               shadow-2xl rounded-sm overflow-hidden"
                >
                    {/* Header du panel */}
                    <div className="px-4 py-3 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between bg-ink-50/80 dark:bg-ink-950/60 shrink-0">
                        <div className="flex items-center gap-2">
                            <Bell size={15} className="text-brand-500" />
                            <span className="text-sm font-bold text-ink-800 dark:text-white">
                                Alertes RH
                            </span>
                            {totalNonLues > 0 && (
                                <span className="text-xs font-semibold bg-danger-500 text-white px-1.5 py-0.5 rounded-full">
                                    {totalNonLues}
                                </span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            {totalNonLues > 0 && (
                                <button
                                    onClick={marquerToutesLues}
                                    className="flex items-center gap-1 text-xs text-brand-500 hover:text-brand-700 font-medium transition-colors"
                                    title="Tout marquer comme lu"
                                >
                                    <CheckCheck size={13} />
                                    Tout lire
                                </button>
                            )}
                            <button
                                onClick={() => setOpen(false)}
                                className="p-1 text-ink-400 hover:text-ink-700 rounded-sm hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors"
                            >
                                <X size={14} />
                            </button>
                        </div>
                    </div>

                    {/* Corps du panel */}
                    <div className="flex-1 overflow-y-auto">
                        {loading && (
                            <div className="p-6 text-center">
                                <div className="w-8 h-8 border-2 border-brand-500 border-t-transparent rounded-full animate-spin mx-auto mb-2" />
                                <p className="text-xs text-ink-400">Chargement des alertes...</p>
                            </div>
                        )}

                        {!loading && notifications.length === 0 && (
                            <div className="p-8 text-center">
                                <div className="w-12 h-12 bg-success-500/10 rounded-full flex items-center justify-center mx-auto mb-3">
                                    <CheckCheck size={20} className="text-success-500" />
                                </div>
                                <p className="text-sm font-semibold text-ink-700 dark:text-ink-200">Aucune alerte en cours</p>
                                <p className="text-xs text-ink-400 mt-1">Toutes les actions RH sont à jour.</p>
                            </div>
                        )}

                        {!loading && notifications.map((notif) => {
                            const cfg = PRIORITE_CONFIG[notif.priorite] || PRIORITE_CONFIG.NORMALE;
                            const Icon = cfg.icon;
                            return (
                                <button
                                    key={notif.idNotification}
                                    onClick={() => handleNotifClick(notif)}
                                    className={`w-full text-left px-4 py-3 ${cfg.bg} border-b border-ink-100 dark:border-ink-800 hover:brightness-95 dark:hover:brightness-110 transition-all group`}
                                >
                                    <div className="flex items-start gap-3">
                                        <Icon size={15} className={`${cfg.iconColor} mt-0.5 shrink-0`} />
                                        <div className="flex-1 min-w-0">
                                            <p className={`text-xs font-bold leading-tight ${cfg.text} truncate`}>
                                                {notif.titre}
                                            </p>
                                            <p className="text-xs text-ink-500 dark:text-ink-400 mt-0.5 leading-snug line-clamp-2">
                                                {notif.message}
                                            </p>
                                            <p className="text-[10px] text-ink-400 mt-1">
                                                {fmtRelative(notif.dateCreation)}
                                            </p>
                                        </div>
                                        <ChevronRight size={13} className="text-ink-300 group-hover:text-brand-500 transition-colors shrink-0 mt-0.5" />
                                    </div>
                                </button>
                            );
                        })}
                    </div>

                    {/* Footer */}
                    {notifications.length > 0 && (
                        <div className="px-4 py-2.5 border-t border-ink-150 dark:border-ink-700 bg-ink-50/80 dark:bg-ink-950/60 shrink-0">
                            <button
                                onClick={() => { navigate('/'); setOpen(false); }}
                                className="text-xs text-brand-500 hover:text-brand-700 font-medium transition-colors"
                            >
                                Voir toutes les alertes sur le dashboard →
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
