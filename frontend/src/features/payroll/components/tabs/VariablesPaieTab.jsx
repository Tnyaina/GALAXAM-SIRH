import { useState, useEffect, useCallback } from 'react';
import { 
    SlidersHorizontal, 
    Plus, 
    Trash2, 
    TrendingUp, 
    TrendingDown, 
    Search,
    Eye,
    RefreshCw,
    Info,
    AlertCircle
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Modal from '@/components/ui/overlays/Modal';
import StatCard from '@/components/ui/data-display/StatCard';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { useAuth } from '@/context/AuthContext';
import { useLocation, useNavigate } from 'react-router-dom';

const parseAriaryAmount = (value) => {
    const normalized = String(value ?? '').trim().replace(/[\s\u00a0\u202f]/g, '').replace(',', '.');
    if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return null;
    const amount = Number(normalized);
    return Number.isFinite(amount) && amount > 0 ? amount : null;
};

export default function VariablesPaieTab({ idPeriodeActive, canMutate = false, mutationDisabledReason = '', onVariableChanged }) {
    const { hasPermission } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const canConfigure = hasPermission('ROLE_ADMIN');
    // État local : variables, filtres, formulaire et retours d'action.
    const [variables, setVariables] = useState([]);
    const [loading, setLoading] = useState(false);
    const [typesMouvements, setTypesMouvements] = useState([]);
    const [eligibleEmployees, setEligibleEmployees] = useState([]);
    const [populationError, setPopulationError] = useState('');
    const [filterSens, setFilterSens] = useState('ALL');
    const [searchQuery, setSearchQuery] = useState('');
    const [feedbackMsg, setFeedbackMsg] = useState(null);

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

    const chargerDonnees = useCallback(async () => {
        setLoading(true);
        setFeedbackMsg(null);
        try {
            const [ajustementsRes, typesRes] = await Promise.all([
                paieService.listerAjustements(idPeriodeActive || null),
                paieService.listerTypesMouvements(idPeriodeActive)
            ]);
            setVariables(Array.isArray(ajustementsRes) ? ajustementsRes : (ajustementsRes?.content || ajustementsRes?.items || []));
            const loadedTypes = Array.isArray(typesRes) ? typesRes : (typesRes?.content || typesRes?.items || []);
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
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Erreur lors du chargement des variables de paie.')
            });
        } finally {
            setLoading(false);
        }
    }, [idPeriodeActive]);

    // Effet : recharge les variables quand la période active change.
    useEffect(() => {
        chargerDonnees();
    }, [chargerDonnees]);

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

    const handleSubmit = async (e) => {
        e.preventDefault();
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

            setIsModalOpen(false);
            setForm({
                idEmploye: '',
                typeMouvement: typesMouvements[0]?.code || '',
                sens: typesMouvements[0]?.sens || '',
                montant: '',
                motif: ''
            });
            await chargerDonnees();
            setFeedbackMsg({ type: 'success', text: 'Variable enregistrée. Approuvez-la avant le calcul réel de la paie.' });
            onVariableChanged?.();
        } catch (err) {
            setFormError(getErrorMessage(err, 'Erreur lors de la création de la variable.'));
        } finally {
            setSubmitting(false);
        }
    };

    const handleSupprimer = async (idAvance) => {
        if (!idAvance) return;
        if (!window.confirm('Êtes-vous sûr de vouloir supprimer cette variable de paie ?')) return;

        try {
            await paieService.supprimerAjustement(idAvance);
            await chargerDonnees();
            setFeedbackMsg({ type: 'success', text: 'Variable supprimée avec succès.' });
            onVariableChanged?.();
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Impossible de supprimer cette variable.')
            });
        }
    };

    const handleApprouver = async (variable) => {
        const idAvance = variable.idAvance ?? variable.id_avance;
        if (!idAvance) return;
        try {
            await paieService.executerCommandeAjustement(idAvance, 'APPROVE', null, variable.version);
            await chargerDonnees();
            setFeedbackMsg({ type: 'success', text: 'Variable approuvée. Le calcul réel peut désormais la prendre en compte.' });
            onVariableChanged?.();
        } catch (err) {
            setFeedbackMsg({ type: 'error', text: getErrorMessage(err, 'Impossible d’approuver cette variable.') });
        }
    };

    const formatAr = (val) => formatMoneyAr(val);

    // Filtrage
    const listVariables = Array.isArray(variables) ? variables : [];
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
    });

    const totalGains = filteredVariables
        .filter(v => v.sens === 'GAIN')
        .reduce((sum, v) => sum + (Number(v.montant) || 0), 0);

    const totalRetenues = filteredVariables
        .filter(v => v.sens === 'RETENUE')
        .reduce((sum, v) => sum + (Number(v.montant) || 0), 0);

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
    const previewBaseIrsa = previewBulletin?.revenuNetImposable ?? previewBulletin?.revenu_net_imposable;
    const previewIrsa = previewBulletin?.irsaNet ?? previewBulletin?.irsa_net;
    const previewIsi = previewBulletin?.retenueIsi5 ?? previewBulletin?.retenue_isi_5;
    const previewIndemnitesNet = previewBulletin?.totalIndemnitesNonImposables ?? previewBulletin?.total_indemnites_non_imposables;
    const previewRetenues = previewBulletin?.totalRetenuesAcomptePret ?? previewBulletin?.total_retenues_acompte_pret;
    const previewAvantArrondi = previewBulletin?.netAvantArrondi ?? previewBulletin?.net_avant_arrondi;
    const previewNetAvantRetenues = previewNetImposable == null ? null
        : Number(previewNetImposable) - Number(previewIrsa || 0) - Number(previewIsi || 0) + Number(previewIndemnitesNet || 0);
    const previewLines = Array.isArray(bulletinPreview?.lignes) ? bulletinPreview.lignes : [];
    const employeeVariables = listVariables.filter(variable =>
        Number(variable.idEmploye ?? variable.id_employe) === Number(form.idEmploye)
        && !['REJETE', 'ANNULE', 'CANCELLED'].includes(variable.statut)
    );

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

            {/* Cartes récapitulatives via StatCard */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <StatCard
                    title="Primes & Gains"
                    value={`+${formatAr(totalGains)}`}
                    subtext="Bonus et indemnités brutes"
                    icon={<TrendingUp size={18} />}
                    color="success"
                />
                <StatCard
                    title="Acomptes & Retenues"
                    value={`-${formatAr(totalRetenues)}`}
                    subtext="Avances et retenues sur salaire"
                    icon={<TrendingDown size={18} />}
                    color="danger"
                />
                <StatCard
                    title="Lignes Enregistrées"
                    value={`${filteredVariables.length} variable(s)`}
                    subtext="Éléments variables du mois"
                    icon={<SlidersHorizontal size={18} />}
                    color="brand"
                />
            </div>

            <Alert
                type="info"
                message="Les repas et déplacements ordinaires proviennent du pointage. Saisissez ici les primes exceptionnelles et autres variables justifiées. Le régime fiscal et social vient de la rubrique configurée."
            />

            {listVariables.some(variable => variable.statut === 'EN_ATTENTE') && (
                <Alert type="warning" message="Des variables attendent votre approbation. Le calcul réel de la paie reste bloqué jusqu’à leur approbation ou suppression." />
            )}

            {/* Barre de filtre et Action */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                <div className="flex flex-wrap items-center gap-2.5 w-full sm:w-auto flex-1">
                    <div className="flex-1 sm:w-72 min-w-[200px]">
                        <Input
                            type="search"
                            placeholder="Rechercher par collaborateur, matricule, motif…"
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            iconLeft={<Search size={14} />}
                            aria-label="Rechercher une variable de paie"
                        />
                    </div>
                    <div className="w-56">
                        <Select
                            value={filterSens}
                            onChange={(val) => setFilterSens(val)}
                            options={[
                                { value: 'ALL', label: 'Tous les éléments (Gains & Retenues)' },
                                { value: 'GAIN', label: '+ Primes & Gains' },
                                { value: 'RETENUE', label: '- Acomptes & Retenues' },
                            ]}
                            aria-label="Filtrer par sens"
                        />
                    </div>
                </div>

                {canConfigure && <Button variant="secondary" size="sm" onClick={() => navigate('/admin/parametres?module=paie&tab=types_variables', { state: { from: location.pathname + location.search } })} iconLeft={<SlidersHorizontal size={14} />}>
                    Configurer les types
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
                    Nouvelle Variable
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
                <Table aria-label="Variables de paie">
                    <Thead>
                        <Tr>
                            <Th>Date</Th>
                            <Th>Matricule</Th>
                            <Th>Collaborateur</Th>
                            <Th>Type d'élément</Th>
                            <Th>Sens</Th>
                            <Th>Régime Fiscal / Social</Th>
                            <Th className="text-right">Montant</Th>
                            <Th>Motif</Th>
                            <Th className="text-center">Statut</Th>
                            <Th className="text-right">Action</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {filteredVariables.map((v, index) => {
                            const idAvance = v.idAvance ?? v.id_avance;
                            const dateDemande = v.dateDemande || v.date_demande;
                            const nomComplet = v.nomComplet || v.nom_complet;
                            const typeLibelle = v.typeLibelle || v.type_libelle || v.typeMouvement || v.type_mouvement;
                            const statInfo = statutLabels[v.statut] || { label: v.statut || 'Enregistré', variant: 'neutral' };
                            const configuredType = typesMouvements.find(type => type.code === v.typeMouvement);
                            const taxable = v.imposable ?? configuredType?.imposable;
                            const social = v.cotisable ?? configuredType?.cotisable;
                            return (
                                <Tr key={idAvance || index} className="hover:bg-[var(--color-bg-subtle)]/50 transition-colors">
                                    <Td className="font-mono text-[11px] text-[var(--color-fg-muted)]">
                                        {dateDemande || '—'}
                                    </Td>
                                    <Td className="font-mono font-bold text-[var(--color-fg)]">
                                        {v.matricule || '—'}
                                    </Td>
                                    <Td className="font-semibold text-[var(--color-fg)] whitespace-nowrap">
                                        {nomComplet || 'Collaborateur'}
                                    </Td>
                                    <Td>
                                        <span className="font-medium text-[var(--color-fg)] block">
                                            {typeLibelle || 'Variable'}
                                        </span>
                                    </Td>
                                    <Td>
                                        <Badge
                                            variant={v.sens === 'GAIN' ? 'success' : 'danger'}
                                            size="xs"
                                        >
                                             {v.sens === 'GAIN' ? '+ Gain' : '- Retenue'}
                                        </Badge>
                                    </Td>
                                    <Td>
                                        <Badge
                                            variant={v.sens === 'GAIN' ? (taxable ? 'primary' : 'purple') : 'neutral'}
                                            size="xs"
                                        >
                                            {regimeLabel(v.sens, taxable, social)}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right font-mono font-bold whitespace-nowrap">
                                        <span className={v.sens === 'GAIN' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'}>
                                            {v.sens === 'GAIN' ? '+' : '-'}{formatAr(v.montant)}
                                        </span>
                                    </Td>
                                    <Td className="text-[var(--color-fg-muted)] max-w-xs truncate" title={v.motif}>
                                        {v.motif || '—'}
                                    </Td>
                                    <Td className="text-center">
                                        <Badge variant={statInfo.variant} size="xs" dot>
                                            {statInfo.label}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        {canMutate && v.statut === 'EN_ATTENTE' && idAvance && (
                                            <Button variant="secondary" size="xs" onClick={() => handleApprouver(v)}>
                                                Approuver
                                            </Button>
                                        )}
                                        {canMutate && v.statut !== 'IMPUTE_PAIE' && idAvance && (
                                            <button
                                                type="button"
                                                onClick={() => handleSupprimer(idAvance)}
                                                className="p-1 hover:bg-[var(--color-danger-500)]/10 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-600)] rounded-none transition-colors"
                                                title="Supprimer cette variable"
                                                aria-label="Supprimer"
                                            >
                                                <Trash2 size={13} />
                                            </button>
                                        )}
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}

            {/* Modale d'ajout de variable */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => setIsModalOpen(false)}
                title="Préparer une variable pour un salarié"
                size="full"
                className="max-h-[94vh]"
                icon={<SlidersHorizontal size={18} />}
                footer={
                    <div className="flex items-center justify-end gap-2 w-full">
                        <Button variant="secondary" size="sm" onClick={() => setIsModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button 
                            variant="primary" 
                            size="sm" 
                            onClick={handleSubmit}
                            disabled={submitting || (possibleDuplicate && !duplicateAcknowledged)}
                        >
                            {submitting ? 'Enregistrement...' : 'Enregistrer pour le calcul'}
                        </Button>
                    </div>
                }
            >
                <form onSubmit={handleSubmit} className="text-xs">
                    {formError && <div role="alert" className="mb-4 flex items-start gap-2 border border-[var(--color-danger-500)]/35 bg-[var(--color-danger-500)]/10 p-3 text-[var(--color-danger-700)]"><AlertCircle size={14} className="mt-0.5 shrink-0" />{formError}</div>}
                    <div className="grid grid-cols-1 lg:grid-cols-[1.05fr_0.95fr] gap-5 items-start">
                      <div className="space-y-4">
                        <div className="border-l-2 border-[var(--color-brand-500)] pl-3">
                            <h4 className="text-sm font-bold text-[var(--color-fg)]">1. Choisir le salarié</h4>
                            <p className="mt-0.5 text-[11px] text-[var(--color-fg-muted)]">Consultez son aperçu de paie avant d’ajouter une variable.</p>
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
                            <h4 className="text-sm font-bold text-[var(--color-fg)]">2. Vérifier puis décrire la variable</h4>
                            <p className="mt-0.5 text-[11px] text-[var(--color-fg-muted)]">Vérifiez l’aperçu à droite. Le sens et le traitement proviennent du catalogue de paie.</p>
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
                        <Input
                            label="Montant en Ariary"
                            type="text"
                            inputMode="decimal"
                            required
                            placeholder="Ex. 1 250,50"
                            hint="Montant positif en Ar."
                            value={form.montant}
                            onChange={(e) => handleFormChange('montant', e.target.value)}
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
                            <label className="flex items-start gap-2 text-[11px] text-[var(--color-fg)] cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={duplicateAcknowledged}
                                    onChange={(event) => { setDuplicateAcknowledged(event.target.checked); setFormError(''); }}
                                    className="mt-0.5 accent-[var(--color-brand-600)]"
                                />
                                <span>Je confirme qu’il s’agit d’un élément distinct et non d’un doublon.</span>
                            </label>
                        </div>
                        )}
                      </div>

                      <aside className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] min-h-[320px] p-4 space-y-4">
                        <div className="flex items-start justify-between gap-3">
                            <div>
                                <div className="flex items-center gap-2 text-sm font-bold text-[var(--color-fg)]">
                                    <Eye size={15} className="text-[var(--color-brand-600)]" /> Aperçu du bulletin
                                </div>
                                <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">Simulation avant l’ajout de cette variable.</p>
                            </div>
                            {form.idEmploye && (
                                <button type="button" onClick={() => setPreviewRevision(revision => revision + 1)} disabled={previewLoading} className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--color-brand-700)] hover:underline disabled:opacity-50">
                                    <RefreshCw size={12} /> Actualiser
                                </button>
                            )}
                        </div>

                        {!form.idEmploye ? (
                            <div className="flex min-h-[210px] flex-col items-center justify-center text-center text-[var(--color-fg-muted)]">
                                <SlidersHorizontal size={24} className="mb-2 opacity-50" />
                                <p className="font-semibold">Sélectionnez un salarié</p>
                                <p className="mt-1 max-w-[230px] text-[11px]">Son bulletin provisoire apparaîtra ici pour vous aider à décider.</p>
                            </div>
                        ) : previewLoading ? (
                            <div className="flex min-h-[210px] items-center justify-center gap-2 text-[var(--color-fg-muted)]">
                                <RefreshCw size={15} className="animate-spin" /> Calcul de l’aperçu…
                            </div>
                        ) : previewError ? (
                            <div className="flex gap-2 border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/10 p-3 text-[11px] text-[var(--color-fg)]">
                                <AlertCircle size={14} className="mt-0.5 shrink-0 text-[var(--color-warning-600)]" />
                                <span>{previewError}</span>
                            </div>
                        ) : previewBulletin ? (
                            <>
                                <div className="grid grid-cols-2 gap-2">
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                                        <span className="block text-[10px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Salaire de base</span>
                                        <strong className="mt-1 block font-mono text-sm text-[var(--color-fg)]">{formatAr(previewBase)}</strong>
                                    </div>
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                                        <span className="block text-[10px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Brut provisoire</span>
                                        <strong className="mt-1 block font-mono text-sm text-[var(--color-fg)]">{formatAr(previewGross)}</strong>
                                    </div>
                                </div>
                                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3 space-y-2" aria-label="Étapes du calcul provisoire">
                                    <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Du brut au net</p>
                                    {[
                                        ['Brut calculé', previewGross],
                                        ['Cotisations salariales', previewCotisations, true],
                                        ['Net imposable', previewNetImposable],
                                        ['Base retenue pour l’IRSA', previewBaseIrsa],
                                        ['IRSA / ISI', Number(previewIrsa || 0) + Number(previewIsi || 0), true],
                                        ['Gains ajoutés après impôt', previewIndemnitesNet],
                                        ['Net avant retenues personnelles', previewNetAvantRetenues],
                                        ['Acomptes et autres retenues', previewRetenues, true],
                                        ['Net avant arrondi', previewAvantArrondi],
                                    ].map(([label, amount, deduction]) => amount != null && (
                                        <div key={label} className="flex items-baseline justify-between gap-3 text-[11px]">
                                            <span className="text-[var(--color-fg-muted)]">{label}</span>
                                            <strong className="shrink-0 font-mono text-[var(--color-fg)]">{deduction && Number(amount) > 0 ? '−' : ''}{formatAr(amount)}</strong>
                                        </div>
                                    ))}
                                </div>
                                <div className="border border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/5 p-3">
                                    <span className="block text-[10px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Net provisoire à payer · après arrondi</span>
                                    <strong className="mt-1 block font-mono text-xl text-[var(--color-success-700)]">{formatAr(previewNet)}</strong>
                                </div>
                                {employeeVariables.length > 0 && (
                                    <div className="space-y-1">
                                        <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Variables déjà enregistrées ({employeeVariables.length})</p>
                                        {employeeVariables.slice(0, 3).map((variable, index) => (
                                            <div key={variable.idAvance ?? variable.id_avance ?? index} className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-1.5 text-[11px]">
                                                <span className="truncate text-[var(--color-fg-muted)]">{variable.typeLibelle || variable.type_libelle || variable.typeMouvement || variable.type_mouvement}</span>
                                                <strong className="shrink-0 font-mono text-[var(--color-fg)]">{variable.sens === 'RETENUE' ? '−' : '+'}{formatAr(variable.montant)}</strong>
                                            </div>
                                        ))}
                                        {employeeVariables.length > 3 && <p className="text-[10px] text-[var(--color-fg-muted)]">Et {employeeVariables.length - 3} autre(s) variable(s).</p>}
                                    </div>
                                )}
                                {previewLines.length > 0 && (
                                    <div className="space-y-1">
                                        <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Principales lignes calculées</p>
                                        {previewLines.slice(0, 4).map((line, index) => (
                                            <div key={`${line.codeRubrique || line.code_rubrique || 'ligne'}-${index}`} className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-1.5 text-[11px]">
                                                <span className="truncate text-[var(--color-fg-muted)]">{line.libelleRubrique || line.libelle_rubrique || line.codeRubrique || line.code_rubrique || 'Élément de paie'}</span>
                                                <strong className="shrink-0 font-mono text-[var(--color-fg)]">{formatAr(line.montantSalarial ?? line.montant_salarial)}</strong>
                                            </div>
                                        ))}
                                    </div>
                                )}
                                <div className="flex gap-2 border-t border-[var(--color-border-subtle)] pt-3 text-[10px] leading-relaxed text-[var(--color-fg-muted)]">
                                    <Info size={13} className="mt-0.5 shrink-0" />
                                    <span>Cette simulation n’enregistre rien. Après l’ajout, l’étape Calcul recalculera le bulletin avec la variable et ses règles fiscales et sociales.</span>
                                </div>
                            </>
                        ) : (
                            <div className="flex min-h-[210px] items-center justify-center text-[11px] text-[var(--color-fg-muted)]">Aucun résultat d’aperçu.</div>
                        )}
                      </aside>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
