import { lazy, Suspense, useCallback, useEffect, useState } from 'react';
import { Calculator, CheckCircle2, Lock } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/api/apiClient';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import { paieService } from '@/features/payroll/api/paieService';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';

const VariablesPaieTab = lazy(() => import('@/features/payroll/components/tabs/VariablesPaieTab'));

export default function DeparturePayrollPanel({ dossier, onDossierChange }) {
    const { hasPermission, user } = useAuth();
    const toast = useToast();
    const [state, setState] = useState(null);
    const [periodContext, setPeriodContext] = useState(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [confirm, setConfirm] = useState(null);
    const dossierId = dossier?.idDossierSortie;
    const canFreeze = hasPermission('POINTAGE_VALIDATE') || user?.role === 'ROLE_ADMIN';
    const canCalculate = hasPermission('PAIE_WRITE') || user?.role === 'ROLE_ADMIN';
    const canValidate = hasPermission('PAIE_VALIDATE') || user?.role === 'ROLE_ADMIN';

    const reload = useCallback(async () => {
        if (!dossierId) return;
        try {
            setState(await dossierSortieService.obtenirPaieFinale(dossierId));
            setError('');
        } catch (e) {
            setError(getErrorMessage(e, 'Impossible de charger la paie finale.'));
        }
    }, [dossierId]);

    useEffect(() => { reload(); }, [reload]);
    useEffect(() => {
        if (!state?.periodeId) return undefined;
        let active = true;
        paieService.getWorkflowContext(state.periodeId)
            .then(context => { if (active) setPeriodContext(context); })
            .catch(() => { if (active) setPeriodContext(null); });
        return () => { active = false; };
    }, [state?.periodeId]);

    const run = async (action, success) => {
        setBusy(true);
        setError('');
        try {
            const result = await action(dossierId);
            setState(result);
            toast.success(success);
            onDossierChange?.(await dossierSortieService.obtenirDossier(dossierId));
        } catch (e) {
            setError(getErrorMessage(e, 'Action impossible. Vérifiez les prérequis du dossier.'));
        } finally {
            setBusy(false);
            setConfirm(null);
        }
    };

    if (!dossierId) return <Alert type="info" title="Enregistrez le dossier"
        message="La préparation doit être enregistrée avant le traitement de la paie finale." />;

    const validated = ['VALIDE', 'CLOTURE'].includes(state?.statutBulletin);
    const calculated = state?.statutBulletin === 'CALCULE';
    const departureIsFuture = Boolean(state?.dateDepart && state.dateDepart > getTodayDateString());
    const evpCanMutate = !validated && periodContext?.availableActions?.ADD_VARIABLE?.allowed === true;
    const steps = [
        { label: 'Pointage final', done: state?.pointageGele, detail: state?.pointageGele ? 'Gelé pour ce salarié' : 'À contrôler puis figer' },
        { label: 'Bulletin individuel', done: calculated || validated, detail: state?.statutBulletin || 'Non calculé' },
        { label: 'Validation du bulletin', done: validated, detail: validated ? 'Validé et conservé pour la clôture' : 'En attente' },
        { label: 'Solde de tout compte', done: Boolean(dossier?.idStc), detail: dossier?.idStc ? 'Scellé' : 'À rapprocher avec le bulletin' },
    ];

    return <div id="panel-paie-finale" role="tabpanel" aria-labelledby="tab-paie-finale" className="space-y-3">
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-3 py-3 sm:px-4">
            <h3 className="text-base font-semibold text-[var(--color-fg)]">Paie finale du salarié sortant</h3>
            <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">
                Le pointage est figé uniquement pour ce salarié. Son bulletin validé sera conservé lors de la clôture collective.
            </p>
        </div>
        {error && <Alert type="error" title="Action non effectuée" message={error} />}
        {departureIsFuture && <Alert type="info" title={`Départ prévu le ${fmtDate(state.dateDepart)}`} message="Préparez le pointage, les congés et les EVP dès maintenant. Le gel individuel et le bulletin final seront disponibles à partir de la date effective de sortie." />}
        {state && !validated && <div className="grid gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
            <div>
                <h4 className="text-sm font-semibold text-[var(--color-fg)]">Avant de figer le pointage</h4>
                <p className="mt-1 text-xs leading-5 text-[var(--color-fg-muted)]">Vérifiez les présences et absences jusqu’au départ, le solde de congés et les primes ou retenues exceptionnelles. Les EVP doivent être saisis et arbitrés avant le calcul du bulletin final.</p>
                <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs font-semibold">
                    <a href="/pointages/suivi" className="text-[var(--color-brand-700)] hover:underline">Contrôler le pointage</a>
                    <a href="/conges/droits" className="text-[var(--color-brand-700)] hover:underline">Vérifier les congés</a>
                </div>
            </div>
        </div>}
        {state && <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-4" aria-label="Progression de la paie finale">
            {steps.map((step, index) => <div key={step.label} className={`flex min-w-0 items-start gap-2 border border-l-[3px] px-2.5 py-2 ${step.done ? 'border-[var(--color-border-subtle)] border-l-[var(--color-brand-600)] bg-[var(--color-brand-50)]' : 'border-[var(--color-border-subtle)] border-l-[var(--color-border-strong)] bg-[var(--color-bg-subtle)]'}`}>
                <span className="flex h-6 w-6 shrink-0 items-center justify-center border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-xs font-semibold text-[var(--color-fg)]">
                    {step.done ? <CheckCircle2 size={15} className="text-[var(--color-brand-700)]" /> : index + 1}
                </span>
                <div className="min-w-0">
                    <p className="text-xs font-semibold text-[var(--color-fg)]">{step.label}</p>
                    <p className="text-xs text-[var(--color-fg-muted)]">{step.detail}</p>
                </div>
            </div>)}
        </div>}
        {state && <div className="flex flex-wrap gap-2">
            {!state.pointageGele && canFreeze && <Button size="sm" variant="outline" disabled={busy || departureIsFuture}
                iconLeft={<Lock size={15} />} onClick={() => setConfirm('freeze')}>Valider et figer le pointage</Button>}
            {state.pointageGele && !validated && canCalculate && <Button size="sm" variant="outline" disabled={busy || departureIsFuture}
                iconLeft={<Calculator size={15} />} onClick={() => run(dossierSortieService.calculerBulletinFinal, 'Bulletin final calculé.')}>
                {calculated ? 'Recalculer le bulletin' : 'Calculer le bulletin'}</Button>}
            {calculated && canValidate && <Button size="sm" variant="primary" disabled={busy || departureIsFuture}
                onClick={() => setConfirm('validate')}>Valider le bulletin final</Button>}
            {state.bulletinId && <span className="px-3 py-2 text-xs text-[var(--color-fg-muted)]">
                Bulletin n° {state.bulletinId}</span>}
        </div>}
        {state?.periodeId && <section className="space-y-3 border-t border-[var(--color-border-subtle)] pt-4" aria-label="EVP du salarié sortant">
            <div><h4 className="text-sm font-bold text-[var(--color-fg)]">Éléments variables du salarié</h4><p className="mt-1 text-xs text-[var(--color-fg-muted)]">Primes et retenues exceptionnelles de la période du départ. Les EVP approuvés alimentent le bulletin individuel puis le STC.</p></div>
            <Suspense fallback={<p className="text-xs text-[var(--color-fg-muted)]">Chargement des EVP…</p>}>
                <VariablesPaieTab idPeriodeActive={state.periodeId} embeddedEmployeeId={state.employeId}
                    canMutate={evpCanMutate} periodStatus={state.statutPeriode}
                    mutationDisabledReason={periodContext?.availableActions?.ADD_VARIABLE?.reasonCodes?.join(', ') || 'Les EVP ne peuvent plus être modifiés pour ce bulletin.'}
                    onVariableChanged={reload} />
            </Suspense>
        </section>}
        {state && !validated && !canFreeze && !canCalculate && !canValidate &&
            <p className="text-sm text-[var(--color-fg-muted)]">Les actions de gel et de validation demandent les droits Pointage et Paie correspondants.</p>}
        <ConfirmDialog isOpen={Boolean(confirm)} onClose={() => setConfirm(null)}
            onConfirm={() => confirm === 'freeze'
                ? run(dossierSortieService.gelerPointageFinal, 'Pointage individuel gelé.')
                : run(dossierSortieService.validerBulletinFinal, 'Bulletin final validé.')}
            title={confirm === 'freeze' ? 'Figer le pointage final' : 'Valider le bulletin final'}
            message={confirm === 'freeze'
                ? 'Les journées jusqu’au départ seront figées pour ce salarié. Les autres salariés restent en cours de saisie.'
                : 'Ce bulletin sera conservé lors du calcul collectif et rapproché du STC.'}
            confirmLabel={confirm === 'freeze' ? 'Figer le pointage' : 'Valider le bulletin'} />
    </div>;
}
