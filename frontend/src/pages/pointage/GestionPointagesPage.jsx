import { lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Clock } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';

// Sous-composants Onglets en Lazy-loading pour une performance optimale
const PointageOverviewTab = lazy(() => import('@/components/pointage/PointageOverviewTab'));
const SaisieQuotidiennePage = lazy(() => import('@/pages/pointage/SaisieQuotidiennePage'));
const AnomaliesPointagePage = lazy(() => import('@/pages/pointage/AnomaliesPointagePage'));
const SynthesePointagePage = lazy(() => import('@/pages/pointage/SynthesePointagePage'));
const PeriodePaiePage = lazy(() => import('@/pages/pointage/PeriodePaiePage'));

export default function GestionPointagesPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'vue-globale';

    const { hasPermission } = useAuth();
    const canViewPeriodes = hasPermission('POINTAGE_VALIDATE');

    const handleTabChange = (tabId, extraParams = {}) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            Object.entries(extraParams).forEach(([k, v]) => {
                if (v !== undefined && v !== null) {
                    next.set(k, v);
                } else {
                    next.delete(k);
                }
            });
            return next;
        });
    };

    return (
        <div className="space-y-4 max-w-[1600px] mx-auto w-full font-sans pb-12">
            <SEO 
                title="Pointage & Présences | GALAXAM SIRH" 
                description="Cockpit de gestion des temps, badgeages, anomalies, majorations HS et clôture de paie." 
            />

            <PageHeader
                title="Pointage & Gestion des Temps"
                subtitle="Supervision de la présence, calcul des majorations (HS, nuit, repas), compensation des retards et clôture paie."
                icon={Clock}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Pointage & Présences' }
                ]}
            />

            {/* Contenu fluide de l'onglet actif */}
            <div className="pt-1">
                <Suspense fallback={<SkeletonTable rows={6} columns={6} />}>
                    {activeTab === 'vue-globale' && (
                        <PointageOverviewTab onTabChange={handleTabChange} />
                    )}

                    {activeTab === 'saisie' && (
                        <SaisieQuotidiennePage />
                    )}

                    {activeTab === 'anomalies' && (
                        <AnomaliesPointagePage />
                    )}

                    {activeTab === 'synthese' && (
                        <SynthesePointagePage />
                    )}

                    {activeTab === 'periodes' && canViewPeriodes && (
                        <PeriodePaiePage />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
