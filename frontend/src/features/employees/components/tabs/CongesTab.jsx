import { useState, useEffect, lazy, Suspense } from 'react';
import { useNavigate } from 'react-router-dom';
import { Calendar, Plus, CalendarDays, ExternalLink, SlidersHorizontal, Eye, Search, Download, MoreHorizontal } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Alert from '@/components/ui/feedback/Alert';
import Loading from '@/components/ui/feedback/Loading';
import { Card } from '@/components/ui/data-display/Card';
import Tabs from '@/components/ui/navigation/Tabs';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Pagination from '@/components/ui/navigation/Pagination';
import { useToast } from '@/components/ui/feedback/Toast';
import Input from '@/components/ui/forms/Input';

import SoldeCongeWidget from '@/features/leave/components/SoldeCongeWidget';
import MouvementsSoldeTable from '@/features/leave/components/MouvementsSoldeTable';

const DemandeCongeModal = lazy(() => import('@/features/leave/components/DemandeCongeModal'));
const DemandeDetailsModal = lazy(() => import('@/features/leave/components/DemandeDetailsModal'));
const InitialisationSoldeModal = lazy(() => import('@/features/leave/components/InitialisationSoldeModal'));
const ExportEtatCongesModal = lazy(() => import('@/features/leave/components/ExportEtatCongesModal'));
const CongeExpirationReviewCard = lazy(() => import('@/features/leave/components/CongeExpirationReviewCard'));

const formatDateOnly = (dateString) => {
  if (!dateString) return '—';
  return new Date(dateString).toLocaleDateString('fr-FR');
};

export default function CongesTab({ idEmploye, employe, canWrite = false, canValidateConges = false }) {
  const navigate = useNavigate();
  const toast = useToast();

  // Contexte : données et actions congés du collaborateur.
  const [soldeEmploye, setSoldeEmploye] = useState(null);
  const [situationData, setSituationData] = useState(null);
  const [mouvements, setMouvements] = useState([]);
  const [demandesEmploye, setDemandesEmploye] = useState([]);
  const [totalDemandes, setTotalDemandes] = useState(0);
  const [consommationPool, setConsommationPool] = useState([]);
  const [referentiels, setReferentiels] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [demandesError, setDemandesError] = useState(null);
  const [showDemandeModal, setShowDemandeModal] = useState(false);
  const [showInitModal, setShowInitModal] = useState(false);
  const [showExportModal, setShowExportModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [detailDemande, setDetailDemande] = useState(null);
  const [activeSubTab, setActiveSubTab] = useState('demandes'); // 'demandes' ou 'registre'

  // Filtres demandes
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [demandesLoading, setDemandesLoading] = useState(true);
  const [refreshDemandes, setRefreshDemandes] = useState(0);

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Confirm Dialog for Annulation
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [confirmAnnulerState, setConfirmAnnulerState] = useState({ isOpen: false, idDemande: null });

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [soldeRes, situationRes, mvtRes, poolRes, refRes] = await Promise.all([
        congeService.getSoldeEmploye(idEmploye).catch(() => null),
        congeService.getSituationEmploye(idEmploye).catch(() => null),
        congeService.getMouvementsEmploye(idEmploye).catch(() => []),
        congeService.getConsommationPool(idEmploye).catch(() => []),
        congeService.getAllReferentiels().catch(() => null),
      ]);
      setSoldeEmploye(soldeRes);
      setSituationData(situationRes);
      setMouvements(mvtRes || []);
      setConsommationPool(poolRes || []);
      setReferentiels(refRes);
      if (!soldeRes && !situationRes) setError('Situation des congés indisponible. Réessayez plus tard.');
    } catch (err) {
      setError(getErrorMessage(err, "Erreur de chargement des congés de l'employé."));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (idEmploye) loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idEmploye]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchQuery.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  useEffect(() => {
    if (!idEmploye) return;
    let cancelled = false;
    setDemandesLoading(true);
    congeService.getDemandesEmploye(idEmploye, {
      page: currentPage, size: pageSize,
      ...(statusFilter !== 'ALL' ? { statut: statusFilter } : {}),
      ...(debouncedSearch ? { q: debouncedSearch } : {}),
    }).then((res) => {
      if (cancelled) return;
      setDemandesEmploye(res?.content || []);
      setTotalDemandes(res?.totalElements || 0);
      setDemandesError(null);
    }).catch((err) => {
      if (cancelled) return;
      setDemandesEmploye([]);
      setTotalDemandes(0);
      setDemandesError(getErrorMessage(err, 'Erreur de chargement des demandes de congé.'));
    }).finally(() => { if (!cancelled) setDemandesLoading(false); });
    return () => { cancelled = true; };
  }, [idEmploye, currentPage, pageSize, statusFilter, debouncedSearch, refreshDemandes]);

  const reloadData = () => { loadData(); setRefreshDemandes((version) => version + 1); };

  const handleAnnulerConfirm = async () => {
    if (confirmAnnulerState.idDemande) {
      try {
        await congeService.annulerDemande(confirmAnnulerState.idDemande);
        toast.success('Demande de congé annulée avec succès.');
        reloadData();
      } catch (err) {
        const msg = getErrorMessage(err, 'Erreur lors de l\'annulation.');
        setError(msg);
        toast.error(msg);
      } finally {
        setConfirmAnnulerState({ isOpen: false, idDemande: null });
      }
    }
  };

  const handleExportDemandesCsv = () => {
    if (!demandesEmploye.length) return;
    const headers = ['Type d\'absence', 'Statut', 'Date Début', 'Date Fin', 'Demi-journée', 'Jours Décomptés', 'Commentaire', 'Motif Refus'];
    const rows = demandesEmploye.map((d) => [
      `"${(d.libelleTypeConge || d.codeTypeConge || '').replace(/"/g, '""')}"`,
      `"${(d.libelleStatutDemande || d.codeStatutDemande || '').replace(/"/g, '""')}"`,
      formatDateOnly(d.dateDebut),
      formatDateOnly(d.dateFin),
      d.estDemiJournee ? (d.momentDemiJournee === 'MATIN' ? 'Matinée' : 'Après-midi') : 'Non',
      d.nbJoursDecomptes ?? 0,
      `"${(d.commentaire || '').replace(/"/g, '""')}"`,
      `"${(d.motifRefus || '').replace(/"/g, '""')}"`
    ]);
    const csvContent = [headers.join(';'), ...rows.map(r => r.join(';'))].join('\r\n');
    const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const fileSuffix = employe?.matricule ? `_${employe.matricule}` : idEmploye ? `_${idEmploye}` : '';
    const dateStr = new Date().toISOString().slice(0, 10);
    a.download = `demandes_conges${fileSuffix}_${dateStr}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Page affichée exportée en CSV.');
  };

  const changeStatus = (status) => { setStatusFilter(status); setCurrentPage(1); setDemandesLoading(true); };
  const changeSearch = (value) => { setSearchQuery(value); setCurrentPage(1); setDemandesLoading(true); };

  if (loading && !soldeEmploye && !situationData) {
    return (
      <div className="p-8 flex justify-center">
        <Loading size="md" label="Chargement des congés du collaborateur..." />
      </div>
    );
  }

  const subTabs = [
    { id: 'demandes', label: `Demandes (${totalDemandes})` },
    { id: 'registre', label: 'Mouvements de solde' },
  ];

  return (
    <div className="space-y-4 font-sans">
      {/* ── En-tête de l'onglet avec hiérarchie claire des boutons ── */}
      <Card padding bordered className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
            <CalendarDays size={15} className="text-[var(--color-brand-500)]" />
            Situation Individuelle des Congés &amp; Absences
          </h3>
          <p className="text-xs text-[var(--color-fg-subtle)] mt-0.5">
            Solde, demandes et historique du collaborateur
          </p>
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
          <details className="relative group">
            <summary className="list-none cursor-pointer inline-flex items-center gap-2 min-h-[30px] px-3 border border-[var(--color-border)] text-xs font-semibold uppercase text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)]">
              <MoreHorizontal size={16} /> Autres actions
            </summary>
            <div className="absolute right-0 top-full mt-1 z-20 min-w-52 p-1 border border-[var(--color-border)] bg-[var(--color-surface)] shadow-lg flex flex-col items-stretch gap-1">
              <Button variant="ghost" size="sm" onClick={() => setShowExportModal(true)} iconLeft={<Download size={14} />} className="justify-start">Bilan annuel Excel</Button>
              {canWrite && <Button variant="ghost" size="sm" onClick={() => setShowInitModal(true)} iconLeft={<SlidersHorizontal size={14} />} className="justify-start">Initialiser le solde</Button>}
              <Button variant="ghost" size="sm" onClick={() => navigate('/conges')} iconLeft={<ExternalLink size={14} />} className="justify-start">Module Congés</Button>
            </div>
          </details>
          {canWrite && (
            <Button variant="primary" size="sm" onClick={() => { setSelectedDemande(null); setShowDemandeModal(true); }} iconLeft={<Plus size={15} />}>
              Poser un congé
            </Button>
          )}
        </div>
      </Card>

      {error && <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />}
      {demandesError && <Alert type="error" message={demandesError} />}

      {/* ── Synthèse des Soldes & Situation TCONGE (StatCards en tête) ── */}
      <SoldeCongeWidget
        soldeData={soldeEmploye}
        situationData={situationData}
        consommationPool={consommationPool}
        employe={employe}
        canWrite={canWrite}
        onOpenInitModal={() => setShowInitModal(true)}
      />

      <Suspense fallback={null}>
        <CongeExpirationReviewCard
          idEmploye={idEmploye}
          canValidate={canValidateConges}
          onSuccess={reloadData}
        />
      </Suspense>

      {/* ── Navigation Sous-onglets ── */}
      <div className="border-b border-[var(--color-border-subtle)]">
        <Tabs
          tabs={subTabs}
          activeTab={activeSubTab}
          onChange={setActiveSubTab}
          variant="underline"
        />
      </div>

      {activeSubTab === 'demandes' && (
        <Card padding bordered className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
            <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
              <Calendar size={14} className="text-[var(--color-brand-500)]" />
              Historique des Demandes de Congé
            </h4>
            <div className="flex items-center gap-3">
              <span className="text-xs text-[var(--color-fg-subtle)] font-mono">
                {totalDemandes} résultat{totalDemandes > 1 ? 's' : ''}
              </span>
              <Button
                variant="outline"
                size="xs"
                onClick={handleExportDemandesCsv}
                disabled={demandesLoading || demandesEmploye.length === 0}
                iconLeft={<Download size={13} />}
                className="text-xs"
                title="Exporter uniquement la page affichée au format CSV"
              >
                Exporter la page
              </Button>
            </div>
          </div>

          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
            <div className="flex flex-wrap gap-1" role="group" aria-label="Filtrer les demandes par statut">
              {[
                ['ALL', 'Toutes'], ['EN_ATTENTE', 'En attente'], ['VALIDE', 'Validées'],
                ['REFUSE', 'Refusées'], ['ANNULE', 'Annulées'],
              ].map(([code, label]) => (
                <Button key={code} type="button" variant={statusFilter === code ? 'primary' : 'outline'} size="xs"
                  onClick={() => changeStatus(code)} aria-pressed={statusFilter === code}>{label}</Button>
              ))}
            </div>
            <div className="relative w-full lg:w-64">
              <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-subtle)] pointer-events-none" />
              <Input aria-label="Rechercher une demande" type="search" value={searchQuery}
                onChange={(event) => changeSearch(event.target.value)} placeholder="Type, motif ou commentaire"
                className="w-full pl-8" />
            </div>
          </div>

          <div className="overflow-x-auto">
            {demandesLoading ? (
              <div className="p-8 flex justify-center"><Loading size="sm" label="Chargement des demandes..." /></div>
            ) : demandesEmploye.length === 0 ? (
              <div className="p-8">
                <EmptyState
                  icon={Calendar}
                  title={statusFilter === 'ALL' && !debouncedSearch ? "Aucune demande de congé" : "Aucun résultat pour ce filtre"}
                  description={statusFilter === 'ALL' && !debouncedSearch
                    ? "Aucune demande de congé n'a encore été enregistrée pour ce collaborateur."
                    : "Essayez de modifier vos filtres de statut ou votre recherche."}
                  bordered={false}
                />
              </div>
            ) : (
              <Table bordered={false}>
                <Thead>
                  <Tr>
                    <Th>Type d'Absence</Th>
                    <Th>Statut</Th>
                    <Th>Période</Th>
                    <Th className="text-center">Jours Décomptés</Th>
                    <Th align="right">Actions</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {demandesEmploye.map((d) => (
                    <Tr key={d.idDemandeConge} className="hover:bg-[var(--color-bg-subtle)]/60 transition-colors">
                      <Td>
                        <Badge variant="default" size="xs" className="font-semibold">
                          {d.libelleTypeConge || d.codeTypeConge}
                        </Badge>
                        {d.libelleMotifEvenement && (
                          <div className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-medium mt-0.5 truncate max-w-xs">{d.libelleMotifEvenement}</div>
                        )}
                      </Td>
                      <Td>
                        <StatusBadge
                          status={d.codeStatutDemande}
                          label={d.libelleStatutDemande}
                          size="xs"
                        />
                        {(d.isRefuse || d.codeStatutDemande === 'REFUSE') && d.motifRefus && (
                          <div className="text-[10px] text-[var(--color-danger-500)] font-medium mt-1 max-w-xs leading-tight truncate" title={d.motifRefus}>
                            Refus : {d.motifRefus}
                          </div>
                        )}
                      </Td>
                      <Td className="font-mono text-xs text-[var(--color-fg)]">
                        {formatDateOnly(d.dateDebut)}
                        {d.estDemiJournee ? (
                          <Badge variant="info" size="xs" className="ml-1.5 font-normal">
                            {d.momentDemiJournee === 'MATIN' ? 'Matinée' : d.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : '0.5 j'}
                          </Badge>
                        ) : (
                          <>
                            <span className="text-[var(--color-fg-subtle)] mx-1">→</span>
                            {formatDateOnly(d.dateFin)}
                          </>
                        )}
                      </Td>
                      <Td className="text-center font-mono font-bold text-[var(--color-fg)] text-xs font-data">
                        {d.nbJoursDecomptes} j
                      </Td>
                      <Td align="right">
                        <div className="flex items-center justify-end">
                          <Button
                            variant="ghost"
                            size="xs"
                            onClick={() => { setDetailDemande(d); setShowDetailModal(true); }}
                            iconLeft={<Eye size={13} />}
                            className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
                            title="Consulter le détail complet de la demande"
                          >
                            Détails
                          </Button>
                        </div>
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            )}
          </div>
          
          {/* Pagination */}
          {totalDemandes > 0 && (
            <Pagination
              currentPage={currentPage}
              totalItems={totalDemandes}
              pageSize={pageSize}
              onPageChange={(page) => { setCurrentPage(page); setDemandesLoading(true); }}
              onPageSizeChange={(size) => { setPageSize(size); setCurrentPage(1); setDemandesLoading(true); }}
              className="bg-transparent border-t-0 pt-1"
            />
          )}
        </Card>
      )}

      {activeSubTab === 'registre' && (
        <MouvementsSoldeTable
          idEmploye={idEmploye}
          mouvements={mouvements}
          soldeActuel={situationData?.soldeJours ?? soldeEmploye?.soldeActuel ?? soldeEmploye?.soldeDisponible}
          canWrite={canWrite}
          canValidate={canValidateConges}
          matricule={employe?.matricule}
          onAjustementSuccess={reloadData}
        />
      )}

      {/* Modal détail demande (Lazy) */}
      {showDetailModal && detailDemande && (
        <Suspense fallback={null}>
          <DemandeDetailsModal
            isOpen={showDetailModal}
            demande={detailDemande}
            canWrite={canWrite}
            onClose={() => { setShowDetailModal(false); setDetailDemande(null); }}
            onEdit={(d) => {
              setShowDetailModal(false);
              setSelectedDemande(d);
              setShowDemandeModal(true);
            }}
            onCancel={(d) => {
              setShowDetailModal(false);
              setConfirmAnnulerState({ isOpen: true, idDemande: d.idDemandeConge });
            }}
          />
        </Suspense>
      )}

      {/* Modal création/modification demande (Lazy) */}
      {showDemandeModal && (
        <Suspense fallback={null}>
          <DemandeCongeModal
            idEmploye={idEmploye}
            initialDemande={selectedDemande}
            referentiels={referentiels}
            isOpen={showDemandeModal}
            onClose={() => { setShowDemandeModal(false); setSelectedDemande(null); }}
            onSuccess={() => { setShowDemandeModal(false); setSelectedDemande(null); reloadData(); }}
          />
        </Suspense>
      )}

      {/* Modal confirmation annulation */}
      <ConfirmDialog
        isOpen={confirmAnnulerState.isOpen}
        title="Annuler la demande de congé"
        message="Êtes-vous sûr de vouloir annuler cette demande ? Cette action est irréversible."
        variant="danger"
        confirmLabel="Confirmer l'annulation"
        cancelLabel="Conserver la demande"
        onConfirm={handleAnnulerConfirm}
        onClose={() => setConfirmAnnulerState({ isOpen: false, idDemande: null })}
      />

      {/* Modal initialisation historique solde & TCONGE (Lazy) */}
      {showInitModal && (
        <Suspense fallback={null}>
          <InitialisationSoldeModal
            isOpen={showInitModal}
            onClose={() => setShowInitModal(false)}
            onSuccess={reloadData}
            employe={employe}
            soldeData={soldeEmploye}
            situationData={situationData}
          />
        </Suspense>
      )}

      {/* Modal export annuel officiel Excel (Lazy) */}
      {showExportModal && (
        <Suspense fallback={null}>
          <ExportEtatCongesModal
            isOpen={showExportModal}
            onClose={() => setShowExportModal(false)}
            idEmploye={idEmploye}
            employe={employe}
          />
        </Suspense>
      )}
    </div>
  );
}
