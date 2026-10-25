#!/usr/bin/env bash
# Скачивает снимок OpenStreetMap для Ялты и Алушты — исходные данные пайплайна характеристик.
# Запуск из корня проекта:  bash tools/osm/fetch-yalta-alushta.sh
# Три небольших запроса вместо одного большого: публичные серверы Overpass режут тяжёлые запросы.
set -uo pipefail
cd "$(dirname "$0")"
OUT_DIR=../../src/main/resources/osm/yalta-alushta
mkdir -p "$OUT_DIR"
SERVERS=(
  https://overpass-api.de/api/interpreter
  https://overpass.private.coffee/api/interpreter
  https://overpass.kumi.systems/api/interpreter
  https://maps.mail.ru/osm/tools/overpass/api/interpreter
)
UA="chevteam-onestep/1.0 (hackathon prototype; contact: github.com/EvgenEng)"
failed=0
for part in places roads coast; do
  ok=0
  for attempt in 1 2; do
    for url in "${SERVERS[@]}"; do
      echo "[$part] попытка $attempt: $url"
      if curl -sS --fail --max-time 200 -A "$UA" -H "Accept: application/json" \
           --data-urlencode "data@$part.overpassql" "$url" -o "$OUT_DIR/$part.json.tmp" \
         && grep -q '"elements"' "$OUT_DIR/$part.json.tmp"; then
        mv "$OUT_DIR/$part.json.tmp" "$OUT_DIR/$part.json"
        echo "[$part] готово: $(wc -c < "$OUT_DIR/$part.json") байт"
        ok=1
        break 2
      fi
      sleep 5
    done
  done
  rm -f "$OUT_DIR/$part.json.tmp"
  [ $ok -eq 1 ] || { echo "[$part] не скачалось" >&2; failed=1; }
done
[ $failed -eq 0 ] && echo "Всё скачано в $OUT_DIR" || { echo "Часть данных не скачалась — запустите скрипт ещё раз чуть позже" >&2; exit 1; }
