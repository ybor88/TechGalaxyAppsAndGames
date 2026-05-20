import clsx from "clsx";

interface LiquiditaIndicatorProps {
  indice: number;
}

function getLabel(indice: number): { testo: string; colore: "green" | "yellow" | "red" } {
  if (indice >= 1.5) return { testo: "Ottima", colore: "green" };
  if (indice >= 1.0) return { testo: "Adeguata", colore: "yellow" };
  return { testo: "Critica", colore: "red" };
}

export default function LiquiditaIndicator({ indice }: LiquiditaIndicatorProps) {
  const n = Number(indice);
  const { testo, colore } = getLabel(n);
  const percentuale = Math.min((n / 3) * 100, 100);

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
      <h2 className="text-base font-semibold text-gray-700 mb-4">
        Indicatore di liquidità
      </h2>
      <div className="flex items-end gap-3 mb-3">
        <span className="text-3xl font-bold text-gray-800">{n.toFixed(2)}</span>
        <span
          className={clsx("text-sm font-medium mb-1", {
            "text-green-600": colore === "green",
            "text-yellow-500": colore === "yellow",
            "text-red-500": colore === "red",
          })}
        >
          {testo}
        </span>
      </div>
      <div className="w-full bg-gray-100 rounded-full h-3">
        <div
          className={clsx("h-3 rounded-full transition-all", {
            "bg-green-500": colore === "green",
            "bg-yellow-400": colore === "yellow",
            "bg-red-500": colore === "red",
          })}
          style={{ width: `${percentuale}%` }}
        />
      </div>
      <p className="text-xs text-gray-400 mt-2">
        Rapporto entrate / uscite &ge; 1.5 = ottima liquidità
      </p>
    </div>
  );
}
