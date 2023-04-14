public_key_path = "~/.ssh/timebase-aws.pub"
region          = "us-east-2"
az              = "us-east-2a"
ami             = "ami-0b0f111b5dcb2800f" // Amazon Linux 2

instance_types = {
  "timebase" = "i3en.6xlarge"
  "client"   = "m5n.8xlarge"
}

num_instances = {
  "client"   = 1
  "timebase" = 3
}
