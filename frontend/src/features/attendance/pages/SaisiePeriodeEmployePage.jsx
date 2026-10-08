import { useState, useEffect, useMemo, useRef, useCallback, lazy, Suspense } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import {
    Calendar,
    ChevronLeft,
    ChevronRight,
    Users,
    User,
    Clock,
    ClockCheck,
    Coffee,
    Save,
    Search,
    AlertTriangle,
    RotateCcw,
    CheckCircle2,
    Briefcase,
    ShieldCheck,
    CalendarCheck,
    Building2,
    FastForward,
    Palmtree,
    Download,
    Upload
} from 'lucide-react';
import Card from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Alert from '@/components/ui/feedback/Alert';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import TimeInput from '@/components/ui/forms/TimeInput';
import StatusPickerPopover from '@/features/attendance/components/controls/StatusPickerPopover';
import PeriodeNavigator from '@/components/ui/navigation/PeriodeNavigator';
import { attendanceService, pointageSyntheseService, periodePaieService } from '@/features/attendance/api/attendanceApi';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';
import { formatHeuresDecimal, formatHeuresMinutes, formatMonnaie } from '@/features/attendance/utils/attendanceDisplayUtils';
import { getTodayDateString, fmtDate } from '@/utils/dateUtils';

const AnomalieRegularisationModal = lazy(() => import('@/features/attendance/components/modals/AnomalieRegularisationModal'));

/** Nettoie une chaîne horaire pour ne pas garder de tiret ou placeholder */
const cleanTime = (t) => {
    if (!t || t === '—' || t === '-' || t === '--:--' || t === 'null' || t === 'undefined') return '';
    return String(t).trim();
};

/** Convertit "HHhMM" ou "HH:MM" en heures décimales */
const parseHhMmToDecimal = (str) => {
    if (!str || typeof str !== 'string') return 0.0;
    const clean = str.replace('h', ':').trim();
    const parts = clean.split(':');
    if (parts.length >= 2) {
        const h = parseInt(parts[0], 10) || 0;
        const m = parseInt(parts[1], 10) || 0;
        return Math.round((h + m / 60) * 100) / 100;
    }
    return parseFloat(str) || 0.0;
};

/** Comparateur de modification pour une ligne journalière */
const isDayRowModified = (current, initial) => {
    if (!current || !initial) return false;
    const normalize = (t) => cleanTime(t);

    const timeFields = ['heureEntreeMatin', 'heureSortieMatin', 'heureEntreeAm', 'heureSortieAm'];
    if (timeFields.some(f => normalize(current[f]) !== normalize(initial[f]))) return true;

    if (Boolean(current.horaireContinu) !== Boolean(initial.horaireContinu)) return true;
    if ((current.codeStatut ?? '') !== (initial.codeStatut ?? '')) return true;

    // Déplacement et repas
    if (Boolean(current.indemniteRepas) !== Boolean(initial.indemniteRepas)) return true;
    if (Boolean(current.deplacementMatin) !== Boolean(initial.deplacementMatin)) return true;
    if (Boolean(current.deplacementAm) !== Boolean(initial.deplacementAm)) return true;

    return JSON.stringify(current.dynamicIndemnites || {}) !== JSON.stringify(initial.dynamicIndemnites || {});
};

export default function SaisiePeriodeEmployePage({
    activePeriode: propActivePeriode = null,
    periodesDisponibles: propPeriodesList = null,
    sitesDisponibles: propSitesList = null
}) {
    const toast = useToast();
    const { hasPermission } = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();

    const canWrite = hasPermission('POINTAGE_WRITE');

    // 1. Période & Sites
    const [periodesList, setPeriodesList] = useState(() => propPeriodesList || []);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(() => {
        const paramP = searchParams.get('periode');
        return paramP ? Number(paramP) : (propActivePeriode?.idPeriodePaie || null);
    });
    const [sitesList, setSitesList] = useState(() => propSitesList || []);
    const [sitesLoadError, setSitesLoadError] = useState(null);
    const [selectedSiteId, setSelectedSiteId] = useState(() => {
        const paramS = searchParams.get('site');
        return paramS ? Number(paramS) : null;
    });

    // 2. Collaborateur
    const [employesList, setEmployesList] = useState([]);
    const [selectedEmployeId, setSelectedEmployeId] = useState(() => {
        const paramE = searchParams.get('employe') || searchParams.get('idEmploye');
        return paramE ? Number(paramE) : null;
    });

    // 3. Recherche sur l'écran d'accueil sans employé sélectionné
    const [employeeSearchFilter, setEmployeeSearchFilter] = useState('');
    const [employeeSearchQuery, setEmployeeSearchQuery] = useState('');
    const [employeeListLoading, setEmployeeListLoading] = useState(false);
    const [employeeListError, setEmployeeListError] = useState(null);
    const [employeeListPage, setEmployeeListPage] = useState(1);
    const [employeeListTotalPages, setEmployeeListTotalPages] = useState(1);
    const [employeeListTotal, setEmployeeListTotal] = useState(0);
    const [employeeListReload, setEmployeeListReload] = useState(0);

    // 4. Données Fiche & Grille Journalière
    const [ficheData, setFicheData] = useState(null);
    const canEditPeriod = canWrite && ficheData?.availableActions?.CORRECT_DAY !== false;
    const [siteParams, setSiteParams] = useState(null);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [extraImportIndemnites, setExtraImportIndemnites] = useState([]);
    const [importPreview, setImportPreview] = useState(null);
    const [importSelections, setImportSelections] = useState({});
    const [importSignals, setImportSignals] = useState({});
    const [importLoading, setImportLoading] = useState(false);
    const importFileRef = useRef(null);
    useEffect(() => {
        setImportSignals({});
    }, [selectedEmployeId, selectedPeriodeId]);
    const displayedIndemnites = useMemo(() => {
        const byId = new Map();
        [...siteIndemnites, ...extraImportIndemnites].forEach(ind => byId.set(String(ind.idIndemnite), ind));
        return [...byId.values()];
    }, [siteIndemnites, extraImportIndemnites]);
    const [derogationsMap, setDerogationsMap] = useState({});
    const [regularisationRow, setRegularisationRow] = useState(null);
    const [grilleRows, setGrilleRows] = useState([]);
    const [initialRows, setInitialRows] = useState([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const [filterJour, setFilterJour] = useState('ALL'); // ALL | MISSING | ANOMALY

    // Références pour timers et preview
    const previewTimers = useRef(new Map());
    const loadReqId = useRef(0);
    const employeeListReqId = useRef(0);

    // Initialisation des périodes si non fournies
    useEffect(() => {
        if (!propPeriodesList || propPeriodesList.length === 0) {
            periodePaieService.getPeriodOptions().then(data => {
                if (data && Array.isArray(data) && data.length > 0) {
                    setPeriodesList(data);
                    if (!selectedPeriodeId) {
                        const active = data.find(p => p.statutCode === 'EN_COURS') || data[0];
                        setSelectedPeriodeId(active.idPeriodePaie);
                    }
                }
            }).catch(console.error);
        } else {
            setPeriodesList(propPeriodesList);
        }
    }, [propPeriodesList, selectedPeriodeId]);

    // Initialisation des sites si non fournis
    useEffect(() => {
        if (!propSitesList || propSitesList.length === 0) {
            attendanceService.getSites().then(data => {
                const list = (data || []).filter(s => s.estActif !== false && s.actif !== false);
                setSitesList(list);
                setSitesLoadError(null);
            }).catch(err => {
                console.error('Erreur chargement des sites:', err);
                setSitesLoadError('Impossible de charger les sites. Le filtre de site peut être incomplet.');
            });
        } else {
            const list = propSitesList.filter(s => s.estActif !== false && s.actif !== false);
            setSitesList(list);
            setSitesLoadError(null);
        }
    }, [propSitesList]);

    useEffect(() => {
        const timer = setTimeout(() => setEmployeeSearchQuery(employeeSearchFilter.trim()), 300);
        return () => clearTimeout(timer);
    }, [employeeSearchFilter]);

    // Chargement de la première page de collaborateurs. La recherche est exécutée côté serveur.
    useEffect(() => {
        if (!selectedPeriodeId) return;
        if (employeeSearchQuery && employeeSearchQuery.length < 2) {
            setEmployesList([]);
            setEmployeeListTotal(0);
            setEmployeeListTotalPages(1);
            setEmployeeListPage(1);
            setEmployeeListLoading(false);
            setEmployeeListError(null);
            return;
        }
        let cancelled = false;
        const requestId = ++employeeListReqId.current;
        setEmployeeListLoading(true);
        setEmployeeListError(null);
        setEmployesList([]);
        setEmployeeListPage(1);

        pointageSyntheseService.getSynthese({
            idPeriodePaie: selectedPeriodeId,
            idSite: selectedSiteId || undefined,
            search: employeeSearchQuery || undefined,
            page: 1,
            size: 100
        }).then(res => {
            if (cancelled || requestId !== employeeListReqId.current) return;
            const items = res?.synthese || [];
            setEmployesList(items);
            setEmployeeListTotal(Number(res?.totalElements) || 0);
            setEmployeeListTotalPages(Math.max(1, Number(res?.totalPages) || 1));
        }).catch(err => {
            if (!cancelled && requestId === employeeListReqId.current) {
                console.error('Erreur chargement collaborateurs:', err);
                setEmployeeListError(err?.response?.data?.message || 'Impossible de charger la liste des collaborateurs. Réessayez ou modifiez le filtre de site.');
            }
        }).finally(() => {
            if (!cancelled && requestId === employeeListReqId.current) setEmployeeListLoading(false);
        });

        return () => { cancelled = true; };
    }, [selectedPeriodeId, selectedSiteId, employeeSearchQuery, employeeListReload]);

    const loadMoreEmployees = async () => {
        if (employeeListLoading || employeeListPage >= employeeListTotalPages) return;
        const requestId = employeeListReqId.current;
        const nextPage = employeeListPage + 1;
        setEmployeeListLoading(true);
        setEmployeeListError(null);
        try {
            const res = await pointageSyntheseService.getSynthese({
                idPeriodePaie: selectedPeriodeId,
                idSite: selectedSiteId || undefined,
                search: employeeSearchQuery || undefined,
                page: nextPage,
                size: 100
            });
            if (requestId !== employeeListReqId.current) return;
            const items = res?.synthese || [];
            setEmployesList(current => {
                const knownIds = new Set(current.map(employee => employee.idEmploye));
                return [...current, ...items.filter(employee => !knownIds.has(employee.idEmploye))];
            });
            setEmployeeListPage(nextPage);
            setEmployeeListTotal(Number(res?.totalElements) || employeeListTotal);
            setEmployeeListTotalPages(Math.max(1, Number(res?.totalPages) || employeeListTotalPages));
        } catch (err) {
            if (requestId === employeeListReqId.current) {
                console.error('Erreur chargement des collaborateurs suivants:', err);
                setEmployeeListError(err?.response?.data?.message || 'Impossible de charger la suite de la liste. Réessayez.');
            }
        } finally {
            if (requestId === employeeListReqId.current) setEmployeeListLoading(false);
        }
    };

    // Index du collaborateur actuel dans la liste
    const currentEmpIndex = useMemo(() => {
        return employesList.findIndex(e => e.idEmploye === selectedEmployeId);
    }, [employesList, selectedEmployeId]);

    // Collaborateur courant dans la liste récap
    const currentEmpSummary = useMemo(() => {
        return employesList.find(e => e.idEmploye === selectedEmployeId) || null;
    }, [employesList, selectedEmployeId]);

    // Détection si le collaborateur est un cadre autonome
    const isAutonome = Boolean(
        ficheData?.estAutonome ||
        currentEmpSummary?.estAutonome ||
        ficheData?.categorie === 'CADRE' ||
        currentEmpSummary?.isCadre
    );

    // Chargement de la fiche employé et préparation de la grille
    const loadFicheEmploye = useCallback(async () => {
        if (!selectedEmployeId || !selectedPeriodeId) {
            setFicheData(null);
            setGrilleRows([]);
            setInitialRows([]);
            setImportPreview(null);
            setLoading(false);
            return;
        }

        const reqId = ++loadReqId.current;
        setLoading(true);
        setError(null);

        try {
            const fiche = await attendanceService.getFicheEmploye(selectedEmployeId, {
                idPeriode: selectedPeriodeId
            });
            const selectedPeriod = periodesList.find(p => Number(p.idPeriodePaie) === Number(selectedPeriodeId));
            const referenceDate = selectedPeriod?.dateDebut || fiche?.dateDebut || getTodayDateString();
            const referenceSiteId = selectedSiteId || fiche?.idSite;
            const siteGrid = referenceSiteId ? await attendanceService.getGrilleSaisie({
                idSite: referenceSiteId,
                date: referenceDate
            }) : null;

            if (reqId !== loadReqId.current) return;

            setFicheData(fiche);
            const sParams = siteGrid?.siteParams || null;
            setSiteParams(sParams);

            const sIndemnites = siteGrid?.siteIndemnites || [];
            setSiteIndemnites(sIndemnites);
            setExtraImportIndemnites([]);
            setImportPreview(null);

            // Extraire la cartographie des dérogations (exclusions) individuelles pour ce collaborateur
            const empInSiteGrid = siteGrid?.grilleData?.find(e => e.idEmploye === selectedEmployeId);
            const dMap = {};
            if (empInSiteGrid?.dynamicIndemnites) {
                Object.entries(empInSiteGrid.dynamicIndemnites).forEach(([k, v]) => {
                    if (v?.motifSuggestion) {
                        dMap[k] = v.motifSuggestion;
                    }
                });
            }
            if (empInSiteGrid?.estDerogeDeplacement) {
                const motifDep = empInSiteGrid.motifDerogationDeplacement || 'Véhicule de fonction';
                dMap['DEPLACEMENT'] = motifDep;
                dMap['DPLCT'] = motifDep;
                dMap['TRANSPORT'] = motifDep;
            }
            if (empInSiteGrid?.estDerogeRepas) {
                dMap['REPAS'] = empInSiteGrid.motifDerogationRepas || 'Exclu repas';
            }
            setDerogationsMap(dMap);

            // Transformer les jours de la fiche en lignes de saisie
            const rawPoints = fiche?.pointagesData || [];
            const empAutonome = Boolean(
                fiche?.estAutonome ||
                currentEmpSummary?.estAutonome ||
                fiche?.categorie === 'CADRE'
            );

            const rows = rawPoints.map((ptg) => {
                const isWeekend = Boolean(
                    ptg.weekend ||
                    ptg.jour?.startsWith('Sam') ||
                    ptg.jour?.startsWith('Dim') ||
                    (ptg.dateComplete && [0, 6].includes(new Date(`${ptg.dateComplete}T00:00:00`).getDay()))
                );
                const isFerie = Boolean(
                    ptg.isFerieChome ||
                    ptg.isFerie ||
                    ptg.statut === 'FERIE_CHOME' ||
                    ptg.statut === 'FERIE' ||
                    ptg.statut === 'FERIE_TRAVAILLE'
                );
                const isConge = Boolean(
                    ptg.isConge ||
                    ptg.statut === 'CONGE' ||
                    ptg.statut === 'CONGE_PAYE'
                );

                const rawEntree = cleanTime(ptg.entree);
                const rawSortieMatin = cleanTime(ptg.sortiePause);
                const rawEntreeAm = cleanTime(ptg.retourPause);
                const rawSortieAm = cleanTime(ptg.sortie);
                const hasSavedHours = Boolean(rawEntree || rawSortieAm);

                const isBlankOuvre = !isWeekend && !isFerie && !isConge && !hasSavedHours;

                // Horaires par défaut :
                // Sur jour ouvré vierge pour autonome : présomption 08:00 - 12:00 / 13:00 - 17:00
                // Sur férié chômé ou weekend sans pointage : rester vide (chômé ou repos)
                const defaultEntreeMatin = isBlankOuvre && empAutonome ? (sParams?.heureDebutMatin || '08:00') : rawEntree;
                const defaultSortieMatin = isBlankOuvre && empAutonome ? (sParams?.heureFinMatin || '12:00') : rawSortieMatin;
                const defaultEntreeAm    = isBlankOuvre && empAutonome ? (sParams?.heureDebutAm || '13:00') : rawEntreeAm;
                const defaultSortieAm    = isBlankOuvre && empAutonome ? (sParams?.heureFinAm || '17:00') : rawSortieAm;

                // Statut de la journée
                let codeStatut = ptg.statut;
                if (!codeStatut || codeStatut === 'NON_SAISI') {
                    if (isFerie) {
                        codeStatut = hasSavedHours ? 'FERIE_TRAVAILLE' : 'FERIE_CHOME';
                    } else if (isWeekend) {
                        codeStatut = hasSavedHours ? 'SAMEDI_TRAVAILLE' : 'REPOS';
                    } else if (isConge) {
                        codeStatut = 'CONGE';
                    } else if (empAutonome) {
                        codeStatut = 'DISPENSE_POINTAGE';
                    } else {
                        codeStatut = hasSavedHours ? 'PRESENT' : 'NON_SAISI';
                    }
                }

                // Indemnités dynamiques complètes selon siteIndemnites et dérogations
                const dynamicIndemnites = {};
                sIndemnites.forEach(ind => {
                    const key = String(ind.idIndemnite);
                    const fractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;
                    const derogMotif = dMap[key] || dMap[ind.code];
                    const isExclu = Boolean(derogMotif);
                    const cocheDefaut = ind.cocheParDefaut !== false;

                    const saved = ptg.indemnites?.[ind.code] || ptg.indemnites?.[key];
                    if (saved && typeof saved === 'object') {
                        const acc = Boolean(saved.accordee ?? saved.accorde);
                        const qte = Number(saved.quantite ?? (acc ? 1 : 0));
                        dynamicIndemnites[key] = {
                            accordee: acc,
                            fractionJour: qte,
                            matin: saved.matin ?? (fractionnable ? (acc && Boolean(ptg.deplacementMatin ?? true)) : undefined),
                            am: saved.am ?? (fractionnable ? (acc && Boolean(ptg.deplacementAm ?? true)) : undefined),
                            motifSuggestion: derogMotif,
                            montantApplique: saved.montant || ind.montantJour
                        };
                    } else {
                        // Valeur par défaut
                        const shouldAccord = (hasSavedHours || (isBlankOuvre && empAutonome)) && !isExclu && cocheDefaut;
                        dynamicIndemnites[key] = {
                            accordee: shouldAccord,
                            fractionJour: shouldAccord ? 1 : 0,
                            matin: fractionnable ? shouldAccord : undefined,
                            am: fractionnable ? shouldAccord : undefined,
                            motifSuggestion: derogMotif,
                            montantApplique: ind.montantJour
                        };
                    }
                });

                // Calcul Heures
                let totalHeuresDec = 0.0;
                let heuresFaitesStr = '00h00';

                if (isFerie && !hasSavedHours) {
                    totalHeuresDec = 0.0;
                    heuresFaitesStr = '00h00';
                } else if (isWeekend && !hasSavedHours) {
                    totalHeuresDec = 0.0;
                    heuresFaitesStr = '00h00';
                } else if (isBlankOuvre && empAutonome) {
                    totalHeuresDec = 8.0;
                    heuresFaitesStr = '08h00';
                } else if (ptg.totalHeuresDecimal != null && !isNaN(ptg.totalHeuresDecimal)) {
                    totalHeuresDec = Number(ptg.totalHeuresDecimal);
                    heuresFaitesStr = ptg.heures && ptg.heures !== '—' && ptg.heures !== '-' ? ptg.heures : formatHeuresMinutes(totalHeuresDec);
                } else if (ptg.heures && ptg.heures !== '—' && ptg.heures !== '-') {
                    heuresFaitesStr = ptg.heures;
                    totalHeuresDec = parseHhMmToDecimal(ptg.heures);
                }

                const repasInd = sIndemnites.find(i => i.code === 'REPAS');
                const depInd = sIndemnites.find(i => ['DEPLACEMENT', 'DPLCT', 'TRANSPORT'].includes(i.code));

                return {
                    idPointage: ptg.idPointage || null,
                    version: ptg.version ?? null,
                    idEmploye: selectedEmployeId,
                    idSite: ptg.idSite || selectedSiteId || fiche?.idSite || 1,
                    datePointage: ptg.dateComplete, // YYYY-MM-DD
                    date: ptg.date,                // 26/12
                    jour: ptg.jour,                // Ven.
                    weekend: isWeekend,
                    ferie: isFerie,
                    conge: isConge,
                    congeApprouve: Boolean(ptg.congeApprouve || (isConge && ptg.statut === 'CONGE' && !hasSavedHours && ptg.congeValide)),
                    canCorrect: ptg.canCorrect !== false,
                    heureEntreeMatin: defaultEntreeMatin,
                    heureSortieMatin: defaultSortieMatin,
                    heureEntreeAm: defaultEntreeAm,
                    heureSortieAm: defaultSortieAm,
                    horaireContinu: Boolean(ptg.horaireContinuJour),
                    codeStatut: codeStatut,
                    heuresFaites: heuresFaitesStr,
                    totalHeuresDecimal: totalHeuresDec,
                    minutesRetard: empAutonome ? 0 : (ptg.minutesRetard || 0),
                    indemniteRepas: repasInd ? Boolean(dynamicIndemnites[String(repasInd.idIndemnite)]?.accordee) : (ptg.indemniteRepas != null ? ptg.indemniteRepas : (isBlankOuvre && empAutonome ? true : false)),
                    deplacementMatin: depInd ? Boolean(dynamicIndemnites[String(depInd.idIndemnite)]?.matin) : (ptg.deplacementMatin != null ? ptg.deplacementMatin : (isBlankOuvre && empAutonome ? true : false)),
                    deplacementAm: depInd ? Boolean(dynamicIndemnites[String(depInd.idIndemnite)]?.am) : (ptg.deplacementAm != null ? ptg.deplacementAm : (isBlankOuvre && empAutonome ? true : false)),
                    dynamicIndemnites: dynamicIndemnites,
                    estAutonome: empAutonome,
                    _previewPending: false,
                    _isModified: false
                };
            });

            setGrilleRows(rows);
            setInitialRows(JSON.parse(JSON.stringify(rows)));
        } catch (err) {
            if (reqId === loadReqId.current) {
                console.error('Erreur chargement fiche saisie employé:', err);
                setError(err?.response?.data?.message || 'Impossible de charger la fiche de ce collaborateur.');
            }
        } finally {
            if (reqId === loadReqId.current) {
                setLoading(false);
            }
        }
    }, [selectedEmployeId, selectedPeriodeId, selectedSiteId, currentEmpSummary, periodesList]);

    useEffect(() => {
        loadFicheEmploye();
    }, [loadFicheEmploye]);

    // Recalcul des lignes modifiées (dirty rows)
    // IMPORTANT : Uniquement les vraies modifications par rapport à initialRows. Ne pas flagger à tort à l'ouverture !
    const modifiedRows = useMemo(() => {
        if (!initialRows || initialRows.length === 0) return [];
        return grilleRows.filter(row => {
            const initial = initialRows.find(i => i.datePointage === row.datePointage);
            return isDayRowModified(row, initial);
        });
    }, [grilleRows, initialRows]);

    const dirtyCount = modifiedRows.length;
    useUnsavedChanges(dirtyCount > 0, `${dirtyCount} journée(s) modifiée(s) non enregistrée(s).`);

    const handleDownloadImportTemplate = async () => {
        if (!selectedEmployeId || !selectedPeriodeId) return;
        setImportLoading(true);
        try {
            const { blob, filename } = await attendanceService.getImportTemplate(selectedEmployeId, selectedPeriodeId);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (err) {
            toast.error?.(err?.response?.data?.message || 'Impossible de créer le modèle de pointage.');
        } finally {
            setImportLoading(false);
        }
    };

    const handleImportFile = async (event) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;
        if (dirtyCount > 0) {
            toast.info?.('Enregistrez les modifications de la grille avant de prévisualiser un import.');
            return;
        }
        setImportLoading(true);
        try {
            const preview = await attendanceService.previewImport(selectedEmployeId, selectedPeriodeId, file);
            setImportPreview(preview);
            setImportSelections(Object.fromEntries((preview.lines || []).map(line => [line.ligne, line.selectedByDefault])));
        } catch (err) {
            toast.error?.(err?.response?.data?.message || 'Impossible de lire le fichier de pointage.');
        } finally {
            setImportLoading(false);
        }
    };

    const handleApplyImport = () => {
        const selected = (importPreview?.lines || []).filter(line => importSelections[line.ligne]
            && ['IMPORT', 'SIGNAL'].includes(line.decision) && line.errors.length === 0);
        const current = new Map(grilleRows.map(row => [row.datePointage, row]));
        const importByDate = new Map();
        const signalsByDate = {};
        const allAllowances = [];
        selected.forEach(line => {
            const row = current.get(line.date);
            if (!row || !row.canCorrect || !line.proposed) return;
            if (line.eventCode) {
                signalsByDate[line.date] = {
                    code: line.eventCode,
                    allowanceOverrides: line.allowanceOverrides || {},
                    halfDayMoment: line.proposed.heureEntreeMatin && line.proposed.heureSortieMatin
                        && !line.proposed.heureEntreeAm && !line.proposed.heureSortieAm ? 'APRES_MIDI'
                        : !line.proposed.heureEntreeMatin && !line.proposed.heureSortieMatin
                            && line.proposed.heureEntreeAm && line.proposed.heureSortieAm ? 'MATIN' : null
                };
            }
            if (line.decision === 'SIGNAL') return;
            if (row.congeApprouve && !row.conge) return;
            const p = line.proposed;
            const dyn = p.dynamicIndemnites || {};
            const repas = line.allowances.find(ind => ind.code === 'REPAS');
            const dep = line.allowances.find(ind => ['DPLCT', 'DEPLACEMENT', 'TRANSPORT'].includes(ind.code));
            allAllowances.push(...line.allowances);
            importByDate.set(line.date, {
                ...row,
                heureEntreeMatin: p.heureEntreeMatin || '',
                heureSortieMatin: p.heureSortieMatin || '',
                heureEntreeAm: p.heureEntreeAm || '',
                heureSortieAm: p.heureSortieAm || '',
                horaireContinu: Boolean(p.horaireContinu),
                codeStatut: p.codeStatut || row.codeStatut,
                dynamicIndemnites: dyn,
                indemniteRepas: repas ? Boolean(dyn[String(repas.idIndemnite)]?.accordee) : false,
                deplacementMatin: dep ? Boolean(dyn[String(dep.idIndemnite)]?.matin) : false,
                deplacementAm: dep ? Boolean(dyn[String(dep.idIndemnite)]?.am) : false,
                _dateAllowances: line.allowances,
                _importPending: true,
                _previewPending: Boolean(p.heureEntreeMatin || p.heureSortieMatin || p.heureEntreeAm || p.heureSortieAm)
            });
        });
        setGrilleRows(rows => rows.map(row => importByDate.get(row.datePointage) || row));
        setImportSignals(previous => ({ ...previous, ...signalsByDate }));
        setExtraImportIndemnites(old => [...old, ...allAllowances]);
        setImportPreview(null);
        toast.success?.(`${importByDate.size} journée(s) chargée(s), ${Object.keys(signalsByDate).length} signalement(s) à régulariser.`);
    };

    const openRegularisation = (row) => {
        if (dirtyCount > 0) {
            toast.info?.('Enregistrez les heures ou indemnités modifiées avant de régulariser.');
            return;
        }
        const signal = importSignals[row.datePointage];
        setRegularisationRow(signal ? {
            ...row,
            importEventCode: signal.code,
            importHalfDayMoment: signal.halfDayMoment,
            importAllowanceOverrides: signal.allowanceOverrides
        } : row);
    };

    // Gestion du Preview temps réel
    useEffect(() => {
        const timers = previewTimers.current;
        grilleRows.filter(row => row._previewPending).forEach(row => {
            const dateKey = row.datePointage;
            if (timers.has(dateKey)) clearTimeout(timers.get(dateKey));

            const timer = setTimeout(async () => {
                try {
                    const preview = await attendanceService.daily.preview({
                        idEmploye: row.idEmploye,
                        idSite: row.idSite,
                        datePointage: row.datePointage,
                        heureEntreeMatin: row.heureEntreeMatin,
                        heureSortieMatin: row.heureSortieMatin,
                        heureEntreeAm: row.heureEntreeAm,
                        heureSortieAm: row.heureSortieAm,
                        horaireContinu: row.horaireContinu,
                        codeStatut: row.codeStatut,
                        estAutonome: row.estAutonome,
                        dynamicIndemnites: row.dynamicIndemnites
                    });

                    setGrilleRows(current => current.map(item => {
                        if (item.datePointage !== dateKey) return item;
                        return {
                            ...item,
                            heuresFaites: preview.totalHeures != null ? formatHeuresMinutes(preview.totalHeures) : item.heuresFaites,
                            totalHeuresDecimal: preview.totalHeures != null ? preview.totalHeures : item.totalHeuresDecimal,
                            minutesRetard: row.estAutonome ? 0 : (preview.minutesRetard || 0),
                            codeStatut: preview.codeStatut || item.codeStatut,
                            _previewPending: false
                        };
                    }));
                } catch (e) {
                    setGrilleRows(current => current.map(item =>
                        item.datePointage === dateKey ? { ...item, _previewPending: false } : item
                    ));
                } finally {
                    timers.delete(dateKey);
                }
            }, 300);

            timers.set(dateKey, timer);
        });

        return () => {
            timers.forEach(clearTimeout);
            timers.clear();
        };
    }, [grilleRows]);

    // Helper : synchronise automatiquement les indemnités d'une journée selon ses heures,
    // le barème du site et les dérogations accordées/exclues du salarié
    const syncRowIndemnites = useCallback((row, hours, indList = siteIndemnites, dMap = derogationsMap) => {
        const hasMatin = Boolean(hours.heureEntreeMatin || hours.heureSortieMatin);
        const hasAm = Boolean(hours.heureEntreeAm || hours.heureSortieAm);
        const hasHours = hasMatin || hasAm;

        const nextDyn = {};

        indList.forEach(ind => {
            const key = String(ind.idIndemnite);
            const fractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;
            const cocheParDefaut = ind.cocheParDefaut !== false;
            const derogMotif = dMap[key] || dMap[ind.code];
            const isDerogeExclu = Boolean(derogMotif);

            let matVal = false;
            let amVal = false;
            let accordeeVal = false;

            if (hasHours && !isDerogeExclu && cocheParDefaut) {
                if (fractionnable) {
                    matVal = hasMatin;
                    amVal = hasAm;
                    accordeeVal = matVal || amVal;
                } else {
                    accordeeVal = true;
                }
            }

            nextDyn[key] = {
                accordee: accordeeVal,
                fractionJour: fractionnable ? ((matVal ? 0.5 : 0) + (amVal ? 0.5 : 0)) : (accordeeVal ? 1 : 0),
                matin: fractionnable ? matVal : undefined,
                am: fractionnable ? amVal : undefined,
                motifSuggestion: derogMotif,
                montantApplique: ind.montantJour
            };
        });

        const repasInd = indList.find(i => i.code === 'REPAS');
        const depInd = indList.find(i => ['DEPLACEMENT', 'DPLCT', 'TRANSPORT'].includes(i.code));
        const repasObj = repasInd ? nextDyn[String(repasInd.idIndemnite)] : null;
        const depObj = depInd ? nextDyn[String(depInd.idIndemnite)] : null;

        return {
            dynamicIndemnites: nextDyn,
            indemniteRepas: repasObj ? repasObj.accordee : false,
            deplacementMatin: depObj ? Boolean(depObj.matin) : false,
            deplacementAm: depObj ? Boolean(depObj.am) : false
        };
    }, [siteIndemnites, derogationsMap]);

    // Mise à jour locale d'une heure avec coche/décochage automatique des indemnités
    const updateDayHours = (datePointage, field, value) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            const updatedHours = {
                heureEntreeMatin: field === 'heureEntreeMatin' ? value : row.heureEntreeMatin,
                heureSortieMatin: field === 'heureSortieMatin' ? value : row.heureSortieMatin,
                heureEntreeAm: field === 'heureEntreeAm' ? value : row.heureEntreeAm,
                heureSortieAm: field === 'heureSortieAm' ? value : row.heureSortieAm,
            };
            const hasHours = Object.values(updatedHours).some(Boolean);
            const syncedIndemnites = syncRowIndemnites(row, updatedHours, row._dateAllowances || siteIndemnites, derogationsMap);

            return {
                ...row,
                ...updatedHours,
                codeStatut: hasHours
                    ? (row.codeStatut === 'NON_SAISI' ? 'PRESENT' : row.codeStatut)
                    : (row.weekend ? 'REPOS' : row.ferie ? 'FERIE_CHOME' : row.conge ? 'CONGE' : 'NON_SAISI'),
                ...syncedIndemnites,
                _previewPending: true
            };
        }));
    };

    // Bascule journée continue
    const toggleDayContinu = (datePointage) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            const nextContinu = !row.horaireContinu;
            return {
                ...row,
                horaireContinu: nextContinu,
                _previewPending: true
            };
        }));
    };

    // Mise à jour statut
    const updateDayStatut = (datePointage, codeStatut, motif = '') => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            return {
                ...row,
                codeStatut,
                motifAjustement: motif,
                _previewPending: true
            };
        }));
    };

    // Mise à jour manuelle d'une indemnité dynamique (case à cocher Aller, Retour ou Globale)
    const updateRowDynamicIndemnite = (datePointage, idIndemnite, value, moment) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;

            const key = String(idIndemnite);
            const existingDyn = { ...(row.dynamicIndemnites || {}) };
            const current = { ...(existingDyn[key] || {}) };
            const boolVal = Boolean(value);

            if (moment === 'MATIN') {
                current.matin = boolVal;
            } else if (moment === 'APRES_MIDI') {
                current.am = boolVal;
            } else {
                current.accordee = boolVal;
            }

            if (moment === 'MATIN' || moment === 'APRES_MIDI') {
                current.accordee = Boolean(current.matin || current.am);
                current.fractionJour = (current.matin ? 0.5 : 0) + (current.am ? 0.5 : 0);
            } else {
                current.fractionJour = boolVal ? 1 : 0;
            }

            existingDyn[key] = current;

            const ind = (row._dateAllowances || siteIndemnites).find(i => String(i.idIndemnite) === key);
            const isRepas = ind?.code === 'REPAS';
            const isDep = ['DEPLACEMENT', 'DPLCT', 'TRANSPORT'].includes(ind?.code);

            return {
                ...row,
                dynamicIndemnites: existingDyn,
                ...(isRepas ? { indemniteRepas: current.accordee } : {}),
                ...(isDep ? {
                    deplacementMatin: Boolean(current.matin),
                    deplacementAm: Boolean(current.am)
                } : {}),
                _previewPending: true
            };
        }));
    };

    // Assistant : Appliquer Horaire Standard sur les jours ouvrés vides (avec indemnités dynamiques)
    const handleApplyStandardHours = () => {
        const hDebM = siteParams?.heureDebutMatin || '08:00';
        const hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00';
        const hFinA = siteParams?.heureFinAm || '17:00';

        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!row.canCorrect || row.weekend || row.ferie || row.conge) return row;
            const isBlank = !row.heureEntreeMatin && !row.heureSortieAm;
            if (!isBlank) return row;

            count++;
            const stdHours = {
                heureEntreeMatin: hDebM,
                heureSortieMatin: hFinM,
                heureEntreeAm: hDebA,
                heureSortieAm: hFinA
            };
            const syncedIndemnites = syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites, derogationsMap);

            return {
                ...row,
                ...stdHours,
                codeStatut: row.estAutonome ? 'DISPENSE_POINTAGE' : 'PRESENT',
                ...syncedIndemnites,
                _previewPending: true
            };
        }));

        if (count > 0) {
            toast.success?.(`Horaire standard appliqué à ${count} journée(s) ouvrée(s).`) ||
            toast({ title: 'Horaire standard appliqué', message: `${count} journée(s) mise(s) à jour.`, type: 'success' });
        } else {
            toast.info?.("Aucune journée ouvrée vide à remplir.") ||
            toast({ title: 'Information', message: 'Toutes les journées ouvrées sont déjà renseignées.', type: 'info' });
        }
    };

    // Assistant : Déduire les pauses sur les journées continues non cochées
    const handleDeducePauses = () => {
        const hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00';

        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!row.canCorrect || row.weekend || row.ferie || row.conge) return row;
            if (row.heureEntreeMatin && row.heureSortieAm && (!row.heureSortieMatin || !row.heureEntreeAm) && !row.horaireContinu) {
                count++;
                return {
                    ...row,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    _previewPending: true
                };
            }
            return row;
        }));

        if (count > 0) {
            toast.success?.(`Pause déjeuner insérée sur ${count} journée(s).`) ||
            toast({ title: 'Pause déjeuner déduite', message: `${count} journée(s) mise(s) à jour.`, type: 'success' });
        } else {
            toast.info?.("Aucune journée continue nécessitant l'insertion de pause.") ||
            toast({ title: 'Information', message: 'Aucune pause à déduire.', type: 'info' });
        }
    };

    // Assistant : Vider/réinitialiser une journée (décoche automatique des indemnités)
    const handleResetDay = (datePointage) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage || !row.canCorrect) return row;
            const emptyHours = { heureEntreeMatin: '', heureSortieMatin: '', heureEntreeAm: '', heureSortieAm: '' };
            const syncedIndemnites = syncRowIndemnites(row, emptyHours, row._dateAllowances || siteIndemnites, derogationsMap);

            return {
                ...row,
                ...emptyHours,
                horaireContinu: false,
                codeStatut: row.weekend ? 'REPOS' : row.ferie ? 'FERIE_CHOME' : row.conge ? 'CONGE' : 'NON_SAISI',
                totalHeuresDecimal: 0,
                heuresFaites: '00h00',
                minutesRetard: 0,
                ...syncedIndemnites,
                _previewPending: true
            };
        }));
    };

    // Assistant : Appliquer horaire standard à une journée (avec indemnités dynamiques)
    const handleApplyDayStandard = (datePointage) => {
        const hDebM = siteParams?.heureDebutMatin || '08:00';
        const hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00';
        const hFinA = siteParams?.heureFinAm || '17:00';

        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage || !row.canCorrect) return row;
            const stdHours = {
                heureEntreeMatin: hDebM,
                heureSortieMatin: hFinM,
                heureEntreeAm: hDebA,
                heureSortieAm: hFinA
            };
            const syncedIndemnites = syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites, derogationsMap);

            return {
                ...row,
                ...stdHours,
                codeStatut: row.estAutonome ? 'DISPENSE_POINTAGE' : (row.ferie ? 'FERIE_TRAVAILLE' : row.weekend ? 'SAMEDI_TRAVAILLE' : 'PRESENT'),
                ...syncedIndemnites,
                _previewPending: true
            };
        }));
    };

    // Assistant : Appliquer horaire standard à toute une semaine (avec indemnités dynamiques)
    const handleApplyWeekStandard = (weekRows) => {
        const hDebM = siteParams?.heureDebutMatin || '08:00';
        const hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00';
        const hFinA = siteParams?.heureFinAm || '17:00';

        const weekDates = new Set(weekRows.filter(r => r.canCorrect && !r.weekend && !r.ferie && !r.conge).map(r => r.datePointage));
        if (weekDates.size === 0) return;

        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!weekDates.has(row.datePointage) || !row.canCorrect) return row;
            count++;
            const stdHours = {
                heureEntreeMatin: hDebM,
                heureSortieMatin: hFinM,
                heureEntreeAm: hDebA,
                heureSortieAm: hFinA
            };
            const syncedIndemnites = syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites, derogationsMap);

            return {
                ...row,
                ...stdHours,
                codeStatut: row.estAutonome ? 'DISPENSE_POINTAGE' : 'PRESENT',
                ...syncedIndemnites,
                _previewPending: true
            };
        }));

        if (count > 0) {
            toast.success?.(`Horaire standard appliqué à ${count} jour(s) de la semaine.`) ||
            toast({ title: 'Semaine complétée', message: `${count} jour(s) mis à jour.`, type: 'success' });
        }
    };

    // Régularisation intelligente directe d'une journée par Congé / Absence
    const handleSaveRegularisationForDay = async (payload) => {
        if (!regularisationRow || !payload) return false;
        if (!canEditPeriod || regularisationRow.canCorrect === false) {
            const message = 'Cette journée ou cette période est en lecture seule.';
            toast.error?.(message) || toast({ title: 'Correction indisponible', message, type: 'error' });
            return { error: message };
        }
        try {
            const idPtg = regularisationRow.idPointage ? Number(regularisationRow.idPointage) : 0;
            const enrichedPayload = {
                ...payload,
                idEmploye: selectedEmployeId,
                idSite: regularisationRow.idSite || selectedSiteId || 1,
                idPeriodePaie: selectedPeriodeId,
                datePointage: regularisationRow.datePointage
            };
            const res = await attendanceService.regulariser(idPtg, enrichedPayload);
            if (res && (res.success || res === true)) {
                setRegularisationRow(null);
                setImportSignals(previous => {
                    const next = { ...previous };
                    delete next[regularisationRow.datePointage];
                    return next;
                });
                toast.success?.(res.message || 'Pointage régularisé avec succès.') ||
                toast({ title: 'Régularisation effectuée', message: res.message || 'Pointage régularisé.', type: 'success' });
                await loadFicheEmploye();
                return true;
            } else {
                const errorMsg = res?.error || res?.message || 'Erreur lors de la régularisation.';
                toast.error?.(errorMsg) || toast({ title: 'Erreur', message: errorMsg, type: 'error' });
                return res || false;
            }
        } catch (err) {
            console.error('Erreur régularisation pointage période:', err);
            const msg = err?.response?.data?.message || err?.message || 'Erreur lors de la régularisation.';
            toast.error?.(msg) || toast({ title: 'Erreur', message: msg, type: 'error' });
            return { error: msg };
        }
    };

    // Sauvegarde en batch
    const handleSaveBatch = async () => {
        if (!canEditPeriod) return;
        if (modifiedRows.length === 0) {
            toast.info?.("Aucune modification à enregistrer.") ||
            toast({ title: 'Information', message: 'Aucune modification à enregistrer.', type: 'info' });
            return;
        }

        setSaving(true);
        try {
            const payload = modifiedRows.map(row => ({
                idPointage: row.idPointage,
                version: row.version,
                idEmploye: row.idEmploye,
                idSite: row.idSite,
                datePointage: row.datePointage,
                heureEntreeMatin: row.heureEntreeMatin || null,
                heureSortieMatin: row.heureSortieMatin || null,
                heureEntreeAm: row.heureEntreeAm || null,
                heureSortieAm: row.heureSortieAm || null,
                horaireContinu: Boolean(row.horaireContinu),
                horaireContinuJour: Boolean(row.horaireContinu),
                codeStatut: row.codeStatut || 'PRESENT',
                estAutonome: Boolean(row.estAutonome),
                dynamicIndemnites: row.dynamicIndemnites || {}
            }));

            const res = await attendanceService.daily.saveBatch(payload);
            if (res && res.success !== false) {
                toast.success?.(`Pointages enregistrés avec succès (${payload.length} jours mis à jour).`) ||
                toast({ title: 'Enregistrement réussi', message: `${payload.length} journée(s) mise(s) à jour.`, type: 'success' });
                await loadFicheEmploye();
            } else {
                toast.error?.(res?.message || "Erreur lors de l'enregistrement des pointages.") ||
                toast({ title: 'Erreur', message: res?.message || 'Erreur lors de la sauvegarde.', type: 'error' });
            }
        } catch (err) {
            console.error('Erreur sauvegarde batch période:', err);
            const msg = err?.response?.data?.message || "Impossible d'enregistrer les pointages.";
            toast.error?.(msg) || toast({ title: 'Erreur', message: msg, type: 'error' });
        } finally {
            setSaving(false);
        }
    };

    // Raccourci clavier global Ctrl+S / ⌘+S
    useEffect(() => {
        const handleKeyDown = (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                e.preventDefault();
                if (dirtyCount > 0 && !saving) {
                    handleSaveBatch();
                }
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [dirtyCount, saving, modifiedRows]);

    // Navigation Collaborateur précédent / suivant
    const handleNavigateEmploye = (direction) => {
        if (currentEmpIndex < 0) return;
        const newIndex = currentEmpIndex + direction;
        if (newIndex >= 0 && newIndex < employesList.length) {
            const target = employesList[newIndex];
            setSelectedEmployeId(target.idEmploye);
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('employe', String(target.idEmploye));
                return next;
            });
        }
    };

    // Filtrage des lignes pour l'affichage
    const filteredRows = useMemo(() => {
        if (filterJour === 'MISSING') {
            return grilleRows.filter(r => !r.weekend && !r.ferie && !r.conge && (!r.heureEntreeMatin || r.codeStatut === 'NON_SAISI'));
        }
        if (filterJour === 'ANOMALY') {
            return grilleRows.filter(r => r.minutesRetard > 0 || r.codeStatut === 'ABSENCE_NON_JUSTIFIEE');
        }
        return grilleRows;
    }, [grilleRows, filterJour]);

    const focusedDate = searchParams.get('date');
    useEffect(() => {
        if (!focusedDate || !grilleRows.some(row => row.datePointage === focusedDate)) return;
        if (filterJour !== 'ALL') {
            setFilterJour('ALL');
            return;
        }
        const frame = requestAnimationFrame(() => {
            document.getElementById(`pointage-day-${focusedDate}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
        return () => cancelAnimationFrame(frame);
    }, [focusedDate, grilleRows, filterJour]);

    // Refs pour la navigation clavier rapide et saut automatique (similaire à SaisieQuotidiennePage / PointageGridTable)
    const inputRefs = useRef({});

    const registerInputRef = (datePointage, fieldIndex, el) => {
        if (!inputRefs.current[datePointage]) {
            inputRefs.current[datePointage] = [];
        }
        inputRefs.current[datePointage][fieldIndex] = el;
    };

    const focusNext = useCallback((datePointage, fieldIndex) => {
        // 1. Chercher le prochain champ éditable du même jour
        for (let nextIndex = fieldIndex + 1; nextIndex <= 3; nextIndex++) {
            const nextEl = inputRefs.current[datePointage]?.[nextIndex];
            if (nextEl && !nextEl.disabled && document.body.contains(nextEl)) {
                nextEl.focus();
                nextEl.select?.();
                return;
            }
        }
        // 2. Sinon, sauter au premier champ éditable du jour ouvré suivant
        const allDates = filteredRows.map(r => r.datePointage);
        const currIdx = allDates.indexOf(datePointage);
        if (currIdx !== -1 && currIdx < allDates.length - 1) {
            for (let d = currIdx + 1; d < allDates.length; d++) {
                const nextDate = allDates[d];
                const dayRefs = inputRefs.current[nextDate];
                if (!dayRefs) continue;
                for (let f = 0; f <= 3; f++) {
                    const targetEl = dayRefs[f];
                    if (targetEl && !targetEl.disabled && document.body.contains(targetEl)) {
                        targetEl.focus();
                        targetEl.select?.();
                        return;
                    }
                }
            }
        }
    }, [filteredRows]);

    const handleKeyDown = useCallback((e, datePointage, fieldIndex) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            focusNext(datePointage, fieldIndex);
            return;
        }

        if (e.key === 'ArrowRight') {
            for (let nextIndex = fieldIndex + 1; nextIndex <= 3; nextIndex++) {
                const nextEl = inputRefs.current[datePointage]?.[nextIndex];
                if (nextEl && !nextEl.disabled && document.body.contains(nextEl)) {
                    e.preventDefault();
                    nextEl.focus();
                    nextEl.select?.();
                    return;
                }
            }
            // Fin de journée -> passer au jour suivant
            const allDates = filteredRows.map(r => r.datePointage);
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx !== -1 && currIdx < allDates.length - 1) {
                for (let d = currIdx + 1; d < allDates.length; d++) {
                    const nextDate = allDates[d];
                    const dayRefs = inputRefs.current[nextDate];
                    if (!dayRefs) continue;
                    for (let f = 0; f <= 3; f++) {
                        const targetEl = dayRefs[f];
                        if (targetEl && !targetEl.disabled && document.body.contains(targetEl)) {
                            e.preventDefault();
                            targetEl.focus();
                            targetEl.select?.();
                            return;
                        }
                    }
                }
            }
            return;
        }

        if (e.key === 'ArrowLeft') {
            for (let prevIndex = fieldIndex - 1; prevIndex >= 0; prevIndex--) {
                const prevEl = inputRefs.current[datePointage]?.[prevIndex];
                if (prevEl && !prevEl.disabled && document.body.contains(prevEl)) {
                    e.preventDefault();
                    prevEl.focus();
                    prevEl.select?.();
                    return;
                }
            }
            // Début de journée -> passer à la fin du jour précédent
            const allDates = filteredRows.map(r => r.datePointage);
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx > 0) {
                for (let d = currIdx - 1; d >= 0; d--) {
                    const prevDate = allDates[d];
                    const dayRefs = inputRefs.current[prevDate];
                    if (!dayRefs) continue;
                    for (let f = 3; f >= 0; f--) {
                        const targetEl = dayRefs[f];
                        if (targetEl && !targetEl.disabled && document.body.contains(targetEl)) {
                            e.preventDefault();
                            targetEl.focus();
                            targetEl.select?.();
                            return;
                        }
                    }
                }
            }
            return;
        }

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            const allDates = filteredRows.map(r => r.datePointage);
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx !== -1 && currIdx < allDates.length - 1) {
                for (let d = currIdx + 1; d < allDates.length; d++) {
                    const nextDate = allDates[d];
                    const dayRefs = inputRefs.current[nextDate];
                    if (!dayRefs) continue;
                    let targetEl = dayRefs[fieldIndex];
                    if (!targetEl || targetEl.disabled || !document.body.contains(targetEl)) {
                        targetEl = dayRefs.find(el => el && !el.disabled && document.body.contains(el));
                    }
                    if (targetEl) {
                        targetEl.focus();
                        targetEl.select?.();
                        return;
                    }
                }
            }
            return;
        }

        if (e.key === 'ArrowUp') {
            e.preventDefault();
            const allDates = filteredRows.map(r => r.datePointage);
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx > 0) {
                for (let d = currIdx - 1; d >= 0; d--) {
                    const prevDate = allDates[d];
                    const dayRefs = inputRefs.current[prevDate];
                    if (!dayRefs) continue;
                    let targetEl = dayRefs[fieldIndex];
                    if (!targetEl || targetEl.disabled || !document.body.contains(targetEl)) {
                        targetEl = dayRefs.find(el => el && !el.disabled && document.body.contains(el));
                    }
                    if (targetEl) {
                        targetEl.focus();
                        targetEl.select?.();
                        return;
                    }
                }
            }
            return;
        }
    }, [focusNext, filteredRows]);

    // Regroupement par semaine pour le rendu style PointageTab avec calcul de l'objectif hebdomadaire
    const groupedByWeeks = useMemo(() => {
        const weeks = [];
        let currentWeek = [];
        let currentWeekNum = null;

        filteredRows.forEach((row) => {
            const dateObj = new Date(`${row.datePointage}T00:00:00`);
            const dayOfWeek = dateObj.getDay() || 7;
            const monday = new Date(dateObj);
            monday.setDate(dateObj.getDate() - (dayOfWeek - 1));
            const weekKey = monday.toISOString().split('T')[0];

            if (currentWeekNum !== weekKey) {
                if (currentWeek.length > 0) {
                    weeks.push({ weekKey: currentWeekNum, rows: currentWeek });
                }
                currentWeek = [row];
                currentWeekNum = weekKey;
            } else {
                currentWeek.push(row);
            }
        });

        if (currentWeek.length > 0) {
            weeks.push({ weekKey: currentWeekNum, rows: currentWeek });
        }

        return weeks.map(w => {
            // Jours ouvrés prévus dans la semaine (hors weekend et férié chômé)
            const joursOuvresSemaine = w.rows.filter(r => !r.weekend && !r.ferie);
            const objectifHeuresSemaine = joursOuvresSemaine.length * 8.0;
            const totalFeriesSemaine = w.rows.filter(r => r.ferie).length;

            return {
                ...w,
                objectifHeuresSemaine,
                totalFeriesSemaine
            };
        });
    }, [filteredRows]);

    // Totaux KPIs de la période pour ce collaborateur
    const kpis = useMemo(() => {
        const joursOuvres = grilleRows.filter(r => !r.weekend && !r.ferie);
        const totalPrevus = joursOuvres.length;
        const totalFeries = grilleRows.filter(r => r.ferie).length;
        const totalRenseignes = joursOuvres.filter(r => (r.totalHeuresDecimal > 0 || r.conge || r.heureEntreeMatin)).length;
        const totalRetardMins = grilleRows.reduce((acc, r) => acc + (r.minutesRetard || 0), 0);
        const totalHeures = grilleRows.reduce((acc, r) => acc + (Number(r.totalHeuresDecimal) || 0), 0);

        return {
            totalPrevus,
            totalFeries,
            totalRenseignes,
            totalRetardMins,
            totalHeures
        };
    }, [grilleRows]);

    return (
        <div className="space-y-4 font-sans pb-16">
            {/* 1. Barre supérieure : Mode de Saisie & Sélecteur de Période & Site */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs">
                <div className="flex items-center gap-3 flex-wrap">
                    {/* Bascule Saisie Collective vs Saisie par Employé */}
                    <div className="inline-flex border border-[var(--color-border)] bg-[var(--color-bg-subtle)] p-0.5">
                        <button
                            type="button"
                            onClick={() => {
                                setSearchParams(prev => {
                                    const next = new URLSearchParams(prev);
                                    next.set('tab', 'saisie');
                                    next.delete('mode');
                                    return next;
                                });
                            }}
                            className="px-3 py-1.5 text-xs font-medium text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] transition-colors cursor-pointer flex items-center gap-1.5"
                        >
                            <Users size={13} />
                            <span>Saisie collective (par date)</span>
                        </button>
                        <button
                            type="button"
                            className="px-3 py-1.5 text-xs font-bold bg-[var(--color-brand-600)] text-[var(--color-on-color)] shadow-xs flex items-center gap-1.5"
                        >
                            <User size={13} />
                            <span>Saisie par collaborateur (période)</span>
                        </button>
                    </div>

                    <span className="h-5 w-px bg-[var(--color-border-subtle)] hidden sm:block" />

                    {/* Sélecteur de Période */}
                    <PeriodeNavigator
                        periodesList={periodesList}
                        selectedPeriodeId={selectedPeriodeId}
                        onSelectPeriode={(pId) => {
                            setSelectedPeriodeId(pId);
                            setSearchParams(prev => {
                                const next = new URLSearchParams(prev);
                                next.set('periode', String(pId));
                                return next;
                            });
                        }}
                    />
                </div>

                {/* Filtre de Site */}
                <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] shrink-0">Site :</span>
                    <SearchSelect
                        options={sitesList.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || ''} — ${s.libelle}` }))}
                        value={selectedSiteId ? String(selectedSiteId) : ''}
                        onChange={(val) => {
                            const newSid = val ? Number(val) : null;
                            setSelectedSiteId(newSid);
                            setSearchParams(prev => {
                                const next = new URLSearchParams(prev);
                                if (newSid) next.set('site', String(newSid));
                                else next.delete('site');
                                return next;
                            });
                        }}
                        placeholder="Tous les sites"
                        size="sm"
                        className="min-w-[190px] text-xs"
                    />
                </div>
                </div>

                {sitesLoadError && <Alert type="error" title="Sites indisponibles" message={sitesLoadError} />}

                {/* CAS 1 : AUCUN COLLABORATEUR SÉLECTIONNÉ (Écran de recherche propre et direct) */}
            {!selectedEmployeId ? (
                <Card padding={false} className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-xs overflow-hidden">
                    <div className="p-6 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div>
                            <h2 className="text-base font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <User size={18} className="text-[var(--color-brand-600)]" />
                                <span>Sélectionnez un collaborateur</span>
                            </h2>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                Choisissez un salarié pour ouvrir sa feuille de pointage mensuelle détaillée, ajuster ses heures ou déclarer des exceptions.
                            </p>
                        </div>

                        {/* Recherche rapide */}
                        <div className="relative min-w-[280px]">
                            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                            <input
                                type="text"
                                value={employeeSearchFilter}
                                onChange={(e) => setEmployeeSearchFilter(e.target.value)}
                                placeholder="Rechercher par nom, prénom ou matricule…"
                                aria-label="Rechercher un collaborateur par nom, prénom ou matricule"
                                className="w-full pl-9 pr-3 py-1.5 text-xs bg-[var(--color-surface)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)]"
                            />
                        </div>
                    </div>

                    {/* Liste des salariés de la période / site */}
                    <div className="p-4">
                        {employeeListError && <Alert type="error" title="Liste indisponible" message={employeeListError} actionLabel="Réessayer" onAction={() => setEmployeeListReload(value => value + 1)} />}
                        {employeeListLoading && employesList.length === 0 ? (
                            <div className="text-center py-12 text-xs text-[var(--color-fg-muted)]" role="status">
                                Chargement des collaborateurs de la période…
                            </div>
                        ) : employesList.length === 0 ? (
                            <div className="text-center py-12 text-xs text-[var(--color-fg-muted)]">
                                {employeeListError
                                    ? 'La liste reste indisponible. Réessayez après avoir vérifié votre connexion.'
                                    : employeeSearchQuery && employeeSearchQuery.length < 2
                                    ? 'Saisissez au moins deux caractères pour lancer la recherche.'
                                    : employeeSearchQuery
                                    ? `Aucun collaborateur ne correspond à « ${employeeSearchQuery} » pour cette période et ce site.`
                                    : 'Aucun collaborateur trouvé pour cette période et ce site.'}
                            </div>
                        ) : (
                            <>
                                <div className="mb-3 flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                                    <span>Affichage de {employesList.length} sur {employeeListTotal} collaborateur{employeeListTotal === 1 ? '' : 's'}</span>
                                    {employeeListLoading && <span role="status">Chargement…</span>}
                                </div>
                                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                                {employesList.map((emp) => {
                                    const isEmpAutonome = Boolean(emp.estAutonome || emp.isCadre || emp.categorie === 'CADRE');
                                    return (
                                        <div
                                            key={emp.idEmploye}
                                            onClick={() => {
                                                setSelectedEmployeId(emp.idEmploye);
                                                setSearchParams(prev => {
                                                    const next = new URLSearchParams(prev);
                                                    next.set('employe', String(emp.idEmploye));
                                                    return next;
                                                });
                                            }}
                                            className="p-3 border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)]/60 transition-all cursor-pointer bg-[var(--color-surface)] flex items-center justify-between group shadow-2xs"
                                        >
                                            <div className="flex items-center gap-3 min-w-0">
                                                <Avatar
                                                    src={emp.photoUrl}
                                                    name={`${emp.nom} ${emp.prenom || ''}`}
                                                    size="md"
                                                    shape="square"
                                                />
                                                <div className="min-w-0">
                                                    <div className="font-bold text-xs text-[var(--color-fg)] group-hover:text-[var(--color-brand-600)] transition-colors truncate">
                                                        {emp.nom} {emp.prenom}
                                                    </div>
                                                    <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-fg-muted)] font-mono mt-0.5">
                                                        <span>{emp.matricule}</span>
                                                        <span>·</span>
                                                        <span className="truncate">{emp.siteNom || emp.siteCode || 'Site'}</span>
                                                    </div>
                                                    <div className="mt-1">
                                                        {isEmpAutonome ? (
                                                            <Badge variant="purple" size="sm" className="text-[10px]">
                                                                Cadre autonome (8h présumé)
                                                            </Badge>
                                                        ) : (
                                                            <Badge variant="info" size="sm" className="text-[10px]">
                                                                Standard 40h
                                                            </Badge>
                                                        )}
                                                    </div>
                                                </div>
                                            </div>

                                            <ChevronRight size={16} className="text-[var(--color-fg-muted)] group-hover:text-[var(--color-brand-600)] shrink-0 transition-transform group-hover:translate-x-0.5" />
                                        </div>
                                    );
                                })}
                                </div>
                                {employeeListPage < employeeListTotalPages && (
                                    <div className="flex justify-center pt-4">
                                        <button
                                            type="button"
                                            onClick={loadMoreEmployees}
                                            disabled={employeeListLoading}
                                            className="px-4 py-2 text-xs font-semibold border border-[var(--color-border)] text-[var(--color-brand-700)] hover:bg-[var(--color-brand-500)]/10 disabled:opacity-50 disabled:cursor-wait"
                                        >
                                            {employeeListLoading ? 'Chargement…' : `Charger la suite (${Math.max(0, employeeListTotal - employesList.length)} restant${employeeListTotal - employesList.length === 1 ? '' : 's'})`}
                                        </button>
                                    </div>
                                )}
                            </>
                        )}
                    </div>
                </Card>
            ) : (
                /* CAS 2 : COLLABORATEUR SÉLECTIONNÉ (Feuille de pointage complète) */
                <>
                    {/* 2. Bandeau Collaborateur & Navigation feuille-à-feuille */}
                    <Card padding={false} className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] overflow-hidden shadow-xs">
                        <div className="p-4 flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40">
                            {/* Profil Collaborateur */}
                            <div className="flex items-center gap-3.5">
                                <Avatar
                                    src={ficheData?.photoUrl}
                                    name={`${ficheData?.prenom || ''} ${ficheData?.nom || ''}`}
                                    size="lg"
                                    shape="square"
                                />
                                <div>
                                    <div className="flex items-center gap-2 flex-wrap">
                                        <h1 className="text-base font-bold text-[var(--color-fg)]">
                                            {ficheData?.nomComplet || `${ficheData?.nom || ''} ${ficheData?.prenom || ''}` || 'Chargement…'}
                                        </h1>
                                        <span className="text-xs font-mono px-1.5 py-0.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                                            {ficheData?.matricule || '—'}
                                        </span>
                                        {isAutonome ? (
                                            <Badge variant="purple" size="sm" className="font-semibold">
                                                Cadre Autonome · Dispense de pointage
                                            </Badge>
                                        ) : (
                                            <Badge variant="info" size="sm">
                                                Standard 40h/sem.
                                            </Badge>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)] mt-1 flex-wrap">
                                        <span>{ficheData?.fonction || 'Fonction non renseignée'}</span>
                                        <span>·</span>
                                        <span>{ficheData?.siteNom || 'Site principal'}</span>
                                        {isAutonome && (
                                            <>
                                                <span>·</span>
                                                <span className="text-[var(--color-purple-600)] font-medium">Présomption 8h/j ouvré</span>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>

                            {/* Outils Navigation & Changement de collaborateur */}
                            <div className="flex items-center gap-2.5 flex-wrap self-end md:self-auto">
                                {/* Bouton pour revenir à la liste générale */}
                                <button
                                    type="button"
                                    onClick={() => {
                                        setSelectedEmployeId(null);
                                        setSearchParams(prev => {
                                            const next = new URLSearchParams(prev);
                                            next.delete('employe');
                                            return next;
                                        });
                                    }}
                                    className="px-2.5 py-1.5 text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] border border-[var(--color-border-subtle)] bg-[var(--color-surface)] hover:bg-[var(--color-surface-hover)] transition-colors cursor-pointer"
                                    title="Changer de collaborateur"
                                >
                                    Liste
                                </button>

                                {/* Navigation Précédent / Suivant */}
                                <div className="flex items-center border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xs">
                                    <button
                                        type="button"
                                        onClick={() => handleNavigateEmploye(-1)}
                                        disabled={currentEmpIndex <= 0}
                                        title="Collaborateur précédent (Alt+←)"
                                        className="px-2.5 py-1.5 text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
                                    >
                                        <ChevronLeft size={16} />
                                    </button>
                                    <div className="px-3 py-1 text-xs font-medium border-x border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                                        {employesList.length > 0 ? (
                                            <span>
                                                <strong className="text-[var(--color-fg)]">{currentEmpIndex + 1}</strong> sur {employesList.length}
                                            </span>
                                        ) : (
                                            <span>1 sur 1</span>
                                        )}
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => handleNavigateEmploye(1)}
                                        disabled={currentEmpIndex < 0 || currentEmpIndex >= employesList.length - 1}
                                        title="Collaborateur suivant (Alt+→)"
                                        className="px-2.5 py-1.5 text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
                                    >
                                        <ChevronRight size={16} />
                                    </button>
                                </div>

                                {/* Sélecteur direct de collaborateur */}
                                <SearchSelect
                                    options={employesList.map(e => ({
                                        value: e.idEmploye,
                                        label: `${e.nom} ${e.prenom || ''} (${e.matricule})`
                                    }))}
                                    value={selectedEmployeId ? String(selectedEmployeId) : ''}
                                    onChange={(val) => {
                                        if (val) {
                                            setSelectedEmployeId(Number(val));
                                            setSearchParams(prev => {
                                                const next = new URLSearchParams(prev);
                                                next.set('employe', val);
                                                return next;
                                            });
                                        }
                                    }}
                                    placeholder="Rechercher un employé…"
                                    size="sm"
                                    className="min-w-[220px] text-xs"
                                />
                            </div>
                        </div>

                        {/* 3. Bandeau KPIs Synthèse du Mois */}
                        <div className="grid grid-cols-2 md:grid-cols-4 divide-x divide-y md:divide-y-0 divide-[var(--color-border-subtle)] border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                            <div className="p-3 text-center">
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-medium">Jours ouvrés prévus</span>
                                <p className="text-lg font-bold text-[var(--color-fg)] mt-0.5">
                                    {kpis.totalPrevus} j
                                    {kpis.totalFeries > 0 && (
                                        <span className="text-[10px] font-normal text-[var(--color-purple-600)] ml-1">
                                            (+{kpis.totalFeries} férié)
                                        </span>
                                    )}
                                </p>
                            </div>
                            <div className="p-3 text-center">
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-medium">Jours renseignés</span>
                                <p className={`text-lg font-bold mt-0.5 ${kpis.totalRenseignes >= kpis.totalPrevus ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                    {kpis.totalRenseignes} / {kpis.totalPrevus}
                                </p>
                            </div>
                            <div className="p-3 text-center">
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-medium">Retard cumulé</span>
                                <p className={`text-lg font-bold mt-0.5 ${kpis.totalRetardMins > 0 ? 'text-[var(--color-warning-600)]' : 'text-[var(--color-fg)]'}`}>
                                    {isAutonome ? '—' : `${kpis.totalRetardMins} min`}
                                </p>
                            </div>
                            <div className="p-3 text-center">
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-medium">Heures travaillées</span>
                                <p className="text-lg font-bold text-[var(--color-brand-600)] mt-0.5">
                                    {formatHeuresDecimal(kpis.totalHeures)}
                                </p>
                            </div>
                        </div>

                        {/* 4. Barre d'outils Assistants & Filtres (Style Grille de Saisie) */}
                        <div className="p-2.5 flex flex-wrap items-center justify-between gap-2.5 bg-[var(--color-surface)]">
                            {/* Filtres d'état */}
                            <div className="flex items-center gap-1">
                                {[
                                    { key: 'ALL', label: 'Tous', count: grilleRows.length },
                                    { key: 'MISSING', label: 'À saisir', count: grilleRows.filter(r => !r.weekend && !r.ferie && !r.conge && (!r.heureEntreeMatin || r.codeStatut === 'NON_SAISI')).length },
                                    { key: 'ANOMALY', label: 'Anomalies', count: grilleRows.filter(r => r.minutesRetard > 0 || r.codeStatut === 'ABSENCE_NON_JUSTIFIEE').length },
                                ].map(({ key, label, count }) => (
                                    <button
                                        key={key}
                                        type="button"
                                        onClick={() => setFilterJour(key)}
                                        className={`px-2.5 py-1 text-xs font-semibold border transition-colors cursor-pointer flex items-center gap-1.5 ${filterJour === key
                                            ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-600)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                            : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                            }`}
                                    >
                                        <span>{label}</span>
                                        <span className="font-mono text-[10px] opacity-75">{count}</span>
                                    </button>
                                ))}
                            </div>

                            {/* Outils Assistants & Enregistrement */}
                            <div className="flex items-center gap-2">
                                <input ref={importFileRef} type="file" accept=".xlsx" className="hidden" onChange={handleImportFile} />
                                <button type="button" onClick={handleDownloadImportTemplate} disabled={!canEditPeriod || importLoading}
                                    title="Télécharger un tableau Excel pour ce collaborateur et cette période"
                                    className="flex items-center gap-1.5 px-3 h-8 text-xs font-semibold border border-[var(--color-border-subtle)] text-[var(--color-fg)] disabled:opacity-50">
                                    <Download size={13} /> Modèle Excel
                                </button>
                                <button type="button" onClick={() => importFileRef.current?.click()} disabled={!canEditPeriod || importLoading || dirtyCount > 0}
                                    title="Prévisualiser un tableau Excel avant de charger ses lignes dans la grille"
                                    className="flex items-center gap-1.5 px-3 h-8 text-xs font-semibold border border-[var(--color-brand-600)] text-[var(--color-brand-700)] disabled:opacity-50">
                                    <Upload size={13} /> {importLoading ? 'Lecture…' : 'Importer'}
                                </button>
                                <button
                                    type="button"
                                    onClick={handleApplyStandardHours}
                                    disabled={!canEditPeriod}
                                    title="Remplir les horaires standard du site sur les jours ouvrés vides"
                                    className="flex items-center gap-1.5 px-3 h-8 text-xs font-semibold border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)] transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <ClockCheck size={13} className="text-[var(--color-success-600)] shrink-0" />
                                    <span>Horaire standard</span>
                                </button>

                                <button
                                    type="button"
                                    onClick={handleDeducePauses}
                                    disabled={!canEditPeriod}
                                    title="Insérer automatiquement la coupure déjeuner sur les journées ayant entrée et sortie sans pause"
                                    className="flex items-center gap-1.5 px-3 h-8 text-xs font-semibold border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)] transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <Coffee size={13} className="text-[var(--color-warning-600)] shrink-0" />
                                    <span>Déduire pauses</span>
                                </button>

                                <span className="h-5 w-px bg-[var(--color-border-subtle)]" />

                                {/* Bouton Enregistrer avec dirtyCount */}
                                <button
                                    type="button"
                                    disabled={dirtyCount === 0 || saving || !canEditPeriod}
                                    onClick={handleSaveBatch}
                                    className={`flex items-center gap-1.5 px-4 h-8 text-xs font-bold transition-all cursor-pointer border shadow-xs ${dirtyCount > 0
                                        ? 'bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] text-[var(--color-on-color)] border-[var(--color-brand-600)]'
                                        : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] opacity-50 cursor-not-allowed'
                                        }`}
                                >
                                    {saving ? (
                                        <span className="animate-spin inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full" />
                                    ) : (
                                        <Save size={13} />
                                    )}
                                    <span>{saving ? 'Enregistrement…' : dirtyCount > 0 ? 'Enregistrer' : 'À jour'}</span>
                                    {dirtyCount > 0 && (
                                        <span className="bg-[var(--color-surface)]/25 text-[var(--color-on-color)] text-[10px] font-mono px-1.5 py-0.2 rounded-none">
                                            {dirtyCount}
                                        </span>
                                    )}
                                    <span className="hidden xl:inline text-[10px] opacity-75 font-normal">(Ctrl+S)</span>
                                </button>
                            </div>
                        </div>
                    </Card>

                    {Object.keys(importSignals).length > 0 && (
                        <section className="border border-[var(--color-warning-600)] bg-[var(--color-surface)] p-3 space-y-2" aria-label="Signalements importés à régulariser">
                            <p className="text-xs font-bold text-[var(--color-warning-700)]">Signalements importés à régulariser</p>
                            <div className="flex flex-wrap gap-2">
                                {Object.entries(importSignals).map(([date, signal]) => {
                                    const row = grilleRows.find(item => item.datePointage === date);
                                    return <button key={date} type="button" disabled={!row?.canCorrect || !canEditPeriod || dirtyCount > 0}
                                        onClick={() => row && openRegularisation(row)}
                                        className="px-2.5 py-1 text-xs border border-[var(--color-warning-600)] text-[var(--color-fg)] disabled:opacity-50">
                                        {date} · {signal.code === 'C' ? 'Congé signalé' : 'Absence signalée'} · Régulariser
                                    </button>;
                                })}
                            </div>
                            {dirtyCount > 0 && <p className="text-xs text-[var(--color-fg-muted)]">Enregistrez les heures et indemnités importées avant de régulariser.</p>}
                        </section>
                    )}

                    {importPreview && (
                        <section role="dialog" aria-label="Aperçu de l'import de pointage" className="border border-[var(--color-brand-600)] bg-[var(--color-surface)] p-4 space-y-3">
                            <div className="flex items-start justify-between gap-3">
                                <div>
                                    <h3 className="font-bold text-[var(--color-fg)]">Aperçu de l’import</h3>
                                    <p className="text-xs text-[var(--color-fg-muted)]">Choisissez les journées à charger dans la grille. Aucune donnée n’est enregistrée maintenant.</p>
                                </div>
                                <button type="button" onClick={() => setImportPreview(null)} className="text-xs text-[var(--color-fg-muted)]">Fermer</button>
                            </div>
                            <div className="max-h-80 overflow-auto border border-[var(--color-border-subtle)]">
                                <table className="w-full text-xs">
                                    <thead className="sticky top-0 bg-[var(--color-bg-subtle)] text-left">
                                        <tr><th className="p-2">Choix</th><th className="p-2">Date</th><th className="p-2">Fichier importé</th><th className="p-2">Déjà enregistré</th><th className="p-2">Contrôle</th></tr>
                                    </thead>
                                    <tbody>
                                        {(importPreview.lines || []).map(line => {
                                            const p = line.proposed;
                                            const e = line.existing;
                                            const formatHours = (obj, imported) => imported
                                                ? [obj?.heureEntreeMatin, obj?.heureSortieMatin, obj?.heureEntreeAm, obj?.heureSortieAm].map(x => x || '—').join(' · ')
                                                : [obj?.entree, obj?.sortiePause, obj?.retourPause, obj?.sortie].map(x => x || '—').join(' · ');
                                            return (
                                                <tr key={line.ligne} className="border-t border-[var(--color-border-subtle)] align-top">
                                                    <td className="p-2"><input type="checkbox" checked={Boolean(importSelections[line.ligne])} disabled={!['IMPORT', 'SIGNAL'].includes(line.decision) || !grilleRows.find(r => r.datePointage === line.date)?.canCorrect || Boolean(e?.congeApprouve && line.eventCode)}
                                                        onChange={event => setImportSelections(prev => ({ ...prev, [line.ligne]: event.target.checked }))} /></td>
                                                    <td className="p-2 whitespace-nowrap font-semibold">{line.date}{line.eventCode && <span className="ml-1 px-1 border border-[var(--color-warning-600)]">{line.eventCode}</span>}</td>
                                                    <td className="p-2 font-mono">
                                                        {p && line.decision === 'IMPORT' ? formatHours(p, true) : 'Aucune heure'}
                                                        {p && line.decision === 'IMPORT' && p.totalHeures != null && <div className="mt-1">Calcul : {formatHeuresMinutes(p.totalHeures)} · {p.libelleStatut || p.codeStatut}</div>}
                                                        {p && ['IMPORT', 'SIGNAL'].includes(line.decision) && (
                                                            <div className="mt-1 text-[var(--color-fg-muted)]">
                                                                {(line.allowances || []).filter(ind => p.dynamicIndemnites?.[String(ind.idIndemnite)]?.accordee)
                                                                    .map(ind => {
                                                                        const selected = p.dynamicIndemnites[String(ind.idIndemnite)];
                                                                        return `${ind.code} ${selected.fractionJour ?? 1}`;
                                                                    }).join(' · ') || 'Aucune indemnité'}
                                                            </div>
                                                        )}
                                                    </td>
                                                    <td className="p-2 font-mono">
                                                        {line.conflict ? formatHours(e, false) : e?.statut || '—'}
                                                        {line.conflict && <div className="mt-1 text-[var(--color-fg-muted)]">{Object.entries(e?.indemnites || {}).filter(([, item]) => item?.accordee).map(([code, item]) => `${code} ${item.quantite ?? 1}`).join(' · ') || 'Aucune indemnité'}</div>}
                                                    </td>
                                                    <td className="p-2">
                                                        {line.errors?.map((message, index) => <div key={index} className="text-[var(--color-danger-600)]">{message}</div>)}
                                                        {line.warnings?.map((message, index) => <div key={index} className="text-[var(--color-warning-700)]">{message}</div>)}
                                                        {['IMPORT', 'SIGNAL'].includes(line.decision) && !line.conflict && !line.errors?.length && <span className="text-[var(--color-success-600)]">{line.decision === 'SIGNAL' ? 'À régulariser' : 'Prête'}</span>}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                            <div className="flex justify-end gap-2">
                                <button type="button" onClick={() => setImportPreview(null)} className="px-3 py-1.5 text-xs border border-[var(--color-border)]">Annuler</button>
                                <button type="button" onClick={handleApplyImport} disabled={!Object.values(importSelections).some(Boolean)}
                                    className="px-3 py-1.5 text-xs font-bold bg-[var(--color-brand-600)] text-[var(--color-on-color)] disabled:opacity-50">Charger les journées sélectionnées</button>
                            </div>
                        </section>
                    )}

                    {/* Bandeau d'assistance & Raccourcis Clavier */}
                    <div className="flex items-center justify-between text-[11px] text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] px-3 py-1.5 border border-[var(--color-border-subtle)]">
                        <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-semibold text-[var(--color-fg)]">Aide à la saisie :</span>
                            <span>Tapez 4 chiffres (ex: <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">0800</kbd> → 08:00) avec saut automatique</span>
                            <span>·</span>
                            <span>Flèches <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">↑</kbd> <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">↓</kbd> <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">←</kbd> <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">→</kbd> et <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">Entrée</kbd> pour naviguer</span>
                        </div>
                        <div className="hidden lg:flex items-center gap-2 shrink-0">
                            <kbd className="px-1.5 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)] font-bold text-[var(--color-brand-600)]">Ctrl+S</kbd>
                            <span>enregistrer</span>
                        </div>
                    </div>

                    {/* 5. Tableau Journalier — Structure et Rendu style PointageTab */}
                    {loading ? (
                        <SkeletonTable rows={10} columns={10} />
                    ) : error ? (
                        <Alert type="error" message={error} />
                    ) : filteredRows.length === 0 ? (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8 text-center text-xs text-[var(--color-fg-muted)]">
                            Aucune journée ne correspond au filtre sélectionné pour cette période.
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {groupedByWeeks.map((week, wIdx) => {
                                const weekTotalHeures = week.rows.reduce((acc, r) => acc + (Number(r.totalHeuresDecimal) || 0), 0);
                                const weekTotalRetard = week.rows.reduce((acc, r) => acc + (r.minutesRetard || 0), 0);

                                return (
                                    <div key={week.weekKey || wIdx} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                                        {/* En-tête de la Semaine */}
                                        <div className="px-3.5 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex items-center justify-between text-xs font-bold text-[var(--color-fg)]">
                                            <div className="flex items-center gap-2">
                                                <Calendar size={13} className="text-[var(--color-brand-600)]" />
                                                <span>Semaine {wIdx + 1}</span>
                                                <span className="font-normal text-[var(--color-fg-muted)]">
                                                    ({week.rows[0]?.date} au {week.rows[week.rows.length - 1]?.date})
                                                </span>
                                            </div>
                                            <div className="flex items-center gap-3 text-[11px] font-mono">
                                                <span>
                                                    Total réalisé : <strong className="text-[var(--color-fg)]">{formatHeuresDecimal(weekTotalHeures)}</strong>
                                                    <span className="text-[var(--color-fg-muted)] font-normal ml-1">
                                                        / {formatHeuresDecimal(week.objectifHeuresSemaine)} prévu
                                                    </span>
                                                    {week.totalFeriesSemaine > 0 && (
                                                        <span className="text-[var(--color-purple-600)] font-sans font-medium text-[10px] ml-1.5">
                                                            ({week.totalFeriesSemaine} férié chômé payé)
                                                        </span>
                                                    )}
                                                </span>
                                                {weekTotalRetard > 0 && !isAutonome && (
                                                    <span className="text-[var(--color-warning-600)] font-semibold">
                                                        Retard : {weekTotalRetard} min
                                                    </span>
                                                )}
                                                {canEditPeriod && (
                                                    <button
                                                        type="button"
                                                        onClick={() => handleApplyWeekStandard(week.rows)}
                                                        title="Appliquer l'horaire standard aux jours ouvrés de cette semaine"
                                                        className="font-sans font-semibold text-[10px] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] bg-[var(--color-brand-500)]/10 hover:bg-[var(--color-brand-500)]/20 px-2 py-0.5 border border-[var(--color-brand-500)]/30 transition-colors flex items-center gap-1 cursor-pointer ml-1"
                                                    >
                                                        <ClockCheck size={11} className="shrink-0" />
                                                        <span>Remplir semaine</span>
                                                    </button>
                                                )}
                                            </div>
                                        </div>

                                        <div className="overflow-x-auto">
                                            <Table bordered={false} density="compact" className="w-full text-left text-xs">
                                                <Thead>
                                                    <Tr className="border-b border-[var(--color-border-subtle)] text-[10px] uppercase font-bold text-[var(--color-fg-muted)] bg-[var(--color-surface)]">
                                                        <Th className="w-20 px-3 py-2">Date</Th>
                                                        <Th className="w-24 px-2 py-2">Jour</Th>
                                                        <Th className="w-24 px-2 py-2 text-center">Entrée Matin</Th>
                                                        <Th className="w-24 px-2 py-2 text-center">Pause / Sortie</Th>
                                                        <Th className="w-24 px-2 py-2 text-center">Retour AM</Th>
                                                        <Th className="w-24 px-2 py-2 text-center">Sortie Fin</Th>
                                                        <Th className="w-16 px-2 py-2 text-center">Retard</Th>
                                                        <Th className="w-24 px-2 py-2 text-center">Heures</Th>
                                                        <Th className="w-32 px-2 py-2 text-center">Statut</Th>
                                                        {displayedIndemnites.map((ind) => (
                                                            <Th key={ind.idIndemnite} className="px-2 py-2 text-center border-l border-[var(--color-border-subtle)] min-w-[110px]" title={ind.libelle}>
                                                                <div className="flex flex-col items-center">
                                                                    <span>{ind.libelle || ind.code}</span>
                                                                    <span className="text-[9px] font-normal text-[var(--color-fg-muted)] normal-case">
                                                                        {ind.sousTitre || (ind.estProratise ? `Prorata ${formatMonnaie(ind.montantJour)}/j` : `${formatMonnaie(ind.montantJour)}/j`)}
                                                                    </span>
                                                                </div>
                                                            </Th>
                                                        ))}
                                                        <Th className="w-20 px-2 py-2 text-center">Actions</Th>
                                                    </Tr>
                                                </Thead>
                                                <Tbody>
                                                    {week.rows.map((row) => {
                                                        const isWeekend = row.weekend;
                                                        const isFerie = row.ferie;
                                                        const isConge = row.conge;
                                                        const hasPunches = Boolean(row.heureEntreeMatin || row.heureSortieAm);

                                                        const initialRow = initialRows.find(i => i.datePointage === row.datePointage);
                                                        const isModified = isDayRowModified(row, initialRow);
                                                        const isNonSaisi = !isWeekend && !isFerie && !isConge && !row.heureEntreeMatin && !row.heureSortieAm;

                                                        let rowBorderCue = 'border-l-2 border-l-transparent';
                                                        if (isModified) {
                                                            rowBorderCue = 'border-l-2 border-l-[var(--color-brand-500)] bg-[var(--color-brand-500)]/[0.03]';
                                                        } else if (isNonSaisi) {
                                                            rowBorderCue = 'border-l-2 border-l-[var(--color-warning-500)]';
                                                        } else if (row.totalHeuresDecimal > 0) {
                                                            rowBorderCue = 'border-l-2 border-l-[var(--color-success-500)]';
                                                        }

                                                        const canEditRow = canEditPeriod && row.canCorrect !== false;
                                                        const chomeColSpan = 7 + displayedIndemnites.length;

                                                        return (
                                                            <Tr
                                                                key={row.datePointage}
                                                                id={`pointage-day-${row.datePointage}`}
                                                                className={`border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors ${rowBorderCue} ${
                                                                    focusedDate === row.datePointage ? 'outline outline-2 outline-[var(--color-brand-500)] outline-offset-[-2px] ' : ''
                                                                }${
                                                                    isWeekend
                                                                        ? 'bg-[var(--color-bg-subtle)]/40 text-[var(--color-fg-muted)]'
                                                                        : isFerie
                                                                            ? 'bg-[var(--color-purple-500)]/[0.04]'
                                                                            : isConge
                                                                                ? 'bg-[var(--color-sky-500)]/[0.04]'
                                                                                : ''
                                                                }`}
                                                            >
                                                                {/* 1. Date */}
                                                                <Td className="font-mono font-bold text-xs px-3 py-1.5 whitespace-nowrap">
                                                                    {row.date}
                                                                </Td>

                                                                {/* 2. Jour avec bouton continu / pause inline */}
                                                                <Td className="text-xs text-[var(--color-fg-muted)] px-2 py-1.5 whitespace-nowrap">
                                                                    <div className="flex items-center gap-1.5">
                                                                        <span>{row.jour}</span>
                                                                        {canEditRow && !isConge && (
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => toggleDayContinu(row.datePointage)}
                                                                                className={`text-[9px] px-1 py-0.2 font-sans font-semibold rounded border transition-colors cursor-pointer shrink-0 ${
                                                                                    row.horaireContinu
                                                                                        ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border-[var(--color-brand-500)]/40 hover:bg-[var(--color-brand-500)]/20'
                                                                                        : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                                                                }`}
                                                                                title={row.horaireContinu ? "Journée continue active (sans pause déjeuner). Cliquer pour rétablir la pause." : "Activer la journée continue (sans pause déjeuner 1h)"}
                                                                            >
                                                                                {row.horaireContinu ? (
                                                                                    <span className="inline-flex items-center gap-0.5">
                                                                                        <FastForward size={8} className="shrink-0" /> Continu
                                                                                    </span>
                                                                                ) : 'Pause'}
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                </Td>

                                                                {/* Ligne fusionnée pour Repos / Férié chômé / Congé sans badge de travail */}
                                                                {isWeekend && !hasPunches ? (
                                                                    <Td colSpan={chomeColSpan} className="px-2 py-1.5 text-center text-[11px] text-[var(--color-fg-muted)]">
                                                                        <div className="flex items-center justify-center gap-2">
                                                                            <span>Repos hebdomadaire</span>
                                                                            {canEditRow && (
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() => handleApplyDayStandard(row.datePointage)}
                                                                                    className="text-[10px] text-[var(--color-brand-600)] hover:underline cursor-pointer"
                                                                                >
                                                                                    + Déclarer travaillé
                                                                                </button>
                                                                            )}
                                                                        </div>
                                                                    </Td>
                                                                ) : isFerie && !hasPunches ? (
                                                                    <Td colSpan={chomeColSpan} className="px-2 py-1.5 text-center text-[11px] text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)] font-medium">
                                                                        <div className="flex items-center justify-center gap-2">
                                                                            <Badge variant="purple" size="sm">Férié chômé payé</Badge>
                                                                            <span className="text-[10px] text-[var(--color-purple-600)] dark:text-[var(--color-purple-400)]">
                                                                                Jour chômé légal (-8h d'obligation de présence)
                                                                            </span>
                                                                            {canEditRow && (
                                                                                <button
                                                                                    type="button"
                                                                                    onClick={() => handleApplyDayStandard(row.datePointage)}
                                                                                    className="text-[10px] text-[var(--color-brand-600)] hover:underline cursor-pointer ml-1"
                                                                                >
                                                                                    + Travaillé (+100%)
                                                                                </button>
                                                                            )}
                                                                        </div>
                                                                    </Td>
                                                                ) : isConge && !hasPunches ? (
                                                                    <Td colSpan={chomeColSpan} className="px-2 py-1.5 text-center text-[11px] text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)] font-medium">
                                                                        <div className="flex items-center justify-center gap-2">
                                                                            <Badge variant="info" size="sm">Congé payé validé</Badge>
                                                                            <span className="text-[10px] text-[var(--color-sky-600)] dark:text-[var(--color-sky-400)]">
                                                                                Piloté par le module Congés
                                                                            </span>
                                                                        </div>
                                                                    </Td>
                                                                ) : (
                                                                    <>
                                                                        {/* 3. Entrée Matin */}
                                                                        <Td className="px-1.5 py-1 text-center">
                                                                            <TimeInput
                                                                                inputRef={(el) => registerInputRef(row.datePointage, 0, el)}
                                                                                value={row.heureEntreeMatin}
                                                                                onChange={(val) => updateDayHours(row.datePointage, 'heureEntreeMatin', val)}
                                                                                onKeyDown={(e) => handleKeyDown(e, row.datePointage, 0)}
                                                                                onComplete={() => focusNext(row.datePointage, 0)}
                                                                                disabled={!canEditRow}
                                                                                placeholder="--:--"
                                                                                className="h-7 text-xs text-center"
                                                                            />
                                                                        </Td>

                                                                        {/* 4 & 5. Pause ou Continu */}
                                                                        {row.horaireContinu ? (
                                                                            <Td colSpan={2} className="px-1.5 py-1 text-center bg-[var(--color-brand-500)]/5 dark:bg-[var(--color-brand-500)]/10">
                                                                                <div className="flex items-center justify-center gap-1.5">
                                                                                    <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">
                                                                                        <FastForward size={10} className="shrink-0 text-[var(--color-brand-600)]" />
                                                                                        Journée continue (sans pause)
                                                                                    </span>
                                                                                    <button
                                                                                        type="button"
                                                                                        onClick={() => toggleDayContinu(row.datePointage)}
                                                                                        className="text-[10px] text-[var(--color-fg-muted)] hover:text-[var(--color-brand-600)] underline cursor-pointer ml-1"
                                                                                    >
                                                                                        Modifier
                                                                                    </button>
                                                                                </div>
                                                                            </Td>
                                                                        ) : (
                                                                            <>
                                                                                <Td className="px-1.5 py-1 text-center">
                                                                                    <TimeInput
                                                                                        inputRef={(el) => registerInputRef(row.datePointage, 1, el)}
                                                                                        value={row.heureSortieMatin}
                                                                                        onChange={(val) => updateDayHours(row.datePointage, 'heureSortieMatin', val)}
                                                                                        onKeyDown={(e) => handleKeyDown(e, row.datePointage, 1)}
                                                                                        onComplete={() => focusNext(row.datePointage, 1)}
                                                                                        disabled={!canEditRow}
                                                                                        placeholder="--:--"
                                                                                        className="h-7 text-xs text-center"
                                                                                    />
                                                                                </Td>
                                                                                <Td className="px-1.5 py-1 text-center">
                                                                                    <TimeInput
                                                                                        inputRef={(el) => registerInputRef(row.datePointage, 2, el)}
                                                                                        value={row.heureEntreeAm}
                                                                                        onChange={(val) => updateDayHours(row.datePointage, 'heureEntreeAm', val)}
                                                                                        onKeyDown={(e) => handleKeyDown(e, row.datePointage, 2)}
                                                                                        onComplete={() => focusNext(row.datePointage, 2)}
                                                                                        disabled={!canEditRow}
                                                                                        placeholder="--:--"
                                                                                        className="h-7 text-xs text-center"
                                                                                    />
                                                                                </Td>
                                                                            </>
                                                                        )}

                                                                        {/* 6. Sortie Fin */}
                                                                        <Td className="px-1.5 py-1 text-center">
                                                                            <TimeInput
                                                                                inputRef={(el) => registerInputRef(row.datePointage, 3, el)}
                                                                                value={row.heureSortieAm}
                                                                                onChange={(val) => updateDayHours(row.datePointage, 'heureSortieAm', val)}
                                                                                onKeyDown={(e) => handleKeyDown(e, row.datePointage, 3)}
                                                                                onComplete={() => focusNext(row.datePointage, 3)}
                                                                                disabled={!canEditRow}
                                                                                placeholder="--:--"
                                                                                className="h-7 text-xs text-center"
                                                                            />
                                                                        </Td>

                                                                        {/* 7. Retard */}
                                                                        <Td className="px-1 py-1 text-center font-mono text-xs">
                                                                            {isAutonome ? (
                                                                                <span className="text-[var(--color-fg-muted)] opacity-50">—</span>
                                                                            ) : row.minutesRetard > 0 ? (
                                                                                <span className="font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] bg-[var(--color-warning-50)] dark:bg-[var(--color-warning-950)] px-1.5 py-0.5 rounded-none border border-[var(--color-warning-200)]">
                                                                                    +{row.minutesRetard}m
                                                                                </span>
                                                                            ) : (
                                                                                <span className="text-[var(--color-fg-muted)] opacity-40">—</span>
                                                                            )}
                                                                        </Td>

                                                                        {/* 8. Heures Faites */}
                                                                        <Td className="px-2 py-1 text-center font-mono font-bold text-xs text-[var(--color-fg)]">
                                                                            <div>{row.heuresFaites}</div>
                                                                            {row.totalHeuresDecimal > 0 && (
                                                                                <div className="text-[10px] text-[var(--color-fg-muted)] font-normal">
                                                                                    ({formatHeuresDecimal(row.totalHeuresDecimal)})
                                                                                </div>
                                                                            )}
                                                                        </Td>

                                                                        {/* 9. Statut */}
                                                                        <Td className="px-2 py-1 text-center">
                                                                            <StatusPickerPopover
                                                                                row={row}
                                                                                currentStatus={row.codeStatut}
                                                                                onStatusChange={(newSt, motif) => updateDayStatut(row.datePointage, newSt, motif)}
                                                                                onOpenRegularisation={(r) => setRegularisationRow(r)}
                                                                                disabled={!canEditRow || Boolean(row.congeApprouve)}
                                                                            />
                                                                        </Td>
                                                                    </>
                                                                )}

                                                                {/* 10. Colonnes Dynamiques d'Indemnités du Site */}
                                                                {displayedIndemnites.map((ind) => {
                                                                    if (row._dateAllowances && !row._dateAllowances.some(item => item.idIndemnite === ind.idIndemnite)) {
                                                                        return <Td key={ind.idIndemnite} className="px-2 py-1 text-center border-l border-[var(--color-border-subtle)]">—</Td>;
                                                                    }
                                                                    const dayInd = row._dateAllowances?.find(item => item.idIndemnite === ind.idIndemnite) || ind;
                                                                    const dynObj = row.dynamicIndemnites?.[String(ind.idIndemnite)] || {};
                                                                    const isFractionnable = dayInd.estFractionnable !== undefined ? dayInd.estFractionnable : dayInd.fractionnable;

                                                                    if (isWeekend && !hasPunches) {
                                                                        return (
                                                                            <Td key={ind.idIndemnite} className="px-2 py-1 text-center border-l border-[var(--color-border-subtle)]">
                                                                                <span className="opacity-30">—</span>
                                                                            </Td>
                                                                        );
                                                                    }

                                                                    if (isFractionnable) {
                                                                        const matVal = Boolean(dynObj.matin);
                                                                        const amVal = Boolean(dynObj.am);

                                                                        return (
                                                                            <Td key={ind.idIndemnite} className="px-2 py-1 text-center border-l border-[var(--color-border-subtle)]">
                                                                                <div className="flex flex-col items-center justify-center gap-0.5">
                                                                                    {dynObj.motifSuggestion && (
                                                                                        <span className="text-[8px] font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]" title={dynObj.motifSuggestion}>
                                                                                            {dynObj.motifSuggestion}
                                                                                        </span>
                                                                                    )}
                                                                                    <div className="flex items-center justify-center gap-2">
                                                                                        <label className={`inline-flex items-center gap-0.5 text-[10px] font-semibold ${!canEditRow ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`} title={`Trajet aller (matin) — ${formatMonnaie(dayInd.montantTrajet || dayInd.montantJour / 2)}`}>
                                                                                            <input
                                                                                                type="checkbox"
                                                                                                checked={matVal}
                                                                                                disabled={!canEditRow}
                                                                                                onChange={(e) => updateRowDynamicIndemnite(row.datePointage, ind.idIndemnite, e.target.checked, 'MATIN')}
                                                                                                className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-600)]"
                                                                                            />
                                                                                            <span className={matVal ? 'text-[var(--color-fg)] font-bold' : 'text-[var(--color-fg-muted)]'}>M</span>
                                                                                        </label>
                                                                                        <label className={`inline-flex items-center gap-0.5 text-[10px] font-semibold ${!canEditRow ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`} title={`Trajet retour (soir) — ${formatMonnaie(dayInd.montantTrajet || dayInd.montantJour / 2)}`}>
                                                                                            <input
                                                                                                type="checkbox"
                                                                                                checked={amVal}
                                                                                                disabled={!canEditRow}
                                                                                                onChange={(e) => updateRowDynamicIndemnite(row.datePointage, ind.idIndemnite, e.target.checked, 'APRES_MIDI')}
                                                                                                className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-600)]"
                                                                                            />
                                                                                            <span className={amVal ? 'text-[var(--color-fg)] font-bold' : 'text-[var(--color-fg-muted)]'}>A</span>
                                                                                        </label>
                                                                                    </div>
                                                                                </div>
                                                                            </Td>
                                                                        );
                                                                    }

                                                                    const isAccordee = Boolean(dynObj.accordee);
                                                                    return (
                                                                        <Td key={ind.idIndemnite} className="px-2 py-1 text-center border-l border-[var(--color-border-subtle)]">
                                                                            <div className="flex flex-col items-center justify-center gap-0.5">
                                                                                {dynObj.motifSuggestion && (
                                                                                    <span className="text-[8px] font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]" title={dynObj.motifSuggestion}>
                                                                                        {dynObj.motifSuggestion}
                                                                                    </span>
                                                                                )}
                                                                                <input
                                                                                    type="checkbox"
                                                                                    checked={isAccordee}
                                                                                    disabled={!canEditRow}
                                                                                    onChange={(e) => updateRowDynamicIndemnite(row.datePointage, ind.idIndemnite, e.target.checked, 'ALL')}
                                                                                    className="w-3.5 h-3.5 rounded accent-[var(--color-brand-600)] cursor-pointer align-middle"
                                                                                    title={`${dayInd.libelle || dayInd.code} — ${formatMonnaie(dayInd.montantJour)}/j`}
                                                                                />
                                                                            </div>
                                                                        </Td>
                                                                    );
                                                                })}

                                                                {/* 11. Actions rapides (Régulariser Congé/Absence, Vider ou Standard) */}
                                                                <Td className="px-2 py-1 text-center whitespace-nowrap">
                                                                    <div className="flex items-center justify-center gap-1.5">
                                                                        {/* Action Régularisation Congé / Absence */}
                                                                        <button
                                                                            type="button"
                                                                            onClick={() => openRegularisation(row)}
                                                                            disabled={!canEditRow}
                                                                            aria-label="Signaler une absence ou un congé pour cette journée"
                                                                            title="Signaler un congé annuel, une permission exceptionnelle ou une absence déduite"
                                                                            className="px-2 py-1 text-[11px] font-semibold text-[var(--color-brand-700)] hover:text-[var(--color-brand-800)] hover:bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/40 rounded transition-colors cursor-pointer inline-flex items-center justify-center gap-1.5 whitespace-nowrap disabled:opacity-50 disabled:cursor-not-allowed"
                                                                        >
                                                                            <Palmtree size={15} />
                                                                            <span>Absence / congé</span>
                                                                        </button>

                                                                        {/* Action Heures : Vider ou Standard */}
                                                                        {(isWeekend && !hasPunches) || (isFerie && !hasPunches) || (isConge && !hasPunches) ? (
                                                                            <span className="opacity-30 text-[10px]">—</span>
                                                                        ) : hasPunches ? (
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => handleResetDay(row.datePointage)}
                                                                                disabled={!canEditRow}
                                                                                title="Vider les heures de cette journée"
                                                                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 rounded transition-colors cursor-pointer inline-flex items-center justify-center"
                                                                            >
                                                                                <RotateCcw size={12} />
                                                                            </button>
                                                                        ) : (
                                                                            <button
                                                                                type="button"
                                                                                onClick={() => handleApplyDayStandard(row.datePointage)}
                                                                                disabled={!canEditRow}
                                                                                title="Appliquer l'horaire standard (08h-17h)"
                                                                                className="px-1.5 py-0.5 text-[10px] font-medium text-[var(--color-brand-600)] hover:bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/30 rounded transition-colors cursor-pointer whitespace-nowrap"
                                                                            >
                                                                                Standard
                                                                            </button>
                                                                        )}
                                                                    </div>
                                                                </Td>
                                                            </Tr>
                                                        );
                                                    })}
                                                </Tbody>
                                            </Table>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </>
            )}

            {/* Modale de Régularisation directe par Congé / Absence (Lazy Loaded) */}
            {regularisationRow && (
                <Suspense fallback={null}>
                    <AnomalieRegularisationModal
                        isOpen={Boolean(regularisationRow)}
                        directDayFlow
                        onClose={() => setRegularisationRow(null)}
                        anomalie={{
                            idPointage: regularisationRow.idPointage || 0,
                            idEmploye: selectedEmployeId,
                            idSite: regularisationRow.idSite || selectedSiteId || 1,
                            idPeriodePaie: selectedPeriodeId,
                            datePointage: regularisationRow.datePointage,
                            nom: ficheData?.nom || currentEmpSummary?.nom || '',
                            prenom: ficheData?.prenom || currentEmpSummary?.prenom || '',
                            matricule: ficheData?.matricule || currentEmpSummary?.matricule || '',
                            photoUrl: ficheData?.photoUrl || currentEmpSummary?.photoUrl,
                            statutCode: regularisationRow.codeStatut || 'NON_SAISI',
                            statutLibelle: regularisationRow.codeStatut,
                            typeAnomalie: 'ABSENCE',
                            heureEntreeMatin: regularisationRow.heureEntreeMatin,
                            heureSortieMatin: regularisationRow.heureSortieMatin,
                            heureEntreeAm: regularisationRow.heureEntreeAm,
                            heureSortieAm: regularisationRow.heureSortieAm,
                            importEventCode: regularisationRow.importEventCode,
                            importHalfDayMoment: regularisationRow.importHalfDayMoment,
                            importAllowanceOverrides: regularisationRow.importAllowanceOverrides,
                            version: regularisationRow.version || 1
                        }}
                        onSuccess={handleSaveRegularisationForDay}
                    />
                </Suspense>
            )}

            {/* 6. Barre d'action inférieure de sauvegarde flottante centrée (uniquement si modifications) */}
            {dirtyCount > 0 && (
                <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-[var(--color-surface)] border border-[var(--color-border-strong)] shadow-2xl px-4 py-2.5 flex items-center gap-4 animate-in fade-in slide-in-from-bottom-3 backdrop-blur-md">
                    <div className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg)]">
                        <span className="w-2 h-2 rounded-full bg-[var(--color-warning-500)] animate-pulse" />
                        <span>{dirtyCount} modification{dirtyCount > 1 ? 's' : ''} non enregistrée{dirtyCount > 1 ? 's' : ''}</span>
                    </div>

                    <button
                        type="button"
                        onClick={loadFicheEmploye}
                        disabled={saving}
                        className="px-3 py-1 text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer transition-colors"
                    >
                        Annuler
                    </button>

                    <button
                        type="button"
                        onClick={handleSaveBatch}
                        disabled={saving || !canEditPeriod}
                        className="px-4 py-1.5 text-xs font-bold bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] text-[var(--color-on-color)] shadow-xs flex items-center gap-1.5 cursor-pointer transition-all"
                    >
                        {saving ? (
                            <span className="animate-spin inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full" />
                        ) : (
                            <Save size={13} />
                        )}
                        <span>Enregistrer les modifications (Ctrl+S)</span>
                    </button>
                </div>
            )}
        </div>
    );
}
