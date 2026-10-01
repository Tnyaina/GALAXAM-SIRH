import { lazy, Suspense } from 'react';
import { BookType, FileText, Award, FileCheck } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import AdministrationSettingsView from '@/features/administration/components/navigation/AdministrationSettingsView';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

const CareerReferenceDataManagementTab = lazy(() => import('@/features/administration/components/reference-data/CareerReferenceDataManagementTab'));
const DocumentTypeManagementTab = lazy(() => import('@/features/administration/components/reference-data/DocumentTypeManagementTab'));
const ReferentielsRhTab = lazy(() => import('@/features/organization/components/structure/ReferentielsRhTab'));

export default function ReferentielsRhAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedTab = searchParams.get('tab') || 'carriere';

    const handleTabChange = (tabKey) => {
        setSearchParams(previous => { const next = new URLSearchParams(previous); next.set('tab', tabKey); return next; });
    };

    const mainTabs = [
        { id: 'carriere', label: 'Catégories', icon: <Award size={16} /> },
        { id: 'modes_calcul', label: 'Calcul du salaire', icon: <FileCheck size={16} /> },
        { id: 'motifs', label: 'Motifs de mouvements', icon: <FileCheck size={16} /> },
        { id: 'evenements', label: 'Événements RH', icon: <FileCheck size={16} /> },
        { id: 'docTypes', label: 'Documents', icon: <FileText size={16} /> },
        { id: 'rh', label: 'Contrats & intervenants', icon: <FileCheck size={16} /> },
    ];

    const activeTab = mainTabs.some(tab => tab.id === requestedTab) ? requestedTab : mainTabs[0].id;

    return (
        <div className="space-y-3 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full">
            <SEO title="Référentiels RH" description="Gestion des catégories socio-professionnelles, types de pièces et listes de référence." />

            <PageHeader
                title="Référentiels RH"
                subtitle="Gestion des grilles catégorielles, pièces justificatives obligatoires et listes de référence RH."
                icon={BookType}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paramètres', href: '/admin/organisation' },
                    { label: 'Référentiels RH' }
                ]}
            />

            <AdministrationSettingsView groups={[{ id: 'sections', label: 'Configuration', items: mainTabs }]} activeId={activeTab} onChange={handleTabChange}>
                <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                    {activeTab === 'carriere' && <CareerReferenceDataManagementTab section="categories" />}
                    {['modes_calcul', 'motifs', 'evenements'].includes(activeTab) && <CareerReferenceDataManagementTab key={activeTab} section={activeTab} />}
                    {activeTab === 'docTypes' && <DocumentTypeManagementTab />}
                    {activeTab === 'rh' && <ReferentielsRhTab />}
                </Suspense>
            </AdministrationSettingsView>
        </div>
    );
}
