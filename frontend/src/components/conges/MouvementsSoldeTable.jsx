import { useState } from 'react';
import { History, PlusCircle, Filter, X } from 'lucide-react';
import congeService from '@/api/congeService';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

const formatDateMouvement = (dateVal, fallbackTimestamp) => {
  const target = dateVal || fallbackTimestamp;
  if (!target) return '—';
  const date = new Date(target);
  if (isNaN(date.getTime())) return String(target);
  return date.toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  });
};

const getMouvementLabel = (type) => {
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
      setError(err?.response?.data || err.message || 'Erreur lors de la création de l\'ajustement.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 sm:p-6 space-y-4 font-sans">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-3 border-b border-slate-100 dark:border-ink-800">
        <div className="flex items-center gap-2">
          <History className="w-4 h-4 text-brand-500" />
          <h4 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white">
            Registre des Mouvements & Crédits de Solde ({filteredMouvements.length})
          </h4>
        </div>

        <div className="flex items-center gap-3 w-full sm:w-auto">
          {/* Filtre par type */}
          <div className="flex items-center gap-1.5 bg-slate-50 dark:bg-ink-950 border border-slate-200 dark:border-ink-700 px-2.5 py-1 text-xs">
            <Filter className="w-3.5 h-3.5 text-slate-400" />
            <select
              value={filterType}
              onChange={(e) => setFilterType(e.target.value)}
              className="bg-transparent text-slate-800 dark:text-white focus:outline-none cursor-pointer text-xs font-medium"
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
                  <Tr key={m.idMouvement} className="hover:bg-slate-50/60 dark:hover:bg-ink-800/30 transition-colors">
                    <Td className="font-mono text-xs text-slate-600 dark:text-ink-300">
                      {formatDateMouvement(m.dateMouvement, m.createdAt)}
                    </Td>
                    <Td>
                      <Badge variant="default" size="xs" className="font-semibold">
                        {getMouvementLabel(m.typeMouvement)}
                      </Badge>
                    </Td>
                    <Td className="text-center font-mono font-bold text-xs">
                      <span className={isPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}>
                        {isPositive ? `+${val.toFixed(2)}` : val.toFixed(2)} j
                      </span>
                    </Td>
                    <Td className="text-xs text-slate-800 dark:text-white">
                      {m.commentaire || '—'}
                    </Td>
                    <Td className="font-mono text-xs text-slate-400">
                      {m.createdBy ? `Admin (${m.createdBy})` : 'Système Auto'}
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        )}
      </div>

      {/* Modal d'Ajustement Manuel RH */}
      {showModalAjustement && (
        <div className="fixed inset-0 bg-slate-900/70 backdrop-blur-xs z-50 flex items-center justify-center p-4">
          <div className="bg-white dark:bg-ink-900 border border-slate-200 dark:border-ink-700 max-w-md w-full p-6 space-y-4 shadow-xl">
            <div className="flex items-center justify-between border-b border-slate-100 dark:border-ink-800 pb-3">
              <h4 className="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
                <PlusCircle className="w-4 h-4 text-brand-500" />
                Ajustement Manuel du Solde
              </h4>
              <button
                type="button"
                onClick={() => setShowModalAjustement(false)}
                className="text-slate-400 hover:text-slate-700"
              >
                <X size={16} />
              </button>
            </div>

            {error && (
              <div className="p-2.5 bg-rose-50 border border-rose-200 text-rose-700 text-xs">
                {error}
              </div>
            )}

            <form onSubmit={handleAjustementSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-ink-200 mb-1">
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
                <p className="text-[11px] text-slate-400 mt-1">
                  Nombre positif pour créditer des jours, négatif pour déduire.
                </p>
              </div>

              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-ink-200 mb-1">
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

              <div className="flex justify-end gap-2 pt-2 border-t border-slate-100 dark:border-ink-800">
                <Button
                  type="button"
                  variant="outline"
                  size="xs"
                  onClick={() => setShowModalAjustement(false)}
                >
                  Annuler
                </Button>
                <Button
                  type="submit"
                  variant="primary"
                  size="xs"
                  loading={loading}
                >
                  Valider l'Ajustement
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
