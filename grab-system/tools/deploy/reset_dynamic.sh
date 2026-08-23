#!/bin/bash
# usage: reset_dynamic.sh <stock>
set -e
source /opt/grab-system/.env
STOCK=$1
mysql -u grab_app -p${SPRING_DATASOURCE_PASSWORD} grab_system -e "DELETE FROM grab_record; DELETE FROM grab_order; UPDATE activity SET total_stock = ${STOCK}, available_stock = ${STOCK}, status = 1 WHERE id = 1;"
# 清 Redis 全套：库存 key + 已抢用户 Set + 待落库队列 + 活动缓存
# （活动缓存 TTL 10 分钟：reset 改了 total_stock 后必须失效，否则 -2 重建用旧值初始化库存）
redis-cli DEL stock:1 bought:1 order:queue activity:1 > /dev/null
echo "RESET_DONE stock=${STOCK}"
