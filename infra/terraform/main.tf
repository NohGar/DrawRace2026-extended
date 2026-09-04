# === data 블록: 이미 존재하는 리소스를 "조회만" 함, Terraform이 만들거나 지우지 않음 ===
# VPC/서브넷은 AWS 계정 생성 시 자동으로 딸려오는 기본(default) 네트워크라서
# 여기서 새로 만들면 안 되고, 그냥 참조만 해서 아래 인스턴스가 어디에 붙을지 알려줌.

data "aws_vpc" "default" {
  id = "vpc-04964d75bbc0125f4"
}

data "aws_subnet" "app" {
  id = "subnet-02564bbb2ab677c28"
}

# SSH 키페어도 마찬가지로 콘솔에서 이미 만들어둔 걸 이름으로 찾아오는 것.
# private key(.pem)는 Terraform이 알 수도, 관리할 수도 없음 (AWS가 최초 생성 시에만 보여줌).
data "aws_key_pair" "drawrace2026" {
  key_name = var.key_name
}

# === resource 블록: Terraform이 실제로 만들고/바꾸고/지우는 대상 ===

# 보안그룹 = 인스턴스 앞단 방화벽. 원래 EC2 콘솔에서 인스턴스 만들 때
# 자동 생성된 "launch-wizard-1"을 그대로 import해서 가져온 것.
resource "aws_security_group" "app" {
  name        = "launch-wizard-1"
  description = "launch-wizard-1 created 2026-08-27T01:35:56.849Z"
  vpc_id      = data.aws_vpc.default.id

  # 22번 포트: SSH 접속용. 지금 0.0.0.0/0(전세계 허용)으로 열려있는데,
  # 보안상으로는 내 IP만 허용하는 게 맞음 — 나중에 좁히는 걸 고려.
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # 8080번 포트: Spring Boot 앱이 실제로 응답하는 포트. 외부 사용자가
  # API를 호출해야 하니 전체 공개(0.0.0.0/0)가 맞음.
  ingress {
    description = "App"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # 나가는 트래픽(egress)은 전부 허용 — 앱이 GHCR에서 이미지 pull 하거나
  # 외부 API 호출할 때 막히면 안 되니까 기본값 그대로 둠.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# 실제 서버 본체. Docker Compose(app/mysql/redis 컨테이너)가 이 위에서 돌아감.
resource "aws_instance" "app" {
  ami                    = "ami-0bc151a94289adb52" # Ubuntu 24.04 AMI
  instance_type          = "t3.medium"             # 2GB(t3.small)에서 OOM 나서 4GB로 결정된 사이즈
  key_name               = data.aws_key_pair.drawrace2026.key_name
  subnet_id              = data.aws_subnet.app.id
  vpc_security_group_ids = [aws_security_group.app.id]

  # SSM Session Manager로 접속하기 위한 필수 조건 — 이 프로필이 없으면
  # 에이전트가 떠 있어도 AWS에 자신을 등록하지 못한다 (ssm.tf 참고).
  iam_instance_profile = aws_iam_instance_profile.ec2_ssm.name

  # 루트 볼륨(OS + 도커 이미지 저장 공간) 설정. gp3는 SSD 계열 중 범용 타입.
  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    iops                  = 3000
    delete_on_termination = true # 인스턴스 지우면 볼륨도 같이 삭제 (남겨두고 싶으면 false)
  }

  tags = {
    Name = "drawrace2026-app"
  }
}

# 고정 공인 IP(Elastic IP). 인스턴스를 stop/start 하면 원래는 공인 IP가 바뀌는데,
# EIP를 붙여두면 재기동해도 같은 IP(43.202.171.23)를 계속 씀 — GitHub Secret(EC2_HOST)이
# 매번 안 바뀌어도 되게 하기 위한 목적.
resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = {
    Name = "drawrace2026-eip"
  }
}
