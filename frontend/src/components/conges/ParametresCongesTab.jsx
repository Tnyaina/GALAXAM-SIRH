import { useState, useEffect } from 'react';
import { Settings, CheckCircle, ShieldAlert, FileText } from 'lucide-react';
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

export default function ParametresCongesTab() {
  const [activeSubTab, setActiveSubTab] = useState('types');
  const toast = useToast();

  const [types, setTypes] = useState([]);
  const [motifs, setMotifs] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modals state
  const [showTypeModal, setShowTypeModal] = useState(false);
  const [showMotifModal, setShowMotifModal] = useState(false);
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


        </div>

        {/* Contenu Principal Paramètres */}
        <div className="flex-1">
          {activeSubTab === 'types' && (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <Settings className="w-5 h-5 text-[var(--color-brand-500)]" />
                    <CardTitle>Configuration des Types de Congés</CardTitle>
                  </div>
                  <Button variant="primary" size="sm" onClick={() => { setSelectedItem(null); setShowTypeModal(true); }}>
                    Nouveau Type
                  </Button>
                </div>
              </CardHeader>
              <CardBody className="p-0">
                <Table>
                  <Thead>
                    <Tr>
                      <Th>Code</Th>
                      <Th>Libellé</Th>
                      <Th className="text-center">Payé</Th>
                      <Th className="text-center">Statut</Th>
                      <Th className="text-right">Actions</Th>
                    </Tr>
                  </Thead>
                  <Tbody>
                    {loading ? (
                      <Tr><Td colSpan={5} className="text-center">Chargement...</Td></Tr>
                    ) : types.length === 0 ? (
                      <Tr><Td colSpan={5} className="text-center">Aucun type de congé configuré.</Td></Tr>
                    ) : (
                      types.map((type) => (
                        <Tr key={type.idTypeConge}>
                          <Td className="font-bold">{type.code}</Td>
                          <Td>{type.libelle}</Td>
                          <Td className="text-center">
                            {type.estPaye ? <CheckCircle className="w-4 h-4 text-success-500 inline" /> : <span className="text-danger-500 font-bold">-</span>}
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
                      ))
                    )}
                  </Tbody>
                </Table>
              </CardBody>
            </Card>
          )}

          {activeSubTab === 'evenements' && (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <ShieldAlert className="w-5 h-5 text-warning-500" />
                    <CardTitle>Quota des Événements Familiaux (10 jours/an)</CardTitle>
                  </div>
                  <Button variant="primary" size="sm" onClick={() => { setSelectedItem(null); setShowMotifModal(true); }}>
                    Ajouter
                  </Button>
                </div>
              </CardHeader>
              <CardBody className="p-0">
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
                    {loading ? (
                      <Tr><Td colSpan={4} className="text-center">Chargement...</Td></Tr>
                    ) : motifs.length === 0 ? (
                      <Tr><Td colSpan={4} className="text-center">Aucun événement configuré.</Td></Tr>
                    ) : (
                      motifs.map((motif) => (
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
                      ))
                    )}
                  </Tbody>
                </Table>
              </CardBody>
            </Card>
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
    </div>
  );
}
