
import Router from "./routes/Router";
import { ToastProvider } from "@/components/ui/feedback/Toast";
import { HelmetProvider } from 'react-helmet-async';
import { NetworkStatusProvider } from '@/context/NetworkStatusContext';
import NetworkStatusBanner from '@/components/system/NetworkStatusBanner';
import ErrorBoundary from '@/components/system/ErrorBoundary';

function App() {
    return (
        <HelmetProvider>
            <NetworkStatusProvider>
                <ToastProvider>
                    <ErrorBoundary>
                        <div className="min-h-screen bg-[var(--color-bg)] text-[var(--color-fg)] antialiased">
                            <Router />
                            <NetworkStatusBanner />
                        </div>
                    </ErrorBoundary>
                </ToastProvider>
            </NetworkStatusProvider>
        </HelmetProvider>
    );
}

export default App;
