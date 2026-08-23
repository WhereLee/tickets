#!/bin/bash
# reset server db + redis before each load test round
set -e
source /opt/grab-system/.env
mysql -u grab_app -p${SPRING_DATASOURCE_PASSWORD} grab_system -e "DELETE FROM grab_record; DELETE FROM grab_order; UPDATE activity SET total_stock = 5000, available_stock = 5000, status = 1 WHERE id = 1;"
redis-cli DEL stock:1 > /dev/null
echo "SERVER_RESET_DONE (activity 1 stock=5000)"
