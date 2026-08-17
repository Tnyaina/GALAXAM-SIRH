import { useState, useEffect } from 'react';
import { Search, Plus, Edit2, CheckCircle, XCircle, Eye, Briefcase, X, ShieldAlert } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Loading from '@/components/ui/feedback/Loading';

export default function FonctionsTab() {
    const [fonctions, setFonctions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
    const [search, setSearch] = useState('');
    const [showInactive, setShowInactive] = useState(false);

    const [showModal, setShowModal] = useState(false);
    const [editingFonction, setEditingFonction] = useState(null);
    const [formData, setFormData] = useState({
        codeFonction: '',
        libelle: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await organisationService.getFonctions();
            setFonctions(data || []);
        } catch (err) {
            setError("Erreur lors du chargement des fonctions/postes.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleOpenCreate = () => {
        setEditingFonction(null);
        setFormData({ codeFonction: '', libelle: '' });
        setActionError(null);
        setShowModal(true);
    };

    const handleOpenEdit = (fonction) => {
        setEditingFonction(fonction);
        setFormData({
            codeFonction: fonction.codeFonction,
            libelle: fonction.libelle
        });
        setActionError(null);
        setShowModal(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!formData.codeFonction.trim()) {
            setActionError("Le code de la fonction est obligatoire.");
            return;
        }
        if (!formData.libelle.trim()) {
            setActionError("Le libellé de la fonction est obligatoire.");
            return;
        }

        try {
            if (editingFonction) {
                await organisationService.updateFonction(editingFonction.idFonction, formData);
                setActionSuccess(`Intitulé de poste "${formData.libelle}" modifié.`);
            } else {
                await organisationService.createFonction(formData);
                setActionSuccess(`Intitulé de poste "${formData.libelle}" créé.`);
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    const handleToggleStatus = async (fonction) => {
        setActionSuccess(null);
        try {
            if (fonction.estActif) {
                await organisationService.deactivateFonction(fonction.idFonction);
                setActionSuccess(`Poste "${fonction.libelle}" désactivé.`);
            } else {
                await organisationService.activateFonction(fonction.idFonction);
                setActionSuccess(`Poste "${fonction.libelle}" réactivé.`);
            }
            loadData();
        } catch {
            setError("Erreur lors du changement de statut.");
        }
    };

    const filteredFonctions = fonctions.filter(f =>
        (showInactive || f.estActif !== false) &&
        (f.codeFonction?.toLowerCase().includes(search.toLowerCase()) ||
         f.libelle?.toLowerCase().includes(search.toLowerCase()))
    );

    if (loading && fonctions.length === 0) {
        return (
            <div className="flex justify-center items-center py-12">
                <Loading size="md" label="Chargement des fonctions/postes..." />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border border-[var(--color-success-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex items-center gap-3 flex-1 max-w-lg">
                    <div className="relative flex-1">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" size={16} />
                        <input
                            type="text"
                            placeholder="Rechercher un poste, code..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-9 pr-4 py-2 border border-[var(--color-border)] rounded-none bg-[var(--color-surface)] text-sm text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                        />
                    </div>
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none whitespace-nowrap">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                        />
                        <Eye size={14} />
                        <span>Afficher désactivés</span>
                    </label>
                </div>
                <Button
                    onClick={handleOpenCreate}
                    variant="primary"
                    size="sm"
                    iconLeft={<Plus size={16} />}
                >
                    Nouveau Poste / Fonction
                </Button>
            </div>

            {/* Tableau des fonctions */}
            <Table>
                <Thead>
                    <Th>Code Fonction</Th>
                    <Th>Intitulé du Poste</Th>
                    <Th>Statut</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {filteredFonctions.length > 0 ? (
                        filteredFonctions.map((f) => (
                            <Tr key={f.idFonction}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{f.codeFonction}</Td>
                                <Td className="font-bold text-[var(--color-fg)]">{f.libelle}</Td>
                                <Td>
                                    <Badge variant={f.estActif ? 'success' : 'default'} size="sm" dot>
                                        {f.estActif ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-2">
                                        <button
                                            onClick={() => handleOpenEdit(f)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={16} />
                                        </button>
                                        <button
                                            onClick={() => handleToggleStatus(f)}
                                            className={`px-2 py-1 text-xs font-bold rounded-none  cursor-pointer uppercase ${
                                                f.estActif
                                                    ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                    : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                            }`}
                                        >
                                            {f.estActif ? "Désactiver" : "Activer"}
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={4} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucune fonction enregistrée.
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>

            {/* Modale Fonction */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Briefcase size={18} className="text-[var(--color-brand-500)]" />
                                {editingFonction ? "Modifier le Poste" : "Nouveau Poste / Fonction"}
                            </h3>
                            <button onClick={() => setShowModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSave} className="p-6 space-y-4">
                            {actionError && (
                                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                                    <ShieldAlert size={14} />
                                    {actionError}
                                </div>
                            )}
                            <Input
                                label="Code Fonction"
                                required
                                disabled={!!editingFonction}
                                value={formData.codeFonction}
                                onChange={(e) => setFormData({ ...formData, codeFonction: e.target.value.toUpperCase() })}
                                placeholder="Ex: DG, DAF, CHEF_DECLARANT"
                            />
                            <Input
                                label="Intitulé du Poste"
                                required
                                value={formData.libelle}
                                onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                                placeholder="Ex: Directeur Général"
                            />
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm">
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