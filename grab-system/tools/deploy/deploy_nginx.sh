#!/bin/bash
# nginx plan B: standalone site server_name = IP, proxy to 8080
set -e

# 1. write grab-system site config (backup not needed, new file)
sudo tee /etc/nginx/sites-enabled/grab-system > /dev/null <<'EOF'
server {
    listen 80;
    server_name 124.223.36.154;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

# 2. validate config BEFORE reload (protect rag site)
sudo nginx -t

# 3. reload only if config valid
sudo systemctl reload nginx
echo "--- listening ports ---"
ss -tln | grep :80
echo "NGINX_DONE"
