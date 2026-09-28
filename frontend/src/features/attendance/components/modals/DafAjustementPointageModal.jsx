import { useState, useEffect, useCallback } from 'react';
import { 
    Save, 
    Edit3,
    UserCheck,
    ArrowLeft,
    FastForward
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
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { fmtDate } from '@/utils/dateUtils';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';

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
    // Données dérivées des deux signatures de props prises en charge par la modale.
    const idEmployeEffective = employe?.idEmploye || propIdEmploye;
    const employeNomEffective = employe ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() : (propEmployeNom || 'Salarié');
    const matriculeEffective = employe?.matricule || propMatricule || '';
    const idPeriodeEffective = propIdPeriode || propIdPeriodePaie || employe?.idPeriodePaie;

    // Contexte
    const { toast } = useToast();

    // État local
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);
    const [statutOptions, setStatutOptions] = useState([]);
    const [canCorrect, setCanCorrect] = useState(false);
    const [pointagesList, setPointagesList] = useState([]);
    const [selectedJour, setSelectedJour] = useState(null);
    const [availableAllowances, setAvailableAllowances] = useState([]);
    const [dynamicAllowances, setDynamicAllowances] = useState({});
    const [allowancesLoading, setAllowancesLoading] = useState(false);
    const [allowancesError, setAllowancesError] = useState('');

    // État du formulaire d'ajustement du jour sélectionné
    const [formData, setFormData] = useState({
        heureEntreeMatin: '',
        heureSortieMatin: '',
        heureEntreeAm: '',
        heureSortieAm: '',
        horaireContinuJour: false,
        statutCode: 'PRESENT',
        motifAjustement: ''
    });

    // Actions et chargement des données
    const handleSelectJour = useCallback((ptg) => {
        setSelectedJour(ptg);
        const cleanTime = (val) => (val && val !== '-' && val !== '—') ? val : '';
        const em = cleanTime(ptg.entree) || cleanTime(ptg.heureEntreeMatin) || '';
        let sm = cleanTime(ptg.sortiePause) || cleanTime(ptg.heureSortieMatin) || '';
        let ea = cleanTime(ptg.retourPause) || cleanTime(ptg.heureEntreeAm) || '';
        const sa = cleanTime(ptg.sortie) || cleanTime(ptg.heureSortieAm) || '';

        const isContinu = Boolean(ptg.horaireContinuJour || ptg.horaireContinu);

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
            motifAjustement: ''
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
            const res = await attendanceService.getFicheEmploye(idEmployeEffective, {
                period: 'periode',
                idPeriode: idPeriodeEffective
            });
            // Les pointages journaliers sont renvoyés dans res.pointagesData (PointageJourDTO)
            const list = res?.pointagesData || res?.pointages || res?.jours || res?.lignes || [];
            setPointagesList(list);
            setStatutOptions(res.statutsCorrection || []);
            setCanCorrect(res.availableActions?.CORRECT_DAY === true);

            if (initialDate && list.length > 0) {
                const match = list.find(p => (p.dateComplete === initialDate || p.datePointage === initialDate || p.date === initialDate));
                if (match) {
                    handleSelectJour(match);
                }
            }
        } catch (err) {
            console.error('Erreur chargement fiche pointage:', err);
            setError("Impossible de charger les pointages de la période pour ce salarié.");
        } finally {
            setLoading(false);
        }
    }, [idEmployeEffective, idPeriodeEffective, initialDate, handleSelectJour]);

    // Effet : recharge la fiche à l'ouverture de la modale.
    useEffect(() => {
        if (isOpen) {
            setSelectedJour(null);
            loadFiche();
        }
    }, [isOpen, loadFiche]);

    useEffect(() => {
        if (!isOpen || !selectedJour) return undefined;
        const date = selectedJour.dateComplete || selectedJour.datePointage || selectedJour.date;
        const site = selectedJour.idSite || employe?.idSite;
        if (!date || !site) {
            setAllowancesError('Site ou date indisponible pour charger les indemnités.');
            return undefined;
        }
        let cancelled = false;
        setAllowancesLoading(true);
        setAllowancesError('');
        attendanceService.getGrilleSaisie({ idSite: site, date })
            .then((grid) => {
                if (cancelled) return;
                const row = grid?.grilleData?.find((item) => Number(item.idEmploye) === Number(idEmployeEffective));
                if (!row) throw new Error('La journée du salarié est introuvable dans la grille.');
                const definitions = grid.siteIndemnites || [];
                const selections = {};
                definitions.forEach((allowance) => {
                    const current = row.dynamicIndemnites?.[String(allowance.idIndemnite)] || {};
                    selections[String(allowance.idIndemnite)] = {
                        accordee: Boolean(current.accordee),
                        matin: Boolean(current.matin),
                        am: Boolean(current.am)
                    };
                });
                setAvailableAllowances(definitions);
                setDynamicAllowances(selections);
            })
            .catch((cause) => {
                if (!cancelled) setAllowancesError(cause?.message || 'Indemnités du site indisponibles.');
            })
            .finally(() => {
                if (!cancelled) setAllowancesLoading(false);
            });
        return () => { cancelled = true; };
    }, [isOpen, selectedJour, idEmployeEffective, employe?.idSite]);

    const handleSaveAjustement = async (e) => {
        e.preventDefault();
        if (!canCorrect || !selectedJour?.canCorrect) return;
        if (allowancesLoading || allowancesError) {
            setError(allowancesError || 'Chargement des indemnités en cours.');
            return;
        }
        if (!formData.motifAjustement.trim()) {
            toast({
                title: 'Motif obligatoire',
                message: 'Veuillez renseigner un motif pour la traçabilité de l’ajustement.',
                type: 'warning'
            });
            return;
        }

        setSaving(true);
        try {
            const dateStr = selectedJour.dateComplete || selectedJour.datePointage || selectedJour.date;
            const payload = {
                ...formData,
                dynamicIndemnites: dynamicAllowances,
                version: selectedJour.version,
                idEmploye: idEmployeEffective,
                idPeriodePaie: idPeriodeEffective,
                datePointage: dateStr,
                idSite: selectedJour.idSite || employe?.idSite
            };

            const idPointage = selectedJour.idPointage || 0;
            const ok = await attendanceService.regulariser(idPointage, payload);
            if (ok) {
                toast({
                    title: 'Pointage rectifié',
                    message: `Le pointage du ${fmtDate(dateStr) || dateStr} a été rectifié avec succès.`,
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

    // Rendu
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Ajustement du pointage — ${employeNomEffective} (${matriculeEffective})`}
            icon={<UserCheck className="w-5 h-5 text-brand-500" />}
            size="4xl"
            className="max-w-6xl w-full"
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
                                    options={statutOptions}
                                    value={formData.statutCode}
                                    onChange={(val) => setFormData(prev => ({ ...prev, statutCode: val }))}
                                    className="h-[32px] text-xs"
                                />
                            </div>

                            {/* Option Journée continue */}
                            <div className="sm:col-span-2 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/25 rounded-none p-2.5 flex items-center justify-between">
                                <div className="flex flex-col">
                                    <span className="text-xs font-semibold text-amber-900 dark:text-amber-200 flex items-center gap-1.5">
                                        <FastForward size={13} className="shrink-0 text-amber-600 dark:text-amber-400" />
                                        Journée continue (Sans pause déjeuner)
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

                            <div className="sm:col-span-2 flex items-center gap-4 pt-1 flex-wrap text-[11px] font-semibold">
                                {allowancesLoading && <span>Chargement des indemnités…</span>}
                                {allowancesError && <span className="text-red-700">{allowancesError}</span>}
                                {!allowancesLoading && !allowancesError && availableAllowances.length === 0 && (
                                    <span>Aucune indemnité journalière configurée pour ce site.</span>
                                )}
                                {availableAllowances.map((allowance) => {
                                    const key = String(allowance.idIndemnite);
                                    const selection = dynamicAllowances[key] || {};
                                    const fractionable = allowance.estFractionnable ?? allowance.fractionnable;
                                    const updateSelection = (field, value) => setDynamicAllowances((previous) => ({
                                        ...previous,
                                        [key]: { ...previous[key], [field]: value }
                                    }));
                                    return (
                                        <div key={key} className="flex items-center gap-2">
                                            <span>{allowance.libelle || allowance.code}</span>
                                            {fractionable ? (
                                                <>
                                                    <label className="flex items-center gap-1">
                                                        <input type="checkbox" checked={Boolean(selection.matin)}
                                                            onChange={(event) => updateSelection('matin', event.target.checked)} /> Matin
                                                    </label>
                                                    <label className="flex items-center gap-1">
                                                        <input type="checkbox" checked={Boolean(selection.am)}
                                                            onChange={(event) => updateSelection('am', event.target.checked)} /> Après-midi
                                                    </label>
                                                </>
                                            ) : (
                                                <label className="flex items-center gap-1">
                                                    <input type="checkbox" checked={Boolean(selection.accordee)}
                                                        onChange={(event) => updateSelection('accordee', event.target.checked)} /> Accorder
                                                </label>
                                            )}
                                        </div>
                                    );
                                })}
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
                                disabled={!canCorrect || !selectedJour?.canCorrect || allowancesLoading || Boolean(allowancesError)}
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

                        <div className="overflow-x-auto border border-[var(--color-border-subtle)] max-h-[62vh] overflow-y-auto">
                            <Table className="w-full text-left border-collapse text-[11.5px]">
                                <Thead className="sticky top-0 bg-[var(--color-surface)] shadow-2xs z-10">
                                    <Tr className="border-b border-[var(--color-border-subtle)] text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                        <Th className="py-2 px-3">Date</Th>
                                        <Th className="py-2 px-2 text-center">Statut</Th>
                                        <Th className="py-2 px-2 text-center">Matin</Th>
                                        <Th className="py-2 px-2 text-center">Après-midi</Th>
                                        <Th className="py-2 px-2 text-center">Total</Th>
                                        <Th className="py-2 px-2 text-center">Retard</Th>
                                        <Th className="py-2 px-2 text-center">Indemnités accordées</Th>
                                        <Th className="py-2 px-3 text-right">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {pointagesList.length === 0 ? (
                                        <Tr>
                                            <Td colSpan={8} className="py-8 text-center text-[var(--color-fg-muted)]">
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
                                            const isContinuRow = Boolean(ptg.horaireContinuJour || ptg.horaireContinu);

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
                                            
                                            const heuresStr = ptg.heures || (totalH > 0 ? formatHeuresMinutes(totalH, true) : '00h00');
                                            const ecartStr = ptg.ecart || (ptg.minutesRetard > 0 ? `+${ptg.minutesRetard}m` : '00 min');

                                            const grantedAllowances = Object.values(ptg.indemnites || {})
                                                .filter((allowance) => allowance?.accordee && Number(allowance.montant) > 0);

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
                                                                isConge ? 'sky' :
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
                                                             isConge ? (ptg.typeConge ? `Congé (${ptg.typeConge})` : 'Congé') :
                                                             (ptg.statut || (isPresent ? 'Présent' : 'Absent'))}
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
                                                    <Td className="py-2 px-2 text-center font-mono text-[10px]">
                                                        {grantedAllowances.length === 0 ? '—' : grantedAllowances.map((allowance) => (
                                                            <span key={allowance.code} className="block">
                                                                {allowance.code} · {Number(allowance.montant).toLocaleString('fr-FR')} Ar
                                                            </span>
                                                        ))}
                                                    </Td>
                                                    <Td className="py-2 px-3 text-right">
                                                        <Button
                                                            type="button"
                                                            variant="outline"
                                                            size="xs"
                                                            disabled={!ptg.canCorrect}
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
