import { lazy, Suspense } from 'react';
import { Building2, Layers, Briefcase } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const FilialesSitesTab = lazy(() => import('@/features/organization/components/structure/FilialesSitesTab'));
const DepartementsServicesTab = lazy(() => import('@/features/organization/components/structure/DepartementsServicesTab'));
const FonctionsTab = lazy(() => import('@/features/organization/components/structure/FonctionsTab'));

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
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Organisation & Structure" description="Administration des sites, départements, services et postes." />

            <PageHeader
                title="Organisation & Structure"
                subtitle="Gestion centralisée des filiales, sites d'affectation, départements, services et fonctions."
                icon={Building2}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paramètres', href: '/admin/organisation' },
                    { label: 'Organisation & Structure' }
                ]}
            />

            {/* Les onglets structurent la page ; les panneaux métier portent leurs propres cadres. */}
            <div className="space-y-4">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                    {activeTab === 'filiales' && <FilialesSitesTab />}
                    {activeTab === 'departements' && <DepartementsServicesTab />}
                    {activeTab === 'fonctions' && <FonctionsTab />}
                </Suspense>
            </div>
        </div>
    );
}
