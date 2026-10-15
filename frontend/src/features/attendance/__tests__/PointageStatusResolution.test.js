import { describe, expect, it } from 'vitest';
import { getAttendanceStatus } from '../utils/attendanceStatus';

describe('présentation du statut renvoyé par le serveur', () => {
    it('conserve les libellés distincts du samedi et du dimanche travaillés', () => {
        expect(getAttendanceStatus('SAMEDI_TRAVAILLE').label).toBe('Samedi travaillé');
        expect(getAttendanceStatus('DIMANCHE_TRAVAILLE').label).toBe('Dimanche travaillé');
    });
});
