import { lazy, Suspense } from 'react';
import { Wrench, ShieldCheck, Gift, Clock, Sliders, Calendar, ArrowLeft, FileText, ShieldAlert } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';

const ParametresGlobauxTab = lazy(() => import('@/components/parametres/paie/ParametresGlobauxTab'));
const ParametresCongesTab = lazy(() => import('@/components/conges/ParametresCongesTab'));
const CalendrierFerieTab = lazy(() => import('@/components/parametres/paie/CalendrierFerieTab'));
const CotisationsIrsaTab = lazy(() => import('@/components/parametres/paie/CotisationsIrsaTab'));
const IndemnitesSitesTab = lazy(() => import('@/components/parametres/paie/IndemnitesSitesTab'));
const MajorationsParametresTab = lazy(() => import('@/components/parametres/paie/MajorationsParametresTab'));

// Composants de paramétrage Pointage
const ToleranceArriveeSiteTab = lazy(() => import('@/components/parametres/pointage/ToleranceArriveeSiteTab'));
const DerogationIndemnitesTab = lazy(() => import('@/components/parametres/pointage/DerogationIndemnitesTab'));

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
        activeModule === 'paie' ? 'parametres_globaux' : 
        activeModule === 'conges' ? 'regles_acquisition' :
        activeModule === 'pointage' ? 'parametres_globaux_pointage' :
        'parametres_globaux_systeme'
    );

    const handleModuleChange = (moduleId) => {
        // Définir l'onglet par défaut du nouveau module
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
        { id: 'parametres_globaux', label: 'Constantes (Paie)', icon: <Sliders size={16} /> },
        { id: 'calendrier', label: 'Calendrier & Fériés', icon: <Calendar size={16} /> },
        { id: 'cotisations_irsa', label: 'Cotisations & Barèmes', icon: <ShieldCheck size={16} /> },
        { id: 'indemnites_sites', label: 'Indemnités (Sites)', icon: <Gift size={16} /> },
        { id: 'majorations', label: 'Majorations & HS', icon: <Clock size={16} /> },
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
        { id: 'derogations_indemnites', label: 'Exclusions Indemnités', icon: <ShieldAlert size={16} /> },
    ];

    // Définition des sous-onglets pour le Système
    const generalTabs = [
        { id: 'parametres_globaux_systeme', label: 'Constantes Générales', icon: <Sliders size={16} /> },
    ];

    const currentTabs = activeModule === 'paie' 
        ? paieTabs 
        : activeModule === 'conges' 
        ? congesTabs 
        : activeModule === 'pointage'
        ? pointageTabs
        : generalTabs;

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
                        <Wrench size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-[var(--color-fg)]">Paramétrage des Modules</h1>
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Configuration spécifique par domaine métier (Paie, Congés, Pointage, etc.)
                        </p>
                    </div>
                </div>
            </div>

            {/* Niveau 1 : Sélection du Module (Pills / Boutons horizontaux) */}
            <div className="flex flex-wrap gap-2">
                {MODULES.map(mod => {
                    const isActive = activeModule === mod.id;
                    return (
                        <button
                            key={mod.id}
                            onClick={() => handleModuleChange(mod.id)}
                            className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium transition-colors border ${
                                isActive 
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-sm' 
                                : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)] hover:text-[var(--color-fg)]'
                            }`}
                        >
                            {mod.icon}
                            {mod.label}
                        </button>
                    );
                })}
            </div>

            {/* Niveau 2 : Sélecteur d'onglets principal avec persistance URL */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
                <Tabs 
                    tabs={currentTabs} 
                    activeTab={activeTab === 'majorations_params' ? 'majorations' : activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                {/* Contenu de l'onglet actif avec le SkeletonTable standardisé */}
                <div className="p-6">
                    <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
                        {activeModule === 'general' && (
                            <>
                                {activeTab === 'parametres_globaux_systeme' && <ParametresGlobauxTab fixedCategory="RH_GENERAL" />}
                            </>
                        )}

                        {activeModule === 'paie' && (
                            <>
                                {activeTab === 'parametres_globaux' && <ParametresGlobauxTab fixedCategory="PAIE" />}
                                {activeTab === 'calendrier' && <CalendrierFerieTab />}
                                {activeTab === 'cotisations_irsa' && <CotisationsIrsaTab />}
                                {activeTab === 'indemnites_sites' && <IndemnitesSitesTab />}
                                {(activeTab === 'majorations' || activeTab === 'majorations_params') && <MajorationsParametresTab />}
                            </>
                        )}

                        {activeModule === 'conges' && (
                            <>
                                {activeTab === 'parametres_globaux_conges' && <ParametresGlobauxTab fixedCategory="CONGES" />}
                                {activeTab === 'regles_acquisition' && <ParametresCongesTab />}
                            </>
                        )}

                        {activeModule === 'pointage' && (
                            <>
                                {activeTab === 'parametres_globaux_pointage' && <ParametresGlobauxTab fixedCategory="POINTAGE" />}
                                {activeTab === 'tolerance_pointage' && <ToleranceArriveeSiteTab />}
                                {activeTab === 'derogations_indemnites' && <DerogationIndemnitesTab />}
                            </>
                        )}
                    </Suspense>
                </div>
            </div>
        </div>
    );
}