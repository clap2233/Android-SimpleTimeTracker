#!/usr/bin/env bash
set -euo pipefail
# Upload the built dist/ and this deploy/ directory to a fresh release folder first.
release_dir=$(pwd)
backup_name=${1:-dist-pre-v3}
[[ "$backup_name" =~ ^dist-[a-zA-Z0-9-]+$ ]] || { echo 'Invalid backup directory name' >&2; exit 1; }
backup_dir="/opt/life-clock/app/$backup_name"
test -f "$release_dir/dist/server/main.js"
test -f /opt/life-clock/app/package.json
test ! -e "$backup_dir"
test ! -e /opt/life-clock/app/dist-v3-staged
/opt/life-clock/node/bin/node --check "$release_dir/dist/server/main.js"
sudo /opt/life-clock/node/bin/node "$release_dir/deploy/initialize-secrets.mjs"
sudo chown ubuntu:ubuntu /home/ubuntu/life-clock-ai-connection.json
sudo chmod 600 /home/ubuntu/life-clock-ai-connection.json
cp -a "$release_dir/dist" /opt/life-clock/app/dist-v3-staged
sudo systemctl stop life-clock.service
mv /opt/life-clock/app/dist "$backup_dir"
mv /opt/life-clock/app/dist-v3-staged /opt/life-clock/app/dist
if sudo systemctl start life-clock.service && curl --fail --silent --retry 10 --retry-connrefused --retry-delay 1 --retry-max-time 20 http://127.0.0.1:4178/health; then
    echo 'V3 service active. Previous dist retained for rollback; backup data untouched.'
else
    sudo systemctl stop life-clock.service
    mv /opt/life-clock/app/dist /opt/life-clock/app/dist-v3-failed
    mv "$backup_dir" /opt/life-clock/app/dist
    sudo systemctl start life-clock.service
    echo 'V3 health check failed; previous service restored.' >&2
    exit 1
fi
