import { it, expect, vi, beforeEach } from 'vitest';
import apiClient from '@/api/apiClient';
import { attendanceService } from '../api/attendanceApi';
vi.mock('@/api/apiClient', () => ({ default: { get: vi.fn() } }));
beforeEach(() => vi.clearAllMocks());
it('récupère le PDF serveur avec le nom de fichier fourni et les filtres sélectionnés', async () => {
 const blob = new Blob(['PDF']);
 apiClient.get.mockResolvedValue({ data: blob, headers: { 'content-disposition': 'attachment; filename="Pointage_Agent_Septembre.pdf"' } });
 const result = await attendanceService.exportPdf(7, { period: 'periode', idPeriode: 9 });
 expect(apiClient.get).toHaveBeenCalledWith('/pointages/employes/7/export/pdf', { params: { period: 'periode', idPeriode: 9 }, responseType: 'blob' });
 expect(result).toEqual({ blob, filename: 'Pointage_Agent_Septembre.pdf' });
});
it('récupère le modèle Excel rempli sans calcul client', async () => {
 const blob = new Blob(['Excel']);
 apiClient.get.mockResolvedValue({ data: blob, headers: { 'content-disposition': 'attachment; filename="Pointage.xlsx"' } });
 expect(await attendanceService.exportExcel(7, { date: '2026-09-01', period: 'mois' })).toEqual({ blob, filename: 'Pointage.xlsx' });
});
it('propage une erreur backend sans produire un fichier de remplacement', async () => {
 const error = new Error('Snapshot indisponible');apiClient.get.mockRejectedValue(error);
 await expect(attendanceService.exportPdf(7)).rejects.toBe(error);
});
