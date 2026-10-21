// Neighbour — layout radice. © Roberto Di Flumeri
import type { Metadata, Viewport } from "next";
import "./globals.css";
import "leaflet/dist/leaflet.css";
import { ProviderSessione } from "@/lib/sessione";
import RegistraSW from "@/components/RegistraSW";
import ValidazioneItaliana from "@/components/ValidazioneItaliana";

export const metadata: Metadata = {
  title: { default: "Neighbour", template: "%s · Neighbour" },
  description: "Persone vere. Più vicine. Scambi, aiuti, eventi e avvisi del tuo quartiere.",
  manifest: "/manifest.webmanifest",
  applicationName: "Neighbour",
  appleWebApp: { capable: true, title: "Neighbour", statusBarStyle: "default" },
  icons: { icon: "/icone/icona-64.png", apple: "/icone/icona-180.png" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#0b6b3a" },
    { media: "(prefers-color-scheme: dark)", color: "#0a140e" },
  ],
  width: "device-width",
  initialScale: 1,
};

// applica il tema prima del render per evitare il lampo di colore
const scriptTema = `try{var t=localStorage.getItem('nb_tema');if(t==='scuro'||(!t&&matchMedia('(prefers-color-scheme: dark)').matches))document.documentElement.classList.add('dark')}catch(e){}`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="it" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: scriptTema }} />
      </head>
      <body>
        <ProviderSessione>{children}</ProviderSessione>
        <RegistraSW />
        <ValidazioneItaliana />
      </body>
    </html>
  );
}
