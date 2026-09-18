import { useState } from 'react';
import { 
    FileSpreadsheet, 
    FileCode, 
    Download, 
    CreditCard, 
    Eye, 
    ShieldCheck
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td, TableEmpty } from '@/components/ui/data-display/Table';
import Alert from '@/components/ui/feedback/Alert';
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
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors du chargement des données d\'aperçu.') });
            setPreviewType(null);
        } finally {
            setPreviewLoading(false);
        }
    };

    return (
        <div className="space-y-5">
            {feedback && (
                <Alert
                    type={feedback.type === 'success' ? 'success' : 'error'}
                    message={feedback.text}
                    onClose={() => setFeedback(null)}
                />
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
                    <span className="px-2.5 py-1 bg-[var(--color-bg-subtle)] font-mono font-bold text-xs border border-[var(--color-border)] text-[var(--color-fg)]">
                        {codePeriode || 'Aucune période'}
                    </span>
                </div>
            </div>

            {outputs && outputs.length > 0 && (
                <Table>
                    <Thead>
                        <tr>
                            <Th>Sortie</Th>
                            <Th>Statut</Th>
                            <Th>Version</Th>
                            <Th>Campagne source</Th>
                            <Th>Checksum source</Th>
                        </tr>
                    </Thead>
                    <Tbody>
                        {outputs.map(output => (
                            <Tr key={output.code}>
                                <Td className="font-semibold">{output.code}</Td>
                                <Td>
                                    <Badge size="xs" variant={output.status === 'READY' || output.status === 'GENERATED' ? 'success' : 'neutral'}>
                                        {output.status}
                                    </Badge>
                                </Td>
                                <Td className="font-mono">v{output.version}</Td>
                                <Td className="font-mono">{output.sourceCampaign}</Td>
                                <Td className="font-mono" title={output.checksum}>{output.checksum?.slice(0, 12)}…</Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}

            {/* Grille des formats d'export */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {/* 1. Déclaration Fiscale SAFI 25 Colonnes (DGI Madagascar) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 flex flex-col justify-between">
                    <div>
                        <div className="flex items-center justify-between mb-3">
                            <div className="flex items-center gap-2.5">
                                <div className="w-9 h-9 flex items-center justify-center bg-[var(--color-info-500)]/10 text-[var(--color-info-600)] border border-[var(--color-info-500)]/20">
                                    <FileSpreadsheet size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        SAFI IRSA — État 301 (25 Colonnes)
                                    </h4>
                                    <Badge variant="info" size="xs" className="mt-0.5">
                                        DGI Madagascar • Art. 01.03.01 CGI
                                    </Badge>
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
                                    <Badge variant="purple" size="xs" className="mt-0.5">
                                        DGI Madagascar • Régime Prestataires 5%
                                    </Badge>
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
                                <div className="w-9 h-9 flex items-center justify-center bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border border-[var(--color-success-500)]/20">
                                    <ShieldCheck size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        DNTS — Cotisations Sociales CNaPS & FMFP
                                    </h4>
                                    <Badge variant="success" size="xs" className="mt-0.5">
                                        Déclaration Trimestrielle / Mensuelle
                                    </Badge>
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
                                <div className="w-9 h-9 flex items-center justify-center bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] border border-[var(--color-warning-500)]/20">
                                    <CreditCard size={20} />
                                </div>
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                        Ordre de Virement Bancaire Collectif
                                    </h4>
                                    <Badge variant="warning" size="xs" className="mt-0.5">
                                        Télétransmission Interbancaire
                                    </Badge>
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
                        <span className="text-xs text-[var(--color-fg-muted)] font-mono">
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
                    <div className="max-h-[60vh]">
                        <Table>
                            <Thead sticky>
                                <tr>
                                    {previewData.length > 0 && Object.keys(previewData[0]).map((col, idx) => (
                                        <Th key={idx}>
                                            {col}
                                        </Th>
                                    ))}
                                </tr>
                            </Thead>
                            <Tbody>
                                {previewData.length === 0 ? (
                                    <TableEmpty colSpan={10} title="Aucune donnée disponible pour cette période." />
                                ) : (
                                    previewData.map((row, rIdx) => (
                                        <Tr key={rIdx}>
                                            {Object.values(row).map((val, cIdx) => (
                                                <Td key={cIdx} isData className="whitespace-nowrap font-mono text-xs">
                                                    {val !== null && val !== undefined ? String(val) : '-'}
                                                </Td>
                                            ))}
                                        </Tr>
                                    ))
                                )}
                            </Tbody>
                        </Table>
                    </div>
                )}
            </Modal>
        </div>
    );
}
