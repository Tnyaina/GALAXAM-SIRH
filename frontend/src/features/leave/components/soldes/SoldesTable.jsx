import { Link } from 'react-router-dom';
import { ArrowUpDown, ArrowUp, ArrowDown, ExternalLink, Calendar } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { useAuth } from '@/context/AuthContext';
import { fmtDate } from '@/utils/dateUtils';

const formatDate = (timestamp) => fmtDate(timestamp);

export default function SoldesTable({
  ganttData,
  paginatedTableData,
  totalTableItems,
  tableSortField,
  tableSortOrder,
  handleTableSort,
  tablePage,
  setTablePage,
  tablePageSize,
  setTablePageSize
}) {
  const { hasPermission } = useAuth();
  const canViewEmploye = hasPermission('EMPLOYE_READ');

  const renderSortIcon = (field) => {
    if (tableSortField !== field) return <ArrowUpDown size={12} className="text-[var(--color-fg-subtle)] opacity-40 ml-1 inline-block" />;
    return tableSortOrder === 'asc'
      ? <ArrowUp size={12} className="text-[var(--color-brand-600)] ml-1 inline-block font-bold" />
      : <ArrowDown size={12} className="text-[var(--color-brand-600)] ml-1 inline-block font-bold" />;
  };

  return (
    <div className="px-4 pb-4 space-y-3">

      {ganttData.length === 0 ? (
        <EmptyState
          icon={Calendar}
          title="Aucune donnée d'absence"
          description="Aucune absence validée ou en attente parmi les collaborateurs affichés."
        />
      ) : (
        <Table>
          <Thead>
            <Tr>
              <Th className="cursor-pointer select-none hover:text-[var(--color-brand-600)]" onClick={() => handleTableSort('employe')}>
                Collaborateur {renderSortIcon('employe')}
              </Th>
              <Th className="cursor-pointer select-none hover:text-[var(--color-brand-600)]" onClick={() => handleTableSort('type')}>
                Type d'absence {renderSortIcon('type')}
              </Th>
              <Th className="cursor-pointer select-none hover:text-[var(--color-brand-600)]" onClick={() => handleTableSort('originalStart')}>
                Date Début {renderSortIcon('originalStart')}
              </Th>
              <Th className="cursor-pointer select-none hover:text-[var(--color-brand-600)]" onClick={() => handleTableSort('originalEnd')}>
                Date Fin {renderSortIcon('originalEnd')}
              </Th>
              <Th className="cursor-pointer select-none hover:text-[var(--color-brand-600)]" onClick={() => handleTableSort('dureeJours')}>
                Jours Décomptés {renderSortIcon('dureeJours')}
              </Th>
              <Th className="cursor-pointer select-none hover:text-[var(--color-brand-600)]" onClick={() => handleTableSort('statut')}>
                Statut {renderSortIcon('statut')}
              </Th>
            </Tr>
          </Thead>
          <Tbody>
            {paginatedTableData.map((item, idx) => (
              <Tr key={idx} className="hover:bg-[var(--color-surface-hover)] transition-colors">
                <Td className="font-bold text-[var(--color-fg)]">
                  {item.idEmploye && canViewEmploye ? (
                    <Link
                      to={`/employes/${item.idEmploye}?tab=conges`}
                      className="hover:text-[var(--color-brand-600)] hover:underline inline-flex items-center gap-1.5 text-[var(--color-fg)]"
                      title={`Consulter le dossier congé de ${item.employe}`}
                    >
                      {item.employe}
                      <ExternalLink size={12} className="text-[var(--color-fg-subtle)] hover:text-[var(--color-brand-600)]" />
                    </Link>
                  ) : (
                    item.employe
                  )}
                </Td>
                <Td>
                  <Badge variant="default" size="xs" className="font-semibold">
                    {item.type}
                  </Badge>
                </Td>
                <Td isData className="font-mono text-xs text-[var(--color-fg-muted)]">
                  {formatDate(item.originalStart)}
                </Td>
                <Td isData className="font-mono text-xs text-[var(--color-fg-muted)]">
                  {formatDate(item.originalEnd)}
                </Td>
                <Td isData className="font-mono text-xs font-bold text-[var(--color-fg)]">
                  {item.dureeJours} j
                </Td>
                <Td>
                  <StatusBadge status={item.statut} size="xs" />
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      )}

      {/* Pagination */}
      {totalTableItems > tablePageSize && (
        <Pagination
          currentPage={tablePage}
          totalItems={totalTableItems}
          pageSize={tablePageSize}
          pageSizeOptions={[5, 10, 20, 50]}
          onPageChange={setTablePage}
          onPageSizeChange={(size) => {
            setTablePageSize(size);
            setTablePage(1);
          }}
          className="bg-transparent border-t-0 pt-1"
        />
      )}
    </div>
  );
}
