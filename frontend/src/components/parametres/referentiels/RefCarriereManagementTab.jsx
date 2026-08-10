import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, ShieldAlert, Award, CreditCard, ArrowUpRight } from 'lucide-react';
import contratService from '@/api/employe/contratService';
import Input, { Textarea } from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';

export default function RefCarriereManagementTab() {
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);
    const [motifsMouvement, setMotifsMouvement] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const [showModalCp, setShowModalCp] = useState(false);
    const [selectedCp, setSelectedCp] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const [formDataCp, setFormDataCp] = useState({ code: '', libelle: '', description: '', actif: true });

    const loadData = async () => {
        setLoading(true);
        setError('');
        try {
            const [cpList, mpList, mmList] = await Promise.all([
                contratService.getCategoriesPro(false),
                contratService.getModesPaie(),
                contratService.getMotifsMouvement()
            ]);
            setCategoriesPro(cpList || []);
            setModesPaie(mpList || []);
            setMotifsMouvement(mmList || []);
        } catch (err) {
            console.error('Erreur chargement référentiels carrière:', err);
            setError('Impossible de charger les référentiels de carrière.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleOpenCreateCp = () => {
        setSelectedCp(null);
        setFormDataCp({ code: '', libelle: '', description: '', actif: true });
        setError('');
        setShowModalCp(true);
    };

    const handleOpenEditCp = (cp) => {
        setSelectedCp(cp);
        setFormDataCp({
            code: cp.code || '',
            libelle: cp.libelle || '',
            description: cp.description || '',
            actif: cp.actif !== undefined ? cp.actif : true
        });
        setError('');
        setShowModalCp(true);
    };

    const handleSubmitCp = async (e) => {
        e.preventDefault();
        setError('');
        if (!formDataCp.code.trim() || !formDataCp.libelle.trim()) {
            setError('Le code et le libellé sont obligatoires.');
            return;
        }

        setSubmitting(true);
        try {
            if (selectedCp) {
                await contratService.updateCategoriePro(selectedCp.idCategorieProfessionnelle, formDataCp);
                setActionSuccess(`Catégorie "${formDataCp.libelle}" modifiée avec succès.`);
            } else {
                await contratService.createCategoriePro(formDataCp);
                setActionSuccess(`Nouvelle catégorie "${formDataCp.libelle}" créée.`);
            }
            setShowModalCp(false);
            loadData();
        } catch (err) {
            console.error('Erreur enregistrement catégorie:', err);
            setError(err.response?.data || "Erreur lors de l'enregistrement.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="space-y-8">
            {/* Action Success / Error */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && !showModalCp && (
                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Section 1: Catégories Professionnelles */}
            <div className="space-y-4">
                <div className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm gap-4">
                    <div>
                        <h2 className="text-base font-bold text-ink-900 dark:text-white flex items-center gap-2">
                            <Award size={18} className="text-brand-500" />
                            Catégories Professionnelles ({categoriesPro.length})
                        </h2>
                        <p className="text-xs text-ink-400">Classification socioprofessionnelle des emplois (Cadres, Agents de maîtrise, Employés, Ouvriers)</p>
                    </div>
                    <Button
                        onClick={handleOpenCreateCp}
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                    >
                        Nouvelle Catégorie
                    </Button>
                </div>

                {loading ? (
                    <div className="flex justify-center items-center py-8">
                        <Loading size="md" label="Chargement des catégories..." />
                    </div>
                ) : (
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                        <div className="overflow-x-auto">
                            <table className="w-full border-collapse text-left text-sm">
                                <thead>
                                    <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                        <th className="px-6 py-4">Code</th>
                                        <th className="px-6 py-4">Libellé</th>
                                        <th className="px-6 py-4">Description</th>
                                        <th className="px-6 py-4">Statut</th>
                                        <th className="px-6 py-4 text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                    {categoriesPro.map((cp) => (
                                        <tr key={cp.idCategorieProfessionnelle} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-6 py-4 font-mono font-bold text-brand-500">{cp.code}</td>
                                            <td className="px-6 py-4 font-semibold">{cp.libelle}</td>
                                            <td className="px-6 py-4 text-xs text-ink-400">{cp.description || '-'}</td>
                                            <td className="px-6 py-4">
                                                {cp.actif ? (
                                                    <Badge variant="success" size="sm">Actif</Badge>
                                                ) : (
                                                    <Badge variant="default" size="sm">Inactif</Badge>
                                                )}
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <button
                                                    onClick={() => handleOpenEditCp(cp)}
                                                    className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                    title="Modifier"
                                                >
                                                    <Edit2 size={16} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                )}
            </div>

            {/* Section 2: Modes de calcul de paie et Motifs */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Modes de Calcul Paie */}
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-6 rounded-sm shadow-sm space-y-4">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                        <CreditCard size={16} className="text-brand-500" />
                        Modes de Calcul de Paie ({modesPaie.length})
                    </h3>
                    <div className="divide-y divide-ink-100 dark:divide-ink-800 border border-ink-150 dark:border-ink-700 rounded-sm">
                        {modesPaie.map((mp) => (
                            <div key={mp.idModeCalculPaie} className="p-3 flex items-center justify-between">
                                <div>
                                    <p className="text-xs font-bold text-ink-900 dark:text-white">{mp.libelle} <span className="font-mono text-ink-400">({mp.code})</span></p>
                                    <p className="text-[11px] text-ink-400">{mp.description}</p>
                                </div>
                                <Badge variant="primary" size="sm">Standard</Badge>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Motifs de Mouvement */}
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-6 rounded-sm shadow-sm space-y-4">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                        <ArrowUpRight size={16} className="text-brand-500" />
                        Motifs de Mouvements / Carrière ({motifsMouvement.length})
                    </h3>
                    <div className="divide-y divide-ink-100 dark:divide-ink-800 border border-ink-150 dark:border-ink-700 rounded-sm max-h-[220px] overflow-y-auto">
                        {motifsMouvement.map((mm) => (
                            <div key={mm.idMotifMouvement} className="p-3 flex items-center justify-between">
                                <p className="text-xs font-semibold text-ink-900 dark:text-white">{mm.libelle}</p>
                                <span className="text-[10px] font-mono font-bold text-brand-500 uppercase">{mm.code}</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Modale d'ajout/modification Catégorie Pro */}
            {showModalCp && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {selectedCp ? 'Modifier la Catégorie Professionnelle' : 'Nouvelle Catégorie Professionnelle'}
                        </div>
                        <form onSubmit={handleSubmitCp}>
                            <div className="p-6 space-y-4">
                                {error && (
                                    <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <ShieldAlert size={14} />
                                        {error}
                                    </div>
                                )}
                                <Input
                                    label="Code"
                                    required
                                    value={formDataCp.code}
                                    onChange={e => setFormDataCp({ ...formDataCp, code: e.target.value })}
                                    placeholder="Ex: CADRE, AM, EMP..."
                                />
                                <Input
                                    label="Libellé"
                                    required
                                    value={formDataCp.libelle}
                                    onChange={e => setFormDataCp({ ...formDataCp, libelle: e.target.value })}
                                    placeholder="Ex: Cadre Supérieur, Agent de Maîtrise..."
                                />
                                <Textarea
                                    label="Description"
                                    rows={2}
                                    value={formDataCp.description}
                                    onChange={e => setFormDataCp({ ...formDataCp, description: e.target.value })}
                                    placeholder="Précisions sur cette catégorie..."
                                />
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <Button type="button" onClick={() => setShowModalCp(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm" loading={submitting}>
                                    Enregistrer
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
