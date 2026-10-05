# Progress

## 2026-10-05 — AWS 과금 리소스 종료 여부 점검

### 요청
- AWS 계정(Chrome에 로그인된 콘솔)에서 돈이 나가는 리소스가 모두 꺼졌는지 확인

### 결과: 직접 확인 못 함 (판정 보류)
- 이 세션은 클라우드 컨테이너에서 돌아서 사용자 PC의 Chrome에 접근할 수 없음 (Claude in Chrome 도구가 연결되지 않음)
- 컨테이너 환경 변수의 AWS 키로 `sts:GetCallerIdentity`를 호출하자 AWS가 `InvalidClientTokenId`(무효 키)로 거부
  - 원인을 확인하려던 시도는 권한 정책(자격증명 탐색 금지)에 막혔고, 더 시도하지 않음
- 따라서 "모두 종료됨/아님"은 아직 판단하지 못함

### 대신 만든 것: `aws_cost_check.py` (CloudShell용 읽기 전용 점검 스크립트)
- 활성화된 모든 리전을 대상으로 리전별 점검 80개와 글로벌 점검 6개 수행
  - EC2·EBS·스냅샷·EIP·퍼블릭 IPv4·NAT·LB·RDS·ElastiCache·EKS·ECS·SageMaker·Lightsail·S3·ECR·Route53 등
- Cost Explorer로 지난달/이번달 서비스별 비용과 최근 3일간 매일 과금된 서비스 표시 (`--no-cost`로 생략 가능, 조회 1회당 $0.01)
- `before-call` 가드로 Describe/List/Get/BatchGet 이외의 호출은 실행 전에 차단
- 오프라인 검증 (실제 AWS 호출 없음, 실제 전송 시도 시 즉시 실패하도록 설정)
  - botocore 응답 스키마로 만든 가짜 응답으로 2개 리전을 4가지 조합으로 전체 실행: 오류 0건, 86개 점검 모두 보고 경로 실행
  - 빈 계정이면 "과금되는 리소스를 찾지 못했습니다"로 판정되는 것 확인
  - TerminateInstances / DeleteBucket / StopDBInstance 호출이 가드에 차단되는 것 확인
  - vermin 검사 결과 Python 3.6 이상에서 실행 가능 (CloudShell의 3.9 포함)
- 파일은 세션 첨부로 전달함 (레포에는 넣지 않음)

### 레포에서 확인한 AWS 관련 단서
- 운영 서버: 서울 리전 EC2 `43.200.222.11` (`docs/sui-service-release-2026-09-09.md`), 이미지 저장소는 GHCR (ECR 아님)
- 레포에는 AWS 리소스를 정리·종료했다는 기록이 없음 → 이 EC2와 EBS/스냅샷, EIP를 우선 확인해야 함

### 다음 단계
1. AWS 콘솔 → CloudShell에서 `python3 aws_cost_check.py`를 실행하고 출력을 공유 → 삭제/해제할 대상 정리
2. 콘솔에서 직접 확인하려면 Billing → Bills (이번 달 서비스·리전별), Cost Explorer (일별, 서비스별), EC2 Global View 순서로 보기
3. Chrome을 직접 조작하길 원하면 사용자 PC에서 Claude를 실행 (Claude Desktop 앱 또는 `claude remote-control`)

> 이 파일은 `claude/aws-billing-check-progress` 브랜치에 커밋·푸시함 (main에는 반영하지 않음, PR 없음).
