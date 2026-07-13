# PULSE 라이브 원본 수집기 배포: S3 버킷, IAM Role, Lambda, EventBridge 1분 규칙을 만든다.
# 사용: .\raw-archive\deploy\deploy-collector.ps1 -ApiKey "<balldontlie API key>" [-Region ap-northeast-2]
param(
    [Parameter(Mandatory = $true)][string]$ApiKey,
    [string]$Region = "ap-northeast-2"
)

$ErrorActionPreference = "Stop"
$FunctionName = "pulse-collector"
$RoleName = "pulse-collector-role"
$RuleName = "pulse-collector-every-minute"

$AccountId = (aws sts get-caller-identity --query Account --output text)
if (-not $AccountId) { throw "AWS 자격 증명이 없습니다. aws configure를 먼저 실행하세요." }
$Bucket = "pulse-raw-$AccountId"
Write-Host "계정: $AccountId / 리전: $Region / 버킷: $Bucket"

# --- S3 버킷 ---
cmd /c "aws s3api head-bucket --bucket $Bucket 2>nul" | Out-Null
if ($LASTEXITCODE -ne 0) {
    if ($Region -eq "us-east-1") {
        aws s3api create-bucket --bucket $Bucket --region $Region | Out-Null
    } else {
        aws s3api create-bucket --bucket $Bucket --region $Region --create-bucket-configuration LocationConstraint=$Region | Out-Null
    }
    Write-Host "버킷 생성 완료: $Bucket"
} else {
    Write-Host "버킷이 이미 있습니다: $Bucket"
}

# --- IAM Role ---
# Windows PowerShell에서는 인라인 JSON 따옴표가 깨질 수 있어 파일로 넘긴다.
$TrustFile = Join-Path $env:TEMP "pulse-trust-policy.json"
'{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}' |
    Out-File -Encoding ascii $TrustFile
cmd /c "aws iam get-role --role-name $RoleName 2>nul" | Out-Null
if ($LASTEXITCODE -ne 0) {
    aws iam create-role --role-name $RoleName --assume-role-policy-document "file://$TrustFile" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "IAM Role 생성 실패" }
    Write-Host "IAM Role 생성 완료: $RoleName (전파 대기 중...)"
    Start-Sleep -Seconds 10
} else {
    Write-Host "IAM Role이 이미 있습니다: $RoleName"
}
$PolicyFile = Join-Path $env:TEMP "pulse-role-policy.json"
@"
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject"],"Resource":"arn:aws:s3:::$Bucket/*"},
 {"Effect":"Allow","Action":["s3:ListBucket"],"Resource":"arn:aws:s3:::$Bucket"},
 {"Effect":"Allow","Action":["logs:CreateLogGroup","logs:CreateLogStream","logs:PutLogEvents"],"Resource":"*"}
]}
"@ | Out-File -Encoding ascii $PolicyFile
aws iam put-role-policy --role-name $RoleName --policy-name pulse-collector-policy --policy-document "file://$PolicyFile"
if ($LASTEXITCODE -ne 0) { throw "IAM 정책 적용 실패" }
$RoleArn = "arn:aws:iam::${AccountId}:role/$RoleName"

# --- Lambda 패키지 ---
$Zip = Join-Path $env:TEMP "pulse-collector.zip"
if (Test-Path $Zip) { Remove-Item $Zip -Force }
Compress-Archive -Path "$PSScriptRoot\..\live-collector\collect_live_raw.py" -DestinationPath $Zip
# PA_ROUND_STRIDE=1이면 라이브 PA를 매 서브 폴링 라운드마다 수집한다.
$EnvVars = "Variables={BDL_API_KEY=$ApiKey,BUCKET=$Bucket,POLL_PA=true,SUBPOLL_INTERVAL=10,PA_ROUND_STRIDE=1,LIVE_GAME_WORKERS=8}"

cmd /c "aws lambda get-function --function-name $FunctionName --region $Region 2>nul" | Out-Null
if ($LASTEXITCODE -ne 0) {
    aws lambda create-function --function-name $FunctionName --region $Region `
        --runtime python3.12 --handler collect_live_raw.handler --role $RoleArn `
        --zip-file "fileb://$Zip" --timeout 55 --memory-size 256 `
        --environment $EnvVars | Out-Null
    Write-Host "Lambda 생성 완료: $FunctionName"
} else {
    aws lambda update-function-code --function-name $FunctionName --region $Region --zip-file "fileb://$Zip" | Out-Null
    aws lambda wait function-updated --function-name $FunctionName --region $Region
    aws lambda update-function-configuration --function-name $FunctionName --region $Region `
        --timeout 55 --memory-size 256 --handler collect_live_raw.handler --environment $EnvVars | Out-Null
    Write-Host "Lambda 업데이트 완료: $FunctionName"
}
aws lambda wait function-active --function-name $FunctionName --region $Region

# --- EventBridge 1분 규칙 ---
aws events put-rule --name $RuleName --schedule-expression "rate(1 minute)" --region $Region | Out-Null
$FnArn = "arn:aws:lambda:${Region}:${AccountId}:function:$FunctionName"
cmd /c "aws lambda add-permission --function-name $FunctionName --region $Region --statement-id eventbridge-invoke --action lambda:InvokeFunction --principal events.amazonaws.com --source-arn arn:aws:events:${Region}:${AccountId}:rule/$RuleName 2>nul" | Out-Null
aws events put-targets --rule $RuleName --region $Region --targets "Id=pulse-collector,Arn=$FnArn" | Out-Null
Write-Host "EventBridge 규칙 연결 완료: $RuleName -> $FunctionName (1분 주기)"

# --- 1회 실행 확인 ---
Write-Host "`n1회 테스트 실행 중..."
$Out = Join-Path $env:TEMP "pulse-collector-test.json"
aws lambda invoke --function-name $FunctionName --region $Region --payload '{}' $Out | Out-Null
Get-Content $Out
Write-Host "`n완료. 원본 데이터는 s3://$Bucket/raw/ 아래에 쌓입니다."
