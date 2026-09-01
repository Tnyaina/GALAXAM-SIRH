import { useState, useEffect } from 'react';
import { AlertTriangle, Clock, Search, CheckCircle, ShieldAlert, Wrench } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import DatePicker from '@/components/ui/forms/DatePicker';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Modal from '@/components/ui/overlays/Modal';
import { useToast } from '@/components/ui/feedback/Toast';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import SEO from '@/components/system/SEO';
import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { getFullPhotoUrl } from '@/utils/fileUtils';
import usePointageAnomalies from '@/hooks/pointage/usePointageAnomalies';

export default function AnomaliesPointagePage() {
    const { toast } = useToast();
    const [sites, setSites] = useState([]);
    const [periodes, setPeriodes] = useState([]);

    const {
        anomalies,
        stats,
        filters,
        loading,
        updateFilters,
        regulariser
    } = usePointageAnomalies();

    useEffect(() => {
        const fetchInitialData = async () => {
            try {
                const [resSites, resPeriodes] = await Promise.all([
                    organisationService.getSites(),
                    periodePaieService.getAllPeriodes()
                ]);
                if (resSites) setSites(resSites);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    updateFilters({ idPeriodePaie: resPeriodes[0].idPeriodePaie });
                }
            } catch (err) {
                console.error('Erreur chargement données initiales:', err);
            }
        };
        fetchInitialData();
    }, []);

    const [selectedAnomalie, setSelectedAnomalie] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [saving, setSaving] = useState(false);

    const [formData, setFormData] = useState({
        statutCode: 'PRESENT',
        heureEntreeMatin: '08:00',
        heureSortieMatin: '12:00',
        heureEntreeAm: '13:00',
        heureSortieAm: '17:00',
        deplacementMatin: true,
        deplacementAm: true,
        indemniteRepasRecue: true,
        motifAjustement: ''
    });

    const openRegularisation = (anom) => {
        setSelectedAnomalie(anom);
        const isAbsent = anom.typeAnomalie === 'ABSENCE' || anom.statutCode === 'ABSENCE_NON_JUSTIFIEE' || anom.statutCode === 'NON_SAISI';
        setFormData({
            statutCode: isAbsent ? 'PRESENT' : (anom.statutCode || 'PRESENT'),
            heureEntreeMatin: anom.heureEntreeMatin || '08:00',
            heureSortieMatin: anom.heureSortieMatin || '12:00',
            heureEntreeAm: anom.heureEntreeAm || '13:00',
            heureSortieAm: anom.heureSortieAm || '17:00',
            deplacementMatin: anom.deplacementMatin !== false,
            deplacementAm: anom.deplacementAm !== false,
            indemniteRepasRecue: anom.indemniteRepasRecue !== false,
            motifAjustement: anom.motifAjustement || ''
        });
        setIsModalOpen(true);
    };

    const handleSaveRegularisation = async (e) => {
        e.preventDefault();
        if (!formData.motifAjustement.trim()) {
            toast({
                title: 'Motif obligatoire',
                message: 'Veuillez préciser le motif de la régularisation pour l\'audit RH.',
                type: 'warning'
            });
            return;
        }

        setSaving(true);
        try {
            const payload = {
                ...formData,
                idEmploye: selectedAnomalie.idEmploye,
                datePointage: selectedAnomalie.datePointage,
                idPeriodePaie: selectedAnomalie.idPeriodePaie,
                idSite: selectedAnomalie.idSite
            };
            const ok = await regulariser(selectedAnomalie.idPointage || 0, payload);
            if (ok) {
                toast({
                    title: 'Régularisation effectuée',
                    message: `Le pointage de ${selectedAnomalie.nom} ${selectedAnomalie.prenom} a été régularisé.`,
                    type: 'success'
                });
                setIsModalOpen(false);
            }
        } finally {
            setSaving(false);
        }
    };

    const siteOptions = [
        { value: 0, label: 'Tous les sites' },
        ...sites.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}` }))
    ];

    const periodeOptions = periodes.map(p => ({
        value: p.idPeriodePaie,
        label: `${p.code || p.libelle} (${p.dateDebut} au ${p.dateFin})`
    }));

    return (
        <>
            <SEO
                title="Gestion des Anomalies de Pointage | GALAXAM SIRH"
                description="Suivi et régularisation des anomalies, retards et absences de pointage."
            />

            <div className="space-y-3.5 max-w-[1600px] mx-auto pb-12">
                <PageHeader
                    title="Anomalies & Régularisations de Pointage"
                    subtitle="Détection des Saisies incomplets, retards non justifiés et absences"
                    breadcrumbs={[
                        { label: 'Accueil', href: '/' },
                        { label: 'Pointages', href: '/pointages/saisie' },
                        { label: 'Anomalies' }
                    ]}
                />

                {/* Barre de navigation guidée */}
                <PointageWorkflowNav />

                {/* 1. Cartes de Statistiques */}
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-3.5 shadow-xs flex items-center justify-between">
                        <div>
                            <p className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Total Anomalies</p>
                            <p className="text-xl font-bold font-data text-[var(--color-fg)] mt-0.5">{stats.total}</p>
                        </div>
                        <div className="w-9 h-9 bg-amber-500/10 text-amber-600 flex items-center justify-center shrink-0">
                            <AlertTriangle size={18} />
                        </div>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-3.5 shadow-xs flex items-center justify-between">
                        <div>
                            <p className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Pointages Incomplets</p>
                            <p className="text-xl font-bold font-data text-rose-600 mt-0.5">{stats.incomplets}</p>
                        </div>
                        <div className="w-9 h-9 bg-rose-500/10 text-rose-600 flex items-center justify-center shrink-0">
                            <ShieldAlert size={18} />
                        </div>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-3.5 shadow-xs flex items-center justify-between">
                        <div>
                            <p className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Retards Non Justifiés</p>
                            <p className="text-xl font-bold font-data text-amber-600 mt-0.5">{stats.retards}</p>
                        </div>
                        <div className="w-9 h-9 bg-amber-500/10 text-amber-600 flex items-center justify-center shrink-0">
                            <Clock size={18} />
                        </div>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-3.5 shadow-xs flex items-center justify-between">
                        <div>
                            <p className="text-[10px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">Absences & Non Saisis</p>
                            <p className="text-xl font-bold font-data text-red-700 mt-0.5">{stats.absences}</p>
                        </div>
                        <div className="w-9 h-9 bg-red-500/10 text-red-700 flex items-center justify-center shrink-0">
                            <AlertTriangle size={18} />
                        </div>
                    </div>
                </div>

                {/* 2. Barre de Filtres */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-3.5 shadow-xs">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 items-end">
                        <div>
                            <Select
                                label="Période de paie"
                                options={periodeOptions}
                                value={filters.idPeriodePaie}
                                onChange={(val) => updateFilters({ idPeriodePaie: val })}
                                className="w-full"
                            />
                        </div>

                        <div>
                            <Select
                                label="Site d'affectation"
                                options={siteOptions}
                                value={filters.idSite}
                                onChange={(val) => updateFilters({ idSite: val })}
                                className="w-full"
                            />
                        </div>

                        <div>
                            <DatePicker
                                label="Date spécifique (optionnel)"
                                value={filters.datePointage}
                                onChange={(val) => updateFilters({ datePointage: val })}
                                className="w-full"
                            />
                        </div>

                        <div>
                            <Input
                                label="Rechercher employé"
                                iconLeft={<Search size={16} />}
                                placeholder="Rechercher par nom, matricule..."
                                value={filters.search}
                                onChange={(e) => updateFilters({ search: e.target.value })}
                                className="w-full"
                            />
                        </div>
                    </div>
                </div>

                {/* 3. Tableau des Anomalies */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-x-auto">
                    <Table className="w-full text-left border-collapse">
                        <Thead>
                            <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                <Th className="py-3 px-4">Date</Th>
                                <Th className="py-3 px-4">Employé</Th>
                                <Th className="py-3 px-4">Site</Th>
                                <Th className="py-3 px-2 text-center">Entrée M.</Th>
                                <Th className="py-3 px-2 text-center">Sortie M.</Th>
                                <Th className="py-3 px-2 text-center">Retour P.</Th>
                                <Th className="py-3 px-2 text-center">Sortie S.</Th>
                                <Th className="py-3 px-3 text-center">Retard</Th>
                                <Th className="py-3 px-3 text-center">Statut</Th>
                                <Th className="py-3 px-4 text-center">Action</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {loading ? (
                                <Tr>
                                    <Td colSpan={10} className="text-center py-12 text-xs font-bold text-[var(--color-fg-muted)]">
                                        Chargement des anomalies...
                                    </Td>
                                </Tr>
                            ) : anomalies.length === 0 ? (
                                <Tr>
                                    <Td colSpan={10} className="text-center py-12 text-xs font-bold text-emerald-600">
                                        <CheckCircle size={24} className="mx-auto mb-2 text-emerald-500" />
                                        Aucune anomalie de pointage détectée pour cette sélection.
                                    </Td>
                                </Tr>
                            ) : (
                                anomalies.map((anom) => (
                                    <Tr key={`${anom.idEmploye}-${anom.datePointage}`} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/50">
                                        <Td className="py-3 px-4 text-xs font-mono font-bold text-[var(--color-fg)]">
                                            {anom.datePointage}
                                        </Td>
                                        <Td className="py-3 px-4">
                                            <div className="flex items-center gap-3">
                                                <Avatar
                                                    src={anom.photoUrl ? getFullPhotoUrl(anom.photoUrl) : null}
                                                    name={`${anom.prenom || ''} ${anom.nom || ''}`}
                                                    size="sm"
                                                    shape="square"
                                                />
                                                <div className="min-w-0">
                                                    <p className="text-xs font-bold text-[var(--color-fg)]">{anom.nom} {anom.prenom}</p>
                                                    <p className="text-[11px] font-mono text-[var(--color-fg-muted)]">{anom.matricule}</p>
                                                </div>
                                            </div>
                                        </Td>
                                        <Td className="py-3 px-4 text-xs text-[var(--color-fg-muted)]">
                                            {anom.siteNom || '—'}
                                        </Td>
                                        <Td className="py-3 px-2 text-center font-mono text-xs">{anom.heureEntreeMatin || '--:--'}</Td>
                                        <Td className="py-3 px-2 text-center font-mono text-xs">{anom.heureSortieMatin || '--:--'}</Td>
                                        <Td className="py-3 px-2 text-center font-mono text-xs">{anom.heureEntreeAm || '--:--'}</Td>
                                        <Td className="py-3 px-2 text-center font-mono text-xs">{anom.heureSortieAm || '--:--'}</Td>
                                        <Td className="py-3 px-3 text-center font-mono text-xs">
                                            {anom.minutesRetard > 0 ? (
                                                <span className="text-amber-600 font-bold">+{anom.minutesRetard} min</span>
                                            ) : (
                                                <span className="text-[var(--color-fg-muted)]">00 min</span>
                                            )}
                                        </Td>
                                        <Td className="py-3 px-3 text-center">
                                            <Badge
                                                variant={
                                                    anom.statutCode === 'ABSENCE_NON_JUSTIFIEE' || anom.typeAnomalie === 'ABSENCE'
                                                        ? 'error'
                                                        : anom.statutCode === 'ANOMALIE_SAISIE'
                                                        ? 'warning'
                                                        : 'info'
                                                }
                                                size="sm"
                                                className="font-bold"
                                            >
                                                {anom.statutLibelle || anom.statutCode}
                                            </Badge>
                                        </Td>
                                        <Td className="py-3 px-4 text-center">
                                            <Button
                                                type="button"
                                                variant="outline"
                                                size="sm"
                                                onClick={() => openRegularisation(anom)}
                                                className="text-xs font-bold flex items-center gap-1.5 mx-auto"
                                            >
                                                <Wrench size={13} />
                                                Régulariser
                                            </Button>
                                        </Td>
                                    </Tr>
                                ))
                            )}
                        </Tbody>
                    </Table>
                </div>
            </div>

            {/* Modale de Régularisation avec Horaires ET Indemnités */}
            {isModalOpen && selectedAnomalie && (
                <Modal
                    isOpen={isModalOpen}
                    onClose={() => setIsModalOpen(false)}
                    title={`Régulariser le pointage du ${selectedAnomalie.datePointage}`}
                    size="xl"
                >
                    <form onSubmit={handleSaveRegularisation} className="space-y-4 pt-2">
                        <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            <p className="text-xs font-bold text-[var(--color-fg)]">
                                Salarié : <span className="font-semibold">{selectedAnomalie.nom} {selectedAnomalie.prenom}</span> <span className="font-mono text-[var(--color-fg-muted)]">({selectedAnomalie.matricule})</span>
                            </p>
                        </div>

                        {/* 0. Statut de régularisation */}
                        <div className="space-y-1.5">
                            <label className="text-xs font-bold text-[var(--color-fg)] uppercase">Statut régularisé :</label>
                            <Select
                                value={formData.statutCode}
                                onChange={(e) => setFormData({ ...formData, statutCode: e.target.value })}
                                options={[
                                    { value: 'PRESENT', label: 'Présent (heures réelles travaillées)' },
                                    { value: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée (justificatif RH / maladie)' },
                                    { value: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée (décomptée du salaire)' },
                                    { value: 'CONGE', label: 'Congé exceptionnel régularisé' }
                                ]}
                            />
                        </div>

                        {/* 1. Horaires corrigés (si présent) */}
                        {formData.statutCode === 'PRESENT' && (
                            <>
                                <div className="space-y-2">
                                    <label className="text-xs font-bold text-[var(--color-fg)] uppercase">Horaires corrigés :</label>
                                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                        <Input
                                            type="time"
                                            label="Entrée M."
                                            value={formData.heureEntreeMatin}
                                            onChange={(e) => setFormData({ ...formData, heureEntreeMatin: e.target.value })}
                                        />
                                        <Input
                                            type="time"
                                            label="Sortie M."
                                            value={formData.heureSortieMatin}
                                            onChange={(e) => setFormData({ ...formData, heureSortieMatin: e.target.value })}
                                        />
                                        <Input
                                            type="time"
                                            label="Retour P."
                                            value={formData.heureEntreeAm}
                                            onChange={(e) => setFormData({ ...formData, heureEntreeAm: e.target.value })}
                                        />
                                        <Input
                                            type="time"
                                            label="Sortie S."
                                            value={formData.heureSortieAm}
                                            onChange={(e) => setFormData({ ...formData, heureSortieAm: e.target.value })}
                                        />
                                    </div>
                                </div>

                                {/* 2. Indemnités accordées ce jour */}
                                <div className="space-y-2 p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                                    <label className="text-xs font-bold text-[var(--color-fg)] uppercase block">Indemnités à appliquer :</label>
                                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                        <label className="flex items-center gap-2 text-xs font-bold cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={formData.deplacementMatin}
                                                onChange={(e) => setFormData({ ...formData, deplacementMatin: e.target.checked })}
                                                className="w-4 h-4 accent-[var(--color-brand-500)]"
                                            />
                                            <span>Trajet Matin</span>
                                        </label>
                                        <label className="flex items-center gap-2 text-xs font-bold cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={formData.deplacementAm}
                                                onChange={(e) => setFormData({ ...formData, deplacementAm: e.target.checked })}
                                                className="w-4 h-4 accent-[var(--color-brand-500)]"
                                            />
                                            <span>Trajet Soir</span>
                                        </label>
                                        <label className="flex items-center gap-2 text-xs font-bold cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={formData.indemniteRepasRecue}
                                                onChange={(e) => setFormData({ ...formData, indemniteRepasRecue: e.target.checked })}
                                                className="w-4 h-4 accent-[var(--color-brand-500)]"
                                            />
                                            <span>Repas pris sur site</span>
                                        </label>
                                    </div>
                                </div>
                            </>
                        )}

                        {/* 3. Motif Obligatoire */}
                        <div>
                            <Input
                                label="Motif de régularisation *"
                                placeholder="Ex: Oubli de badgeage, mission extérieure, justificatif médical..."
                                value={formData.motifAjustement}
                                onChange={(e) => setFormData({ ...formData, motifAjustement: e.target.value })}
                                required
                            />
                        </div>

                        <div className="flex justify-end gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
                            <Button type="button" variant="ghost" onClick={() => setIsModalOpen(false)}>
                                Annuler
                            </Button>
                            <Button type="submit" variant="primary" loading={saving}>
                                Enregistrer la régularisation
                            </Button>
                        </div>
                    </form>
                </Modal>
            )}
        </>
    );
}
