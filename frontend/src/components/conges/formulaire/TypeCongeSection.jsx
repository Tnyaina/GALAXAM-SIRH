import { AlertTriangle } from 'lucide-react';

export default function TypeCongeSection({
  idTypeConge, setIdTypeConge,
  idMotifEvenementFamilial, setIdMotifEvenementFamilial,
  typesConge, motifsFamiliaux,
  isExceptionnel, fieldErrors, setFieldErrors,
  inputErrorClass
}) {
  return (
    <>
      {/* ── Section 1 : Type de congé ────────────────────────────── */}
      <div className="space-y-1.5">
        <label htmlFor="idTypeConge" className="block text-sm font-semibold text-[var(--color-fg)]">
          Type de congé / absence <span className="text-danger-500">*</span>
        </label>
        <select
          id="idTypeConge"
          value={idTypeConge}
          onChange={(e) => {
            setIdTypeConge(e.target.value);
            setIdMotifEvenementFamilial('');
            setFieldErrors((prev) => ({ ...prev, idTypeConge: null }));
          }}
          aria-invalid={!!fieldErrors.idTypeConge}
          className={`w-full px-3 py-2.5 bg-[var(--color-bg-subtle)] border rounded-none text-[var(--color-fg)] focus:outline-none cursor-pointer text-sm ${inputErrorClass(fieldErrors.idTypeConge) || 'border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)]'}`}
        >
          <option value="">Sélectionner un type de congé…</option>
          {typesConge.map((t) => (
            <option key={t.idTypeConge} value={t.idTypeConge}>
              {t.libelle} — {t.estPaye ? 'Rémunéré' : 'Sans solde'}{t.soumisDelaiPreavis ? ' · Préavis requis' : ''}
            </option>
          ))}
        </select>
        {fieldErrors.idTypeConge && (
          <p className="text-xs text-danger-500 flex items-center gap-1">
            <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.idTypeConge}
          </p>
        )}
      </div>

      {/* Motif Événement Familial (Si EXCEPTIONNEL) */}
      {isExceptionnel && (
        <div className="pl-3 border-l-2 border-warning-500 space-y-1.5 mt-4">
          <label htmlFor="idMotifEvenementFamilial" className="block text-sm font-semibold text-[var(--color-fg)]">
            Motif d'événement familial <span className="text-danger-500">*</span>
          </label>
          <select
            id="idMotifEvenementFamilial"
            value={idMotifEvenementFamilial}
            onChange={(e) => {
              setIdMotifEvenementFamilial(e.target.value);
              setFieldErrors((prev) => ({ ...prev, idMotifEvenementFamilial: null }));
            }}
            aria-invalid={!!fieldErrors.idMotifEvenementFamilial}
            className={`w-full px-3 py-2.5 bg-[var(--color-bg-subtle)] border rounded-none text-[var(--color-fg)] focus:outline-none cursor-pointer text-sm ${inputErrorClass(fieldErrors.idMotifEvenementFamilial) || 'border-[var(--color-border-subtle)] focus:border-warning-500'}`}
          >
            <option value="">Sélectionner un motif…</option>
            {motifsFamiliaux.map((m) => (
              <option key={m.idMotif} value={m.idMotif}>
                {m.libelle} ({m.nbJoursAutorises} jours autorisés)
              </option>
            ))}
          </select>
          {fieldErrors.idMotifEvenementFamilial && (
            <p className="text-xs text-danger-500 flex items-center gap-1">
              <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {fieldErrors.idMotifEvenementFamilial}
            </p>
          )}
        </div>
      )}
    </>
  );
}
