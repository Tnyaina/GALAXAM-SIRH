import { Children, createContext, forwardRef, isValidElement, useContext, useEffect, useRef } from 'react';
import { ChevronDown, ChevronUp, ChevronsUpDown } from 'lucide-react';

const TableContext = createContext({ density: 'comfortable', rowHover: true });

/* ============================================================================
   TABLE — wrapper
   Props :
   - maxHeight   : active un scroll vertical + en-tête collant (ex: '480px')
   - toolbar     : contenu affiché au-dessus du <table> (ex: <TableSelectionBar />)
   - bordered    : cadre de surface autonome (false si intégrée dans une Card déjà encadrée)
   ============================================================================ */
export function Table({
  children,
  className = '',
  tableClassName = '',
  tableProps = {},
  maxHeight,
  toolbar,
  divided = false,
  bordered = true,
  density: densityProp,
  size,
  rowHover = true,
  style,
  'aria-label': ariaLabel,
  ...containerProps
}) {
  const density = densityProp || (size === 'sm' ? 'compact' : 'comfortable');
  const contextValue = { density, rowHover };

  return (
    <TableContext.Provider value={contextValue}>
      <div
        className={`relative ${bordered ? 'border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm' : ''} rounded-none ${maxHeight ? 'overflow-auto' : 'overflow-x-auto'
          } ${divided ? '[&_th:not(:last-child)]:border-r [&_th:not(:last-child)]:border-[var(--color-border-subtle)] [&_td:not(:last-child)]:border-r [&_td:not(:last-child)]:border-[var(--color-border-subtle)]' : ''} ${className}`}
        style={{ ...(maxHeight ? { maxHeight } : null), ...style }}
        {...containerProps}
      >
        {toolbar}
        <table {...tableProps} aria-label={tableProps['aria-label'] || ariaLabel} className={`w-full border-collapse text-sm ${tableClassName}`}>
          {children}
        </table>
      </div>
    </TableContext.Provider>
  );
}

/* THEAD
   Prop ajouté : sticky — colle l'en-tête en haut quand Table a un maxHeight */
export function Thead({ children, className = '', sticky = false, ...props }) {
  const childrenArray = Children.toArray(children);
  const hasTrChild = childrenArray.some(
    (child) => isValidElement(child) && (child.type === Tr || child.type === 'tr')
  );
  const hasThChild = childrenArray.some((child) => isValidElement(child) && child.type === Th);

  const content = !hasTrChild && hasThChild ? <tr>{children}</tr> : children;

  return (
    <thead
      className={`border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] ${sticky ? 'sticky top-0 z-10 shadow-[0_1px_0_0_var(--color-border-subtle)]' : ''
        } ${className}`}
      {...props}
    >
      {content}
    </thead>
  );
}

/* TH */
export function Th({ children, sortable, sorted, onSort, sortLabel, scope = 'col', className = '', ...props }) {
  const { density } = useContext(TableContext);
  const isCentered = className.includes('text-center');
  const isRight = className.includes('text-right');
  const justifyClass = isCentered
    ? 'justify-center text-center'
    : isRight
      ? 'justify-end text-right'
      : 'justify-start text-left';

  return (
    <th
      scope={scope}
      className={`${density === 'compact' ? 'px-2 py-1.5 text-[9px]' : 'px-3 py-2 text-[10px]'} font-bold uppercase tracking-wider text-[var(--color-fg-muted)] whitespace-nowrap
        ${!isCentered && !isRight ? 'text-left' : ''}
        ${className}
      `}
      aria-sort={sortable ? (sorted === 'asc' ? 'ascending' : sorted === 'desc' ? 'descending' : 'none') : undefined}
      {...props}
    >
      {sortable ? (
        <button
          type="button"
          onClick={onSort}
          aria-label={sortLabel ? `Trier par ${sortLabel}` : undefined}
          disabled={!onSort}
          className={`inline-flex w-full items-center gap-1.5 ${justifyClass} text-left font-[inherit] text-inherit transition-colors duration-[var(--duration-fast)] hover:text-[var(--color-fg)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)] disabled:cursor-default`}
        >
          {children}
          {sorted === 'asc' ? <ChevronUp size={13} aria-hidden="true" /> : sorted === 'desc' ? <ChevronDown size={13} aria-hidden="true" /> : <ChevronsUpDown size={13} className="text-[var(--color-fg-subtle)]" aria-hidden="true" />}
        </button>
      ) : (
        <div className={`flex items-center gap-1.5 ${justifyClass}`}>{children}</div>
      )}
    </th>
  );
}

/* TH DÉDIÉE À LA CASE "TOUT SÉLECTIONNER"
   Props : checked, indeterminate, onChange */
export function ThCheckbox({ checked = false, indeterminate = false, onChange, className = '', ...props }) {
  const ref = useRef(null);
  useEffect(() => {
    if (ref.current) ref.current.indeterminate = indeterminate;
  }, [indeterminate]);

  return (
    <th scope="col" className={`w-10 px-3 py-2 ${className}`} {...props}>
      <Checkbox ref={ref} checked={checked} onChange={onChange} aria-label="Tout sélectionner" />
    </th>
  );
}

/* TBODY
   Prop ajouté : zebra — alterne un fond léger une ligne sur deux */
export function Tbody({ children, className = '', zebra = false, ...props }) {
  return (
    <tbody
      className={`divide-y divide-[var(--color-border-subtle)] ${zebra ? '[&>tr:nth-child(even)]:bg-[var(--color-bg-subtle)]/40' : ''
        } ${className}`}
      {...props}
    >
      {children}
    </tbody>
  );
}

/* TR
   Le fond de sélection est plus visible (10% -> 12%) et une fine barre
   latérale marque clairement la ligne cochée, en plus du fond. */
export function Tr({ children, onClick, selected = false, className = '', ...props }) {
  const { rowHover } = useContext(TableContext);
  return (
    <tr
      className={`
        transition-colors duration-[var(--duration-fast)]
        ${selected
          ? 'bg-[var(--color-brand-500)]/[0.12] text-[var(--color-fg)] shadow-[inset_2px_0_0_0_var(--color-brand-500)]'
          : rowHover ? 'hover:bg-[var(--color-surface-hover)]' : ''
        }
        ${onClick ? 'cursor-pointer' : ''}
        ${className}
      `}
      onClick={onClick}
      {...props}
    >
      {children}
    </tr>
  );
}

/* TD */
export function Td({ children, muted = false, isData = false, className = '', ...props }) {
  const { density } = useContext(TableContext);
  return (
    <td
      className={`${density === 'compact' ? 'px-2 py-1.5 text-[11px]' : 'px-3 py-2 text-xs'} ${muted ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]'
        } ${isData ? 'font-data' : ''} ${className}`}
      {...props}
    >
      {children}
    </td>
  );
}

/* TD DÉDIÉE À LA CASE DE SÉLECTION D'UNE LIGNE
   stopPropagation évite de déclencher le onClick de la Tr (ex: ouverture d'un détail) */
export function TdCheckbox({ checked = false, onChange, className = '', ...props }) {
  return (
    <td className={`w-10 px-3 py-2 ${className}`} onClick={(e) => e.stopPropagation()} {...props}>
      <Checkbox checked={checked} onChange={onChange} aria-label="Sélectionner la ligne" />
    </td>
  );
}

/* CASE À COCHER — style unique partagé par ThCheckbox / TdCheckbox,
   cohérent avec la palette Deep Navy & Slate Blue (accent = brand-500). */
export const Checkbox = forwardRef(function Checkbox({ className = '', ...props }, ref) {
  return (
    <input
      ref={ref}
      type="checkbox"
      className={`h-[15px] w-[15px] cursor-pointer accent-[var(--color-brand-500)] align-middle
        border border-[var(--color-border)] bg-[var(--color-surface)]
        focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--color-focus-ring)]
        ${className}`}
      onClick={(e) => e.stopPropagation()}
      {...props}
    />
  );
});

/* BARRE D'ACTIONS GROUPÉES — à passer en toolbar={} sur <Table>.
   Ne s'affiche que si count > 0. */
export function TableSelectionBar({ count, onClear, actions, className = '' }) {
  if (!count) return null;
  return (
    <div
      className={`flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-[var(--color-border-subtle)] bg-[var(--color-brand-500)]/[0.08] px-3 py-2 text-xs font-medium text-[var(--color-fg)] ${className}`}
    >
      <span>
        {count} ligne{count > 1 ? 's' : ''} sélectionnée{count > 1 ? 's' : ''}
      </span>
      <div className="flex flex-wrap items-center gap-3">
        {actions}
        <button
          type="button"
          onClick={onClear}
          className="text-[var(--color-fg-muted)] transition-colors duration-[var(--duration-fast)] hover:text-[var(--color-fg)] hover:underline"
        >
          Désélectionner
        </button>
      </div>
    </div>
  );
}

/* ÉTAT VIDE — à placer dans un <Tbody> à la place des <Tr> quand il n'y a pas de données.
   Ex : <Tbody>{rows.length === 0 ? <TableEmpty colSpan={6} /> : rows.map(...)}</Tbody> */
export function TableEmpty({ colSpan, title = 'Aucune donnée', description, className = '' }) {
  return (
    <tr>
      <td colSpan={colSpan} className={`px-3 py-10 text-center ${className}`}>
        <div role="status" aria-live="polite">
        <p className="text-sm font-medium text-[var(--color-fg-muted)]">{title}</p>
        {description && <p className="mt-1 text-xs text-[var(--color-fg-subtle)]">{description}</p>}
        </div>
      </td>
    </tr>
  );
}

export function TableCaption({ children, className = '', ...props }) {
  return <caption className={`px-3 py-2 text-left text-xs text-[var(--color-fg-muted)] ${className}`} {...props}>{children}</caption>;
}

/* TFOOT */
export function Tfoot({ children, className = '', ...props }) {
  return (
    <tfoot
      className={`border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-xs font-medium text-[var(--color-fg-muted)] ${className}`}
      {...props}
    >
      {children}
    </tfoot>
  );
}
