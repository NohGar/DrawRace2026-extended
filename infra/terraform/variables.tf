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
