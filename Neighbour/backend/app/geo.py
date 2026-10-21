"""
Geografia con privacy by design (§7, §11 "Rischio posizione").

- Le coordinate inviate dal client non vengono mai salvate così come sono:
  si arrotondano al centro di una cella geohash.
    · area dell'utente  → precisione 6 (cella di circa 1,2 × 0,6 km)
    · luogo di un contenuto → precisione 7 (cella di circa 150 × 150 m)
- Il feed filtra lato server con i prefissi geohash (cella e vicine) e poi con la
  distanza reale tra i centri delle celle: nessuna coordinata precisa esce dal server.

© Roberto Di Flumeri
"""

import math

_BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"
_DECODE = {c: i for i, c in enumerate(_BASE32)}

PRECISIONE_AREA = 6
PRECISIONE_LUOGO = 7
# precisione dei prefissi usati per la ricerca: cella ~4,9 × 4,9 km, con le 8 vicine copre il raggio massimo
PRECISIONE_RICERCA = 5


def codifica(lat: float, lon: float, precisione: int) -> str:
    lat_int, lon_int = [-90.0, 90.0], [-180.0, 180.0]
    out, bit, ch, pari = [], 0, 0, True
    while len(out) < precisione:
        intervallo, valore = (lon_int, lon) if pari else (lat_int, lat)
        medio = (intervallo[0] + intervallo[1]) / 2
        if valore >= medio:
            ch |= 1 << (4 - bit)
            intervallo[0] = medio
        else:
            intervallo[1] = medio
        pari = not pari
        bit += 1
        if bit == 5:
            out.append(_BASE32[ch])
            bit, ch = 0, 0
    return "".join(out)


def limiti(geohash: str) -> tuple[float, float, float, float]:
    """(lat_min, lat_max, lon_min, lon_max) della cella."""
    lat_int, lon_int = [-90.0, 90.0], [-180.0, 180.0]
    pari = True
    for c in geohash:
        v = _DECODE[c]
        for i in range(4, -1, -1):
            intervallo = lon_int if pari else lat_int
            medio = (intervallo[0] + intervallo[1]) / 2
            if (v >> i) & 1:
                intervallo[0] = medio
            else:
                intervallo[1] = medio
            pari = not pari
    return lat_int[0], lat_int[1], lon_int[0], lon_int[1]


def centro(geohash: str) -> tuple[float, float]:
    la0, la1, lo0, lo1 = limiti(geohash)
    return round((la0 + la1) / 2, 6), round((lo0 + lo1) / 2, 6)


def vicine(geohash: str) -> list[str]:
    """La cella e le 8 adiacenti (stessa precisione)."""
    la0, la1, lo0, lo1 = limiti(geohash)
    dlat, dlon = la1 - la0, lo1 - lo0
    clat, clon = (la0 + la1) / 2, (lo0 + lo1) / 2
    celle = []
    for i in (-1, 0, 1):
        for j in (-1, 0, 1):
            lat = max(-89.999999, min(89.999999, clat + i * dlat))
            lon = ((clon + j * dlon + 180) % 360) - 180
            celle.append(codifica(lat, lon, len(geohash)))
    return sorted(set(celle))


def approssima(lat: float, lon: float, precisione: int) -> tuple[str, float, float]:
    """Restituisce geohash e centro della cella: è l'unica posizione che viene salvata."""
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise ValueError("Coordinate non valide")
    gh = codifica(lat, lon, precisione)
    la, lo = centro(gh)
    return gh, la, lo


def distanza_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = math.radians(lat2 - lat1), math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def prefissi_ricerca(lat: float, lon: float) -> list[str]:
    return vicine(codifica(lat, lon, PRECISIONE_RICERCA))


def distanza_leggibile(m: float) -> str:
    """Distanza arrotondata: non permette di risalire alla posizione."""
    if m < 300:
        return "meno di 300 m"
    if m < 950:  # sopra i 950 m l'arrotondamento al centinaio darebbe "1000 m"
        return f"circa {int(round(m / 100.0) * 100)} m"
    return f"circa {m / 1000:.1f} km".replace(".", ",")
