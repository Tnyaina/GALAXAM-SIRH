import { useDashboard } from '@/features/dashboard/hooks/useDashboard';
import DashboardHeader from '@/features/dashboard/components/layout/DashboardHeader';
import KpiGrid from '@/features/dashboard/components/views/KpiGrid';
import AbsentsWidget from '@/features/dashboard/components/views/widgets/AbsentsWidget';
import PlanningQuinzaineWidget from '@/features/dashboard/components/views/widgets/PlanningQuinzaineWidget';
import EcheancesWidget from '@/features/dashboard/components/views/widgets/EcheancesWidget';
import ActiviteRhWidget from '@/features/dashboard/components/views/widgets/ActiviteRhWidget';
import OperationalQueueWidget from '@/features/dashboard/components/views/widgets/OperationalQueueWidget';
import PayrollCycleWidget from '@/features/dashboard/components/views/widgets/PayrollCycleWidget';
import ModuleOverviewWidget from '@/features/dashboard/components/views/widgets/ModuleOverviewWidget';
import HasPermission from '@/features/auth/components/HasPermission';
import SEO from '@/components/system/SEO';

export default function Dashboard() {
    // Hook métier
    const { data, loading, error, refresh, lastRefresh } = useDashboard();

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

            <KpiGrid 
                loading={loading}
                kpis={kpis}
                actionsCount={actionsATraiter.reduce((sum, action) => sum + (Number(action.count) || 0), 0)}
                absentsAujourdhuiCount={absentsAujourdhui.length}
            />

            {/* Lecture opérationnelle : priorités puis continuité des cycles. */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 items-stretch">
                <OperationalQueueWidget loading={loading} actions={actionsATraiter} className="h-full" />
                <HasPermission authority="PAIE_READ"><PayrollCycleWidget className="h-full" /></HasPermission>

                {/* Anticipation des absences et disponibilité du jour. */}
                {(loading || congesQuinzaine.length > 0) && <PlanningQuinzaineWidget
                    loading={loading} 
                    absences={congesQuinzaine.length > 0 ? congesQuinzaine : absentsAujourdhui}
                    className="h-full"
                />}

                {(loading || absentsAujourdhui.length > 0) && <AbsentsWidget loading={loading} absentsAujourdhui={absentsAujourdhui} className="h-full" />}
                {(loading || totalAlertesContrats > 0 || alertes30j.length > 0) && <EcheancesWidget
                    loading={loading}
                    alertesCritiques={alertesCritiques}
                    alertes7j={alertes7j}
                    alertes30j={alertes30j}
                    totalAlertesContrats={totalAlertesContrats}
                    className="h-full"
                />}

            </div>

            <ModuleOverviewWidget
                kpis={kpis}
                demandesCount={demandesCongeAValider.length}
                absentsCount={absentsAujourdhui.length}
            />

            {/* Toujours présent : vide ou rempli, le journal reste à sa place en bas. */}
            <ActiviteRhWidget loading={loading} activite={activite} />
        </div>
    );
}
