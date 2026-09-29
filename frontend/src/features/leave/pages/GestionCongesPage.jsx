import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarDays, FileSpreadsheet } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';

// Composants UI du Design System Galaxam
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import { useAuth } from '@/context/AuthContext';
import { fmtDateInput } from '@/utils/dateUtils';

// Sous-composants Onglets
const SoldesOverviewTab     = lazy(() => import('@/features/leave/components/SoldesOverviewTab'));
const DemandesValidationTab = lazy(() => import('@/features/leave/components/DemandesValidationTab'));

export default function GestionCongesPage() {
  const { hasPermission } = useAuth();
  // Contexte et navigation
  const [searchParams, setSearchParams] = useSearchParams();

  // État local
  const [nbEnAttente, setNbEnAttente] = useState(null);
  const [referentiels, setReferentiels]         = useState(null);
  const [error, setError]     = useState(null);
  const [showGrandRecap, setShowGrandRecap] = useState(false);
  const [dateFin, setDateFin] = useState(() => fmtDateInput(new Date()));
  const [dateDebut, setDateDebut] = useState(() => `${new Date().getFullYear()}-01-01`);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const requestSequence = useRef(0);

  // Données dérivées
  const tabFromUrl = searchParams.get('tab');
  const activeTab = tabFromUrl === 'demandes' ? 'demandes' : 'planning';
  const tabsList = [
    { id: 'planning', label: 'Planning des absences' },
    { id: 'demandes', label: 'Demandes et décisions', badge: nbEnAttente > 0 ? nbEnAttente : undefined }
  ];

  // Effets
  const loadGlobalData = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setError(null);
    const [pendingResult, refsResult] = await Promise.allSettled([
        congeService.getAllDemandes({ statut: 'EN_ATTENTE', page: 1, size: 1 }),
        congeService.getAllReferentiels(),
    ]);
    if (requestId !== requestSequence.current) return;
    setNbEnAttente(pendingResult.status === 'fulfilled' ? pendingResult.value?.totalElements ?? null : null);
    setReferentiels(refsResult.status === 'fulfilled' ? refsResult.value?.data || refsResult.value || null : null);
    const failed = [pendingResult, refsResult].find(result => result.status === 'rejected');
    if (failed) setError(getErrorMessage(failed.reason, 'Certaines données des congés sont indisponibles.'));
  }, []);

  useEffect(() => {
    loadGlobalData();
    return () => { requestSequence.current += 1; };
  }, [loadGlobalData]);

  // Actions
  const handleTabChange = (tabId) => {
    setSearchParams(previous => {
      const next = new URLSearchParams(previous);
      next.set('tab', tabId);
      return next;
    });
  };

  const handleGrandRecap = async () => {
    setExporting(true);
    setExportError(null);
    try {
      await congeService.telechargerGrandRecapCongesExcel(dateDebut, dateFin);
      setShowGrandRecap(false);
    } catch (err) {
      setExportError(getErrorMessage(err, 'Impossible de générer le récapitulatif annuel.'));
    } finally {
      setExporting(false);
    }
  };

  // Rendu
  return (
    <div className="space-y-4 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans pb-8">
      <SEO title="Congés et absences" description="Planning des absences et traitement des demandes." />

      <PageHeader
        title="Congés et absences"
        subtitle="Planifier les absences de l'équipe et traiter les demandes."
        icon={CalendarDays}
        actions={hasPermission('CONGE_READ') && (
          <Button variant="outline" size="sm" iconLeft={<FileSpreadsheet size={15} />}
            onClick={() => { setExportError(null); setShowGrandRecap(true); }}>
            Grand récapitulatif Excel
          </Button>
        )}
        breadcrumbs={[
          { label: 'Tableau de bord', href: '/dashboard' },
          { label: 'Congés & Absences' }
        ]}
      />

      {error && (
        <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
      )}

      {/* Barre d'onglets sobre de premier niveau */}
      <Tabs
        tabs={tabsList}
        activeTab={activeTab}
        onChange={handleTabChange}
        variant="underline"
      />

      <Modal isOpen={showGrandRecap} onClose={() => setShowGrandRecap(false)} title="Grand récapitulatif des congés">
        <div className="space-y-4 p-4">
          <p className="text-sm text-[var(--color-fg-muted)]">
            Solde avant la période, mouvements entre les deux dates et situation valorisée
            au TCONGE applicable à la date de fin.
          </p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Input label="Début de la période" type="date" value={dateDebut}
              max={dateFin} required onChange={(event) => setDateDebut(event.target.value)} />
            <Input label="Fin de la période" type="date" value={dateFin}
              min={dateDebut} max={fmtDateInput(new Date())} required
              onChange={(event) => setDateFin(event.target.value)} />
          </div>
          {exportError && <Alert type="error" message={exportError} />}
          <div className="flex flex-wrap justify-end gap-2 border-t border-[var(--color-border-subtle)] pt-4">
            <Button variant="outline" onClick={() => setShowGrandRecap(false)} disabled={exporting}>Annuler</Button>
            <Button iconLeft={<FileSpreadsheet size={15} />} loading={exporting}
              disabled={!dateDebut || !dateFin || dateFin < dateDebut || dateFin > fmtDateInput(new Date())}
              onClick={handleGrandRecap}>
              Télécharger Excel
            </Button>
          </div>
        </div>
      </Modal>

      {/* Contenu fluide de l'onglet actif (sans sur-boîte étouffante) */}
      <div className="space-y-5">
        <Suspense fallback={<SkeletonTable rows={5} columns={5} />}>
          {activeTab === 'planning' && (
            <SoldesOverviewTab
              nbEnAttente={nbEnAttente}
              referentiels={referentiels}
              onReload={loadGlobalData}
              onOpenDemandes={() => handleTabChange('demandes')}
            />
          )}

          {activeTab === 'demandes' && (
            <DemandesValidationTab
              referentiels={referentiels}
              onReload={loadGlobalData}
            />
          )}
        </Suspense>
      </div>
    </div>
  );
}
