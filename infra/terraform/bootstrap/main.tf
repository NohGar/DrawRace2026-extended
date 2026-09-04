# =============================================================================
# Remote state 백엔드용 S3 버킷
# =============================================================================
# infra/terraform/(본 설정)의 terraform.tfstate를 로컬 대신 여기서 만드는
# 버킷에 저장하기 위한 사전 준비. 락(lock)은 별도 DynamoDB 테이블 없이
# S3 자체 conditional write 기능(Terraform 1.10+, backend의 use_lockfile)으로
# 처리한다 — 관리할 리소스를 하나라도 줄이기 위함.

# 버킷 이름은 전세계에서 유일해야 해서 계정 ID를 붙여 충돌을 피한다.
resource "aws_s3_bucket" "tfstate" {
  bucket = "drawrace2026-tfstate-272736188148"

  # state 파일을 담는 버킷이 실수로 삭제되면 전체 인프라를 다시 import해야
  # 하는 최악의 상황이 온다. terraform destroy로도 못 지우게 잠가둔다.
  lifecycle {
    prevent_destroy = true
  }

  tags = {
    Name    = "drawrace2026-tfstate"
    Purpose = "terraform-remote-state"
  }
}

# 버저닝: state가 손상되거나 잘못 덮어써졌을 때 이전 버전으로 되돌릴 수 있는
# 유일한 안전망. remote state 버킷에는 사실상 필수.
resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

# state 파일에는 DB 비밀번호 등 민감정보가 평문으로 들어갈 수 있어 저장 시
# 암호화(SSE-S3)를 강제한다.
resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# state 버킷은 절대 인터넷에 공개되면 안 된다 (DB 비밀번호가 그대로 노출됨).
# 버킷 단위로 퍼블릭 액세스를 전부 차단.
resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
