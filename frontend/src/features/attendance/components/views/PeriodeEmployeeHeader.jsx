import {
    ChevronLeft,
    ChevronRight,
    ClockCheck,
    Coffee,
    Save,
    Download,
    Upload
} from 'lucide-react';
import Card from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { formatHeuresDecimal } from '@/features/attendance/utils/attendanceDisplayUtils';
import { isAttendanceUnentered, isAttendanceUnjustifiedAbsence } from '@/features/attendance/utils/attendanceStatus';

export default function PeriodeEmployeeHeader({
    ficheData,
    isAutonome,
    canEditPeriod,
    dirtyCount = 0,
    saving = false,
    employesList = [],
    currentEmpIndex = -1,
    selectedEmployeId,
    kpis,
    filterJour = 'ALL',
    setFilterJour,
    grilleRows = [],
    importLoading = false,
    onNavigateEmploye,
    onSelectEmploye,
    onBackToList,
    onDownloadTemplate,
    onImportClick,
    onApplyStandard,
    onDeducePauses,
    onSaveBatch
}) {
    const missingCount = grilleRows.filter(r => !r.weekend && !r.ferie && !r.conge && (!r.heureEntreeMatin || isAttendanceUnentered(r))).length;
    const anomalyCount = grilleRows.filter(r => r.minutesRetard > 0 || isAttendanceUnjustifiedAbsence(r)).length;

    return (
        <Card padding={false} className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] overflow-hidden shadow-xs">
            {/* 1. Profil Collaborateur & Navigation feuille-à-feuille */}
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
                    <Button
                        variant="secondary"
                        size="sm"
                        onClick={onBackToList}
                        title="Changer de collaborateur"
                    >
                        Liste
                    </Button>

                    {/* Navigation Précédent / Suivant */}
                    <div className="flex items-center border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xs">
                        <Button
                            variant="ghost"
                            size="xs"
                            iconOnly
                            icon={<ChevronLeft size={16} />}
                            onClick={() => onNavigateEmploye(-1)}
                            disabled={currentEmpIndex <= 0}
                            title="Collaborateur précédent (Alt+←)"
                            aria-label="Collaborateur précédent"
                            className="rounded-none border-0"
                        />
                        <div className="px-3 py-1 text-xs font-medium border-x border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                            {employesList.length > 0 ? (
                                <span>
                                    <strong className="text-[var(--color-fg)]">{currentEmpIndex + 1}</strong> sur {employesList.length}
                                </span>
                            ) : (
                                <span>1 sur 1</span>
                            )}
                        </div>
                        <Button
                            variant="ghost"
                            size="xs"
                            iconOnly
                            icon={<ChevronRight size={16} />}
                            onClick={() => onNavigateEmploye(1)}
                            disabled={currentEmpIndex < 0 || currentEmpIndex >= employesList.length - 1}
                            title="Collaborateur suivant (Alt+→)"
                            aria-label="Collaborateur suivant"
                            className="rounded-none border-0"
                        />
                    </div>

                    {/* Sélecteur direct de collaborateur */}
                    <SearchSelect
                        options={employesList.map(e => ({
                            value: e.idEmploye,
                            label: `${e.nom} ${e.prenom || ''} (${e.matricule})`
                        }))}
                        value={selectedEmployeId ? String(selectedEmployeId) : ''}
                        onChange={(val) => {
                            if (val) onSelectEmploye(Number(val));
                        }}
                        placeholder="Rechercher un employé…"
                        size="sm"
                        className="min-w-[220px] text-xs"
                    />
                </div>
            </div>

            {/* 2. Bandeau KPIs Synthèse du Mois */}
            {kpis && (
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
            )}

            {/* 3. Barre d'outils Assistants & Filtres */}
            <div className="p-2.5 flex flex-wrap items-center justify-between gap-2.5 bg-[var(--color-surface)]">
                {/* Filtres d'état */}
                <div className="flex items-center gap-1">
                    {[
                        { key: 'ALL', label: 'Tous', count: grilleRows.length },
                        { key: 'MISSING', label: 'À saisir', count: missingCount },
                        { key: 'ANOMALY', label: 'Anomalies', count: anomalyCount },
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
                    <Button
                        variant="secondary"
                        size="sm"
                        iconLeft={<Download size={13} />}
                        onClick={onDownloadTemplate}
                        disabled={!canEditPeriod || importLoading}
                        title="Télécharger un tableau Excel pour ce collaborateur et cette période"
                    >
                        Modèle Excel
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        iconLeft={<Upload size={13} />}
                        onClick={onImportClick}
                        disabled={!canEditPeriod || importLoading || dirtyCount > 0}
                        loading={importLoading}
                        title={dirtyCount > 0 ? "Enregistrez d'abord vos modifications en cours" : "Prévisualiser un tableau Excel"}
                        className="border-[var(--color-brand-600)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] hover:bg-[var(--color-brand-500)]/10"
                    >
                        Importer
                    </Button>
                    <Button
                        variant="secondary"
                        size="sm"
                        iconLeft={<ClockCheck size={13} className="text-[var(--color-success-600)] shrink-0" />}
                        onClick={onApplyStandard}
                        disabled={!canEditPeriod}
                        title="Remplir les horaires standard du site sur les jours ouvrés vides"
                    >
                        Horaire standard
                    </Button>
                    <Button
                        variant="secondary"
                        size="sm"
                        iconLeft={<Coffee size={13} className="text-[var(--color-warning-600)] shrink-0" />}
                        onClick={onDeducePauses}
                        disabled={!canEditPeriod}
                        title="Insérer automatiquement la coupure déjeuner sur les journées continues"
                    >
                        Déduire pauses
                    </Button>

                    <span className="h-5 w-px bg-[var(--color-border-subtle)]" />

                    {/* Bouton Enregistrer avec dirtyCount */}
                    <Button
                        variant="primary"
                        size="sm"
                        iconLeft={<Save size={13} />}
                        onClick={onSaveBatch}
                        disabled={dirtyCount === 0 || saving || !canEditPeriod}
                        loading={saving}
                        className={dirtyCount > 0 ? 'bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)]' : ''}
                    >
                        <span>{saving ? 'Enregistrement…' : dirtyCount > 0 ? 'Enregistrer' : 'À jour'}</span>
                        {dirtyCount > 0 && (
                            <span className="bg-[var(--color-surface)]/25 text-[var(--color-on-color)] text-[10px] font-mono px-1.5 py-0.2">
                                {dirtyCount}
                            </span>
                        )}
                        <span className="hidden xl:inline text-[10px] opacity-75 font-normal">(Ctrl+S)</span>
                    </Button>
                </div>
            </div>
        </Card>
    );
}
