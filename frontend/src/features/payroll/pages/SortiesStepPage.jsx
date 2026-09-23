import { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    FileText, Download, Lock,
    Users, CreditCard, BookOpen, Receipt, Building2,
    Calculator, Shield
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import Modal from '@/components/ui/overlays/Modal';
import { Card } from '@/components/ui/data-display/Card';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import periodePaieService from '@/features/attendance/api/periodePaieService';
import { getErrorMessage } from '@/api/apiClient';

/**
 * SortiesStepPage — Étape 7 : Sorties de campagne & Clôture (Maquette V1 Full V3).
 *
 * Organisation par domaines logiques :
 * - SALARIÉS : Bulletins individuels et consolidés
 * - PAIEMENT : État de virement bancaire
 * - PAIE : Journal de paie & état nominatif
 * - FISCAL : Sorties mensuelles IRSA & ISI
 * - SOCIAL : Données mensuelles pour consolidations CNaPS / Santé
 * - COMPTABILITÉ : Export OD et écritures comptables
 * - AUDIT : Dossier de campagne et scellement
 *
 * Action de clôture uniquement si le backend l'autorise (CLOSE.allowed).
 */
export default function SortiesStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [actionLoading, setActionLoading] = useState(false);
    const [confirmCloseModal, setConfirmCloseModal] = useState(false);
    const [downloadingType, setDownloadingType] = useState(null);
    const [feedback, setFeedback] = useState(null);

    const actions = context?.availableActions || {};
    const canClose = actions?.CLOSE?.allowed === true;
    // CAPABILITY : seule source pour autoriser un export. Ce n'est pas un état métier.
    const canGenerateOutput = actions?.GENERATE_OUTPUT?.allowed === true;
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const isCloturee = periode?.state === 'CLOTUREE';
    const isValidee = ['PAIE_VALIDEE', 'CLOTUREE'].includes(periode?.state);
    const codePeriode = periode?.code || `Période #${numericPeriodId}`;

    const handleCloturer = useCallback(async () => {
        if (!numericPeriodId) return;
        setActionLoading(true);
        setFeedback(null);
        try {
            await periodePaieService.executeCommand(numericPeriodId, 'CLOSE', { version: periode?.version });
            setFeedback({ type: 'success', text: 'Campagne définitivement clôturée. Les données sont immuables.' });
            setConfirmCloseModal(false);
            await refresh();
        } catch (err) {
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la clôture.') });
        } finally {
            setActionLoading(false);
        }
    }, [numericPeriodId, periode, refresh]);

    const handleDownload = async (type, label) => {
        if (!numericPeriodId) return;
        setDownloadingType(type);
        try {
            let res;
            if (type === 'VIREMENT') {
                res = await paieService.exporterFichierVirement(numericPeriodId);
            } else if (type === 'SAFI') {
                res = await paieService.genererExportSafi(numericPeriodId);
            } else if (type === 'DNTS') {
                res = await paieService.genererExportDntsCnaps(numericPeriodId);
            } else if (type === 'ISI') {
                res = await paieService.genererExportEdiIsi(numericPeriodId);
            }

            if (res?.data) {
                const blob = new Blob([res.data], { type: 'application/octet-stream' });
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `${type.toLowerCase()}_${codePeriode}.txt`;
                a.click();
                URL.revokeObjectURL(url);
                setFeedback({ type: 'success', text: `Document ${label} téléchargé avec succès.` });
            } else {
                setFeedback({ type: 'info', text: `Génération du document ${label} transmise.` });
            }
        } catch (err) {
            setFeedback({ type: 'error', text: getErrorMessage(err, `Erreur de génération du document ${label}.`) });
        } finally {
            setDownloadingType(null);
        }
    };

    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="OUTPUTS"
            loading={ctxLoading}
            onRefresh={refresh}
            title={`Sorties de ${codePeriode}`}
            description="Documents officiels de la campagne, états financiers et archivage légal scellé."
            primaryAction={!isCloturee && canClose ? {
                label: 'Clôturer la campagne',
                onClick: () => setConfirmCloseModal(true),
                loading: actionLoading,
                icon: <Lock size={14} />
            } : null}
        >
            <SEO
                title={`Sorties de ${codePeriode} | GALAXAM SIRH`}
                description="Bulletins, virements, déclarations et sorties réglementaires."
            />

            <div className="space-y-6">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        title={feedback.type === 'error' ? 'Erreur' : 'Information'}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* Bannière d'état */}
                {isCloturee ? (
                    <div className="p-4 bg-[var(--color-surface)] border-2 border-[var(--color-border-subtle)] rounded-none flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="w-9 h-9 bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] rounded-full flex items-center justify-center">
                                <Lock size={18} />
                            </div>
                            <div>
                                <div className="flex items-center gap-2">
                                    <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                        Campagne archivée & clôturée
                                    </h3>
                                    <Badge variant="secondary" size="xs">READ ONLY</Badge>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                    Les données de paie de cette période sont définitivement immuables. Tous les documents ci-dessous restent téléchargeables.
                                </p>
                            </div>
                        </div>
                    </div>
                ) : !isValidee ? (
                    <Alert
                        type="warning"
                        title="Validation requise"
                        message="La campagne de paie doit d'abord être validée avant que les sorties et fichiers de virement officiels ne soient débloqués."
                        action={
                            <Button
                                variant="outline"
                                size="xs"
                                onClick={() => navigate(`/paie/periodes/${numericPeriodId}/validation`)}
                            >
                                Aller à la validation
                            </Button>
                        }
                    />
                ) : null}

                {/* Grille des 7 domaines de sorties */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">

                    {/* 1. SALARIÉS */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <Users size={14} className="text-[var(--color-brand-600)]" />
                                    Salariés
                                </span>
                                <Badge variant="success" size="xs">Disponibles</Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Bulletins de paie individuels au format officiel certifié DAF.
                            </p>
                        </div>
                        <div className="pt-2">
                            <Button
                                variant="secondary"
                                size="xs"
                                className="w-full"
                                onClick={() => navigate(`/paie/periodes/${numericPeriodId}/controle`)}
                                iconLeft={<FileText size={13} />}
                            >
                                Consulter les bulletins
                            </Button>
                        </div>
                    </Card>

                    {/* 2. PAIEMENT */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <CreditCard size={14} className="text-[var(--color-sky-600)]" />
                                    Paiement
                                </span>
                                <Badge variant={isValidee ? 'success' : 'default'} size="xs">
                                    {isValidee ? 'Prêt' : 'En attente'}
                                </Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Fichier d'ordre de virement bancaire collectif pour la trésorerie.
                            </p>
                        </div>
                        <div className="pt-2">
                            <Button
                                variant="primary"
                                size="xs"
                                className="w-full"
                                disabled={!canGenerateOutput || downloadingType === 'VIREMENT'}
                                loading={downloadingType === 'VIREMENT'}
                                onClick={() => handleDownload('VIREMENT', 'Ordre de virement')}
                                iconLeft={<Download size={13} />}
                            >
                                Télécharger fichier virement
                            </Button>
                        </div>
                    </Card>

                    {/* 3. PAIE */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <BookOpen size={14} className="text-[var(--color-success-600)]" />
                                    Paie
                                </span>
                                <Badge variant="default" size="xs">Consultation</Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Journal de paie mensuel et état nominatif de contrôle complet.
                            </p>
                        </div>
                        <div className="pt-2">
                            <Button
                                variant="secondary"
                                size="xs"
                                className="w-full"
                                onClick={() => navigate(`/paie/periodes/${numericPeriodId}/controle`)}
                                iconLeft={<BookOpen size={13} />}
                            >
                                État nominatif & Journal
                            </Button>
                        </div>
                    </Card>

                    {/* 4. FISCAL */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <Receipt size={14} className="text-[var(--color-warning-600)]" />
                                    Fiscal
                                </span>
                                <Badge variant={isValidee ? 'success' : 'default'} size="xs">
                                    {isValidee ? 'Calculé' : 'En attente'}
                                </Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Déclarations mensuelles IRSA retenue à la source et EDI-ISI.
                            </p>
                        </div>
                        <div className="pt-2 flex gap-2">
                            <Button
                                variant="secondary"
                                size="xs"
                                className="flex-1"
                                disabled={!canGenerateOutput || downloadingType === 'SAFI'}
                                loading={downloadingType === 'SAFI'}
                                onClick={() => handleDownload('SAFI', 'Bordereau IRSA')}
                                iconLeft={<Download size={13} />}
                            >
                                IRSA
                            </Button>
                            <Button
                                variant="secondary"
                                size="xs"
                                className="flex-1"
                                disabled={!canGenerateOutput || downloadingType === 'ISI'}
                                loading={downloadingType === 'ISI'}
                                onClick={() => handleDownload('ISI', 'EDI-ISI')}
                                iconLeft={<Download size={13} />}
                            >
                                EDI-ISI
                            </Button>
                        </div>
                    </Card>

                    {/* 5. SOCIAL */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <Building2 size={14} className="text-[var(--color-purple-600)]" />
                                    Social
                                </span>
                                <Badge variant="info" size="xs">Consolidable</Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Données mensuelles prêtes pour consolidation trimestrielle DNS CNaPS & Santé.
                            </p>
                        </div>
                        <div className="pt-2">
                            <Button
                                variant="secondary"
                                size="xs"
                                className="w-full"
                                onClick={() => navigate('/paie/declarations')}
                                iconLeft={<Building2 size={13} />}
                            >
                                Voir Déclarations trimestrielles
                            </Button>
                        </div>
                    </Card>

                    {/* 6. COMPTABILITÉ */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <Calculator size={14} className="text-[var(--color-brand-600)]" />
                                    Comptabilité
                                </span>
                                <Badge variant="default" size="xs">
                                    Indisponible
                                </Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Export des écritures d'OD de paie (comptes 64, 42, 43, 44).
                            </p>
                        </div>
                        <div className="pt-2">
                            <Button
                                variant="secondary"
                                size="xs"
                                className="w-full"
                                disabled
                                title="L'export OD n'est pas encore disponible côté backend."
                                iconLeft={<Download size={13} />}
                            >
                                Export OD comptables
                            </Button>
                        </div>
                    </Card>

                    {/* 7. AUDIT */}
                    <Card className="p-4 border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                        <div>
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <Shield size={14} className="text-[var(--color-success-600)]" />
                                    Audit
                                </span>
                                <Badge variant="default" size="xs">Indisponible</Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Dossier de scellement de campagne et empreintes d'intégrité DAF.
                            </p>
                        </div>
                        <div className="pt-2">
                            <Button
                                variant="secondary"
                                size="xs"
                                className="w-full"
                                disabled
                                title="Aucun endpoint de certificat d'intégrité n'est disponible."
                                iconLeft={<Shield size={13} />}
                            >
                                Certificat d'intégrité
                            </Button>
                        </div>
                    </Card>
                </div>
            </div>

            {/* Modal de confirmation de clôture définitive */}
            <Modal
                isOpen={confirmCloseModal}
                onClose={() => setConfirmCloseModal(false)}
                title="Clôture définitive de la campagne"
                size="md"
            >
                <div className="space-y-4 text-xs">
                    <p className="text-[var(--color-fg)]">
                        La clôture est une action définitive qui verrouille l’ensemble de la période <strong>{codePeriode}</strong> en lecture seule stricte.
                    </p>
                    <div className="p-3 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 rounded text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]">
                        Aucun recalcul, ajustement ou ajout de variable ne sera plus possible.
                    </div>
                    <div className="flex justify-end gap-2 pt-2">
                        <Button variant="secondary" size="sm" onClick={() => setConfirmCloseModal(false)}>
                            Annuler
                        </Button>
                        <Button variant="danger" size="sm" onClick={handleCloturer} loading={actionLoading}>
                            Confirmer la clôture
                        </Button>
                    </div>
                </div>
            </Modal>
        </PayrollCampaignShell>
    );
}
