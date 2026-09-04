# =============================================================================
# SSH 폐쇄를 위한 SSM Session Manager 전환
# =============================================================================
# 지금까지 EC2 접속(사람 SSH + CI/CD 배포)이 전부 22번 포트를 통했다.
# 22번을 0.0.0.0/0으로 열어두는 건 인터넷 전체에 로그인 시도 표면을 내주는
# 것이라, SSH를 걷어내고 대신 두 곳 모두 AWS IAM 인증 기반의 SSM으로 옮긴다:
#   - 사람 접속: `aws ssm start-session --target <instance-id>`
#   - CI/CD 배포: GitHub Actions가 OIDC로 IAM 역할을 잠깐 빌려 쓰고
#     `aws ssm send-command`로 EC2 위에서 배포 스크립트를 돌린다.
# 두 경로 다 인바운드 포트가 필요 없다 — EC2가 SSM 엔드포인트로 아웃바운드
# HTTPS(443)를 여는 구조라, 보안그룹의 SSH ingress를 통째로 지울 수 있다.

# --- ① EC2가 SSM에 자신을 등록하기 위한 역할 ---------------------------------
# SSM 에이전트(Ubuntu 24.04 AMI에 기본 설치돼있음)가 이 역할을 신뢰해서
# "나는 이 인스턴스다"라고 AWS에 증명하는 데 쓴다. AWS 관리형 정책
# AmazonSSMManagedInstanceCore가 SSM이 필요로 하는 권한 세트를 이미 갖고 있다.
resource "aws_iam_role" "ec2_ssm" {
  name = "drawrace2026-ec2-ssm"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2_ssm.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# 실제 EC2에 붙는 건 role이 아니라 instance profile (role을 감싸는 얇은 래퍼).
resource "aws_iam_instance_profile" "ec2_ssm" {
  name = "drawrace2026-ec2-ssm"
  role = aws_iam_role.ec2_ssm.name
}


# --- ② GitHub Actions가 장기 액세스키 없이 AWS를 잠깐 빌리기 위한 OIDC ------
# 기존 방식(SSH 키를 GitHub Secret에 박아둠)은 유출되면 그걸로 끝인 정적
# 자격증명이었다. OIDC는 그 대신 "이 워크플로우 실행이 진짜 이 리포에서
# 온 게 맞다"는 걸 GitHub가 서명한 토큰으로 증명하고, AWS가 그걸 검증해서
# 실행 시간 동안만 유효한 임시 자격증명을 내준다 — 훔쳐갈 고정 키 자체가 없다.
data "tls_certificate" "github_oidc" {
  url = "https://token.actions.githubusercontent.com/.well-known/openid-configuration"
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.github_oidc.certificates[0].sha1_fingerprint]
}

# 이 역할을 빌려갈 수 있는 대상을 "NohGar/DrawRace2026-extended 리포에서
# 실행된 워크플로우"로만 좁힌다 (sub 클레임 조건) — 다른 리포/포크가
# 같은 OIDC 프로바이더를 통해 이 역할을 가져다 쓰는 걸 막는다.
#
# sub 값은 "repo:OWNER/REPO:..."가 아니라 GitHub가 계정/리포 이름 뒤에
# 불변 숫자 ID를 붙인 "repo:OWNER@계정ID/REPO@리포ID:..." 형태다 (이름이
# 같은 계정/리포가 삭제 후 재생성돼도 예전 신뢰관계를 재사용 못 하게 하는
# GitHub의 최신 방식). CloudTrail에서 실제 실패한 AssumeRoleWithWebIdentity
# 호출의 sub 클레임을 보고 이 값(NohGar=167192674, DrawRace2026-extended=1346773917)을
# 확인했다 — 리포 이름만으로 패턴을 짰다가 처음엔 막혔었다.
resource "aws_iam_role" "github_deploy" {
  name = "drawrace2026-github-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:NohGar@167192674/DrawRace2026-extended@1346773917:*"
        }
      }
    }]
  })
}

# 배포 워크플로우가 실제로 필요한 건 "이 인스턴스 하나에 셸 명령을 보내고
# 그 결과를 읽는" 권한뿐이다. 계정 전체 EC2/SSM 권한을 주지 않고 리소스를
# 이 인스턴스 하나로 못박는다 — 자격증명이 새도 반경이 여기로 한정됨.
resource "aws_iam_role_policy" "github_deploy_ssm" {
  name = "ssm-send-command"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SendCommandToThisInstanceOnly"
        Effect = "Allow"
        Action = ["ssm:SendCommand"]
        Resource = [
          "arn:aws:ec2:${var.aws_region}:${data.aws_caller_identity.current.account_id}:instance/${aws_instance.app.id}",
          "arn:aws:ssm:${var.aws_region}::document/AWS-RunShellScript",
        ]
      },
      {
        Sid      = "ReadCommandResult"
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*" # 이 두 액션은 리소스 수준 제한을 지원하지 않음(AWS 스펙)
      }
    ]
  })
}

data "aws_caller_identity" "current" {}
