#!/bin/bash
# deploy v3: mysql flush=2 + nginx keepalive/worker_connections + new jar
set -e

echo "=== [1/4] mysql flush_log_at_trx_commit=2 ==="
sudo tee /etc/mysql/mysql.conf.d/zz-grab-tuning.cnf > /dev/null <<'EOF'
[mysqld]
innodb_buffer_pool_size = 512M
innodb_flush_log_at_trx_commit = 2
EOF
sudo systemctl restart mysql
sudo mysql -e "SHOW VARIABLES LIKE 'innodb_flush_log_at_trx_commit';"

echo "=== [2/4] nginx worker_connections + keepalive upstream ==="
sudo sed -i 's/worker_connections 768;/worker_connections 2048;/' /etc/nginx/nginx.conf
sudo tee /etc/nginx/sites-enabled/grab-system > /dev/null <<'EOF'
upstream grab_backend {
    server 127.0.0.1:8080;
    keepalive 32;
}

server {
    listen 80;
    server_name 124.223.36.154;

    location / {
        proxy_pass http://grab_backend;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
EOF
sudo nginx -t
sudo systemctl reload nginx

echo "=== [3/4] restart app with new jar ==="
sudo systemctl start grab-system
sleep 12
systemctl is-active grab-system

echo "=== [4/4] verify ==="
grep worker_connections /etc/nginx/nginx.conf | head -2
echo "DEPLOY_V3_DONE"
