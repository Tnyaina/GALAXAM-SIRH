import { useState, useEffect, useCallback } from 'react';
import pointageAnomalieService from '@/services/pointageAnomalieService';
import periodePaieService from '@/services/periodePaieService';

export function usePointageAnomalies() {
    const [anomalies, setAnomalies] = useState([]);
    const [stats, setStats] = useState({ total: 0, incomplets: 0, retards: 0, absences: 0 });
    const [periodes, setPeriodes] = useState([]);

    const [selectedSite, setSelectedSite] = useState(0); // 0 = Tous
    const [selectedPeriode, setSelectedPeriode] = useState('');
    const [dateFilterMode, setDateFilterMode] = useState('ALL'); // 'ALL' | 'SPECIFIC' | 'INTERVAL'
    const [selectedDate, setSelectedDate] = useState('');
    const [dateDebut, setDateDebut] = useState('');
    const [dateFin, setDateFin] = useState('');
    const [typeFilter, setTypeFilter] = useState('ALL'); // 'ALL' | 'INCOMPLET' | 'RETARD' | 'ABSENCE'
    const [searchTerm, setSearchTerm] = useState('');

    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    // Pagination
    const [currentPage, setCurrentPage] = useState(1);
    const pageSize = 10;

    const loadPeriodes = useCallback(async () => {
        try {
            const data = await periodePaieService.getAllPeriodes();
            setPeriodes(data || []);
            if (data && data.length > 0 && !selectedPeriode) {
                // Sélectionner par défaut la période en cours ou la plus récente
                const active = data.find(p => p.statutCode === 'EN_COURS' || p.statutCode === 'A_VALIDER') || data[0];
                if (active) setSelectedPeriode(active.idPeriodePaie);
            }
        } catch (err) {
            console.error('Erreur chargement périodes pour anomalies:', err);
        }
    }, [selectedPeriode]);

    const loadAnomalies = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = {
                idSite: selectedSite > 0 ? selectedSite : undefined,
                idPeriodePaie: selectedPeriode ? selectedPeriode : undefined,
                typeAnomalie: typeFilter,
                search: searchTerm.trim() || undefined
            };

            if (dateFilterMode === 'SPECIFIC' && selectedDate) {
                params.datePointage = selectedDate;
            } else if (dateFilterMode === 'INTERVAL') {
                if (dateDebut) params.dateDebut = dateDebut;
                if (dateFin) params.dateFin = dateFin;
            }

            const res = await pointageAnomalieService.getAnomalies(params);
            if (res) {
                setAnomalies(res.anomalies || []);
                setStats(res.stats || { total: 0, incomplets: 0, retards: 0, absences: 0 });
            }
        } catch (err) {
            console.error('Erreur chargement anomalies:', err);
            setError("Impossible de charger la liste des anomalies de pointage.");
        } finally {
            setLoading(false);
        }
    }, [selectedSite, selectedPeriode, dateFilterMode, selectedDate, dateDebut, dateFin, typeFilter, searchTerm]);

    // Réinitialiser la pagination si les filtres changent
    useEffect(() => {
        setCurrentPage(1);
    }, [selectedSite, selectedPeriode, dateFilterMode, selectedDate, dateDebut, dateFin, typeFilter, searchTerm]);

    useEffect(() => {
        loadPeriodes();
    }, [loadPeriodes]);

    useEffect(() => {
        loadAnomalies();
    }, [loadAnomalies]);

    const regulariser = async (idPointage, payload) => {
        setSaving(true);
        setError(null);
        try {
            const res = await pointageAnomalieService.regulariserAnomalie(idPointage, payload);
            if (res.success) {
                await loadAnomalies();
                return true;
            } else {
                setError(res.message);
                return false;
            }
        } catch (err) {
            setError(err.response?.data?.message || "Erreur lors de la régularisation de l'anomalie.");
            return false;
        } finally {
            setSaving(false);
        }
    };

    const regulariserStandard = async (anomalie) => {
        const isTMM = anomalie.idSite === 2;
        const payload = {
            actionType: 'PRESENCE',
            heureEntreeMatin: '08:00',
            heureSortieMatin: '12:00',
            heureEntreeAm: isTMM ? '14:00' : '13:00',
            heureSortieAm: isTMM ? '18:00' : '17:00',
            motifAjustement: 'Régularisation horaire normal',
            idEmploye: anomalie.idEmploye,
            datePointage: anomalie.datePointage,
            idPeriodePaie: anomalie.idPeriodePaie,
            idSite: anomalie.idSite
        };
        return regulariser(anomalie.idPointage || 0, payload);
    };

    const justifierRetardRapide = async (anomalie, motif) => {
        const payload = {
            actionType: 'PRESENCE',
            heureEntreeMatin: anomalie.heureEntreeMatin || '08:00',
            heureSortieMatin: anomalie.heureSortieMatin || '12:00',
            heureEntreeAm: anomalie.heureEntreeAm || '13:00',
            heureSortieAm: anomalie.heureSortieAm || '17:00',
            motifAjustement: motif || 'Retard justifié',
            idEmploye: anomalie.idEmploye,
            datePointage: anomalie.datePointage,
            idPeriodePaie: anomalie.idPeriodePaie,
            idSite: anomalie.idSite
        };
        return regulariser(anomalie.idPointage || 0, payload);
    };

    const regulariserBatch = async (items) => {
        setSaving(true);
        setError(null);
        try {
            const res = await pointageAnomalieService.regulariserBatch(items);
            if (res.success) {
                await loadAnomalies();
                return res;
            } else {
                setError(res.message);
                return { success: false, message: res.message };
            }
        } catch (err) {
            const msg = err.response?.data?.message || "Erreur lors du traitement par lot des anomalies.";
            setError(msg);
            return { success: false, message: msg };
        } finally {
            setSaving(false);
        }
    };

    return {
        anomalies,
        stats,
        periodes,
        selectedSite,
        setSelectedSite,
        selectedPeriode,
        setSelectedPeriode,
        dateFilterMode,
        setDateFilterMode,
        selectedDate,
        setSelectedDate,
        dateDebut,
        setDateDebut,
        dateFin,
        setDateFin,
        typeFilter,
        setTypeFilter,
        searchTerm,
        setSearchTerm,
        currentPage,
        setCurrentPage,
        pageSize,
        loading,
        saving,
        error,
        setError,
        loadAnomalies,
        regulariser,
        regulariserStandard,
        justifierRetardRapide,
        regulariserBatch
    };
}
