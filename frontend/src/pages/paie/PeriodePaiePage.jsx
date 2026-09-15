import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    CalendarRange,
    Plus,
    RefreshCw,
    CheckCircle,
    Play,
    ArrowRight,
    Clock,
    CheckCheck,
    Archive,
    ShieldCheck,
    Sparkles
} from 'lucide-react';
import { usePeriodePaie } from '@/hooks/pointage/usePeriodePaie';
import periodePaieService from '@/services/periodePaieService';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Input from '@/components/ui/forms/Input';
import DatePicker from '@/components/ui/forms/DatePicker';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Card, { CardBody } from '@/components/ui/data-display/Card';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import HasPermission from '@/components/auth/HasPermission';
import SEO from '@/components/system/SEO';

const STATUT_CONFIG = {
    'BROUILLON': {
        variant: 'secondary',
        label: 'Brouillon',
        desc: 'Période configurée mais non active'
    },
    'EN_COURS': {
        variant: 'info',
        label: 'En cours (Saisie active)',
        desc: 'Pointages ouverts aux agents'
    },
    'A_VALIDER': {
        variant: 'warning',
        label: 'À valider (Fin de mois)',
        desc: 'Saisie terminée, en attente de validation'
    },
    'VALIDEE': {
        variant: 'success',
        label: 'Validée (Verrouillée)',
        desc: 'Pointages verrouillés et figés'
    },
    'CLOTUREE': {
        variant: 'danger',
        label: 'Clôturée (Paie générée)',
        desc: 'Période archivée définitivement'
    }
};

const WORKFLOW_ACTIONS = {
    OPEN: { targetState: 'EN_COURS', label: 'Ouvrir la saisie', title: 'Ouvrir la période à la saisie', variant: 'outline', Icon: Play },
    SUBMIT: { targetState: 'A_VALIDER', label: 'Transmettre pour validation', title: 'Transmettre la période pour validation', variant: 'outline', Icon: ArrowRight },
    APPROVE: { targetState: 'VALIDEE', label: 'Valider', title: 'Validation de la période & gel des données Paie', variant: 'primary', Icon: CheckCircle },
    CLOSE: { targetState: 'CLOTUREE', label: 'Clôturer', title: 'Clôture définitive de la période', variant: 'danger', Icon: Archive }
};

const MOIS_FR = [
    'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
    'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'
];

const computeNextPeriodeSuggestion = (periodesList) => {
    // Cycle de paie standard : du 26 du mois M-1 au 25 du mois M
    if (!periodesList || periodesList.length === 0) {
        const now = new Date();
        const y = now.getFullYear();
        const m = now.getMonth(); // 0 à 11
        const d = now.getDate();

        let targetY = y;
        let targetM = d >= 26 ? m + 1 : m;
        if (targetM > 11) {
            targetM = 0;
            targetY += 1;
        }

        const prevM = targetM === 0 ? 11 : targetM - 1;
        const prevY = targetM === 0 ? targetY - 1 : targetY;

        const startPad = String(prevM + 1).padStart(2, '0');
        const endPad = String(targetM + 1).padStart(2, '0');

        return {
            code: `${targetY}-${endPad}`,
            libelle: `${MOIS_FR[targetM]} ${targetY}`,
            dateDebut: `${prevY}-${startPad}-26`,
            dateFin: `${targetY}-${endPad}-25`,
            baseLibelle: null
        };
    }

    // Trouver la période la plus récente par dateFin ou dateDebut
    const sorted = [...periodesList].sort((a, b) => {
        const dFinA = a.dateFin || a.date_fin || '';
        const dFinB = b.dateFin || b.date_fin || '';
        return dFinB.localeCompare(dFinA);
    });

    const latest = sorted[0];
    let nextYear, nextMonth;

    if (latest.dateFin) {
        const parts = latest.dateFin.split('-');
        if (parts.length === 3) {
            let y = parseInt(parts[0], 10);
            let m = parseInt(parts[1], 10) - 1; // 0 à 11
            m += 1;
            if (m > 11) {
                m = 0;
                y += 1;
            }
            nextYear = y;
            nextMonth = m;
        }
    }

    if (!nextYear) {
        const now = new Date();
        nextYear = now.getFullYear();
        nextMonth = now.getMonth();
    }

    // Cycle 26 au 25 pour le mois nextMonth
    const prevMonth = nextMonth === 0 ? 11 : nextMonth - 1;
    const prevYear = nextMonth === 0 ? nextYear - 1 : nextYear;

    const startPad = String(prevMonth + 1).padStart(2, '0');
    const endPad = String(nextMonth + 1).padStart(2, '0');

    return {
        code: `${nextYear}-${endPad}`,
        libelle: `${MOIS_FR[nextMonth]} ${nextYear}`,
        dateDebut: `${prevYear}-${startPad}-26`,
        dateFin: `${nextYear}-${endPad}-25`,
        baseLibelle: latest.libelle || latest.code
    };
};

const computePeriodeForDate = (dateStr) => {
    if (!dateStr) return null;
    try {
        const parts = dateStr.split('-');
        if (parts.length === 3) {
            const y = parseInt(parts[0], 10);
            const m = parseInt(parts[1], 10) - 1; // 0 à 11
            const d = parseInt(parts[2], 10);

            // Règle du cycle 26 -> 25 :
            // Si le jour >= 26, la date appartient à la paie du mois suivant (M+1)
            // Si le jour <= 25, la date appartient à la paie du mois en cours (M)
            let targetYear = y;
            let targetMonth = d >= 26 ? m + 1 : m;
            if (targetMonth > 11) {
                targetMonth = 0;
                targetYear += 1;
            }

            const startMonth = targetMonth === 0 ? 11 : targetMonth - 1;
            const startYear = targetMonth === 0 ? targetYear - 1 : targetYear;

            const startMonthPad = String(startMonth + 1).padStart(2, '0');
            const targetMonthPad = String(targetMonth + 1).padStart(2, '0');

            return {
                code: `${targetYear}-${targetMonthPad}`,
                libelle: `${MOIS_FR[targetMonth]} ${targetYear}`,
                dateDebut: `${startYear}-${startMonthPad}-26`,
                dateFin: `${targetYear}-${targetMonthPad}-25`,
                baseLibelle: null
            };
        }
    } catch {
        // Ignorer les erreurs de parsing de date
    }
    return null;
};

export default function PeriodePaiePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const {
        periodes,
        loading,
        error,
        setError,
        loadPeriodes,
        createPeriode,
        executeCommand
    } = usePeriodePaie();

    const toast = useToast();

    // Calcul automatique de la prochaine période suggérée
    const nextSuggestion = useMemo(() => computeNextPeriodeSuggestion(periodes), [periodes]);

    // Modale de Création
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [newPeriode, setNewPeriode] = useState({
        code: '',
        libelle: '',
        dateDebut: '',
        dateFin: ''
    });

    // Détection de l'action "create" dans l'URL pour ouvrir directement la modale
    useEffect(() => {
        const action = searchParams.get('action');
        if (action === 'create') {
            const targetDate = searchParams.get('targetDate');
            const customForDate = targetDate ? computePeriodeForDate(targetDate) : null;
            const initial = customForDate || nextSuggestion;
            setNewPeriode({
                code: initial.code,
                libelle: initial.libelle,
                dateDebut: initial.dateDebut,
                dateFin: initial.dateFin
            });
            setIsCreateModalOpen(true);

            // Nettoyer les paramètres d'URL pour ne pas ré-ouvrir au refresh/close
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.delete('action');
                next.delete('targetDate');
                return next;
            }, { replace: true });
        }
    }, [searchParams, nextSuggestion, setSearchParams]);

    const handleOpenCreateWithSuggestion = () => {
        setNewPeriode({
            code: nextSuggestion.code,
            libelle: nextSuggestion.libelle,
            dateDebut: nextSuggestion.dateDebut,
            dateFin: nextSuggestion.dateFin
        });
        setIsCreateModalOpen(true);
    };

    const handleApplyCycle2625 = () => {
        let y, m;
        if (newPeriode.code && newPeriode.code.includes('-')) {
            const parts = newPeriode.code.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else if (newPeriode.dateFin) {
            const parts = newPeriode.dateFin.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else {
            const now = new Date();
            y = now.getFullYear();
            m = now.getMonth();
        }
        const prevM = m === 0 ? 11 : m - 1;
        const prevY = m === 0 ? y - 1 : y;
        const startPad = String(prevM + 1).padStart(2, '0');
        const endPad = String(m + 1).padStart(2, '0');
        setNewPeriode(prev => ({
            ...prev,
            dateDebut: `${prevY}-${startPad}-26`,
            dateFin: `${y}-${endPad}-25`
        }));
    };

    const handleApplyCycleCivil = () => {
        let y, m;
        if (newPeriode.code && newPeriode.code.includes('-')) {
            const parts = newPeriode.code.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else if (newPeriode.dateFin) {
            const parts = newPeriode.dateFin.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else {
            const now = new Date();
            y = now.getFullYear();
            m = now.getMonth();
        }
        const mPad = String(m + 1).padStart(2, '0');
        const lastDay = new Date(y, m + 1, 0).getDate();
        setNewPeriode(prev => ({
            ...prev,
            dateDebut: `${y}-${mPad}-01`,
            dateFin: `${y}-${mPad}-${String(lastDay).padStart(2, '0')}`
        }));
    };

    const handleOpenCreateBlank = () => {
        setNewPeriode({
            code: '',
            libelle: '',
            dateDebut: '',
            dateFin: ''
        });
        setIsCreateModalOpen(true);
    };

    // Simulation server-driven des jours ouvrables (Backend-Driven)
    const [simulation, setSimulation] = useState(null);
    const [simulating, setSimulating] = useState(false);

    useEffect(() => {
        if (newPeriode.dateDebut && newPeriode.dateFin) {
            if (new Date(newPeriode.dateDebut) <= new Date(newPeriode.dateFin)) {
                let active = true;
                setSimulating(true);
                periodePaieService.simulerPeriode(newPeriode.dateDebut, newPeriode.dateFin)
                    .then(data => {
                        if (active) setSimulation(data);
                    })
                    .catch(() => {
                        if (active) setSimulation(null);
                    })
                    .finally(() => {
                        if (active) setSimulating(false);
                    });
                return () => { active = false; };
            } else {
                setSimulation(null);
            }
        } else {
            setSimulation(null);
        }
    }, [newPeriode.dateDebut, newPeriode.dateFin]);

    // Modale de Confirmation de Statut (pour Rouvrir, Valider, Clôturer)
    const [confirmAction, setConfirmAction] = useState({
        isOpen: false,
        periode: null,
        command: null,
        title: '',
        message: '',
        variant: 'primary',
        confirmLabel: 'Confirmer'
    });

    // Statistiques des périodes
    const stats = useMemo(() => {
        const total = periodes.length;
        const enCours = periodes.filter(p => p.statutCode === 'EN_COURS').length;
        const aValider = periodes.filter(p => p.statutCode === 'A_VALIDER').length;
        const verrouillees = periodes.filter(p => p.statutCode === 'VALIDEE' || p.statutCode === 'CLOTUREE').length;
        return { total, enCours, aValider, verrouillees };
    }, [periodes]);

    const handleCreate = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        const success = await createPeriode(newPeriode);
        setSubmitting(false);
        if (success) {
            toast({
                title: 'Période créée',
                message: `La période "${newPeriode.libelle}" (${simulation?.nbJoursOuvrables ? simulation.nbJoursOuvrables + 'j ouvrables' : ''}) a été créée avec succès.`,
                type: 'success'
            });
            setIsCreateModalOpen(false);
            setNewPeriode({ code: '', libelle: '', dateDebut: '', dateFin: '' });
            setSimulation(null);
        } else {
            toast({
                title: 'Erreur',
                message: error || 'Impossible de créer la période de paie.',
                type: 'danger'
            });
        }
    };

    const triggerWorkflowAction = (periode, command) => {
        const config = WORKFLOW_ACTIONS[command];
        setConfirmAction({
            isOpen: true,
            periode,
            command,
            title: config.title,
            message: `Confirmez-vous l'action « ${config.label} » pour la période "${periode.libelle}" ?`,
            variant: config.variant,
            confirmLabel: config.label
        });
    };

    const handleExecuteWorkflow = async (periode, command) => {
        const config = WORKFLOW_ACTIONS[command];
        setSubmitting(true);
        const success = await executeCommand(periode, command);
        setSubmitting(false);
        setConfirmAction(prev => ({ ...prev, isOpen: false }));
        if (success) {
            toast({
                title: 'Statut mis à jour',
                message: `La période est désormais au statut : ${STATUT_CONFIG[config.targetState]?.label || config.targetState}`,
                type: 'success'
            });
        } else {
            toast({
                title: 'Erreur',
                message: error || 'Impossible de mettre à jour le statut.',
                type: 'danger'
            });
        }
    };

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Périodes de Paie & Clôture" description="Gestion des périodes mensuelles de pointage et cycle de paie." />

            {/* Barre d'action de création de période */}
            <div className="flex items-center justify-between bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs">
                <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Calendrier des Cycles de Paie
                    </h3>
                    <p className="text-[11.5px] text-[var(--color-fg-muted)] mt-0.5">
                        Création, activation, validation et clôture immuable des périodes de pointage et de paie.
                    </p>
                </div>

                <div className="relative flex items-center flex-wrap gap-2 shrink-0">
                    <Button
                        onClick={loadPeriodes}
                        variant="outline"
                        size="sm"
                        icon={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                    >
                        Actualiser
                    </Button>
                    <HasPermission authority={['PAIE_WRITE', 'PAIE_VALIDATE', 'POINTAGE_VALIDATE']}>
                        <Button
                            onClick={handleOpenCreateWithSuggestion}
                            variant="primary"
                            size="sm"
                            icon={<Sparkles size={14} className="text-amber-300" />}
                            title={`Créer automatiquement la suite logique : ${nextSuggestion.libelle} (${nextSuggestion.dateDebut} au ${nextSuggestion.dateFin})`}
                        >
                            Créer {nextSuggestion.libelle} (Suggéré)
                        </Button>
                        <Button
                            onClick={handleOpenCreateBlank}
                            variant="outline"
                            size="sm"
                            icon={<Plus size={14} />}
                            title="Créer une période avec des dates personnalisées"
                        >
                            Personnalisée...
                        </Button>
                    </HasPermission>
                </div>
            </div>

            {/* Cartes KPI / Statistiques */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
                <Card bordered shadow="sm">
                    <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                        <div className="w-9 h-9 bg-brand-50 dark:bg-brand-900/30 text-brand-500 flex items-center justify-center shrink-0">
                            <CalendarRange size={18} />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400">Total Périodes</p>
                            <p className="text-xl font-bold font-data text-ink-800 dark:text-white">{stats.total}</p>
                        </div>
                    </CardBody>
                </Card>

                <Card bordered shadow="sm">
                    <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                        <div className="w-9 h-9 bg-[var(--color-info-500)]/10 text-[var(--color-info-500)] flex items-center justify-center shrink-0">
                            <Clock size={18} />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400">En cours (Active)</p>
                            <p className="text-xl font-bold font-data text-[var(--color-info-500)]">{stats.enCours}</p>
                        </div>
                    </CardBody>
                </Card>

                <Card bordered shadow="sm">
                    <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                        <div className="w-9 h-9 bg-warning-500/10 text-warning-500 flex items-center justify-center shrink-0">
                            <CheckCheck size={18} />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400">À Valider (Fin de mois)</p>
                            <p className="text-xl font-bold font-data text-warning-500">{stats.aValider}</p>
                        </div>
                    </CardBody>
                </Card>

                <Card bordered shadow="sm">
                    <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                        <div className="w-9 h-9 bg-success-500/10 text-success-500 flex items-center justify-center shrink-0">
                            <ShieldCheck size={18} />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400">Verrouillées / Clôturées</p>
                            <p className="text-xl font-bold font-data text-success-500">{stats.verrouillees}</p>
                        </div>
                    </CardBody>
                </Card>
            </div>

            {error && <Alert type="danger" message={error} onClose={() => setError(null)} />}

            {/* Table des Périodes */}
            <div className="bg-[var(--color-surface)] dark:bg-[var(--color-surface)] border border-ink-150 dark:border-[var(--color-border)] shadow-sm overflow-hidden">
                <div className="p-4 border-b border-ink-150 dark:border-[var(--color-border)] flex items-center justify-between">
                    <div>
                        <h2 className="text-sm font-bold uppercase tracking-wider text-ink-800 dark:text-white">
                            Historique et Statut des Périodes de Paie
                        </h2>
                        <p className="text-xs text-ink-400 dark:text-[var(--color-fg-muted)] mt-0.5">
                            Le verrouillage bloque instantanément toute modification dans la Saisie Quotidienne.
                        </p>
                    </div>
                </div>

                {loading && periodes.length === 0 ? (
                    <SkeletonTable rows={5} cols={6} />
                ) : (
                    <div className={`transition-opacity duration-200 ${loading ? 'opacity-50 pointer-events-none' : 'opacity-100'}`}>
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>Code Période</Th>
                                    <Th>Libellé</Th>
                                    <Th>Date Début</Th>
                                    <Th>Date Fin</Th>
                                    <Th className="text-center">Jours Ouvrables</Th>
                                    <Th>Statut</Th>
                                    <Th className="text-right">Actions sur le Cycle</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {periodes.map(p => {
                                    const isLocked = p.statutCode === 'VALIDEE' || p.statutCode === 'CLOTUREE';
                                    return (
                                        <Tr key={p.idPeriodePaie} className={isLocked ? 'bg-ink-50/30 dark:bg-[var(--color-bg-subtle)]' : ''}>
                                            <Td className="font-mono font-bold text-brand-600 dark:text-brand-400">
                                                {p.code}
                                            </Td>
                                            <Td className="font-semibold text-ink-900 dark:text-white">
                                                {p.libelle}
                                            </Td>
                                            <Td className="text-xs font-mono">{p.dateDebut}</Td>
                                            <Td className="text-xs font-mono">{p.dateFin}</Td>
                                            <Td className="text-center">
                                                <div className="inline-flex items-center gap-1.5">
                                                    <span className="px-2 py-0.5 font-bold font-data text-xs bg-brand-500/10 text-brand-600 dark:text-brand-400 border border-brand-500/20">
                                                        {p.nbJoursOuvrables ?? '-'} j
                                                    </span>
                                                    {p.nbJoursFeries > 0 && (
                                                        <span className="text-[10px] font-medium text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]" title={`${p.nbJoursFeries} jour(s) férié(s) chômé(s)`}>
                                                            ({p.nbJoursFeries} f.)
                                                        </span>
                                                    )}
                                                </div>
                                            </Td>
                                            <Td>
                                                <Badge variant={STATUT_CONFIG[p.statutCode]?.variant || 'secondary'}>
                                                    {STATUT_CONFIG[p.statutCode]?.label || p.statutLibelle}
                                                </Badge>
                                            </Td>
                                            <Td className="text-right space-x-2">
                                                {Object.entries(WORKFLOW_ACTIONS).map(([command, config]) => {
                                                    if (p.availableActions?.[command]?.allowed !== true) return null;
                                                    const ActionIcon = config.Icon;
                                                    return (
                                                    <Button
                                                        key={command}
                                                        size="xs"
                                                        variant={config.variant}
                                                        icon={<ActionIcon size={13} />}
                                                        onClick={() => triggerWorkflowAction(p, command)}
                                                    >
                                                        {config.label}
                                                    </Button>
                                                    );
                                                })}
                                            </Td>
                                        </Tr>
                                    );
                                })}
                                {periodes.length === 0 && !loading && (
                                    <Tr>
                                        <Td colSpan={7} className="text-center py-12 text-ink-400 dark:text-[var(--color-fg-muted)]">
                                            <CalendarRange size={36} className="mx-auto mb-2 opacity-40" />
                                            Aucune période de paie configurée pour le moment.
                                        </Td>
                                    </Tr>
                                )}
                            </Tbody>
                        </Table>
                    </div>
                )}
            </div>

            {/* Modale de Création de Période */}
            <Modal
                isOpen={isCreateModalOpen}
                onClose={() => setIsCreateModalOpen(false)}
                title="Créer une Nouvelle Période de Paie"
            >
                <form onSubmit={handleCreate} className="space-y-4">
                    {/* Bannière de suggestion automatique */}
                    {nextSuggestion && (
                        <div className="p-3 bg-amber-500/10 dark:bg-amber-950/25 border border-amber-500/30 flex items-center justify-between gap-3 text-xs">
                            <div className="flex items-center gap-2">
                                <Sparkles size={16} className="text-amber-600 dark:text-amber-400 shrink-0" />
                                <span className="text-amber-950 dark:text-amber-200">
                                    Suite suggérée : <strong>{nextSuggestion.libelle}</strong> ({nextSuggestion.dateDebut} au {nextSuggestion.dateFin})
                                </span>
                            </div>
                            <button
                                type="button"
                                onClick={() => setNewPeriode({
                                    code: nextSuggestion.code,
                                    libelle: nextSuggestion.libelle,
                                    dateDebut: nextSuggestion.dateDebut,
                                    dateFin: nextSuggestion.dateFin
                                })}
                                className="px-2 py-1 bg-amber-500/20 hover:bg-amber-500/30 text-amber-900 dark:text-amber-200 font-bold text-[11px] border border-amber-500/40 cursor-pointer whitespace-nowrap transition-colors"
                            >
                                Appliquer la suggestion
                            </button>
                        </div>
                    )}

                    <div className="bg-brand-50/50 dark:bg-brand-900/20 p-3 border border-brand-200 dark:border-brand-800/40 text-xs text-brand-800 dark:text-brand-300">
                        La période sera initialement créée au statut <strong>Brouillon</strong>. Vous pourrez ensuite l'ouvrir à la saisie.
                    </div>
                    <Input
                        label="Code Période (ex: 2026-09)"
                        placeholder="AAAA-MM"
                        value={newPeriode.code}
                        onChange={e => setNewPeriode({ ...newPeriode, code: e.target.value })}
                        required
                    />
                    <Input
                        label="Libellé complet (ex: Septembre 2026)"
                        placeholder="Nom de la période"
                        value={newPeriode.libelle}
                        onChange={e => setNewPeriode({ ...newPeriode, libelle: e.target.value })}
                        required
                    />
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <span className="text-xs font-semibold text-[var(--color-fg)]">Plage de dates</span>
                            <div className="flex items-center gap-1.5">
                                <button
                                    type="button"
                                    onClick={handleApplyCycle2625}
                                    className="px-2 py-0.5 text-[10.5px] font-medium bg-brand-500/10 text-brand-600 dark:text-brand-400 border border-brand-500/30 hover:bg-brand-500/20 transition-colors cursor-pointer"
                                    title="Appliquer le cycle standard de paie du 26 au 25"
                                >
                                    Cycle 26 au 25 (Standard)
                                </button>
                                <button
                                    type="button"
                                    onClick={handleApplyCycleCivil}
                                    className="px-2 py-0.5 text-[10.5px] font-medium bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)] hover:text-[var(--color-fg)] transition-colors cursor-pointer"
                                    title="Appliquer le mois civil du 1er au dernier jour"
                                >
                                    Mois civil (1 au 31)
                                </button>
                            </div>
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <DatePicker
                                label="Date de début"
                                value={newPeriode.dateDebut}
                                onChange={val => setNewPeriode({ ...newPeriode, dateDebut: val })}
                                required
                            />
                            <DatePicker
                                label="Date de fin"
                                value={newPeriode.dateFin}
                                onChange={val => setNewPeriode({ ...newPeriode, dateFin: val })}
                                required
                            />
                        </div>
                    </div>

                    {/* Bloc Synthèse Dynamique Calculée Côté Serveur (Backend-Driven) */}
                    {simulating && (
                        <div className="p-3 bg-ink-50 dark:bg-[var(--color-surface)]/40 border border-ink-200 dark:border-[var(--color-border-subtle)] text-xs text-ink-500 animate-pulse flex items-center gap-2">
                            <span className="w-2 h-2 rounded-full bg-brand-500 animate-ping"></span>
                            Calcul officiel des jours ouvrables par le serveur...
                        </div>
                    )}

                    {simulation && !simulating && (
                        <div className="bg-brand-50/60 dark:bg-brand-950/30 border border-brand-200 dark:border-brand-800/50 p-3.5 flex items-center justify-between gap-4 text-xs">
                            <div>
                                <span className="text-ink-600 dark:text-[var(--color-fg-muted)] block text-[11px] uppercase tracking-wider font-semibold">
                                    Jours Ouvrables Réels
                                </span>
                                <span className="font-bold text-lg text-brand-600 dark:text-brand-400 font-data">
                                    {simulation.nbJoursOuvrables} jours
                                </span>
                                <span className="text-[10px] text-ink-500 block">
                                    (Lun - Ven hors fériés chômés)
                                </span>
                            </div>
                            <div className="text-right text-[11px] text-ink-600 dark:text-[var(--color-fg-muted)] space-y-0.5 border-l border-brand-200 dark:border-brand-800/40 pl-4">
                                <div>Total calendaire : <strong className="font-data text-ink-900 dark:text-ink-100">{simulation.nbJoursCalendaires} j</strong></div>
                                <div>Fériés chômés : <strong className="font-data text-ink-900 dark:text-ink-100">{simulation.nbJoursFeries} j</strong></div>
                                <div>Week-ends : <strong className="font-data text-ink-900 dark:text-ink-100">{simulation.nbWeekends} j</strong></div>
                            </div>
                        </div>
                    )}

                    <div className="pt-4 flex justify-end gap-3 border-t border-ink-150 dark:border-[var(--color-border)]">
                        <Button type="button" variant="ghost" size="sm" onClick={() => setIsCreateModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={submitting}>
                            Créer la période
                        </Button>
                    </div>
                </form>
            </Modal>

            {/* Modale de Confirmation d'Action (Rouvrir / Valider / Clôturer) */}
            <ConfirmDialog
                isOpen={confirmAction.isOpen}
                onClose={() => setConfirmAction(prev => ({ ...prev, isOpen: false }))}
                onConfirm={() => handleExecuteWorkflow(confirmAction.periode, confirmAction.command)}
                title={confirmAction.title}
                message={confirmAction.message}
                confirmLabel={confirmAction.confirmLabel}
                cancelLabel="Annuler"
                variant={confirmAction.variant}
                loading={submitting}
            />
        </div>
    );
}
