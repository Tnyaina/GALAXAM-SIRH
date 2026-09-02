import { useState, useEffect, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import periodePaieService from '@/services/periodePaieService';

// Sous-composants Onglets en Lazy-loading pour une performance optimale
const PointageOverviewTab = lazy(() => import('@/components/pointage/PointageOverviewTab'));
const SaisieQuotidiennePage = lazy(() => import('@/pages/pointage/SaisieQuotidiennePage'));
const AnomaliesPointagePage = lazy(() => import('@/pages/pointage/AnomaliesPointagePage'));
const SynthesePointagePage = lazy(() => import('@/pages/pointage/SynthesePointagePage'));
const PeriodePaiePage = lazy(() => import('@/pages/pointage/PeriodePaiePage'));

export default function GestionPointagesPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'saisie';
    const [activePeriode, setActivePeriode] = useState(null);

    const { hasPermission } = useAuth();
    const canViewPeriodes = hasPermission('POINTAGE_VALIDATE');

    useEffect(() => {
        const fetchActivePeriode = async () => {
            try {
                const list = await periodePaieService.getAllPeriodes();
                if (list && list.length > 0) {
                    const activeP = list.find(p => p.statutCode === 'EN_COURS' || p.statutCode === 'A_VALIDER') || list[0];
                    setActivePeriode(activeP);
                }
            } catch (err) {
                console.error('Erreur chargement période active:', err);
            }
        };
        fetchActivePeriode();
    }, [activeTab]);

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
        <div className="space-y-3 max-w-[1600px] mx-auto w-full font-sans pb-8">
            <SEO 
                title="Pointage & Présences | GALAXAM SIRH" 
                description="Cockpit de gestion des temps, badgeages, anomalies, majorations HS et clôture de paie." 
            />

            {/* Barre de navigation et statut */}
            <PointageWorkflowNav activePeriode={activePeriode} />

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
