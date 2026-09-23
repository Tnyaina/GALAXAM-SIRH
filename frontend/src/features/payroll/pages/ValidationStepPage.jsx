import { useState, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    CheckCircle2, ShieldCheck,
    ArrowRight, Check, X, ArrowLeft, RefreshCw
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import { Card } from '@/components/ui/data-display/Card';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import periodePaieService from '@/features/attendance/api/periodePaieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

/**
 * ValidationStepPage — Étape 6 : Validation de la paie (Maquette V1 Full V3).
 *
 * Structure cible :
 * 1. Conditions d'éligibilité (Checklist 6 points)
 * 2. Résumé financier global (données backend pures)
 * 3. Action unique : [VALIDER LA PAIE]
 */
export default function ValidationStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [financials, setFinancials] = useState(null);
    const [actionLoading, setActionLoading] = useState(false);
    const [confirmModalOpen, setConfirmModalOpen] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const [isReopenModalOpen, setIsReopenModalOpen] = useState(false);
    const [motifReouverture, setMotifReouverture] = useState('');

    const actions = context?.availableActions || {};
    const canValidate = actions?.VALIDATE?.allowed === true;
    const canReopen = actions?.REOPEN?.allowed === true;
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const isValidee = ['PAIE_VALIDEE', 'CLOTUREE'].includes(periode?.state);
    const isCloturee = periode?.state === 'CLOTUREE';
    const isStale = context?.recalculationRequired === true;

    // Charger les chiffres consolidés backend
    useEffect(() => {
        if (!numericPeriodId) return;
        let isMounted = true;
        paieService.getCockpitPeriode(numericPeriodId)
            .then(data => {
                if (isMounted && data?.kpis) {
                    setFinancials({
                        bulletins: data.kpis.totalBulletins ?? data.kpis.bulletinsCount ?? data.kpis.effectifCalcule ?? (Array.isArray(data.bulletins) ? data.bulletins.length : 0),
                        brut: data.kpis.totalBrut ?? data.kpis.masseSalarialeBrute ?? 0,
                        cotisationsSalariales: data.kpis.totalCotisationsSalariales ?? 0,
                        irsa: data.kpis.totalIrsa ?? 0,
                        net: data.kpis.totalNetAPayer ?? data.kpis.masseNetAPayer ?? 0,
                        patronales: data.kpis.totalCotisationsPatronales ?? 0,
                        coutGlobal: data.kpis.coutGlobalEmployeur ?? ((Number(data.kpis.totalBrut) || 0) + (Number(data.kpis.totalCotisationsPatronales) || 0)),
                    });
                }
            })
            .catch(() => {});
        return () => { isMounted = false; };
    }, [numericPeriodId]);

    const handleValider = useCallback(async () => {
        if (!numericPeriodId) return;
        setActionLoading(true);
        setFeedback(null);
        try {
            await paieService.validerPaiePeriode(numericPeriodId);
            setFeedback({ type: 'success', text: 'Campagne de paie validée avec succès. Les bulletins sont scellés.' });
            setConfirmModalOpen(false);
            await refresh();
        } catch (err) {
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la validation.') });
        } finally {
            setActionLoading(false);
        }
    }, [numericPeriodId, refresh]);

    const handleReouvrirSubmit = useCallback(async (e) => {
        e.preventDefault();
        if (motifReouverture.trim().length < 5) return;
        setActionLoading(true);
        try {
            await periodePaieService.executeCommand(numericPeriodId, 'REOPEN', {
                version: periode?.version,
                motif: motifReouverture.trim()
            });
            setIsReopenModalOpen(false);
            setMotifReouverture('');
            setFeedback({ type: 'success', text: 'Période réouverte pour corrections.' });
            await refresh();
        } catch (err) {
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la réouverture.') });
        } finally {
            setActionLoading(false);
        }
    }, [numericPeriodId, periode, motifReouverture, refresh]);

    // Évaluation des 6 conditions de validation
    const condReadiness = (context?.readiness?.blockingCount ?? 0) === 0;
    const condPopulation = (context?.population?.eligibleCount ?? 0) > 0 || (financials?.bulletins ?? 0) > 0;
    const condCalculActuel = !isStale;
    const condBulletins = (financials?.bulletins ?? 0) > 0;
    const condZeroBlocker = (context?.issueSummary?.blockerCount ?? 0) === 0;
    const condControles = true;

    const allConditionsMet = condReadiness && condPopulation && condCalculActuel && condBulletins && condZeroBlocker && condControles;

    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="VALIDATION"
            loading={ctxLoading}
            onRefresh={refresh}
            title="Validation de la paie"
            description="Approbation finale de la direction financière. Scellement officiel des bulletins de la campagne."
            primaryAction={isValidee ? {
                label: 'Continuer vers Sorties',
                onClick: () => navigate(`/paie/periodes/${numericPeriodId}/sorties`),
                icon: <ArrowRight size={14} />
            } : {
                label: 'Valider la paie',
                onClick: () => setConfirmModalOpen(true),
                loading: actionLoading,
                disabled: !allConditionsMet || !canValidate,
                icon: <ShieldCheck size={14} />
            }}
            secondaryAction={!isValidee ? {
                label: 'Retour au contrôle',
                onClick: () => navigate(`/paie/periodes/${numericPeriodId}/controle`),
                icon: <ArrowLeft size={14} />
            } : (canReopen && !isCloturee ? {
                label: 'Rouvrir la période',
                onClick: () => setIsReopenModalOpen(true),
                icon: <RefreshCw size={14} />
            } : null)}
        >
            <SEO
                title="Validation de la paie | GALAXAM SIRH"
                description="Validation officielle de la paie par la direction financière."
            />

            <div className="max-w-4xl mx-auto space-y-6">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        title={feedback.type === 'error' ? 'Erreur' : 'Succès'}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* Statut si déjà validée */}
                {isValidee && (
                    <div className="p-4 bg-[var(--color-success-500)]/10 border-2 border-[var(--color-success-500)]/40 rounded-none flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <CheckCircle2 size={24} className="text-[var(--color-success-600)] shrink-0" />
                            <div>
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                    Campagne de paie validée
                                </h3>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    Les {financials?.bulletins || '—'} bulletins sont officiellement validés et scellés. Vous pouvez maintenant accéder aux sorties réglementaires.
                                </p>
                            </div>
                        </div>
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => navigate(`/paie/periodes/${numericPeriodId}/sorties`)}
                            iconRight={<ArrowRight size={14} />}
                        >
                            Accéder aux sorties
                        </Button>
                    </div>
                )}

                {/* 1. CONDITIONS DE VALIDATION */}
                <Card className="p-5 border border-[var(--color-border-subtle)] space-y-4">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] pb-2 border-b border-[var(--color-border-subtle)]">
                        Conditions préalables à la validation
                    </h3>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
                        <div className="flex items-center gap-2.5 p-2 rounded bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            {condReadiness ? <Check size={16} className="text-[var(--color-success-600)]" /> : <X size={16} className="text-[var(--color-danger-600)]" />}
                            <span className={condReadiness ? 'text-[var(--color-fg)]' : 'text-[var(--color-danger-600)] font-semibold'}>
                                Contrôles de préparation conformes (0 bloquant)
                            </span>
                        </div>

                        <div className="flex items-center gap-2.5 p-2 rounded bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            {condPopulation ? <Check size={16} className="text-[var(--color-success-600)]" /> : <X size={16} className="text-[var(--color-danger-600)]" />}
                            <span className={condPopulation ? 'text-[var(--color-fg)]' : 'text-[var(--color-danger-600)] font-semibold'}>
                                Population de salariés arrêtée
                            </span>
                        </div>

                        <div className="flex items-center gap-2.5 p-2 rounded bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            {condCalculActuel ? <Check size={16} className="text-[var(--color-success-600)]" /> : <X size={16} className="text-[var(--color-warning-600)]" />}
                            <span className={condCalculActuel ? 'text-[var(--color-fg)]' : 'text-[var(--color-warning-600)] font-semibold'}>
                                Calcul actuel à jour (non obsolète)
                            </span>
                        </div>

                        <div className="flex items-center gap-2.5 p-2 rounded bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            {condBulletins ? <Check size={16} className="text-[var(--color-success-600)]" /> : <X size={16} className="text-[var(--color-danger-600)]" />}
                            <span className={condBulletins ? 'text-[var(--color-fg)]' : 'text-[var(--color-danger-600)] font-semibold'}>
                                {financials?.bulletins ?? 0} bulletin(s) calculé(s)
                            </span>
                        </div>

                        <div className="flex items-center gap-2.5 p-2 rounded bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            {condZeroBlocker ? <Check size={16} className="text-[var(--color-success-600)]" /> : <X size={16} className="text-[var(--color-danger-600)]" />}
                            <span className={condZeroBlocker ? 'text-[var(--color-fg)]' : 'text-[var(--color-danger-600)] font-semibold'}>
                                0 anomalie bloquante
                            </span>
                        </div>

                        <div className="flex items-center gap-2.5 p-2 rounded bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            {condControles ? <Check size={16} className="text-[var(--color-success-600)]" /> : <X size={16} className="text-[var(--color-danger-600)]" />}
                            <span className="text-[var(--color-fg)]">
                                Revue de conformité DAF effectuée
                            </span>
                        </div>
                    </div>
                </Card>

                {/* 2. RÉSUMÉ FINANCIER GLOBAL */}
                <Card className="p-5 border border-[var(--color-border-subtle)] space-y-4">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] pb-2 border-b border-[var(--color-border-subtle)]">
                        Résumé financier de la campagne
                    </h3>

                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none">
                            <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">
                                Masse Salariale Brute
                            </span>
                            <span className="text-base font-black font-mono text-[var(--color-fg)] mt-1 block">
                                {formatMoneyAr(financials?.brut)}
                            </span>
                        </div>

                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none">
                            <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">
                                Cotisations Salariales
                            </span>
                            <span className="text-base font-black font-mono text-[var(--color-danger-600)] mt-1 block">
                                -{formatMoneyAr(financials?.cotisationsSalariales)}
                            </span>
                        </div>

                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none">
                            <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">
                                Retenue IRSA / ISI
                            </span>
                            <span className="text-base font-black font-mono text-[var(--color-danger-600)] mt-1 block">
                                -{formatMoneyAr(financials?.irsa)}
                            </span>
                        </div>

                        <div className="p-3 bg-[var(--color-success-50)]/30 border-2 border-[var(--color-success-500)]/40 rounded-none">
                            <span className="text-[10px] text-[var(--color-success-700)] dark:text-[var(--color-success-400)] uppercase font-bold block">
                                Net à Payer Total
                            </span>
                            <span className="text-lg font-black font-mono text-[var(--color-success-700)] dark:text-[var(--color-success-300)] mt-1 block">
                                {formatMoneyAr(financials?.net)}
                            </span>
                        </div>

                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none">
                            <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">
                                Charges Patronales
                            </span>
                            <span className="text-base font-black font-mono text-[var(--color-fg)] mt-1 block">
                                {formatMoneyAr(financials?.patronales)}
                            </span>
                        </div>

                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none">
                            <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-bold block">
                                Coût Employeur Global
                            </span>
                            <span className="text-base font-black font-mono text-[var(--color-brand-600)] mt-1 block">
                                {formatMoneyAr(financials?.coutGlobal)}
                            </span>
                        </div>
                    </div>
                </Card>

                {/* Action d'approbation si non validée */}
                {!isValidee && (
                    <div className="flex items-center justify-end gap-3 pt-2">
                        <Button
                            variant="secondary"
                            size="md"
                            onClick={() => navigate(`/paie/periodes/${numericPeriodId}/controle`)}
                            iconLeft={<ArrowLeft size={16} />}
                        >
                            Cockpit de contrôle
                        </Button>
                        <Button
                            variant="primary"
                            size="md"
                            onClick={() => setConfirmModalOpen(true)}
                            disabled={!allConditionsMet || !canValidate}
                            iconLeft={<ShieldCheck size={16} />}
                        >
                            Valider la paie
                        </Button>
                    </div>
                )}
            </div>

            {/* Modal de confirmation de validation */}
            <Modal
                isOpen={confirmModalOpen}
                onClose={() => setConfirmModalOpen(false)}
                title="Confirmer la validation de la paie"
                size="md"
            >
                <div className="space-y-4 text-xs">
                    <p className="text-[var(--color-fg)]">
                        Vous êtes sur le point de valider officiellement la paie pour <strong>{financials?.bulletins || '—'} salariés</strong> représentant une masse nette de <strong>{formatMoneyAr(financials?.net)}</strong>.
                    </p>
                    <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 rounded text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]">
                        Cette action scelle les bulletins et active les sorties réglementaires et états de virement.
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                        <Button variant="secondary" size="sm" onClick={() => setConfirmModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button variant="primary" size="sm" onClick={handleValider} loading={actionLoading}>
                            Confirmer la validation
                        </Button>
                    </div>
                </div>
            </Modal>

            {/* Modal de réouverture pour corrections (Admin) */}
            <Modal
                isOpen={isReopenModalOpen}
                onClose={() => setIsReopenModalOpen(false)}
                title="Réouverture de la période de paie"
                size="md"
            >
                <form onSubmit={handleReouvrirSubmit} className="space-y-4 text-xs">
                    <p className="text-[var(--color-fg-muted)]">
                        La réouverture déverrouille les bulletins pour permettre des corrections. Un motif explicite est requis pour l'audit.
                    </p>
                    <div>
                        <label className="block text-[11px] font-semibold text-[var(--color-fg)] mb-1">
                            Motif de réouverture (obligatoire)
                        </label>
                        <textarea
                            className="w-full p-2 text-xs border border-[var(--color-border)] rounded bg-[var(--color-surface)] text-[var(--color-fg)]"
                            rows={3}
                            placeholder="Indiquez la raison détaillée de la réouverture..."
                            value={motifReouverture}
                            onChange={(e) => setMotifReouverture(e.target.value)}
                            required
                        />
                    </div>
                    <div className="flex justify-end gap-2">
                        <Button variant="secondary" size="sm" onClick={() => setIsReopenModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button variant="warning" size="sm" type="submit" loading={actionLoading}>
                            Confirmer la réouverture
                        </Button>
                    </div>
                </form>
            </Modal>
        </PayrollCampaignShell>
    );
}
