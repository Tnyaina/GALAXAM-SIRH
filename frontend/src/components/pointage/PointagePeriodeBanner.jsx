import { CalendarRange, ArrowRight, CheckCircle, RotateCcw, AlertTriangle } from 'lucide-react';
import { Link } from 'react-router-dom';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

export default function PointagePeriodeBanner({
    periodeInfo,
    selectedDate,
    isAdmin,
    onTriggerStatut
}) {
    if (!periodeInfo) {
        return (
            <div className="px-4 py-3 bg-amber-50/60 dark:bg-amber-950/20 border border-amber-200 dark:border-amber-800/40 flex items-center justify-between gap-3 text-xs text-amber-800 dark:text-amber-300">
                <div className="flex items-center gap-2">
                    <AlertTriangle size={15} className="text-amber-500 shrink-0" />
                    <span>Aucune période de paie n'est configurée pour cette date ({selectedDate}). La sauvegarde sera bloquée.</span>
                </div>
                {isAdmin && (
                    <Link to="/pointages/periodes" className="font-bold underline hover:text-amber-900 shrink-0">
                        Gérer les périodes ➔
                    </Link>
                )}
            </div>
        );
    }

    const isLocked = periodeInfo.statut_code === 'VALIDEE' || periodeInfo.statut_code === 'CLOTUREE';
    const isAValider = periodeInfo.statut_code === 'A_VALIDER';

    const getBannerStyle = () => {
        if (isLocked) return 'bg-ink-50/70 dark:bg-ink-950/60 border-ink-200 dark:border-ink-800 text-ink-700 dark:text-ink-300';
        if (isAValider) return 'bg-amber-50/50 dark:bg-amber-950/20 border-amber-200 dark:border-amber-800/40 text-amber-900 dark:text-amber-200';
        return 'bg-brand-50/40 dark:bg-brand-950/20 border-brand-200 dark:border-brand-800/30 text-[var(--color-fg)]';
    };

    return (
        <div className={`px-5 py-3 border flex flex-col md:flex-row items-start md:items-center justify-between gap-3 shadow-xs ${getBannerStyle()}`}>
            <div className="flex items-center gap-3">
                <div className="w-8 h-8 flex items-center justify-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-brand-600)] shrink-0">
                    <CalendarRange size={16} />
                </div>
                <div className="flex flex-wrap items-center gap-2 text-xs">
                    <span className="font-bold text-[var(--color-fg)]">
                        Période : {periodeInfo.periode_libelle || periodeInfo.periode_code}
                    </span>
                    <span className="font-mono text-[var(--color-fg-muted)]">
                        ({periodeInfo.date_debut} au {periodeInfo.date_fin})
                    </span>
                    <Badge variant={
                        isLocked ? 'success' :
                        isAValider ? 'warning' :
                        periodeInfo.statut_code === 'BROUILLON' ? 'secondary' : 'info'
                    } className="text-[10px]">
                        {periodeInfo.statut_libelle || periodeInfo.statut_code}
                    </Badge>
                </div>
            </div>

            {/* Actions Contextuelles Directes */}
            <div className="flex items-center gap-2 self-end md:self-auto shrink-0">
                {periodeInfo.statut_code === 'EN_COURS' && (
                    <Button 
                        size="xs" 
                        variant="outline" 
                        icon={<ArrowRight size={13}/>}
                        onClick={() => onTriggerStatut('A_VALIDER', 'Transmettre le mois à la DAF', 'Confirmez-vous la fin de saisie de cette période pour transmission à la DAF ?')}
                    >
                        Transmettre à la DAF
                    </Button>
                )}
                {isAValider && isAdmin && (
                    <Button 
                        size="xs" 
                        variant="primary" 
                        icon={<CheckCircle size={13}/>}
                        onClick={() => onTriggerStatut('VALIDEE', 'Valider et verrouiller la période', 'Confirmez-vous la validation définitive des heures pour le calcul de la paie ? Les pointages seront verrouillés.')}
                    >
                        Valider la période (DAF)
                    </Button>
                )}
                {isLocked && isAdmin && (
                    <Button 
                        size="xs" 
                        variant="ghost" 
                        className="!text-warning-600 hover:!bg-warning-500/10"
                        icon={<RotateCcw size={13}/>}
                        onClick={() => onTriggerStatut('EN_COURS', 'Rouvrir la période', 'Attention : La réouverture permettra à nouveau aux agents de modifier les pointages. Confirmez-vous le déverrouillage ?')}
                    >
                        Rouvrir la saisie
                    </Button>
                )}
            </div>
        </div>
    );
}
