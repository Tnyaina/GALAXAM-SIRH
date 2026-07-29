import { Children, isValidElement } from 'react';
import { ChevronUp, ChevronDown } from 'lucide-react';

/* TABLE WRAPPER */
export function Table({ children, className = '', dark = false, ...props }) {
  return (
    <div
      className={`overflow-x-auto border ${
        dark
          ? 'border-[#3a3a3a] bg-[#2d2d2d]'
          : 'border-[#e2e2e2] bg-white shadow-sm'
      } ${className}`}
      {...props}
    >
      <table className="w-full text-sm">
        {children}
      </table>
    </div>
  );
}

/* THEAD */
export function Thead({ children, className = '', dark = false, ...props }) {
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
      className={`border-b ${
        dark
          ? 'border-[#444] bg-[#252525]'
          : 'border-[#e2e2e2] bg-[#f4f4f4]'
      } ${className}`}
      {...props}
    >
      {content}
    </thead>
  );
}

/* TH */
export function Th({ children, sortable, sorted, onSort, dark = false, className = '', ...props }) {
  return (
    <th
      className={`px-4 py-3 text-left text-[10px] font-bold uppercase tracking-widest transition-colors
        ${dark ? 'text-[#888]' : 'text-[#666]'}
        ${sortable
          ? dark
            ? 'cursor-pointer hover:text-[#ccc] select-none'
            : 'cursor-pointer hover:text-[#2d2d2d] select-none'
          : ''}
        ${className}
      `}
      onClick={sortable ? onSort : undefined}
      {...props}
    >
      <div className="flex items-center gap-1.5">
        {children}
        {sortable && (
          <span className="flex flex-col">
            <ChevronUp
              size={10}
              className={sorted === 'asc'
                ? 'text-[#2196f3]'
                : dark ? 'text-[#555]' : 'text-[#ccc]'}
            />
            <ChevronDown
              size={10}
              className={sorted === 'desc'
                ? 'text-[#2196f3]'
                : dark ? 'text-[#555]' : 'text-[#ccc]'}
              style={{ marginTop: -2 }}
            />
          </span>
        )}
      </div>
    </th>
  );
}

/* TBODY */
export function Tbody({ children, className = '', dark = false, ...props }) {
  return (
    <tbody
      className={`divide-y ${dark ? 'divide-[#3a3a3a]' : 'divide-[#ececec]'} ${className}`}
      {...props}
    >
      {children}
    </tbody>
  );
}

/* TR */
export function Tr({ children, onClick, dark = false, selected = false, className = '', ...props }) {
  return (
    <tr
      className={`transition-colors
        ${selected
          ? dark ? 'bg-[#1a3a52]' : 'bg-[#e8f4fd]'
          : dark
            ? 'hover:bg-[#363636]'
            : 'hover:bg-[#fafafa]'}
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
export function Td({ children, muted = false, dark = false, className = '', ...props }) {
  return (
    <td
      className={`px-4 py-3 ${
        muted
          ? dark ? 'text-[#666]' : 'text-[#999]'
          : dark ? 'text-[#d0d0d0]' : 'text-[#2d2d2d]'
      } ${className}`}
      {...props}
    >
      {children}
    </td>
  );
}

/* TFOOT — optionnel, pour totaux / pagination inline */
export function Tfoot({ children, className = '', dark = false, ...props }) {
  return (
    <tfoot
      className={`border-t text-xs font-medium ${
        dark
          ? 'border-[#444] bg-[#252525] text-[#777]'
          : 'border-[#e2e2e2] bg-[#f9f9f9] text-[#888]'
      } ${className}`}
      {...props}
    >
      {children}
    </tfoot>
  );
}