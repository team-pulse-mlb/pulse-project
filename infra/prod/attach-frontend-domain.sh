#!/usr/bin/env bash
# CloudFront 배포에 커스텀 도메인(별칭 + ACM 인증서)을 붙이고 Route53 별칭 레코드를 만든다.
# 선행 조건: pulsemlb.com 네임서버가 Route53로 위임되어 ACM 인증서가 ISSUED 상태여야 한다.
#
# 사용: bash infra/prod/attach-frontend-domain.sh
set -euo pipefail

DISTRIBUTION_ID="E2WHX2XB2FI3AQ"
CERT_ARN="arn:aws:acm:us-east-1:027903150609:certificate/5ed6b44d-23d5-4105-82c0-435c8c4c5eb7"
ZONE_ID="Z02504742OIKZQBJEKWR9"
CF_ZONE_ID="Z2FDTNDATAQYW2"   # CloudFront 별칭용 고정 호스팅 영역 ID
DOMAINS=("pulsemlb.com" "www.pulsemlb.com")

echo "[1/3] ACM 인증서 상태 확인"
status=$(aws acm describe-certificate --region us-east-1 --certificate-arn "$CERT_ARN" \
  --query "Certificate.Status" --output text)
if [ "$status" != "ISSUED" ]; then
  echo "인증서가 아직 ISSUED가 아니다(현재: $status). 네임서버 위임·검증 완료 후 다시 실행한다." >&2
  exit 1
fi

echo "[2/3] CloudFront 배포에 별칭·인증서 연결"
tmp=$(mktemp -d)
aws cloudfront get-distribution-config --id "$DISTRIBUTION_ID" > "$tmp/dist.json"
etag=$(python -c "import json,sys;print(json.load(open('$tmp/dist.json'))['ETag'])")
python - "$tmp/dist.json" "$tmp/config.json" "$CERT_ARN" "${DOMAINS[@]}" <<'PY'
import json, sys
src, out, cert = sys.argv[1], sys.argv[2], sys.argv[3]
domains = sys.argv[4:]
cfg = json.load(open(src))["DistributionConfig"]
cfg["Aliases"] = {"Quantity": len(domains), "Items": domains}
cfg["ViewerCertificate"] = {
    "ACMCertificateArn": cert,
    "SSLSupportMethod": "sni-only",
    "MinimumProtocolVersion": "TLSv1.2_2021",
    "Certificate": cert,
    "CertificateSource": "acm",
}
json.dump(cfg, open(out, "w"))
PY
aws cloudfront update-distribution --id "$DISTRIBUTION_ID" \
  --distribution-config "file://$tmp/config.json" --if-match "$etag" >/dev/null
echo "  별칭 연결 완료"

echo "[3/3] Route53 프론트 별칭 레코드 생성"
changes=""
for d in "${DOMAINS[@]}"; do
  for t in A AAAA; do
    changes="$changes{\"Action\":\"UPSERT\",\"ResourceRecordSet\":{\"Name\":\"$d\",\"Type\":\"$t\",\"AliasTarget\":{\"HostedZoneId\":\"$CF_ZONE_ID\",\"DNSName\":\"d2xpr7sxcwqlz0.cloudfront.net\",\"EvaluateTargetHealth\":false}}},"
  done
done
cat > "$tmp/records.json" <<EOF
{ "Comment": "frontend alias to CloudFront", "Changes": [ ${changes%,} ] }
EOF
aws route53 change-resource-record-sets --hosted-zone-id "$ZONE_ID" \
  --change-batch "file://$tmp/records.json" --query "ChangeInfo.Status" --output text

echo "완료. https://pulsemlb.com 으로 확인한다(전파에 수 분 소요)."
