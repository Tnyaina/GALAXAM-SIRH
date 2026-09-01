import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Printer, X, Award, CheckCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { DocumentFrame } from '@/components/carriere/ContractDocument';
import { fmtDateFr } from '@/utils/carriereUtils';

export default function CertificatTravailModal({
    isOpen,
    onClose,
    employe,
    contrat,
    affectation,
    loadingAffectation = false, // true pendant la récupération de l'affectation liée au contrat (contrat historique)
    filiale,
    signataire, // { nom, fonction } — optionnel, personne habilitée qui délivre le certificat
    dateDebutInitiale = null,
}) {
    const [mounted, setMounted] = useState(false);
    const [typeAnciennete, setTypeAnciennete] = useState('contrat'); // 'contrat' | 'globale'
    const printRef = useRef(null);

    useEffect(() => {
        if (isOpen) {
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
    }, [isOpen]);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    const getVal = (...keys) => {
        for (const k of keys) {
            if (k !== undefined && k !== null && String(k).trim() !== '') {
                return String(k).trim();
            }
        }
        return null;
    };

    const nomComplet = `${employe?.nom || ''} ${employe?.prenom || ''}`.trim() || '……………';
    const numContrat = contrat?.numeroContrat || 'CTR-EXPIRE';
    const filialeNom = filiale?.libelle || contrat?.libelleFiliale || employe?.filiale || employe?.libelleFiliale || 'GALAXAM LOGISTICS';
    const filialeAdresse = filiale?.adresse || contrat?.adresseFiliale || employe?.adresseFiliale || null;
    const filialeNif = filiale?.nif || contrat?.nifFiliale || employe?.nifFiliale || null;
    const filialeStat = filiale?.stat || contrat?.statFiliale || employe?.statFiliale || null;

    const dateDebutContrat = contrat?.dateDebut;
    const dateFinRaw = contrat?.dateFin || new Date();
    const dateFin = fmtDateFr(dateFinRaw);

    const dateDebutGlobale = contrat?.dateOrigineChaine || dateDebutInitiale || employe?.dateEmbauche || employe?.dateEntree || contrat?.dateDebut;

    const dateDebutEffectif = typeAnciennete === 'globale' && dateDebutGlobale ? dateDebutGlobale : dateDebutContrat;
    const dateDebut = fmtDateFr(dateDebutEffectif);
    const anciennete = typeAnciennete === 'globale'
        ? (contrat?.ancienneteEffectiveTexte || '—')
        : (contrat?.ancienneteContratTexte || '—');

    const fonction = getVal(
        affectation?.libelleFonction,
        affectation?.fonction,
        affectation?.nomFonction,
        contrat?.libelleFonction,
        contrat?.libelleFonctionCible,
        contrat?.libelleFonctionSource,
        contrat?.fonction,
        employe?.fonction,
        employe?.poste,
        employe?.libelleFonction,
        employe?.titrePoste
    ) || '—';

    const categorieProfessionnelle = getVal(
        affectation?.libelleCategorieProfessionnelle,
        affectation?.libelleCategoriePro,
        affectation?.libelleCategorie,
        affectation?.categorie,
        contrat?.libelleCategorieProfessionnelle,
        contrat?.libelleCategoriePro,
        contrat?.libelleCategorie,
        contrat?.categorie,
        employe?.categorieProfessionnelle,
        employe?.libelleCategoriePro,
        employe?.categorie
    );

    const natureContrat = getVal(
        contrat?.libelleTypeContrat,
        contrat?.libelleTypeContratCible,
        contrat?.typeContrat,
        affectation?.libelleTypeContrat
    );

    const siteNom = getVal(
        affectation?.libelleSite,
        contrat?.libelleSite,
        employe?.site,
        employe?.libelleSite
    ) || 'Antananarivo';

    const deptNom = getVal(
        affectation?.libelleDepartement,
        contrat?.libelleDepartement,
        employe?.departement,
        employe?.libelleDepartement
    ) || 'Siège';

    const siteDept = `${siteNom} - ${deptNom}`;

    const employeAdresse = employe?.adresse || null;
    const employeCin = employe?.cin || employe?.numeroCin || null;
    const employeMatricule = employe?.matricule || null;
    const employeCnaps = employe?.numeroCnaps || null;

    const dateEmission = fmtDateFr(new Date());
    const signataireNom = signataire?.nom || null;
    const signataireFonction = signataire?.fonction || 'Directeur des Ressources Humaines';

    const handlePrint = () => {
        window.print();
    };

    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-5 bg-black/60 backdrop-blur-[3px] print:p-0 print:bg-white print:static print:block">
            <div className="absolute inset-0 print:hidden" onClick={onClose} />

            {/* Modale Container */}
            <div
                className={`
                    relative bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-none shadow-2xl
                    w-full max-w-3xl max-h-[92vh] overflow-hidden flex flex-col
                    transform    print:shadow-none print:border-none print:w-full print:max-w-none print:h-auto print:max-h-none print:overflow-visible print:transform-none print:p-0 print:bg-white
                    ${mounted ? 'scale-100 opacity-100' : 'scale-95 opacity-0'}
                `}
            >
                {/* Header d'actions (masqué à l'impression) */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-ink-50 dark:bg-ink-950 flex-shrink-0 flex items-center justify-between print:hidden">
                    <div className="flex items-center gap-2 font-bold text-base text-ink-900 dark:text-white">
                        <Award size={20} className="text-brand-500" />
                        <span>Certificat de Travail Officiel</span>
                        <span className="text-xs text-ink-400 font-normal">({numContrat})</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            type="button"
                            onClick={handlePrint}
                            variant="primary"
                            size="md"
                            iconLeft={<Printer size={16} />}
                        >
                            Imprimer / Imprimer en PDF
                        </Button>
                        <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-none">
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Barre de sélection du mode d'ancienneté (masquée à l'impression) */}
                <div className="px-6 py-2.5 bg-brand-500/5 dark:bg-brand-500/10 border-b border-ink-150 dark:border-ink-700 flex flex-wrap items-center justify-between gap-3 flex-shrink-0 print:hidden text-xs">
                    <div className="flex items-center gap-2">
                        <span className="font-semibold text-ink-600 dark:text-ink-300">Base d'ancienneté :</span>
                        <div className="inline-flex p-0.5 bg-ink-150 dark:bg-ink-800 rounded-none">
                            <button
                                type="button"
                                onClick={() => setTypeAnciennete('contrat')}
                                className={`px-3 py-1 text-xs font-semibold rounded-xs  cursor-pointer ${
                                    typeAnciennete === 'contrat'
                                        ? 'bg-white dark:bg-ink-900 text-brand-500 shadow-xs font-bold'
                                        : 'text-ink-600 dark:text-ink-400 hover:text-ink-900 dark:hover:text-white'
                                }`}
                            >
                                Spécifique au contrat ({fmtDateFr(dateDebutContrat)})
                            </button>
                            <button
                                type="button"
                                onClick={() => setTypeAnciennete('globale')}
                                className={`px-3 py-1 text-xs font-semibold rounded-xs  cursor-pointer ${
                                    typeAnciennete === 'globale'
                                        ? 'bg-white dark:bg-ink-900 text-brand-500 shadow-xs font-bold'
                                        : 'text-ink-600 dark:text-ink-400 hover:text-ink-900 dark:hover:text-white'
                                }`}
                            >
                                Globale entreprise ({fmtDateFr(dateDebutGlobale)})
                            </button>
                        </div>
                    </div>
                    <span className="font-mono font-bold text-brand-500 text-xs">
                        Ancienneté calculée : {anciennete || '—'}
                    </span>
                </div>

                {/* Corps du Document Officiel */}
                <div className="flex-1 overflow-y-auto p-6 lg:p-10 bg-ink-50/50 dark:bg-ink-950/40 print:bg-white print:p-0 print:overflow-visible print:block">
                    {loadingAffectation && (
                        <div className="mb-4 text-center text-[11px] text-ink-400 print:hidden">
                            Récupération des données du poste (fonction, site, département)...
                        </div>
                    )}
                    <div ref={printRef} className="certificat-print-portal w-full max-w-[650px] mx-auto print:max-w-none print:w-full print:m-0 print:p-0">

                        <DocumentFrame watermark={null} className="border border-ink-200 dark:border-ink-700 bg-white dark:bg-ink-900 p-8 shadow-sm print:border-none print:p-6 print:bg-white print:text-black">

                            {/* En-tête de Société */}
                            <div className="text-center pb-6 border-b-2 border-ink-900 dark:border-white mb-6 print:pb-3 print:mb-3">
                                <h1 className="text-2xl font-black uppercase tracking-widest text-ink-900 dark:text-white font-serif print:text-xl">
                                    {filialeNom}
                                </h1>
                                <p className="text-[11px] font-sans uppercase tracking-widest text-ink-500 mt-1 print:text-[10px]">
                                    Direction des Ressources Humaines &amp; Juridique
                                </p>
                                {filialeAdresse && (
                                    <p className="text-[10px] font-sans text-ink-400 mt-1 print:text-[9px]">{filialeAdresse}</p>
                                )}
                                {(filialeNif || filialeStat) && (
                                    <p className="text-[10px] font-sans text-ink-400 mt-0.5 print:text-[9px]">
                                        {filialeNif && <>NIF&nbsp;: {filialeNif}</>}
                                        {filialeNif && filialeStat && <span className="mx-1.5">·</span>}
                                        {filialeStat && <>STAT&nbsp;: {filialeStat}</>}
                                    </p>
                                )}
                            </div>

                            {/* Titre du document */}
                            <div className="py-6 text-center print:py-3">
                                <h2 className="text-2xl font-serif font-bold uppercase tracking-wider text-ink-900 dark:text-white print:text-xl">
                                    Certificat de Travail
                                </h2>
                                <p className="text-xs font-mono text-ink-400 mt-2 print:mt-1">Réf. N° CERT-{numContrat}</p>
                            </div>

                            {/* Attestation officielle */}
                            <div className="py-4 space-y-5 text-sm leading-[1.9] font-serif text-ink-900 dark:text-ink-100 text-justify print:py-2 print:space-y-3 print:leading-loose">
                                <p>
                                    Je soussigné(e), agissant en qualité de <strong>{signataireFonction}</strong> de la société <strong>{filialeNom}</strong>{filialeAdresse && <>, établie au <strong>{filialeAdresse}</strong></>}, certifie par la présente que :
                                </p>

                                <p>
                                    <strong>Monsieur / Madame {nomComplet}</strong>
                                    {employeAdresse && <>, domicilié(e) à {employeAdresse}</>}
                                    {employeMatricule && <>, matricule interne n° <strong>{employeMatricule}</strong></>}
                                    {employeCin && <>, titulaire de la CIN n° <strong>{employeCin}</strong></>},
                                    {' '}a été employé(e) au sein de notre établissement du <strong>{dateDebut}</strong> au <strong>{dateFin}</strong>
                                    {anciennete && <>, soit une ancienneté de <strong>{anciennete}</strong></>},
                                    {' '}sous contrat n° <strong>{numContrat}</strong>{natureContrat && <> ({natureContrat})</>}.
                                </p>

                                <p>
                                    Au cours de cette période, l'intéressé(e) a occupé le poste de <strong>{fonction}</strong>
                                    {categorieProfessionnelle && <> — catégorie professionnelle {categorieProfessionnelle}</>},
                                    {' '}au sein de {siteDept}.
                                </p>

                                <p>
                                    L'intéressé(e) a quitté nos effectifs le <strong>{dateFin}</strong>{employeCnaps && <>. Son numéro d'immatriculation à la Caisse Nationale de Prévoyance Sociale (CNaPS) est <strong>{employeCnaps}</strong></>}.
                                </p>

                                <p className="text-xs text-ink-500 pt-1">
                                    Le présent certificat est délivré pour servir et valoir ce que de droit. Il ne constitue ni un reçu pour solde de tout compte, ni une renonciation à un droit quelconque de l'une ou l'autre des parties.
                                </p>
                            </div>

                            {/* Signatures & Cachet */}
                            <div className="pt-10 mt-6 border-t border-ink-150 dark:border-ink-800 flex justify-between items-end text-xs font-sans print:pt-4 print:mt-4">
                                <div>
                                    <p className="text-ink-400">Délivré à Antananarivo, le</p>
                                    <p className="font-bold text-ink-900 dark:text-white">{dateEmission}</p>
                                </div>

                                <div className="text-center pr-6">
                                    <p className="font-bold uppercase tracking-wider text-ink-900 dark:text-white print:pb-8">
                                        {signataireFonction}
                                    </p>
                                    {signataireNom && (
                                        <p className="text-ink-500 pb-8 print:pb-6">{signataireNom}</p>
                                    )}
                                    {!signataireNom && <div className="pb-12 print:pb-8" />}
                                    <div className="border-t border-dashed border-ink-300 dark:border-ink-700 w-48 mx-auto pt-1 text-[10px] text-ink-400 print:border-black">
                                        Signature &amp; Cachet Officiel
                                    </div>
                                </div>
                            </div>

                        </DocumentFrame>

                    </div>
                </div>

                {/* Footer Modal (masqué à l'impression) */}
                <div className="px-6 py-3 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex items-center justify-between text-xs text-ink-400 print:hidden">
                    <span className="flex items-center gap-1.5 text-success-600 font-semibold">
                        <CheckCircle size={14} /> Document RH conforme aux normes réglementaires
                    </span>
                    <Button type="button" onClick={onClose} variant="outline" size="md">
                        Fermer
                    </Button>
                </div>
            </div>
        </div>,
        document.body
    );
}