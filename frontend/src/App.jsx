import Router from "./routes/Router";
import { ToastProvider } from "@/components/ui/Toast"; // Import du Provider

function App() {
    return (
        <ToastProvider>
            <div className="min-h-screen bg-[var(--color-bg)] text-[var(--color-fg)] antialiased">
                <Router />
            </div>
        </ToastProvider>
    );
}

export default App;