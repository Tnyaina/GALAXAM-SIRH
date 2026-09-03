import SearchSelect from '@/components/ui/forms/SearchSelect';

export default function TypeCongeSection({
  idTypeConge, setIdTypeConge,
  idMotifEvenementFamilial, setIdMotifEvenementFamilial,
  typesConge, motifsFamiliaux,
  isExceptionnel, fieldErrors, setFieldErrors,
  _inputErrorClass
}) {
  return (
    <>
      {/* ── Section 1 : Type de congé ────────────────────────────── */}
      <div className="space-y-1.5">
        <SearchSelect
          label="Type de congé / absence"
          required
          value={idTypeConge}
          onChange={(val) => {
            setIdTypeConge(val);
            setIdMotifEvenementFamilial('');
            setFieldErrors((prev) => ({ ...prev, idTypeConge: null }));
          }}
          options={typesConge
            .filter(t => t.estActif !== false && t.actif !== false || String(t.idTypeConge) === String(idTypeConge))
            .map(t => ({
              value: t.idTypeConge,
              label: t.libelle,
              sublabel: `${t.estPaye ? 'Rémunéré' : 'Sans solde'}${t.soumisDelaiPreavis ? ' · Préavis requis' : ''}`
            }))}
          placeholder="Sélectionner un type de congé…"
          error={fieldErrors.idTypeConge}
        />
      </div>

      {/* Motif Événement Familial (Si EXCEPTIONNEL) */}
      {isExceptionnel && (
        <div className="pl-3 border-l-2 border-[var(--color-warning-500)] space-y-1.5 mt-4">
          <SearchSelect
            label="Motif d'événement familial"
            required
            value={idMotifEvenementFamilial}
            onChange={(val) => {
              setIdMotifEvenementFamilial(val);
              setFieldErrors((prev) => ({ ...prev, idMotifEvenementFamilial: null }));
            }}
            options={motifsFamiliaux
              .filter(m => m.estActif !== false && m.actif !== false || String(m.idMotif) === String(idMotifEvenementFamilial))
              .map(m => ({
                value: m.idMotif,
                label: m.libelle,
                sublabel: `${m.nbJoursAutorises} jours autorisés`
              }))}
            placeholder="Sélectionner un motif…"
            error={fieldErrors.idMotifEvenementFamilial}
          />
        </div>
      )}
    </>
  );
}
