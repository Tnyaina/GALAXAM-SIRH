import { Children, isValidElement, useEffect, useRef, forwardRef } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';

/* ============================================================================
   TABLE — wrapper
   Props ajoutés :
   - maxHeight   : active un scroll vertical + en-tête collant (ex: '480px')
   - toolbar     : contenu affiché au-dessus du <table> (ex: <TableSelectionBar />)
   ============================================================================ */
export function Table({ children, className = '', maxHeight, toolbar, divided = false, style, ...props }) {
  return (
    <div
      className={`relative border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm rounded-none ${maxHeight ? 'overflow-auto' : 'overflow-x-auto'
        } ${divided ? '[&_th:not(:last-child)]:border-r [&_th:not(:last-child)]:border-[var(--color-border-subtle)] [&_td:not(:last-child)]:border-r [&_td:not(:last-child)]:border-[var(--color-border-subtle)]' : ''} ${className}`}
      style={{ ...(maxHeight ? { maxHeight } : null), ...style }}
      {...props}
    >
      {toolbar}
      <table className="w-full border-collapse text-sm">{children}</table>
    </div>
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
export function Th({ children, sortable, sorted, onSort, className = '', ...props }) {
  const isCentered = className.includes('text-center');
  const isRight = className.includes('text-right');
  const justifyClass = isCentered
    ? 'justify-center text-center'
    : isRight
      ? 'justify-end text-right'
      : 'justify-start text-left';

  return (
    <th
      className={`px-3 py-2 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] whitespace-nowrap
        ${!isCentered && !isRight ? 'text-left' : ''}
        ${sortable ? 'cursor-pointer select-none transition-colors duration-[var(--duration-fast)] hover:text-[var(--color-fg)]' : ''}
        ${className}
      `}
      aria-sort={sortable ? (sorted === 'asc' ? 'ascending' : sorted === 'desc' ? 'descending' : 'none') : undefined}
      onClick={sortable ? onSort : undefined}
      {...props}
    >
      <div className={`flex items-center gap-1.5 ${justifyClass}`}>
        {children}
        {sortable && (
          <span className="flex flex-col">
            <ChevronUp
              size={9}
              className={sorted === 'asc' ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}
            />
            <ChevronDown
              size={9}
              className={sorted === 'desc' ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}
            />
          </span>
        )}
      </div>
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
    <th className={`w-10 px-3 py-2 ${className}`} {...props}>
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
  return (
    <tr
      className={`
        transition-colors duration-[var(--duration-fast)]
        ${selected
          ? 'bg-[var(--color-brand-500)]/[0.12] text-[var(--color-fg)] shadow-[inset_2px_0_0_0_var(--color-brand-500)]'
          : 'hover:bg-[var(--color-surface-hover)]'
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
  return (
    <td
      className={`px-3 py-2 text-xs ${muted ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]'
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
      className={`flex items-center justify-between border-b border-[var(--color-border-subtle)] bg-[var(--color-brand-500)]/[0.08] px-3 py-2 text-xs font-medium text-[var(--color-fg)] ${className}`}
    >
      <span>
        {count} ligne{count > 1 ? 's' : ''} sélectionnée{count > 1 ? 's' : ''}
      </span>
      <div className="flex items-center gap-4">
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
        <p className="text-sm font-medium text-[var(--color-fg-muted)]">{title}</p>
        {description && <p className="mt-1 text-xs text-[var(--color-fg-subtle)]">{description}</p>}
      </td>
    </tr>
  );
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