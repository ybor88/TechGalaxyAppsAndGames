"use client";
// Registrazione del service worker (PWA installabile). © Roberto Di Flumeri
import { useEffect } from "react";

export default function RegistraSW() {
  useEffect(() => {
    if ("serviceWorker" in navigator && process.env.NODE_ENV === "production") {
      navigator.serviceWorker.register("/sw.js").catch(() => undefined);
    }
  }, []);
  return null;
}
