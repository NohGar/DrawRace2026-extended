# apply/import 끝나고 나서 터미널에 바로 값을 보여주는 용도.
# 매번 AWS 콘솔 들어가서 IP 찾을 필요 없이 `terraform output`으로 확인 가능.

output "instance_id" {
  value = aws_instance.app.id
}

output "public_ip" {
  value = aws_eip.app.public_ip
}

output "security_group_id" {
  value = aws_security_group.app.id
}
