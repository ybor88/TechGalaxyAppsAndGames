"""Geohash, approssimazione e distanze leggibili. © Roberto Di Flumeri"""

from app import geo


def test_codifica_e_centro_della_cella():
    gh = geo.codifica(45.4869, 9.1897, 6)
    assert gh == "u0ndc0"
    lat, lon = geo.centro(gh)
    assert geo.codifica(lat, lon, 6) == gh


def test_vicine_sono_nove_celle_distinte():
    assert len(geo.vicine("u0ndc")) == 9


def test_distanza_leggibile_arrotondata():
    assert geo.distanza_leggibile(120) == "meno di 300 m"
    assert geo.distanza_leggibile(640) == "circa 600 m"
    assert geo.distanza_leggibile(970) == "circa 1,0 km"
    assert geo.distanza_leggibile(2349) == "circa 2,3 km"
