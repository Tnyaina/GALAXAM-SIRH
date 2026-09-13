import { lazy, Suspense, useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { CalendarRange, Banknote, SlidersHorizontal, FileText } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import periodePaieService from '@/services/periodePaieService';

// Lazy loading des composants onglets
const CockpitPaieTab = lazy(() => import('@/components/paie/CockpitPaieTab'));
const VariablesPaieTab = lazy(() => import('@/components/paie/VariablesPaieTab'));
const PeriodePaiePage = lazy(() => import('@/pages/paie/PeriodePaiePage'));
const ExportsPaieTab = lazy(() => import('@/components/paie/ExportsPaieTab'));

export default function GestionPaiePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const rawTab = searchParams.get('tab') || 'cockpit';

    // Rétrocompatibilité : redirection de l'ancien onglet validation vers le cockpit Pointage
    useEffect(() => {
        if (rawTab === 'validation') {
            navigate('/pointages?tab=validation', { replace: true });
        }
    }, [rawTab, navigate]);

    const activeTab = rawTab === 'validation' ? 'cockpit' : rawTab;

    // État partagé des périodes de paie
    const [periodes, setPeriodes] = useState([]);
    const [idPeriodeActive, setIdPeriodeActive] = useState(null);
    const [loadingPeriodes, setLoadingPeriodes] = useState(false);

    const chargerPeriodes = async () => {
        setLoadingPeriodes(true);
        try {
            const data = await periodePaieService.getAllPeriodes();
            const liste = Array.isArray(data) ? data : data?.items || [];
            setPeriodes(liste);

            // Sélection par défaut : première période ouverte ou première de la liste
            if (liste.length > 0 && !idPeriodeActive) {
                const enCours = liste.find(p => p.codeStatut === 'EN_COURS' || p.codeStatut === 'OUVERTE' || p.statut === 'EN_COURS');
                const selected = enCours || liste[0];
                setIdPeriodeActive(selected.idPeriodePaie || selected.id_periode_paie);
            }
        } catch (err) {
            console.error('Erreur lors du chargement des périodes de paie :', err);
        } finally {
            setLoadingPeriodes(false);
        }
    };

    useEffect(() => {
        chargerPeriodes();
    }, []);

    const handleTabChange = (tabId) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    const periodeActiveObj = periodes.find(p => (p.idPeriodePaie || p.id_periode_paie) === idPeriodeActive);

    const tabs = [
        {
            id: 'cockpit',
            label: 'Cockpit Paie',
            icon: Banknote
        },
        {
            id: 'variables',
            label: 'Variables & Primes',
            icon: SlidersHorizontal
        },
        {
            id: 'periodes',
            label: 'Périodes de Paie',
            icon: CalendarRange
        },
        {
            id: 'exports',
            label: 'Déclarations & Exports',
            icon: FileText
        }
    ];

    return (
        <div className="space-y-4 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-8">
            <SEO 
                title="Cockpit Paie & Rémunération | GALAXAM SIRH"
                description="Cockpit DAF de calcul, validation de la paie, suivi des variables, déclarations SAFI 25 col et DNTS CNaPS."
            />

            <PageHeader
                title="Paie & Rémunération"
                subtitle="Cockpit de gestion de la paie, contrôle M-1, calculs légaux Madagascar (IRSA, CNaPS, FMFP, Santé) et exports réglementaires."
                icon={Banknote}
            />

            {/* Navigation par Onglets */}
            <Tabs
                tabs={tabs}
                activeTab={activeTab}
                onChange={handleTabChange}
            />

            {/* Contenu fluide de l'onglet actif */}
            <div className="pt-2">
                <Suspense fallback={<SkeletonTable rows={6} cols={6} />}>
                    {activeTab === 'cockpit' && (
                        <CockpitPaieTab
                            idPeriodeActive={idPeriodeActive}
                            setIdPeriodeActive={setIdPeriodeActive}
                            periodes={periodes}
                            onReloadPeriodes={chargerPeriodes}
                        />
                    )}
                    {activeTab === 'variables' && (
                        <VariablesPaieTab
                            idPeriodeActive={idPeriodeActive}
                        />
                    )}
                    {activeTab === 'periodes' && (
                        <PeriodePaiePage />
                    )}
                    {activeTab === 'exports' && (
                        <ExportsPaieTab
                            idPeriodeActive={idPeriodeActive}
                            codePeriode={periodeActiveObj?.code || ''}
                        />
                    )}
                </Suspense>
            </div>
        </div>
    );
}
