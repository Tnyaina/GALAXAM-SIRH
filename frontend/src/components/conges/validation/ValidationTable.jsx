import { Check, X, Eye, Clock, CheckCircle, XCircle, Ban } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';

const formatDate = (ts) => {
  if (!ts) return '-';
  return new Date(ts).toLocaleDateString('fr-FR');
};

const BADGE_VARIANT = {
  VALIDE:     'success',
  REFUSE:     'danger',
  EN_ATTENTE: 'warning',
  ANNULE:     'neutral',
  BROUILLON:  'default',
};

const STATUT_LABEL = {
  VALIDE:     'Validé',
  REFUSE:     'Refusé',
  EN_ATTENTE: 'En attente',
  ANNULE:     'Annulé',
  BROUILLON:  'Brouillon',
};

const STATUT_ICON = {
  EN_ATTENTE: Clock,
  VALIDE:     CheckCircle,
  REFUSE:     XCircle,
  ANNULE:     Ban,
};

export default function ValidationTable({
  demandes,
  activeStatut,
  displayTotal,
  searchTerm,
  currentPage,
  pageSize,
  setCurrentPage,
  setPageSize,
  onOpenDetail,
  onOpenRefus,
  onOpenValider
}) {
  return (
    <Card>
      <CardBody className="p-0">
        {/* En-tête contextuel */}
        {activeStatut === 'EN_ATTENTE' && displayTotal > 0 && (
          <div className="px-4 py-2.5 border-b border-[var(--color-border-subtle)] bg-warning-500/5 flex items-center gap-2">
            <Clock size={13} className="text-warning-500 shrink-0" />
            <span className="text-xs font-semibold text-warning-500">
              {displayTotal} demande{displayTotal > 1 ? 's' : ''} en attente de traitement
            </span>
          </div>
        )}

        {demandes.length === 0 ? (
          <EmptyState
            title={searchTerm
              ? `Aucun résultat pour "${searchTerm}"`
              : activeStatut === 'EN_ATTENTE'
                ? 'Aucune demande en attente'
                : 'Aucune demande trouvée'}
            description={
              searchTerm
                ? 'Essayez un autre terme de recherche.'
                : activeStatut === 'EN_ATTENTE'
                  ? 'Toutes les demandes ont été traitées.'
                  : 'Aucune demande ne correspond à cette sélection.'
            }
          />
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <Thead>
                <Tr>
                  <Th>Réf.</Th>
                  <Th>Employé</Th>
                  <Th>Type</Th>
                  <Th>Période</Th>
                  <Th className="text-center">Durée</Th>
                  <Th>Statut</Th>
                  <Th className="text-right">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {demandes.map((d) => {
                  const employeName = `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employé ${d.idEmploye}`;
                  const isEnAttente = d.codeStatutDemande === 'EN_ATTENTE';
                  const StatutIcon  = STATUT_ICON[d.codeStatutDemande];
                  return (
                    <Tr
                      key={d.idDemandeConge}
                      className={`hover:bg-[var(--color-surface-hover)] group ${
                        isEnAttente ? 'border-l-2 border-l-warning-500' : 'border-l-2 border-l-transparent'
                      }`}
                    >
                      <Td className="font-data text-xs text-[var(--color-fg-subtle)] whitespace-nowrap">
                        #{d.idDemandeConge}
                      </Td>
                      <Td>
                        <div className="font-semibold text-[var(--color-fg)] text-sm leading-tight">{employeName}</div>
                        {d.matricule && (
                          <div className="text-[11px] text-[var(--color-fg-subtle)] font-data">{d.matricule}</div>
                        )}
                      </Td>
                      <Td>
                        <Badge variant="brand" size="sm">{d.codeTypeConge || 'ANNUEL'}</Badge>
                        {d.libelleMotifEvenement && (
                          <div className="text-[11px] text-warning-500 mt-0.5 font-medium">{d.libelleMotifEvenement}</div>
                        )}
                      </Td>
                      <Td className="font-data text-xs text-[var(--color-fg-muted)] whitespace-nowrap">
                        <div className="leading-relaxed">
                          <span className="text-[var(--color-fg-subtle)]">Du</span> {formatDate(d.dateDebut)}
                        </div>
                        <div>
                          <span className="text-[var(--color-fg-subtle)]">Au</span> {formatDate(d.dateFin)}
                        </div>
                      </Td>
                      <Td className="text-center font-data whitespace-nowrap">
                        <span className="font-bold text-[var(--color-fg)] text-sm">{d.nbJoursDecomptes}</span>
                        <span className="text-xs text-[var(--color-fg-muted)]"> j</span>
                      </Td>
                      <Td>
                        <div className="inline-flex items-center gap-1.5">
                          {StatutIcon && <StatutIcon size={12} className={`shrink-0 ${
                            d.codeStatutDemande === 'VALIDE'     ? 'text-success-500' :
                            d.codeStatutDemande === 'REFUSE'     ? 'text-danger-500'  :
                            d.codeStatutDemande === 'EN_ATTENTE' ? 'text-warning-500' :
                            'text-[var(--color-fg-subtle)]'
                          }`} />}
                          <Badge variant={BADGE_VARIANT[d.codeStatutDemande] || 'default'} size="sm">
                            {STATUT_LABEL[d.codeStatutDemande] || d.codeStatutDemande}
                          </Badge>
                        </div>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {d.codeStatutDemande === 'REFUSE' && d.motifRefus && (
                            <div className="text-[11px] text-danger-500 font-medium leading-tight max-w-xs" title={d.motifRefus}>
                              Motif : {d.motifRefus}
                            </div>
                          )}
                          {d.derogationSoldeNegatif   && <Badge variant="danger"  size="sm">Solde négatif</Badge>}
                          {!d.justificatifFourni && d.codeTypeConge === 'MALADIE' && <Badge variant="warning" size="sm">Justificatif manquant</Badge>}
                        </div>
                      </Td>
                      <Td>
                        <div className="flex items-center justify-end gap-1">
                          {isEnAttente && (
                            <>
                              <Button variant="danger"  size="xs" iconLeft={<X size={12}/>}     onClick={() => onOpenRefus(d)}>Refuser</Button>
                              <Button variant="success" size="xs" iconLeft={<Check size={12}/>} onClick={() => onOpenValider(d)}>Valider</Button>
                            </>
                          )}
                          <Button
                            variant="ghost"
                            size="xs"
                            iconLeft={<Eye size={13}/>}
                            onClick={() => onOpenDetail(d)}
                            title="Voir le détail"
                            className="opacity-0 group-hover:opacity-100"
                          />
                        </div>
                      </Td>
                    </Tr>
                  );
                })}
              </Tbody>
            </Table>

            {/* Pagination et compteurs de résultats */}
            {displayTotal > 0 && (
              <Pagination
                currentPage={currentPage}
                totalItems={displayTotal}
                pageSize={pageSize}
                onPageChange={setCurrentPage}
                onPageSizeChange={setPageSize}
                className="bg-[var(--color-bg-subtle)]"
              />
            )}
          </div>
        )}
      </CardBody>
    </Card>
  );
}
