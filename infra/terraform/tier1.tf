# =============================================================================
# Tier 1 — stateful 계층(MySQL / Redis)을 EC2 밖으로 분리
# =============================================================================
# Tier 0에서는 app + MySQL + Redis가 한 EC2 안에서 메모리를 공유했다.
# t3.small(2GB)에서 OOM Killer가 반복 발동한 게 그 구조의 첫 신호였고,
# t3.medium 수직 확장으로 급한 불은 껐지만 "app을 수평 복제하려면 상태
# 저장소가 인스턴스 밖에 있어야 한다"는 한계는 그대로 남았다.
#
# 이 파일은 그 분리를 구현한다:
#   - MySQL  -> RDS (aws_db_instance)
#   - Redis  -> ElastiCache (aws_elasticache_cluster)
#
# main.tf(Tier 0)의 리소스는 콘솔에서 만든 걸 import한 것이지만,
# 여기 리소스는 전부 Terraform이 신규 생성한다.
# =============================================================================


# --- 추가 서브넷 조회 ---------------------------------------------------------
# RDS/ElastiCache의 서브넷 그룹은 "2개 이상의 AZ"에 걸쳐 있어야 생성된다.
# (인스턴스 자체는 한 AZ에만 뜨더라도, 그룹은 다중 AZ를 요구한다 —
#  나중에 Multi-AZ로 승격할 때 AWS가 다른 AZ에 스탠바이를 만들 자리를 미리
#  확보해두는 것.)
# main.tf가 참조하는 subnet-02564bbb2ab677c28은 ap-northeast-2c에 있으므로,
# 여기서는 2a의 기본 서브넷을 하나 더 조회해서 짝을 맞춘다.
data "aws_subnet" "secondary" {
  id = "subnet-01f8bb959fda282fb" # ap-northeast-2a, 계정 기본 서브넷
}


# --- 보안그룹: DB/캐시 전용 ---------------------------------------------------
# 핵심은 source를 0.0.0.0/0이 아니라 "app의 보안그룹"으로 못박는 것.
# 이러면 RDS/Redis에 접근할 수 있는 건 오직 app 보안그룹이 붙은 EC2뿐이고,
# 인터넷에서는 3306/6379 포트가 아예 닫혀 있다.
# (Tier 0의 8080이 전체 공개인 것과 정반대 — DB는 절대 공개하지 않는다.)

resource "aws_security_group" "rds" {
  name        = "drawrace2026-rds"
  description = "RDS MySQL - allow 3306 only from the app security group"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "MySQL from app"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id] # cidr_blocks가 아니라 SG 참조
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "drawrace2026-rds"
  }
}

resource "aws_security_group" "redis" {
  name        = "drawrace2026-redis"
  description = "ElastiCache Redis - allow 6379 only from the app security group"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "Redis from app"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "drawrace2026-redis"
  }
}


# --- 서브넷 그룹 -------------------------------------------------------------
# "이 관리형 서비스를 어느 서브넷들에 놓을지" 목록. Tier 0에는 없던 개념이고,
# 계층을 분리하면 새로 관리해야 하는 포인트 중 하나(§3 트레이드오프에서 언급).

resource "aws_db_subnet_group" "main" {
  name       = "drawrace2026-db-subnet"
  subnet_ids = [data.aws_subnet.app.id, data.aws_subnet.secondary.id]

  tags = {
    Name = "drawrace2026-db-subnet"
  }
}

resource "aws_elasticache_subnet_group" "main" {
  name       = "drawrace2026-cache-subnet"
  subnet_ids = [data.aws_subnet.app.id, data.aws_subnet.secondary.id]
}


# --- RDS 파라미터 그룹 ------------------------------------------------------
# MySQL 서버 설정을 담는 그룹. 여기서는 한글이 깨지지 않도록 문자셋을
# utf8mb4로 명시한다. (Docker의 mysql:8.0 이미지는 기본이 utf8mb4였지만,
# RDS 기본 파라미터 그룹은 값이 다를 수 있어 명시적으로 고정한다.)
# 이것도 Tier 0에는 없던 관리 대상이다.
resource "aws_db_parameter_group" "mysql8" {
  name   = "drawrace2026-mysql8"
  family = "mysql8.0"

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }
}


# --- RDS: MySQL ------------------------------------------------------------
resource "aws_db_instance" "mysql" {
  identifier     = "drawrace2026-mysql"
  engine         = "mysql"
  engine_version = "8.0"
  instance_class = "db.t3.micro" # vCPU 2 / 1GB — 개발/포트폴리오 최소 사이즈

  # 스토리지: 20GB gp3에서 시작, autoscaling으로 100GB까지 자동 확장 허용.
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.mysql8.name
  publicly_accessible    = false # 인터넷에서 직접 접근 불가 (VPC 내부에서만)
  multi_az               = false # 단일 AZ — 고가용성은 Tier 3의 몫

  # 백업: 매일 자동 스냅샷 7일 보존. Tier 0의 docker volume에는 없던 안전장치.
  backup_retention_period = 7
  backup_window           = "17:00-18:00" # UTC = KST 02:00-03:00 (트래픽 적은 시간)
  maintenance_window      = "mon:18:00-mon:19:00"

  # 포트폴리오 환경이라 teardown을 쉽게 둔다:
  #   - skip_final_snapshot: 삭제 시 마지막 스냅샷 안 남김 (보존할 실데이터 없음)
  #   - deletion_protection: 실수 삭제 방지 잠금 해제 (운영이라면 true)
  skip_final_snapshot = true
  deletion_protection = false

  tags = {
    Name = "drawrace2026-mysql"
  }
}


# --- ElastiCache: Redis --------------------------------------------------
# 단일 노드(Cluster Mode Disabled, replica 0). Redis는 현재 세션/캐시
# 용도라 노드 1개로 충분하고, HA가 필요해지면 replication group으로 승격한다.
resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "drawrace2026-redis"
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  tags = {
    Name = "drawrace2026-redis"
  }
}
