import { useEffect } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { toPointageBatchPayload } from '@/features/attendance/utils/pointageBatchPayload';
import { applyPointagePreview, pointagePreviewSignature } from '@/features/attendance/utils/pointageRow';

/** Prévisualisation commune aux grilles par jour et par période, protégée contre les réponses périmées. */
export function usePointageBatchPreview(rows, setRows, keyOf, decoratePreview, delay = 250) {
    useEffect(() => {
        const pending = rows.filter(row => row._previewPending);
        if (!pending.length) return undefined;
        const signatures = new Map(pending.map(row => [keyOf(row), pointagePreviewSignature(row)]));
        let active = true;
        const timer = setTimeout(async () => {
            try {
                const results = await attendanceService.daily.previewBatch(pending.map(toPointageBatchPayload));
                if (!active) return;
                const byKey = new Map(pending.map((row, index) => [keyOf(row), results?.[index]]));
                setRows(current => current.map(row => {
                    const key = keyOf(row);
                    if (!row._previewPending || pointagePreviewSignature(row) !== signatures.get(key)) return row;
                    const result = byKey.get(key);
                    if (!result || result.error || !result.preview) {
                        const message = result?.error || 'Prévisualisation indisponible.';
                        return { ...row, _previewPending: false, _previewError: message,
                            hasValidationError: true, validationMessages: [message] };
                    }
                    const calculated = applyPointagePreview(row, result.preview);
                    return decoratePreview ? decoratePreview(calculated, result.preview) : calculated;
                }));
            } catch (error) {
                if (!active) return;
                const message = getErrorMessage(error, 'Prévisualisation indisponible.');
                setRows(current => current.map(row => row._previewPending
                    && pointagePreviewSignature(row) === signatures.get(keyOf(row))
                    ? { ...row, _previewPending: false, _previewError: message,
                        hasValidationError: true, validationMessages: [message] } : row));
            }
        }, delay);
        return () => { active = false; clearTimeout(timer); };
    }, [rows, setRows, keyOf, decoratePreview, delay]);
}
