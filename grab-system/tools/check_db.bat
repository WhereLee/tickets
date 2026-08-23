@echo off
mysql -u root -proot grab_system -e "SELECT COUNT(*) AS order_count FROM grab_order WHERE activity_id = 1; SELECT COUNT(*) AS record_count FROM grab_record WHERE activity_id = 1; SELECT available_stock FROM activity WHERE id = 1;"
