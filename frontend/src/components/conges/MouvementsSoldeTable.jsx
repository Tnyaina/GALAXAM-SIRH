import { useState } from 'react';
import { History, PlusCircle } from 'lucide-react';
import congeService from '@/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Card } from '@/components/ui/data-display/Card';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';

const formatDateMouvement = (dateVal, fallbackTimestamp) => {
  const target = dateVal || fallbackTimestamp;
  if (!target) return '—';
  const date = new Date(target);
  if (isNaN(date.getTime())) return String(target);
  return date.toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  });
};

const getMouvementLabel = (type, commentaire) => {
  if (type === 'AJUSTEMENT' && commentaire && commentaire.startsWith('[SOLDE_INITIAL]')) {
    return 'Solde initial (reprise historique)';
  }
  switch (type) {
    case 'ACQUISITION': return 'Acquisition mensuelle';
    case 'BONIFICATION': return 'Bonification ancienneté';
    case 'PRISE': return 'Prise de congé';
    case 'AJUSTEMENT': return 'Ajustement manuel RH';
    case 'ANNULATION': return 'Annulation de demande';
    case 'EXPIRATION': return 'Prescription triennale';
    default: return type;
  }
};

const TYPE_OPTIONS = [
  { value: 'ALL', label: 'Tous les mouvements' },
  { value: 'ACQUISITION', label: 'Acquisition mensuelle (+2.5j)' },
  { value: 'SOLDE_INITIAL', label: 'Solde initial (reprise historique)' },
  { value: 'BONIFICATION', label: 'Bonification d\'ancienneté' },
  { value: 'PRISE', label: 'Prise de congé' },
  { value: 'AJUSTEMENT', label: 'Ajustement manuel RH' },
  { value: 'ANNULATION', label: 'Annulation de demande' },
  { value: 'EXPIRATION', label: 'Expiration triennale' },
];

export default function MouvementsSoldeTable({ idEmploye, mouvements, onAjustementSuccess }) {
  const [filterType, setFilterType] = useState('ALL');
  const [showModalAjustement, setShowModalAjustement] = useState(false);
  const [nbJours, setNbJours] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const safeMouvements = Array.isArray(mouvements) ? mouvements : (mouvements?.items || mouvements?.content || mouvements?.data || []);
  const filteredMouvements = safeMouvements.filter((mvt) => {
    if (!mvt) return false;
    if (filterType === 'ALL') return true;
    if (filterType === 'SOLDE_INITIAL') {
      return mvt.typeMouvement === 'AJUSTEMENT' && mvt.commentaire?.startsWith('[SOLDE_INITIAL]');
    }
    if (filterType === 'AJUSTEMENT') {
      return mvt.typeMouvement === 'AJUSTEMENT' && !mvt.commentaire?.startsWith('[SOLDE_INITIAL]');
    }
    return mvt.typeMouvement === filterType;
  });

  const handleAjustementSubmit = async (e) => {
    e.preventDefault();
    if (!nbJours || !commentaire.trim()) {
      setError('Veuillez saisir le nombre de jours et le motif explicatif.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      await congeService.creerAjustementManuel({
        idEmploye,
        nbJours: parseFloat(nbJours),
        commentaire: commentaire.trim(),
      });
      setNbJours('');
      setCommentaire('');
      setShowModalAjustement(false);
      if (onAjustementSuccess) onAjustementSuccess();
    } catch (err) {
      setError(getErrorMessage(err, 'Erreur lors de la création de l\'ajustement.'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card padding bordered className="space-y-4 font-sans">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
        <div className="flex items-center gap-2">
          <History className="w-4 h-4 text-brand-500" />
          <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
            Registre des Mouvements & Crédits de Solde ({filteredMouvements.length})
          </h4>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Filtre par type */}
          <div className="w-56">
            <Select
              value={filterType}
              onChange={(val) => setFilterType(val)}
              options={TYPE_OPTIONS}
            />
          </div>

          {/* Bouton d'ajustement RH */}
          {idEmploye && (
            <Button
              variant="primary"
              size="xs"
              onClick={() => setShowModalAjustement(true)}
              iconLeft={<PlusCircle size={14} />}
              className="font-bold"
            >
              Ajustement RH
            </Button>
          )}
        </div>
      </div>

      <div className="overflow-x-auto">
        {filteredMouvements.length === 0 ? (
          <div className="p-8">
            <EmptyState
              icon={History}
              title="Aucun mouvement"
              description="Aucun mouvement de solde n'a été enregistré pour ce filtre."
            />
          </div>
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>Date</Th>
                <Th>Type de Mouvement</Th>
                <Th className="text-center">Impact Solde</Th>
                <Th>Motif & Justification</Th>
                <Th>Opérateur</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filteredMouvements.map((m) => {
                const val = Number(m.nbJours || 0);
                const isPositive = val > 0;
                return (
                  <Tr key={m.idMouvement} className="hover:bg-[var(--color-surface-hover)] transition-colors">
                    <Td className="font-mono text-xs text-[var(--color-fg-muted)]">
                      {formatDateMouvement(m.dateMouvement, m.createdAt)}
                    </Td>
                    <Td>
                      <Badge variant="default" size="xs" className="font-semibold">
                        {getMouvementLabel(m.typeMouvement, m.commentaire)}
                      </Badge>
                    </Td>
                    <Td className="text-center font-mono font-bold text-xs">
                      <span className={isPositive ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'}>
                        {isPositive ? `+${val.toFixed(2)}` : val.toFixed(2)} j
                      </span>
                    </Td>
                    <Td className="text-xs text-[var(--color-fg)]">
                      {m.commentaire || '—'}
                    </Td>
                    <Td className="font-mono text-xs text-[var(--color-fg-subtle)]">
                      {m.createdBy ? `Admin (${m.createdBy})` : 'Système Auto'}
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        )}
      </div>

      {/* Modal d'Ajustement Manuel RH (Standardisé Design System) */}
      <Modal
        isOpen={showModalAjustement}
        onClose={() => {
          setShowModalAjustement(false);
          setError(null);
        }}
        title="Ajustement Manuel du Solde"
        icon={<PlusCircle className="w-5 h-5 text-[var(--color-brand-500)]" />}
        size="md"
        footer={
          <div className="flex justify-end gap-2 w-full">
            <Button
              type="button"
              variant="outline"
              size="xs"
              onClick={() => {
                setShowModalAjustement(false);
                setError(null);
              }}
            >
              Annuler
            </Button>
            <Button
              type="submit"
              form="form-ajustement-solde"
              variant="primary"
              size="xs"
              loading={loading}
            >
              Valider l'Ajustement
            </Button>
          </div>
        }
      >
        <form id="form-ajustement-solde" onSubmit={handleAjustementSubmit} className="space-y-4">
          {error && (
            <Alert type="error" message={error} />
          )}

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-1">
              Nombre de jours à ajouter / déduire
            </label>
            <Input
              type="number"
              step="0.5"
              value={nbJours}
              onChange={(e) => setNbJours(e.target.value)}
              placeholder="Ex : +1.5 ou -2"
              required
              className="font-mono text-sm"
            />
            <p className="text-[11px] text-[var(--color-fg-subtle)] mt-1">
              Nombre positif pour créditer des jours, négatif pour déduire.
            </p>
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-1">
              Motif / Justification réglementaire
            </label>
            <Textarea
              value={commentaire}
              onChange={(e) => setCommentaire(e.target.value)}
              placeholder="Justification RH obligatoire (ex: régularisation fin de contrat, rachat...)"
              rows={3}
              required
            />
          </div>
        </form>
      </Modal>
    </Card>
  );
}
