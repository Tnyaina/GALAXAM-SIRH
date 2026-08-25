import { useState, useEffect, useCallback, useMemo } from 'react';
import pointageSaisieService from '@/api/pointage/pointageSaisieService';
import periodePaieService from '@/services/periodePaieService';
import { fmtDateInput } from '@/utils/paieUtils';

export function usePointageSaisie() {
    const todayStr = fmtDateInput(new Date());

    const [selectedSite, setSelectedSite] = useState(1); // 1 = TNR Siège, 2 = TMM Agence
    const [selectedDate, setSelectedDate] = useState(todayStr);
    const [viewMode, setViewMode] = useState('grille'); // 'grille' | 'gantt'
    const [ganttPeriod, setGanttPeriod] = useState('semaine'); // 'jour' | 'semaine' | 'mois'

    const [searchTerm, setSearchTerm] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const pageSize = 15;

    const [grilleData, setGrilleData] = useState([]);
    const [ganttData, setGanttData] = useState([]);
    const [periodeInfo, setPeriodeInfo] = useState(null);
    const [siteParams, setSiteParams] = useState({
        heureDebutMatin: '08:00',
        heureFinMatin: '12:00',
        heureDebutAm: '13:00',
        heureFinAm: '17:00',
        horaireContinu: false,
        siteDeplacementActif: true,
        siteRepasActif: false
    });

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [successMsg, setSuccessMsg] = useState(null);

    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            if (viewMode === 'grille') {
                const data = await pointageSaisieService.getGrilleSaisie({
                    idSite: selectedSite,
                    date: selectedDate
                });
                if (data) {
                    const sParams = data.siteParams || siteParams;
                    if (data.siteParams) setSiteParams(data.siteParams);
                    if (data.grilleData) {
                        const computedData = data.grilleData.map((row) => calculateRowValues(row, sParams));
                        setGrilleData(computedData);
                    }
                    setPeriodeInfo(data.periodeInfo || null);
                }
            } else {
                const data = await pointageSaisieService.getGanttView({
                    idSite: selectedSite,
                    date: selectedDate,
                    period: ganttPeriod
                });
                setGanttData(data || []);
            }
        } catch (err) {
            console.error('Erreur chargement saisie pointages:', err);
            setError("Impossible de charger les données de pointage pour ce site.");
        } finally {
            setLoading(false);
        }
    }, [selectedSite, selectedDate, viewMode, ganttPeriod]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    // Calcul des indicateurs et statut d'une ligne selon les paramètres du site (RG-PTG-01)
    const calculateRowValues = (row, sParams) => {
        const parseMins = (str) => {
            if (!str || str.length < 5 || str === '—') return null;
            const parts = str.split(':');
            return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
        };

        const em = parseMins(row.heureEntreeMatin);
        const sm = parseMins(row.heureSortieMatin);
        const ea = parseMins(row.heureEntreeAm);
        const sa = parseMins(row.heureSortieAm);

        const isCongeFull = row.typeCongeJour === 'FULL_DAY' || row.codeStatut === 'CONGE';
        const isCongeMatin = row.typeCongeJour === 'MATIN';
        const isCongeAm = row.typeCongeJour === 'APRES_MIDI';

        const isPauseAuto = (em !== null && sa !== null && sm === null && ea === null);
        let smEffectif = sm;
        let eaEffectif = ea;

        if (isPauseAuto) {
            // Déduction automatique de la pause standard du site
            smEffectif = parseMins(sParams?.heureFinMatin || '12:00');
            eaEffectif = parseMins(sParams?.heureDebutAm || (sParams?.horaireContinu ? '13:00' : '14:00'));
        }

        let hMatin = (em !== null && smEffectif !== null && smEffectif >= em) ? (smEffectif - em) : 0;
        let hAm = (eaEffectif !== null && sa !== null && sa >= eaEffectif) ? (sa - eaEffectif) : 0;

        if (isCongeMatin) hMatin = 240; // 4h
        if (isCongeAm) hAm = 240; // 4h

        let totalMins = isCongeFull ? 0 : (hMatin + hAm);
        let totalH = Math.round((totalMins / 60.0) * 100) / 100;

        let hasValidationError = false;
        if (em !== null && sm !== null && em >= sm) hasValidationError = true;
        if (ea !== null && sa !== null && ea >= sa) hasValidationError = true;
        if (sm !== null && ea !== null && sm > ea) hasValidationError = true;
        if (em !== null && ea !== null && em >= ea) hasValidationError = true;
        if (em !== null && sa !== null && em >= sa) hasValidationError = true;

        // Calcul du retard cumulé (Arrivée Matin + Retour Pause)
        const startMatinNormal = parseMins(sParams?.heureDebutMatin || '08:00');
        const startAmNormal = parseMins(sParams?.heureDebutAm || (sParams?.horaireContinu ? '13:00' : '14:00'));

        let retMatin = (em !== null && startMatinNormal !== null && em > startMatinNormal) ? (em - startMatinNormal) : 0;
        let retAm = (!isPauseAuto && ea !== null && startAmNormal !== null && ea > startAmNormal) ? (ea - startAmNormal) : 0;
        let totalMinutesRetard = retMatin + retAm;

        // Détection fine et rigoureuse du statut
        let codeStatut = 'PRESENT';
        if (isCongeFull) {
            codeStatut = 'CONGE';
        } else if (hasValidationError) {
            codeStatut = 'ANOMALIE_SAISIE';
        } else if (em === null && sm === null && ea === null && sa === null) {
            codeStatut = row.estAutonome ? 'DISPENSE_POINTAGE' : 'NON_SAISI';
        } else if (isPauseAuto) {
            // Arrivée matin et départ soir avec pause standard déduite
            codeStatut = totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT';
        } else if ((em !== null && sm === null) || (ea !== null && sa === null) || (em === null && sm !== null) || (ea === null && sa !== null)) {
            // Badgeage orphelin / incomplet
            codeStatut = 'ANOMALIE_SAISIE';
        } else if (em !== null && sm !== null && ea === null && sa === null) {
            // Matin seul effectué
            codeStatut = isCongeAm ? (totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT') : 'ANOMALIE_SAISIE';
        } else if (em === null && sm === null && ea !== null && sa !== null) {
            // Après-midi seul effectué
            codeStatut = isCongeMatin ? (totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT') : 'ANOMALIE_SAISIE';
        } else if (totalH === 0.0) {
            codeStatut = 'ABSENCE_NON_JUSTIFIEE';
        } else if (totalMinutesRetard > 0) {
            codeStatut = 'RETARD';
        } else {
            codeStatut = 'PRESENT';
        }

        return {
            ...row,
            totalHeures: row.estAutonome ? 8.0 : totalH,
            minutesRetard: totalMinutesRetard,
            codeStatut,
            hasValidationError,
            isPauseAuto
        };
    };

    // Recalcul en temps réel lors de la modification des heures d'une ligne
    const updateRowHours = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const updated = { ...row, [field]: value };
                return calculateRowValues(updated, siteParams);
            })
        );
    };

    // Remplir les horaires standards du site pour les employés non encore saisis
    const autoFillStandardHours = () => {
        const hDebM = siteParams.heureDebutMatin || '08:00';
        const hFinM = siteParams.heureFinMatin || '12:00';
        const hDebA = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
        const hFinA = siteParams.heureFinAm || '17:00';

        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.codeStatut === 'CONGE' || row.estAutonome) return row;
                // Si la ligne est encore vierge/non saisie
                const isBlank = !row.heureEntreeMatin && !row.heureSortieMatin && !row.heureEntreeAm && !row.heureSortieAm;
                if (!isBlank) return row;

                const filled = {
                    ...row,
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA
                };
                return calculateRowValues(filled, siteParams);
            })
        );
    };

    // Déduire et expliciter la pause pour toutes les lignes ayant Entrée Matin et Sortie Soir
    const autoDeduceAllPauses = () => {
        const hFinM = siteParams.heureFinMatin || '12:00';
        const hDebA = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');

        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.codeStatut === 'CONGE' || row.estAutonome) return row;
                if (row.heureEntreeMatin && row.heureSortieAm && (!row.heureSortieMatin || !row.heureEntreeAm)) {
                    const filled = {
                        ...row,
                        heureSortieMatin: row.heureSortieMatin || hFinM,
                        heureEntreeAm: row.heureEntreeAm || hDebA
                    };
                    return calculateRowValues(filled, siteParams);
                }
                return row;
            })
        );
    };

    // Modification d'une case d'indemnité
    const updateRowIndemnite = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => (row.idEmploye === idEmploye ? { ...row, [field]: value } : row))
        );
    };

    // Filtrage et Pagination
    const filteredGrilleData = useMemo(() => {
        if (!searchTerm || !searchTerm.trim()) return grilleData;
        const q = searchTerm.toLowerCase().trim();
        return grilleData.filter(
            (r) =>
                r.nom?.toLowerCase().includes(q) ||
                r.prenom?.toLowerCase().includes(q) ||
                r.matricule?.toLowerCase().includes(q)
        );
    }, [grilleData, searchTerm]);

    // Réinitialiser à la page 1 si les critères de sélection changent
    useEffect(() => {
        setCurrentPage(1);
    }, [selectedSite, selectedDate, searchTerm]);

    const totalPages = Math.ceil(filteredGrilleData.length / pageSize) || 1;

    // Sécurité ajustement : si la page courante dépasse le total de pages
    useEffect(() => {
        if (currentPage > totalPages && totalPages > 0) {
            setCurrentPage(1);
        }
    }, [totalPages, currentPage]);

    const paginatedGrilleData = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return filteredGrilleData.slice(start, start + pageSize);
    }, [filteredGrilleData, currentPage, pageSize]);

    // KPIs de synthèse
    const kpis = useMemo(() => {
        const total = grilleData.length;
        const presents = grilleData.filter((r) => r.codeStatut === 'PRESENT' || r.codeStatut === 'RETARD').length;
        const retards = grilleData.filter((r) => (r.minutesRetard || 0) > 0).length;
        const absences = grilleData.filter((r) => r.codeStatut === 'ABSENCE_NON_JUSTIFIEE' || r.codeStatut === 'ABSENT').length;
        return { total, presents, retards, absences };
    }, [grilleData]);

    // Sauvegarde par lot
    const savePointages = async () => {
        const hasErrors = grilleData.some((r) => r.hasValidationError);
        if (hasErrors) {
            setError("Veuillez corriger les erreurs de saisie (horaires incohérents) avant de sauvegarder.");
            return;
        }

        setSaving(true);
        setError(null);
        setSuccessMsg(null);
        try {
            const resp = await pointageSaisieService.saveGrilleSaisie(grilleData);
            if (resp && resp.success) {
                setSuccessMsg(resp.message || "Pointages enregistrés avec succès.");
                await loadData();
            } else {
                setError(resp?.message || "Erreur lors de l'enregistrement des pointages.");
            }
        } catch (err) {
            console.error('Erreur sauvegarde pointages:', err);
            const msg = err.response?.data?.message || "Une erreur est survenue lors de l'enregistrement.";
            setError(msg);
        } finally {
            setSaving(false);
        }
    };

    const updatePeriodeStatutDirect = async (idPeriode, statutCode) => {
        try {
            setSaving(true);
            const resp = await periodePaieService.updateStatut(idPeriode, statutCode);
            if (resp.success) {
                await loadData();
                return true;
            } else {
                setError(resp.message);
                return false;
            }
        } catch (err) {
            setError(err.response?.data?.message || "Erreur lors de la mise à jour du statut de la période.");
            return false;
        } finally {
            setSaving(false);
        }
    };

    return {
        selectedSite,
        setSelectedSite,
        selectedDate,
        setSelectedDate,
        viewMode,
        setViewMode,
        ganttPeriod,
        setGanttPeriod,
        searchTerm,
        setSearchTerm,
        currentPage,
        setCurrentPage,
        totalPages,
        grilleData: paginatedGrilleData,
        allGrilleData: grilleData,
        ganttData,
        periodeInfo,
        siteParams,
        kpis,
        loading,
        saving,
        error,
        successMsg,
        setError,
        setSuccessMsg,
        loadData,
        updateRowHours,
        updateRowIndemnite,
        autoFillStandardHours,
        autoDeduceAllPauses,
        updatePeriodeStatutDirect,
        savePointages
    };
}
