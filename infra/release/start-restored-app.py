#!/usr/bin/env python3
"""One-shot, server-local read-only application rehearsal on the migrated restore.

Never targets production containers, credentials, schemas or volumes. Failure stops
only containers created by this invocation and preserves every file/container/volume.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import socket
import stat
import subprocess
import sys
import time
from urllib.parse import urlsplit
from urllib.request import build_opener, ProxyHandler, HTTPRedirectHandler

DOCKER = ['/usr/bin/docker', '--host', 'unix:///var/run/docker.sock']
REHEARSAL = Path('/srv/trekkey-backups/sui-release-20260908-rehearsal-v2')
OUTPUT = Path('/srv/trekkey-backups/sui-release-20260908-restored-app-v1')
JAR = Path('/opt/trekkey/sui-release/restored-v7-app.jar')
JAR_SHA = 'b59819b78bf87d7681c7ae65fb117433eea0774fd9032e0b539db37279d971f9'
VOLUME = 'trekkey-sui-release-20260908-rehearsal-v2-data'
OLD_DB = 'trekkey-sui-release-20260908-rehearsal-v2'
DATABASE = 'trekkey-sui-release-20260908-restored-mysql-v1'
BACKEND = 'trekkey-sui-release-20260908-restored-backend-v1'
NETWORK = 'trekkey-sui-release-20260908-restored-net-v1'
JAVA_IMAGE = 'eclipse-temurin:21.0.12_8-jre-jammy'
TOKEN = Path('/srv/trekkey-sui-testnet/secrets/gateway/gateway-token')
PUBLIC_ENV = Path('/etc/trekkey/sui-runtime.env')
UPLOADS = Path('/var/lib/trekkey/uploads')
STAGE = 'ARGUMENTS'


def require(condition):
    if not condition:
        raise RuntimeError('RESTORED_APP_CONTRACT_FAILED')


def run(args, *, check=True, timeout=30):
    # Never echo argv, captured output or exception text: SQL commands may read
    # server-local credentials, and a database error could contain record values.
    return subprocess.run(DOCKER + args, stdin=subprocess.DEVNULL,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=timeout, check=check,
                          env={'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LC_ALL': 'C'})


def inspect(kind, name):
    return json.loads(run([kind, 'inspect', name]).stdout)[0]


def guard_path(path, *, file=False, owners=(0,), mode=None):
    require(path.is_absolute() and path.resolve() == path)
    for ancestor in path.parents:
        info = ancestor.lstat()
        require(stat.S_ISDIR(info.st_mode) and info.st_uid in (0, 10001)
                and not stat.S_IMODE(info.st_mode) & 0o022)
    info = path.lstat()
    require(info.st_uid in owners and not stat.S_IMODE(info.st_mode) & 0o022)
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 if file else stat.S_ISDIR(info.st_mode))
    require(mode is None or stat.S_IMODE(info.st_mode) == mode)


def private_read(path, *, owners=(0,)):
    guard_path(path, file=True, owners=owners, mode=0o600)
    require(path.stat().st_size < 65536)
    return path.read_text().strip()


def write_new(path, value, uid=0, mode=0o600):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, mode)
    try:
        os.fchown(fd, uid, uid)
        os.fchmod(fd, mode)  # The private process umask must not make the JAR unreadable by UID 10001.
        with os.fdopen(fd, 'wb', closefd=False) as stream:
            stream.write(value if isinstance(value, bytes) else value.encode())
            stream.flush()
            os.fsync(fd)
    finally:
        os.close(fd)


def runtime_values(raw):
    values = {}
    expected = {'SUI_NETWORK', 'SUI_CHAIN_IDENTIFIER', 'SUI_PACKAGE_ID', 'SUI_REGISTRY_ID',
                'BLOCKCHAIN_ANCHORING_MODE', 'BLOCKCHAIN_WORKER_ENABLED',
                'LEGACY_KAIA_READ_ENABLED', 'LEGACY_KAIA_CHAIN_ID', 'LEGACY_KAIA_RPC_URL',
                'LEGACY_KAIA_CONTRACT_ADDRESS', 'LEGACY_KAIA_RUNTIME_CODE_HASH',
                'LEGACY_KAIA_CONTRACT_VERSION'}
    for line in raw.splitlines():
        require('=' in line and '\r' not in line)
        key, value = line.split('=', 1)
        require(key in expected and key not in values and value and not re.search(r'[\s$`]', value))
        values[key] = value
    require(set(values) == expected and values['SUI_NETWORK'] == 'testnet'
            and values['LEGACY_KAIA_READ_ENABLED'] == 'true' and values['LEGACY_KAIA_CHAIN_ID'] == '1001')
    require(re.fullmatch(r'[0-9a-f]{8}', values['SUI_CHAIN_IDENTIFIER']))
    for key in ('SUI_PACKAGE_ID', 'SUI_REGISTRY_ID', 'LEGACY_KAIA_RUNTIME_CODE_HASH'):
        require(re.fullmatch(r'0x[0-9a-f]{64}', values[key]) and values[key] != '0x' + '0' * 64)
    require(re.fullmatch(r'0x[0-9a-f]{40}', values['LEGACY_KAIA_CONTRACT_ADDRESS'])
            and values['LEGACY_KAIA_CONTRACT_ADDRESS'] != '0x' + '0' * 40)
    require(re.fullmatch(r'[A-Za-z0-9_.-]{1,100}', values['LEGACY_KAIA_CONTRACT_VERSION']))
    uri = urlsplit(values['LEGACY_KAIA_RPC_URL'])
    require(uri.scheme == 'https' and uri.hostname and not uri.username and not uri.password
            and not uri.query and not uri.fragment)
    # Only these two public production settings are deliberately overridden.
    values.update(BLOCKCHAIN_ANCHORING_MODE='READ_ONLY', BLOCKCHAIN_WORKER_ENABLED='false')
    return values


def mysql_command():
    return ['create', '--name', DATABASE, '--pull=never', '--network', NETWORK,
            '--publish', '127.0.0.1:13307:3306', '--memory', '1g', '--memory-swap', '1g',
            '--cpus', '1', '--pids-limit', '256', '--restart', 'no', '--read-only',
            '--security-opt', 'no-new-privileges:true', '--tmpfs', '/tmp:rw,noexec,nosuid,size=64m',
            '--tmpfs', '/var/run/mysqld:rw,nosuid,size=16m', '--log-opt', 'max-size=2m',
            '--log-opt', 'max-file=1', '--mount', f'type=volume,src={VOLUME},dst=/var/lib/mysql',
            '--mount', f'type=bind,src={REHEARSAL}/mysql-root-password,dst=/run/secrets/mysql-root-password,readonly',
            '--env', 'MYSQL_ROOT_PASSWORD_FILE=/run/secrets/mysql-root-password', '--env', 'TZ=UTC',
            'mysql:8.4', '--local-infile=0', '--secure-file-priv=NULL', '--event-scheduler=OFF',
            '--read-only=ON', '--super-read-only=ON', '--mysqlx=OFF',
            '--innodb-buffer-pool-size=256M', '--max-connections=20']


def backend_command():
    args = ['create', '--name', BACKEND, '--pull=never', '--network', 'host',
            '--user', '10001:10001', '--init', '--read-only', '--cap-drop', 'ALL',
            '--security-opt', 'no-new-privileges:true', '--memory', '1536m', '--memory-swap', '1536m',
            '--cpus', '1', '--pids-limit', '256', '--restart', 'no',
            '--tmpfs', '/tmp:rw,noexec,nosuid,size=128m,uid=10001,gid=10001,mode=1770',
            '--log-opt', 'max-size=2m', '--log-opt', 'max-file=1',
            '--env-file', str(OUTPUT / 'runtime.env')]
    for source, target in ((OUTPUT / 'app.jar', '/app/app.jar'),
                           (UPLOADS, '/uploads'),
                           (TOKEN, '/run/backend-secrets/SUI_GATEWAY_TOKEN')):
        args += ['--mount', f'type=bind,src={source},dst={target},readonly']
    for name in ('DATASOURCE_PASSWORD', 'JWT_SECRET_KEY', 'EVIDENCE_LOOKUP_HMAC_SECRET'):
        args += ['--mount', f'type=bind,src={OUTPUT}/secrets/{name},dst=/run/backend-secrets/{name},readonly']
    return args + ['--entrypoint', 'java', JAVA_IMAGE, '-XX:MaxRAMPercentage=65', '-jar', '/app/app.jar',
                   '--spring.jpa.hibernate.ddl-auto=validate', '--server.address=127.0.0.1', '--server.port=18082']


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_readiness(status, content_type, body):
    require(status == 200 and content_type.split(';', 1)[0].strip().lower() == 'application/json'
            and 0 < len(body) <= 262144)
    envelope = json.loads(body)
    require(isinstance(envelope, dict) and envelope.get('isSuccess') is True
            and envelope.get('code') == 'SUCCESS_200' and type(envelope.get('httpStatus')) is int
            and envelope['httpStatus'] == 200 and isinstance(envelope.get('data'), list))
    for organization in envelope['data']:
        require(isinstance(organization, dict) and set(organization) == {'id', 'name'}
                and type(organization['id']) is int and organization['id'] > 0
                and isinstance(organization['name'], str) and organization['name'].strip())


def main():
    global STAGE
    require(sys.argv[1:] == ['--start-approved-restored-app'] and os.geteuid() == 0)
    os.umask(0o077)
    STAGE = 'RESTORE_RESULT'
    guard_path(REHEARSAL, mode=0o700)
    require(not OUTPUT.exists() and not OUTPUT.is_symlink())
    result = json.loads(private_read(REHEARSAL / 'result.json'))
    require(result.get('status') == 'PASS' and result.get('containerStopped') is True
            and result.get('migrationAppliedToRestoreOnly') is True and result.get('immutableRecordsPreserved') is True)
    require(private_read(REHEARSAL / 'immutable-before.sha256') == private_read(REHEARSAL / 'immutable-after.sha256'))
    STAGE = 'RESTORE_VOLUME'
    old = inspect('container', OLD_DB)
    require(old['State']['Running'] is False and old['Config']['Image'] == 'mysql:8.4'
            and any(m.get('Name') == VOLUME and m['Destination'] == '/var/lib/mysql' for m in old['Mounts']))
    volume = inspect('volume', VOLUME)
    require(volume['Driver'] == 'local' and (volume.get('Labels') or {}).get('trekkey.restore-audit') == '20260908')
    require(not run(['ps', '--quiet', '--filter', f'volume={VOLUME}']).stdout.strip())
    STAGE = 'RUNTIME_NAMES'
    for kind, name in (('container', DATABASE), ('container', BACKEND), ('network', NETWORK)):
        require(run([kind, 'inspect', name], check=False).returncode != 0)
    STAGE = 'LOCAL_IMAGES'
    for image in ('mysql:8.4', JAVA_IMAGE):
        inspect('image', image)  # No pull/build occurs in this helper.
    STAGE = 'LOOPBACK_PORTS'
    for port in (13307, 18082):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.bind(('127.0.0.1', port))
    STAGE = 'PROTECTED_JAR'
    guard_path(JAR, file=True, mode=0o600)
    require(JAR.stat().st_size < 200 * 1024 * 1024)
    jar = JAR.read_bytes()
    require(hashlib.sha256(jar).hexdigest() == JAR_SHA)
    STAGE = 'UPLOAD_DIRECTORY'
    guard_path(UPLOADS, owners=(0, 10001))
    STAGE = 'GATEWAY_TOKEN'
    token = private_read(TOKEN, owners=(10001,))
    require(re.fullmatch(r'[!-~]{32,4096}', token))
    STAGE = 'RESTORE_PASSWORD'
    password = private_read(REHEARSAL / 'mysql-root-password', owners=(999,))
    require(re.fullmatch(r'[0-9a-f]{64}', password))
    STAGE = 'PUBLIC_CONFIGURATION'
    values = runtime_values(private_read(PUBLIC_ENV))
    values.update(SPRING_PROFILES_ACTIVE='sui', BLOCKCHAIN_PROVIDER='SUI',
                  SPRING_JPA_HIBERNATE_DDL_AUTO='validate', SPRING_CONFIG_IMPORT='configtree:/run/backend-secrets/',
                  DATASOURCE_URL='jdbc:mysql://127.0.0.1:13307/trekkey_restore?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC',
                  DATASOURCE_USERNAME='restore_import', CORS_ALLOWED_ORIGIN='http://127.0.0.1:18082',
                  FRONT_BASE_URL='http://127.0.0.1:18082', FILE_UPLOAD_DIR='/uploads',
                  JWT_ACCESS_EXPIRATION='900', JWT_REFRESH_EXPIRATION='900',
                  JWT_REFRESH_COOKIE_NAME='trekkey_restore_rehearsal', SUI_GATEWAY_URL='http://127.0.0.1:9187',
                  SERVER_ADDRESS='127.0.0.1', SPRINGDOC_API_DOCS_ENABLED='false',
                  SPRINGDOC_SWAGGER_UI_ENABLED='false', TZ='UTC')
    STAGE = 'EXCLUSIVE_OUTPUT'
    OUTPUT.mkdir(mode=0o700)  # Exclusive one-shot claim; never automatically removed.
    owned = []
    STAGE = 'PRIVATE_CONFIGURATION'
    try:
        write_new(OUTPUT / 'run.claim', 'Read-only restored application rehearsal. Preserve all artifacts.\n')
        (OUTPUT / 'secrets').mkdir(mode=0o700)
        write_new(OUTPUT / 'secrets' / 'DATASOURCE_PASSWORD', password, uid=10001)
        write_new(OUTPUT / 'secrets' / 'JWT_SECRET_KEY', secrets.token_hex(64), uid=10001)
        write_new(OUTPUT / 'secrets' / 'EVIDENCE_LOOKUP_HMAC_SECRET', secrets.token_hex(32), uid=10001)
        write_new(OUTPUT / 'app.jar', jar, mode=0o444)
        write_new(OUTPUT / 'runtime.env', ''.join(f'{key}={value}\n' for key, value in values.items()))
        STAGE = 'DATABASE_CREATE'
        run(['network', 'create', '--driver', 'bridge', '--opt', 'com.docker.network.bridge.enable_icc=false',
             '--label', 'trekkey.restore-http=20260908', NETWORK])
        # Recheck immediately before mounting a volume formerly attached to a stopped container.
        require(not run(['ps', '--quiet', '--filter', f'volume={VOLUME}']).stdout.strip())
        run(mysql_command())
        owned.append(DATABASE)
        db = inspect('container', DATABASE)
        require(db['HostConfig']['PortBindings'] == {'3306/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '13307'}]})
        run(['start', DATABASE])
        STAGE = 'DATABASE_READ_ONLY'
        probe = ('MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql --no-defaults '
                 '--protocol=socket --user=restore_import --batch --skip-column-names trekkey_restore '
                 '-e "SELECT @@global.read_only,@@global.super_read_only, '
                 '(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE())"')
        ready = False
        for _ in range(18):
            check = run(['exec', '--user', '0', DATABASE, 'sh', '-c', probe], check=False, timeout=2)
            if check.returncode == 0:
                require(check.stdout.strip() == b'1\t1\t48')
                ready = True
                break
            time.sleep(1)
        require(ready)
        STAGE = 'APPLICATION_CREATE'
        run(backend_command())
        owned.append(BACKEND)
        app = inspect('container', BACKEND)
        require(app['HostConfig']['NetworkMode'] == 'host' and app['Config']['User'] == '10001:10001'
                and len([m for m in app['Mounts'] if m['Type'] == 'bind']) == 6
                and all(m['RW'] is False for m in app['Mounts'] if m['Type'] == 'bind'))
        run(['start', BACKEND])
        STAGE = 'APPLICATION_READINESS'
        client = build_opener(ProxyHandler({}), NoRedirect())
        ready = False
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            require(inspect('container', BACKEND)['State']['Running'] is True)
            try:
                with client.open('http://127.0.0.1:18082/api/organizations?keyword=SYNTHETIC', timeout=2) as response:
                    validate_readiness(response.status, response.headers.get('Content-Type', ''), response.read(262145))
                    ready = True
                    break
            except OSError:
                time.sleep(min(2, max(0, deadline - time.monotonic())))
        require(ready)
        write_new(OUTPUT / 'result.json', json.dumps({
            'status': 'PASS', 'jarSha256': JAR_SHA, 'databaseReadOnly': True,
            'databaseSuperReadOnly': True, 'ddlAuto': 'validate', 'workerEnabled': False,
            'anchoringMode': 'READ_ONLY', 'apiPort': 18082, 'databasePort': 13307,
            'productionReplaced': False, 'piiPrinted': False, 'secretsPrinted': False,
            'containersLeftRunningForHttpVerification': True}) + '\n')
        print('RESTORED_APP_READY port=18082 mysql=13307 database_read_only=true ddl=validate production_changed=false')
    except BaseException:
        for container in reversed(owned):
            try:
                run(['stop', '--time', '15', container], check=False, timeout=20)
            except Exception:
                pass
        write_new(OUTPUT / 'failure.json', json.dumps({'status': 'FAIL', 'stage': STAGE, 'statePreserved': True}) + '\n')
        raise


def self_test():
    # Pure contract tests: no Docker, socket, file or secret access.
    sample = {'SUI_NETWORK': 'testnet', 'SUI_CHAIN_IDENTIFIER': '4c78adac',
              'SUI_PACKAGE_ID': '0x' + '1' * 64, 'SUI_REGISTRY_ID': '0x' + '2' * 64,
              'BLOCKCHAIN_ANCHORING_MODE': 'LOCAL_RELAYER', 'BLOCKCHAIN_WORKER_ENABLED': 'true',
              'LEGACY_KAIA_READ_ENABLED': 'true', 'LEGACY_KAIA_CHAIN_ID': '1001',
              'LEGACY_KAIA_RPC_URL': 'https://public-en-kairos.node.kaia.io',
              'LEGACY_KAIA_CONTRACT_ADDRESS': '0x' + '3' * 40,
              'LEGACY_KAIA_RUNTIME_CODE_HASH': '0x' + '4' * 64, 'LEGACY_KAIA_CONTRACT_VERSION': '1'}
    encode = lambda values: '\n'.join(f'{key}={value}' for key, value in values.items())
    parsed = runtime_values(encode(sample))
    require(parsed['BLOCKCHAIN_ANCHORING_MODE'] == 'READ_ONLY' and parsed['BLOCKCHAIN_WORKER_ENABLED'] == 'false')
    mutations = [('SUI_NETWORK', 'mainnet'), ('LEGACY_KAIA_CHAIN_ID', '8217'),
                 ('LEGACY_KAIA_RPC_URL', 'https://user:password@example.com'),
                 ('LEGACY_KAIA_RPC_URL', 'https://example.com?apiKey=secret'),
                 ('LEGACY_KAIA_RUNTIME_CODE_HASH', '0x' + '0' * 64),
                 ('SUI_PACKAGE_ID', '0x' + '1' * 40), ('UNRECOGNIZED_SECRET', 'synthetic'),
                 ('LEGACY_KAIA_CONTRACT_VERSION', '1`touch /tmp/synthetic`')]
    for key, value in mutations:
        try:
            runtime_values(encode({**sample, key: value}))
        except RuntimeError:
            continue
        raise RuntimeError('RESTORED_APP_SELF_TEST_FAILED')
    for raw in (encode(sample) + '\nSUI_NETWORK=testnet', encode({key: value for key, value in sample.items() if key != 'SUI_REGISTRY_ID'})):
        try:
            runtime_values(raw)
        except RuntimeError:
            continue
        raise RuntimeError('RESTORED_APP_SELF_TEST_FAILED')
    db, app = mysql_command(), backend_command()
    require('--read-only=ON' in db and '--super-read-only=ON' in db and '127.0.0.1:13307:3306' in db)
    require('--spring.jpa.hibernate.ddl-auto=validate' in app and '--server.address=127.0.0.1' in app
            and '--server.port=18082' in app and all('trekkey-backend-1' not in item for item in db + app))
    require(all('readonly' in app[index + 1] for index, value in enumerate(app) if value == '--mount'))
    valid = {'isSuccess': True, 'code': 'SUCCESS_200', 'httpStatus': 200, 'data': []}
    validate_readiness(200, 'application/json; charset=utf-8', json.dumps(valid).encode())
    validate_readiness(200, 'application/json', json.dumps({**valid, 'data': [{'id': 1, 'name': 'SYNTHETIC'}]}).encode())
    invalid_envelopes = [{**valid, 'isSuccess': False}, {**valid, 'code': 'SUCCESS_201'},
                         {**valid, 'httpStatus': '200'}, {**valid, 'data': None},
                         {**valid, 'data': [{'id': True, 'name': 'SYNTHETIC'}]},
                         {**valid, 'data': [{'id': 1, 'name': ''}]},
                         {**valid, 'data': [{'id': 1, 'name': 'SYNTHETIC', 'password': 'synthetic'}]}]
    for invalid in invalid_envelopes:
        try:
            validate_readiness(200, 'application/json', json.dumps(invalid).encode())
        except RuntimeError:
            continue
        raise RuntimeError('RESTORED_APP_SELF_TEST_FAILED')
    for status, content_type, body in ((503, 'application/json', json.dumps(valid).encode()),
                                       (200, 'text/html', b'<html>proxy</html>'),
                                       (200, 'application/json', b'x' * 262145)):
        try:
            validate_readiness(status, content_type, body)
        except RuntimeError:
            continue
        raise RuntimeError('RESTORED_APP_SELF_TEST_FAILED')
    print('RESTORED_APP_SELF_TEST_PASS cases=26 network=false secrets=false')


if __name__ == '__main__':
    try:
        if sys.argv[1:] == ['--self-test']:
            self_test()
        else:
            main()
    except BaseException:
        print('RESTORED_APP_FAILED stage=' + STAGE + ' state_preserved=true secrets_printed=false', file=sys.stderr)
        raise SystemExit(1)
