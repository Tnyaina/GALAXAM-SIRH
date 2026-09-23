import { useState, useEffect, useCallback } from 'react';
import { 
    SlidersHorizontal, 
    Plus, 
    Trash2, 
    TrendingUp, 
    TrendingDown, 
    Search
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

export default function VariablesPaieTab({ idPeriodeActive, canMutate = false, mutationDisabledReason = '', onVariableChanged }) {
    // État local : variables, filtres, formulaire et retours d'action.
    const [variables, setVariables] = useState([]);
    const [loading, setLoading] = useState(false);
    const [typesMouvements, setTypesMouvements] = useState([]);
    const [filterSens, setFilterSens] = useState('ALL');
    const [searchQuery, setSearchQuery] = useState('');
    const [feedbackMsg, setFeedbackMsg] = useState(null);

    // Modal Nouvelle Variable
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [duplicateAcknowledged, setDuplicateAcknowledged] = useState(false);
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

    const handleFormChange = (field, val) => {
        if (field === 'idEmploye' || field === 'typeMouvement') {
            setDuplicateAcknowledged(false);
        }
        setForm(prev => {
            const next = { ...prev, [field]: val };
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
            setFeedbackMsg({ type: 'error', text: 'Veuillez sélectionner un employé.' });
            return;
        }
        if (!form.typeMouvement || !typesMouvements.some(type => type.code === form.typeMouvement)) {
            setFeedbackMsg({ type: 'error', text: 'Veuillez sélectionner un type configuré.' });
            return;
        }
        if (!form.montant || Number(form.montant) <= 0) {
            setFeedbackMsg({ type: 'error', text: 'Veuillez renseigner un montant valide.' });
            return;
        }
        if (!form.motif || form.motif.trim().length < 8) {
            setFeedbackMsg({ type: 'error', text: 'Ajoutez une justification précise (au moins 8 caractères) pour conserver la trace de cette variable.' });
            return;
        }
        if (possibleDuplicate && !duplicateAcknowledged) {
            setFeedbackMsg({ type: 'warning', text: 'Une variable similaire existe déjà pour ce collaborateur sur cette période. Confirmez qu’il ne s’agit pas d’un doublon.' });
            return;
        }

        setSubmitting(true);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(form.idEmploye),
                idPeriodePaie: idPeriodeActive || null,
                typeMouvement: form.typeMouvement,
                sens: form.sens,
                montant: Number(form.montant),
                motif: form.motif || 'Variable de paie',
                statut: 'APPROUVE'
            });

            setIsModalOpen(false);
            setForm({
                idEmploye: '',
                typeMouvement: typesMouvements[0]?.code || '',
                sens: typesMouvements[0]?.sens || '',
                montant: '',
                motif: ''
            });
            setFeedbackMsg({ type: 'success', text: 'Variable de paie enregistrée avec succès.' });
            await chargerDonnees();
            onVariableChanged?.();
        } catch (err) {
            setFeedbackMsg({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la création de la variable.') });
        } finally {
            setSubmitting(false);
        }
    };

    const handleSupprimer = async (idAvance) => {
        if (!idAvance) return;
        if (!window.confirm('Êtes-vous sûr de vouloir supprimer cette variable de paie ?')) return;

        try {
            await paieService.supprimerAjustement(idAvance);
            setFeedbackMsg({ type: 'success', text: 'Variable supprimée avec succès.' });
            await chargerDonnees();
            onVariableChanged?.();
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Impossible de supprimer cette variable.')
            });
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
        BROUILLON: { label: 'Brouillon', variant: 'neutral' },
        REJETE: { label: 'Rejeté', variant: 'danger' },
    };

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
                message="Contrôle de saisie : le type détermine le sens et le régime de la ligne. Chaque variable doit être justifiée ; une ligne similaire est signalée avant enregistrement."
            />

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

                <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setIsModalOpen(true)}
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
                    onAction={canMutate ? () => setIsModalOpen(true) : undefined}
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
                                            variant={v.sens === 'GAIN' ? (['NOTE_DE_FRAIS', 'EXTRA_DEPLACEMENT'].includes(v.typeMouvement) ? 'purple' : 'primary') : 'neutral'}
                                            size="xs"
                                        >
                                            {v.sens === 'GAIN'
                                                ? (['NOTE_DE_FRAIS', 'EXTRA_DEPLACEMENT'].includes(v.typeMouvement) ? 'Exonéré / Frais réels' : 'Brut imposable (CNaPS/IRSA)')
                                                : (v.typeMouvement === 'AVANCE_SPECIALE' ? 'Net (Avance 1/10e Art. 68)' : 'Déduction Net à payer')}
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
                title="Nouvelle Variable de Paie"
                size="lg"
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
                            disabled={submitting}
                        >
                            {submitting ? 'Enregistrement...' : 'Valider la variable'}
                        </Button>
                    </div>
                }
            >
                <form onSubmit={handleSubmit} className="space-y-4 text-xs">
                    <div>
                        <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                            Collaborateur *
                        </label>
                        <EmployeSearchSelect
                            required
                            value={form.idEmploye}
                            onChange={(employeeId) => handleFormChange('idEmploye', employeeId)}
                            placeholder="Rechercher un collaborateur…"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <Select
                            label="Type de Variable"
                            required
                            value={form.typeMouvement}
                            onChange={(val) => handleFormChange('typeMouvement', val)}
                            options={typesMouvements.map(t => ({
                                value: t.code,
                                label: `${t.libelle} (${t.sens})`
                            }))}
                        />

                        <div>
                            <span className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">Sens appliqué</span>
                            <div className="h-9 px-3 flex items-center border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]">
                                {form.sens === 'GAIN' ? 'Gain (+ brut / indemnité)' : form.sens === 'RETENUE' ? 'Retenue (- net à payer)' : 'Sélectionnez un type'}
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <Input
                            label="Montant en Ariary"
                            type="number"
                            step="1000"
                            min="1000"
                            required
                            placeholder="Ex: 100000"
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
                                    onChange={(event) => setDuplicateAcknowledged(event.target.checked)}
                                    className="mt-0.5 accent-[var(--color-brand-600)]"
                                />
                                <span>Je confirme qu’il s’agit d’un élément distinct et non d’un doublon.</span>
                            </label>
                        </div>
                    )}
                </form>
            </Modal>
        </div>
    );
}
