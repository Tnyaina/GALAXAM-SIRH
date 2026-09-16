import { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { CheckCheck, AlertTriangle, Lock, CheckCircle2 } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import Modal from '@/components/ui/overlays/Modal';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';
import periodePaieService from '@/api/pointage/periodePaieService';

/**
 * ValidationStepPage — Étape 6 : Validation et clôture.
 *
 * Permet à la DAF de :
 * - Valider tous les bulletins calculés (déclenche le statut VALIDE)
 * - Clôturer la période (verrous définitifs, accès lecture seule)
 * - Rouvrir la période avec motif si corrections nécessaires (Admin uniquement)
 */
export default function ValidationStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [actionLoading, setActionLoading] = useState(null);
    const [feedback, setFeedback] = useState(null);
    const [isReopenModalOpen, setIsReopenModalOpen] = useState(false);
    const [motifReouverture, setMotifReouverture] = useState('');

    const actions = context?.availableActions || {};
    const canValidate = actions?.VALIDATE?.allowed === true;
    const canClose = actions?.CLOSE?.allowed === true;
    const canReopen = actions?.REOPEN?.allowed === true;
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const isCloturee = periode?.state === 'CLOTUREE';
    const isValidee = periode?.state === 'VALIDEE';

    const handleValider = useCallback(async () => {
        if (!numericPeriodId) return;
        if (!window.confirm('Confirmer la validation de tous les bulletins calculés ?')) return;
        setActionLoading('valider');
        setFeedback(null);
        try {
            await paieService.validerPaiePeriode(numericPeriodId);
            setFeedback({ type: 'success', text: 'Bulletins validés avec succès. La période est prête pour clôture.' });
            await refresh();
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors de la validation.' });
        } finally {
            setActionLoading(null);
        }
    }, [numericPeriodId, refresh]);

    const handleCloturer = useCallback(async () => {
        if (!numericPeriodId) return;
        if (!window.confirm('ATTENTION : La clôture est définitive et verrouille tous les bulletins (lecture seule). Confirmer ?')) return;
        setActionLoading('cloturer');
        setFeedback(null);
        try {
            await periodePaieService.executeCommand(numericPeriodId, 'CLOSE', { version: periode?.version });
            setFeedback({ type: 'success', text: 'Période clôturée. Les bulletins sont immuables.' });
            await refresh();
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors de la clôture.' });
        } finally {
            setActionLoading(null);
        }
    }, [numericPeriodId, periode, refresh]);

    const handleReouvrirSubmit = useCallback(async (e) => {
        e.preventDefault();
        if (motifReouverture.trim().length < 5) {
            alert('Le motif de réouverture est obligatoire (5 caractères minimum).');
            return;
        }
        setActionLoading('rouvrir');
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
            alert(err?.response?.data?.message || 'Erreur lors de la réouverture.');
        } finally {
            setActionLoading(null);
        }
    }, [numericPeriodId, periode, motifReouverture, refresh]);

    const statutVariant = { VALIDEE: 'success', CLOTUREE: 'secondary', EN_COURS: 'warning' };
    const formatDate = (d) => d ? new Date(d).toLocaleDateString('fr-MG', { day: '2-digit', month: 'long', year: 'numeric' }) : '–';

    const translateReason = (code) => {
        const map = {
            PAYROLL_PERIOD_PREREQUISITE_BLOCKED: 'Les bulletins doivent d’abord être calculés.',
            PAYROLL_NOT_CALCULATED: 'Aucun bulletin n’a encore été calculé pour cette période.',
            PAYROLL_NOT_VALIDATED: 'Tous les bulletins doivent être validés avant la clôture définitive.',
            PERIOD_ALREADY_CLOSED: 'La période est déjà clôturée.',
            PERIOD_ALREADY_VALIDATED: 'La période est déjà validée.',
            PERIOD_NOT_FOUND: 'Période introuvable.',
            CALCULATION_INPUTS_CHANGED: 'Des variables ont été modifiées depuis le dernier calcul.',
        };
        return map[code] || 'Condition préalable requise.';
    };

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Validation & Clôture | Paie GALAXAM SIRH"
                description="Validation des bulletins de paie et clôture de la campagne."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/validation`)}
                activeStep="VALIDATION"
                loading={ctxLoading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-3xl mx-auto px-4 py-6 space-y-6 w-full">

                {/* En-tête */}
                <div className="flex items-start gap-3">
                    <div className="w-10 h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0">
                        <CheckCheck size={20} strokeWidth={2} />
                    </div>
                    <div>
                        <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                            Étape 6 — Validation & Clôture
                        </h1>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Validez les bulletins et clôturez la campagne pour générer les exports réglementaires.
                        </p>
                    </div>
                </div>

                {/* Feedback */}
                {feedback && <Alert type={feedback.type} message={feedback.text} onClose={() => setFeedback(null)} />}

                {/* Résumé période */}
                {periode && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4">
                        <p className="text-[10px] uppercase tracking-wider text-[var(--color-fg-muted)] mb-2">Période en cours</p>
                        <div className="flex items-center gap-3 flex-wrap">
                            <span className="text-sm font-bold font-mono text-[var(--color-fg)]">{periode.code}</span>
                            <Badge variant={statutVariant[periode.state] || 'default'} dot size="sm">
                                {periode.state}
                            </Badge>
                            <span className="text-xs text-[var(--color-fg-muted)]">
                                {formatDate(periode.startDate)} → {formatDate(periode.endDate)}
                            </span>
                        </div>
                    </div>
                )}

                {/* Actions selon l'état */}
                {isCloturee ? (
                    <div className="flex items-center gap-3 p-4 bg-[var(--color-success-500)]/5 border border-[var(--color-success-500)]/20">
                        <Lock size={16} className="text-[var(--color-success-500)] shrink-0" />
                        <div className="flex-1">
                            <p className="text-xs font-bold text-[var(--color-fg)]">Période clôturée</p>
                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                Les bulletins sont immuables. Vous pouvez maintenant générer les exports réglementaires.
                            </p>
                        </div>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => navigate(`/paie/periodes/${numericPeriodId}/sorties`)}
                            id="btn-goto-sorties"
                        >
                            Aller aux exports →
                        </Button>
                    </div>
                ) : (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-4">
                        <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            Actions disponibles
                        </h2>

                        {/* Validation des bulletins */}
                        <div className="flex items-start justify-between gap-4 p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                            <div>
                                <p className="text-xs font-semibold text-[var(--color-fg)]">1. Valider les bulletins calculés</p>
                                <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                    Approuve tous les bulletins au statut CALCULE → VALIDE.
                                    {!canValidate && (
                                        <span className="ml-1 text-[var(--color-warning-600)] font-medium">
                                            — {(actions?.VALIDATE?.reasonCodes || []).map(translateReason).join(' · ') || 'Bulletins en attente de calcul'}
                                        </span>
                                    )}
                                </p>
                            </div>
                            <Button
                                variant={canValidate ? 'success' : 'outline'}
                                size="sm"
                                disabled={!canValidate || actionLoading !== null}
                                loading={actionLoading === 'valider'}
                                iconLeft={<CheckCircle2 size={13} />}
                                onClick={handleValider}
                                id="btn-valider-bulletins"
                            >
                                Valider
                            </Button>
                        </div>

                        {/* Clôture */}
                        <div className="flex items-start justify-between gap-4 p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                            <div>
                                <p className="text-xs font-semibold text-[var(--color-fg)]">2. Clôturer la période</p>
                                <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                    Verrouille définitivement les bulletins (lecture seule). Irréversible sans réouverture manuelle.
                                    {!canClose && (
                                        <span className="ml-1 text-[var(--color-warning-600)] font-medium">
                                            — {(actions?.CLOSE?.reasonCodes || []).map(translateReason).join(' · ') || 'Requiert que tous les bulletins soient validés'}
                                        </span>
                                    )}
                                </p>
                            </div>
                            <Button
                                variant={canClose ? 'primary' : 'outline'}
                                size="sm"
                                disabled={!canClose || actionLoading !== null}
                                loading={actionLoading === 'cloturer'}
                                iconLeft={<Lock size={13} />}
                                onClick={handleCloturer}
                                id="btn-cloturer-periode"
                            >
                                Clôturer
                            </Button>
                        </div>

                        {/* Réouverture (Admin) */}
                        {canReopen && (
                            <div className="flex items-start justify-between gap-4 p-3 border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/5">
                                <div>
                                    <p className="text-xs font-semibold text-[var(--color-fg)]">Réouverture (Admin)</p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        Rouvre la période pour corrections exceptionnelles. Requiert un motif circonstancié.
                                    </p>
                                </div>
                                <Button
                                    variant="outline"
                                    size="sm"
                                    disabled={actionLoading !== null}
                                    onClick={() => setIsReopenModalOpen(true)}
                                    className="border-[var(--color-warning-500)]/50"
                                    id="btn-rouvrir-periode"
                                >
                                    Rouvrir
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </div>

            <PayrollNavigationFooter
                prevStep={{
                    label: 'Contrôle des bulletins',
                    destination: `/paie/periodes/${numericPeriodId}/controle`,
                }}
                nextStep={{
                    label: 'Sorties & Documents',
                    destination: `/paie/periodes/${numericPeriodId}/sorties`,
                    disabled: !isValidee && !isCloturee,
                    disabledReason: 'Les bulletins doivent d’abord être validés pour accéder aux sorties.',
                }}
            />

            {/* Modal de réouverture */}
            <Modal
                isOpen={isReopenModalOpen}
                onClose={() => { setIsReopenModalOpen(false); setMotifReouverture(''); }}
                title="Motif de réouverture"
                icon={<AlertTriangle size={16} className="text-[var(--color-warning-500)]" />}
                size="md"
                footer={
                    <>
                        <Button variant="ghost" size="sm" onClick={() => { setIsReopenModalOpen(false); setMotifReouverture(''); }}>
                            Annuler
                        </Button>
                        <Button
                            variant="primary"
                            size="sm"
                            loading={actionLoading === 'rouvrir'}
                            onClick={handleReouvrirSubmit}
                            id="btn-confirmer-reouverture"
                        >
                            Confirmer la réouverture
                        </Button>
                    </>
                }
            >
                <form onSubmit={handleReouvrirSubmit} className="space-y-3">
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        La réouverture annule la clôture et remet la période en état ouvert.
                        Toutes les modifications seront tracées.
                    </p>
                    <div>
                        <label htmlFor="motif-reouverture" className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                            Motif de réouverture <span className="text-[var(--color-danger-500)]">*</span>
                        </label>
                        <textarea
                            id="motif-reouverture"
                            rows={3}
                            value={motifReouverture}
                            onChange={e => setMotifReouverture(e.target.value)}
                            placeholder="Décrivez la raison de la réouverture (minimum 5 caractères)…"
                            className="w-full px-3 py-2 text-xs bg-[var(--color-surface)] border border-[var(--color-border)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)]/40 resize-none"
                            required
                        />
                    </div>
                </form>
            </Modal>
        </div>
    );
}
