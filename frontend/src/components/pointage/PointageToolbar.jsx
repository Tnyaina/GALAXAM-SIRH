import { Search, ClockCheck, Coffee, Sparkles, MapPin } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';

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
    isReadOnly = false
}) {
    const siteOptions = sites.map(s => ({
        value: s.idSite,
        label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}`
    }));

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-xs">
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-end">
                {/* 1. Contexte : Sélection du Site (4 cols) */}
                <div className="lg:col-span-4 space-y-1">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-1.5">
                        <MapPin size={14} className="text-[var(--color-brand-500)]" />
                        Site d'affectation
                    </label>
                    <Select
                        options={siteOptions}
                        value={selectedSite}
                        onChange={(val) => onSiteChange(val)}
                        disabled={isReadOnly}
                        className="w-full"
                    />
                </div>

                {/* 2. Contexte : Date de pointage avec PeriodNavigator (3 cols) */}
                <div className="lg:col-span-3 space-y-1">
                    <div className="flex items-center justify-between">
                        <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                            Date de pointage
                        </label>
                        <button
                            type="button"
                            onClick={() => onDateChange(new Date().toISOString().split('T')[0])}
                            className="text-[10px] font-bold text-[var(--color-brand-600)] hover:underline cursor-pointer"
                        >
                            Aujourd'hui
                        </button>
                    </div>
                    <PeriodNavigator
                        viewPeriod="jour"
                        selectedDate={selectedDate}
                        onDateChange={(val) => onDateChange(val)}
                        className="w-full"
                    />
                </div>

                {/* 3. Recherche Employé (3 cols) */}
                <div className="lg:col-span-3 space-y-1">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                        Filtrer employé
                    </label>
                    <Input
                        iconLeft={<Search size={16} />}
                        placeholder="Nom, matricule..."
                        value={searchTerm}
                        onChange={(e) => onSearchChange(e.target.value)}
                        className="w-full"
                    />
                </div>

                {/* 4. Actions Rapides d'Aide à la Saisie (2 cols) */}
                <div className="lg:col-span-2 space-y-1">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-1">
                        <Sparkles size={13} className="text-amber-500" />
                        Aide à la saisie
                    </label>
                    <div className="flex gap-1.5">
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={isReadOnly}
                            onClick={onAutoFillStandard}
                            className="flex-1 text-[10px] font-bold py-2 px-1.5 whitespace-nowrap"
                            title="Remplir automatiquement 08h-12h et 13h-17h pour les salariés vides (hors congés et fériés)"
                        >
                            <ClockCheck size={13} className="mr-1 text-emerald-600 shrink-0" />
                            08h-17h
                        </Button>
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            disabled={isReadOnly}
                            onClick={onAutoDeducePauses}
                            className="flex-1 text-[10px] font-bold py-2 px-1.5 whitespace-nowrap"
                            title="Déduire automatiquement la pause déjeuner pour les salariés ayant uniquement Entrée Matin et Sortie Soir"
                        >
                            <Coffee size={13} className="mr-1 text-amber-600 shrink-0" />
                            Pauses
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
