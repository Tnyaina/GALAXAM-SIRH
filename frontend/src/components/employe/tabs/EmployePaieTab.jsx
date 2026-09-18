import { useState, useEffect, useCallback } from 'react';
import {
    Banknote,
    FileText,
    Receipt,
    Plus,
    Eye,
    RefreshCw,
    TrendingUp,
    TrendingDown,
    Calculator
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Modal from '@/components/ui/overlays/Modal';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Card, CardTitle } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/carriereUtils';
import paieService from '@/api/paie/paieService';
import stcService from '@/api/paie/stcService';
import { getErrorMessage } from '@/api/apiClient';
import BulletinPaieDrawer from '@/components/paie/BulletinPaieDrawer';
import StcPreparationModal from '@/components/paie/stc/StcPreparationModal';
import StcRecuModal from '@/components/paie/stc/StcRecuModal';

export default function EmployePaieTab({ idEmploye, employe, canWrite = false }) {
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState(null);
    const [feedbackMsg, setFeedbackMsg] = useState(null);

    // 1. Synthèse financière backend-driven
    const [synthese, setSynthese] = useState(null);

    // 2. Historique des bulletins de paie
    const [bulletins, setBulletins] = useState([]);

    // 3. Mouvements et ajustements
    const [mouvements, setMouvements] = useState([]);
    const [typesMouvements, setTypesMouvements] = useState([]);

    // 4. Solde de Tout Compte (STC)
    const [stcList, setStcList] = useState([]);

    // Modales
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);

    const [isStcPrepOpen, setIsStcPrepOpen] = useState(false);
    const [selectedStcRecuId, setSelectedStcRecuId] = useState(null);

    // Modale Nouveau Mouvement / Variable
    const [isMouvementModalOpen, setIsMouvementModalOpen] = useState(false);
    const [submittingMvt, setSubmittingMvt] = useState(false);
    const [mvtForm, setMvtForm] = useState({
        typeMouvement: 'ACOMPTE_15',
        sens: 'RETENUE',
        montant: '',
        motif: '',
    });

    const chargerDonnees = useCallback(async () => {
        if (!idEmploye) return;
        setLoading(true);
        setErrorMsg(null);
        try {
            const [synthRes, bListRes, mvtRes, stcRes, typesRes] = await Promise.all([
                paieService.getSynthesePaieEmploye(idEmploye).catch(() => null),
                paieService.listerBulletinsEmploye(idEmploye).catch(() => []),
                paieService.listerAjustements(null, idEmploye).catch(() => []),
                stcService.listerStcEmploye(idEmploye).catch(() => []),
                paieService.listerTypesMouvements().catch(() => []),
            ]);

            setSynthese(synthRes);
            setBulletins(Array.isArray(bListRes) ? bListRes : []);
            setMouvements(Array.isArray(mvtRes) ? mvtRes : (mvtRes?.content || mvtRes?.items || []));
            setStcList(Array.isArray(stcRes) ? stcRes : []);
            setTypesMouvements(Array.isArray(typesRes) ? typesRes : (typesRes?.content || typesRes?.items || []));
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors du chargement du dossier paie de l\'employé.'));
        } finally {
            setLoading(false);
        }
    }, [idEmploye]);

    useEffect(() => {
        chargerDonnees();
    }, [chargerDonnees]);

    // Ouvrir le BulletinPaieDrawer
    const handleVoirBulletin = (idBulletin) => {
        setSelectedBulletinId(idBulletin);
        setIsDrawerOpen(true);
    };

    // Ouvrir la modale d'ajout de mouvement pré-remplie
    const handleOpenAddMouvement = (defaultType = 'ACOMPTE_15') => {
        const found = typesMouvements.find(t => t.code === defaultType);
        setMvtForm({
            typeMouvement: defaultType,
            sens: found?.sens || (defaultType === 'NOTE_DE_FRAIS' || defaultType === 'PRIM_EXC' ? 'GAIN' : 'RETENUE'),
            montant: '',
            motif: '',
        });
        setIsMouvementModalOpen(true);
    };

    const handleMvtTypeChange = (code) => {
        const found = typesMouvements.find(t => t.code === code);
        setMvtForm(prev => ({
            ...prev,
            typeMouvement: code,
            sens: found?.sens || (code === 'NOTE_DE_FRAIS' || code === 'PRIM_EXC' ? 'GAIN' : 'RETENUE'),
        }));
    };

    const handleSaveMouvement = async (e) => {
        e.preventDefault();
        if (!mvtForm.montant || Number(mvtForm.montant) <= 0) {
            setFeedbackMsg({ type: 'error', message: 'Veuillez saisir un montant valide.' });
            return;
        }

        setSubmittingMvt(true);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(idEmploye),
                idPeriodePaie: null, // rattaché à la période active par le moteur
                typeMouvement: mvtForm.typeMouvement,
                sens: mvtForm.sens,
                montant: Number(mvtForm.montant),
                motif: mvtForm.motif || 'Saisie depuis dossier employé',
                statut: 'APPROUVE',
            });
            setIsMouvementModalOpen(false);
            setFeedbackMsg({ type: 'success', message: 'Mouvement de paie enregistré avec succès.' });
            await chargerDonnees();
        } catch (err) {
            setFeedbackMsg({ type: 'error', message: getErrorMessage(err, 'Erreur lors de la création du mouvement.') });
        } finally {
            setSubmittingMvt(false);
        }
    };

    const isContratClos = synthese?.codeStatutContrat === 'CLOTURE' || synthese?.codeStatutContrat === 'ROMPU' || (synthese?.contratActif === false && synthese?.idContrat != null);

    if (loading) {
        return (
            <div className="space-y-4 p-4">
                <SkeletonTable rows={4} columns={4} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {feedbackMsg && (
                <Alert
                    type={feedbackMsg.type}
                    message={feedbackMsg.message}
                    onClose={() => setFeedbackMsg(null)}
                />
            )}

            {errorMsg && (
                <Alert
                    type="error"
                    title="Erreur Paie"
                    message={errorMsg}
                    onClose={() => setErrorMsg(null)}
                />
            )}

            {/* ========================================================================= */}
            {/* 1. SYNTHÈSE PAIE COMPACTE (100% BACKEND-DRIVEN)                           */}
            {/* ========================================================================= */}
            <Card padding bordered className="space-y-4">
                <div className="flex justify-between items-center">
                    <CardTitle size="sm" className="uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Banknote size={16} className="text-[var(--color-brand-600)]" />
                        Synthèse Rémunération &amp; Paramètres de Paie
                    </CardTitle>
                    <Button
                        type="button"
                        variant="outline"
                        size="xs"
                        onClick={chargerDonnees}
                        iconLeft={<RefreshCw size={12} />}
                    >
                        Actualiser
                    </Button>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 pt-1">
                    {/* Salaire contractuel */}
                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] space-y-0.5">
                        <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block">
                            Salaire Contractuel
                        </span>
                        <p className="text-sm font-bold font-mono text-[var(--color-fg)]">
                            {formatMoneyAr(synthese?.salaireBaseContractuel)}
                        </p>
                        <span className="text-[10px] text-[var(--color-fg-subtle)] block">
                            Base brute mensuelle
                        </span>
                    </div>

                    {/* Taux horaire légal */}
                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] space-y-0.5">
                        <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block">
                            Taux Horaire
                        </span>
                        <p className="text-sm font-bold font-mono text-[var(--color-fg)]">
                            {formatMoneyAr(synthese?.tauxHoraire)}
                        </p>
                        <span className="text-[10px] text-[var(--color-fg-subtle)] block">
                            / heure ({synthese?.diviseurHeures || '173.3333'} h)
                        </span>
                    </div>

                    {/* TCONGE applicable */}
                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] space-y-0.5">
                        <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block">
                            Taux Journalier TCONGE
                        </span>
                        <p className="text-sm font-bold font-mono text-[var(--color-brand-600)]">
                            {formatMoneyAr(synthese?.tauxJournalierConge)}
                        </p>
                        <span className="text-[10px] text-[var(--color-fg-subtle)] block">
                            Valorisation congé / j
                        </span>
                    </div>

                    {/* Dernière période payée */}
                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] space-y-0.5">
                        <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block">
                            Dernière Période
                        </span>
                        <p className="text-sm font-bold text-[var(--color-fg)] truncate">
                            {synthese?.dernierePeriodeCode || '—'}
                        </p>
                        <span className="text-[10px] text-[var(--color-fg-subtle)] block">
                            {synthese?.derniereDatePaiement ? fmtDate(synthese.derniereDatePaiement) : 'Aucun bulletin'}
                        </span>
                    </div>

                    {/* Dernier Brut / Net */}
                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] space-y-0.5">
                        <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block">
                            Dernier Net Payé
                        </span>
                        <p className="text-sm font-bold font-mono text-[var(--color-success-600)]">
                            {formatMoneyAr(synthese?.dernierNet)}
                        </p>
                        <span className="text-[10px] text-[var(--color-fg-subtle)] font-mono block">
                            Brut : {formatMoneyAr(synthese?.dernierBrut)}
                        </span>
                    </div>

                    {/* Engagements / Prêts / Avances */}
                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] space-y-0.5">
                        <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block">
                            Avances / Prêts
                        </span>
                        <p className="text-xs font-bold font-mono text-[var(--color-warning-600)] truncate">
                            Av : {formatMoneyAr(synthese?.totalAvancesEnCours)}
                        </p>
                        <p className="text-[10.5px] font-mono text-[var(--color-fg-muted)] truncate">
                            Prêt : {formatMoneyAr(synthese?.capitalRestantPrets)}
                        </p>
                    </div>
                </div>
            </Card>

            {/* ========================================================================= */}
            {/* 2. SECTION SOLDE DE TOUT COMPTE (STC)                                     */}
            {/* ========================================================================= */}
            <Card padding bordered className="space-y-4">
                <div className="flex justify-between items-center">
                    <CardTitle size="sm" className="uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Calculator size={16} className="text-[var(--color-brand-600)]" />
                        Solde de Tout Compte (STC) &amp; Fin de Contrat
                    </CardTitle>

                    {/* Si contrat clos et aucun STC validé, proposer de préparer le STC */}
                    {isContratClos && stcList.length === 0 && canWrite && (
                        <Button
                            type="button"
                            variant="primary"
                            size="xs"
                            iconLeft={<Calculator size={13} />}
                            onClick={() => setIsStcPrepOpen(true)}
                        >
                            Préparer le Solde de Tout Compte
                        </Button>
                    )}
                </div>

                {stcList.length > 0 ? (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>N° Reçu STC</Th>
                                <Th>Date Départ</Th>
                                <Th>Motif</Th>
                                <Th>Total Brut</Th>
                                <Th>Prêts Apurés</Th>
                                <Th>Net STC</Th>
                                <Th>Statut</Th>
                                <Th align="right">Action</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {stcList.map((s) => (
                                <Tr key={s.idStc}>
                                    <Td className="font-mono font-bold text-[var(--color-fg)]">
                                        {s.numeroRecu || `STC-#${s.idStc}`}
                                    </Td>
                                    <Td>{fmtDate(s.dateDepart)}</Td>
                                    <Td className="text-xs text-[var(--color-fg-muted)]">{s.motifRupture}</Td>
                                    <Td className="font-mono">{formatMoneyAr(s.totalBrutStc)}</Td>
                                    <Td className="font-mono text-[var(--color-danger-600)]">
                                        {formatMoneyAr(s.soldePretsDeduit)}
                                    </Td>
                                    <Td className="font-mono font-bold text-[var(--color-fg)]">
                                        {formatMoneyAr(s.netAPayerStc)}
                                    </Td>
                                    <Td>
                                        <Badge
                                            variant={s.statut === 'VALIDE' ? 'success' : 'warning'}
                                            size="xs"
                                        >
                                            {s.statut}
                                        </Badge>
                                    </Td>
                                    <Td align="right">
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<FileText size={13} />}
                                            onClick={() => setSelectedStcRecuId(s.idStc)}
                                        >
                                            Consulter la Quittance
                                        </Button>
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                ) : isContratClos ? (
                    <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex items-center justify-between text-xs">
                        <div className="space-y-0.5">
                            <p className="font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                Contrat clôturé ou rompu — STC non encore établi
                            </p>
                            <p className="text-[11px] text-[var(--color-fg-muted)]">
                                Le collaborateur n'a pas encore de reçu de Solde de Tout Compte émis dans le système.
                            </p>
                        </div>
                        {canWrite && (
                            <Button
                                type="button"
                                variant="primary"
                                size="xs"
                                iconLeft={<Calculator size={13} />}
                                onClick={() => setIsStcPrepOpen(true)}
                            >
                                Établir le STC
                            </Button>
                        )}
                    </div>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)] italic p-2">
                        Contrat actif ({synthese?.numeroContrat || 'En cours'}) — Aucun Solde de Tout Compte requis à ce jour.
                    </p>
                )}
            </Card>

            {/* ========================================================================= */}
            {/* 3. HISTORIQUE DES BULLETINS DE PAIE                                       */}
            {/* ========================================================================= */}
            <Card padding bordered className="space-y-4">
                <div className="flex justify-between items-center">
                    <CardTitle size="sm" className="uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <FileText size={16} className="text-[var(--color-brand-600)]" />
                        Historique des Bulletins de Paie ({bulletins.length})
                    </CardTitle>
                </div>

                {bulletins.length === 0 ? (
                    <EmptyState
                        title="Aucun bulletin de paie généré"
                        description="Aucune paie n'a encore été calculée ou clôturée pour ce salarié."
                    />
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Période</Th>
                                <Th>Type</Th>
                                <Th>Date Calcul</Th>
                                <Th>Salaire Base</Th>
                                <Th>Total Brut</Th>
                                <Th>Cotisations</Th>
                                <Th>IRSA / ISI</Th>
                                <Th>Net à Payer</Th>
                                <Th>Statut</Th>
                                <Th align="right">Action</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {bulletins.map((b) => (
                                <Tr key={b.idBulletin}>
                                    <Td>
                                        <div className="font-semibold text-[var(--color-fg)]">
                                            {b.codePeriode}
                                        </div>
                                        <span className="text-[10.5px] text-[var(--color-fg-muted)] block truncate max-w-[150px]">
                                            {b.libellePeriode}
                                        </span>
                                    </Td>
                                    <Td>
                                        <Badge variant={b.typeBulletin === 'STC' ? 'warning' : 'default'} size="xs">
                                            {b.typeBulletin || 'MENSUEL'}
                                        </Badge>
                                    </Td>
                                    <Td className="text-xs text-[var(--color-fg-muted)] font-mono">
                                        {fmtDate(b.dateCalcul)}
                                    </Td>
                                    <Td className="font-mono">{formatMoneyAr(b.salaireBase)}</Td>
                                    <Td className="font-mono font-semibold">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                    <Td className="font-mono text-[var(--color-danger-600)]">
                                        -{formatMoneyAr(b.totalCotisationsSalariales)}
                                    </Td>
                                    <Td className="font-mono text-[var(--color-danger-600)]">
                                        -{formatMoneyAr(b.irsaNet || b.retenueIsi5)}
                                    </Td>
                                    <Td className="font-mono font-bold text-[var(--color-fg)]">
                                        {formatMoneyAr(b.netAPayer)}
                                    </Td>
                                    <Td>
                                        <Badge
                                            variant={
                                                b.statut === 'VALIDE' || b.statut === 'CLOTURE'
                                                    ? 'success'
                                                    : b.statut === 'CALCULE'
                                                    ? 'info'
                                                    : 'default'
                                            }
                                            size="xs"
                                        >
                                            {b.statut}
                                        </Badge>
                                    </Td>
                                    <Td align="right">
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<Eye size={13} />}
                                            onClick={() => handleVoirBulletin(b.idBulletin)}
                                        >
                                            Voir le bulletin
                                        </Button>
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                )}
            </Card>

            {/* ========================================================================= */}
            {/* 4. MOUVEMENTS DE PAIE (ACOMPTES, AVANCES, FRAIS, PRIMES, RETENUES)       */}
            {/* ========================================================================= */}
            <Card padding bordered className="space-y-4">
                <div className="flex flex-wrap justify-between items-center gap-2">
                    <CardTitle size="sm" className="uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Receipt size={16} className="text-[var(--color-brand-600)]" />
                        Mouvements &amp; Ajustements de Paie ({mouvements.length})
                    </CardTitle>

                    {canWrite && (
                        <div className="flex flex-wrap gap-1.5">
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                iconLeft={<Plus size={12} />}
                                onClick={() => handleOpenAddMouvement('ACOMPTE_15')}
                            >
                                Acompte du 15
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                iconLeft={<Plus size={12} />}
                                onClick={() => handleOpenAddMouvement('AVANCE_SPECIALE')}
                            >
                                Avance Spéciale
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                iconLeft={<Plus size={12} />}
                                onClick={() => handleOpenAddMouvement('NOTE_DE_FRAIS')}
                            >
                                Note de Frais (Gain)
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                iconLeft={<Plus size={12} />}
                                onClick={() => handleOpenAddMouvement('PRIM_EXC')}
                            >
                                Prime
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                iconLeft={<Plus size={12} />}
                                onClick={() => handleOpenAddMouvement('RET_DIV')}
                            >
                                Retenue Divers
                            </Button>
                        </div>
                    )}
                </div>

                {mouvements.length === 0 ? (
                    <EmptyState
                        title="Aucun mouvement de paie"
                        description="Aucun acompte, avance, prime ou note de frais n'a été enregistré pour ce salarié."
                    />
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Date</Th>
                                <Th>Type de Mouvement</Th>
                                <Th>Nature (Sens)</Th>
                                <Th>Montant</Th>
                                <Th>Motif / Justification</Th>
                                <Th>Statut</Th>
                                <Th>Imputation Paie</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {mouvements.map((m) => {
                                const isGain = m.sens === 'GAIN';
                                return (
                                    <Tr key={m.idAvance || m.idAjustement}>
                                        <Td className="text-xs font-mono text-[var(--color-fg-muted)]">
                                            {fmtDate(m.dateDemande || m.datePaiement)}
                                        </Td>
                                        <Td>
                                            <div className="font-semibold text-[var(--color-fg)]">
                                                {m.typeLibelle || m.typeMouvement}
                                            </div>
                                            <span className="text-[10px] text-[var(--color-fg-subtle)] font-mono block">
                                                {m.typeMouvement}
                                            </span>
                                        </Td>
                                        <Td>
                                            <Badge
                                                variant={isGain ? 'success' : 'danger'}
                                                size="xs"
                                                className="font-bold flex items-center gap-1 w-fit"
                                            >
                                                {isGain ? <TrendingUp size={11} /> : <TrendingDown size={11} />}
                                                {isGain ? 'GAIN / REMBOURSEMENT' : 'RETENUE'}
                                            </Badge>
                                        </Td>
                                        <Td className={`font-mono font-bold ${isGain ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                            {isGain ? '+' : '-'}{formatMoneyAr(m.montant)}
                                        </Td>
                                        <Td className="text-xs text-[var(--color-fg-muted)] max-w-xs truncate">
                                            {m.motif || '—'}
                                        </Td>
                                        <Td>
                                            <Badge
                                                variant={
                                                    m.statut === 'APPROUVE' || m.statut === 'VALIDE' || m.statut === 'REGLE'
                                                        ? 'success'
                                                        : m.statut === 'EN_ATTENTE'
                                                        ? 'warning'
                                                        : 'default'
                                                }
                                                size="xs"
                                            >
                                                {m.statut}
                                            </Badge>
                                        </Td>
                                        <Td className="text-xs text-[var(--color-fg-muted)]">
                                            {m.idBulletin ? (
                                                <span className="text-[var(--color-success-600)] font-semibold font-mono">
                                                    Imputé (Bulletin #{m.idBulletin})
                                                </span>
                                            ) : (
                                                <span className="text-[var(--color-warning-600)] italic">
                                                    En attente d'imputation
                                                </span>
                                            )}
                                        </Td>
                                    </Tr>
                                );
                            })}
                        </Tbody>
                    </Table>
                )}
            </Card>

            {/* ========================================================================= */}
            {/* MODALES DÉLÉGUÉES (RÉUTILISATION STRICTE DES COMPOSANTS EXISTANTS)       */}
            {/* ========================================================================= */}

            {/* 1. BulletinPaieDrawer existant réutilisé */}
            <BulletinPaieDrawer
                isOpen={isDrawerOpen}
                onClose={() => {
                    setIsDrawerOpen(false);
                    setSelectedBulletinId(null);
                }}
                idBulletin={selectedBulletinId}
                onRecalculateSuccess={chargerDonnees}
            />

            {/* 2. Modale Solde de Tout Compte (STC) */}
            {isStcPrepOpen && (
                <StcPreparationModal
                    isOpen={isStcPrepOpen}
                    onClose={() => setIsStcPrepOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={synthese?.nomComplet || employe?.nom || ''}
                    dateDepartInitiale={synthese?.dateFinContrat || null}
                    motifInitial="DEMISSION"
                    onStcValide={() => {
                        chargerDonnees();
                    }}
                />
            )}

            {/* 3. Modale Quittance Libératoire STC imprimable */}
            {selectedStcRecuId && (
                <StcRecuModal
                    isOpen={Boolean(selectedStcRecuId)}
                    onClose={() => setSelectedStcRecuId(null)}
                    idStc={selectedStcRecuId}
                />
            )}

            {/* 4. Modale Saisie Nouveau Mouvement */}
            <Modal
                isOpen={isMouvementModalOpen}
                onClose={() => setIsMouvementModalOpen(false)}
                title="Ajouter un Mouvement / Variable de Paie"
                icon={<Plus size={18} className="text-[var(--color-brand-600)]" />}
                size="md"
                footer={
                    <div className="flex justify-end gap-2 w-full">
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => setIsMouvementModalOpen(false)}
                            disabled={submittingMvt}
                        >
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            variant="primary"
                            size="sm"
                            onClick={handleSaveMouvement}
                            loading={submittingMvt}
                        >
                            Enregistrer le mouvement
                        </Button>
                    </div>
                }
            >
                <form onSubmit={handleSaveMouvement} className="space-y-4">
                    <div>
                        <span className="text-xs text-[var(--color-fg-muted)] block">Salarié bénéficiaire</span>
                        <strong className="text-sm text-[var(--color-fg)]">
                            {synthese?.nomComplet || `${employe?.nom || ''} ${employe?.prenom || ''}`} ({synthese?.matricule || employe?.matricule})
                        </strong>
                    </div>

                    <Select
                        label="Type de Mouvement *"
                        required
                        value={mvtForm.typeMouvement}
                        onChange={(e) => handleMvtTypeChange(e.target.value)}
                        options={
                            typesMouvements.length > 0
                                ? typesMouvements.map(t => ({
                                      value: t.code,
                                      label: `${t.libelle} (${t.sens === 'GAIN' ? 'GAIN' : 'RETENUE'})`,
                                  }))
                                : [
                                      { value: 'ACOMPTE_15', label: 'Acompte du 15 (RETENUE)' },
                                      { value: 'AVANCE_SPECIALE', label: 'Avance Spéciale (RETENUE)' },
                                      { value: 'NOTE_DE_FRAIS', label: 'Note de Frais / Remboursement (GAIN)' },
                                      { value: 'PRIM_EXC', label: 'Prime Exceptionnelle (GAIN)' },
                                      { value: 'CALL_PERSO', label: 'Appels Personnels (RETENUE)' },
                                      { value: 'RET_DIV', label: 'Retenue Diverse (RETENUE)' },
                                  ]
                        }
                    />

                    <div className="p-2.5 bg-[var(--color-bg-subtle)]/60 border border-[var(--color-border-subtle)] flex items-center justify-between text-xs">
                        <span className="text-[var(--color-fg-muted)]">Nature de l'impact :</span>
                        <Badge
                            variant={mvtForm.sens === 'GAIN' ? 'success' : 'danger'}
                            size="xs"
                            className="font-bold"
                        >
                            {mvtForm.sens === 'GAIN' ? '+ GAIN / REMBOURSEMENT' : '- RETENUE SUR SALAIRE'}
                        </Badge>
                    </div>

                    <Input
                        type="number"
                        label="Montant (Ariary) *"
                        required
                        min="1"
                        placeholder="Ex: 50000"
                        value={mvtForm.montant}
                        onChange={(e) => setMvtForm(prev => ({ ...prev, montant: e.target.value }))}
                    />

                    <Input
                        type="text"
                        label="Motif / Justificatif"
                        placeholder="Ex: Frais kilométriques mission Tamatave, Acompte quinzaine..."
                        value={mvtForm.motif}
                        onChange={(e) => setMvtForm(prev => ({ ...prev, motif: e.target.value }))}
                    />
                </form>
            </Modal>
        </div>
    );
}
