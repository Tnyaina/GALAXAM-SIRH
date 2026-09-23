import { useState } from 'react';
import { ShieldAlert, Plus, Search, Trash2, Edit2, User, Gift } from 'lucide-react';
import { useDerogationIndemnites } from '@/features/attendance/hooks/useDerogationIndemnites';
import Button from '@/components/ui/forms/Button';
import Input, { Select, Textarea } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import pointageParametreService from '@/features/administration/api/pointageParametreService';
import { useToast } from '@/components/ui/feedback/Toast';

export default function DerogationIndemnitesTab() {
    const {
        derogations,
        indemnites,
        loading,
        error,
        searchTerm,
        setSearchTerm,
        indemniteFilter,
        setIndemniteFilter,
        showModal,
        editingDerogation,
        formData,
        setFormData,
        submitting,
        openModal,
        closeModal,
        saveDerogation,
        reload
    } = useDerogationIndemnites();

    const toast = useToast();
    const [confirmDelete, setConfirmDelete] = useState({
        isOpen: false,
        derogation: null,
        loading: false
    });

    const handleDelete = async () => {
        if (!confirmDelete.derogation) return;
        setConfirmDelete(prev => ({ ...prev, loading: true }));
        try {
            await pointageParametreService.deleteDerogation(confirmDelete.derogation.idDerogation);
            toast({ title: 'Succès', message: "Exclusion d'indemnité supprimée.", type: 'success' });
            setConfirmDelete({ isOpen: false, derogation: null, loading: false });
            await reload();
        } catch {
            toast({ title: 'Erreur', message: "Erreur lors de la suppression de l'exclusion.", type: 'error' });
            setConfirmDelete(prev => ({ ...prev, loading: false }));
        }
    };

    if (loading) return <SkeletonTable rows={5} cols={7} />;

    return (
        <div className="space-y-6">
            {/* Bannière explicative */}
            <div className="bg-[var(--color-surface)] p-5 rounded-none border border-[var(--color-border-subtle)] shadow-sm flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-start gap-3">
                    <div className="p-2.5 bg-[var(--color-warning-50)] text-[var(--color-warning-600)] shrink-0">
                        <ShieldAlert size={22} />
                    </div>
                    <div>
                        <h3 className="text-lg font-bold text-[var(--color-fg)]">Exclusions d'indemnités par employé</h3>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 leading-relaxed">
                            Par défaut, tous les employés d'un site perçoivent les indemnités attribuées à ce site. 
                            Ajoutez un employé ici uniquement pour l'<strong>exclure</strong> d'une indemnité particulière (ex : véhicule de fonction attribué).
                        </p>
                    </div>
                </div>

                <Button variant="primary" iconLeft={<Plus size={16} />} className="shrink-0 whitespace-nowrap" onClick={() => openModal()}>
                    Ajouter une exclusion
                </Button>
            </div>

            {error && <Alert type="error" message={error} />}

            {/* Barre de recherche et filtres */}
            <div className="flex flex-col sm:flex-row gap-3 items-center justify-between bg-[var(--color-surface)] p-4 border border-[var(--color-border-subtle)]">
                <div className="w-full sm:w-80">
                    <Input
                        placeholder="Rechercher employé ou motif..."
                        iconLeft={<Search size={16} />}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>
                <div className="w-full sm:w-60">
                    <Select
                        value={indemniteFilter}
                        onChange={(e) => setIndemniteFilter(e.target.value)}
                    >
                        <option value="ALL">Toutes les indemnités</option>
                        {indemnites.map(i => (
                            <option key={i.idIndemnite} value={i.idIndemnite}>
                                {i.code} — {i.libelle}
                            </option>
                        ))}
                    </Select>
                </div>
            </div>

            {/* Table des dérogations */}
            {derogations.length === 0 ? (
                <EmptyState
                    icon={Gift}
                    title="Aucune exclusion enregistrée"
                    description="Aucune exclusion d'indemnité ne correspond à vos critères."
                    actionLabel="Ajouter une exclusion"
                    onAction={() => openModal()}
                />
            ) : (
                <Table>
                    <Thead>
                        <Tr>
                            <Th>Matricule</Th>
                            <Th>Employé</Th>
                            <Th>Indemnité</Th>
                            <Th>Statut</Th>
                            <Th>Motif</Th>
                            <Th>Période d'effet</Th>
                            <Th className="text-right">Actions</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {derogations.map((d) => (
                            <Tr key={d.idDerogation}>
                                <Td isData className="font-mono font-bold text-xs">{d.matriculeEmploye}</Td>
                                <Td className="font-semibold text-[var(--color-fg)]">
                                    <div className="flex items-center gap-2">
                                        <User size={15} className="text-[var(--color-fg-muted)]" />
                                        {d.nomPrenomEmploye}
                                    </div>
                                </Td>
                                <Td isData className="text-xs font-bold text-[var(--color-brand-500)]">
                                    {d.codeIndemnite} ({d.libelleIndemnite})
                                </Td>
                                <Td>
                                    {d.beneficie ? (
                                        <Badge variant="success">Accordé (Perçoit)</Badge>
                                    ) : (
                                        <Badge variant="danger">Exclu (Ne perçoit pas)</Badge>
                                    )}
                                </Td>
                                <Td className="text-xs text-[var(--color-fg-muted)] max-w-xs truncate">
                                    {d.motif || '—'}
                                </Td>
                                <Td isData muted className="text-xs font-mono">
                                    Du {d.dateDebut ? d.dateDebut.substring(0, 10) : '—'} {d.dateFin ? `au ${d.dateFin.substring(0, 10)}` : '(En cours)'}
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end gap-1.5">
                                        <button
                                            onClick={() => openModal(d)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier l'exclusion"
                                            aria-label={`Modifier exclusion ${d.nomPrenomEmploye}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        <button
                                            onClick={() => setConfirmDelete({ isOpen: true, derogation: d, loading: false })}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                            title="Supprimer l'exclusion"
                                            aria-label={`Supprimer exclusion ${d.nomPrenomEmploye}`}
                                        >
                                            <Trash2 size={15} />
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}

            {/* Modale d'Ajout / Édition */}
            <Modal
                isOpen={showModal}
                onClose={closeModal}
                title={editingDerogation ? 'Modifier l\'exclusion d\'indemnité' : 'Exclure un employé d\'une indemnité'}
                icon={<ShieldAlert size={18} />}
                size="md"
                footer={
                    <>
                        <Button type="button" variant="ghost" size="sm" onClick={closeModal} disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="derogation-form" variant="primary" size="sm" loading={submitting}>
                            Enregistrer l'exclusion
                        </Button>
                    </>
                }
            >
                <form id="derogation-form" onSubmit={saveDerogation} className="space-y-4">
                    <EmployeSearchSelect
                        label="Employé *"
                        value={formData.idEmploye}
                        onChange={(empId) => setFormData({ ...formData, idEmploye: empId })}
                        required
                    />

                    <Select
                        label="Indemnité *"
                        value={formData.idIndemnite}
                        onChange={(e) => setFormData({ ...formData, idIndemnite: e.target.value })}
                        required
                    >
                        {indemnites
                            .filter(i => i.estActif !== false && i.actif !== false || String(i.idIndemnite) === String(formData.idIndemnite))
                            .map(i => (
                                <option key={i.idIndemnite} value={i.idIndemnite}>
                                    {i.code} — {i.libelle}
                                </option>
                            ))}
                    </Select>

                    <div className="p-3 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/20 text-xs text-[var(--color-danger-500)] font-medium flex items-start gap-2 rounded-none">
                        <ShieldAlert size={16} className="shrink-0 text-[var(--color-danger-500)] mt-0.5" />
                        <div>
                            <strong className="font-bold">Effet :</strong> Cet enregistrement retirera cette indemnité à l'employé pour la période indiquée.
                        </div>
                    </div>

                    <Textarea
                        label="Motif ou justification"
                        placeholder="Ex : Véhicule de fonction ou logement fourni par l'entreprise"
                        value={formData.motif}
                        onChange={(e) => setFormData({ ...formData, motif: e.target.value })}
                    />

                    <div className="grid grid-cols-2 gap-3">
                        <Input
                            type="date"
                            label="Date de début *"
                            value={formData.dateDebut}
                            onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                            required
                        />
                        <Input
                            type="date"
                            label="Date de fin"
                            value={formData.dateFin}
                            onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                        />
                    </div>
                </form>
            </Modal>

            {/* Boîte de confirmation de suppression */}
            <ConfirmDialog
                isOpen={confirmDelete.isOpen}
                onClose={() => setConfirmDelete({ isOpen: false, derogation: null, loading: false })}
                onConfirm={handleDelete}
                title="Supprimer cette exclusion d'indemnité ?"
                message={`L'employé "${confirmDelete.derogation?.nomPrenomEmploye}" recommencera à percevoir l'indemnité "${confirmDelete.derogation?.libelleIndemnite}".`}
                confirmLabel="Supprimer l'exclusion"
                variant="danger"
                loading={confirmDelete.loading}
            />
        </div>
    );
}

