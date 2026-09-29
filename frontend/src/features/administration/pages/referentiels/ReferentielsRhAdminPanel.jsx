import { lazy, Suspense } from 'react';
import { BookType, FileText, Award, FileCheck } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const CareerReferenceDataManagementTab = lazy(() => import('@/features/administration/components/reference-data/CareerReferenceDataManagementTab'));
const DocumentTypeManagementTab = lazy(() => import('@/features/administration/components/reference-data/DocumentTypeManagementTab'));
const ReferentielsRhTab = lazy(() => import('@/features/organization/components/structure/ReferentielsRhTab'));

export default function ReferentielsRhAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'carriere';

    const handleTabChange = (tabKey) => {
        setSearchParams({ tab: tabKey });
    };

    const mainTabs = [
        { id: 'carriere', label: 'Catégories Professionnelles', icon: <Award size={16} /> },
        { id: 'docTypes', label: 'Types de Documents', icon: <FileText size={16} /> },
        { id: 'rh', label: 'Autres Référentiels RH', icon: <FileCheck size={16} /> },
    ];

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Référentiels RH" description="Gestion des catégories socio-professionnelles, types de pièces et listes de référence." />

            <PageHeader
                title="Référentiels Métier & Documents"
                subtitle="Gestion des grilles catégorielles, pièces justificatives obligatoires et listes de référence RH."
                icon={BookType}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paramètres', href: '/admin/organisation' },
                    { label: 'Référentiels Métier & Documents' }
                ]}
            />

            <div className="space-y-4">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                    {activeTab === 'carriere' && <CareerReferenceDataManagementTab />}
                    {activeTab === 'docTypes' && <DocumentTypeManagementTab />}
                    {activeTab === 'rh' && <ReferentielsRhTab />}
                </Suspense>
            </div>
        </div>
    );
}
