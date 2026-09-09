import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const shell = readFileSync(new URL('./deploy-service.sh', import.meta.url), 'utf8');
const code = shell.split("<<'TREKKEY_DEPLOY_PYTHON'\n")[1].split('\nTREKKEY_DEPLOY_PYTHON')[0];
const revision = '1'.repeat(40);
const checksum = '2'.repeat(64);
const actualCompose = readFileSync(new URL('../../docker-compose.prod.yml', import.meta.url), 'utf8');
const prelude = `\nns = {'__name__': 'release_contract_test'}\nexec(compile(${JSON.stringify(code)}, '<release-helper>', 'exec'), ns)\n`;
function python(body) {
  const result = spawnSync('python3', ['-c', prelude + body], { encoding: 'utf8', timeout: 10000 });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout;
}
function expression(name, value, expected = true, extra = '') {
  python(`\nimport json\nvalue = json.loads(${JSON.stringify(JSON.stringify(value))})\ntry:\n ns[${JSON.stringify(name)}](value${extra})\n accepted = True\nexcept ns['Refusal']:\n accepted = False\nassert accepted is ${expected ? 'True' : 'False'}\n`);
}
const approval = () => ({ version: 1, releaseSha: revision, network: 'testnet', artifacts:
  Object.fromEntries(['compose', 'migration', 'backupHelper', 'httpVerifier', 'preflight', 'helper'].map(key => [key, checksum])) });
const proof = () => ({ version: 1, status: 'PASS', releaseSha: revision, network: 'testnet', mysqlTests: 26,
  legacyJsonChecks: 6, legacyBinaryChecks: 12, completionChecks: 12, hibernateValidate: true,
  schemaRehearsal: true, immutableRecordsPreserved: true,
  evidence: Array.from({ length: 4 }, (_, i) => ({ path: `/srv/trekkey-backups/sui-release-20260908-gate/proof-${i}.json`, sha256: checksum })) });

test('shell syntax and embedded Python parse without executing deployment', () => {
  assert.equal(spawnSync('bash', ['-n', fileURLToPath(new URL('./deploy-service.sh', import.meta.url))]).status, 0);
  python('assert ns["OLD_REVISION"] == "0615984797b7c1c6b51f2f9e7ae7ca41332ae15c"\n');
});

test('approval requires exact testnet full SHA and complete content hashes', () => {
  expression('validate_approval', approval(), true, `, '${revision}'`);
  for (const mutation of [value => value.releaseSha = 'main', value => value.network = 'mainnet',
    value => value.releaseSha = '3'.repeat(40), value => delete value.artifacts.migration,
    value => value.artifacts.extra = checksum, value => value.artifacts.compose = 'latest',
    value => value.command = 'arbitrary']) {
    const value = approval(); mutation(value);
    expression('validate_approval', value, false, `, '${revision}'`);
  }
  expression('validate_approval', approval(), false, ", 'main'");
});

test('preflight requires all actual gates, not skipped tests or truthy strings', () => {
  expression('validate_preflight', proof(), true, `, '${revision}'`);
  for (const mutation of [value => value.mysqlTests = 25, value => value.legacyBinaryChecks = 0,
    value => value.completionChecks = 11, value => value.hibernateValidate = 'true',
    value => value.immutableRecordsPreserved = 1, value => value.schemaRehearsal = false,
    value => value.status = 'SKIP', value => value.network = 'mainnet',
    value => value.releaseSha = '3'.repeat(40), value => value.evidence = [],
    value => value.evidence[0].path = '/etc/trekkey/trekkey.env',
    value => value.evidence[0].path = '/srv/trekkey-sui-testnet/../secret.json',
    value => value.evidence[0] = value.evidence[1],
    value => value.evidence[0].sha256 = 'incorrect']) {
    const value = proof(); mutation(value);
    expression('validate_preflight', value, false, `, '${revision}'`);
  }
});

test('image identity rejects wrong platform, revision, source, ambiguous digest or tag-only images', () => {
  const repository = 'ghcr.io/tok-baro/trekkey_backend';
  const make = () => ({ Architecture: 'arm64', Os: 'linux', Id: `sha256:${checksum}`,
    Config: { Labels: { 'org.opencontainers.image.revision': revision,
      'org.opencontainers.image.source': 'https://github.com/Tok-Baro/Trekkey_BackEnd' } },
    RepoDigests: [`${repository}@sha256:${checksum}`] });
  const args = `, '${repository}', '${revision}'`;
  expression('validate_image', make(), true, args);
  for (const mutation of [value => value.Architecture = 'amd64', value => value.Os = 'windows',
    value => value.Config.Labels['org.opencontainers.image.revision'] = '4'.repeat(40),
    value => value.Config.Labels['org.opencontainers.image.source'] = 'https://github.com/other/repo',
    value => value.RepoDigests = [`${repository}:latest`], value => value.RepoDigests.push(`${repository}@sha256:${'3'.repeat(64)}`),
    value => value.Id = 'latest']) {
    const value = make(); mutation(value); expression('validate_image', value, false, args);
  }
});

test('readiness requires the actual bounded JSON organization success contract, not HTTP 200 alone', () => {
  python(`
import json
valid={'isSuccess':True,'code':'SUCCESS_200','httpStatus':200,'data':[{'id':1,'name':'SYNTHETIC'}]}
ns['validate_readiness'](200,'application/json; charset=utf-8',json.dumps(valid).encode())
mutations=[{'isSuccess':False},{'code':'SUCCESS_201'},{'httpStatus':'200'},{'data':None},
 {'data':[{'id':True,'name':'SYNTHETIC'}]},{'data':[{'id':1,'name':''}]},
 {'data':[{'id':1,'name':'SYNTHETIC','password':'must-not-pass'}]}]
for changed in mutations:
 try: ns['validate_readiness'](200,'application/json',json.dumps({**valid,**changed}).encode()); accepted=True
 except ns['Refusal']: accepted=False
 assert not accepted
for status,content,body in [(503,'application/json',json.dumps(valid).encode()),(200,'text/html',b'<html>proxy</html>'),(200,'application/json',b'x'*262145)]:
 try: ns['validate_readiness'](status,content,body); accepted=True
 except ns['Refusal']: accepted=False
 assert not accepted
`);
});

test('real production compose preserves historical MySQL and top-level volumes despite nested dependency keys', () => {
  python(`
candidate=${JSON.stringify(actualCompose)}.encode()
assert candidate.count(b'  mysql:\\n')==2
assert candidate.count(b'  backend:\\n')==2
mysql,volumes=ns['compose_preserved_sections'](candidate)
# Independently captured from revision 0615984797b7c1c6b51f2f9e7ae7ca41332ae15c,
# not recomputed from the current candidate. CI need not fetch historical git objects.
assert len(mysql)==843 and ns['digest'](mysql)=='236652238805734d440dcf9498c91c484e743cc459a06c001ece186bf660445d'
assert len(volumes)==23 and ns['digest'](volumes)=='92204bcb6b5635ad0bef36b7c527b567ec14407fa133859042d115d3b17d58b2'
original=b'services:\\n'+mysql+b'  backend:\\n    image: original\\n\\n'+volumes
ns['validate_compose_preservation'](candidate,original)
`);
});

test('changed MySQL fields or top-level volume identity fail byte-preservation gate', () => {
  python(`
candidate=${JSON.stringify(actualCompose)}.encode()
mysql,volumes=ns['compose_preserved_sections'](candidate)
original=b'services:\\n'+mysql+b'  backend:\\n    image: original\\n\\n'+volumes
for changed in [candidate.replace(b'image: mysql:8.4',b'image: mysql:8.5',1),
                candidate.replace(b'MYSQL_DATABASE: trekkey',b'MYSQL_DATABASE: wrong',1),
                candidate.replace(b'volumes:\\n  mysql-data:\\n',b'volumes:\\n  other-data:\\n')]:
 assert changed!=candidate
 try: ns['validate_compose_preservation'](changed,original); accepted=True
 except ns['Refusal']: accepted=False
 assert not accepted
`);
});

test('duplicate service keys and unsupported top-level shapes fail instead of choosing an ambiguous block', () => {
  python(`
candidate=${JSON.stringify(actualCompose)}.encode()
for changed in [candidate.replace(b'  gateway:\\n',b'  backend:\\n'),candidate+b'volumes:\\n  extra:\\n',
                candidate.replace(b'services:\\n',b'services: {}\\n')]:
 try: ns['compose_preserved_sections'](changed); accepted=True
 except ns['Refusal']: accepted=False
 assert not accepted
`);
});

const fake = `
class Fake:
 def __init__(self, fail=None, rollback_fail=False):
  self.events=[]; self.paused=False; self.fail=fail; self.rollback_fail=rollback_fail
 def step(self, name):
  self.events.append(name)
  if name==self.fail: raise ns['Refusal']('synthetic')
 def prepare(self): self.step('prepare')
 def pause(self): self.paused=True; self.step('pause')
 def backup(self): self.step('backup')
 def migrate(self): self.step('migrate')
 def activate(self): self.step('activate')
 def health(self): self.step('health')
 def finish(self): self.step('finish')
 def rollback(self):
  self.events.append('rollback')
  if self.rollback_fail: raise ns['Refusal']('synthetic')
`;
test('release state machine orders quiescence and verified backup before any migration/activation', () => {
  python(fake + `\nf=Fake()\nassert ns['execute'](f)==0\nassert f.events==['prepare','pause','backup','migrate','activate','health','finish']\n`);
});
test('preflight refusal never stops services or attempts rollback', () => {
  python(fake + `\nf=Fake('prepare')\nassert ns['execute'](f)==1\nassert f.events==['prepare']\n`);
});
test('every post-quiescence failure restores application and never continues to later stages', () => {
  python(fake + `
stages=['prepare','pause','backup','migrate','activate','health','finish']
for stage in stages[1:]:
 f=Fake(stage)
 assert ns['execute'](f)==1
 assert f.events==stages[:stages.index(stage)+1]+['rollback']
`);
});
test('rollback failure is reported as failure rather than release success', () => {
  const output = python(fake + `\nf=Fake('health',True)\nassert ns['execute'](f)==1\nassert f.events[-1]=='rollback'\n`);
  assert.match(output, /application_rollback=false/);
  assert.doesNotMatch(output, /SUI_RELEASE_PASS/);
});
test('activation and rollback compose calls are limited to application services, never DB recreation', () => {
  python(`
from pathlib import Path
ns['protected_read']=lambda path,*a: ns['ROLLBACK_OVERRIDE'] if path.name=='rollback-override.yml' else b'candidate'
ns['replace_from_protected']=lambda *a: None
ns['create_private']=lambda *a: None
r=ns['Runtime']('${revision}'); r.original_compose=b'old'; r.original_env=b'candidate'
r.image_ids={'backend':'backend-id','gateway':'gateway-id'}
r.initially_running=['trekkey-sui-testnet-backend-1','trekkey-sui-testnet-gateway-1']
events=[]
r.compose=lambda *a,**kw: events.append(('compose',a,kw))
r.inspect=lambda name: {'State':{'Running':True},'Image':name.removeprefix('trekkey-').removesuffix('-1')+'-id'}
r.check_mysql_untouched=lambda: events.append(('mysql-identity-check',))
r.http_readiness=lambda: events.append(('http-readiness-check',))
r.run=lambda args: events.append(('run',args))
r.activate(); r.rollback()
calls=[event[1] for event in events if event[0]=='compose']
assert calls==[('up','-d','--no-deps','--force-recreate','backend','gateway'),('stop','--timeout','30','gateway','backend'),('up','-d','--no-deps','--force-recreate','backend')]
assert all('mysql' not in call and 'down' not in call and 'rm' not in call for call in calls)
assert events[-2:]==[('mysql-identity-check',),('http-readiness-check',)]
`);
});
test('partial additive migration can roll back app, but any new Sui record blocks old worker restart', () => {
  python(`
ns['protected_read']=lambda path,*a: ns['ROLLBACK_OVERRIDE'] if path.name=='rollback-override.yml' else b'old'
ns['create_private']=lambda *a: None
for has_sui in [False,True]:
 r=ns['Runtime']('${revision}'); r.migration_started=True; r.original_env=b'old'; r.initially_running=[]
 r.check_mysql_untouched=lambda: None
 r.http_readiness=lambda: None
 r.inspect=lambda name: {'State':{'Running':True}}
 starts=[]; r.run=lambda args: starts.append(args)
 r.compose=lambda *args,**kw: starts.append((args,kw))
 def mysql(sql):
  if 'information_schema' in sql: return b'1' if "table_name='anc_batch'" in sql else b'0'
  if 'LIKE' in sql: return b'1' if has_sui else b'0'
  return b'0'
 r.mysql=mysql
 try: r.rollback(); refused=False
 except ns['Refusal']: refused=True
 assert refused==has_sui
 assert bool(starts)!=has_sui
`);
});

test('rollback disables old automatic DDL through a saved protected compose override', () => {
  python(`
r=ns['Runtime']('${revision}'); captured=[]
r.run=lambda argv,**kwargs: captured.append((argv,kwargs))
r.compose('up','-d','--no-deps','--force-recreate','backend',old=True)
args,options=captured[0]
assert args.count('-f')==2
assert str(r.audit/'rollback-override.yml') in args
assert 'SPRING_JPA_HIBERNATE_DDL_AUTO: "none"' in ns['ROLLBACK_OVERRIDE'].decode()
assert options['env']['BACKEND_IMAGE']==ns['OLD_IMAGE']
`);
});
