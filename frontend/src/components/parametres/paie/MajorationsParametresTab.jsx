import { useState, useEffect } from 'react';
import { Edit2, Clock, Info } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { fmtDateInput } from '@/utils/paieUtils';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import { useToast } from '@/components/ui/feedback/Toast';

export default function MajorationsParametresTab() {
    const todayStr = fmtDateInput(new Date());

    const [majorations, setMajorations] = useState([]);
    const [configLegale, setConfigLegale] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();

    // Modale Majoration
    const [showMajorationModal, setShowMajorationModal] = useState(false);
    const [editingMajoration, setEditingMajoration] = useState(null);
    const [majorationFormData, setMajorationFormData] = useState({
        typeMajoration: '', taux: 0, description: '', dateDebut: todayStr, dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [majData, cfgData] = await Promise.all([
                paieConfigService.getTauxMajoration(),
                paieConfigService.getConfigLegaleActive().catch(() => null),
            ]);
            setMajorations(majData || []);
            setConfigLegale(cfgData || null);
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
        if (submitting) return;
        setModalError(null);
        setSubmitting(true);
        try {
            if (editingMajoration) {
                await paieConfigService.updateTauxMajoration(editingMajoration.idTauxMajoration, majorationFormData);
                toast({ title: 'Succès', message: `Taux de majoration "${majorationFormData.typeMajoration}" modifié.`, type: 'success' });
            } else {
                await paieConfigService.createTauxMajoration(majorationFormData);
                toast({ title: 'Succès', message: `Taux de majoration "${majorationFormData.typeMajoration}" créé.`, type: 'success' });
            }
            setShowMajorationModal(false);
            loadData();
        } catch (err) {
            setModalError(err.response?.data || "Erreur de sauvegarde.");
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={5} columns={5} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {error && <Alert type="error" message={error} />}

            {/* Bannière réglementaire — 100% dynamique depuis BDD */}
            <div className="bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 p-4 rounded-none flex items-start gap-3">
                <Info className="text-[var(--color-brand-500)] shrink-0 mt-0.5" size={18} />
                <div className="text-xs text-[var(--color-fg-muted)] space-y-1">
                    <p className="font-semibold text-[var(--color-fg)]">Cadre Réglementaire en vigueur
                        {configLegale && (
                            <span className="ml-2 font-normal text-[var(--color-fg-muted)]">— {configLegale.codeVersion}</span>
                        )}
                    </p>
                    {configLegale && (
                        <p>• Plafond légal d'heures supplémentaires :{' '}
                            <span className="font-bold text-[var(--color-fg)]">{configLegale.plafondHsHebdo}h / semaine</span>
                            {' '}| Tranche HS 1 (premières <span className="font-bold text-[var(--color-fg)]">{configLegale.seuilHsTranche1}h</span>) :{' '}
                            <span className="font-bold text-[var(--color-fg)]">
                                +{majorations.find(m => m.typeMajoration === 'HS_30')
                                    ? Math.round(majorations.find(m => m.typeMajoration === 'HS_30').taux * 100) + '%'
                                    : '—'}
                            </span>
                            {' '}| Au-delà :{' '}
                            <span className="font-bold text-[var(--color-fg)]">
                                +{majorations.find(m => m.typeMajoration === 'HS_50')
                                    ? Math.round(majorations.find(m => m.typeMajoration === 'HS_50').taux * 100) + '%'
                                    : '—'}
                            </span>
                        </p>
                    )}
                    {majorations.filter(m => ['NUIT','DIMANCHE','FERIE'].includes(m.typeMajoration)).map(m => (
                        <p key={m.typeMajoration}>• {m.description || m.typeMajoration} :{' '}
                            <span className="font-medium text-[var(--color-fg)]">+{Math.round(m.taux * 100)}%</span>
                        </p>
                    ))}
                    {!configLegale && majorations.length === 0 && (
                        <p className="">Aucun taux configuré — ajoutez des taux ci-dessous.</p>
                    )}
                </div>
            </div>

            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
                <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                    <div>
                        <h4 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                            <Clock size={18} className="text-[var(--color-brand-500)]" />
                            Taux de Majoration des Heures Supplémentaires, Nuit, Dimanche, Fériés
                        </h4>
                        <p className="text-xs text-[var(--color-fg-muted)]">Configuration et description exacte des taux de majoration appliqués au calcul des heures spéciales.</p>
                    </div>
                </div>

                <Table>
                    <Thead>
                        <Th>Type de Majoration</Th>
                        <Th>Description / Raison Légale</Th>
                        <Th className="text-right">Taux (%)</Th>
                        <Th className="text-right">Taux Multiplicateur</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {majorations.length === 0 ? (
                            <Tr>
                                <Td colSpan={5} className="p-0">
                                    <EmptyState
                                        icon={Clock}
                                        title="Aucun taux de majoration"
                                        description="Aucun taux de majoration configuré."
                                    />
                                </Td>
                            </Tr>
                        ) : (
                            majorations.map((m) => {
                                const percent = ((m.taux || 0) * 100).toFixed(0);
                                const mult = (1 + (m.taux || 0)).toFixed(2);
                                return (
                                    <Tr key={m.idTauxMajoration}>
                                        <Td isData className="font-bold text-[var(--color-brand-500)]">{m.typeMajoration}</Td>
                                        <Td isData className="text-xs text-[var(--color-fg)]">{m.description || '-'}</Td>
                                        <Td isData className="text-right font-bold text-[var(--color-warning-500)]">+{percent}%</Td>
                                        <Td isData muted className="text-right">x{mult}</Td>
                                        <Td className="text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingMajoration(m);
                                                    setMajorationFormData({
                                                        typeMajoration: m.typeMajoration,
                                                        taux: m.taux,
                                                        description: m.description || '',
                                                        dateDebut: fmtDateInput(m.dateDebut) || todayStr,
                                                        dateFin: fmtDateInput(m.dateFin) || ''
                                                    });
                                                    setModalError(null);
                                                    setShowMajorationModal(true);
                                                }}
                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                title="Modifier la majoration"
                                                aria-label={`Modifier ${m.typeMajoration}`}
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
            <Modal
                isOpen={showMajorationModal}
                onClose={() => setShowMajorationModal(false)}
                title={editingMajoration ? "Modifier le Taux de Majoration" : "Nouveau Taux de Majoration"}
                icon={<Clock size={18} />}
                size="md"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowMajorationModal(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="majoration-form" variant="primary" size="sm" loading={submitting}>
                            Enregistrer
                        </Button>
                    </>
                }
            >
                <form id="majoration-form" onSubmit={handleSaveMajoration} className="space-y-4">
                    {modalError && <Alert type="error" message={modalError} />}
                    <Input
                        label="Type Majoration"
                        required
                        disabled={!!editingMajoration}
                        value={majorationFormData.typeMajoration}
                        onChange={(e) => setMajorationFormData({ ...majorationFormData, typeMajoration: e.target.value })}
                        placeholder="Ex: HS_30, NUIT, FERIE"
                    />
                    <Input
                        label="Description / Raison"
                        value={majorationFormData.description}
                        onChange={(e) => setMajorationFormData({ ...majorationFormData, description: e.target.value })}
                        placeholder="Ex: 41ᵉ à 48ᵉ heure de la semaine"
                    />
                    <Input
                        label="Taux de Majoration (Ex: 0.30 pour +30%)"
                        type="number"
                        min="0"
                        max="5"
                        step="0.01"
                        required
                        value={majorationFormData.taux}
                        onChange={(e) => setMajorationFormData({ ...majorationFormData, taux: parseFloat(e.target.value) || 0 })}
                        hint="Ex: 0.30 pour +30%, 0.50 pour +50%, 1.00 pour +100%"
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
                            min={fmtDateInput(majorationFormData.dateDebut)}
                            value={fmtDateInput(majorationFormData.dateFin)}
                            onChange={(e) => setMajorationFormData({ ...majorationFormData, dateFin: e.target.value })}
                        />
                    </div>
                </form>
            </Modal>
        </div>
    );
}