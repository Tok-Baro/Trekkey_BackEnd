#!/usr/bin/env python3
"""Server-local Sui configuration preparation. No keys/PII are printed or exported."""
import json
import os
import re
import stat
import subprocess
from pathlib import Path
from urllib.parse import urlsplit


def require(condition):
    if not condition:
        raise RuntimeError("PREPARATION_CONTRACT_FAILED")


def private_read(path):
    info = path.lstat()
    require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1 and info.st_size < 65536
            and info.st_uid in (0, 10001) and stat.S_IMODE(info.st_mode) == 0o600)
    return path.read_text().strip()


def write_new(path, value, uid=0):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        os.fchown(fd, uid, uid)
        with os.fdopen(fd, "w", closefd=False) as stream:
            stream.write(value)
            stream.flush()
            os.fsync(fd)
    finally:
        os.close(fd)


def main():
    require(os.geteuid() == 0)
    os.umask(0o077)
    for parent in (Path('/etc/trekkey'), Path('/srv/trekkey-backups')):
        info = parent.lstat()
        require(stat.S_ISDIR(info.st_mode) and info.st_uid == 0 and stat.S_IMODE(info.st_mode) == 0o700)
    environment = json.loads(subprocess.check_output([
        'docker', '--host', 'unix:///var/run/docker.sock', 'inspect', '--format', '{{json .Config.Env}}', 'trekkey-backend-1']))
    env = dict(entry.split('=', 1) for entry in environment)
    require(env.get('BLOCKCHAIN_CHAIN_ID') == '1001')
    require(re.fullmatch(r'0x[0-9a-fA-F]{40}', env.get('BLOCKCHAIN_CONTRACT_ADDRESS', '')))
    require(re.fullmatch(r'0x[0-9a-fA-F]{64}', env.get('BLOCKCHAIN_RUNTIME_CODE_HASH', '')))
    require(re.fullmatch(r'[A-Za-z0-9_.-]{1,100}', env.get('BLOCKCHAIN_CONTRACT_VERSION', '')))
    rpc = urlsplit(env.get('BLOCKCHAIN_RPC_URL', ''))
    require(rpc.scheme == 'https' and rpc.hostname and not rpc.username and not rpc.password
            and not rpc.query and not rpc.fragment)
    manifest = json.loads(private_read(Path('/srv/trekkey-sui-testnet/secrets/deployment/deployment.json')))
    require(manifest.get('network') == 'testnet' and manifest.get('protocolVersion') == 1)
    require(re.fullmatch(r'[0-9a-f]{8}', manifest.get('chainIdentifier', '')))
    for field in ('packageId', 'registryId', 'relayerAddress'):
        require(re.fullmatch(r'0x[0-9a-f]{64}', manifest.get(field, '')))
    # Preserve the effective old lookup secret, including the old JWT fallback. Rotating it
    # here would invalidate existing duplicate-lookup hashes and is a different migration.
    hmac = env.get('EVIDENCE_LOOKUP_HMAC_SECRET') or env.get('JWT_SECRET_KEY')
    require(isinstance(hmac, str) and len(hmac) >= 32 and '\n' not in hmac and '\r' not in hmac)
    command = '''set -eu
test "$MYSQL_DATABASE" = trekkey
if test -n "${MYSQL_ROOT_PASSWORD_FILE:-}"; then IFS= read -r MYSQL_PWD < "$MYSQL_ROOT_PASSWORD_FILE" || test -n "$MYSQL_PWD"; else MYSQL_PWD=$MYSQL_ROOT_PASSWORD; fi
export MYSQL_PWD
exec mysql --protocol=socket -uroot --database=trekkey --batch --skip-column-names -e "SELECT JSON_ARRAYAGG(public_id) FROM anc_credential WHERE status='ANCHORED' AND credential_type='AWARD'"
'''
    ids = json.loads(subprocess.check_output(['docker', '--host', 'unix:///var/run/docker.sock', 'exec', 'trekkey-mysql-1', 'sh', '-c', command]))
    require(isinstance(ids, list) and len(ids) == 6 and len(set(ids)) == 6)
    require(all(re.fullmatch(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}', item) for item in ids))
    values = {
        'SUI_NETWORK': 'testnet', 'SUI_CHAIN_IDENTIFIER': manifest['chainIdentifier'],
        'SUI_PACKAGE_ID': manifest['packageId'], 'SUI_REGISTRY_ID': manifest['registryId'],
        'BLOCKCHAIN_ANCHORING_MODE': 'LOCAL_RELAYER', 'BLOCKCHAIN_WORKER_ENABLED': 'true',
        'LEGACY_KAIA_READ_ENABLED': 'true', 'LEGACY_KAIA_CHAIN_ID': '1001',
        'LEGACY_KAIA_RPC_URL': env['BLOCKCHAIN_RPC_URL'],
        'LEGACY_KAIA_CONTRACT_ADDRESS': env['BLOCKCHAIN_CONTRACT_ADDRESS'].lower(),
        'LEGACY_KAIA_RUNTIME_CODE_HASH': env['BLOCKCHAIN_RUNTIME_CODE_HASH'].lower(),
        'LEGACY_KAIA_CONTRACT_VERSION': env['BLOCKCHAIN_CONTRACT_VERSION'],
    }
    require(all('\n' not in value and '\r' not in value and '$' not in value for value in values.values()))
    public_file = Path('/etc/trekkey/sui-runtime.env')
    secret_dir = Path('/etc/trekkey/sui-secrets')
    reference_dir = Path('/srv/trekkey-backups/sui-release-20260908-http')
    require(all(not path.exists() and not path.is_symlink() for path in (public_file, secret_dir, reference_dir)))
    secret_dir.mkdir(mode=0o700)
    reference_dir.mkdir(mode=0o700)
    write_new(secret_dir / 'EVIDENCE_LOOKUP_HMAC_SECRET', hmac, 10001)
    write_new(public_file, ''.join(f'{key}={value}\n' for key, value in values.items()))
    write_new(reference_dir / 'ids.json', json.dumps(sorted(ids)) + '\n')
    print('SUI_RUNTIME_PREPARED network=testnet legacy_read=true hmac_preserved=true key_created=false ids=6 secrets_printed=false')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        print('SUI_RUNTIME_PREPARATION_FAILED partial_files_preserved=true secrets_printed=false')
        raise SystemExit(1)
