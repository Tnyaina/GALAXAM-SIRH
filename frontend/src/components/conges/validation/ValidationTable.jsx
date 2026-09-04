import { Check, X, Eye, Clock, Calendar } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';

const formatDate = (ts) => {
  if (!ts) return '—';
  const date = new Date(ts);
  return date.toLocaleDateString('fr-FR');
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
  canValidate = true,
  onOpenDetail,
  onOpenRefus,
  onOpenValider
}) {
  return (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xs p-5 sm:p-6 space-y-4">
      {/* En-tête contextuel pour les demandes en attente */}
      {activeStatut === 'EN_ATTENTE' && displayTotal > 0 && (
        <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <Clock size={15} className="text-[var(--color-warning-500)] shrink-0" />
            <span className="text-xs font-bold text-[var(--color-fg)]">
              {displayTotal} demande{displayTotal > 1 ? 's' : ''} en attente de décision hiérarchique ou RH
            </span>
          </div>
          <span className="text-[11px] text-[var(--color-fg-muted)]">
            Action requise pour libérer le solde ou autoriser l'absence
          </span>
        </div>
      )}

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
        <div className="overflow-x-auto">
          <Table>
            <Thead>
              <Tr>
                <Th>Réf.</Th>
                <Th>Collaborateur & Affectation</Th>
                <Th>Type d'Absence</Th>
                <Th>Période d'Absence</Th>
                <Th className="text-center">Durée Décomptée</Th>
                <Th>Statut</Th>
                <Th align="right">Actions Décisionnelles</Th>
              </Tr>
            </Thead>
            <Tbody>
              {demandes.map((d) => {
                const employeName = `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employé ${d.idEmploye}`;
                const isEnAttente = Boolean(d.isEnAttente ?? (d.codeStatutDemande === 'EN_ATTENTE'));
                const isRefuse = Boolean(d.isRefuse ?? (d.codeStatutDemande === 'REFUSE'));

                return (
                  <Tr
                    key={d.idDemandeConge}
                    className="hover:bg-[var(--color-surface-hover)] transition-colors group"
                  >
                    {/* Référence */}
                    <Td className="font-mono text-xs text-[var(--color-fg-subtle)] whitespace-nowrap">
                      #{d.idDemandeConge}
                    </Td>

                    {/* Collaborateur */}
                    <Td>
                      <div className="font-bold text-[var(--color-fg)] text-xs leading-tight">
                        {employeName}
                      </div>
                      {d.matricule && (
                        <div className="text-[11px] text-[var(--color-fg-subtle)] font-mono mt-0.5">
                          Matr. {d.matricule}
                        </div>
                      )}
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

                    {/* Période */}
                    <Td className="text-xs whitespace-nowrap">
                      <div className="font-medium text-[var(--color-fg)]">
                        {formatDate(d.dateDebut)} <span className="text-[var(--color-fg-subtle)]">→</span> {formatDate(d.dateFin)}
                      </div>
                    </Td>

                    {/* Durée */}
                    <Td className="text-center whitespace-nowrap">
                      {d.estDemiJournee ? (
                        <div>
                          <span className="font-mono font-bold text-brand-600 text-xs">0,5 j</span>
                          <div className="text-[10px] text-[var(--color-fg-subtle)]">
                            {d.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : 'Matin'}
                          </div>
                        </div>
                      ) : (
                        <div>
                          <span className="font-mono font-bold text-[var(--color-fg)] text-xs">
                            {d.nbJoursDecomptes}
                          </span>
                          <span className="text-xs text-[var(--color-fg-muted)]"> j ouvrés</span>
                        </div>
                      )}
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
                          {!d.justificatifFourni && d.codeTypeConge === 'MALADIE' && (
                            <Badge variant="warning" size="xs">Justificatif attendu</Badge>
                          )}
                        </div>
                      </div>
                    </Td>

                    {/* Actions décisionnelles */}
                    <Td align="right" className="whitespace-nowrap">
                      <div className="flex items-center justify-end gap-1.5">
                        {isEnAttente && canValidate ? (
                          <>
                            <Button
                              variant="danger"
                              size="xs"
                              iconLeft={<X size={12} />}
                              onClick={() => onOpenRefus(d)}
                              className="text-xs font-semibold"
                            >
                              Refuser
                            </Button>
                            <Button
                              variant="success"
                              size="xs"
                              iconLeft={<Check size={12} />}
                              onClick={() => onOpenValider(d)}
                              className="text-xs font-semibold"
                            >
                              Valider
                            </Button>
                          </>
                        ) : null}

                        <Button
                          variant="ghost"
                          size="xs"
                          iconLeft={<Eye size={13} />}
                          onClick={() => onOpenDetail(d)}
                          title="Consulter la fiche détaillée"
                          className="text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)]"
                        >
                          Détails
                        </Button>
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </Tbody>
          </Table>
        </div>
      )}

      {/* Pagination */}
      {displayTotal > 0 && (
        <Pagination
          currentPage={currentPage}
          totalItems={displayTotal}
          pageSize={pageSize}
          pageSizeOptions={[10, 20, 50]}
          onPageChange={setCurrentPage}
          onPageSizeChange={setPageSize}
          className="bg-transparent border-t-0 pt-1"
        />
      )}
    </div>
  );
}
