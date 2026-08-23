@echo off
mysql -u root -proot grab_system -e "DELETE FROM grab_record; DELETE FROM grab_order; UPDATE activity SET available_stock = total_stock WHERE id = 1;"
