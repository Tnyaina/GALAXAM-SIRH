import { useState } from 'react';
import { CheckSquare, XSquare } from 'lucide-react';
import congeService from '@/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';

const fmtDate = (d) => {
  if (!d) return '-';
  try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return d; }
};

export default function ValidationDemandeModal({ demandesAValider, onActionComplete }) {
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [showRefusModal, setShowRefusModal] = useState(false);
  const [motifRefus, setMotifRefus] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  const handleValider = async (demande) => {
    try {
      setLoading(true);
      setError(null);
      setSuccessMsg(null);
      await congeService.validerDemande(demande.idDemandeConge);
      setSuccessMsg(`La demande #${demande.idDemandeConge} de ${demande.prenom} ${demande.nom} a été validée avec succès.`);
      if (onActionComplete) onActionComplete();
    } catch (err) {
      setError(getErrorMessage(err, 'Erreur lors de la validation de la demande.'));
    } finally {
      setLoading(false);
    }
  };

  const handleRefuserSubmit = async (e) => {
    e.preventDefault();
    if (!selectedDemande || !motifRefus.trim()) {
      setError('Le motif de refus est obligatoire.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setSuccessMsg(null);
      await congeService.refuserDemande(selectedDemande.idDemandeConge, motifRefus.trim());
      setSuccessMsg(`La demande #${selectedDemande.idDemandeConge} a été refusée.`);
      setShowRefusModal(false);
      setSelectedDemande(null);
      setMotifRefus('');
      if (onActionComplete) onActionComplete();
    } catch (err) {
      setError(getErrorMessage(err, 'Erreur lors du refus de la demande.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="rounded-none border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm font-sans">
      <CardHeader>
        <div className="flex items-center justify-between w-full">
          <div className="flex items-center gap-2">
            <CheckSquare className="w-4 h-4 text-success-500" />
            <CardTitle>Circuit de Validation des Demandes de Congé</CardTitle>
          </div>
          <Badge variant={(demandesAValider || []).length > 0 ? 'warning' : 'default'} size="sm">
            {(demandesAValider || []).length} en attente
          </Badge>
        </div>
      </CardHeader>

      <CardBody className="space-y-4">
        {/* Messages */}
        {error && (
          <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
        )}

        {successMsg && (
          <Alert type="success" message={successMsg} />
        )}

        {/* Liste des Demandes à Valider */}
        <div className="overflow-x-auto">
          {(!demandesAValider || demandesAValider.length === 0) ? (
            <div className="p-8 border-t border-[var(--color-border-subtle)]">
                <EmptyState
                    icon={CheckSquare}
                    title="Aucune demande en attente"
                    description="Il n'y a aucune demande de congé en attente de validation pour le moment."
                />
            </div>
          ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>Employé</Th>
                <Th>Type de Congé</Th>
                <Th>Période</Th>
                <Th className="text-center">Durée</Th>
                <Th>Options / Spécificités</Th>
                <Th className="text-right">Actions</Th>
              </Tr>
            </Thead>
            <Tbody>
                {demandesAValider.map((d) => (
                  <Tr key={d.idDemandeConge}>
                    {/* Employé */}
                    <Td>
                      <div className="font-bold text-[var(--color-fg)]">
                        {d.prenom} {d.nom}
                      </div>
                      <div className="text-[11px] text-[var(--color-fg-subtle)] font-data">
                        Matricule #{d.matricule}
                      </div>
                    </Td>

                    {/* Type de congé */}
                    <Td>
                      <span className="font-semibold text-[var(--color-fg)]">
                        {d.libelleTypeConge}
                      </span>
                      {d.libelleMotifEvenement && (
                        <div className="text-[11px] text-warning-500 font-medium">
                          Motif : {d.libelleMotifEvenement}
                        </div>
                      )}
                    </Td>

                    {/* Période */}
                    <Td className="font-data text-xs">
                      Du {fmtDate(d.dateDebut)} au {fmtDate(d.dateFin)}
                      <div className="text-[10px] text-[var(--color-fg-subtle)]">
                        Saisi le {fmtDate(d.dateDemande)} par {d.saisiParIdentifiant || 'Système'}
                      </div>
                    </Td>

                    {/* Durée */}
                    <Td className="text-center font-data font-extrabold text-[var(--color-brand-500)] text-xs">
                      {d.estDemiJournee ? (
                        <div>
                          <span className="text-info-600 dark:text-info-400">0,5j</span>
                          <div className="text-[10px] text-[var(--color-fg-muted)] font-normal">
                            {d.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : 'Matin'}
                          </div>
                        </div>
                      ) : (
                        `${d.nbJoursDecomptes}j`
                      )}
                    </Td>

                    {/* Options / Spécificités */}
                    <Td className="space-y-1">
                      {d.extensionPathologiqueMaternite && (
                        <Badge variant="danger" size="sm">
                          Extension Maladie (119j)
                        </Badge>
                      )}
                      {d.suspendAcquisitionSolde && (
                        <Badge variant="warning" size="sm">
                          Sans Solde
                        </Badge>
                      )}
                      {d.derogationSoldeNegatif && (
                        <Badge variant="warning" size="sm">
                          Solde Négatif
                        </Badge>
                      )}
                      {d.derogationDelaiPreavis && (
                        <Badge variant="primary" size="sm">
                          Préavis Ignoré
                        </Badge>
                      )}
                      {d.commentaire && (
                        <div className="text-[11px] text-[var(--color-fg-muted)]  max-w-xs truncate">
                          &ldquo;{d.commentaire}&rdquo;
                        </div>
                      )}
                    </Td>

                    {/* Actions */}
                    <Td className="text-right">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          variant="success"
                          size="xs"
                          onClick={() => handleValider(d)}
                          loading={loading}
                          className="rounded-none font-bold uppercase"
                        >
                          Valider
                        </Button>
                        <Button
                          variant="danger"
                          size="xs"
                          onClick={() => {
                            setSelectedDemande(d);
                            setShowRefusModal(true);
                          }}
                          loading={loading}
                          className="rounded-none font-bold uppercase"
                        >
                          Refuser
                        </Button>
                      </div>
                    </Td>
                  </Tr>
                ))}
            </Tbody>
          </Table>
          )}
        </div>
      </CardBody>

      {/* Modal de Refus Explicite */}
      {showRefusModal && selectedDemande && (
        <div className="fixed inset-0 bg-ink-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none max-w-md w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-3">
              <h4 className="text-xs font-bold text-[var(--color-danger-500)] flex items-center gap-2 uppercase tracking-wider">
                <XSquare className="w-4 h-4" />
                Refus de la Demande #{selectedDemande.idDemandeConge}
              </h4>
              <button onClick={() => setShowRefusModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-bold">
                ×
              </button>
            </div>

            <form onSubmit={handleRefuserSubmit} className="space-y-4 text-xs">
              <p className="text-[var(--color-fg)]">
                Vous êtes sur le point de refuser la demande de congé de <strong>{selectedDemande.prenom} {selectedDemande.nom}</strong> ({selectedDemande.nbJoursDecomptes} jours).
              </p>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
                  Motif du Refus (Obligatoire) *
                </label>
                <Textarea
                  rows="3"
                  placeholder="Expliquez la raison du refus..."
                  value={motifRefus}
                  onChange={(e) => setMotifRefus(e.target.value)}
                  required
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowRefusModal(false)}
                  className="rounded-none"
                >
                  Annuler
                </Button>
                <Button
                  type="submit"
                  variant="danger"
                  size="sm"
                  loading={loading}
                  className="rounded-none font-bold uppercase"
                >
                  Confirmer le Refus
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </Card>
  );
}
