import { Calendar, Clock, ArrowDownCircle, ShieldAlert, Info, CheckCircle, AlertTriangle } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';

export default function SoldeCongeWidget({ soldeData, consommationPool, employe }) {
  if (!soldeData) return null;

  const soldeActuel = Number(soldeData.soldeActuel || 0);
  const totalAcquis = Number(soldeData.totalAcquis || 0);
  const totalPris = Number(soldeData.totalPris || 0);
  const totalBonification = Number(soldeData.totalBonification || 0);
  const totalCumule = totalAcquis + totalBonification;
  const totalExpire = Number(soldeData.totalExpire || 0);

  // Pool partagé permissions exceptionnelles (10j / an max)
  const poolJoursConsommes = consommationPool && consommationPool.length > 0
    ? Number(consommationPool[0].joursConsommes || 0)
    : 0;

  // Calcul de l'ancienneté et éligibilité légale au congé annuel payé (Règle des 12 mois - Art. 83)
  const dateDebut = employe?.dateEmbauche || employe?.dateDebutContratActif || employe?.dateDebut;
  let ancienneteInfo = null;

  if (employe?.ancienneteEffectiveMois !== undefined && employe?.ancienneteEffectiveMois !== null) {
    const months = Number(employe.ancienneteEffectiveMois);
    const years = Math.floor(months / 12);
    const remMonths = months % 12;
    const isEligible = months >= 12;

    let dateDebutStr = '—';
    let dateOuvertureStr = '—';
    if (dateDebut) {
      const dDebut = new Date(dateDebut);
      if (!isNaN(dDebut.getTime())) {
        dateDebutStr = dDebut.toLocaleDateString('fr-FR');
        const dateOuverture = new Date(dDebut);
        dateOuverture.setFullYear(dateOuverture.getFullYear() + 1);
        dateOuvertureStr = dateOuverture.toLocaleDateString('fr-FR');
      }
    }

    ancienneteInfo = {
      isEligible,
      months,
      years,
      remMonths,
      textAnciennete: employe.ancienneteEffectiveTexte || (years > 0 ? `${years} an${years > 1 ? 's' : ''}` : `${months} mois`),
      dateDebutStr,
      dateOuvertureStr
    };
  } else if (dateDebut) {
    const dDebut = new Date(dateDebut);
    if (!isNaN(dDebut.getTime())) {
      const now = new Date();
      let months = (now.getFullYear() - dDebut.getFullYear()) * 12 + (now.getMonth() - dDebut.getMonth());
      if (now.getDate() < dDebut.getDate()) months--;
      months = Math.max(0, months);

      const years = Math.floor(months / 12);
      const remMonths = months % 12;

      const dateOuverture = new Date(dDebut);
      dateOuverture.setFullYear(dateOuverture.getFullYear() + 1);

      const isEligible = months >= 12;
      const textAnciennete = years > 0
        ? `${years} an${years > 1 ? 's' : ''}${remMonths > 0 ? ` et ${remMonths} mois` : ''}`
        : `${months} mois`;

      ancienneteInfo = {
        isEligible,
        months,
        years,
        remMonths,
        textAnciennete,
        dateDebutStr: dDebut.toLocaleDateString('fr-FR'),
        dateOuvertureStr: dateOuverture.toLocaleDateString('fr-FR')
      };
    }
  }

  const getSoldeColor = () => {
    if (soldeActuel < 0) return 'danger';
    if (soldeActuel > 0) return 'brand';
    return 'neutral';
  };

  const formatJours = (val) => {
    const num = Number(val || 0);
    return Number.isInteger(num) ? num.toString() : num.toFixed(1);
  };

  return (
    <div className="space-y-4">
      {/* ── Bandeau Statut Éligibilité Légale au Congé Annuel (Art. 83) ── */}
      {ancienneteInfo && (
        ancienneteInfo.isEligible ? (
          <div className="p-3.5 bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/30 flex flex-wrap items-center justify-between gap-3 text-xs text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
            <div className="flex items-center gap-2.5">
              <CheckCircle size={17} className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] shrink-0" />
              <div>
                <span className="font-bold text-sm">Éligible aux congés payés annuels (Art. 85)</span>
                <p className="text-xs text-[var(--color-success-600)] dark:text-[var(--color-success-400)] mt-0.5">
                  Ancienneté de <strong>{ancienneteInfo.textAnciennete}</strong> (embauché(e) le {ancienneteInfo.dateDebutStr}). Droit ouvert depuis le {ancienneteInfo.dateOuvertureStr}.
                </p>
              </div>
            </div>
            <span className="text-[11px] font-bold bg-[var(--color-success-500)]/20 text-[var(--color-success-600)] dark:text-[var(--color-success-400)] px-2.5 py-1 border border-[var(--color-success-500)]/30">
              Droit Ouvert (≥ 1 an)
            </span>
          </div>
        ) : (
          <div className="p-3.5 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex flex-wrap items-center justify-between gap-3 text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-200)]">
            <div className="flex items-center gap-2.5">
              <AlertTriangle size={17} className="text-[var(--color-warning-600)] shrink-0" />
              <div>
                <span className="font-bold text-sm">Période d'acquisition d'ancienneté en cours ({ancienneteInfo.months} mois / 12)</span>
                <p className="text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] mt-0.5">
                  L'ouverture du droit légal au congé annuel payé aura lieu le <strong>{ancienneteInfo.dateOuvertureStr}</strong> (Art. 85). Une <strong>dérogation RH</strong> est requise pour toute prise anticipée.
                </p>
              </div>
            </div>
            <span className="text-[11px] font-bold bg-[var(--color-warning-500)]/20 text-[var(--color-warning-700)] dark:text-[var(--color-warning-200)] px-2.5 py-1 border border-[var(--color-warning-500)]/30">
              Ancienneté &lt; 1 an (Dérogation requise)
            </span>
          </div>
        )
      )}

      {/* Grille des 4 Cartes Métier Harmonieuses */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* 1. Solde Net Utilisable */}
        <StatCard
          title="Solde Disponible"
          value={`${soldeActuel > 0 ? '+' : ''}${formatJours(soldeActuel)} j`}
          icon={<Calendar size={20} />}
          color={getSoldeColor()}
          subtext="Jours utilisables immédiatement"
        />

        {/* 2. Total des Droits Historiques */}
        <StatCard
          title="Cumul des Droits Acquis"
          value={`+${formatJours(totalCumule)} j`}
          icon={<Clock size={20} />}
          color="neutral"
          subtext="Total octroyé depuis l'embauche (2.5j/m)"
        />

        {/* 3. Déductions : Prises & Prescriptions */}
        <StatCard
          title={totalExpire > 0 ? "Congés Déduits & Prescrits" : "Congés Payés Posés"}
          value={`${formatJours(totalPris + totalExpire)} j`}
          icon={<ArrowDownCircle size={20} />}
          color={totalExpire > 0 ? "warning" : "neutral"}
          subtext={totalExpire > 0 ? `${formatJours(totalPris)}j pris • ${formatJours(totalExpire)}j forclos (+36 mois)` : "Total congés payés consommés"}
        />

        {/* 4. Quota Événements Familiaux */}
        <StatCard
          title="Événements Familiaux"
          value={`${formatJours(poolJoursConsommes)} / 10 j`}
          icon={<ShieldAlert size={20} />}
          color={poolJoursConsommes >= 10 ? 'danger' : poolJoursConsommes >= 7 ? 'warning' : 'neutral'}
          subtext="Mariage, décès, naissance (non déduit du solde)"
        />
      </div>

      {/* Bandeau explicatif de la formule du solde si prescription ou déductions */}
      {totalExpire > 0 && (
        <div className="px-4 py-2.5 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex flex-wrap items-center justify-between gap-3 text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-200)]">
          <div className="flex items-center gap-2">
            <Info size={16} className="text-[var(--color-warning-600)] shrink-0" />
            <span className="font-semibold">Décomposition du Solde :</span>
            <span className="font-data">
              <strong>+{formatJours(totalCumule)}j</strong> (Acquis)
              <span className="mx-1.5 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">−</span>
              <strong>{formatJours(totalExpire)}j</strong> (Prescription légale &gt; 3 ans)
              <span className="mx-1.5 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">−</span>
              <strong>{formatJours(totalPris)}j</strong> (Pris)
              <span className="mx-1.5 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">=</span>
              <strong className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">+{formatJours(soldeActuel)} jours disponibles</strong>
            </span>
          </div>
          <span className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]">
            Code du travail : prescription après 36 mois sans prise.
          </span>
        </div>
      )}
    </div>
  );
}
