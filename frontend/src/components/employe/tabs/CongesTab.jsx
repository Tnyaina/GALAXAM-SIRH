import { useState, useEffect } from 'react';
import { Calendar, Plus, RefreshCw } from 'lucide-react';
import congeService from '@/api/congeService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Loading from '@/components/ui/feedback/Loading';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Pagination from '@/components/ui/navigation/Pagination';

import SoldeCongeWidget from '@/components/conges/SoldeCongeWidget';
import MouvementsSoldeTable from '@/components/conges/MouvementsSoldeTable';
import DemandeCongeModal from '@/components/conges/DemandeCongeModal';

const formatDateOnly = (dateString) => {
  if (!dateString) return '';
  return new Date(dateString).toLocaleDateString('fr-FR');
};

export default function CongesTab({ idEmploye, canWrite = false }) {
  const [soldeEmploye, setSoldeEmploye] = useState(null);
  const [mouvements, setMouvements] = useState([]);
  const [demandesEmploye, setDemandesEmploye] = useState([]);
  const [totalDemandes, setTotalDemandes] = useState(0);
  const [consommationPool, setConsommationPool] = useState([]);
  const [referentiels, setReferentiels] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showDemandeModal, setShowDemandeModal] = useState(false);
  const [activeSubTab, setActiveSubTab] = useState('demandes'); // 'demandes' ou 'registre'

  // Pagination
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // Confirm Dialog for Annulation
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
      setError(err?.response?.data || err.message || 'Erreur chargement congés de l\'employé.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (idEmploye) {
      if (currentPage === 1) {
          loadData();
      } else {
          setCurrentPage(1); // will trigger the other effect if it was not 1
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
        setError(err?.response?.data || err.message || 'Erreur lors de l\'annulation.');
      } finally {
        setConfirmAnnulerState({ isOpen: false, idDemande: null });
      }
    }
  };

  if (loading && !soldeEmploye) {
    return (
      <div className="p-8 flex justify-center">
        <Loading size="md" label="Chargement des congés de l'employé..." />
      </div>
    );
  }

  return (
    <div className="space-y-6 font-sans">
      {/* En-tête de l'onglet avec CTA Soumettre Congé */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 rounded-none">
        <div>
          <h3 className="text-sm font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
            <Calendar className="w-4 h-4 text-[var(--color-brand-500)]" />
            Situation Individuelle des Congés & Absences
          </h3>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="ghost"
            size="xs"
            onClick={loadData}
            loading={loading}
            iconLeft={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
            className="rounded-none text-[var(--color-fg-muted)]"
          />
          {canWrite && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => setShowDemandeModal(true)}
              iconLeft={<Plus size={16} />}
              className="rounded-none font-bold uppercase"
            >
              Poser un Congé
            </Button>
          )}
        </div>
      </div>

      {error && <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />}

      {/* Widget Synthese Solde */}
      <SoldeCongeWidget soldeData={soldeEmploye} consommationPool={consommationPool} />

      {/* Navigation Sous-onglets */}
      <div className="flex border-b border-[var(--color-border-subtle)] mt-2">
        <button
          onClick={() => setActiveSubTab('demandes')}
          className={`px-4 py-3 text-xs font-bold uppercase tracking-wider  ${
            activeSubTab === 'demandes'
              ? 'border-b-2 border-[var(--color-brand-500)] text-[var(--color-brand-500)]'
              : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
          }`}
        >
          Demandes de Congé
        </button>
        <button
          onClick={() => setActiveSubTab('registre')}
          className={`px-4 py-3 text-xs font-bold uppercase tracking-wider  ${
            activeSubTab === 'registre'
              ? 'border-b-2 border-[var(--color-brand-500)] text-[var(--color-brand-500)]'
              : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
          }`}
        >
          Historique Solde
        </button>
      </div>

      {activeSubTab === 'demandes' && (
        <Card className="rounded-none border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm ">
          <CardHeader>
            <div className="flex items-center justify-between w-full">
              <div className="flex items-center gap-2">
                <Calendar className="w-4 h-4 text-[var(--color-brand-500)]" />
                <CardTitle>Historique des Demandes</CardTitle>
              </div>
              <span className="text-xs font-data text-[var(--color-fg-muted)]">
                {totalDemandes} demande{totalDemandes > 1 ? 's' : ''}
              </span>
            </div>
          </CardHeader>
          <CardBody>
            <div className="overflow-x-auto">
              <Table>
                <Thead>
                  <Tr>
                    <Th>Type</Th>
                    <Th>Statut</Th>
                    <Th>Période</Th>
                    <Th className="text-center">Décompte</Th>
                    <Th>Dérogations</Th>
                    <Th className="text-right">Actions</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {demandesEmploye.length === 0 ? (
                    <Tr>
                      <Td colSpan="6" className="text-center py-6 text-[var(--color-fg-muted)] ">
                        Aucune demande de congé enregistrée.
                      </Td>
                    </Tr>
                  ) : (
                    demandesEmploye.map((d) => (
                      <Tr key={d.idDemandeConge}>
                        <Td>
                          <div className="font-bold text-[var(--color-fg)]">{d.libelleTypeConge}</div>
                          {d.libelleMotifEvenement && (
                            <div className="text-[11px] text-warning-500">{d.libelleMotifEvenement}</div>
                          )}
                        </Td>
                        <Td>
                          <Badge
                            variant={
                              d.codeStatutDemande === 'VALIDE'
                                ? 'success'
                                : d.codeStatutDemande === 'REFUSE'
                                ? 'danger'
                                : d.codeStatutDemande === 'ANNULE'
                                ? 'default'
                                : 'warning'
                            }
                            size="sm"
                          >
                            {d.libelleStatutDemande}
                          </Badge>
                          {d.codeStatutDemande === 'REFUSE' && d.motifRefus && (
                            <div className="text-[11px] text-danger-500 font-medium mt-1 max-w-xs leading-tight" title={d.motifRefus}>
                              Motif : {d.motifRefus}
                            </div>
                          )}
                        </Td>
                        <Td className="font-data text-xs">
                          Du {formatDateOnly(d.dateDebut)} au {formatDateOnly(d.dateFin)}
                        </Td>
                        <Td className="text-center font-data font-extrabold text-[var(--color-brand-500)] text-xs">
                          {d.nbJoursDecomptes}j
                        </Td>
                        <Td className="space-y-1">
                          {d.extensionPathologiqueMaternite && (
                            <Badge variant="danger" size="sm">Ext. Pathologique</Badge>
                          )}
                          {d.suspendAcquisitionSolde && (
                            <Badge variant="warning" size="sm">Sans Solde</Badge>
                          )}
                          {d.derogationSoldeNegatif && (
                            <Badge variant="warning" size="sm">Solde Négatif</Badge>
                          )}
                          {d.commentaire && (
                            <div className="text-[11px] text-[var(--color-fg-muted)] max-w-xs truncate" title={d.commentaire}>
                              {d.commentaire}
                            </div>
                          )}
                        </Td>
                        <Td className="text-right">
                          {canWrite && d.codeStatutDemande !== 'ANNULE' && d.codeStatutDemande !== 'REFUSE' && (
                            <Button
                              variant="ghost"
                              size="xs"
                              onClick={() => setConfirmAnnulerState({ isOpen: true, idDemande: d.idDemandeConge })}
                              className="rounded-none text-danger-500 hover:text-danger-600"
                            >
                              Annuler
                            </Button>
                          )}
                        </Td>
                      </Tr>
                    ))
                  )}
                </Tbody>
              </Table>
            </div>
            
            {/* Pagination */}
            {totalDemandes > 0 && (
              <Pagination
                currentPage={currentPage}
                totalItems={totalDemandes}
                pageSize={pageSize}
                onPageChange={setCurrentPage}
                onPageSizeChange={setPageSize}
                className="bg-transparent border-t-0"
              />
            )}
          </CardBody>
        </Card>
      )}

      {activeSubTab === 'registre' && (
        <div className="">
          <MouvementsSoldeTable
            idEmploye={idEmploye}
            mouvements={mouvements}
            onAjustementSuccess={loadData}
          />
        </div>
      )}

      {/* Modal création demande */}
      <DemandeCongeModal
        idEmploye={idEmploye}
        referentiels={referentiels}
        isOpen={showDemandeModal}
        onClose={() => setShowDemandeModal(false)}
        onSuccess={loadData}
      />

      <ConfirmDialog
        isOpen={confirmAnnulerState.isOpen}
        title="Annuler la demande"
        message={`Voulez-vous vraiment annuler la demande #${confirmAnnulerState.idDemande} ?`}
        confirmLabel="Annuler la demande"
        cancelLabel="Fermer"
        variant="danger"
        onConfirm={handleAnnulerConfirm}
        onClose={() => setConfirmAnnulerState({ isOpen: false, idDemande: null })}
      />
    </div>
  );
}
