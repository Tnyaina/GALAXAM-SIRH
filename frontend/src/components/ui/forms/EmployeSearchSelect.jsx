import { useState, useEffect, useRef } from 'react';
import { Search, User, X, AlertTriangle } from 'lucide-react';
import employeService from '@/api/employe/employeService';
import { useDebounce } from '@/hooks/useDebounce';

export default function EmployeSearchSelect({
  label = 'Employé',
  required = false,
  value,
  onChange,
  employes: employesProp = null,
  error = null,
  placeholder = 'Rechercher par nom, prénom ou matricule…',
  className = '',
  disabled = false,
}) {
  const [selectedEmployeObj, setSelectedEmployeObj] = useState(null);
  const [empSearchQuery, setEmpSearchQuery] = useState('');
  const [showEmpDropdown, setShowEmpDropdown] = useState(false);
  const [listEmployes, setListEmployes] = useState([]);
  const [loadingList, setLoadingList] = useState(false);
  const dropdownRef = useRef(null);

  // Debounce de 300ms sur la frappe utilisateur (évite de surcharger l'API)
  const debouncedQuery = useDebounce(empSearchQuery, 300);

  // Mode 1 : Si la liste des employés est passée en prop (ex: managers N+1)
  useEffect(() => {
    if (employesProp && Array.isArray(employesProp)) {
      setListEmployes(employesProp);
    }
  }, [employesProp]);

  // Mode 2 : Si pas de prop, recherche débouncée côté serveur (Front + Back)
  useEffect(() => {
    if (employesProp && Array.isArray(employesProp)) return;

    let isMounted = true;
    setLoadingList(true);

    employeService
      .getEmployes({ q: debouncedQuery.trim() || undefined, size: 20 })
      .then((res) => {
        if (!isMounted) return;
        const list = Array.isArray(res) ? res : res?.content || [];
        setListEmployes(list);
      })
      .catch((err) => console.error('Erreur recherche serveur employés dans EmployeSearchSelect', err))
      .finally(() => {
        if (isMounted) setLoadingList(false);
      });

    return () => {
      isMounted = false;
    };
  }, [debouncedQuery, employesProp]);

  // Retrouver l'objet employé si seule la valeur (ID) est passée depuis le parent
  useEffect(() => {
    if (value) {
      // Chercher d'abord dans la liste courante
      const found = listEmployes.find((emp) => String(emp.idEmploye) === String(value));
      if (found) {
        setSelectedEmployeObj(found);
      } else if (!selectedEmployeObj || String(selectedEmployeObj.idEmploye) !== String(value)) {
        // Sinon charger les détails de l'employé spécifique par son ID depuis le serveur
        employeService
          .getEmployeFicheDetail(value)
          .then((empData) => {
            if (empData) setSelectedEmployeObj(empData);
          })
          .catch(() => null);
      }
    } else {
      setSelectedEmployeObj(null);
    }
  }, [value, listEmployes, selectedEmployeObj]);

  // Fermeture du dropdown au clic extérieur
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setShowEmpDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelect = (emp) => {
    setSelectedEmployeObj(emp);
    setShowEmpDropdown(false);
    setEmpSearchQuery('');
    if (onChange) {
      onChange(emp ? emp.idEmploye : '', emp);
    }
  };

  const handleClear = () => {
    setSelectedEmployeObj(null);
    setEmpSearchQuery('');
    if (onChange) {
      onChange('', null);
    }
  };

  // Filtrage local complémentaire si employesProp a été fourni
  const displayEmployes = employesProp
    ? listEmployes.filter((emp) => {
        const query = empSearchQuery.toLowerCase().trim();
        if (!query) return true;
        const fullName = `${emp.nom || ''} ${emp.prenom || ''}`.toLowerCase();
        const matricule = (emp.matricule || '').toLowerCase();
        return fullName.includes(query) || matricule.includes(query);
      })
    : listEmployes;

  return (
    <div className={`space-y-1.5 relative ${className}`} ref={dropdownRef}>
      {label && (
        <label className="block text-sm font-semibold text-[var(--color-fg)]">
          {label} {required && <span className="text-danger-500">*</span>}
        </label>
      )}

      {selectedEmployeObj ? (
        /* Carte récapitulative de l'employé sélectionné */
        <div className="flex items-center justify-between p-3 border-2 border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5 text-sm font-sans">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-[var(--color-brand-500)] text-white flex items-center justify-center font-bold text-xs shrink-0">
              {(selectedEmployeObj.prenom?.[0] || selectedEmployeObj.nom?.[0] || 'E').toUpperCase()}
            </div>
            <div>
              <div className="font-bold text-[var(--color-fg)]">
                {selectedEmployeObj.nom} {selectedEmployeObj.prenom}
              </div>
              <div className="text-xs text-[var(--color-fg-muted)]">
                {selectedEmployeObj.matricule
                  ? `Matricule : ${selectedEmployeObj.matricule}`
                  : `ID : #${selectedEmployeObj.idEmploye}`}
              </div>
            </div>
          </div>
          {!disabled && (
            <button
              type="button"
              onClick={handleClear}
              className="inline-flex items-center gap-1 text-xs text-danger-500 hover:text-danger-700 font-semibold px-2 py-1 border border-danger-500/30 hover:bg-danger-500/10 cursor-pointer"
            >
              <X className="w-3.5 h-3.5" /> Changer
            </button>
          )}
        </div>
      ) : (
        /* Champ de recherche avec autocomplétion */
        <div className="relative">
          <div className="relative flex items-center">
            <Search className="w-4 h-4 absolute left-3 text-[var(--color-fg-muted)] pointer-events-none" />
            <input
              type="text"
              disabled={disabled}
              value={empSearchQuery}
              onChange={(e) => {
                setEmpSearchQuery(e.target.value);
                setShowEmpDropdown(true);
              }}
              onFocus={() => !disabled && setShowEmpDropdown(true)}
              placeholder={placeholder}
              aria-invalid={!!error}
              className={`w-full pl-9 pr-4 py-2.5 bg-[var(--color-bg-subtle)] border rounded-none text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] focus:outline-none text-sm ${
                error
                  ? 'border-danger-500 focus:border-danger-500'
                  : 'border-[var(--color-border-subtle)] focus:border-[var(--color-brand-500)]'
              }`}
            />
          </div>

          {/* Liste déroulante des résultats */}
          {showEmpDropdown && !disabled && (
            <div className="absolute left-0 right-0 top-full mt-1 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xl z-50 max-h-56 overflow-y-auto font-sans">
              {loadingList ? (
                <div className="p-3 text-xs text-[var(--color-fg-muted)] text-center">
                  Chargement des employés…
                </div>
              ) : displayEmployes.length === 0 ? (
                <div className="p-3 text-xs text-[var(--color-fg-muted)] text-center">
                  Aucun employé trouvé pour "{empSearchQuery}"
                </div>
              ) : (
                displayEmployes.slice(0, 20).map((emp) => (
                  <button
                    key={emp.idEmploye}
                    type="button"
                    onClick={() => handleSelect(emp)}
                    className="w-full text-left px-4 py-2.5 hover:bg-[var(--color-brand-500)]/10 flex items-center justify-between border-b border-[var(--color-border-subtle)] last:border-0 transition-colors cursor-pointer"
                  >
                    <div className="flex items-center gap-2.5">
                      <User className="w-4 h-4 text-[var(--color-brand-500)] shrink-0" />
                      <span className="font-semibold text-[var(--color-fg)]">
                        {emp.nom} {emp.prenom}
                      </span>
                    </div>
                    {emp.matricule && (
                      <span className="text-xs text-[var(--color-fg-muted)] font-mono bg-[var(--color-bg-subtle)] px-2 py-0.5 border border-[var(--color-border-subtle)]">
                        {emp.matricule}
                      </span>
                    )}
                  </button>
                ))
              )}
            </div>
          )}
        </div>
      )}

      {error && (
        <p className="text-xs text-danger-500 flex items-center gap-1">
          <AlertTriangle className="w-3.5 h-3.5 shrink-0" /> {error}
        </p>
      )}
    </div>
  );
}
