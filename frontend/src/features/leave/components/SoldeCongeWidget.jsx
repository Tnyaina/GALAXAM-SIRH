import { Calendar, Clock, ArrowDownCircle, ShieldAlert, Coins, ChevronDown } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';
import Button from '@/components/ui/forms/Button';
import { formatMoneyAr } from '@/utils/moneyUtils';
import Alert from '@/components/ui/feedback/Alert';

const formatJours = (value) => {
  const number = Number(value ?? 0);
  return Number.isInteger(number) ? String(number) : number.toFixed(2);
};

/** Présentation des valeurs servies par le backend ; aucune décision d'éligibilité ni valeur en dur ici. */
export default function SoldeCongeWidget({ soldeData, situationData = null, valorisationLots = null, valorisationError = null, valorisationLoading = false, consommationPool, consommationError = null, employe, onOpenPeriodesModal }) {
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

  const soldeNumber = solde != null ? Number(solde) : null;
  const soldeSubtext = bonification != null && Number(bonification) > 0
    ? `Dont ${formatJours(bonification)} j de bonification`
    : cycle?.statut
      ? `Cycle ${cycle.statut.toLowerCase()}`
      : 'Solde net à date';

  return (
    <section aria-label="Situation des congés" className="space-y-3 font-sans">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        <StatCard
          title="Solde disponible"
          value={soldeNumber == null ? '-' : `${soldeNumber > 0 ? '+' : ''}${formatJours(soldeNumber)} j`}
          icon={<Calendar size={20} />}
          color={soldeNumber != null && soldeNumber < 0 ? 'danger' : 'brand'}
          subtext={soldeSubtext}
        />
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3.5 flex flex-col justify-center gap-2">
          <div>
            <p className="text-xs font-semibold text-[var(--color-fg-muted)]">Valeur restante du stock (FIFO)</p>
            <p className="text-lg font-bold text-[var(--color-fg)]">
              {valorisationError ? 'Indisponible' : valorisationLoading && !valorisationLots ? 'Chargement…' : valorisationLots?.soldeValorisation == null ? 'Non déterminée' : formatMoneyAr(valorisationLots.soldeValorisation)}
            </p>
            <p className="text-[11px] text-[var(--color-fg-subtle)]">Valeur issue des lots restants, pas du solde multiplié par le TCONGE.</p>
          </div>
          {valorisationError && <Alert type="error" message={`Valeur des lots : ${valorisationError}`} />}
        </div>
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3.5 flex flex-col justify-center gap-2">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2 min-w-0">
              <Coins size={17} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shrink-0" />
              <div>
                <p className="text-xs font-semibold text-[var(--color-fg-muted)]">Taux TCONGE applicable</p>
                <p className="text-sm font-semibold text-[var(--color-fg)]">
                  {taux == null ? 'Non renseigné' : `${formatMoneyAr(taux)} / jour`}
                </p>
              </div>
            </div>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-fg-muted)]">
            <p>
              {cycle?.dateDebut && cycle?.dateFin
                ? <>Cycle de référence : <strong className="text-[var(--color-fg)]">{cycle.dateDebut} → {cycle.dateFin}</strong></>
                : 'Aucun cycle de référence actif'}
              {employe?.ancienneteEffectiveTexte && <> · Ancienneté : {employe.ancienneteEffectiveTexte}</>}
            </p>
            {onOpenPeriodesModal && (
              <Button variant="ghost" size="xs" onClick={onOpenPeriodesModal} className="text-xs text-[var(--color-brand-600)] hover:underline p-0 h-auto font-medium">
                Contrôler les droits
              </Button>
            )}
          </div>
        </div>
      </div>
      <p className="text-[11px] text-[var(--color-fg-muted)]">Le solde en jours, la valeur historique du stock et le TCONGE applicable sont trois informations distinctes.</p>

      <details className="group border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
        <summary className="cursor-pointer list-none flex items-center justify-between gap-3 p-3 text-xs font-semibold text-[var(--color-fg)] focus:outline-none focus:ring-2 focus:ring-[var(--color-focus-ring)]">
          <span>Voir le détail des droits, prises et mouvements du cycle</span>
          <ChevronDown size={16} className="text-[var(--color-fg-muted)] group-open:rotate-180 transition-transform shrink-0" />
        </summary>
        <div className="border-t border-[var(--color-border-subtle)] p-3 space-y-3">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <StatCard
              title="Droits acquis"
              value={acquis == null ? '-' : `${formatJours(acquis)} j`}
              icon={<Clock size={18} />}
              color="neutral"
              subtext={bonification != null && Number(bonification) > 0 ? `Bonification : ${formatJours(bonification)} j` : 'Droits cumulés'}
            />
            <StatCard
              title="Congés pris"
              value={pris == null ? '-' : `${formatJours(pris)} j`}
              icon={<ArrowDownCircle size={18} />}
              color="neutral"
              subtext={expire != null && Number(expire) > 0 ? `Droits expirés : ${formatJours(expire)} j` : 'Absences validées'}
            />
            <StatCard
              title="Événements familiaux"
              value={pool ? `${formatJours(pool.joursConsommes)} / ${formatJours(pool.plafond)} j` : '-'}
              icon={<ShieldAlert size={18} />}
              color="neutral"
              subtext={pool ? `${formatJours(pool.joursEnAttente)} j réservés · ${formatJours(pool.joursEngages)} j engagés` : consommationError ? 'Données du quota indisponibles' : 'Quota non initialisé'}
            />
          </div>
          {(sourceTaux || cycle) && (
            <div className="grid sm:grid-cols-2 gap-2 text-xs text-[var(--color-fg-muted)]">
              {sourceTaux && <p>Taux actuel : <strong className="text-[var(--color-fg)]">{sourceTaux === 'REPRISE_CUTOVER' ? 'reprise historique' : sourceTaux === 'CALCUL_PERIODE_REFERENCE' ? 'cycle précédent' : sourceTaux}</strong></p>}
              {cycle && <p>Cycle : <strong className="text-[var(--color-fg)]">{cycle.statut || '-'}</strong>
                {cycle.modeReprise ? ` · Reprise ${cycle.openingComplete ? 'complète' : 'partielle'}` : ''}</p>}
              {cycle?.cumulDroitsTotal != null && <p>Droits acquis dans le cycle : <strong className="text-[var(--color-fg)]">{formatJours(cycle.cumulDroitsTotal)} j</strong></p>}
              {cycle?.tauxProjection != null && <p>Taux estimé du prochain cycle : <strong className="text-[var(--color-fg)]">{formatMoneyAr(cycle.tauxProjection)} / jour</strong><span className="block">Valeur acquise ÷ jours acquis à ce jour ; non applicable avant clôture et activation.</span></p>}
            </div>
          )}
        </div>
      </details>
    </section>
  );
}
