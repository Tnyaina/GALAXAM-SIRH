import { useState } from 'react';
import { CheckSquare, Check, X, ShieldAlert, Inbox, XSquare } from 'lucide-react';
import congeService from '@/api/congeService';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import EmptyState from '@/components/ui/data-display/EmpyState';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';

// Utilities
const formatDate = (timestamp) => {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  return date.toLocaleDateString('fr-FR');
};

export default function ValidationTab({ demandesAValider = [], onReload }) {
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [showRefusModal, setShowRefusModal] = useState(false);
  const [showValiderModal, setShowValiderModal] = useState(false);
  const [motifRefus, setMotifRefus] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const toast = useToast();

  const handleValiderConfirm = async () => {
    if (!selectedDemande) return;
    try {
      setLoading(true);
      setError(null);
      await congeService.validerDemande(selectedDemande.idDemandeConge);
      toast({
        title: 'Succès',
        message: `La demande #${selectedDemande.idDemandeConge} a été validée avec succès.`,
        type: 'success',
        duration: 3000
      });
      setShowValiderModal(false);
      setSelectedDemande(null);
      if (onReload) onReload();
    } catch (err) {
      setError(err?.response?.data || err.message || 'Erreur lors de la validation.');
    } finally {
      setLoading(false);
    }
  };

  const handleRefuserConfirm = async () => {
    if (!selectedDemande || !motifRefus.trim()) {
      setError('Le motif de refus est obligatoire.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await congeService.refuserDemande(selectedDemande.idDemandeConge, motifRefus.trim());
      toast({
        title: 'Refus enregistré',
        message: `La demande #${selectedDemande.idDemandeConge} a été refusée.`,
        type: 'success',
        duration: 3000
      });
      setShowRefusModal(false);
      setSelectedDemande(null);
      setMotifRefus('');
      if (onReload) onReload();
    } catch (err) {
      setError(err?.response?.data || err.message || 'Erreur lors du refus.');
    } finally {
      setLoading(false);
    }
  };

  const initValidation = (demande) => {
    setSelectedDemande(demande);
    setShowValiderModal(true);
  };

  const initRefus = (demande) => {
    setSelectedDemande(demande);
    setMotifRefus('');
    setShowRefusModal(true);
  };

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Workflow Stepper simplifié */}
      <div className="flex items-center gap-4 bg-[var(--color-surface)] p-4 border border-[var(--color-border-subtle)]">
         <div className="flex items-center gap-2 text-[var(--color-fg-muted)]">
           <div className="w-8 h-8 rounded-full bg-[var(--color-bg-subtle)] flex items-center justify-center font-data text-xs font-bold">1</div>
           <span className="text-sm font-semibold uppercase tracking-wide">Soumission</span>
         </div>
         <div className="h-px bg-[var(--color-border-subtle)] flex-1 mx-2" />
         <div className="flex items-center gap-2 text-[var(--color-brand-500)]">
           <div className="w-8 h-8 rounded-full bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 flex items-center justify-center font-data text-xs font-bold">2</div>
           <span className="text-sm font-semibold uppercase tracking-wide">Validation RH/DAF</span>
         </div>
         <div className="h-px bg-[var(--color-border-subtle)] flex-1 mx-2" />
         <div className="flex items-center gap-2 text-[var(--color-fg-subtle)]">
           <div className="w-8 h-8 rounded-full bg-[var(--color-bg-subtle)] flex items-center justify-center font-data text-xs font-bold">3</div>
           <span className="text-sm font-semibold uppercase tracking-wide">Traitement</span>
         </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between w-full">
            <div className="flex items-center gap-2">
              <CheckSquare className="w-5 h-5 text-[var(--color-warning-500)]" />
              <CardTitle>File d'attente de Validation</CardTitle>
            </div>
            <Badge variant={demandesAValider.length > 0 ? 'warning' : 'default'} size="sm">
              {demandesAValider.length} en attente
            </Badge>
          </div>
        </CardHeader>

        <CardBody className="space-y-4 p-0 sm:p-6">
          {error && <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />}

          {demandesAValider.length === 0 ? (
            <EmptyState 
              icon={Inbox} 
              title="Aucune demande en attente" 
              description="Toutes les demandes de congés ont été traitées." 
            />
          ) : (
            <div className="overflow-x-auto">
              <Table>
                <Thead>
                  <Tr>
                    <Th>Employé</Th>
                    <Th>Type</Th>
                    <Th>Période</Th>
                    <Th className="text-center">Durée</Th>
                    <Th>Remarques</Th>
                    <Th className="text-right">Actions</Th>
                  </Tr>
                </Thead>
                <Tbody>
                  {demandesAValider.map((d) => (
                    <Tr key={d.idDemandeConge} className="hover:bg-[var(--color-surface-hover)]">
                      <Td className="font-semibold text-[var(--color-fg)]">
                        {d.prenom} {d.nom}
                      </Td>
                      <Td>
                        <Badge variant="brand">{d.codeTypeConge}</Badge>
                      </Td>
                      <Td className="font-data text-xs text-[var(--color-fg-muted)]">
                        Du {formatDate(d.dateDebut)} <br />
                        Au {formatDate(d.dateFin)}
                      </Td>
                      <Td className="text-center font-data font-bold text-[var(--color-fg)]">
                        {d.nbJoursDecomptes} j
                      </Td>
                      <Td>
                        {d.justificatifFourni && (
                          <Badge variant="success" size="sm" className="mb-1">
                            Justificatif fourni
                          </Badge>
                        )}
                        {!d.justificatifFourni && d.codeTypeConge === 'MALADIE' && (
                          <Badge variant="danger" size="sm" className="mb-1">
                            <ShieldAlert className="w-3 h-3 mr-1 inline" /> Justificatif manquant
                          </Badge>
                        )}
                        {d.derogationSoldeNegatif && (
                          <Badge variant="danger" size="sm" className="mb-1">
                            Dépassement Solde
                          </Badge>
                        )}
                        {d.motif && (
                          <div className="text-xs text-[var(--color-fg-subtle)] truncate max-w-[150px]" title={d.motif}>
                            &ldquo;{d.motif}&rdquo;
                          </div>
                        )}
                      </Td>
                      <Td className="text-right space-x-2">
                        <Button
                          variant="danger"
                          size="sm"
                          iconLeft={<X size={14} />}
                          onClick={() => initRefus(d)}
                        >
                          Refuser
                        </Button>
                        <Button
                          variant="success"
                          size="sm"
                          iconLeft={<Check size={14} />}
                          onClick={() => initValidation(d)}
                        >
                          Valider
                        </Button>
                      </Td>
                    </Tr>
                  ))}
                </Tbody>
              </Table>
            </div>
          )}
        </CardBody>
      </Card>

      {/* Dialog Confirmation Validation */}
      <ConfirmDialog
        isOpen={showValiderModal}
        title="Confirmer la validation"
        message={`Êtes-vous sûr de vouloir valider la demande de ${selectedDemande?.prenom} ${selectedDemande?.nom} (${selectedDemande?.nbJoursDecomptes} jours de ${selectedDemande?.codeTypeConge}) ?`}
        confirmLabel="Valider la demande"
        confirmVariant="success"
        onConfirm={handleValiderConfirm}
        onClose={() => { setShowValiderModal(false); setSelectedDemande(null); }}
        loading={loading}
      />

      {/* Modale Refus */}
      <Modal
        isOpen={showRefusModal}
        onClose={() => { setShowRefusModal(false); setSelectedDemande(null); setMotifRefus(''); }}
        title={`Refuser la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''}`}
        icon={<XSquare size={20} />}
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={() => { setShowRefusModal(false); setSelectedDemande(null); setMotifRefus(''); }}>
              Annuler
            </Button>
            <Button variant="danger" onClick={handleRefuserConfirm} loading={loading}>
              Confirmer le refus
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Alert 
            type="warning" 
            message="Le refus d'une demande de congé doit être justifié." 
          />
          <div className="space-y-2">
            <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Motif du refus *</label>
            <Textarea
              value={motifRefus}
              onChange={(e) => setMotifRefus(e.target.value)}
              placeholder="Indiquez la raison du refus (ex: Nécessité de service, effectif insuffisant...)"
              rows={4}
              disabled={loading}
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
