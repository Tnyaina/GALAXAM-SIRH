import { useState, useRef } from 'react';
import { Upload, X, FileText, AlertCircle } from 'lucide-react';

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
            p-4 sm:p-4.5 text-center border-2 border-dashed cursor-pointer rounded-none
            ${dragOver
                            ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5'
                            : 'border-[var(--color-border)] bg-[var(--color-bg-subtle)] hover:border-[var(--color-brand-500)]'}
          `}
                >
                    <Upload size={18} className="mx-auto text-[var(--color-brand-500)] mb-1.5" />
                    <p className="text-xs font-bold text-[var(--color-fg)]">{label}</p>
                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 font-data">
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
                        className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)]  rounded-none cursor-pointer"
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

export default FileUpload;
