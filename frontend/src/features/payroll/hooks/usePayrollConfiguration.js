import { useEffect, useRef, useState, useCallback } from 'react';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';

// Un cache borné pour les deux catalogues de référence, invalidé après mutation.
const cache = new Map();
export default function usePayrollConfiguration(params) {
    const key = JSON.stringify(params);
    const [state, setState] = useState({ data: null, loading: true, refreshing: false, error: '' });
    const [revision, setRevision] = useState(0);
    const request = useRef(0);
    const refresh = useCallback(() => { cache.clear(); setRevision(r => r + 1); }, []);
    useEffect(() => {
        const controller = new AbortController();
        const id = ++request.current;
        const cached = cache.get(key);
        if (cached) setState({ data: cached, loading: false, refreshing: true, error: '' });
        else setState(previous => ({ ...previous, loading: !previous.data, refreshing: true, error: '' }));
        paieService.getConfigurationCatalogue(JSON.parse(key), controller.signal).then(data => {
            if (controller.signal.aborted || id !== request.current) return;
            if (cache.size >= 30) cache.delete(cache.keys().next().value);
            cache.set(key, data);
            setState({ data, loading: false, refreshing: false, error: '' });
        }).catch(error => {
            if (controller.signal.aborted || id !== request.current) return;
            setState(previous => ({ ...previous, loading: false, refreshing: false,
                error: getErrorMessage(error, 'Le catalogue n’a pas pu être chargé. Réessayez avec Actualiser.') }));
        });
        return () => controller.abort();
    }, [key, revision]);
    return { ...state, refresh };
}
