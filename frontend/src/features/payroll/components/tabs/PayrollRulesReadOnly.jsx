import { useEffect, useMemo, useState } from 'react';
import { Search, ShieldAlert } from 'lucide-react';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

function traitement(type, rubrique) {
    if (!rubrique) return 'Rubrique absente : traitement à vérifier';
    if (type.nature !== 'VARIABLE_DAF') return 'Circuit métier dédié dans le moteur';
    if (type.sens === 'RETENUE') return 'Après impôt et cotisations : retenue sur le net disponible';
    return rubrique.etapeCalcul === 'BRUT'
        ? 'Avant cotisations et impôt : gain dans le brut'
        : 'Après impôt : gain ajouté au net avant retenues';
}

export default function PayrollRulesReadOnly() {
    const [catalogue, setCatalogue] = useState({ types: [], rubriques: [] });
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [query, setQuery] = useState('');

    useEffect(() => {
        let active = true;
        paieService.getCatalogueVariables()
            .then(data => { if (active) setCatalogue({
                types: Array.isArray(data?.types) ? data.types : [],
                rubriques: Array.isArray(data?.rubriques) ? data.rubriques : [],
            }); })
            .catch(cause => { if (active) setError(getErrorMessage(cause, 'Catalogue des rubriques indisponible.')); })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const rows = useMemo(() => {
        const rubriques = new Map(catalogue.rubriques.map(rubrique => [Number(rubrique.id), rubrique]));
        const search = query.trim().toLowerCase();
        return catalogue.types
            .map(type => ({ type, rubrique: rubriques.get(Number(type.idRubrique)) }))
            .filter(({ type, rubrique }) => !search || `${type.code} ${type.libelle} ${rubrique?.code || ''} ${rubrique?.libelle || ''}`.toLowerCase().includes(search));
    }, [catalogue, query]);

    return (
        <div className="space-y-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5">
            <div>
                <h3 className="font-bold text-base text-[var(--color-fg)]">Lecture des règles de paie actuelles</h3>
                <p className="text-sm text-[var(--color-fg-muted)] mt-1">
                    Correspondance entre les types de variables, les rubriques et les étapes du moteur. Cette vue ne modifie aucun calcul.
                </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-2 text-xs">
                {['1 · Brut et gains', '2 · Cotisations', '3 · IRSA ou ISI', '4 · Gains nets, retenues et arrondi'].map(stage => (
                    <div key={stage} className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 font-semibold">{stage}</div>
                ))}
            </div>

            <Alert type="info" message="Pour les variables manuelles, l’étape d’intervention est indépendante des assiettes. IRSA décide de l’inclusion fiscale, CNaPS et santé ont des assiettes distinctes. Les réglages affichés sont ceux de la rubrique liée ; pour une famille versionnée, le calcul sélectionne la version couvrant la période. Les circuits métier dédiés suivent leur propre calcul." />
            <Alert type="warning" message="Les anciennes rubriques peuvent ne pas porter de référence ou de date d’effet. Une exonération doit être vérifiée dans son contexte avant de créer une nouvelle rubrique. Les circuits métier dédiés conservent leur calcul propre." />

            {error && <Alert type="error" message={error} />}
            {loading ? <SkeletonTable rows={6} columns={6} /> : (
                <>
                    <div className="max-w-sm">
                        <Input type="search" value={query} onChange={event => setQuery(event.target.value)} iconLeft={<Search size={15} />} placeholder="Rechercher un type ou une rubrique" aria-label="Rechercher une règle de paie" />
                    </div>
                    <div className="overflow-x-auto">
                        <Table aria-label="Règles de calcul actuellement appliquées">
                            <Thead><Tr>
                                <Th>Type de variable</Th><Th>Rubrique liée</Th><Th>Étape effective</Th>
                                <Th>IRSA</Th><Th>CNaPS</Th><Th>Santé</Th><Th>Référence / effet</Th><Th>Base congés</Th>
                            </Tr></Thead>
                            <Tbody>
                                {rows.map(({ type, rubrique }) => (
                                    <Tr key={type.id ?? type.code}>
                                        <Td><span className="font-semibold">{type.libelle}</span><span className="block text-[11px] font-mono text-[var(--color-fg-muted)]">{type.code}</span></Td>
                                        <Td>{rubrique ? <><span>{rubrique.libelle}</span><span className="block text-[11px] font-mono text-[var(--color-fg-muted)]">{rubrique.code}</span></> : 'Non liée'}</Td>
                                        <Td>{traitement(type, rubrique)}</Td>
                                        <Td>{type.sens === 'RETENUE' ? 'Sans effet' : type.nature !== 'VARIABLE_DAF' ? 'Circuit dédié' : !rubrique ? 'À vérifier' : (rubrique.soumisIrsa ? 'Dans l’assiette' : 'Hors assiette')}</Td>
                                        <Td>{type.sens === 'RETENUE' ? 'Sans effet' : type.nature !== 'VARIABLE_DAF' ? 'Circuit dédié' : !rubrique ? 'À vérifier' : (rubrique.soumisCnaps ? 'Dans l’assiette' : 'Hors assiette')}</Td>
                                        <Td>{type.sens === 'RETENUE' ? 'Sans effet' : type.nature !== 'VARIABLE_DAF' ? 'Circuit dédié' : !rubrique ? 'À vérifier' : (rubrique.soumisSante ? 'Dans l’assiette' : 'Hors assiette')}</Td>
                                        <Td>{rubrique ? <><span>{rubrique.referenceReglementaire || 'Historique non renseigné'}</span><span className="block text-[11px]">{rubrique.dateDebutEffet || 'Date non renseignée'}</span></> : '—'}</Td>
                                        <Td>Non déterminée par ce type</Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)] flex items-start gap-2"><ShieldAlert size={15} className="shrink-0" /> Les montants et assiettes exacts restent expliqués sur chaque bulletin calculé ou simulé. L'ordre d'affichage du référentiel ne change pas l'ordre de calcul.</p>
                </>
            )}
        </div>
    );
}
