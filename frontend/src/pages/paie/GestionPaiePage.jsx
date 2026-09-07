import { useState, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarRange, Banknote } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

// Lazy loading du composant des périodes de paie
const PeriodePaiePage = lazy(() => import('@/pages/paie/PeriodePaiePage'));

export default function GestionPaiePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'periodes';

    const handleTabChange = (tabId) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    const tabs = [
        {
            id: 'periodes',
            label: 'Périodes de Paie & Clôture',
            icon: CalendarRange
        }
    ];

    return (
        <div className="space-y-4 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-8">
            <SEO 
                title="Paie & Rémunération | GALAXAM SIRH" 
                description="Gestion des périodes de paie, validation des données de clôture et suivi de rémunération." 
            />

            <PageHeader
                title="Paie & Rémunération"
                subtitle="Gestion des périodes de paie, validation des clôtures de pointage et calcul des rémunérations."
                icon={Banknote}
            />

            {/* Navigation par Onglets */}
            <Tabs
                tabs={tabs}
                activeTab={activeTab}
                onChange={handleTabChange}
            />

            {/* Contenu fluide de l'onglet actif */}
            <div className="pt-2">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    {activeTab === 'periodes' && (
                        <PeriodePaiePage />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
