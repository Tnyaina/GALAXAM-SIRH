import { DocumentFrame, DocumentHeader, Clause, ClauseDiff, ArticleBlock, DocumentFooter } from '@/features/employees/components/carriere/ContractDocument';
import HasPermission from '@/features/auth/components/HasPermission';
import { fmtDateFr } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';

export default function AvenantPreviewDocument({
    className = '',
    typeEvenementActuel,
    nomEmploye,
    formData,
    exigeNouveauContrat,
    avantTypeContrat,
    apresTypeContrat,
    avantSite,
    apresSite,
    avantDepartement,
    apresDepartement,
    avantService,
    apresService,
    avantFonction,
    apresFonction,
    avantCategorie,
    apresCategorie,
    avantResponsable,
    apresResponsable,
    avantModePaie,
    apresModePaie,
    avantSalaire,
}) {
    return (
        <DocumentFrame className={className} watermark="Avenant">
            <DocumentHeader
                eyebrow={typeEvenementActuel?.libelle || 'Mouvement RH'}
                title="Avenant au contrat de travail"
            />

            <div className="pt-4 text-[13px] leading-relaxed space-y-3 font-normal">
                <p>
                    Le présent avenant modifie les conditions applicables à <strong>{nomEmploye || 'Non renseigné'}</strong>, à effet du{' '}
                    <strong>{fmtDateFr(formData.dateEffet) || 'Non définie'}</strong>.
                </p>

                {exigeNouveauContrat && (
                    <ArticleBlock n="1" title="Nouveau contrat">
                        <ClauseDiff label="Type de contrat" before={avantTypeContrat} after={apresTypeContrat} />
                        <Clause label="Prise d'effet" value={fmtDateFr(formData.dateDebut || formData.dateEffet)} />
                        {formData.dateFin && <Clause label="Terme prévu" value={fmtDateFr(formData.dateFin)} />}
                    </ArticleBlock>
                )}

                <ArticleBlock n={exigeNouveauContrat ? "2" : "1"} title="Affectation et poste">
                    <ClauseDiff label="Site d'affectation" before={avantSite} after={apresSite} />
                    <ClauseDiff label="Département" before={avantDepartement} after={apresDepartement} />
                    <ClauseDiff label="Service" before={avantService} after={apresService} />
                    <ClauseDiff label="Fonction / Intitulé du poste" before={avantFonction} after={apresFonction} />
                    <ClauseDiff label="Catégorie professionnelle" before={avantCategorie} after={apresCategorie} />
                    <ClauseDiff label="Responsable hiérarchique" before={avantResponsable} after={apresResponsable} />
                </ArticleBlock>

                <ArticleBlock n={exigeNouveauContrat ? "3" : "2"} title="Rémunération et mode de calcul">
                    <ClauseDiff label="Mode de calcul de paie" before={avantModePaie} after={apresModePaie} />
                    <HasPermission authority="PAIE_READ" fallback={<ClauseDiff label="Salaire de base brut" before="[Confidentiel]" after="[Confidentiel]" />}>
                        <ClauseDiff
                            label="Salaire de base brut"
                            before={avantSalaire != null ? fmtMoney(avantSalaire) : null}
                            after={formData.salaireBase ? fmtMoney(formData.salaireBase) : null}
                        />
                    </HasPermission>
                </ArticleBlock>

                {formData.motif && (
                    <ArticleBlock n={exigeNouveauContrat ? "4" : "3"} title="Motif et observations">
                        <p className="text-[var(--color-fg-muted)]">{formData.motif}</p>
                    </ArticleBlock>
                )}
            </div>

            <DocumentFooter
                lieu={apresSite || avantSite || ''}
                dateSignature={fmtDateFr(formData.dateSignature || formData.dateEffet)}
            />
        </DocumentFrame>
    );
}
