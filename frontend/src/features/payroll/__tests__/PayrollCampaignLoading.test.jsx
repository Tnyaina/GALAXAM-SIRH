// @vitest-environment jsdom
import { act, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { usePayrollCampaign } from '../hooks/usePayrollCampaign';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';

vi.mock('@/features/attendance/api/attendanceApi', () => ({
    periodePaieService: { getPeriodOptions: vi.fn(), getContext: vi.fn() },
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;
let latest;

function Probe() {
    const campaign = usePayrollCampaign(41);
    useEffect(() => { latest = campaign; }, [campaign]);
    return <span>{campaign.context?.selectedPeriod?.code || 'chargement'}</span>;
}

beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    periodePaieService.getPeriodOptions.mockResolvedValue([{ idPeriodePaie: 41, code: 'P41' }]);
    periodePaieService.getContext.mockResolvedValue({ selectedPeriod: { id: 41, code: 'P41' } });
});

afterEach(() => {
    act(() => root.unmount());
    host.remove();
    vi.clearAllMocks();
});

it('charge les options légères puis actualise seulement le contexte', async () => {
    await act(async () => root.render(<Probe />));
    expect(host.textContent).toBe('P41');
    expect(periodePaieService.getPeriodOptions).toHaveBeenCalledTimes(1);
    expect(periodePaieService.getPeriodOptions).toHaveBeenCalledWith({ includePayrollStatus: true, force: false });
    await act(async () => { await latest.refreshSilent(); });
    expect(periodePaieService.getContext).toHaveBeenCalledTimes(2);
    expect(periodePaieService.getPeriodOptions).toHaveBeenCalledTimes(1);
    await act(async () => { await latest.refreshSilent(41, { reloadPeriods: true }); });
    expect(periodePaieService.getPeriodOptions).toHaveBeenCalledWith({ includePayrollStatus: true, force: true });
});
