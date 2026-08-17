import { lazy, Suspense } from 'react';
import { Wrench, ShieldCheck, Gift, Clock, Sliders, Calendar, ArrowLeft } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';

const CotisationsIrsaTab = lazy(() => import('@/components/parametres/paie/CotisationsIrsaTab'));
const IndemnitesSitesTab = lazy(() => import('@/components/parametres/paie/IndemnitesSitesTab'));
const MajorationsParametresTab = lazy(() => import('@/components/parametres/paie/MajorationsParametresTab'));
const ParametresGlobauxTab = lazy(() => import('@/components/parametres/paie/ParametresGlobauxTab'));
const CalendrierFerieTab = lazy(() => import('@/components/parametres/paie/CalendrierFerieTab'));

export default function PaieConfigAdminPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'cotisations_irsa';

    const handleTabChange = (tabKey) => {
        setSearchParams({ tab: tabKey });
    };

    const mainTabs = [
        { id: 'cotisations_irsa', label: 'Cotisations & Barème IRSA', icon: <ShieldCheck size={16} /> },
        { id: 'indemnites_sites', label: 'Indemnités & Tarifs par Site', icon: <Gift size={16} /> },
        { id: 'majorations', label: 'Majorations (Heures Supp)', icon: <Clock size={16} /> },
        { id: 'parametres_globaux', label: 'Paramètres Globaux', icon: <Sliders size={16} /> },
        { id: 'calendrier', label: 'Calendrier Jours Fériés & Chômés', icon: <Calendar size={16} /> },
    ];

    return (
        <div className="space-y-6">
            {/* Lien retour */}
            <Link 
                to="/dashboard" 
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold transition-all"
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête de la page */}
            <div className="bg-[var(--color-surface)] rounded-none shadow-sm border border-[var(--color-border-subtle)] p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-[var(--color-brand-500)] text-white rounded-none flex items-center justify-center shadow-md shrink-0">
                        <Wrench size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-[var(--color-fg)]">Paramétrage Paie & Barèmes Fiscaux</h1>
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Gestion des cotisations sociales, du barème IRSA, des indemnités par site, des majorations, des paramètres globaux et du calendrier des jours fériés.
                        </p>
                    </div>
                </div>
            </div>

            {/* Sélecteur d'onglets principal avec persistance URL & Composant Tabs réutilisable */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab === 'majorations_params' ? 'majorations' : activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                {/* Contenu de l'onglet actif avec le SkeletonTable standardisé */}
                <div className="p-6">
                    <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                        {activeTab === 'cotisations_irsa' && <CotisationsIrsaTab />}
                        {activeTab === 'indemnites_sites' && <IndemnitesSitesTab />}
                        {(activeTab === 'majorations' || activeTab === 'majorations_params') && <MajorationsParametresTab />}
                        {activeTab === 'parametres_globaux' && <ParametresGlobauxTab />}
                        {activeTab === 'calendrier' && <CalendrierFerieTab />}
                    </Suspense>
                </div>
            </div>
        </div>
    );
}