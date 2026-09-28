import { useState, useMemo } from 'react';
import { History, PlusCircle, Download, Search } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
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
import Pagination from '@/components/ui/navigation/Pagination';
import { useToast } from '@/components/ui/feedback/Toast';

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
  { value: 'ACQUISITION', label: 'Acquisition mensuelle' },
  { value: 'SOLDE_INITIAL', label: 'Solde initial (reprise historique)' },
  { value: 'BONIFICATION', label: 'Bonification d\'ancienneté' },
  { value: 'PRISE', label: 'Prise de congé' },
  { value: 'AJUSTEMENT', label: 'Ajustement manuel RH' },
  { value: 'ANNULATION', label: 'Annulation de demande' },
  { value: 'EXPIRATION', label: 'Expiration' },
];

export default function MouvementsSoldeTable({
  idEmploye,
  mouvements,
  soldeActuel = null,
  canWrite = false,
  matricule = '',
  onAjustementSuccess
}) {
  const toast = useToast();

  // État local
  const [filterType, setFilterType] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [showModalAjustement, setShowModalAjustement] = useState(false);
  const [nbJours, setNbJours] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Données dérivées brutes
  const safeMouvements = useMemo(() => {
    return Array.isArray(mouvements) ? mouvements : (mouvements?.items || mouvements?.content || mouvements?.data || []);
  }, [mouvements]);

  // Filtrage combiné (type + recherche textuelle)
  const filteredMouvements = useMemo(() => {
    return safeMouvements.filter((mvt) => {
      if (!mvt) return false;
      if (filterType !== 'ALL') {
        if (filterType === 'SOLDE_INITIAL') {
          if (!(mvt.typeMouvement === 'AJUSTEMENT' && mvt.commentaire?.startsWith('[SOLDE_INITIAL]'))) {
            return false;
          }
        } else if (filterType === 'AJUSTEMENT') {
          if (!(mvt.typeMouvement === 'AJUSTEMENT' && !mvt.commentaire?.startsWith('[SOLDE_INITIAL]'))) {
            return false;
          }
        } else if (mvt.typeMouvement !== filterType) {
          return false;
        }
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const typeLabel = getMouvementLabel(mvt.typeMouvement, mvt.commentaire).toLowerCase();
        const comment = (mvt.commentaire || '').toLowerCase();
        const operator = (mvt.createdBy ? String(mvt.createdBy) : 'admin système').toLowerCase();
        const dateStr = formatDateMouvement(mvt.dateMouvement, mvt.createdAt).toLowerCase();
        if (!typeLabel.includes(q) && !comment.includes(q) && !operator.includes(q) && !dateStr.includes(q)) {
          return false;
        }
      }

      return true;
    });
  }, [safeMouvements, filterType, searchQuery]);

  const paginatedMouvements = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return filteredMouvements.slice(start, start + pageSize);
  }, [filteredMouvements, currentPage, pageSize]);

  const handleFilterChange = (val) => {
    setFilterType(val);
    setCurrentPage(1);
  };

  // Export CSV
  const handleExportCsv = () => {
    if (!filteredMouvements.length) return;

    const headers = ['Date', 'Type de mouvement', 'Impact (jours)', 'Solde cumulé (jours)', 'Motif / Justification', 'Opérateur'];
    const rows = filteredMouvements.map(m => [
      formatDateMouvement(m.dateMouvement, m.createdAt),
      `"${(getMouvementLabel(m.typeMouvement, m.commentaire) || '').replace(/"/g, '""')}"`,
      Number(m.nbJours || 0).toFixed(2),
      m.soldeApres !== null && m.soldeApres !== undefined ? Number(m.soldeApres).toFixed(2) : '',
      `"${(m.commentaire || '').replace(/"/g, '""')}"`,
      `"${(m.createdBy ? `Admin (#${m.createdBy})` : 'Système Auto').replace(/"/g, '""')}"`
    ]);

    const csvContent = [headers.join(';'), ...rows.map(r => r.join(';'))].join('\r\n');
    const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    const fileSuffix = matricule ? `_${matricule}` : idEmploye ? `_${idEmploye}` : '';
    const dateStr = new Date().toISOString().slice(0, 10);
    a.download = `registre_conges${fileSuffix}_${dateStr}.csv`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Registre des mouvements exporté en CSV avec succès.');
  };

  // Actions utilisateur
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
      toast.success('Ajustement manuel de solde enregistré avec succès.');
      if (onAjustementSuccess) onAjustementSuccess();
    } catch (err) {
      setError(getErrorMessage(err, 'Erreur lors de la création de l\'ajustement.'));
    } finally {
      setLoading(false);
    }
  };

  // Rendu
  return (
    <Card padding bordered className="space-y-4 font-sans">
      {/* ── En-tête du registre avec actions ── */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
        <div className="flex items-center gap-2">
          <History className="w-4 h-4 text-[var(--color-brand-500)]" />
          <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
            Registre des Mouvements & Crédits de Solde ({filteredMouvements.length})
          </h4>
        </div>

        <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto justify-end">
          {/* Bouton Export CSV */}
          <Button
            variant="outline"
            size="sm"
            onClick={handleExportCsv}
            disabled={filteredMouvements.length === 0}
            iconLeft={<Download size={13} />}
            className="text-xs"
            title="Exporter l'historique complet des mouvements au format CSV"
          >
            Export CSV
          </Button>

          {/* Bouton d'ajustement RH */}
          {canWrite && idEmploye && (
            <Button
              variant="primary"
              size="sm"
              onClick={() => setShowModalAjustement(true)}
              iconLeft={<PlusCircle size={14} />}
              className="font-bold text-xs"
            >
              Ajustement RH
            </Button>
          )}
        </div>
      </div>

      {soldeActuel != null && (
        <p className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 text-xs text-[var(--color-fg-muted)]">
          Solde actuel selon le backend : <strong className="text-[var(--color-fg)]">{Number(soldeActuel).toFixed(2)} j</strong>
        </p>
      )}

      {/* ── Filtres et Recherche rapide ── */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
        {/* Filtre par type */}
        <div className="w-full sm:w-64">
          <Select
            value={filterType}
            onChange={handleFilterChange}
            options={TYPE_OPTIONS}
          />
        </div>

        {/* Champ de recherche rapide */}
        <div className="relative w-full sm:w-64">
          <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-subtle)] pointer-events-none" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setCurrentPage(1);
            }}
            placeholder="Rechercher motif, opérateur..."
            className="w-full pl-8 pr-3 py-1.5 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-surface)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] focus:outline-none focus:border-[var(--color-brand-500)]"
          />
        </div>
      </div>

      <div className="overflow-x-auto">
        {filteredMouvements.length === 0 ? (
          <div className="p-8">
            <EmptyState
              icon={History}
              title="Aucun mouvement"
              description={safeMouvements.length === 0
                ? "Aucun mouvement de solde n'a encore été enregistré pour ce collaborateur."
                : "Aucun résultat ne correspond aux filtres de recherche sélectionnés."}
            />
          </div>
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th>Date</Th>
                <Th>Type de Mouvement</Th>
                <Th className="text-center">Impact Solde</Th>
                <Th className="text-center">Solde Cumulé</Th>
                <Th>Motif & Justification</Th>
                <Th>Opérateur</Th>
              </Tr>
            </Thead>
            <Tbody>
              {paginatedMouvements.map((m) => {
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
                    <Td className="text-center font-mono font-bold text-xs font-data">
                      <span className={isPositive ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'}>
                        {isPositive ? `+${val.toFixed(2)}` : val.toFixed(2)} j
                      </span>
                    </Td>
                    <Td className="text-center font-mono font-bold text-xs font-data">
                      {m.soldeApres !== null && m.soldeApres !== undefined ? (
                        <span className={m.soldeApres >= 0 ? 'text-[var(--color-fg)]' : 'text-[var(--color-danger-600)]'}>
                          {m.soldeApres >= 0 ? `+${Number(m.soldeApres).toFixed(2)}` : Number(m.soldeApres).toFixed(2)} j
                        </span>
                      ) : (
                        <span className="text-[var(--color-fg-subtle)] font-normal">—</span>
                      )}
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

      {/* Pagination Registre */}
      {filteredMouvements.length > 0 && (
        <Pagination
          currentPage={currentPage}
          totalItems={filteredMouvements.length}
          pageSize={pageSize}
          onPageChange={setCurrentPage}
          onPageSizeChange={(newSize) => {
            setPageSize(newSize);
            setCurrentPage(1);
          }}
          className="bg-transparent border-t-0 pt-1"
        />
      )}

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
