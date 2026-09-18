import { useMemo } from 'react';
import { CalendarDays, RefreshCcw, AlertTriangle, LayoutDashboard } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import PayrollPeriodStepper from '@/components/paie/PayrollPeriodStepper';

/**
 * PayrollCampaignHeader — Barre de commande supérieure pour les étapes de paie.
 *
 * Affiche :
 * - Le fil d'Ariane et le sélecteur réactif de période
 * - Le statut actuel avec badge coloré
 * - L'indicateur de recalcul requis (si variables modifiées ou anomalies)
 * - Le bouton "Recalculer" avec confirmation
 * - Le Stepper horizontal interactif (les 7 étapes du workflow)
 *
 * @prop {Object}   context           - Objet retourné par usePayrollCampaign (selectedPeriod, steps, etc.)
 * @prop {Array}    periodes          - Liste des périodes disponibles
 * @prop {Function} onSelectPeriod    - Callback lorsqu'une nouvelle période est choisie
 * @prop {string}   activeStep        - Code de l'étape actuellement affichée (page active)
 * @prop {boolean}  loading           - Affiche l'état de chargement dans le stepper
 * @prop {Function} onRefresh         - Callback déclenché au clic sur "Recalculer"
 * @prop {boolean}  refreshLoading   - Vrai pendant le recalcul
 */
const STATUT_VARIANT = {
    EN_COURS: 'warning',
    A_VALIDER: 'info',
    TRANSMIS_DAF: 'info',
    VALIDEE: 'primary',
    PAIE_VALIDEE: 'success',
    CLOTUREE: 'secondary',
    BROUILLON: 'default',
};

const STATUT_LABEL = {
    EN_COURS: 'Pointage En Cours',
    A_VALIDER: 'Transmise DAF',
    TRANSMIS_DAF: 'Transmise DAF',
    VALIDEE: 'Pointage Scellé & Figé',
    PAIE_VALIDEE: 'Paie Validée & Approuvée',
    CLOTUREE: 'Période Clôturée',
    BROUILLON: 'Brouillon',
};

export default function PayrollCampaignHeader({
    context,
    periodes = [],
    onSelectPeriod,
    activeStep,
    loading = false,
    onRefresh,
    refreshLoading = false,
}) {
    const navigate = useNavigate();
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const steps = context?.campaignSteps || [];
    const currentStep = context?.currentStep;
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;

    const formatDate = (d) => {
        if (!d) return '–';
        return new Date(d).toLocaleDateString('fr-MG', { day: '2-digit', month: 'short', year: 'numeric' });
    };

    const activePeriodId = periode?.id;

    const periodOptions = useMemo(() => {
        return (periodes || []).map(p => {
            const pId = p.idPeriodePaie || p.id_periode_paie || p.id;
            const pCode = p.code || '';
            const pLib = p.libelle || p.statutLibelle || '';
            const pStat = p.statutCode || p.codeStatut || p.statut || '';
            const readableStat = STATUT_LABEL[pStat] || pStat || 'Pointage En Cours';
            return {
                value: String(pId),
                label: `${pCode}${pLib ? ` — ${pLib}` : ''} (${readableStat})`
            };
        });
    }, [periodes]);

    return (
        <div className="sticky top-0 z-30 bg-[var(--color-bg)] border-b border-[var(--color-border-subtle)] shadow-sm">

            {/* Ligne de titre période & sélecteur */}
            <div className="px-4 py-2.5 flex items-center justify-between gap-3 flex-wrap">
                <div className="flex items-center gap-3 min-w-0">
                    <div className="w-8 h-8 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0 shadow-xs">
                        <CalendarDays size={16} strokeWidth={2.2} />
                    </div>
                    <div className="min-w-0 flex items-center gap-2.5 flex-wrap">
                        {/* Sélecteur de période réactif */}
                        {periodOptions.length > 0 ? (
                            <Select
                                value={activePeriodId ? String(activePeriodId) : ''}
                                onChange={(val) => onSelectPeriod && onSelectPeriod(Number(val))}
                                options={periodOptions}
                                className="text-xs font-semibold max-w-xs"
                                aria-label="Sélectionner une période"
                            />
                        ) : (
                            <h2 className="text-sm font-bold text-[var(--color-fg)] truncate">
                                {periode?.code ? `Période ${periode.code}` : 'Campagne de paie'}
                            </h2>
                        )}

                        {/* Badge statut période */}
                        {periode?.state && (
                            <Badge
                                variant={STATUT_VARIANT[periode.state] || 'default'}
                                size="sm"
                                dot
                            >
                                {STATUT_LABEL[periode.state] || periode.state || 'En cours'}
                            </Badge>
                        )}

                        {(periode?.startDate || periode?.endDate) && (
                            <span className="text-[11px] text-[var(--color-fg-muted)] font-mono truncate hidden sm:inline">
                                {formatDate(periode.startDate)} → {formatDate(periode.endDate)}
                                {periode.label && (
                                    <span className="ml-1 text-[var(--color-fg-subtle)]">({periode.label})</span>
                                )}
                            </span>
                        )}
                    </div>
                </div>

                {/* Actions rapides */}
                <div className="flex items-center gap-2 shrink-0">
                    <Button
                        variant="ghost"
                        size="xs"
                        iconLeft={<LayoutDashboard size={13} />}
                        onClick={() => navigate(activePeriodId ? `/paie?periodId=${activePeriodId}` : '/paie')}
                        aria-label="Retour au tableau de bord paie"
                    >
                        Tableau de bord
                    </Button>
                    <Button
                        variant="ghost"
                        size="xs"
                        iconLeft={<RefreshCcw size={12} className={refreshLoading ? 'animate-spin' : ''} />}
                        onClick={onRefresh}
                        disabled={refreshLoading}
                        aria-label="Actualiser le contexte de la campagne"
                    >
                        Actualiser
                    </Button>
                </div>
            </div>

            {/* Stepper horizontal */}
            <PayrollPeriodStepper
                steps={steps}
                currentStep={currentStep}
                activeCode={activeStep}
                loading={loading}
            />

            {/* Bannière recalcul */}
            {recalcRequired && (
                <div
                    role="alert"
                    className="flex items-center gap-2.5 px-4 py-2 bg-[var(--color-warning-500)]/10 border-t border-[var(--color-warning-500)]/30"
                >
                    <AlertTriangle size={14} className="text-[var(--color-warning-600)] shrink-0" aria-hidden="true" />
                    <p className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] flex-1">
                        <span className="font-bold">Recalcul recommandé.</span>
                        {recalcReason && <span className="ml-1">{recalcReason}</span>}
                    </p>
                    {onRefresh && (
                        <Button
                            variant="outline"
                            size="xs"
                            loading={refreshLoading}
                            onClick={onRefresh}
                            className="border-[var(--color-warning-500)]/50 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]"
                        >
                            Relancer le calcul
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
}
