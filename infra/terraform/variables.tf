# 값이 바뀔 수 있는 것들을 코드에 하드코딩하지 않고 변수로 빼둔 것.
# default가 있어서 지금은 그냥 이 값 그대로 쓰이지만, 나중에 리전을 바꾸거나
# 다른 키페어를 쓰게 되면 -var 옵션이나 .tfvars 파일로 덮어쓸 수 있음.

variable "aws_region" {
  description = "AWS region hosting the infrastructure"
  type        = string
  default     = "ap-northeast-2"
}

variable "key_name" {
  description = "Name of the existing EC2 key pair used for SSH access"
  type        = string
  default     = "drawrace2026"
}

# --- Tier 1 (RDS) ---
# DB 접속정보는 코드/state에 하드코딩하지 않고 변수로 받는다.
# default가 없으므로 terraform.tfvars(gitignore됨)에 값을 넣어야 apply가 된다.
# 예) terraform.tfvars:
#   db_username = "drawrace"
#   db_password = "<강한 비밀번호>"

variable "db_name" {
  description = "Initial database name created on the RDS instance"
  type        = string
  default     = "team05_db"
}

variable "db_username" {
  description = "Master username for the RDS MySQL instance"
  type        = string
}

variable "db_password" {
  description = "Master password for the RDS MySQL instance"
  type        = string
  sensitive   = true # plan/apply 출력에 값이 노출되지 않게 함
}
