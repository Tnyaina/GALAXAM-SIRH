import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { History, PlusCircle, Download, Search, FileText, ArrowUpRight, ArrowDownRight, Calendar } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Card } from '@/components/ui/data-display/Card';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import Pagination from '@/components/ui/navigation/Pagination';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';

const formatDateMouvement = (dateVal, fallbackTimestamp) => {
  const target = dateVal || fallbackTimestamp;
  if (!target) return '-';
  const date = new Date(target);
  if (isNaN(date.getTime())) return String(target);
  return date.toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  });
};

const formatMotifMouvement = (commentaire) => {
  if (!commentaire) return '—';
  const reprise = commentaire.match(/^(?:\[SOLDE_INITIAL\]\s*)?Reprise cutover - Solde initial au (\d{4}-\d{2}-\d{2})(?:\s*;\s*lot\s+(\d+))?\s*:\s*(.*)$/i);
  if (reprise) {
    const date = formatDateMouvement(reprise[1]);
    const details = reprise[3].replace(/^Arrêté DAF\s*:\s*/i, 'Arrêté DAF : ').trim();
    return `Reprise historique au ${date}${reprise[2] ? ` · lot ${reprise[2]}` : ''}${details ? ` · ${details}` : ''}`;
  }
  return commentaire
    .replace(/^\[SOLDE_INITIAL\]\s*/i, '')
    .replace(/^\[REGULARISATION_POINTAGE\]\s*/i, 'Régularisation de pointage · ')
    .replace(/^\[([A-Z0-9_]+)\]\s*/i, '')
    .trim() || '—';
};

const todayLocal = () => {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
};

const getMouvementBadgeConfig = (type, commentaire) => {
  if (type === 'AJUSTEMENT' && commentaire &&
    (commentaire.startsWith('[SOLDE_INITIAL]') || commentaire.startsWith('Reprise - Solde initial'))) {
    return { variant: 'info', label: 'Solde initial (reprise)' };
  }
  if (type === 'AJUSTEMENT' && commentaire?.startsWith('Correction DAF du reliquat de reprise')) {
    return { variant: 'info', label: 'Correction reprise DAF' };
  }
  switch (type) {
    case 'ACQUISITION': return { variant: 'success', label: 'Acquisition mensuelle' };
    case 'BONIFICATION': return { variant: 'purple', label: 'Bonification ancienneté' };
    case 'PRISE': return { variant: 'warning', label: 'Prise de congé' };
    case 'AJUSTEMENT': return { variant: 'neutral', label: 'Ajustement manuel RH' };
    case 'ANNULATION': return { variant: 'neutral', label: 'Annulation' };
    case 'EXPIRATION': return { variant: 'danger', label: 'Expiration' };
    default: return { variant: 'default', label: type };
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
  canValidate = false,
  matricule = '',
  onAjustementSuccess,
  onVoirDemande,
  loadError = null,
  loading: sourceLoading = false,
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
  const [dateMouvement, setDateMouvement] = useState(todayLocal);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [showCorrection, setShowCorrection] = useState(false);
  const [correction, setCorrection] = useState({ jours: '', valeur: '', motif: '' });
  const [correctionResult, setCorrectionResult] = useState(null);
  const [correctionError, setCorrectionError] = useState(null);
  const [correctionLoading, setCorrectionLoading] = useState(false);

  // Données dérivées brutes
  const safeMouvements = useMemo(() => {
    return Array.isArray(mouvements) ? mouvements : (mouvements?.items || mouvements?.content || mouvements?.data || []);
  }, [mouvements]);
  const hasCutover = safeMouvements.some(m => m.typeMouvement === 'AJUSTEMENT'
    && (m.commentaire?.startsWith('[SOLDE_INITIAL]')
      || m.commentaire?.startsWith('Reprise - Solde initial')));
  // valeurSource est un montant de lot pour les crédits, mais un taux unitaire
  // de secours pour les prises : ce n'est pas leur imputation FIFO réelle.
  const hasValeurSource = safeMouvements.some(m => m.valeurSource != null && Number(m.valeurSource) > 0);
  const showFilters = safeMouvements.length > 1 || filterType !== 'ALL' || searchQuery.trim() !== '';

  const [correctionAvant, setCorrectionAvant] = useState({ jours: null, valeur: null });
  const [lotsCorrection, setLotsCorrection] = useState([]);
  const [selectedLotCorrection, setSelectedLotCorrection] = useState(null);

  const ouvrirCorrection = async () => {
    setShowCorrection(true);
    setCorrectionResult(null);
    setCorrectionError(null);
    setCorrectionLoading(true);
    try {
      const situation = await congeService.getValorisationLots(idEmploye);
      const sources = safeMouvements.filter(m => m.typeMouvement === 'AJUSTEMENT'
        && (m.commentaire?.startsWith('[SOLDE_INITIAL]') || m.commentaire?.startsWith('Reprise cutover - Solde initial')));
      const lots = sources.map(source => situation?.lots?.find(lot => Number(lot.idSource) === Number(source.idMouvement))
        || { idSource: source.idMouvement, dateAcquisition: source.dateOrigineLot || source.dateMouvement, jours: 0, valeur: 0 });
      setLotsCorrection(lots);
      const reprise = lots[0];
      setSelectedLotCorrection(reprise?.idSource ?? null);
      const j = String(reprise?.jours ?? 0);
      const v = String(reprise?.valeur ?? 0);
      setCorrectionAvant({ jours: reprise?.jours ?? 0, valeur: reprise?.valeur ?? 0 });
      setCorrection({ jours: j, valeur: v, motif: '' });
    } catch (err) {
      setCorrectionError(getErrorMessage(err, 'Impossible de charger le reliquat de reprise.'));
    } finally {
      setCorrectionLoading(false);
    }
  };

  const soumettreCorrection = async (event) => {
    event.preventDefault();
    setCorrectionLoading(true);
    setCorrectionError(null);
    try {
      const resultat = await congeService.corrigerReliquatCutover(idEmploye, {
        nouveauReliquatJours: correction.jours,
        nouveauReliquatValeur: correction.valeur,
        motif: correction.motif.trim(),
        idLotReprise: selectedLotCorrection,
      });
      setCorrectionResult(resultat);
      onAjustementSuccess?.();
      toast.success('Reliquat de reprise corrigé et historisé.');
    } catch (err) {
      setCorrectionError(getErrorMessage(err, 'Impossible de corriger le reliquat de reprise.'));
    } finally {
      setCorrectionLoading(false);
    }
  };

  // Filtrage combiné (type + recherche textuelle)
  const filteredMouvements = useMemo(() => {
    return safeMouvements.filter((mvt) => {
      if (!mvt) return false;
      if (filterType !== 'ALL') {
        if (filterType === 'SOLDE_INITIAL') {
          if (!(mvt.typeMouvement === 'AJUSTEMENT' &&
            (mvt.commentaire?.startsWith('[SOLDE_INITIAL]') || mvt.commentaire?.startsWith('Reprise - Solde initial')))) {
            return false;
          }
        } else if (filterType === 'AJUSTEMENT') {
          if (!(mvt.typeMouvement === 'AJUSTEMENT' &&
            !mvt.commentaire?.startsWith('[SOLDE_INITIAL]') &&
            !mvt.commentaire?.startsWith('Reprise - Solde initial'))) {
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
        nbJours: Number(String(nbJours).replace(',', '.')),
        commentaire: commentaire.trim(),
        dateMouvement,
      });
      setNbJours('');
      setCommentaire('');
      setDateMouvement(todayLocal());
      setShowModalAjustement(false);
      toast.success('Ajustement manuel de solde enregistré avec succès.');
      if (onAjustementSuccess) onAjustementSuccess();
    } catch (err) {
      setError(getErrorMessage(err, 'Erreur lors de la création de l\'ajustement.'));
    } finally {
      setLoading(false);
    }
  };

  const getMouvementLabel = (type, commentaire) => getMouvementBadgeConfig(type, commentaire).label;

  const statsMouvements = useMemo(() => {
    let totalAcquis = 0;
    let totalConsomme = 0;
    safeMouvements.forEach((m) => {
      const n = Number(m.nbJours || 0);
      if (n > 0) totalAcquis += n;
      else if (n < 0) totalConsomme += Math.abs(n);
    });
    return {
      totalAcquis,
      totalConsomme,
      nbMouvements: safeMouvements.length,
    };
  }, [safeMouvements]);

  // Rendu
  return (
    <Card padding bordered className="space-y-4 font-sans">
      {/* ── En-tête du registre avec actions ── */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
        <div className="flex items-center gap-2">
          <History className="w-4 h-4 text-[var(--color-brand-500)]" />
          <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
            Registre des Mouvements & Crédits de Solde{loadError ? ' (indisponible)' : ` (${filteredMouvements.length})`}
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
          {canValidate && hasCutover && (
            <Button variant="outline" size="sm" onClick={ouvrirCorrection} className="text-xs">
              Corriger la reprise
            </Button>
          )}
        </div>
      </div>

      {/* ── Synthèse des flux du solde (Cartouches) ── */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Solde actuel :
            </span>
            <span className="font-mono text-base font-bold text-[var(--color-fg)] mt-0.5 block">
              {soldeActuel != null ? `${Number(soldeActuel).toFixed(2)} j` : `${(statsMouvements.totalAcquis - statsMouvements.totalConsomme).toFixed(2)} j`}
            </span>
          </div>
          <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex items-center justify-center shrink-0">
            <Calendar size={16} />
          </div>
        </div>

        <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Cumul crédits & reprises
            </span>
            <span className="font-mono text-base font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)] mt-0.5 block">
              {loadError ? 'Indisponible' : `+${statsMouvements.totalAcquis.toFixed(2)} j`}
            </span>
          </div>
          <div className="w-8 h-8 rounded-none bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] dark:text-[var(--color-success-400)] flex items-center justify-center shrink-0">
            <ArrowUpRight size={16} />
          </div>
        </div>

        <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center justify-between">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Cumul débits & prises
            </span>
            <span className="font-mono text-base font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] mt-0.5 block">
              {loadError ? 'Indisponible' : `-${statsMouvements.totalConsomme.toFixed(2)} j`}
            </span>
          </div>
          <div className="w-8 h-8 rounded-none bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] flex items-center justify-center shrink-0">
            <ArrowDownRight size={16} />
          </div>
        </div>
      </div>

      {/* ── Filtres et Recherche rapide ── */}
      {showFilters && <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
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
      </div>}

      <div className="overflow-x-auto">
        {loadError ? (
          <div className="p-5"><Alert type="error" title="Registre indisponible" message={loadError} /></div>
        ) : sourceLoading ? (
          <div className="p-8 flex justify-center"><span className="text-xs text-[var(--color-fg-muted)]">Chargement du registre…</span></div>
        ) : filteredMouvements.length === 0 ? (
          <div className="p-8">
            <EmptyState
              icon={History}
              title="Aucun mouvement"
              description={safeMouvements.length === 0
                ? "Aucun mouvement de solde n'a encore été enregistré pour ce collaborateur."
                : "Aucun résultat ne correspond aux filtres de recherche sélectionnés."}
              bordered={false}
            />
          </div>
        ) : (
          <Table bordered={false}>
            <Thead>
              <Tr>
                <Th>Date</Th>
                <Th>Type de Mouvement</Th>
                <Th className="text-center">Impact Solde</Th>
                <Th className="text-center">Solde Cumulé</Th>
                {hasValeurSource && <Th className="text-right">Valorisation</Th>}
                <Th>Justification</Th>
                <Th>Opérateur</Th>
              </Tr>
            </Thead>
            <Tbody>
              {paginatedMouvements.map((m) => {
                const val = Number(m.nbJours || 0);
                const isPositive = val > 0;
                const badgeCfg = getMouvementBadgeConfig(m.typeMouvement, m.commentaire);
                const demandeMatch = m.commentaire ? m.commentaire.match(/Demande #(\d+)/i) : null;
                const isRegularisation = m.commentaire?.includes('[REGULARISATION_POINTAGE]');
                const repriseMatch = m.commentaire?.match(/^(?:\[SOLDE_INITIAL\]\s*)?Reprise cutover - Solde initial au \d{4}-\d{2}-\d{2}(?:\s*;\s*lot\s+(\d+))?\s*:\s*(.*)$/i);
                const referenceDaf = repriseMatch?.[2]?.replace(/^Arrêté DAF\s*:\s*/i, '').trim();

                return (
                  <Tr key={m.idMouvement} className="hover:bg-[var(--color-surface-hover)] transition-colors">
                    <Td className="font-mono text-xs text-[var(--color-fg-muted)]">
                      {formatDateMouvement(m.dateMouvement, m.createdAt)}
                    </Td>
                    <Td>
                      <Badge variant={badgeCfg.variant} size="xs" className="font-semibold">
                        {badgeCfg.label}
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
                          {Number(m.soldeApres).toFixed(2)} j
                        </span>
                      ) : (
                        <span className="text-[var(--color-fg-subtle)] font-normal">-</span>
                      )}
                    </Td>
                    {hasValeurSource && (
                      <Td className="text-right font-mono text-xs text-[var(--color-fg-muted)]">
                        {m.valeurSource != null && Number(m.valeurSource) > 0
                          ? <span className="inline-flex flex-col items-end">
                              <span className="font-semibold text-[var(--color-fg)]">{formatMoneyAr(m.valeurSource)}{m.typeMouvement === 'PRISE' ? '/j' : ''}</span>
                              <span className="text-[10px] text-[var(--color-fg-muted)]">{m.typeMouvement === 'PRISE' ? 'Taux de référence' : Number(m.nbJours) > 0 ? 'Valeur créditée' : 'Valeur de référence'}</span>
                            </span>
                          : <span className="text-[var(--color-fg-subtle)]">—</span>}
                      </Td>
                    )}
                    <Td className="text-xs text-[var(--color-fg)]">
                      <div className="flex items-center gap-1.5 flex-wrap">
                        {isRegularisation && (
                          <Badge variant="purple" size="xs">
                            Régul. Pointage
                          </Badge>
                        )}
                        {repriseMatch ? (
                          <span className="min-w-0">
                            <span className="block font-medium">{m.dateOrigineLot ? `Exercice ${String(m.dateOrigineLot).slice(0, 4)}` : 'Solde global'}{repriseMatch[1] ? ` · lot ${repriseMatch[1]}` : ''}</span>
                            {referenceDaf && <span className="block text-[10px] text-[var(--color-fg-muted)]">Réf. DAF : {referenceDaf}</span>}
                            <details className="mt-1 text-[10px] text-[var(--color-fg-muted)]">
                              <summary className="w-fit cursor-pointer text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]">Voir le commentaire source</summary>
                              <span className="block max-w-xl break-words pt-1">{m.commentaire}</span>
                            </details>
                          </span>
                        ) : <span>{formatMotifMouvement(m.commentaire)}</span>}
                        {demandeMatch && onVoirDemande && (
                          <button
                            type="button"
                            onClick={() => onVoirDemande(demandeMatch[1])}
                            className="inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] font-mono font-medium rounded-none text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] bg-[var(--color-brand-500)]/10 hover:bg-[var(--color-brand-500)]/20 transition-colors"
                            title={`Consulter le détail de la demande #${demandeMatch[1]}`}
                          >
                            <FileText size={10} />
                            Voir demande #{demandeMatch[1]}
                          </button>
                        )}
                      </div>
                    </Td>
                    <Td className="font-mono text-xs text-[var(--color-fg-subtle)]">
                      <span title={m.createdBy ? `Identifiant opérateur : ${m.createdBy}` : undefined}>{m.createdBy ? 'Admin' : 'Système'}</span>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        )}
      </div>
      {filteredMouvements.some(m => m.typeMouvement === 'PRISE' && m.valeurSource != null && Number(m.valeurSource) > 0) && (
        <p className="text-xs text-[var(--color-fg-muted)]">
          Pour une prise, le taux affiché sert de référence en cas de congé anticipé. La valeur réellement consommée dépend des droits utilisés, du plus ancien au plus récent.
        </p>
      )}

      {/* Pagination Registre */}
      {filteredMouvements.length > pageSize && (
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
        title="Ajustement du solde"
        icon={<PlusCircle className="w-5 h-5 text-[var(--color-brand-500)]" />}
        size="md"
        footer={
          <div className="flex justify-end gap-2 w-full">
            <Button
              type="button"
              variant="outline"
              size="sm"
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
              size="sm"
              loading={loading}
            >
              Valider l'ajustement
            </Button>
          </div>
        }
      >
        <form id="form-ajustement-solde" onSubmit={handleAjustementSubmit} className="space-y-4">
          {error && (
            <Alert type="error" message={error} />
          )}

          <div>
            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1" htmlFor="date-ajustement-solde">
              Date d’effet du mouvement *
            </label>
            <Input
              id="date-ajustement-solde"
              type="date"
              value={dateMouvement}
              onChange={(e) => setDateMouvement(e.target.value)}
              required
            />
            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Cette date détermine à partir de quand l’ajustement entre dans l’historique du solde.</p>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
              Nombre de jours (positif ou négatif) *
            </label>
            <Input
              type="number"
              step="0.01"
              value={nbJours}
              onChange={(e) => setNbJours(e.target.value)}
              placeholder="Ex. +1.5 ou -2"
              required
              className="font-mono text-sm"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
              Motif réglementaire *
            </label>
            <Textarea
              value={commentaire}
              onChange={(e) => setCommentaire(e.target.value)}
              placeholder="Régularisation, fin de contrat, rachat..."
              rows={3}
              required
            />
          </div>
        </form>
      </Modal>
      <Modal
        isOpen={showCorrection}
        onClose={() => setShowCorrection(false)}
        title="Corriger le reliquat de reprise"
        size="md"
      >
        {correctionError && <Alert type="error" message={correctionError} />}

        {correctionResult ? (
          <div className="space-y-4 font-sans text-sm">
            {/* Succès */}
            <div className="p-3 bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/30 flex items-start gap-3">
              <span className="text-[var(--color-success-600)] text-xl leading-none mt-0.5">✓</span>
              <div>
                <p className="font-semibold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">Reliquat corrigé et historisé</p>
                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">Un mouvement d'ajustement a été généré dans le grand-livre.</p>
              </div>
            </div>

            {/* Résumé après */}
            <div className="grid grid-cols-2 gap-3">
              <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">Nouveau reliquat</span>
                <span className="font-mono text-base font-bold text-[var(--color-fg)]">{correctionResult.nouveauReliquatJours} j</span>
              </div>
              <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">Nouvelle valeur</span>
                <span className="font-mono text-base font-bold text-[var(--color-fg)]">{formatMoneyAr(correctionResult.nouvelleValeur)}</span>
              </div>
            </div>

            {/* Écart à régulariser */}
            {Number(correctionResult.regularisationAExaminer) !== 0 && (
              <div className="p-3 border border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10">
                <p className="text-xs font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] mb-1">Écart de valorisation sur prises antérieures</p>
                <p className="font-mono text-lg font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]">
                  {Number(correctionResult.regularisationAExaminer) > 0 ? '+' : ''}{formatMoneyAr(correctionResult.regularisationAExaminer)}
                </p>
                <p className="text-xs text-[var(--color-fg-muted)] mt-1.5">
                  Différence constatée sur les congés déjà consommés avant correction.
                </p>
                {Number(correctionResult.regularisationAExaminer) > 0 && correctionResult.idCorrection && (
                  <p className="text-xs text-[var(--color-fg-muted)] mt-1.5">
                    Référence à saisir dans le motif de la régularisation Paie : <strong>CORRECTION_CUTOVER#{correctionResult.idCorrection}</strong>
                  </p>
                )}
                {Number(correctionResult.regularisationAExaminer) < 0 && (
                  <p className="text-xs text-[var(--color-fg-muted)] mt-1.5">
                    Écart conservé pour examen DAF. Aucune retenue automatique sur la paie.
                  </p>
                )}
                {Number(correctionResult.regularisationAExaminer) > 0 && (
                  <Link
                    to="/paie?tab=periodes"
                    className="inline-block mt-2 border border-[var(--color-border)] px-3 py-1.5 text-xs font-bold text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                  >
                    Campagnes de paie →
                  </Link>
                )}
              </div>
            )}
          </div>
        ) : (
          <form onSubmit={soumettreCorrection} className="space-y-4 font-sans text-sm">

            {/* Information */}
            <div className="p-2.5 bg-[var(--color-brand-500)]/8 border-l-4 border-[var(--color-brand-500)]">
              <p className="text-xs text-[var(--color-fg-muted)]">
                Corrigez seulement une erreur du solde repris. Ce lot exclut les acquisitions ultérieures ; les prises déjà clôturées restent inchangées.
              </p>
            </div>
            {lotsCorrection.length > 1 && <div>
              <label className="block text-xs font-semibold mb-1">Lot à corriger</label>
              <Select value={selectedLotCorrection || ''} onChange={(value) => {
                const lot = lotsCorrection.find(item => String(item.idSource) === String(value));
                setSelectedLotCorrection(lot?.idSource ?? null);
                setCorrectionAvant({ jours: lot?.jours ?? 0, valeur: lot?.valeur ?? 0 });
                setCorrection({ jours: String(lot?.jours ?? 0), valeur: String(lot?.valeur ?? 0), motif: '' });
              }} options={lotsCorrection.map((lot, index) => ({ value: lot.idSource, label: `Lot ${index + 1} · ${lot.dateAcquisition || 'origine inconnue'} · ${lot.jours} j` }))} />
            </div>}

            {/* État actuel (avant) */}
            {correctionLoading ? (
              <div className="py-4 text-center text-xs text-[var(--color-fg-muted)]">Chargement du reliquat actuel…</div>
            ) : correctionAvant.jours !== null && (
              <div>
                <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-2">Reste du lot de reprise uniquement :</p>
                <div className="grid grid-cols-2 gap-2">
                  <div className="p-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                    <span className="text-[10px] text-[var(--color-fg-subtle)] block">Jours</span>
                    <span className="font-mono font-bold text-sm text-[var(--color-fg)]">{Number(correctionAvant.jours).toFixed(2)} j</span>
                  </div>
                  <div className="p-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                    <span className="text-[10px] text-[var(--color-fg-subtle)] block">Valeur</span>
                    <span className="font-mono font-bold text-sm text-[var(--color-fg)]">{formatMoneyAr(correctionAvant.valeur)}</span>
                  </div>
                </div>
              </div>
            )}

            <div className="border-t border-[var(--color-border-subtle)] pt-4 space-y-4">
              <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Nouvelles valeurs corrigées :</p>

              <div>
                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                  Jours restants après correction <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Input
                  type="number" min="0" step="0.01" required disabled={correctionLoading}
                  value={correction.jours}
                  onChange={e => setCorrection(prev => ({ ...prev, jours: e.target.value }))}
                  placeholder="Ex. 4.25"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                  Valeur restante corrigée (Ar) <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <MoneyInput
                  min="0" maxDecimals={2} required disabled={correctionLoading}
                  value={correction.valeur}
                  onValueChange={value => setCorrection(prev => ({ ...prev, valeur: value }))}
                  placeholder="Ex. 32 894"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                  Motif de la correction <span className="text-[var(--color-danger-500)]">*</span>
                </label>
                <Textarea
                  required rows={2} disabled={correctionLoading} value={correction.motif}
                  onChange={e => setCorrection(prev => ({ ...prev, motif: e.target.value }))}
                  placeholder="Justification de la correction"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
              <Button type="button" variant="ghost" size="sm" onClick={() => setShowCorrection(false)}>Annuler</Button>
              <Button type="submit" variant="primary" size="sm" loading={correctionLoading}>Enregistrer la correction</Button>
            </div>
          </form>
        )}
      </Modal>
    </Card>
  );
}
