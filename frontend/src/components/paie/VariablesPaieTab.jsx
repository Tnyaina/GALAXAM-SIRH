import { useState, useEffect, useCallback } from 'react';
import { 
    SlidersHorizontal, 
    Plus, 
    Trash2, 
    TrendingUp, 
    TrendingDown, 
    AlertCircle, 
    CheckCircle2, 
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
import paieService from '@/api/paie/paieService';
import { getErrorMessage } from '@/api/apiClient';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';

export default function VariablesPaieTab({ idPeriodeActive }) {
    const [variables, setVariables] = useState([]);
    const [loading, setLoading] = useState(false);
    const [typesMouvements, setTypesMouvements] = useState([]);
    const [filterSens, setFilterSens] = useState('ALL');
    const [searchQuery, setSearchQuery] = useState('');
    const [feedbackMsg, setFeedbackMsg] = useState(null);

    // Modal Nouvelle Variable
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [form, setForm] = useState({
        idEmploye: '',
        typeMouvement: 'PRIM_EXC',
        sens: 'GAIN',
        montant: '',
        motif: ''
    });

    const chargerDonnees = useCallback(async () => {
        setLoading(true);
        setFeedbackMsg(null);
        try {
            const [ajustementsRes, typesRes] = await Promise.all([
                paieService.listerAjustements(idPeriodeActive || null),
                paieService.listerTypesMouvements()
            ]);
            setVariables(Array.isArray(ajustementsRes) ? ajustementsRes : (ajustementsRes?.content || ajustementsRes?.items || []));
            setTypesMouvements(Array.isArray(typesRes) ? typesRes : (typesRes?.content || typesRes?.items || []));
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Erreur lors du chargement des variables de paie.')
            });
        } finally {
            setLoading(false);
        }
    }, [idPeriodeActive]);

    useEffect(() => {
        chargerDonnees();
    }, [chargerDonnees]);

    const handleFormChange = (field, val) => {
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
            alert('Veuillez sélectionner un employé.');
            return;
        }
        if (!form.montant || Number(form.montant) <= 0) {
            alert('Veuillez renseigner un montant valide.');
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
                typeMouvement: 'PRIM_EXC',
                sens: 'GAIN',
                montant: '',
                motif: ''
            });
            setFeedbackMsg({ type: 'success', text: 'Variable de paie enregistrée avec succès.' });
            await chargerDonnees();
        } catch (err) {
            alert(getErrorMessage(err, 'Erreur lors de la création de la variable.'));
        } finally {
            setSubmitting(false);
        }
    };

    const handleSupprimer = async (idAvance) => {
        if (!window.confirm('Êtes-vous sûr de vouloir supprimer cette variable de paie ?')) return;

        try {
            await paieService.supprimerAjustement(idAvance);
            setFeedbackMsg({ type: 'success', text: 'Variable supprimée avec succès.' });
            await chargerDonnees();
        } catch (err) {
            setFeedbackMsg({
                type: 'error',
                text: getErrorMessage(err, 'Impossible de supprimer cette variable.')
            });
        }
    };

    const formatAr = (val) => {
        if (!val) return '0 Ar';
        return Number(val).toLocaleString('fr-MG') + ' Ar';
    };

    // Filtrage
    const listVariables = Array.isArray(variables) ? variables : [];
    const filteredVariables = listVariables.filter(v => {
        const matchSens = filterSens === 'ALL' || v.sens === filterSens;
        const q = searchQuery.toLowerCase();
        const matchSearch = !q || 
            (v.nom_complet && v.nom_complet.toLowerCase().includes(q)) ||
            (v.matricule && v.matricule.toLowerCase().includes(q)) ||
            (v.type_libelle && v.type_libelle.toLowerCase().includes(q)) ||
            (v.motif && v.motif.toLowerCase().includes(q));
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
                    actionLabel="Ajouter une variable"
                    onAction={() => setIsModalOpen(true)}
                    actionIcon={<Plus size={14} />}
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
                            <Th className="text-right">Montant</Th>
                            <Th>Motif</Th>
                            <Th className="text-center">Statut</Th>
                            <Th className="text-right">Action</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {filteredVariables.map((v) => {
                            const statInfo = statutLabels[v.statut] || { label: v.statut || 'Enregistré', variant: 'neutral' };
                            return (
                                <Tr key={v.id_avance} className="hover:bg-[var(--color-bg-subtle)]/50 transition-colors">
                                    <Td className="font-mono text-[11px] text-[var(--color-fg-muted)]">
                                        {v.date_demande || '—'}
                                    </Td>
                                    <Td className="font-mono font-bold text-[var(--color-fg)]">
                                        {v.matricule || '—'}
                                    </Td>
                                    <Td className="font-semibold text-[var(--color-fg)] whitespace-nowrap">
                                        {v.nom_complet || 'Collaborateur'}
                                    </Td>
                                    <Td>
                                        <span className="font-medium text-[var(--color-fg)] block">
                                            {v.type_libelle || v.type_mouvement}
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
                                    <Td className="text-right font-mono font-bold whitespace-nowrap">
                                        <span className={v.sens === 'GAIN' ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}>
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
                                        {v.statut !== 'IMPUTE_PAIE' && (
                                            <button
                                                type="button"
                                                onClick={() => handleSupprimer(v.id_avance)}
                                                className="p-1 hover:bg-rose-500/10 text-[var(--color-fg-muted)] hover:text-rose-600 rounded-none transition-colors"
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
                            options={[
                                { value: 'PRIM_EXC', label: 'Prime exceptionnelle (Gain)' },
                                { value: 'ACOMPTE_15', label: 'Acompte quinzaine (Retenue)' },
                                { value: 'AVANCE_SPEC', label: 'Avance spéciale (Retenue)' },
                                { value: 'DED_DIV', label: 'Déduction diverse (Retenue)' },
                                ...(Array.isArray(typesMouvements) ? typesMouvements : [])
                                    .filter(t => !['PRIM_EXC', 'ACOMPTE_15', 'AVANCE_SPEC', 'DED_DIV'].includes(t.code))
                                    .map(t => ({ value: t.code, label: `${t.libelle} (${t.sens})` }))
                            ]}
                        />

                        <Select
                            label="Sens"
                            required
                            value={form.sens}
                            onChange={(val) => handleFormChange('sens', val)}
                            options={[
                                { value: 'GAIN', label: 'Gain (+ brut imposable)' },
                                { value: 'RETENUE', label: 'Retenue (- net à payer)' },
                            ]}
                        />
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
                            placeholder="Ex: Prime transport spéciale projet"
                            value={form.motif}
                            onChange={(e) => handleFormChange('motif', e.target.value)}
                        />
                    </div>
                </form>
            </Modal>
        </div>
    );
}
