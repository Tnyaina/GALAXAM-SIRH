import Input, { Select } from '@/components/ui/forms/Input';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import SearchSelect from '@/components/ui/forms/SearchSelect';

export default function AffectationFieldsSection({
    formData,
    setField,
    sites = [],
    departements = [],
    services = [],
    fonctions = [],
    categoriesPro = [],
    modesPaie = [],
    responsables = [],
}) {
    return (
        <div className="space-y-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-1 border-b border-ink-150 dark:border-ink-700">
                2. Affectation Carrière & Poste
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Select
                    label="Site d'Exploitation"
                    required
                    value={formData.idSite}
                    onChange={(e) => setField('idSite', e.target.value)}
                >
                    <option value="">-- Sélectionner un site --</option>
                    {sites.map(s => (
                        <option key={s.idSite} value={s.idSite}>
                            {s.codeSite} — {s.libelle}
                        </option>
                    ))}
                </Select>

                <Select
                    label="Département"
                    required
                    value={formData.idDepartement}
                    onChange={(e) => setField('idDepartement', e.target.value)}
                >
                    <option value="">-- Sélectionner un département --</option>
                    {departements.map(d => (
                        <option key={d.idDepartement} value={d.idDepartement}>
                            {d.codeDepartement} — {d.libelle}
                        </option>
                    ))}
                </Select>

                <Select
                    label="Service"
                    required
                    value={formData.idService}
                    onChange={(e) => setField('idService', e.target.value)}
                    disabled={!formData.idDepartement}
                >
                    <option value="">
                        {formData.idDepartement
                            ? '-- Sélectionner un service --'
                            : '-- Choisir un département d\'abord --'}
                    </option>
                    {services
                        .filter(srv => !formData.idDepartement || String(srv.idDepartement) === String(formData.idDepartement))
                        .map(srv => (
                            <option key={srv.idService} value={srv.idService}>
                                {srv.codeService} — {srv.libelle}
                            </option>
                        ))
                    }
                </Select>

                <SearchSelect
                    label="Fonction / Poste"
                    required
                    value={formData.idFonction}
                    onChange={(val) => setField('idFonction', val)}
                    options={fonctions.map((f) => ({
                        value: f.idFonction,
                        label: f.libelle,
                        sublabel: f.codeFonction,
                    }))}
                    placeholder="Rechercher une fonction…"
                />

                <EmployeSearchSelect
                    label="Responsable Hiérarchique (N+1)"
                    value={formData.idResponsableHierarchique}
                    onChange={(empId) => setField('idResponsableHierarchique', empId)}
                    employes={responsables}
                    placeholder="Rechercher le manager N+1…"
                />

                <Select
                    label="Catégorie Professionnelle"
                    required
                    value={formData.idCategorieProfessionnelle}
                    onChange={(e) => setField('idCategorieProfessionnelle', e.target.value)}
                >
                    <option value="">-- Sélectionner une catégorie --</option>
                    {categoriesPro.map((c, idx) => {
                        const idCat = c.idCategorieProfessionnelle ?? c.idCategorie ?? idx;
                        return (
                            <option key={idCat} value={idCat}>
                                {c.code} — {c.libelle}
                            </option>
                        );
                    })}
                </Select>

                <Select
                    label="Mode de Calcul de la Paie"
                    required
                    value={formData.idModeCalculPaie}
                    onChange={(e) => setField('idModeCalculPaie', e.target.value)}
                >
                    <option value="">-- Sélectionner --</option>
                    {modesPaie.map((m, idx) => {
                        const idMode = m.idModeCalculPaie ?? m.idModeCalcul ?? idx;
                        return (
                            <option key={idMode} value={idMode}>
                                {m.code} — {m.libelle}
                            </option>
                        );
                    })}
                </Select>

                <Input
                    label="Salaire de Base (Ar)"
                    type="number"
                    required
                    value={formData.salaireBase}
                    onChange={(e) => setField('salaireBase', e.target.value)}
                    placeholder="Ex: 1500000"
                />
            </div>
        </div>
    );
}
