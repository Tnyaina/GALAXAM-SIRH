import { useDashboard } from '@/hooks/useDashboard';
import DashboardHeader from '@/components/dashboard/DashboardHeader';
import KpiGrid from '@/components/dashboard/KpiGrid';
import CongesAValiderWidget from '@/components/dashboard/widgets/CongesAValiderWidget';
import AbsentsWidget from '@/components/dashboard/widgets/AbsentsWidget';
import EcheancesWidget from '@/components/dashboard/widgets/EcheancesWidget';
import ActiviteRhWidget from '@/components/dashboard/widgets/ActiviteRhWidget';
import AccesRapidesWidget from '@/components/dashboard/widgets/AccesRapidesWidget';

export default function Dashboard() {
    const { data, loading, error, refresh, lastRefresh } = useDashboard();

    const kpis = data?.kpis;
    const alertesCritiques = data?.alertesCritiques || [];
    const alertes7j = data?.alertes7j || [];
    const alertes30j = data?.alertes30j || [];

    const activite = data?.activiteRecente || [];
    const absentsAujourdhui = data?.absentsAujourdhui || [];
    const demandesCongeAValider = data?.demandesCongeAValider || [];

    const totalAlertesContrats = alertesCritiques.length + alertes7j.length;

    return (
        <div className="space-y-6">
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
                demandesCongeAValiderCount={demandesCongeAValider.length}
                absentsAujourdhuiCount={absentsAujourdhui.length}
            />

            <div className="grid grid-cols-1 xl:grid-cols-2 gap-5">
                <CongesAValiderWidget 
                    loading={loading} 
                    demandesCongeAValider={demandesCongeAValider} 
                />
                <AbsentsWidget 
                    loading={loading} 
                    absentsAujourdhui={absentsAujourdhui} 
                />
            </div>

            <div className="grid grid-cols-1 xl:grid-cols-3 gap-5">
                <div className="xl:col-span-2 space-y-5">
                    <EcheancesWidget 
                        loading={loading}
                        alertesCritiques={alertesCritiques}
                        alertes7j={alertes7j}
                        alertes30j={alertes30j}
                        totalAlertesContrats={totalAlertesContrats}
                    />
                </div>
                <div className="space-y-5">
                    <ActiviteRhWidget 
                        loading={loading}
                        activite={activite}
                    />
                    <AccesRapidesWidget />
                </div>
            </div>
        </div>
    );
}