#!/usr/bin/env bash
set -euo pipefail

install -d -o judge -g judge /home/judge/etc /home/judge/data /home/judge/log
for id in 0 1 2 3; do install -d -o judge -g judge "/home/judge/run${id}"; done

sed \
  -e "s|^OJ_HOST_NAME=.*|OJ_HOST_NAME=${DB_HOST:-db}|" \
  -e "s|^OJ_USER_NAME=.*|OJ_USER_NAME=${DB_USERNAME:-jisuoj}|" \
  -e "s|^OJ_PASSWORD=.*|OJ_PASSWORD=${DB_PASSWORD:-jisuoj}|" \
  -e "s|^OJ_DB_NAME=.*|OJ_DB_NAME=${DB_NAME:-jol}|" \
  -e "s|^OJ_PORT_NUMBER=.*|OJ_PORT_NUMBER=${DB_PORT:-3306}|" \
  -e "s|^OJ_RUNNING=.*|OJ_RUNNING=${JUDGE_WORKERS:-2}|" \
  -e "s|^OJ_UDP_SERVER=.*|OJ_UDP_SERVER=0.0.0.0|" \
  -e "s|^OJ_USE_DOCKER=.*|OJ_USE_DOCKER=0|" \
  /opt/jisuoj/judge.conf.template > /home/judge/etc/judge.conf

chown judge:judge /home/judge/etc/judge.conf
exec /usr/bin/judged /home/judge debug
