#!/bin/bash
# server local bench: bypass public bandwidth, hit 127.0.0.1:8080 directly
# usage: server_bench.sh <concurrency> <count>
CONC=$1
COUNT=$2
START=$(date +%s%N)
seq 1 $COUNT | xargs -P $CONC -I{} curl -s -X POST -o /dev/null -w "%{time_total}\n" "http://127.0.0.1:8080/order/grab?userId={}&activityId=1&quantity=1" > /tmp/bench_out.txt
END=$(date +%s%N)
AVG=$(awk '{s+=$1; if($1>max)max=$1} END {printf "avg=%.3fs max=%.3fs", s/NR, max}' /tmp/bench_out.txt)
ELAPSED=$(echo "scale=3; ($END-$START)/1000000000" | bc)
RATE=$(echo "scale=0; $COUNT/$ELAPSED" | bc)
echo "concurrency=$CONC count=$COUNT elapsed=${ELAPSED}s rate=${RATE}req/s $AVG"
echo "BENCH_DONE"
