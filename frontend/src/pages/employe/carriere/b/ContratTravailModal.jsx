import { useState, useEffect, useRef } from 'react';
import { createPortal } from 'react-dom';
import { Printer, X, FileText, Building, Calendar, DollarSign, UserCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { DocumentFrame } from '@/components/carriere/ContractDocument';
import HasPermission from '@/components/auth/HasPermission';
import { fmtDateFr, fmtMoney } from '@/utils/carriereUtils';

export default function ContratTravailModal({
    isOpen,
    onClose,
    employe,
    contrat,
    affectation,
    loadingAffectation = false,
    filiale,
}) {
    const [mounted, setMounted] = useState(false);
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
    const numContrat = contrat?.numeroContrat || 'CTR-OFFICIEL';
    const filialeNom = filiale?.libelle || contrat?.libelleFiliale || employe?.filiale || employe?.libelleFiliale || 'GALAXAM LOGISTICS';
    const filialeAdresse = filiale?.adresse || contrat?.adresseFiliale || employe?.adresseFiliale || null;
    const filialeNif = filiale?.nif || contrat?.nifFiliale || employe?.nifFiliale || null;
    const filialeStat = filiale?.stat || contrat?.statFiliale || employe?.statFiliale || null;

    const dateDebut = fmtDateFr(contrat?.dateDebut);
    const dateFin = contrat?.dateFin ? fmtDateFr(contrat?.dateFin) : null;
    const dateSignature = fmtDateFr(contrat?.dateSignature || contrat?.dateDebut);
    const estCdi = !contrat?.dateFin && (contrat?.libelleTypeContrat?.includes('Indéterminé') || contrat?.libelleTypeContrat?.includes('CDI'));

    const fonction = getVal(
        affectation?.libelleFonction,
        affectation?.fonction,
        contrat?.libelleFonction,
        employe?.libelleFonction,
        employe?.poste
    ) || '—';

    const categoriePro = getVal(
        affectation?.libelleCategorieProfessionnelle,
        affectation?.libelleCategoriePro,
        contrat?.libelleCategorieProfessionnelle,
        employe?.libelleCategoriePro
    );

    const siteNom = getVal(
        affectation?.libelleSite,
        contrat?.libelleSite,
        employe?.libelleSite
    ) || 'Antananarivo';

    const departementNom = getVal(
        affectation?.libelleDepartement,
        contrat?.libelleDepartement,
        employe?.libelleDepartement
    );

    const serviceNom = getVal(
        affectation?.libelleService,
        contrat?.libelleService,
        employe?.libelleService
    );

    const managerNom = getVal(
        affectation?.nomPrenomResponsable,
        affectation?.nomResponsableHierarchique,
        contrat?.nomPrenomResponsable
    );

    const salaireBase = affectation?.salaireBase || contrat?.salaireBase;
    const modePaie = getVal(
        affectation?.libelleModeCalculPaie,
        affectation?.modeCalculPaie,
        contrat?.libelleModeCalculPaie
    );

    const aPeriodeEssai = contrat?.aPeriodeEssai || (contrat?.dureeEssaiMois && Number(contrat?.dureeEssaiMois) > 0);
    const dureeEssaiMois = contrat?.dureeEssaiMois;
    const dateFinEssai = contrat?.dateFinEssai ? fmtDateFr(contrat?.dateFinEssai) : null;

    const handlePrint = () => {
        window.print();
    };

    return createPortal(
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-4 bg-black/60 backdrop-blur-xs font-sans print:p-0 print:bg-white print:static print:inset-auto">
            <div
                className={`relative w-full max-w-4xl max-h-[92vh] flex flex-col bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xl transition-all duration-200 print:border-none print:shadow-none print:max-h-none print:w-full print:m-0 ${
                    mounted ? 'scale-100 opacity-100' : 'scale-95 opacity-0'
                }`}
            >
                {/* Barre de contrôle supérieure (masquée à l'impression) */}
                <div className="flex items-center justify-between px-5 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] print:hidden">
                    <div className="flex items-center gap-2.5">
                        <FileText className="w-5 h-5 text-[var(--color-brand-500)]" />
                        <div>
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                Document Officiel — Contrat de Travail
                            </h3>
                            <p className="text-[11px] text-[var(--color-fg-muted)] font-mono">
                                N° {numContrat} · {contrat?.libelleTypeContrat || 'Contrat'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={handlePrint}
                            iconLeft={<Printer size={14} />}
                            className="font-semibold shadow-xs"
                        >
                            Imprimer / PDF
                        </Button>
                        <button
                            type="button"
                            onClick={onClose}
                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface)] border border-transparent hover:border-[var(--color-border-subtle)] transition-colors cursor-pointer"
                            title="Fermer (Échap)"
                        >
                            <X size={18} />
                        </button>
                    </div>
                </div>

                {/* Corps du Document Contractuel scrollable */}
                <div className="flex-1 overflow-y-auto p-6 sm:p-10 bg-[var(--color-bg-subtle)]/50 print:bg-white print:p-0 print:overflow-visible">
                    {loadingAffectation && (
                        <div className="mb-4 text-center text-xs text-[var(--color-fg-muted)] print:hidden">
                            Chargement des données de poste et de rémunération...
                        </div>
                    )}

                    <div ref={printRef} className="w-full max-w-[720px] mx-auto print:max-w-none print:w-full">
                        <DocumentFrame className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-8 sm:p-12 shadow-sm text-[13px] leading-relaxed text-[var(--color-fg)] space-y-6 print:border-none print:p-6 print:bg-white print:text-black">
                            
                            {/* En-tête Institutionnel */}
                            <div className="text-center pb-6 border-b-2 border-[var(--color-fg)] print:border-black space-y-1">
                                <h1 className="text-2xl font-black uppercase tracking-wider font-serif print:text-xl">
                                    {filialeNom}
                                </h1>
                                <p className="text-xs uppercase tracking-widest text-[var(--color-fg-muted)] print:text-black">
                                    Direction des Ressources Humaines &amp; des Affaires Juridiques
                                </p>
                                {filialeAdresse && (
                                    <p className="text-[11px] text-[var(--color-fg-muted)] print:text-black">{filialeAdresse}</p>
                                )}
                                {(filialeNif || filialeStat) && (
                                    <p className="text-[10.5px] font-mono text-[var(--color-fg-muted)] print:text-black">
                                        {filialeNif && <>NIF : {filialeNif}</>}
                                        {filialeNif && filialeStat && <> · </>}
                                        {filialeStat && <>STAT : {filialeStat}</>}
                                    </p>
                                )}
                            </div>

                            {/* Titre du contrat */}
                            <div className="text-center py-4 space-y-1">
                                <h2 className="text-xl font-bold uppercase tracking-wide font-serif">
                                    {contrat?.libelleTypeContrat || 'Contrat de Travail'}
                                </h2>
                                <p className="text-xs font-mono text-[var(--color-fg-muted)] print:text-black">
                                    Référence : <strong>{numContrat}</strong>
                                </p>
                            </div>

                            {/* Préambule & Parties */}
                            <div className="space-y-3 text-justify text-xs sm:text-[13px]">
                                <p>
                                    <strong>ENTRE LES SOUSSIGNÉS :</strong>
                                </p>
                                <p className="pl-3 border-l-2 border-[var(--color-border-subtle)]">
                                    La société <strong>{filialeNom}</strong>, sise à {filialeAdresse || 'Antananarivo, Madagascar'}, représentée légalement par sa Direction des Ressources Humaines,<br />
                                    ci-après désignée <em>« l'Employeur »</em>, d'une part,
                                </p>
                                <p className="pl-3 border-l-2 border-[var(--color-border-subtle)]">
                                    Et <strong>Madame / Monsieur {nomComplet}</strong>
                                    {employe?.matricule && <>, matricule n° <strong>{employe.matricule}</strong></>}
                                    {employe?.cin && <>, titulaire de la CIN n° <strong>{employe.cin}</strong></>}
                                    {employe?.adresse && <>, domicilié(e) à {employe.adresse}</>},<br />
                                    ci-après désigné(e) <em>« le Salarié »</em>, d'autre part.
                                </p>
                                <p className="pt-2">
                                    Il a été convenu et arrêté ce qui suit en conformité avec la législation du travail en vigueur en République de Madagascar :
                                </p>
                            </div>

                            {/* Article 1 : Nature & Durée */}
                            <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] print:text-black flex items-center gap-1.5">
                                    <Calendar size={14} /> Article 1 — Engagement, Nature &amp; Durée
                                </h3>
                                <div className="space-y-1 pl-4 text-xs sm:text-[12.5px]">
                                    <p>
                                        Le présent contrat est conclu sous la forme d'un <strong>{contrat?.libelleTypeContrat || 'Contrat de Travail'}</strong>.
                                    </p>
                                    <p>
                                        • <strong>Date de prise d'effet :</strong> {dateDebut}
                                    </p>
                                    <p>
                                        • <strong>Terme du contrat :</strong> {estCdi ? <span className="font-semibold">Indéterminé (CDI)</span> : (dateFin || 'Selon terme fixé')}
                                    </p>
                                    <p>
                                        • <strong>Date de signature :</strong> {dateSignature}
                                    </p>
                                    {aPeriodeEssai && (
                                        <p className="bg-[var(--color-bg-subtle)] p-2 border border-[var(--color-border-subtle)] mt-1.5">
                                            <strong>Période d'essai :</strong> Une période d'essai de <strong>{dureeEssaiMois || '—'} mois</strong> est convenue, expirant le <strong>{dateFinEssai || '…'}</strong>, conformément aux dispositions conventionnelles.
                                        </p>
                                    )}
                                </div>
                            </div>

                            {/* Article 2 : Affectation & Fonctions */}
                            <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] print:text-black flex items-center gap-1.5">
                                    <Building size={14} /> Article 2 — Fonctions &amp; Lieu de Travail
                                </h3>
                                <div className="space-y-1 pl-4 text-xs sm:text-[12.5px]">
                                    <p>
                                        • <strong>Fonction occupée :</strong> <strong className="text-[var(--color-fg)] print:text-black">{fonction}</strong>
                                    </p>
                                    {categoriePro && (
                                        <p>
                                            • <strong>Catégorie professionnelle :</strong> {categoriePro}
                                        </p>
                                    )}
                                    <p>
                                        • <strong>Lieu de travail / Site :</strong> {siteNom}
                                        {departementNom && <> · Département : {departementNom}</>}
                                        {serviceNom && <> · Service : {serviceNom}</>}
                                    </p>
                                    {managerNom && (
                                        <p>
                                            • <strong>Supérieur hiérarchique direct (N+1) :</strong> {managerNom}
                                        </p>
                                    )}
                                </div>
                            </div>

                            {/* Article 3 : Rémunération */}
                            <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] print:text-black flex items-center gap-1.5">
                                    <DollarSign size={14} /> Article 3 — Rémunération
                                </h3>
                                <div className="space-y-1 pl-4 text-xs sm:text-[12.5px]">
                                    <p>
                                        En contrepartie de ses prestations, le Salarié percevra un salaire de base mensuel fixé à :
                                    </p>
                                    <p className="font-mono text-base font-black text-[var(--color-brand-500)] print:text-black py-1">
                                        <HasPermission authority="PAIE_READ" fallback={<span>[Confidentiel]</span>}>
                                            {salaireBase ? fmtMoney(salaireBase) : '— Ar'}
                                        </HasPermission>
                                    </p>
                                    {modePaie && (
                                        <p className="text-[11px] text-[var(--color-fg-muted)] print:text-black">
                                            Modalité de calcul : {modePaie}
                                        </p>
                                    )}
                                </div>
                            </div>

                            {/* Article 4 : Obligations & Confidentialité */}
                            <div className="space-y-1.5 pt-2 border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] print:text-black text-justify">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] print:text-black flex items-center gap-1.5 mb-1">
                                    <UserCheck size={14} /> Article 4 — Dispositions Générales
                                </h3>
                                <p>
                                    Le Salarié s'engage à consacrer l'exclusivité de son activité professionnelle au service de l'Employeur, à observer scrupuleusement les consignes de sécurité, le règlement intérieur et la confidentialité des données de l'entreprise.
                                </p>
                            </div>

                            {/* Signatures */}
                            <div className="pt-8 border-t-2 border-[var(--color-border-subtle)] flex justify-between items-start text-xs print:pt-4">
                                <div className="w-5/12 text-center space-y-12">
                                    <p className="font-bold">Pour l'Employeur</p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] print:text-black italic">Signature &amp; Cachet</p>
                                </div>
                                <div className="w-5/12 text-center space-y-12">
                                    <p className="font-bold">Le Salarié</p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] print:text-black italic">Précédé de la mention « Lu et approuvé »</p>
                                </div>
                            </div>

                        </DocumentFrame>
                    </div>
                </div>
            </div>
        </div>,
        document.body
    );
}
