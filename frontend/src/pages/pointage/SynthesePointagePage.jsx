import { useState, useMemo, useEffect } from 'react';
import { Search, Clock } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import SEO from '@/components/system/SEO';
import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import usePointageSynthese from '@/hooks/pointage/usePointageSynthese';

export default function SynthesePointagePage() {
    const [sites, setSites] = useState([]);
    const [periodes, setPeriodes] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');

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

    return (
        <>
            <SEO
                title="Synthèse Mensuelle des Pointages | GALAXAM SIRH"
                description="Consolidation mensuelle des heures travaillées, retards et indemnités pour la paie."
            />

            <div className="space-y-4 max-w-[1600px] mx-auto pb-12">
                <PageHeader
                    title="Synthèse Mensuelle & Compensations"
                    subtitle="Consolidation des heures travaillées, retards, majorations et indemnités pour la paie"
                    breadcrumbs={[
                        { label: 'Accueil', href: '/' },
                        { label: 'Pointages', href: '/pointages/saisie' },
                        { label: 'Synthèse Mensuelle' }
                    ]}
                />

                {/* Barre de navigation guidée */}
                <PointageWorkflowNav />

                {/* Filtres */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
                        <div>
                            <Select
                                label="Période de paie"
                                options={periodeOptions}
                                value={selectedPeriode}
                                onChange={(val) => setSelectedPeriode(val)}
                                className="w-full"
                            />
                        </div>

                        <div>
                            <Select
                                label="Site d'affectation"
                                options={siteOptions}
                                value={selectedSite}
                                onChange={(val) => setSelectedSite(val)}
                                className="w-full"
                            />
                        </div>

                        <div>
                            <Input
                                label="Filtrer employé"
                                iconLeft={<Search size={16} />}
                                placeholder="Rechercher par nom, matricule..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="w-full"
                            />
                        </div>
                    </div>
                </div>

                {/* Tableau Synthèse */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-x-auto">
                    <Table className="w-full text-left border-collapse">
                        <Thead>
                            <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                <Th className="py-3 px-4">Employé</Th>
                                <Th className="py-3 px-4">Site</Th>
                                <Th className="py-3 px-3 text-center">Présences (j)</Th>
                                <Th className="py-3 px-3 text-center">Congés (j)</Th>
                                <Th className="py-3 px-3 text-center">Absences (j)</Th>
                                <Th className="py-3 px-3 text-center">Total Heures</Th>
                                <Th className="py-3 px-3 text-center">Heures Nuit</Th>
                                <Th className="py-3 px-3 text-center">Total Retards</Th>
                                <Th className="py-3 px-3 text-center">Repas</Th>
                                <Th className="py-3 px-3 text-center">Déplacements</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {loading ? (
                                <Tr>
                                    <Td colSpan={10} className="text-center py-12 text-xs font-bold text-[var(--color-fg-muted)]">
                                        <Clock size={16} className="animate-spin inline mr-2 text-[var(--color-brand-500)]" />
                                        Calcul et consolidation de la synthèse...
                                    </Td>
                                </Tr>
                            ) : error ? (
                                <Tr>
                                    <Td colSpan={10} className="text-center py-12 text-xs font-bold text-rose-600">
                                        {error}
                                    </Td>
                                </Tr>
                            ) : filteredData.length === 0 ? (
                                <Tr>
                                    <Td colSpan={10} className="text-center py-12 text-xs font-bold text-[var(--color-fg-muted)]">
                                        Aucune donnée de synthèse trouvée pour cette période.
                                    </Td>
                                </Tr>
                            ) : (
                                filteredData.map((row) => (
                                    <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/50">
                                        <Td className="py-3 px-4">
                                            <p className="text-xs font-bold text-[var(--color-fg)]">{row.nom} {row.prenom}</p>
                                            <p className="text-[11px] font-mono text-[var(--color-fg-muted)]">{row.matricule}</p>
                                        </Td>
                                        <Td className="py-3 px-4 text-xs text-[var(--color-fg-muted)]">
                                            {row.siteNom || '—'}
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-bold text-xs text-emerald-700 dark:text-emerald-400">
                                            {row.nbJoursPresents} j
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-bold text-xs text-amber-600">
                                            {row.nbJoursConge || 0} j
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-bold text-xs text-rose-600">
                                            {row.nbJoursAbsence || 0} j
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-mono font-bold text-xs text-[var(--color-fg)]">
                                            {row.totalHeuresTravaillees ? `${row.totalHeuresTravaillees.toFixed(2)} h` : '0.00 h'}
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-mono text-xs text-indigo-600">
                                            {row.heuresNuitTotal ? `${row.heuresNuitTotal.toFixed(2)} h` : '0.00 h'}
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-mono text-xs">
                                            {row.totalMinutesRetard > 0 ? (
                                                <span className="text-amber-600 font-bold">+{row.totalMinutesRetard} min</span>
                                            ) : (
                                                <span className="text-emerald-700 dark:text-emerald-400">0 min</span>
                                            )}
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-mono font-bold text-xs text-[var(--color-fg)]">
                                            {row.nbJoursRepas || 0}
                                        </Td>
                                        <Td className="py-3 px-3 text-center font-mono font-bold text-xs text-[var(--color-fg)]">
                                            {row.nbJoursDeplacement || 0} j
                                        </Td>
                                    </Tr>
                                ))
                            )}
                        </Tbody>
                    </Table>
                </div>
            </div>
        </>
    );
}
