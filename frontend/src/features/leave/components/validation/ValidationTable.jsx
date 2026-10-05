import { Eye, Calendar } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { fmtDate } from '@/utils/dateUtils';

const formatDate = (ts) => fmtDate(ts);

export default function ValidationTable({
  demandes,
  activeStatut,
  displayTotal,
  searchTerm,
  currentPage,
  pageSize,
  setCurrentPage,
  setPageSize,
  canValidate = true,
  onOpenDetail
}) {
  return (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xs p-5 sm:p-6 space-y-4">
      {demandes.length === 0 ? (
        <div className="p-8">
          <EmptyState
            icon={Calendar}
            title={searchTerm
              ? `Aucun résultat pour "${searchTerm}"`
              : activeStatut === 'EN_ATTENTE'
                ? 'Aucune demande en attente'
                : 'Aucune demande trouvée'}
            description={
              searchTerm
                ? 'Essayez un autre terme de recherche ou réinitialisez les filtres.'
                : activeStatut === 'EN_ATTENTE'
                  ? 'Toutes les demandes de congés ont été traitées.'
                  : 'Aucune demande ne correspond à cette sélection.'
            }
          />
        </div>
      ) : (
        <>
        <div className="md:hidden divide-y divide-[var(--color-border-subtle)]">
          {demandes.map((d) => {
            const employeName = `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employé ${d.idEmploye}`;
            const aDecider = canValidate && d.availableActions?.APPROVE?.allowed === true;
            return <article key={d.idDemandeConge} className="py-3 space-y-2.5">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <p className="text-sm font-bold text-[var(--color-fg)] truncate">{employeName}</p>
                  <p className="text-[11px] text-[var(--color-fg-muted)]">{d.matricule && `${d.matricule} · `}Demande #{d.idDemandeConge}</p>
                </div>
                <StatusBadge status={d.codeStatutDemande || d.statutCode} size="xs" />
              </div>
              <p className="text-xs text-[var(--color-fg)]">{d.libelleTypeConge || d.codeTypeConge} · {formatDate(d.dateDebut)} → {formatDate(d.dateFin)}</p>
              <div className="flex items-center justify-between gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className={`min-h-[44px] inline-flex items-center justify-center font-medium text-xs px-3.5 rounded-lg transition-colors ${
                    aDecider
                      ? 'border-[var(--color-brand-300)] text-[var(--color-brand-700)] bg-[var(--color-brand-50)]/70 hover:bg-[var(--color-brand-100)] dark:border-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] dark:bg-[var(--color-brand-950)]/40'
                      : 'border-[var(--color-border)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                  }`}
                  iconLeft={<Eye size={14} className={aDecider ? "text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]" : "text-[var(--color-fg-subtle)]"} />}
                  onClick={() => onOpenDetail(d)}
                  aria-label={`${aDecider ? 'Examiner' : 'Voir les détails de'} la demande #${d.idDemandeConge} de ${employeName}`}
                >
                  {aDecider ? 'Examiner' : 'Détails'}
                </Button>
              </div>
            </article>;
          })}
        </div>
        <div className="hidden md:block overflow-x-auto">
          <Table bordered={false}>
            <Thead>
              <Tr>
                <Th>Collaborateur</Th>
                <Th>Type d'absence</Th>
                <Th>Période et durée</Th>
                <Th>Statut</Th>
                <Th className="text-right">Action</Th>
              </Tr>
            </Thead>
            <Tbody>
              {demandes.map((d) => {
                const employeName = `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employé ${d.idEmploye}`;
                const isRefuse = Boolean(d.isRefuse ?? (d.codeStatutDemande === 'REFUSE'));

                return (
                  <Tr
                    key={d.idDemandeConge}
                    className="hover:bg-[var(--color-surface-hover)] transition-colors group"
                  >
                    {/* Collaborateur */}
                    <Td>
                      <div className="font-bold text-[var(--color-fg)] text-xs leading-tight">
                        {employeName}
                      </div>
                      {d.matricule && (
                        <div className="text-[11px] text-[var(--color-fg-subtle)] font-mono mt-0.5">
                          {d.matricule} · Demande #{d.idDemandeConge}
                        </div>
                      )}
                      {!d.matricule && <div className="text-[11px] text-[var(--color-fg-subtle)] mt-0.5">Demande #{d.idDemandeConge}</div>}
                    </Td>

                    {/* Type de congé (Badge neutre uniforme, pas d'arc-en-ciel) */}
                    <Td>
                      <div className="flex items-center gap-1.5 flex-wrap">
                        <Badge variant="default" size="xs" className="font-semibold">
                          {d.libelleTypeConge || d.codeTypeConge}
                        </Badge>
                        {d.estPaye ? (
                          <Badge variant="success" size="xs" className="font-medium">
                            Rémunéré
                          </Badge>
                        ) : (
                          <Badge variant="neutral" size="xs" className="font-medium">
                            Sans solde
                          </Badge>
                        )}
                        {d.libelleMotifEvenement && (
                          <span className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-medium block">
                            {d.libelleMotifEvenement}
                          </span>
                        )}
                      </div>
                    </Td>

                    {/* Période et décompte fourni par le backend */}
                    <Td className="text-xs whitespace-nowrap">
                      <div className="font-medium text-[var(--color-fg)]">
                        {formatDate(d.dateDebut)} <span className="text-[var(--color-fg-subtle)]">→</span> {formatDate(d.dateFin)}
                      </div>
                      <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                        {d.nbJoursDecomptes} jour{Number(d.nbJoursDecomptes) > 1 ? 's' : ''} décompté{Number(d.nbJoursDecomptes) > 1 ? 's' : ''}
                        {d.estDemiJournee && ` · ${d.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : 'Matin'}`}
                      </div>
                    </Td>

                    {/* Statut & Alertes */}
                    <Td>
                      <div className="space-y-1">
                        <StatusBadge status={d.codeStatutDemande || d.statutCode} size="xs" />
                        <div className="flex flex-wrap gap-1">
                          {isRefuse && d.motifRefus && (
                            <p className="text-[10px] text-[var(--color-danger-500)] font-medium truncate max-w-xs" title={d.motifRefus}>
                              Motif : {d.motifRefus}
                            </p>
                          )}
                          {d.derogationSoldeNegatif && (
                            <Badge variant="danger" size="xs">Solde négatif</Badge>
                          )}
                        </div>
                      </div>
                    </Td>

                    {/* Le détail expose les décisions autorisées par le backend. */}
                    <Td className="text-right whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1.5">
                        {(() => {
                          const isAExaminer = canValidate && d.availableActions?.APPROVE?.allowed === true;
                          return (
                            <Button
                              variant="outline"
                              size="sm"
                              className={`min-h-[44px] sm:min-h-[34px] sm:h-[34px] inline-flex items-center justify-center font-medium text-xs px-3.5 rounded-lg transition-colors ${
                                isAExaminer
                                  ? 'border-[var(--color-brand-300)] text-[var(--color-brand-700)] bg-[var(--color-brand-50)]/70 hover:bg-[var(--color-brand-100)] dark:border-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] dark:bg-[var(--color-brand-950)]/40 shadow-2xs'
                                  : 'border-[var(--color-border)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                              }`}
                              iconLeft={<Eye size={14} className={isAExaminer ? "text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]" : "text-[var(--color-fg-subtle)]"} />}
                              onClick={() => onOpenDetail(d)}
                              title="Consulter la demande et ses actions disponibles"
                              aria-label={`${isAExaminer ? 'Examiner' : 'Voir les détails de'} la demande #${d.idDemandeConge} de ${employeName}`}
                            >
                              {isAExaminer ? 'Examiner' : 'Détails'}
                            </Button>
                          );
                        })()}
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </div>
        </>
      )}

      {/* Pagination */}
      {displayTotal > 0 && (
        <Pagination
          currentPage={currentPage}
          totalItems={displayTotal}
          pageSize={pageSize}
          pageSizeOptions={[10, 20, 50]}
          onPageChange={setCurrentPage}
          onPageSizeChange={(size) => { setPageSize(size); setCurrentPage(1); }}
          className="bg-transparent border-t-0 pt-1"
        />
      )}
    </div>
  );
}
