import { useState, useEffect, useRef } from 'react';
import { Printer, FileText } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import stcService from '@/features/payroll/api/stcService';
import { getErrorMessage } from '@/api/apiClient';

/**
 * Modale Quittance Libératoire Financière Officielle du STC (Article 53 Code du Travail - Loi 2003-044).
 */
export default function StcRecuModal({ isOpen, onClose, idStc, initialRecu = null }) {
    const [recu, setRecu] = useState(initialRecu);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const printAreaRef = useRef(null);

    useEffect(() => {
        if (!isOpen) {
            setRecu(null);
            setError(null);
            return;
        }

        if (initialRecu) {
            setRecu(initialRecu);
            return;
        }

        if (idStc) {
            const charger = async () => {
                setLoading(true);
                setError(null);
                try {
                    const data = await stcService.obtenirRecuStc(idStc);
                    setRecu(data);
                } catch (err) {
                    setError(getErrorMessage(err, 'Impossible de charger le reçu officiel de STC.'));
                } finally {
                    setLoading(false);
                }
            };
            charger();
        }
    }, [isOpen, idStc, initialRecu]);

    const handlePrint = () => {
        window.print();
    };

    if (!isOpen) return null;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={recu?.numeroRecu ? `Quittance Libératoire — ${recu.numeroRecu}` : 'Reçu de Solde de Tout Compte'}
            icon={<FileText size={18} className="text-[var(--color-brand-600)]" />}
            size="3xl"
            footer={
                <div className="flex justify-between items-center w-full">
                    <span className="text-[11px] text-[var(--color-fg-subtle)]">
                        Document officiel conforme à l'Art. 53 du Code du Travail Malgache
                    </span>
                    <div className="flex gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={onClose}>
                            Fermer
                        </Button>
                        {recu && (
                            <Button
                                type="button"
                                variant="primary"
                                size="sm"
                                iconLeft={<Printer size={15} />}
                                onClick={handlePrint}
                            >
                                Imprimer la Quittance
                            </Button>
                        )}
                    </div>
                </div>
            }
        >
            {loading && (
                <div className="py-12 text-center text-xs text-[var(--color-fg-muted)]">
                    Chargement de la quittance libératoire...
                </div>
            )}

            {error && (
                <div className="p-4 bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] border border-[var(--color-danger-500)]/20 text-xs">
                    {error}
                </div>
            )}

            {recu && (
                <div ref={printAreaRef} className="space-y-4 text-xs p-1">
                    {/* Entête Document */}
                    <div className="border-b border-[var(--color-border)] pb-4 flex justify-between items-start">
                        <div>
                            <h2 className="text-sm font-bold text-[var(--color-fg)] uppercase tracking-wider">
                                {recu.entrepriseNom || 'GALAXAM LOGISTICS'}
                            </h2>
                            <p className="text-[11px] text-[var(--color-fg-muted)]">{recu.entrepriseAdresse}</p>
                            <p className="text-[10px] text-[var(--color-fg-subtle)] font-mono mt-0.5">
                                NIF : {recu.entrepriseNif} | STAT : {recu.entrepriseStat} | CNaPS : {recu.entrepriseCnaps}
                            </p>
                        </div>
                        <div className="text-right">
                            <Badge variant="success" size="sm" className="font-bold font-mono">
                                {recu.numeroRecu || 'STC-OFFICIEL'}
                            </Badge>
                            <p className="text-[10px] text-[var(--color-fg-muted)] mt-1">
                                Émis le : <span className="font-semibold">{fmtDate(recu.dateEmission)}</span>
                            </p>
                            <p className="text-[10px] text-[var(--color-fg-subtle)] uppercase">
                                Statut : {recu.statut || 'VALIDÉ'}
                            </p>
                        </div>
                    </div>

                    {/* Bloc Identité Salarié */}
                    <div className="bg-[var(--color-bg-subtle)]/40 p-3 border border-[var(--color-border-subtle)] grid grid-cols-2 gap-2 text-[11px]">
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Salarié :</span>{' '}
                            <strong className="text-[var(--color-fg)]">{recu.nomComplet}</strong>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Matricule :</span>{' '}
                            <span className="font-mono font-bold text-[var(--color-fg)]">{recu.matricule}</span>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Fonction :</span>{' '}
                            <span className="text-[var(--color-fg)]">{recu.intitulePoste || '—'}</span>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Département / Site :</span>{' '}
                            <span className="text-[var(--color-fg)]">{recu.departement || '—'} / {recu.site || '—'}</span>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">CIN :</span>{' '}
                            <span className="font-mono text-[var(--color-fg)]">{recu.cinNumero || '—'}</span>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Ancienneté :</span>{' '}
                            <span className="font-medium text-[var(--color-fg)]">{recu.ancienneteTexte || '—'}</span>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Date d'embauche :</span>{' '}
                            <span className="text-[var(--color-fg)]">{fmtDate(recu.dateEmbauche)}</span>
                        </div>
                        <div>
                            <span className="text-[var(--color-fg-muted)]">Date de cessation :</span>{' '}
                            <span className="font-bold text-[var(--color-fg)]">{fmtDate(recu.dateDepart)}</span> ({recu.motifRupture || 'DEMISSION'})
                        </div>
                    </div>

                    {/* Tableau Récapitulatif Financier */}
                    <div className="border border-[var(--color-border-subtle)] overflow-hidden">
                        <table className="w-full text-left text-xs border-collapse">
                            <thead>
                                <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[10px] text-[var(--color-fg-muted)] uppercase">
                                    <th className="p-2">Éléments du Décompte Financier</th>
                                    <th className="p-2 text-right">Montant (Ariary)</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-[var(--color-border-subtle)] font-mono text-[11px]">
                                {Number(recu.salaireProrata) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans">Salaire proratisé du dernier mois</td>
                                        <td className="p-2 text-right">{formatMoneyAr(recu.salaireProrata)}</td>
                                    </tr>
                                )}
                                <tr>
                                    <td className="p-2 font-sans">
                                        Indemnité Compensatrice de Congés Payés (ICCP)
                                        <span className="text-[10px] text-[var(--color-fg-muted)] block font-normal">
                                            Solde : {recu.soldeCongesRestant} j × {formatMoneyAr(recu.tauxJournalierConge)} / j
                                        </span>
                                    </td>
                                    <td className="p-2 text-right">{formatMoneyAr(recu.indemniteCongesPayes)}</td>
                                </tr>
                                {Number(recu.indemnitePreavis) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans">Indemnité compensatrice de préavis ({recu.moisPreavis} mois)</td>
                                        <td className="p-2 text-right">{formatMoneyAr(recu.indemnitePreavis)}</td>
                                    </tr>
                                )}
                                {Number(recu.indemniteLicenciement) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans">Indemnité de licenciement légale / conventionnelle</td>
                                        <td className="p-2 text-right">{formatMoneyAr(recu.indemniteLicenciement)}</td>
                                    </tr>
                                )}
                                {Number(recu.autresIndemnites) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans">Autres indemnités et gratifications</td>
                                        <td className="p-2 text-right">{formatMoneyAr(recu.autresIndemnites)}</td>
                                    </tr>
                                )}
                                <tr className="bg-[var(--color-bg-subtle)]/60 font-bold">
                                    <td className="p-2 font-sans uppercase">TOTAL BRUT DU SOLDE DE TOUT COMPTE</td>
                                    <td className="p-2 text-right">{formatMoneyAr(recu.totalBrut ?? recu.totalBrutStc)}</td>
                                </tr>
                                <tr>
                                    <td className="p-2 font-sans text-[var(--color-fg-muted)]">- Retenues sociales salariales (CNaPS & Santé)</td>
                                    <td className="p-2 text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.cotisationsSociales)}</td>
                                </tr>
                                <tr>
                                    <td className="p-2 font-sans text-[var(--color-fg-muted)]">- Retenue fiscale IRSA</td>
                                    <td className="p-2 text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.retenueIrsa)}</td>
                                </tr>
                                {Number(recu.soldePretsApure) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans text-[var(--color-fg-muted)]">- Remboursement et apurement intégral des prêts d'entreprise</td>
                                        <td className="p-2 text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.soldePretsApure)}</td>
                                    </tr>
                                )}
                                {Number(recu.soldeAvancesApure) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans text-[var(--color-fg-muted)]">- Déduction des avances et acomptes en cours</td>
                                        <td className="p-2 text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.soldeAvancesApure)}</td>
                                    </tr>
                                )}
                                {Number(recu.autresDeductions) > 0 && (
                                    <tr>
                                        <td className="p-2 font-sans text-[var(--color-fg-muted)]">- Autres déductions conventionnelles</td>
                                        <td className="p-2 text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.autresDeductions)}</td>
                                    </tr>
                                )}
                            </tbody>
                            <tfoot>
                                <tr className="bg-[var(--color-ink-950)] text-white font-bold">
                                    <td className="p-3 font-sans uppercase tracking-wider text-xs">
                                        NET À PAYER DÉFINITIF
                                        <span className="text-[10px] text-[var(--color-ink-200)] block font-normal capitalize">
                                            Règlement par {recu.modeReglement || 'VIREMENT'}
                                        </span>
                                    </td>
                                    <td className="p-3 text-right text-base font-mono font-black">
                                        {formatMoneyAr(recu.netAPayer ?? recu.netAPayerStc)}
                                    </td>
                                </tr>
                            </tfoot>
                        </table>
                    </div>

                    {/* Somme en toutes lettres */}
                    {recu.netEnLettres && (
                        <p className="text-[11px] text-[var(--color-fg-muted)] ">
                            Arrêté le présent solde de tout compte à la somme nette de : <strong className="text-[var(--color-fg)] not-">{recu.netEnLettres} Ariary</strong>.
                        </p>
                    )}

                    {/* Mention légale libératoire */}
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[11px] leading-relaxed text-[var(--color-fg-muted)]">
                        <p className="font-semibold text-[var(--color-fg)] mb-1">Mention légale libératoire (Art. 53 Loi n° 2003-044) :</p>
                        <p>{recu.mentionLiberatoire}</p>
                        <p className="mt-2 text-[10px] text-[var(--color-fg-subtle)]">
                            Mention manuscrite obligatoire du salarié : <span className="font-semibold text-[var(--color-fg)]">"{recu.formuleManuscriteObligatoire}"</span>
                        </p>
                    </div>

                    {/* Blocs Signatures */}
                    <div className="grid grid-cols-2 gap-6 pt-4 text-center text-xs">
                        <div className="border-t border-dashed border-[var(--color-border)] pt-2">
                            <p className="font-semibold text-[var(--color-fg)]">Pour l'Employeur / Direction Générale</p>
                            <p className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">Signature et cachet officiel</p>
                            <div className="h-14"></div>
                        </div>
                        <div className="border-t border-dashed border-[var(--color-border)] pt-2">
                            <p className="font-semibold text-[var(--color-fg)]">Le Salarié</p>
                            <p className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                Précédé de la mention manuscrite obligatoire
                            </p>
                            <div className="h-14"></div>
                        </div>
                    </div>
                </div>
            )}
        </Modal>
    );
}
