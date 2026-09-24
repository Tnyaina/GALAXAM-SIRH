import { useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { Printer, X, Download, Calendar, Clock, Scale } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { formatMonnaie, getPeriodeLabelForFilename } from '@/features/attendance/utils/attendanceDisplayUtils';

/**
 * Modale et vue d'impression officielle / export PDF de la fiche de pointage.
 * Conception sobre, corporate et professionnelle (KISS).
 * Optimisée pour le rendu A4 portrait via @media print sans aucune coupure.
 */
export default function PointageFichePrintModal({
    isOpen,
    onClose,
    employe,
    ficheData
}) {
    // Référence DOM de la zone imprimable
    const printRef = useRef(null);

    // Données dérivées : métadonnées backend prioritaires, puis repli sur le collaborateur.
    const nom = employe?.nom || (ficheData?.nom ? ficheData.nom : (ficheData?.nomComplet ? ficheData.nomComplet.split(' ')[0] : 'Employe'));
    const prenom = employe?.prenom || (ficheData?.prenom ? ficheData.prenom : (ficheData?.nomComplet ? ficheData.nomComplet.split(' ').slice(1).join(' ') : ''));
    const pInitial = prenom ? prenom.trim().charAt(0).toUpperCase() : 'P';

    const matricule = ficheData?.matricule && ficheData.matricule !== '—'
        ? ficheData.matricule
        : (employe?.matricule || '0000');

    const matClean = matricule && matricule !== '—' ? matricule : '0000';
    const perClean = getPeriodeLabelForFilename(ficheData?.libellePeriode, ficheData?.codePeriode);
    const nomClean = nom.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9_-]/g, '_');
    const docRefName = `Pointage_${nomClean}_${pInitial}_${matClean}_${perClean}`;

    // Effet : donne un nom explicite au document pendant l'impression.
    useEffect(() => {
        if (isOpen) {
            const prevTitle = document.title;
            document.title = docRefName;
            return () => {
                document.title = prevTitle;
            };
        }
    }, [isOpen, docRefName]);

    // Rendu : garde d'ouverture et de données.
    if (!isOpen || !ficheData) return null;

    const nomComplet = ficheData?.nomComplet && ficheData.nomComplet !== 'Collaborateur'
        ? ficheData.nomComplet
        : (employe ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() : 'Collaborateur');

    const siteNom = ficheData?.siteNom && ficheData.siteNom !== '—'
        ? ficheData.siteNom
        : (employe?.nomSite || employe?.siteNom || employe?.site || 'Siège');

    const fonction = ficheData?.fonction && ficheData.fonction !== '—'
        ? ficheData.fonction
        : (employe?.nomFonction || employe?.fonction || employe?.intitulePoste || 'Collaborateur');

    const categorie = ficheData?.categorie && ficheData.categorie !== '—'
        ? ficheData.categorie
        : (employe?.codeCategorie || employe?.categorie || employe?.libelleCategoriePro || '—');

    const kpis = ficheData.kpis || {};
    const recap = ficheData.recap || {};
    const bilans = ficheData.bilansHebdo || [];
    const pointages = ficheData.pointagesData || [];
    const configuredAllowances = Object.values(ficheData.indemnitesDisponibles || {})
        .map((allowance) => [allowance.code, {
            code: allowance.code,
            periodicite: allowance.periodicite,
        }]);
    const recordedAllowances = pointages.flatMap((pointage) => Object.values(pointage.indemnites || {})
        .map((allowance) => [allowance.code, { code: allowance.code }]));
    const dynamicAllowances = Array.from(new Map([...recordedAllowances, ...configuredAllowances]).values())
        .sort((a, b) => a.code.localeCompare(b.code, 'fr'));
    const dynamicAllowanceTotals = Object.fromEntries(dynamicAllowances.map(({ code }) => [code,
        pointages.reduce((total, pointage) => {
            const allowance = pointage.indemnites?.[code];
            return total + (allowance?.accordee ? Number(allowance.montant || 0) : 0);
        }, 0)
    ]));

    // Actions et fonctions de présentation propres au document.
    const handlePrint = () => {
        window.print();
    };

    const formatDecimalToHhMm = (dec) => {
        if (dec == null || isNaN(dec) || dec === 0) return '00h00';
        const sign = dec < 0 ? '-' : '';
        const abs = Math.abs(dec);
        const h = Math.floor(abs);
        const m = Math.round((abs - h) * 60);
        const finalH = m === 60 ? h + 1 : h;
        const finalM = m === 60 ? 0 : m;
        return `${sign}${String(finalH).padStart(2, '0')}h${String(finalM).padStart(2, '0')}`;
    };

    const parseHhMmToDecimal = (str) => {
        if (!str || !str.includes('h')) return 0.0;
        const parts = str.split('h');
        const h = parseFloat(parts[0]) || 0;
        const m = parseFloat(parts[1]) || 0;
        return Math.round((h + m / 60.0) * 100.0) / 100.0;
    };

    const dateEdition = new Date().toLocaleDateString('fr-FR', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
    });

    const formatStatut = (statut) => {
        const s = (statut || '').toUpperCase();
        switch (s) {
            case 'PRESENT': return 'Présent';
            case 'RETARD': return 'Retard';
            case 'DEMI_JOURNEE': return 'Demi-journée';
            case 'DISPENSE_POINTAGE': return 'Dispensé';
            case 'SAMEDI_TRAVAILLE': return 'Samedi travaillé';
            case 'DIMANCHE_TRAVAILLE': return 'Dimanche travaillé';
            case 'FERIE_TRAVAILLE': return 'Férié travaillé (+100%)';
            case 'FERIE_CHOME': return 'Férié chômé';
            case 'CONGE':
            case 'CONGE_PAYE': return 'Congé payé';
            case 'ABSENCE_AUTORISEE': return 'Absence autorisée';
            case 'ABSENCE_NON_JUSTIFIEE': return 'Absence injustifiée';
            case 'MALADIE': return 'Maladie';
            case 'REPOS': return 'Repos hebdomadaire';
            case 'PLANIFIE': return 'Planifié';
            case 'NON_CONCERNE': return 'Non concerné';
            case 'NON_ECHU': return '—';
            default: return statut || 'Présent';
        }
    };

    const workedDaysCount = pointages.filter(p => {
        const st = (p.statut || '').toUpperCase();
        return ['PRESENT', 'DISPENSE_POINTAGE', 'DEMI_JOURNEE', 'RETARD', 'SAMEDI_TRAVAILLE', 'DIMANCHE_TRAVAILLE', 'FERIE_TRAVAILLE'].includes(st);
    }).length;

    const dureeHebdo = ficheData?.reglesAppliquees?.dureeHebdoLegale || kpis?.dureeHebdoLegale || '40';
    const dureeJour = Number(dureeHebdo) > 0 ? Number(dureeHebdo) / 5 : 8;

    const modalContent = (
        <div className="fixed inset-0 z-[9999] overflow-y-auto bg-black/60 flex items-center justify-center p-2 sm:p-4 print:p-0 print:bg-white print:static print:block print:overflow-visible">
            {/* Règles CSS dédiées à l'impression A4 évitant tout tronquage ou coupure */}
            <style>{`
                @media print {
                    @page {
                        size: A4 portrait;
                        margin: 8mm 10mm 8mm 10mm;
                    }
                    html, body {
                        background: #ffffff !important;
                        height: auto !important;
                        min-height: auto !important;
                        overflow: visible !important;
                        margin: 0 !important;
                        padding: 0 !important;
                    }
                    body * {
                        visibility: hidden !important;
                    }
                    #pointage-print-sheet, #pointage-print-sheet * {
                        visibility: visible !important;
                    }
                    #pointage-print-sheet {
                        position: absolute !important;
                        left: 0 !important;
                        top: 0 !important;
                        width: 100% !important;
                        max-width: 100% !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        box-shadow: none !important;
                        border: none !important;
                        overflow: visible !important;
                        background: #ffffff !important;
                    }
                    tr {
                        page-break-inside: avoid !important;
                        break-inside: avoid !important;
                    }
                    .print-avoid-break {
                        page-break-inside: avoid !important;
                        break-inside: avoid !important;
                    }
                }
            `}</style>

            {/* Arrière-plan cliquable pour fermer (écran uniquement) */}
            <div className="fixed inset-0 print:hidden" onClick={onClose} />

            {/* Conteneur modale */}
            <div className="relative bg-white text-slate-900 w-full max-w-4xl shadow-2xl rounded-none border border-slate-300 print:border-none print:shadow-none print:max-w-none print:w-full print:overflow-visible overflow-hidden my-auto">

                {/* Barre d'action supérieure (masquée à l'impression) */}
                <div className="bg-slate-100 border-b border-slate-300 px-4 py-2.5 flex items-center justify-between gap-3 print:hidden">
                    <div className="flex items-center gap-2">
                        <Printer size={16} className="text-slate-700" />
                        <span className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                            Aperçu Fiche Officielle de Pointage (Format A4)
                        </span>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            type="button"
                            variant="primary"
                            size="sm"
                            onClick={handlePrint}
                            icon={<Download size={14} />}
                            className="text-xs font-bold cursor-pointer"
                        >
                            Imprimer / Enregistrer en PDF
                        </Button>
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={onClose}
                            icon={<X size={14} />}
                            className="text-xs cursor-pointer"
                        >
                            Fermer
                        </Button>
                    </div>
                </div>

                {/* ZONE DOCUMENT IMPRIMABLE (A4) */}
                <div id="pointage-print-sheet" ref={printRef} className="p-6 sm:p-8 space-y-4 text-[10.5px] leading-normal font-sans print:p-2 print:space-y-3.5 print:text-[10px]">
                    {/* 1. EN-TÊTE OFFICIEL CORPORATE */}
                    <div className="flex items-start justify-between border-b-2 border-slate-800 pb-2">
                        <div>
                            <h1 className="text-base sm:text-lg font-black tracking-tight text-slate-900 uppercase">
                                GALAXAM LOGISTICS
                            </h1>
                            <p className="text-[10px] text-slate-600 font-medium">
                                Système Intégré de Gestion des Ressources Humaines &amp; Paie
                            </p>
                        </div>
                        <div className="text-right">
                            <p className="text-[9px] text-slate-700 mt-0.5 font-mono font-bold">
                                {docRefName}
                            </p>
                            <p className="text-[8.5px] text-slate-500 font-mono">
                                Édité le {dateEdition}
                            </p>
                        </div>
                    </div>

                    {/* 2. CARTOUCHE D'IDENTIFICATION (2 COLONNES SOBRES) */}
                    <div className="grid grid-cols-2 gap-4 border border-slate-300 p-2.5 bg-slate-50/50 print:p-2">
                        <div className="space-y-0.5">
                            <div className="text-[9px] font-bold text-slate-500 uppercase">Salarié</div>
                            <div className="text-xs font-bold text-slate-900">{nomComplet}</div>
                            <div className="font-mono text-slate-700">Matricule : <span className="font-bold">{matricule}</span></div>
                            <div className="text-slate-700">Fonction : {fonction}</div>
                        </div>
                        <div className="space-y-0.5 border-l border-slate-200 pl-4">
                            <div className="text-[9px] font-bold text-slate-500 uppercase">Période &amp; Affectation</div>
                            <div className="text-xs font-bold text-slate-900">
                                {ficheData.libellePeriode || 'Période active'}
                            </div>
                            <div className="font-mono text-slate-700">
                                Dates : du {ficheData.dateDebut || '—'} au {ficheData.dateFin || '—'}
                            </div>
                            <div className="text-slate-700">
                                Site : {siteNom} <span className="text-slate-500">({categorie})</span>
                            </div>
                        </div>
                    </div>

                    {/* 3. SYNTHÈSE MENSUELLE PAIE (TABLEAU CHIFFRÉ COMPACT) */}
                    <div>
                        <h2 className="text-[10px] font-bold text-slate-900 uppercase tracking-wider mb-1 flex items-center gap-1.5">
                            <Clock size={11} />
                            I. Synthèse Mensuelle &amp; Consolidation Paie
                        </h2>
                        <table className="w-full border-collapse border border-slate-300 text-center text-[9px]">
                            <thead>
                                <tr className="bg-slate-100 font-bold text-slate-800 border-b border-slate-300">
                                    <th className="py-1 px-1 border-r border-slate-300 text-left">Jours Travaillés</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Heures faites</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Heures Sup (30%)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Heures Sup (50%)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Heures de Nuit</th>
                                    <th className="py-1 px-1 border-r border-slate-300 text-rose-700">Retards</th>
                                    <th className="py-1 px-1 border-r border-slate-300 text-emerald-700">Heures compensées</th>
                                    <th className="py-1 px-1 border-r border-slate-300 text-rose-700">Retenue salaire</th>
                                    {dynamicAllowances.map((allowance) => (
                                        <th key={allowance.code} className="py-1 px-1 border-l border-slate-300">
                                            {allowance.code}
                                            {allowance.periodicite ? ` (${allowance.periodicite.replaceAll('_', ' ').toLowerCase()})` : ''}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody>
                                <tr className="font-mono font-medium">
                                    <td className="py-1 px-1 border-r border-slate-300 text-left font-sans">{kpis.joursTravailles || '—'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold text-slate-800">
                                        {kpis.heuresRealisees || '00h00'}
                                        {kpis.heuresRealisees && (
                                            <span className="text-[8px] text-slate-500 font-normal ml-1">
                                                ({parseHhMmToDecimal(kpis.heuresRealisees).toFixed(2)}h)
                                            </span>
                                        )}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.heuresHs30 || '0.0h'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.heuresHs50 || '0.0h'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.heuresNuit || '0.0h'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300 text-rose-700 font-bold">
                                        {kpis.retardCumule || '00h00'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold text-emerald-700">
                                        {recap.retardCompense && recap.retardCompense !== '00h00' ? recap.retardCompense : '—'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold text-rose-700">
                                        {recap.retardNet && recap.retardNet !== '00h00' ? `-${recap.retardNet}` : '00h00'}
                                    </td>
                                    {dynamicAllowances.map((allowance) => (
                                        <td key={allowance.code} className="py-1 px-1 border-l border-slate-300">
                                            {formatMonnaie(dynamicAllowanceTotals[allowance.code])}
                                        </td>
                                    ))}
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    {/* 4. DÉCOMPOSITION HEBDOMADAIRE & COMPENSATION */}
                    <div>
                        <div className="flex items-center justify-between mb-1">
                            <h2 className="text-[10px] font-bold text-slate-900 uppercase tracking-wider flex items-center gap-1.5">
                                <Scale size={11} />
                                II. Décomposition Hebdomadaire &amp; Compensation
                            </h2>
                            <span className="text-[8px] text-slate-600 font-sans ">
                                Objectif calculé selon le calendrier et les absences · Compensation hebdomadaire
                            </span>
                        </div>
                        <table className="w-full border-collapse border border-slate-300 text-center text-[8.5px]">
                            <thead>
                                <tr className="bg-slate-100 font-bold text-slate-800 border-b border-slate-300">
                                    <th className="py-1 px-1.5 border-r border-slate-300 text-left">Semaine</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Jours</th>
                                    <th className="py-1 px-1 border-r border-slate-300" title="Objectif hebdomadaire à atteindre (abaissé pour congés et fériés)">Objectif semaine</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Heures faites</th>
                                    <th className="py-1 px-1 border-r border-slate-300 text-rose-700" title="Cumul des retards constatés sur la semaine (matin et après-midi)">Retards</th>
                                    <th className="py-1 px-1 border-r border-slate-300 text-emerald-700">Compensé</th>
                                    <th className="py-1 px-1">Impact Paie</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200">
                                {bilans.map((b, idx) => {
                                    const hasDeficit = b.heuresDeficit > 0;
                                    const hasHs = b.heuresHsNettes > 0;
                                    const hRealDec = b.totalHeuresTravaillees != null ? Number(b.totalHeuresTravaillees) : 0;
                                    const hRetDec = b.heuresRetard != null ? Number(b.heuresRetard) : 0;
                                    const hSeuil = b.seuilHeuresTroncon != null ? Number(b.seuilHeuresTroncon) : 40.0;
                                    return (
                                        <tr key={idx} className="font-mono">
                                            <td className="py-0.5 px-1.5 border-r border-slate-300 text-left font-sans">
                                                Semaine {b.numeroSemaine || idx + 1} ({b.debutSemaine} au {b.finSemaine})
                                            </td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{b.nbJoursTroncon} j</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-bold text-slate-800">
                                                <div>{formatDecimalToHhMm(hSeuil)}</div>
                                                {(b.nbJoursConge > 0 || b.nbJoursFerie > 0) && (
                                                    <div className="text-[7.5px] font-sans font-normal text-slate-500">
                                                        {b.nbJoursConge > 0 && `-${b.nbJoursConge}j congé `}
                                                        {b.nbJoursFerie > 0 && `-${b.nbJoursFerie}j férié`}
                                                    </div>
                                                )}
                                            </td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-bold text-slate-800">
                                                {formatDecimalToHhMm(hRealDec)}
                                                <span className="text-[7.5px] text-slate-500 font-normal ml-1">
                                                    ({hRealDec.toFixed(2)}h)
                                                </span>
                                            </td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 text-rose-700 font-bold">
                                                {hRetDec > 0 ? formatDecimalToHhMm(hRetDec) : '—'}
                                            </td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-semibold text-emerald-700">
                                                {b.heuresCompensees > 0 ? formatDecimalToHhMm(b.heuresCompensees) : '—'}
                                            </td>
                                            <td className="py-0.5 px-1 font-bold font-sans">
                                                {hasDeficit ? (
                                                    <span className="text-rose-700">
                                                        Retenue : -{formatDecimalToHhMm(b.heuresDeficit)}
                                                    </span>
                                                ) : hasHs ? (
                                                    <span className="text-emerald-700">
                                                        Heures Sup : +{formatDecimalToHhMm(b.heuresHsNettes)}
                                                    </span>
                                                ) : (
                                                    <span className="text-slate-600">Semaine validée</span>
                                                )}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    {/* 5. JOURNAL QUOTIDIEN DÉTAILLÉ */}
                    <div>
                        <h2 className="text-[10px] font-bold text-slate-900 uppercase tracking-wider mb-1 flex items-center gap-1.5">
                            <Calendar size={11} />
                            III. Journal Quotidien des Pointages
                        </h2>
                        <table className="w-full border-collapse border border-slate-300 text-center text-[8px]">
                            <thead>
                                <tr className="bg-slate-100 font-bold text-slate-800 border-b border-slate-300">
                                    <th className="py-0.5 px-1 border-r border-slate-300 text-left">Date</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Jour</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Entrée</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Sortie Pause</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Retour</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Sortie</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300 text-rose-700" title="Retards constatés sur la journée (matin et après-midi)">Retard</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300" title="Temps travaillé sur la journée">Heures faites</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300" title="Écart par rapport à l'objectif journalier configuré">Écart</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300 text-left">Statut</th>
                                    {dynamicAllowances.map((allowance) => (
                                        <th key={allowance.code} className="py-0.5 px-1">
                                            {allowance.code}
                                            {allowance.periodicite ? ` (${allowance.periodicite.replaceAll('_', ' ').toLowerCase()})` : ''}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200 font-mono">
                                {pointages.map((pj, idx) => {
                                    const st = (pj.statut || '').toUpperCase();
                                    const isConge = st === 'CONGE' || st === 'CONGE_PAYE';
                                    const isFerie = st === 'FERIE_CHOME' || (st === 'FERIE' && (!pj.heures || pj.heures === '00h00' || pj.heures === '—'));
                                    const isWeekend = st === 'REPOS' || (pj.isRepos && pj.isRepos());
                                    const isNonEchu = st === 'NON_ECHU';
                                    const isMerged = isConge || isFerie || isWeekend || isNonEchu;

                                    const isContinu = Boolean(
                                        pj.horaireContinuJour ||
                                        pj.horaireContinu
                                    );
                                    const isRetard = pj.minutesRetard > 0;
                                    const isPositiveEcart = pj.ecart && pj.ecart.startsWith('+');
                                    const isNegativeEcart = pj.ecart && pj.ecart.startsWith('-');

                                    return (
                                        <tr
                                            key={idx}
                                            className={
                                                isConge
                                                    ? 'bg-sky-50/60'
                                                    : isFerie
                                                        ? 'bg-purple-50/60'
                                                        : isWeekend
                                                            ? 'bg-slate-50 text-slate-400'
                                                            : ''
                                            }
                                        >
                                            <td className="py-0.5 px-1 border-r border-slate-300 text-left font-sans font-medium">
                                                {pj.dateComplete || pj.date}
                                            </td>
                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.jour}</td>

                                            {/* Cols 3 à 9 fusionnées si Congé / Férié / Repos / Non Échu */}
                                            {isMerged ? (
                                                <td colSpan={7} className="py-0.5 px-1 border-r border-slate-300 text-center font-sans">
                                                    {isConge ? (
                                                        <span className="text-sky-700 font-bold text-[7.5px] uppercase tracking-wide">
                                                            Congé Payé — Jour d'absence autorisée (Objectif hebdo abaissé de {dureeJour}h)
                                                        </span>
                                                    ) : isFerie ? (
                                                        <span className="text-purple-700 font-bold text-[7.5px] uppercase tracking-wide">
                                                            Férié Chômé Légal — Jour chômé payé (Objectif hebdo abaissé de {dureeJour}h)
                                                        </span>
                                                    ) : isWeekend ? (
                                                        <span className="text-slate-400  text-[7.5px]">
                                                            Repos hebdomadaire
                                                        </span>
                                                    ) : (
                                                        <span className="text-slate-400 text-[7.5px]">—</span>
                                                    )}
                                                </td>
                                            ) : (
                                                <>
                                                    <td className="py-0.5 px-0.5 border-r border-slate-300 font-medium">
                                                        {pj.entree || '—'}
                                                    </td>
                                                    {isContinu ? (
                                                        <td colSpan={2} className="py-0.5 px-0.5 border-r border-slate-300 text-[7.5px] font-sans text-amber-800 bg-amber-50">
                                                            Journée continue
                                                        </td>
                                                    ) : (
                                                        <>
                                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.sortiePause || '—'}</td>
                                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.retourPause || '—'}</td>
                                                        </>
                                                    )}
                                                    <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.sortie || '—'}</td>
                                                    <td className="py-0.5 px-0.5 border-r border-slate-300 font-medium">
                                                        {isRetard ? (
                                                            <span className="text-rose-700 font-bold">{pj.minutesRetard} min</span>
                                                        ) : '—'}
                                                    </td>
                                                    <td className="py-0.5 px-1 border-r border-slate-300 font-bold">
                                                        {pj.heures || '—'}
                                                    </td>
                                                    <td className="py-0.5 px-1 border-r border-slate-300 font-bold">
                                                        {isPositiveEcart ? (
                                                            <span className="text-emerald-700">{pj.ecart}</span>
                                                        ) : isNegativeEcart ? (
                                                            <span className="text-rose-700">{pj.ecart}</span>
                                                        ) : (
                                                            <span className="text-slate-500">{pj.ecart || '—'}</span>
                                                        )}
                                                    </td>
                                                </>
                                            )}

                                            <td className="py-0.5 px-1 border-r border-slate-300 text-left font-sans font-medium">
                                                {formatStatut(pj.statut)}
                                            </td>
                                            {dynamicAllowances.map((allowance) => {
                                                const value = pj.indemnites?.[allowance.code];
                                                return (
                                                    <td key={allowance.code} className="py-0.5 px-1 font-sans">
                                                        {value?.accordee && Number(value.montant) > 0 ? formatMonnaie(value.montant) : '—'}
                                                    </td>
                                                );
                                            })}
                                        </tr>
                                    );
                                })}
                            </tbody>
                            <tfoot className="border-t-2 border-slate-800 bg-slate-100 font-bold text-[8px]">
                                <tr>
                                    <td colSpan={6} className="py-1 px-1.5 text-left font-sans uppercase">
                                        Total Période
                                    </td>
                                    <td className="py-1 px-0.5 border-r border-slate-300 text-rose-700 font-mono font-bold">
                                        {kpis.retardCumule || '00h00'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-mono">
                                        {recap.tempsRealise || '00h00'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-mono text-slate-700">
                                        {recap.ecartCumule || '—'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300 text-left font-sans">
                                        {kpis.joursTravailles ? `${kpis.joursTravailles} travaillés` : `${workedDaysCount} j travaillés`}
                                    </td>
                                    {dynamicAllowances.map((allowance) => (
                                        <td key={allowance.code} className="py-1 px-1 font-sans">
                                            {formatMonnaie(dynamicAllowanceTotals[allowance.code])}
                                        </td>
                                    ))}
                                </tr>
                            </tfoot>
                        </table>
                    </div>

                    {/* 6. CADRE D'ÉMARGEMENT ET SIGNATURES OFFICIELLES */}
                    <div className="pt-3 border-t-2 border-slate-800 grid grid-cols-3 gap-3 text-center print-avoid-break">
                        <div className="border border-slate-300 p-2 min-h-[85px] flex flex-col justify-between">
                            <span className="text-[9px] font-bold uppercase text-slate-700">
                                Le Salarié
                            </span>
                            <span className="text-[8px] text-slate-400 ">
                                « Lu et approuvé » — Date &amp; Signature
                            </span>
                        </div>
                        <div className="border border-slate-300 p-2 min-h-[85px] flex flex-col justify-between">
                            <span className="text-[9px] font-bold uppercase text-slate-700">
                                Le Responsable Hiérarchique
                            </span>
                            <span className="text-[8px] text-slate-400 ">
                                Visa &amp; Validation Site
                            </span>
                        </div>
                        <div className="border border-slate-300 p-2 min-h-[85px] flex flex-col justify-between">
                            <span className="text-[9px] font-bold uppercase text-slate-700">
                                La Direction
                            </span>
                            <span className="text-[8px] text-slate-400 ">
                                Visa Paie &amp; Conformité
                            </span>
                        </div>
                    </div>

                </div>

            </div>
        </div>
    );

    return createPortal(modalContent, document.body);
}
