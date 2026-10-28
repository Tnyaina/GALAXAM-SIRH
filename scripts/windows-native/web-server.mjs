import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scripts = path.dirname(fileURLToPath(import.meta.url));
const packagedWeb = path.resolve(scripts, '../app/web');
const root = fs.existsSync(path.join(packagedWeb, 'index.html')) ? packagedWeb : path.resolve(scripts, '../../frontend/dist');
const port = Number(process.env.GALAXAM_WEB_PORT || 18088);
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.json': 'application/json; charset=utf-8' };
if (!fs.existsSync(path.join(root, 'index.html'))) throw new Error(`Frontend non compile : ${root}`);

http.createServer((request, response) => {
  if (request.url?.startsWith('/api/')) {
    const upstream = http.request({ hostname: '127.0.0.1', port: 8080, path: request.url, method: request.method, headers: { ...request.headers, host: '127.0.0.1:8080' } }, backend => {
      response.writeHead(backend.statusCode || 502, backend.headers);
      backend.pipe(response);
    });
    upstream.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end('Backend indisponible'); });
    request.pipe(upstream);
    return;
  }
  let pathname;
  try { pathname = decodeURIComponent(new URL(request.url || '/', 'http://localhost').pathname); }
  catch { response.writeHead(400); response.end(); return; }
  const file = path.resolve(root, '.' + pathname);
  if (file !== root && !file.startsWith(root + path.sep)) { response.writeHead(403); response.end(); return; }
  fs.stat(file, (error, stat) => {
    const target = !error && stat.isFile() ? file : path.join(root, 'index.html');
    const stream = fs.createReadStream(target);
    stream.on('error', () => { if (!response.headersSent) response.writeHead(500); response.end(); });
    response.setHeader('Content-Type', types[path.extname(target)] || 'application/octet-stream');
    response.setHeader('X-Content-Type-Options', 'nosniff');
    if (target.endsWith('index.html')) response.setHeader('Cache-Control', 'no-store');
    stream.pipe(response);
  });
}).listen(port, '127.0.0.1', () => console.log(`GALAXAM web: http://127.0.0.1:${port}`));
