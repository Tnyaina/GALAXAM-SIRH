import { useState, useEffect, useCallback, useRef } from 'react';
import { 
    SlidersHorizontal, 
    Plus, 
    Trash2, 
    TrendingUp, 
    TrendingDown, 
    Search,
    Eye,
    Info,
    AlertCircle,
    CheckCircle2
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Select from '@/components/ui/forms/Select';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import { isManualPayrollVariable, isManualPayrollVariableType } from '@/features/payroll/utils/variableTypes';
import { useAuth } from '@/context/AuthContext';
import { useLocation, useNavigate } from 'react-router-dom';

const parseAriaryAmount = (value) => {
    const normalized = String(value ?? '').trim().replace(/[\s\u00a0\u202f]/g, '').replace(',', '.');
    if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return null;
    const amount = Number(normalized);
    return Number.isFinite(amount) && amount > 0 ? amount : null;
};

export default function VariablesPaieTab({ idPeriodeActive, canMutate = false, mutationDisabledReason = '', onVariableChanged, onPendingCountChange }) {
    const { hasPermission } = useAuth();
    const toast = useToast();
    const navigate = useNavigate();
    const location = useLocation();
    const canConfigure = hasPermission('ROLE_ADMIN');
    // État local : variables, filtres, formulaire et retours d'action.
    const [variables, setVariables] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshError, setRefreshError] = useState(false);
    const requestIdRef = useRef(0);
    const [typesMouvements, setTypesMouvements] = useState([]);
    const [eligibleEmployees, setEligibleEmployees] = useState([]);
    const [populationError, setPopulationError] = useState('');
    const [filterSens, setFilterSens] = useState('ALL');
    const [showCancelled, setShowCancelled] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [feedbackMsg, setFeedbackMsg] = useState(null);

    // Modales de confirmation (Approuver & Supprimer)
    const [approvingVariable, setApprovingVariable] = useState(null);
    const [deletingVariable, setDeletingVariable] = useState(null);
    const [cancelingVariable, setCancelingVariable] = useState(null);
    const [cancelReason, setCancelReason] = useState('');
    const [cancelError, setCancelError] = useState('');
    const [actionLoading, setActionLoading] = useState(false);

    // Modal Nouvelle Variable
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [formError, setFormError] = useState('');
    const [duplicateAcknowledged, setDuplicateAcknowledged] = useState(false);
    const [bulletinPreview, setBulletinPreview] = useState(null);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [previewError, setPreviewError] = useState('');
    const [previewRevision, setPreviewRevision] = useState(0);
    const [form, setForm] = useState({
        idEmploye: '',
        typeMouvement: '',
        sens: '',
        montant: '',
        motif: ''
    });

    const chargerDonnees = useCallback(async ({ silent = false } = {}) => {
        const requestId = ++requestIdRef.current;
        if (!silent) setLoading(true);
        try {
            const [ajustementsRes, typesRes] = await Promise.all([
                paieService.listerAjustements(idPeriodeActive || null),
                paieService.listerTypesMouvements(idPeriodeActive)
            ]);
            if (requestIdRef.current !== requestId) return;
            setVariables(Array.isArray(ajustementsRes) ? ajustementsRes : (ajustementsRes?.content || ajustementsRes?.items || []));
            setRefreshError(false);
            const loadedTypes = (Array.isArray(typesRes) ? typesRes : (typesRes?.content || typesRes?.items || []))
                .filter(isManualPayrollVariableType);
            setTypesMouvements(loadedTypes);
            if (loadedTypes.length > 0) {
                setForm(prev => ({
                    ...prev,
                    typeMouvement: loadedTypes.some(type => type.code === prev.typeMouvement)
                        ? prev.typeMouvement
                        : loadedTypes[0].code,
                    sens: loadedTypes.some(type => type.code === prev.typeMouvement)
                        ? prev.sens
                        : loadedTypes[0].sens
                }));
            }
        } catch (err) {
            if (requestIdRef.current === requestId) {
                setRefreshError(true);
                if (!silent) setFeedbackMsg({ type: 'error', text: getErrorMessage(err, 'Erreur lors du chargement des variables de paie.') });
            }
        } finally {
            if (requestIdRef.current === requestId) setLoading(false);
        }
    }, [idPeriodeActive]);

    // Effet : recharge les variables quand la période active change.
    useEffect(() => {
        chargerDonnees();
        return () => { requestIdRef.current += 1; };
    }, [chargerDonnees]);

    useEffect(() => {
        const refreshIfVisible = () => {
            if (document.visibilityState === 'visible' && !isModalOpen) chargerDonnees({ silent: true });
        };
        const timer = window.setInterval(refreshIfVisible, 60000);
        document.addEventListener('visibilitychange', refreshIfVisible);
        return () => {
            window.clearInterval(timer);
            document.removeEventListener('visibilitychange', refreshIfVisible);
        };
    }, [chargerDonnees, isModalOpen]);

    useEffect(() => {
        setIsModalOpen(false);
        setForm(previous => ({ ...previous, idEmploye: '' }));
    }, [idPeriodeActive]);

    useEffect(() => {
        let cancelled = false;
        setEligibleEmployees([]);
        setPopulationError('');
        if (!idPeriodeActive) return undefined;
        paieService.getCockpitPeriode(idPeriodeActive, { light: true })
            .then(cockpit => {
                if (cancelled) return;
                const members = Array.isArray(cockpit?.population) ? cockpit.population : [];
                setEligibleEmployees(members
                    .filter(member => ['INCLUDED', 'INCLUS'].includes(member.decision))
                    .map(member => ({
                        idEmploye: member.employeeId ?? member.idEmploye,
                        matricule: member.matricule,
                        nom: member.employeeName || member.nomPrenom || 'Salarié',
                        prenom: ''
                    })));
            })
            .catch(error => {
                if (!cancelled) setPopulationError(getErrorMessage(error, 'Population de la campagne indisponible.'));
            });
        return () => { cancelled = true; };
    }, [idPeriodeActive]);

    useEffect(() => {
        let cancelled = false;
        if (!isModalOpen || !form.idEmploye || !idPeriodeActive) {
            setBulletinPreview(null);
            setPreviewError('');
            return undefined;
        }
        setPreviewLoading(true);
        setPreviewError('');
        paieService.calculerBulletinEmploye(Number(form.idEmploye), idPeriodeActive, true)
            .then(result => { if (!cancelled) setBulletinPreview(result); })
            .catch(error => {
                if (!cancelled) {
                    setBulletinPreview(null);
                    setPreviewError(getErrorMessage(error, 'Aperçu du bulletin indisponible.'));
                }
            })
            .finally(() => { if (!cancelled) setPreviewLoading(false); });
        return () => { cancelled = true; };
    }, [form.idEmploye, idPeriodeActive, isModalOpen, previewRevision]);

    const handleFormChange = (field, val) => {
        setFormError('');
        if (field === 'idEmploye' || field === 'typeMouvement' || field === 'sens') {
            setDuplicateAcknowledged(false);
        }
        setForm(prev => {
            const next = { ...prev, [field]: val };
            if (field === 'sens') {
                const firstType = typesMouvements.find(type => type.sens === val);
                next.typeMouvement = firstType?.code || '';
            }
            if (field === 'typeMouvement') {
                const found = typesMouvements.find(t => t.code === val);
                if (found && found.sens) {
                    next.sens = found.sens;
                }
            }
            return next;
        });
    };

    const handleSubmit = async (e, keepOpen = false) => {
        e.preventDefault();
        if (submitting) return;
        if (!form.idEmploye) {
            setFormError('Veuillez sélectionner un salarié.');
            return;
        }
        if (!eligibleEmployees.some(employee => Number(employee.idEmploye) === Number(form.idEmploye))) {
            setFormError('Ce salarié ne fait pas partie de la population éligible de la campagne.');
            return;
        }
        if (!form.typeMouvement || !typesMouvements.some(type => type.code === form.typeMouvement)) {
            setFormError('Veuillez sélectionner un type configuré.');
            return;
        }
        const amount = parseAriaryAmount(form.montant);
        if (amount === null) {
            setFormError('Saisissez un montant positif, avec au plus deux décimales (ex. 1 250,50).');
            return;
        }
        if (!form.motif || form.motif.trim().length < 8) {
            setFormError('Ajoutez une justification précise (au moins 8 caractères) pour conserver la trace de cette variable.');
            return;
        }
        if (possibleDuplicate && !duplicateAcknowledged) {
            setFormError('Une variable similaire existe déjà. Confirmez qu’il s’agit d’un élément distinct.');
            return;
        }

        setSubmitting(true);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(form.idEmploye),
                idPeriodePaie: idPeriodeActive || null,
                typeMouvement: form.typeMouvement,
                sens: form.sens,
                montant: amount,
                motif: form.motif || 'Variable de paie'
            });

            if (keepOpen) {
                setForm(previous => ({ ...previous, montant: '', motif: '' }));
                setDuplicateAcknowledged(false);
                toast.success('Variable enregistrée. Vous pouvez saisir la suivante pour ce salarié ou en choisir un autre.');
                setPreviewRevision(revision => revision + 1);
            } else {
                setIsModalOpen(false);
                setForm({ idEmploye: '', typeMouvement: typesMouvements[0]?.code || '', sens: typesMouvements[0]?.sens || '', montant: '', motif: '' });
            }
            await chargerDonnees({ silent: true });
            if (!keepOpen) toast.success('Variable enregistrée. Approuvez-la avant le calcul réel de la paie.');
            onVariableChanged?.();
        } catch (err) {
            setFormError(getErrorMessage(err, 'Erreur lors de la création de la variable.'));
        } finally {
            setSubmitting(false);
        }
    };

    const handleConfirmSupprimer = async () => {
        if (!deletingVariable) return;
        const idAvance = deletingVariable.idAvance ?? deletingVariable.id_avance;
        if (!idAvance) return;

        setActionLoading(true);
        try {
            await paieService.supprimerAjustement(idAvance);
            await chargerDonnees({ silent: true });
            toast.success('Variable supprimée.');
            onVariableChanged?.();
            setDeletingVariable(null);
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Impossible de supprimer cette variable.')
            });
        } finally {
            setActionLoading(false);
        }
    };

    const handleConfirmAnnuler = async () => {
        if (!cancelingVariable) return;
        if (cancelReason.trim().length < 8) {
            setCancelError('Indiquez un motif d’annulation de huit caractères minimum.');
            return;
        }
        setActionLoading(true);
        setCancelError('');
        try {
            await paieService.executerCommandeAjustement(cancelingVariable.idAvance, 'CANCEL', cancelReason.trim(), cancelingVariable.version);
            await chargerDonnees({ silent: true });
            toast.success('Variable annulée. Recalculez le bulletin concerné.');
            onVariableChanged?.();
            setCancelingVariable(null);
            setCancelReason('');
            setCancelError('');
        } catch (err) {
            setCancelError(getErrorMessage(err, 'Impossible d’annuler cette variable.'));
        } finally {
            setActionLoading(false);
        }
    };

    const handleConfirmApprouver = async () => {
        if (!approvingVariable) return;
        const idAvance = approvingVariable.idAvance ?? approvingVariable.id_avance;
        if (!idAvance) return;

        setActionLoading(true);
        try {
            await paieService.executerCommandeAjustement(idAvance, 'APPROVE', null, approvingVariable.version);
            await chargerDonnees({ silent: true });
            toast.success('Variable approuvée. Le calcul réel peut désormais la prendre en compte.');
            onVariableChanged?.();
            setApprovingVariable(null);
        } catch (err) {
            setFeedbackMsg({ type: 'error', text: getErrorMessage(err, 'Impossible d’approuver cette variable.') });
        } finally {
            setActionLoading(false);
        }
    };

    const formatAr = (val) => formatMoneyAr(val);

    // Filtrage
    const listVariables = Array.isArray(variables) ? variables : [];
    const cancelledCount = listVariables.filter(v => v.statut === 'ANNULE').length;
    const activeVariables = listVariables.filter(v => v.statut !== 'ANNULE');
    // Un même type peut être saisi plusieurs fois (ex. deux primes exceptionnelles).
    // Le système ne le bloque donc pas : il force une vérification consciente.
    const possibleDuplicate = Boolean(form.idEmploye && form.typeMouvement && listVariables.some((variable) => {
        const employeeId = variable.idEmploye ?? variable.id_employe;
        const type = variable.typeMouvement ?? variable.type_mouvement;
        return Number(employeeId) === Number(form.idEmploye)
            && type === form.typeMouvement
            && !['REJETE', 'ANNULE', 'CANCELLED'].includes(variable.statut);
    }));
    const filteredVariables = listVariables.filter(v => {
        if (!showCancelled && v.statut === 'ANNULE') return false;
        const matchSens = filterSens === 'ALL' || v.sens === filterSens;
        const q = searchQuery.toLowerCase();
        const nomComplet = v.nomComplet || v.nom_complet || '';
        const typeLibelle = v.typeLibelle || v.type_libelle || v.typeMouvement || v.type_mouvement || '';
        const matricule = v.matricule || '';
        const motif = v.motif || '';
        const matchSearch = !q || 
            nomComplet.toLowerCase().includes(q) ||
            matricule.toLowerCase().includes(q) ||
            typeLibelle.toLowerCase().includes(q) ||
            motif.toLowerCase().includes(q);
        return matchSens && matchSearch;
    }).sort((a, b) => Number(b.statut === 'EN_ATTENTE') - Number(a.statut === 'EN_ATTENTE'));
    const pagedVariables = filteredVariables.slice((currentPage - 1) * pageSize, currentPage * pageSize);

    const totalGains = activeVariables
        .filter(v => v.sens === 'GAIN')
        .reduce((sum, v) => sum + (Number(v.montant) || 0), 0);

    const totalRetenues = activeVariables
        .filter(v => v.sens === 'RETENUE')
        .reduce((sum, v) => sum + (Number(v.montant) || 0), 0);
    const pendingCount = listVariables.filter(v => v.statut === 'EN_ATTENTE').length;

    useEffect(() => {
        if (!loading && !refreshError) onPendingCountChange?.(pendingCount);
    }, [loading, refreshError, pendingCount, onPendingCountChange]);

    const statutLabels = {
        IMPUTE_PAIE: { label: 'Imputé en paie', variant: 'info' },
        VALIDE: { label: 'Validé', variant: 'success' },
        EN_ATTENTE: { label: 'En attente', variant: 'warning' },
        APPROUVE: { label: 'Approuvée', variant: 'success' },
        BROUILLON: { label: 'Brouillon', variant: 'neutral' },
        REJETE: { label: 'Rejeté', variant: 'danger' },
    };
    const regimeLabel = (sens, imposable, cotisable) => {
        if (sens !== 'GAIN') return 'Déduction du net à payer';
        if (imposable == null || cotisable == null) return 'Régime à vérifier';
        return `${imposable ? 'IRSA' : 'Hors IRSA'} · ${cotisable ? 'Cotisations' : 'Hors cotisations'}`;
    };
    const selectedType = typesMouvements.find(type => type.code === form.typeMouvement);
    const previewBulletin = bulletinPreview?.bulletin;
    const previewNet = previewBulletin?.netAPayer ?? previewBulletin?.net_a_payer;
    const previewGross = previewBulletin?.totalGainsBruts ?? previewBulletin?.total_gains_bruts;
    const previewBase = previewBulletin?.salaireBase ?? previewBulletin?.salaire_base;
    const previewCotisations = previewBulletin?.totalCotisationsSalariales ?? previewBulletin?.total_cotisations_salariales;
    const previewNetImposable = previewBulletin?.salaireNetImposable ?? previewBulletin?.salaire_net_imposable;
    const previewRevenuIrsa = previewBulletin?.revenuNetImposable ?? previewBulletin?.revenu_net_imposable;
    const previewBaseIrsa = previewBulletin?.baseIrsaArrondie ?? previewBulletin?.base_irsa_arrondie;
    const previewIrsa = previewBulletin?.irsaNet ?? previewBulletin?.irsa_net;
    const previewIsi = previewBulletin?.retenueIsi5 ?? previewBulletin?.retenue_isi_5;
    const previewIndemnitesNet = previewBulletin?.totalIndemnitesNonImposables ?? previewBulletin?.total_indemnites_non_imposables;
    const previewRetenues = previewBulletin?.totalRetenuesAcomptePret ?? previewBulletin?.total_retenues_acompte_pret;
    const previewAvantArrondi = previewBulletin?.netAvantArrondi ?? previewBulletin?.net_avant_arrondi;
    const previewLines = Array.isArray(bulletinPreview?.lignes) ? bulletinPreview.lignes : [];
    const previewFiscalite = bulletinPreview?.explicationFiscalite;
    const previewHsExoneree = Number(previewFiscalite?.remunerationHsExonereeIrsa
        ?? previewLines.find(line => line.codeRubrique === 'HS_EXONEREE_IRSA')?.montantSalarial ?? 0);
    const previewIsIrsa = Number(previewIsi || 0) === 0
        && previewLines.some(line => ['IMPOT_IRSA_BRUT', 'IMPOT_IRSA_NET'].includes(line.codeRubrique));
    const previewAjustement = previewAvantArrondi != null && previewNet != null
        ? Math.round((Number(previewNet) - Number(previewAvantArrondi)) * 100) / 100 : 0;
    const previewSteps = [
        ['Brut calculé', previewGross, '', 'font-bold text-[var(--color-fg)]'],
        Number(previewCotisations || 0) > 0 && ['Cotisations salariales', previewCotisations, '−', 'text-[var(--color-danger-600)] font-semibold'],
        ['Net après cotisations', previewNetImposable, '', 'font-medium text-[var(--color-fg)]'],
        previewIsIrsa && previewHsExoneree > 0 && ['Heures supplémentaires exclues de la base IRSA', previewHsExoneree, '−', 'text-[var(--color-fg-muted)]'],
        previewIsIrsa && previewHsExoneree > 0 && ['Revenu soumis à l’IRSA', previewRevenuIrsa, '', 'text-[var(--color-fg)]'],
        previewIsIrsa && ['Base fiscale IRSA (arrondie)', previewBaseIrsa, '', 'text-[var(--color-fg-muted)]'],
        previewIsIrsa && Number(previewIrsa || 0) > 0 && ['IRSA prélevé', previewIrsa, '−', 'text-[var(--color-danger-600)] font-semibold'],
        Number(previewIsi || 0) > 0 && ['ISI prélevé', previewIsi, '−', 'text-[var(--color-danger-600)] font-semibold'],
        Number(previewIndemnitesNet || 0) > 0 && ['Indemnités et remboursements non imposables', previewIndemnitesNet, '+', 'text-[var(--color-success-700)] font-semibold'],
        Number(previewRetenues || 0) > 0 && ['Acomptes, prêts et autres retenues', previewRetenues, '−', 'text-[var(--color-danger-600)] font-semibold'],
        ['Net avant arrondi', previewAvantArrondi, '', 'text-[var(--color-fg)] font-medium'],
        previewAjustement !== 0 && ['Ajustement à la dizaine d’ariary', Math.abs(previewAjustement), previewAjustement > 0 ? '+' : '−', 'text-[var(--color-fg)]'],
    ].filter(Boolean);
    const employeeVariables = listVariables.filter(variable =>
        Number(variable.idEmploye ?? variable.id_employe) === Number(form.idEmploye)
        && !['REJETE', 'ANNULE', 'CANCELLED'].includes(variable.statut)
    );
    const previewEmployee = eligibleEmployees.find(emp => Number(emp.idEmploye ?? emp.id_employe) === Number(form.idEmploye));
    const montantNum = parseAriaryAmount(form.montant);
    const hasValidMontant = montantNum != null && montantNum > 0;

    // Rendu
    return (
        <div className="space-y-4">
            {feedbackMsg && (
                <Alert
                    type={feedbackMsg.type}
                    message={feedbackMsg.text}
                    onClose={() => setFeedbackMsg(null)}
                />
            )}

            <div className="grid grid-cols-2 lg:grid-cols-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] divide-x divide-[var(--color-border-subtle)]">
                {[
                    ['Variables', listVariables.length, 'text-[var(--color-fg)]'],
                    ['À approuver', pendingCount, pendingCount > 0 ? 'text-[var(--color-warning-700)]' : 'text-[var(--color-fg)]'],
                    ['Gains', `+${formatAr(totalGains)}`, 'text-[var(--color-success-700)]'],
                    ['Retenues', `−${formatAr(totalRetenues)}`, 'text-[var(--color-danger-700)]'],
                ].map(([label, value, tone]) => (
                    <div key={label} className="p-3 min-w-0">
                        <p className="text-[10px] uppercase tracking-wider font-bold text-[var(--color-fg-muted)]">{label}</p>
                        <p className={`mt-1 text-lg font-bold font-mono truncate ${tone}`}>{value}</p>
                    </div>
                ))}
            </div>

            <details className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2 text-xs text-[var(--color-fg-muted)]">
                <summary className="font-semibold text-[var(--color-fg)] cursor-pointer">Quelles variables saisir ici ?</summary>
                <p className="mt-2">Les repas et déplacements ordinaires proviennent du pointage. Saisissez ici les primes et retenues exceptionnelles justifiées. Le régime fiscal et social provient du type configuré.</p>
            </details>

            {pendingCount > 0 && (
                <Alert type="warning" message={`${pendingCount} variable(s) en attente d’approbation. Le calcul réel reste bloqué jusqu’à leur approbation ou suppression.`} />
            )}
            {refreshError && <p role="status" className="text-xs text-[var(--color-warning-700)]">Mise à jour indisponible ; les dernières données chargées restent affichées.</p>}

            {/* Barre de filtre et Action */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                <div className="flex flex-wrap items-center gap-2.5 w-full sm:w-auto flex-1">
                    <div className="flex-1 sm:w-72 min-w-[200px]">
                        <Input
                            type="search"
                            placeholder="Rechercher par collaborateur, matricule, motif…"
                            value={searchQuery}
                            onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
                            iconLeft={<Search size={14} />}
                            aria-label="Rechercher une variable de paie"
                        />
                    </div>
                    <div className="w-56">
                        <Select
                            value={filterSens}
                            onChange={(val) => { setFilterSens(val); setCurrentPage(1); }}
                            options={[
                                { value: 'ALL', label: 'Tous les éléments (Gains & Retenues)' },
                                { value: 'GAIN', label: '+ Primes & Gains' },
                                { value: 'RETENUE', label: '- Acomptes & Retenues' },
                            ]}
                            aria-label="Filtrer par sens"
                        />
                    </div>
                </div>

                {canConfigure && <Button variant="ghost" size="sm" onClick={() => navigate('/admin/parametres?module=paie&tab=types_variables', { state: { from: location.pathname + location.search } })} iconLeft={<SlidersHorizontal size={14} />}>
                    Catalogue des types
                </Button>}
                {cancelledCount > 0 && <Button variant="ghost" size="sm" onClick={() => { setShowCancelled(value => !value); setCurrentPage(1); }}>
                    {showCancelled ? 'Masquer les annulées' : `Afficher les annulées (${cancelledCount})`}
                </Button>}
                <Button
                    variant="primary"
                    size="sm"
                    onClick={() => { setFormError(''); setIsModalOpen(true); }}
                    iconLeft={<Plus size={14} />}
                    className="w-full sm:w-auto justify-center shrink-0"
                    disabled={!canMutate}
                    title={!canMutate ? mutationDisabledReason : undefined}
                >
                    Ajouter une variable
                </Button>
            </div>

            {/* Table des variables */}
            {loading ? (
                <SkeletonTable rows={6} cols={6} />
            ) : filteredVariables.length === 0 ? (
                <EmptyState
                    icon={SlidersHorizontal}
                    title="Aucune variable de paie"
                    description="Il n’y a aucun élément variable (prime, acompte, retenue) enregistré pour cette période ou ces filtres."
                    actionLabel={canMutate ? 'Ajouter une variable' : undefined}
                    onAction={canMutate ? () => { setFormError(''); setIsModalOpen(true); } : undefined}
                    actionIcon={canMutate ? <Plus size={14} /> : undefined}
                />
            ) : (
                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                <Table aria-label="Variables de paie" density="compact">
                    <Thead>
                        <Tr>
                            <Th>Salarié</Th>
                            <Th>Variable</Th>
                            <Th className="text-right">Montant</Th>
                            <Th>Justification</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {pagedVariables.map((v, index) => {
                            const idAvance = v.idAvance ?? v.id_avance;
                            const nomComplet = v.nomComplet || v.nom_complet;
                            const typeLibelle = v.typeLibelle || v.type_libelle || v.typeMouvement || v.type_mouvement;
                            const statInfo = statutLabels[v.statut] || { label: v.statut || 'Enregistré', variant: 'neutral' };
                            const configuredType = typesMouvements.find(type => type.code === v.typeMouvement);
                            const taxable = v.imposable ?? configuredType?.imposable;
                            const social = v.cotisable ?? configuredType?.cotisable;
                            return (
                                <Tr key={idAvance || index} className={v.statut === 'EN_ATTENTE' ? 'bg-[var(--color-warning-50)]/50' : ''}>
                                    <Td className="font-semibold text-[var(--color-fg)]">
                                        <span className="block">{nomComplet || 'Collaborateur'}</span>
                                        <span className="block font-mono font-normal text-[11px] text-[var(--color-fg-muted)]">{v.matricule || '—'}</span>
                                    </Td>
                                    <Td>
                                        <span className="font-medium text-[var(--color-fg)] block">
                                            {typeLibelle || 'Variable'}
                                        </span>
                                        <span className="block text-[11px] text-[var(--color-fg-muted)]">{regimeLabel(v.sens, taxable, social)}</span>
                                    </Td>
                                    <Td className="text-right font-mono font-bold whitespace-nowrap">
                                        <span className={v.sens === 'GAIN' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'}>
                                            {v.sens === 'GAIN' ? '+' : '-'}{formatAr(v.montant)}
                                        </span>
                                    </Td>
                                    <Td className="text-[var(--color-fg-muted)] max-w-xs whitespace-normal">
                                        <span className="block">{v.motif || '—'}</span>
                                        {(v.dateDemande || v.date_demande) && <span className="block text-[11px] text-[var(--color-fg-subtle)]">Saisie le {fmtDate(v.dateDemande || v.date_demande)}</span>}
                                    </Td>
                                    <Td>
                                        <Badge variant={statInfo.variant} size="xs" dot>
                                            {statInfo.label}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right whitespace-nowrap">
                                        <div className="inline-flex items-center justify-end gap-1.5">
                                            {canMutate && v.statut === 'EN_ATTENTE' && idAvance && (
                                                <Button
                                                    variant="outline"
                                                    size="xs"
                                                    iconLeft={<CheckCircle2 size={13} className="text-[var(--color-brand-600)]" />}
                                                    onClick={() => setApprovingVariable(v)}
                                                    className="text-[var(--color-brand-600)] border-[var(--color-brand-300)] hover:bg-[var(--color-brand-50)] dark:border-[var(--color-brand-700)] dark:hover:bg-[var(--color-brand-900)]/30"
                                                >
                                                    Approuver
                                                </Button>
                                            )}
                                            {canMutate && v.statut === 'EN_ATTENTE' && isManualPayrollVariable(v) && idAvance && (
                                                <Button
                                                    variant="ghost"
                                                    size="xs"
                                                    iconLeft={<Trash2 size={13} />}
                                                    onClick={() => setDeletingVariable(v)}
                                                    className="text-[var(--color-danger-600)] hover:bg-[var(--color-danger-50)] dark:hover:bg-[var(--color-danger-950)]/40"
                                                >
                                                    Supprimer
                                                </Button>
                                            )}
                                            {canMutate && ['APPROUVE', 'IMPUTE_PAIE'].includes(v.statut) && isManualPayrollVariable(v) && idAvance && (
                                                <Button variant="ghost" size="xs" iconLeft={<Trash2 size={13} />}
                                                    onClick={() => { setCancelingVariable(v); setCancelReason(''); setCancelError(''); }}
                                                    className="text-[var(--color-danger-600)] hover:bg-[var(--color-danger-50)]">
                                                    Annuler la variable
                                                </Button>
                                            )}
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
                <Pagination currentPage={currentPage} totalItems={filteredVariables.length} pageSize={pageSize} pageSizeOptions={[10, 25, 50]} onPageChange={setCurrentPage} onPageSizeChange={(size) => { setPageSize(size); setCurrentPage(1); }} />
                </div>
            )}

            {/* Modale d'ajout de variable */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Ajouter une variable de paie"
                size="4xl"
                className="max-h-[92vh]"
                icon={<SlidersHorizontal size={18} />}
                footer={
                    <div className="flex items-center justify-between w-full">
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setIsModalOpen(false)}
                            disabled={submitting}
                        >
                            Annuler
                        </Button>
                        <div className="flex items-center gap-2">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={(event) => handleSubmit(event, true)}
                                disabled={submitting || (possibleDuplicate && !duplicateAcknowledged)}
                            >
                                Enregistrer et ajouter une autre
                            </Button>
                            <Button 
                                variant="primary" 
                                size="sm" 
                                onClick={handleSubmit}
                                disabled={submitting || (possibleDuplicate && !duplicateAcknowledged)}
                            >
                                {submitting ? 'Enregistrement…' : 'Enregistrer pour approbation'}
                            </Button>
                        </div>
                    </div>
                }
            >
                <form onSubmit={handleSubmit} className="text-xs">
                    {formError && <div role="alert" className="mb-4 flex items-start gap-2 border border-[var(--color-danger-500)]/35 bg-[var(--color-danger-500)]/10 p-3 text-[var(--color-danger-700)]"><AlertCircle size={14} className="mt-0.5 shrink-0" />{formError}</div>}
                    <div className="grid grid-cols-1 lg:grid-cols-[1fr_1.25fr] gap-6 items-start">
                      <div className="space-y-4">
                        <div className="border-l-2 border-[var(--color-brand-500)] pl-3">
                            <h4 className="text-sm font-bold text-[var(--color-fg)]">1. Salarié concerné</h4>
                            <p className="mt-0.5 text-[11px] text-[var(--color-fg-muted)]">Choisissez un salarié inclus dans cette campagne.</p>
                        </div>
                        <div>
                            <EmployeSearchSelect
                                required
                                value={form.idEmploye}
                                employes={eligibleEmployees}
                                onChange={(employeeId) => handleFormChange('idEmploye', employeeId)}
                                placeholder="Rechercher par nom ou matricule…"
                            />
                            {populationError && <p className="mt-2 text-[11px] text-[var(--color-danger-600)]">{populationError}</p>}
                        </div>

                        <div className="border-l-2 border-[var(--color-brand-500)] pl-3 pt-1">
                            <h4 className="text-sm font-bold text-[var(--color-fg)]">2. Variable et justification</h4>
                            <p className="mt-0.5 text-[11px] text-[var(--color-fg-muted)]">Le traitement fiscal et social provient du type choisi.</p>
                        </div>
                        <div className="grid grid-cols-2 gap-2" role="group" aria-label="Sens de la variable">
                            {[
                                { sens: 'GAIN', label: 'Ajouter un gain', detail: 'Prime ou indemnité', icon: <TrendingUp size={16} /> },
                                { sens: 'RETENUE', label: 'Effectuer une retenue', detail: 'Déduction sur paie', icon: <TrendingDown size={16} /> }
                            ].map(option => (
                                <button
                                    key={option.sens}
                                    type="button"
                                    aria-pressed={form.sens === option.sens}
                                    disabled={!typesMouvements.some(type => type.sens === option.sens)}
                                    onClick={() => handleFormChange('sens', option.sens)}
                                    className={`flex min-h-[70px] items-start gap-2.5 border p-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${form.sens === option.sens ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5' : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] hover:border-[var(--color-brand-500)]/60'}`}
                                >
                                    <span className={option.sens === 'GAIN' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}>{option.icon}</span>
                                    <span><strong className="block text-[11px] text-[var(--color-fg)]">{option.label}</strong><small className="mt-0.5 block text-[10px] text-[var(--color-fg-muted)]">{option.detail}</small></span>
                                </button>
                            ))}
                        </div>
                        <Select
                            label="Type de variable"
                            required
                            value={form.typeMouvement}
                            onChange={(val) => handleFormChange('typeMouvement', val)}
                            options={typesMouvements.filter(t => t.sens === form.sens).map(t => ({
                                value: t.code,
                                label: t.libelle
                            }))}
                        />

                        {selectedType && <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 space-y-1">
                        <strong className="text-[var(--color-fg)]">{selectedType.libelle}</strong>
                        {selectedType.description && <p className="text-[var(--color-fg-muted)]">{selectedType.description}</p>}
                        <p className="text-[var(--color-fg-muted)]">Traitement : {regimeLabel(selectedType.sens, selectedType.imposable, selectedType.cotisable)}</p>
                        </div>}

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <MoneyInput
                            label="Montant en Ariary"
                            required
                            placeholder="Ex. 1 250,50"
                            hint="Montant positif en Ar."
                            value={form.montant}
                            onValueChange={(value) => handleFormChange('montant', value)}
                            min={0}
                            maxDecimals={2}
                        />

                        <Input
                            label="Motif / Justification"
                            type="text"
                            required
                            minLength={8}
                            placeholder="Ex: Prime transport – mission projet"
                            value={form.motif}
                            onChange={(e) => handleFormChange('motif', e.target.value)}
                        />
                        </div>

                        {possibleDuplicate && (
                        <div className="border border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10 p-3 space-y-2">
                            <p className="text-xs font-semibold text-[var(--color-fg)]">Variable similaire déjà enregistrée</p>
                            <p className="text-[11px] text-[var(--color-fg-muted)]">Même collaborateur et même type sur cette période. Vérifiez le montant et le motif avant de poursuivre.</p>
                            <div className="flex items-start gap-2 pt-1">
                                <input
                                    id="confirm-duplicate-variable"
                                    type="checkbox"
                                    checked={duplicateAcknowledged}
                                    onChange={(event) => { setDuplicateAcknowledged(event.target.checked); setFormError(''); }}
                                    className="mt-0.5 accent-[var(--color-brand-600)] cursor-pointer"
                                />
                                <label htmlFor="confirm-duplicate-variable" className="text-[11px] text-[var(--color-fg)] cursor-pointer">
                                    Je confirme qu’il s’agit d’un élément distinct et non d’un doublon.
                                </label>
                            </div>
                        </div>
                        )}
                      </div>

                      <aside className="border border-[var(--color-border)] bg-[var(--color-surface)] shadow-sm p-4 space-y-3.5 border-t-2 border-t-[var(--color-brand-500)]">
                        <div className="flex items-center justify-between pb-2.5 border-b border-[var(--color-border-subtle)]">
                            <div>
                                <div className="flex items-center gap-2 text-sm font-bold text-[var(--color-fg)]">
                                    <Eye size={16} className="text-[var(--color-brand-600)]" />
                                    <span>Bulletin actuel</span>
                                    {form.idEmploye && (
                                        <Badge variant="brand" size="xs" dot>
                                            Simulation
                                        </Badge>
                                    )}
                                </div>
                                <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">
                                    Aperçu provisoire avant l’imputation de cette variable.
                                </p>
                            </div>
                            {previewEmployee && (
                                <Badge variant="neutral" size="xs" className="font-mono font-semibold">
                                    #{previewEmployee.matricule}
                                </Badge>
                            )}
                        </div>

                        {!form.idEmploye ? (
                            <div className="flex min-h-[160px] flex-col items-center justify-center text-center text-[var(--color-fg-muted)] p-6 bg-[var(--color-bg-subtle)]/40 border border-dashed border-[var(--color-border-subtle)]">
                                <SlidersHorizontal size={28} className="mb-2 opacity-40 text-[var(--color-brand-600)]" />
                                <p className="font-semibold text-xs text-[var(--color-fg)]">Sélectionnez un salarié</p>
                                <p className="mt-1 max-w-[260px] text-[11px]">Son bulletin provisoire apparaîtra ici avec tous les calculs intermédiaires pour vous guider.</p>
                            </div>
                        ) : previewLoading ? (
                            <div className="flex min-h-[160px] flex-col items-center justify-center gap-2 text-xs text-[var(--color-fg-muted)] p-6">
                                <div className="h-5 w-5 animate-spin rounded-full border-2 border-[var(--color-brand-500)] border-t-transparent" />
                                <span>Calcul de l’aperçu du bulletin…</span>
                            </div>
                        ) : previewError ? (
                            <div className="flex gap-2.5 border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/10 p-3.5 text-xs text-[var(--color-fg)]">
                                <AlertCircle size={16} className="mt-0.5 shrink-0 text-[var(--color-warning-600)]" />
                                <span>{previewError}</span>
                            </div>
                        ) : previewBulletin ? (
                            <div className="space-y-3.5">
                                {/* KPIs Salaire de Base et Brut Provisoire */}
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/60 p-3">
                                        <span className="block text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Salaire contractuel</span>
                                        <strong className="mt-1 block font-mono text-base font-bold text-[var(--color-fg)]">{formatAr(previewBase)}</strong>
                                    </div>
                                    <div className="border border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5 p-3">
                                        <span className="block text-[11px] font-bold uppercase tracking-wider text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">Brut provisoire</span>
                                        <strong className="mt-1 block font-mono text-base font-bold text-[var(--color-fg)]">{formatAr(previewGross)}</strong>
                                    </div>
                                </div>

                                {/* Décomposition détaillée du brut au net */}
                                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/30 p-3.5 space-y-2" aria-label="Étapes du calcul provisoire">
                                    <div className="flex items-center justify-between pb-1.5 border-b border-[var(--color-border-subtle)]">
                                        <p className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">Décomposition du brut au net</p>
                                        <span className="text-[10px] text-[var(--color-fg-muted)] font-mono">Période active</span>
                                    </div>
                                    <div className="space-y-1">
                                        {previewSteps.map(([label, amount, sign, valueClass]) => amount != null && (
                                            <div key={label} className="flex items-baseline justify-between gap-3 text-xs py-1 border-b border-[var(--color-border-subtle)]/30 last:border-0">
                                                <span className="text-[var(--color-fg-muted)] truncate">{label}</span>
                                                <strong className={`shrink-0 font-mono ${valueClass || 'text-[var(--color-fg)]'}`}>
                                                    {sign}{formatAr(amount)}
                                                </strong>
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                {/* Net Provisoire à payer - Grand encart de synthèse */}
                                <div className="border-2 border-[var(--color-success-500)]/40 bg-[var(--color-success-500)]/10 p-3.5 flex items-center justify-between">
                                    <div>
                                        <span className="block text-xs font-bold uppercase tracking-wider text-[var(--color-success-800)] dark:text-[var(--color-success-200)]">
                                            Net actuel à payer · après arrondi
                                        </span>
                                        <small className="text-[10px] text-[var(--color-fg-muted)]">Situation calculée avant ajout</small>
                                    </div>
                                    <strong className="font-mono text-2xl font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-300)]">
                                        {formatAr(previewNet)}
                                    </strong>
                                </div>

                                {/* Simulation d'impact dynamique de la variable saisie */}
                                {hasValidMontant && (
                                    <div className={`p-3 border text-xs flex items-center justify-between ${
                                        form.sens === 'RETENUE'
                                            ? 'border-[var(--color-danger-500)]/30 bg-[var(--color-danger-500)]/5 text-[var(--color-danger-800)] dark:text-[var(--color-danger-200)]'
                                            : 'border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5 text-[var(--color-brand-800)] dark:text-[var(--color-brand-200)]'
                                    }`}>
                                        <div className="flex items-center gap-2">
                                            {form.sens === 'RETENUE' ? (
                                                <TrendingDown size={17} className="text-[var(--color-danger-600)] shrink-0" />
                                            ) : (
                                                <TrendingUp size={17} className="text-[var(--color-success-600)] shrink-0" />
                                            )}
                                            <div>
                                                <strong className="block text-xs font-bold uppercase tracking-wider">
                                                    {form.sens === 'RETENUE' ? 'Impact retenue directe' : 'Impact gain estimé'}
                                                </strong>
                                                <span className="text-[11px] opacity-80">
                                                    {form.sens === 'RETENUE'
                                                        ? 'Déduction directe sur le net à payer'
                                                        : (selectedType?.cotisable || selectedType?.imposable
                                                            ? 'Gain brut (soumis aux règles fiscales & sociales)'
                                                            : 'Indemnité nette ajoutée directement')}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="text-right shrink-0">
                                            <span className="block font-mono text-sm font-bold">
                                                {form.sens === 'RETENUE' ? '−' : '+'}{formatAr(montantNum)}
                                            </span>
                                            <span className="text-[10px] opacity-90 block">Effet réel visible après simulation du bulletin.</span>
                                        </div>
                                    </div>
                                )}

                                {/* Variables Déjà Enregistrées */}
                                {employeeVariables.length > 0 && (
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3 space-y-2">
                                        <div className="flex items-center justify-between pb-1 border-b border-[var(--color-border-subtle)]">
                                            <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                                Mouvements déjà enregistrés ({employeeVariables.length})
                                            </p>
                                        </div>
                                        <div className="space-y-1">
                                            {employeeVariables.slice(0, 4).map((variable, index) => (
                                                <div key={variable.idAvance ?? variable.id_avance ?? index} className="flex justify-between items-center gap-3 py-1 text-xs border-b border-[var(--color-border-subtle)]/30 last:border-0">
                                                    <span className="truncate text-[var(--color-fg-muted)]">{variable.typeLibelle || variable.type_libelle || variable.typeMouvement || variable.type_mouvement}</span>
                                                    <strong className={`shrink-0 font-mono text-xs ${variable.sens === 'RETENUE' ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-success-600)]'}`}>
                                                        {variable.sens === 'RETENUE' ? '−' : '+'}{formatAr(variable.montant)}
                                                    </strong>
                                                </div>
                                            ))}
                                            {employeeVariables.length > 4 && <p className="text-[10px] text-[var(--color-fg-muted)]">Et {employeeVariables.length - 4} autre(s) variable(s).</p>}
                                        </div>
                                    </div>
                                )}

                                {/* Rubriques calculées du bulletin provisoire */}
                                {previewLines.length > 0 && (
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3 space-y-2">
                                        <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] pb-1 border-b border-[var(--color-border-subtle)]">
                                            Rubriques calculées ({previewLines.length})
                                        </p>
                                        <div className="space-y-1">
                                            {previewLines.map((line, index) => (
                                                <div key={`${line.codeRubrique || line.code_rubrique || 'ligne'}-${index}`} className="flex justify-between items-center gap-3 py-1 text-xs border-b border-[var(--color-border-subtle)]/30 last:border-0">
                                                    <span className="text-[var(--color-fg-muted)]">{line.libelleRubrique || line.libelle_rubrique || line.codeRubrique || line.code_rubrique || 'Élément de paie'}</span>
                                                    <strong className={`shrink-0 font-mono text-xs ${line.sens === 'RETENUE' ? 'text-[var(--color-danger-600)]' : line.sens === 'GAIN' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                        {line.sens === 'PATRONAL' ? 'Employeur · ' : line.sens === 'RETENUE' ? '−' : line.sens === 'GAIN' ? '+' : 'Info · '}
                                                        {formatAr(line.sens === 'PATRONAL' ? line.montantPatronal : line.montantSalarial ?? line.montant_salarial)}
                                                    </strong>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                <div className="flex gap-2 border-t border-[var(--color-border-subtle)] pt-3 text-[10px] leading-relaxed text-[var(--color-fg-muted)]">
                                    <Info size={13} className="mt-0.5 shrink-0" />
                                    <span>Cette simulation n’enregistre rien. Après l’ajout, l’étape Calcul recalculera le bulletin avec la variable et ses règles fiscales et sociales.</span>
                                </div>
                            </div>
                        ) : (
                            <div className="flex min-h-[120px] items-center justify-center text-xs text-[var(--color-fg-muted)]">Aucun résultat d’aperçu.</div>
                        )}
                      </aside>
                    </div>
                </form>
            </Modal>

            {/* Modale de Confirmation - Approbation */}
            <ConfirmDialog
                isOpen={Boolean(approvingVariable)}
                onClose={() => !actionLoading && setApprovingVariable(null)}
                onConfirm={handleConfirmApprouver}
                title="Approuver la variable de paie"
                message="Cette variable sera validée et prise en compte dans le calcul réel des bulletins de cette période."
                confirmLabel={actionLoading ? 'Approbation…' : 'Approuver la variable'}
                cancelLabel="Annuler"
                variant="primary"
                loading={actionLoading}
            >
                {approvingVariable && (
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/60 p-3 space-y-2 text-xs">
                        <div className="flex items-center justify-between">
                            <span className="text-[var(--color-fg-muted)]">Salarié</span>
                            <span className="font-semibold text-[var(--color-fg)]">
                                {approvingVariable.nomComplet || approvingVariable.nom_complet || 'Salarié'}
                                {approvingVariable.matricule ? ` (${approvingVariable.matricule})` : ''}
                            </span>
                        </div>
                        <div className="flex items-center justify-between">
                            <span className="text-[var(--color-fg-muted)]">Type & Sens</span>
                            <span className="font-medium text-[var(--color-fg)]">
                                {approvingVariable.typeLibelle || approvingVariable.type_libelle || approvingVariable.typeMouvement}
                                {' · '}
                                <span className={approvingVariable.sens === 'GAIN' ? 'text-[var(--color-success-600)] font-semibold' : 'text-[var(--color-danger-600)] font-semibold'}>
                                    {approvingVariable.sens === 'GAIN' ? 'Gain' : 'Retenue'}
                                </span>
                            </span>
                        </div>
                        <div className="flex items-center justify-between">
                            <span className="text-[var(--color-fg-muted)]">Montant</span>
                            <span className={`font-mono font-bold text-sm ${approvingVariable.sens === 'GAIN' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                {approvingVariable.sens === 'GAIN' ? '+' : '-'}{formatAr(approvingVariable.montant)}
                            </span>
                        </div>
                        {approvingVariable.motif && (
                            <div className="pt-1.5 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)]">
                                <span className="font-medium text-[var(--color-fg)]">Motif : </span>
                                {approvingVariable.motif}
                            </div>
                        )}
                    </div>
                )}
            </ConfirmDialog>

            <ConfirmDialog
                isOpen={Boolean(cancelingVariable)}
                onClose={() => {
                    if (!actionLoading) {
                        setCancelingVariable(null);
                        setCancelReason('');
                        setCancelError('');
                    }
                }}
                onConfirm={handleConfirmAnnuler}
                title="Annuler la variable de paie"
                message="Cette annulation sera tracée. Si la variable figure déjà sur un bulletin, recalculez ce bulletin pour retirer son effet."
                confirmLabel={actionLoading ? 'Annulation…' : 'Confirmer l’annulation'}
                variant="danger"
                loading={actionLoading}
            >
                <Input
                    label="Motif d’annulation"
                    placeholder="Ex : Erreur de saisie, justificatif manquant (8 caractères min.)..."
                    value={cancelReason}
                    onChange={event => {
                        setCancelReason(event.target.value);
                        if (cancelError) setCancelError('');
                    }}
                    error={cancelError}
                    required
                    autoFocus
                />
            </ConfirmDialog>

            {/* Modale de Confirmation - Suppression */}
            <ConfirmDialog
                isOpen={Boolean(deletingVariable)}
                onClose={() => !actionLoading && setDeletingVariable(null)}
                onConfirm={handleConfirmSupprimer}
                title="Supprimer la variable de paie"
                message="Êtes-vous sûr de vouloir supprimer définitivement cette variable de paie ? Cette action est irréversible."
                confirmLabel={actionLoading ? 'Suppression…' : 'Supprimer définitivement'}
                cancelLabel="Annuler"
                variant="danger"
                loading={actionLoading}
            >
                {deletingVariable && (
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/60 p-3 space-y-2 text-xs">
                        <div className="flex items-center justify-between">
                            <span className="text-[var(--color-fg-muted)]">Salarié</span>
                            <span className="font-semibold text-[var(--color-fg)]">
                                {deletingVariable.nomComplet || deletingVariable.nom_complet || 'Salarié'}
                                {deletingVariable.matricule ? ` (${deletingVariable.matricule})` : ''}
                            </span>
                        </div>
                        <div className="flex items-center justify-between">
                            <span className="text-[var(--color-fg-muted)]">Type & Sens</span>
                            <span className="font-medium text-[var(--color-fg)]">
                                {deletingVariable.typeLibelle || deletingVariable.type_libelle || deletingVariable.typeMouvement}
                                {' · '}
                                <span className={deletingVariable.sens === 'GAIN' ? 'text-[var(--color-success-600)] font-semibold' : 'text-[var(--color-danger-600)] font-semibold'}>
                                    {deletingVariable.sens === 'GAIN' ? 'Gain' : 'Retenue'}
                                </span>
                            </span>
                        </div>
                        <div className="flex items-center justify-between">
                            <span className="text-[var(--color-fg-muted)]">Montant</span>
                            <span className={`font-mono font-bold text-sm ${deletingVariable.sens === 'GAIN' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                {deletingVariable.sens === 'GAIN' ? '+' : '-'}{formatAr(deletingVariable.montant)}
                            </span>
                        </div>
                        {deletingVariable.motif && (
                            <div className="pt-1.5 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)]">
                                <span className="font-medium text-[var(--color-fg)]">Motif : </span>
                                {deletingVariable.motif}
                            </div>
                        )}
                    </div>
                )}
            </ConfirmDialog>
        </div>
    );
}
