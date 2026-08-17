import { useState, useEffect } from 'react';

/**
 * useDebounce
 * Retarde la mise à jour d'une valeur jusqu'à ce que l'utilisateur
 * ait cessé de taper pendant `delay` ms.
 *
 * Conforme runtime.md §4 — évite les race conditions sur les requêtes
 * de recherche en ne déclenchant le fetch qu'après stabilisation du terme.
 *
 * @param {*}      value  - Valeur à debouncer (ex : terme de recherche)
 * @param {number} delay  - Délai en ms (défaut : 350)
 * @returns {*} Valeur debouncée
 */
export function useDebounce(value, delay = 350) {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedValue(value);
    }, delay);

    return () => clearTimeout(timer);
  }, [value, delay]);

  return debouncedValue;
}

export default useDebounce;
