import { useState, useEffect, useRef } from 'react';
import { Printer, FileText } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td, Tfoot } from '@/components/ui/data-display/Table';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import stcService from '@/features/payroll/api/stcService';
import { getErrorMessage } from '@/api/apiClient';

/**
 * Reçu détaillé du calcul de fin de contrat.
 */
export default function StcRecuModal({ isOpen, onClose, idStc, initialRecu = null }) {
    // État local
    const [recu, setRecu] = useState(initialRecu);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const printAreaRef = useRef(null);

    // Effet : charge le reçu seulement à l'ouverture, sauf s'il est déjà fourni.
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
                    setError(getErrorMessage(err, 'Impossible de charger le décompte de STC.'));
                } finally {
                    setLoading(false);
                }
            };
            charger();
        }
    }, [isOpen, idStc, initialRecu]);

    // Action utilisateur
    const handlePrint = () => {
        window.print();
    };

    // Rendu : garde de fermeture.
    if (!isOpen) return null;

    // Rendu : document officiel et actions de la modale.
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={recu?.numeroRecu ? `Décompte de fin de contrat — ${recu.numeroRecu}` : 'Décompte de fin de contrat'}
            icon={<FileText size={18} className="text-[var(--color-brand-600)]" />}
            size="3xl"
            footer={
                <div className="flex justify-between items-center w-full">
                    <span className="text-[11px] text-[var(--color-fg-subtle)]">
                        Calcul validé ; paiement à attester séparément
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
                                Imprimer le décompte
                            </Button>
                        )}
                    </div>
                </div>
            }
        >
            {loading && (
                <div className="py-12 text-center text-xs text-[var(--color-fg-muted)]">
                    Chargement du décompte...
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
                                {recu.numeroRecu || 'STC'}
                            </Badge>
                            <p className="text-[10px] text-[var(--color-fg-muted)] mt-1">
                                Émis le : <span className="font-semibold">{fmtDate(recu.dateEmission)}</span>
                            </p>
                            <p className="text-[10px] text-[var(--color-fg-subtle)] uppercase">
                                Statut : {recu.statut || 'VALIDÉ'}
                            </p>
                            {recu.dateReglement && (
                                <p className="text-[10px] text-[var(--color-fg-subtle)]">
                                    Payé le {fmtDate(recu.dateReglement)} · Réf. {recu.referenceReglement}
                                </p>
                            )}
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
                    <Table density="compact" bordered className="overflow-hidden">
                        <Thead>
                            <Tr>
                                <Th>Éléments du Décompte Financier</Th>
                                <Th className="text-right">Montant (Ariary)</Th>
                            </Tr>
                        </Thead>
                        <Tbody className="font-mono text-[11px]">
                            {Number(recu.salaireProrata) > 0 && (
                                <Tr>
                                    <Td className="font-sans">Salaire proratisé du dernier mois</Td>
                                    <Td className="text-right">{formatMoneyAr(recu.salaireProrata)}</Td>
                                </Tr>
                            )}
                            <Tr>
                                <Td className="font-sans">
                                    Indemnité Compensatrice de Congés Payés (ICCP)
                                    <span className="text-[10px] text-[var(--color-fg-muted)] block font-normal">
                                        {Number(recu.soldeCongesRestant) < 0
                                            ? `Solde négatif de ${Math.abs(Number(recu.soldeCongesRestant))} jour(s) à examiner séparément`
                                            : `Solde : ${recu.soldeCongesRestant} j, valorisation des droits acquis`}
                                    </span>
                                </Td>
                                <Td className="text-right">{formatMoneyAr(recu.indemniteCongesPayes)}</Td>
                            </Tr>
                            {Number(recu.indemnitePreavis) > 0 && (
                                <Tr>
                                    <Td className="font-sans">Indemnité compensatrice de préavis ({recu.moisPreavis} mois)</Td>
                                    <Td className="text-right">{formatMoneyAr(recu.indemnitePreavis)}</Td>
                                </Tr>
                            )}
                            {Number(recu.indemniteLicenciement) > 0 && (
                                <Tr>
                                    <Td className="font-sans">Indemnité de licenciement légale / conventionnelle</Td>
                                    <Td className="text-right">{formatMoneyAr(recu.indemniteLicenciement)}</Td>
                                </Tr>
                            )}
                            {Number(recu.autresIndemnites) > 0 && (
                                <Tr>
                                    <Td className="font-sans">Autres indemnités et gratifications</Td>
                                    <Td className="text-right">{formatMoneyAr(recu.autresIndemnites)}</Td>
                                </Tr>
                            )}
                            <Tr className="bg-[var(--color-bg-subtle)]/60 font-bold">
                                <Td className="font-sans uppercase">TOTAL BRUT DU SOLDE DE TOUT COMPTE</Td>
                                <Td className="text-right">{formatMoneyAr(recu.totalBrut ?? recu.totalBrutStc)}</Td>
                            </Tr>
                            <Tr>
                                <Td className="font-sans text-[var(--color-fg-muted)]">- Retenues sociales salariales (CNaPS & Santé)</Td>
                                <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.cotisationsSociales)}</Td>
                            </Tr>
                            <Tr>
                                <Td className="font-sans text-[var(--color-fg-muted)]">- Retenue fiscale IRSA</Td>
                                <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.retenueIrsa)}</Td>
                            </Tr>
                            {Number(recu.soldePretsApure) > 0 && (
                                <Tr>
                                    <Td className="font-sans text-[var(--color-fg-muted)]">- Remboursement et apurement intégral des prêts d'entreprise</Td>
                                    <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.soldePretsApure)}</Td>
                                </Tr>
                            )}
                            {Number(recu.soldeAvancesApure) > 0 && (
                                <Tr>
                                    <Td className="font-sans text-[var(--color-fg-muted)]">- Déduction des avances et acomptes en cours</Td>
                                    <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.soldeAvancesApure)}</Td>
                                </Tr>
                            )}
                            {Number(recu.autresDeductions) > 0 && (
                                <Tr>
                                    <Td className="font-sans text-[var(--color-fg-muted)]">- Autres déductions conventionnelles</Td>
                                    <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(recu.autresDeductions)}</Td>
                                </Tr>
                            )}
                        </Tbody>
                        <Tfoot>
                            <Tr className="bg-[var(--color-ink-950)] text-[var(--color-on-color)] font-bold">
                                <Td className="p-3 font-sans uppercase tracking-wider text-xs text-inherit">
                                    NET CALCULÉ À PAYER
                                    <span className="text-[10px] text-[var(--color-ink-200)] block font-normal capitalize">
                                        Règlement par {recu.modeReglement || 'VIREMENT'}
                                    </span>
                                </Td>
                                <Td className="p-3 text-right text-base font-mono font-black text-inherit">
                                    {formatMoneyAr(recu.netAPayer ?? recu.netAPayerStc)}
                                </Td>
                            </Tr>
                        </Tfoot>
                    </Table>

                    {/* Somme en toutes lettres */}
                    {recu.netEnLettres && (
                        <p className="text-[11px] text-[var(--color-fg-muted)] ">
                            Arrêté le présent solde de tout compte à la somme nette de : <strong className="text-[var(--color-fg)] not-">{recu.netEnLettres} Ariary</strong>.
                        </p>
                    )}

                    {/* Portée du reçu */}
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[11px] leading-relaxed text-[var(--color-fg-muted)]">
                        <p className="font-semibold text-[var(--color-fg)] mb-1">Portée du décompte (Code du travail 2024, art. 95 et 96) :</p>
                        <p>{recu.mentionLiberatoire}</p>
                        <p className="mt-2 text-[10px] text-[var(--color-fg-subtle)]">
                            Si une signature est recueillie, elle atteste seulement la remise du document. Aucune formule manuscrite obligatoire n’est imposée ici.
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
                                Accusé de remise du document
                            </p>
                            <div className="h-14"></div>
                        </div>
                    </div>
                </div>
            )}
        </Modal>
    );
}
