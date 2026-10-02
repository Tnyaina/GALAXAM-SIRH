import { useState, useEffect, useCallback, useRef } from 'react';
import {
    FileText,
    FileDown,
    FileSpreadsheet,
    ChevronDown,
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
import BulletinVariableCorrection from './BulletinVariableCorrection';

/** Consultation du bulletin calculé. */
export default function BulletinPaieDrawer({ isOpen, onClose, idBulletin, onRecalculateSuccess }) {
    const [loading, setLoading]           = useState(false);
    const [data, setData]                 = useState(null);
    const [error, setError]               = useState(null);
    const [documentLoading, setDocumentLoading] = useState(false);
    const [exportOpen, setExportOpen] = useState(false);
    const exportRef = useRef(null);

    useEffect(() => {
        const close = event => { if (!exportRef.current?.contains(event.target)) setExportOpen(false); };
        const escape = event => { if (event.key === 'Escape') setExportOpen(false); };
        document.addEventListener('pointerdown', close);
        document.addEventListener('keydown', escape);
        return () => { document.removeEventListener('pointerdown', close); document.removeEventListener('keydown', escape); };
    }, []);

    const openDocument = async (format) => {
        setExportOpen(false);
        setDocumentLoading(true);
        setError(null);
        try {
            const { blob, filename } = await paieService.getBulletinDocument(idBulletin, format);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (cause) {
            setError(getErrorMessage(cause, 'Impossible de générer le document.'));
        } finally {
            setDocumentLoading(false);
        }
    };

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

    // Les montants enregistrés ont deux décimales. Les décimales nulles restent masquées.
    const formatAr = (val) => val == null || val === '' ? 'Non renseigné' : formatMoneyAr(val);
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
    const netAvantImpot = bulletin?.salaireNetImposable;
    const retenueIsi = Number(bulletin?.retenueIsi5 || 0);
    const indemniteNet = Number(bulletin?.totalIndemnitesNonImposables || 0);
    const retenuesTresorerie = Number(bulletin?.totalRetenuesAcomptePret || 0);
    const ajustementArrondi = bulletin?.netAvantArrondi != null && bulletin?.netAPayer != null
        ? Math.round((Number(bulletin.netAPayer) - Number(bulletin.netAvantArrondi)) * 100) / 100
        : null;
    const estIrsa = !retenueIsi && lignes.some(l => l.codeRubrique === 'IMPOT_IRSA_NET' || l.codeRubrique === 'IMPOT_IRSA_BRUT');
    const gainsBruts = lignes.filter(l => l.sens === 'GAIN' && (l.etapeCalcul
        ? l.etapeCalcul === 'BRUT'
        : l.imposable !== false && l.categorie !== 'NET' && l.categorie !== 'INDEMNITE_NON_IMPOSABLE'));
    const retenuesBrut = lignes.filter(l => l.sens === 'RETENUE' && l.categorie === 'SALAIRE_BASE');
    const hsExonereeIrsa = Number(fiscalite?.remunerationHsExonereeIrsa ?? lignes.find(l => l.codeRubrique === 'HS_EXONEREE_IRSA')?.montantSalarial ?? 0);
    const gainsBrutsExoneres = lignes.filter(l => l.sens === 'GAIN' && l.etapeCalcul === 'BRUT' && l.imposable === false)
        .reduce((total, l) => total + Number(l.montantSalarial || 0), 0);
    const totalLignesBrut = gainsBruts.reduce((total, l) => total + Number(l.montantSalarial || 0), 0)
        - retenuesBrut.reduce((total, l) => total + Number(l.montantSalarial || 0), 0);

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
                            <div className="relative" ref={exportRef}>
                                <Button variant="primary" size="sm" onClick={() => setExportOpen(open => !open)}
                                    disabled={documentLoading} aria-expanded={exportOpen} aria-haspopup="menu"
                                    className="flex items-center gap-2 font-semibold">
                                    <FileDown size={15} /> Exporter <ChevronDown size={14} />
                                </Button>
                                {exportOpen && <div role="menu" className="absolute bottom-full left-0 mb-2 w-64 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xl z-50">
                                    <button type="button" role="menuitem" onClick={() => openDocument('xlsx')}
                                        className="w-full px-4 py-3 flex items-center gap-3 text-left hover:bg-[var(--color-bg-subtle)]">
                                        <FileSpreadsheet size={19} className="text-[var(--color-success-600)]" />
                                        <span><strong className="block text-sm">Format Excel (.xlsx)</strong><span className="block text-xs text-[var(--color-fg-muted)]">Bulletin détaillé et montants</span></span>
                                    </button>
                                    <button type="button" role="menuitem" onClick={() => openDocument('pdf')}
                                        className="w-full px-4 py-3 flex items-center gap-3 text-left border-t border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]">
                                        <FileText size={19} className="text-[var(--color-danger-600)]" />
                                        <span><strong className="block text-sm">Bulletin PDF</strong><span className="block text-xs text-[var(--color-fg-muted)]">Télécharger au format A4</span></span>
                                    </button>
                                </div>}
                            </div>
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

                                {/* Montants principaux du bulletin */}
                                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5">
                                    <StatCard title="Brut" value={formatAr(bulletin.totalGainsBruts)} color="neutral" />
                                    <StatCard title="Cotisations salariales" value={formatAr(bulletin.totalCotisationsSalariales)} color="neutral" />
                                    {retenueIsi > 0
                                        ? <StatCard title="ISI" value={formatAr(bulletin.retenueIsi5)} color="neutral" />
                                        : estIrsa && <StatCard title="IRSA" value={formatAr(bulletin.irsaNet)} color="neutral" />}
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
                                {bulletin.idPeriodePaie && bulletin.idEmploye && <BulletinVariableCorrection
                                    key={`${bulletin.idPeriodePaie}-${bulletin.idEmploye}`}
                                    bulletin={bulletin}
                                    onBulletinUpdated={chargerBulletin}
                                    onCampaignUpdated={onRecalculateSuccess}
                                />}
                                <section aria-labelledby="bulletin-composition">
                                    <h3 id="bulletin-composition" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] mb-2">Montants du mois</h3>
                                        <Card shadow="none" padding className="space-y-2">
                                            <div className="space-y-1.5 font-mono text-[11px]">
                                                {gainsBruts.length || retenuesBrut.length ? (
                                                    <>
                                                        {[...gainsBruts, ...retenuesBrut].sort((a, b) => Number(a.ordre || 0) - Number(b.ordre || 0)).map((l, index) => (
                                                            <div key={`${l.codeRubrique}-${index}`} className="flex justify-between gap-4 text-[var(--color-fg)]">
                                                                <span className="font-sans">{l.libelleRubrique}{l.commentaire && <span className="block text-xs font-normal text-[var(--color-fg-muted)]">{l.commentaire}</span>}</span>
                                                                <span className={`font-bold whitespace-nowrap ${l.sens === 'GAIN' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                                                    {l.sens === 'GAIN' ? '+' : '−'}{formatAr(l.montantSalarial)}
                                                                </span>
                                                            </div>
                                                        ))}
                                                        <div className="flex justify-between gap-4 border-t border-[var(--color-border-subtle)] pt-2 font-bold">
                                                            <span className="font-sans">Total brut</span>
                                                            <span>{formatAr(bulletin.totalGainsBruts)}</span>
                                                        </div>
                                                        {Math.abs(Math.round((totalLignesBrut - Number(bulletin.totalGainsBruts || 0)) * 100)) > 0 && (
                                                            <p className="font-sans text-[var(--color-warning-700)]">Les rubriques ne correspondent pas au total brut enregistré.</p>
                                                        )}
                                                    </>
                                                ) : <p className="font-sans text-[var(--color-fg-muted)]">Détail du brut indisponible.</p>}
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
                                        {!explication && Number(lignes.find(l => l.codeRubrique === 'RETENUE_ABSENCE')?.montantSalarial || 0) > 0 && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">Retenue pour absences et retards</span>
                                                <span className="font-mono font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">-{formatAr(lignes.find(l => l.codeRubrique === 'RETENUE_ABSENCE')?.montantSalarial)}</span>
                                            </div>
                                        )}

                                        {/* Heures supplémentaires */}
                                        {((bulletin.heuresHs30 || 0) > 0 || (bulletin.heuresHs50 || 0) > 0) && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-fg)]">Heures supplémentaires (+30%: {formatHours(bulletin.heuresHs30)}h / +50%: {formatHours(bulletin.heuresHs50)}h)</span>
                                                <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                    +{formatAr(Number(explication?.montantHs30 || 0) + Number(explication?.montantHs50 || 0))}
                                                </span>
                                            </div>
                                        )}

                                        {/* Heures de nuit */}
                                        {(bulletin.heuresNuit || 0) > 0 && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-fg)]">Majoration de nuit (+30% / {bulletin.heuresNuit}h)</span>
                                                <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                    +{formatAr(explication?.montantNuit)}
                                                </span>
                                            </div>
                                        )}

                                        {/* Indemnités forfaitaires */}
                                        {indemniteNet > 0 && (
                                            <div className="flex justify-between items-center p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
                                                <span className="text-[var(--color-fg)]">Indemnités et remboursements non imposables</span>
                                                <span className="font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">+{formatAr(bulletin.totalIndemnitesNonImposables)}</span>
                                            </div>
                                        )}

                                        {!explication && !lignes.some(l => ['RETENUE_ABSENCE', 'HEURES_SUP_30', 'HEURES_SUP_50'].includes(l.codeRubrique)) && indemniteNet === 0 && (
                                            <p className="text-[11px] text-[var(--color-fg-muted)] py-2">Aucun détail de pointage transmis.</p>
                                        )}
                                </section>

                                <section aria-labelledby="bulletin-fiscal" className="space-y-3">
                                    <h3 id="bulletin-fiscal" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Du brut au net à payer</h3>
                                        {/* Synthèse waterfall */}
                                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] divide-y divide-[var(--color-border-subtle)]">
                                            <div className="flex justify-between items-center px-3.5 py-2.5 font-bold text-xs">
                                                <span className="uppercase tracking-wide text-[var(--color-fg)]">TOTAL BRUT</span>
                                                <span className="font-mono text-sm text-[var(--color-fg)]">{formatAr(bulletin.totalGainsBruts)}</span>
                                            </div>
                                            <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                <span className="text-[var(--color-fg-muted)]">Cotisations salariales</span>
                                                <span className="font-mono font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                    −{formatAr(bulletin.totalCotisationsSalariales)}
                                                </span>
                                            </div>
                                            <div className="flex justify-between items-center px-3.5 py-2.5 font-bold text-xs bg-[var(--color-bg-subtle)]">
                                                <span className="text-[var(--color-fg)]">Net après cotisations</span>
                                                <span className="font-mono text-[var(--color-fg)]">{formatAr(netAvantImpot)}</span>
                                            </div>
                                            {estIrsa && gainsBrutsExoneres > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Gains du brut exclus de l’assiette IRSA</span>
                                                    <span className="font-mono">−{formatAr(gainsBrutsExoneres)}</span>
                                                </div>
                                            )}
                                            {estIrsa && hsExonereeIrsa > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Heures supplémentaires exclues de la base IRSA</span>
                                                    <span className="font-mono">−{formatAr(hsExonereeIrsa)}</span>
                                                </div>
                                            )}
                                            {estIrsa && (hsExonereeIrsa > 0 || gainsBrutsExoneres > 0) && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Revenu soumis à l’IRSA</span>
                                                    <span className="font-mono">{formatAr(bulletin.revenuNetImposable)}</span>
                                                </div>
                                            )}
                                            {estIrsa && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Base fiscale IRSA (arrondie)</span>
                                                    <span className="font-mono">{formatAr(bulletin.baseIrsaArrondie)}</span>
                                                </div>
                                            )}
                                            {estIrsa && Number(bulletin.irsaNet || 0) > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">IRSA prélevé</span>
                                                    <span className="font-mono font-semibold text-[var(--color-danger-600)]">−{formatAr(bulletin.irsaNet)}</span>
                                                </div>
                                            )}
                                            {retenueIsi > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">ISI prélevé</span>
                                                    <span className="font-mono font-semibold text-[var(--color-danger-600)]">−{formatAr(bulletin.retenueIsi5)}</span>
                                                </div>
                                            )}
                                            {indemniteNet > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Indemnités et remboursements non imposables</span>
                                                    <span className="font-mono font-semibold text-[var(--color-success-600)]">+{formatAr(bulletin.totalIndemnitesNonImposables)}</span>
                                                </div>
                                            )}
                                            {retenuesTresorerie > 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Acomptes, prêts et autres retenues</span>
                                                    <span className="font-mono font-semibold text-[var(--color-danger-600)]">−{formatAr(bulletin.totalRetenuesAcomptePret)}</span>
                                                </div>
                                            )}
                                            {bulletin.netAvantArrondi != null && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Net avant arrondi</span>
                                                    <span className="font-mono font-semibold">{formatAr(bulletin.netAvantArrondi)}</span>
                                                </div>
                                            )}
                                            {ajustementArrondi !== null && ajustementArrondi !== 0 && (
                                                <div className="flex justify-between items-center px-3.5 py-2 text-[11px]">
                                                    <span className="text-[var(--color-fg-muted)]">Ajustement à la dizaine d’ariary</span>
                                                    <span className="font-mono font-semibold">{ajustementArrondi > 0 ? '+' : '−'}{formatAr(Math.abs(ajustementArrondi))}</span>
                                                </div>
                                            )}
                                            <div className="flex justify-between items-center px-3.5 py-3 bg-[var(--color-ink-950)] text-[var(--color-on-color)]">
                                                <span className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-ink-300)]">NET À PAYER</span>
                                                <span className="font-mono font-black text-base text-[var(--color-on-color)]">{formatAr(bulletin.netAPayer)}</span>
                                            </div>
                                        </div>

                                        {/* Détail tranches IRSA */}
                                        {estIrsa && (
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
                                        {(Number(bulletin.totalCotisationsPatronales) > 0 || Number(bulletin.coutGlobalEmployeur) > 0) && (
                                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 text-[11px] space-y-1.5">
                                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">Coût Employeur</span>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">Cotisations patronales</span>
                                                    <span className="font-mono font-semibold">{formatAr(bulletin.totalCotisationsPatronales)}</span>
                                                </div>
                                                {Number(bulletin.coutGlobalEmployeur) > 0 && (
                                                    <div className="flex justify-between items-center font-bold border-t border-[var(--color-border-subtle)] pt-1.5">
                                                        <span className="text-[var(--color-fg)]">Coût total employeur</span>
                                                        <span className="font-mono">{formatAr(bulletin.coutGlobalEmployeur)}</span>
                                                    </div>
                                                )}
                                            </div>
                                        )}
                                </section>

                                {lignes.length > 0 && (
                                    <section aria-labelledby="bulletin-rubriques" className="space-y-3">
                                        {(() => {
                                            // Séparer rubriques salariales et patronales
                                            const lignesSal = lignes.filter(l => !l.categorie?.includes('PATRONAL') && !l.codeRubrique?.endsWith('_PAT') && l.sens !== 'PATRONAL');
                                            const lignesPat = lignes.filter(l =>  l.categorie?.includes('PATRONAL') ||  l.codeRubrique?.endsWith('_PAT') || l.sens === 'PATRONAL');

                                            const renderTableSalarial = (rows) => (
                                                <div>
                                                    <h3 id="bulletin-rubriques" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] mb-1.5">
                                                        Résultats salarié · {rows.length} rubriques
                                                    </h3>
                                                    <Card shadow="none">
                                                        <div className="px-3 py-1.5 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                            <span>Bulletin n° {bulletin.idBulletin}</span>
                                                            {bulletin.codePeriode && <span>Période {bulletin.codePeriode}</span>}
                                                        </div>
                                                        <div className="overflow-x-auto border-t border-[var(--color-border-subtle)]">
                                                            <table className="w-full text-left border-collapse">
                                                                <thead>
                                                                    <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[10px] text-[var(--color-fg-muted)] uppercase">
                                                                        <th className="p-2">Désignation</th>
                                                                        <th className="p-2 text-right">Base ou taux</th>
                                                                        <th className="p-2 text-right">Gain</th>
                                                                        <th className="p-2 text-right">Retenue</th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody className="divide-y divide-[var(--color-border-subtle)]">
                                                                    {rows.map((l, i) => {
                                                                        const isRetenue = l.sens === 'RETENUE';
                                                                        const isGain    = l.sens === 'GAIN';
                                                                        const isInfo    = l.sens === 'INFO';
                                                                        // Montants salarié
                                                                        const rawRet = isRetenue ? (l.montantSalarial ?? l.montantRetenue) : null;
                                                                        const rawGn  = isGain    ? (l.montantSalarial ?? l.montantGain)    : null;
                                                                        const retVal = rawRet != null ? Number(rawRet) : 0;
                                                                        const gnVal  = rawGn  != null ? Number(rawGn)  : 0;
                                                                        const baseVal = l.categorie !== 'NET' && l.baseCalcul != null ? Number(l.baseCalcul) : null;
                                                                        return (
                                                                            <tr key={i} className="hover:bg-[var(--color-bg-subtle)]/40 font-mono text-[11px]">
                                                                                <td className="p-2 font-sans font-medium text-[var(--color-fg)]">
                                                                                    {l.libelleRubrique}
                                                                                    {isInfo && (
                                                                                        <span className="ml-1.5 text-[9px] font-mono px-1 py-0.5 rounded bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]">
                                                                                            info
                                                                                        </span>
                                                                                    )}
                                                                                </td>
                                                                                <td className="p-2 text-right text-[var(--color-fg-muted)]">{baseVal != null ? formatAr(baseVal) : '—'}</td>
                                                                                <td className="p-2 text-right text-[var(--color-success-600)] font-bold">{gnVal > 0 ? formatAr(gnVal) : <span className="text-[var(--color-fg-subtle)] font-normal">—</span>}</td>
                                                                                <td className="p-2 text-right text-[var(--color-danger-600)] font-bold">{retVal > 0 ? formatAr(retVal) : <span className="text-[var(--color-fg-subtle)] font-normal">—</span>}</td>
                                                                            </tr>
                                                                        );
                                                                    })}
                                                                </tbody>
                                                            </table>
                                                        </div>
                                                    </Card>
                                                </div>
                                            );

                                            const renderTablePatronal = (rows) => (
                                                <div>
                                                    <h3 id="bulletin-rubriques-pat" className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] mb-1.5">
                                                        Charges patronales · {rows.length} rubriques
                                                    </h3>
                                                    <Card shadow="none">
                                                        <div className="px-3 py-1.5 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                            <span>Part employeur</span>
                                                            {bulletin.codePeriode && <span>Période {bulletin.codePeriode}</span>}
                                                        </div>
                                                        <div className="overflow-x-auto border-t border-[var(--color-border-subtle)]">
                                                            <table className="w-full text-left border-collapse">
                                                                <thead>
                                                                    <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[10px] text-[var(--color-fg-muted)] uppercase">
                                                                        <th className="p-2">Désignation</th>
                                                                        <th className="p-2 text-right">Base</th>
                                                                        <th className="p-2 text-right">Taux</th>
                                                                        <th className="p-2 text-right">Cotisation patronale</th>
                                                                    </tr>
                                                                </thead>
                                                                <tbody className="divide-y divide-[var(--color-border-subtle)]">
                                                                    {rows.map((l, i) => {
                                                                        const rawPat = l.montantPatronal ?? l.montantRetenue;
                                                                        const patVal = rawPat != null ? Number(rawPat) : 0;
                                                                        const baseVal = l.baseCalcul != null ? Number(l.baseCalcul) : null;
                                                                        const tauxVal = l.tauxPatronal != null ? Number(l.tauxPatronal) : null;
                                                                        return (
                                                                            <tr key={i} className="hover:bg-[var(--color-bg-subtle)]/40 font-mono text-[11px]">
                                                                                <td className="p-2 font-sans font-medium text-[var(--color-fg)]">{l.libelleRubrique}</td>
                                                                                <td className="p-2 text-right text-[var(--color-fg-muted)]">{baseVal != null ? formatAr(baseVal) : '—'}</td>
                                                                                <td className="p-2 text-right text-[var(--color-fg-muted)]">{tauxVal != null ? `${tauxVal}%` : '—'}</td>
                                                                                <td className="p-2 text-right font-bold text-[var(--color-fg)]">{patVal > 0 ? formatAr(patVal) : <span className="text-[var(--color-fg-subtle)] font-normal">—</span>}</td>
                                                                            </tr>
                                                                        );
                                                                    })}
                                                                </tbody>
                                                            </table>
                                                        </div>
                                                    </Card>
                                                </div>
                                            );

                                            return (
                                                <>
                                                    {renderTableSalarial(lignesSal)}
                                                    {lignesPat.length > 0 && renderTablePatronal(lignesPat)}
                                                </>
                                            );
                                        })()}
                                    </section>
                                )}
                            </div>
                        </>
                    )}
                </div>
            </SlideOver>

        </>
    );
}
