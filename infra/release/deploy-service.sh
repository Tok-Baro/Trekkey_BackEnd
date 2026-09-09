#!/bin/bash
# One-shot, exact-revision Kaia -> Sui TESTNET cutover. Install root:root 0700.
# No secrets, response bodies, SQL values or backup contents are printed.
set -euo pipefail
set +x
export PATH=/usr/sbin:/usr/bin:/sbin:/bin LC_ALL=C
unset DOCKER_HOST DOCKER_CONTEXT MYSQL_PWD MYSQL_HOST MYSQL_TCP_PORT PYTHONPATH PYTHONHOME
exec /usr/bin/python3 - "$@" <<'TREKKEY_DEPLOY_PYTHON'
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import sys
import time
import urllib.request

BASE = Path('/opt/trekkey/sui-release')
BACKUPS = Path('/srv/trekkey-backups')
OLD_REVISION = '0615984797b7c1c6b51f2f9e7ae7ca41332ae15c'
OLD_IMAGE = 'ghcr.io/tok-baro/trekkey_backend:' + OLD_REVISION
DOCKER = ['docker', '--host', 'unix:///var/run/docker.sock']
SAFE_ENV = {'PATH': '/usr/sbin:/usr/bin:/sbin:/bin', 'LC_ALL': 'C', 'DOCKER_CONFIG': '/root/.docker'}
ARTIFACTS = {
    'compose': BASE / 'docker-compose.prod.yml',
    'migration': BASE / '2026-09-08-sui-anchor-context.sql',
    'backupHelper': BASE / 'backup-service.sh',
    'httpVerifier': BASE / 'verify-legacy-http.mjs',
    'preflight': BASE / 'preflight-result.json',
    'helper': BASE / 'deploy-service.sh',
}
REFERENCE = BACKUPS / 'sui-release-20260908-http/reference.json'
CUTOVER = BACKUPS / 'sui-release-20260908-cutover'
COMPOSE = Path('/opt/trekkey/docker-compose.prod.yml')
ENV_FILE = Path('/etc/trekkey/trekkey.env')
RUNTIME = Path('/etc/trekkey/sui-runtime.env')
IMAGE_ENV = Path('/etc/trekkey/sui-images.env')
ROLLBACK_OVERRIDE = b'services:\n  backend:\n    environment:\n      SPRING_JPA_HIBERNATE_DDL_AUTO: "none"\n'
SHA = re.compile(r'[0-9a-f]{40}\Z')
HASH = re.compile(r'[0-9a-f]{64}\Z')


class Refusal(Exception):
    pass


def require(condition):
    if not condition:
        raise Refusal('RELEASE_CONTRACT_REFUSED')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def validate_approval(value, revision):
    require(isinstance(revision, str) and SHA.fullmatch(revision) is not None)
    require(isinstance(value, dict) and set(value) == {'version', 'releaseSha', 'network', 'artifacts'})
    require(value['version'] == 1 and value['releaseSha'] == revision and value['network'] == 'testnet')
    hashes = value['artifacts']
    require(isinstance(hashes, dict) and set(hashes) == set(ARTIFACTS))
    require(all(isinstance(item, str) and HASH.fullmatch(item) for item in hashes.values()))
    return value


def validate_preflight(value, revision):
    expected = {'version': 1, 'status': 'PASS', 'releaseSha': revision, 'network': 'testnet',
                'mysqlTests': 26, 'legacyJsonChecks': 6, 'legacyBinaryChecks': 12,
                'completionChecks': 12, 'hibernateValidate': True,
                'schemaRehearsal': True, 'immutableRecordsPreserved': True}
    require(isinstance(value, dict) and set(value) == set(expected) | {'evidence'})
    require(all(type(value[key]) is type(item) and value[key] == item for key, item in expected.items()))
    evidence = value['evidence']
    require(isinstance(evidence, list) and 4 <= len(evidence) <= 12)
    paths = set()
    for entry in evidence:
        require(isinstance(entry, dict) and set(entry) == {'path', 'sha256'})
        path, checksum = entry['path'], entry['sha256']
        require(isinstance(path, str) and path.startswith(('/srv/trekkey-backups/sui-release-20260908-',
                                                        '/srv/trekkey-sui-testnet/')))
        require(str(Path(path)) == path and '..' not in Path(path).parts and path not in paths)
        require(Path(path).name.endswith(('.json', '.sha256', '.xml', '.log')))
        require(isinstance(checksum, str) and HASH.fullmatch(checksum))
        paths.add(path)
    return value


def validate_image(info, repository, revision):
    require(isinstance(info, dict) and info.get('Architecture') == 'arm64' and info.get('Os') == 'linux')
    require(re.fullmatch(r'sha256:[0-9a-f]{64}', info.get('Id', '')) is not None)
    labels = info.get('Config', {}).get('Labels') or {}
    require(labels.get('org.opencontainers.image.revision') == revision)
    require(labels.get('org.opencontainers.image.source', '').lower()
            == 'https://github.com/tok-baro/trekkey_backend')
    matches = [item for item in info.get('RepoDigests', [])
               if re.fullmatch(re.escape(repository) + r'@sha256:[0-9a-f]{64}', item)]
    require(len(matches) == 1)
    return matches[0], info['Id']


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


def compose_preserved_sections(content):
    # This release supports the observed block-style services/volumes document only.
    # Anchored indentation is essential: dependencies also contain mysql/backend keys.
    require(isinstance(content, bytes))
    top = list(re.finditer(rb'^([A-Za-z][A-Za-z0-9_-]*):[ \t]*\n', content, re.MULTILINE))
    require([item.group(1) for item in top] == [b'services', b'volumes'])
    services = content[top[0].end():top[1].start()]
    matches = list(re.finditer(rb'^  ([A-Za-z][A-Za-z0-9_-]*):[ \t]*\n', services, re.MULTILINE))
    names = [item.group(1) for item in matches]
    require(len(names) == len(set(names)) and b'mysql' in names and b'backend' in names)
    index = names.index(b'mysql')
    end = matches[index + 1].start() if index + 1 < len(matches) else len(services)
    return services[matches[index].start():end], content[top[1].start():]


def validate_compose_preservation(candidate, original):
    require(compose_preserved_sections(candidate) == compose_preserved_sections(original))


def private_directory(path, exact_mode=None):
    for item in [path, *path.parents]:
        info = item.lstat()
        require(stat.S_ISDIR(info.st_mode) and info.st_uid == 0 and not info.st_mode & 0o022)
    if exact_mode is not None:
        require(stat.S_IMODE(path.stat().st_mode) == exact_mode)


def protected_read(path, modes=(0o600,), limit=1048576):
    private_directory(path.parent)
    descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    try:
        info = os.fstat(descriptor)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and info.st_gid == 0
                and info.st_nlink == 1 and stat.S_IMODE(info.st_mode) in modes
                and 0 < info.st_size <= limit)
        data = os.read(descriptor, limit + 1)
        require(len(data) <= limit)
        return data
    finally:
        os.close(descriptor)


def create_private(path, data):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        with os.fdopen(descriptor, 'wb', closefd=False) as stream:
            stream.write(data)
            stream.flush()
            os.fsync(descriptor)
    finally:
        os.close(descriptor)


def replace_from_protected(path, data):
    # Both existing target and parent were checked before the exclusive release claim.
    candidate = path.with_name(path.name + '.sui-release-20260908-new')
    create_private(candidate, data)
    os.replace(candidate, path)
    descriptor = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


MYSQL_COMMAND = '''set -eu
test "$MYSQL_DATABASE" = trekkey
if test -n "${MYSQL_ROOT_PASSWORD_FILE:-}"; then
  IFS= read -r MYSQL_PWD < "$MYSQL_ROOT_PASSWORD_FILE" || test -n "$MYSQL_PWD"
else MYSQL_PWD=$MYSQL_ROOT_PASSWORD; fi
export MYSQL_PWD
exec mysql --no-defaults --protocol=socket -uroot --database=trekkey --binary-mode=1 --local-infile=0 --batch --skip-column-names
'''
IMMUTABLE_SQL = '''
SELECT id,SHA2(canonical_bytes,256),SHA2(file_manifest_canonical_bytes,256),SHA2(payload_json,256),HEX(content_hash),HEX(file_manifest_hash),HEX(credential_id_hash),status FROM anc_credential ORDER BY id;
SELECT id,public_id,HEX(batch_id_hash),HEX(merkle_root),HEX(schema_version_hash),HEX(approval_digest),HEX(issuer_signature),SHA2(approval_payload_json,256),approval_nonce,status FROM anc_batch ORDER BY id;
SELECT id,organization_id,key_version,HEX(signer_address),SHA2(signer_ref,256),status,valid_from,valid_until,compromised_at FROM anc_issuer_key ORDER BY id;
SELECT id,chain_id,HEX(contract_address),HEX(relayer_address),HEX(tx_hash),SHA2(signed_raw_transaction,256),tx_nonce,contract_version,status FROM anc_chain_transaction ORDER BY id;
'''
PREFLIGHT_SQL = '''
SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()),
(SELECT COUNT(*) FROM anc_credential WHERE status='ANCHORED'),
(SELECT COUNT(*) FROM anc_credential WHERE status='ANCHORED' AND credential_type='AWARD'),
(SELECT COUNT(*) FROM anc_batch),(SELECT COUNT(*) FROM anc_chain_transaction),(SELECT COUNT(*) FROM anc_issuer_key),
(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='chain_context' AND table_name IN ('anc_batch','anc_issuer_key','anc_chain_transaction','anc_credential_status_event')),
(SELECT COUNT(*) FROM (SELECT batch_id FROM anc_chain_transaction WHERE operation_type='ANCHOR_BATCH' GROUP BY batch_id HAVING COUNT(*)>1) x),
(SELECT COUNT(*) FROM (SELECT credential_status_event_id FROM anc_chain_transaction WHERE credential_status_event_id IS NOT NULL GROUP BY credential_status_event_id HAVING COUNT(*)>1) y),
(SELECT COUNT(*) FROM anc_chain_transaction WHERE chain_id<>1001 OR OCTET_LENGTH(contract_address)<>20 OR (relayer_address IS NOT NULL AND OCTET_LENGTH(relayer_address)<>20) OR status<>'CONFIRMED'),
(SELECT COUNT(*) FROM anc_outbox_event WHERE status<>'PROCESSED');
'''
POSTFLIGHT_SQL = '''
SELECT (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='chain_context' AND column_type='varchar(384)' AND is_nullable='YES' AND table_name IN ('anc_batch','anc_issuer_key','anc_chain_transaction','anc_credential_status_event')),
(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='anc_chain_transaction' AND column_name IN ('contract_address','relayer_address') AND column_type='varbinary(32)'),
(SELECT COUNT(*) FROM anc_chain_transaction WHERE chain_context=CONCAT('KAIA|',chain_id,'|0x',LOWER(HEX(contract_address)),'|',contract_version)),
(SELECT COUNT(*) FROM anc_batch b JOIN anc_chain_transaction t ON t.batch_id=b.id AND t.operation_type='ANCHOR_BATCH' WHERE b.chain_context=t.chain_context),
(SELECT COUNT(*) FROM anc_issuer_key WHERE chain_context IS NULL);
'''


class Runtime:
    def __init__(self, revision):
        self.revision = revision
        self.audit = BACKUPS / ('sui-release-20260908-deploy-' + revision)
        self.images = {}
        self.image_ids = {}
        self.paused = False
        self.migration_started = False
        self.compose_installed = False
        self.initially_running = []
        self.log_number = 0

    def run(self, argv, data=None, timeout=60, env=None):
        result = subprocess.run(argv, input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                timeout=timeout, env=env or SAFE_ENV, check=False)
        # Error text may contain SQL/connection values. Retain it only on the server.
        if result.returncode:
            self.log_number += 1
            create_private(self.audit / ('command-%03d.stderr' % self.log_number), result.stderr or b'No error text.\n')
            raise Refusal('RELEASE_COMMAND_FAILED')
        return result.stdout

    def inspect(self, name):
        return json.loads(self.run(DOCKER + ['inspect', name]))[0]

    def mysql(self, sql):
        return self.run(DOCKER + ['exec', '-i', 'trekkey-mysql-1', 'sh', '-c', MYSQL_COMMAND],
                        sql.encode(), timeout=60)

    def compose(self, *arguments, old=False):
        command = DOCKER + ['compose', '-p', 'trekkey', '--env-file', str(ENV_FILE)]
        environment = dict(SAFE_ENV)
        if old:
            command += ['-f', str(self.audit / 'original-compose.yml'),
                        '-f', str(self.audit / 'rollback-override.yml')]
            environment['BACKEND_IMAGE'] = OLD_IMAGE
        else:
            command += ['--env-file', str(RUNTIME), '--env-file', str(IMAGE_ENV), '-f', str(COMPOSE)]
        return self.run(command + list(arguments), timeout=180, env=environment)

    def prepare(self):
        require(os.geteuid() == 0)
        private_directory(BASE, 0o700)
        private_directory(BACKUPS, 0o700)
        capacity = os.statvfs(BACKUPS)
        require(capacity.f_bavail * capacity.f_frsize >= 6 * 1024 ** 3)
        approval_path = BASE / ('approved-' + self.revision + '.json')
        approval = validate_approval(json.loads(protected_read(approval_path)), self.revision)
        for name, path in ARTIFACTS.items():
            modes = (0o700,) if name in ('helper', 'backupHelper') else (0o600,)
            require(digest(protected_read(path, modes)) == approval['artifacts'][name])
        proof = validate_preflight(json.loads(protected_read(ARTIFACTS['preflight'])), self.revision)
        for item in proof['evidence']:
            require(digest(protected_read(Path(item['path']))) == item['sha256'])
        protected_read(REFERENCE)
        self.original_compose = protected_read(COMPOSE, (0o600, 0o644))
        self.original_env = protected_read(ENV_FILE)
        protected_read(RUNTIME)
        require(not self.audit.exists() and not self.audit.is_symlink())
        require(not CUTOVER.exists() and not CUTOVER.is_symlink())
        require(not IMAGE_ENV.exists() and not IMAGE_ENV.is_symlink())
        self.audit.mkdir(mode=0o700)
        create_private(self.audit / 'run.claim', (self.revision + '\n').encode())
        create_private(self.audit / 'approval.json', json.dumps(approval).encode())
        create_private(self.audit / 'original-compose.yml', self.original_compose)
        create_private(self.audit / 'original-runtime.env', self.original_env)
        # The original application defaults to ddl-auto=update. Never let its rollback
        # reinterpret or narrow the expanded Sui schema, including after a partial DDL failure.
        create_private(self.audit / 'rollback-override.yml', ROLLBACK_OVERRIDE)
        create_private(self.audit / 'rollback-override.sha256', (digest(ROLLBACK_OVERRIDE) + '\n').encode())
        backend = self.inspect('trekkey-backend-1')
        mysql = self.inspect('trekkey-mysql-1')
        require(backend['Config']['Image'] == OLD_IMAGE and backend['State']['Running'])
        require(mysql['Config']['Image'] == 'mysql:8.4' and mysql['State']['Running'])
        require(backend['Config']['Labels']['com.docker.compose.project'] == 'trekkey')
        self.mysql_id = mysql['Id']
        self.mysql_started = mysql['State']['StartedAt']
        for name in ('trekkey-sui-testnet-backend-1', 'trekkey-sui-testnet-gateway-1'):
            if self.inspect(name)['State']['Running']:
                self.initially_running.append(name)
        # The compose's exact bytes are approval-bound; MySQL service text must also be unchanged.
        candidate = protected_read(ARTIFACTS['compose'])
        validate_compose_preservation(candidate, self.original_compose)
        require(self.mysql(PREFLIGHT_SQL).decode().strip() == '48\t6\t6\t1\t1\t1\t0\t0\t0\t0\t0')
        for kind, repository in (('backend', 'ghcr.io/tok-baro/trekkey_backend'),
                                 ('gateway', 'ghcr.io/tok-baro/trekkey_backend-sui-gateway')):
            tag = repository + ':' + self.revision
            self.run(DOCKER + ['pull', tag], timeout=600)
            info = json.loads(self.run(DOCKER + ['image', 'inspect', tag]))[0]
            self.images[kind], self.image_ids[kind] = validate_image(info, repository, self.revision)
        create_private(self.audit / 'images.json', json.dumps(self.images).encode())
        image_values = ('BACKEND_IMAGE=' + self.images['backend'] + '\nSUI_GATEWAY_IMAGE=' + self.images['gateway'] + '\n')
        create_private(IMAGE_ENV, image_values.encode())
        # Validate the candidate without changing the installed production compose.
        self.run(DOCKER + ['compose', '-p', 'trekkey', '--env-file', str(ENV_FILE),
                 '--env-file', str(RUNTIME), '--env-file', str(IMAGE_ENV), '-f', str(ARTIFACTS['compose']),
                 'config', '--quiet'])

    def pause(self):
        self.paused = True  # A partial stop still requires restoration.
        for name in ('trekkey-backend-1', *self.initially_running):
            self.run(DOCKER + ['stop', '--time', '30', name], timeout=40)
        require(all(not self.inspect(name)['State']['Running'] for name in
                    ('trekkey-backend-1', 'trekkey-sui-testnet-backend-1', 'trekkey-sui-testnet-gateway-1')))

    def backup(self):
        self.run([str(ARTIFACTS['backupHelper']), '--cutover'], timeout=900)
        private_directory(CUTOVER, 0o700)
        sums = protected_read(CUTOVER / 'SHA256SUMS').decode().splitlines()
        names = {'database.sql.gz', 'testnet-initial.sql.gz', 'testnet-full.sql.gz', 'configuration.tar.gz',
                 'uploads.tar.gz', 'testnet-state.tar.gz', 'service-images.tar.gz'}
        require(len(sums) == 7)
        observed = set()
        for line in sums:
            parts = line.split('  ')
            require(len(parts) == 2 and HASH.fullmatch(parts[0]))
            path = Path(parts[1])
            require(path.parent == CUTOVER and path.name in names and path.name not in observed)
            info = path.lstat()
            require(stat.S_ISREG(info.st_mode) and info.st_uid == 0 and info.st_gid == 0
                    and info.st_nlink == 1 and stat.S_IMODE(info.st_mode) == 0o600 and info.st_size > 0)
            # Hash streams, never load multi-GB images or private archives into output/memory.
            self.run(['gzip', '-t', str(path)], timeout=180)
            actual = self.run(['sha256sum', str(path)], timeout=180).decode().split()[0]
            require(actual == parts[0])
            observed.add(path.name)
        require(observed == names)

    def migrate(self):
        require(self.mysql(PREFLIGHT_SQL).decode().strip() == '48\t6\t6\t1\t1\t1\t0\t0\t0\t0\t0')
        before = digest(self.mysql(IMMUTABLE_SQL))
        create_private(self.audit / 'immutable-before.sha256', (before + '\n').encode())
        self.migration_started = True
        self.mysql(protected_read(ARTIFACTS['migration']).decode())
        require(self.mysql(POSTFLIGHT_SQL).decode().strip() == '4\t2\t1\t1\t1')
        after = digest(self.mysql(IMMUTABLE_SQL))
        create_private(self.audit / 'immutable-after.sha256', (after + '\n').encode())
        require(before == after)

    def activate(self):
        self.compose_installed = True
        replace_from_protected(COMPOSE, protected_read(ARTIFACTS['compose']))
        self.compose('up', '-d', '--no-deps', '--force-recreate', 'backend', 'gateway')
        for kind in ('backend', 'gateway'):
            info = self.inspect('trekkey-' + kind + '-1')
            require(info['State']['Running'] and info['Image'] == self.image_ids[kind])
        self.check_mysql_untouched()

    def check_mysql_untouched(self):
        info = self.inspect('trekkey-mysql-1')
        require(info['Id'] == self.mysql_id and info['State']['Running'] and info['State']['StartedAt'] == self.mysql_started)

    def http_readiness(self):
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, *args):
                return None
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        ready = False
        for _ in range(36):
            try:
                with opener.open('http://127.0.0.1:8080/api/organizations?keyword=SYNTHETIC', timeout=2) as reply:
                    validate_readiness(reply.status, reply.headers.get('Content-Type', ''), reply.read(262145))
                    ready = True
            except Exception:
                ready = False
            if ready:
                break
            time.sleep(2)
        require(ready)

    def health(self):
        self.http_readiness()
        # Runs in the gateway's shared backend network namespace. The bearer token stays in memory.
        probe = '''const fs=require('node:fs'); (async()=>{const m=JSON.parse(fs.readFileSync('/run/manifest/deployment.json'));
const token=fs.readFileSync('/run/keys/gateway-token','utf8').trim();
const r=await fetch('http://127.0.0.1:9187/v1/identity',{headers:{Authorization:'Bearer '+token},redirect:'error',signal:AbortSignal.timeout(20000)});
if(r.status!==200)throw 0;const v=await r.json();if(v.ok!==true||!v.data||!['network','chainIdentifier','packageId','registryId','protocolVersion'].every(k=>v.data[k]===m[k])||v.data.network!=='testnet')throw 0;
process.stdout.write('IDENTITY_PASS');})().catch(()=>process.exitCode=1);'''
        require(self.run(DOCKER + ['exec', 'trekkey-gateway-1', 'node', '-e', probe], timeout=25) == b'IDENTITY_PASS')
        result = self.run(DOCKER + ['run', '--rm', '--pull=never', '--network', 'host', '--read-only',
            '--user', '0:0', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges', '--memory', '256m',
            '--cpus', '0.5', '--pids-limit', '64', '--mount', 'type=bind,source=' + str(BASE) + ',target=/app,readonly',
            '--mount', 'type=bind,source=' + str(REFERENCE.parent) + ',target=/references,readonly',
            '--entrypoint', 'node', self.images['gateway'], '/app/verify-legacy-http.mjs', 'verify',
            '--reference-file', '/references/reference.json', '--live'], timeout=330)
        require(json.loads(result) == {'credentials': 6, 'jsonChecks': 6, 'binaryChecks': 12, 'passed': True})
        create_private(self.audit / 'legacy-live-result.json', result)
        self.check_mysql_untouched()

    def rollback(self):
        # Additive DDL stays. Never replace a database after user writes or Sui transactions.
        if self.compose_installed:
            self.compose('stop', '--timeout', '30', 'gateway', 'backend')
        if self.migration_started:
            require(self.mysql('SELECT COUNT(*) FROM anc_chain_transaction WHERE chain_id<>1001 OR OCTET_LENGTH(contract_address)<>20;').strip() == b'0')
            # A partial DDL failure may leave only some additive columns. Check present columns
            # individually so it can still restore the old application without undoing any DDL.
            for table in ('anc_batch', 'anc_issuer_key', 'anc_chain_transaction', 'anc_credential_status_event'):
                exists = self.mysql("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='" + table + "' AND column_name='chain_context';")
                require(exists.strip() in (b'0', b'1'))
                if exists.strip() == b'1':
                    require(self.mysql("SELECT COUNT(*) FROM " + table + " WHERE chain_context LIKE 'SUI|%';").strip() == b'0')
        require(protected_read(ENV_FILE) == self.original_env)
        if self.compose_installed:
            replace_from_protected(COMPOSE, self.original_compose)
        if self.compose_installed or self.migration_started:
            require(protected_read(self.audit / 'rollback-override.yml') == ROLLBACK_OVERRIDE)
            self.compose('up', '-d', '--no-deps', '--force-recreate', 'backend', old=True)
        else:
            self.run(DOCKER + ['start', 'trekkey-backend-1'])
        # Restore only services that were running before this operation; single journal writer.
        for name in reversed(self.initially_running):
            self.run(DOCKER + ['start', name])
        self.check_mysql_untouched()
        require(self.inspect('trekkey-backend-1')['State']['Running'])
        self.http_readiness()
        create_private(self.audit / 'rollback.json', json.dumps({'applicationRestored': True,
            'databaseRestored': False, 'expandedSchemaPreserved': True,
            'automaticDdlDisabled': self.compose_installed or self.migration_started}).encode())

    def finish(self):
        create_private(self.audit / 'result.json', json.dumps({'status': 'PASS', 'releaseSha': self.revision,
            'network': 'testnet', 'legacyJsonChecks': 6, 'legacyBinaryChecks': 12,
            'mysqlRecreated': False, 'immutableRecordsPreserved': True, 'backup': str(CUTOVER),
            'images': self.images}).encode())


def execute(runtime):
    stage = 'PREPARE'
    try:
        runtime.prepare()
        for stage, action in [('PAUSE', runtime.pause), ('BACKUP', runtime.backup),
                              ('MIGRATION', runtime.migrate), ('ACTIVATE', runtime.activate),
                              ('HEALTH', runtime.health), ('RESULT', runtime.finish)]:
            print('SUI_RELEASE_STAGE ' + stage, flush=True)
            action()
    except BaseException:
        restored = False
        if runtime.paused:
            try:
                runtime.rollback()
                restored = True
            except BaseException:
                pass
        print('SUI_RELEASE_FAILED stage=' + stage + ' application_rollback=' + str(restored).lower()
              + ' state_preserved=true secrets_printed=false', flush=True)
        return 1
    print('SUI_RELEASE_PASS network=testnet legacy_http_checks=18 mysql_recreated=false secrets_printed=false', flush=True)
    return 0


def main(arguments):
    require(len(arguments) == 1 and SHA.fullmatch(arguments[0]) is not None)
    os.umask(0o077)
    def interrupted(_signal, _frame):
        raise Refusal('RELEASE_INTERRUPTED')
    signal.signal(signal.SIGINT, interrupted)
    signal.signal(signal.SIGTERM, interrupted)
    return execute(Runtime(arguments[0]))


if __name__ == '__main__':
    try:
        sys.exit(main(sys.argv[1:]))
    except BaseException as error:
        if isinstance(error, SystemExit):
            raise
        print('SUI_RELEASE_REFUSED secrets_printed=false', flush=True)
        sys.exit(1)
TREKKEY_DEPLOY_PYTHON
