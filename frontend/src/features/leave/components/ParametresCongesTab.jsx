import { useState, useEffect } from 'react';
import { Settings, ShieldAlert, FileText, Eye, Power, Check, Edit2 } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { useToast } from '@/components/ui/feedback/Toast';
import congeService from '@/features/leave/api/congeService';
import useConfirm from '@/hooks/useConfirm';
import { TypeCongeModal, MotifFamilialModal } from './ParametresCongesModals';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SideTabs from '@/components/ui/navigation/SideTabs';

export default function ParametresCongesTab() {
  // Contexte
  const toast = useToast();
  const { confirm, ConfirmModal } = useConfirm();

  // État local
  const [activeSubTab, setActiveSubTab] = useState('types');
  const [types, setTypes] = useState([]);
  const [motifs, setMotifs] = useState([]);
  const [showInactive, setShowInactive] = useState(false);
  const [loading, setLoading] = useState(true);

  // État des modales
  const [showTypeModal, setShowTypeModal] = useState(false);
  const [showMotifModal, setShowMotifModal] = useState(false);
  const [selectedItem, setSelectedItem] = useState(null);

  // Chargement des données
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

  // Effet : charge les référentiels à l'ouverture de l'onglet.
  useEffect(() => {
    fetchParametres();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Actions utilisateur
  const handleEditType = (item) => {
    setSelectedItem(item);
    setShowTypeModal(true);
  };

  const handleEditMotif = (item) => {
    setSelectedItem(item);
    setShowMotifModal(true);
  };

  const handleToggleTypeStatus = (type) => {
    const isActif = type.estActif !== false;
    const actionText = isActif ? 'Désactiver' : 'Activer';
    confirm({
      title: `${actionText} le type de congé`,
      message: isActif
        ? `Êtes-vous sûr de vouloir désactiver le type de congé "${type.libelle}" (${type.code}) ?\n\nIl ne sera plus disponible pour les nouvelles demandes de congés.`
        : `Êtes-vous sûr de vouloir réactiver le type de congé "${type.libelle}" (${type.code}) ?`,
      confirmLabel: actionText,
      variant: isActif ? 'danger' : 'primary',
      onConfirm: async () => {
        try {
          if (isActif) {
            await congeService.desactiverTypeConge(type.idTypeConge);
            toast({ title: 'Succès', message: `Type de congé "${type.libelle}" désactivé.`, type: 'success' });
          } else {
            await congeService.activerTypeConge(type.idTypeConge);
            toast({ title: 'Succès', message: `Type de congé "${type.libelle}" réactivé.`, type: 'success' });
          }
          fetchParametres();
        } catch {
          toast({ title: 'Erreur', message: 'Erreur lors du changement de statut.', type: 'error' });
        }
      }
    });
  };

  const handleToggleMotifStatus = (motif) => {
    const isActif = motif.estActif !== false;
    const actionText = isActif ? 'Désactiver' : 'Activer';
    confirm({
      title: `${actionText} le motif familial`,
      message: isActif
        ? `Êtes-vous sûr de vouloir désactiver le motif "${motif.libelle}" ?\n\nIl ne sera plus sélectionnable dans les événements familiaux.`
        : `Êtes-vous sûr de vouloir réactiver le motif "${motif.libelle}" ?`,
      confirmLabel: actionText,
      variant: isActif ? 'danger' : 'primary',
      onConfirm: async () => {
        try {
          if (isActif) {
            await congeService.desactiverMotifFamilial(motif.idMotif);
            toast({ title: 'Succès', message: `Motif "${motif.libelle}" désactivé.`, type: 'success' });
          } else {
            await congeService.activerMotifFamilial(motif.idMotif);
            toast({ title: 'Succès', message: `Motif "${motif.libelle}" réactivé.`, type: 'success' });
          }
          fetchParametres();
        } catch {
          toast({ title: 'Erreur', message: 'Erreur lors du changement de statut.', type: 'error' });
        }
      }
    });
  };

  // Rendu
  return (
    <SideTabs
      title="Configuration des congés"
      tabs={[
        { id: 'types', label: 'Types de congés', icon: <FileText size={16} />, count: types.length },
        { id: 'evenements', label: 'Événements familiaux', icon: <ShieldAlert size={16} />, count: motifs.length }
      ]}
      activeTab={activeSubTab}
      onChange={setActiveSubTab}
      className="space-y-6"
    >
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
                    {types.filter(t => showInactive || t.estActif !== false).length > 0 && (
                      <Button variant="primary" size="sm" onClick={() => { setSelectedItem(null); setShowTypeModal(true); }}>
                        Nouveau Type
                      </Button>
                    )}
                  </div>
                </div>
              </CardHeader>
              <CardBody className="p-0">
                {loading ? (
                    <div className="p-4"><SkeletonTable rows={5} columns={8} bordered={false} /></div>
                ) : types.filter(t => showInactive || t.estActif !== false).length === 0 ? (
                    <div className="p-8">
                        <EmptyState
                            icon={Settings}
                            title="Aucun type de congé configuré"
                            description="Veuillez ajouter un type de congé pour commencer."
                            actionLabel="Nouveau Type"
                            onAction={() => { setSelectedItem(null); setShowTypeModal(true); }}
                            bordered={false}
                        />
                    </div>
                ) : (
                <Table bordered={false}>
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
                            <div className="flex justify-end gap-1.5">
                              <Button
                                variant="ghost"
                                size="sm"
                                iconOnly
                                className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
                                onClick={() => handleEditType(type)}
                                icon={<Edit2 size={15} />}
                                title={`Modifier ${type.libelle}`}
                                aria-label={`Modifier le type de congé ${type.libelle}`}
                              />
                              {type.estActif !== false ? (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  iconOnly
                                  onClick={() => handleToggleTypeStatus(type)}
                                  className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                                  icon={<Power size={15} />}
                                  title={`Désactiver ${type.libelle}`}
                                  aria-label={`Désactiver le type de congé ${type.libelle}`}
                                />
                              ) : (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  iconOnly
                                  onClick={() => handleToggleTypeStatus(type)}
                                  className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center text-[var(--color-success-600)] hover:text-[var(--color-success-700)] hover:bg-[var(--color-success-600)]/10"
                                  icon={<Check size={15} />}
                                  title={`Réactiver ${type.libelle}`}
                                  aria-label={`Réactiver le type de congé ${type.libelle}`}
                                />
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
                    <ShieldAlert className="w-5 h-5 text-[var(--color-warning-500)]" />
                    <CardTitle>Quota annuel des Événements Familiaux</CardTitle>
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
                    {motifs.filter(m => showInactive || m.estActif !== false).length > 0 && (
                      <Button variant="primary" size="sm" onClick={() => { setSelectedItem(null); setShowMotifModal(true); }}>
                        Ajouter
                      </Button>
                    )}
                  </div>
                </div>
              </CardHeader>
              <CardBody className="p-0">
                {loading ? (
                    <div className="p-4"><SkeletonTable rows={5} columns={4} bordered={false} /></div>
                ) : motifs.filter(m => showInactive || m.estActif !== false).length === 0 ? (
                    <div className="p-8">
                        <EmptyState
                            icon={ShieldAlert}
                            title="Aucun événement configuré"
                            description="Veuillez ajouter un événement familial pour commencer."
                            actionLabel="Ajouter"
                            onAction={() => { setSelectedItem(null); setShowMotifModal(true); }}
                            bordered={false}
                        />
                    </div>
                ) : (
                <Table bordered={false}>
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
                            <div className="flex justify-end gap-1.5">
                              <Button
                                variant="ghost"
                                size="sm"
                                iconOnly
                                className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
                                onClick={() => handleEditMotif(motif)}
                                icon={<Edit2 size={15} />}
                                title={`Modifier ${motif.libelle}`}
                                aria-label={`Modifier le motif ${motif.libelle}`}
                              />
                              {motif.estActif !== false ? (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  iconOnly
                                  onClick={() => handleToggleMotifStatus(motif)}
                                  className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                                  icon={<Power size={15} />}
                                  title={`Désactiver ${motif.libelle}`}
                                  aria-label={`Désactiver le motif ${motif.libelle}`}
                                />
                              ) : (
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  iconOnly
                                  onClick={() => handleToggleMotifStatus(motif)}
                                  className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center text-[var(--color-success-600)] hover:text-[var(--color-success-700)] hover:bg-[var(--color-success-600)]/10"
                                  icon={<Check size={15} />}
                                  title={`Réactiver ${motif.libelle}`}
                                  aria-label={`Réactiver le motif ${motif.libelle}`}
                                />
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

      <ConfirmModal />

    </SideTabs>
  );
}
