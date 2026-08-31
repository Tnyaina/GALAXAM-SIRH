import { Clock, Users, CheckCircle, Palmtree, AlertTriangle, ShieldCheck, Lock } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

export default function PointagePeriodeBanner({
    periodeInfo,
    totalEmployes = 0,
    totalPresents = 0,
    totalConges = 0,
    totalAnomalies = 0
}) {
    if (!periodeInfo) {
        return (
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex items-center justify-between">
                <div className="flex items-center gap-3">
                    <div className="p-2 rounded bg-amber-500/10 text-amber-600">
                        <Clock size={18} />
                    </div>
                    <div>
                        <p className="text-sm font-bold text-[var(--color-fg)]">Période non déterminée</p>
                        <p className="text-xs text-[var(--color-fg-muted)]">Aucune période de paie ouverte pour cette date.</p>
                    </div>
                </div>
            </div>
        );
    }

    const statut = periodeInfo.statut_code || 'EN_COURS';
    const isAValider = statut === 'A_VALIDER';
    const isValidated = statut === 'VALIDEE';
    const isCloturee = statut === 'CLOTUREE';
    const isLocked = isValidated || isCloturee;

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
            {/* Infos Période */}
            <div className="flex items-center gap-3">
                <div className="p-2.5 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)]">
                    <Clock size={20} />
                </div>
                <div>
                    <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-[var(--color-fg)]">
                            Période : {periodeInfo.libelle || periodeInfo.code}
                        </span>
                        <span className="text-xs font-mono text-[var(--color-fg-muted)]">
                            ({periodeInfo.date_debut} au {periodeInfo.date_fin})
                        </span>
                        {statut === 'EN_COURS' && (
                            <Badge variant="success" size="sm" className="font-bold flex items-center gap-1">
                                <CheckCircle size={12} />
                                Saisie active
                            </Badge>
                        )}
                        {isAValider && (
                            <Badge variant="warning" size="sm" className="font-bold flex items-center gap-1">
                                <ShieldCheck size={12} />
                                En validation DAF
                            </Badge>
                        )}
                        {isLocked && (
                            <Badge variant="neutral" size="sm" className="font-bold flex items-center gap-1">
                                <Lock size={12} />
                                Verrouillée / Clôturée
                            </Badge>
                        )}
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                        {isLocked
                            ? 'Cette période est validée/clôturée par la DAF. La saisie quotidienne est en lecture seule.'
                            : isAValider
                            ? 'Période transmise à la DAF pour vérification et validation finale.'
                            : 'Saisie opérationnelle, suivi des heures effectives et indemnités en cours.'}
                    </p>
                </div>
            </div>

            {/* Statistiques Rapides */}
            <div className="flex items-center gap-3 self-end md:self-auto">
                <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs font-bold text-[var(--color-fg)]">
                    <Users size={14} className="text-[var(--color-fg-muted)]" />
                    <span>{totalEmployes} inscrits</span>
                </div>
                <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-emerald-500/10 border border-emerald-500/20 text-xs font-bold text-emerald-700 dark:text-emerald-400">
                    <CheckCircle size={14} />
                    <span>{totalPresents} présents</span>
                </div>
                {totalConges > 0 && (
                    <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-amber-500/10 border border-amber-500/20 text-xs font-bold text-amber-700 dark:text-amber-400">
                        <Palmtree size={14} />
                        <span>{totalConges} congés</span>
                    </div>
                )}
                {totalAnomalies > 0 && (
                    <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-rose-500/10 border border-rose-500/20 text-xs font-bold text-rose-700 dark:text-rose-400">
                        <AlertTriangle size={14} />
                        <span>{totalAnomalies} anomalies</span>
                    </div>
                )}
            </div>
        </div>
    );
}
