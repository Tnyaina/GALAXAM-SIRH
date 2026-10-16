import { useState, useEffect, useCallback } from 'react';
import {
    AlertTriangle,
    ArrowRight,
    Award,
    BadgeCheck,
    Banknote,
    Building2,
    Calculator,
    Calendar,
    CalendarCheck,
    CheckCircle2,
    Clock,
    CreditCard,
    ExternalLink,
    FileCheck,
    FileSpreadsheet,
    FileText,
    HelpCircle,
    Info,
    Key,
    Laptop,
    Lock,
    Save,
    ShieldAlert,
    ShieldCheck,
    User,
    UserX,
    XCircle
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import contratService from '@/features/employees/api/contratService';
import { optionsMotifsSortie } from '@/features/employees/utils/motifsSortie';
import { getErrorMessage } from '@/api/apiClient';

const OPTIONS_DECISION_PRET = [
    { value: 'MAINTIEN_ECHEANCIER', label: '1. Maintien de l’échéancier (Virement personnel du salarié)' },
    { value: 'REMBOURSEMENT_SEPARE', label: '2. Remboursement séparé direct (Chèque ou virement dédié)' },
    { value: 'RETENUE_STC_AUTORISEE', label: '3. Retenue sur STC (Dans la limite de la quotité saisissable légale)' },
    { value: 'CONTENTIEUX_RECOUVREMENT', label: '4. Dossier transmis au service recouvrement / contentieux' },
];

const MODES_REMISE_CERTIFICAT = [
    { value: 'MAIN_PROPRE', label: 'Remise en main propre contre décharge' },
    { value: 'COURRIER_RECOMMANDE', label: 'Envoi postal recommandé avec AR' },
    { value: 'EMAIL_SECURISE', label: 'Courrier électronique sécurisé (PDF signé)' },
];

export default function DossierSortieModal({
    isOpen,
    onClose,
    idDossier = null,
    idEmploye = null,
    nomEmploye = '',
    dateDepartInitiale = null,
    motifInitial = 'DEMISSION',
    onSaved,
    onOpenStcModal,
}) {
    const toast = useToast();
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [delivrantCertificat, setDelivrantCertificat] = useState(false);
    const [simulating, setSimulating] = useState(false);
    const [sealing, setSealing] = useState(false);
    const [simulationResult, setSimulationResult] = useState(null);
    const [commentaireScellement, setCommentaireScellement] = useState('');
    const [errorMsg, setErrorMsg] = useState(null);
    const [dossier, setDossier] = useState(null);
    const [motifsSortie, setMotifsSortie] = useState([]);
    const [activeTab, setActiveTab] = useState('synthese'); // 'synthese' | 'checklist' | 'pret' | 'decompte' | 'offboarding'

    // Formulaire de saisie éditable
    const [dateDepart, setDateDepart] = useState('');
    const [motifRupture, setMotifRupture] = useState('DEMISSION');
    const [decisionPret, setDecisionPret] = useState('MAINTIEN_ECHEANCIER');
    const [justificationDecisionPret, setJustificationDecisionPret] = useState('');
    const [montantPretRetenu, setMontantPretRetenu] = useState(0);

    // Checklist offboarding
    const [offboarding, setOffboarding] = useState({
        restitutionBadge: false,
        restitutionMateriel: false,
        restitutionCles: false,
        entretienDepart: false,
        commentaires: '',
    });

    // Remise certificat
    const [dateDelivranceCertificat, setDateDelivranceCertificat] = useState(getTodayDateString());
    const [modeRemiseCertificat, setModeRemiseCertificat] = useState('MAIN_PROPRE');

    // Chargement des motifs de rupture légaux
    useEffect(() => {
        if (!isOpen) return;
        contratService.getMotifsSortie()
            .then(data => setMotifsSortie(optionsMotifsSortie(data)))
            .catch(() => {});
    }, [isOpen]);

    // Chargement du dossier
    const chargerDossier = useCallback(async () => {
        if (!isOpen) return;
        setLoading(true);
        setErrorMsg(null);
        try {
            let data;
            if (idDossier) {
                data = await dossierSortieService.obtenirDossier(idDossier);
            } else if (idEmploye) {
                const dateRef = dateDepartInitiale || getTodayDateString();
                data = await dossierSortieService.obtenirDossierEmploye(idEmploye, dateRef, motifInitial);
            }
            if (data) {
                setDossier(data);
                setDateDepart(data.dateDepart || dateDepartInitiale || getTodayDateString());
                setMotifRupture(data.motifRupture || motifInitial || 'DEMISSION');
                setDecisionPret(data.decisionPret || 'MAINTIEN_ECHEANCIER');
                setJustificationDecisionPret(data.justificationDecisionPret || '');
                setMontantPretRetenu(data.montantPretRetenu || 0);
                if (data.checklistOffboarding) {
                    setOffboarding(prev => ({
                        ...prev,
                        ...data.checklistOffboarding
                    }));
                }
                if (data.snapshotSimulation) {
                    setSimulationResult({
                        referenceSimulation: data.referenceSimulation,
                        version: data.version,
                        decompte: data.snapshotSimulation,
                    });
                }
            }
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Impossible de charger les données du dossier de sortie.'));
        } finally {
            setLoading(false);
        }
    }, [isOpen, idDossier, idEmploye, dateDepartInitiale, motifInitial]);

    useEffect(() => {
        if (isOpen) {
            chargerDossier();
        } else {
            setDossier(null);
            setErrorMsg(null);
            setSimulationResult(null);
            setActiveTab('synthese');
        }
    }, [isOpen, chargerDossier]);

    // Sauvegarde du dossier préparatoire
    const handleSauvegarder = async () => {
        if (!dossier?.idEmploye) return;
        setSaving(true);
        setErrorMsg(null);
        try {
            const payload = {
                idDossierSortie: dossier.idDossierSortie || idDossier || null,
                idEmploye: dossier.idEmploye,
                idContrat: dossier.idContrat || null,
                version: dossier.version || 1,
                dateDepart: dateDepart,
                motifRupture: motifRupture,
                statutDossier: dossier.statutDossier || 'EN_PREPARATION',
                decisionPret: (dossier.capitalRestantPrets && Number(dossier.capitalRestantPrets) > 0) ? decisionPret : null,
                justificationDecisionPret: justificationDecisionPret || null,
                montantPretRetenu: montantPretRetenu ? Number(montantPretRetenu) : 0,
                checklistOffboarding: offboarding,
                donneesPreparation: {
                    dateDepart,
                    motifRupture,
                    offboarding,
                }
            };
            const updated = await dossierSortieService.sauvegarderDossier(payload);
            setDossier(updated);
            toast.show('Dossier préparatoire de sortie enregistré avec succès.', 'success');
            if (onSaved) onSaved(updated);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors de la sauvegarde du dossier de sortie.'));
        } finally {
            setSaving(false);
        }
    };

    // Calcul de la simulation STC
    const handleSimulerStc = async () => {
        if (!dossier?.idDossierSortie) {
            toast.show('Veuillez d’abord sauvegarder le dossier avant de simuler le STC.', 'warning');
            return;
        }
        setSimulating(true);
        setErrorMsg(null);
        try {
            const res = await dossierSortieService.simulerStc(dossier.idDossierSortie);
            setSimulationResult(res);
            setDossier(prev => ({
                ...prev,
                version: res.version,
                referenceSimulation: res.referenceSimulation,
                snapshotSimulation: res.decompte,
                statutDossier: 'PRET_A_VALIDER'
            }));
            setActiveTab('decompte');
            toast.show('Simulation STC calculée. Référence vérifiable générée.', 'success');
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors du calcul de la simulation STC.'));
        } finally {
            setSimulating(false);
        }
    };

    // Scellement transactionnel du STC
    const handleScellerStc = async () => {
        if (!dossier?.idDossierSortie) return;
        const refSim = simulationResult?.referenceSimulation || dossier?.referenceSimulation;
        if (!refSim) {
            toast.show('Une simulation préalable est requise avant le scellement définitif.', 'warning');
            return;
        }
        setSealing(true);
        setErrorMsg(null);
        try {
            const payload = {
                referenceSimulation: refSim,
                version: dossier.version || simulationResult?.version || 1,
                commentaire: commentaireScellement || null
            };
            const updated = await dossierSortieService.scellerStc(dossier.idDossierSortie, payload);
            setDossier(updated);
            toast.show('STC validé et scellé avec succès. Les résultats financiers sont verrouillés.', 'success');
            if (onSaved) onSaved(updated);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors du scellement du STC.'));
        } finally {
            setSealing(false);
        }
    };

    // Enregistrement de la délivrance du certificat
    const handleDelivrerCertificat = async () => {
        if (!dossier?.idDossierSortie) {
            toast.show('Veuillez d’abord sauvegarder le dossier avant de formaliser le certificat.', 'warning');
            return;
        }
        setDelivrantCertificat(true);
        try {
            const updated = await dossierSortieService.enregistrerDelivranceCertificat(dossier.idDossierSortie, {
                dateDelivrance: dateDelivranceCertificat,
                modeRemise: modeRemiseCertificat
            });
            setDossier(updated);
            toast.show('Délivrance du certificat de travail enregistrée (Art. 50 Loi 2024-004).', 'success');
            if (onSaved) onSaved(updated);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Impossible d’enregistrer la remise du certificat de travail.'));
        } finally {
            setDelivrantCertificat(false);
        }
    };

    // Helpers d'affichage
    const getBadgeSeverity = (niveau) => {
        switch (niveau) {
            case 'BLOQUANT': return 'danger';
            case 'AVERTISSEMENT': return 'warning';
            default: return 'info';
        }
    };

    const getBadgeStatut = (statut) => {
        switch (statut) {
            case 'CONFORME': return { label: 'Conforme', variant: 'success', icon: CheckCircle2 };
            case 'A_VERIFIER': return { label: 'À vérifier', variant: 'warning', icon: AlertTriangle };
            case 'NON_CONFORME': return { label: 'Non conforme', variant: 'danger', icon: XCircle };
            default: return { label: statut, variant: 'default', icon: Info };
        }
    };

    const getBadgeSource = (source) => {
        switch (source) {
            case 'LEGAL_MADAGASCAR_2024': return { label: 'Loi 2024-004 (Légal)', color: 'bg-purple-100 text-purple-800 border-purple-200' };
            case 'REGLE_INTERNE_GALAXAM': return { label: 'Procédure GALAXAM', color: 'bg-blue-100 text-blue-800 border-blue-200' };
            case 'TECHNIQUE_SI': return { label: 'Cohérence Système', color: 'bg-gray-100 text-gray-800 border-gray-200' };
            default: return { label: source, color: 'bg-gray-100 text-gray-700' };
        }
    };

    const hasLoans = dossier?.capitalRestantPrets && Number(dossier.capitalRestantPrets) > 0;
    const nbBloquants = dossier?.checklist?.filter(c => c.niveau === 'BLOQUANT' && c.statut === 'NON_CONFORME').length || 0;
    const nbAvertissements = dossier?.checklist?.filter(c => c.niveau === 'AVERTISSEMENT' && c.statut !== 'CONFORME').length || 0;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Dossier de sortie · ${dossier?.nomComplet || nomEmploye || 'Salarié'}`}
            size="xl"
            footer={
                <div className="flex flex-wrap items-center justify-between gap-3 w-full">
                    <div className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)]">
                        {dossier?.statutDossier === 'VALIDE' || dossier?.statutDossier === 'CLOTURE' ? (
                            <span className="flex items-center gap-1.5 text-emerald-700 font-semibold">
                                <ShieldCheck size={16} /> STC Scellé & Verrouillé #{dossier.idStc || ''}
                            </span>
                        ) : dossier?.pretAValider ? (
                            <span className="flex items-center gap-1.5 text-emerald-700 font-semibold">
                                <BadgeCheck size={16} /> Checklist conforme · Prêt pour calcul STC
                            </span>
                        ) : (
                            <span className="flex items-center gap-1.5 text-amber-700 font-medium">
                                <AlertTriangle size={16} /> {nbBloquants} point(s) bloquant(s) à résoudre
                            </span>
                        )}
                    </div>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" onClick={onClose} disabled={saving || simulating || sealing}>
                            Fermer
                        </Button>
                        {dossier?.statutDossier !== 'VALIDE' && dossier?.statutDossier !== 'CLOTURE' && (
                            <>
                                <Button
                                    variant="secondary"
                                    onClick={handleSauvegarder}
                                    loading={saving}
                                    disabled={simulating || sealing}
                                    iconLeft={<Save size={15} />}
                                >
                                    Sauvegarder le dossier
                                </Button>
                                <Button
                                    variant="primary"
                                    onClick={handleSimulerStc}
                                    loading={simulating}
                                    disabled={saving || sealing || !dossier?.idDossierSortie}
                                    iconLeft={<Calculator size={15} />}
                                >
                                    {simulationResult || dossier?.referenceSimulation ? 'Recalculer la simulation' : 'Simuler le STC'}
                                </Button>
                            </>
                        )}
                        {(dossier?.statutDossier === 'VALIDE' || dossier?.statutDossier === 'CLOTURE') && onOpenStcModal && (
                            <Button
                                variant="secondary"
                                onClick={() => onOpenStcModal(dossier)}
                                iconRight={<ExternalLink size={15} />}
                            >
                                Consulter le reçu STC
                            </Button>
                        )}
                    </div>
                </div>
            }
        >
            <div className="space-y-5">
                {errorMsg && (
                    <Alert type="error" title="Erreur dossier de sortie" message={errorMsg} />
                )}

                {/* En-tête Salarié & Contrat */}
                <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)]">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="flex items-center gap-3">
                            <div className="w-11 h-11 bg-white border border-[var(--color-border-subtle)] flex items-center justify-center font-bold text-[var(--color-brand-600)] shadow-xs">
                                {dossier?.matricule || <User size={20} />}
                            </div>
                            <div>
                                <h3 className="font-bold text-base text-[var(--color-fg)]">
                                    {dossier?.nomComplet || nomEmploye || 'Chargement...'}
                                </h3>
                                <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-2 flex-wrap">
                                    <span>{dossier?.intitulePoste || 'Poste non renseigné'}</span>
                                    <span>·</span>
                                    <span>{dossier?.departement || 'Département'}</span>
                                    <span>·</span>
                                    <span>Site : <strong className="text-[var(--color-fg)]">{dossier?.site || dossier?.codeSite || 'Siège'}</strong></span>
                                </p>
                            </div>
                        </div>

                        <div className="flex items-center gap-3 flex-wrap">
                            <div className="text-right text-xs">
                                <span className="text-[var(--color-fg-muted)] block">Type contrat & Ancienneté</span>
                                <span className="font-semibold text-[var(--color-fg)]">
                                    {dossier?.typeContrat || 'CDI'} · {dossier?.ancienneteAnnees ?? 0} an(s) {dossier?.ancienneteMois ?? 0} mois
                                </span>
                            </div>
                            <Badge
                                variant={
                                    dossier?.statutDossier === 'CLOTURE' ? 'success' :
                                    dossier?.statutDossier === 'VALIDE' ? 'primary' :
                                    dossier?.statutDossier === 'PRET_A_VALIDER' ? 'info' : 'warning'
                                }
                                size="md"
                            >
                                {dossier?.statutDossier || 'EN_PREPARATION'}
                            </Badge>
                        </div>
                    </div>

                    {/* Paramètres de départ */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mt-4 pt-3 border-t border-[var(--color-border-subtle)]">
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Date effective de départ *
                            </label>
                            <Input
                                type="date"
                                value={dateDepart}
                                onChange={(e) => setDateDepart(e.target.value)}
                                disabled={loading || saving}
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Motif de rupture légal *
                            </label>
                            <SearchSelect
                                options={motifsSortie.length > 0 ? motifsSortie : [
                                    { value: 'DEMISSION', label: 'Démission du salarié' },
                                    { value: 'LICENCIEMENT_SIMPLE', label: 'Licenciement simple' },
                                    { value: 'FIN_CONTRAT', label: 'Fin de contrat (CDD)' },
                                    { value: 'RUPTURE_CONVENTIONNELLE', label: 'Rupture conventionnelle d’un commun accord' },
                                    { value: 'RETRAITE', label: 'Départ à la retraite' },
                                    { value: 'FAUTE_LOURDE', label: 'Licenciement pour faute lourde' },
                                ]}
                                value={motifRupture}
                                onChange={(val) => setMotifRupture(val)}
                                disabled={loading || saving}
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Salaire de base contractuel
                            </label>
                            <div className="h-[38px] px-3 bg-white border border-[var(--color-border-subtle)] flex items-center justify-between text-sm">
                                <span className="font-data font-semibold text-[var(--color-fg)]">
                                    {dossier?.salaireBase ? formatMoneyAr(dossier.salaireBase) : '–'}
                                </span>
                                <span className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.tauxHoraire ? `${formatMoneyAr(dossier.tauxHoraire)}/h` : ''}
                                </span>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Onglets du Dossier */}
                <div className="border-b border-[var(--color-border-subtle)] flex items-center gap-2 overflow-x-auto">
                    <button
                        type="button"
                        onClick={() => setActiveTab('synthese')}
                        className={`px-3.5 py-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 whitespace-nowrap ${
                            activeTab === 'synthese'
                                ? 'border-[var(--color-brand-600)] text-[var(--color-brand-600)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <FileSpreadsheet size={15} /> 1. Données sources & Synthèse
                    </button>
                    <button
                        type="button"
                        onClick={() => setActiveTab('checklist')}
                        className={`px-3.5 py-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 whitespace-nowrap ${
                            activeTab === 'checklist'
                                ? 'border-[var(--color-brand-600)] text-[var(--color-brand-600)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <ShieldAlert size={15} /> 2. Checklist de conformité
                        {nbBloquants > 0 && (
                            <span className="ml-1 px-1.5 py-0.2 bg-red-100 text-red-700 text-[10px] font-bold">
                                {nbBloquants}
                            </span>
                        )}
                    </button>
                    <button
                        type="button"
                        onClick={() => setActiveTab('pret')}
                        className={`px-3.5 py-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 whitespace-nowrap ${
                            activeTab === 'pret'
                                ? 'border-[var(--color-brand-600)] text-[var(--color-brand-600)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <Banknote size={15} /> 3. Prêts & Règlements
                        {hasLoans && (
                            <span className="ml-1 px-1.5 py-0.2 bg-amber-100 text-amber-800 text-[10px] font-bold">
                                En cours
                            </span>
                        )}
                    </button>
                    <button
                        type="button"
                        onClick={() => setActiveTab('decompte')}
                        className={`px-3.5 py-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 whitespace-nowrap ${
                            activeTab === 'decompte'
                                ? 'border-[var(--color-brand-600)] text-[var(--color-brand-600)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <Calculator size={15} /> 4. Décompte & Scellement STC
                        {(simulationResult || dossier?.referenceSimulation) && (
                            <span className="ml-1 px-1.5 py-0.2 bg-emerald-100 text-emerald-800 text-[10px] font-bold">
                                Simulé
                            </span>
                        )}
                    </button>
                    <button
                        type="button"
                        onClick={() => setActiveTab('offboarding')}
                        className={`px-3.5 py-2.5 text-xs font-semibold border-b-2 transition-colors flex items-center gap-1.5 whitespace-nowrap ${
                            activeTab === 'offboarding'
                                ? 'border-[var(--color-brand-600)] text-[var(--color-brand-600)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <Award size={15} /> 5. Offboarding & Certificat (Art. 50)
                    </button>
                </div>

                {/* CONTENU ONGLET 1 : Données sources en lecture */}
                {activeTab === 'synthese' && (
                    <div className="space-y-4">
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                            {/* Pointage */}
                            <div className="p-3.5 bg-white border border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-[var(--color-brand-700)]">
                                        <Clock size={15} /> Pointage du mois
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-gray-500">
                                        {dossier?.sourcePointage || 'Pointage réel'}
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <span className="text-xl font-bold font-data text-[var(--color-fg)]">
                                        {dossier?.joursPointageReels ?? '0'} j
                                    </span>
                                    <span className="text-xs text-[var(--color-fg-muted)] ml-1">
                                        / {dossier?.joursSousContrat ?? 30} j sous contrat
                                    </span>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] flex justify-between border-t border-[var(--color-border-subtle)] pt-2">
                                    <span>Volume horaire :</span>
                                    <strong className="font-data text-[var(--color-fg)]">{dossier?.totalHeuresPointage ?? 0} h</strong>
                                </div>
                                <p className="text-[11px] text-[var(--color-fg-muted)] italic">
                                    {dossier?.pointageGele ? 'Snapshot de paie gelé.' : 'Lecture directe temps réel sans verrouiller le cycle.'}
                                </p>
                            </div>

                            {/* Congés payés */}
                            <div className="p-3.5 bg-white border border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-emerald-700">
                                        <CalendarCheck size={15} /> Congés payés (FIFO)
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-gray-500">
                                        Solde acquis
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <span className="text-xl font-bold font-data text-emerald-700">
                                        {dossier?.soldeCongesRestant ?? '0'} j
                                    </span>
                                    <span className="text-xs text-[var(--color-fg-muted)] ml-1">
                                        reliquat restant
                                    </span>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] flex justify-between border-t border-[var(--color-border-subtle)] pt-2">
                                    <span>ICCP estimée :</span>
                                    <strong className="font-data text-emerald-700">
                                        {dossier?.iccpEstimee ? formatMoneyAr(dossier.iccpEstimee) : '0 Ar'}
                                    </strong>
                                </div>
                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.demandesCongesEnAttente > 0 ? (
                                        <span className="text-amber-700 font-medium">
                                            {dossier.demandesCongesEnAttente} demande(s) en attente de validation
                                        </span>
                                    ) : 'Aucune demande en attente.'}
                                </p>
                            </div>

                            {/* Paie mensuelle & Avances */}
                            <div className="p-3.5 bg-white border border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-blue-700">
                                        <FileText size={15} /> Bulletin du mois
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-gray-500">
                                        {dossier?.statutBulletinMensuel || 'NON GÉNÉRÉ'}
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <span className="text-xl font-bold font-data text-[var(--color-fg)]">
                                        {dossier?.netBulletinMensuel ? formatMoneyAr(dossier.netBulletinMensuel) : '0 Ar'}
                                    </span>
                                    <span className="text-xs text-[var(--color-fg-muted)] ml-1">Net</span>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] flex justify-between border-t border-[var(--color-border-subtle)] pt-2">
                                    <span>Déjà payé :</span>
                                    <strong className="font-data text-[var(--color-fg)]">
                                        {dossier?.montantBulletinDejaPaye ? formatMoneyAr(dossier.montantBulletinDejaPaye) : '0 Ar'}
                                    </strong>
                                </div>
                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.soldeBulletinARegler ? (
                                        <span>Reste à payer : <strong className="font-data">{formatMoneyAr(dossier.soldeBulletinARegler)}</strong></span>
                                    ) : 'Inclus ou régularisé dans le STC.'}
                                </p>
                            </div>
                        </div>

                        {/* STC scellé si existant */}
                        {dossier?.idStc && (
                            <div className="p-4 bg-emerald-50 border border-emerald-200 text-emerald-950 flex flex-wrap items-center justify-between gap-4">
                                <div className="flex items-center gap-3">
                                    <BadgeCheck size={24} className="text-emerald-700 shrink-0" />
                                    <div>
                                        <h4 className="font-bold text-sm">STC scellé & validé : N° {dossier.numeroRecu}</h4>
                                        <p className="text-xs text-emerald-800">
                                            Montant net final : <strong className="font-data">{formatMoneyAr(dossier.netAPayerStc)}</strong>
                                            {dossier.dateReglement && ` · Réglé le ${fmtDate(dossier.dateReglement)} (${dossier.referenceReglement || 'Comptabilisé'})`}
                                        </p>
                                    </div>
                                </div>
                                <Badge variant="success" size="md">
                                    Régularisé
                                </Badge>
                            </div>
                        )}
                    </div>
                )}

                {/* CONTENU ONGLET 2 : Checklist dynamique */}
                {activeTab === 'checklist' && (
                    <div className="space-y-3">
                        <div className="flex items-center justify-between text-xs text-[var(--color-fg-muted)] px-1">
                            <span>Évaluation automatique en temps réel des règles légales et internes GALAXAM.</span>
                            <span className="font-medium text-[var(--color-fg)]">
                                {dossier?.checklist?.filter(c => c.statut === 'CONFORME').length || 0} / {dossier?.checklist?.length || 0} conformes
                            </span>
                        </div>

                        <div className="space-y-2 max-h-[420px] overflow-y-auto pr-1">
                            {dossier?.checklist?.map((item, idx) => {
                                const st = getBadgeStatut(item.statut);
                                const src = getBadgeSource(item.sourceNorme);
                                const IconComp = st.icon;

                                return (
                                    <div
                                        key={item.code || idx}
                                        className={`p-3 border flex flex-col sm:flex-row sm:items-start justify-between gap-3 ${
                                            item.statut === 'NON_CONFORME' && item.niveau === 'BLOQUANT'
                                                ? 'bg-red-50/70 border-red-200'
                                                : item.statut !== 'CONFORME'
                                                    ? 'bg-amber-50/70 border-amber-200'
                                                    : 'bg-white border-[var(--color-border-subtle)]'
                                        }`}
                                    >
                                        <div className="flex items-start gap-2.5 min-w-0">
                                            <IconComp size={18} className={`shrink-0 mt-0.5 ${
                                                st.variant === 'danger' ? 'text-red-600' :
                                                st.variant === 'warning' ? 'text-amber-600' : 'text-emerald-600'
                                            }`} />
                                            <div>
                                                <div className="flex items-center gap-2 flex-wrap mb-1">
                                                    <h4 className="text-xs font-bold text-[var(--color-fg)]">
                                                        {item.titre}
                                                    </h4>
                                                    <span className={`text-[10px] font-semibold px-2 py-0.5 border ${src.color}`}>
                                                        {src.label}
                                                    </span>
                                                    <Badge variant={getBadgeSeverity(item.niveau)} size="xs">
                                                        {item.niveau}
                                                    </Badge>
                                                </div>
                                                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                                    {item.message}
                                                </p>
                                                {item.actionRecommandee && (
                                                    <p className="text-[11px] text-[var(--color-brand-700)] font-medium mt-1">
                                                        → Action recommandée : {item.actionRecommandee}
                                                    </p>
                                                )}
                                            </div>
                                        </div>

                                        <div className="flex sm:flex-col items-end justify-between sm:justify-start gap-2 shrink-0">
                                            <Badge variant={st.variant} size="sm">
                                                {st.label}
                                            </Badge>
                                            {item.lienModule && (
                                                <a
                                                    href={item.lienModule}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline inline-flex items-center gap-1"
                                                >
                                                    Ouvrir <ExternalLink size={12} />
                                                </a>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 3 : Prêts & Règlements */}
                {activeTab === 'pret' && (
                    <div className="space-y-4">
                        <div className="p-4 bg-white border border-[var(--color-border-subtle)] space-y-3">
                            <h4 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Banknote size={16} className="text-[var(--color-brand-600)]" />
                                État des prêts & avances en cours
                            </h4>

                            {hasLoans ? (
                                <div className="space-y-3">
                                    <div className="p-3 bg-amber-50 border border-amber-200 text-amber-900 text-xs rounded-none flex items-start gap-2.5">
                                        <AlertTriangle size={18} className="text-amber-700 shrink-0 mt-0.5" />
                                        <div>
                                            <p className="font-bold">Prêt d'entreprise en cours de remboursement</p>
                                            <p className="mt-0.5">
                                                Capital restant dû au départ : <strong className="font-data text-sm text-amber-950">{formatMoneyAr(dossier.capitalRestantPrets)}</strong>.
                                            </p>
                                            <p className="text-[11px] mt-1 text-amber-800">
                                                Règle juridique impérative : le solde d'un prêt ne peut pas être déduit d'office sans décision documentée et respect de la quotité saisissable légale.
                                            </p>
                                        </div>
                                    </div>

                                    <div className="space-y-3 pt-2">
                                        <div>
                                            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                                Décision de gestion du prêt au départ du salarié *
                                            </label>
                                            <SearchSelect
                                                options={OPTIONS_DECISION_PRET}
                                                value={decisionPret}
                                                onChange={(val) => setDecisionPret(val)}
                                                disabled={saving}
                                            />
                                        </div>

                                        {decisionPret === 'RETENUE_STC_AUTORISEE' && (
                                            <div>
                                                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                                    Montant de retenue convenu sur le STC (dans la limite de la quotité légale) *
                                                </label>
                                                <Input
                                                    type="number"
                                                    value={montantPretRetenu}
                                                    onChange={(e) => setMontantPretRetenu(e.target.value)}
                                                    placeholder="Montant en Ariary"
                                                    disabled={saving}
                                                />
                                            </div>
                                        )}

                                        <div>
                                            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                                Justification écrite & traçabilité de la décision *
                                            </label>
                                            <textarea
                                                className="w-full text-xs p-2.5 border border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)] outline-none min-h-[70px]"
                                                placeholder="Indiquez les accords avec le collaborateur, les références de chèque de remboursement ou les termes de maintien..."
                                                value={justificationDecisionPret}
                                                onChange={(e) => setJustificationDecisionPret(e.target.value)}
                                                disabled={saving}
                                            />
                                        </div>
                                    </div>
                                </div>
                            ) : (
                                <div className="p-4 bg-gray-50 border border-gray-200 text-gray-700 text-xs flex items-center gap-2">
                                    <CheckCircle2 size={16} className="text-emerald-600" />
                                    <span>Aucun prêt d'entreprise en cours pour ce collaborateur.</span>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 4 : Décompte & Scellement STC */}
                {activeTab === 'decompte' && (
                    <div className="space-y-4">
                        {/* En-tête de la simulation & Référence d'intégrité */}
                        <div className="p-4 bg-white border border-[var(--color-border-subtle)] space-y-3">
                            <div className="flex flex-wrap items-center justify-between gap-3">
                                <div className="flex items-center gap-2">
                                    <Calculator size={18} className="text-[var(--color-brand-600)]" />
                                    <div>
                                        <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                            Décompte financier Solde de Tout Compte (STC)
                                        </h4>
                                        <p className="text-xs text-[var(--color-fg-muted)]">
                                            Calcul officiel réutilisant le moteur financier <code className="text-xs bg-gray-100 px-1 py-0.5">StcService</code>.
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {dossier?.statutDossier === 'VALIDE' || dossier?.statutDossier === 'CLOTURE' ? (
                                        <Badge variant="success" size="md">
                                            <ShieldCheck size={14} className="inline mr-1" /> Scellé & Verrouillé
                                        </Badge>
                                    ) : (
                                        <Button
                                            variant="secondary"
                                            size="sm"
                                            onClick={handleSimulerStc}
                                            loading={simulating}
                                            disabled={sealing || !dossier?.idDossierSortie}
                                            iconLeft={<Calculator size={14} />}
                                        >
                                            {simulationResult || dossier?.referenceSimulation ? 'Recalculer la simulation' : 'Calculer la simulation STC'}
                                        </Button>
                                    )}
                                </div>
                            </div>

                            {/* Référence vérifiable */}
                            {(simulationResult?.referenceSimulation || dossier?.referenceSimulation) ? (
                                <div className="p-3 bg-slate-50 border border-slate-200 text-xs flex flex-wrap items-center justify-between gap-2">
                                    <div className="flex items-center gap-2">
                                        <Lock size={15} className="text-slate-600" />
                                        <div>
                                            <span className="font-semibold text-slate-800">Référence de calcul vérifiable : </span>
                                            <code className="font-mono bg-white px-2 py-0.5 border border-slate-300 text-slate-900 font-bold select-all">
                                                {simulationResult?.referenceSimulation || dossier?.referenceSimulation}
                                            </code>
                                        </div>
                                    </div>
                                    <div className="text-slate-500 font-mono text-[11px]">
                                        Version dossier : <strong>v{dossier?.version || simulationResult?.version || 1}</strong>
                                    </div>
                                </div>
                            ) : (
                                <div className="p-4 bg-amber-50 border border-amber-200 text-xs text-amber-900 flex items-center gap-2">
                                    <Info size={16} className="text-amber-700 shrink-0" />
                                    <span>Aucune simulation active. Cliquez sur <strong>« Calculer la simulation STC »</strong> pour générer l'empreinte vérifiable et le décompte chiffré.</span>
                                </div>
                            )}
                        </div>

                        {/* Détail du Décompte */}
                        {(() => {
                            const decompte = simulationResult?.decompte || dossier?.snapshotSimulation;
                            if (!decompte) return null;

                            return (
                                <div className="space-y-4">
                                    {/* Grille des montants */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                        {/* Bloc 1: Droits bruts */}
                                        <div className="p-3.5 bg-white border border-[var(--color-border-subtle)] space-y-2">
                                            <h5 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-700)] border-b pb-1.5">
                                                1. Droits Bruts
                                            </h5>
                                            <div className="space-y-1.5 text-xs">
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Salaire prorata :</span>
                                                    <span className="font-data font-semibold">{formatMoneyAr(decompte.salaireProrata || 0)}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">ICCP ({decompte.soldeCongesRestant ?? 0} j) :</span>
                                                    <span className="font-data font-semibold">{formatMoneyAr(decompte.indemniteCongesPayes || 0)}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Préavis ({decompte.moisPreavis ?? 0} m) :</span>
                                                    <span className="font-data font-semibold">{formatMoneyAr(decompte.indemnitePreavis || 0)}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Indemnité rupture :</span>
                                                    <span className="font-data font-semibold">{formatMoneyAr(decompte.indemniteLicenciement || 0)}</span>
                                                </div>
                                                <div className="pt-2 border-t flex justify-between font-bold text-[var(--color-fg)]">
                                                    <span>Total Brut STC :</span>
                                                    <span className="font-data text-emerald-700">{formatMoneyAr(decompte.totalBrutStc || 0)}</span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Bloc 2: Cotisations & Fiscalité */}
                                        <div className="p-3.5 bg-white border border-[var(--color-border-subtle)] space-y-2">
                                            <h5 className="text-xs font-bold uppercase tracking-wider text-purple-800 border-b pb-1.5">
                                                2. Cotisations & Impôt
                                            </h5>
                                            <div className="space-y-1.5 text-xs">
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">CNaPS (1%) :</span>
                                                    <span className="font-data font-semibold text-red-600">-{formatMoneyAr(decompte.cotisationCnaps || 0)}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Santé / OSTIE (1%) :</span>
                                                    <span className="font-data font-semibold text-red-600">-{formatMoneyAr(decompte.cotisationSante || 0)}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Net Imposable :</span>
                                                    <span className="font-data font-semibold">{formatMoneyAr(decompte.netImposable || 0)}</span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">IRSA net :</span>
                                                    <span className="font-data font-semibold text-red-600">-{formatMoneyAr(decompte.irsaNet || 0)}</span>
                                                </div>
                                                <div className="pt-2 border-t flex justify-between font-bold text-[var(--color-fg)]">
                                                    <span>Total Déductions Légales :</span>
                                                    <span className="font-data text-red-700">
                                                        -{formatMoneyAr((decompte.totalCotisationsSociales || 0) + (decompte.irsaNet || 0))}
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Bloc 3: Déductions & Net à payer */}
                                        <div className="p-3.5 bg-white border border-[var(--color-border-subtle)] space-y-2">
                                            <h5 className="text-xs font-bold uppercase tracking-wider text-amber-800 border-b pb-1.5">
                                                3. Déductions & Net
                                            </h5>
                                            <div className="space-y-1.5 text-xs">
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Avances déduites :</span>
                                                    <span className="font-data font-semibold text-red-600">
                                                        {Number(decompte.soldeAvancesDeduit || 0) > 0 ? `-${formatMoneyAr(decompte.soldeAvancesDeduit)}` : '0 Ar'}
                                                    </span>
                                                </div>
                                                <div className="flex justify-between">
                                                    <span className="text-[var(--color-fg-muted)]">Prêts déduits (Phase 2) :</span>
                                                    <span className="font-data font-semibold text-gray-500">
                                                        0 Ar (Maintien séparé)
                                                    </span>
                                                </div>
                                                <div className="p-2.5 mt-3 bg-emerald-50 border border-emerald-200">
                                                    <span className="text-[11px] font-bold uppercase tracking-wider text-emerald-900 block">
                                                        Net STC à Payer
                                                    </span>
                                                    <span className="font-data text-xl font-bold text-emerald-700 block mt-0.5">
                                                        {formatMoneyAr(decompte.netAPayerStc || 0)}
                                                    </span>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Tableau complet des rubriques */}
                                    {decompte.lignes && decompte.lignes.length > 0 && (
                                        <div className="border border-[var(--color-border-subtle)] bg-white overflow-x-auto">
                                            <table className="w-full text-xs text-left">
                                                <thead className="bg-[var(--color-bg-subtle)] text-[11px] uppercase font-semibold text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)]">
                                                    <tr>
                                                        <th className="px-3 py-2">Code</th>
                                                        <th className="px-3 py-2">Rubrique</th>
                                                        <th className="px-3 py-2 text-right">Base</th>
                                                        <th className="px-3 py-2 text-right">Taux</th>
                                                        <th className="px-3 py-2 text-right">Montant</th>
                                                    </tr>
                                                </thead>
                                                <tbody className="divide-y divide-[var(--color-border-subtle)]">
                                                    {decompte.lignes.map((l, idx) => (
                                                        <tr key={idx} className={l.sens === 'RETENUE' ? 'bg-red-50/30' : ''}>
                                                            <td className="px-3 py-2 font-mono text-[11px] text-gray-600">{l.code}</td>
                                                            <td className="px-3 py-2 font-medium">{l.libelle}</td>
                                                            <td className="px-3 py-2 text-right font-data text-gray-600">{l.base ? formatMoneyAr(l.base) : '–'}</td>
                                                            <td className="px-3 py-2 text-right font-data text-gray-600">{l.taux ? `${l.taux}%` : '–'}</td>
                                                            <td className={`px-3 py-2 text-right font-data font-semibold ${l.sens === 'RETENUE' ? 'text-red-700' : 'text-emerald-700'}`}>
                                                                {l.sens === 'RETENUE' ? `-${formatMoneyAr(l.montant)}` : formatMoneyAr(l.montant)}
                                                            </td>
                                                        </tr>
                                                    ))}
                                                </tbody>
                                            </table>
                                        </div>
                                    )}

                                    {/* Zone de scellement transactionnel si non scellé */}
                                    {dossier?.statutDossier !== 'VALIDE' && dossier?.statutDossier !== 'CLOTURE' ? (
                                        <div className="p-4 bg-emerald-50/70 border-2 border-emerald-300 space-y-3">
                                            <div className="flex items-start gap-3">
                                                <ShieldCheck size={22} className="text-emerald-700 shrink-0 mt-0.5" />
                                                <div className="space-y-1">
                                                    <h5 className="text-sm font-bold text-emerald-950">
                                                        Confirmation et scellement transactionnel du STC
                                                    </h5>
                                                    <p className="text-xs text-emerald-900 leading-relaxed">
                                                        Le scellement effectue un contrôle de fraîcheur transactionnel sur les données de calcul (pointage, congés, contrat).
                                                        Le résultat net affiché de <strong className="font-data underline">{formatMoneyAr(decompte.netAPayerStc || 0)}</strong> sera scellé dans le registre financier officiel <code>paie_stc</code>.
                                                    </p>
                                                </div>
                                            </div>

                                            {nbBloquants > 0 && (
                                                <Alert
                                                    type="error"
                                                    title="Scellement bloqué par la checklist"
                                                    message={`Le dossier comporte ${nbBloquants} point(s) bloquant(s). Veuillez les résoudre avant de sceller.`}
                                                />
                                            )}

                                            <div>
                                                <label className="block text-[11px] font-semibold text-emerald-950 mb-1">
                                                    Commentaire ou référence de validation (optionnel)
                                                </label>
                                                <Input
                                                    type="text"
                                                    placeholder="Ex: Validation après vérification des heures et accord de sortie..."
                                                    value={commentaireScellement}
                                                    onChange={(e) => setCommentaireScellement(e.target.value)}
                                                    disabled={sealing}
                                                />
                                            </div>

                                            <div className="flex items-center justify-end gap-3 pt-2">
                                                <Button
                                                    variant="primary"
                                                    onClick={handleScellerStc}
                                                    loading={sealing}
                                                    disabled={nbBloquants > 0 || !(simulationResult?.referenceSimulation || dossier?.referenceSimulation)}
                                                    iconLeft={<ShieldCheck size={16} />}
                                                >
                                                    Confirmer et sceller définitivement le STC
                                                </Button>
                                            </div>
                                        </div>
                                    ) : (
                                        <div className="p-4 bg-slate-100 border border-slate-300 flex items-center justify-between">
                                            <div className="flex items-center gap-2.5">
                                                <Lock size={18} className="text-slate-700" />
                                                <div>
                                                    <p className="text-xs font-bold text-slate-800">
                                                        STC scellé et verrouillé · N° STC : #{dossier.idStc}
                                                    </p>
                                                    <p className="text-[11px] text-slate-600">
                                                        Les résultats financiers sont immuables. Le reçu financier officiel est disponible.
                                                    </p>
                                                </div>
                                            </div>
                                            {onOpenStcModal && (
                                                <Button
                                                    variant="secondary"
                                                    size="sm"
                                                    onClick={() => onOpenStcModal(dossier)}
                                                    iconRight={<ExternalLink size={13} />}
                                                >
                                                    Afficher le reçu STC
                                                </Button>
                                            )}
                                        </div>
                                    )}
                                </div>
                            );
                        })()}
                    </div>
                )}

                {/* CONTENU ONGLET 4 : Offboarding & Certificat de travail */}
                {activeTab === 'offboarding' && (
                    <div className="space-y-4">
                        {/* Certificat de travail légal */}
                        <div className="p-4 bg-white border border-[var(--color-border-subtle)] space-y-3">
                            <div className="flex items-center justify-between">
                                <h4 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
                                    <Award size={16} className="text-purple-700" />
                                    Certificat de travail (Loi 2024-004, Art. 50)
                                </h4>
                                {dossier?.certificatDelivre ? (
                                    <Badge variant="success" size="sm">
                                        Délivré le {fmtDate(dossier.dateDelivranceCertificat)}
                                    </Badge>
                                ) : (
                                    <Badge variant="warning" size="sm">
                                        À délivrer à la cessation
                                    </Badge>
                                )}
                            </div>

                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Le certificat de travail est exigible à la cessation effective du contrat. Sa délivrance est <strong className="text-[var(--color-fg)]">indépendante</strong> de la validation financière ou du règlement du STC.
                            </p>

                            {!dossier?.certificatDelivre ? (
                                <div className="bg-purple-50/60 p-3.5 border border-purple-200 space-y-3">
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                        <div>
                                            <label className="block text-[11px] font-semibold text-purple-900 mb-1">
                                                Date effective de remise
                                            </label>
                                            <Input
                                                type="date"
                                                value={dateDelivranceCertificat}
                                                onChange={(e) => setDateDelivranceCertificat(e.target.value)}
                                            />
                                        </div>
                                        <div>
                                            <label className="block text-[11px] font-semibold text-purple-900 mb-1">
                                                Mode de remise
                                            </label>
                                            <SearchSelect
                                                options={MODES_REMISE_CERTIFICAT}
                                                value={modeRemiseCertificat}
                                                onChange={(val) => setModeRemiseCertificat(val)}
                                            />
                                        </div>
                                    </div>

                                    <Button
                                        variant="primary"
                                        size="sm"
                                        onClick={handleDelivrerCertificat}
                                        loading={delivrantCertificat}
                                        iconLeft={<FileCheck size={14} />}
                                    >
                                        Enregistrer la remise du certificat de travail
                                    </Button>
                                </div>
                            ) : (
                                <div className="p-3 bg-purple-50 border border-purple-200 text-xs text-purple-950 flex items-center justify-between">
                                    <div>
                                        <p className="font-semibold">Certificat de travail délivré au salarié</p>
                                        <p className="text-[11px] text-purple-800">
                                            Remis le {fmtDate(dossier.dateDelivranceCertificat)} via {dossier.modeRemiseCertificat || 'Remise en main propre'}.
                                        </p>
                                    </div>
                                    <BadgeCheck size={20} className="text-purple-700" />
                                </div>
                            )}
                        </div>

                        {/* Formalités matérielles d'offboarding */}
                        <div className="p-4 bg-white border border-[var(--color-border-subtle)] space-y-3">
                            <h4 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Key size={16} className="text-[var(--color-brand-600)]" />
                                Formalités de restitution du matériel GALAXAM
                            </h4>

                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                                <label className="flex items-center gap-2.5 p-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] cursor-pointer text-xs">
                                    <input
                                        type="checkbox"
                                        checked={offboarding.restitutionBadge}
                                        onChange={(e) => setOffboarding(prev => ({ ...prev, restitutionBadge: e.target.checked }))}
                                        className="rounded-none text-[var(--color-brand-600)]"
                                    />
                                    <span>Badge d'accès aux locaux restitué</span>
                                </label>
                                <label className="flex items-center gap-2.5 p-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] cursor-pointer text-xs">
                                    <input
                                        type="checkbox"
                                        checked={offboarding.restitutionMateriel}
                                        onChange={(e) => setOffboarding(prev => ({ ...prev, restitutionMateriel: e.target.checked }))}
                                        className="rounded-none text-[var(--color-brand-600)]"
                                    />
                                    <span>Ordinateur / Téléphone restitués</span>
                                </label>
                                <label className="flex items-center gap-2.5 p-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] cursor-pointer text-xs">
                                    <input
                                        type="checkbox"
                                        checked={offboarding.restitutionCles}
                                        onChange={(e) => setOffboarding(prev => ({ ...prev, restitutionCles: e.target.checked }))}
                                        className="rounded-none text-[var(--color-brand-600)]"
                                    />
                                    <span>Clés des locaux / Véhicule restitués</span>
                                </label>
                                <label className="flex items-center gap-2.5 p-2.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] cursor-pointer text-xs">
                                    <input
                                        type="checkbox"
                                        checked={offboarding.entretienDepart}
                                        onChange={(e) => setOffboarding(prev => ({ ...prev, entretienDepart: e.target.checked }))}
                                        className="rounded-none text-[var(--color-brand-600)]"
                                    />
                                    <span>Entretien de sortie RH réalisé</span>
                                </label>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Modal>
    );
}
