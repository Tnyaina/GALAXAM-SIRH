import { useState, useMemo, useEffect } from 'react';
import { Link } from 'react-router-dom';
import {
    AlertTriangle,
    RefreshCw,
    Search,
    Clock,
    XCircle,
    AlertCircle,
    Wrench,
    CheckCircle2,
    FileText,
    ArrowLeft,
    ClockCheck,
    Check,
    Layers
} from 'lucide-react';
import { usePointageAnomalies } from '@/hooks/pointage/usePointageAnomalies';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Input from '@/components/ui/forms/Input';
import Card, { CardBody } from '@/components/ui/data-display/Card';
import Pagination from '@/components/ui/navigation/Pagination';
import Modal from '@/components/ui/overlays/Modal';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import { useAuth } from '@/context/AuthContext';
import SEO from '@/components/system/SEO';

const TYPE_CONFIG = {
    'INCOMPLET': { variant: 'warning', label: 'Pointage Incomplet', icon: Clock },
    'RETARD': { variant: 'danger', label: 'Retard non justifié', icon: AlertTriangle },
    'ABSENCE': { variant: 'secondary', label: 'Absence injustifiée', icon: XCircle }
};

export default function AnomaliesPointagePage() {
    const {
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
    } = usePointageAnomalies();

    const { user, hasPermission } = useAuth();
    const isAdmin = user?.role === 'ROLE_ADMIN';
    const canViewEmploye = hasPermission('EMPLOYE_READ');
    const toast = useToast();

    // Modale de régularisation
    const [selectedAnomalie, setSelectedAnomalie] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [formData, setFormData] = useState({
        actionType: 'PRESENCE', // 'PRESENCE' | 'ABSENCE_JUSTIFIEE'
        heureEntreeMatin: '',
        heureSortieMatin: '',
        heureEntreeAm: '',
        heureSortieAm: '',
        motifAjustement: ''
    });

    // --- Traitement en masse (état 100% local, ne modifie pas le hook) ---
    const [selectedKeys, setSelectedKeys] = useState(() => new Set());
    const [bulkRunning, setBulkRunning] = useState(false);
    const [sortBy, setSortBy] = useState('date'); // 'date' | 'employe'
    const [localPageSize, setLocalPageSize] = useState(pageSize || 25);

    /** Clé stable pour une anomalie, indépendante de la pagination (utilisée pour la sélection et le key React). */
    const getAnoKey = (ano) => (ano.idPointage ? `ptg-${ano.idPointage}` : `emp-${ano.idEmploye}-${ano.datePointage}`);

    const handleQuickStandard = async (ano) => {
        const success = await regulariserStandard(ano);
        if (success) {
            toast({
                title: 'Pointage régularisé',
                message: `Horaires standards appliqués pour ${ano.nom} ${ano.prenom} (${ano.datePointage}).`,
                type: 'success'
            });
        }
    };

    const handleQuickJustify = async (ano, motif) => {
        const success = await justifierRetardRapide(ano, motif);
        if (success) {
            toast({
                title: 'Retard justifié',
                message: `Le retard de ${ano.nom} ${ano.prenom} a été justifié (${motif}).`,
                type: 'success'
            });
        }
    };

    const openRegularisation = (anomalie) => {
        setSelectedAnomalie(anomalie);
        setFormData({
            actionType: 'PRESENCE',
            heureEntreeMatin: anomalie.heureEntreeMatin || '08:00',
            heureSortieMatin: anomalie.heureSortieMatin || '12:00',
            heureEntreeAm: anomalie.heureEntreeAm || '13:00',
            heureSortieAm: anomalie.heureSortieAm || '17:00',
            motifAjustement: anomalie.motifAjustement || ''
        });
        setIsModalOpen(true);
    };

    const handleSaveRegularisation = async (e) => {
        e.preventDefault();
        if (!formData.motifAjustement || !formData.motifAjustement.trim()) {
            toast({
                title: 'Motif obligatoire',
                message: 'Veuillez préciser le motif de régularisation (ex: Oubli de badgeage, Retard justifié transport).',
                type: 'warning'
            });
            return;
        }

        const payload = {
            ...formData,
            idEmploye: selectedAnomalie.idEmploye,
            datePointage: selectedAnomalie.datePointage,
            idPeriodePaie: selectedAnomalie.idPeriodePaie,
            idSite: selectedAnomalie.idSite
        };

        const success = await regulariser(selectedAnomalie.idPointage || 0, payload);
        if (success) {
            toast({
                title: 'Anomalie régularisée',
                message: `Le pointage de ${selectedAnomalie.nom} ${selectedAnomalie.prenom} a été régularisé avec succès.`,
                type: 'success'
            });
            setIsModalOpen(false);
            setSelectedAnomalie(null);
        } else {
            toast({
                title: 'Erreur',
                message: error || "Impossible d'enregistrer la régularisation.",
                type: 'danger'
            });
        }
    };

    // Tri (permet de regrouper visuellement les anomalies d'un même employé)
    const sortedAnomalies = useMemo(() => {
        const arr = [...anomalies];
        if (sortBy === 'employe') {
            arr.sort((a, b) => {
                const nomA = `${a.nom || ''} ${a.prenom || ''}`.toLowerCase();
                const nomB = `${b.nom || ''} ${b.prenom || ''}`.toLowerCase();
                return nomA.localeCompare(nomB) || (a.datePointage || '').localeCompare(b.datePointage || '');
            });
        }
        return arr;
    }, [anomalies, sortBy]);

    // Pagination (taille de page ajustable localement, indépendante du hook)
    const totalPages = Math.ceil(sortedAnomalies.length / localPageSize) || 1;

    // Sécurité ajustement : si la page courante dépasse le total de pages
    useEffect(() => {
        if (currentPage > totalPages && totalPages > 0) {
            setCurrentPage(1);
        }
    }, [totalPages, currentPage, setCurrentPage]);

    const paginatedAnomalies = useMemo(() => {
        const start = (currentPage - 1) * localPageSize;
        return sortedAnomalies.slice(start, start + localPageSize);
    }, [sortedAnomalies, currentPage, localPageSize]);

    // --- Sélection multiple ---
    const selectedAnomalies = useMemo(
        () => anomalies.filter((a) => selectedKeys.has(getAnoKey(a))),
        [anomalies, selectedKeys]
    );
    const isPageFullySelected = paginatedAnomalies.length > 0 && paginatedAnomalies.every((a) => selectedKeys.has(getAnoKey(a)));

    const toggleOne = (ano) => {
        setSelectedKeys((prev) => {
            const next = new Set(prev);
            const key = getAnoKey(ano);
            if (next.has(key)) next.delete(key); else next.add(key);
            return next;
        });
    };

    const toggleSelectPage = () => {
        setSelectedKeys((prev) => {
            const next = new Set(prev);
            if (isPageFullySelected) {
                paginatedAnomalies.forEach((a) => next.delete(getAnoKey(a)));
            } else {
                paginatedAnomalies.forEach((a) => next.add(getAnoKey(a)));
            }
            return next;
        });
    };

    const selectAllFiltered = () => {
        setSelectedKeys(new Set(anomalies.map((a) => getAnoKey(a))));
    };

    const clearSelection = () => setSelectedKeys(new Set());

    const handleBulkStandard = async () => {
        const items = selectedAnomalies.filter((a) => a.typeAnomalie === 'INCOMPLET' || a.typeAnomalie === 'ABSENCE');
        if (items.length === 0) return;
        setBulkRunning(true);
        const payloadList = items.map((ano) => {
            const isTMM = ano.idSite === 2;
            return {
                idEmploye: ano.idEmploye,
                datePointage: ano.datePointage,
                idPeriodePaie: ano.idPeriodePaie,
                idSite: ano.idSite,
                actionType: 'PRESENCE',
                heureEntreeMatin: '08:00',
                heureSortieMatin: '12:00',
                heureEntreeAm: isTMM ? '14:00' : '13:00',
                heureSortieAm: isTMM ? '18:00' : '17:00',
                motifAjustement: 'Régularisation horaire normal en masse'
            };
        });
        const res = await regulariserBatch(payloadList);
        setBulkRunning(false);
        if (res?.success) {
            toast({
                title: 'Régularisation en masse',
                message: `${res.count || items.length} pointage(s) régularisé(s) instantanément avec les horaires standard.`,
                type: 'success'
            });
            clearSelection();
        } else {
            toast({
                title: 'Erreur régularisation en masse',
                message: res?.message || 'Une erreur est survenue.',
                type: 'danger'
            });
        }
    };

    const handleBulkJustify = async () => {
        const items = selectedAnomalies.filter((a) => a.typeAnomalie === 'RETARD');
        if (items.length === 0) return;
        setBulkRunning(true);
        const payloadList = items.map((ano) => ({
            idEmploye: ano.idEmploye,
            datePointage: ano.datePointage,
            idPeriodePaie: ano.idPeriodePaie,
            idSite: ano.idSite,
            actionType: 'PRESENCE',
            heureEntreeMatin: ano.heureEntreeMatin || '08:00',
            heureSortieMatin: ano.heureSortieMatin || '12:00',
            heureEntreeAm: ano.heureEntreeAm || '13:00',
            heureSortieAm: ano.heureSortieAm || '17:00',
            motifAjustement: 'Retard justifié transport / circulation'
        }));
        const res = await regulariserBatch(payloadList);
        setBulkRunning(false);
        if (res?.success) {
            toast({
                title: 'Justification en masse',
                message: `${res.count || items.length} retard(s) justifié(s) instantanément.`,
                type: 'success'
            });
            clearSelection();
        } else {
            toast({
                title: 'Erreur justification en masse',
                message: res?.message || 'Une erreur est survenue.',
                type: 'danger'
            });
        }
    };

    return (
        <div className="space-y-6 font-sans">
            <SEO title="Gestion des Anomalies" description="Suivi et régularisation des anomalies et retards de pointage." />

            {/* Lien retour */}
            <Link
                to="/dashboard"
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold"
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête standard Galaxam */}
            <div className="relative overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-sm p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="absolute top-0 right-0 w-64 h-64 bg-[var(--color-brand-500)]/5 rounded-none blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />

                <div className="relative flex items-center gap-5">
                    <div className="w-14 h-14 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-lg shadow-[var(--color-brand-500)]/20 shrink-0">
                        <AlertTriangle size={26} strokeWidth={2.5} />
                    </div>
                    <div>
                        <h1 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-fg)] tracking-tight">
                            Gestion des Anomalies de Pointage
                        </h1>
                        <p className="text-sm text-[var(--color-fg-muted)] mt-1 font-medium">
                            Supervision des pointages incomplets, retards non justifiés et régularisation avant validation de paie.
                        </p>
                    </div>
                </div>

                <div className="relative flex items-center gap-3 shrink-0">
                    <Button
                        onClick={loadAnomalies}
                        variant="outline"
                        size="sm"
                        icon={<RefreshCw size={14} className={loading ? 'animate-spin' : ''} />}
                    >
                        Actualiser
                    </Button>
                </div>
            </div>

            {/* 4 Cartes Statistiques KPI — cliquables : servent aussi de filtre par type */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <button
                    type="button"
                    onClick={() => { setTypeFilter('ALL'); setCurrentPage(1); }}
                    className={`text-left transition-all ${typeFilter === 'ALL' ? 'ring-2 ring-[var(--color-brand-500)]' : ''}`}
                >
                    <Card bordered shadow="sm">
                        <CardBody className="p-5 flex items-center gap-4">
                            <div className="w-11 h-11 bg-amber-500/10 text-amber-600 flex items-center justify-center">
                                <AlertCircle size={20} />
                            </div>
                            <div>
                                <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Total Anomalies</p>
                                <p className="text-2xl font-bold text-ink-800 dark:text-white">{stats.total}</p>
                            </div>
                        </CardBody>
                    </Card>
                </button>

                <button
                    type="button"
                    onClick={() => { setTypeFilter('INCOMPLET'); setCurrentPage(1); }}
                    className={`text-left transition-all ${typeFilter === 'INCOMPLET' ? 'ring-2 ring-[var(--color-brand-500)]' : ''}`}
                >
                    <Card bordered shadow="sm">
                        <CardBody className="p-5 flex items-center gap-4">
                            <div className="w-11 h-11 bg-warning-500/10 text-warning-600 flex items-center justify-center">
                                <Clock size={20} />
                            </div>
                            <div>
                                <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Incomplets</p>
                                <p className="text-2xl font-bold text-warning-600">{stats.incomplets}</p>
                            </div>
                        </CardBody>
                    </Card>
                </button>

                <button
                    type="button"
                    onClick={() => { setTypeFilter('RETARD'); setCurrentPage(1); }}
                    className={`text-left transition-all ${typeFilter === 'RETARD' ? 'ring-2 ring-[var(--color-brand-500)]' : ''}`}
                >
                    <Card bordered shadow="sm">
                        <CardBody className="p-5 flex items-center gap-4">
                            <div className="w-11 h-11 bg-danger-500/10 text-danger-600 flex items-center justify-center">
                                <AlertTriangle size={20} />
                            </div>
                            <div>
                                <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Retards à justifier</p>
                                <p className="text-2xl font-bold text-danger-600">{stats.retards}</p>
                            </div>
                        </CardBody>
                    </Card>
                </button>

                <button
                    type="button"
                    onClick={() => { setTypeFilter('ABSENCE'); setCurrentPage(1); }}
                    className={`text-left transition-all ${typeFilter === 'ABSENCE' ? 'ring-2 ring-[var(--color-brand-500)]' : ''}`}
                >
                    <Card bordered shadow="sm">
                        <CardBody className="p-5 flex items-center gap-4">
                            <div className="w-11 h-11 bg-ink-100 dark:bg-ink-800 text-ink-600 dark:text-ink-300 flex items-center justify-center">
                                <XCircle size={20} />
                            </div>
                            <div>
                                <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Absences injustifiées</p>
                                <p className="text-2xl font-bold text-ink-700 dark:text-ink-200">{stats.absences}</p>
                            </div>
                        </CardBody>
                    </Card>
                </button>
            </div>

            {error && <Alert type="danger" message={error} onClose={() => setError(null)} />}

            {/* Barre de Filtres */}
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-4 shadow-sm flex flex-col md:flex-row items-center justify-between gap-4">
                <div className="flex flex-wrap items-center gap-4 w-full md:w-auto">
                    {/* Filtre Site */}
                    <div className="flex items-center gap-2">
                        <label className="text-xs font-bold text-ink-400 uppercase tracking-wider">Site :</label>
                        <select
                            value={selectedSite}
                            onChange={(e) => {
                                setSelectedSite(Number(e.target.value));
                                setCurrentPage(1);
                            }}
                            className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-2 outline-none"
                        >
                            <option value={0}>Tous les sites</option>
                            <option value={1}>TNR — Antananarivo Siège</option>
                            <option value={2}>TMM — Toamasina Agence</option>
                        </select>
                    </div>

                    {/* Filtre Période */}
                    <div className="flex items-center gap-2">
                        <label className="text-xs font-bold text-ink-400 uppercase tracking-wider">Période :</label>
                        <select
                            value={selectedPeriode}
                            onChange={(e) => {
                                setSelectedPeriode(e.target.value);
                                setCurrentPage(1);
                            }}
                            className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-2 outline-none"
                        >
                            <option value="">Toutes les périodes</option>
                            {periodes.map(p => (
                                <option key={p.idPeriodePaie} value={p.idPeriodePaie}>
                                    {p.libelle || p.code} ({p.statutLibelle})
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Filtre Date / Intervalle */}
                    <div className="flex items-center gap-2">
                        <label className="text-xs font-bold text-ink-400 uppercase tracking-wider">Date :</label>
                        <select
                            value={dateFilterMode}
                            onChange={(e) => {
                                setDateFilterMode(e.target.value);
                                setCurrentPage(1);
                            }}
                            className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-2 outline-none"
                        >
                            <option value="ALL">Toutes les dates</option>
                            <option value="SPECIFIC">Date spécifique</option>
                            <option value="INTERVAL">Intervalle de dates</option>
                        </select>

                        {dateFilterMode === 'SPECIFIC' && (
                            <input
                                type="date"
                                value={selectedDate}
                                onChange={(e) => setSelectedDate(e.target.value)}
                                className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-1.5 outline-none"
                            />
                        )}

                        {dateFilterMode === 'INTERVAL' && (
                            <div className="flex items-center gap-1.5">
                                <input
                                    type="date"
                                    placeholder="Du"
                                    value={dateDebut}
                                    onChange={(e) => setDateDebut(e.target.value)}
                                    className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-1.5 outline-none"
                                />
                                <span className="text-xs text-ink-400">à</span>
                                <input
                                    type="date"
                                    placeholder="Au"
                                    value={dateFin}
                                    onChange={(e) => setDateFin(e.target.value)}
                                    className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-1.5 outline-none"
                                />
                            </div>
                        )}
                    </div>

                    {/* Tri (aide à regrouper les anomalies d'un même employé) */}
                    <div className="flex items-center gap-2">
                        <label className="text-xs font-bold text-ink-400 uppercase tracking-wider">Trier par :</label>
                        <select
                            value={sortBy}
                            onChange={(e) => { setSortBy(e.target.value); setCurrentPage(1); }}
                            className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-2 outline-none"
                        >
                            <option value="date">Date (plus récente en premier)</option>
                            <option value="employe">Employé (regroupe les cas d'une même personne)</option>
                        </select>
                    </div>

                    {/* Recherche Employé */}
                    <div className="w-52">
                        <Input
                            placeholder="Rechercher employé..."
                            icon={<Search size={14} />}
                            value={searchTerm}
                            onChange={(e) => {
                                setSearchTerm(e.target.value);
                                setCurrentPage(1);
                            }}
                        />
                    </div>
                </div>
            </div>

            {/* Barre d'actions groupées — visible uniquement si une sélection est en cours */}
            {selectedKeys.size > 0 && (
                <div className="bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)] p-4 flex flex-col sm:flex-row items-center justify-between gap-3">
                    <p className="text-sm font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] flex items-center gap-2">
                        <Layers size={16} />
                        {selectedKeys.size} anomalie(s) sélectionnée(s)
                    </p>
                    <div className="flex flex-wrap items-center gap-2">
                        <Button
                            size="sm"
                            variant="outline"
                            icon={<ClockCheck size={14} />}
                            onClick={handleBulkStandard}
                            disabled={bulkRunning || selectedAnomalies.every((a) => a.typeAnomalie !== 'INCOMPLET' && a.typeAnomalie !== 'ABSENCE')}
                        >
                            Appliquer horaires standard
                        </Button>
                        <Button
                            size="sm"
                            variant="outline"
                            icon={<Check size={14} />}
                            onClick={handleBulkJustify}
                            disabled={bulkRunning || selectedAnomalies.every((a) => a.typeAnomalie !== 'RETARD')}
                        >
                            Justifier les retards
                        </Button>
                        <Button size="sm" variant="ghost" onClick={clearSelection} disabled={bulkRunning}>
                            Désélectionner tout
                        </Button>
                    </div>
                </div>
            )}

            {/* Tableau des Anomalies */}
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="p-4 border-b border-ink-150 dark:border-ink-700 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                    <div>
                        <h2 className="text-sm font-bold uppercase tracking-wider text-ink-800 dark:text-white">
                            Liste des Anomalies Détectées ({anomalies.length})
                        </h2>
                        <p className="text-xs text-ink-400 dark:text-ink-400 mt-0.5">
                            Régularisez les heures ou justifiez les retards avant le verrouillage DAF.
                        </p>
                    </div>
                    {!loading && anomalies.length > 0 && (
                        <div className="flex items-center gap-3 text-xs">
                            <button
                                type="button"
                                onClick={toggleSelectPage}
                                className="font-bold text-[var(--color-brand-600)] hover:underline"
                            >
                                {isPageFullySelected ? 'Désélectionner cette page' : 'Sélectionner cette page'}
                            </button>
                            <span className="text-ink-300">•</span>
                            <button
                                type="button"
                                onClick={selectAllFiltered}
                                className="font-bold text-[var(--color-brand-600)] hover:underline"
                            >
                                Tout sélectionner ({anomalies.length} correspondant aux filtres)
                            </button>
                        </div>
                    )}
                </div>

                {loading ? (
                    <SkeletonTable rows={6} cols={9} />
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th className="w-10">
                                    <input
                                        type="checkbox"
                                        checked={isPageFullySelected}
                                        onChange={toggleSelectPage}
                                        className="w-4 h-4 cursor-pointer accent-[var(--color-brand-500)]"
                                        title="Sélectionner tous les éléments de cette page"
                                    />
                                </Th>
                                <Th>Date</Th>
                                <Th>Employé</Th>
                                <Th>Site</Th>
                                <Th>Type & Description</Th>
                                <Th className="text-center">Horaires Saisis</Th>
                                <Th className="text-center">Retard</Th>
                                <Th>Motif / Justification</Th>
                                <Th className="text-right">Action</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {paginatedAnomalies.map((ano, idx) => {
                                const isLocked = ano.statutPeriodeCode === 'VALIDEE' || ano.statutPeriodeCode === 'CLOTUREE';
                                const rowKey = getAnoKey(ano);
                                const isSelected = selectedKeys.has(rowKey);
                                const isNewEmployeeGroup = sortBy === 'employe' && idx > 0 && paginatedAnomalies[idx - 1].idEmploye !== ano.idEmploye;
                                return (
                                    <Tr
                                        key={rowKey}
                                        className={`hover:bg-ink-50/40 dark:hover:bg-ink-950/40 ${isSelected ? 'bg-[var(--color-brand-500)]/5' : ''} ${isNewEmployeeGroup ? 'border-t-2 border-t-[var(--color-brand-500)]/30' : ''}`}
                                    >
                                        <Td>
                                            <input
                                                type="checkbox"
                                                checked={isSelected}
                                                onChange={() => toggleOne(ano)}
                                                className="w-4 h-4 cursor-pointer accent-[var(--color-brand-500)]"
                                            />
                                        </Td>
                                        <Td className="font-mono text-xs font-bold text-ink-900 dark:text-white whitespace-nowrap">
                                            {ano.datePointage}
                                        </Td>
                                        <Td>
                                            {canViewEmploye ? (
                                                <Link
                                                    to={`/employes/${ano.idEmploye}?tab=pointage`}
                                                    className="group flex items-center gap-2.5 hover:opacity-90 transition-opacity"
                                                    title="Ouvrir la fiche employé (onglet Pointage)"
                                                >
                                                    <div className="w-8 h-8 rounded-none bg-brand-500 text-white flex items-center justify-center font-bold text-xs shrink-0 group-hover:ring-2 group-hover:ring-brand-500/40">
                                                        {ano.nom?.substring(0, 1)}{ano.prenom?.substring(0, 1)}
                                                    </div>
                                                    <div>
                                                        <p className="text-xs font-bold text-ink-900 dark:text-white group-hover:text-[var(--color-brand-600)] dark:group-hover:text-[var(--color-brand-400)] transition-colors underline-offset-2 group-hover:underline">
                                                            {ano.nom} {ano.prenom}
                                                        </p>
                                                        <p className="text-[11px] text-ink-400 font-mono">{ano.matricule}</p>
                                                    </div>
                                                </Link>
                                            ) : (
                                                <div className="flex items-center gap-2.5">
                                                    <div className="w-8 h-8 rounded-none bg-brand-500 text-white flex items-center justify-center font-bold text-xs shrink-0">
                                                        {ano.nom?.substring(0, 1)}{ano.prenom?.substring(0, 1)}
                                                    </div>
                                                    <div>
                                                        <p className="text-xs font-bold text-ink-900 dark:text-white">{ano.nom} {ano.prenom}</p>
                                                        <p className="text-[11px] text-ink-400 font-mono">{ano.matricule}</p>
                                                    </div>
                                                </div>
                                            )}
                                        </Td>
                                        <Td className="text-xs text-ink-500">{ano.siteNom || '—'}</Td>
                                        <Td>
                                            <div className="space-y-1">
                                                <Badge variant={TYPE_CONFIG[ano.typeAnomalie]?.variant || 'warning'}>
                                                    {TYPE_CONFIG[ano.typeAnomalie]?.label || ano.typeAnomalie}
                                                </Badge>
                                                <p className="text-[11px] text-ink-500 dark:text-ink-400">{ano.descriptionAnomalie}</p>
                                            </div>
                                        </Td>
                                        <Td className="text-center font-mono text-xs">
                                            <div className="inline-block bg-ink-50 dark:bg-ink-800 p-1 px-2 border border-ink-150 dark:border-ink-700 text-ink-700 dark:text-ink-300">
                                                {ano.heureEntreeMatin || '—'} - {ano.heureSortieMatin || '—'} | {ano.heureEntreeAm || '—'} - {ano.heureSortieAm || '—'}
                                            </div>
                                        </Td>
                                        <Td className="text-center font-mono text-xs font-bold">
                                            {ano.minutesRetard > 0 ? (
                                                <span className="text-danger-600">-{ano.minutesRetard} min</span>
                                            ) : (
                                                <span className="text-ink-400">0 min</span>
                                            )}
                                        </Td>
                                        <Td className="text-xs text-ink-600 dark:text-ink-300 max-w-[200px] truncate">
                                            {ano.motifAjustement ? (
                                                <span className="flex items-center gap-1">
                                                    <FileText size={13} className="text-brand-500 shrink-0" />
                                                    {ano.motifAjustement}
                                                </span>
                                            ) : (
                                                <span className=" text-ink-300 dark:text-ink-500">Non renseigné</span>
                                            )}
                                        </Td>
                                        <Td className="text-right">
                                            <div className="flex items-center justify-end gap-1.5">
                                                {/* Action 1-clic pour Incomplets & Absences */}
                                                {(ano.typeAnomalie === 'INCOMPLET' || ano.typeAnomalie === 'ABSENCE') && (
                                                    <Button
                                                        size="xs"
                                                        variant="outline"
                                                        icon={<ClockCheck size={13} className="text-[var(--color-brand-500)]" />}
                                                        onClick={() => handleQuickStandard(ano)}
                                                        disabled={isLocked && !isAdmin}
                                                        title="Appliquer les horaires normaux du site en 1 clic"
                                                    >
                                                        Standard
                                                    </Button>
                                                )}

                                                {/* Action 1-clic pour Retards */}
                                                {ano.typeAnomalie === 'RETARD' && (
                                                    <Button
                                                        size="xs"
                                                        variant="outline"
                                                        icon={<Check size={13} className="text-[var(--color-success-600)]" />}
                                                        onClick={() => handleQuickJustify(ano, 'Retard justifié transport / circulation')}
                                                        disabled={isLocked && !isAdmin}
                                                        title="Justifier le retard immédiatement"
                                                    >
                                                        Justifier
                                                    </Button>
                                                )}

                                                <Button
                                                    size="xs"
                                                    variant="primary"
                                                    icon={<Wrench size={13} />}
                                                    onClick={() => openRegularisation(ano)}
                                                    disabled={isLocked && !isAdmin}
                                                    title={isLocked ? "Période validée" : "Ouvrir l'éditeur complet"}
                                                >
                                                    Détails
                                                </Button>
                                            </div>
                                        </Td>
                                    </Tr>
                                );
                            })}
                            {anomalies.length === 0 && !loading && (
                                <Tr>
                                    <Td colSpan={9} className="text-center py-12 text-ink-400 dark:text-ink-400">
                                        <CheckCircle2 size={36} className="mx-auto mb-2 text-success-500 opacity-60" />
                                        Aucune anomalie détectée pour les filtres sélectionnés. Tous les pointages sont conformes !
                                    </Td>
                                </Tr>
                            )}
                        </Tbody>
                    </Table>
                )}

                {/* Pagination */}
                {!loading && anomalies.length > 0 && (
                    <div className="p-4 border-t border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/50 flex flex-col sm:flex-row items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                            <label className="text-xs font-bold text-ink-400 uppercase tracking-wider whitespace-nowrap">Par page :</label>
                            <select
                                value={localPageSize}
                                onChange={(e) => {
                                    setLocalPageSize(Number(e.target.value));
                                    setCurrentPage(1);
                                }}
                                className="bg-transparent border border-ink-200 dark:border-ink-700 text-xs font-bold text-ink-800 dark:text-white p-2 outline-none"
                            >
                                <option value={10}>10</option>
                                <option value={25}>25</option>
                                <option value={50}>50</option>
                                <option value={100}>100</option>
                            </select>
                        </div>
                        <Pagination
                            currentPage={currentPage}
                            totalPages={totalPages}
                            totalItems={anomalies.length}
                            pageSize={localPageSize}
                            onPageChange={setCurrentPage}
                        />
                    </div>
                )}
            </div>

            {/* Modale de Régularisation Améliorée et Aérée */}
            <Modal
                isOpen={isModalOpen}
                onClose={() => {
                    setIsModalOpen(false);
                    setSelectedAnomalie(null);
                }}
                size="xl"
                title={`Régularisation de Pointage — ${selectedAnomalie?.nom || ''} ${selectedAnomalie?.prenom || ''}`}
            >
                {selectedAnomalie && (
                    <form onSubmit={handleSaveRegularisation} className="space-y-5">
                        {/* En-tête employé & anomalie */}
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-4 bg-amber-500/10 border border-amber-500/30">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 bg-brand-500 text-white font-bold flex items-center justify-center text-sm shrink-0">
                                    {selectedAnomalie.nom?.substring(0, 1)}{selectedAnomalie.prenom?.substring(0, 1)}
                                </div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <span className="font-bold text-sm text-[var(--color-fg)]">
                                            {selectedAnomalie.nom} {selectedAnomalie.prenom}
                                        </span>
                                        <span className="text-xs font-mono text-[var(--color-fg-muted)]">
                                            ({selectedAnomalie.matricule})
                                        </span>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        Site : <span className="font-medium text-[var(--color-fg)]">{selectedAnomalie.siteNom || '—'}</span>
                                    </p>
                                </div>
                            </div>
                            <div className="text-left sm:text-right">
                                <span className="inline-block px-2.5 py-1 text-xs font-bold font-mono bg-amber-500/20 text-amber-800 dark:text-amber-300 border border-amber-500/40">
                                    Date : {selectedAnomalie.datePointage}
                                </span>
                            </div>
                        </div>

                        {/* Motif de l'anomalie détectée */}
                        <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-start gap-2.5">
                            <AlertTriangle size={17} className="shrink-0 text-amber-500 mt-0.5" />
                            <div className="text-xs text-[var(--color-fg)]">
                                <span className="font-bold">Anomalie constatée : </span>
                                <span>{selectedAnomalie.descriptionAnomalie}</span>
                            </div>
                        </div>

                        {/* Choix du mode de régularisation */}
                        <div className="space-y-1.5">
                            <label className="text-xs font-bold text-[var(--color-fg)] uppercase tracking-wider">
                                Mode de régularisation :
                            </label>
                            <div className="grid grid-cols-2 gap-3">
                                <button
                                    type="button"
                                    onClick={() => setFormData({ ...formData, actionType: 'PRESENCE' })}
                                    className={`p-3.5 text-left border text-xs font-bold transition-all flex items-center justify-between ${formData.actionType === 'PRESENCE'
                                            ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shadow-sm'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                        }`}
                                >
                                    <span>Présence effective (Rectifier les heures)</span>
                                    {formData.actionType === 'PRESENCE' && <Check size={16} className="text-[var(--color-brand-500)]" />}
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setFormData({ ...formData, actionType: 'ABSENCE_JUSTIFIEE' })}
                                    className={`p-3.5 text-left border text-xs font-bold transition-all flex items-center justify-between ${formData.actionType === 'ABSENCE_JUSTIFIEE'
                                            ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shadow-sm'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                        }`}
                                >
                                    <span>Absence justifiée (Avec motif RH)</span>
                                    {formData.actionType === 'ABSENCE_JUSTIFIEE' && <Check size={16} className="text-[var(--color-brand-500)]" />}
                                </button>
                            </div>
                        </div>

                        {/* Bloc Horaires Rectifiés */}
                        {formData.actionType === 'PRESENCE' && (
                            <div className="space-y-3 p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                <div className="flex items-center justify-between">
                                    <p className="text-xs font-bold text-[var(--color-fg)] uppercase tracking-wider">
                                        Horaires à appliquer :
                                    </p>
                                    <button
                                        type="button"
                                        onClick={() => {
                                            const isTMM = selectedAnomalie.idSite === 2;
                                            setFormData(prev => ({
                                                ...prev,
                                                heureEntreeMatin: '08:00',
                                                heureSortieMatin: '12:00',
                                                heureEntreeAm: isTMM ? '14:00' : '13:00',
                                                heureSortieAm: isTMM ? '18:00' : '17:00'
                                            }));
                                        }}
                                        className="text-xs font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] hover:underline flex items-center gap-1.5"
                                    >
                                        <ClockCheck size={14} />
                                        Remplir horaires standard du site
                                    </button>
                                </div>
                                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                    <Input
                                        type="time"
                                        label="Entrée Matin"
                                        value={formData.heureEntreeMatin}
                                        onChange={(e) => setFormData({ ...formData, heureEntreeMatin: e.target.value })}
                                        required
                                    />
                                    <Input
                                        type="time"
                                        label="Sortie Matin"
                                        value={formData.heureSortieMatin}
                                        onChange={(e) => setFormData({ ...formData, heureSortieMatin: e.target.value })}
                                        required
                                    />
                                    <Input
                                        type="time"
                                        label="Retour Pause"
                                        value={formData.heureEntreeAm}
                                        onChange={(e) => setFormData({ ...formData, heureEntreeAm: e.target.value })}
                                        required
                                    />
                                    <Input
                                        type="time"
                                        label="Sortie Soir"
                                        value={formData.heureSortieAm}
                                        onChange={(e) => setFormData({ ...formData, heureSortieAm: e.target.value })}
                                        required
                                    />
                                </div>
                            </div>
                        )}

                        {/* Motif obligatoire */}
                        <div>
                            <Input
                                label="Motif ou Justification de la régularisation *"
                                placeholder="Ex: Oubli de badgeage, Retard justifié par ordre de mission, Panne technique..."
                                value={formData.motifAjustement}
                                onChange={(e) => setFormData({ ...formData, motifAjustement: e.target.value })}
                                required
                            />
                        </div>

                        <div className="pt-4 flex justify-end gap-3 border-t border-[var(--color-border-subtle)]">
                            <Button
                                type="button"
                                variant="ghost"
                                size="md"
                                onClick={() => {
                                    setIsModalOpen(false);
                                    setSelectedAnomalie(null);
                                }}
                            >
                                Annuler
                            </Button>
                            <Button
                                type="submit"
                                variant="primary"
                                size="md"
                                loading={saving}
                            >
                                Valider la régularisation
                            </Button>
                        </div>
                    </form>
                )}
            </Modal>
        </div>
    );
}