import { lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarRange, Banknote, ClipboardCheck, SlidersHorizontal, FileText } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

// Lazy loading des composants onglets
const PeriodePaiePage = lazy(() => import('@/pages/paie/PeriodePaiePage'));
const PointageValidationDafTab = lazy(() => import('@/pages/paie/PointageValidationDafTab'));

// Stubs temporaires pour les onglets en cours de développement
const VariablesPaieStub = () => (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center text-sm text-[var(--color-fg-muted)]">
        <SlidersHorizontal size={32} className="mx-auto mb-3 text-[var(--color-fg-subtle)] opacity-50" />
        <p className="font-semibold">Variables & Ajustements</p>
        <p className="text-xs mt-1">Ce module est en cours de développement.</p>
    </div>
);

const BulletinsPaieStub = () => (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center text-sm text-[var(--color-fg-muted)]">
        <FileText size={32} className="mx-auto mb-3 text-[var(--color-fg-subtle)] opacity-50" />
        <p className="font-semibold">Bulletins de Paie</p>
        <p className="text-xs mt-1">La génération des bulletins sera disponible après la clôture de la période.</p>
    </div>
);

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
            label: 'Périodes de Paie',
            icon: CalendarRange
        },
        {
            id: 'validation',
            label: 'Pointage & Validation',
            icon: ClipboardCheck
        },
        {
            id: 'variables',
            label: 'Variables',
            icon: SlidersHorizontal
        },
        {
            id: 'bulletins',
            label: 'Bulletins de Paie',
            icon: FileText
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
                    {activeTab === 'validation' && (
                        <PointageValidationDafTab />
                    )}
                    {activeTab === 'variables' && (
                        <VariablesPaieStub />
                    )}
                    {activeTab === 'bulletins' && (
                        <BulletinsPaieStub />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
