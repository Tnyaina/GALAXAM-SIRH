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
import StatCard from '@/components/ui/data-display/StatCard';
import { Card } from '@/components/ui/data-display/Card';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import paieService from '@/features/payroll/api/paieService';
import stcService from '@/features/payroll/api/stcService';
import { getErrorMessage } from '@/api/apiClient';
import BulletinPaieDrawer from '@/features/payroll/components/views/BulletinPaieDrawer';
import StcPreparationModal from '@/features/payroll/components/modals/stc/StcPreparationModal';
import StcRecuModal from '@/features/payroll/components/modals/stc/StcRecuModal';
import DemandeAcompteAvanceModal from './DemandeAcompteAvanceModal';

function AccordionSection({ id, icon, title, badge = null, children, defaultOpen = true, variant = 'default' }) {
    const [isOpen, setIsOpen] = useState(defaultOpen);
    const variantBorder = variant === 'warning' ? 'border-[var(--color-warning-500)]/40' : variant === 'danger' ? 'border-[var(--color-danger-500)]/40' : 'border-[var(--color-border-subtle)]';
    const iconColor = variant === 'warning' ? 'text-[var(--color-warning-600)]' : variant === 'danger' ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-brand-600)]';
    return (
        <div className={`bg-[var(--color-surface)] border ${variantBorder} rounded-none overflow-hidden`}>
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

export default function EmployePaieTab({ idEmploye, employe, canWrite }) {
    // État local : synthèse, documents ouverts et retours d'action.
    const [loading, setLoading] = useState(true);
    const [sectionErrors, setSectionErrors] = useState({});
    const [feedbackMsg, setFeedbackMsg] = useState(null);
    const [synthese, setSynthese] = useState(null);
    const [bulletins, setBulletins] = useState([]);
    const [mouvements, setMouvements] = useState([]);
    const [stcList, setStcList] = useState([]);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [isStcPrepOpen, setIsStcPrepOpen] = useState(false);
    const [selectedStcRecuId, setSelectedStcRecuId] = useState(null);
    const [isDemandeOpen, setIsDemandeOpen] = useState(false);

    const chargerDonnees = useCallback(async () => {
        if (!idEmploye) return;
        setLoading(true);
        setSectionErrors({});
        try {
            const [synthRes, bListRes, mvtRes, stcRes] = await Promise.allSettled([
                paieService.getSynthesePaieEmploye(idEmploye),
                paieService.listerBulletinsEmploye(idEmploye),
                paieService.listerAjustements(null, idEmploye),
                stcService.listerStcEmploye(idEmploye),
            ]);
            const errors = {};
            if (synthRes.status === 'fulfilled') setSynthese(synthRes.value);
            else { setSynthese(null); errors.synthese = getErrorMessage(synthRes.reason, 'Synthèse indisponible.'); }
            if (bListRes.status === 'fulfilled') setBulletins(Array.isArray(bListRes.value) ? bListRes.value : []);
            else { setBulletins([]); errors.bulletins = getErrorMessage(bListRes.reason, 'Bulletins indisponibles.'); }
            if (mvtRes.status === 'fulfilled') {
                const value = mvtRes.value;
                setMouvements(Array.isArray(value) ? value : (value?.content || value?.items || []));
            } else { setMouvements([]); errors.mouvements = getErrorMessage(mvtRes.reason, 'Mouvements indisponibles.'); }
            if (stcRes.status === 'fulfilled') setStcList(Array.isArray(stcRes.value) ? stcRes.value : []);
            else { setStcList([]); errors.stc = getErrorMessage(stcRes.reason, 'Soldes de tout compte indisponibles.'); }
            setSectionErrors(errors);
        } catch (err) {
            setSectionErrors({ synthese: getErrorMessage(err, "Erreur lors du chargement du dossier paie de l'employé.") });
        } finally {
            setLoading(false);
        }
    }, [idEmploye]);

    // Effet : charge le dossier paie du collaborateur sélectionné.
    useEffect(() => { chargerDonnees(); }, [chargerDonnees]);

    const handleVoirBulletin = (idBulletin) => { setSelectedBulletinId(idBulletin); setIsDrawerOpen(true); };

    const hasStcNeeded = synthese?.stcActionRequise === true;

    if (loading) return <div className="space-y-4 p-4"><SkeletonTable rows={4} columns={4} /></div>;

    // Rendu
    return (
        <div className="space-y-3">
            {feedbackMsg && <Alert type={feedbackMsg.type} message={feedbackMsg.message} onClose={() => setFeedbackMsg(null)} />}
            {sectionErrors.synthese && <Alert type="error" title="Synthèse indisponible" message={sectionErrors.synthese} />}

            {hasStcNeeded && (
                <div className="flex items-start gap-2.5 p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/40 rounded-none text-xs">
                    <AlertCircle size={15} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                    <div className="space-y-1 flex-1">
                        <p className="font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">Solde de Tout Compte à établir</p>
                        <p className="text-[var(--color-fg-muted)]">Le contrat est terminé et aucun solde de tout compte en cours n’a été trouvé.</p>
                    </div>
                    {canWrite && (
                        <Button type="button" variant="warning" size="xs" iconLeft={<Calculator size={13} />} onClick={() => setIsStcPrepOpen(true)} className="shrink-0">
                            Etablir le STC
                        </Button>
                    )}
                </div>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3">
                <div><h2 className="text-base font-bold text-[var(--color-fg)]">Paie et rémunération</h2><p className="text-xs text-[var(--color-fg-muted)]">Situation du salarié et historique des bulletins</p></div>
                <div className="flex flex-wrap gap-2">
                    {canWrite && <Button type="button" variant="primary" size="xs" onClick={() => setIsDemandeOpen(true)}>Demander un acompte / une avance</Button>}
                    <Button type="button" variant="outline" size="xs" onClick={chargerDonnees} iconLeft={<RefreshCw size={12} />}>Actualiser</Button>
                </div>
            </div>

            {/* Synthèse lisible en un coup d'œil, fournie par le backend. */}
            {synthese && <>
                <Card padding shadow="none" className="space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <div><h3 className="text-sm font-bold">Dernier bulletin</h3><p className="text-xs text-[var(--color-fg-muted)]">{synthese.dernierePeriodeLibelle || synthese.dernierePeriodeCode || 'Aucun bulletin'}{synthese.derniereDateCalcul ? ` · calculé le ${fmtDate(synthese.derniereDateCalcul)}` : ''}</p></div>
                        {synthese.dernierStatut && <Badge variant={synthese.dernierStatut === 'VALIDE' || synthese.dernierStatut === 'CLOTURE' ? 'success' : 'info'} size="xs">{synthese.dernierStatut}</Badge>}
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-2">
                        <StatCard title="Salaire de base" value={formatMoneyAr(synthese.salaireBaseContractuel)} subtext="Montant contractuel de l'affectation" icon={<Banknote size={16} />} color="brand" />
                        <StatCard title="Net du dernier bulletin" value={synthese.dernierBulletinId ? formatMoneyAr(synthese.dernierNet) : '—'} subtext={synthese.dernierBulletinId ? `Brut : ${formatMoneyAr(synthese.dernierBrut)}` : 'Aucun bulletin calculé'} icon={<FileText size={16} />} color="success" />
                        <StatCard title="Acomptes à imputer" value={formatMoneyAr(synthese.acomptesRestants)} subtext="Solde approuvé non soldé" icon={<Receipt size={16} />} color="neutral" />
                        <StatCard title="Avances en cours" value={formatMoneyAr(synthese.avancesRestantes)} subtext="Solde approuvé restant" icon={<Receipt size={16} />} color="neutral" />
                    </div>
                    {(synthese.demandesAcompteAvanceEnAttente ?? 0) > 0 && <p className="text-xs text-[var(--color-fg-muted)]">{synthese.demandesAcompteAvanceEnAttente} demande(s) en attente d’approbation · détail dans les mouvements ci-dessous.</p>}
                </Card>
                <AccordionSection id="paie-parametres" icon={<Calculator size={15} />} title="Paramètres de calcul et prêts" defaultOpen={false}>
                    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 text-xs">
                        <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-2"><dt className="text-[var(--color-fg-muted)]">Taux horaire</dt><dd className="font-semibold text-right">{formatMoneyAr(synthese.tauxHoraire)} / h{synthese.diviseurHeures ? ` · base ${synthese.diviseurHeures} h` : ''}</dd></div>
                        <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-2"><dt className="text-[var(--color-fg-muted)]">Taux congé journalier</dt><dd className="font-semibold text-right">{formatMoneyAr(synthese.tauxJournalierConge)}</dd></div>
                        <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-2"><dt className="text-[var(--color-fg-muted)]">Capital restant des prêts</dt><dd className="font-semibold text-right">{formatMoneyAr(synthese.capitalRestantPrets)}</dd></div>
                    </dl>
                </AccordionSection>
            </>}

            {/* ===== BULLETINS ===== */}
            <AccordionSection
                id="paie-bulletins"
                icon={<FileText size={15} />}
                title="Bulletins de paie"
                badge={bulletins.length > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 text-[10px] font-bold bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] rounded-none">
                        {bulletins.length}
                    </span>
                )}
                defaultOpen={true}
            >
                {sectionErrors.bulletins ? <Alert type="error" message={sectionErrors.bulletins} /> : bulletins.length === 0 ? (
                    <EmptyState title="Aucun bulletin de paie" description="Aucune paie n'a encore ete calculee ou cloturee pour ce salarie." />
                ) : (
                    <div className="overflow-x-auto -mx-4">
                        <Table className="w-full text-xs">
                            <Thead>
                                <Tr>
                                    <Th>Periode</Th>
                                    <Th>Type</Th>
                                        <Th>Calculé le</Th>
                                        <Th className="text-right">Brut</Th>
                                        <Th className="text-right font-bold">Net à payer</Th>
                                    <Th>Statut</Th>
                                    <Th className="text-right">Action</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {bulletins.map((b) => (
                                    <Tr key={b.idBulletin} className="hover:bg-[var(--color-bg-subtle)] transition-colors">
                                        <Td>
                                            <div className="font-semibold text-[var(--color-fg)] leading-tight">{b.codePeriode}</div>
                                            <span className="text-[10px] text-[var(--color-fg-muted)] block truncate max-w-[140px]">{b.libellePeriode}</span>
                                        </Td>
                                        <Td><Badge variant={b.typeBulletin === 'STC' ? 'warning' : 'default'} size="xs">{b.typeBulletin || 'MENSUEL'}</Badge></Td>
                                        <Td className="font-mono text-[var(--color-fg-muted)]">{fmtDate(b.dateCalcul)}</Td>
                                        <Td className="text-right font-mono">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                        <Td className="text-right font-mono font-bold text-[var(--color-fg)]">{formatMoneyAr(b.netAPayer)}</Td>
                                        <Td>
                                            <Badge variant={b.statut === 'VALIDE' || b.statut === 'CLOTURE' ? 'success' : b.statut === 'CALCULE' ? 'info' : 'default'} size="xs">{b.statut}</Badge>
                                        </Td>
                                        <Td className="text-right">
                                            <Button type="button" variant="outline" size="xs" iconLeft={<Eye size={12} />} onClick={() => handleVoirBulletin(b.idBulletin)}>Voir</Button>
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
                title="Mouvements et ajustements"
                badge={mouvements.length > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 text-[10px] font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] rounded-none">
                        {mouvements.length}
                    </span>
                )}
                defaultOpen={false}
            >
                {sectionErrors.mouvements ? <Alert type="error" message={sectionErrors.mouvements} /> : mouvements.length === 0 ? (
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
            {(hasStcNeeded || stcList.length > 0 || sectionErrors.stc) && (
                <AccordionSection
                    id="paie-stc"
                    icon={<Calculator size={15} />}
                    title="Solde de Tout Compte (STC)"
                    badge={hasStcNeeded && <Badge variant="warning" size="xs" className="ml-1">À établir</Badge>}
                    defaultOpen={stcList.length > 0}
                    variant={hasStcNeeded ? 'warning' : 'default'}
                >
                    {sectionErrors.stc ? <Alert type="error" message={sectionErrors.stc} /> : stcList.length > 0 ? (
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
                        <p className="text-xs text-[var(--color-fg-muted)] py-2">Aucun solde de tout compte enregistré.</p>
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
            {isDemandeOpen && <DemandeAcompteAvanceModal idEmploye={idEmploye} onClose={() => setIsDemandeOpen(false)} onCreated={() => { setFeedbackMsg({ type: 'success', message: 'Demande enregistrée en attente d’approbation.' }); chargerDonnees(); }} />}
        </div>
    );
}
