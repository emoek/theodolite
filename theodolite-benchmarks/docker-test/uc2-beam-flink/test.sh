#!/bin/sh

docker-compose exec kcat kcat -C -b kafka:9092 -t output -s key=s -s value=s -r http://schema-registry:8081 -f '%k:%s\n' -c 20 |
    tee /dev/stderr |
    awk -F ':' '!/^%/ {print $1}' |
    sort |
    uniq |
    wc -l |
    grep "\b10\b"
