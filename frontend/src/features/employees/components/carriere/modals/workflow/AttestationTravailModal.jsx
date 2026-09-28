import { useState, useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { Printer, X, Download } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Loading from '@/components/ui/feedback/Loading';
import documentService from '@/api/documentService';
import { fmtDateFr } from '@/utils/dateUtils';

export default function AttestationTravailModal({
    isOpen,
    onClose,
    employe,
    contrat,
    affectation,
    filiale,
    signataire,
    dateDebutInitiale = null,
}) {
    // État local : animation, options de document et chargement.
    const [mounted, setMounted] = useState(false);
    const [typeAnciennete, setTypeAnciennete] = useState('globale'); // 'contrat' | 'globale'
    const [inclureSalaire, setInclureSalaire] = useState(false);
    const [loadingData, setLoadingData] = useState(true);
    const [downloadingDocx, setDownloadingDocx] = useState(false);
    const [docData, setDocData] = useState(null);
    const printRef = useRef(null);

    // Données dérivées et référence DOM du document.
    const idEmp = employe?.idEmploye || contrat?.idEmploye;

    // Chargement et actions utilisateur
    const chargerDonnees = useCallback(async () => {
        if (!idEmp) return;
        setLoadingData(true);
        try {
            const data = await documentService.getAttestationData(idEmp, typeAnciennete, inclureSalaire);
            setDocData(data);
        } catch (err) {
            console.error('Erreur chargement attestation backend:', err);
        } finally {
            setLoadingData(false);
        }
    }, [idEmp, typeAnciennete, inclureSalaire]);

    // Effets : chargent à l'ouverture et ferment avec Échap.
    useEffect(() => {
        if (isOpen) {
            chargerDonnees();
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
    }, [isOpen, chargerDonnees]);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    const handleDownloadDocx = async () => {
        if (!idEmp || downloadingDocx) return;
        setDownloadingDocx(true);
        try {
            const filename = `Attestation_Travail_${docData?.employe?.matricule || idEmp}.docx`;
            await documentService.downloadAttestationDocx(idEmp, typeAnciennete, inclureSalaire, filename);
        } catch (err) {
            console.error('Erreur téléchargement DOCX:', err);
        } finally {
            setDownloadingDocx(false);
        }
    };

    const handlePrint = () => {
        window.print();
    };

    if (!isOpen) return null;

    // Données réelles selon modèle d'en-tête officiel
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

    const refDoc = docData?.reference || `ATT-${contrat?.numeroContrat || 'CTR'}`;
    const dateEntreeTexte = docData?.dateEntreeTexte || fmtDateFr(dateDebutInitiale || contrat?.dateDebut || new Date());
    const libFonction = docData?.libelleFonction || affectation?.libelleFonction || contrat?.libelleFonction || '';
    const catPro = docData?.categorieProfessionnelle || affectation?.libelleCategorieProfessionnelle || '';
    const departement = docData?.departement || affectation?.libelleDepartement || '';
    const superieur = docData?.superieurHierarchique || '';
    const tempsTravail = docData?.tempsTravail || 'plein';
    const heuresHebdo = docData?.heuresHebdomadaires || 40;
    const typeContratLib = docData?.typeContrat || 'durée indéterminée';
    const ancienneteCalculee = docData?.ancienneteTexte || '';

    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-6 bg-black/60 backdrop-blur-[2px] print:p-0 print:bg-white print:static print:block">
            {/* Règles CSS dédiées à l'impression A4 évitant le cadrage déformé et masquant le reste de la page */}
            <style>{`
                @media print {
                    @page {
                        size: A4 portrait;
                        margin: 12mm 15mm;
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
                    #attestation-print-sheet, #attestation-print-sheet * {
                        visibility: visible !important;
                    }
                    #attestation-print-sheet {
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

            <div className="absolute inset-0 print:hidden" onClick={onClose} />

            {/* Modale Container */}
            <div
                className={`
                    relative bg-white border border-gray-300 rounded shadow-2xl
                    w-full max-w-4xl max-h-[95vh] overflow-hidden flex flex-col
                    transform print:shadow-none print:border-none print:w-full print:max-w-none print:h-auto print:max-h-none print:overflow-visible print:transform-none print:p-0
                    ${mounted ? 'scale-100 opacity-100' : 'scale-95 opacity-0'}
                `}
            >
                {/* Barre d'actions (masquée à l'impression) */}
                <div className="px-6 py-3.5 border-b border-gray-200 bg-gray-50 flex-shrink-0 flex items-center justify-between print:hidden">
                    <div className="flex items-center gap-2">
                        <span className="font-semibold text-sm text-gray-900">Attestation de Travail</span>
                        <span className="text-xs text-gray-500">({refDoc})</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            type="button"
                            onClick={handleDownloadDocx}
                            variant="outline"
                            size="sm"
                            loading={downloadingDocx}
                            iconLeft={<Download size={15} />}
                            title="Télécharger le fichier Word (.docx) conforme au modèle officiel"
                        >
                            Télécharger Word (.docx)
                        </Button>
                        <Button
                            type="button"
                            onClick={handlePrint}
                            variant="primary"
                            size="sm"
                            iconLeft={<Printer size={15} />}
                            title="Imprimer ou enregistrer en PDF (dans la fenêtre, décocher « En-têtes et pieds de page » pour masquer l'URL)"
                        >
                            Imprimer / PDF
                        </Button>
                        <button
                            onClick={onClose}
                            className="text-gray-400 hover:text-gray-700 p-1 ml-2 rounded hover:bg-gray-200 transition-colors"
                            aria-label="Fermer"
                        >
                            <X size={18} />
                        </button>
                    </div>
                </div>

                {/* Options d'attestation (masquée à l'impression) */}
                <div className="px-6 py-2.5 bg-gray-100/70 border-b border-gray-200 flex flex-wrap items-center justify-between gap-4 flex-shrink-0 print:hidden text-xs text-gray-700">
                    <div className="flex flex-wrap items-center gap-4">
                        <div className="flex items-center gap-2">
                            <span className="font-medium text-gray-600">Base d'ancienneté :</span>
                            <div className="inline-flex bg-gray-200 p-0.5 rounded">
                                <button
                                    type="button"
                                    onClick={() => setTypeAnciennete('globale')}
                                    className={`px-2.5 py-0.5 rounded text-xs transition-colors ${
                                        typeAnciennete === 'globale'
                                            ? 'bg-white text-gray-900 font-semibold shadow-xs'
                                            : 'text-gray-600 hover:text-gray-900'
                                    }`}
                                >
                                    Depuis l'embauche initiale
                                </button>
                                <button
                                    type="button"
                                    onClick={() => setTypeAnciennete('contrat')}
                                    className={`px-2.5 py-0.5 rounded text-xs transition-colors ${
                                        typeAnciennete === 'contrat'
                                            ? 'bg-white text-gray-900 font-semibold shadow-xs'
                                            : 'text-gray-600 hover:text-gray-900'
                                    }`}
                                >
                                    Contrat actuel
                                </button>
                            </div>
                        </div>

                        <label className="flex items-center gap-2 cursor-pointer select-none font-normal text-gray-700">
                            <input
                                type="checkbox"
                                checked={inclureSalaire}
                                onChange={(e) => setInclureSalaire(e.target.checked)}
                                className="w-3.5 h-3.5 text-blue-600 rounded border-gray-300 focus:ring-blue-500 cursor-pointer"
                            />
                            <span>Mentionner le salaire de base</span>
                        </label>
                    </div>

                    {ancienneteCalculee && (
                        <div className="text-gray-600 text-xs">
                            Ancienneté : <span className="font-medium text-gray-900">{ancienneteCalculee}</span>
                        </div>
                    )}
                </div>

                {/* Feuille de document officielle — Rendu 1:1 modèle Word (Police Calibri, pas de déco superflue) */}
                <div className="flex-1 overflow-y-auto p-6 lg:p-10 bg-gray-200/60 print:bg-white print:p-0 print:overflow-visible print:block">
                    {loadingData ? (
                        <div className="py-20 flex justify-center print:hidden">
                            <Loading size="lg" label="Chargement du document officiel..." />
                        </div>
                    ) : (
                        <div
                            ref={printRef}
                            id="attestation-print-sheet"
                            className="w-full max-w-[720px] mx-auto bg-white p-10 md:p-14 shadow-md text-black leading-relaxed print:shadow-none print:p-0"
                            style={{ fontFamily: "Calibri, 'Segoe UI', Arial, sans-serif" }}
                        >
                            {/* EN-TÊTE ENTREPRISE OFFICIEL */}
                            <div className="text-center pb-3 border-b-2 border-[#1f3864]">
                                <div className="text-base md:text-lg font-bold tracking-wider uppercase text-[#1f3864]">
                                    {ent.nom}
                                </div>
                                <div className="text-xs text-gray-700 mt-1">
                                    {ent.adresse} · {ent.ville} · {ent.pays}
                                </div>
                                <div className="text-xs text-gray-700">
                                    {ent.nif && <span>NIF : {ent.nif}</span>}
                                    {ent.nif && ent.stat && <span> · </span>}
                                    {ent.stat && <span>STAT : {ent.stat}</span>}
                                    {ent.rcs && <span> · {ent.rcs}</span>}
                                </div>
                                <div className="text-xs text-gray-700">
                                    Tél. : {ent.telephone} · Email : {ent.email}
                                </div>
                            </div>

                            {/* TITRE DU DOCUMENT */}
                            <div className="text-center mt-8 mb-4">
                                <h1 className="text-lg md:text-xl font-bold uppercase tracking-wide text-black">
                                    ATTESTATION DE TRAVAIL
                                </h1>
                            </div>

                            {/* RÉFÉRENCE ET DATE */}
                            <div className="text-right text-xs text-gray-700 mb-6">
                                Référence : {refDoc} — {sig.ville}, le {sig.dateEmissionTexte}
                            </div>

                            {/* TEXTE OFFICIEL STRICT (CONFORME AU MODÈLE DOCX) */}
                            <div className="space-y-4 text-xs md:text-sm text-justify leading-relaxed text-black">
                                <p>
                                    Je soussigné(e), <strong>{sig.nom}</strong>, agissant en qualité de <strong>{sig.fonction}</strong> de la société <strong>{ent.nom}</strong>
                                    {ent.adresse ? `, dont le siège social est situé ${ent.adresse}` : ''}
                                    {ent.rcs ? `, immatriculée au RCS sous le numéro ${ent.rcs}` : ''}
                                    {ent.nif || ent.stat ? (
                                        <>, sous le {ent.nif ? <>NIF n° <strong>{ent.nif}</strong></> : ''}{ent.nif && ent.stat ? ' et le ' : ''}{ent.stat ? <>STAT n° <strong>{ent.stat}</strong></> : ''}</>
                                    ) : ''}
                                    , atteste par la présente que :
                                </p>

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
                            </div>

                            {/* SIGNATURE & CACHET (ALIGNE À DROITE, CONFORME MODÈLE) */}
                            <div className="mt-12 text-right text-xs leading-relaxed text-black">
                                <p>{sig.ville ? `${sig.ville}, le ` : 'Le '}{sig.dateEmissionTexte}</p>
                                <p className="italic mt-1">Fait pour servir et valoir ce que de droit.</p>
                                <div className="pt-6">
                                    <p className="font-bold">{sig.nom}</p>
                                    <p>{sig.fonction}</p>
                                    <p className="text-gray-500 pt-1">Signature et cachet de l'entreprise</p>
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>,
        document.body
    );
}
