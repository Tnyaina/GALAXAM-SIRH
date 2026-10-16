/** Cache mémoire court pour lectures API : dédoublonne les requêtes simultanées. */
export function createReadCache(ttlMs) {
    const pending = new Map();
    const values = new Map();
    let revision = 0;

    return {
        invalidate() {
            revision += 1;
            pending.clear();
            values.clear();
        },
        get(key, load) {
            const cached = values.get(key);
            if (cached?.expiresAt > Date.now()) return Promise.resolve(cached.data);
            if (!pending.has(key)) {
                const currentRevision = revision;
                const request = load().then(data => {
                    if (currentRevision === revision) values.set(key, { data, expiresAt: Date.now() + ttlMs });
                    return data;
                }).finally(() => {
                    if (pending.get(key) === request) pending.delete(key);
                });
                pending.set(key, request);
            }
            return pending.get(key);
        }
    };
}

export const sessionCacheKey = (key, token) => `${token || 'anonymous'}:${key}`;

export function dispatchCacheEvent(name) {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event(name));
}
