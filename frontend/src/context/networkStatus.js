import { createContext, useContext } from 'react';

export const NetworkStatusContext = createContext({
    online: true,
    serverReachable: true,
    isChecking: false,
    lastCheckedAt: null,
    checkConnectivity: async () => true,
});

export const useNetworkStatus = () => useContext(NetworkStatusContext);
