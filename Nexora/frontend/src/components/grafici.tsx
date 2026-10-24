"use client";
// Grafici (recharts) con i colori del logo: ciano, blu elettrico, viola. © Roberto Di Flumeri

import {
  Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, ComposedChart, Legend, Line, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { euro, numero } from "@/lib/formato";

export const COLORI = ["#3b82f6", "#8b5cf6", "#38bdf8", "#6366f1", "#a78bfa", "#22d3ee", "#818cf8", "#c084fc"];
const assi = { stroke: "rgb(var(--tenue))", fontSize: 12, tickLine: false, axisLine: false } as const;
const griglia = <CartesianGrid strokeDasharray="3 3" stroke="rgb(var(--bordo))" vertical={false} />;
const stileTooltip = { contentStyle: { background: "rgb(var(--pannello))", border: "1px solid rgb(var(--bordo))", borderRadius: 12, color: "rgb(var(--testo))" }, labelStyle: { color: "rgb(var(--testo))", fontWeight: 600 } };
// Importi in centesimi: 1M = 1.000.000 € = 100.000.000 centesimi (prima la soglia era 100.000 €,
// e un asse fino a 120.000 € mostrava "1M").
const euroCompatto = (v: number) => (Math.abs(v) >= 100_000_000 ? `${Math.round(v / 10_000_000) / 10}M` : Math.abs(v) >= 100_000 ? `${Math.round(v / 100_000)}k` : `${Math.round(v / 100)}`);

type Serie = { chiave: string; nome: string; colore?: string; tipo?: "barra" | "linea" | "area"; quantita?: boolean };

export function GraficoSerie({ dati, asseX, serie, altezza = 280, formattaX, quantita }: {
  dati: Record<string, any>[]; asseX: string; serie: Serie[]; altezza?: number; formattaX?: (v: string) => string; quantita?: boolean;
}) {
  return (
    <div style={{ height: altezza }} className="w-full">
      <ResponsiveContainer>
        <ComposedChart data={dati} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <defs>
            {serie.map((s, i) => (
              <linearGradient key={s.chiave} id={`g-${s.chiave}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={s.colore || COLORI[i]} stopOpacity={0.9} />
                <stop offset="100%" stopColor={s.colore || COLORI[i]} stopOpacity={s.tipo === "area" ? 0.05 : 0.55} />
              </linearGradient>
            ))}
          </defs>
          {griglia}
          <XAxis dataKey={asseX} {...assi} tickFormatter={formattaX} minTickGap={16} />
          <YAxis {...assi} width={48} tickFormatter={quantita ? (v) => numero(v) : euroCompatto} />
          <Tooltip {...stileTooltip} labelFormatter={(l) => (formattaX ? formattaX(String(l)) : String(l))}
            formatter={(v: number, n: string) => [quantita || serie.find((s) => s.nome === n)?.quantita ? numero(v) : euro(v), n]} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {serie.map((s, i) => s.tipo === "linea"
            ? <Line key={s.chiave} dataKey={s.chiave} name={s.nome} stroke={s.colore || COLORI[i]} strokeWidth={2.5} dot={false} />
            : s.tipo === "area"
              ? <Area key={s.chiave} dataKey={s.chiave} name={s.nome} stroke={s.colore || COLORI[i]} fill={`url(#g-${s.chiave})`} strokeWidth={2} />
              : <Bar key={s.chiave} dataKey={s.chiave} name={s.nome} fill={`url(#g-${s.chiave})`} radius={[6, 6, 0, 0]} maxBarSize={36} />)}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Previsione con banda di confidenza: storico pieno, previsione tratteggiata. */
export function GraficoPrevisione({ storico, previsione, quantita, altezza = 300 }: {
  storico: { data: string; valore: number }[]; previsione: { data: string; valore: number; minimo: number; massimo: number }[]; quantita?: boolean; altezza?: number;
}) {
  // l'ultimo giorno di storico è anche il punto di partenza della previsione, così le due linee si uniscono
  const dati: Record<string, unknown>[] = storico.map((p, i) => ({ data: p.data, storico: p.valore,
    ...(i === storico.length - 1 ? { previsione: p.valore, banda: [p.valore, p.valore] } : {}) }));
  for (const p of previsione) dati.push({ data: p.data, previsione: p.valore, banda: [p.minimo, p.massimo] });
  const fmt = (v: number) => (quantita ? numero(Math.round(v * 10) / 10) : euro(v, true));
  return (
    <div style={{ height: altezza }} className="w-full">
      <ResponsiveContainer>
        <ComposedChart data={dati} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <defs>
            <linearGradient id="g-storico" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#3b82f6" stopOpacity={0.5} /><stop offset="100%" stopColor="#3b82f6" stopOpacity={0.03} /></linearGradient>
          </defs>
          {griglia}
          <XAxis dataKey="data" {...assi} tickFormatter={(d) => new Date(d).toLocaleDateString("it-IT", { day: "2-digit", month: "short" })} minTickGap={28} />
          <YAxis {...assi} width={48} tickFormatter={quantita ? (v) => numero(v) : euroCompatto} />
          <Tooltip {...stileTooltip} labelFormatter={(d) => new Date(String(d)).toLocaleDateString("it-IT", { weekday: "short", day: "2-digit", month: "short" })}
            formatter={(v: number | number[], n: string) => [Array.isArray(v) ? `${fmt(v[0])} – ${fmt(v[1])}` : fmt(v), n]} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          <Area dataKey="storico" name="Storico" stroke="#3b82f6" fill="url(#g-storico)" strokeWidth={1.8} />
          <Area dataKey="banda" name="Intervallo 80%" stroke="none" fill="#8b5cf6" fillOpacity={0.18} />
          <Line dataKey="previsione" name="Previsione" stroke="#8b5cf6" strokeWidth={2.5} strokeDasharray="6 4" dot={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  );
}

export function GraficoBarreOrizzontali({ dati, chiave, etichetta, altezza, quantita }: { dati: Record<string, any>[]; chiave: string; etichetta: string; altezza?: number; quantita?: boolean }) {
  return (
    <div style={{ height: altezza ?? Math.max(160, dati.length * 34) }} className="w-full">
      <ResponsiveContainer>
        <BarChart data={dati} layout="vertical" margin={{ top: 0, right: 16, left: 0, bottom: 0 }}>
          <defs><linearGradient id="g-oriz" x1="0" y1="0" x2="1" y2="0"><stop offset="0%" stopColor="#38bdf8" /><stop offset="55%" stopColor="#3b82f6" /><stop offset="100%" stopColor="#8b5cf6" /></linearGradient></defs>
          <XAxis type="number" hide />
          <YAxis type="category" dataKey={etichetta} {...assi} width={150} tickFormatter={(v: string) => (v.length > 22 ? `${v.slice(0, 21)}…` : v)} />
          <Tooltip {...stileTooltip} formatter={(v: number) => [quantita ? numero(v) : euro(v), quantita ? "Quantità" : "Ricavi"]} />
          <Bar dataKey={chiave} fill="url(#g-oriz)" radius={[0, 6, 6, 0]} maxBarSize={22} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function GraficoTorta({ dati, chiave, etichetta, altezza = 240 }: { dati: Record<string, any>[]; chiave: string; etichetta: string; altezza?: number }) {
  return (
    <div style={{ height: altezza }} className="w-full">
      <ResponsiveContainer>
        <PieChart>
          <Pie data={dati} dataKey={chiave} nameKey={etichetta} innerRadius="55%" outerRadius="85%" paddingAngle={2} stroke="none">
            {dati.map((_, i) => <Cell key={i} fill={COLORI[i % COLORI.length]} />)}
          </Pie>
          <Tooltip {...stileTooltip} formatter={(v: number, n: string) => [euro(v), n]} />
          <Legend wrapperStyle={{ fontSize: 12 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

export function Scintilla({ dati, colore = "#3b82f6" }: { dati: number[]; colore?: string }) {
  return (
    <div className="h-10 w-28">
      <ResponsiveContainer>
        <AreaChart data={dati.map((v, i) => ({ i, v }))}>
          <Area dataKey="v" stroke={colore} fill={colore} fillOpacity={0.15} strokeWidth={1.5} isAnimationActive={false} />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}
