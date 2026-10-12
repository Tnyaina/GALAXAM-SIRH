import { describe, expect, it } from 'vitest';
import { formatDocumentExtensions, getAllowedDocumentExtensions, validateDocumentFile } from '../documentUploadRules';

describe('document upload rules', () => {
    it('normalizes the configured extensions for the file picker', () => {
        const type = { extensionAutorisee: '.PDF, jpg,PDF' };
        expect(getAllowedDocumentExtensions(type)).toEqual(['pdf', 'jpg']);
        expect(formatDocumentExtensions(type)).toBe('.PDF, .JPG');
    });

    it('checks extension and configured size before upload', () => {
        const type = { extensionAutorisee: 'pdf', tailleMaxMo: 1 };
        expect(validateDocumentFile(new File(['ok'], 'scan.pdf'), type)).toBe('');
        expect(validateDocumentFile(new File(['bad'], 'scan.png'), type)).toContain('Format non autorisé');
        expect(validateDocumentFile(new File([new Uint8Array(1024 * 1024 + 1)], 'large.pdf'), type)).toContain('taille maximale');
    });
});
