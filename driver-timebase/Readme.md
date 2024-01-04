# Timebase driver for OpenMessaging framework

## Testing environment deployment

The deployment consists of two steps:
1. Configuration of testing infrastructure on AWS with Terraform
2. Timebase and OpenMessaging clients provisioning with Ansible

**Note:** since Terraform and Ansible resources are located within _deploy_ directory, it's assumed that all subsequent commands will be executed from this directory as well.

To proceed with environment preparation, follow the instruction below:
1. Initialize Terraform modules by running (this should be executed only once, and isn't required for next testing' rounds):

```bash
terraform init
```

2. Deploy testing infrastructure:

```bash
terraform apply --auto-approve
```

3. Configure software provisioning with Ansible by setting up environment variables from the table below:

|        Variable         |                    Description                     |
|-------------------------|----------------------------------------------------|
| TESTING_REGISTRY_URL    | URL of Docker registry, containing Timebase images |
| TESTING_REGISTRY_USER   | Username to access the registry                    |
| TESTING_REGISTRY_PASS   | Password to access the registry                    |
| TESTING_TIMEBASE_IMAGE  | Full image name for Timebase                       |
| TESTING_TIMEBASE_TAG    | Timebase image tag                                 |
| TESTING_TIMEBASE_SERIAL | Timebase serial number                             |

4. Provision testing software with Ansible:

```bash
ansible-playbook -i hosts.ini deploy-test.yml
```

5. When testing is finished, the underlying infrastructure could be decommissioned with the following Terraform command:

```bash
terraform destroy --auto-approve
```

## Testing environment usage

Testing environment for Timebase consists of 4 nodes:
- 3 server nodes for Timebase cluster
- 1 client node for test execution

To run the test, login to deployed client node (its IP address will be shown at the end of Ansible playbook's execution) and run the commands below:

```bash
cd /opt/benchmark
sudo bin/benchmark --drivers driver-timebase/timebase-latency.yaml workloads/1-topic-16-partition-256b.yaml
```

When the test will finish, a JSON file with results will be stored within _/opt/benchmark_ directory. Use the following command to retrieve this file to your local machine:

```bash
scp -i PATH/TO/PRIVATE_KEY ec2-user@CLIENT_IP:/opt/benchmark/*.json .
```

