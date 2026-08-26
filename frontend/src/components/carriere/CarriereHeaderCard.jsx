import {
    Briefcase, Calendar, Clock, Award, DollarSign, MapPin, FileText, UserCircle, CreditCard
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate, fmtMoney } from '@/utils/carriereUtils';

function StatItem({ icon, label, value, subtext, highlight = false }) {
    return (
        <div className="flex items-center gap-2.5 min-w-0">
            <div className="text-ink-350 dark:text-ink-500 flex-shrink-0">{icon}</div>
            <div className="min-w-0">
                <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400 truncate">{label}</p>
                <p className={`text-sm font-semibold truncate ${highlight ? 'text-brand-500 font-mono' : 'text-ink-900 dark:text-ink-50'}`}>
                    {value || '-'}
                </p>
                {subtext && (
                    <p className="text-[11px] font-medium text-ink-400 dark:text-ink-400 truncate">
                        {subtext}
                    </p>
                )}
            </div>
        </div>
    );
}

export default function CarriereHeaderCard({
    employe,
    contratActif,
    affectationActive,
    brouillonWf,
    etatEssai,
    workflowPlanifieFutur,
    estEssaiRenouveleFlag,
    canWrite,
    onOpenConfirmation,
    onOpenRenouvellement,
}) {
    const rawMode = affectationActive?.libelleModeCalculPaie || affectationActive?.libelleModePaie || 'Mensuel fixe';
    const isHoraire = rawMode.toLowerCase().includes('horaire');
    const isJournalier = rawMode.toLowerCase().includes('journal');
    const periodiciteLabel = isHoraire ? 'Horaire' : isJournalier ? 'Journalier' : 'Mensuel';
    const uniteLabel = isHoraire ? '/ heure' : isJournalier ? '/ jour' : '/ mois';

    const salaireValeur = affectationActive?.salaireBase !== undefined
        ? `${fmtMoney(affectationActive.salaireBase)} ${uniteLabel}`
        : null;

    return (
        <div className="space-y-4">
            {/* 📊 STATISTIQUES : Statut Contractuel Actuel */}
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-none shadow-sm p-5">
                <div className="flex flex-wrap items-center gap-x-8 gap-y-4">
                    <div className="flex items-center gap-3 pr-8 border-r border-ink-150 dark:border-ink-700 last:border-0">
                        <div className={`w-10 h-10 rounded-none flex items-center justify-center flex-shrink-0 ${contratActif ? 'bg-success-500/10 text-success-600' : brouillonWf ? 'bg-warning-500/10 text-warning-600' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'
                            }`}>
                            <Briefcase size={18} />
                        </div>
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Statut Actuel</p>
                            {contratActif ? (
                                <Badge variant="success" size="sm" dot>ACTIF</Badge>
                            ) : brouillonWf ? (
                                <Badge variant="warning" size="sm" dot>BROUILLON EN ATTENTE</Badge>
                            ) : (
                                <Badge variant="default" size="sm" dot>AUCUN CONTRAT</Badge>
                            )}
                        </div>
                    </div>

                    <StatItem
                        icon={<FileText size={16} />}
                        label="Type de Contrat"
                        value={contratActif?.libelleTypeContrat}
                    />
                    <StatItem
                        icon={<UserCircle size={16} />}
                        label="Intervenant"
                        value={contratActif?.libelleTypeIntervenant}
                    />
                    <StatItem
                        icon={<Award size={16} />}
                        label="Fonction Actuelle"
                        value={affectationActive?.libelleFonction}
                    />
                    <StatItem
                        icon={<Calendar size={16} />}
                        label="Date d'Entrée"
                        value={contratActif?.dateDebut ? fmtDate(contratActif.dateDebut) : null}
                    />
                    <StatItem
                        icon={<MapPin size={16} />}
                        label="Site / Département"
                        value={
                            affectationActive
                                ? `${affectationActive.libelleSite || '-'} / ${affectationActive.libelleDepartement || '-'}`
                                : null
                        }
                    />
                    <StatItem
                        icon={<DollarSign size={16} />}
                        label={`Salaire Actuel (${periodiciteLabel})`}
                        value={salaireValeur}
                        subtext={rawMode}
                        highlight
                    />
                </div>
            </div>

            {/* ⚠️ BANNIÈRE PÉRIODE D'ESSAI EN COURS */}
            {etatEssai?.aPeriodeEssai && etatEssai?.dateFinEssai && (
                <div className={`p-4 rounded-none border flex flex-col md:flex-row md:items-center justify-between gap-4 ${etatEssai.estPasse
                        ? 'bg-danger-500/10 border-danger-500/30 text-danger-800 dark:text-danger-300'
                        : 'bg-warning-500/10 border-warning-500/30 text-warning-900 dark:text-warning-200'
                    }`}>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-none bg-white dark:bg-ink-900 shadow-sm flex-shrink-0 mt-0.5">
                            <Clock className={etatEssai.estPasse ? 'text-danger-500' : 'text-warning-500'} size={20} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h4 className="text-xs font-bold uppercase tracking-wider">
                                    Période d'essai en cours {estEssaiRenouveleFlag && '(Renouvelée)'}
                                </h4>
                                {etatEssai.joursRestants !== null && (
                                    <Badge variant={etatEssai.estPasse ? 'danger' : 'warning'} size="xs">
                                        {etatEssai.estPasse
                                            ? `Dépassée de ${Math.abs(etatEssai.joursRestants)} jour(s)`
                                            : `Reste ${etatEssai.joursRestants} jour(s)`}
                                    </Badge>
                                )}
                            </div>
                            <p className="text-xs mt-1 text-ink-600 dark:text-ink-300">
                                Échéance prévue le <strong>{fmtDate(etatEssai.dateFinEssai)}</strong>.
                                {workflowPlanifieFutur
                                    ? ` Un événement (${workflowPlanifieFutur.libelleTypeEvenement || workflowPlanifieFutur.codeTypeEvenement}) est déjà planifié au ${fmtDate(workflowPlanifieFutur.dateEffet)}.`
                                    : ' Aucune action RH de confirmation ou de rupture n\'a encore été validée pour cet essai.'}
                            </p>
                        </div>
                    </div>

                    {canWrite && !workflowPlanifieFutur && (
                        <div className="flex items-center gap-2 self-end md:self-auto flex-shrink-0">
                            {!estEssaiRenouveleFlag && (
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    onClick={onOpenRenouvellement}
                                >
                                    Renouveler l'essai
                                </Button>
                            )}
                            <Button
                                variant="primary"
                                size="xs"
                                onClick={onOpenConfirmation}
                            >
                                Confirmer l'essai
                            </Button>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
