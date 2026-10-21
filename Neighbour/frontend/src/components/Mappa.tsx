"use client";
// Mappa di quartiere (F01) con OpenStreetMap — © Roberto Di Flumeri
// I contenuti sono disegnati come aree di circa 150 m, mai come punti precisi: nessun indirizzo è mostrato.

import Link from "next/link";
import { useEffect } from "react";
import { Circle, MapContainer, Popup, TileLayer, useMap, useMapEvents } from "react-leaflet";
import { COLORI, NOMI } from "@/lib/mappa";

export type { PuntoMappa } from "@/lib/mappa";
import type { PuntoMappa } from "@/lib/mappa";

const ATTRIBUZIONE = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
const TESSERE = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";

function Ricentra({ lat, lon, zoom }: { lat: number; lon: number; zoom?: number }) {
  const map = useMap();
  useEffect(() => { map.setView([lat, lon], zoom ?? map.getZoom()); }, [lat, lon, zoom, map]);
  return null;
}

function Clic({ onClic }: { onClic: (lat: number, lon: number) => void }) {
  useMapEvents({ click: (e) => onClic(e.latlng.lat, e.latlng.lng) });
  return null;
}

export function zoomPerRaggio(raggio: number): number {
  return raggio <= 500 ? 16 : raggio <= 1000 ? 15 : raggio <= 2200 ? 14 : 13;
}

export default function Mappa({ centro, raggio, punti = [], altezza = 480, onClic, selezione, raggioSelezione }: {
  centro: { lat: number; lon: number }; raggio?: number; punti?: PuntoMappa[]; altezza?: number;
  onClic?: (lat: number, lon: number) => void; selezione?: { lat: number; lon: number } | null; raggioSelezione?: number;
}) {
  return (
    <div style={{ height: altezza }} className="overflow-hidden rounded-2xl border border-bordo">
      <MapContainer center={[centro.lat, centro.lon]} zoom={zoomPerRaggio(raggio ?? 1500)} style={{ height: "100%", width: "100%" }}
        scrollWheelZoom={false} aria-label="Mappa del quartiere">
        <TileLayer attribution={ATTRIBUZIONE} url={TESSERE} />
        <Ricentra lat={centro.lat} lon={centro.lon} zoom={raggio ? zoomPerRaggio(raggio) : undefined} />
        {onClic && <Clic onClic={onClic} />}
        {raggio && (
          <Circle center={[centro.lat, centro.lon]} radius={raggio}
            pathOptions={{ color: "#0b6b3a", weight: 2, dashArray: "6 6", fillColor: "#65a34a", fillOpacity: 0.06 }} />
        )}
        {selezione && (
          <Circle center={[selezione.lat, selezione.lon]} radius={raggioSelezione ?? 150}
            pathOptions={{ color: "#ea7a1a", weight: 3, fillColor: "#ea7a1a", fillOpacity: 0.25 }} />
        )}
        {punti.map((p) => (
          <Circle key={`${p.genere}-${p.id}`} center={[p.lat, p.lon]} radius={75}
            pathOptions={{ color: COLORI[p.tipo] || "#0b6b3a", weight: 2, fillColor: COLORI[p.tipo] || "#0b6b3a", fillOpacity: 0.45 }}>
            <Popup>
              <div className="space-y-1">
                <div className="text-xs font-semibold uppercase" style={{ color: COLORI[p.tipo] }}>{NOMI[p.tipo] || p.tipo}</div>
                <div className="font-semibold">{p.titolo}</div>
                {p.distanza && <div className="text-xs opacity-80">{p.distanza} · zona approssimativa</div>}
                <Link href={p.link} className="text-sm font-semibold underline">Apri</Link>
              </div>
            </Popup>
          </Circle>
        ))}
      </MapContainer>
    </div>
  );
}

