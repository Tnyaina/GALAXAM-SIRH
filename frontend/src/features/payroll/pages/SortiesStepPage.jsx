import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Archive, ArrowRight, BookOpen, CheckCircle2, FileText, LockKeyhole, ShieldCheck } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

/** Dernière étape : contrôle des sorties disponibles et clôture de la période. */
export default function SortiesStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const id = Number(periodId) || null;
    const { periodes, context, loading, refreshSilent } = usePayrollCampaign(id);
    const [cockpit, setCockpit] = useState(null);
    const [cockpitError, setCockpitError] = useState('');
    const [confirmOpen, setConfirmOpen] = useState(false);
    const [closing, setClosing] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const period = context?.selectedPeriod || context?.currentPeriod;
    const status = period?.workflowStatuses?.paie?.code || period?.state;
    const closed = status === 'CLOTUREE';
    const validated = ['VALIDEE', 'PAIE_VALIDEE', 'CLOTUREE'].includes(status);
    const canClose = context?.availableActions?.CLOSE?.allowed === true;
    const periodCode = period?.code || `Période ${periodId}`;

    useEffect(() => {
        let active = true;
        if (!id) return undefined;
        paieService.getCockpitPeriode(id, { page: 1, size: 1 })
            .then(data => { if (active) setCockpit(data); })
            .catch(error => { if (active) setCockpitError(getErrorMessage(error, 'Totaux indisponibles.')); });
        return () => { active = false; };
    }, [id]);

    const closePeriod = useCallback(async () => {
        if (!id || !canClose) return;
        setClosing(true);
        setFeedback(null);
        try {
            await periodePaieService.executeCommand(id, 'CLOSE', { version: period?.version });
            setConfirmOpen(false);
            setFeedback({ type: 'success', message: 'Période clôturée. Les documents restent consultables.' });
            await refreshSilent();
        } catch (error) {
            setFeedback({ type: 'error', message: getErrorMessage(error, 'La clôture a échoué.') });
        } finally {
            setClosing(false);
        }
    }, [id, canClose, period, refreshSilent]);

    const kpis = cockpit?.kpis || {};
    return (
        <PayrollCampaignShell context={context} periodes={periodes} activeStep="OUTPUTS" loading={loading}
            title="Sorties et clôture" subtitle="Vérifiez la campagne, accédez aux documents disponibles et clôturez la période."
            prevStep={{ label: 'Validation', destination: `/paie/periodes/${id}/validation` }}
            primaryAction={!closed ? {
                label: 'Clôturer la période', icon: <LockKeyhole size={14} />,
                onClick: () => setConfirmOpen(true), disabled: !canClose || closing,
                disabledReason: 'La clôture doit être autorisée par le contrôle de la campagne.',
            } : undefined}
            footerCenter={closed ? 'Période clôturée · consultation des documents maintenue' : undefined}>
            <div className="space-y-4">
                {feedback && <Alert type={feedback.type} message={feedback.message} onClose={() => setFeedback(null)} />}
                {cockpitError && <Alert type="warning" message={cockpitError} />}
                {!validated && <Alert type="warning" title="Validation requise" message="Validez la paie avant la clôture et l’accès aux documents de la période." />}
                {closed && <Alert type="success" title="Période clôturée" message="La période est en lecture seule. Les bulletins individuels restent accessibles." />}

                <div className="grid grid-cols-2 xl:grid-cols-4 gap-3">
                    <StatCard title="Période" value={periodCode} color="neutral" />
                    <StatCard title="Bulletins calculés" value={kpis.effectifCalcule ?? '—'} color="neutral" />
                    <StatCard title="Brut total" value={kpis.totalGainsBruts == null ? '—' : formatMoneyAr(kpis.totalGainsBruts)} color="neutral" />
                    <StatCard title="Net à payer" value={kpis.totalNetAPayer == null ? '—' : formatMoneyAr(kpis.totalNetAPayer)} color="brand" />
                </div>

                <Card shadow="none">
                    <CardHeader><CardTitle size="sm">Documents de la période</CardTitle></CardHeader>
                    <CardBody className="space-y-0">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-3 border-b border-[var(--color-border-subtle)]">
                            <div className="flex items-start gap-3">
                                <FileText size={18} className="mt-0.5 text-[var(--color-brand-600)] shrink-0" />
                                <div><p className="text-sm font-semibold">Bulletins individuels</p><p className="text-xs text-[var(--color-fg-muted)]">PDF et Excel par salarié, depuis la liste de contrôle.</p></div>
                            </div>
                            <Button variant="outline" size="sm" iconRight={<ArrowRight size={14} />} onClick={() => navigate(`/paie/periodes/${id}/controle`)}>Consulter</Button>
                        </div>
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 py-3">
                            <div className="flex items-start gap-3">
                                <BookOpen size={18} className="mt-0.5 text-[var(--color-fg-muted)] shrink-0" />
                                <div><p className="text-sm font-semibold">Grand livre et états mensuels</p><p className="text-xs text-[var(--color-fg-muted)]">Catalogue et disponibilité des exports de paie.</p></div>
                            </div>
                            <Button variant="outline" size="sm" iconRight={<ArrowRight size={14} />} onClick={() => navigate(`/paie/declarations?periodId=${id}`)}>Voir les exports</Button>
                        </div>
                    </CardBody>
                </Card>

                <Card shadow="none">
                    <CardHeader><CardTitle size="sm">Clôture de la campagne</CardTitle></CardHeader>
                    <CardBody>
                        <div className="grid gap-3 md:grid-cols-3 text-sm">
                            <div className="flex items-start gap-2"><ShieldCheck size={17} className="text-[var(--color-brand-600)] shrink-0" /><span>Paie validée et contrôles terminés</span></div>
                            <div className="flex items-start gap-2"><CheckCircle2 size={17} className="text-[var(--color-brand-600)] shrink-0" /><span>Bulletins conservés pour consultation</span></div>
                            <div className="flex items-start gap-2"><Archive size={17} className="text-[var(--color-brand-600)] shrink-0" /><span>Statut de période verrouillé après clôture</span></div>
                        </div>
                        <p className="mt-3 text-xs text-[var(--color-fg-muted)]">Les futurs exports mensuels et trimestriels ne conditionnent pas la clôture de cette période.</p>
                    </CardBody>
                </Card>
            </div>

            <Modal isOpen={confirmOpen} onClose={() => setConfirmOpen(false)} title="Clôturer la période" size="md">
                <div className="space-y-4 text-sm">
                    <p>La période <strong>{periodCode}</strong> passera en lecture seule. Vérifiez la validation de la paie avant de poursuivre.</p>
                    <div className="flex justify-end gap-2"><Button variant="outline" size="sm" onClick={() => setConfirmOpen(false)}>Annuler</Button><Button variant="primary" size="sm" loading={closing} onClick={closePeriod}>Confirmer la clôture</Button></div>
                </div>
            </Modal>
        </PayrollCampaignShell>
    );
}
