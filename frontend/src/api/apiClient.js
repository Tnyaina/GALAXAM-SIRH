import axios from 'axios';

// URL de ton API Spring Boot (définie dans ton .env)
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/';

const apiClient = axios.create({
    baseURL: BASE_URL,
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    },
});

export default apiClient;