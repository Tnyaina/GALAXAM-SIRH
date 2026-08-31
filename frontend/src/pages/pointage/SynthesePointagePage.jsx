import { useState, useMemo, useEffect } from 'react';
import { Search, Clock, Layers, CalendarRange, CheckCircle2, AlertTriangle, ArrowRight } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import SEO from '@/components/system/SEO';
import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import usePointageSynthese from '@/hooks/pointage/usePointageSynthese';

export default function SynthesePointagePage() {
    const [sites, setSites] = useState([]);
    const [periodes, setPeriodes] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [activeTab, setActiveTab] = useState('hebdo'); // 'hebdo' ou 'mensuel'

    const [semaines, setSemaines] = useState([]);
    const [loadingSemaines, setLoadingSemaines] = useState(false);

    useEffect(() => {
        const fetchSites = async () => {
            try {
                const res = await organisationService.getSites();
                if (res) setSites(res);
            } catch (err) {
                console.error('Erreur chargement sites:', err);
            }
        };
        fetchSites();
    }, []);

    const {
        selectedPeriode,
        setSelectedPeriode,
        selectedSite,
        setSelectedSite,
        syntheseData,
        loading,
        error
    } = usePointageSynthese(1, 0);

    useEffect(() => {
        const fetchPeriodes = async () => {
            try {
                const list = await periodePaieService.getAllPeriodes();
                if (list && list.length > 0) {
                    setPeriodes(list);
                    setSelectedPeriode(list[0].idPeriodePaie);
                }
            } catch (err) {
                console.error('Erreur chargement périodes:', err);
            }
        };
        fetchPeriodes();
    }, [setSelectedPeriode]);

    // Charger les semaines backend pour la période sélectionnée
    useEffect(() => {
        if (!selectedPeriode) return;
        const fetchSemaines = async () => {
            setLoadingSemaines(true);
            try {
                const res = await pointageSyntheseService.getSemaines({
                    idPeriodePaie: selectedPeriode,
                    idSite: selectedSite || undefined
                });
                if (res?.semaines) {
                    setSemaines(res.semaines);
                }
            } catch (err) {
                console.error('Erreur chargement semaines:', err);
            } finally {
                setLoadingSemaines(false);
            }
        };
        fetchSemaines();
    }, [selectedPeriode, selectedSite]);

    const periodeOptions = periodes.map(p => ({
        value: p.idPeriodePaie,
        label: `${p.libelle || p.code} (${p.statutLibelle || p.statutCode})`
    }));

    const siteOptions = [
        { value: 0, label: 'Tous les sites' },
        ...sites.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}` }))
    ];

    const filteredData = useMemo(() => {
        if (!searchTerm.trim()) return syntheseData;
        const term = searchTerm.toLowerCase().trim();
        return syntheseData.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(term)) ||
            (r.prenom && r.prenom.toLowerCase().includes(term)) ||
            (r.matricule && r.matricule.toLowerCase().includes(term))
        );
    }, [syntheseData, searchTerm]);

    const fmtMoney = (val) => {
        if (!val || isNaN(val)) return '0 Ar';
        return new Intl.NumberFormat('fr-FR').format(Math.round(val)) + ' Ar';
    };

    return (
        <>
            <SEO
                title="Synthèse des Pointages & Compensations | GALAXAM SIRH"
                description="Consolidation hebdomadaire et mensuelle des heures travaillées, retards et indemnités pour la paie."
            />

            <div className="space-y-5 max-w-[1600px] mx-auto pb-16">
                <PageHeader
                    title="Synthèse & Compensations des Pointages"
                    subtitle="Consolidez les semaines de travail, compensez les retards et préparez la clôture paie."
                    breadcrumbs={[
                        { label: 'Accueil', href: '/' },
                        { label: 'Pointages', href: '/pointages/saisie' },
                        { label: 'Synthèse' }
                    ]}
                />

                {/* Workflow Nav */}
                <PointageWorkflowNav />

                {/* Filtres de Période et Site */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-xs">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
                        <div>
                            <Select
                                label="Période de paie"
                                options={periodeOptions}
                                value={selectedPeriode}
                                onChange={(val) => setSelectedPeriode(val)}
                                className="w-full h-[38px]"
                            />
                        </div>

                        <div>
                            <Select
                                label="Site d'affectation"
                                options={siteOptions}
                                value={selectedSite}
                                onChange={(val) => setSelectedSite(val)}
                                className="w-full h-[38px]"
                            />
                        </div>

                        <div>
                            <Input
                                label="Filtrer employé"
                                iconLeft={<Search size={15} />}
                                placeholder="Rechercher par nom, matricule…"
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="w-full h-[38px]"
                            />
                        </div>
                    </div>
                </div>

                {/* Sélecteur d'Onglets Guidés : Hebdomadaire vs Mensuel */}
                <div className="flex border-b border-[var(--color-border-subtle)] gap-2 bg-[var(--color-bg-subtle)] p-1.5 border border-[var(--color-border-subtle)]">
                    <button
                        type="button"
                        onClick={() => setActiveTab('hebdo')}
                        className={`flex items-center gap-2 px-5 py-2.5 text-xs font-bold transition-all cursor-pointer ${
                            activeTab === 'hebdo'
                                ? 'bg-[var(--color-surface)] text-brand-700 dark:text-brand-300 shadow-xs border border-[var(--color-border-subtle)]'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <Layers size={15} />
                        <span>1. Synthèse Hebdomadaire (Par Semaine)</span>
                        <span className="bg-brand-500/10 text-brand-700 px-1.5 py-0.5 text-[10px] font-mono">
                            {semaines.length} semaines
                        </span>
                    </button>

                    <button
                        type="button"
                        onClick={() => setActiveTab('mensuel')}
                        className={`flex items-center gap-2 px-5 py-2.5 text-xs font-bold transition-all cursor-pointer ${
                            activeTab === 'mensuel'
                                ? 'bg-[var(--color-surface)] text-brand-700 dark:text-brand-300 shadow-xs border border-[var(--color-border-subtle)]'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <CalendarRange size={15} />
                        <span>2. Synthèse Mensuelle Globale (Clôture DAF)</span>
                    </button>
                </div>

                {/* VUE 1 : Synthèse Hebdomadaire Séquentielle */}
                {activeTab === 'hebdo' && (
                    <div className="space-y-4">
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-xs">
                            <h3 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Layers size={16} className="text-brand-500" />
                                <span>Découpage Hebdomadaire &amp; Validation Séquentielle</span>
                            </h3>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                Validez les semaines dans l'ordre chronologique. Les heures supplémentaires (30 % / 50 %) et compensations de retards sont calculées par tronçon.
                            </p>

                            {loadingSemaines ? (
                                <div className="p-8 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                                    <Clock size={15} className="animate-spin text-brand-500" />
                                    Chargement du découpage hebdomadaire...
                                </div>
                            ) : (
                                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mt-4">
                                    {semaines.map((sem) => (
                                        <div
                                            key={sem.id}
                                            className={`border p-4 transition-all ${
                                                sem.estValidee
                                                    ? 'border-emerald-500/40 bg-emerald-500/5'
                                                    : sem.ready
                                                    ? 'border-brand-500/40 bg-brand-500/5'
                                                    : sem.nbAnomalies > 0
                                                    ? 'border-amber-500/40 bg-amber-500/5'
                                                    : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] opacity-70'
                                            }`}
                                        >
                                            <div className="flex items-center justify-between">
                                                <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                                    {sem.title}
                                                </span>
                                                {sem.estValidee ? (
                                                    <CheckCircle2 size={14} className="text-emerald-600" />
                                                ) : sem.nbAnomalies > 0 ? (
                                                    <AlertTriangle size={14} className="text-amber-600" />
                                                ) : null}
                                            </div>

                                            <p className="text-xs font-mono text-[var(--color-fg-muted)] mt-0.5">
                                                {sem.dates} ({sem.nbJoursOuvrables}j ouvrés)
                                            </p>

                                            <p className={`text-[13px] font-bold mt-2 ${
                                                sem.estValidee
                                                    ? 'text-emerald-700 dark:text-emerald-300'
                                                    : sem.ready
                                                    ? 'text-brand-700 dark:text-brand-300'
                                                    : sem.nbAnomalies > 0
                                                    ? 'text-amber-700'
                                                    : 'text-[var(--color-fg)]'
                                            }`}>
                                                {sem.statut}
                                            </p>

                                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 leading-snug">
                                                {sem.desc}
                                            </p>

                                            <div className="mt-3 pt-2.5 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-xs">
                                                <span className="font-mono text-[var(--color-fg-muted)]">
                                                    {sem.totalHeures}h / {sem.seuilHeures}h
                                                </span>
                                                {sem.ready && (
                                                    <span className="text-brand-600 font-bold flex items-center gap-1">
                                                        Prête <ArrowRight size={12} />
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* VUE 2 : Synthèse Mensuelle Globale */}
                {activeTab === 'mensuel' && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                        <div className="px-5 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                Tableau Mensuel Consolidé pour la Paie
                            </h3>
                            <span className="text-xs font-mono text-[var(--color-fg-muted)]">
                                {filteredData.length} salarié(s)
                            </span>
                        </div>

                        {loading ? (
                            <div className="p-12 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                                <Clock size={16} className="animate-spin text-brand-500" />
                                Chargement de la synthèse mensuelle...
                            </div>
                        ) : error ? (
                            <div className="p-4 text-xs font-bold text-rose-600 bg-rose-50 border-l-4 border-rose-500">
                                {error}
                            </div>
                        ) : (
                            <div className="overflow-x-auto">
                                <Table className="w-full text-left border-collapse text-[12.5px]">
                                    <Thead>
                                        <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                            <Th className="py-2.5 px-4">Salarié</Th>
                                            <Th className="py-2.5 px-3">Site</Th>
                                            <Th className="py-2.5 px-2 text-center">Présences</Th>
                                            <Th className="py-2.5 px-2 text-center">Congés</Th>
                                            <Th className="py-2.5 px-2 text-center">Fériés</Th>
                                            <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Heures Totales</Th>
                                            <Th className="py-2.5 px-2 text-center">HS 30 %</Th>
                                            <Th className="py-2.5 px-2 text-center">HS 50 %</Th>
                                            <Th className="py-2.5 px-2 text-center">Nuit</Th>
                                            <Th className="py-2.5 px-2 text-center text-rose-600">Déficit Retenue</Th>
                                            <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Repas</Th>
                                            <Th className="py-2.5 px-3 text-center">Déplacement</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {filteredData.map((row) => (
                                            <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                                <Td className="py-2.5 px-4 font-semibold">
                                                    {row.nom} {row.prenom}
                                                    <span className="block text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                        {row.matricule} {row.estCadre && '· CADRE'}
                                                    </span>
                                                </Td>
                                                <Td className="py-2.5 px-3 text-xs text-[var(--color-fg-muted)]">
                                                    {row.siteNom || `Site #${row.idSite}`}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursPresents} j</Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursConge} j</Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursFerieChome} j</Td>
                                                <Td className="py-2.5 px-3 text-center font-mono font-bold border-l border-[var(--color-border-subtle)]">
                                                    {row.totalHeuresTravaillees} h
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono text-indigo-600 font-bold">
                                                    {row.heuresHs30 > 0 ? `+${row.heuresHs30}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono text-indigo-700 font-bold">
                                                    {row.heuresHs50 > 0 ? `+${row.heuresHs50}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">
                                                    {row.heuresNuitTotal > 0 ? `${row.heuresNuitTotal}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono font-bold text-rose-600">
                                                    {row.heuresAbsenceRetenues > 0 ? `-${row.heuresAbsenceRetenues}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-3 text-center font-mono border-l border-[var(--color-border-subtle)]">
                                                    {row.nbJoursRepas} j ({fmtMoney(row.montantRepas)})
                                                </Td>
                                                <Td className="py-2.5 px-3 text-center font-mono">
                                                    {row.nbJoursDeplacement} j ({fmtMoney(row.montantDeplacement)})
                                                </Td>
                                            </Tr>
                                        ))}
                                    </Tbody>
                                </Table>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </>
    );
}
