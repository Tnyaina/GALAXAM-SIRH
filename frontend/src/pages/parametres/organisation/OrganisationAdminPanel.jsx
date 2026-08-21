import { lazy, Suspense } from 'react';
import { Building2, Layers, Briefcase, ArrowLeft } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

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
        <div className="space-y-6">
            {/* Lien retour */}
            <Link 
                to="/dashboard" 
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold "
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête de la page */}
            <div className="bg-[var(--color-surface)] rounded-none shadow-sm border border-[var(--color-border-subtle)] p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-[var(--color-brand-500)] text-white rounded-none flex items-center justify-center shadow-md shrink-0">
                        <Building2 size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-[var(--color-fg)]">Organisation & Structure</h1>
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Gestion des filiales, départements, services et fonctions de l'entreprise.
                        </p>
                    </div>
                </div>
            </div>

            {/* Sélecteur d'onglets principal avec Tabs Design System et persistance URL */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
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