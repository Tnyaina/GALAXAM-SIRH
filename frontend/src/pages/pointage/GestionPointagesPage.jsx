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

// Redirection de l'ancien onglet vue-globale vers saisie (rétrocompatibilité)
const LEGACY_TAB_MAP = { 'vue-globale': 'saisie' };

export default function GestionPointagesPage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const rawTab = searchParams.get('tab') || 'saisie';
    // Résoudre les anciens onglets dépréciés
    const activeTab = LEGACY_TAB_MAP[rawTab] || rawTab;
    const [activePeriode, setActivePeriode] = useState(null);

    // Réécrire l'URL si un ancien onglet est détecté
    useEffect(() => {
        if (LEGACY_TAB_MAP[rawTab]) {
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('tab', LEGACY_TAB_MAP[rawTab]);
                return next;
            }, { replace: true });
        }
    }, [rawTab, setSearchParams]);

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
                </Suspense>
            </div>
        </div>
    );
}
