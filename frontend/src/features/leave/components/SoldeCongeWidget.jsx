import { Calendar, Clock, ArrowDownCircle, ShieldAlert, Coins, ChevronDown } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';
import Button from '@/components/ui/forms/Button';
import { formatMoneyAr } from '@/utils/moneyUtils';

const formatJours = (value) => {
  const number = Number(value ?? 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(2);
};

/** Présentation des valeurs servies par le backend ; aucune décision d'éligibilité ici. */
export default function SoldeCongeWidget({ soldeData, situationData = null, consommationPool, employe, canWrite = false, onOpenInitModal }) {
  if (!soldeData && !situationData) return null;

  const solde = situationData?.soldeJours ?? soldeData?.soldeActuel ?? soldeData?.soldeDisponible;
  const acquis = situationData?.totalAcquis ?? soldeData?.totalAcquis;
  const pris = situationData?.totalPris ?? soldeData?.totalPris;
  const bonification = soldeData?.totalBonification;
  const expire = soldeData?.totalExpire;
  const taux = situationData?.tauxApplicable?.montant ?? soldeData?.tauxJournalierConge;
  const sourceTaux = situationData?.tauxApplicable?.source ?? soldeData?.sourceTauxConge;
  const cycle = situationData?.periodeReference;
  const pool = consommationPool?.[0];

  return (
    <section aria-label="Situation des congés" className="space-y-3">
      <div className="grid gap-3 lg:grid-cols-[minmax(15rem,1fr)_minmax(0,2fr)]">
        <StatCard
          title="Solde disponible"
          value={solde == null ? '—' : `${Number(solde) > 0 ? '+' : ''}${formatJours(solde)} j`}
          icon={<Calendar size={20} />}
          color={Number(solde) < 0 ? 'danger' : 'brand'}
          subtext="Jours utilisables selon la situation RH"
        />
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3.5 flex flex-col justify-center gap-2">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 min-w-0">
              <Coins size={17} className="text-[var(--color-brand-600)] shrink-0" />
              <div>
                <p className="text-xs font-semibold text-[var(--color-fg-muted)]">Taux TCONGE applicable</p>
                <p className="text-sm font-semibold text-[var(--color-fg)]">
                  {taux == null ? 'Non renseigné' : `${formatMoneyAr(taux)} / jour`}
                </p>
              </div>
            </div>
            {canWrite && onOpenInitModal && taux == null && (
              <Button variant="outline" size="xs" onClick={onOpenInitModal}>Initialiser le taux</Button>
            )}
          </div>
          <p className="text-xs text-[var(--color-fg-muted)]">
            {cycle?.dateDebut && cycle?.dateFin
              ? <>Cycle de référence : <strong className="text-[var(--color-fg)]">{cycle.dateDebut} → {cycle.dateFin}</strong></>
              : 'Aucun cycle de référence actif'}
            {employe?.ancienneteEffectiveTexte && <> · Ancienneté : {employe.ancienneteEffectiveTexte}</>}
          </p>
        </div>
      </div>

      <details className="group border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
        <summary className="cursor-pointer list-none flex items-center justify-between gap-3 p-3 text-xs font-semibold text-[var(--color-fg)] focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)]">
          <span>Voir le détail des droits, prises et mouvements du cycle</span>
          <ChevronDown size={16} className="text-[var(--color-fg-muted)] group-open:rotate-180 transition-transform shrink-0" />
        </summary>
        <div className="border-t border-[var(--color-border-subtle)] p-3 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <StatCard title="Droits acquis" value={acquis == null ? '—' : `${formatJours(acquis)} j`} icon={<Clock size={18} />} color="neutral"
              subtext={bonification != null ? `Bonification : ${formatJours(bonification)} j` : undefined} />
            <StatCard title="Congés pris" value={pris == null ? '—' : `${formatJours(pris)} j`} icon={<ArrowDownCircle size={18} />} color="neutral"
              subtext={expire != null ? `Droits expirés : ${formatJours(expire)} j` : undefined} />
            <StatCard title="Événements familiaux" value={pool ? `${formatJours(pool.joursConsommes)} / ${formatJours(pool.plafond)} j` : '—'}
              icon={<ShieldAlert size={18} />} color="neutral"
              subtext={pool ? `${formatJours(pool.joursEnAttente)} j réservés · ${formatJours(pool.joursEngages)} j engagés` : 'Aucune donnée de quota'} />
          </div>
          {(sourceTaux || cycle) && (
            <div className="grid sm:grid-cols-2 gap-2 text-xs text-[var(--color-fg-muted)]">
              {sourceTaux && <p>Source du taux : <strong className="text-[var(--color-fg)]">{sourceTaux}</strong></p>}
              {cycle && <p>Cycle : <strong className="text-[var(--color-fg)]">{cycle.statut || '—'}</strong>
                {cycle.modeReprise ? ` · Reprise ${cycle.openingComplete ? 'complète' : 'partielle'}` : ''}</p>}
              {cycle?.cumulDroitsTotal != null && <p>Droits du cycle : <strong className="text-[var(--color-fg)]">{formatJours(cycle.cumulDroitsTotal)} j</strong></p>}
              {cycle?.tauxProjection != null && <p>Taux projeté : <strong className="text-[var(--color-fg)]">{formatMoneyAr(cycle.tauxProjection)} / jour</strong></p>}
            </div>
          )}
        </div>
      </details>
    </section>
  );
}
