import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, ShieldAlert, Award, CreditCard, ArrowUpRight, X } from 'lucide-react';
import contratService from '@/api/employe/contratService';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

export default function RefCarriereManagementTab() {
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);
    const [motifsMouvement, setMotifsMouvement] = useState([]);
    const [typesEvenement, setTypesEvenement] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const [showModalCp, setShowModalCp] = useState(false);
    const [selectedCp, setSelectedCp] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const [formDataCp, setFormDataCp] = useState({ code: '', libelle: '', description: '', estEligibleHs: true, actif: true });

    const loadData = async () => {
        setLoading(true);
        setError('');
        try {
            const [cpList, mpList, mmList, tevList] = await Promise.all([
                contratService.getCategoriesPro(false),
                contratService.getModesPaie(),
                contratService.getMotifsMouvement(),
                contratService.getTypesEvenementRh(),
            ]);
            setCategoriesPro(cpList || []);
            setModesPaie(mpList || []);
            setMotifsMouvement(mmList || []);
            setTypesEvenement(tevList || []);
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
        setFormDataCp({ code: '', libelle: '', description: '', estEligibleHs: true, actif: true });
        setError('');
        setShowModalCp(true);
    };

    const handleOpenEditCp = (cp) => {
        setSelectedCp(cp);
        setFormDataCp({
            code: cp.code || '',
            libelle: cp.libelle || '',
            description: cp.description || '',
            estEligibleHs: cp.estEligibleHs !== undefined ? cp.estEligibleHs : true,
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
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border border-[var(--color-success-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && !showModalCp && (
                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Section 1: Catégories Professionnelles */}
            <div className="space-y-4">
                <div className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm gap-4">
                    <div>
                        <h2 className="text-base font-bold text-[var(--color-fg)] flex items-center gap-2">
                            <Award size={18} className="text-[var(--color-brand-500)]" />
                            Catégories Professionnelles ({categoriesPro.length})
                        </h2>
                        <p className="text-xs text-[var(--color-fg-muted)]">Classification socioprofessionnelle des emplois (Cadres, Agents de maîtrise, Employés, Ouvriers)</p>
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
                    <div className="p-4">
                        <SkeletonTable rows={5} columns={6} />
                    </div>
                ) : (
                    <Table>
                        <Thead>
                            <Th>Code</Th>
                            <Th>Libellé</Th>
                            <Th>Description</Th>
                            <Th>Éligible HS</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {categoriesPro.map((cp) => (
                                <Tr key={cp.idCategorieProfessionnelle}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">{cp.code}</Td>
                                    <Td className="font-bold text-[var(--color-fg)]">{cp.libelle}</Td>
                                    <Td muted className="text-xs">{cp.description || '-'}</Td>
                                    <Td>
                                        {cp.estEligibleHs ? (
                                            <Badge variant="success" size="sm">Oui</Badge>
                                        ) : (
                                            <Badge variant="warning" size="sm">Non</Badge>
                                        )}
                                    </Td>
                                    <Td>
                                        <Badge variant={cp.actif ? 'success' : 'default'} size="sm" dot>
                                            {cp.actif ? 'Actif' : 'Inactif'}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        <button
                                            onClick={() => handleOpenEditCp(cp)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={16} />
                                        </button>
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                )}
            </div>

            {/* Section 2: Modes de calcul de paie, Motifs et Types d'Événements RH */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {/* Modes de Calcul Paie */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 rounded-none shadow-sm space-y-4">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <CreditCard size={16} className="text-[var(--color-brand-500)]" />
                        Modes de Paie ({modesPaie.length})
                    </h3>
                    <div className="divide-y divide-[var(--color-border-subtle)] border border-[var(--color-border-subtle)] rounded-none max-h-[260px] overflow-y-auto">
                        {modesPaie.map((mp) => (
                            <div key={mp.idModeCalculPaie} className="p-3 flex items-center justify-between">
                                <div>
                                    <p className="text-xs font-bold text-[var(--color-fg)]">{mp.libelle} <span className="font-mono text-[var(--color-fg-muted)]">({mp.code})</span></p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)]">{mp.description}</p>
                                </div>
                                <Badge variant="primary" size="sm">Standard</Badge>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Motifs de Mouvement */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 rounded-none shadow-sm space-y-4">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <ArrowUpRight size={16} className="text-[var(--color-brand-500)]" />
                        Motifs de Mouvements ({motifsMouvement.length})
                    </h3>
                    <div className="divide-y divide-[var(--color-border-subtle)] border border-[var(--color-border-subtle)] rounded-none max-h-[260px] overflow-y-auto">
                        {motifsMouvement.map((mm) => (
                            <div key={mm.idMotifMouvement} className="p-3 flex items-center justify-between">
                                <p className="text-xs font-semibold text-[var(--color-fg)]">{mm.libelle}</p>
                                <span className="text-[10px] font-mono font-bold text-[var(--color-brand-500)] uppercase">{mm.code}</span>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Types d'Événements RH */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 rounded-none shadow-sm space-y-4">
                    <h3 className="text-sm font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Award size={16} className="text-[var(--color-brand-500)]" />
                        Types d'Événements RH ({typesEvenement.length})
                    </h3>
                    <div className="divide-y divide-[var(--color-border-subtle)] border border-[var(--color-border-subtle)] rounded-none max-h-[260px] overflow-y-auto">
                        {typesEvenement.map((te) => (
                            <div key={te.idTypeEvenementRh || te.code} className="p-3 flex items-center justify-between">
                                <div>
                                    <p className="text-xs font-semibold text-[var(--color-fg)]">{te.libelle}</p>
                                    <p className="text-[10px] font-mono text-[var(--color-brand-500)]">{te.code}</p>
                                </div>
                                <Badge variant="success" size="sm">Actif</Badge>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Modale d'ajout/modification Catégorie Pro */}
            {showModalCp && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Award size={18} className="text-[var(--color-brand-500)]" />
                                {selectedCp ? 'Modifier la Catégorie Professionnelle' : 'Nouvelle Catégorie Professionnelle'}
                            </h3>
                            <button onClick={() => setShowModalCp(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSubmitCp} className="p-6 space-y-4">
                            {error && (
                                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                                    <ShieldAlert size={14} />
                                    {error}
                                </div>
                            )}
                            <Input
                                label="Code"
                                required
                                value={formDataCp.code}
                                onChange={e => setFormDataCp({ ...formDataCp, code: e.target.value.toUpperCase() })}
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
                            <label className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)] cursor-pointer p-3 border border-[var(--color-border-subtle)] rounded-none hover:bg-[var(--color-bg-subtle)] transition-colors mt-2">
                                <input
                                    type="checkbox"
                                    checked={formDataCp.estEligibleHs}
                                    onChange={(e) => setFormDataCp({ ...formDataCp, estEligibleHs: e.target.checked })}
                                    className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                                />
                                <span>Éligible aux Heures Supplémentaires</span>
                            </label>
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
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