// Legenda dei colori della mappa — © Roberto Di Flumeri
import { COLORI, NOMI } from "@/lib/mappa";

export default function Legenda() {
  return (
    <ul className="flex flex-wrap gap-3 text-xs text-tenue" aria-label="Legenda">
      {["regalo", "offerta", "aiuto", "segnalazione", "evento", "servizio"].map((t) => (
        <li key={t} className="flex items-center gap-1">
          <span className="inline-block h-3 w-3 rounded-full" style={{ background: COLORI[t] }} aria-hidden /> {NOMI[t]}
        </li>
      ))}
    </ul>
  );
}
