# 이 블록은 "어떤 도구(AWS provider)로, 어떤 버전으로 인프라를 다룰지" 고정하는 설정.
# 버전을 안 박아두면 나중에 팀원/CI가 다른 버전을 받아서 동작이 미묘하게 달라질 수 있음.
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# 실제 AWS API를 호출할 때 쓸 리전. 인증 정보(Access Key)는 여기 안 적고
# `aws configure`로 로컬에 저장된 값을 그대로 사용함 (코드에 키를 넣지 않기 위함).
provider "aws" {
  region = var.aws_region
}
