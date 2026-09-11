import { lazy, Suspense } from 'react';
import { Wrench, ShieldCheck, Gift, Clock, Sliders, Calendar, FileText, Scale } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import PageHeader from '@/components/ui/navigation/PageHeader';
import SEO from '@/components/system/SEO';

const ParametresGlobauxTab = lazy(() => import('@/components/parametres/paie/ParametresGlobauxTab'));
const ParametresCongesTab = lazy(() => import('@/components/conges/ParametresCongesTab'));
const CalendrierFerieTab = lazy(() => import('@/components/parametres/paie/CalendrierFerieTab'));
const CotisationsIrsaTab = lazy(() => import('@/components/parametres/paie/CotisationsIrsaTab'));
const IndemnitesSitesTab = lazy(() => import('@/components/parametres/paie/IndemnitesSitesTab'));
const ReglementationFiscaleTab = lazy(() => import('@/components/parametres/paie/ReglementationFiscaleTab'));

// Composants de paramétrage Pointage
const ToleranceArriveeSiteTab = lazy(() => import('@/components/parametres/pointage/ToleranceArriveeSiteTab'));

const MODULES = [
    { id: 'general', label: 'Général & Système', icon: <Sliders size={18} /> },
    { id: 'paie', label: 'Paie & Rémunération', icon: <FileText size={18} /> },
    { id: 'conges', label: 'Congés & Absences', icon: <Calendar size={18} /> },
    { id: 'pointage', label: 'Pointage & Présence', icon: <Clock size={18} /> }
];

export default function ParametresModulesPanel() {
    const [searchParams, setSearchParams] = useSearchParams();
    
    // Niveau 1: Module
    const activeModule = searchParams.get('module') || 'paie';
    
    // Niveau 2: Sous-onglet
    const activeTab = searchParams.get('tab') || (
        activeModule === 'paie' ? 'reglementation_fiscale' : 
        activeModule === 'conges' ? 'regles_acquisition' :
        activeModule === 'pointage' ? 'parametres_globaux_pointage' :
        'parametres_globaux_systeme'
    );

    const handleModuleChange = (moduleId) => {
        let defaultTab = 'parametres_globaux';
        if (moduleId === 'conges') defaultTab = 'parametres_globaux_conges';
        else if (moduleId === 'pointage') defaultTab = 'parametres_globaux_pointage';
        else if (moduleId === 'general') defaultTab = 'parametres_globaux_systeme';
        
        setSearchParams({ module: moduleId, tab: defaultTab });
    };

    const handleTabChange = (tabKey) => {
        setSearchParams({ module: activeModule, tab: tabKey });
    };

    // Définition des sous-onglets pour la Paie
    const paieTabs = [
        { id: 'reglementation_fiscale', label: 'Cadre Réglementaire & IRSA', icon: <Scale size={16} /> },
        { id: 'cotisations_irsa', label: 'Cotisations Sociales & Sites', icon: <ShieldCheck size={16} /> },
        { id: 'indemnites_sites', label: 'Indemnités (Sites)', icon: <Gift size={16} /> },
        { id: 'calendrier', label: 'Calendrier & Fériés', icon: <Calendar size={16} /> },
    ];

    // Définition des sous-onglets pour les Congés
    const congesTabs = [
        { id: 'parametres_globaux_conges', label: 'Constantes (Congés)', icon: <Sliders size={16} /> },
        { id: 'regles_acquisition', label: 'Types & Motifs', icon: <Gift size={16} /> },
    ];

    // Définition des sous-onglets pour le Pointage
    const pointageTabs = [
        { id: 'parametres_globaux_pointage', label: 'Constantes (Pointage)', icon: <Sliders size={16} /> },
        { id: 'tolerance_pointage', label: 'Tolérances d\'Arrivée (Sites)', icon: <Clock size={16} /> },
    ];

    // Définition des sous-onglets pour le Système
    const generalTabs = [
        { id: 'parametres_globaux_systeme', label: 'Constantes Générales', icon: <Sliders size={16} /> },
        { id: 'parametres_globaux_contrats', label: 'Règles Contrats & CDD', icon: <FileText size={16} /> },
    ];

    const currentTabs = activeModule === 'paie' 
        ? paieTabs 
        : activeModule === 'conges' 
        ? congesTabs 
        : activeModule === 'pointage'
        ? pointageTabs
        : generalTabs;

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Paramétrage des Modules" description="Configuration des barèmes, cotisations, majorations et règles métier." />

            <PageHeader
                title="Paramétrage des Modules Métier"
                subtitle="Configuration technique des barèmes de paie, règles de congés, tolérances de pointage et constantes système."
                icon={Wrench}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paramètres', href: '/admin/organisation' },
                    { label: 'Paramétrage des Modules' }
                ]}
            />

            {/* Niveau 1 : Sélection du Module (Pills / Boutons horizontaux compacts) */}
            <div className="flex flex-wrap gap-1.5">
                {MODULES.map(mod => {
                    const isActive = activeModule === mod.id;
                    return (
                        <button
                            key={mod.id}
                            onClick={() => handleModuleChange(mod.id)}
                            className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold transition-colors border ${
                                isActive 
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-xs' 
                                : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)] hover:text-[var(--color-fg)]'
                            }`}
                        >
                            {mod.icon}
                            {mod.label}
                        </button>
                    );
                })}
            </div>

            {/* Niveau 2 : Sélecteur d'onglets principal avec persistance URL */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden flex flex-col">
                <Tabs 
                    tabs={currentTabs} 
                    activeTab={activeTab === 'majorations_params' ? 'majorations' : activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                {/* Contenu de l'onglet actif avec le SkeletonTable standardisé */}
                <div className="p-4 sm:p-4.5">
                    <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                        {activeModule === 'general' && (
                            <>
                                {activeTab === 'parametres_globaux_systeme' && <ParametresGlobauxTab fixedCategory="RH_GENERAL" />}
                                {activeTab === 'parametres_globaux_contrats' && <ParametresGlobauxTab fixedCategory="CONTRAT" />}
                            </>
                        )}

                        {activeModule === 'paie' && (
                            <>
                                {activeTab === 'reglementation_fiscale' && <ReglementationFiscaleTab />}
                                {activeTab === 'calendrier' && <CalendrierFerieTab />}
                                {activeTab === 'cotisations_irsa' && <CotisationsIrsaTab />}
                                {activeTab === 'indemnites_sites' && <IndemnitesSitesTab />}
                                {/* Compatibilité ascendante : les anciens onglets 'majorations' et 'parametres_globaux' redirigent vers reglementation */}
                                {(activeTab === 'majorations' || activeTab === 'majorations_params' || activeTab === 'parametres_globaux') && <ReglementationFiscaleTab />}
                            </>
                        )}

                        {activeModule === 'conges' && (
                            <>
                                {activeTab === 'parametres_globaux_conges' && <ParametresGlobauxTab fixedCategory="CONGE" />}
                                {activeTab === 'regles_acquisition' && <ParametresCongesTab />}
                            </>
                        )}

                        {activeModule === 'pointage' && (
                            <>
                                {activeTab === 'parametres_globaux_pointage' && <ParametresGlobauxTab fixedCategory="POINTAGE" />}
                                {activeTab === 'tolerance_pointage' && <ToleranceArriveeSiteTab />}
                            </>
                        )}
                    </Suspense>
                </div>
            </div>
        </div>
    );
}