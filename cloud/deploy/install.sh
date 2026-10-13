#!/usr/bin/env bash
set -euo pipefail
# Run from the uploaded release folder; do not replace the existing HTTP site.
release_dir=$(pwd)
node_archive=/home/ubuntu/node-v22.23.3-linux-x64.tar.xz
printf '%s  %s\n' 'df450af89261115ef9f9e3830c3eeb2cc9213b63c720b1af623cb5dcbe2e02de' "$node_archive" | sha256sum --check --status
sudo install -d -m 755 /opt/life-clock
if [ ! -x /opt/life-clock/node/bin/node ]; then
    sudo install -d -m 755 /opt/life-clock/node
    sudo tar -xJf "$node_archive" -C /opt/life-clock/node --strip-components=1
fi
export PATH=/opt/life-clock/node/bin:$PATH
node --version
sudo install -d -o ubuntu -g ubuntu -m 755 /opt/life-clock/app
if sudo systemctl is-active --quiet life-clock.service; then sudo systemctl stop life-clock.service; fi
# The tar archive was prepared without node_modules, data or credentials.
tar -xzf life-clock-release.tar.gz -C /opt/life-clock/app
cd /opt/life-clock/app
npm ci --omit=dev --no-audit --no-fund
if ! id lifeclock >/dev/null 2>&1; then sudo useradd --system --home /var/lib/life-clock --shell /usr/sbin/nologin lifeclock; fi
sudo install -d -o lifeclock -g lifeclock -m 700 /var/lib/life-clock
sudo /opt/life-clock/node/bin/node deploy/initialize-secrets.mjs
sudo chown ubuntu:ubuntu /home/ubuntu/life-clock-connection.json
sudo chmod 600 /home/ubuntu/life-clock-connection.json
sudo install -m 644 deploy/life-clock.service /etc/systemd/system/life-clock.service
sudo systemctl daemon-reload
sudo systemctl enable --now life-clock.service
curl --fail --silent --retry 10 --retry-connrefused --retry-delay 1 --retry-max-time 20 http://127.0.0.1:4178/health
# Use a dedicated virtual environment instead of changing system Python packages.
if [ ! -x /opt/life-clock/certbot/bin/certbot ]; then
    if ! dpkg-query -W -f='${Status}' python3-venv 2>/dev/null | grep -q 'install ok installed'; then
        sudo apt-get update -qq
        sudo apt-get install -y python3-venv
    fi
    sudo python3 -m venv /opt/life-clock/certbot
    sudo /opt/life-clock/certbot/bin/pip install 'certbot>=5.4,<6'
fi
sudo /opt/life-clock/certbot/bin/certbot --version
if [ ! -f /etc/letsencrypt/live/124.221.50.129/fullchain.pem ]; then
    sudo /opt/life-clock/certbot/bin/certbot certonly --non-interactive --agree-tos --register-unsafely-without-email --preferred-profile shortlived --webroot --webroot-path /var/www/html --ip-address 124.221.50.129 --cert-name 124.221.50.129
fi
sudo install -m 644 deploy/life-clock.nginx /etc/nginx/sites-available/life-clock
sudo ln -sfn /etc/nginx/sites-available/life-clock /etc/nginx/sites-enabled/life-clock
sudo nginx -t
sudo systemctl reload nginx
sudo install -m 644 deploy/life-clock-renew.service /etc/systemd/system/life-clock-renew.service
sudo install -m 644 deploy/life-clock-renew.timer /etc/systemd/system/life-clock-renew.timer
sudo systemctl daemon-reload
sudo systemctl enable --now life-clock-renew.timer
sudo /opt/life-clock/certbot/bin/certbot renew --cert-name 124.221.50.129 --dry-run --run-deploy-hooks --deploy-hook '/usr/sbin/nginx -t && /usr/bin/systemctl reload nginx'
sudo systemctl is-active life-clock.service life-clock-renew.timer
echo 'Life Clock installation and certificate renewal checks complete.'
