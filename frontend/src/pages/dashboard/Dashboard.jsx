import { useDashboard } from '@/hooks/useDashboard';
import DashboardHeader from '@/components/dashboard/DashboardHeader';
import KpiGrid from '@/components/dashboard/KpiGrid';
import CongesAValiderWidget from '@/components/dashboard/widgets/CongesAValiderWidget';
import AbsentsWidget from '@/components/dashboard/widgets/AbsentsWidget';
import PlanningQuinzaineWidget from '@/components/dashboard/widgets/PlanningQuinzaineWidget';
import BrouillonsEnAttenteWidget from '@/components/dashboard/widgets/BrouillonsEnAttenteWidget';
import EcheancesWidget from '@/components/dashboard/widgets/EcheancesWidget';
import ActiviteRhWidget from '@/components/dashboard/widgets/ActiviteRhWidget';
import AccesRapidesWidget from '@/components/dashboard/widgets/AccesRapidesWidget';
import SEO from '@/components/system/SEO';

export default function Dashboard() {
    const { data, loading, error, refresh, lastRefresh } = useDashboard();

    const kpis = data?.kpis;
    const alertesCritiques = data?.alertesCritiques || [];
    const alertes7j = data?.alertes7j || [];
    const alertes30j = data?.alertes30j || [];

    const activite = data?.activiteRecente || [];
    const absentsAujourdhui = data?.absentsAujourdhui || [];
    const congesQuinzaine = data?.congesQuinzaine || [];
    const demandesCongeAValider = data?.demandesCongeAValider || [];
    const brouillons = data?.brouillons || [];

    const totalAlertesContrats = alertesCritiques.length + alertes7j.length;

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
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
                demandesCongeAValiderCount={demandesCongeAValider.length}
                absentsAujourdhuiCount={absentsAujourdhui.length}
            />

            {/* Layout principal équilibré : Grille 2 colonnes parfaitement alignée */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-stretch">
                {/* Ligne 1 : Congés à valider & Salariés absents */}
                <CongesAValiderWidget 
                    loading={loading} 
                    demandesCongeAValider={demandesCongeAValider}
                    className="h-full"
                />
                <AbsentsWidget 
                    loading={loading} 
                    absentsAujourdhui={absentsAujourdhui}
                    className="h-full"
                />

                {/* Ligne 2 : Échéances contrats/essais & Supervision planning quinzaine */}
                <EcheancesWidget 
                    loading={loading}
                    alertesCritiques={alertesCritiques}
                    alertes7j={alertes7j}
                    alertes30j={alertes30j}
                    totalAlertesContrats={totalAlertesContrats}
                    className="h-full"
                />
                <PlanningQuinzaineWidget 
                    loading={loading}
                    absences={congesQuinzaine.length > 0 ? congesQuinzaine : absentsAujourdhui}
                    className="h-full"
                />

                {/* Ligne 3 : Brouillons en attente & Accès rapides */}
                <BrouillonsEnAttenteWidget
                    loading={loading}
                    brouillons={brouillons}
                    className="h-full"
                />
                <AccesRapidesWidget 
                    className="h-full"
                />

                {/* Activité récente (pleine largeur en bas si présente) */}
                {activite.length > 0 && (
                    <div className="lg:col-span-2">
                        <ActiviteRhWidget 
                            loading={loading}
                            activite={activite}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}