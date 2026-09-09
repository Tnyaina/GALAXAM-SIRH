import { useEffect, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import { useState } from 'react';
import periodePaieService from '@/services/periodePaieService';

// Sous-composants Onglets en Lazy-loading pour une performance optimale
const SaisieQuotidiennePage = lazy(() => import('@/pages/pointage/SaisieQuotidiennePage'));
const AnomaliesPointagePage = lazy(() => import('@/pages/pointage/AnomaliesPointagePage'));
const SynthesePointagePage = lazy(() => import('@/pages/pointage/SynthesePointagePage'));
const SuiviPeriodePage = lazy(() => import('@/pages/pointage/SuiviPeriodePage'));
const PointageValidationDafTab = lazy(() => import('@/pages/pointage/PointageValidationDafTab'));

const VALID_TABS = ['saisie', 'suivi', 'anomalies', 'synthese', 'validation'];

export default function GestionPointagesPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const rawTab = (searchParams.get('tab') || 'saisie').toLowerCase();
    const normalizedTab = rawTab === 'arbitrage' ? 'validation' : rawTab;
    const activeTab = VALID_TABS.includes(normalizedTab) ? normalizedTab : 'saisie';
    const [activePeriode, setActivePeriode] = useState(null);

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
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-8">
            <SEO 
                title="Pointage & Présences | GALAXAM SIRH" 
                description="Cockpit de gestion des temps, badgeages, anomalies, majorations HS et clôture de paie." 
            />

            {/* Barre de navigation et statut */}
            <PointageWorkflowNav activePeriode={activePeriode} />

            {/* Contenu fluide de l'onglet actif */}
            <div className="pt-1 w-full">
                <Suspense fallback={<SkeletonTable rows={6} columns={6} />}>
                    {activeTab === 'saisie' && (
                        <SaisieQuotidiennePage />
                    )}

                    {activeTab === 'suivi' && (
                        <SuiviPeriodePage activePeriode={activePeriode} onTabChange={handleTabChange} />
                    )}

                    {activeTab === 'anomalies' && (
                        <AnomaliesPointagePage />
                    )}

                    {activeTab === 'synthese' && (
                        <SynthesePointagePage />
                    )}

                    {activeTab === 'validation' && (
                        <PointageValidationDafTab />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
