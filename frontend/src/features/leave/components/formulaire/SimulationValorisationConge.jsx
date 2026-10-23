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
    <Card padding bordered className="space-y-3" aria-live="polite">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h4 className="text-sm font-semibold text-[var(--color-fg)]">Estimation des droits utilisés</h4>
          <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Les droits les plus anciens sont utilisés en premier.</p>
        </div>
        <div className="text-right">
          <p className="text-[10px] uppercase tracking-wide text-[var(--color-fg-muted)]">Indemnité estimée</p>
          <p className="text-lg font-bold text-[var(--color-fg)]">{formatMoneyAr(report.valeurEstimee)}</p>
        </div>
      </div>
      <div className="grid grid-cols-2 gap-3 border-y border-[var(--color-border-subtle)] py-2.5 text-xs">
        <div><span className="block text-[var(--color-fg-muted)]">Jours demandés</span><strong className="mt-0.5 block">{day(report.joursDemandes)} j</strong></div>
        <div><span className="block text-[var(--color-fg-muted)]">Droits disponibles à la date de prise</span><strong className="mt-0.5 block">{day(report.soldeAvant)} j</strong></div>
      </div>
      {!report.soldeSuffisant && <Alert type="warning" title="Droits insuffisants" message={`Il manque ${day(Math.max(0, Number(report.joursDemandes) - Number(report.soldeAvant)))} j. Le montant présenté couvre uniquement les lots disponibles.`} />}
      {report.demandesEnAttente?.length > 0 && <Alert type="warning" message="Une ou plusieurs demandes non validées peuvent modifier les droits disponibles avant cette prise." />}
      <details className="group">
        <summary className="flex cursor-pointer list-none items-center justify-between gap-2 text-xs font-semibold text-[var(--color-brand-700)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)]">
          <span>Voir les lots utilisés ({report.lots?.length || 0})</span>
          <ChevronDown size={15} aria-hidden="true" className="transition-transform group-open:rotate-180" />
        </summary>
        <div className="mt-2 overflow-x-auto">
          {!report.lots?.length ? <p className="py-3 text-xs text-[var(--color-fg-muted)]">Aucun lot disponible à la date de prise.</p> : (
            <Table bordered={false}><Thead><Tr><Th>Origine du droit</Th><Th className="text-right">Jours utilisés</Th><Th className="text-right">Valeur historique</Th></Tr></Thead>
              <Tbody>{report.lots.map((lot) => <Tr key={lot.idSource}><Td className="text-xs">{origin(lot)}</Td><Td className="text-right font-mono text-xs">{day(lot.jours)} j</Td><Td className="text-right text-xs">{formatMoneyAr(lot.valeur)}</Td></Tr>)}</Tbody>
            </Table>
          )}
        </div>
      </details>
      <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-[var(--color-fg-muted)]"><Info size={13} className="mt-0.5 shrink-0" />Estimation sans réservation. Les acquisitions futures et les demandes non validées ne sont pas déduites; le montant définitif dépendra des imputations enregistrées à la validation.</p>
    </Card>
  );
}
