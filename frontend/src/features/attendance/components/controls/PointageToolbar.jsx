import { Search, ClockCheck, Coffee, Save, CalendarOff } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import Button from '@/components/ui/forms/Button';
import ActionMenu from '@/components/ui/overlays/ActionMenu';

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
            <div className="flex flex-col gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-3 py-2.5 xl:flex-row xl:items-center xl:justify-between xl:gap-4 [&_button]:rounded-none">

                {/* Bloc 1 : Sélection Site, Date & Recherche */}
                <div className="flex w-full min-w-0 flex-wrap items-center gap-2 xl:flex-1">
                    {/* Site */}
                    <div className="min-w-0 shrink-0">
                        <SearchSelect
                            options={siteOptions}
                            value={selectedSite ? String(selectedSite) : ''}
                            onChange={(val) => onSiteChange(val || '')}
                            disabled={isReadOnly}
                            aria-label="Site de pointage"
                            placeholder="Sélectionner un site..."
                            size="sm"
                            buttonClassName="ui-toolbar-control"
                            className="text-xs min-w-[190px]"
                        />
                    </div>

                    {/* Date */}
                    <div className="shrink-0">
                        <PeriodNavigator
                            viewPeriod="jour"
                            selectedDate={selectedDate}
                            onDateChange={(val) => onDateChange(val)}
                            allowFutureDates={false}
                            className="ui-toolbar-control"
                        />
                    </div>

                    {/* Recherche */}
                    <div className="w-full min-w-0 sm:w-52">
                        <Input
                            icon={<Search size={12} className="text-[var(--color-fg-muted)]" />}
                            aria-label="Rechercher un collaborateur par nom ou matricule"
                            placeholder="Nom, matricule…"
                            value={searchTerm}
                            onChange={(e) => onSearchChange(e.target.value)}
                            className="ui-toolbar-control text-xs"
                        />
                    </div>
                </div>

                {/* Bloc 2 : actions secondaires regroupées, enregistrement visible */}
                <div className="flex w-full shrink-0 flex-wrap items-center justify-start gap-2 xl:w-auto xl:justify-end">
                    {(!isReadOnly || onAddConge) && <ActionMenu label="Autres actions de pointage" triggerClassName="ui-toolbar-icon-control" items={[
                        { label: 'Plages du site', section: 'Saisie', icon: <ClockCheck size={14} />, disabled: isReadOnly, onClick: onAutoFillStandard },
                        { label: 'Déduire les pauses', section: 'Saisie', icon: <Coffee size={14} />, disabled: isReadOnly, onClick: onAutoDeducePauses },
                        ...(onAddConge ? [{ label: 'Congé / absence', section: 'Navigation', icon: <CalendarOff size={14} />, onClick: onAddConge }] : []),
                    ]} />}

                    <span aria-live="polite" className="text-[11px] text-[var(--color-fg-muted)] whitespace-nowrap">
                        {dirtyCount > 0 ? `${dirtyCount} modif.` : 'À jour'}
                    </span>

                    {/* Action principale */}
                    <Button
                        type="button"
                        variant={dirtyCount > 0 ? 'primary' : 'secondary'}
                        size="sm"
                        disabled={isReadOnly || saving || dirtyCount === 0}
                        loading={saving}
                        onClick={onSave}
                        className="ui-toolbar-control shrink-0"
                        iconLeft={<Save size={13} />}
                        title={dirtyCount > 0 ? `${dirtyCount} modification(s) non enregistrée(s) — Entrée depuis une heure ou Ctrl+S` : 'Enregistrer les pointages (Entrée depuis une heure ou Ctrl+S)'}
                    >
                        Enregistrer
                    </Button>
                </div>
            </div>
        </div>
    );
}
