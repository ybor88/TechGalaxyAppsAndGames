// Cornice delle pagine di accesso — © Roberto Di Flumeri
import Image from "next/image";
import Link from "next/link";
import { CambiaTema, Copyright } from "./ui";

export default function SchermataAccesso({ titolo, children }: { titolo: string; children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <div className="absolute right-4 top-4"><CambiaTema /></div>
      <Link href="/" className="mb-6 flex items-center gap-3" aria-label="Torna alla home">
        <Image src="/logo.png" alt="" width={48} height={48} className="rounded-xl" />
        <span className="text-xl font-bold">Neighbour</span>
      </Link>
      <main className="scheda w-full max-w-md">
        <h1 className="mb-4 text-xl font-bold">{titolo}</h1>
        {children}
      </main>
      <div className="mt-6"><Copyright /></div>
    </div>
  );
}
