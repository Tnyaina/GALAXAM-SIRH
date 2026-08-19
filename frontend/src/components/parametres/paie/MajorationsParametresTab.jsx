import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, Clock, ShieldAlert, X } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput } from '@/utils/paieUtils';

export default function MajorationsParametresTab() {
    const todayStr = fmtDateInput(new Date());

    const [majorations, setMajorations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Modale Majoration
    const [showMajorationModal, setShowMajorationModal] = useState(false);
    const [editingMajoration, setEditingMajoration] = useState(null);
    const [majorationFormData, setMajorationFormData] = useState({
        typeMajoration: '', taux: 0, dateDebut: todayStr, dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const majData = await paieConfigService.getTauxMajoration();
            setMajorations(majData || []);
        } catch (err) {
            setError("Erreur lors du chargement des taux de majoration.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Majoration
    const handleSaveMajoration = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        try {
            if (editingMajoration) {
                await paieConfigService.updateTauxMajoration(editingMajoration.idTauxMajoration, majorationFormData);
                setActionSuccess(`Taux de majoration "${majorationFormData.typeMajoration}" modifié.`);
            } else {
                await paieConfigService.createTauxMajoration(majorationFormData);
                setActionSuccess(`Taux de majoration "${majorationFormData.typeMajoration}" créé.`);
            }
            setShowMajorationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    if (loading) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-[var(--color-brand-500)] border-t-transparent rounded-none animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
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

            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
                <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                    <div>
                        <h4 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                            <Clock size={18} className="text-[var(--color-brand-500)]" />
                            Taux de Majoration des Heures Supplémentaires, Nuit, Dimanche, Fériés
                        </h4>
                        <p className="text-xs text-[var(--color-fg-muted)]">Configuration des pourcentages de majoration appliqués au calcul des heures spéciales.</p>
                    </div>
                    <Button
                        onClick={() => {
                            setEditingMajoration(null);
                            setMajorationFormData({ typeMajoration: '', taux: 0, dateDebut: todayStr, dateFin: '' });
                            setActionError(null);
                            setShowMajorationModal(true);
                        }}
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                    >
                        Nouveau Taux de Majoration
                    </Button>
                </div>

                <Table>
                    <Thead>
                        <Th>Type de Majoration</Th>
                        <Th className="text-right">Taux (%)</Th>
                        <Th className="text-right">Taux Multiplicateur</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {majorations.length === 0 ? (
                            <Tr>
                                <Td colSpan={4} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                    Aucun taux de majoration configuré.
                                </Td>
                            </Tr>
                        ) : (
                            majorations.map((m) => {
                                const percent = ((m.taux || 0) * 100).toFixed(0);
                                const mult = (1 + (m.taux || 0)).toFixed(2);
                                return (
                                    <Tr key={m.idTauxMajoration}>
                                        <Td isData className="font-bold text-[var(--color-brand-500)]">{m.typeMajoration}</Td>
                                        <Td isData className="text-right font-bold text-[var(--color-warning-500)]">+{percent}%</Td>
                                        <Td isData muted className="text-right">x{mult}</Td>
                                        <Td className="text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingMajoration(m);
                                                    setMajorationFormData({
                                                        typeMajoration: m.typeMajoration,
                                                        taux: m.taux,
                                                        dateDebut: fmtDateInput(m.dateDebut) || todayStr,
                                                        dateFin: fmtDateInput(m.dateFin) || ''
                                                    });
                                                    setActionError(null);
                                                    setShowMajorationModal(true);
                                                }}
                                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                        </Td>
                                    </Tr>
                                );
                            })
                        )}
                    </Tbody>
                </Table>
            </div>

            {/* Modale Majoration */}
            {showMajorationModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Clock size={18} className="text-[var(--color-brand-500)]" />
                                {editingMajoration ? "Modifier le Taux de Majoration" : "Nouveau Taux de Majoration"}
                            </h3>
                            <button onClick={() => setShowMajorationModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSaveMajoration} className="p-6 space-y-4">
                            {actionError && (
                                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-2">
                                    <ShieldAlert size={16} />
                                    {actionError}
                                </div>
                            )}
                            <Input
                                label="Type Majoration"
                                required
                                disabled={!!editingMajoration}
                                value={majorationFormData.typeMajoration}
                                onChange={(e) => setMajorationFormData({ ...majorationFormData, typeMajoration: e.target.value })}
                                placeholder="Ex: HS_30, NUIT, FERIE"
                            />
                            <Input
                                label="Taux (Ex: 0.30 pour +30%)"
                                type="number"
                                step="0.01"
                                required
                                value={majorationFormData.taux}
                                onChange={(e) => setMajorationFormData({ ...majorationFormData, taux: parseFloat(e.target.value) || 0 })}
                            />
                            <div className="grid grid-cols-2 gap-4">
                                <Input
                                    label="Date Début"
                                    type="date"
                                    required
                                    value={fmtDateInput(majorationFormData.dateDebut)}
                                    onChange={(e) => setMajorationFormData({ ...majorationFormData, dateDebut: e.target.value })}
                                />
                                <Input
                                    label="Date Fin (Optionnel)"
                                    type="date"
                                    value={fmtDateInput(majorationFormData.dateFin)}
                                    onChange={(e) => setMajorationFormData({ ...majorationFormData, dateFin: e.target.value })}
                                />
                            </div>
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowMajorationModal(false)} variant="ghost" size="sm">
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