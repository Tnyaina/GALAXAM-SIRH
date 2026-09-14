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
import Modal from '@/components/ui/overlays/Modal';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/api/paie/paieService';
import employeService from '@/api/employe/employeService';

export default function VariablesPaieTab({ idPeriodeActive }) {
    const [variables, setVariables] = useState([]);
    const [loading, setLoading] = useState(false);
    const [typesMouvements, setTypesMouvements] = useState([]);
    const [employes, setEmployes] = useState([]);
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
                text: err.response?.data?.error || 'Erreur lors du chargement des variables de paie.'
            });
        } finally {
            setLoading(false);
        }
    }, [idPeriodeActive]);

    const chargerEmployes = useCallback(async () => {
        try {
            const res = await employeService.getEmployes({ size: 1000, actif: true });
            setEmployes(Array.isArray(res) ? res : (res?.content || res?.items || []));
        } catch (err) {
            console.error('Erreur chargement employés :', err);
        }
    }, []);

    useEffect(() => {
        chargerDonnees();
        chargerEmployes();
    }, [chargerDonnees, chargerEmployes]);

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
            alert(err.response?.data?.error || 'Erreur lors de la création de la variable.');
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
                text: err.response?.data?.error || 'Impossible de supprimer cette variable.'
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

    return (
        <div className="space-y-4">
            {feedbackMsg && (
                <div className={`p-3 text-xs flex items-center gap-2 border ${
                    feedbackMsg.type === 'success' 
                        ? 'bg-emerald-50 border-emerald-200 text-emerald-800' 
                        : 'bg-rose-50 border-rose-200 text-rose-800'
                }`}>
                    {feedbackMsg.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                    <span>{feedbackMsg.text}</span>
                </div>
            )}

            {/* Cartes récapitulatives */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 flex items-center justify-between">
                    <div>
                        <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Total Primes & Bonus (Gains)
                        </span>
                        <span className="text-base font-bold font-mono text-emerald-600 block mt-1">
                            +{formatAr(totalGains)}
                        </span>
                    </div>
                    <div className="w-8 h-8 rounded-none bg-emerald-500/10 text-emerald-600 flex items-center justify-center border border-emerald-500/20">
                        <TrendingUp size={16} />
                    </div>
                </div>

                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 flex items-center justify-between">
                    <div>
                        <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Total Acomptes & Déductions
                        </span>
                        <span className="text-base font-bold font-mono text-rose-600 block mt-1">
                            -{formatAr(totalRetenues)}
                        </span>
                    </div>
                    <div className="w-8 h-8 rounded-none bg-rose-500/10 text-rose-600 flex items-center justify-center border border-rose-500/20">
                        <TrendingDown size={16} />
                    </div>
                </div>

                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 flex items-center justify-between">
                    <div>
                        <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Lignes Enregistrées
                        </span>
                        <span className="text-base font-bold font-mono text-[var(--color-fg)] block mt-1">
                            {filteredVariables.length} variable(s)
                        </span>
                    </div>
                    <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] flex items-center justify-center border border-[var(--color-brand-500)]/20">
                        <SlidersHorizontal size={16} />
                    </div>
                </div>
            </div>

            {/* Barre de filtre et Action */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-2.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                <div className="flex items-center gap-2 w-full sm:w-auto">
                    <div className="relative flex-1 sm:w-64">
                        <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                        <input
                            type="text"
                            placeholder="Rechercher employé, type, motif..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full text-xs pl-8 pr-3 py-1.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none focus:outline-none"
                        />
                    </div>

                    <select
                        value={filterSens}
                        onChange={(e) => setFilterSens(e.target.value)}
                        className="text-xs py-1.5 px-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none focus:outline-none"
                    >
                        <option value="ALL">Tous les sens</option>
                        <option value="GAIN">Primes & Gains</option>
                        <option value="RETENUE">Acomptes & Retenues</option>
                    </select>
                </div>

                <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setIsModalOpen(true)}
                    className="flex items-center gap-1.5 w-full sm:w-auto justify-center"
                >
                    <Plus size={14} />
                    Nouvelle Variable
                </Button>
            </div>

            {/* Table des variables */}
            {loading ? (
                <SkeletonTable rows={6} cols={6} />
            ) : (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                        <thead>
                            <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)]">
                                <th className="p-3 font-medium">Date</th>
                                <th className="p-3 font-medium">Matricule</th>
                                <th className="p-3 font-medium">Collaborateur</th>
                                <th className="p-3 font-medium">Type de Variable</th>
                                <th className="p-3 font-medium">Sens</th>
                                <th className="p-3 font-medium text-right">Montant</th>
                                <th className="p-3 font-medium">Motif</th>
                                <th className="p-3 font-medium text-center">Statut</th>
                                <th className="p-3 font-medium text-right">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[var(--color-border-subtle)]">
                            {filteredVariables.length === 0 ? (
                                <tr>
                                    <td colSpan={9} className="p-8 text-center text-xs text-[var(--color-fg-muted)] italic">
                                        Aucune variable enregistrée pour ces critères.
                                    </td>
                                </tr>
                            ) : (
                                filteredVariables.map((v) => (
                                    <tr key={v.id_avance} className="hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                        <td className="p-3 font-mono text-[11px] text-[var(--color-fg-muted)]">
                                            {v.date_demande || '-'}
                                        </td>
                                        <td className="p-3 font-mono font-bold text-[var(--color-fg)]">
                                            {v.matricule}
                                        </td>
                                        <td className="p-3 font-semibold text-[var(--color-fg)]">
                                            {v.nom_complet}
                                        </td>
                                        <td className="p-3">
                                            <span className="font-medium text-[var(--color-fg)] block">
                                                {v.type_libelle || v.type_mouvement}
                                            </span>
                                            <span className="text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                {v.type_code || v.type_mouvement}
                                            </span>
                                        </td>
                                        <td className="p-3">
                                            <span className={`px-2 py-0.5 text-[10px] font-semibold border ${
                                                v.sens === 'GAIN'
                                                    ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                                                    : 'bg-rose-50 text-rose-700 border-rose-200'
                                            }`}>
                                                {v.sens === 'GAIN' ? '+ GAIN' : '- RETENUE'}
                                            </span>
                                        </td>
                                        <td className="p-3 text-right font-mono font-bold">
                                            <span className={v.sens === 'GAIN' ? 'text-emerald-600' : 'text-rose-600'}>
                                                {v.sens === 'GAIN' ? '+' : '-'}{formatAr(v.montant)}
                                            </span>
                                        </td>
                                        <td className="p-3 text-[var(--color-fg-muted)] max-w-xs truncate" title={v.motif}>
                                            {v.motif || '-'}
                                        </td>
                                        <td className="p-3 text-center">
                                            <span className={`px-2 py-0.5 text-[10px] font-mono border ${
                                                v.statut === 'IMPUTE_PAIE'
                                                    ? 'bg-blue-50 text-blue-700 border-blue-200'
                                                    : 'bg-amber-50 text-amber-700 border-amber-200'
                                            }`}>
                                                {v.statut}
                                            </span>
                                        </td>
                                        <td className="p-3 text-right">
                                            {v.statut !== 'IMPUTE_PAIE' && (
                                                <button
                                                    onClick={() => handleSupprimer(v.id_avance)}
                                                    className="p-1 text-rose-500 hover:text-rose-700 hover:bg-rose-50 cursor-pointer"
                                                    title="Supprimer cette variable"
                                                >
                                                    <Trash2 size={15} />
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
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
                        <select
                            required
                            className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                            value={form.idEmploye}
                            onChange={(e) => handleFormChange('idEmploye', e.target.value)}
                        >
                            <option value="">-- Sélectionner un collaborateur --</option>
                            {(Array.isArray(employes) ? employes : []).map((emp) => (
                                <option key={emp.idEmploye || emp.id_employe} value={emp.idEmploye || emp.id_employe}>
                                    [{emp.matricule}] {emp.nom} {emp.prenom}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Type de Variable *
                            </label>
                            <select
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                                value={form.typeMouvement}
                                onChange={(e) => handleFormChange('typeMouvement', e.target.value)}
                            >
                                <option value="PRIM_EXC">Prime exceptionnelle (Gain)</option>
                                <option value="ACOMPTE_15">Acompte quinzaine (Retenue)</option>
                                <option value="AVANCE_SPEC">Avance spéciale (Retenue)</option>
                                <option value="DED_DIV">Déduction diverse (Retenue)</option>
                                {(Array.isArray(typesMouvements) ? typesMouvements : []).map((t) => (
                                    <option key={t.idTypeMouvement || t.code} value={t.code}>
                                        {t.libelle} ({t.sens})
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Sens *
                            </label>
                            <select
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                                value={form.sens}
                                onChange={(e) => handleFormChange('sens', e.target.value)}
                            >
                                <option value="GAIN">Gain (+ brut)</option>
                                <option value="RETENUE">Retenue (- net)</option>
                            </select>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Montant en Ariary *
                            </label>
                            <input
                                type="number"
                                step="1000"
                                min="1000"
                                required
                                placeholder="Ex: 100000"
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none font-mono"
                                value={form.montant}
                                onChange={(e) => handleFormChange('montant', e.target.value)}
                            />
                        </div>

                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Motif / Référence
                            </label>
                            <input
                                type="text"
                                placeholder="Ex: Prime transport spéciale projet"
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                                value={form.motif}
                                onChange={(e) => handleFormChange('motif', e.target.value)}
                            />
                        </div>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
