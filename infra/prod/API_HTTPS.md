# API HTTPS (api.pulsemlb.com)

프론트가 HTTPS(CloudFront)이므로 API도 HTTPS여야 브라우저 혼합 콘텐츠 차단을 피한다. EC2(`pulse-app`, EIP 43.202.34.229) 호스트에 nginx 리버스 프록시를 두고 Let's Encrypt 인증서로 `api.pulsemlb.com`을 HTTPS화한다. 백엔드 컨테이너는 계속 `127.0.0.1:8080`만 바라보고, 8080은 외부에 열지 않는다.

## 선행 조건

- `api.pulsemlb.com` A 레코드가 EIP를 가리킨다(Route53 `Z02504742OIKZQBJEKWR9`에 등록 완료). 네임서버 위임이 전파돼 외부에서 해석돼야 certbot HTTP-01 검증이 통과한다.
- 보안 그룹(`pulse-app-sg`, `sg-00dca8014d950a122`)에 80·443 인바운드를 개방한다.

```bash
aws ec2 authorize-security-group-ingress --group-id sg-00dca8014d950a122 \
  --ip-permissions IpProtocol=tcp,FromPort=80,ToPort=80,IpRanges='[{CidrIp=0.0.0.0/0,Description=http-acme}]'
aws ec2 authorize-security-group-ingress --group-id sg-00dca8014d950a122 \
  --ip-permissions IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges='[{CidrIp=0.0.0.0/0,Description=https-api}]'
```

## EC2에서 실행

SSM 또는 SSH로 접속해 실행한다.

```bash
sudo apt-get update
sudo apt-get install -y nginx certbot python3-certbot-nginx
```

`/etc/nginx/sites-available/api.pulsemlb.com`:

```nginx
server {
    listen 80;
    server_name api.pulsemlb.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE(/api/sse): 버퍼링을 끄고 타임아웃을 늘려 스트림이 끊기지 않게 한다.
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        chunked_transfer_encoding on;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/api.pulsemlb.com /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

# Let's Encrypt 발급(자동 갱신 타이머 포함). 이메일은 운영 담당 주소로 지정한다.
sudo certbot --nginx -d api.pulsemlb.com --redirect --agree-tos -m ops@pulsemlb.com --no-eff-email
```

certbot이 443 서버 블록과 인증서 경로, HTTP→HTTPS 리다이렉트를 자동 구성한다. 갱신은 `certbot.timer`가 처리한다.

## 백엔드 설정 반영

`/home/ubuntu/pulse-runtime/.env`에 추가 후 `docker compose -f docker-compose.prod.yml up -d pulse-api`로 재기동한다.

```dotenv
CORS_ALLOWED_ORIGINS=https://pulsemlb.com,https://www.pulsemlb.com
REFRESH_TOKEN_COOKIE_SECURE=true
```

- `pulsemlb.com`(프론트)과 `api.pulsemlb.com`(API)은 같은 등록 도메인이라 SameSite=Lax로도 리프레시 쿠키가 전송된다. 별도 SameSite=None 전환은 불필요하다.
- SSE는 CORS 설정을 그대로 따르므로 위 오리진이 반드시 포함돼야 한다.

## 검증

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://api.pulsemlb.com/api/games
# 프론트에서 로그인·SSE 수신·재발급이 정상 동작하는지 확인한다.
```

`/actuator/health`는 비공개 관리 포트 8081에서만 제공하므로 외부 HTTPS 검증에 사용하지 않는다. 위 명령이 `200`을 반환하면 nginx TLS 종료와 API 프록시가 모두 정상이다.
