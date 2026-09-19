import { useState, useEffect, lazy, Suspense } from 'react';
import { Link } from 'react-router-dom';
import { Calendar, Plus, RefreshCw, CalendarDays, ExternalLink, SlidersHorizontal } from 'lucide-react';
import congeService from '@/api/congeService';
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

import SoldeCongeWidget from '@/components/conges/SoldeCongeWidget';
import MouvementsSoldeTable from '@/components/conges/MouvementsSoldeTable';

const DemandeCongeModal = lazy(() => import('@/components/conges/DemandeCongeModal'));
const InitialisationSoldeModal = lazy(() => import('@/components/conges/InitialisationSoldeModal'));

const formatDateOnly = (dateString) => {
  if (!dateString) return '—';
  return new Date(dateString).toLocaleDateString('fr-FR');
};

export default function CongesTab({ idEmploye, employe, canWrite = false }) {
  const [soldeEmploye, setSoldeEmploye] = useState(null);
  const [mouvements, setMouvements] = useState([]);
  const [demandesEmploye, setDemandesEmploye] = useState([]);
  const [totalDemandes, setTotalDemandes] = useState(0);
  const [consommationPool, setConsommationPool] = useState([]);
  const [referentiels, setReferentiels] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showDemandeModal, setShowDemandeModal] = useState(false);
  const [showInitModal, setShowInitModal] = useState(false);
  const [activeSubTab, setActiveSubTab] = useState('demandes'); // 'demandes' ou 'registre'

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Confirm Dialog for Annulation
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [confirmAnnulerState, setConfirmAnnulerState] = useState({ isOpen: false, idDemande: null });

  const loadDemandes = async () => {
    try {
      const res = await congeService.getDemandesEmploye(idEmploye, { page: currentPage, size: pageSize });
      setDemandesEmploye(res?.content || []);
      setTotalDemandes(res?.totalElements || 0);
    } catch (err) {
      console.error(err);
    }
  };

  const loadData = async () => {
    try {
      setLoading(true);
      setError(null);
      const [soldeRes, mvtRes, demRes, poolRes, refRes] = await Promise.all([
        congeService.getSoldeEmploye(idEmploye).catch(() => null),
        congeService.getMouvementsEmploye(idEmploye).catch(() => []),
        congeService.getDemandesEmploye(idEmploye, { page: 1, size: pageSize }).catch(() => null),
        congeService.getConsommationPool(idEmploye).catch(() => []),
        congeService.getAllReferentiels().catch(() => null),
      ]);

      setSoldeEmploye(soldeRes);
      setMouvements(mvtRes || []);
      setDemandesEmploye(demRes?.content || []);
      setTotalDemandes(demRes?.totalElements || 0);
      setConsommationPool(poolRes || []);
      setReferentiels(refRes);
    } catch (err) {
      setError(getErrorMessage(err, 'Erreur chargement congés de l\'employé.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (idEmploye) {
      if (currentPage === 1) {
          loadData();
      } else {
          setCurrentPage(1);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [idEmploye]);

  useEffect(() => {
    if (idEmploye && !loading) {
      loadDemandes();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentPage, pageSize]);

  const handleAnnulerConfirm = async () => {
    if (confirmAnnulerState.idDemande) {
      try {
        await congeService.annulerDemande(confirmAnnulerState.idDemande);
        loadData();
      } catch (err) {
        setError(getErrorMessage(err, 'Erreur lors de l\'annulation.'));
      } finally {
        setConfirmAnnulerState({ isOpen: false, idDemande: null });
      }
    }
  };

  if (loading && !soldeEmploye) {
    return (
      <div className="p-8 flex justify-center">
        <Loading size="md" label="Chargement des congés du collaborateur..." />
      </div>
    );
  }

  const subTabs = [
    { id: 'demandes', label: `Demandes de Congé (${totalDemandes})` },
    { id: 'registre', label: 'Registre des Mouvements de Solde' },
  ];

  return (
    <div className="space-y-5 font-sans">
      {/* En-tête de l'onglet avec CTA Soumettre Congé */}
      <Card padding bordered className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
            <CalendarDays size={15} className="text-[var(--color-brand-500)]" />
            Situation Individuelle des Congés &amp; Absences
          </h3>
          <p className="text-xs text-[var(--color-fg-subtle)] mt-0.5">
            Suivi des droits légaux, compteurs et historique des demandes
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link
            to="/conges"
            className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] bg-[var(--color-brand-500)]/10 hover:bg-[var(--color-brand-500)]/20 border border-[var(--color-brand-500)]/30 transition-colors"
            title="Ouvrir le module de gestion globale des congés"
          >
            <span>Module Congés</span>
            <ExternalLink size={12} />
          </Link>
          <Button
            variant="ghost"
            size="xs"
            onClick={loadData}
            loading={loading}
            iconLeft={<RefreshCw size={13} />}
            className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
          >
            Actualiser
          </Button>
          {canWrite && (
            <Button
              variant="outline"
              size="xs"
              onClick={() => setShowInitModal(true)}
              iconLeft={<SlidersHorizontal size={13} />}
              className="text-xs font-semibold"
              title="Initialiser le solde historique ou renseigner le TCONGE de migration issu d'Excel"
            >
              Initialiser Solde / TCONGE
            </Button>
          )}
          {canWrite && (
            <Button
              variant="primary"
              size="xs"
              onClick={() => { setSelectedDemande(null); setShowDemandeModal(true); }}
              iconLeft={<Plus size={14} />}
              className="font-bold text-xs"
            >
              Poser un Congé
            </Button>
          )}
        </div>
      </Card>

      {error && <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />}

      {/* Synthèse des Soldes */}
      <SoldeCongeWidget
        soldeData={soldeEmploye}
        consommationPool={consommationPool}
        employe={employe}
        canWrite={canWrite}
        onOpenInitModal={() => setShowInitModal(true)}
      />

      {/* Navigation Sous-onglets officielle */}
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
          <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)] dark:border-[var(--color-border-subtle)]">
            <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] dark:text-white flex items-center gap-2">
              <Calendar size={14} className="text-brand-500" />
              Historique des Demandes de Congé
            </h4>
            <span className="text-xs text-[var(--color-fg-subtle)] font-mono">
              {totalDemandes} demande{totalDemandes > 1 ? 's' : ''} enregistrée{totalDemandes > 1 ? 's' : ''}
            </span>
          </div>

          <div className="overflow-x-auto">
            {demandesEmploye.length === 0 ? (
              <div className="p-8">
                <EmptyState
                  icon={Calendar}
                  title="Aucune demande de congé"
                  description="Aucune demande de congé n'a encore été enregistrée pour ce collaborateur."
                />
              </div>
            ) : (
              <Table>
                <Thead>
                  <Tr>
                    <Th>Type d'Absence</Th>
                    <Th>Statut</Th>
                    <Th>Période</Th>
                    <Th className="text-center">Jours Décomptés</Th>
                    <Th>Particularités RH</Th>
                    <Th align="right">Actions</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {demandesEmploye.map((d) => (
                    <Tr key={d.idDemandeConge} className="hover:bg-[var(--color-bg-subtle)]/60 dark:hover:bg-ink-800/30 transition-colors">
                      <Td>
                        <Badge variant="default" size="xs" className="font-semibold">
                          {d.libelleTypeConge || d.codeTypeConge}
                        </Badge>
                        {d.libelleMotifEvenement && (
                          <div className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-medium mt-0.5">{d.libelleMotifEvenement}</div>
                        )}
                      </Td>
                      <Td>
                        <StatusBadge
                          status={d.codeStatutDemande}
                          label={d.libelleStatutDemande}
                          size="xs"
                        />
                        {(d.isRefuse || d.codeStatutDemande === 'REFUSE') && d.motifRefus && (
                          <div className="text-[10px] text-[var(--color-danger-500)] font-medium mt-1 max-w-xs leading-tight" title={d.motifRefus}>
                            Motif : {d.motifRefus}
                          </div>
                        )}
                      </Td>
                      <Td className="font-mono text-xs text-[var(--color-fg)] dark:text-[var(--color-fg)]">
                        {formatDateOnly(d.dateDebut)} <span className="text-[var(--color-fg-subtle)]">→</span> {formatDateOnly(d.dateFin)}
                      </Td>
                      <Td className="text-center font-mono font-bold text-[var(--color-fg)] dark:text-white text-xs">
                        {d.nbJoursDecomptes} j
                      </Td>
                      <Td className="space-y-1">
                        {d.extensionPathologiqueMaternite && (
                          <Badge variant="danger" size="xs">Ext. Pathologique</Badge>
                        )}
                        {d.suspendAcquisitionSolde && (
                          <Badge variant="warning" size="xs">Sans Solde</Badge>
                        )}
                        {d.derogationSoldeNegatif && (
                          <Badge variant="warning" size="xs">Solde Négatif</Badge>
                        )}
                        {d.commentaire && (
                          <div className="text-[11px] text-[var(--color-fg-subtle)] max-w-xs truncate" title={d.commentaire}>
                            {d.commentaire}
                          </div>
                        )}
                      </Td>
                      <Td align="right">
                        <div className="flex items-center justify-end gap-1.5">
                          {canWrite && d.availableActions?.EDIT?.allowed === true && (
                            <Button
                              variant="ghost"
                              size="xs"
                              onClick={() => { setSelectedDemande(d); setShowDemandeModal(true); }}
                              className="text-brand-600 hover:text-brand-700"
                            >
                              Modifier
                            </Button>
                          )}
                          {canWrite && d.availableActions?.CANCEL?.allowed === true && (
                            <Button
                              variant="ghost"
                              size="xs"
                              onClick={() => setConfirmAnnulerState({ isOpen: true, idDemande: d.idDemandeConge })}
                              className="text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)]"
                            >
                              Annuler
                            </Button>
                          )}
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
              onPageChange={setCurrentPage}
              onPageSizeChange={setPageSize}
              className="bg-transparent border-t-0 pt-1"
            />
          )}
        </Card>
      )}

      {activeSubTab === 'registre' && (
        <MouvementsSoldeTable
          idEmploye={idEmploye}
          mouvements={mouvements}
          onAjustementSuccess={loadData}
        />
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
            onSuccess={() => { setShowDemandeModal(false); setSelectedDemande(null); loadData(); }}
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
            onSuccess={loadData}
            employe={employe}
            soldeData={soldeEmploye}
          />
        </Suspense>
      )}
    </div>
  );
}
