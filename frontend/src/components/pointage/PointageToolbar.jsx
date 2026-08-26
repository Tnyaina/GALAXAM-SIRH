import { Search, Save, ClockCheck, Coffee, Timer } from 'lucide-react';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';

export default function PointageToolbar({
    sitesList = [],
    selectedSite,
    onSiteChange,
    selectedDate,
    onDateChange,
    isToday,
    siteParams,
    searchTerm,
    onSearchChange,
    viewMode,
    isReadOnlyForUser,
    saving,
    loading,
    onApplyStandardHours,
    onApplyAutoPauses,
    onSave
}) {
    return (
        <div className="space-y-3">
            {/* 1. Barre de Contexte & Filtres (Site, Date, Recherche) */}
            <div className="bg-[var(--color-surface)] p-4 border border-[var(--color-border-subtle)] flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4 shadow-sm">
                <div className="flex flex-wrap items-center gap-4 w-full md:w-auto">
                    {/* Choix du Site */}
                    <div className="flex items-center gap-2">
                        <label htmlFor="pointage-site-select" className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                            Site :
                        </label>
                        <select
                            id="pointage-site-select"
                            value={selectedSite}
                            onChange={(e) => onSiteChange(Number(e.target.value))}
                            className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-xs font-bold text-[var(--color-fg)] px-3 py-2 outline-none rounded-none focus:border-[var(--color-brand-500)] cursor-pointer"
                        >
                            {sitesList && sitesList.length > 0 ? (
                                sitesList.map((s) => (
                                    <option key={s.idSite} value={s.idSite}>
                                        {s.codeSite ? `${s.codeSite} — ` : ''}{s.libelle || s.nomSite || `Site ${s.idSite}`}
                                    </option>
                                ))
                            ) : (
                                <>
                                    <option value={1}>TNR — Antananarivo Siège</option>
                                    <option value={2}>TMM — Toamasina Agence</option>
                                </>
                            )}
                        </select>
                    </div>

                    {/* Badge type d'horaire du site */}
                    <span className="hidden sm:inline-flex items-center gap-1.5 px-2.5 py-1.5 text-[11px] font-semibold bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                        <Timer size={14} className="text-[var(--color-brand-500)]" />
                        {siteParams?.horaireContinu ? 'Horaire continu (Pause 1h : 12h-13h)' : 'Non continu (Pause 2h : 12h-14h)'}
                    </span>

                    {/* Navigation Temporelle Jour */}
                    <div className="flex items-center gap-2">
                        <PeriodNavigator
                            viewPeriod="jour"
                            selectedDate={selectedDate}
                            onDateChange={onDateChange}
                            allowFutureDates={false}
                        />
                        {isToday && <Badge variant="info">Aujourd'hui</Badge>}
                    </div>

                    <div className="hidden lg:block w-px h-7 bg-[var(--color-border-subtle)]" />

                    {/* Recherche d'employé */}
                    <div className="w-64">
                        <Input
                            placeholder="Rechercher un employé..."
                            icon={<Search size={14} />}
                            value={searchTerm}
                            onChange={(e) => onSearchChange(e.target.value)}
                        />
                    </div>
                </div>

                {/* Bouton Enregistrer Principal (mis en valeur à droite) */}
                {viewMode === 'grille' && (
                    <div className="flex items-center self-end md:self-auto shrink-0">
                        <Button
                            variant={isReadOnlyForUser ? 'outline' : 'primary'}
                            size="md"
                            icon={<Save size={16} />}
                            onClick={onSave}
                            disabled={saving || loading || isReadOnlyForUser}
                            title={isReadOnlyForUser ? "La période est verrouillée ou non modifiable avec votre profil" : "Enregistrer les modifications"}
                        >
                            {saving ? 'Enregistrement...' : isReadOnlyForUser ? 'Pointages verrouillés' : 'Enregistrer les pointages'}
                        </Button>
                    </div>
                )}
            </div>

            {/* 2. Barre d'Actions de Saisie Rapide (Aérée, séparée et bien espacée) */}
            {viewMode === 'grille' && !isReadOnlyForUser && (
                <div className="bg-[var(--color-bg-subtle)]/60 px-4 py-2.5 border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                        <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                            Aide à la saisie :
                        </span>
                        <div className="flex items-center gap-2.5">
                            <Button
                                variant="outline"
                                size="sm"
                                icon={<ClockCheck size={14} className="text-[var(--color-brand-500)]" />}
                                onClick={onApplyStandardHours}
                                title="Remplir automatiquement les horaires normaux du site pour les employés non encore saisis"
                            >
                                Remplir horaires standard
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                icon={<Coffee size={14} className="text-indigo-500" />}
                                onClick={onApplyAutoPauses}
                                title="Déduire et expliciter la pause (1h ou 2h selon le site) pour les pointages ayant une entrée et sortie"
                            >
                                Déduire les pauses
                            </Button>
                        </div>
                    </div>

                    <span className="text-[11px] text-[var(--color-fg-muted)]  hidden md:inline">
                        Astuce : Naviguez d'une cellule à l'autre avec les flèches directionnelles ou Entrée.
                    </span>
                </div>
            )}
        </div>
    );
}
