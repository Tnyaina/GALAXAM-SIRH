import { useState, useEffect, useCallback } from 'react';
import pointageService from '@/api/pointage/pointageService';
import { fmtDateInput } from '@/utils/paieUtils';

export function useEmployePointages(idEmploye) {
    const todayStr = fmtDateInput(new Date());

    const [selectedDate, setSelectedDate] = useState(todayStr);
    const [viewPeriod, setViewPeriod] = useState('mois'); // 'jour' | 'semaine' | 'mois'

    const [pointagesData, setPointagesData] = useState([]);
    const [kpis, setKpis] = useState({
        joursTravailles: '0 j',
        joursPrevus: '18 prévus',
        heuresRealisees: '00h00',
        heuresObjectif: 'objectif 144h',
        retardCumule: '00h00',
        retardOccurrences: '0 occurrence',
        heuresSupp: '00h00',
        heuresSuppStatut: 'à valider',
        absences: '00 j',
        absencesStatut: 'aucune'
    });
    const [recap, setRecap] = useState({
        tempsTheorique: '144h00',
        tempsRealise: '00h00',
        tempsAbsence: '00h00',
        heuresSupp: '00h00',
        retards: '00h00'
    });
    const [reglesAppliquees, setReglesAppliquees] = useState({
        horaireJournalier: '08h00',
        pause: '01h00',
        tolerance: '05 min',
        debutNormal: '08:00'
    });

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadFichePointage = useCallback(async () => {
        if (!idEmploye) return;
        setLoading(true);
        setError(null);
        try {
            const data = await pointageService.getFichePointageEmploye(idEmploye, {
                date: selectedDate,
                period: viewPeriod
            });

            if (data) {
                if (data.pointagesData) setPointagesData(data.pointagesData);
                if (data.kpis) setKpis(data.kpis);
                if (data.recap) setRecap(data.recap);
                if (data.reglesAppliquees) setReglesAppliquees(data.reglesAppliquees);
            }
        } catch (err) {
            console.error('Erreur chargement pointages dynamiques employé:', err);
            setError("Impossible de charger les pointages de cet employé.");
        } finally {
            setLoading(false);
        }
    }, [idEmploye, selectedDate, viewPeriod]);

    useEffect(() => {
        loadFichePointage();
    }, [loadFichePointage]);

    return {
        selectedDate,
        setSelectedDate,
        viewPeriod,
        setViewPeriod,
        pointagesData,
        kpis,
        recap,
        reglesAppliquees,
        loading,
        error,
        reload: loadFichePointage
    };
}
