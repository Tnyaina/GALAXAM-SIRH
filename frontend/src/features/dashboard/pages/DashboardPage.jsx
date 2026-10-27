import { Link } from 'react-router-dom';
import { Building2, CalendarDays, ChevronRight, ClipboardCheck, Clock, Clock3, UserCheck, Users, WalletCards } from 'lucide-react';
import { useDashboard } from '@/features/dashboard/hooks/useDashboard';
import DashboardHeader from '@/features/dashboard/components/layout/DashboardHeader';
import StatCard from '@/components/ui/data-display/StatCard';
import { SkeletonStatCard } from '@/components/ui/feedback/Skeleton';
import { AbsentsWidget, ActiviteRhWidget, EcheancesWidget, OperationalQueueWidget, PayrollCycleWidget, PlanningQuinzaineWidget } from '@/features/dashboard/components/views/widgets/DashboardWidgets';
import HasPermission from '@/features/auth/components/HasPermission';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { canAccessRoleGatedRoute } from '@/utils/sidebarUtils';

export default function DashboardPage() {
    // Hook métier
    const { data, loading, error, refresh, lastRefresh } = useDashboard();
    const { hasPermission, user } = useAuth();

    // Données dérivées
    const kpis = data?.kpis;
    const alertesCritiques = data?.alertesCritiques || [];
    const alertes7j = data?.alertes7j || [];
    const alertes30j = data?.alertes30j || [];

    const activite = data?.activiteRecente || [];
    const absentsAujourdhui = data?.absentsAujourdhui || [];
    const congesQuinzaine = data?.congesQuinzaine || [];
    const demandesCongeAValider = data?.demandesCongeAValider || [];
    const actionsATraiter = data?.actionsATraiter || [];

    const totalAlertesContrats = alertesCritiques.length + alertes7j.length;
    const modules = [
        { icon: Building2, title: 'Organisation', text: 'Sites, structures et paramètres', to: '/admin/organisation', visible: hasPermission('ORGANISATION_WRITE') && canAccessRoleGatedRoute('/admin/organisation', user) },
        { icon: Users, title: 'Employés', text: `${kpis?.totalEffectifActif ?? 0} actif(s) · ${kpis?.contratsExpirant30j ?? 0} échéance(s)`, to: '/employes', visible: hasPermission('EMPLOYE_READ') },
        { icon: CalendarDays, title: 'Congés & absences', text: `${absentsAujourdhui.length} absent(s) · ${demandesCongeAValider.length} à valider`, to: '/conges', visible: hasPermission('CONGE_READ') },
        { icon: Clock3, title: 'Pointage', text: 'Saisie, anomalies et validation', to: '/pointages?tab=suivi', visible: hasPermission('POINTAGE_READ') },
        { icon: WalletCards, title: 'Paie', text: 'Cycle, variables et bulletins', to: '/paie', visible: hasPermission('PAIE_READ') },
    ].filter((module) => module.visible);

    // Rendu
    return (
        <div className="space-y-4 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Tableau de bord" description="Vue d'ensemble et indicateurs clés RH." />
            
            <DashboardHeader 
                loading={loading}
                error={error}
                refresh={refresh}
                lastRefresh={lastRefresh}
                demandesCongeAValiderCount={demandesCongeAValider.length}
                totalAlertesContrats={totalAlertesContrats}
            />

            {/* Indicateurs propres au tableau de bord */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                {loading ? Array.from({ length: 4 }).map((_, index) => <SkeletonStatCard key={index} />) : (
                    <>
                        <StatCard title="Effectif actif" value={kpis?.totalEffectifActif ?? '—'} subtext={`${kpis?.nouvellesEmbauchesMois ?? 0} arrivée(s) ce mois`} icon={<Users size={18} />} color="brand" />
                        <StatCard title="Actions à traiter" value={actionsATraiter.reduce((sum, action) => sum + (Number(action.count) || 0), 0)} subtext={actionsATraiter.length > 0 ? 'Ouvrir les priorités opérationnelles' : 'Aucune action en attente'} icon={<ClipboardCheck size={18} />} color={actionsATraiter.length > 0 ? 'warning' : 'brand'} />
                        <StatCard title="Absents ce jour" value={absentsAujourdhui.length} subtext="Congés validés en cours" icon={<UserCheck size={18} />} color={absentsAujourdhui.length > 0 ? 'info' : 'brand'} />
                        <StatCard title="Échéances à 30 jours" value={kpis?.contratsExpirant30j ?? '—'} subtext={`${kpis?.essaisEnCours ?? 0} période(s) d’essai en cours`} icon={<Clock size={18} />} color={kpis?.contratsExpirant30j > 0 ? 'warning' : 'brand'} />
                    </>
                )}
            </div>

            {/* Lecture opérationnelle : priorités puis continuité des cycles */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 items-stretch">
                <OperationalQueueWidget loading={loading} actions={actionsATraiter} className="h-full" />
                <HasPermission authority="PAIE_READ"><PayrollCycleWidget className="h-full" /></HasPermission>

                {/* Anticipation des absences et disponibilité du jour */}
                {(loading || congesQuinzaine.length > 0) && (
                    <PlanningQuinzaineWidget
                        loading={loading} 
                        absences={congesQuinzaine.length > 0 ? congesQuinzaine : absentsAujourdhui}
                        className="h-full"
                    />
                )}

                {(loading || absentsAujourdhui.length > 0) && (
                    <AbsentsWidget loading={loading} absentsAujourdhui={absentsAujourdhui} className="h-full" />
                )}

                {(loading || totalAlertesContrats > 0 || alertes30j.length > 0) && (
                    <EcheancesWidget
                        loading={loading}
                        alertesCritiques={alertesCritiques}
                        alertes7j={alertes7j}
                        alertes30j={alertes30j}
                        totalAlertesContrats={totalAlertesContrats}
                        className="h-full"
                    />
                )}
            </div>

            {modules.length > 0 && (
                <section>
                    <div className="mb-3">
                        <h2 className="text-sm font-bold text-[var(--color-fg)]">Domaines du GRH</h2>
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
            )}

            {/* Flux d'activité récent */}
            <ActiviteRhWidget loading={loading} activite={activite} />
        </div>
    );
}
