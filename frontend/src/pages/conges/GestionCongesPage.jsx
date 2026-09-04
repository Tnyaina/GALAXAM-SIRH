import { useState, useEffect, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarDays } from 'lucide-react';
import congeService from '@/api/congeService';

// Composants UI du Design System Galaxam
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';

// Sous-composants Onglets
const SoldesOverviewTab     = lazy(() => import('@/components/conges/SoldesOverviewTab'));
const DemandesValidationTab = lazy(() => import('@/components/conges/DemandesValidationTab'));

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
    setLoading(true);
    setError(null);
    try {
      const [resDemandes, resRefs] = await Promise.all([
        congeService.getAllDemandes(),
        congeService.getAllReferentiels(),
      ]);
      const demandesList = Array.isArray(resDemandes) 
        ? resDemandes 
        : (resDemandes?.items || resDemandes?.content || resDemandes?.data || []);
      setDemandesGlobales(demandesList);
      setReferentiels(resRefs?.data || resRefs || null);
    } catch (err) {
      console.error('Erreur lors du chargement des congés globaux:', err);
      setError(err?.response?.data?.message || err?.userMessage || 'Impossible de récupérer les données des congés.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadGlobalData();
  }, []);

  const demandesList = Array.isArray(demandesGlobales) 
    ? demandesGlobales 
    : (demandesGlobales?.items || demandesGlobales?.content || demandesGlobales?.data || []);

  const nbEnAttente = demandesList.filter(
    (d) => Boolean(d.isEnAttente ?? (d.statutCode === 'EN_ATTENTE' || d.codeStatutDemande === 'EN_ATTENTE'))
  ).length;

  const tabsList = [
    {
      id: 'soldes',
      label: 'Soldes & Planning prévisionnel',
    },
    {
      id: 'demandes',
      label: 'Demandes & Validations',
      badge: nbEnAttente > 0 ? nbEnAttente : undefined,
    }
  ];

  return (
    <div className="space-y-5 max-w-[1440px] mx-auto w-full font-sans pb-12">
      <SEO title="Congés & Absences" description="Gestion des demandes de congés et soldes." />

      <PageHeader
        title="Gestion des Congés & Absences"
        subtitle="Supervision globale des absences, planning prévisionnel d'équipe et approbation des demandes."
        icon={CalendarDays}
        breadcrumbs={[
          { label: 'Tableau de bord', href: '/dashboard' },
          { label: 'Congés & Absences' }
        ]}
      />

      {error && (
        <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
      )}

      {/* Barre d'onglets sobre de premier niveau */}
      <div className="border-b border-[var(--color-border-subtle)]">
        <Tabs
          tabs={tabsList}
          activeTab={activeTab}
          onChange={handleTabChange}
          variant="underline"
        />
      </div>

      {/* Contenu fluide de l'onglet actif (sans sur-boîte étouffante) */}
      <div className="space-y-5">
        <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
          {activeTab === 'soldes' && (
            <SoldesOverviewTab
              loading={loading}
              error={error}
              demandesGlobales={demandesGlobales}
              referentiels={referentiels}
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
        </Suspense>
      </div>
    </div>
  );
}
