import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { EllipsisVertical } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/** Menu d'actions compact, rendu hors des tableaux pour éviter leur découpe. */
export default function ActionMenu({ label = 'Actions', triggerLabel, triggerClassName = '', items = [], disabled = false }) {
    const triggerRef = useRef(null);
    const menuRef = useRef(null);
    const [position, setPosition] = useState(null);
    const open = Boolean(position);

    useEffect(() => {
        if (!open) return undefined;
        const close = (event) => {
            if (triggerRef.current?.contains(event.target) || menuRef.current?.contains(event.target)) return;
            setPosition(null);
        };
        const onKeyDown = (event) => {
            if (event.key === 'Escape') {
                setPosition(null);
                triggerRef.current?.focus();
            }
        };
        const onScroll = (event) => {
            if (!menuRef.current?.contains(event.target)) setPosition(null);
        };
        const onResize = () => setPosition(null);
        document.addEventListener('pointerdown', close);
        document.addEventListener('keydown', onKeyDown);
        window.addEventListener('scroll', onScroll, true);
        window.addEventListener('resize', onResize);
        menuRef.current?.querySelector('button:not(:disabled)')?.focus();
        return () => {
            document.removeEventListener('pointerdown', close);
            document.removeEventListener('keydown', onKeyDown);
            window.removeEventListener('scroll', onScroll, true);
            window.removeEventListener('resize', onResize);
        };
    }, [open]);

    const toggle = () => {
        if (open) return setPosition(null);
        const rect = triggerRef.current.getBoundingClientRect();
        const width = 264;
        const sectionCount = new Set(items.map(item => item.section).filter(Boolean)).size;
        const height = Math.min(items.length * 42 + sectionCount * 28 + 12, 320);
        setPosition({
            left: Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8)),
            top: rect.bottom + height > window.innerHeight ? Math.max(8, rect.top - height - 4) : rect.bottom + 4
        });
    };

    return <>
        <Button ref={triggerRef} type="button" variant="outline" size="xs"
            iconOnly={!triggerLabel} icon={triggerLabel ? undefined : <EllipsisVertical size={16} />}
            iconRight={triggerLabel ? <EllipsisVertical size={14} /> : undefined}
            className={triggerClassName}
            aria-label={label} aria-haspopup="menu" aria-expanded={open}
            disabled={disabled} onClick={toggle}>{triggerLabel}</Button>
        {open && createPortal(<div ref={menuRef} role="menu" aria-label={label}
            className="fixed z-[80] w-[264px] border border-[var(--color-border)] bg-[var(--color-surface)] p-1 shadow-lg"
            style={position}>
            {items.map((item, index) => <div key={item.label}>
                {item.section && item.section !== items[index - 1]?.section && (
                    <div role="presentation" className={`${index > 0 ? 'border-t border-[var(--color-border-subtle)] mt-1 pt-2' : 'pt-1'} px-3 pb-1 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]`}>
                        {item.section}
                    </div>
                )}
                <button type="button" role="menuitem"
                disabled={item.disabled} onClick={() => { setPosition(null); item.onClick?.(); triggerRef.current?.focus(); }}
                className={`flex w-full items-center gap-2 px-3 py-2 text-left text-xs hover:bg-[var(--color-bg-subtle)] focus:bg-[var(--color-bg-subtle)] disabled:opacity-40 disabled:cursor-not-allowed ${item.danger ? 'text-[var(--color-danger-700)]' : 'text-[var(--color-fg)]'}`}>
                {item.icon && <span aria-hidden="true" className="shrink-0 text-[var(--color-fg-muted)]">{item.icon}</span>}
                {item.label}
                </button>
            </div>)}
        </div>, document.body)}
    </>;
}
