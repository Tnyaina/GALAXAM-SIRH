export const IDLE_TIMEOUT_MS = Number(import.meta.env?.VITE_SESSION_IDLE_TIMEOUT_SECONDS || 900) * 1000;
export const IDLE_ACTIVITY_STORAGE_KEY = 'galaxam-last-user-activity';

export const isIdleExpired = (lastActivityAt, now, timeoutMs = IDLE_TIMEOUT_MS) =>
    !Number.isFinite(lastActivityAt) || now - lastActivityAt >= timeoutMs;

// Deliberately exclude pointer movement, focus, network traffic, and background timers.
export const USER_ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'touchstart', 'click', 'input', 'change', 'submit'];
