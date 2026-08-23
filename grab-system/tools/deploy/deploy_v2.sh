#!/bin/bash
# deploy v2: mysql buffer pool 512M + jvm heap 1536m + restart
set -e

echo "=== [1/3] mysql buffer pool ==="
sudo tee /etc/mysql/mysql.conf.d/zz-grab-tuning.cnf > /dev/null <<'EOF'
[mysqld]
innodb_buffer_pool_size = 512M
EOF
sudo systemctl restart mysql
sudo mysql -e "SHOW VARIABLES LIKE 'innodb_buffer_pool_size';"

echo "=== [2/3] systemd heap 1536m ==="
sudo tee /etc/systemd/system/grab-system.service > /dev/null <<'EOF'
[Unit]
Description=grab-system spring boot app
After=network.target mysql.service redis-server.service

[Service]
User=ubuntu
Group=ubuntu
WorkingDirectory=/opt/grab-system
EnvironmentFile=/opt/grab-system/.env
ExecStart=/usr/bin/java -Xms512m -Xmx1536m -XX:MaxMetaspaceSize=256m -jar /opt/grab-system/grab-system-1.0.0.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF
sudo systemctl daemon-reload
sudo systemctl enable grab-system
sudo systemctl start grab-system
sleep 12

echo "=== [3/3] verify ==="
systemctl is-active grab-system
sudo journalctl -u grab-system -n 5 --no-pager | tail -5
echo "DEPLOY_V2_DONE"
