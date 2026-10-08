import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CalendarDays, FileSpreadsheet, CheckCircle2, Circle, Clock3 } from 'lucide-react';
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
const PeriodesReferenceTab  = lazy(() => import('@/features/leave/components/PeriodesReferenceTab'));

export default function GestionCongesPage() {
  const { hasPermission } = useAuth();
  // Contexte et navigation
  const [searchParams, setSearchParams] = useSearchParams();

  // État local
  const [nbEnAttente, setNbEnAttente] = useState(null);
  const [referentiels, setReferentiels] = useState(null);
  const [periodesEnAttente, setPeriodesEnAttente] = useState([]);
  const [error, setError] = useState(null);
  const [showGrandRecap, setShowGrandRecap] = useState(false);
  const [dateFin, setDateFin] = useState(() => fmtDateInput(new Date()));
  const [dateDebut, setDateDebut] = useState(() => `${new Date().getFullYear()}-01-01`);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const requestSequence = useRef(0);

  // Données dérivées
  const tabFromUrl = searchParams.get('tab');
  const activeTab = ['demandes', 'periodes'].includes(tabFromUrl) ? tabFromUrl : 'planning';
  const nbTauxAActiver = periodesEnAttente?.length || 0;
  const tabsList = [
    { id: 'planning', label: 'Planning des absences' },
    { id: 'demandes', label: 'Demandes et décisions', badge: nbEnAttente > 0 ? nbEnAttente : undefined },
    { id: 'periodes', label: 'Contrôle des droits', badge: nbTauxAActiver > 0 ? nbTauxAActiver : undefined }
  ];

  const leaveCycleAtControl = activeTab === 'periodes';

  // Effets
  const loadGlobalData = useCallback(async () => {
    const requestId = ++requestSequence.current;
    setError(null);
    const needsReferentiels = activeTab !== 'periodes';
    const [pendingResult, refsResult, pendingRatesResult] = await Promise.allSettled([
        congeService.getAllDemandes({ statut: 'EN_ATTENTE', page: 1, size: 1 }),
        needsReferentiels ? congeService.getAllReferentiels() : Promise.resolve(null),
        congeService.getPeriodesEnAttenteActivation(),
    ]);
    if (requestId !== requestSequence.current) return;
    setNbEnAttente(pendingResult.status === 'fulfilled' ? pendingResult.value?.totalElements ?? null : null);
    if (needsReferentiels) {
      setReferentiels(refsResult.status === 'fulfilled' ? refsResult.value?.data || refsResult.value || null : null);
    }
    const pendingRates = pendingRatesResult.status === 'fulfilled' && Array.isArray(pendingRatesResult.value)
      ? pendingRatesResult.value : [];
    setPeriodesEnAttente(pendingRates);
    const failed = [pendingResult, refsResult].find(result => result.status === 'rejected');
    if (failed) setError(getErrorMessage(failed.reason, 'Certaines données des congés sont indisponibles.'));
  }, [activeTab]);

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

      {/* Indicateur informatif du cycle : la navigation entre les vues reste dans les onglets. */}
      <section aria-label="Cycle de gestion des congés" className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-4 py-3">
        <div className="grid grid-cols-1 md:grid-cols-[1fr_auto_1fr_auto_1fr] items-center gap-3">
          <div aria-current={!leaveCycleAtControl ? 'step' : undefined} className="flex items-center gap-2">
            {!leaveCycleAtControl
              ? <CheckCircle2 size={17} className="shrink-0 text-[var(--color-brand-600)]" aria-hidden="true" />
              : <Circle size={17} className="shrink-0 text-[var(--color-fg-muted)]" aria-hidden="true" />}
            <div>
              <p className={`text-xs font-semibold ${!leaveCycleAtControl ? 'text-[var(--color-brand-700)]' : 'text-[var(--color-fg-muted)]'}`}>Demandes & planning</p>
              <p className="text-[11px] text-[var(--color-fg-muted)]">{nbEnAttente > 0 ? `${nbEnAttente} demande(s) en attente` : 'Gestion au fil de l’eau'}</p>
            </div>
          </div>
          <span className="hidden md:block h-px bg-[var(--color-border)]" aria-hidden="true" />
          <div className="flex items-center gap-2">
            <Clock3 size={17} className="shrink-0 text-[var(--color-info-600)]" aria-hidden="true" />
            <div>
              <p className="text-xs font-semibold text-[var(--color-fg)]">Acquisition mensuelle <span className="ml-1 text-[10px] uppercase tracking-wide text-[var(--color-info-700)]">Automatique</span></p>
              <p className="text-[11px] text-[var(--color-fg-muted)]">Après validation de la paie</p>
            </div>
          </div>
          <span className="hidden md:block h-px bg-[var(--color-border)]" aria-hidden="true" />
          <div aria-current={leaveCycleAtControl ? 'step' : undefined} className="flex items-center gap-2">
            {leaveCycleAtControl
              ? <CheckCircle2 size={17} className="shrink-0 text-[var(--color-brand-600)]" aria-hidden="true" />
              : <Circle size={17} className="shrink-0 text-[var(--color-fg-muted)]" aria-hidden="true" />}
            <div>
              <p className={`text-xs font-semibold ${leaveCycleAtControl ? 'text-[var(--color-brand-700)]' : 'text-[var(--color-fg-muted)]'}`}>Contrôle annuel DAF</p>
              <p className="text-[11px] text-[var(--color-fg-muted)]">Contrôle des droits</p>
            </div>
          </div>
        </div>
      </section>

      {/* Barre d'onglets sobre de premier niveau */}
      <Tabs
        tabs={tabsList}
        activeTab={activeTab}
        onChange={handleTabChange}
        variant="underline"
      />

      <Modal isOpen={showGrandRecap} onClose={() => setShowGrandRecap(false)} title="Grand récapitulatif des congés" size="lg">
        <div className="space-y-4">
          <p className="text-sm text-[var(--color-fg-muted)]">
            Solde avant la période, mouvements entre les deux dates et valeur du stock
            restant calculée à partir des lots historiques au dernier jour choisi.
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

          {activeTab === 'periodes' && (
            <PeriodesReferenceTab
              initialIdEmploye={Number(searchParams.get('employe')) || null}
              canValidate={hasPermission('CONGE_VALIDATE')}
              periodesEnAttente={periodesEnAttente}
              onReload={loadGlobalData}
            />
          )}
        </Suspense>
      </div>
    </div>
  );
}
