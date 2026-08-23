#!/bin/bash
# reset for ticket test: stock = 10 (200+ users grabbing 10 tickets)
set -e
source /opt/grab-system/.env
mysql -u grab_app -p${SPRING_DATASOURCE_PASSWORD} grab_system -e "DELETE FROM grab_record; DELETE FROM grab_order; UPDATE activity SET total_stock = 10, available_stock = 10, status = 1 WHERE id = 1;"
redis-cli DEL stock:1 > /dev/null
echo "TICKETS_RESET_DONE (activity 1 stock=10)"
