// Ghost Me — layout radice. © Roberto Di Flumeri
import type { Metadata, Viewport } from "next";
import "./globals.css";
import { ProviderSessione } from "@/lib/sessione";
import RegistraSW from "@/components/RegistraSW";
import ValidazioneItaliana from "@/components/ValidazioneItaliana";

export const metadata: Metadata = {
  title: { default: "Ghost Me", template: "%s · Ghost Me" },
  description: "Una copia digitale di te, sotto il tuo controllo.",
  manifest: "/manifest.webmanifest",
  applicationName: "Ghost Me",
  appleWebApp: { capable: true, title: "Ghost Me", statusBarStyle: "black-translucent" },
  icons: { icon: "/icone/icona-64.png", apple: "/icone/icona-180.png" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#f6f7fc" },
    { media: "(prefers-color-scheme: dark)", color: "#090c1e" },
  ],
  width: "device-width",
  initialScale: 1,
};

// applica il tema prima del render per evitare il lampo di colore
const scriptTema = `try{var t=localStorage.getItem('gm_tema');if(t==='scuro'||(!t&&matchMedia('(prefers-color-scheme: dark)').matches))document.documentElement.classList.add('dark')}catch(e){}`;

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
