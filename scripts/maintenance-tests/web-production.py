#!/usr/bin/env python3
"""Recette destructive UNIQUEMENT sur le projet jetable galaxam-web-audit.
Usage: python3 scripts/maintenance-tests/web-production.py
La stack doit etre demarree avec les variables de la recette dans l'environnement.
"""
import http.client
import json
import pathlib
import shutil
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[2]
compose = ['docker', 'compose', '-f', str(root / 'docker-compose.web.yml')]
def run(args, ok=True):
    p = subprocess.run(args, text=True, capture_output=True)
    if ok and p.returncode:
        raise AssertionError(p.stderr[-3000:] + p.stdout[-3000:])
    return p
config = json.loads(run(compose + ['config', '--format', 'json']).stdout)
assert config['name'] == 'galaxam-web-audit', 'Refus de toucher une autre installation'
assert config['volumes']['pg_data']['name'] == 'galaxam_web_audit_pg'
pg = run(compose + ['ps', '-q', 'postgres']).stdout.strip()
backend = run(compose + ['ps', '-q', 'backend']).stdout.strip()
port = int(config['services']['frontend']['ports'][0]['published'])
def sql(query):
    return run(['docker', 'exec', pg, 'psql', '-X', '-U', 'galaxam_admin', '-d', 'sirh_galaxam', '-At', '-v', 'ON_ERROR_STOP=1', '-c', query]).stdout.strip()
def request(method, path, payload=None, headers=None):
    conn = http.client.HTTPConnection('127.0.0.1', port, timeout=20)
    h = {'Content-Type': 'application/json', **(headers or {})}
    conn.request(method, path, json.dumps(payload) if payload else None, h)
    r = conn.getresponse()
    data = r.read()
    result = r.status, dict(r.getheaders()), data
    conn.close()
    return result
assert request('GET', '/api/auth/ping')[0] == 200
origin = f'http://localhost:{port}'
status, headers, _ = request('OPTIONS', '/api/auth/login', headers={'Origin':origin,'Access-Control-Request-Method':'POST'})
assert status == 200 and headers.get('Access-Control-Allow-Origin') == origin
assert request('OPTIONS','/api/auth/login',headers={'Origin':'https://forbidden.example','Access-Control-Request-Method':'POST'})[0] == 403
credentials = {'email': config['services']['backend']['environment']['GALAXAM_DEFAULT_ADMIN_EMAIL'], 'motDePasse': config['services']['backend']['environment']['GALAXAM_DEFAULT_ADMIN_PASSWORD']}
# DTO du serveur utilise email / motDePasse.
status, headers, body = request('POST','/api/auth/login',credentials,{'Origin':origin})
assert status == 200, (status,body)
cookie = headers['Set-Cookie']
assert 'HttpOnly' in cookie and 'SameSite=Lax' in cookie and 'Secure' not in cookie
assert request('POST','/api/auth/refresh',headers={'Origin':origin,'Cookie':cookie.split(';')[0]})[0] == 200
status, headers, body = request('POST','/api/auth/login',credentials,{'Origin':origin,'X-Forwarded-Proto':'https'})
assert status == 200, (status,body)
assert 'Secure' in headers['Set-Cookie'] and 'SameSite=None' in headers['Set-Cookie']
print('HTTP, CORS, login/refresh et indicateur TLS : OK')
sql("CREATE TABLE public.maintenance_probe(id integer PRIMARY KEY, value text); INSERT INTO maintenance_probe VALUES(1,'preserved')")
run(['docker','exec',backend,'sh','-c','mkdir -p /app/uploads/.hidden; printf document > /app/uploads/.hidden/probe.txt'])
run(compose + ['stop','frontend','backend'])
try:
    with tempfile.TemporaryDirectory(prefix='galaxam-migrations-') as directory:
        d=pathlib.Path(directory)
        files = sorted((root/'docker/migrations').glob('[0-9][0-9][0-9][0-9]_*.sql'))
        for f in files:
            shutil.copy(f,d)
        number = max(int(f.name[:4]) for f in files) + 1
        def migrate(ok=True):
            return run(compose+['run','--rm','--no-deps','-v',f'{d}:/migrations:ro','migrate'],ok)
        migration=d/f'{number:04d}_maintenance_probe.sql'
        migration.write_text('ALTER TABLE public.maintenance_probe ADD COLUMN extra text;\n')
        migrate(); migrate()
        assert sql('SELECT value FROM maintenance_probe WHERE id=1') == 'preserved'
        migration.write_text('ALTER TABLE public.maintenance_probe ADD COLUMN altered text;\n')
        assert migrate(False).returncode != 0
        migration.write_text('ALTER TABLE public.maintenance_probe ADD COLUMN extra text;\n')
        bad=d/f'{number+1:04d}_maintenance_failure.sql'
        bad.write_text('ALTER TABLE maintenance_probe ADD COLUMN must_rollback text; SELECT no_such_function();\n')
        assert migrate(False).returncode != 0
        assert sql("SELECT count(*) FROM information_schema.columns WHERE table_name='maintenance_probe' AND column_name='must_rollback'") == '0'
        bad.unlink()
        # Export/restauration sur la base jetable, avec objet cree apres sauvegarde.
        run(['docker','exec',pg,'pg_dump','-U','galaxam_admin','-d','sirh_galaxam','-Fc','-f','/tmp/audit.dump'])
        sql("UPDATE maintenance_probe SET value='modified'; CREATE TABLE should_disappear(id int)")
        run(['docker','exec',pg,'dropdb','-U','galaxam_admin','--force','sirh_galaxam'])
        run(['docker','exec',pg,'createdb','-U','galaxam_admin','sirh_galaxam'])
        run(['docker','exec',pg,'pg_restore','-U','galaxam_admin','-d','sirh_galaxam','--single-transaction','--no-owner','--exit-on-error','/tmp/audit.dump'])
        assert sql('SELECT value FROM maintenance_probe') == 'preserved'
        assert sql("SELECT count(*) FROM information_schema.tables WHERE table_name='should_disappear'") == '0'
        print('Migrations idempotentes, empreinte, rollback transactionnel et restauration PostgreSQL : OK')
finally:
    # Pas de nouvelles migrations : le test a ajoute sa propre version de schema.
    run(compose+['up','-d','--no-deps','backend','frontend'])
