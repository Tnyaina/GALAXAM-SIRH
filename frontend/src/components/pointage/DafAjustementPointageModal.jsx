import { useState, useEffect, useCallback } from 'react';
import { 
    Clock, 
    AlertTriangle, 
    CheckCircle2, 
    Save, 
    Edit3,
    Calendar,
    X,
    UserCheck,
    ChevronRight,
    ArrowLeft
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Loading from '@/components/ui/feedback/Loading';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import pointageEmployeService from '@/api/pointage/pointageEmployeService';
import pointageAnomalieService from '@/api/pointage/pointageAnomalieService';
import { fmtDate } from '@/utils/carriereUtils';
import { formatHeuresMinutes } from '@/utils/pointageUtils';

const STATUT_OPTIONS = [
    { value: 'PRESENT', label: 'Présent (Journée normale)' },
    { value: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée' },
    { value: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée (décomptée)' }
];

export default function DafAjustementPointageModal({
    isOpen,
    onClose,
    idEmploye,
    employeNom,
    matricule,
    idPeriode,
    onSuccess
}) {
    const { toast } = useToast();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);
    const [pointagesList, setPointagesList] = useState([]);
    const [selectedJour, setSelectedJour] = useState(null);

    // Formulaire d'ajustement du jour sélectionné
    const [formData, setFormData] = useState({
        heureEntreeMatin: '',
        heureSortieMatin: '',
        heureEntreeAm: '',
        heureSortieAm: '',
        statutCode: 'PRESENT',
        deplacementMatin: true,
        deplacementAm: true,
        indemniteRepasRecue: true,
        motifAjustement: ''
    });

    const loadFiche = useCallback(async () => {
        if (!idEmploye || !idPeriode) return;
        setLoading(true);
        setError('');
        try {
            const res = await pointageEmployeService.getFicheEmploye(idEmploye, {
                period: 'periode',
                idPeriode
            });
            // Les pointages journaliers sont dans res.pointages ou res.jours
            const list = res?.pointages || res?.jours || res?.lignes || [];
            setPointagesList(list);
        } catch (err) {
            console.error('Erreur chargement fiche pointage DAF:', err);
            setError("Impossible de charger les pointages de la période pour ce salarié.");
        } finally {
            setLoading(false);
        }
    }, [idEmploye, idPeriode]);

    useEffect(() => {
        if (isOpen) {
            setSelectedJour(null);
            loadFiche();
        }
    }, [isOpen, loadFiche]);

    const handleSelectJour = (ptg) => {
        setSelectedJour(ptg);
        setFormData({
            heureEntreeMatin: ptg.heureEntreeMatin || '08:00',
            heureSortieMatin: ptg.heureSortieMatin || '12:00',
            heureEntreeAm: ptg.heureEntreeAm || '13:00',
            heureSortieAm: ptg.heureSortieAm || '17:00',
            statutCode: ptg.codeStatutJour || ptg.statutCode || (ptg.present ? 'PRESENT' : 'ABSENCE_NON_JUSTIFIEE'),
            deplacementMatin: ptg.deplacementMatin !== false,
            deplacementAm: ptg.deplacementAm !== false,
            indemniteRepasRecue: ptg.indemniteRepasRecue !== false,
            motifAjustement: ptg.motifAjustement || 'Rectification directe DAF'
        });
    };

    const handleSaveAjustement = async (e) => {
        e.preventDefault();
        if (!formData.motifAjustement.trim()) {
            toast({
                title: 'Motif obligatoire',
                message: 'Veuillez renseigner un motif pour la traçabilité de l’ajustement DAF.',
                type: 'warning'
            });
            return;
        }

        setSaving(true);
        try {
            const payload = {
                ...formData,
                idEmploye,
                idPeriodePaie: idPeriode,
                datePointage: selectedJour.datePointage || selectedJour.date,
                idSite: selectedJour.idSite || 1
            };

            const idPointage = selectedJour.idPointage || 0;
            const ok = await pointageAnomalieService.regulariser(idPointage, payload);
            if (ok) {
                toast({
                    title: 'Pointage rectifié',
                    message: `Le pointage du ${fmtDate(payload.datePointage)} a été rectifié par la DAF.`,
                    type: 'success'
                });
                setSelectedJour(null);
                await loadFiche();
                if (onSuccess) onSuccess();
            }
        } catch (err) {
            console.error('Erreur ajustement pointage DAF:', err);
            toast({
                title: 'Erreur',
                message: err.response?.data?.message || err.userMessage || 'Erreur lors de la rectification.',
                type: 'error'
            });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Ajustement DAF — ${employeNom || 'Salarié'} (${matricule || ''})`}
            icon={<UserCheck className="w-5 h-5 text-brand-500" />}
            size="2xl"
            className="max-w-[780px]"
        >
            <div className="space-y-4 text-xs font-sans">
                {error && <Alert type="error" message={error} />}

                {loading ? (
                    <div className="py-12 flex justify-center">
                        <Loading size="md" label="Chargement des pointages de la période…" />
                    </div>
                ) : selectedJour ? (
                    /* Vue 2 : Formulaire de rectification d'un jour spécifique */
                    <form onSubmit={handleSaveAjustement} className="space-y-4">
                        <div className="flex items-center justify-between bg-[var(--color-bg-subtle)] p-2.5 border border-[var(--color-border-subtle)]">
                            <button
                                type="button"
                                onClick={() => setSelectedJour(null)}
                                className="flex items-center gap-1 text-xs text-brand-600 hover:underline font-semibold cursor-pointer"
                            >
                                <ArrowLeft size={14} /> Retour à la liste des jours
                            </button>
                            <span className="font-bold text-[var(--color-fg)]">
                                Journée du {fmtDate(selectedJour.datePointage || selectedJour.date)}
                            </span>
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3.5 bg-[var(--color-surface)] p-4 border border-[var(--color-border-subtle)] shadow-xs">
                            {/* Statut du jour */}
                            <div className="sm:col-span-2">
                                <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                    Statut de la journée
                                </label>
                                <Select
                                    options={STATUT_OPTIONS}
                                    value={formData.statutCode}
                                    onChange={(val) => setFormData(prev => ({ ...prev, statutCode: val }))}
                                    className="h-[32px] text-xs"
                                />
                            </div>

                            {/* Horaires Matin */}
                            <div>
                                <label className="block text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">
                                    Entrée Matin
                                </label>
                                <Input
                                    type="time"
                                    value={formData.heureEntreeMatin}
                                    onChange={(e) => setFormData(prev => ({ ...prev, heureEntreeMatin: e.target.value }))}
                                    disabled={formData.statutCode !== 'PRESENT'}
                                    className="h-[32px] text-xs font-mono"
                                />
                            </div>
                            <div>
                                <label className="block text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">
                                    Sortie Matin
                                </label>
                                <Input
                                    type="time"
                                    value={formData.heureSortieMatin}
                                    onChange={(e) => setFormData(prev => ({ ...prev, heureSortieMatin: e.target.value }))}
                                    disabled={formData.statutCode !== 'PRESENT'}
                                    className="h-[32px] text-xs font-mono"
                                />
                            </div>

                            {/* Horaires Après-midi */}
                            <div>
                                <label className="block text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">
                                    Entrée Après-midi
                                </label>
                                <Input
                                    type="time"
                                    value={formData.heureEntreeAm}
                                    onChange={(e) => setFormData(prev => ({ ...prev, heureEntreeAm: e.target.value }))}
                                    disabled={formData.statutCode !== 'PRESENT'}
                                    className="h-[32px] text-xs font-mono"
                                />
                            </div>
                            <div>
                                <label className="block text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">
                                    Sortie Après-midi
                                </label>
                                <Input
                                    type="time"
                                    value={formData.heureSortieAm}
                                    onChange={(e) => setFormData(prev => ({ ...prev, heureSortieAm: e.target.value }))}
                                    disabled={formData.statutCode !== 'PRESENT'}
                                    className="h-[32px] text-xs font-mono"
                                />
                            </div>

                            {/* Indemnités accordées */}
                            <div className="sm:col-span-2 flex flex-wrap items-center gap-4 pt-2 border-t border-[var(--color-border-subtle)]">
                                <label className="inline-flex items-center gap-1.5 cursor-pointer select-none text-[11px] font-semibold">
                                    <input
                                        type="checkbox"
                                        checked={formData.indemniteRepasRecue}
                                        onChange={(e) => setFormData(prev => ({ ...prev, indemniteRepasRecue: e.target.checked }))}
                                        className="rounded accent-brand-600"
                                    />
                                    <span>Indemnité repas accordée</span>
                                </label>
                                <label className="inline-flex items-center gap-1.5 cursor-pointer select-none text-[11px] font-semibold">
                                    <input
                                        type="checkbox"
                                        checked={formData.deplacementMatin}
                                        onChange={(e) => setFormData(prev => ({ ...prev, deplacementMatin: e.target.checked }))}
                                        className="rounded accent-brand-600"
                                    />
                                    <span>Déplacement matin</span>
                                </label>
                                <label className="inline-flex items-center gap-1.5 cursor-pointer select-none text-[11px] font-semibold">
                                    <input
                                        type="checkbox"
                                        checked={formData.deplacementAm}
                                        onChange={(e) => setFormData(prev => ({ ...prev, deplacementAm: e.target.checked }))}
                                        className="rounded accent-brand-600"
                                    />
                                    <span>Déplacement soir</span>
                                </label>
                            </div>

                            {/* Motif obligatoire DAF */}
                            <div className="sm:col-span-2 pt-1">
                                <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                    Motif de la rectification DAF <span className="text-danger-500">*</span>
                                </label>
                                <Input
                                    placeholder="Ex: Rectifié suite justificatif mission signé par la direction"
                                    value={formData.motifAjustement}
                                    onChange={(e) => setFormData(prev => ({ ...prev, motifAjustement: e.target.value }))}
                                    required
                                    className="h-[32px] text-xs"
                                />
                            </div>
                        </div>

                        <div className="flex items-center justify-end gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => setSelectedJour(null)}
                            >
                                Annuler
                            </Button>
                            <Button
                                type="submit"
                                variant="primary"
                                size="sm"
                                loading={saving}
                                icon={<Save size={14} />}
                                className="font-bold shadow-xs"
                            >
                                Enregistrer la rectification
                            </Button>
                        </div>
                    </form>
                ) : (
                    /* Vue 1 : Tableau des jours du mois pour ce salarié */
                    <div className="space-y-3">
                        <div className="flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                            <span>Sélectionnez la journée à rectifier pour ajuster les heures ou le statut :</span>
                            <span className="font-mono font-bold">{pointagesList.length} jour(s) enregistrés</span>
                        </div>

                        <div className="overflow-x-auto border border-[var(--color-border-subtle)] max-h-[420px] overflow-y-auto">
                            <Table className="w-full text-left border-collapse text-[11.5px]">
                                <Thead className="sticky top-0 bg-[var(--color-surface)] shadow-2xs z-10">
                                    <Tr className="border-b border-[var(--color-border-subtle)] text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                        <Th className="py-2 px-3">Date</Th>
                                        <Th className="py-2 px-2 text-center">Statut</Th>
                                        <Th className="py-2 px-2 text-center">Matin</Th>
                                        <Th className="py-2 px-2 text-center">Après-midi</Th>
                                        <Th className="py-2 px-2 text-center">Total</Th>
                                        <Th className="py-2 px-2 text-center">Retard</Th>
                                        <Th className="py-2 px-3 text-right">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {pointagesList.length === 0 ? (
                                        <Tr>
                                            <Td colSpan={7} className="py-8 text-center text-[var(--color-fg-muted)]">
                                                Aucun pointage trouvé pour cette période.
                                            </Td>
                                        </Tr>
                                    ) : (
                                        pointagesList.map((ptg) => {
                                            const hasAnomalie = Boolean(ptg.anomalieSaisie || ptg.absenceNonJustifiee || (ptg.minutesRetard > 0 && !ptg.motifAjustement));
                                            const isConge = Boolean(ptg.conge || ptg.absenceJustifiee);
                                            const dateVal = ptg.datePointage || ptg.date;

                                            return (
                                                <Tr
                                                    key={ptg.idPointage || dateVal}
                                                    className={`border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/50 transition-colors ${
                                                        hasAnomalie ? 'bg-amber-500/[0.04]' : ''
                                                    }`}
                                                >
                                                    <Td className="py-2 px-3 font-medium">
                                                        <span>{fmtDate(dateVal)}</span>
                                                        {hasAnomalie && (
                                                            <span className="block text-[9.5px] font-bold text-amber-700 dark:text-amber-400">
                                                                ⚠ Anomalie détectée
                                                            </span>
                                                        )}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center">
                                                        <Badge
                                                            variant={
                                                                isConge ? 'warning' :
                                                                ptg.absenceNonJustifiee ? 'danger' :
                                                                ptg.present ? 'success' : 'neutral'
                                                            }
                                                            size="xs"
                                                        >
                                                            {ptg.libelleStatut || (ptg.present ? 'Présent' : isConge ? 'Congé' : 'Absent')}
                                                        </Badge>
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono text-[11px]">
                                                        {ptg.heureEntreeMatin && ptg.heureSortieMatin
                                                            ? `${ptg.heureEntreeMatin.substring(0, 5)} - ${ptg.heureSortieMatin.substring(0, 5)}`
                                                            : '—'}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono text-[11px]">
                                                        {ptg.heureEntreeAm && ptg.heureSortieAm
                                                            ? `${ptg.heureEntreeAm.substring(0, 5)} - ${ptg.heureSortieAm.substring(0, 5)}`
                                                            : '—'}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono font-bold">
                                                        {formatHeuresMinutes(ptg.totalHeures || 0, true)}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono">
                                                        {ptg.minutesRetard > 0 ? (
                                                            <span className="text-danger-600 font-bold">+{ptg.minutesRetard}m</span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)]">0</span>
                                                        )}
                                                    </Td>
                                                    <Td className="py-2 px-3 text-right">
                                                        <Button
                                                            type="button"
                                                            variant="outline"
                                                            size="xs"
                                                            onClick={() => handleSelectJour(ptg)}
                                                            icon={<Edit3 size={11} />}
                                                            className="h-6 px-2 text-[11px] font-semibold"
                                                        >
                                                            Rectifier
                                                        </Button>
                                                    </Td>
                                                </Tr>
                                            );
                                        })
                                    )}
                                </Tbody>
                            </Table>
                        </div>
                    </div>
                )}
            </div>
        </Modal>
    );
}
