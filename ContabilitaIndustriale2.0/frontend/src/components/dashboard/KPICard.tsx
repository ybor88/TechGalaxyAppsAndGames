import { ReactNode } from "react";
import clsx from "clsx";

interface KPICardProps {
  titolo: string;
  valore: string;
  sottotitolo?: string;
  icona?: ReactNode;
  tendenza?: "positivo" | "negativo" | "neutro";
}

export default function KPICard({
  titolo,
  valore,
  sottotitolo,
  icona,
  tendenza = "neutro",
}: KPICardProps) {
  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-500">{titolo}</span>
        {icona && <span className="text-gray-400">{icona}</span>}
      </div>
      <span
        className={clsx("text-2xl font-bold", {
          "text-green-600": tendenza === "positivo",
          "text-red-500": tendenza === "negativo",
          "text-gray-800": tendenza === "neutro",
        })}
      >
        {valore}
      </span>
      {sottotitolo && (
        <span className="text-xs text-gray-400">{sottotitolo}</span>
      )}
    </div>
  );
}
