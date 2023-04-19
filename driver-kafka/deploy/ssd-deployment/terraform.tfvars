public_key_path = "~/.ssh/kafka_aws.pub"
region          = "us-east-2"
az              = "us-east-2c"
ami             = "ami-09e57b4a97b8f8b10" // AlmaLinux 8

instance_types = {
  "kafka"     = "i3en.6xlarge"
  "zookeeper" = "i3en.2xlarge"
  "client"    = "m5n.8xlarge"
}

num_instances = {
  "client"    = 4
  "kafka"     = 3
  "zookeeper" = 3
}
