#!/bin/bash
# deploy app: systemd unit + start + verify
set -e

# 1. ensure .env has username too
grep -q SPRING_DATASOURCE_USERNAME /opt/grab-system/.env || echo "SPRING_DATASOURCE_USERNAME=grab_app" >> /opt/grab-system/.env
cat /opt/grab-system/.env

# 2. write systemd unit (modeled after rag-*.service)
sudo tee /etc/systemd/system/grab-system.service > /dev/null <<'EOF'
[Unit]
Description=grab-system spring boot app
After=network.target mysql.service redis-server.service

[Service]
User=ubuntu
Group=ubuntu
WorkingDirectory=/opt/grab-system
EnvironmentFile=/opt/grab-system/.env
ExecStart=/usr/bin/java -Xms512m -Xmx1g -XX:MaxMetaspaceSize=256m -jar /opt/grab-system/grab-system-1.0.0.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

# 3. reload + enable + start
sudo systemctl daemon-reload
sudo systemctl enable grab-system
sudo systemctl start grab-system
sleep 10

# 4. verify service active
systemctl is-active grab-system
echo "--- last logs ---"
sudo journalctl -u grab-system -n 10 --no-pager | tail -10
echo "APP_DEPLOY_DONE"
