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
