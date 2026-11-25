import {createServer, request} from 'node:http';
import {createReadStream, existsSync, statSync} from 'node:fs';
import {resolve, extname, sep} from 'node:path';
import {loadEnvFile} from 'node:process';

if (existsSync('.env')) loadEnvFile('.env');
const root = resolve(process.argv[2] || 'public');
const origin = new URL(process.env.API_ORIGIN || 'http://127.0.0.1:3000');
const port = Number(process.env.PORT || 5173);
const mime = {'.html':'text/html', '.js':'text/javascript', '.css':'text/css', '.png':'image/png', '.webmanifest':'application/manifest+json'};

createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname.startsWith('/api/')) {
    const headers = {...req.headers, host:origin.host};
    const upstream = request(new URL(url.pathname + url.search, origin.origin), {method:req.method, headers}, response => {
      res.writeHead(response.statusCode, response.headers);
      response.pipe(res);
    });
    upstream.on('error', () => {
      if (!res.headersSent) res.writeHead(502, {'Content-Type':'application/json'});
      res.end(JSON.stringify({error:"L’API RépareCampus est inaccessible."}));
    });
    res.on('close', () => upstream.destroy());
    req.pipe(upstream);
    return;
  }
  let file;
  try { file = resolve(root, '.' + decodeURIComponent(url.pathname === '/' ? '/index.html' : url.pathname)); }
  catch { res.writeHead(400).end(); return; }
  if (!file.startsWith(root + sep) || !existsSync(file) || !statSync(file).isFile()) {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, {'Content-Type':mime[extname(file)] || 'application/octet-stream', 'Cache-Control':'no-cache'});
  createReadStream(file).pipe(res);
}).listen(port, process.env.HOST || '127.0.0.1', () => console.log('RépareCampus · http://localhost:' + port));
