#!/usr/bin/env bash
# Скачивает карту высот SRTM (1″, ~30 м) для Южного берега Крыма — источник для «подъёма к морю» и «вида на море».
# Данные: NASA SRTM через AWS Terrain Tiles (открытые, без ключа).
# Запуск из корня проекта:  bash tools/dem/fetch-srtm.sh
# Тайлы (~50 МБ) кладутся в tools/dem/raw и в git не попадают: в проект пойдёт вырезка региона (~2 МБ).
set -uo pipefail
cd "$(dirname "$0")"
mkdir -p raw
UA="chevteam-onestep/1.0 (hackathon prototype)"
for tile in N44E033 N44E034; do
  if [ -s "raw/$tile.hgt.gz" ]; then echo "$tile уже скачан"; continue; fi
  url="https://s3.amazonaws.com/elevation-tiles-prod/skadi/N44/$tile.hgt.gz"
  echo "Скачиваем $url ..."
  if curl -sS --fail --max-time 600 -A "$UA" "$url" -o "raw/$tile.hgt.gz.tmp"; then
    mv "raw/$tile.hgt.gz.tmp" "raw/$tile.hgt.gz"
    echo "$tile: $(wc -c < "raw/$tile.hgt.gz") байт"
  else
    rm -f "raw/$tile.hgt.gz.tmp"
    echo "Не удалось скачать $tile — попробуйте ещё раз" >&2
    exit 1
  fi
done
echo "Готово: tools/dem/raw"
