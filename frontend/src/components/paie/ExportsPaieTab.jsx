import { useState } from 'react';
import { 
    FileSpreadsheet, 
    FileCode, 
    Download, 
    CreditCard, 
    Eye, 
    AlertCircle, 
    CheckCircle2,
    ShieldCheck
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import paieService from '@/api/paie/paieService';
import { getErrorMessage } from '@/api/apiClient';

export default function ExportsPaieTab({ idPeriodeActive, codePeriode, outputCapability, outputs = [] }) {
    const [downloading, setDownloading] = useState(null);
    const [previewType, setPreviewType] = useState(null);
    const [previewData, setPreviewData] = useState([]);
    const [previewLoading, setPreviewLoading] = useState(false);
    const [feedback, setFeedback] = useState(null);
    const outputAllowed = outputCapability?.allowed === true;
    const outputReason = (outputCapability?.reasonCodes || []).join(' · ');

    const handleDownload = async (type, label) => {
        if (!idPeriodeActive || !outputAllowed) {
            setFeedback({
                type: 'error',
                text: !idPeriodeActive
                    ? 'Veuillez sélectionner une période de paie.'
                    : `Sorties indisponibles : ${outputReason || 'la campagne doit être validée.'}`
            });
            return;
        }

        setDownloading(type);
        setFeedback(null);
        try {
            await paieService.telechargerFichierExport(idPeriodeActive, type);
            setFeedback({ type: 'success', text: `Export ${label} téléchargé avec succès.` });
        } catch (err) {
            setFeedback({ 
                type: 'error', 
                text: getErrorMessage(err, `Erreur lors du téléchargement de l'export ${label}.`)
            });
        } finally {
            setDownloading(null);
        }
    };

    const handleOpenPreview = async (type) => {
        if (!idPeriodeActive || !outputAllowed) return;
        setPreviewType(type);
        setPreviewLoading(true);
        setPreviewData([]);
        try {
            if (type === 'safi') {
                const data = await paieService.obtenirDonneesSafi(idPeriodeActive);
                setPreviewData(Array.isArray(data) ? data : (data?.content || data?.items || []));
            } else if (type === 'dnts') {
                const data = await paieService.obtenirDonneesDnts(idPeriodeActive);
                setPreviewData(Array.isArray(data) ? data : (data?.content || data?.items || []));
            } else if (type === 'virement') {
                const data = await paieService.obtenirDonneesVirement(idPeriodeActive);
                setPreviewData(Array.isArray(data) ? data : (data?.content || data?.items || []));
            }
        } catch (err) {
            alert(getErrorMessage(err, 'Erreur lors du chargement des données d\'aperçu.'));
            setPreviewType(null);
        } finally {
            setPreviewLoading(false);
        }
    };

    return (
        <div className="space-y-5">
            {feedback && (
                <div className={`p-3 text-xs flex items-center gap-2 border ${
                    feedback.type === 'success' 
                        ? 'bg-emerald-50 border-emerald-200 text-emerald-800' 
                        : 'bg-rose-50 border-rose-200 text-rose-800'
                }`}>
                    {feedback.type === 'success' ? <CheckCircle2 size={16} /> : <AlertCircle size={16} />}
                    <span>{feedback.text}</span>
                </div>
            )}

            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex items-center justify-between">
                <div>
                    <h3 className="text-sm font-bold text-[var(--color-fg)]">
                        Restitutions Légales & Fichiers Bancaires
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                        Génération conforme aux référentiels de la DGI Madagascar, CNaPS et aux normes interbancaires.
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <span className="text-xs text-[var(--color-fg-muted)]">Période ciblée :</span>
                    <span className="px-2.5 py-1 bg-[var(--color-bg-subtle)] font-mono font-bold text-xs border border-[var(--color-border)]">
                        {codePeriode || 'Aucune période'}
                    </span>
                </div>
            </div>

            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                <table className="w-full text-xs">
                    <thead className="bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]">
                        <tr>
                            <th className="text-left p-2">Sortie</th>
                            <th className="text-left p-2">Statut</th>
                            <th className="text-left p-2">Version</th>
                            <th className="text-left p-2">Campagne source</th>
                            <th className="text-left p-2">Checksum source</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--color-border-subtle)]">
                        {outputs.map(output => (
                            <tr key={output.code}>
                                <td className="p-2 font-semibold">{output.code}</td>
                                <td className="p-2">{output.status}</td>
                                <td className="p-2 font-mono">v{output.version}</td>
                                <td className="p-2 font-mono">{output.sourceCampaign}</td>
                                <td className="p-2 font-mono" title={output.checksum}>{output.checksum?.slice(0, 12)}…</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Grille des formats d'export */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* 1. Déclaration Fiscale SAFI 25 Colonnes (DGI Madagascar) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 flex flex-col justify-between">
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-2.5">
                                <div className="w-9 h-9 flex items-center justify-center bg-blue-500/10 text-blue-600 border border-blue-500/20">
                                    <FileSpreadsheet size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        SAFI IRSA — État 301 (25 Colonnes)
                                    </h4>
                                    <span className="text-[10px] uppercase font-mono tracking-wider text-blue-600 bg-blue-50 px-1.5 py-0.5 border border-blue-200">
                                        DGI Madagascar • Art. 01.03.01 CGI
                                    </span>
                                </div>
                            </div>
                        </div>

                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed mb-4">
                            Format fiscal officiel de déclaration de l'IRSA comportant l'intégralité des 25 colonnes réglementaires : 
                            identification du salarié, salaires bruts, avantages en nature, déductions CNaPS/Santé, base imposable, 
                            impôt brut, charges de famille et IRSA net versé.
                        </p>
                    </div>

                    <div className="pt-3 border-t border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenPreview('safi')}
                            disabled={!outputAllowed}
                            title={outputReason}
                            className="flex items-center gap-1 text-xs"
                        >
                            <Eye size={13} />
                            Aperçu Données
                        </Button>

                        <div className="flex items-center gap-2">
                            <Button
                                variant="secondary"
                                size="sm"
                                onClick={() => handleDownload('safi-ods', 'SAFI Modèle ODS')}
                                disabled={!outputAllowed || downloading === 'safi-ods'}
                                className="flex items-center gap-1.5 text-xs"
                            >
                                <Download size={13} />
                                {downloading === 'safi-ods' ? 'Génération...' : 'Format ODS Officiel'}
                            </Button>

                            <Button
                                variant="primary"
                                size="sm"
                                onClick={() => handleDownload('safi-csv', 'SAFI CSV')}
                                disabled={!outputAllowed || downloading === 'safi-csv'}
                                className="flex items-center gap-1.5 text-xs"
                            >
                                <Download size={13} />
                                {downloading === 'safi-csv' ? 'Génération...' : 'Format CSV'}
                            </Button>
                        </div>
                    </div>
                </div>

                {/* 2. Télé-Déclaration EDI ISI XML (DGI Prestataires) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 flex flex-col justify-between">
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-2.5">
                                <div className="w-9 h-9 flex items-center justify-center bg-purple-500/10 text-purple-600 border border-purple-500/20">
                                    <FileCode size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        EDI ISI — Télé-Déclaration XML
                                    </h4>
                                    <span className="text-[10px] uppercase font-mono tracking-wider text-purple-600 bg-purple-50 px-1.5 py-0.5 border border-purple-200">
                                        DGI Madagascar • Régime Prestataires 5%
                                    </span>
                                </div>
                            </div>
                        </div>

                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed mb-4">
                            Fichier XML conforme au schéma XSD de la Direction Générale des Impôts pour la déclaration 
                            et le versement de l'Impôt Synthétique Intermittent (ISI 5%) retenu à la source sur les honoraires 
                            des consultants et prestataires externes.
                        </p>
                    </div>

                    <div className="pt-3 border-t border-[var(--color-border-subtle)] flex items-center justify-end">
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleDownload('edi-isi', 'EDI ISI XML')}
                            disabled={!outputAllowed || downloading === 'edi-isi'}
                            title={outputReason}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Download size={13} />
                            {downloading === 'edi-isi' ? 'Export XML...' : 'Télécharger XML EDI ISI'}
                        </Button>
                    </div>
                </div>

                {/* 3. Déclaration CNaPS / FMFP — DNTS */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 flex flex-col justify-between">
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-2.5">
                                <div className="w-9 h-9 flex items-center justify-center bg-emerald-500/10 text-emerald-600 border border-emerald-500/20">
                                    <ShieldCheck size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        DNTS — Cotisations Sociales CNaPS & FMFP
                                    </h4>
                                    <span className="text-[10px] uppercase font-mono tracking-wider text-emerald-600 bg-emerald-50 px-1.5 py-0.5 border border-emerald-200">
                                        Déclaration Trimestrielle / Mensuelle
                                    </span>
                                </div>
                            </div>
                        </div>

                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed mb-4">
                            Bordereau DNTS normalisé listant les salaires bruts plafonnés (Plafond 8x SMIG = 2 100 864 Ar), 
                            les cotisations salariales (1%), patronales (13%), et la contribution FMFP (1%), ventilés par collaborateur 
                            avec numéro CNaPS.
                        </p>
                    </div>

                    <div className="pt-3 border-t border-[var(--color-border-subtle)] flex items-center justify-between">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenPreview('dnts')}
                            disabled={!outputAllowed}
                            title={outputReason}
                            className="flex items-center gap-1 text-xs"
                        >
                            <Eye size={13} />
                            Aperçu Données
                        </Button>

                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleDownload('dnts-csv', 'DNTS CNaPS')}
                            disabled={!outputAllowed || downloading === 'dnts-csv'}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Download size={13} />
                            {downloading === 'dnts-csv' ? 'Génération...' : 'Télécharger DNTS CSV'}
                        </Button>
                    </div>
                </div>

                {/* 4. Ordre de Virement Bancaire */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 flex flex-col justify-between">
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-2.5">
                                <div className="w-9 h-9 flex items-center justify-center bg-amber-500/10 text-amber-600 border border-amber-500/20">
                                    <CreditCard size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        Ordre de Virement Bancaire Collectif
                                    </h4>
                                    <span className="text-[10px] uppercase font-mono tracking-wider text-amber-600 bg-amber-50 px-1.5 py-0.5 border border-amber-200">
                                        Télétransmission Interbancaire
                                    </span>
                                </div>
                            </div>
                        </div>

                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed mb-4">
                            Fichier d'ordres de virement destiné à la banque domiciliataire de GALAXAM (BNI, BMOI, Société Générale, BOA). 
                            Comprend les coordonnées bancaires (RIB complet 23 chiffres), nom du bénéficiaire et montant Net à payer.
                        </p>
                    </div>

                    <div className="pt-3 border-t border-[var(--color-border-subtle)] flex items-center justify-between">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenPreview('virement')}
                            disabled={!outputAllowed}
                            title={outputReason}
                            className="flex items-center gap-1 text-xs"
                        >
                            <Eye size={13} />
                            Aperçu Données
                        </Button>

                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleDownload('virement-csv', 'Ordre Virement')}
                            disabled={!outputAllowed || downloading === 'virement-csv'}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Download size={13} />
                            {downloading === 'virement-csv' ? 'Génération...' : 'Télécharger Virement CSV'}
                        </Button>
                    </div>
                </div>
            </div>

            {/* Modale d'aperçu des données tabulaires */}
            <Modal
                isOpen={!!previewType}
                onClose={() => setPreviewType(null)}
                title={`Aperçu des données : ${
                    previewType === 'safi' ? 'SAFI IRSA 25 Colonnes' :
                    previewType === 'dnts' ? 'DNTS CNaPS & FMFP' : 'Ordres de Virement Bancaire'
                }`}
                size="full"
                footer={
                    <div className="flex items-center justify-between w-full">
                        <span className="text-xs text-[var(--color-fg-muted)]">
                            {previewData.length} ligne(s) dans le fichier
                        </span>
                        <Button variant="secondary" size="sm" onClick={() => setPreviewType(null)}>
                            Fermer
                        </Button>
                    </div>
                }
            >
                {previewLoading ? (
                    <div className="p-12 text-center text-xs text-[var(--color-fg-muted)]">
                        Chargement des données réglementaires...
                    </div>
                ) : (
                    <div className="overflow-x-auto max-h-[60vh]">
                        <table className="w-full text-left text-xs border-collapse">
                            <thead>
                                <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[10px] text-[var(--color-fg-muted)]">
                                    {previewData.length > 0 && Object.keys(previewData[0]).map((col, idx) => (
                                        <th key={idx} className="p-2 font-semibold uppercase tracking-wider whitespace-nowrap">
                                            {col}
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-[var(--color-border-subtle)] font-mono text-[11px]">
                                {previewData.length === 0 ? (
                                    <tr>
                                        <td colSpan={10} className="p-8 text-center text-[var(--color-fg-muted)] italic">
                                            Aucune donnée disponible pour cette période.
                                        </td>
                                    </tr>
                                ) : (
                                    previewData.map((row, rIdx) => (
                                        <tr key={rIdx} className="hover:bg-[var(--color-bg-subtle)]/50">
                                            {Object.values(row).map((val, cIdx) => (
                                                <td key={cIdx} className="p-2 whitespace-nowrap">
                                                    {val !== null && val !== undefined ? String(val) : '-'}
                                                </td>
                                            ))}
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                )}
            </Modal>
        </div>
    );
}
