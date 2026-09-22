import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    History, Calendar, Users, CheckCircle2, Lock,
    ArrowRight, Search, ShieldCheck
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import paieService from '@/api/paie/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * HistoriqueCampagnesPage — Historique des campagnes de paie (Maquette V1 Full V3).
 *
 * Liste claire de toutes les campagnes passées et en cours :
 * - Période (Code, dates)
 * - Population
 * - Statut (CLÔTURÉE, VALIDÉE, EN COURS)
 * - Version / Snapshot
 * - Masse Nette à payer
 * - Date de validation / clôture
 * - Action [Ouvrir] (ouvre la campagne directement en READ ONLY si clôturée)
 */
export default function HistoriqueCampagnesPage() {
    const navigate = useNavigate();
    const [periodes, setPeriodes] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [feedback, setFeedback] = useState(null);

    const chargerCampagnes = useCallback(async () => {
        setLoading(true);
        try {
            const list = await paieService.listerPeriodes();
            setPeriodes(Array.isArray(list) ? list : []);
        } catch (err) {
            setFeedback({ type: 'error', text: 'Impossible de charger l’historique des périodes de paie.' });
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        chargerCampagnes();
    }, [chargerCampagnes]);

    const filteredPeriodes = periodes.filter(p => {
        if (!searchQuery.trim()) return true;
        const q = searchQuery.toLowerCase();
        return (p.code || '').toLowerCase().includes(q) ||
            (p.state || '').toLowerCase().includes(q);
    });

    const formatDate = (d) => {
        if (!d) return '—';
        return new Date(d).toLocaleDateString('fr-MG', { day: '2-digit', month: 'short', year: 'numeric' });
    };

    const handleOpenCampaign = (p) => {
        // Naviguer vers la première étape ou contrôle selon le statut
        const isClosed = p.state === 'CLOTUREE';
        if (isClosed) {
            navigate(`/paie/periodes/${p.idPeriodePaie}/sorties`);
        } else {
            navigate(`/paie/periodes/${p.idPeriodePaie}/preparation`);
        }
    };

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Historique des campagnes | GALAXAM SIRH"
                description="Historique complet des campagnes de paie, statuts et scellements."
            />

            {/* Header d'espace */}
            <div className="bg-[var(--color-surface)] border-b border-[var(--color-border-subtle)] px-6 py-4">
                <div className="max-w-6xl mx-auto flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                    <div>
                        <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Paie & Rémunération
                        </span>
                        <h1 className="text-lg font-bold text-[var(--color-fg)]">
                            Historique des campagnes de paie
                        </h1>
                    </div>
                </div>
            </div>

            <div className="flex-1 max-w-6xl mx-auto px-4 py-6 space-y-5 w-full">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* Barre de recherche */}
                <div className="flex items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-2.5 rounded-md">
                    <div className="w-full sm:w-72">
                        <Input
                            size="xs"
                            placeholder="Rechercher par période ou statut..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            leftIcon={<Search size={13} />}
                        />
                    </div>
                    <span className="text-xs text-[var(--color-fg-muted)]">
                        {filteredPeriodes.length} campagne(s) enregistrée(s)
                    </span>
                </div>

                {/* Table de l'historique */}
                {loading ? (
                    <div className="p-8 text-center text-xs text-[var(--color-fg-muted)]">Chargement de l'historique...</div>
                ) : filteredPeriodes.length === 0 ? (
                    <div className="p-8 text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-md text-xs text-[var(--color-fg-muted)]">
                        Aucune campagne enregistrée dans le système.
                    </div>
                ) : (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-md overflow-hidden">
                        <div className="overflow-x-auto">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>Période</Th>
                                        <Th>Dates du cycle</Th>
                                        <Th className="text-center">Population</Th>
                                        <Th className="text-center">Statut</Th>
                                        <Th className="text-center">Snapshot</Th>
                                        <Th className="text-right">Masse Nette</Th>
                                        <Th>Clôture / Validation</Th>
                                        <Th className="text-center w-24">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {filteredPeriodes.map((p) => {
                                        const isClosed = p.state === 'CLOTUREE';
                                        const isValidated = ['VALIDEE', 'PAIE_VALIDEE'].includes(p.state);

                                        return (
                                            <Tr
                                                key={p.idPeriodePaie}
                                                className="hover:bg-[var(--color-bg-subtle)] transition-colors"
                                            >
                                                <Td className="font-bold text-[var(--color-fg)]">
                                                    <div className="flex items-center gap-1.5">
                                                        <span>{p.code}</span>
                                                        {p.actif && <Badge variant="primary" size="xs">Active</Badge>}
                                                    </div>
                                                </Td>
                                                <Td className="text-[var(--color-fg-muted)] font-mono text-[11px]">
                                                    {formatDate(p.dateDebut || p.startDate)} → {formatDate(p.dateFin || p.endDate)}
                                                </Td>
                                                <Td className="text-center">
                                                    <span className="font-medium text-[var(--color-fg)]">
                                                        {p.nbSalariesTotal ?? p.populationCount ?? '—'}
                                                    </span>
                                                </Td>
                                                <Td className="text-center">
                                                    <Badge
                                                        variant={isClosed ? 'secondary' : (isValidated ? 'success' : 'warning')}
                                                        size="xs"
                                                    >
                                                        {isClosed ? 'CLÔTURÉE' : (isValidated ? 'VALIDÉE' : (p.state || 'EN COURS'))}
                                                    </Badge>
                                                </Td>
                                                <Td className="text-center font-mono text-[11px] text-[var(--color-fg-muted)]">
                                                    v{p.version || 1}
                                                </Td>
                                                <Td className="text-right font-mono font-bold text-[var(--color-fg)]">
                                                    {p.totalNet ? formatMoneyAr(p.totalNet) : '—'}
                                                </Td>
                                                <Td className="text-[var(--color-fg-muted)] text-[11px]">
                                                    {formatDate(p.dateCloture || p.dateValidation || p.updatedAt)}
                                                </Td>
                                                <Td className="text-center">
                                                    <Button
                                                        variant={isClosed ? 'ghost' : 'primary'}
                                                        size="xs"
                                                        onClick={() => handleOpenCampaign(p)}
                                                        iconRight={<ArrowRight size={13} />}
                                                    >
                                                        {isClosed ? 'Consulter' : 'Ouvrir'}
                                                    </Button>
                                                </Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
