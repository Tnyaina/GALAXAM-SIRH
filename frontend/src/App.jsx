
import Router from "./routes/Router";
import { ToastProvider } from "@/components/ui/feedback/Toast";
import { HelmetProvider } from 'react-helmet-async';
import { NetworkStatusProvider } from '@/context/NetworkStatusContext';
import NetworkStatusBanner from '@/components/system/NetworkStatusBanner';

function App() {
    return (
        <HelmetProvider>
            <NetworkStatusProvider>
                <ToastProvider>
                    <div className="min-h-screen bg-[var(--color-bg)] text-[var(--color-fg)] antialiased">
                        <Router />
                        <NetworkStatusBanner />
                    </div>
                </ToastProvider>
            </NetworkStatusProvider>
        </HelmetProvider>
    );
}

export default App;
