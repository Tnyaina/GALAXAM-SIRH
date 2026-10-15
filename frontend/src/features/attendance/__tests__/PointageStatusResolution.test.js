import { describe, expect, it } from 'vitest';
import { resolveAttendanceStatus } from '../utils/attendanceStatus';

describe('qualification affichée dans la grille par période', () => {
    it('distingue le samedi travaillé du dimanche travaillé', () => {
        expect(resolveAttendanceStatus({ hasHours: true, weekend: true, sunday: false })).toBe('SAMEDI_TRAVAILLE');
        expect(resolveAttendanceStatus({ hasHours: true, weekend: true, sunday: true })).toBe('DIMANCHE_TRAVAILLE');
    });
});
