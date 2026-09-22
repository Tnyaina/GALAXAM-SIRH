import { useState, useEffect, useCallback } from 'react';
import {
    Banknote,
    FileText,
    Receipt,
    Eye,
    RefreshCw,
    TrendingUp,
    TrendingDown,
    Calculator,
    ChevronDown,
    ChevronRight,
    AlertCircle,
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/carriereUtils';
import paieService from '@/api/paie/paieService';
import stcService from '@/api/paie/stcService';
import { getErrorMessage } from '@/api/apiClient';
import BulletinPaieDrawer from '@/components/paie/BulletinPaieDrawer';
import StcPreparationModal from '@/components/paie/stc/StcPreparationModal';
import StcRecuModal from '@/components/paie/stc/StcRecuModal';

function AccordionSection({ id, icon, title, badge = null, children, defaultOpen = true, variant = 'default' }) {
    const [isOpen, setIsOpen] = useState(defaultOpen);
    const variantBorder = variant === 'warning' ? 'border-[var(--color-warning-500)]/40' : variant === 'danger' ? 'border-[var(--color-danger-500)]/40' : 'border-[var(--color-border-subtle)]';
    const iconColor = variant === 'warning' ? 'text-[var(--color-warning-600)]' : variant === 'danger' ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-brand-600)]';
    return (
        <div className={`bg-[var(--color-surface)] border ${variantBorder} rounded-md overflow-hidden`}>
            <button
                type="button"
                id={id}
                onClick={() => setIsOpen(v => !v)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-[var(--color-bg-subtle)]/60 transition-colors text-left group"
                aria-expanded={isOpen}
            >
                <div className="flex items-center gap-2.5 min-w-0">
                    <span className={`shrink-0 ${iconColor}`}>{icon}</span>
                    <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] truncate">{title}</span>
                    {badge}
                </div>
                <span className="shrink-0 text-[var(--color-fg-muted)] group-hover:text-[var(--color-fg)] transition-colors">
                    {isOpen ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                </span>
            </button>
            {isOpen && (
                <div className="border-t border-[var(--color-border-subtle)] px-4 py-4">{children}</div>
            )}
        </div>
    );
}

function KpiCard({ label, value, sublabel, highlight = false, mono = true }) {
    return (
        <div className="p-2.5 bg-[var(--color-bg-subtle)]/40 border border-[var(--color-border-subtle)] rounded space-y-0.5">
            <span className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] block leading-tight">{label}</span>
            <p className={`text-sm font-bold ${mono ? 'font-mono' : ''} ${highlight ? 'text-[var(--color-success-600)]' : 'text-[var(--color-fg)]'} leading-snug`}>{value ?? '—'}</p>
            {sublabel && <span className="text-[10px] text-[var(--color-fg-subtle)] block leading-tight">{sublabel}</span>}
        </div>
    );
}

export default function EmployePaieTab({ idEmploye, employe, canWrite }) {
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState(null);
    const [feedbackMsg, setFeedbackMsg] = useState(null);
    const [synthese, setSynthese] = useState(null);
    const [bulletins, setBulletins] = useState([]);
    const [mouvements, setMouvements] = useState([]);
    const [stcList, setStcList] = useState([]);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [isStcPrepOpen, setIsStcPrepOpen] = useState(false);
    const [selectedStcRecuId, setSelectedStcRecuId] = useState(null);

    const chargerDonnees = useCallback(async () => {
        if (!idEmploye) return;
        setLoading(true);
        setErrorMsg(null);
        try {
            const [synthRes, bListRes, mvtRes, stcRes] = await Promise.all([
                paieService.getSynthesePaieEmploye(idEmploye).catch(() => null),
                paieService.listerBulletinsEmploye(idEmploye).catch(() => []),
                paieService.listerAjustements(null, idEmploye).catch(() => []),
                stcService.listerStcEmploye(idEmploye).catch(() => []),
            ]);
            setSynthese(synthRes);
            setBulletins(Array.isArray(bListRes) ? bListRes : []);
            setMouvements(Array.isArray(mvtRes) ? mvtRes : (mvtRes?.content || mvtRes?.items || []));
            setStcList(Array.isArray(stcRes) ? stcRes : []);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, "Erreur lors du chargement du dossier paie de l'employe."));
        } finally {
            setLoading(false);
        }
    }, [idEmploye]);

    useEffect(() => { chargerDonnees(); }, [chargerDonnees]);

    const handleVoirBulletin = (idBulletin) => { setSelectedBulletinId(idBulletin); setIsDrawerOpen(true); };

    const isContratClos = synthese?.codeStatutContrat === 'CLOTURE' || synthese?.codeStatutContrat === 'ROMPU' || (synthese?.contratActif === false && synthese?.idContrat != null);
    const hasStcNeeded = isContratClos && stcList.length === 0;

    if (loading) return <div className="space-y-4 p-4"><SkeletonTable rows={4} columns={4} /></div>;

    return (
        <div className="space-y-3">
            {feedbackMsg && <Alert type={feedbackMsg.type} message={feedbackMsg.message} onClose={() => setFeedbackMsg(null)} />}
            {errorMsg && <Alert type="error" title="Erreur Paie" message={errorMsg} onClose={() => setErrorMsg(null)} />}

            {hasStcNeeded && (
                <div className="flex items-start gap-2.5 p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/40 rounded-md text-xs">
                    <AlertCircle size={15} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                    <div className="space-y-1 flex-1">
                        <p className="font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">Solde de Tout Compte à établir</p>
                        <p className="text-[var(--color-fg-muted)]">Le contrat de ce salarié est cloture ou rompu mais aucun STC n'a encore ete emis.</p>
                    </div>
                    {canWrite && (
                        <Button type="button" variant="warning" size="xs" iconLeft={<Calculator size={13} />} onClick={() => setIsStcPrepOpen(true)} className="shrink-0">
                            Etablir le STC
                        </Button>
                    )}
                </div>
            )}

            <div className="flex justify-end">
                <Button type="button" variant="outline" size="xs" onClick={chargerDonnees} iconLeft={<RefreshCw size={12} />}>Actualiser</Button>
            </div>

            {/* ===== SYNTHESE ===== */}
            <AccordionSection id="paie-synthese" icon={<Banknote size={15} />} title="Synthese Remuneration & Parametres de Paie" defaultOpen={true}>
                {!synthese ? (
                    <p className="text-xs text-[var(--color-fg-muted)] py-2">Donnees de synthese indisponibles. Le salarie n&apos;a peut-etre pas encore de fiche de paie active.</p>
                ) : (
                    <div className="space-y-4">
                        <div>
                            <p className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] mb-2 tracking-wider">Parametres de calcul</p>
                            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                                <KpiCard label="Salaire Contractuel" value={formatMoneyAr(synthese.salaireBaseContractuel)} sublabel="Base brute mensuelle" />
                                <KpiCard label="Taux Horaire" value={formatMoneyAr(synthese.tauxHoraire)} sublabel={`/ heure (${synthese.diviseurHeures || '173.33'} h)`} />
                                <KpiCard label="Taux Journalier TCONGE" value={formatMoneyAr(synthese.tauxJournalierConge)} sublabel="Valorisation conge / jour" />
                                <KpiCard label="Mode de calcul" value={synthese.modeCalculPaie || 'MENSUEL'} sublabel={synthese.categorieProf || ''} mono={false} />
                            </div>
                        </div>
                        <div>
                            <p className="text-[10.5px] uppercase font-semibold text-[var(--color-fg-muted)] mb-2 tracking-wider">Derniere paie</p>
                            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
                                <KpiCard label="Derniere Periode" value={synthese.dernierePeriodeCode || '—'} sublabel={synthese.derniereDatePaiement ? fmtDate(synthese.derniereDatePaiement) : 'Aucun bulletin'} mono={false} />
                                <KpiCard label="Dernier Net Paye" value={formatMoneyAr(synthese.dernierNet)} sublabel={`Brut : ${formatMoneyAr(synthese.dernierBrut)}`} highlight={!!synthese.dernierNet} />
                                <KpiCard label="Avances en cours" value={formatMoneyAr(synthese.totalAvancesEnCours)} sublabel="Montant a deduire" />
                                <KpiCard label="Capital restant Prets" value={formatMoneyAr(synthese.capitalRestantPrets)} sublabel="Encours prets employeur" />
                            </div>
                        </div>
                    </div>
                )}
            </AccordionSection>

            {/* ===== BULLETINS ===== */}
            <AccordionSection
                id="paie-bulletins"
                icon={<FileText size={15} />}
                title="Historique des Bulletins de Paie"
                badge={bulletins.length > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 text-[10px] font-bold bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] rounded-full">
                        {bulletins.length}
                    </span>
                )}
                defaultOpen={true}
            >
                {bulletins.length === 0 ? (
                    <EmptyState title="Aucun bulletin de paie" description="Aucune paie n'a encore ete calculee ou cloturee pour ce salarie." />
                ) : (
                    <div className="overflow-x-auto -mx-4">
                        <Table className="w-full text-xs">
                            <Thead>
                                <Tr>
                                    <Th>Periode</Th>
                                    <Th>Type</Th>
                                    <Th>Date Calcul</Th>
                                    <Th className="text-right">Brut</Th>
                                    <Th className="text-right">Cotisations</Th>
                                    <Th className="text-right">IRSA / ISI</Th>
                                    <Th className="text-right font-bold">Net a Payer</Th>
                                    <Th>Statut</Th>
                                    <Th className="text-right">Action</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {bulletins.map((b) => (
                                    <Tr key={b.idBulletin} className="hover:bg-[var(--color-bg-subtle)] cursor-pointer transition-colors" onClick={() => handleVoirBulletin(b.idBulletin)}>
                                        <Td>
                                            <div className="font-semibold text-[var(--color-fg)] leading-tight">{b.codePeriode}</div>
                                            <span className="text-[10px] text-[var(--color-fg-muted)] block truncate max-w-[140px]">{b.libellePeriode}</span>
                                        </Td>
                                        <Td><Badge variant={b.typeBulletin === 'STC' ? 'warning' : 'default'} size="xs">{b.typeBulletin || 'MENSUEL'}</Badge></Td>
                                        <Td className="font-mono text-[var(--color-fg-muted)]">{fmtDate(b.dateCalcul)}</Td>
                                        <Td className="text-right font-mono">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">-{formatMoneyAr(b.totalCotisationsSalariales)}</Td>
                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">-{formatMoneyAr(b.irsaNet || b.retenueIsi5)}</Td>
                                        <Td className="text-right font-mono font-bold text-[var(--color-fg)]">{formatMoneyAr(b.netAPayer)}</Td>
                                        <Td>
                                            <Badge variant={b.statut === 'VALIDE' || b.statut === 'CLOTURE' ? 'success' : b.statut === 'CALCULE' ? 'info' : 'default'} size="xs">{b.statut}</Badge>
                                        </Td>
                                        <Td className="text-right">
                                            <Button type="button" variant="outline" size="xs" iconLeft={<Eye size={12} />} onClick={(e) => { e.stopPropagation(); handleVoirBulletin(b.idBulletin); }}>Voir</Button>
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    </div>
                )}
            </AccordionSection>

            {/* ===== MOUVEMENTS ===== */}
            <AccordionSection
                id="paie-mouvements"
                icon={<Receipt size={15} />}
                title="Mouvements & Ajustements de Paie"
                badge={mouvements.length > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 text-[10px] font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] rounded-full">
                        {mouvements.length}
                    </span>
                )}
                defaultOpen={false}
            >
                {mouvements.length === 0 ? (
                    <EmptyState title="Aucun mouvement de paie" description="Aucun acompte, avance, prime ou note de frais n'a ete enregistre pour ce salarie." />
                ) : (
                    <div className="space-y-2">
                        <p className="text-[11px] text-[var(--color-fg-muted)] italic">Lecture seule — saisie via la campagne de paie</p>
                        <div className="overflow-x-auto -mx-4">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>Date</Th>
                                        <Th>Type de Mouvement</Th>
                                        <Th>Nature</Th>
                                        <Th className="text-right">Montant</Th>
                                        <Th>Motif</Th>
                                        <Th>Statut</Th>
                                        <Th>Imputation</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {mouvements.map((m) => {
                                        const isGain = m.sens === 'GAIN';
                                        return (
                                            <Tr key={m.idAvance || m.idAjustement}>
                                                <Td className="font-mono text-[var(--color-fg-muted)]">{fmtDate(m.dateDemande || m.datePaiement)}</Td>
                                                <Td>
                                                    <div className="font-semibold text-[var(--color-fg)]">{m.typeLibelle || m.typeMouvement}</div>
                                                    <span className="text-[10px] text-[var(--color-fg-subtle)] font-mono">{m.typeMouvement}</span>
                                                </Td>
                                                <Td>
                                                    <Badge variant={isGain ? 'success' : 'danger'} size="xs" className="flex items-center gap-1 w-fit">
                                                        {isGain ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
                                                        {isGain ? 'GAIN' : 'RETENUE'}
                                                    </Badge>
                                                </Td>
                                                <Td className={`text-right font-mono font-bold ${isGain ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                                    {isGain ? '+' : '-'}{formatMoneyAr(m.montant)}
                                                </Td>
                                                <Td className="text-[var(--color-fg-muted)] max-w-[160px] truncate">{m.motif || '—'}</Td>
                                                <Td>
                                                    <Badge variant={m.statut === 'APPROUVE' || m.statut === 'VALIDE' || m.statut === 'REGLE' ? 'success' : m.statut === 'EN_ATTENTE' ? 'warning' : 'default'} size="xs">
                                                        {m.statut}
                                                    </Badge>
                                                </Td>
                                                <Td>
                                                    {m.idBulletin
                                                        ? <span className="text-[var(--color-success-600)] font-semibold font-mono">#{m.idBulletin}</span>
                                                        : <span className="text-[var(--color-warning-600)]">En attente</span>
                                                    }
                                                </Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </div>
                    </div>
                )}
            </AccordionSection>

            {/* ===== STC (conditionnel) ===== */}
            {(isContratClos || stcList.length > 0) && (
                <AccordionSection
                    id="paie-stc"
                    icon={<Calculator size={15} />}
                    title="Solde de Tout Compte (STC)"
                    badge={stcList.length === 0 && isContratClos && <Badge variant="warning" size="xs" className="ml-1">Non etabli</Badge>}
                    defaultOpen={stcList.length > 0}
                    variant={stcList.length === 0 && isContratClos ? 'warning' : 'default'}
                >
                    {stcList.length > 0 ? (
                        <div className="overflow-x-auto -mx-4">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>N Recu STC</Th>
                                        <Th>Date Depart</Th>
                                        <Th>Motif</Th>
                                        <Th className="text-right">Total Brut</Th>
                                        <Th className="text-right">Prets Apures</Th>
                                        <Th className="text-right">Net STC</Th>
                                        <Th>Statut</Th>
                                        <Th className="text-right">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {stcList.map((s) => (
                                        <Tr key={s.idStc}>
                                            <Td className="font-mono font-bold text-[var(--color-fg)]">{s.numeroRecu || `STC-#${s.idStc}`}</Td>
                                            <Td>{fmtDate(s.dateDepart)}</Td>
                                            <Td className="text-[var(--color-fg-muted)]">{s.motifRupture}</Td>
                                            <Td className="text-right font-mono">{formatMoneyAr(s.totalBrutStc)}</Td>
                                            <Td className="text-right font-mono text-[var(--color-danger-600)]">{formatMoneyAr(s.soldePretsDeduit)}</Td>
                                            <Td className="text-right font-mono font-bold text-[var(--color-fg)]">{formatMoneyAr(s.netAPayerStc)}</Td>
                                            <Td><Badge variant={s.statut === 'VALIDE' ? 'success' : 'warning'} size="xs">{s.statut}</Badge></Td>
                                            <Td className="text-right">
                                                <Button type="button" variant="outline" size="xs" iconLeft={<FileText size={12} />} onClick={() => setSelectedStcRecuId(s.idStc)}>Quittance</Button>
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </div>
                    ) : (
                        <p className="text-xs text-[var(--color-fg-muted)] py-2">Contrat actif ({synthese?.numeroContrat || 'En cours'}) — Aucun Solde de Tout Compte requis a ce jour.</p>
                    )}
                </AccordionSection>
            )}

            {/* ===== MODALES ===== */}
            <BulletinPaieDrawer
                isOpen={isDrawerOpen}
                onClose={() => { setIsDrawerOpen(false); setSelectedBulletinId(null); }}
                idBulletin={selectedBulletinId}
                onRecalculateSuccess={chargerDonnees}
            />
            {isStcPrepOpen && (
                <StcPreparationModal
                    isOpen={isStcPrepOpen}
                    onClose={() => setIsStcPrepOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={synthese?.nomComplet || employe?.nom || ''}
                    dateDepartInitiale={synthese?.dateFinContrat || null}
                    motifInitial="DEMISSION"
                    onStcValide={chargerDonnees}
                />
            )}
            {selectedStcRecuId && (
                <StcRecuModal isOpen={Boolean(selectedStcRecuId)} onClose={() => setSelectedStcRecuId(null)} idStc={selectedStcRecuId} />
            )}
        </div>
    );
}
