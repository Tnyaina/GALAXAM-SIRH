import { useCallback, useEffect, useMemo, useState } from 'react';
import { Edit3, UserCheck } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Pagination from '@/components/ui/navigation/Pagination';
import Loading from '@/components/ui/feedback/Loading';
import FormError from '@/components/ui/feedback/FormError';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { filterPointageDays } from '@/features/attendance/utils/pointageDayFilter';
import { getAttendanceStatus } from '@/features/attendance/utils/attendanceStatus';
import { cleanPointageTime } from '@/features/attendance/utils/pointageRow';
import { fmtDate } from '@/utils/dateUtils';
import AnomalieRegularisationModal from './AnomalieRegularisationModal';

const PAGE_SIZE = 10;
const FILTERS = [['ALL', 'Tous les jours'], ['EXCEPTIONS', 'Exceptions'], ['ANOMALIES', 'Anomalies']];

/** Le contrôle DAF choisit une journée ; la correction utilise le parcours commun de régularisation. */
export default function DafAjustementPointageModal({
    isOpen, onClose, employe = null, idEmploye: propIdEmploye,
    employeNom: propEmployeNom, matricule: propMatricule,
    idPeriode: propIdPeriode, idPeriodePaie: propIdPeriodePaie,
    initialDate = null, onSuccess
}) {
    const idEmploye = employe?.idEmploye || propIdEmploye;
    const idPeriode = propIdPeriode || propIdPeriodePaie || employe?.idPeriodePaie;
    const nom = employe ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() : (propEmployeNom || 'Salarié');
    const matricule = employe?.matricule || propMatricule || '';
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [jours, setJours] = useState([]);
    const [canCorrect, setCanCorrect] = useState(false);
    const [selectedDay, setSelectedDay] = useState(null);
    const [filter, setFilter] = useState('ALL');
    const [page, setPage] = useState(1);

    const loadDays = useCallback(async () => {
        if (!idEmploye || !idPeriode) return;
        setLoading(true);
        setError('');
        try {
            const sheet = await attendanceService.getFicheEmploye(idEmploye, { period: 'periode', idPeriode });
            const items = sheet?.pointagesData || [];
            setJours(items);
            setCanCorrect(sheet?.availableActions?.CORRECT_DAY === true);
            if (initialDate) {
                const target = items.find(day => (day.dateComplete || day.datePointage) === initialDate);
                if (target && !target.congeApprouve) setSelectedDay(target);
            }
        } catch {
            setError('Impossible de charger les journées de cette période.');
        } finally {
            setLoading(false);
        }
    }, [idEmploye, idPeriode, initialDate]);

    useEffect(() => {
        if (!isOpen) return;
        loadDays();
    }, [isOpen, loadDays]);

    const filtered = useMemo(() => filterPointageDays(jours, filter), [jours, filter]);
    const visible = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

    const closeDay = () => {
        setSelectedDay(null);
        if (initialDate) onClose();
    };

    const saveCorrection = async (payload) => {
        try {
            const result = await attendanceService.regulariser(selectedDay?.idPointage || 0, payload);
            if (result?.success === false) return { error: result.message || 'Régularisation refusée.' };
            setSelectedDay(null);
            await loadDays();
            if (onSuccess) await onSuccess();
            return { success: true };
        } catch (cause) {
            return { error: cause?.response?.data?.message || cause?.message || 'Régularisation impossible.' };
        }
    };

    if (selectedDay) {
        const datePointage = selectedDay.dateComplete || selectedDay.datePointage;
        const minutesRetard = Number(selectedDay.minutesRetard || 0);
        return <AnomalieRegularisationModal
            isOpen={isOpen}
            directDayFlow
            onClose={closeDay}
            onSuccess={saveCorrection}
            anomalie={{
                idPointage: selectedDay.idPointage || 0,
                idEmploye, idPeriodePaie: idPeriode,
                idSite: selectedDay.idSite || employe?.idSite,
                datePointage, version: selectedDay.version,
                nom, prenom: '', matricule,
                siteNom: selectedDay.nomSite || employe?.nomSite || '',
                statutCode: selectedDay.statut,
                statutLibelle: getAttendanceStatus(selectedDay.statut).label,
                typeAnomalie: minutesRetard > 0 ? 'RETARD' : undefined,
                minutesRetard,
                heureEntreeMatin: cleanPointageTime(selectedDay.entree || selectedDay.heureEntreeMatin),
                heureSortieMatin: cleanPointageTime(selectedDay.sortiePause || selectedDay.heureSortieMatin),
                heureEntreeAm: cleanPointageTime(selectedDay.retourPause || selectedDay.heureEntreeAm),
                heureSortieAm: cleanPointageTime(selectedDay.sortie || selectedDay.heureSortieAm),
                horaireContinuJour: selectedDay.horaireContinuJour,
                motifAjustement: selectedDay.observation || ''
            }}
        />;
    }

    return <Modal isOpen={isOpen} onClose={onClose}
        title={`Ajustement du pointage — ${nom} (${matricule})`}
        icon={<UserCheck className="h-5 w-5 text-[var(--color-brand-500)]" />}
        size="4xl">
        <div className="space-y-3">
            {error && <FormError message={error} />}
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-fg-muted)]">
                <span>Choisissez une journée à régulariser : heures, retard réel, congé ou absence.</span>
                <span>{filtered.length} / {jours.length} journées</span>
            </div>
            <div className="flex flex-wrap gap-1" role="group" aria-label="Filtrer les journées à rectifier">
                {FILTERS.map(([value, label]) => <Button key={value} type="button" size="xs"
                    variant={filter === value ? 'primary' : 'outline'} aria-pressed={filter === value}
                    onClick={() => { setFilter(value); setPage(1); }}>{label}</Button>)}
            </div>
            {loading ? <Loading size="md" label="Chargement des journées…" /> : <>
                <div className="overflow-x-auto border border-[var(--color-border-subtle)]">
                    <Table bordered={false}>
                        <Thead><Tr><Th>Date</Th><Th>Statut</Th><Th>Horaires</Th><Th>Heures</Th><Th>Retard</Th><Th>Indemnités</Th><Th align="right">Action</Th></Tr></Thead>
                        <Tbody>
                            {visible.length === 0 && <Tr><Td colSpan={7} className="py-8 text-center">Aucune journée pour ce filtre.</Td></Tr>}
                            {visible.map(day => {
                                const date = day.dateComplete || day.datePointage;
                                const status = getAttendanceStatus(day.statut);
                                const allowances = Object.values(day.indemnites || {}).filter(item => item?.accordee && Number(item.montant) > 0);
                                return <Tr key={date}>
                                    <Td className="font-data">{fmtDate(date)}</Td>
                                    <Td><Badge variant={status.variant} size="xs">{day.congeApprouve ? 'Congé validé' : status.label}</Badge></Td>
                                    <Td className="font-data text-xs">{day.entree || '—'} – {day.sortie || '—'}</Td>
                                    <Td className="font-data">{day.heures || '—'}</Td>
                                    <Td className="font-data">{Number(day.minutesRetard) > 0 ? `${day.minutesRetard} min` : '—'}</Td>
                                    <Td className="text-xs">{allowances.length ? allowances.map(item => item.code).join(', ') : '—'}</Td>
                                    <Td align="right"><Button type="button" variant="outline" size="xs"
                                        iconLeft={<Edit3 size={12} />}
                                        disabled={!canCorrect || !day.canCorrect || day.congeApprouve}
                                        onClick={() => setSelectedDay(day)}
                                        aria-label={`Rectifier la journée du ${fmtDate(date)}`}>
                                        Rectifier
                                    </Button></Td>
                                </Tr>;
                            })}
                        </Tbody>
                    </Table>
                </div>
                {filtered.length > PAGE_SIZE && <Pagination currentPage={page} totalItems={filtered.length}
                    pageSize={PAGE_SIZE} onPageChange={setPage} />}
            </>}
            <p className="text-xs text-[var(--color-fg-muted)]">Les congés déjà validés se modifient dans Congés & Absences.</p>
        </div>
    </Modal>;
}
