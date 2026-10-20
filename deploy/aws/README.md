# Deploying CourtBook on AWS

Do this in Sprint 7, not before. New AWS accounts start on the Free plan with credits, and the account closes when the credits run out or after six months, whichever comes first. Check the current terms on the [AWS Free Tier page](https://aws.amazon.com/free/) when you sign up.

Everything stays in one region. The commands below use `us-east-1`; pick one and stick with it.

## 0. Account safety (10 minutes, do it first)

1. Turn on MFA for the root user. Create an IAM user with the `AdministratorAccess` policy for daily work, set up `aws configure` with it, and stop using root.
2. Billing → Budgets → create a cost budget of **$5** with an email alert.
3. Check Billing → Credits once a week.

## 1. Queue, tables and the instance role

```bash
export AWS_REGION=us-east-1
./deploy/aws/provision.sh
```

That creates the SQS queue and its dead-letter queue, the three DynamoDB tables, and an IAM role (`courtbook-ec2`) that can use only those.

## 2. Security groups

```bash
VPC=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)

APP_SG=$(aws ec2 create-security-group --group-name courtbook-app --description "CourtBook app" \
  --vpc-id "$VPC" --query GroupId --output text)
aws ec2 authorize-security-group-ingress --group-id "$APP_SG" --protocol tcp --port 80 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id "$APP_SG" --protocol tcp --port 443 --cidr 0.0.0.0/0
# SSH only from your current IP
aws ec2 authorize-security-group-ingress --group-id "$APP_SG" --protocol tcp --port 22 \
  --cidr "$(curl -s https://checkip.amazonaws.com)/32"

DB_SG=$(aws ec2 create-security-group --group-name courtbook-db --description "CourtBook RDS" \
  --vpc-id "$VPC" --query GroupId --output text)
# Postgres is reachable only from the app instance's security group
aws ec2 authorize-security-group-ingress --group-id "$DB_SG" --protocol tcp --port 5432 --source-group "$APP_SG"
```

## 3. RDS for PostgreSQL

Smallest instance, single zone, not publicly reachable:

```bash
aws rds create-db-instance \
  --db-instance-identifier courtbook \
  --engine postgres \
  --db-instance-class db.t4g.micro \
  --allocated-storage 20 \
  --db-name courtbook \
  --master-username courtbook \
  --master-user-password 'CHOOSE_A_LONG_PASSWORD' \
  --vpc-security-group-ids "$DB_SG" \
  --no-publicly-accessible \
  --no-multi-az \
  --backup-retention-period 1

aws rds wait db-instance-available --db-instance-identifier courtbook
aws rds describe-db-instances --db-instance-identifier courtbook \
  --query 'DBInstances[0].Endpoint.Address' --output text   # → RDS_HOST
```

Flyway creates the `users`, `booking` and `tournament` schemas on first start. Each service only ever touches its own schema.

## 4. The EC2 instance

Five JVMs need roughly 2–3 GB of RAM with the heap caps in `.env.prod`, so a 4 GB Graviton instance is the safe choice. **Check its hourly price in the console against your remaining credits before launching.**

```bash
AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64 \
  --query Parameter.Value --output text)
aws ec2 create-key-pair --key-name courtbook --query KeyMaterial --output text > ~/.ssh/courtbook.pem
chmod 600 ~/.ssh/courtbook.pem

INSTANCE=$(aws ec2 run-instances --image-id "$AMI" --instance-type t4g.medium \
  --key-name courtbook --security-group-ids "$APP_SG" \
  --iam-instance-profile Name=courtbook-ec2 \
  --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=20,VolumeType=gp3}' \
  --tag-specifications 'ResourceType=instance,Tags=[{Key=Name,Value=courtbook}]' \
  --query 'Instances[0].InstanceId' --output text)
aws ec2 wait instance-running --instance-ids "$INSTANCE"

EIP=$(aws ec2 allocate-address --query AllocationId --output text)
aws ec2 associate-address --instance-id "$INSTANCE" --allocation-id "$EIP"
aws ec2 describe-addresses --allocation-ids "$EIP" --query 'Addresses[0].PublicIp' --output text
```

Point a domain at that IP (an A record). A free subdomain from a dynamic-DNS provider works; Caddy needs a name, not a bare IP, to get a certificate.

## 5. Put the app on the instance

```bash
ssh -i ~/.ssh/courtbook.pem ec2-user@YOUR_IP 'bash -s' < deploy/aws/server-setup.sh
scp -i ~/.ssh/courtbook.pem deploy/docker-compose.prod.yml deploy/Caddyfile deploy/.env.prod.example \
  ec2-user@YOUR_IP:~/courtbook/
ssh -i ~/.ssh/courtbook.pem ec2-user@YOUR_IP
cd ~/courtbook
cp .env.prod.example .env.prod && nano .env.prod     # fill in every CHANGE_ME
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker compose -f docker-compose.prod.yml --env-file .env.prod ps
```

The images are public on GitHub Container Registry, so the instance doesn't need any registry login. No AWS keys go on the instance: the SDK picks up the `courtbook-ec2` role automatically.

## 6. Automatic deploys

`.github/workflows/deploy.yml` builds and pushes images on every merge to `main`, then SSHes in and runs `pull` + `up -d`. Add these repository secrets: `DEPLOY_HOST` (the IP or domain), `DEPLOY_SSH_KEY` (contents of `courtbook.pem`). It also needs SSH open to GitHub's runners; the simplest option is to allow port 22 from `0.0.0.0/0` with key-only login (Amazon Linux disables passwords by default), or deploy by hand with the commands above.

## 7. Before the account closes

1. `pg_dump` the database: from the instance, `docker run --rm postgres:17-alpine pg_dump "postgresql://courtbook:PASSWORD@RDS_HOST/courtbook?sslmode=require" > courtbook.sql`.
2. Record the demo.
3. Either move CourtBook to free hosting (Render for the services, Neon for Postgres, as in the Go project) so the club can keep using it, or tell members ahead of time that it's shutting down.
4. Delete the instance, the Elastic IP (they cost money when unattached), the RDS instance, the tables and the queues.
