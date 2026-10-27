import { useMemo, useState } from 'react';
import { Archive, ChevronDown, FileText, Layers3 } from 'lucide-react';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Loading from '@/components/ui/feedback/Loading';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { formatMoneyAr } from '@/utils/moneyUtils';

const number = (value) => value == null || !Number.isFinite(Number(value)) ? null : Number(value);
const formatDays = (value) => value == null ? '—' : `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(value)} j`;
const formatDate = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : '—';
const fmtMoney = (value) => value == null ? 'Non déterminée' : formatMoneyAr(value);
const isOpening = (lot) => lot.origine === 'REPRISE_CUTOVER';

const fifoRank = (lot) => isOpening(lot) ? 0 : lot.origine === 'AJUSTEMENT' ? 1 : 2;

function movementKind(movement) {
  if (movement.typeMouvement === 'AJUSTEMENT' && movement.commentaire?.includes('Reprise cutover')) return 'REPRISE';
  return movement.typeMouvement;
}

function lotLabel(lot) {
  if (isOpening(lot)) return lot.source?.dateOrigineLot ? 'Lot historique repris' : 'Reprise initiale agrégée';
  if (lot.origine === 'ACQUISITION') return 'Acquisition mensuelle';
  if (lot.origine === 'BONIFICATION') return 'Bonification';
  if (lot.origine === 'AJUSTEMENT') return 'Ajustement';
  return lot.origine || 'Origine non précisée';
}

export default function DroitsLotsPanel({ situation, movements = [], loading = false, error = null, movementsError = null, onRetry, onVoirDemande }) {
  const [selectedId, setSelectedId] = useState(null);
  const sourceMovements = useMemo(() => new Map((Array.isArray(movements) ? movements : []).map((item) => [Number(item.idMouvement), item])), [movements]);
  const rows = useMemo(() => {
    if (!situation) return [];
    const remaining = Array.isArray(situation.lots) ? situation.lots : [];
    const sourceIds = new Set(remaining.map((lot) => Number(lot.idSource)));
    const imputationOrigins = new Map((situation.imputations || []).map((item) => [Number(item.idSource), item.origine]));
    const imputationSourceIds = new Set((situation.imputations || []).filter((item) => item.idSource > 0).map((item) => Number(item.idSource)));
    const movementsAsLots = (Array.isArray(movements) ? movements : [])
      .filter((movement) => ['ACQUISITION', 'BONIFICATION'].includes(movement.typeMouvement)
        || (movement.typeMouvement === 'AJUSTEMENT' && Number(movement.nbJours) > 0 && movement.valeurSource != null))
      .filter((movement) => imputationSourceIds.has(Number(movement.idMouvement)) && !sourceIds.has(Number(movement.idMouvement)))
      .map((movement) => ({
        idSource: movement.idMouvement,
        origine: imputationOrigins.get(Number(movement.idMouvement)) || movementKind(movement),
        dateAcquisition: movement.dateOrigineLot || movement.dateMouvement,
        jours: 0,
        valeur: 0,
        exhausted: true,
      }));
    const merged = [...remaining, ...movementsAsLots];
    return merged.sort((a, b) => fifoRank(a) - fifoRank(b)
      || String(a.dateAcquisition || '').localeCompare(String(b.dateAcquisition || ''))
      || Number(a.idSource) - Number(b.idSource)).map((lot) => {
      const source = sourceMovements.get(Number(lot.idSource));
      const history = (situation.imputations || []).filter((item) => Number(item.idSource) === Number(lot.idSource));
      // In the backend trace, a consumption is positive and a cancellation/restoration is negative.
      const usedDays = history.reduce((sum, item) => sum + Number(item.jours || 0), 0);
      const usedValue = history.reduce((sum, item) => sum + Number(item.valeur || 0), 0);
      const remainingDays = number(lot.jours) ?? 0;
      const remainingValue = number(lot.valeur);
      const initialDays = source?.nbJours != null ? Number(source.nbJours) : (remainingDays + usedDays);
      const initialValue = source?.valeurSource != null ? Number(source.valeurSource)
        : remainingValue == null ? null : remainingValue + usedValue;
      // A certified aggregate opening stock does not identify a historical per-day rate.
      const unitRate = (!isOpening(lot) || source?.dateOrigineLot) && initialDays > 0 && initialValue != null ? initialValue / initialDays : null;
      return { ...lot, source, history, usedDays, usedValue, remainingDays, remainingValue, initialDays, initialValue, unitRate };
    });
  }, [situation, movements, sourceMovements]);
  const selected = rows.find((lot) => Number(lot.idSource) === Number(selectedId));

  if (loading && !situation) return <Card padding bordered><div className="p-8 flex justify-center"><Loading size="sm" label="Chargement des lots valorisés…" /></div></Card>;
  if (error && !situation) return <Alert type="error" title="Lots indisponibles" message={error} actionLabel="Réessayer" onAction={onRetry} />;
  if (!situation) return <Alert type="warning" title="Situation valorisée indisponible" message="Aucune donnée de lots n'est disponible pour ce collaborateur." actionLabel="Réessayer" onAction={onRetry} />;

  return (
    <div className="space-y-4">
      {error && <Alert type="error" title="Actualisation des lots impossible" message={error} actionLabel="Réessayer" onAction={onRetry} />}
      {movementsError && <Alert type="warning" title="Registre incomplet" message={`Les mouvements sources ne sont pas disponibles : ${movementsError}. Les lots restants restent affichés, mais leur détail d'origine peut être incomplet.`} actionLabel="Réessayer" onAction={onRetry} />}
      <Card padding bordered className="space-y-3">
        <div className="flex flex-wrap justify-between items-start gap-3">
          <div>
            <h4 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2"><Layers3 size={16} /> Droits et lots valorisés</h4>
            <p className="text-xs text-[var(--color-fg-muted)] mt-1">Ordre FIFO : reprise, ajustements, puis acquisitions les plus anciennes.</p>
          </div>
          <div className="text-right">
            <p className="text-[10px] uppercase tracking-wide text-[var(--color-fg-muted)]">Stock disponible</p>
            <p className="text-sm font-bold text-[var(--color-fg)]">{formatDays(number(situation.soldeJours))} · {fmtMoney(number(situation.soldeValorisation))}</p>
          </div>
        </div>
        {number(situation.joursSansLot) > 0 && <Alert type="warning" message={`${formatDays(number(situation.joursSansLot))} sans imputation à un lot valorisé. Ce volume n'est pas ventilé artificiellement.`} />}
        {rows.length === 0 ? (
          <EmptyState icon={Archive} title="Aucun lot disponible" description="Aucun lot restant ni mouvement source relié à une imputation n'est exposé pour ce collaborateur." bordered={false} />
        ) : (
          <div className="overflow-x-auto">
            <Table bordered={false} aria-label="Synthèse chronologique des lots de congés">
              <Thead><Tr><Th>Lot</Th><Th>Origine</Th><Th className="text-right">Jours restants</Th><Th className="text-right">Taux du lot</Th><Th className="text-right">Valeur restante</Th><Th>Détail</Th></Tr></Thead>
              <Tbody>
                {rows.map((lot, index) => (
                  <Tr key={lot.idSource} selected={selectedId === lot.idSource}>
                    <Td><div className="flex items-center gap-2"><span className="text-xs font-semibold text-[var(--color-fg)]">{index + 1}. {lotLabel(lot)}</span>{isOpening(lot) && !lot.source?.dateOrigineLot && <Badge variant="info" size="xs">Global</Badge>}</div></Td>
                    <Td className="text-xs">{isOpening(lot) && !lot.source?.dateOrigineLot ? <span className="text-[var(--color-fg-muted)]">Origine non détaillée</span> : (
                      <span className="block">
                        {lot.dateAcquisition && <span className="block font-semibold text-[var(--color-fg)]">Exercice {String(lot.dateAcquisition).slice(0, 4)}</span>}
                        {!isOpening(lot) && <span className="text-[var(--color-fg-muted)]">Acquis le {formatDate(lot.dateAcquisition)}</span>}
                      </span>
                    )}</Td>
                    <Td className="text-right"><strong className="font-mono text-xs">{formatDays(lot.remainingDays)}</strong><span className="block text-[10px] text-[var(--color-fg-muted)]">sur {formatDays(lot.initialDays)} {isOpening(lot) ? 'repris' : 'initiaux'}</span></Td>
                    <Td className="text-right text-xs">{lot.unitRate == null ? '—' : `${formatMoneyAr(lot.unitRate)} / j`}</Td>
                    <Td className="text-right text-xs font-semibold">{fmtMoney(lot.remainingValue)}</Td>
                    <Td><Button variant="ghost" size="xs" aria-label={`${selectedId === lot.idSource ? 'Masquer' : 'Voir'} l’historique du lot ${lotLabel(lot)} #${lot.idSource}`} aria-expanded={selectedId === lot.idSource} onClick={() => setSelectedId(selectedId === lot.idSource ? null : lot.idSource)} iconLeft={<ChevronDown size={13} className={selectedId === lot.idSource ? 'rotate-180' : ''} />}>{selectedId === lot.idSource ? 'Masquer' : 'Historique'}</Button></Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          </div>
        )}
      </Card>

      {selected && (
        <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4 space-y-3" aria-label={`Historique du lot ${lotLabel(selected)}`}>
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h5 className="text-sm font-semibold text-[var(--color-fg)]">Historique · {lotLabel(selected)}{selected.source?.dateOrigineLot ? ` ${String(selected.dateAcquisition).slice(0, 4)}` : ''}</h5>
            <span className="text-xs text-[var(--color-fg-muted)]">{selected.history.length ? `${formatDays(selected.usedDays)} consommés nets` : 'Aucune consommation'}</span>
          </div>
          <div className="overflow-x-auto">
            {selected.history.length === 0 ? <p className="text-xs text-[var(--color-fg-muted)]">Ce lot n’a pas encore été utilisé.</p> : (
              <Table bordered={false}><Thead><Tr><Th>Mouvement</Th><Th>Demande</Th><Th className="text-right">Impact sur le lot</Th><Th className="text-right">Montant historique</Th><Th className="text-right">Taux retenu</Th></Tr></Thead><Tbody>
                {selected.history.map((item, index) => {
                  const movement = sourceMovements.get(Number(item.idMouvement));
                  const consumed = Number(item.jours) > 0;
                  const amount = number(item.valeur);
                  return <Tr key={`${item.idMouvement}-${item.idSource}-${index}`}>
                    <Td className="text-xs">{movement?.typeMouvement || 'Mouvement'} · {formatDate(movement?.dateMouvement)}</Td>
                    <Td className="text-xs">{item.idDemande == null ? '—' : <Button variant="ghost" size="xs" iconLeft={<FileText size={12} />} onClick={() => onVoirDemande?.(item.idDemande)}>Demande #{item.idDemande}</Button>}</Td>
                    <Td className={`text-right font-mono text-xs ${consumed ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-success-600)]'}`}>{formatDays(Math.abs(Number(item.jours)))} {consumed ? 'consommés' : 'restitués'}</Td>
                    <Td className="text-right text-xs">{amount == null ? 'Non déterminé' : `${formatMoneyAr(Math.abs(amount))} ${consumed ? 'consommés' : 'restitués'}`}</Td>
                    <Td className="text-right text-xs">{item.tauxHistorique == null ? '—' : `${formatMoneyAr(item.tauxHistorique)} / j`}</Td>
                  </Tr>;
                })}
              </Tbody></Table>
            )}
          </div>
          <details className="border-t border-[var(--color-border-subtle)] pt-2 text-xs">
            <summary className="cursor-pointer font-medium text-[var(--color-brand-600)] focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]">Source et justification</summary>
            <div className="mt-2 space-y-1 text-[var(--color-fg-muted)]">
              <p>Valeur initiale : {fmtMoney(selected.initialValue)} · mouvement #{selected.idSource}{selected.source?.dateMouvement ? ` du ${formatDate(selected.source.dateMouvement)}` : ''}</p>
              {selected.source ? <p>Référence : {selected.source.commentaire || 'Aucune précision'}{selected.source.createdBy ? ` · enregistré par ${selected.source.createdBy}` : ''}</p> : <p>Mouvement source indisponible dans le registre chargé.</p>}
              {isOpening(selected) && !selected.source?.dateOrigineLot && <p>Cette reprise globale ne précise pas l’exercice d’origine des jours.</p>}
            </div>
          </details>
        </section>
      )}
    </div>
  );
}
