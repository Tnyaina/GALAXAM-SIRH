import { useState, useEffect, useCallback } from 'react';
import { 
    Save, 
    Edit3,
    UserCheck,
    ArrowLeft
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
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
    { value: 'DIMANCHE_TRAVAILLE', label: 'Dimanche travaillé (Majoré)' },
    { value: 'SAMEDI_TRAVAILLE', label: 'Samedi travaillé' },
    { value: 'AUTONOME', label: 'Autonome (Cadre / Forfait)' },
    { value: 'CONGE', label: 'Congé / Permission payée' },
    { value: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée' },
    { value: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée (décomptée)' },
    { value: 'REPOS', label: 'Repos hebdomadaire / Férié chômé' }
];

export default function DafAjustementPointageModal({
    isOpen,
    onClose,
    employe = null,
    idEmploye: propIdEmploye,
    employeNom: propEmployeNom,
    matricule: propMatricule,
    idPeriode: propIdPeriode,
    idPeriodePaie: propIdPeriodePaie,
    initialDate = null,
    onSuccess
}) {
    const idEmployeEffective = employe?.idEmploye || propIdEmploye;
    const employeNomEffective = employe ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() : (propEmployeNom || 'Salarié');
    const matriculeEffective = employe?.matricule || propMatricule || '';
    const idPeriodeEffective = propIdPeriode || propIdPeriodePaie || employe?.idPeriodePaie;

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
        horaireContinuJour: false,
        statutCode: 'PRESENT',
        deplacementMatin: true,
        deplacementAm: true,
        indemniteRepasRecue: true,
        motifAjustement: ''
    });

    const handleSelectJour = useCallback((ptg) => {
        setSelectedJour(ptg);
        const cleanTime = (val) => (val && val !== '-' && val !== '—') ? val : '';
        const em = cleanTime(ptg.entree) || cleanTime(ptg.heureEntreeMatin) || '08:00';
        let sm = cleanTime(ptg.sortiePause) || cleanTime(ptg.heureSortieMatin) || '12:00';
        let ea = cleanTime(ptg.retourPause) || cleanTime(ptg.heureEntreeAm) || '13:00';
        const sa = cleanTime(ptg.sortie) || cleanTime(ptg.heureSortieAm) || '17:00';

        const isContinu = Boolean(
            ptg.horaireContinuJour || 
            ptg.horaireContinu || 
            (sm === '12:00' && ea === '12:00')
        );

        if (isContinu) {
            sm = '';
            ea = '';
        }

        let st = ptg.statut || ptg.statutCode || ptg.codeStatutJour;
        if (!st || st === 'AUTONOME') st = 'PRESENT';

        setFormData({
            heureEntreeMatin: em,
            heureSortieMatin: sm,
            heureEntreeAm: ea,
            heureSortieAm: sa,
            horaireContinuJour: isContinu,
            statutCode: st,
            deplacementMatin: ptg.deplacementMatin !== false,
            deplacementAm: ptg.deplacementAm !== false,
            indemniteRepasRecue: (ptg.indemniteRepas ?? ptg.indemniteRepasRecue) !== false,
            motifAjustement: ptg.motifAjustement || 'Rectification directe DAF'
        });
    }, []);

    const loadFiche = useCallback(async () => {
        if (!idEmployeEffective || !idPeriodeEffective) {
            setLoading(false);
            return;
        }
        setLoading(true);
        setError('');
        try {
            const res = await pointageEmployeService.getFicheEmploye(idEmployeEffective, {
                period: 'periode',
                idPeriode: idPeriodeEffective
            });
            // Les pointages journaliers sont renvoyés dans res.pointagesData (PointageJourDTO)
            const list = res?.pointagesData || res?.pointages || res?.jours || res?.lignes || [];
            setPointagesList(list);

            if (initialDate && list.length > 0) {
                const match = list.find(p => (p.dateComplete === initialDate || p.datePointage === initialDate || p.date === initialDate));
                if (match) {
                    handleSelectJour(match);
                }
            }
        } catch (err) {
            console.error('Erreur chargement fiche pointage DAF:', err);
            setError("Impossible de charger les pointages de la période pour ce salarié.");
        } finally {
            setLoading(false);
        }
    }, [idEmployeEffective, idPeriodeEffective, initialDate, handleSelectJour]);

    useEffect(() => {
        if (isOpen) {
            setSelectedJour(null);
            loadFiche();
        }
    }, [isOpen, loadFiche]);

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
            const dateStr = selectedJour.dateComplete || selectedJour.datePointage || selectedJour.date;
            const payload = {
                ...formData,
                idEmploye: idEmployeEffective,
                idPeriodePaie: idPeriodeEffective,
                datePointage: dateStr,
                idSite: selectedJour.idSite || 1
            };

            const idPointage = selectedJour.idPointage || 0;
            const ok = await pointageAnomalieService.regulariser(idPointage, payload);
            if (ok) {
                toast({
                    title: 'Pointage rectifié',
                    message: `Le pointage du ${fmtDate(dateStr) || dateStr} a été rectifié par la DAF.`,
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
            title={`Ajustement DAF — ${employeNomEffective} (${matriculeEffective})`}
            icon={<UserCheck className="w-5 h-5 text-brand-500" />}
            size="2xl"
            className="max-w-[820px]"
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
                                Journée du {fmtDate(selectedJour.dateComplete || selectedJour.datePointage) || selectedJour.dateComplete || selectedJour.date}
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

                            {/* Option Journée continue */}
                            <div className="sm:col-span-2 bg-amber-500/10 border border-amber-500/25 rounded-md p-2.5 flex items-center justify-between">
                                <div className="flex flex-col">
                                    <span className="text-xs font-semibold text-amber-900 dark:text-amber-200 flex items-center gap-1.5">
                                        ⚡ Journée continue (Sans pause déjeuner)
                                    </span>
                                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                                        Pas de déduction automatique d'1h de pause. Durée calculée directement entre l'Entrée et la Sortie.
                                    </span>
                                </div>
                                <label className="relative inline-flex items-center cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={formData.horaireContinuJour}
                                        onChange={(e) => {
                                            const checked = e.target.checked;
                                            setFormData(prev => ({
                                                ...prev,
                                                horaireContinuJour: checked,
                                                heureSortieMatin: checked ? '' : prev.heureSortieMatin,
                                                heureEntreeAm: checked ? '' : prev.heureEntreeAm
                                            }));
                                        }}
                                        className="w-4 h-4 rounded accent-brand-600 cursor-pointer"
                                    />
                                </label>
                            </div>

                            {formData.horaireContinuJour ? (
                                <>
                                    {/* Entrée continue */}
                                    <div>
                                        <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                            Heure d'Entrée
                                        </label>
                                        <Input
                                            type="time"
                                            value={formData.heureEntreeMatin}
                                            onChange={(e) => setFormData(prev => ({ ...prev, heureEntreeMatin: e.target.value }))}
                                            className="h-[32px] text-xs font-mono"
                                        />
                                    </div>

                                    {/* Sortie continue */}
                                    <div>
                                        <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                            Heure de Sortie
                                        </label>
                                        <Input
                                            type="time"
                                            value={formData.heureSortieAm}
                                            onChange={(e) => setFormData(prev => ({ ...prev, heureSortieAm: e.target.value }))}
                                            className="h-[32px] text-xs font-mono"
                                        />
                                    </div>
                                </>
                            ) : (
                                <>
                                    {/* Horaires Matin */}
                                    <div>
                                        <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                            Plage du Matin (Entrée — Sortie)
                                        </label>
                                        <div className="grid grid-cols-2 gap-2">
                                            <Input
                                                type="time"
                                                value={formData.heureEntreeMatin}
                                                onChange={(e) => setFormData(prev => ({ ...prev, heureEntreeMatin: e.target.value }))}
                                                className="h-[32px] text-xs font-mono"
                                            />
                                            <Input
                                                type="time"
                                                value={formData.heureSortieMatin}
                                                onChange={(e) => setFormData(prev => ({ ...prev, heureSortieMatin: e.target.value }))}
                                                className="h-[32px] text-xs font-mono"
                                            />
                                        </div>
                                    </div>

                                    {/* Horaires Après-midi */}
                                    <div>
                                        <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                            Plage Après-midi (Retour — Sortie)
                                        </label>
                                        <div className="grid grid-cols-2 gap-2">
                                            <Input
                                                type="time"
                                                value={formData.heureEntreeAm}
                                                onChange={(e) => setFormData(prev => ({ ...prev, heureEntreeAm: e.target.value }))}
                                                className="h-[32px] text-xs font-mono"
                                            />
                                            <Input
                                                type="time"
                                                value={formData.heureSortieAm}
                                                onChange={(e) => setFormData(prev => ({ ...prev, heureSortieAm: e.target.value }))}
                                                className="h-[32px] text-xs font-mono"
                                            />
                                        </div>
                                    </div>
                                </>
                            )}

                            {/* Indemnités accordées */}
                            <div className="sm:col-span-2 flex items-center gap-4 pt-1 flex-wrap">
                                <label className="inline-flex items-center gap-1.5 cursor-pointer select-none text-[11px] font-semibold">
                                    <input
                                        type="checkbox"
                                        checked={formData.indemniteRepasRecue}
                                        onChange={(e) => setFormData(prev => ({ ...prev, indemniteRepasRecue: e.target.checked }))}
                                        className="rounded accent-brand-600"
                                    />
                                    <span>Indemnité repas accordée (2 000 Ar)</span>
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
                            <span>Sélectionnez la journée à rectifier pour ajuster les heures, statuts ou indemnités :</span>
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
                                        <Th className="py-2 px-2 text-center">Repas</Th>
                                        <Th className="py-2 px-2 text-center">Déplac.</Th>
                                        <Th className="py-2 px-3 text-right">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {pointagesList.length === 0 ? (
                                        <Tr>
                                            <Td colSpan={9} className="py-8 text-center text-[var(--color-fg-muted)]">
                                                Aucun pointage trouvé pour cette période.
                                            </Td>
                                        </Tr>
                                    ) : (
                                        pointagesList.map((ptg) => {
                                            const dateVal = ptg.dateComplete || ptg.datePointage;
                                            const dObj = dateVal ? new Date(dateVal + 'T00:00:00') : null;
                                            const dow = dObj ? dObj.getDay() : -1;
                                            const isSunday = dow === 0;
                                            const isSaturday = dow === 6;
                                            const isWeekend = isSunday || isSaturday;

                                            const totalH = Number(ptg.totalHeures || (ptg.heures ? parseFloat(ptg.heures) : 0));
                                            const hasWorked = totalH > 0;

                                            const isAbsent = ptg.statut === 'ABSENT' || ptg.statut === 'ABSENCE_NON_JUSTIFIEE' || ptg.codeStatut === 'ABSENCE_NON_JUSTIFIEE';
                                            const isConge = ptg.statut === 'CONGE' || ptg.codeStatut === 'CONGE' || Boolean(ptg.typeConge);
                                            const isRetard = ptg.statut === 'RETARD' || (ptg.ecart && ptg.ecart.startsWith('-') && !ptg.ecartPositif) || (ptg.minutesRetard > 0);
                                            const isRepos = (isWeekend && !hasWorked) || ptg.statut === 'REPOS' || ptg.codeStatut === 'REPOS';
                                            const isDimancheTrav = isSunday && hasWorked;
                                            const isSamediTrav = isSaturday && hasWorked;
                                            const isPlanifie = ptg.statut === 'PLANIFIE' || ptg.statut === 'NON_ECHU';
                                            const isAutonome = (ptg.statut === 'AUTONOME' || ptg.statut === 'DISPENSE_POINTAGE') && !isWeekend;
                                            const isPresent = (ptg.statut === 'PRESENT' || hasWorked || ptg.present) && !isRepos && !isDimancheTrav && !isSamediTrav;
                                            const hasAnomalie = Boolean(ptg.anomalieSaisie || isAbsent || isRetard || ptg.statut === 'ANOMALIE');

                                            const dateFormatted = dateVal ? fmtDate(dateVal) : `${ptg.date} (${ptg.jour || ''})`;
                                            
                                            const cleanT = (val) => (val && val !== '-' && val !== '—') ? val : null;
                                            const isContinuRow = Boolean(
                                                ptg.horaireContinuJour || 
                                                ptg.horaireContinu || 
                                                (cleanT(ptg.sortiePause) === '12:00' && cleanT(ptg.retourPause) === '12:00')
                                            );

                                            const matinStr = isContinuRow
                                                ? (cleanT(ptg.entree) ? `${cleanT(ptg.entree)}` : '—')
                                                : ((cleanT(ptg.entree) && cleanT(ptg.sortiePause))
                                                    ? `${cleanT(ptg.entree)} - ${cleanT(ptg.sortiePause)}`
                                                    : (cleanT(ptg.entree) ? cleanT(ptg.entree) : '—'));
                                            const amStr = isContinuRow
                                                ? (cleanT(ptg.sortie) ? `${cleanT(ptg.sortie)}` : '—')
                                                : ((cleanT(ptg.retourPause) && cleanT(ptg.sortie))
                                                    ? `${cleanT(ptg.retourPause)} - ${cleanT(ptg.sortie)}`
                                                    : (cleanT(ptg.sortie) ? cleanT(ptg.sortie) : '—'));
                                            
                                            const heuresStr = ptg.heures || (totalH > 0 ? formatHeuresMinutes(totalH, true) : (isRepos ? '00h00' : '08h00'));
                                            const ecartStr = ptg.ecart || (ptg.minutesRetard > 0 ? `+${ptg.minutesRetard}m` : '00 min');

                                            // Repas & Déplacement statut
                                            const aRepas = ptg.indemniteRepasRecue || ptg.indemniteRepas || ptg.repas;
                                            const depM = ptg.deplacementMatin !== false;
                                            const depA = ptg.deplacementAm !== false;
                                            const aDeplacement = ptg.indemniteDeplacementRecue || (depM || depA);

                                            return (
                                                <Tr
                                                    key={ptg.idPointage || ptg.dateComplete || ptg.date}
                                                    className={`border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/50 transition-colors ${
                                                        hasAnomalie ? 'bg-amber-500/[0.04]' : ''
                                                    }`}
                                                >
                                                    <Td className="py-2 px-3 font-medium">
                                                        <span>{dateFormatted}</span>
                                                        {hasAnomalie && (
                                                            <span className="block text-[9.5px] font-bold text-amber-700 dark:text-amber-400">
                                                                Anomalie détectée
                                                            </span>
                                                        )}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center">
                                                        <Badge
                                                            variant={
                                                                isDimancheTrav ? 'purple' :
                                                                isSamediTrav ? 'brand' :
                                                                isPlanifie ? 'neutral' :
                                                                isConge ? 'warning' :
                                                                isAbsent ? 'danger' :
                                                                isRetard ? 'warning' :
                                                                isAutonome ? 'brand' :
                                                                isRepos ? 'neutral' :
                                                                isPresent ? 'success' : 'neutral'
                                                            }
                                                            size="xs"
                                                        >
                                                            {isDimancheTrav ? 'Dimanche (+40%)' :
                                                             isSamediTrav ? 'Samedi' :
                                                             isRepos ? 'Repos' :
                                                             isPlanifie ? 'Planifié' :
                                                             (ptg.statut || (isPresent ? 'Présent' : isConge ? 'Congé' : 'Absent'))}
                                                        </Badge>
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono text-[11px]">
                                                        {matinStr}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono text-[11px]">
                                                        {amStr}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono font-bold">
                                                        {heuresStr}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono">
                                                        {ecartStr}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono text-[10.5px]">
                                                        {aRepas ? (
                                                            <span className="px-1.5 py-0.5 bg-amber-500/10 text-amber-700 dark:text-amber-300 font-bold border border-amber-500/20">
                                                                2 000 Ar
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)]">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="py-2 px-2 text-center font-mono text-[10px]">
                                                        {aDeplacement && hasWorked ? (
                                                            <span className="px-1.5 py-0.5 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 font-bold border border-emerald-500/20">
                                                                {depM && depA ? 'M+A' : depM ? 'Matin' : 'Soir'}
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)]">—</span>
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
