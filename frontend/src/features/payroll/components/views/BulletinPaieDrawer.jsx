import { useState, useEffect, useCallback, useRef } from 'react';
import {
    FileText,
    Printer,
    ExternalLink,
} from 'lucide-react';
import SlideOver from '@/components/ui/overlays/SlideOver';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import { formatMoneyAr } from '@/utils/moneyUtils';
import BulletinPaiePrintView from './BulletinPaiePrintView';

/** Consultation du bulletin calculé. */
export default function BulletinPaieDrawer({ isOpen, onClose, idBulletin }) {
    const [loading, setLoading]           = useState(false);
    const [data, setData]                 = useState(null);
    const [error, setError]               = useState(null);
    const [showPrintModal, setShowPrintModal] = useState(false);

    // Référence vers la zone de scroll du body du drawer pour revenir en haut.
    const bodyRef = useRef(null);

    const chargerBulletin = useCallback(async () => {
        if (!idBulletin) return;
        setLoading(true);
        setError(null);
        try {
            const res = await paieService.getBulletinComplet(idBulletin);
            setData(res);
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible de charger le bulletin.'));
        } finally {
            setLoading(false);
        }
    }, [idBulletin]);

    // Charge le bulletin à l'ouverture ou au changement d'ID.
    useEffect(() => {
        if (isOpen && idBulletin) {
            setData(null);
            // Scroll to top du drawer
            if (bodyRef.current) bodyRef.current.scrollTop = 0;
            chargerBulletin();
        } else {
            setData(null);
            setError(null);
        }
    }, [isOpen, idBulletin, chargerBulletin]);

    const formatAr      = (val) => val == null || val === '' ? 'Non renseigné' : formatMoneyAr(val);
    const formatMinutes = (totalMin) => {
        if (!totalMin || totalMin <= 0) return '0 min';
        const h = Math.floor(totalMin / 60), m = totalMin % 60;
        if (h === 0) return `${m} min`;
        if (m === 0) return `${h} h`;
        return `${h} h ${m} min`;
    };
    const formatHours = (value) => Number(value || 0).toFixed(2).replace('.', ',');

    const bulletin    = data?.bulletin;
    const explication = data?.explication;
    const fiscalite   = data?.explicationFiscalite;
    const lignes      = Array.isArray(data?.lignes) ? data.lignes : [];

    const nomAffiche      = bulletin ? (bulletin.nomPrenom || [bulletin.nom, bulletin.prenom].filter(Boolean).join(' ') || [bulletin.nomEmploye, bulletin.prenomEmploye].filter(Boolean).join(' ') || 'Employé') : '';
    const joursTravailles = bulletin ? (bulletin.joursTravailles ?? bulletin.nbJoursTravailles) : null;
    const heuresNormales  = bulletin?.heuresNormales;
    const netImposable    = bulletin ? (bulletin.revenuNetImposable ?? bulletin.salaireNetImposable ?? bulletin.netImposable) : null;

    return (
        <>
            <SlideOver
                isOpen={isOpen}
                onClose={onClose}
                title={bulletin ? `Bulletin de paie · ${nomAffiche}` : 'Bulletin de paie'}
                subtitle={bulletin
                    ? [`Matricule ${bulletin.matricule || 'non renseigné'}`, bulletin.codePeriode && `Période ${bulletin.codePeriode}`, bulletin.nomSite, bulletin.typeContrat].filter(Boolean).join(' · ')
                    : 'Chargement du bulletin'}
                icon={<FileText size={18} />}
                width="3xl"
                bodyRef={bodyRef}
                footer={
                    bulletin && (
                        <div className="flex flex-wrap items-center justify-between gap-2 w-full">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setShowPrintModal(true)}
                                className="flex items-center gap-1.5"
                            >
                                <Printer size={15} />
                                Imprimer ou exporter
                            </Button>
                            <div className="flex items-center gap-2">
                                <Button variant="secondary" size="sm" onClick={onClose}>
                                    Fermer
                                </Button>
                            </div>
                        </div>
                    )
                }
            >
                <div className="space-y-0 text-xs font-sans -m-4 sm:-m-5">

                    {loading && (
                        <div className="p-4 sm:p-5">
                            <SkeletonTable rows={8} cols={4} />
                        </div>
                    )}

                    {error && <Alert type="error" message={error} className="m-4" />}

                    {bulletin && (
                        <>
                            {/* ══ HERO HEADER — 4 métriques clés ══ */}
                            <div className="px-4 sm:px-5 pt-4 sm:pt-5 pb-3 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)]">
                                {/* Alerte variation importante */}
                                {bulletin.aAlerte === true && bulletin.ecartM1Pct != null && (
                                    <Alert type="warning" className="mb-3" message={`Variation du net · ${bulletin.ecartM1Pct > 0 ? '+' : ''}${bulletin.ecartM1Pct} % par rapport au mois précédent`} />
                                )}

                                {/* 4 KPIs financiers */}
                                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                                    <StatCard title="Brut" value={formatAr(bulletin.totalGainsBruts)} color="neutral" />
                                    <StatCard title="Cotisations salariales" value={formatAr(bulletin.totalCotisationsSalariales)} color="neutral" />
                                    <StatCard title="IRSA" value={formatAr(bulletin.irsaNet)} color="neutral" />
                                    <StatCard title="Net à payer" value={formatAr(bulletin.netAPayer)} color="brand" />
                                </div>

                                {/* Liens contextuels */}
                                <div className="flex items-center gap-3 pt-3">
                                    <a href={`/employes/${bulletin.idEmploye}/pointage`} target="_blank" rel="noreferrer"
                                        className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline">
                                        <ExternalLink size={11} />
                                        Pointage{bulletin.versionSnapshotPointage ? ` · v${bulletin.versionSnapshotPointage}` : ''}
                                    </a>
                                    <span className="text-[var(--color-fg-muted)]">·</span>
                                    <a href={`/employes/${bulletin.idEmploye}/conges`} target="_blank" rel="noreferrer"
                                        className="inline-flex items-center gap-1 text-[11px] font-semibold text-[var(--color-brand-600)] hover:underline">
                                        <ExternalLink size={11} /> Congés
                                    </a>
                                    <span className="text-[var(--color-fg-muted)]">·</span>
                                    <span className="text-[11px] text-[var(--color-fg-muted)] ml-auto font-mono">
                                        {joursTravailles != null ? `${joursTravailles}j travaillés${heuresNormales != null ? ` · ${formatHours(heuresNormales)} h` : ''}` : ''}
                                    </span>
                                </div>
                            </div>

                            <div className="p-4 sm:p-5 space-y-5">
                                <section aria-labelledby="bulletin-composition">
                                    <h3 id="bulletin-composition" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] mb-2">Montants du mois</h3>
                                        {/* Principaux écarts du mois */}
                                        <Card shadow="none" padding className="space-y-2">
                                            <div className="space-y-1.5 font-mono text-[11px]">
                                                <div className="flex justify-between items-center text-[var(--color-fg)]">
                                                    <span className="font-sans">Salaire de base contractuel</span>
                                                    <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-bold">+{formatAr(bulletin.salaireBase)}</span>
                                                </div>
                                                {Number(bulletin.retenueAbsence || explication?.montantRetenueAbsence) > 0 && (
                                                    <div className="flex justify-between items-center text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                        <span className="font-sans">Déficit horaire / Absences</span>
                                                        <span className="font-bold">-{formatAr(bulletin.retenueAbsence || explication?.montantRetenueAbsence)}</span>
                                                    </div>
                                                )}
                                                {explication && (explication.nbJoursConge || 0) > 0 && (
                                                    <div className="flex justify-between items-center">
                                                        <span className="font-sans">Congés pris ({explication.nbJoursConge} j · TCONGE)</span>
                                                        <span className={`font-bold ${explication.impactNetConge >= 0 ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                                            {explication.impactNetConge >= 0 ? `+${formatAr(explication.impactNetConge)}` : `-${formatAr(Math.abs(explication.impactNetConge))}`}
                                                        </span>
                                                    </div>
                                                )}
                                                {Number(bulletin.majorationHeuresSupplementaires || (explication ? (explication.montantHs30 || 0) + (explication.montantHs50 || 0) : 0)) > 0 && (
                                                    <div className="flex justify-between items-center text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                        <span className="font-sans">Heures supplémentaires</span>
                                                        <span className="font-bold">+{formatAr(bulletin.majorationHeuresSupplementaires || ((explication?.montantHs30 || 0) + (explication?.montantHs50 || 0)))}</span>
                                                    </div>
                                                )}
                                                {Number(bulletin.totalPrimesEtAutres || (bulletin.primePerformance || 0) + (bulletin.primeAnciennete || 0) + (bulletin.primeAssiduite || 0)) > 0 && (
                                                    <div className="flex justify-between items-center text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                        <span className="font-sans">Primes et autres gains</span>
                                                        <span className="font-bold">+{formatAr(bulletin.totalPrimesEtAutres || ((bulletin.primePerformance || 0) + (bulletin.primeAnciennete || 0) + (bulletin.primeAssiduite || 0)))}</span>
                                                    </div>
                                                )}
                                                {Number(bulletin.totalRetenuesAvances) > 0 && (
                                                    <div className="flex justify-between items-center text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                        <span className="font-sans">Acomptes & Avances prélevés</span>
                                                        <span className="font-bold">-{formatAr(bulletin.totalRetenuesAvances)}</span>
                                                    </div>
                                                )}
                                            </div>
                                        </Card>

                                </section>

                                <section aria-labelledby="bulletin-elements" className="space-y-3">
                                    <h3 id="bulletin-elements" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Éléments calculés</h3>
                                        {/* Congés Payés */}
                                        {explication && (explication.nbJoursConge || 0) > 0 && (
                                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2">
                                                <div className="flex justify-between items-center">
                                                    <span className="font-semibold text-xs text-[var(--color-fg)]">
                                                        Congés payés ({explication.nbJoursConge} j)
                                                    </span>
                                                    <span className={`font-mono font-bold text-xs ${explication.impactNetConge >= 0 ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                                        {explication.impactNetConge >= 0 ? `+${formatAr(explication.impactNetConge)}` : `-${formatAr(Math.abs(explication.impactNetConge))}`}
                                                    </span>
                                                </div>
                                                <div className="text-[11px] text-[var(--color-fg-muted)] pl-2.5 border-l-2 border-[var(--color-info-400)] dark:border-[var(--color-info-600)] space-y-0.5">
                                                    <div>Déduction base (1/30ème) : <span className="font-medium text-[var(--color-fg)]">-{formatAr(explication.deductionCongeTrentieme)}</span> ({explication.nbJoursConge} j × {formatAr(explication.tauxJournalierTrentieme)}/j)</div>
                                                    <div>Indemnité congé (TCONGE) : <span className="font-medium text-[var(--color-fg)]">+{formatAr(explication.indemniteConge)}</span> ({explication.nbJoursConge} j × {formatAr(explication.tauxJournalierConge)}/j)</div>
                                                </div>
                                            </div>
                                        )}

                                        {/* Retenue Absences */}
                                        {explication && (explication.montantRetenueAbsence > 0 || explication.minutesAbsenceRetenues > 0) && (
                                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 space-y-2">
                                                <div className="flex justify-between items-center">
                                                    <span className="font-semibold text-xs text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                        Retenue pour déficit de présence
                                                    </span>
                                                    <span className="font-mono font-bold text-xs text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                        -{formatAr(explication.montantRetenueAbsence)}
                                                    </span>
                                                </div>
                                                <div className="text-[11px] text-[var(--color-fg-muted)] pl-2.5 border-l-2 border-[var(--color-danger-400)] dark:border-[var(--color-danger-600)] space-y-0.5">
                                                    <div>Déficit net retenu : <span className="font-medium text-[var(--color-fg)]">{formatMinutes(explication.minutesAbsenceRetenues)}</span> ({explication.minutesAbsenceRetenues} min · {formatHours(explication.heuresAbsenceRetenues)} h)</div>
                                                    <div>Diviseur mensuel : <span className="font-medium text-[var(--color-fg)]">{explication.diviseurMensuelHeures} h</span> · Taux horaire : <span className="font-medium text-[var(--color-fg)]">{formatAr(explication.tauxHoraire)}/h</span></div>
                                                    {explication.minutesRetard > 0 && (
                                                        <div className="text-[10px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                                            Retards enregistrés · {formatMinutes(explication.minutesRetard)}
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        )}

                                        {/* Fallback absence sans explication */}
                                        {!explication && (bulletin.retenueAbsence || 0) > 0 && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">Retenue pour absences et retards</span>
                                                <span className="font-mono font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(bulletin.retenueAbsence)}</span>
                                            </div>
                                        )}

                                        {/* Heures supplémentaires */}
                                        {((bulletin.heuresHs30 || 0) > 0 || (bulletin.heuresHs50 || 0) > 0) && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-fg)]">Heures supplémentaires (+30%: {formatHours(bulletin.heuresHs30)}h / +50%: {formatHours(bulletin.heuresHs50)}h)</span>
                                                <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                    +{formatAr(bulletin.majorationHeuresSupplementaires || ((explication?.montantHs30 || 0) + (explication?.montantHs50 || 0)))}
                                                </span>
                                            </div>
                                        )}

                                        {/* Heures de nuit */}
                                        {(bulletin.heuresNuit || 0) > 0 && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-fg)]">Majoration de nuit (+30% / {bulletin.heuresNuit}h)</span>
                                                <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                    +{formatAr(bulletin.majorationHeuresNuit || (explication?.montantNuit ?? 0))}
                                                </span>
                                            </div>
                                        )}

                                        {/* Indemnités forfaitaires */}
                                        {bulletin.totalIndemnitesForfaitaires > 0 && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-fg)]">Indemnités repas et déplacement</span>
                                                <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">+{formatAr(bulletin.totalIndemnitesForfaitaires)}</span>
                                            </div>
                                        )}

                                        {!explication && (bulletin.retenueAbsence || 0) === 0 && (bulletin.heuresHs30 || 0) === 0 && (bulletin.heuresHs50 || 0) === 0 && !bulletin.totalIndemnitesForfaitaires && (
                                            <p className="text-[11px] text-[var(--color-fg-muted)] py-2">Aucun détail de pointage transmis.</p>
                                        )}
                                </section>

                                <section aria-labelledby="bulletin-fiscal" className="space-y-3">
                                    <h3 id="bulletin-fiscal" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Fiscalité et cotisations</h3>
                                        {/* Synthèse waterfall */}
                                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] divide-y divide-[var(--color-border-subtle)]">
                                            <div className="flex justify-between items-center px-3.5 py-2.5 font-bold text-xs">
                                                <span className="uppercase tracking-wide text-[var(--color-fg)]">TOTAL BRUT</span>
                                                <span className="font-mono text-sm text-[var(--color-fg)]">{formatAr(bulletin.totalGainsBruts)}</span>
                                            </div>
                                            <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                <span className="text-[var(--color-fg-muted)]">Cotisations salariales</span>
                                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                    {formatAr(bulletin.totalCotisationsSalariales)}
                                                </span>
                                            </div>
                                            <div className="flex justify-between items-center px-3.5 py-2.5 font-bold text-xs bg-[var(--color-bg-subtle)]">
                                                <span className="text-[var(--color-fg)]">Revenu net imposable</span>
                                                <span className="font-mono text-[var(--color-fg)]">{formatAr(fiscalite?.revenuNetImposable ?? netImposable)}</span>
                                            </div>
                                            <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                <span className="text-[var(--color-fg-muted)]">Base imposable IRSA</span>
                                                <span className="font-mono font-semibold text-[var(--color-fg)]">{formatAr(fiscalite?.assietteIrsa ?? bulletin.assietteIrsa ?? bulletin.baseIrsaArrondie)}</span>
                                            </div>
                                            <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                <span className="text-[var(--color-fg-muted)]">IRSA dû</span>
                                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(fiscalite?.irsaDu ?? bulletin.irsaDu ?? bulletin.irsaNet)}</span>
                                            </div>
                                            {Number(bulletin.totalRetenuesAvances) > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Acomptes et prêts prélevés</span>
                                                    <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(bulletin.totalRetenuesAvances)}</span>
                                                </div>
                                            )}
                                            <div className="flex justify-between items-center px-3.5 py-3 bg-[var(--color-ink-950)] text-[var(--color-on-color)]">
                                                <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-ink-300)]">NET À PAYER</span>
                                                <span className="font-mono font-black text-base text-[var(--color-on-color)]">{formatAr(bulletin.netAPayer)}</span>
                                            </div>
                                        </div>

                                        {/* Détail tranches IRSA */}
                                        {(fiscalite || bulletin.assietteIrsa != null || bulletin.baseIrsaArrondie != null) && (
                                            <Card shadow="none">
                                                <CardHeader><CardTitle size="sm">Calcul de l’IRSA</CardTitle></CardHeader>
                                                    <div data-testid="irsa-breakdown">
                                                        {(fiscalite?.detailsTranches || []).map((tranche, index) => (
                                                            <div key={`${tranche.borneInferieure}-${index}`} className="grid grid-cols-[1fr_auto_auto] gap-3 px-3.5 py-2 border-b last:border-b-0 border-[var(--color-border-subtle)] text-[11px]">
                                                                <span className="text-[var(--color-fg-muted)]">
                                                                    {formatAr(tranche.borneInferieure)} → {tranche.borneSuperieure == null ? 'au-delà' : formatAr(tranche.borneSuperieure)}
                                                                </span>
                                                                <span className="font-mono text-[var(--color-fg-muted)]">
                                                                    {formatAr(tranche.montantTaxable)} × {(Number(tranche.taux) * 100).toLocaleString('fr-FR')} %
                                                                </span>
                                                                <span className="font-mono font-semibold text-[var(--color-fg)]">{formatAr(tranche.impotProduit)}</span>
                                                            </div>
                                                        ))}
                                                        {(!fiscalite?.detailsTranches || fiscalite.detailsTranches.length === 0) && (
                                                            <p className="px-3.5 py-3 text-[11px] text-[var(--color-fg-muted)]">
                                                                Détail des tranches non fourni pour ce bulletin.
                                                            </p>
                                                        )}
                                                        <div className="grid grid-cols-2 gap-2 px-3.5 py-2.5 bg-[var(--color-bg-subtle)] text-[11px] border-t border-[var(--color-border-subtle)]">
                                                            <span className="text-[var(--color-fg-muted)]">IRSA calculé</span>
                                                            <span className="text-right font-mono font-semibold">{formatAr(fiscalite?.irsaCalcule ?? bulletin.irsaCalcule ?? bulletin.irsaBrut)}</span>
                                                            <span className="text-[var(--color-fg-muted)]">Réduction charges familiales</span>
                                                            <span className="text-right font-mono">-{formatAr(fiscalite?.reductionChargeFamille ?? bulletin.reductionChargeFamille ?? bulletin.reductionEnfants)}</span>
                                                            <span className="font-bold text-[var(--color-fg)]">IRSA dû (net)</span>
                                                            <span className="text-right font-mono font-bold text-[var(--color-danger-600)]">{formatAr(fiscalite?.irsaDu ?? bulletin.irsaDu ?? bulletin.irsaNet)}</span>
                                                        </div>
                                                    </div>
                                            </Card>
                                        )}

                                        {/* Charges patronales */}
                                        {(bulletin.totalCotisationsPatronales > 0 || bulletin.coutTotalEmployeur > 0) && (
                                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 text-[11px] space-y-1.5">
                                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">Coût Employeur</span>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">Cotisations patronales</span>
                                                    <span className="font-mono font-semibold">{formatAr(bulletin.totalCotisationsPatronales)}</span>
                                                </div>
                                                {bulletin.coutTotalEmployeur > 0 && (
                                                    <div className="flex justify-between items-center font-bold border-t border-[var(--color-border-subtle)] pt-1.5">
                                                        <span className="text-[var(--color-fg)]">Coût total employeur</span>
                                                        <span className="font-mono">{formatAr(bulletin.coutTotalEmployeur)}</span>
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                </section>

                                {lignes.length > 0 && (
                                    <section aria-labelledby="bulletin-rubriques" className="space-y-2">
                                        <h3 id="bulletin-rubriques" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Résultats détaillés · {lignes.length} rubriques</h3>
                                        <Card shadow="none">
                                                {/* Méta-données */}
                                                <div className="px-3 py-1.5 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                    <span>Bulletin n° {bulletin.idBulletin}</span>
                                                    {bulletin.codePeriode && <span>Période {bulletin.codePeriode}</span>}
                                                </div>
                                                <div className="overflow-x-auto border-t border-[var(--color-border-subtle)]">
                                                    <table className="w-full text-left border-collapse">
                                                        <thead>
                                                            <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[10px] text-[var(--color-fg-muted)] uppercase">
                                                                <th className="p-2">Désignation</th>
                                                                <th className="p-2 text-right">Base</th>
                                                                <th className="p-2 text-right">Gain</th>
                                                                <th className="p-2 text-right">Retenue</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody className="divide-y divide-[var(--color-border-subtle)]">
                                                            {lignes.map((l, i) => {
                                                                const isRetenue = l.sens === 'RETENUE';
                                                                const isGain    = l.sens === 'GAIN' || l.sens === 'INFO';
                                                                const montantRet = isRetenue ? (l.montantSalarial ?? l.montantRetenue) : (l.montantRetenue && Number(l.montantRetenue) > 0 ? l.montantRetenue : null);
                                                                const montantGn  = isGain    ? (l.montantSalarial ?? l.montantGain)    : (l.montantGain    && Number(l.montantGain) > 0    ? l.montantGain    : null);
                                                                return (
                                                                    <tr key={i} className="hover:bg-[var(--color-bg-subtle)]/40 font-mono text-[11px]">
                                                                        <td className="p-2 font-sans font-medium text-[var(--color-fg)]">{l.libelleRubrique}</td>
                                                                        <td className="p-2 text-right text-[var(--color-fg-muted)]">{formatAr(l.baseCalcul)}</td>
                                                                        <td className="p-2 text-right text-[var(--color-success-600)] font-bold">{montantGn && Number(montantGn) > 0 ? formatAr(montantGn) : '0 Ar'}</td>
                                                                        <td className="p-2 text-right text-[var(--color-danger-600)] font-bold">{montantRet && Number(montantRet) > 0 ? formatAr(montantRet) : '0 Ar'}</td>
                                                                    </tr>
                                                                );
                                                            })}
                                                        </tbody>
                                                    </table>
                                                </div>
                                        </Card>
                                    </section>
                                )}
                            </div>
                        </>
                    )}
                </div>
            </SlideOver>

            {/* Modal d'impression A4 */}
            {showPrintModal && data && (
                <BulletinPaiePrintView
                    bulletinComplet={data}
                    onClose={() => setShowPrintModal(false)}
                />
            )}
        </>
    );
}
