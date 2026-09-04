# 이 디렉토리(bootstrap)는 "원격 state를 저장할 S3 버킷" 자체를 만드는 곳이라
# 여기서만큼은 역설적으로 로컬 state를 그대로 쓴다 — S3 버킷이 아직 없는데
# 그 버킷을 backend로 쓸 수는 없기 때문 (닭이 먼저냐 달걀이 먼저냐 문제).
# 한 번 apply하고 나면 이 디렉토리는 거의 다시 건드릴 일이 없다.

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = "ap-northeast-2"
}
