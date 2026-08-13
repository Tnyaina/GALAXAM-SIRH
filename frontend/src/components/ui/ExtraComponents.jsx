import { useState, useRef } from 'react';
import { ChevronRight, ChevronDown, Upload, X, FileText, AlertCircle } from 'lucide-react';

/* =========================================================================
   1. BREADCRUMB (Fil d'Ariane)
   ========================================================================= */
export function Breadcrumb({ items = [] }) {
    return (
        <nav aria-label="Breadcrumb" className="flex items-center gap-1.5 text-xs text-[var(--color-fg-muted)]">
            {items.map((item, index) => {
                const isLast = index === items.length - 1;
                return (
                    <div key={index} className="flex items-center gap-1.5">
                        {index > 0 && <ChevronRight size={12} className="text-[var(--color-fg-subtle)] shrink-0" />}
                        {isLast || !item.href ? (
                            <span className={`font-semibold ${isLast ? 'text-[var(--color-fg)] font-data' : ''}`}>
                                {item.label}
                            </span>
                        ) : (
                            <a
                                href={item.href}
                                className="hover:text-[var(--color-brand-500)] transition-colors underline-offset-2 hover:underline"
                            >
                                {item.label}
                            </a>
                        )}
                    </div>
                );
            })}
        </nav>
    );
}

/* =========================================================================
   2. TOOLTIP (Info-bulle au survol)
   ========================================================================= */
export function Tooltip({ children, content, position = 'top' }) {
    const [visible, setVisible] = useState(false);

    const positionClasses = {
        top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
        bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
        left: 'right-full top-1/2 -translate-y-1/2 mr-2',
        right: 'left-full top-1/2 -translate-y-1/2 ml-2',
    };

    return (
        <div
            className="relative inline-flex"
            onMouseEnter={() => setVisible(true)}
            onMouseLeave={() => setVisible(false)}
            onFocus={() => setVisible(true)}
            onBlur={() => setVisible(false)}
        >
            {children}
            {visible && content && (
                <div
                    role="tooltip"
                    className={`
            absolute z-50 whitespace-nowrap px-2.5 py-1 text-[11px] font-medium
            bg-[var(--color-fg)] text-[var(--color-bg)] rounded-none shadow-md
            pointer-events-none transition-opacity duration-150
            ${positionClasses[position] || positionClasses.top}
          `}
                >
                    {content}
                </div>
            )}
        </div>
    );
}

/* =========================================================================
   3. FILE UPLOAD / DROPZONE (Importation de documents)
   ========================================================================= */
export function FileUpload({ onFileSelect, accept = '.pdf,.png,.jpg', maxMb = 10, label = 'Déposez votre document ici' }) {
    const [dragOver, setDragOver] = useState(false);
    const [file, setFile] = useState(null);
    const [error, setError] = useState('');
    const inputRef = useRef(null);

    const handleFile = (f) => {
        if (!f) return;
        if (f.size > maxMb * 1024 * 1024) {
            setError(`Le fichier dépasse la taille maximale de ${maxMb} MB.`);
            return;
        }
        setError('');
        setFile(f);
        if (onFileSelect) onFileSelect(f);
    };

    const handleDrop = (e) => {
        e.preventDefault();
        setDragOver(false);
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            handleFile(e.dataTransfer.files[0]);
        }
    };

    return (
        <div className="space-y-2">
            <input
                ref={inputRef}
                type="file"
                accept={accept}
                className="hidden"
                onChange={(e) => e.target.files?.[0] && handleFile(e.target.files[0])}
            />

            {!file ? (
                <div
                    onClick={() => inputRef.current?.click()}
                    onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                    onDragLeave={() => setDragOver(false)}
                    onDrop={handleDrop}
                    className={`
            p-6 text-center border-2 border-dashed cursor-pointer transition-colors rounded-none
            ${dragOver
                            ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5'
                            : 'border-[var(--color-border)] bg-[var(--color-bg-subtle)] hover:border-[var(--color-brand-500)]'}
          `}
                >
                    <Upload size={20} className="mx-auto text-[var(--color-brand-500)] mb-2" />
                    <p className="text-xs font-bold text-[var(--color-fg)]">{label}</p>
                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 font-data">
                        Formats acceptés : {accept} (Max {maxMb} MB)
                    </p>
                </div>
            ) : (
                <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] flex items-center justify-between gap-3 rounded-none">
                    <div className="flex items-center gap-2.5 min-w-0">
                        <FileText size={18} className="text-[var(--color-brand-500)] shrink-0" />
                        <div className="min-w-0">
                            <p className="text-xs font-semibold text-[var(--color-fg)] truncate">{file.name}</p>
                            <p className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                {(file.size / (1024 * 1024)).toFixed(2)} MB
                            </p>
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={() => { setFile(null); if (onFileSelect) onFileSelect(null); }}
                        className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] transition-colors rounded-none cursor-pointer"
                        aria-label="Supprimer le fichier"
                    >
                        <X size={15} />
                    </button>
                </div>
            )}

            {error && (
                <p className="text-xs text-[var(--color-danger-500)] font-semibold flex items-center gap-1">
                    <AlertCircle size={13} /> {error}
                </p>
            )}
        </div>
    );
}

/* =========================================================================
   4. ACCORDION / COLLAPSE (Panneau repliable)
   ========================================================================= */
export function AccordionItem({ title, subtitle, children, defaultOpen = false }) {
    const [open, setOpen] = useState(defaultOpen);

    return (
        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none">
            <button
                type="button"
                onClick={() => setOpen((v) => !v)}
                className="w-full px-5 py-3.5 flex items-center justify-between text-left hover:bg-[var(--color-bg-subtle)] transition-colors rounded-none cursor-pointer"
            >
                <div>
                    <p className="text-sm font-bold text-[var(--color-fg)]">{title}</p>
                    {subtitle && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{subtitle}</p>}
                </div>
                <ChevronDown
                    size={16}
                    className={`text-[var(--color-fg-muted)] transition-transform duration-200 ${open ? 'rotate-180' : ''}`}
                />
            </button>
            {open && (
                <div className="px-5 py-4 border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-fg)] space-y-2">
                    {children}
                </div>
            )}
        </div>
    );
}

/* =========================================================================
   5. CHIP INPUT (Saisie de puces / tags)
   ========================================================================= */
export function ChipInput({ tags = [], onChange, placeholder = 'Ajouter une valeur...' }) {
    const [input, setInput] = useState('');

    const addTag = () => {
        const trimmed = input.trim();
        if (trimmed && !tags.includes(trimmed)) {
            onChange([...tags, trimmed]);
            setInput('');
        }
    };

    const removeTag = (indexToRemove) => {
        onChange(tags.filter((_, index) => index !== indexToRemove));
    };

    const handleKeyDown = (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            addTag();
        } else if (e.key === 'Backspace' && !input && tags.length > 0) {
            removeTag(tags.length - 1);
        }
    };

    return (
        <div className="flex flex-wrap items-center gap-1.5 p-2 bg-[var(--color-surface)] border-2 border-[var(--color-border)] focus-within:border-[var(--color-brand-500)] rounded-none transition-colors">
            {tags.map((tag, index) => (
                <span
                    key={index}
                    className="inline-flex items-center gap-1 px-2 py-0.5 text-xs font-semibold uppercase tracking-wider bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/20 rounded-none font-data"
                >
                    {tag}
                    <button
                        type="button"
                        onClick={() => removeTag(index)}
                        className="hover:text-[var(--color-danger-500)] transition-colors cursor-pointer"
                    >
                        <X size={12} />
                    </button>
                </span>
            ))}
            <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={tags.length === 0 ? placeholder : ''}
                className="flex-1 min-w-[120px] bg-transparent text-xs text-[var(--color-fg)] outline-none px-1 py-0.5"
            />
        </div>
    );
}