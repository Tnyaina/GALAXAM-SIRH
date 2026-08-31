import { Children, isValidElement } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';

/* TABLE WRAPPER */
export function Table({ children, className = '', ...props }) {
  return (
    <div
      className={`overflow-x-auto border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-sm rounded-none ${className}`}
      {...props}
    >
      <table className="w-full text-sm">
        {children}
      </table>
    </div>
  );
}

/* THEAD */
export function Thead({ children, className = '', ...props }) {
  const childrenArray = Children.toArray(children);
  const hasTrChild = childrenArray.some(
    (child) => isValidElement(child) && (child.type === Tr || child.type === 'tr')
  );
  const hasThChild = childrenArray.some(
    (child) => isValidElement(child) && child.type === Th
  );

  const content = !hasTrChild && hasThChild ? <tr>{children}</tr> : children;

  return (
    <thead
      className={`border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] ${className}`}
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
  const justifyClass = isCentered ? 'justify-center text-center' : isRight ? 'justify-end text-right' : 'justify-start text-left';

  return (
    <th
      className={`px-4 py-3 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] 
        ${!isCentered && !isRight ? 'text-left' : ''}
        ${sortable ? 'cursor-pointer hover:text-[var(--color-fg)] select-none' : ''}
        ${className}
      `}
      onClick={sortable ? onSort : undefined}
      {...props}
    >
      <div className={`flex items-center gap-1.5 ${justifyClass}`}>
        {children}
        {sortable && (
          <span className="flex flex-col">
            <ChevronUp
              size={10}
              className={sorted === 'asc' ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}
            />
            <ChevronDown
              size={10}
              className={sorted === 'desc' ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}
              style={{ marginTop: -2 }}
            />
          </span>
        )}
      </div>
    </th>
  );
}

/* TBODY */
export function Tbody({ children, className = '', ...props }) {
  return (
    <tbody
      className={`divide-y divide-[var(--color-border-subtle)] ${className}`}
      {...props}
    >
      {children}
    </tbody>
  );
}

/* TR */
export function Tr({ children, onClick, selected = false, className = '', ...props }) {
  return (
    <tr
      className={`
        ${selected
          ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-fg)]'
          : 'hover:bg-[var(--color-surface-hover)]'}
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
      className={`px-4 py-3 ${
        muted ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]'
      } ${isData ? 'font-data' : ''} ${className}`}
      {...props}
    >
      {children}
    </td>
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