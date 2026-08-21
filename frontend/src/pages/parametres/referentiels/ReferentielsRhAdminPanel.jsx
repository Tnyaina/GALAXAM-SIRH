import { lazy, Suspense } from 'react';
import { ArrowLeft, BookType, FileText, Award, FileCheck } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

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
        { id: 'rh', label: 'Autres Référentiels', icon: <FileCheck size={16} /> },
    ];

    return (
        <div className="space-y-6">
            <Link 
                to="/dashboard" 
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold "
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            <div className="bg-[var(--color-surface)] rounded-none shadow-sm border border-[var(--color-border-subtle)] p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-[var(--color-brand-500)] text-white rounded-none flex items-center justify-center shadow-md shrink-0">
                        <BookType size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-[var(--color-fg)]">Référentiels RH</h1>
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Gestion des catégories professionnelles, types de documents, et des listes de références.
                        </p>
                    </div>
                </div>
            </div>

            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
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
