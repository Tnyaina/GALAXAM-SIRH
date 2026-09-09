import { useSearchParams } from 'react-router-dom';
import { 
    CalendarDays, 
    AlertTriangle, 
    Layers, 
    CalendarCheck2, 
    Lock,
    ShieldCheck
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function PointageWorkflowNav({ activePeriode = null }) {
    const [searchParams, setSearchParams] = useSearchParams();
    const currentTab = searchParams.get('tab') || 'saisie';
    const { hasPermission } = useAuth();
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    const handleSelectTab = (tabId) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    const stCode = activePeriode?.statutCode || activePeriode?.statut_code;
    const isTransmis = stCode === 'A_VALIDER' || stCode === 'TRANSMIS_DAF';
    const isCloturee = stCode === 'CLOTUREE' || stCode === 'VALIDEE';

    // Onglets organisés par workflow : Saisie → Suivi → Anomalies → Synthèse → Arbitrage DAF
    const tabs = [
        {
            tab: 'saisie',
            label: 'Saisie Quotidienne',
            icon: CalendarDays,
            showAlways: true
        },
        {
            tab: 'suivi',
            label: 'Suivi de Période',
            icon: CalendarCheck2,
            showAlways: true
        },
        {
            tab: 'anomalies',
            label: 'Anomalies & Retards',
            icon: AlertTriangle,
            // Visible uniquement pour la DAF (contrôle global et réconciliation)
            showAlways: false
        },
        {
            tab: 'synthese',
            label: 'Synthèse & Clôture',
            icon: Layers,
            showAlways: true
        },
        {
            tab: 'validation',
            label: 'Validation DAF',
            icon: ShieldCheck,
            showAlways: false,
            badge: isTransmis ? 'À valider' : null
        }
    ].filter(t => t.showAlways || isDaf);

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs flex flex-wrap items-center justify-between gap-3 px-2 py-1.5">
            {/* 1. Onglets horizontaux fins & élégants */}
            <div className="flex items-center gap-1 overflow-x-auto">
                {tabs.map((t) => {
                    const Icon = t.icon;
                    const isActive = currentTab === t.tab;
                    return (
                        <button
                            key={t.tab}
                            type="button"
                            onClick={() => handleSelectTab(t.tab)}
                            className={`
                                flex items-center gap-2 px-3.5 py-1.5 text-xs font-semibold rounded-none transition-all cursor-pointer whitespace-nowrap
                                ${isActive 
                                    ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold border border-[var(--color-brand-500)]/30' 
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] border border-transparent'
                                }
                            `}
                        >
                            <Icon size={14} className={isActive ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-muted)]'} />
                            <span>{t.label}</span>
                            {t.badge && (
                                <span className="ml-1 px-1.5 py-0.2 text-[9px] font-bold uppercase bg-amber-500/20 text-amber-700 dark:text-amber-300 border border-amber-500/40">
                                    {t.badge}
                                </span>
                            )}
                        </button>
                    );
                })}
            </div>

            {/* 2. Statut compact & discret de la période active */}
            {activePeriode && (
                <div className="flex items-center gap-2.5 text-xs pr-2">
                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                        Période : <strong className="font-mono text-[var(--color-fg)]">{activePeriode.libelle || activePeriode.code}</strong>
                    </span>
                    <span className={`px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider border ${
                        isCloturee 
                            ? 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border)]' 
                            : isTransmis 
                            ? 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/30' 
                            : 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/30'
                    }`}>
                        {activePeriode.statutLibelle || activePeriode.statutCode || 'En cours'}
                    </span>
                    {isCloturee && (
                        <span className="flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)] hidden md:inline-flex">
                            <Lock size={12} />
                            (Lecture seule)
                        </span>
                    )}
                </div>
            )}
        </div>
    );
}

