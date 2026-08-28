import { lazy, Suspense } from 'react';
import { BookType, FileText, Award, FileCheck } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const RefCarriereManagementTab = lazy(() => import('@/components/parametres/referentiels/RefCarriereManagementTab'));
const DocumentTypeManagementTab = lazy(() => import('@/components/parametres/referentiels/DocumentTypeManagementTab'));
const ReferentielsRhTab = lazy(() => import('@/components/parametres/structure/ReferentielsRhTab'));

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
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Référentiels RH" description="Gestion des catégories socio-professionnelles, types de pièces et listes de référence." />

            <PageHeader
                title="Référentiels Métier & Documents"
                subtitle="Gestion des grilles catégorielles, pièces justificatives obligatoires et listes de référence RH."
                icon={BookType}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/' },
                    { label: 'Paramètres', href: '/parametres/referentiels' },
                    { label: 'Référentiels Métier & Documents' }
                ]}
            />

            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden flex flex-col">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                <div className="p-6">
                    <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                        {activeTab === 'carriere' && <RefCarriereManagementTab />}
                        {activeTab === 'docTypes' && <DocumentTypeManagementTab />}
                        {activeTab === 'rh' && <ReferentielsRhTab />}
                    </Suspense>
                </div>
            </div>
        </div>
    );
}
