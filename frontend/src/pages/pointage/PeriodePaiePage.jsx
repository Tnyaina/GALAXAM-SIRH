import { useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { 
    CalendarRange, 
    Plus, 
    RefreshCw, 
    CheckCircle, 
    Play, 
    ArrowRight, 
    ArrowLeft,
    RotateCcw, 
    Clock, 
    CheckCheck, 
    Archive,
    ShieldCheck
} from 'lucide-react';
import { usePeriodePaie } from '@/hooks/pointage/usePeriodePaie';
import { pointageSyntheseService } from '@/services/pointageSyntheseService';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Card, { CardBody } from '@/components/ui/data-display/Card';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import { useAuth } from '@/context/AuthContext';
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
        desc: 'Saisie terminée, attente validation DAF' 
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

export default function PeriodePaiePage() {
    const {
        periodes,
        loading,
        error,
        setError,
        loadPeriodes,
        createPeriode,
        updateStatut
    } = usePeriodePaie();

    const { hasRole, hasAuthority } = useAuth();
    const canManage = hasRole?.('ADMIN') || hasAuthority?.('ROLE_ADMIN') || true;
    const toast = useToast();

    // Modale de Création
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [newPeriode, setNewPeriode] = useState({
        code: '',
        libelle: '',
        dateDebut: '',
        dateFin: ''
    });

    // Modale de Confirmation de Statut (pour Rouvrir, Valider, Clôturer)
    const [confirmAction, setConfirmAction] = useState({
        isOpen: false,
        periode: null,
        targetStatut: null,
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
                message: `La période "${newPeriode.libelle}" a été créée avec succès.`,
                type: 'success'
            });
            setIsCreateModalOpen(false);
            setNewPeriode({ code: '', libelle: '', dateDebut: '', dateFin: '' });
        } else {
            toast({
                title: 'Erreur',
                message: error || 'Impossible de créer la période de paie.',
                type: 'danger'
            });
        }
    };

    const triggerStatutChange = async (periode, nextStatutCode) => {
        if (nextStatutCode === 'EN_COURS') {
            if (periode.statutCode === 'BROUILLON') {
                // Brouillon -> En cours
                setConfirmAction({
                    isOpen: true,
                    periode,
                    targetStatut: nextStatutCode,
                    title: 'Ouvrir la période à la saisie',
                    message: `Souhaitez-vous ouvrir la période "${periode.libelle}" (${periode.dateDebut} au ${periode.dateFin}) ? Les agents de pointage pourront immédiatement débuter la saisie quotidienne.`,
                    variant: 'primary',
                    confirmLabel: 'Ouvrir la saisie'
                });
            } else {
                // Validée/Clôturée -> En cours (Réouverture)
                setConfirmAction({
                    isOpen: true,
                    periode,
                    targetStatut: nextStatutCode,
                    title: 'Rouvrir et déverrouiller la période',
                    message: `Attention : La réouverture de la période "${periode.libelle}" réactivera les droits de saisie et de modification des pointages pour les agents et débloquera le gel des données. Confirmez-vous le déverrouillage exceptionnel ?`,
                    variant: 'warning',
                    confirmLabel: 'Oui, rouvrir la saisie'
                });
            }
        } else if (nextStatutCode === 'A_VALIDER') {
            // En cours -> À valider
            setConfirmAction({
                isOpen: true,
                periode,
                targetStatut: nextStatutCode,
                title: 'Transmettre la période pour validation',
                message: `Confirmez-vous la fin de saisie pour la période "${periode.libelle}" ? Le statut passera à "À valider" pour vérification par la DAF.`,
                variant: 'warning',
                confirmLabel: 'Transmettre pour validation'
            });
        } else if (nextStatutCode === 'VALIDEE') {
            // À valider -> Validée (avec vérification préalable des anomalies)
            let warningDetails = '';
            try {
                const check = await pointageSyntheseService.getPreClotureCheck(periode.idPeriodePaie);
                if (check && check.totalAnomalies > 0) {
                    warningDetails = `\n\n⚠️ Information importante : Il subsiste ${check.totalAnomalies} anomalie(s) non régularisée(s) sur cette période (${check.totalIncomplets} pointages incomplets, ${check.totalAbsencesInjustifiees} absences non justifiées, ${check.totalRetardsNonJustifies} retards sans motif). Vous pouvez valider dès maintenant pour geler les données en l'état ou vérifier la page des anomalies au préalable.`;
                }
            } catch (err) {
                console.error('Erreur vérification anomalies :', err);
            }

            setConfirmAction({
                isOpen: true,
                periode,
                targetStatut: nextStatutCode,
                title: 'Validation de la période & Gel des données Paie',
                message: `Vous êtes sur le point de valider la période "${periode.libelle}". Tous les pointages seront verrouillés et les cumuls légaux (heures normales, HS 30/50, indemnités) seront gelés définitivement pour la Paie.${warningDetails}`,
                variant: 'primary',
                confirmLabel: 'Valider et figer les données'
            });
        } else if (nextStatutCode === 'CLOTUREE') {
            // Validée -> Clôturée
            setConfirmAction({
                isOpen: true,
                periode,
                targetStatut: nextStatutCode,
                title: 'Clôture définitive de la période',
                message: `La clôture marque l'archivage final pour "${periode.libelle}". Confirmez-vous la clôture définitive de cette période de paie ?`,
                variant: 'danger',
                confirmLabel: 'Clôturer définitivement'
            });
        }
    };

    const handleExecuteStatut = async (id, targetStatut) => {
        setSubmitting(true);
        const success = await updateStatut(id, targetStatut);
        setSubmitting(false);
        setConfirmAction(prev => ({ ...prev, isOpen: false }));
        if (success) {
            toast({
                title: 'Statut mis à jour',
                message: `La période est désormais au statut : ${STATUT_CONFIG[targetStatut]?.label || targetStatut}`,
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
        <div className="space-y-6 font-sans">
            <SEO title="Périodes de Paie & Clôture" description="Gestion des périodes mensuelles de pointage et cycle de paie." />

            {/* Lien retour */}
            <Link 
                to="/dashboard" 
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold"
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête standard Galaxam */}
            <div className="relative overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-sm p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="absolute top-0 right-0 w-64 h-64 bg-[var(--color-brand-500)]/5 rounded-none blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />
                
                <div className="relative flex items-center gap-5">
                    <div className="w-14 h-14 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-lg shadow-[var(--color-brand-500)]/20 shrink-0">
                        <CalendarRange size={26} strokeWidth={2.5} />
                    </div>
                    <div>
                        <h1 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-fg)] tracking-tight">
                            Périodes de Paie & Clôture
                        </h1>
                        <p className="text-sm text-[var(--color-fg-muted)] mt-1 font-medium">
                            Cycle de paie mensuel — ouverture, validation DAF, clôture et verrouillage de la saisie des présences.
                        </p>
                    </div>
                </div>

                <div className="relative flex items-center gap-3 shrink-0">
                    <Button
                        onClick={loadPeriodes}
                        variant="outline"
                        size="sm"
                        icon={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                    >
                        Actualiser
                    </Button>
                    {canManage && (
                        <Button
                            onClick={() => setIsCreateModalOpen(true)}
                            variant="primary"
                            size="sm"
                            icon={<Plus size={16} />}
                        >
                            Nouvelle période
                        </Button>
                    )}
                </div>
            </div>

            {/* Cartes KPI / Statistiques */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-brand-50 dark:bg-brand-900/30 text-brand-500 flex items-center justify-center">
                            <CalendarRange size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Total Périodes</p>
                            <p className="text-2xl font-bold text-ink-800 dark:text-white">{stats.total}</p>
                        </div>
                    </CardBody>
                </Card>

                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-sky-500/10 text-sky-500 flex items-center justify-center">
                            <Clock size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">En cours (Active)</p>
                            <p className="text-2xl font-bold text-sky-500">{stats.enCours}</p>
                        </div>
                    </CardBody>
                </Card>

                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-warning-500/10 text-warning-500 flex items-center justify-center">
                            <CheckCheck size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">À Valider (Fin de mois)</p>
                            <p className="text-2xl font-bold text-warning-500">{stats.aValider}</p>
                        </div>
                    </CardBody>
                </Card>

                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-success-500/10 text-success-500 flex items-center justify-center">
                            <ShieldCheck size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Verrouillées / Clôturées</p>
                            <p className="text-2xl font-bold text-success-500">{stats.verrouillees}</p>
                        </div>
                    </CardBody>
                </Card>
            </div>

            {error && <Alert type="danger" message={error} onClose={() => setError(null)} />}

            {/* Table des Périodes */}
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="p-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between">
                    <div>
                        <h2 className="text-sm font-bold uppercase tracking-wider text-ink-800 dark:text-white">
                            Historique et Statut des Périodes de Paie
                        </h2>
                        <p className="text-xs text-ink-400 dark:text-ink-400 mt-0.5">
                            Le verrouillage bloque instantanément toute modification dans la Saisie Quotidienne.
                        </p>
                    </div>
                </div>

                {loading && periodes.length === 0 ? (
                    <SkeletonTable rows={5} cols={6} />
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Code Période</Th>
                                <Th>Libellé</Th>
                                <Th>Date Début</Th>
                                <Th>Date Fin</Th>
                                <Th>Statut</Th>
                                <Th className="text-right">Actions sur le Cycle</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {periodes.map(p => {
                                const isLocked = p.statutCode === 'VALIDEE' || p.statutCode === 'CLOTUREE';
                                return (
                                    <Tr key={p.idPeriodePaie} className={isLocked ? 'bg-ink-50/30 dark:bg-ink-950/30' : ''}>
                                        <Td className="font-mono font-bold text-brand-600 dark:text-brand-400">
                                            {p.code}
                                        </Td>
                                        <Td className="font-semibold text-ink-900 dark:text-white">
                                            {p.libelle}
                                        </Td>
                                        <Td className="text-xs font-mono">{p.dateDebut}</Td>
                                        <Td className="text-xs font-mono">{p.dateFin}</Td>
                                        <Td>
                                            <Badge variant={STATUT_CONFIG[p.statutCode]?.variant || 'secondary'}>
                                                {STATUT_CONFIG[p.statutCode]?.label || p.statutLibelle}
                                            </Badge>
                                        </Td>
                                        <Td className="text-right space-x-2">
                                            {p.statutCode === 'BROUILLON' && (
                                                <Button 
                                                    size="xs" 
                                                    variant="outline" 
                                                    icon={<Play size={13}/>} 
                                                    onClick={() => triggerStatutChange(p, 'EN_COURS')}
                                                >
                                                    Ouvrir la saisie
                                                </Button>
                                            )}
                                            {p.statutCode === 'EN_COURS' && (
                                                <Button 
                                                    size="xs" 
                                                    variant="outline" 
                                                    icon={<ArrowRight size={13}/>} 
                                                    onClick={() => triggerStatutChange(p, 'A_VALIDER')}
                                                >
                                                    Terminer mois (À valider)
                                                </Button>
                                            )}
                                            {p.statutCode === 'A_VALIDER' && (
                                                <Button 
                                                    size="xs" 
                                                    variant="primary" 
                                                    icon={<CheckCircle size={13}/>} 
                                                    onClick={() => triggerStatutChange(p, 'VALIDEE')}
                                                >
                                                    Valider (DAF)
                                                </Button>
                                            )}
                                            {p.statutCode === 'VALIDEE' && (
                                                <Button 
                                                    size="xs" 
                                                    variant="danger" 
                                                    icon={<Archive size={13}/>} 
                                                    onClick={() => triggerStatutChange(p, 'CLOTUREE')}
                                                >
                                                    Clôturer
                                                </Button>
                                            )}
                                            {isLocked && canManage && (
                                                <Button 
                                                    size="xs" 
                                                    variant="ghost" 
                                                    className="!text-warning-600 dark:!text-warning-400 hover:!bg-warning-500/10" 
                                                    icon={<RotateCcw size={13}/>} 
                                                    onClick={() => triggerStatutChange(p, 'EN_COURS')}
                                                >
                                                    Rouvrir
                                                </Button>
                                            )}
                                        </Td>
                                    </Tr>
                                );
                            })}
                            {periodes.length === 0 && !loading && (
                                <Tr>
                                    <Td colSpan={6} className="text-center py-12 text-ink-400 dark:text-ink-400">
                                        <CalendarRange size={36} className="mx-auto mb-2 opacity-40" />
                                        Aucune période de paie configurée pour le moment.
                                    </Td>
                                </Tr>
                            )}
                        </Tbody>
                    </Table>
                )}
            </div>

            {/* Modale de Création de Période */}
            <Modal 
                isOpen={isCreateModalOpen} 
                onClose={() => setIsCreateModalOpen(false)}
                title="Créer une Nouvelle Période de Paie"
            >
                <form onSubmit={handleCreate} className="space-y-4">
                    <div className="bg-brand-50/50 dark:bg-brand-900/20 p-3 border border-brand-200 dark:border-brand-800/40 text-xs text-brand-800 dark:text-brand-300">
                        La période sera initialement créée au statut <strong>Brouillon</strong>. Vous pourrez ensuite l'ouvrir à la saisie.
                    </div>
                    <Input 
                        label="Code Période (ex: 2026-09)" 
                        placeholder="AAAA-MM"
                        value={newPeriode.code} 
                        onChange={e => setNewPeriode({...newPeriode, code: e.target.value})} 
                        required 
                    />
                    <Input 
                        label="Libellé complet (ex: Septembre 2026)" 
                        placeholder="Nom de la période"
                        value={newPeriode.libelle} 
                        onChange={e => setNewPeriode({...newPeriode, libelle: e.target.value})} 
                        required 
                    />
                    <div className="grid grid-cols-2 gap-4">
                        <Input 
                            type="date" 
                            label="Date de début" 
                            value={newPeriode.dateDebut} 
                            onChange={e => setNewPeriode({...newPeriode, dateDebut: e.target.value})} 
                            required 
                        />
                        <Input 
                            type="date" 
                            label="Date de fin" 
                            value={newPeriode.dateFin} 
                            onChange={e => setNewPeriode({...newPeriode, dateFin: e.target.value})} 
                            required 
                        />
                    </div>
                    
                    <div className="pt-4 flex justify-end gap-3 border-t border-ink-150 dark:border-ink-700">
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
                onConfirm={() => handleExecuteStatut(confirmAction.periode?.idPeriodePaie, confirmAction.targetStatut)}
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
