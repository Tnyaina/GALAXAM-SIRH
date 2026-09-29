import { useState, useEffect } from 'react';
import { Edit2, Award, CreditCard, ArrowUpRight } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import contratService from '@/features/employees/api/contratService';
import Input, { Textarea, Checkbox } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import { Card, CardHeader } from '@/components/ui/data-display/Card';

export default function CareerReferenceDataManagementTab() {
    // État local : référentiels, retours d'action et modales.
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);
    const [motifsMouvement, setMotifsMouvement] = useState([]);
    const [typesEvenement, setTypesEvenement] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalError, setModalError] = useState('');
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

    // Effet : charge les référentiels de carrière à l'ouverture.
    useEffect(() => {
        loadData();
    }, []);

    const handleOpenEditCp = (cp) => {
        setSelectedCp(cp);
        setFormDataCp({
            code: cp.code || '',
            libelle: cp.libelle || '',
            description: cp.description || '',
            estEligibleHs: cp.estEligibleHs !== undefined ? cp.estEligibleHs : true,
            actif: cp.actif !== undefined ? cp.actif : true
        });
        setModalError('');
        setShowModalCp(true);
    };

    const handleSubmitCp = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError(null);

        if (!formDataCp.code.trim() || !formDataCp.libelle.trim()) {
            setModalError('Le code et le libellé sont obligatoires.');
            return;
        }

        if (!selectedCp) return;

        setSubmitting(true);
        try {
            await contratService.updateCategoriePro(selectedCp.idCategorieProfessionnelle, formDataCp);
            setActionSuccess(`Catégorie "${formDataCp.libelle}" modifiée avec succès.`);
            setShowModalCp(false);
            loadData();
        } catch (err) {
            console.error('Erreur enregistrement catégorie:', err);
            setModalError(getErrorMessage(err, "Erreur lors de l'enregistrement."));
        } finally {
            setSubmitting(false);
        }
    };

    // Rendu
    return (
        <div className="space-y-8">
            {/* Notifications */}
            {actionSuccess && <Alert type="success" message={actionSuccess} />}
            {error && <Alert type="error" message={error} />}

            {/* Section 1: Catégories Professionnelles */}
            <Card className="overflow-hidden p-0" shadow="none">
                <CardHeader divided className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center gap-3.5">
                    <div>
                        <h2 className="text-base font-bold text-[var(--color-fg)] flex items-center gap-2">
                            <Award size={18} className="text-[var(--color-brand-500)]" />
                            Catégories Professionnelles ({categoriesPro.length})
                        </h2>
                        <p className="text-xs text-[var(--color-fg-muted)]">Classification socioprofessionnelle des emplois (Cadres, Agents de maîtrise, Employés, Ouvriers)</p>
                    </div>
                </CardHeader>

                {loading ? (
                    <div className="p-4">
                        <SkeletonTable rows={5} columns={6} bordered={false} />
                    </div>
                ) : categoriesPro.length === 0 ? (
                    <div className="p-8">
                        <EmptyState 
                            icon={Award}
                            title="Aucune catégorie professionnelle"
                            description="Aucune catégorie professionnelle répertoriée."
                            bordered={false}
                        />
                    </div>
                ) : (
                    <Table bordered={false}>
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
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier la catégorie"
                                            aria-label={`Modifier ${cp.libelle}`}
                                        >
                                            <Edit2 size={16} />
                                        </button>
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                )}
            </Card>

            {/* Section 2: Modes de calcul de paie, Motifs et Types d'Événements RH */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                {/* Modes de Calcul Paie */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 rounded-none shadow-sm space-y-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-2">
                        <CreditCard size={15} className="text-[var(--color-brand-500)]" />
                        Modes de Paie ({modesPaie.length})
                    </h3>
                    <div className="divide-y divide-[var(--color-border-subtle)] max-h-[260px] overflow-y-auto">
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
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 rounded-none shadow-sm space-y-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-2">
                        <ArrowUpRight size={15} className="text-[var(--color-brand-500)]" />
                        Motifs de Mouvements ({motifsMouvement.length})
                    </h3>
                    <div className="divide-y divide-[var(--color-border-subtle)] max-h-[260px] overflow-y-auto">
                        {motifsMouvement.map((mm) => (
                            <div key={mm.idMotifMouvement} className="p-3 flex items-center justify-between">
                                <p className="text-xs font-semibold text-[var(--color-fg)]">{mm.libelle}</p>
                                <span className="text-[10px] font-mono font-bold text-[var(--color-brand-500)] uppercase">{mm.code}</span>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Types d'Événements RH */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 rounded-none shadow-sm space-y-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-2">
                        <Award size={15} className="text-[var(--color-brand-500)]" />
                        Types d'Événements RH ({typesEvenement.length})
                    </h3>
                    <div className="divide-y divide-[var(--color-border-subtle)] max-h-[260px] overflow-y-auto">
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
            <Modal
                isOpen={showModalCp}
                onClose={() => setShowModalCp(false)}
                title="Modifier la Catégorie Professionnelle"
                icon={<Award size={18} />}
                size="lg"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowModalCp(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="cp-form" variant="primary" size="sm" loading={submitting}>
                            Enregistrer
                        </Button>
                    </>
                }
            >
                <form id="cp-form" onSubmit={handleSubmitCp} className="space-y-4">
                    {modalError && <FormError message={modalError} />}
                    <Input
                        label="Code Catégorie"
                        required
                        disabled
                        value={formDataCp.code}
                        onChange={e => setFormDataCp({ ...formDataCp, code: e.target.value.toUpperCase() })}
                        placeholder="Ex: CADRE, AM, EMP, OUV"
                    />
                    <Input
                        label="Intitulé / Libellé"
                        required
                        value={formDataCp.libelle}
                        onChange={e => setFormDataCp({ ...formDataCp, libelle: e.target.value })}
                        placeholder="Ex: Cadre Supérieur, Agent de Maîtrise..."
                    />
                    <Textarea
                        label="Description (Optionnel)"
                        rows={2}
                        value={formDataCp.description}
                        onChange={e => setFormDataCp({ ...formDataCp, description: e.target.value })}
                        placeholder="Précisions sur les critères de cette catégorie..."
                    />
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-1">
                        <Checkbox
                            label="Éligible au calcul des Heures Supplémentaires"
                            checked={formDataCp.estEligibleHs}
                            onChange={(e) => setFormDataCp({ ...formDataCp, estEligibleHs: e.target.checked })}
                        />
                        <p className="text-[11px] text-[var(--color-fg-muted)] ml-6">
                            Décocher pour les cadres dirigeants au forfait jours / sans comptabilisation des heures supp.
                        </p>
                    </div>
                </form>
            </Modal>
        </div>
    );
}
