/* Береговая линия и пляжи. © OpenStreetMap, ODbL */
[out:json][timeout:120];
(
  way["natural"="coastline"](44.38,33.95,44.72,34.46);
  nwr["natural"="beach"](44.38,33.95,44.72,34.46);
  nwr["leisure"="beach_resort"](44.38,33.95,44.72,34.46);
);
out tags geom;
