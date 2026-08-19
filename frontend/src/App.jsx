
import Router from "./routes/Router";
import { ToastProvider } from "@/components/ui/feedback/Toast";
import { HelmetProvider } from 'react-helmet-async';

function App() {
    return (
        <HelmetProvider>
            <ToastProvider>
                <div className="min-h-screen bg-[var(--color-bg)] text-[var(--color-fg)] antialiased">
                    <Router />
                </div>
            </ToastProvider>
        </HelmetProvider>
    );
}

export default App;