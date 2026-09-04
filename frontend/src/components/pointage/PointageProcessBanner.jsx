import { Link } from 'react-router-dom';
import {
    CalendarCheck,
    Send,
    ArrowRight,
    CheckCircle2
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

export default function PointageProcessBanner({
    periodeInfo = null,
    totalAnomalies = 0,
    isReadOnly = false,
    submittingDaf = false,
    onRequestSubmitDaf
}) {
    const isEnCours = periodeInfo?.statut_code === 'EN_COURS' || !periodeInfo?.statut_code;
    const isCloturee = periodeInfo?.statut_code === 'CLOTUREE' || periodeInfo?.statut_code === 'VALIDEE';
    const isEnValidation = periodeInfo?.statut_code === 'A_VALIDER';

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
            {/* Titre du Guide et État de la Période Active */}
            <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
                <div className="flex items-center gap-2.5">
                    <div className="w-7 h-7 bg-brand-500/10 text-brand-600 dark:text-brand-400 flex items-center justify-center">
                        <CalendarCheck size={16} />
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <span className="text-[10px] font-bold uppercase tracking-wider text-brand-500">
                                Cycle Opérationnel de Pointage
                            </span>
                            {periodeInfo && (
                                <Badge
                                    variant={isCloturee ? 'secondary' : isEnValidation ? 'warning' : 'success'}
                                    size="xs"
                                    dot
                                >
                                    {periodeInfo.statut_libelle || periodeInfo.statut_code || 'En cours'}
                                </Badge>
                            )}
                        </div>
                        <h3 className="text-xs font-bold text-[var(--color-fg)]">
                            Période : {periodeInfo?.libelle || periodeInfo?.code || 'Période en cours'}
                            {periodeInfo?.date_debut && periodeInfo?.date_fin && (
                                <span className="font-mono text-[11px] font-normal text-[var(--color-fg-muted)] ml-2">
                                    ({periodeInfo.date_debut} → {periodeInfo.date_fin})
                                </span>
                            )}
                        </h3>
                    </div>
                </div>

                {/* Bouton de Soumission Finale à la DAF */}
                {isEnCours && onRequestSubmitDaf && (
                    <Button
                        type="button"
                        variant="primary"
                        size="xs"
                        disabled={isReadOnly || submittingDaf}
                        loading={submittingDaf}
                        onClick={onRequestSubmitDaf}
                        iconLeft={<Send size={13} />}
                        className="font-bold shrink-0 shadow-xs"
                    >
                        Transmettre le mois à la DAF
                    </Button>
                )}
            </div>

            {/* Les 3 Étapes Clés du Processus Opérationnel */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pt-1">
                {/* 1. Saisie Quotidienne (Étape en cours) */}
                <div className="p-3 bg-brand-500/5 border border-brand-500/30 flex items-start justify-between gap-2">
                    <div className="space-y-1">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-brand-600 dark:text-brand-400">
                            <span className="w-4 h-4 bg-brand-500 text-white text-[10px] flex items-center justify-center font-bold">1</span>
                            <span>Saisie Quotidienne</span>
                            <Badge variant="primary" size="xs">En cours</Badge>
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-muted)] leading-tight">
                            Pointer les horaires, heures sup, pauses et indemnités jour par jour.
                        </p>
                    </div>
                </div>

                {/* 2. Contrôle & Régularisation des Anomalies */}
                <Link
                    to="/pointages/anomalies"
                    className="p-3 bg-[var(--color-bg-subtle)] hover:bg-amber-500/10 border border-[var(--color-border-subtle)] hover:border-amber-500/40 transition-colors flex items-start justify-between gap-2 group"
                >
                    <div className="space-y-1">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-fg)] group-hover:text-amber-600">
                            <span className="w-4 h-4 bg-ink-200 dark:bg-ink-700 text-ink-800 dark:text-ink-200 text-[10px] flex items-center justify-center font-bold">2</span>
                            <span>Contrôle des Anomalies</span>
                            {totalAnomalies > 0 ? (
                                <Badge variant="warning" size="xs">{totalAnomalies} en attente</Badge>
                            ) : (
                                <span className="text-[10px] text-emerald-600 dark:text-emerald-400 flex items-center gap-0.5">
                                    <CheckCircle2 size={11} /> 0 anomalie
                                </span>
                            )}
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-muted)] leading-tight">
                            Vérifier et corriger les Saisies incomplets et sorties orphelines.
                        </p>
                    </div>
                    <ArrowRight size={14} className="text-[var(--color-fg-muted)] group-hover:text-amber-600 shrink-0 mt-0.5 transition-transform group-hover:translate-x-0.5" />
                </Link>

                {/* 3. Synthèse Hebdomadaire & Compensations */}
                <Link
                    to="/pointages/synthese"
                    className="p-3 bg-[var(--color-bg-subtle)] hover:bg-indigo-500/10 border border-[var(--color-border-subtle)] hover:border-indigo-500/40 transition-colors flex items-start justify-between gap-2 group"
                >
                    <div className="space-y-1">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-fg)] group-hover:text-indigo-600">
                            <span className="w-4 h-4 bg-ink-200 dark:bg-ink-700 text-ink-800 dark:text-ink-200 text-[10px] flex items-center justify-center font-bold">3</span>
                            <span>Synthèse Hebdomadaire & Mensuelle</span>
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-muted)] leading-tight">
                            Consolider les semaines (HS 30%, HS 50%, compensation retards et cumul paie).
                        </p>
                    </div>
                    <ArrowRight size={14} className="text-[var(--color-fg-muted)] group-hover:text-indigo-600 shrink-0 mt-0.5 transition-transform group-hover:translate-x-0.5" />
                </Link>
            </div>
        </div>
    );
}
