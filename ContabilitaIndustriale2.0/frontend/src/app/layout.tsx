import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Contabilità Industriale 2.0",
  description: "Gestione contabilità industriale e controllo di gestione 100% open source",
  icons: {
    icon: "/logo.jpeg",
    apple: "/logo.jpeg",
  },
  openGraph: {
    title: "Contabilità Industriale 2.0",
    description: "ERP industriale 100% open source",
    images: [{ url: "/logo.jpeg" }],
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="it">
      <body className="bg-[#f8fafc] min-h-screen">{children}</body>
    </html>
  );
}
