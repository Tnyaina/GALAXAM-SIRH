import { useState, useEffect, useCallback } from 'react';
import { FileCheck, Award, Download } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Loading from '@/components/ui/feedback/Loading';
import Modal from '@/components/ui/overlays/Modal';
import documentService from '@/api/documentService';
import { fmtDateFr } from '@/utils/dateUtils';

/**
 * Modal unifiée pour l'édition et l'impression des documents officiels RH :
 * - Attestation de travail (salarié en activité)
 * - Certificat de travail (salarié en fin de contrat / sortant)
 */
export default function DocumentOfficielModal({
    isOpen,
    onClose,
    typeDocument = 'ATTESTATION', // 'ATTESTATION' | 'CERTIFICAT'
    employe,
    contrat,
    affectation,
    filiale,
    signataire,
    dateDebutInitiale = null,
}) {
    const isAttestation = typeDocument === 'ATTESTATION';

    const [typeAnciennete, setTypeAnciennete] = useState('globale');
    const [inclureSalaire, setInclureSalaire] = useState(false);
    const [loadingData, setLoadingData] = useState(true);
    const [downloadingDocx, setDownloadingDocx] = useState(false);
    const [docData, setDocData] = useState(null);

    const idEmp = employe?.idEmploye || contrat?.idEmploye;
    const idContrat = contrat?.idContrat;

    const chargerDonnees = useCallback(async () => {
        setLoadingData(true);
        try {
            if (isAttestation) {
                if (!idEmp) return;
                const data = await documentService.getAttestationData(idEmp, typeAnciennete, inclureSalaire);
                setDocData(data);
            } else {
                if (!idContrat) return;
                const data = await documentService.getCertificatData(idContrat, typeAnciennete);
                setDocData(data);
            }
        } catch (err) {
            console.error(`Erreur chargement ${typeDocument.toLowerCase()} backend:`, err);
        } finally {
            setLoadingData(false);
        }
    }, [isAttestation, idEmp, idContrat, typeAnciennete, inclureSalaire, typeDocument]);

    useEffect(() => {
        if (isOpen) {
            chargerDonnees();
        }
    }, [isOpen, chargerDonnees]);

    const handleDownloadDocx = async () => {
        if (downloadingDocx) return;
        setDownloadingDocx(true);
        try {
            if (isAttestation) {
                if (!idEmp) return;
                const filename = `Attestation_Travail_${docData?.employe?.matricule || idEmp}.docx`;
                await documentService.downloadAttestationDocx(idEmp, typeAnciennete, inclureSalaire, filename);
            } else {
                if (!idContrat) return;
                const filename = `Certificat_Travail_${docData?.employe?.matricule || idContrat}.docx`;
                await documentService.downloadCertificatDocx(idContrat, typeAnciennete, filename);
            }
        } catch (err) {
            console.error(`Erreur téléchargement DOCX ${typeDocument.toLowerCase()}:`, err);
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
        nationalite: employe?.nationalite || '',
        numeroCin: employe?.cin || employe?.numeroCin || '',
        adresse: employe?.adresse || '',
    };

    const sig = docData?.signataire || {
        nom: signataire?.nom || 'Direction des Ressources Humaines',
        fonction: signataire?.fonction || 'Directeur des Ressources Humaines',
        ville: filiale?.ville || null,
        dateEmissionTexte: fmtDateFr(new Date()),
    };

    const refPrefix = isAttestation ? 'ATT-' : 'CERT-';
    const refDoc = docData?.reference || `${refPrefix}${contrat?.numeroContrat || 'CTR'}`;
    const dateEntreeTexte = docData?.dateEntreeTexte || fmtDateFr(dateDebutInitiale || contrat?.dateDebut || new Date());
    const dateSortieTexte = docData?.dateSortieTexte || fmtDateFr(contrat?.dateFin || new Date());
    const dureeTotaleTexte = docData?.dureeTotaleTexte || '';
    const ancienneteCalculee = docData?.ancienneteTexte || '';
    const typeContratLib = docData?.typeContrat || 'durée indéterminée';
    const libFonction = isAttestation
        ? (docData?.libelleFonction || affectation?.libelleFonction || contrat?.libelleFonction || '')
        : (docData?.dernierPoste || affectation?.libelleFonction || contrat?.libelleFonction || '');
    const catPro = isAttestation
        ? (docData?.categorieProfessionnelle || affectation?.libelleCategorieProfessionnelle || '')
        : (docData?.derniereCategorie || affectation?.libelleCategorieProfessionnelle || '');
    const departement = isAttestation
        ? (docData?.departement || affectation?.libelleDepartement || '')
        : (docData?.dernierDepartement || affectation?.libelleDepartement || '');
    const superieur = docData?.superieurHierarchique || '';
    const tempsTravail = docData?.tempsTravail || 'plein';
    const heuresHebdo = docData?.heuresHebdomadaires || 40;
    const postesOccupes = docData?.postesOccupes || [];

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

    const sheetId = isAttestation ? 'attestation-print-sheet' : 'certificat-print-sheet';
    const modalTitle = isAttestation
        ? `Attestation de travail · ${emp.nomComplet}`
        : `Certificat de travail · ${emp.nomComplet}`;
    const modalIcon = isAttestation ? <FileCheck size={18} /> : <Award size={18} />;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={modalTitle}
            icon={modalIcon}
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
                    #${sheetId}, #${sheetId} * {
                        visibility: visible !important;
                    }
                    #${sheetId} {
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
                {/* Options d'affichage et de contenu */}
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3 text-xs text-[var(--color-fg)] print:hidden">
                    <div className="flex flex-wrap items-center gap-4">
                        <div className="flex items-center gap-2">
                            <span className="font-medium text-[var(--color-fg-muted)]">
                                {isAttestation ? "Base d'ancienneté :" : "Période prise en compte :"}
                            </span>
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
                                    {isAttestation ? "Depuis l'embauche initiale" : "Carrière complète"}
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

                        {isAttestation && (
                            <label className="flex items-center gap-2 cursor-pointer select-none font-normal text-[var(--color-fg)]">
                                <input
                                    type="checkbox"
                                    checked={inclureSalaire}
                                    onChange={(e) => setInclureSalaire(e.target.checked)}
                                    className="w-3.5 h-3.5 text-[var(--color-brand-600)] rounded-none border-[var(--color-border-subtle)] focus:ring-[var(--color-brand-500)] cursor-pointer"
                                />
                                <span>Mentionner le salaire de base</span>
                            </label>
                        )}
                    </div>

                    {isAttestation && ancienneteCalculee && (
                        <div className="text-[var(--color-fg-muted)] text-xs">
                            Ancienneté : <span className="font-medium text-[var(--color-fg)]">{ancienneteCalculee}</span>
                        </div>
                    )}

                    {!isAttestation && dureeTotaleTexte && (
                        <div className="text-[var(--color-fg-muted)] text-xs">
                            Durée totale : <span className="font-medium text-[var(--color-fg)]">{dureeTotaleTexte}</span>
                        </div>
                    )}
                </div>

                {/* Feuille de document officielle */}
                {loadingData ? (
                    <div className="py-16 flex justify-center print:hidden">
                        <Loading size="md" label={`Chargement ${isAttestation ? "de l'attestation" : "du certificat"} de travail...`} />
                    </div>
                ) : (
                    <div className="p-4 sm:p-6 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] print:bg-[var(--color-surface)] print:p-0 print:border-none">
                        <div
                            id={sheetId}
                            className="w-full max-w-[680px] mx-auto bg-[var(--color-surface)] p-8 sm:p-12 shadow-sm text-[var(--color-fg)] leading-relaxed print:shadow-none print:p-0 border border-[var(--color-border-subtle)] print:border-none"
                            style={{ fontFamily: "Calibri, 'Segoe UI', Arial, sans-serif" }}
                        >
                            {/* EN-TÊTE ENTREPRISE OFFICIEL */}
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
                                    {isAttestation ? 'ATTESTATION DE TRAVAIL' : 'CERTIFICAT DE TRAVAIL'}
                                </h1>
                            </div>

                            {/* RÉFÉRENCE ET DATE */}
                            <div className="text-right text-xs text-[var(--color-fg)] mb-6">
                                Référence : {refDoc} · {sig.ville ? `${sig.ville}, le ` : 'Le '}{sig.dateEmissionTexte}
                            </div>

                            {/* TEXTE JURIDIQUE STRICT */}
                            <div className="space-y-4 text-xs md:text-sm text-justify leading-relaxed text-[var(--color-fg)]">
                                <p>
                                    Je soussigné(e), <strong>{sig.nom}</strong>, agissant en qualité de <strong>{sig.fonction}</strong> de la société <strong>{ent.nom}</strong>
                                    {ent.adresse ? `, dont le siège social est situé ${ent.adresse}` : ''}
                                    {ent.rcs ? `, immatriculée au RCS sous le numéro ${ent.rcs}` : ''}
                                    {ent.nif || ent.stat ? (
                                        <>, sous le {ent.nif ? <>NIF n° <strong>{ent.nif}</strong></> : ''}{ent.nif && ent.stat ? ' et le ' : ''}{ent.stat ? <>STAT n° <strong>{ent.stat}</strong></> : ''}</>
                                    ) : ''}
                                    , {isAttestation ? 'atteste' : 'certifie'} par la présente que :
                                </p>

                                {isAttestation ? (
                                    <>
                                        <p>
                                            Madame/Monsieur <strong>{emp.nomComplet}</strong>
                                            {emp.dateNaissance && <>, né(e) le <strong>{fmtDateFr(emp.dateNaissance)}</strong></>}
                                            {emp.lieuNaissance && <> à <strong>{emp.lieuNaissance}</strong></>}
                                            {emp.nationalite && <>, de nationalité <strong>{emp.nationalite}</strong></>}
                                            {emp.numeroCin && <>, titulaire de la pièce d'identité n° <strong>{emp.numeroCin}</strong></>}
                                            {emp.adresse && <>, demeurant {emp.adresse}</>}
                                            {(emp.dateNaissance || emp.lieuNaissance || emp.nationalite || emp.numeroCin || emp.adresse) ? ', est employé(e)' : ' est employé(e)'} au sein de notre entreprise depuis le <strong>{dateEntreeTexte}</strong>, dans le cadre d'un contrat de travail à <strong>{typeContratLib}</strong>.
                                        </p>

                                        <p>
                                            {libFonction ? (
                                                <>Il/Elle occupe actuellement le poste de <strong>{libFonction}</strong></>
                                            ) : (
                                                <>Il/Elle est actuellement en fonction</>
                                            )}
                                            {catPro ? ` (${catPro})` : ''}
                                            {departement ? ` au sein du département ${departement}` : ''}
                                            {superieur ? `, sous la responsabilité de ${superieur}` : ''}.
                                            {' '}Il/Elle exerce ses fonctions à temps <strong>{tempsTravail}</strong>, pour une durée hebdomadaire de <strong>{heuresHebdo} heures</strong>.
                                        </p>

                                        {inclureSalaire && docData?.salaireBaseTexte && (
                                            <p>
                                                La rémunération mensuelle de base brute de l'intéressé(e) s'élève à <strong>{docData.salaireBaseTexte} Ariary</strong>.
                                            </p>
                                        )}

                                        <p>
                                            Cette attestation est délivrée à l'intéressé(e) à sa demande, pour servir et valoir ce que de droit, notamment auprès de toute administration, organisme bancaire, ambassade ou consulat.
                                        </p>
                                    </>
                                ) : (
                                    <>
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
                                            Le contrat de travail a pris fin le <strong>{dateSortieTexte}</strong>.
                                        </p>

                                        <p>
                                            Le présent certificat est délivré pour servir et valoir ce que de droit.
                                        </p>
                                    </>
                                )}
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
