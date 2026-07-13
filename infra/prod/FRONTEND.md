# 프론트엔드 배포 (S3 + CloudFront)

프론트엔드는 Vercel에서 **비공개 S3 버킷 + CloudFront(OAC)**로 이관했다. 정적 빌드 산출물을 S3에 올리고 CloudFront가 HTTPS로 서빙한다.

## 구성

| 리소스 | 값 | 역할 |
|---|---|---|
| S3 버킷 | `pulse-frontend-027903150609` (ap-northeast-2, 비공개) | 빌드 산출물 저장 |
| CloudFront 배포 | `E2WHX2XB2FI3AQ` → `d2xpr7sxcwqlz0.cloudfront.net` | HTTPS 서빙·캐시·SPA 폴백 |
| OAC | `E31YBFA69ZC64T` | CloudFront만 S3를 읽도록 서명 |
| ACM 인증서 | `arn:aws:acm:us-east-1:027903150609:certificate/5ed6b44d-23d5-4105-82c0-435c8c4c5eb7` (us-east-1) | `pulsemlb.com`·`www.pulsemlb.com` TLS |
| Route53 호스팅 영역 | `Z02504742OIKZQBJEKWR9` (`pulsemlb.com`) | DNS |

버킷은 퍼블릭 액세스를 전면 차단했고, 버킷 정책으로 위 CloudFront 배포(OAC)만 `s3:GetObject`를 허용한다. S3 정적 웹사이트 호스팅은 쓰지 않는다.

### CloudFront 동작

- **SPA 라우팅 폴백**: 403·404 응답을 `/index.html`(200)로 매핑해 클라이언트 라우팅이 새로고침·직접 진입에서도 동작한다.
- **캐시**: 관리형 `CachingOptimized` 정책이 오리진의 `Cache-Control`을 따른다. 업로드 시 해시 자산은 장기 불변 캐시, `index.html`은 no-cache로 지정한다.
- **기본 루트 객체**: `index.html`. `redirect-to-https`, 압축, HTTP/2·3.

## 도메인 연결 (선행: 네임서버 위임)

`pulsemlb.com`은 외부 등록업체에서 구입했으므로, 등록업체 콘솔에서 **네임서버를 Route53로 위임**해야 아래가 동작한다. 등록업체의 네임서버 설정에 다음 4개를 지정한다.

```
ns-846.awsdns-41.net
ns-478.awsdns-59.com
ns-1674.awsdns-17.co.uk
ns-1413.awsdns-48.org
```

위임이 전파되면(수십 분~수 시간) ACM DNS 검증이 자동 완료된다. 상태 확인:

```bash
aws acm describe-certificate --region us-east-1 \
  --certificate-arn arn:aws:acm:us-east-1:027903150609:certificate/5ed6b44d-23d5-4105-82c0-435c8c4c5eb7 \
  --query "Certificate.Status"
```

`ISSUED`가 되면 아래 "도메인 별칭 연결"을 진행한다.

### 도메인 별칭 연결 (인증서 ISSUED 이후)

1. CloudFront 배포에 별칭(`pulsemlb.com`, `www.pulsemlb.com`)과 ACM 인증서를 붙인다. 현재 config를 받아 `Aliases`·`ViewerCertificate`만 채워 `update-distribution`으로 반영한다.
2. Route53에 프론트 A/AAAA 별칭 레코드를 추가한다(대상: CloudFront 배포, 별칭 호스팅 영역 ID는 CloudFront 고정값 `Z2FDTNDATAQYW2`).

`infra/prod/attach-frontend-domain.sh`가 위 두 단계를 자동화한다(인증서가 `ISSUED`일 때만 실행).

## 수동 배포 절차

```bash
cd frontend
npm ci
VITE_API_BASE_URL=https://api.pulsemlb.com npm run build

# 1) 해시 자산: 장기 불변 캐시 (index.html 제외 → 삭제 대상에서도 제외)
aws s3 sync dist/ s3://pulse-frontend-027903150609/ \
  --delete --exclude "index.html" \
  --cache-control "public,max-age=31536000,immutable"

# 2) index.html: no-cache
aws s3 cp dist/index.html s3://pulse-frontend-027903150609/index.html \
  --cache-control "no-cache,no-store,must-revalidate" \
  --content-type "text/html; charset=utf-8"

# 3) CloudFront 무효화
aws cloudfront create-invalidation \
  --distribution-id E2WHX2XB2FI3AQ --paths "/*"
```

배포 직후 도메인 위임 전에는 `https://d2xpr7sxcwqlz0.cloudfront.net`로 확인한다.

## 자동 배포 (GitHub Actions)

`.github/workflows/frontend-deploy.yml`이 `main`의 `frontend/**` 변경에서 위 절차를 수행한다. 저장소 설정에 다음이 필요하다.

- Secrets: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (S3·CloudFront 배포 권한 IAM 사용자)
- Variables: `FRONTEND_S3_BUCKET=pulse-frontend-027903150609`, `CLOUDFRONT_DISTRIBUTION_ID=E2WHX2XB2FI3AQ`, `VITE_API_BASE_URL=https://api.pulsemlb.com`

변수가 비어 있으면 빌드까지만 하고 배포는 건너뛴다.

## API HTTPS (api.pulsemlb.com)

프론트가 HTTPS이므로 API도 HTTPS여야 한다(혼합 콘텐츠 차단). EC2(`pulse-app`, 43.202.34.229) 앞단에 nginx 리버스 프록시를 두고 Let's Encrypt 인증서를 발급한다. 절차는 `infra/prod/API_HTTPS.md` 참고. 백엔드 CORS 허용 오리진은 `CORS_ALLOWED_ORIGINS`(기본 prod 값 `https://pulsemlb.com,https://www.pulsemlb.com`), 리프레시 쿠키는 `REFRESH_TOKEN_COOKIE_SECURE=true`로 설정한다.
