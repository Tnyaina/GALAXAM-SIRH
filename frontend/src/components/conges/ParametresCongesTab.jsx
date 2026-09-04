import { useState, useEffect } from 'react';
import { Settings, ShieldAlert, FileText, Eye, Clock, RotateCcw, AlertTriangle, CheckCircle, Play } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { useToast } from '@/components/ui/feedback/Toast';
import congeService from '@/api/congeService';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import TypeCongeModal from './TypeCongeModal';
import MotifFamilialModal from './MotifFamilialModal';
import { Edit2, Trash2 } from 'lucide-react';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

export default function ParametresCongesTab() {
  const [activeSubTab, setActiveSubTab] = useState('types');
  const toast = useToast();

  const [types, setTypes] = useState([]);
  const [motifs, setMotifs] = useState([]);
  const [showInactive, setShowInactive] = useState(false);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showTypeModal, setShowTypeModal] = useState(false);
  const [showMotifModal, setShowMotifModal] = useState(false);
  const [showPurgeConfirm, setShowPurgeConfirm] = useState(false);
  const [purging, setPurging] = useState(false);
  const [selectedItem, setSelectedItem] = useState(null);

  // ConfirmDialog state
  const [confirmState, setConfirmState] = useState({
    isOpen: false,
    id: null,
    type: null // 'TYPE_CONGE' ou 'MOTIF'
  });

  const fetchParametres = async () => {
    setLoading(true);
    try {
      const t = await congeService.getTypesConge(false); // Récupère même les inactifs pour l'administration
      const m = await congeService.getMotifsFamiliaux(false);
      setTypes(t);
      setMotifs(m);
    } catch (err) {
      console.error(err);
      toast({ title: 'Erreur', message: 'Impossible de charger les paramètres.', type: 'error' });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchParametres();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleEditType = (item) => {
    setSelectedItem(item);
    setShowTypeModal(true);
  };

  const handleEditMotif = (item) => {
    setSelectedItem(item);
    setShowMotifModal(true);
  };

  const handleDeleteType = (id) => {
    setConfirmState({ isOpen: true, id, type: 'TYPE_CONGE' });
  };

  const handleDeleteMotif = (id) => {
    setConfirmState({ isOpen: true, id, type: 'MOTIF' });
  };

  const executeDelete = async () => {
    if (!confirmState.id) return;
    try {
      if (confirmState.type === 'TYPE_CONGE') {
        await congeService.supprimerTypeConge(confirmState.id);
        toast({ title: 'Succès', message: 'Type de congé désactivé.', type: 'success' });
      } else if (confirmState.type === 'MOTIF') {
        await congeService.supprimerMotifFamilial(confirmState.id);
        toast({ title: 'Succès', message: 'Événement désactivé.', type: 'success' });
      }
      fetchParametres();
    } catch (err) {
      console.error(err);
      toast({ title: 'Erreur', message: 'Erreur lors de la désactivation.', type: 'error' });
    } finally {
      setConfirmState({ isOpen: false, id: null, type: null });
    }
  };

  const handleExecuterPurge = async () => {
    setPurging(true);
    try {
      const res = await congeService.declencherPurgeExpiration();
      toast({
        title: 'Purge Réussie',
        message: res.message || 'La purge triennale des droits expirés a été exécutée avec succès.',
        type: 'success',
      });
      setShowPurgeConfirm(false);
      fetchParametres();
    } catch (err) {
      console.error(err);
      toast({
        title: 'Erreur',
        message: err.response?.data || 'Impossible d\'exécuter la purge.',
        type: 'error',
      });
    } finally {
      setPurging(false);
    }
  };

  return (
    <div className="space-y-6 ">
      <div className="flex flex-col md:flex-row gap-6">
        
        {/* Menu Latéral Paramètres */}
        <div className="w-full md:w-64 shrink-0 space-y-1">
          <button
            onClick={() => setActiveSubTab('types')}
            className={`w-full text-left px-4 py-2.5 text-xs font-bold uppercase tracking-wider flex items-center gap-2 border-l-2  ${
              activeSubTab === 'types' 
                ? 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)] border-[var(--color-brand-500)]' 
                : 'bg-transparent text-[var(--color-fg-subtle)] border-transparent hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-fg)]'
            }`}
          >
            <FileText size={16} />
            Types de Congés
          </button>
          
          <button
            onClick={() => setActiveSubTab('evenements')}
            className={`w-full text-left px-4 py-2.5 text-xs font-bold uppercase tracking-wider flex items-center gap-2 border-l-2  ${
              activeSubTab === 'evenements' 
                ? 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)] border-[var(--color-brand-500)]' 
                : 'bg-transparent text-[var(--color-fg-subtle)] border-transparent hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-fg)]'
            }`}
          >
            <ShieldAlert size={16} />
            Événements Familiaux
          </button>

          <button
            onClick={() => setActiveSubTab('prescription')}
            className={`w-full text-left px-4 py-2.5 text-xs font-bold uppercase tracking-wider flex items-center gap-2 border-l-2  ${
              activeSubTab === 'prescription' 
                ? 'bg-[var(--color-brand-50)] text-[var(--color-brand-600)] border-[var(--color-brand-500)]' 
                : 'bg-transparent text-[var(--color-fg-subtle)] border-transparent hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-fg)]'
            }`}
          >
            <Clock size={16} />
            Prescription & Purge Triennale
          </button>

        </div>

        {/* Contenu Principal Paramètres */}
        <div className="flex-1">
          {activeSubTab === 'types' && (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between flex-wrap gap-3">
                  <div className="flex items-center gap-2">
                    <Settings className="w-5 h-5 text-[var(--color-brand-500)]" />
                    <CardTitle>Configuration des Types de Congés</CardTitle>
                  </div>
                  <div className="flex items-center gap-3">
                    <label className="flex items-center gap-1.5 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer hover:text-[var(--color-fg)] px-2 py-1 rounded-none hover:bg-[var(--color-bg-subtle)]">
                      <input
                        type="checkbox"
                        checked={showInactive}
                        onChange={(e) => setShowInactive(e.target.checked)}
                        className="w-3.5 h-3.5 rounded-none accent-[var(--color-brand-500)]"
                      />
                      <Eye size={13} />
                      <span>Afficher masqués</span>
                    </label>
                    <Button variant="primary" size="sm" onClick={() => { setSelectedItem(null); setShowTypeModal(true); }}>
                      Nouveau Type
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardBody className="p-0">
                {loading ? (
                    <div className="p-4"><SkeletonTable rows={5} columns={8} /></div>
                ) : types.filter(t => showInactive || t.estActif !== false).length === 0 ? (
                    <div className="p-8">
                        <EmptyState
                            icon={Settings}
                            title="Aucun type de congé configuré"
                            description="Veuillez ajouter un type de congé pour commencer."
                            actionLabel="Nouveau Type"
                            onAction={() => { setSelectedItem(null); setShowTypeModal(true); }}
                        />
                    </div>
                ) : (
                <Table>
                  <Thead>
                    <Tr>
                      <Th>Code</Th>
                      <Th>Libellé</Th>
                      <Th className="text-center">Rémunération</Th>
                      <Th className="text-center">Ancienneté Min.</Th>
                      <Th className="text-center">Préavis Légal</Th>
                      <Th className="text-center">Justificatif</Th>
                      <Th className="text-center">Durée / Plafond</Th>
                      <Th className="text-center">Statut</Th>
                      <Th className="text-right">Actions</Th>
                    </Tr>
                  </Thead>
                  <Tbody>
                      {types.filter(t => showInactive || t.estActif !== false).map((type) => (
                        <Tr key={type.idTypeConge}>
                          <Td className="font-bold">{type.code}</Td>
                          <Td>{type.libelle}</Td>
                          <Td className="text-center">
                            <Badge variant={type.estPaye ? 'success' : 'danger'} size="sm">
                              {type.estPaye ? 'Rémunéré' : 'Non payé'}
                            </Badge>
                          </Td>
                          <Td className="text-center font-data text-xs font-semibold">
                            {type.exigeAncienneteMinMois > 0 ? (
                              <span className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-bold">{type.exigeAncienneteMinMois} mois ({type.exigeAncienneteMinMois / 12} an)</span>
                            ) : (
                              <span className="text-[var(--color-fg-subtle)]">Aucune (0m)</span>
                            )}
                          </Td>
                          <Td className="text-center">
                            <Badge variant={type.soumisDelaiPreavis ? 'warning' : 'neutral'} size="sm">
                              {type.soumisDelaiPreavis ? 'Exigé (15j)' : 'Non soumis'}
                            </Badge>
                          </Td>
                          <Td className="text-center">
                            <Badge variant={type.exigeJustificatif ? 'info' : 'neutral'} size="sm">
                              {type.exigeJustificatif ? 'Obligatoire' : 'Optionnel'}
                            </Badge>
                          </Td>
                          <Td className="text-center text-xs font-medium">
                            {type.dureeFixeJours ? `Fixe : ${type.dureeFixeJours}j` : type.plafondJoursEpisode ? `Max : ${type.plafondJoursEpisode}j/demande` : '-'}
                          </Td>
                          <Td className="text-center">
                            <Badge variant={type.estActif ? 'success' : 'neutral'} size="sm">
                              {type.estActif ? 'Actif' : 'Inactif'}
                            </Badge>
                          </Td>
                          <Td className="text-right">
                            <div className="flex justify-end gap-2">
                              <Button variant="ghost" size="xs" onClick={() => handleEditType(type)} iconLeft={<Edit2 size={14} />} />
                              {type.estActif && (
                                <Button variant="ghost" size="xs" onClick={() => handleDeleteType(type.idTypeConge)} className="text-danger-500 hover:text-danger-600" iconLeft={<Trash2 size={14} />} />
                              )}
                            </div>
                          </Td>
                        </Tr>
                      ))}
                  </Tbody>
                </Table>
                )}
              </CardBody>
            </Card>
          )}

          {activeSubTab === 'evenements' && (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between flex-wrap gap-3">
                  <div className="flex items-center gap-2">
                    <ShieldAlert className="w-5 h-5 text-warning-500" />
                    <CardTitle>Quota des Événements Familiaux (10 jours/an)</CardTitle>
                  </div>
                  <div className="flex items-center gap-3">
                    <label className="flex items-center gap-1.5 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer hover:text-[var(--color-fg)] px-2 py-1 rounded-none hover:bg-[var(--color-bg-subtle)]">
                      <input
                        type="checkbox"
                        checked={showInactive}
                        onChange={(e) => setShowInactive(e.target.checked)}
                        className="w-3.5 h-3.5 rounded-none accent-[var(--color-brand-500)]"
                      />
                      <Eye size={13} />
                      <span>Afficher masqués</span>
                    </label>
                    <Button variant="primary" size="sm" onClick={() => { setSelectedItem(null); setShowMotifModal(true); }}>
                      Ajouter
                    </Button>
                  </div>
                </div>
              </CardHeader>
              <CardBody className="p-0">
                {loading ? (
                    <div className="p-4"><SkeletonTable rows={5} columns={4} /></div>
                ) : motifs.filter(m => showInactive || m.estActif !== false).length === 0 ? (
                    <div className="p-8">
                        <EmptyState
                            icon={ShieldAlert}
                            title="Aucun événement configuré"
                            description="Veuillez ajouter un événement familial pour commencer."
                            actionLabel="Ajouter"
                            onAction={() => { setSelectedItem(null); setShowMotifModal(true); }}
                        />
                    </div>
                ) : (
                <Table>
                  <Thead>
                    <Tr>
                      <Th>Motif (Code du Travail)</Th>
                      <Th className="text-center">Jours Autorisés</Th>
                      <Th className="text-center">Statut</Th>
                      <Th className="text-right">Actions</Th>
                    </Tr>
                  </Thead>
                  <Tbody>
                      {motifs.filter(m => showInactive || m.estActif !== false).map((motif) => (
                        <Tr key={motif.idMotif}>
                          <Td className="font-semibold">{motif.libelle} ({motif.code})</Td>
                          <Td className="text-center font-data font-bold">{motif.nbJoursAutorises} jours</Td>
                          <Td className="text-center">
                            <Badge variant={motif.estActif ? 'success' : 'neutral'} size="sm">
                              {motif.estActif ? 'Actif' : 'Inactif'}
                            </Badge>
                          </Td>
                          <Td className="text-right">
                            <div className="flex justify-end gap-2">
                              <Button variant="ghost" size="xs" onClick={() => handleEditMotif(motif)} iconLeft={<Edit2 size={14} />} />
                              {motif.estActif && (
                                <Button variant="ghost" size="xs" onClick={() => handleDeleteMotif(motif.idMotif)} className="text-danger-500 hover:text-danger-600" iconLeft={<Trash2 size={14} />} />
                              )}
                            </div>
                          </Td>
                        </Tr>
                      ))}
                  </Tbody>
                </Table>
                )}
              </CardBody>
            </Card>
          )}

          {activeSubTab === 'prescription' && (
            <div className="space-y-6">
              <Card>
                <CardHeader>
                  <div className="flex items-center gap-2">
                    <Clock className="w-5 h-5 text-[var(--color-brand-500)]" />
                    <CardTitle>Cadre Légal & Règle Triennale de Prescription (Madagascar)</CardTitle>
                  </div>
                </CardHeader>
                <CardBody className="space-y-6">
                  <div className="p-4 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex items-start gap-3">
                    <AlertTriangle className="text-[var(--color-warning-600)] shrink-0 mt-0.5" size={20} />
                    <div className="text-xs space-y-1.5 text-[var(--color-warning-700)] dark:text-[var(--color-warning-200)]">
                      <p className="font-bold text-sm">Prescription légale après 3 ans (36 mois) :</p>
                      <p className="leading-relaxed">
                        Conformément au Code du travail malgache, les jours de congés payés acquis par un salarié doivent être pris dans un délai maximum de <strong>3 ans</strong> suivant leur ouverture de droit. Les droits non consommés au-delà de cette échéance sont légalement forclos et purgés du solde.
                      </p>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-2">
                      <div className="flex items-center gap-2 font-bold text-xs uppercase tracking-wider text-[var(--color-brand-600)]">
                        <CheckCircle size={15} />
                        Règle FIFO Active
                      </div>
                      <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                        Lorsqu'un employé pose des congés, le système consomme toujours en priorité ses droits les plus anciens.
                      </p>
                    </div>

                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-2">
                      <div className="flex items-center gap-2 font-bold text-xs uppercase tracking-wider text-[var(--color-brand-600)]">
                        <Clock size={15} />
                        Planificateur Nocturne
                      </div>
                      <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                        Exécution automatique chaque nuit à <strong>02h00</strong> et à chaque redémarrage de l'application.
                      </p>
                    </div>

                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-2">
                      <div className="flex items-center gap-2 font-bold text-xs uppercase tracking-wider text-[var(--color-brand-600)]">
                        <RotateCcw size={15} />
                        Traçabilité Complète
                      </div>
                      <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                        Chaque purge génère une écriture négative <code>EXPIRATION</code> dans le grand-livre des mouvements de solde.
                      </p>
                    </div>
                  </div>

                  <div className="pt-4 border-t border-[var(--color-border-subtle)] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                    <div>
                      <h4 className="text-sm font-bold text-[var(--color-fg)]">Déclenchement Manuel de la Purge Triennale</h4>
                      <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                        Forcer l'évaluation immédiate de tous les soldes pour régulariser les droits ayant dépassé 36 mois.
                      </p>
                    </div>

                    <Button
                      variant="primary"
                      size="sm"
                      onClick={() => setShowPurgeConfirm(true)}
                      loading={purging}
                      icon={<Play size={14} />}
                    >
                      Exécuter la Purge Maintenant
                    </Button>
                  </div>
                </CardBody>
              </Card>
            </div>
          )}

        </div>
      </div>

      <TypeCongeModal 
        isOpen={showTypeModal} 
        onClose={() => setShowTypeModal(false)} 
        initialData={selectedItem} 
        onSuccess={fetchParametres} 
      />
      <MotifFamilialModal 
        isOpen={showMotifModal} 
        onClose={() => setShowMotifModal(false)} 
        initialData={selectedItem} 
        onSuccess={fetchParametres} 
      />

      <ConfirmDialog
        isOpen={confirmState.isOpen}
        title="Confirmer la désactivation"
        message={
          confirmState.type === 'TYPE_CONGE'
            ? "Voulez-vous vraiment désactiver ce type de congé ?"
            : "Voulez-vous vraiment désactiver cet événement familial ?"
        }
        confirmLabel="Désactiver"
        cancelLabel="Annuler"
        variant="danger"
        onConfirm={executeDelete}
        onClose={() => setConfirmState({ isOpen: false, id: null, type: null })}
      />

      <ConfirmDialog
        isOpen={showPurgeConfirm}
        title="Exécuter la Purge Triennale des Congés"
        message="Cette opération va analyser l'ensemble des employés actifs et purger les droits acquis il y a plus de 36 mois qui n'ont pas encore été consommés selon la règle FIFO. Souhaitez-vous continuer ?"
        confirmLabel="Lancer la purge"
        cancelLabel="Annuler"
        variant="warning"
        loading={purging}
        onConfirm={handleExecuterPurge}
        onClose={() => setShowPurgeConfirm(false)}
      />
    </div>
  );
}
