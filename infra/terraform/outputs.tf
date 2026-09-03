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

# --- Tier 1 ---
# 앱의 .env에 넣을 접속 주소. host:port 형태가 아니라 host만 나오므로
# JDBC URL 조립 시 :3306을 붙인다.
output "rds_endpoint" {
  description = "RDS MySQL endpoint hostname (append :3306)"
  value       = aws_db_instance.mysql.address
}

output "redis_endpoint" {
  description = "ElastiCache Redis primary endpoint hostname"
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}
