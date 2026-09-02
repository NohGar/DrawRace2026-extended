data "aws_vpc" "default" {
  id = "vpc-04964d75bbc0125f4"
}

data "aws_subnet" "app" {
  id = "subnet-02564bbb2ab677c28"
}

data "aws_key_pair" "drawrace2026" {
  key_name = var.key_name
}

resource "aws_security_group" "app" {
  name        = "launch-wizard-1"
  description = "launch-wizard-1 created 2026-08-27T01:35:56.849Z"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "App"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "app" {
  ami                    = "ami-0bc151a94289adb52"
  instance_type          = "t3.medium"
  key_name               = data.aws_key_pair.drawrace2026.key_name
  subnet_id              = data.aws_subnet.app.id
  vpc_security_group_ids = [aws_security_group.app.id]

  root_block_device {
    volume_size           = 20
    volume_type            = "gp3"
    iops                   = 3000
    delete_on_termination  = true
  }

  tags = {
    Name = "drawrace2026-app"
  }
}

resource "aws_eip" "app" {
  domain   = "vpc"
  instance = aws_instance.app.id

  tags = {
    Name = "drawrace2026-eip"
  }
}
