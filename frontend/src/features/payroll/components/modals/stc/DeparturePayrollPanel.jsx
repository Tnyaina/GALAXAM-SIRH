import { useCallback, useEffect, useState } from 'react';
import { Calculator, CheckCircle2, Clock, Lock } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Alert from '@/components/ui/feedback/Alert';
import { Card } from '@/components/ui/data-display/Card';
import { useToast } from '@/components/ui/feedback/Toast';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/api/apiClient';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';

export default function DeparturePayrollPanel({ dossier, onDossierChange }) {
    const { hasPermission, user } = useAuth();
    const toast = useToast();
    const [state, setState] = useState(null);
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
    const steps = [
        { label: 'Pointage final', done: state?.pointageGele, detail: state?.pointageGele ? 'Gelé pour ce salarié' : 'À contrôler puis figer' },
        { label: 'Bulletin individuel', done: calculated || validated, detail: state?.statutBulletin || 'Non calculé' },
        { label: 'Validation du bulletin', done: validated, detail: validated ? 'Validé et conservé pour la clôture' : 'En attente' },
        { label: 'Solde de tout compte', done: Boolean(dossier?.idStc), detail: dossier?.idStc ? 'Scellé' : 'À rapprocher avec le bulletin' },
    ];

    return <div id="panel-paie-finale" role="tabpanel" aria-labelledby="tab-paie-finale" className="space-y-4">
        <div>
            <h3 className="text-base font-semibold text-[var(--color-fg)]">Paie finale du salarié sortant</h3>
            <p className="mt-1 text-sm text-[var(--color-fg-muted)]">
                Le pointage est figé uniquement pour ce salarié. Son bulletin validé sera conservé lors de la clôture collective.
            </p>
        </div>
        {error && <Alert type="error" title="Action non effectuée" message={error} />}
        {state && <Card shadow="none" className="divide-y divide-[var(--color-border-subtle)]">
            {steps.map((step, index) => <div key={step.label} className="flex items-center gap-3 p-3">
                <span className="flex h-7 w-7 shrink-0 items-center justify-center border border-[var(--color-border-subtle)] text-xs font-semibold text-[var(--color-fg)]">
                    {step.done ? <CheckCircle2 size={16} className="text-[var(--color-success-700)]" /> : index + 1}
                </span>
                <div className="min-w-0 flex-1">
                    <p className="text-sm font-semibold text-[var(--color-fg)]">{step.label}</p>
                    <p className="text-xs text-[var(--color-fg-muted)]">{step.detail}</p>
                </div>
                {step.done ? <CheckCircle2 size={16} className="text-[var(--color-success-700)]" />
                    : <Clock size={16} className="text-[var(--color-fg-muted)]" />}
            </div>)}
        </Card>}
        {state && <div className="flex flex-wrap gap-2">
            {!state.pointageGele && canFreeze && <Button size="sm" variant="outline" disabled={busy}
                iconLeft={<Lock size={15} />} onClick={() => setConfirm('freeze')}>Valider et figer le pointage</Button>}
            {state.pointageGele && !validated && canCalculate && <Button size="sm" variant="outline" disabled={busy}
                iconLeft={<Calculator size={15} />} onClick={() => run(dossierSortieService.calculerBulletinFinal, 'Bulletin final calculé.')}>
                {calculated ? 'Recalculer le bulletin' : 'Calculer le bulletin'}</Button>}
            {calculated && canValidate && <Button size="sm" variant="primary" disabled={busy}
                onClick={() => setConfirm('validate')}>Valider le bulletin final</Button>}
            {state.bulletinId && <span className="px-3 py-2 text-xs text-[var(--color-fg-muted)]">
                Bulletin n° {state.bulletinId}</span>}
        </div>}
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
