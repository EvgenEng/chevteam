/* Отели и места рядом: кафе, бары, магазины, остановки, достопримечательности, парки, стройки. © OpenStreetMap, ODbL */
[out:json][timeout:120];
(
  nwr["tourism"~"^(hotel|guest_house|apartment|hostel|motel)$"](44.38,33.95,44.72,34.46);
  nwr["amenity"~"^(cafe|restaurant|fast_food|bar|pub|nightclub|pharmacy|hospital|clinic)$"](44.38,33.95,44.72,34.46);
  nwr["shop"~"^(supermarket|convenience|mall|marketplace)$"](44.38,33.95,44.72,34.46);
  node["highway"="bus_stop"](44.38,33.95,44.72,34.46);
  node["public_transport"="platform"](44.38,33.95,44.72,34.46);
  nwr["tourism"~"^(attraction|museum|viewpoint)$"](44.38,33.95,44.72,34.46);
  nwr["historic"~"^(castle|palace|monument|memorial)$"](44.38,33.95,44.72,34.46);
  way["landuse"="construction"](44.38,33.95,44.72,34.46);
  way["leisure"~"^(park|garden|nature_reserve)$"](44.38,33.95,44.72,34.46);
);
out tags geom;
