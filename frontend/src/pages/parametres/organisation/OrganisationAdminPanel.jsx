import { lazy, Suspense } from 'react';
import { Building2, Layers, Briefcase } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const FilialesSitesTab = lazy(() => import('@/components/parametres/structure/FilialesSitesTab'));
const DepartementsServicesTab = lazy(() => import('@/components/parametres/structure/DepartementsServicesTab'));
const FonctionsTab = lazy(() => import('@/components/parametres/structure/FonctionsTab'));

export default function OrganisationAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'filiales';

    const handleTabChange = (tabKey) => {
        setSearchParams({ tab: tabKey });
    };

    const mainTabs = [
        { id: 'filiales', label: 'Filiales & Sites', icon: <Building2 size={16} /> },
        { id: 'departements', label: 'Départements & Services', icon: <Layers size={16} /> },
        { id: 'fonctions', label: 'Postes & Fonctions', icon: <Briefcase size={16} /> }
    ];

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Organisation & Structure" description="Administration des sites, départements, services et postes." />

            <PageHeader
                title="Organisation & Structure"
                subtitle="Gestion centralisée des filiales, sites d'affectation, départements, services et fonctions."
                icon={Building2}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/' },
                    { label: 'Paramètres', href: '/parametres/organisation' },
                    { label: 'Organisation & Structure' }
                ]}
            />

            {/* Sélecteur d'onglets principal avec Tabs Design System et persistance URL */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden flex flex-col">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                {/* Contenu de l'onglet actif */}
                <div className="p-6">
                    <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                        {activeTab === 'filiales' && <FilialesSitesTab />}
                        {activeTab === 'departements' && <DepartementsServicesTab />}
                        {activeTab === 'fonctions' && <FonctionsTab />}
                    </Suspense>
                </div>
            </div>
        </div>
    );
}