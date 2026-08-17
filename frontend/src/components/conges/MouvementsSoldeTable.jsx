import { useState } from 'react';
import { History, PlusCircle, Filter } from 'lucide-react';
import congeService from '@/api/congeService';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';

const formatDateTime = (timestamp) => {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  return date.toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit'
  });
};

export default function MouvementsSoldeTable({ idEmploye, mouvements, onAjustementSuccess }) {
  const [filterType, setFilterType] = useState('ALL');
  const [showModalAjustement, setShowModalAjustement] = useState(false);
  const [nbJours, setNbJours] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const filteredMouvements = (mouvements || []).filter((mvt) => {
    if (filterType === 'ALL') return true;
    return mvt.typeMouvement === filterType;
  });

  const getTypeBadgeVariant = (type) => {
    switch (type) {
      case 'ACQUISITION': return 'primary';
      case 'BONIFICATION': return 'warning';
      case 'PRISE': return 'info';
      case 'AJUSTEMENT': return 'warning';
      case 'ANNULATION': return 'success';
      case 'EXPIRATION': return 'danger';
      default: return 'default';
    }
  };

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
      setError(err?.response?.data || err.message || 'Erreur lors de la création de l\'ajustement.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="rounded-none border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm font-sans">
      <CardHeader>
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 w-full">
          <div className="flex items-center gap-2">
            <History className="w-4 h-4 text-blue-500" />
            <CardTitle>Historique Comptable des Mouvements de Solde (Ledger)</CardTitle>
          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto">
            {/* Filtre par type */}
            <div className="flex items-center gap-1.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] px-2.5 py-1 text-xs">
              <Filter className="w-3.5 h-3.5 text-[var(--color-fg-muted)]" />
              <select
                value={filterType}
                onChange={(e) => setFilterType(e.target.value)}
                className="bg-transparent text-[var(--color-fg)] focus:outline-none cursor-pointer text-xs"
              >
                <option value="ALL">Tous les mouvements</option>
                <option value="ACQUISITION">Acquisition mensuelle (+2.5j)</option>
                <option value="BONIFICATION">Bonification d'ancienneté</option>
                <option value="PRISE">Prise de congé</option>
                <option value="AJUSTEMENT">Ajustement manuel RH</option>
                <option value="ANNULATION">Annulation de demande</option>
                <option value="EXPIRATION">Expiration triennale</option>
              </select>
            </div>

            {/* Bouton d'ajustement RH */}
            {idEmploye && (
              <Button
                variant="primary"
                size="xs"
                onClick={() => setShowModalAjustement(true)}
                iconLeft={<PlusCircle size={14} />}
                className="rounded-none font-bold uppercase"
              >
                Ajustement RH
              </Button>
            )}
          </div>
        </div>
      </CardHeader>

      <CardBody>
        <div className="overflow-x-auto">
          <Table>
            <Thead>
              <Tr>
                <Th>Date & Heure</Th>
                <Th>Type Mouvement</Th>
                <Th className="text-center">Impact Solde</Th>
                <Th>Motif & Explication Comptable</Th>
                <Th>Opérateur</Th>
              </Tr>
            </Thead>
            <Tbody>
              {filteredMouvements.length === 0 ? (
                <Tr>
                  <Td colSpan="5" className="text-center py-6 text-[var(--color-fg-muted)] ">
                    Aucun mouvement de solde enregistré.
                  </Td>
                </Tr>
              ) : (
                filteredMouvements.map((m) => {
                  const val = Number(m.nbJours || 0);
                  const isPositive = val > 0;
                  return (
                    <Tr key={m.idMouvement}>
                      <Td className="font-data text-xs">
                        {formatDateTime(m.createdAt || m.dateMouvement)}
                      </Td>
                      <Td>
                        <Badge variant={getTypeBadgeVariant(m.typeMouvement)} size="sm">
                          {m.typeMouvement}
                        </Badge>
                      </Td>
                      <Td className="text-center font-data font-extrabold text-xs">
                        <span className={isPositive ? 'text-success-500' : 'text-danger-500'}>
                          {isPositive ? `+${val.toFixed(2)}` : val.toFixed(2)}j
                        </span>
                      </Td>
                      <Td className="text-xs text-[var(--color-fg)]">
                        {m.commentaire || '—'}
                      </Td>
                      <Td className="font-data text-xs text-[var(--color-fg-subtle)]">
                        {m.createdBy ? `Admin (${m.createdBy})` : 'Système Auto'}
                      </Td>
                    </Tr>
                  );
                })
              )}
            </Tbody>
          </Table>
        </div>
      </CardBody>

      {/* Modal d'Ajustement Manuel RH */}
      {showModalAjustement && (
        <div className="fixed inset-0 bg-[var(--color-brand-900)]/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none max-w-md w-full p-6 space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-3">
              <h4 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2 uppercase tracking-wider">
                <PlusCircle className="w-4 h-4 text-[var(--color-brand-500)]" />
                Ajustement Manuel du Solde (RH / DAF)
              </h4>
              <button onClick={() => setShowModalAjustement(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-bold">
                ×
              </button>
            </div>

            {error && (
              <div className="p-3 bg-danger-500/10 border border-danger-500/30 text-xs text-danger-500">
                {error}
              </div>
            )}

            <form onSubmit={handleAjustementSubmit} className="space-y-4 text-xs">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
                  Nombre de jours (+ ou -) *
                </label>
                <Input
                  type="number"
                  step="0.5"
                  placeholder="ex: +2.5 ou -1.0"
                  value={nbJours}
                  onChange={(e) => setNbJours(e.target.value)}
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] mb-1">
                  Motif & Explication Régularisation *
                </label>
                <Textarea
                  rows="3"
                  placeholder="Justification comptable de l'ajustement..."
                  value={commentaire}
                  onChange={(e) => setCommentaire(e.target.value)}
                  required
                />
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowModalAjustement(false)}
                  className="rounded-none"
                >
                  Annuler
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  size="sm"
                  loading={loading}
                  className="rounded-none font-bold uppercase"
                >
                  Enregistrer
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </Card>
  );
}
