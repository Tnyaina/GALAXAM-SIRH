import { useState, useRef, useEffect } from 'react';
import { MoreVertical } from 'lucide-react';

/**
 * DropdownMenu — Menu contextuel déroulant (Actions, filtres, options).
 */
export function DropdownMenu({
    trigger,
    children,
    align = 'right', // 'right' | 'left'
    className = '',
}) {
    const [isOpen, setIsOpen] = useState(false);
    const menuRef = useRef(null);

    useEffect(() => {
        const handleClickOutside = (event) => {
            if (menuRef.current && !menuRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        };
        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
        }
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, [isOpen]);

    const alignClass = align === 'left' ? 'left-0' : 'right-0';

    return (
        <div className={`relative inline-flex text-left ${className}`} ref={menuRef}>
            {/* Bouton déclencheur */}
            {trigger ? (
                <div onClick={() => setIsOpen((prev) => !prev)} className="cursor-pointer">
                    {trigger}
                </div>
            ) : (
                <button
                    type="button"
                    onClick={() => setIsOpen((prev) => !prev)}
                    className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none transition-colors cursor-pointer"
                    aria-label="Actions"
                >
                    <MoreVertical size={16} />
                </button>
            )}

            {/* Menu flottant */}
            {isOpen && (
                <div
                    className={`
            absolute top-full mt-1.5 z-50 w-52 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]
            shadow-lg rounded-none py-1 animate-in fade-in zoom-in-95 duration-100 ${alignClass}
          `}
                    onClick={() => setIsOpen(false)}
                >
                    {children}
                </div>
            )}
        </div>
    );
}

export function DropdownItem({
    children,
    icon,
    onClick,
    variant = 'default', // 'default' | 'danger' | 'success'
    disabled = false,
    className = '',
}) {
    const variantStyles = {
        default: 'text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]',
        danger: 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10',
        success: 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10',
    };

    return (
        <button
            type="button"
            disabled={disabled}
            onClick={onClick}
            className={`
        w-full flex items-center gap-2.5 px-4 py-2 text-xs font-semibold text-left transition-colors cursor-pointer rounded-none
        disabled:opacity-40 disabled:cursor-not-allowed
        ${variantStyles[variant] || variantStyles.default}
        ${className}
      `}
        >
            {icon && <span className="shrink-0">{icon}</span>}
            <span className="truncate">{children}</span>
        </button>
    );
}

export function DropdownDivider() {
    return <div className="h-px my-1 bg-[var(--color-border-subtle)]" />;
}

export function DropdownHeader({ children }) {
    return (
        <div className="px-4 py-1.5 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)]">
            {children}
        </div>
    );
}