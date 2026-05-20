import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BusinessFlow ERP",
  description: "Gestione contabilità aziendale 100% open source",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="it">
      <body className="bg-gray-50 min-h-screen">{children}</body>
    </html>
  );
}
