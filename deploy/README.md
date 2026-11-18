# 배포 (PLAN T-013)

VM 1대에 Docker Compose로 MySQL·백엔드·프론트·Caddy(HTTPS)를 띄운다 ([ARCHITECTURE 9장](../docs/ARCHITECTURE.md)). 프론트는 `https://app.<도메인>`, API는 `https://api.<도메인>`이다. 둘 다 같은 등록 도메인 아래라 로그인 쿠키가 first-party로 오간다 ([ADR-0002](../docs/adr/0002-auth-token-cookie.md)).

| 파일 | 내용 |
|---|---|
| `compose.yaml` | 서버에서 도는 서비스. MySQL과 백엔드는 밖으로 포트를 열지 않고, Caddy만 80·443을 연다 |
| `Caddyfile` | `app.` → 프론트, `api.` → 백엔드. 인증서는 Caddy가 Let's Encrypt에서 자동으로 받고 갱신한다 |
| `deploy.sh` | 내 컴퓨터에서 이미지를 빌드해 서버로 보내고 다시 띄운다. 레지스트리를 쓰지 않는다 |
| `../backend/Dockerfile`, `../frontend/Dockerfile` | 운영 이미지 |

백업·복구·모니터링 같은 운영 마감은 T-065에서 한다.

## 사람이 준비할 것 (PLAN H-6, H-7)

1. **VM**: 메모리 2GB 이상(MySQL·백엔드 JVM·프론트가 함께 돈다). 리눅스에 Docker Engine과 Compose 플러그인을 설치하고, 방화벽(보안 그룹)에서 22(SSH), 80, 443(TCP·UDP)을 연다. EC2로 만드는 순서는 아래 "EC2로 서버 만들기".
2. **SSH**: 내 컴퓨터에서 키로 접속되게 하고 `~/.ssh/config`에 이름을 붙여 두면 편하다(예: `every-study-vm`). 접속 사용자가 `sudo` 없이 `docker`를 쓸 수 있어야 한다(`docker` 그룹).
3. **도메인과 DNS**: 도메인을 사서 A 레코드 두 개를 VM의 공인 IP로 둔다: `app.<도메인>`, `api.<도메인>`. Cloudflare DNS를 쓰면 프록시(주황 구름)는 끈다(Caddy가 인증서를 직접 받는다).
4. **OAuth 리다이렉트 URI** (H-7): 카카오·구글 콘솔에 `https://api.<도메인>/login/oauth2/code/kakao`, `https://api.<도메인>/login/oauth2/code/google`을 추가한다. 구글은 승인된 JavaScript 원본에 `https://app.<도메인>`도 둔다.
5. **카카오맵** (H-2·H-7): 지도를 쓸 때만. JavaScript SDK 도메인에 `https://app.<도메인>`을 추가한다. 지도를 켜지 않은 임시 환경에서는 `NEXT_PUBLIC_SAMPLE_PLACES=true`로 빌드해 예시 장소로 시험한다.

## EC2로 서버 만들기 (예: 서울 리전 t4g.small)

처음 사용자가 적을 때의 기준이다. 메모리 예산은 대략 OS·Docker 200MB, MySQL 400~500MB, 백엔드 700MB 안팎(힙 512MB + JVM 나머지), 프론트 150MB, Caddy 30MB로 2GB 안에 들어가고, 순간적으로 몰리는 몫은 스왑 2GB가 받는다. 관측 도구를 붙일 때는 서버에 수집기만 둔다(PLAN T-065 메모).

1. **요금 알림부터**: AWS Budgets에서 월 예산 알림(예: $30)을 만든다. 2026-11 기준 t4g.small + 디스크 20GB + 공인 IPv4로 대략 월 $21에 부가세가 붙는다. 정확한 단가는 AWS 요금 계산기로 확인한다.
2. **인스턴스**: 리전 아시아 태평양(서울), AMI는 Ubuntu Server LTS **64비트(Arm)**, 유형 `t4g.small`(2 vCPU, 2GB), 스토리지 gp3 20GB. 키 페어를 새로 만들어 내려받는다(`chmod 400`). Arm이라 Apple Silicon Mac에서 이미지를 바로 빌드한다(`deploy.sh`가 서버 CPU를 보고 맞춘다).
3. **보안 그룹**: 인바운드 22(TCP, **내 IP만**), 80(TCP), 443(TCP), 443(UDP, HTTP/3). 나머지는 열지 않는다(MySQL은 밖으로 열 필요가 없다).
4. **탄력적 IP**: 하나 할당해 인스턴스에 연결하고, DNS A 레코드는 이 주소로 둔다. 자동 공인 IP는 인스턴스를 멈췄다 켜면 바뀐다.
5. **접속 설정** (내 컴퓨터 `~/.ssh/config`):
   ```
   Host every-study-vm
     HostName <탄력적 IP>
     User ubuntu
     IdentityFile ~/.ssh/<키 파일>.pem
   ```
6. **스왑 2GB** (서버에서 한 번):
   ```bash
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
   sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab   # 재부팅 뒤에도 켜지게
   free -h                                                       # Swap 2.0Gi 확인
   ```
7. **Docker**: Docker 공식 문서의 Ubuntu 설치 방법(apt 저장소)으로 Docker Engine과 Compose 플러그인을 설치한 뒤, `sudo usermod -aG docker ubuntu` 하고 다시 접속한다. `docker compose version`이 나오면 된다. Docker는 부팅 때 자동으로 켜지고, 컨테이너는 `restart: unless-stopped`로 함께 올라온다.

백엔드 힙은 서버 환경 파일의 `BACKEND_HEAP`(기본 `512m`)으로 바꾼다. 인스턴스를 키우면 함께 올린다.

새벽 시간대에 인스턴스를 멈춰 비용을 줄이는 방법은 지금 쓰지 않는다. 멈춰도 디스크·탄력적 IP 요금은 계속 나가 절약이 작고(하루 6시간이면 월 $4 정도), 예약 알림(ADR-0005)이 그 시간에 빠질 수 있다. 알림 기능이 생긴 뒤 다시 판단한다(PLAN T-065 메모).

## 서버 환경 파일

서버의 배포 디렉토리(기본 `~/every-study`)에 `.env`를 만든다. 사람이 직접 쓰고, 저장소에 올리지 않는다. 키 이름은 저장소 루트의 `.env.example` "운영 배포" 묶음에 있다.

```bash
ssh every-study-vm
mkdir -p ~/every-study && cd ~/every-study
nano .env      # 아래 키를 채운다
chmod 600 .env
```

필요한 키: `APP_DOMAIN`, `ACME_EMAIL`, `MYSQL_USER`, `MYSQL_PASSWORD`, `JWT_SECRET`, `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. 선택 키는 `BACKEND_HEAP`. 빠진 키가 있으면 `docker compose`가 키 이름을 알려 주고 멈춘다. 로컬과 다른 값(특히 `JWT_SECRET`, `MYSQL_PASSWORD`)을 새로 만들어 쓴다.

## 배포 (내 컴퓨터에서, 저장소 루트)

```bash
# 처음, 또는 둘 다 바꿨을 때
DEPLOY_HOST=every-study-vm APP_DOMAIN=example.com NEXT_PUBLIC_SAMPLE_PLACES=true bash deploy/deploy.sh all

# 하나만 다시 배포
DEPLOY_HOST=every-study-vm APP_DOMAIN=example.com bash deploy/deploy.sh backend
DEPLOY_HOST=every-study-vm APP_DOMAIN=example.com NEXT_PUBLIC_SAMPLE_PLACES=true bash deploy/deploy.sh frontend
```

- 서버 CPU(amd64·arm64)를 확인해 그에 맞는 이미지를 만든다. Apple Silicon Mac에서 amd64 이미지를 만들면 시간이 더 걸린다.
- 백엔드는 시작할 때 Flyway가 DB 스키마를 맞춘다.
- 처음 배포하면 Caddy가 인증서를 받느라 1~2분 걸릴 수 있다. DNS가 아직 퍼지지 않았으면 실패하고 다시 시도한다.

## 확인과 문제 해결 (서버에서, 배포 디렉토리)

| 하려는 일 | 명령 |
|---|---|
| 상태 | `docker compose ps` |
| 기록 | `docker compose logs -f backend` (`frontend`, `caddy`, `mysql`) |
| 다시 시작 | `docker compose restart backend` |
| 모두 끄기 (데이터는 볼륨에 남음) | `docker compose down` |

- 로그인 뒤 다시 로그인 화면으로 돌아오면: `AUTH_COOKIE_DOMAIN`이 `APP_DOMAIN`과 같은지(compose가 맞춰 넣는다), 브라우저 주소가 `app.<도메인>`인지 확인한다.
- 공급자가 `redirect_uri_mismatch`를 주면: 콘솔에 등록한 주소가 `https://api.<도메인>/login/oauth2/code/<공급자>`와 글자까지 같은지 확인한다.
