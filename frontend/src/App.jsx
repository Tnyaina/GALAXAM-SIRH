import { useEffect } from 'react';
import Router from "./routes/Router";
import { ToastProvider } from "@/components/ui/feedback/Toast";

function App() {
    // useEffect(() => {
    //     // Empêche le scroll (trackpad Mac / molette) de modifier la valeur des inputs de type number
    //     const handleWheel = (e) => {
    //         if (document.activeElement && document.activeElement.tagName === 'INPUT' && document.activeElement.type === 'number') {
    //             document.activeElement.blur();
    //         }
    //     };

    //     window.addEventListener('wheel', handleWheel, { passive: true });
    //     return () => window.removeEventListener('wheel', handleWheel);
    // }, []);

    return (
        <ToastProvider>
            <div className="min-h-screen bg-[var(--color-bg)] text-[var(--color-fg)] antialiased">
                <Router />
            </div>
        </ToastProvider>
    );
}

export default App;