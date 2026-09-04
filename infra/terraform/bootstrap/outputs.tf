output "tfstate_bucket" {
  description = "S3 bucket name to reference in the main config's backend \"s3\" block"
  value       = aws_s3_bucket.tfstate.id
}
