import { useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { Printer, X, Download, ShieldCheck, Calendar, Clock } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { formatMonnaie, getPeriodeLabelForFilename } from '@/utils/pointageUtils';

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
    const printRef = useRef(null);

    // Récupération prioritaire des métadonnées backend-driven ou repli sur l'objet employe
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

    useEffect(() => {
        if (isOpen) {
            const prevTitle = document.title;
            document.title = docRefName;
            return () => {
                document.title = prevTitle;
            };
        }
    }, [isOpen, docRefName]);

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
                            <span className="inline-block px-2 py-0.5 border border-slate-800 text-[10px] font-bold uppercase tracking-wider bg-slate-50">
                                Fiche Individuelle de Pointage
                            </span>
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
                                    <th className="py-1 px-1 border-r border-slate-300 text-left">Jours Présents</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Réalisé (hh:mm)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Réalisé (déc.)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">HS 30%</th>
                                    <th className="py-1 px-1 border-r border-slate-300">HS 50%</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Nuit</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Retard Brut</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Compensé 1:1</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Retenue Nette</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Repas</th>
                                    <th className="py-1 px-1">Déplacement</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr className="font-mono font-medium">
                                    <td className="py-1 px-1 border-r border-slate-300 text-left font-sans">{kpis.joursTravailles || '—'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold">{kpis.heuresRealisees || '00h00'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold text-slate-700">
                                        {kpis.heuresRealisees ? `${parseHhMmToDecimal(kpis.heuresRealisees).toFixed(2)}h` : '0.00h'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.heuresHs30 || '0.0h'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.heuresHs50 || '0.0h'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.heuresNuit || '0.0h'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.retardCumule || '00h00'}</td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold text-slate-800">
                                        {recap.retardCompense && recap.retardCompense !== '00h00' ? `-${recap.retardCompense}` : '—'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300 font-bold text-slate-900">
                                        {recap.retardNet && recap.retardNet !== '00h00' ? `-${recap.retardNet}` : '00h00'}
                                    </td>
                                    <td className="py-1 px-1 border-r border-slate-300">{kpis.montantRepasTotal || '0 Ar'}</td>
                                    <td className="py-1 px-1">{kpis.montantDeplacementTotal || '0 Ar'}</td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    {/* 4. DÉCOMPOSITION HEBDOMADAIRE (RG-PTG-05) */}
                    <div>
                        <h2 className="text-[10px] font-bold text-slate-900 uppercase tracking-wider mb-1 flex items-center gap-1.5">
                            <ShieldCheck size={11} />
                            II. Décomposition Hebdomadaire &amp; Compensation
                        </h2>
                        <table className="w-full border-collapse border border-slate-300 text-center text-[9px]">
                            <thead>
                                <tr className="bg-slate-100 font-bold text-slate-800 border-b border-slate-300">
                                    <th className="py-1 px-1.5 border-r border-slate-300 text-left">Semaine</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Jours</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Seuil</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Réalisé (hh:mm)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Réalisé (déc.)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Retards (hh:mm)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Retards (déc.)</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Surplus Brut</th>
                                    <th className="py-1 px-1 border-r border-slate-300">Compensé (1:1)</th>
                                    <th className="py-1 px-1">Solde Net</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200">
                                {bilans.map((b, idx) => {
                                    const hasDeficit = b.heuresDeficit > 0;
                                    const hasHs = b.heuresHsNettes > 0;
                                    const hRealDec = b.totalHeuresTravaillees != null ? Number(b.totalHeuresTravaillees) : 0;
                                    const hRetDec = b.heuresRetard != null ? Number(b.heuresRetard) : 0;
                                    return (
                                        <tr key={idx} className="font-mono">
                                            <td className="py-0.5 px-1.5 border-r border-slate-300 text-left font-sans">
                                                Semaine {b.numeroSemaine || idx + 1} ({b.debutSemaine} au {b.finSemaine})
                                            </td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{b.nbJoursTroncon} j</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{b.seuilHeuresTroncon ? `${Math.round(b.seuilHeuresTroncon)}h00` : '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-bold">{formatDecimalToHhMm(hRealDec)}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-bold text-slate-700">{hRealDec > 0 ? `${hRealDec.toFixed(2)}h` : '0h'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{hRetDec > 0 ? formatDecimalToHhMm(hRetDec) : '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 text-slate-600">{hRetDec > 0 ? `${hRetDec.toFixed(2)}h` : '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{b.heuresHsBrutes > 0 ? `+${formatDecimalToHhMm(b.heuresHsBrutes)} (${b.heuresHsBrutes.toFixed(2)}h)` : '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{b.heuresCompensees > 0 ? `-${formatDecimalToHhMm(b.heuresCompensees)} (${b.heuresCompensees.toFixed(2)}h)` : '—'}</td>
                                            <td className="py-0.5 px-1 font-bold font-sans">
                                                {hasDeficit ? `-${formatDecimalToHhMm(b.heuresDeficit)} (Retenue)` : hasHs ? `+${formatDecimalToHhMm(b.heuresHsNettes)} (HS Nettes)` : 'Équilibré'}
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
                                    <th className="py-0.5 px-1 border-r border-slate-300 text-left">Statut</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Entrée 1</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Sortie 1</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Entrée 2</th>
                                    <th className="py-0.5 px-0.5 border-r border-slate-300">Sortie 2</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300">Total (hh:mm)</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300">Total (déc.)</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300">Écart</th>
                                    <th className="py-0.5 px-1 border-r border-slate-300">Repas</th>
                                    <th className="py-0.5 px-1">Dépl.</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-200 font-mono">
                                {pointages.map((pj, idx) => {
                                    const isWeekend = pj.isRepos && pj.isRepos();
                                    const decVal = pj.totalHeuresDecimal != null ? pj.totalHeuresDecimal : (pj.heures ? parseHhMmToDecimal(pj.heures) : null);
                                    return (
                                        <tr key={idx} className={isWeekend ? 'bg-slate-50 text-slate-400' : ''}>
                                            <td className="py-0.5 px-1 border-r border-slate-300 text-left font-sans">
                                                {pj.dateComplete || pj.date}
                                            </td>
                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.jour}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 text-left font-sans font-medium">
                                                {pj.statut || 'Présent'}
                                            </td>
                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.entree || '—'}</td>
                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.sortiePause || '—'}</td>
                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.retourPause || '—'}</td>
                                            <td className="py-0.5 px-0.5 border-r border-slate-300">{pj.sortie || '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-bold">{pj.heures || '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-bold text-slate-700">
                                                {decVal != null && decVal > 0 ? decVal.toFixed(2) : (pj.heures && pj.heures !== '00h00' ? decVal?.toFixed(2) : '—')}
                                            </td>
                                            <td className="py-0.5 px-1 border-r border-slate-300">{pj.ecart || '—'}</td>
                                            <td className="py-0.5 px-1 border-r border-slate-300 font-sans">
                                                {pj.montantRepas > 0 ? formatMonnaie(pj.montantRepas) : '—'}
                                            </td>
                                            <td className="py-0.5 px-1 font-sans">
                                                {pj.montantDeplacement > 0 ? formatMonnaie(pj.montantDeplacement) : '—'}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    {/* 6. CADRE D'ÉMARGEMENT ET SIGNATURES OFFICIELLES */}
                    <div className="pt-3 border-t-2 border-slate-800 grid grid-cols-3 gap-3 text-center print-avoid-break">
                        <div className="border border-slate-300 p-2 min-h-[85px] flex flex-col justify-between">
                            <span className="text-[9px] font-bold uppercase text-slate-700">
                                Le Salarié
                            </span>
                            <span className="text-[8px] text-slate-400 italic">
                                « Lu et approuvé » — Date &amp; Signature
                            </span>
                        </div>
                        <div className="border border-slate-300 p-2 min-h-[85px] flex flex-col justify-between">
                            <span className="text-[9px] font-bold uppercase text-slate-700">
                                Le Responsable Hiérarchique
                            </span>
                            <span className="text-[8px] text-slate-400 italic">
                                Visa &amp; Validation Site
                            </span>
                        </div>
                        <div className="border border-slate-300 p-2 min-h-[85px] flex flex-col justify-between">
                            <span className="text-[9px] font-bold uppercase text-slate-700">
                                La Direction (DAF / RH)
                            </span>
                            <span className="text-[8px] text-slate-400 italic">
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
