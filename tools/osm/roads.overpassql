/* Дороги (шум) и лестницы (подъёмы к морю). © OpenStreetMap, ODbL */
[out:json][timeout:120];
(
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary)$"](44.38,33.95,44.72,34.46);
  way["highway"="steps"](44.38,33.95,44.72,34.46);
);
out tags geom;
