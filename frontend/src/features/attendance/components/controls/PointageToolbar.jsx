import { Search, ClockCheck, Coffee, Save, CalendarOff } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import Button from '@/components/ui/forms/Button';

export default function PointageToolbar({
    sites = [],
    selectedSite,
    onSiteChange,
    selectedDate,
    onDateChange,
    searchTerm,
    onSearchChange,
    onAutoFillStandard,
    onAutoDeducePauses,
    onAddConge,
    isReadOnly = false,
    // Props pour Save intégré
    onSave,
    saving = false,
    dirtyCount = 0,
}) {
    const siteOptions = sites
        .filter(s => s.estActif !== false && s.actif !== false || String(s.idSite) === String(selectedSite))
        .map(s => ({
            value: s.idSite,
            label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}`
        }));

    return (
        <div>
            <div className="px-4 py-3 flex flex-col xl:flex-row xl:items-center xl:justify-between gap-2 xl:gap-4 [&_button]:rounded-none">

                {/* Bloc 1 : Sélection Site, Date & Recherche */}
                <div className="flex w-full xl:w-auto xl:flex-1 min-w-0 flex-wrap xl:flex-nowrap items-center gap-2">
                    {/* Site */}
                    <div className="flex items-center gap-1 shrink-0">
                        <SearchSelect
                            options={siteOptions}
                            value={selectedSite ? String(selectedSite) : ''}
                            onChange={(val) => onSiteChange(val || '')}
                            disabled={isReadOnly}
                            aria-label="Site de pointage"
                            placeholder="Sélectionner un site..."
                            size="sm"
                            className="text-xs min-w-[190px]"
                        />
                    </div>

                    <div className="h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                    {/* Date */}
                    <div className="shrink-0">
                        <PeriodNavigator
                            viewPeriod="jour"
                            selectedDate={selectedDate}
                            onDateChange={(val) => onDateChange(val)}
                            allowFutureDates={false}
                        />
                    </div>

                    <span
                        className="hidden xl:inline text-[10px] text-[var(--color-fg-subtle)] whitespace-nowrap"
                        title="Aller au jour précédent ou suivant"
                        aria-label="Raccourci clavier : Commande ou Ctrl avec flèche gauche ou droite pour changer de jour"
                    >
                        ⌘/Ctrl + ←/→
                    </span>

                    <div className="h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                    {/* Recherche */}
                    <div className="w-full shrink-0 sm:w-44">
                        <Input
                            icon={<Search size={12} className="text-[var(--color-fg-muted)]" />}
                            aria-label="Rechercher un collaborateur par nom ou matricule"
                            placeholder="Nom, matricule…"
                            value={searchTerm}
                            onChange={(e) => onSearchChange(e.target.value)}
                            className="h-8 text-xs"
                        />
                    </div>
                </div>

                {/* Bloc 2 : Outils d'automatisation, Congés & Enregistrement */}
                <div className="flex w-full xl:w-auto shrink-0 flex-wrap xl:flex-nowrap items-center justify-start xl:justify-end gap-1.5">
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isReadOnly}
                        onClick={onAutoFillStandard}
                        title="Préremplir les plages matin et après-midi du site aux lignes non saisies"
                        className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
                        iconLeft={<ClockCheck size={13} className="text-[var(--color-success-600)] shrink-0" />}
                    >
                        Plages du site
                    </Button>
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={isReadOnly}
                        onClick={onAutoDeducePauses}
                        title="Déduire automatiquement la pause déjeuner pour les pointages continus"
                        className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
                        iconLeft={<Coffee size={13} className="text-[var(--color-warning-600)] shrink-0" />}
                    >
                        Déduire pauses
                    </Button>
                    {onAddConge && (
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={onAddConge}
                            title="Déclarer un congé ou une absence pour un collaborateur"
                            className="border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/10 text-[var(--color-warning-800)] hover:bg-[var(--color-warning-500)]/20 dark:text-[var(--color-warning-300)]"
                            iconLeft={<CalendarOff size={13} className="shrink-0" />}
                        >
                            Congé / Absence
                        </Button>
                    )}

                    <div className="h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                    {/* Bouton Enregistrer */}
                    <Button
                        type="button"
                        variant={dirtyCount > 0 ? 'primary' : 'secondary'}
                        size="sm"
                        disabled={isReadOnly || saving || dirtyCount === 0}
                        loading={saving}
                        onClick={onSave}
                        className="shrink-0"
                        iconLeft={<Save size={13} />}
                        title={dirtyCount > 0 ? `${dirtyCount} modification(s) non enregistrée(s) — Entrée depuis une heure ou Ctrl+S` : 'Enregistrer les pointages (Entrée depuis une heure ou Ctrl+S)'}
                    >
                        Enregistrer
                    </Button>
                    <span aria-live="polite" className="w-16 text-center text-[10px] text-[var(--color-fg-muted)]">
                        {dirtyCount > 0 ? `${dirtyCount} modif.` : 'À jour'}
                    </span>
                    <span className="hidden xl:inline text-[10px] text-[var(--color-fg-muted)]">Entrée / Ctrl+S</span>
                </div>
            </div>
        </div>
    );
}
