import { Link } from 'react-router-dom';
import { Building2, CalendarDays, ChevronRight, Clock3, Users, WalletCards } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

export default function ModuleOverviewWidget({ kpis = {}, demandesCount = 0, absentsCount = 0 }) {
    const { hasPermission } = useAuth();
    const modules = [
        { icon: Building2, title: 'Organisation', text: 'Sites, structures et paramètres', to: '/parametres/organisation', visible: hasPermission('ORGANISATION_READ') },
        { icon: Users, title: 'Employés', text: `${kpis.totalEffectifActif ?? 0} actif(s) · ${kpis.contratsExpirant30j ?? 0} échéance(s)`, to: '/employes', visible: hasPermission('EMPLOYE_READ') },
        { icon: CalendarDays, title: 'Congés & absences', text: `${absentsCount} absent(s) · ${demandesCount} à valider`, to: '/conges', visible: hasPermission('CONGE_READ') },
        { icon: Clock3, title: 'Pointage', text: 'Saisie, anomalies et validation', to: '/pointages?tab=suivi', visible: hasPermission('POINTAGE_READ') },
        { icon: WalletCards, title: 'Paie', text: 'Cycle, variables et bulletins', to: '/paie', visible: hasPermission('PAIE_READ') },
    ].filter((item) => item.visible);

    if (modules.length === 0) return null;
    return (
        <section>
            <div className="mb-3">
                <h2 className="text-sm font-bold text-[var(--color-fg)]">Domaines du SIRH</h2>
                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">Accès direct et état synthétique de chaque périmètre.</p>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-3">
                {modules.map(({ icon: Icon, title, text, to }) => (
                    <Link key={title} to={to} className="group min-h-28 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 hover:border-[var(--color-brand-500)] hover:shadow-xs transition-all flex flex-col">
                        <span className="w-8 h-8 flex items-center justify-center bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)]"><Icon size={17} /></span>
                        <span className="mt-3 text-xs font-bold text-[var(--color-fg)]">{title}</span>
                        <span className="mt-1 text-[11px] leading-relaxed text-[var(--color-fg-muted)]">{text}</span>
                        <ChevronRight size={14} className="mt-auto self-end text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-600)] group-hover:translate-x-0.5 transition-transform" />
                    </Link>
                ))}
            </div>
        </section>
    );
}
