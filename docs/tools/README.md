# Trekkey 문서 도구

Markdown가 정본이며 `docs/documentation-home.html`은 검색·탐색용 생성물이다. HTML 내용을 직접 수정하지 않는다.

## 산출물

| 도구 | 입력 | 출력·검사 |
| --- | --- | --- |
| `extract-spring-api.cjs` | Spring controller working tree 또는 Git ref | controller handler와 고유 method+path JSON |
| `build-documentation-readers.cjs` | `docs/**/*.md` 전체 | `docs/documentation-home.html` |
| `verify-documentation-system.cjs` | Markdown, 생성 HTML, working tree 또는 backend ref, sibling frontend | reader 전수 parity, 33 routes, 108 APIs, local links, 기본 secret pattern |

## 실행

`marked`가 일반 Node module path에 없으면 Codex의 workspace dependencies에서 Node와 package path를 얻는다. 현재 desktop runtime 예시는 다음과 같다.

```bash
export TREKKEY_NODE=/Users/winterflw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node
export NODE_PATH=/Users/winterflw/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules

"$TREKKEY_NODE" docs/tools/build-documentation-readers.cjs
"$TREKKEY_NODE" docs/tools/verify-documentation-system.cjs
```

일반 개발 환경에서 `marked`가 설치돼 있다면 다음처럼 실행해도 된다.

```bash
node docs/tools/build-documentation-readers.cjs
node docs/tools/verify-documentation-system.cjs
```

## API 추출

현재 checkout:

```bash
node docs/tools/extract-spring-api.cjs
```

working tree를 바꾸지 않고 최신 통합 ref 감사:

```bash
node docs/tools/extract-spring-api.cjs --ref origin/main
```

출력에는 다음이 포함된다.

- controller handler mapping 수
- 같은 method+path의 consumes overload를 합친 고유 operation 수
- HTTP verb별 고유 operation 수
- controller, Java method, source file

## 검증 계약

`verify-documentation-system.cjs`는 실패 시 non-zero로 종료한다.

1. 필수 정본 문서 존재
2. 모든 `docs/**/*.md`가 reader manifest에 정확히 한 번 포함
3. 상대 Markdown link의 local target 존재
4. `docs/spec/pages.md` marker 구간과 프론트 33개 route exact set 일치
5. `docs/spec/api-catalog.md` marker 구간과 현재 입력의 108개 method+path exact set 일치
6. 문서의 대표적 private key·AWS access key·Notion token pattern 부재

기본 frontend 위치는 sibling `../Trekkey`, backend는 미커밋 파일을 포함한 working tree다. 명시적 ref 감사는 환경변수로 지정하며 현재 카탈로그와 다르면 실패해야 한다. HEAD만 비교해 신규 미커밋 API를 누락하는 검증을 통과로 처리하지 않는다.

```bash
TREKKEY_FRONTEND_ROOT=/absolute/path/to/Trekkey \
TREKKEY_DOCS_BACKEND_REF=HEAD \
node docs/tools/verify-documentation-system.cjs
```

## HTML reader 점검

빌드와 verifier 통과 후 로컬 HTTP server에서 연다.

```bash
python3 -m http.server 4173 --directory docs
```

확인 항목:

1. `documentation-home.html`이 기능 카탈로그를 기본으로 연다.
2. sidebar에서 현재 상태, 화면·API, 설계·기술, 보안·운영, 출품·회의 문서를 선택할 수 있다.
3. 검색으로 `subjectRef`, `졸업요건`, `reconcile`, `POST`를 찾을 수 있다.
4. 문서 내부 local Markdown link가 reader 안에서 전환된다.
5. 현재 문서 목차와 긴 API table이 동작한다.
6. 좁은 viewport에서 sidebar·본문·table을 읽을 수 있다.
7. browser console error가 없다.

## 변경 절차

1. 코드와 현재 ref를 먼저 감사한다.
2. 기능·route·API 정본 Markdown을 수정한다.
3. reader를 재생성한다.
4. verifier, 관련 test, `git diff --check`를 실행한다.
5. browser에서 검색·문서 이동·mobile layout을 확인한다.

생성 성공만으로 완료 처리하지 않는다. 화면을 직접 열어야 한다.
