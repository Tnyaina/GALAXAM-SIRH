import ManualVariableValueFields from '../ManualVariableValueFields';
import { manualMethod, manualValuePayload } from '../../utils/manualVariableCalculation';
import { useCallback, useEffect, useState } from 'react';
import { Calculator, Plus, RefreshCw } from 'lucide-react';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { useToast } from '@/components/ui/feedback/Toast';
import Input, { Textarea } from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { isManualPayrollVariable } from '@/features/payroll/utils/variableTypes';

const statuses = {
    EN_ATTENTE: ['À approuver', 'warning'],
    APPROUVE: ['Approuvée', 'success'],
    IMPUTE_PAIE: ['Imputée', 'info'],
    ANNULE: ['Annulée', 'neutral'],
    REJETE: ['Rejetée', 'neutral'],
};

const money = value => formatMoneyAr(value ?? 0);

export default function BulletinVariableCorrection({ bulletin, onBulletinUpdated, onCampaignUpdated }) {
    const toast = useToast();
    const periodId = bulletin?.idPeriodePaie;
    const employeeId = bulletin?.idEmploye;
    const [variables, setVariables] = useState([]);
    const [types, setTypes] = useState([]);
    const [context, setContext] = useState(null);
    const [correctionStatus, setCorrectionStatus] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [showForm, setShowForm] = useState(false);
    const [showCancelled, setShowCancelled] = useState(false);
    const [form, setForm] = useState({ typeMouvement: '', montant: '', motif: '' });
    const [cancelId, setCancelId] = useState(null);
    const [cancelReason, setCancelReason] = useState('');
    const [preview, setPreview] = useState(null);

    const refresh = useCallback(async () => {
        if (!periodId || !employeeId) return;
        const [items, catalogue, workflow, status] = await Promise.all([
            paieService.listerAjustements(periodId, employeeId),
            paieService.listerTypesMouvements(periodId),
            paieService.getWorkflowContext(periodId),
            paieService.getBulletinCorrectionStatus(bulletin.idBulletin),
        ]);
        setVariables(Array.isArray(items) ? items : []);
        setTypes(Array.isArray(catalogue) ? catalogue : []);
        setContext(workflow);
        setCorrectionStatus(status);
    }, [periodId, employeeId, bulletin.idBulletin]);

    useEffect(() => {
        let active = true;
        refresh().catch(cause => { if (active) setError(getErrorMessage(cause, 'Variables du bulletin indisponibles.')); });
        return () => { active = false; };
    }, [refresh]);

    const action = async (operation, success) => {
        setBusy(true);
        setError('');
        try {
            await operation();
            setPreview(null);
            toast.success(success);
            try {
                await refresh();
                await onCampaignUpdated?.();
            } catch (cause) {
                setError(getErrorMessage(cause, 'Action enregistrée, mais actualisation impossible. Rechargez la page.'));
            }
            return true;
        } catch (cause) {
            setError(getErrorMessage(cause, 'Action impossible.'));
            return false;
        } finally {
            setBusy(false);
        }
    };

    const manualVariables = variables.filter(isManualPayrollVariable);
    const cancelledCount = manualVariables.filter(variable => variable.statut === 'ANNULE').length;
    const visibleVariables = manualVariables.filter(variable => showCancelled || variable.statut !== 'ANNULE');
    const pendingCount = correctionStatus?.pendingCount ?? manualVariables.filter(variable => variable.statut === 'EN_ATTENTE').length;
    const pending = pendingCount > 0;
    const stale = correctionStatus?.recalculationRequired === true;
    const canAdd = context?.availableActions?.ADD_VARIABLE?.allowed === true;
    const canCalculate = context?.availableActions?.CALCULATE?.allowed === true;
    const selectedType = types.find(type => type.code === form.typeMouvement);
    const previewBulletin = preview?.bulletin;
    const taxType = Number(bulletin.retenueIsi5 || previewBulletin?.retenueIsi5 || 0) > 0
        ? 'ISI' : Number(bulletin.irsaNet || previewBulletin?.irsaNet || 0) > 0 ? 'IRSA' : null;
    const comparison = [
        ['Brut', bulletin.totalGainsBruts, previewBulletin?.totalGainsBruts],
        ['Cotisations salariales', bulletin.totalCotisationsSalariales, previewBulletin?.totalCotisationsSalariales],
        taxType && [taxType,
            Number(bulletin.retenueIsi5 || 0) > 0 ? bulletin.retenueIsi5 : bulletin.irsaNet,
            Number(previewBulletin?.retenueIsi5 || 0) > 0 ? previewBulletin.retenueIsi5 : previewBulletin?.irsaNet],
        ['Net à payer', bulletin.netAPayer, previewBulletin?.netAPayer],
    ].filter(Boolean);

    const submit = async event => {
        event.preventDefault();
        let valuePayload;
        try { valuePayload = manualValuePayload(selectedType, form); }
        catch (cause) { setError(cause.message); return; }
        if (!selectedType || form.motif.trim().length < 8) {
            setError('Renseignez le type, un montant positif et un motif de huit caractères minimum.');
            return;
        }
        const saved = await action(() => paieService.creerAjustement({
            idEmploye: employeeId,
            idPeriodePaie: periodId,
            typeMouvement: selectedType.code,
            sens: selectedType.sens,
            ...valuePayload,
            motif: form.motif.trim(),
        }), 'Variable créée. Approuvez-la avant la simulation et le recalcul.');
        if (saved) {
            setShowForm(false);
            setForm({ typeMouvement: '', montant: '', motif: '' });
        }
    };

    const cancel = async variable => {
        if (cancelReason.trim().length < 8) {
            setError('Indiquez un motif d’annulation de huit caractères minimum.');
            return;
        }
        const saved = await action(() => paieService.executerCommandeAjustement(variable.idAvance, 'CANCEL', cancelReason.trim(), variable.version),
            'Variable annulée. Vérifiez le nouveau calcul du bulletin.');
        if (saved) {
            setCancelId(null);
            setCancelReason('');
        }
    };

    const simulate = async () => {
        setBusy(true);
        setError('');
        try {
            const result = await paieService.calculerBulletinEmploye(employeeId, periodId, true);
            setPreview(result);
            toast.success('Simulation actualisée. Aucun montant n’a été enregistré.');
        } catch (cause) {
            setError(getErrorMessage(cause, 'Simulation impossible.'));
        } finally {
            setBusy(false);
        }
    };

    const recalculate = async () => {
        const saved = await action(() => paieService.calculerBulletinEmploye(employeeId, periodId, false),
            'Bulletin recalculé. Vérifiez les nouveaux montants avant validation.');
        if (saved) {
            try {
                await onBulletinUpdated?.();
            } catch (cause) {
                setError(getErrorMessage(cause, 'Bulletin recalculé, mais rechargement impossible. Rechargez la page.'));
            }
        }
    };

    return (
        <section className="space-y-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3" aria-label="Variables du bulletin">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg)]">Variables du bulletin</h3>
                {canAdd && <Button type="button" variant="outline" size="xs" iconLeft={<Plus size={13} />} onClick={() => setShowForm(value => !value)} disabled={busy}>
                    {showForm ? 'Fermer' : 'Ajouter'}
                </Button>}
            </div>

            {error && <Alert type="error" message={error} onClose={() => setError('')} />}
            {pending && <Alert type="warning" message={`${pendingCount} variable${pendingCount > 1 ? 's' : ''} à approuver. Le calcul est bloqué.`} />}
            {stale && !pending && <Alert type="warning" message="Bulletin à recalculer après modification." />}

            {cancelledCount > 0 && <Button type="button" variant="ghost" size="xs" onClick={() => setShowCancelled(value => !value)}>
                {showCancelled ? 'Masquer les annulées' : `Afficher les annulées (${cancelledCount})`}
            </Button>}

            {visibleVariables.length === 0 ? (
                <p className="py-1 text-xs text-[var(--color-fg-muted)]">Aucune variable pour ce bulletin.</p>
            ) : (
                <div className="divide-y divide-[var(--color-border-subtle)] border-y border-[var(--color-border-subtle)]">
                    {visibleVariables.map(variable => {
                        const [label, variant] = statuses[variable.statut] || [variable.statut, 'neutral'];
                        const actions = variable.workflowActions || {
                            APPROVE: canAdd && variable.statut === 'EN_ATTENTE',
                            CANCEL: canAdd && ['EN_ATTENTE', 'APPROUVE', 'IMPUTE_PAIE'].includes(variable.statut) && !variable.versementConfirmeAt,
                        };
                        return <div key={variable.idAvance} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5 py-2">
                            <div className="min-w-0 flex-1">
                                <div className="flex flex-wrap items-center gap-2">
                                    <span className="text-xs font-semibold text-[var(--color-fg)]">{variable.typeLibelle || variable.typeMouvement}</span>
                                    <Badge variant={variant} size="xs">{label}</Badge>
                                </div>
                                {variable.motif && <p className="truncate text-[11px] text-[var(--color-fg-muted)]">{variable.motif}</p>}
                            </div>
                            <div className="flex flex-wrap items-center justify-end gap-1.5">
                                <strong className={`whitespace-nowrap font-data text-xs ${variable.sens === 'RETENUE' ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-success-700)]'}`}>
                                    {variable.sens === 'RETENUE' ? '−' : '+'}{money(variable.montant)}
                                </strong>
                                {actions.APPROVE && <Button type="button" variant="outline" size="xs" disabled={busy}
                                    onClick={() => action(() => paieService.executerCommandeAjustement(variable.idAvance, 'APPROVE', null, variable.version), 'Variable approuvée. Vous pouvez simuler le bulletin.')}>Approuver</Button>}
                                {actions.CANCEL && <Button type="button" variant="ghost" size="xs" disabled={busy} onClick={() => { setCancelId(variable.idAvance); setCancelReason(''); }}>Annuler</Button>}
                            </div>
                            {cancelId === variable.idAvance && <div className="w-full space-y-2 bg-[var(--color-bg-subtle)] p-2">
                                <Input label="Motif d’annulation" value={cancelReason} onChange={event => setCancelReason(event.target.value)} placeholder="Motif de la correction" />
                                <div className="flex gap-2"><Button type="button" variant="danger" size="xs" disabled={busy} onClick={() => cancel(variable)}>Confirmer l’annulation</Button>
                                    <Button type="button" variant="ghost" size="xs" onClick={() => setCancelId(null)}>Fermer</Button></div>
                            </div>}
                        </div>;
                    })}
                </div>
            )}

            {showForm && <form onSubmit={submit} className="space-y-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3">
                <SearchSelect
                    label="Type de variable"
                    placeholder="Choisir un type"
                    value={form.typeMouvement}
                    onChange={value => setForm(current => ({ ...current, typeMouvement: value }))}
                    options={types.map(type => ({ value: type.code, label: type.libelle }))}
                />
                <ManualVariableValueFields type={selectedType} form={form} onChange={(name, value) => setForm(current => ({ ...current, [name]: value }))} />
                {manualMethod(selectedType) === 'MONTANT' && <MoneyInput label="Montant (Ar)" placeholder="Ex. 100 000,50" value={form.montant} onValueChange={value => setForm(current => ({ ...current, montant: value }))} min={0} maxDecimals={2} />}
                <Textarea label="Motif" placeholder="Ex. Prime de performance du mois" value={form.motif} onChange={event => setForm(current => ({ ...current, motif: event.target.value }))} rows={2} />
                <div className="flex gap-2"><Button type="submit" variant="primary" size="xs" disabled={busy}>Enregistrer</Button>
                    <Button type="button" variant="ghost" size="xs" onClick={() => setShowForm(false)}>Fermer</Button></div>
            </form>}

            {canAdd && (pending || stale || manualVariables.some(variable => variable.statut === 'APPROUVE')) && <div className="space-y-2 border-t border-[var(--color-border-subtle)] pt-2.5">
                <div className="flex flex-wrap gap-2">
                    <Button type="button" variant="outline" size="xs" iconLeft={<Calculator size={13} />} onClick={simulate} disabled={busy || pending}>Simuler ce bulletin</Button>
                    <Button type="button" variant="primary" size="xs" iconLeft={<RefreshCw size={13} />} onClick={recalculate}
                        disabled={busy || pending || !canCalculate || !stale || !preview}>Recalculer ce bulletin</Button>
                </div>
                {previewBulletin && <div className="border border-[var(--color-border-subtle)]">
                    <div className="grid grid-cols-3 gap-2 bg-[var(--color-bg-subtle)] p-2 text-[11px] font-bold text-[var(--color-fg-muted)]"><span>Montant</span><span className="text-right">Actuel</span><span className="text-right">Simulé</span></div>
                    {comparison.map(([label, current, projected]) => <div key={label} className="grid grid-cols-3 gap-2 border-t border-[var(--color-border-subtle)] p-2 text-[11px] text-[var(--color-fg)]"><span>{label}</span><span className="text-right font-data">{money(current)}</span><span className="text-right font-data font-semibold">{money(projected)}</span></div>)}
                    <div className="flex justify-between border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-2 text-[11px] font-bold text-[var(--color-fg)]">
                        <span>Écart de net à payer</span>
                        <span className="font-data">{Number(previewBulletin.netAPayer || 0) >= Number(bulletin.netAPayer || 0) ? '+' : '−'}{money(Math.abs(Number(previewBulletin.netAPayer || 0) - Number(bulletin.netAPayer || 0)))}</span>
                    </div>
                </div>}
                {!canCalculate && !pending && <p className="text-[11px] text-[var(--color-fg-muted)]">Le recalcul n’est pas autorisé pour cette campagne.</p>}
            </div>}
        </section>
    );
}
