"""Вырезает регион из тайлов SRTM (.hgt.gz) в компактный файл для пайплайна.

Формат: gzip( "DEM1" | south, west, north, east (float64 BE) | rows, cols (int32 BE) | rows×cols int16 BE ),
строки — с севера на юг, столбцы — с запада на восток, шаг 1″. Пустоты SRTM (-32768) → 0 (над морем это и есть уровень моря).
Запуск: python3 tools/dem/crop.py tools/dem/raw src/main/resources/dem/yalta-alushta.dem.gz 44.38 33.95 44.72 34.46
"""
import gzip, struct, sys, os

N = 3601

def tile(raw, lat, lon):
    name = f"N{lat:02d}E{lon:03d}.hgt.gz"
    data = gzip.open(os.path.join(raw, name)).read()
    return struct.unpack(f'>{N * N}h', data)

def main(raw, out, south, west, north, east):
    south, west, north, east = map(float, (south, west, north, east))
    step = 1 / 3600
    rows = int(round((north - south) / step)) + 1
    cols = int(round((east - west) / step)) + 1
    cache = {}
    values = []
    for r in range(rows):
        lat = north - r * step
        for c in range(cols):
            lon = west + c * step
            tl, tn = int(lat // 1), int(lon // 1)
            if (tl, tn) not in cache:
                cache[(tl, tn)] = tile(raw, tl, tn)
            t = cache[(tl, tn)]
            row = int(round((tl + 1 - lat) * 3600))
            col = int(round((lon - tn) * 3600))
            v = t[min(N - 1, row) * N + min(N - 1, col)]
            values.append(0 if v == -32768 else v)
    with gzip.open(out, 'wb') as f:
        f.write(b'DEM1' + struct.pack('>4d2i', south, west, north, east, rows, cols))
        f.write(struct.pack(f'>{len(values)}h', *values))
    print(rows, 'x', cols, 'points, max', max(values), 'm ->', out, os.path.getsize(out), 'bytes')

if __name__ == '__main__':
    main(*sys.argv[1:])
