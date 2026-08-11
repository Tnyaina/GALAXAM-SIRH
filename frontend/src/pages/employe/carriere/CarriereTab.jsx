import { useState, useEffect } from 'react';
import { 
    Briefcase, Building, Landmark, Calendar, ShieldAlert, CheckCircle, 
    Clock, Plus, FileText, Eye, RefreshCw, UserMinus, Award,
    DollarSign, MapPin
} from 'lucide-react';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';
import contratService from '@/api/employe/contratService';
import EmbaucheModal from './b/EmbaucheModal';
import TransitionModal from './b/TransitionModal';
import RecapitulatifBrouillonModal from './RecapitulatifBrouillonModal';
import ConfirmationEssaiModal from './b/ConfirmationEssaiModal';
import RenouvellementEssaiModal from './b/RenouvellementEssaiModal';
import RuptureContratModal from './b/RuptureContratModal';
import CertificatTravailModal from './b/CertificatTravailModal';
import { useCarriere } from '@/hooks/useCarriere';
import { 
    isEmbaucheEvent, isRuptureEvent,
    trouverWorkflowPlanifieFutur, fmtDate, fmtMoney 
} from '@/utils/carriereUtils';

function StatItem({ icon, label, value, highlight = false }) {
    return (
        <div className="flex items-center gap-2.5 min-w-0">
            <div className="text-ink-350 dark:text-ink-500 flex-shrink-0">{icon}</div>
            <div className="min-w-0">
                <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400 truncate">{label}</p>
                <p className={`text-sm font-semibold truncate ${highlight ? 'text-brand-500 font-mono' : 'text-ink-900 dark:text-ink-50'}`}>
                    {value || '-'}
                </p>
            </div>
        </div>
    );
}

export default function CarriereTab({ idEmploye, employe, nomEmploye, matriculeEmploye, canWrite = true, autoOpenWorkflowId = null, onEmployeUpdated }) {
    const {
        loading,
        errorMsg,
        brouillonWf,
        contratActif,
        affectationActive,
        contratsList,
        workflowsList,
        etatEssai,
        refreshData
    } = useCarriere(idEmploye);

    const [actionSuccessMsg, setActionSuccessMsg] = useState('');

    // Modales
    const [isEmbaucheModalOpen, setIsEmbaucheModalOpen] = useState(false);
    const [isTransitionModalOpen, setIsTransitionModalOpen] = useState(false);
    const [isRecapModalOpen, setIsRecapModalOpen] = useState(false);
    const [isConfirmationModalOpen, setIsConfirmationModalOpen] = useState(false);
    const [isRenouvellementModalOpen, setIsRenouvellementModalOpen] = useState(false);
    const [isRuptureModalOpen, setIsRuptureModalOpen] = useState(false);
    const [isCertificatModalOpen, setIsCertificatModalOpen] = useState(false);
    const [selectedCertificatContrat, setSelectedCertificatContrat] = useState(null);
    const [selectedWfModal, setSelectedWfModal] = useState(null);
    const [editInitialData, setEditInitialData] = useState(null);
    const [isTransitioning, setIsTransitioning] = useState(false);

    const handleOpenCertificat = (contrat = null) => {
        setSelectedCertificatContrat(contrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null));
        setIsCertificatModalOpen(true);
    };

    // Auto-ouverture du modal si un workflowId est passé via l'URL (?wf=)
    useEffect(() => {
        if (loading) return;
        if (!autoOpenWorkflowId) return;
        const targetId = Number(autoOpenWorkflowId);
        if (isNaN(targetId)) return;

        const wfFound = workflowsList.find(wf => Number(wf.idWorkflow) === targetId);
        if (wfFound) {
            setSelectedWfModal(wfFound);
            setIsRecapModalOpen(true);
        } else if (brouillonWf && Number(brouillonWf.idWorkflow) === targetId) {
            setSelectedWfModal(brouillonWf);
            setIsRecapModalOpen(true);
        }

        // Nettoyer le paramètre `wf` de l'URL pour éviter la ré-ouverture au rafraîchissement (F5)
        try {
            const url = new URL(window.location.href);
            if (url.searchParams.has('wf')) {
                url.searchParams.delete('wf');
                window.history.replaceState({}, '', url.pathname + url.search);
            }
        } catch (e) {
            console.error('Erreur nettoyage URL wf:', e);
        }
    }, [loading, autoOpenWorkflowId, workflowsList, brouillonWf]);

    const handleOpenCreateModal = () => {
        setEditInitialData(null);
        setIsEmbaucheModalOpen(true);
    };

    const handleOpenEditModal = (data) => {
        setEditInitialData(data);
        const code = data?.codeTypeEvenement;
        if (isEmbaucheEvent(code)) {
            setIsEmbaucheModalOpen(true);
        } else if (isRuptureEvent(code)) {
            setIsRuptureModalOpen(true);
        } else {
            setIsTransitionModalOpen(true);
        }
    };

    const handleSuccessAction = async (msg, createdWf = null) => {
        setActionSuccessMsg(msg);
        setTimeout(() => setActionSuccessMsg(''), 6000);

        if (createdWf && createdWf.idWorkflow) {
            setIsTransitioning(true);
            try {
                const freshWf = await contratService.getWorkflow(createdWf.idWorkflow);
                setSelectedWfModal(freshWf || createdWf);
            } catch {
                setSelectedWfModal(createdWf);
            } finally {
                setIsTransitioning(false);
                setIsRecapModalOpen(true);
            }
        }

        refreshData();
        if (onEmployeUpdated) {
            onEmployeUpdated();
        }
    };

    const getStatutBadge = (statutCode) => {
        switch (statutCode) {
            case 'ACTIF':
            case 'ACTIVE':
            case 'VALIDE':
                return <Badge variant="success" size="sm">{statutCode}</Badge>;
            case 'BROUILLON':
            case 'PLANIFIEE':
                return <Badge variant="warning" size="sm">{statutCode}</Badge>;
            case 'ANNULE':
            case 'ANNULEE':
            case 'ROMPU':
                return <Badge variant="danger" size="sm">{statutCode}</Badge>;
            case 'CLOTURE':
            case 'HISTORISEE':
                return <Badge variant="default" size="sm">{statutCode}</Badge>;
            default:
                return <Badge variant="primary" size="sm">{statutCode}</Badge>;
        }
    };

    const workflowsValidesDuContrat = contratActif
        ? workflowsList
            .filter((wf) => wf.codeStatutWorkflow === 'VALIDE' && (Number(wf.idContratCible) === Number(contratActif.idContrat) || Number(wf.idContratSource) === Number(contratActif.idContrat)))
            .sort((a, b) => new Date(b.dateEffet || b.dateDemande || 0) - new Date(a.dateEffet || a.dateDemande || 0))
        : [];
    const workflowCplActif = workflowsValidesDuContrat[0] || null;

    const todayStr = new Date().toISOString().substring(0, 10);
    const workflowPlanifieFutur = trouverWorkflowPlanifieFutur(workflowsList, contratActif?.idContrat, todayStr);
    const estEssaiRenouveleFlag = etatEssai.estRenouvele;

    if (loading) {
        return (
            <div className="py-16 flex justify-center">
                <Loading size="md" label="Chargement du dossier carrière & contrat..." />
            </div>
        );
    }

    return (
        <div className="space-y-8">

            {/* 📊 HEADER & STATISTIQUES : Statut Contractuel Actuel */}
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-sm shadow-sm p-5">
                <div className="flex flex-wrap items-center gap-x-8 gap-y-4">
                    <div className="flex items-center gap-3 pr-8 border-r border-ink-150 dark:border-ink-700 last:border-0">
                        <div className={`w-10 h-10 rounded-full flex items-center justify-center flex-shrink-0 ${
                            contratActif ? 'bg-success-500/10 text-success-600' : brouillonWf ? 'bg-warning-500/10 text-warning-600' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'
                        }`}>
                            <Briefcase size={18} />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Statut Actuel</p>
                            {contratActif ? (
                                <Badge variant="success" size="sm" dot>ACTIF</Badge>
                            ) : brouillonWf ? (
                                <Badge variant="warning" size="sm" dot>BROUILLON EN ATTENTE</Badge>
                            ) : (
                                <Badge variant="default" size="sm" dot>AUCUN CONTRAT</Badge>
                            )}
                        </div>
                    </div>

                    <StatItem
                        icon={<FileText size={16} />}
                        label="Type de Contrat"
                        value={contratActif?.libelleTypeContrat}
                    />
                    <StatItem
                        icon={<Award size={16} />}
                        label="Fonction Actuelle"
                        value={affectationActive?.libelleFonction}
                    />
                    <StatItem
                        icon={<Calendar size={16} />}
                        label="Date d'Entrée"
                        value={contratActif?.dateDebut ? fmtDate(contratActif.dateDebut) : null}
                    />
                    <StatItem
                        icon={<MapPin size={16} />}
                        label="Site / Département"
                        value={
                            affectationActive
                                ? `${affectationActive.libelleSite || '-'} / ${affectationActive.libelleDepartement || '-'}`
                                : null
                        }
                    />
                    <StatItem
                        icon={<DollarSign size={16} />}
                        label="Salaire Actuel"
                        value={affectationActive?.salaireBase !== undefined ? fmtMoney(affectationActive.salaireBase) : null}
                        highlight
                    />
                </div>
            </div>

            {/* Messages Succès / Erreur */}
            {actionSuccessMsg && (
                <div className="bg-success-500/10 text-success-700 dark:text-success-400 border border-success-500/30 p-4 rounded-sm text-xs font-bold flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <CheckCircle size={16} />
                        <span>{actionSuccessMsg}</span>
                    </div>
                </div>
            )}

            {errorMsg && (
                <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 p-4 rounded-sm text-xs flex items-center gap-2">
                    <ShieldAlert size={16} />
                    <span>{errorMsg}</span>
                </div>
            )}

            {/* 🟡 BANNIÈRE : BROUILLON D'EMBAUCHE EN COURS */}
            {brouillonWf && brouillonWf.codeStatutWorkflow === 'BROUILLON' && (
                <div className="bg-warning-500/10 border border-warning-500/30 p-5 rounded-sm flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-warning-500/20 text-warning-700 dark:text-warning-400 rounded-full flex items-center justify-center font-bold flex-shrink-0">
                            <Clock size={20} />
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <h4 className="text-sm font-bold text-ink-900 dark:text-white">
                                    {brouillonWf.codeTypeEvenement === 'EMBAUCHE_INITIALE'
                                        ? 'Brouillon d\'Embauche en attente de Validation'
                                        : brouillonWf.codeTypeEvenement === 'CONFIRMATION_ESSAI'
                                        ? 'Confirmation d\'Essai en attente de Validation'
                                        : brouillonWf.codeTypeEvenement === 'RENOUVELLEMENT_ESSAI'
                                        ? 'Renouvellement d\'Essai en attente de Validation'
                                        : `${brouillonWf.libelleTypeEvenement || 'Mouvement RH'} en attente de Validation`}
                                </h4>
                                <Badge variant="warning" size="sm">BROUILLON</Badge>
                            </div>
                            <p className="text-xs text-ink-500 dark:text-ink-300">
                                Contrat N° <span className="font-mono font-bold text-brand-500">{brouillonWf.numeroContratCible}</span> ({brouillonWf.libelleTypeContrat || 'Contrat'}) — Poste : <span className="font-semibold">{brouillonWf.libelleFonction || '-'}</span> à <span className="font-semibold">{brouillonWf.libelleSite || '-'}</span>
                            </p>
                        </div>
                    </div>

                    <div className="flex items-center gap-2 self-end sm:self-auto">
                        {canWrite && (
                            <Button
                                onClick={() => handleOpenEditModal(brouillonWf)}
                                variant="outline"
                                size="sm"
                            >
                                Corriger
                            </Button>
                        )}
                        <Button
                            onClick={() => { setSelectedWfModal(brouillonWf); setIsRecapModalOpen(true); }}
                            variant="primary"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                        >
                            Relecture & Validation
                        </Button>
                    </div>
                </div>
            )}

            {/* 🔵 BANNIÈRE : MOUVEMENT RH PLANIFIÉ À DATE ULTÉRIEURE */}
            {workflowPlanifieFutur && (
                <div className="bg-brand-500/10 border border-brand-500/30 p-5 rounded-sm flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-brand-500/20 text-brand-600 dark:text-brand-400 rounded-full flex items-center justify-center font-bold flex-shrink-0">
                            <Calendar size={20} />
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <h4 className="text-sm font-bold text-ink-900 dark:text-white">
                                    Mouvement RH Planifié (Prendra effet le {fmtDate(workflowPlanifieFutur.dateEffet)})
                                </h4>
                                <Badge variant="primary" size="sm">VALIDÉ — PLANIFIÉ</Badge>
                            </div>
                            <p className="text-xs text-ink-500 dark:text-ink-300">
                                {workflowPlanifieFutur.libelleTypeEvenementRh || 'Transition'} — Poste cible : <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowPlanifieFutur.libelleFonction || '-'}</span> à <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowPlanifieFutur.libelleSite || '-'}</span> (Nouveau salaire : <span className="font-mono font-semibold text-brand-500">{fmtMoney(workflowPlanifieFutur.salaireBase)}</span>)
                            </p>
                        </div>
                    </div>

                    <Button
                        onClick={() => { setSelectedWfModal(workflowPlanifieFutur); setIsRecapModalOpen(true); }}
                        variant="outline"
                        size="sm"
                        iconLeft={<Eye size={16} />}
                    >
                        Consulter le Mouvement
                    </Button>
                </div>
            )}

            {/* 🟢 SECTION : CONTRAT ACTIF & AFFECTATION EN COURS */}
            {contratActif ? (
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-sm shadow-sm p-6 space-y-6">
                    <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-4 border-b border-ink-150 dark:border-ink-700">
                        <div className="flex items-center gap-3">
                            <div className="w-10 h-10 bg-success-500/10 text-success-600 rounded-full flex items-center justify-center font-bold">
                                <Briefcase size={20} />
                            </div>
                            <div>
                                <div className="flex items-center gap-2">
                                    <h3 className="text-base font-bold text-ink-900 dark:text-white">
                                        Contrat Actif N° {contratActif.numeroContrat}
                                    </h3>
                                    <Badge variant="success" size="sm">ACTIF</Badge>
                                    {(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai) && !workflowsList.some(w => w.codeStatutWorkflow === 'VALIDE' && w.codeTypeEvenement === 'CONFIRMATION_ESSAI' && (Number(w.idContratCible) === Number(contratActif.idContrat) || Number(w.idContratSource) === Number(contratActif.idContrat))) && (
                                        <Badge variant="warning" size="sm" icon={<Clock size={12} />}>
                                            Période d'Essai (Fin le {fmtDate(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai)})
                                        </Badge>
                                    )}
                                </div>
                                <p className="text-xs text-ink-400">Relation contractuelle et juridique en cours d'exécution</p>
                            </div>
                        </div>

                        {canWrite && !brouillonWf && (
                            <div className="flex flex-wrap items-center gap-2">
                                <Button
                                    onClick={() => setIsTransitionModalOpen(true)}
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<RefreshCw size={16} />}
                                >
                                    Mouvement / Transition RH
                                </Button>
                                <Button
                                    onClick={() => setIsRuptureModalOpen(true)}
                                    variant="danger"
                                    size="sm"
                                    iconLeft={<UserMinus size={16} />}
                                >
                                    Rupture / Fin de Contrat
                                </Button>
                            </div>
                        )}
                    </div>

                    {/* Grille Contrat + Affectation */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        
                        {/* Bloc 1 : Termes du Contrat */}
                        <div className="bg-ink-50/50 dark:bg-ink-950/40 p-4 border border-ink-150 dark:border-ink-800 rounded-sm space-y-2 text-xs">
                            <h4 className="font-bold text-brand-500 uppercase tracking-wider text-[10px] pb-1 border-b border-ink-150 dark:border-ink-800 flex items-center gap-1.5">
                                <Building size={12} /> Termes Contractuels
                            </h4>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Type de Contrat :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleTypeContrat || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Filiale Employeuse :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleFiliale || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Date de Début :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{fmtDate(contratActif.dateDebut)}</span>
                            </div>
                            {etatEssai.isEssaiActif && (
                                <div className={`py-2 px-2.5 my-1.5 border rounded space-y-2 ${
                                    etatEssai.isEssaiEchu 
                                        ? 'bg-danger-500/10 border-danger-500/30 text-danger-800 dark:text-danger-300' 
                                        : 'bg-warning-500/10 border-warning-500/20 text-warning-800 dark:text-warning-300'
                                }`}>
                                    <div className="flex justify-between items-center font-medium">
                                        <span className="flex items-center gap-1.5 font-bold">
                                            <Clock size={13} /> {
                                                etatEssai.isEssaiEchu 
                                                    ? 'Période d\'Essai Échue (Action requise) :' 
                                                    : (etatEssai.isDernierJour ? 'Dernier Jour d\'Essai :' : 'Période d\'Essai :')
                                            }
                                        </span>
                                        <span className="font-bold">
                                            {etatEssai.dureeEssaiMois} Mois 
                                            {etatEssai.isEssaiEchu 
                                                ? ` (Finie le ${fmtDate(etatEssai.dateFinEssaiStr)})` 
                                                : (etatEssai.isDernierJour ? ' (Échéance ce soir à 23h59)' : ` (Fin le ${fmtDate(etatEssai.dateFinEssaiStr)})`)
                                            }
                                        </span>
                                    </div>
                                    {canWrite && (!brouillonWf || !['CONFIRMATION_ESSAI', 'RENOUVELLEMENT_ESSAI'].includes(brouillonWf?.codeTypeEvenement)) && (
                                        <div className="pt-1.5 border-t border-current/20 flex flex-wrap items-center gap-2">
                                            <Button
                                                onClick={() => setIsConfirmationModalOpen(true)}
                                                variant="success"
                                                size="xs"
                                                iconLeft={<CheckCircle size={13} />}
                                            >
                                                Confirmer l'Essai
                                            </Button>
                                            {!estEssaiRenouveleFlag ? (
                                                <Button
                                                    onClick={() => setIsRenouvellementModalOpen(true)}
                                                    variant="outline"
                                                    size="xs"
                                                    iconLeft={<RefreshCw size={13} />}
                                                >
                                                    Renouveler l'Essai
                                                </Button>
                                            ) : (
                                                <Badge variant="warning" size="sm">
                                                    2ème Période (Renouvelée — Max légal d'essai atteint)
                                                </Badge>
                                            )}
                                        </div>
                                    )}
                                </div>
                            )}
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Date de Fin :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{fmtDate(contratActif.dateFin)}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Date Signature :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{fmtDate(contratActif.dateSignature)}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Effectif Légal :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">
                                    {contratActif.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Hors effectif)'}
                                </span>
                            </div>
                        </div>

                        {/* Bloc 2 : Affectation & Paie */}
                        <div className="bg-ink-50/50 dark:bg-ink-950/40 p-4 border border-ink-150 dark:border-ink-800 rounded-sm space-y-2 text-xs">
                            <h4 className="font-bold text-brand-500 uppercase tracking-wider text-[10px] pb-1 border-b border-ink-150 dark:border-ink-800 flex items-center gap-1.5">
                                <Landmark size={12} /> Affectation Courante
                            </h4>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Site :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleSite || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Département :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleDepartement || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Service :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleService || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Fonction / Poste :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleFonction || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Catégorie Pro :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleCategoriePro || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between">
                                <span className="text-ink-400">Supérieur N+1 :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.nomPrenomResponsable || '-'}</span>
                            </div>
                            <div className="py-1 flex justify-between border-t border-ink-150 dark:border-ink-800 mt-1 pt-2">
                                <span className="text-ink-400">Salaire de Base :</span>
                                <span className="font-bold text-brand-500 font-mono text-sm">{fmtMoney(affectationActive?.salaireBase)}</span>
                            </div>
                        </div>
                    </div>
                </div>
            ) : !brouillonWf && (
                /* ⚪ EMPTY STATE : PAS DE CONTRAT ACTIF */
                <div className="p-10 text-center bg-white dark:bg-ink-900 border border-dashed border-ink-200 dark:border-ink-700 rounded-sm space-y-4">
                    <div className="w-14 h-14 bg-brand-500/10 text-brand-500 rounded-full flex items-center justify-center mx-auto">
                        <Briefcase size={28} />
                    </div>
                    <div className="max-w-md mx-auto space-y-1">
                        <h3 className="text-base font-bold text-ink-900 dark:text-white">Aucun Contrat Actif</h3>
                        <p className="text-xs text-ink-400">
                            Cet employé ne dispose pas encore de contrat de travail enregistré. Vous pouvez initier son embauche.
                        </p>
                    </div>
                    {canWrite && (
                        <Button
                            onClick={handleOpenCreateModal}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Initier l'Embauche (Créer Contrat)
                        </Button>
                    )}
                </div>
            )}

            {/* 📋 HISTORIQUE DE CARRIÈRE & WORKFLOWS */}
            <div className="space-y-6 pt-4">
                
                {/* Historique des Workflows */}
                <div className="space-y-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2 pb-2 border-b border-ink-150 dark:border-ink-700">
                        <Clock size={14} /> Historique des Événements & Workflows RH ({workflowsList.length})
                    </h3>

                    {workflowsList.length === 0 ? (
                        <p className="text-xs text-ink-400">Aucun événement RH enregistré.</p>
                    ) : (
                        <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-sm">
                            <table className="w-full text-xs text-left">
                                <thead>
                                    <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-bold uppercase tracking-widest text-[10px]">
                                        <th className="px-4 py-3">Événement RH</th>
                                        <th className="px-4 py-3">Contrat Cible</th>
                                        <th className="px-4 py-3">Poste / Fonction</th>
                                        <th className="px-4 py-3">Date d'Effet</th>
                                        <th className="px-4 py-3">Statut Workflow</th>
                                        <th className="px-4 py-3 text-right">Action</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                    {workflowsList.map((wf) => (
                                        <tr key={wf.idWorkflow} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-semibold">
                                                {wf.libelleTypeEvenement || wf.codeTypeEvenement}
                                            </td>
                                            <td className="px-4 py-3 font-mono font-bold text-brand-500">
                                                {wf.numeroContratCible || '-'}
                                            </td>
                                            <td className="px-4 py-3">
                                                {wf.libelleFonction || '-'} ({wf.libelleSite || '-'})
                                            </td>
                                            <td className="px-4 py-3 font-mono">
                                                {fmtDate(wf.dateEffet)}
                                            </td>
                                            <td className="px-4 py-3">
                                                {getStatutBadge(wf.codeStatutWorkflow)}
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                {wf.codeStatutWorkflow === 'BROUILLON' ? (
                                                    <Button
                                                        onClick={() => { setSelectedWfModal(wf); setIsRecapModalOpen(true); }}
                                                        variant="ghost"
                                                        size="xs"
                                                    >
                                                        Relecture
                                                    </Button>
                                                ) : (
                                                    <Button
                                                        onClick={() => { setSelectedWfModal(wf); setIsRecapModalOpen(true); }}
                                                        variant="ghost"
                                                        size="xs"
                                                        iconLeft={<Eye size={14} />}
                                                    >
                                                        Détails
                                                    </Button>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>

                {/* Historique des Contrats */}
                <div className="space-y-3 pt-4">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2 pb-2 border-b border-ink-150 dark:border-ink-700">
                        <FileText size={14} /> Historique des Contrats de Travail ({contratsList.length})
                    </h3>

                    {contratsList.length === 0 ? (
                        <p className="text-xs text-ink-400">Aucun contrat dans l'historique.</p>
                    ) : (
                        <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-sm">
                            <table className="w-full text-xs text-left">
                                <thead>
                                    <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-bold uppercase tracking-widest text-[10px]">
                                        <th className="px-4 py-3">N° Contrat</th>
                                        <th className="px-4 py-3">Dates (Début → Fin)</th>
                                        <th className="px-4 py-3">Date Signature</th>
                                        <th className="px-4 py-3">Effectif</th>
                                        <th className="px-4 py-3">Description</th>
                                        <th className="px-4 py-3 text-right">Document</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                    {contratsList.map((ct) => (
                                        <tr key={ct.idContrat} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-mono font-bold text-brand-500">
                                                {ct.numeroContrat}
                                            </td>
                                            <td className="px-4 py-3 font-mono">
                                                {fmtDate(ct.dateDebut)} → {fmtDate(ct.dateFin)}
                                            </td>
                                            <td className="px-4 py-3 font-mono">
                                                {fmtDate(ct.dateSignature)}
                                            </td>
                                            <td className="px-4 py-3">
                                                {ct.comptabiliseEffectif ? 'Oui' : 'Non'}
                                            </td>
                                            <td className="px-4 py-3 text-ink-500">
                                                {ct.description || '-'}
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                {(ct.codeStatutContrat === 'CLOTURE' || ct.codeStatutContrat === 'ROMPU' || (ct.dateFin && ct.codeStatutContrat !== 'ACTIF')) ? (
                                                    <Button
                                                        onClick={() => handleOpenCertificat(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        iconLeft={<Award size={13} />}
                                                    >
                                                        Certificat
                                                    </Button>
                                                ) : (
                                                    <span className="text-[11px] text-ink-400 italic">Contrat en cours</span>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            </div>

            {/* Modale Formulaire Embauche (Création / Édition) */}
            {isEmbaucheModalOpen && (
                <EmbaucheModal
                    isOpen={isEmbaucheModalOpen}
                    onClose={() => setIsEmbaucheModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon d\'embauche enregistré avec succès !', res)}
                />
            )}

            {/* Modale Récapitulatif DAF & Validation */}
            {isRecapModalOpen && (
                <RecapitulatifBrouillonModal
                    isOpen={isRecapModalOpen}
                    onClose={() => {
                        setIsRecapModalOpen(false);
                        setSelectedWfModal(null);
                        try {
                            const url = new URL(window.location.href);
                            if (url.searchParams.has('wf')) {
                                url.searchParams.delete('wf');
                                window.history.replaceState({}, '', url.pathname + url.search);
                            }
                        } catch (e) {
                            console.error('Erreur nettoyage URL wf onClose:', e);
                        }
                    }}
                    workflowCpl={selectedWfModal || brouillonWf}
                    onEdit={(wf) => handleOpenEditModal(wf)}
                    onActionSuccess={(msg) => handleSuccessAction(msg)}
                />
            )}

            {/* Modale Slide-Over Transition / Mouvement RH (Sprint 2) */}
            {isTransitionModalOpen && (
                <TransitionModal
                    isOpen={isTransitionModalOpen}
                    onClose={() => setIsTransitionModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    workflowActif={workflowCplActif}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon de transition enregistré avec succès !', res)}
                />
            )}

            {/* Modale de Confirmation Rapide Période d'Essai */}
            {isConfirmationModalOpen && (
                <ConfirmationEssaiModal
                    isOpen={isConfirmationModalOpen}
                    onClose={() => setIsConfirmationModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    onSuccess={(res) => handleSuccessAction('Demande de confirmation d\'essai créée avec succès !', res)}
                />
            )}

            {/* Modale de Renouvellement Rapide Période d'Essai */}
            {isRenouvellementModalOpen && (
                <RenouvellementEssaiModal
                    isOpen={isRenouvellementModalOpen}
                    onClose={() => setIsRenouvellementModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    onSuccess={(res) => handleSuccessAction('Demande de renouvellement d\'essai créée avec succès !', res)}
                />
            )}

            {/* Modale de Rupture et Clôture de Contrat */}
            {isRuptureModalOpen && (
                <RuptureContratModal
                    isOpen={isRuptureModalOpen}
                    onClose={() => setIsRuptureModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    workflowActif={workflowCplActif}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon de rupture et fin de contrat créé avec succès !', res)}
                />
            )}

            {/* Modale de Génération du Certificat de Travail */}
            {isCertificatModalOpen && (
                <CertificatTravailModal
                    isOpen={isCertificatModalOpen}
                    onClose={() => setIsCertificatModalOpen(false)}
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedCertificatContrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null)}
                    affectation={affectationActive}
                    filiale={selectedCertificatContrat?.filiale || contratActif?.filiale}
                    dateDebutInitiale={
                        contratsList.length > 0
                            ? [...contratsList].sort((a, b) => new Date(a.dateDebut || 0) - new Date(b.dateDebut || 0))[0]?.dateDebut
                            : selectedCertificatContrat?.dateDebut
                    }
                />
            )}

            {/* Overlay d'attente/transition fluide */}
            {isTransitioning && (
                <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-ink-950/70 backdrop-blur-sm transition-all duration-300">
                    <div className="flex flex-col items-center gap-4 p-6 bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-800 rounded-lg shadow-2xl max-w-sm w-full text-center">
                        <Loading size="md" label="Enregistrement en cours..." />
                        <p className="text-[11px] text-ink-400 -mt-2">
                            Préparation du récapitulatif DAF...
                        </p>
                    </div>
                </div>
            )}
        </div>
    );
}