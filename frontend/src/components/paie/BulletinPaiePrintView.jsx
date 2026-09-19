import { useRef } from 'react';
import { Printer, X } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/carriereUtils';

/**
 * Vue imprimable A4 normalisée pour Bulletin de Paie Malagasy (GALAXAM SIRH).
 * Compatible impression papier ou export PDF du navigateur via window.print().
 */
export default function BulletinPaiePrintView({ bulletinComplet, onClose }) {
    const printRef = useRef(null);

    if (!bulletinComplet || !bulletinComplet.bulletin) {
        return null;
    }

    const { bulletin, lignes = [] } = bulletinComplet;

    const handlePrint = () => {
        window.print();
    };

    const formatAr = (val) => formatMoneyAr(val);

    return (
        <div className="fixed inset-0 z-[100] bg-black/70 backdrop-blur-sm overflow-y-auto flex flex-col items-center p-4 sm:p-6 print:p-0 print:bg-white print:static">
            {/* Barre d'actions non imprimable */}
            <div className="w-full max-w-[210mm] flex items-center justify-between bg-[var(--color-surface)] border border-[var(--color-border)] p-3 mb-4 shadow-lg print:hidden">
                <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider">
                        Aperçu avant impression
                    </span>
                    <span className="text-xs font-mono bg-[var(--color-bg-subtle)] px-2 py-0.5 border border-[var(--color-border-subtle)]">
                        {bulletin.referenceBulletin || `BP-${bulletin.idBulletin}`}
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <Button variant="primary" size="sm" onClick={handlePrint} className="flex items-center gap-1.5">
                        <Printer size={15} />
                        Imprimer / Enregistrer PDF
                    </Button>
                    <button
                        onClick={onClose}
                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]"
                        title="Fermer l'aperçu"
                    >
                        <X size={18} />
                    </button>
                </div>
            </div>

            {/* Feuille A4 imprimable */}
            <div
                ref={printRef}
                className="w-full max-w-[210mm] min-h-[297mm] bg-white text-slate-900 p-8 shadow-2xl border border-slate-200 print:border-none print:shadow-none print:p-0 print:m-0 font-sans text-xs"
            >
                {/* En-tête Société & Titre */}
                <div className="border-b-2 border-slate-800 pb-4 mb-4 flex justify-between items-start">
                    <div>
                        <h1 className="text-xl font-bold tracking-tight text-slate-900 uppercase">
                            GALAXAM LOGISTICS SARL
                        </h1>
                        <p className="text-[11px] text-slate-600">Transport & Logistique Multimodale - Madagascar</p>
                        <p className="text-[10px] text-slate-500 mt-1">
                            Siège : Enceinte Portuaire / Zone Industrielle - Antananarivo 101<br />
                            NIF : 4001289345 | STAT : 52291 11 2020 0 10234 | RCS : 2020 B 00412
                        </p>
                    </div>
                    <div className="text-right border-l-2 border-slate-200 pl-4">
                        <h2 className="text-sm font-bold uppercase tracking-wide text-slate-800">
                            BULLETIN DE PAIE
                        </h2>
                        <p className="text-xs font-mono font-semibold text-slate-700 mt-0.5">
                            {bulletin.typeBulletin || 'MENSUEL'}
                        </p>
                        <p className="text-[11px] text-slate-600 mt-1">
                            Réf : <span className="font-mono font-bold">{bulletin.referenceBulletin || `BP-${bulletin.idBulletin}`}</span>
                        </p>
                        <p className="text-[11px] text-slate-600">
                            Période : <span className="font-semibold">{bulletin.codePeriode || 'En cours'}</span>
                        </p>
                    </div>
                </div>

                {/* Bloc Salarié & Contrat */}
                <div className="grid grid-cols-2 gap-4 border border-slate-300 bg-slate-50/50 p-3 mb-4 rounded-none">
                    <div className="space-y-1">
                        <div className="flex justify-between border-b border-slate-200 pb-0.5">
                            <span className="text-slate-500 font-medium">Matricule :</span>
                            <span className="font-mono font-bold text-slate-800">{bulletin.matricule}</span>
                        </div>
                        <div className="flex justify-between border-b border-slate-200 pb-0.5">
                            <span className="text-slate-500 font-medium">Nom & Prénom :</span>
                            <span className="font-bold text-slate-900">{bulletin.nomPrenom || [bulletin.nom, bulletin.prenom].filter(Boolean).join(' ') || [bulletin.nomEmploye, bulletin.prenomEmploye].filter(Boolean).join(' ') || 'Employé'}</span>
                        </div>
                        <div className="flex justify-between border-b border-slate-200 pb-0.5">
                            <span className="text-slate-500 font-medium">Fonction :</span>
                            <span className="text-slate-800 font-medium">{bulletin.intitulePoste || bulletin.fonction || '-'}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-slate-500 font-medium">Site d'affectation :</span>
                            <span className="text-slate-800">{bulletin.nomSite || 'Siège'}</span>
                        </div>
                    </div>

                    <div className="space-y-1">
                        <div className="flex justify-between border-b border-slate-200 pb-0.5">
                            <span className="text-slate-500 font-medium">N° CNaPS :</span>
                            <span className="font-mono text-slate-800">{bulletin.numeroCnaps || 'Non renseigné'}</span>
                        </div>
                        <div className="flex justify-between border-b border-slate-200 pb-0.5">
                            <span className="text-slate-500 font-medium">N° CIN :</span>
                            <span className="font-mono text-slate-800">{bulletin.cinNumero || bulletin.cin || 'Non renseigné'}</span>
                        </div>
                        <div className="flex justify-between border-b border-slate-200 pb-0.5">
                            <span className="text-slate-500 font-medium">Date d'embauche :</span>
                            <span className="text-slate-800">{bulletin.dateEmbauche ? fmtDate(bulletin.dateEmbauche) : '-'}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-slate-500 font-medium">Régime fiscal :</span>
                            <span className="font-semibold text-slate-800">{bulletin.typeContrat || 'Salarié droit commun'}</span>
                        </div>
                    </div>
                </div>

                {/* Bloc Pointage / Temps de présence */}
                <div className="grid grid-cols-4 gap-2 border border-slate-300 p-2 mb-4 text-center bg-white text-[11px]">
                    <div className="border-r border-slate-200 pr-2">
                        <span className="text-slate-500 block text-[10px]">Jours Travaillés</span>
                        <span className="font-bold text-slate-800">{bulletin.joursTravailles ?? bulletin.nbJoursTravailles ?? '-'} j</span>
                    </div>
                    <div className="border-r border-slate-200 pr-2">
                        <span className="text-slate-500 block text-[10px]">Heures Normales</span>
                        <span className="font-bold text-slate-800">{bulletin.heuresNormales == null ? 'Détail indisponible' : `${bulletin.heuresNormales} h`}</span>
                    </div>
                    <div className="border-r border-slate-200 pr-2">
                        <span className="text-slate-500 block text-[10px]">Heures Supp. (30%/50%)</span>
                        <span className="font-bold text-slate-800">
                            {(bulletin.heuresHs30 || 0) + (bulletin.heuresHs50 || 0)} h
                        </span>
                    </div>
                    <div>
                        <span className="text-slate-500 block text-[10px]">Heures de Nuit</span>
                        <span className="font-bold text-slate-800">{bulletin.heuresNuit || 0} h</span>
                    </div>
                </div>

                {/* Tableau principal des rubriques de paie */}
                <table className="w-full border-collapse border border-slate-300 mb-4 text-[11px]">
                    <thead>
                        <tr className="bg-slate-100 text-slate-700 font-semibold border-b border-slate-300">
                            <th className="border-r border-slate-300 p-1.5 text-left w-16">Code</th>
                            <th className="border-r border-slate-300 p-1.5 text-left">Désignation Rubrique</th>
                            <th className="border-r border-slate-300 p-1.5 text-right w-24">Base</th>
                            <th className="border-r border-slate-300 p-1.5 text-right w-16">Taux Sal.</th>
                            <th className="border-r border-slate-300 p-1.5 text-right w-24">Retenue Sal.</th>
                            <th className="border-r border-slate-300 p-1.5 text-right w-24">Gain Brut</th>
                            <th className="border-r border-slate-300 p-1.5 text-right w-16">Taux Pat.</th>
                            <th className="p-1.5 text-right w-24">Charge Pat.</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-200">
                        {lignes.length === 0 ? (
                            <tr>
                                <td colSpan={8} className="p-4 text-center text-slate-400 ">
                                    Aucune ligne ventilée sur ce bulletin.
                                </td>
                            </tr>
                        ) : (
                            lignes.map((ligne, idx) => {
                                const isRetenue = ligne.sens === 'RETENUE';
                                const isGain = ligne.sens === 'GAIN' || ligne.sens === 'INFO';
                                const montantRetenue = isRetenue ? (ligne.montantSalarial ?? ligne.montantRetenue) : (ligne.montantRetenue && Number(ligne.montantRetenue) > 0 ? ligne.montantRetenue : null);
                                const montantGain = isGain ? (ligne.montantSalarial ?? ligne.montantGain) : (ligne.montantGain && Number(ligne.montantGain) > 0 ? ligne.montantGain : null);
                                return (
                                    <tr key={idx} className={idx % 2 === 1 ? 'bg-slate-50/40' : 'bg-white'}>
                                        <td className="border-r border-slate-200 p-1 font-mono text-[10px] text-slate-600">
                                            {ligne.codeRubrique || '-'}
                                        </td>
                                        <td className="border-r border-slate-200 p-1 font-medium text-slate-800">
                                            {ligne.libelleRubrique}
                                        </td>
                                        <td className="border-r border-slate-200 p-1 text-right font-mono">
                                            {ligne.baseCalcul ? Number(ligne.baseCalcul).toLocaleString('fr-MG') : '-'}
                                        </td>
                                        <td className="border-r border-slate-200 p-1 text-right font-mono text-slate-600">
                                            {ligne.tauxSalarial ? `${Number(ligne.tauxSalarial)}%` : '-'}
                                        </td>
                                        <td className="border-r border-slate-200 p-1 text-right font-mono text-rose-700">
                                            {montantRetenue && Number(montantRetenue) > 0
                                                ? Number(montantRetenue).toLocaleString('fr-MG')
                                                : '-'}
                                        </td>
                                        <td className="border-r border-slate-200 p-1 text-right font-mono font-medium text-emerald-700">
                                            {montantGain && Number(montantGain) > 0
                                                ? Number(montantGain).toLocaleString('fr-MG')
                                                : '-'}
                                        </td>
                                        <td className="border-r border-slate-200 p-1 text-right font-mono text-slate-600">
                                            {ligne.tauxPatronal ? `${Number(ligne.tauxPatronal)}%` : '-'}
                                        </td>
                                        <td className="p-1 text-right font-mono text-slate-700">
                                            {ligne.montantPatronal && Number(ligne.montantPatronal) > 0
                                                ? Number(ligne.montantPatronal).toLocaleString('fr-MG')
                                                : '-'}
                                        </td>
                                    </tr>
                                );
                            })
                        )}
                    </tbody>
                </table>

                {/* Synthèse financière et Totaux */}
                <div className="grid grid-cols-2 gap-4 border border-slate-300 p-3 mb-6 bg-slate-50/70">
                    <div className="space-y-1 text-[11px]">
                        <div className="flex justify-between">
                            <span className="text-slate-600">Salaire de Base Contractuel :</span>
                            <span className="font-mono font-medium">{formatAr(bulletin.salaireBase)}</span>
                        </div>
                        <div className="flex justify-between">
                            <span className="text-slate-600">Total des Gains Bruts :</span>
                            <span className="font-mono font-bold text-slate-900">{formatAr(bulletin.totalGainsBruts)}</span>
                        </div>
                        <div className="flex justify-between text-rose-700">
                            <span>Cotisations Sociales Salariales :</span>
                            <span className="font-mono font-semibold">- {formatAr(bulletin.totalCotisationsSalariales)}</span>
                        </div>
                        <div className="flex justify-between font-medium border-t border-slate-200 pt-1">
                            <span className="text-slate-700">Net Imposable (IRSA) :</span>
                            <span className="font-mono font-bold">{formatAr(bulletin.salaireNetImposable ?? bulletin.netImposable)}</span>
                        </div>
                        <div className="flex justify-between text-rose-700">
                            <span>Retenue Fiscale IRSA Nette :</span>
                            <span className="font-mono font-semibold">- {formatAr(bulletin.irsaNet)}</span>
                        </div>
                        <div className="flex justify-between text-rose-700">
                            <span>Avances, Acomptes & Déductions :</span>
                            <span className="font-mono font-semibold">- {formatAr(bulletin.totalRetenuesAvances)}</span>
                        </div>
                    </div>

                    <div className="flex flex-col justify-between border-l border-slate-300 pl-4">
                        <div className="space-y-1 text-[11px]">
                            <div className="flex justify-between text-slate-600">
                                <span>Total Cotisations Patronales :</span>
                                <span className="font-mono">{formatAr(bulletin.totalCotisationsPatronales)}</span>
                            </div>
                            <div className="flex justify-between text-slate-600">
                                <span>Coût Total Employeur :</span>
                                <span className="font-mono font-semibold">{formatAr(bulletin.coutTotalEmployeur)}</span>
                            </div>
                        </div>

                        {/* Encadré NET A PAYER */}
                        <div className="bg-slate-900 text-white p-3 text-center border-2 border-slate-900">
                            <span className="text-[10px] uppercase font-bold tracking-wider text-slate-300 block">
                                NET À PAYER AU SALARIÉ
                            </span>
                            <span className="text-xl font-mono font-black tracking-tight text-white block mt-0.5">
                                {formatAr(bulletin.netAPayer)}
                            </span>
                            <span className="text-[10px] text-slate-400 block mt-0.5 ">
                                Payé par virement bancaire sur compte désigné
                            </span>
                        </div>
                    </div>
                </div>

                {/* Signatures & Mentions légales */}
                <div className="grid grid-cols-2 gap-8 pt-4 border-t border-slate-300 text-[11px]">
                    <div>
                        <p className="font-semibold text-slate-700 mb-8">Pour la Direction Générale / DAF :</p>
                        <p className="text-[10px] text-slate-400 ">Signature et cachet de l'entreprise</p>
                    </div>
                    <div className="text-right">
                        <p className="font-semibold text-slate-700 mb-8">Le Salarié :</p>
                        <p className="text-[10px] text-slate-400 ">Mention manuscrite « Vu et approuvé » et signature</p>
                    </div>
                </div>

                <div className="mt-8 pt-2 border-t border-slate-200 text-center text-[9px] text-slate-400">
                    Document officiel généré par GALAXAM SIRH — Conformité Code du Travail & Code Général des Impôts de Madagascar.
                </div>
            </div>
        </div>
    );
}
