import { useState, useEffect, useCallback, useRef } from 'react';
import { Edit2, Award, CreditCard, ArrowUpRight } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import contratService from '@/features/employees/api/contratService';
import Input, { Textarea, Checkbox } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import FormError from '@/components/ui/feedback/FormError';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import { Card, CardHeader } from '@/components/ui/data-display/Card';

export default function CareerReferenceDataManagementTab({ section = 'categories' }) {
    // État local : référentiels, retours d'action et modales.
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);
    const [motifsMouvement, setMotifsMouvement] = useState([]);
    const [typesEvenement, setTypesEvenement] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalError, setModalError] = useState('');
    const [fieldErrors, setFieldErrors] = useState({});
    const { toast } = useToast();
    const submitLock = useRef(false);

    const [showModalCp, setShowModalCp] = useState(false);
    const [selectedCp, setSelectedCp] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    const [formDataCp, setFormDataCp] = useState({ code: '', libelle: '', description: '', estEligibleHs: true, actif: true });

    const loadData = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            if (section === 'categories') setCategoriesPro(await contratService.getCategoriesPro(false) || []);
            else if (section === 'modes_calcul') setModesPaie(await contratService.getModesPaie() || []);
            else if (section === 'motifs') setMotifsMouvement(await contratService.getMotifsMouvement() || []);
            else if (section === 'evenements') setTypesEvenement(await contratService.getTypesEvenementRh() || []);
        } catch (err) {
            console.error('Erreur chargement référentiels carrière:', err);
            setError('Impossible de charger les référentiels de carrière.');
        } finally {
            setLoading(false);
        }
    }, [section]);

    // Effet : charge les référentiels de carrière à l'ouverture.
    useEffect(() => {
        loadData();
    }, [loadData]);

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
        setFieldErrors({});
        setShowModalCp(true);
    };

    const handleSubmitCp = async (e) => {
        e.preventDefault();
        if (submitLock.current || submitting) return;
        setModalError(null);

        const errors = {};
        if (!formDataCp.code.trim()) {
            errors.code = "Le code de la catégorie est obligatoire.";
        }
        if (!formDataCp.libelle.trim()) {
            errors.libelle = "L'intitulé est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }
        setFieldErrors({});

        if (!selectedCp) return;

        submitLock.current = true;
        setSubmitting(true);
        try {
            await contratService.updateCategoriePro(selectedCp.idCategorieProfessionnelle, formDataCp);
            toast({ title: 'Succès', message: `Catégorie "${formDataCp.libelle}" modifiée avec succès.`, type: 'success' });
            setShowModalCp(false);
            loadData();
        } catch (err) {
            console.error('Erreur enregistrement catégorie:', err);
            setModalError(getErrorMessage(err, "Erreur lors de l'enregistrement."));
        } finally {
            submitLock.current = false;
            setSubmitting(false);
        }
    };

    const selectedReference = { modes_calcul: modesPaie, motifs: motifsMouvement, evenements: typesEvenement }[section];

    // Rendu
    return (
        <div className="space-y-8">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Section 1: Catégories Professionnelles */}
            {!error && section === 'categories' && <Card className="overflow-hidden p-0" shadow="none">
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
                                            <Badge variant="default" size="sm">Non</Badge>
                                        )}
                                    </Td>
                                    <Td>
                                        <Badge variant={cp.actif ? 'success' : 'default'} size="sm" dot>
                                            {cp.actif ? 'Actif' : 'Inactif'}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        <Button variant="ghost" className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
                                            onClick={() => handleOpenEditCp(cp)}
                                            title="Modifier la catégorie"
                                            aria-label={`Modifier ${cp.libelle}`}
                                        >
                                            <Edit2 size={16} />
                                        </Button>
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                )}
            </Card>}

            {/* Section 2: Modes de calcul de paie, Motifs et Types d'Événements RH */}
            <div className="space-y-4">
                {loading && section !== 'categories' && <SkeletonTable rows={5} columns={3} />}
                {!loading && !error && selectedReference?.length === 0 && <EmptyState title="Aucune référence" description="Aucun élément n’est renseigné dans cette liste." />}
                {/* Modes de Calcul Paie */}
                {!loading && !error && selectedReference?.length > 0 && section === 'modes_calcul' && <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 rounded-none shadow-sm space-y-3">
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
                </div>}

                {/* Motifs de Mouvement */}
                {!loading && !error && selectedReference?.length > 0 && section === 'motifs' && <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 rounded-none shadow-sm space-y-3">
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
                </div>}

                {/* Types d'Événements RH */}
                {!loading && !error && selectedReference?.length > 0 && section === 'evenements' && <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 rounded-none shadow-sm space-y-3">
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
                </div>}
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
                        error={fieldErrors.code}
                        onChange={e => {
                            setFormDataCp({ ...formDataCp, code: e.target.value.toUpperCase() });
                            if (fieldErrors.code) setFieldErrors(prev => ({ ...prev, code: undefined }));
                        }}
                        placeholder="Ex: CADRE, AM, EMP, OUV"
                    />
                    <Input
                        label="Intitulé / Libellé"
                        required
                        value={formDataCp.libelle}
                        error={fieldErrors.libelle}
                        onChange={e => {
                            setFormDataCp({ ...formDataCp, libelle: e.target.value });
                            if (fieldErrors.libelle) setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }}
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
