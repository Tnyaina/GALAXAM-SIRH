import { useState, useEffect, lazy, Suspense } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { CalendarDays, ArrowLeft } from 'lucide-react';
import congeService from '@/api/congeService';

// Composants UI du Design System Galaxam
import Tabs from '@/components/ui/navigation/Tabs';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

// Sous-composants Onglets
const SoldesOverviewTab       = lazy(() => import('@/components/conges/SoldesOverviewTab'));
const DemandesValidationTab   = lazy(() => import('@/components/conges/DemandesValidationTab'));
const ParametresCongesTab     = lazy(() => import('@/components/conges/ParametresCongesTab'));

export default function GestionCongesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get('tab') || 'soldes';

  const handleTabChange = (tabId) => {
    setSearchParams({ tab: tabId });
  };

  // Data states
  const [demandesGlobales, setDemandesGlobales] = useState([]);
  const [referentiels, setReferentiels]         = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);

  const loadGlobalData = async () => {
    try {
      setLoading(true);
      setError(null);

      const [globalesRes, refRes] = await Promise.all([
        congeService.getAllDemandes().catch(() => []),
        congeService.getAllReferentiels().catch(() => null),
      ]);
      setDemandesGlobales(globalesRes || []);
      setReferentiels(refRes);

    } catch (err) {
      setError(err?.response?.data || err.message || 'Erreur lors du chargement des donnees.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadGlobalData();
  }, []);

  const nbEnAttente = demandesGlobales.filter(d => d.codeStatutDemande === 'EN_ATTENTE').length;

  const tabsList = [
    {
      id: 'soldes',
      label: 'Planning & Vue d\'ensemble',
    },
    {
      id: 'demandes',
      label: 'Demandes & Validation',
      badge: nbEnAttente > 0 ? nbEnAttente : undefined,
    },
    {
      id: 'parametres',
      label: 'Parametres',
    },
  ];

  return (
    <div className="space-y-6 font-sans animate-fade-in">
      {/* Lien retour */}
      <Link 
        to="/dashboard" 
        className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold transition-all"
      >
        <ArrowLeft size={16} />
        Retour au Tableau de bord
      </Link>

      {/* En-tête de la page (Design Premium sans animations/arrondis) */}
      <div className="relative overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-sm p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
        {/* Motif décoratif arrière-plan */}
        <div className="absolute top-0 right-0 w-64 h-64 bg-[var(--color-brand-500)]/5 rounded-full blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />
        
        <div className="relative flex items-center gap-5">
          <div className="w-14 h-14 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-lg shadow-[var(--color-brand-500)]/20 shrink-0">
            <CalendarDays size={26} strokeWidth={2.5} />
          </div>
          <div>
            <h1 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-fg)] tracking-tight">Gestion des Conges & Absences</h1>
            <p className="text-sm text-[var(--color-fg-muted)] mt-1 font-medium">
              Supervision globale, planning des equipes et traitement des demandes.
            </p>
          </div>
        </div>
      </div>

      {error && (
         <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
      )}

      {/* Sélecteur d'onglets principal avec Tabs Design System et persistance URL */}
      <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden flex flex-col">
        <Tabs
          tabs={tabsList}
          activeTab={activeTab}
          onChange={handleTabChange}
          variant="underline"
        />

        {/* Contenu de l'onglet actif */}
        <div className="p-6">
          <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
            {activeTab === 'soldes' && (
              <SoldesOverviewTab
                loading={loading}
                error={error}
                demandesGlobales={demandesGlobales}
                onReload={loadGlobalData}
              />
            )}

            {activeTab === 'demandes' && (
              <DemandesValidationTab
                loading={loading}
                demandes={demandesGlobales}
                referentiels={referentiels}
                onReload={loadGlobalData}
              />
            )}

            {activeTab === 'parametres' && (
              <ParametresCongesTab />
            )}
          </Suspense>
        </div>
      </div>
    </div>
  );
}
