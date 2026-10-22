// Cornice delle pagine pubbliche (accesso, registrazione, pagamento): sfondo notte del logo. © Roberto Di Flumeri
import Image from "next/image";
import Link from "next/link";
import { Copyright } from "./ui";

export function Marchio({ dimensione = 40 }: { dimensione?: number }) {
  return (
    <span className="flex items-center gap-3">
      <Image src="/logo.png" alt="" width={dimensione} height={dimensione} priority className="drop-shadow-lg" />
      <span className="font-extrabold tracking-[0.2em] text-white" style={{ fontSize: dimensione * 0.5 }}>NE<span className="testo-gradiente">X</span>ORA</span>
    </span>
  );
}

export default function Accoglienza({ children, larga }: { children: React.ReactNode; larga?: boolean }) {
  return (
    <div className="sfondo-notte flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <Link href="/" className="mb-6" aria-label="Nexora, pagina iniziale"><Marchio dimensione={52} /></Link>
      <main className={`w-full ${larga ? "max-w-2xl" : "max-w-md"}`}>
        <div className="rounded-2xl border border-white/10 bg-pannello p-6 text-testo shadow-2xl sm:p-8">{children}</div>
      </main>
      <Copyright className="mt-8 text-[#98a4cc]" />
    </div>
  );
}
