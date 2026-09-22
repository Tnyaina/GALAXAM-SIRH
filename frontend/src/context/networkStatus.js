import { createContext, useContext } from 'react';

export const NetworkStatusContext = createContext({ online: true, serverReachable: true });

export const useNetworkStatus = () => useContext(NetworkStatusContext);
