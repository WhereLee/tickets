#!/bin/bash
# verify db + redis consistency after load test
set -e
source /opt/grab-system/.env
mysql -u grab_app -p${SPRING_DATASOURCE_PASSWORD} grab_system -e "SELECT COUNT(*) AS order_count FROM grab_order WHERE activity_id=1; SELECT COUNT(*) AS record_count FROM grab_record WHERE activity_id=1; SELECT total_stock, available_stock FROM activity WHERE id=1;"
echo "redis stock:"
redis-cli GET stock:1
echo "CHECK_DONE"
