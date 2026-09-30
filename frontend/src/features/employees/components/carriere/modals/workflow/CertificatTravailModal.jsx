import { useState, useEffect, useCallback } from 'react';
import { Award, Download } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Loading from '@/components/ui/feedback/Loading';
import Modal from '@/components/ui/overlays/Modal';
import documentService from '@/api/documentService';
import { fmtDateFr } from '@/utils/dateUtils';

export default function CertificatTravailModal({
    isOpen,
    onClose,
    employe,
    contrat,
    affectation,
    filiale,
    signataire,
    dateDebutInitiale = null,
}) {
    const [typeAnciennete, setTypeAnciennete] = useState('globale');
    const [loadingData, setLoadingData] = useState(true);
    const [downloadingDocx, setDownloadingDocx] = useState(false);
    const [docData, setDocData] = useState(null);

    const idContrat = contrat?.idContrat;

    const chargerDonnees = useCallback(async () => {
        if (!idContrat) return;
        setLoadingData(true);
        try {
            const data = await documentService.getCertificatData(idContrat, typeAnciennete);
            setDocData(data);
        } catch (err) {
            console.error('Erreur chargement certificat backend:', err);
        } finally {
            setLoadingData(false);
        }
    }, [idContrat, typeAnciennete]);

    useEffect(() => {
        if (isOpen) {
            chargerDonnees();
        }
    }, [isOpen, chargerDonnees]);

    const handleDownloadDocx = async () => {
        if (!idContrat || downloadingDocx) return;
        setDownloadingDocx(true);
        try {
            const filename = `Certificat_Travail_${docData?.employe?.matricule || idContrat}.docx`;
            await documentService.downloadCertificatDocx(idContrat, typeAnciennete, filename);
        } catch (err) {
            console.error('Erreur téléchargement DOCX certificat:', err);
        } finally {
            setDownloadingDocx(false);
        }
    };

    if (!isOpen) return null;

    const ent = {
        nom: docData?.entreprise?.nom || filiale?.libelle || filiale?.raisonSociale || contrat?.libelleFiliale || 'GALAXAM LOGISTICS',
        adresse: docData?.entreprise?.adresse || filiale?.adresse || null,
        ville: docData?.entreprise?.ville || filiale?.ville || null,
        pays: docData?.entreprise?.pays || filiale?.pays || null,
        nif: docData?.entreprise?.nif || filiale?.nif || null,
        stat: docData?.entreprise?.stat || filiale?.stat || null,
        rcs: docData?.entreprise?.rcs || filiale?.rcs || null,
        telephone: docData?.entreprise?.telephone || filiale?.telephone || '+261 32 12 131 01',
        email: docData?.entreprise?.email || filiale?.email || 'contact@galaxam.com',
    };

    const emp = docData?.employe || {
        nomComplet: `${employe?.nom || ''} ${employe?.prenom || ''}`.trim() || 'Collaborateur',
        matricule: employe?.matricule || '',
        dateNaissance: employe?.dateNaissance || null,
        lieuNaissance: employe?.lieuNaissance || '',
        numeroCin: employe?.cin || employe?.numeroCin || '',
        adresse: employe?.adresse || '',
    };

    const sig = docData?.signataire || {
        nom: signataire?.nom || 'Direction des Ressources Humaines',
        fonction: signataire?.fonction || 'Directeur des Ressources Humaines',
        ville: filiale?.ville || null,
        dateEmissionTexte: fmtDateFr(new Date()),
    };

    const refDoc = docData?.reference || `CERT-${contrat?.numeroContrat || 'CTR'}`;
    const dateEntreeTexte = docData?.dateEntreeTexte || fmtDateFr(dateDebutInitiale || contrat?.dateDebut || new Date());
    const dateSortieTexte = docData?.dateSortieTexte || fmtDateFr(contrat?.dateFin || new Date());
    const dureeTotaleTexte = docData?.dureeTotaleTexte || '';
    const typeContratLib = docData?.typeContrat || 'durée indéterminée';
    const mentionLiberatoire = docData?.mentionLiberatoire || "Nous confirmons que le/la salarié(e) quitte l'entreprise libre de tout engagement et de toute dette envers la société.";
    const postesOccupes = docData?.postesOccupes || [];
    const libFonction = docData?.dernierPoste || affectation?.libelleFonction || contrat?.libelleFonction || '';
    const catPro = docData?.derniereCategorie || affectation?.libelleCategorieProfessionnelle || '';
    const departement = docData?.dernierDepartement || affectation?.libelleDepartement || '';

    const modalFooter = (
        <div className="flex items-center justify-between w-full flex-wrap gap-3">
            <span className="text-xs text-[var(--color-fg-muted)]">
                Référence : {refDoc}
            </span>
            <div className="flex items-center gap-2">
                <Button
                    type="button"
                    onClick={handleDownloadDocx}
                    variant="primary"
                    size="sm"
                    loading={downloadingDocx}
                    iconLeft={<Download size={15} />}
                    title="Télécharger le fichier Word (.docx)"
                >
                    Télécharger Word (.docx)
                </Button>
                <Button variant="ghost" size="sm" onClick={onClose}>
                    Fermer
                </Button>
            </div>
        </div>
    );

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Certificat de travail · ${emp.nomComplet}`}
            icon={<Award size={18} />}
            size="3xl"
            footer={modalFooter}
        >
            {/* Règles CSS dédiées à l'impression A4 */}
            <style>{`
                @media print {
                    @page {
                        size: A4 portrait;
                        margin: 12mm 15mm;
                    }
                    html, body {
                        background: var(--color-print-paper) !important;
                        height: auto !important;
                        min-height: auto !important;
                        overflow: visible !important;
                        margin: 0 !important;
                        padding: 0 !important;
                    }
                    body * {
                        visibility: hidden !important;
                    }
                    #certificat-print-sheet, #certificat-print-sheet * {
                        visibility: visible !important;
                    }
                    #certificat-print-sheet {
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
                    }
                }
            `}</style>

            <div className="space-y-4">
                {/* Options de période */}
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3 text-xs text-[var(--color-fg)] print:hidden">
                    <div className="flex items-center gap-2">
                        <span className="font-medium text-[var(--color-fg-muted)]">Période prise en compte :</span>
                        <div className="inline-flex bg-[var(--color-border-subtle)] p-0.5 rounded-none">
                            <button
                                type="button"
                                onClick={() => setTypeAnciennete('globale')}
                                className={`px-2.5 py-1 text-xs transition-colors cursor-pointer ${
                                    typeAnciennete === 'globale'
                                        ? 'bg-[var(--color-surface)] text-[var(--color-fg)] font-semibold shadow-xs'
                                        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                Carrière complète
                            </button>
                            <button
                                type="button"
                                onClick={() => setTypeAnciennete('contrat')}
                                className={`px-2.5 py-1 text-xs transition-colors cursor-pointer ${
                                    typeAnciennete === 'contrat'
                                        ? 'bg-[var(--color-surface)] text-[var(--color-fg)] font-semibold shadow-xs'
                                        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                Contrat actuel
                            </button>
                        </div>
                    </div>

                    {dureeTotaleTexte && (
                        <div className="text-[var(--color-fg-muted)] text-xs">
                            Durée totale : <span className="font-medium text-[var(--color-fg)]">{dureeTotaleTexte}</span>
                        </div>
                    )}
                </div>

                {/* Feuille de document officielle */}
                {loadingData ? (
                    <div className="py-16 flex justify-center print:hidden">
                        <Loading size="md" label="Chargement du certificat de travail..." />
                    </div>
                ) : (
                    <div className="p-4 sm:p-6 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] print:bg-[var(--color-surface)] print:p-0 print:border-none">
                        <div
                            ref={printRef}
                            id="certificat-print-sheet"
                            className="w-full max-w-[680px] mx-auto bg-[var(--color-surface)] p-8 sm:p-12 shadow-sm text-[var(--color-fg)] leading-relaxed print:shadow-none print:p-0 border border-[var(--color-border-subtle)] print:border-none"
                            style={{ fontFamily: "Calibri, 'Segoe UI', Arial, sans-serif" }}
                        >
                            {/* EN-TÊTE DE SOCIÉTÉ OFFICIEL */}
                            <div className="text-center pb-3 border-b-2 border-[var(--color-print-brand)]">
                                <div className="text-base md:text-lg font-bold tracking-wider uppercase text-[var(--color-print-brand)]">
                                    {ent.nom}
                                </div>
                                <div className="text-xs text-[var(--color-fg)] mt-1">
                                    {ent.adresse} · {ent.ville} · {ent.pays}
                                </div>
                                <div className="text-xs text-[var(--color-fg)]">
                                    {ent.nif && <span>NIF : {ent.nif}</span>}
                                    {ent.nif && ent.stat && <span> · </span>}
                                    {ent.stat && <span>STAT : {ent.stat}</span>}
                                    {ent.rcs && <span> · {ent.rcs}</span>}
                                </div>
                                <div className="text-xs text-[var(--color-fg)]">
                                    Tél. : {ent.telephone} · Email : {ent.email}
                                </div>
                            </div>

                            {/* TITRE DU DOCUMENT */}
                            <div className="text-center mt-8 mb-4">
                                <h1 className="text-lg md:text-xl font-bold uppercase tracking-wide text-[var(--color-fg)]">
                                    CERTIFICAT DE TRAVAIL
                                </h1>
                            </div>

                            {/* RÉFÉRENCE ET DATE */}
                            <div className="text-right text-xs text-[var(--color-fg)] mb-6">
                                Référence : {refDoc} · {sig.ville ? `${sig.ville}, le ` : 'Le '}{sig.dateEmissionTexte}
                            </div>

                            {/* TEXTE DU CERTIFICAT */}
                            <div className="space-y-4 text-xs md:text-sm text-justify leading-relaxed text-[var(--color-fg)]">
                                <p>
                                    Je soussigné(e), <strong>{sig.nom}</strong>, agissant en qualité de <strong>{sig.fonction}</strong> de la société <strong>{ent.nom}</strong>
                                    {ent.adresse ? `, dont le siège social est situé ${ent.adresse}` : ''}
                                    {ent.rcs ? `, immatriculée au RCS sous le numéro ${ent.rcs}` : ''}
                                    {ent.nif || ent.stat ? (
                                        <>, sous le {ent.nif ? <>NIF n° <strong>{ent.nif}</strong></> : ''}{ent.nif && ent.stat ? ' et le ' : ''}{ent.stat ? <>STAT n° <strong>{ent.stat}</strong></> : ''}</>
                                    ) : ''}
                                    , certifie par la présente que :
                                </p>

                                <p>
                                    Madame/Monsieur <strong>{emp.nomComplet}</strong>
                                    {emp.dateNaissance && <>, né(e) le <strong>{fmtDateFr(emp.dateNaissance)}</strong></>}
                                    {emp.lieuNaissance && <> à <strong>{emp.lieuNaissance}</strong></>}
                                    {emp.adresse && <>, demeurant {emp.adresse}</>}
                                    {(emp.dateNaissance || emp.lieuNaissance || emp.adresse) ? ', a été employé(e)' : ' a été employé(e)'} au sein de notre entreprise du <strong>{dateEntreeTexte}</strong> au <strong>{dateSortieTexte}</strong>, soit une durée totale de <strong>{dureeTotaleTexte}</strong>, dans le cadre d'un contrat de travail à <strong>{typeContratLib}</strong>.
                                </p>

                                {(postesOccupes.length > 0 || libFonction) && (
                                    <div>
                                        <p className="mb-2">
                                            Durant cette période, l'intéressé(e) a occupé successivement les postes suivants :
                                        </p>
                                        {postesOccupes.length > 0 ? (
                                            <div className="space-y-1 pl-4">
                                                {postesOccupes.map((p, idx) => (
                                                    <div key={idx}>
                                                        · Du {p.dateDebutTexte} au {p.dateFinTexte} : <strong>{p.libelleFonction}</strong>{p.categorieProfessionnelle ? ` (${p.categorieProfessionnelle})` : ''}{p.departement ? `. Département ${p.departement}` : ''}
                                                    </div>
                                                ))}
                                            </div>
                                        ) : (
                                            <div className="pl-4">
                                                · Du {dateEntreeTexte} au {dateSortieTexte} : <strong>{libFonction}</strong>{catPro ? ` (${catPro})` : ''}{departement ? `. Département ${departement}` : ''}
                                            </div>
                                        )}
                                    </div>
                                )}

                                {libFonction && (
                                    <p>
                                        Au moment de son départ, Madame/Monsieur <strong>{emp.nomComplet}</strong> occupait le poste de <strong>{libFonction}</strong>{catPro ? `, classé au niveau ${catPro}` : ''}{departement ? `, au sein du département ${departement}` : ''}.
                                    </p>
                                )}

                                <p>
                                    Le contrat de travail a pris fin le <strong>{dateSortieTexte}</strong>. {mentionLiberatoire}
                                </p>

                                <p>
                                    Le présent certificat est délivré pour servir et valoir ce que de droit, conformément aux dispositions légales et conventionnelles applicables. Nous confirmons que le/la salarié(e) a rempli ses fonctions avec sérieux et professionnalisme durant toute la durée de sa collaboration au sein de notre entreprise.
                                </p>
                            </div>

                            {/* SIGNATURE & CACHET */}
                            <div className="mt-12 text-right text-xs leading-relaxed text-[var(--color-fg)]">
                                <p>{sig.ville ? `${sig.ville}, le ` : 'Le '}{sig.dateEmissionTexte}</p>
                                <p className="mt-1">Fait pour servir et valoir ce que de droit.</p>
                                <div className="pt-6">
                                    <p className="font-bold">{sig.nom}</p>
                                    <p>{sig.fonction}</p>
                                    <p className="text-[var(--color-fg-muted)] pt-1">Signature et cachet de l'entreprise</p>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Modal>
    );
}
