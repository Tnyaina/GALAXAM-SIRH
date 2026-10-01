import { useState, useEffect, useCallback } from 'react';
import { Edit2, Power } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';

export default function ModesPaiementTab() {
    const toast = useToast();
    const [modes, setModes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // Modal de modification
    const [editingMode, setEditingMode] = useState(null);
    const [formData, setFormData] = useState({
        libelle: '',
        actif: true,
        exigeRib: false,
        inclusFichierVirement: false,
    });
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState(null);

    const chargerModes = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await paieService.listerTousModesPaiement();
            setModes(Array.isArray(data) ? data : []);
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible de charger les modes de paiement.'));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        chargerModes();
    }, [chargerModes]);

    const handleEdit = (mode) => {
        setEditingMode(mode);
        setFormData({
            libelle: mode.libelle || '',
            actif: Boolean(mode.actif),
            exigeRib: Boolean(mode.exigeRib),
            inclusFichierVirement: Boolean(mode.inclusFichierVirement),
        });
        setSaveError(null);
    };

    const handleToggleActif = async (mode) => {
        try {
            await paieService.modifierModePaiement(mode.idModePaiement, {
                libelle: mode.libelle,
                exigeRib: mode.exigeRib,
                inclusFichierVirement: mode.inclusFichierVirement,
                actif: !mode.actif,
            });
            toast.success(`Mode de paiement « ${mode.libelle} » ${!mode.actif ? 'activé' : 'désactivé'}.`);
            chargerModes();
        } catch (err) {
            toast.error(getErrorMessage(err, 'Erreur lors du changement de statut.'));
        }
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (!editingMode) return;
        setSaving(true);
        setSaveError(null);
        try {
            await paieService.modifierModePaiement(editingMode.idModePaiement, formData);
            toast.success(`Mode de paiement « ${formData.libelle} » mis à jour.`);
            setEditingMode(null);
            chargerModes();
        } catch (err) {
            setSaveError(getErrorMessage(err, 'Erreur lors de la mise à jour.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h4 className="font-bold text-[var(--color-fg)] text-base">Modes de Paiement des Salaires</h4>
                    <p className="text-xs text-[var(--color-fg-muted)]">Nomenclature et règles de versement (salaires, acomptes, soldes de tout compte).</p>
                </div>
            </div>

            {error && <Alert type="error" message={error} />}

            {loading ? (
                <SkeletonTable rows={4} columns={6} />
            ) : (
                <Table bordered={false}>
                    <Thead>
                        <Th>CODE</Th>
                        <Th>LIBELLÉ</Th>
                        <Th>EXIGE UN RIB</Th>
                        <Th>INCLUS DANS FICHIER VIREMENT</Th>
                        <Th>STATUT</Th>
                        <Th className="text-right">ACTIONS</Th>
                    </Thead>
                    <Tbody>
                        {modes.map((m) => {
                            const isActif = m.actif !== false;
                            return (
                                <Tr key={m.idModePaiement}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">{m.code}</Td>
                                    <Td className="font-bold text-[var(--color-fg)]">{m.libelle}</Td>
                                    <Td>
                                        <Badge variant={m.exigeRib ? 'warning' : 'default'} size="sm">
                                            {m.exigeRib ? 'OUI (RIB OBLIGATOIRE)' : 'NON'}
                                        </Badge>
                                    </Td>
                                    <Td>
                                        <Badge variant={m.inclusFichierVirement ? 'brand' : 'default'} size="sm">
                                            {m.inclusFichierVirement ? 'OUI' : 'NON'}
                                        </Badge>
                                    </Td>
                                    <Td>
                                        <Badge
                                            variant={isActif ? 'success' : 'default'}
                                            size="sm"
                                            dot
                                        >
                                            {isActif ? 'ACTIF' : 'INACTIF'}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        <div className="flex items-center justify-end gap-1">
                                            <button
                                                onClick={() => handleEdit(m)}
                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                title="Modifier le mode de paiement"
                                                aria-label={`Modifier ${m.libelle}`}
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                            <button
                                                onClick={() => handleToggleActif(m)}
                                                className={`p-1.5 ${isActif ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10' : 'text-[var(--color-success-600)] hover:bg-[var(--color-success-500)]/10'} rounded-none cursor-pointer transition-colors`}
                                                title={isActif ? "Désactiver ce mode" : "Activer ce mode"}
                                                aria-label={isActif ? `Désactiver ${m.libelle}` : `Activer ${m.libelle}`}
                                            >
                                                <Power size={15} />
                                            </button>
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}

            {/* Modal de modification */}
            {editingMode && (
                <Modal
                    isOpen={Boolean(editingMode)}
                    onClose={() => setEditingMode(null)}
                    title={`Modifier le mode : ${editingMode.libelle}`}
                    size="md"
                    footer={
                        <div className="flex justify-end gap-2 w-full">
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setEditingMode(null)}
                            >
                                Annuler
                            </Button>
                            <Button
                                type="submit"
                                form="form-modifier-mode-paiement"
                                variant="primary"
                                size="sm"
                                loading={saving}
                            >
                                Enregistrer
                            </Button>
                        </div>
                    }
                >
                    <form id="form-modifier-mode-paiement" onSubmit={handleSave} className="space-y-4 text-xs">
                        {saveError && <FormError message={saveError} />}

                        <div className="bg-[var(--color-bg-subtle)] p-2.5 border border-[var(--color-border-subtle)] space-y-1">
                            <span className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider font-bold">
                                Code technique immuable
                            </span>
                            <p className="font-mono font-bold text-xs text-[var(--color-brand-500)]">
                                {editingMode.code}
                            </p>
                        </div>

                        <Input
                            id="mode-paiement-libelle"
                            label="Libellé affiché"
                            required
                            value={formData.libelle}
                            onChange={(e) => setFormData(d => ({ ...d, libelle: e.target.value }))}
                            placeholder="Ex. Virement Bancaire"
                        />

                        <div className="space-y-2.5 pt-2 border-t border-[var(--color-border-subtle)]">
                            <div className="flex items-center gap-2">
                                <input
                                    id="mode-paiement-exige-rib"
                                    type="checkbox"
                                    checked={formData.exigeRib}
                                    onChange={(e) => setFormData(d => ({ ...d, exigeRib: e.target.checked }))}
                                    className="rounded border-[var(--color-border-subtle)] text-[var(--color-brand-600)] focus:ring-[var(--color-brand-500)] cursor-pointer"
                                />
                                <label htmlFor="mode-paiement-exige-rib" className="text-xs text-[var(--color-fg)] font-medium cursor-pointer">
                                    Exige la saisie obligatoire d’un RIB bancaire pour l’employé
                                </label>
                            </div>

                            <div className="flex items-center gap-2">
                                <input
                                    id="mode-paiement-inclus-virement"
                                    type="checkbox"
                                    checked={formData.inclusFichierVirement}
                                    onChange={(e) => setFormData(d => ({ ...d, inclusFichierVirement: e.target.checked }))}
                                    className="rounded border-[var(--color-border-subtle)] text-[var(--color-brand-600)] focus:ring-[var(--color-brand-500)] cursor-pointer"
                                />
                                <label htmlFor="mode-paiement-inclus-virement" className="text-xs text-[var(--color-fg)] font-medium cursor-pointer">
                                    Inclus dans les fichiers de virement bancaire groupé (SEPA / format banque)
                                </label>
                            </div>

                            <div className="flex items-center gap-2">
                                <input
                                    id="mode-paiement-actif"
                                    type="checkbox"
                                    checked={formData.actif}
                                    onChange={(e) => setFormData(d => ({ ...d, actif: e.target.checked }))}
                                    className="rounded border-[var(--color-border-subtle)] text-[var(--color-brand-600)] focus:ring-[var(--color-brand-500)] cursor-pointer"
                                />
                                <label htmlFor="mode-paiement-actif" className="text-xs text-[var(--color-fg)] font-medium cursor-pointer">
                                    Mode de paiement actif et sélectionnable pour les règlements
                                </label>
                            </div>
                        </div>
                    </form>
                </Modal>
            )}
        </div>
    );
}
