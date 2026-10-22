// Nexora — layout radice. © Roberto Di Flumeri
import type { Metadata, Viewport } from "next";
import "./globals.css";
import RegistraSW from "@/components/RegistraSW";
import { ProviderSessione } from "@/lib/sessione";

export const metadata: Metadata = {
  title: { default: "Nexora ERP", template: "%s · Nexora" },
  description: "ERP cloud per PMI ed e-commerce: vendite, magazzino multi-sede, fatturazione elettronica, analytics e intelligenza artificiale.",
  manifest: "/manifest.webmanifest",
  applicationName: "Nexora",
  authors: [{ name: "Roberto Di Flumeri" }],
  appleWebApp: { capable: true, title: "Nexora", statusBarStyle: "black-translucent" },
  icons: { icon: "/icone/icona-64.png", apple: "/icone/icona-180.png" },
};

export const viewport: Viewport = { themeColor: "#0b143a", width: "device-width", initialScale: 1 };

// applica il tema prima del render per evitare il lampo di colore
const scriptTema = "try{var t=localStorage.getItem('nx_tema');if(t==='scuro'||(!t&&matchMedia('(prefers-color-scheme: dark)').matches))document.documentElement.classList.add('dark')}catch(e){}";

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="it" suppressHydrationWarning>
      <head><script dangerouslySetInnerHTML={{ __html: scriptTema }} /></head>
      <body>
        <ProviderSessione>{children}</ProviderSessione>
        <RegistraSW />
      </body>
    </html>
  );
}
