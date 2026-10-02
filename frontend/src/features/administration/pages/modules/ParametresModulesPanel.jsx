import { lazy, Suspense } from 'react';
import { Wrench, ShieldCheck, Gift, Clock, Sliders, Calendar, FileText, Scale, CreditCard } from 'lucide-react';
import { Navigate, useSearchParams } from 'react-router-dom';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import AdministrationSettingsView from '@/features/administration/components/navigation/AdministrationSettingsView';
import PageHeader from '@/components/ui/navigation/PageHeader';
import SEO from '@/components/system/SEO';

const ParametresGlobauxTab = lazy(() => import('@/features/administration/components/payroll/ParametresGlobauxTab'));
const ParametresCongesTab = lazy(() => import('@/features/leave/components/ParametresCongesTab'));
const CalendrierFerieTab = lazy(() => import('@/features/administration/components/payroll/CalendrierFerieTab'));
const CotisationsIrsaTab = lazy(() => import('@/features/administration/components/payroll/CotisationsIrsaTab'));
const IndemnitesSitesTab = lazy(() => import('@/features/administration/components/payroll/IndemnitesSitesTab'));
const ReglementationFiscaleTab = lazy(() => import('@/features/administration/components/payroll/ReglementationFiscaleTab'));
const ModesPaiementTab = lazy(() => import('@/features/administration/components/payroll/ModesPaiementTab'));

const MODULES = [
    { id: 'general', label: 'Règles communes', icon: <Sliders size={18} /> },
    { id: 'paie', label: 'Paie', icon: <FileText size={18} /> },
    { id: 'conges', label: 'Congés', icon: <Calendar size={18} /> },
    { id: 'pointage', label: 'Pointage', icon: <Clock size={18} /> },
    { id: 'calendrier', label: 'Calendrier', icon: <Calendar size={18} /> }
];

export default function ParametresModulesPanel() {
    const [searchParams, setSearchParams] = useSearchParams();

    // Niveau 1: Module
    const requestedModule = searchParams.get('module');
    const activeModule = searchParams.get('tab') === 'calendrier' ? 'calendrier' : MODULES.some(module => module.id === requestedModule) ? requestedModule : 'paie';

    // Niveau 2: Sous-onglet
    const requestedTab = searchParams.get('tab') || (
        activeModule === 'paie' ? 'reglementation_fiscale' :
        activeModule === 'conges' ? 'regles_acquisition' :
        activeModule === 'pointage' ? 'parametres_globaux_pointage' :
        activeModule === 'calendrier' ? 'calendrier' :
        'parametres_globaux_systeme'
    );

    const handleModuleChange = (moduleId) => {
        let defaultTab = 'reglementation_fiscale';
        if (moduleId === 'conges') defaultTab = 'parametres_globaux_conges';
        else if (moduleId === 'pointage') defaultTab = 'parametres_globaux_pointage';
        else if (moduleId === 'general') defaultTab = 'parametres_globaux_systeme';

        if (moduleId === 'calendrier') defaultTab = 'calendrier';
        setSearchParams(previous => { const next = new URLSearchParams(previous); next.set('module', moduleId); next.set('tab', previous.get(`${moduleId}_tab`) || defaultTab); next.delete('subtab'); return next; });
    };

    const handleTabChange = (tabKey) => {
        setSearchParams(previous => {
            const next = new URLSearchParams(previous);
            next.set('module', activeModule); next.set('tab', tabKey); next.set(`${activeModule}_tab`, tabKey);
            next.delete('subtab');
            return next;
        });
    };

    const paieGroups = [
        { id: 'fiscalite', label: 'Fiscalité & cotisations', items: [
            { id: 'reglementation_fiscale', label: 'Cadre réglementaire & IRSA', icon: <Scale size={16} /> },
            { id: 'cotisations_irsa', label: 'Cotisations & organismes', icon: <ShieldCheck size={16} /> },
        ] },
        { id: 'primes', label: 'Primes & indemnités', items: [
            { id: 'parametres_globaux_paie', label: 'Règles & barèmes de paie', icon: <Sliders size={16} /> },
            { id: 'indemnites_sites', label: 'Indemnités par site', icon: <Gift size={16} /> },
        ] },
        { id: 'paiement', label: 'Paiement', items: [
            { id: 'modes_paiement', label: 'Modes de paiement', icon: <CreditCard size={16} /> },
        ] },
    ];

    // Définition des sous-onglets pour les Congés
    const congesTabs = [
        { id: 'parametres_globaux_conges', label: 'Constantes (Congés)', icon: <Sliders size={16} /> },
        { id: 'regles_acquisition', label: 'Types & Motifs', icon: <Gift size={16} /> },
    ];

    // Définition des sous-onglets pour le Pointage
    const pointageTabs = [
        { id: 'parametres_globaux_pointage', label: 'Constantes (Pointage)', icon: <Sliders size={16} /> },
    ];

    // Définition des sous-onglets pour le Système
    const generalTabs = [
        { id: 'parametres_globaux_systeme', label: 'Constantes Générales', icon: <Sliders size={16} /> },
        { id: 'parametres_globaux_contrats', label: 'Règles Contrats & CDD', icon: <FileText size={16} /> },
    ];

    const currentTabs = activeModule === 'paie'
        ? paieGroups.flatMap(group => group.items)
        : activeModule === 'conges'
        ? congesTabs
        : activeModule === 'pointage'
        ? pointageTabs
        : activeModule === 'calendrier' ? [{ id: 'calendrier', label: 'Calendrier & jours fériés', icon: <Calendar size={16} /> }] : generalTabs;

    const canonicalTab = { majorations: 'reglementation_fiscale', majorations_params: 'reglementation_fiscale', parametres_globaux: 'parametres_globaux_paie', tolerance_pointage: 'parametres_globaux_pointage' }[requestedTab] || requestedTab;
    const activeTab = currentTabs.some(tab => tab.id === canonicalTab) ? canonicalTab : currentTabs[0].id;

    const legacyConfigurationTab = {
        rubriques_variables: 'rubriques', types_variables: 'types', regles_calcul: 'regles',
    }[requestedTab];
    if (activeModule === 'paie' && legacyConfigurationTab) {
        const next = new URLSearchParams(searchParams);
        next.delete('module'); next.delete('tab'); next.set('vue', legacyConfigurationTab);
        return <Navigate to={`/paie/configuration?${next.toString()}`} replace />;
    }

    return (
        <div className="flex-1 min-h-0 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full flex flex-col overflow-hidden">
            <SEO title="Paramétrage des Modules" description="Configuration des barèmes, cotisations, majorations et règles métier." />

            <PageHeader
                className="shrink-0 mb-3.5"
                title="Règles métier"
                subtitle="Choisissez un domaine, puis la règle ou le barème à configurer."
                icon={Wrench}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Paramètres', href: '/admin/organisation' },
                    { label: 'Règles métier' }
                ]}
            />

            <AdministrationSettingsView
                groups={[{ id: 'domaines', label: 'Domaines métier', items: MODULES }]}
                activeId={activeModule} onChange={handleModuleChange}
                contained scrollContent={false}
            >
                <div className="flex min-h-0 flex-1 flex-col overflow-hidden">
                    <AdministrationSettingsView
                        groups={activeModule === 'paie' ? paieGroups : [{ id: activeModule, label: 'Configuration', items: currentTabs }]}
                        activeId={activeTab === 'majorations_params' || activeTab === 'majorations' ? 'reglementation_fiscale' : activeTab === 'parametres_globaux' ? 'parametres_globaux_paie' : activeTab}
                        onChange={handleTabChange} contained
                    >
                {/* Contenu de l'onglet actif avec le SkeletonTable standardisé */}
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
                                {(activeTab === 'parametres_globaux_paie' || activeTab === 'parametres_globaux') && (
                                    <ParametresGlobauxTab fixedCategory="PAIE" />
                                )}
                                {activeTab === 'modes_paiement' && <ModesPaiementTab />}
                                {activeTab === 'calendrier' && <CalendrierFerieTab />}
                                {activeTab === 'cotisations_irsa' && <CotisationsIrsaTab />}
                                {activeTab === 'indemnites_sites' && <IndemnitesSitesTab />}
                                {/* Compatibilité ascendante : les anciens onglets 'majorations' et 'majorations_params' redirigent vers reglementation */}
                                {(activeTab === 'majorations' || activeTab === 'majorations_params') && <ReglementationFiscaleTab />}
                            </>
                        )}

                        {activeModule === 'conges' && (
                            <>
                                {activeTab === 'parametres_globaux_conges' && <ParametresGlobauxTab fixedCategory="CONGE" />}
                                {activeTab === 'regles_acquisition' && <ParametresCongesTab />}
                            </>
                        )}

                        {activeModule === 'calendrier' && <CalendrierFerieTab />}
                        {activeModule === 'pointage' && (
                            <>
                                {(activeTab === 'parametres_globaux_pointage' || activeTab === 'tolerance_pointage') && (
                                    <ParametresGlobauxTab fixedCategory="POINTAGE" />
                                )}
                            </>
                        )}
                </Suspense>
                    </AdministrationSettingsView>
                </div>
            </AdministrationSettingsView>
        </div>
    );
}
