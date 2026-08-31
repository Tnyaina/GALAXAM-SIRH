import { useState } from 'react';
import { X } from 'lucide-react';

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
        <div className="flex flex-wrap items-center gap-1.5 p-1.5 bg-[var(--color-surface)] border border-[var(--color-border)] focus-within:border-[var(--color-brand-500)] rounded-none">
            {tags.map((tag, index) => (
                <span
                    key={index}
                    className="inline-flex items-center gap-1 px-1.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/20 rounded-none font-data"
                >
                    {tag}
                    <button
                        type="button"
                        onClick={() => removeTag(index)}
                        className="hover:text-[var(--color-danger-500)]  cursor-pointer"
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

export default ChipInput;
