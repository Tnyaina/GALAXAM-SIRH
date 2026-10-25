import { ChevronDown, Info } from 'lucide-react';
import Alert from '@/components/ui/feedback/Alert';
import Loading from '@/components/ui/feedback/Loading';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { formatMoneyAr } from '@/utils/moneyUtils';

const day = (value) => new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(Number(value || 0));
const date = (value) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : 'Date inconnue';
const origin = (lot) => lot.origine === 'REPRISE_CUTOVER'
  ? `Reprise agrégée · arrêté ${date(lot.dateAcquisition)}`
  : lot.origine === 'ACQUISITION'
    ? `Acquisition · ${date(lot.dateAcquisition)}`
    : `${lot.origine || 'Droit'} · ${date(lot.dateAcquisition)}`;

export default function SimulationValorisationConge({ report, error, loading }) {
  if (loading) return <Card padding bordered><div className="flex justify-center py-3"><Loading size="sm" label="Estimation des droits…" /></div></Card>;
  if (error) return <Alert type="warning" title="Estimation indisponible" message={error} />;
  if (!report || report.applicable === false) return null;

  return (
    <Card padding bordered className="space-y-2.5" aria-live="polite">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h4 className="text-sm font-semibold text-[var(--color-fg)]">Estimation de la demande</h4>
          <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">{day(report.joursDemandes)} j demandés · {day(report.soldeAvant)} j disponibles</p>
        </div>
        <div className="text-right">
          <p className="text-[10px] uppercase tracking-wide text-[var(--color-fg-muted)]">Indemnité estimée</p>
          <p className="text-lg font-bold text-[var(--color-fg)]">{formatMoneyAr(report.valeurEstimee)}</p>
        </div>
      </div>
      {!report.soldeSuffisant && <Alert type="warning" title="Solde insuffisant" message={`Il manque ${day(Math.max(0, Number(report.joursDemandes) - Number(report.soldeAvant)))} j. Le montant estimé couvre les droits disponibles.`} />}
      {report.demandesEnAttente?.length > 0 && <p className="text-xs text-[var(--color-warning-700)]">Des demandes en attente peuvent modifier le solde disponible.</p>}
      <details className="group border-t border-[var(--color-border-subtle)] pt-2">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-2 text-xs font-semibold text-[var(--color-brand-700)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)]">
          <span>Détail des droits utilisés ({report.lots?.length || 0} lots)</span>
          <ChevronDown size={15} aria-hidden="true" className="transition-transform group-open:rotate-180" />
        </summary>
        <div className="mt-2 overflow-x-auto">
          {!report.lots?.length ? <p className="py-2 text-xs text-[var(--color-fg-muted)]">Aucun lot disponible à la date de prise.</p> : (
            <Table bordered={false}><Thead><Tr><Th>Origine</Th><Th className="text-right">Jours</Th><Th className="text-right">Valeur</Th></Tr></Thead>
              <Tbody>{report.lots.map((lot) => <Tr key={lot.idSource}><Td className="text-xs">{origin(lot)}</Td><Td className="text-right font-mono text-xs">{day(lot.jours)} j</Td><Td className="text-right text-xs">{formatMoneyAr(lot.valeur)}</Td></Tr>)}</Tbody>
            </Table>
          )}
          <p className="mt-2 flex items-start gap-1.5 text-[11px] leading-relaxed text-[var(--color-fg-muted)]"><Info size={13} className="mt-0.5 shrink-0" />Estimation sans réservation; le montant final dépendra des imputations à la validation.</p>
        </div>
      </details>
    </Card>
  );
}
