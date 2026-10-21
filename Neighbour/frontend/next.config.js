/** @type {import('next').NextConfig} Neighbour — © Roberto Di Flumeri */
const BACKEND = process.env.NEIGHBOUR_BACKEND_URL || "http://127.0.0.1:8200";
const sviluppo = process.env.NODE_ENV !== "production";

// Content Security Policy: solo risorse proprie, più le tessere della mappa OpenStreetMap
const CSP = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${sviluppo ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob: https://*.tile.openstreetmap.org",
  "connect-src 'self'",
  "font-src 'self'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

module.exports = {
  // le API usano percorsi senza "/" finale: nessun redirect automatico
  skipTrailingSlashRedirect: true,
  poweredByHeader: false,
  experimental: { proxyTimeout: 60_000 },
  async rewrites() {
    return [{ source: "/v1/:path*", destination: `${BACKEND}/v1/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: CSP },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "X-Frame-Options", value: "DENY" },
          // la posizione serve solo all'app stessa, e solo dopo il consenso
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(self)" },
        ],
      },
      { source: "/sw.js", headers: [{ key: "Cache-Control", value: "no-cache" }] },
    ];
  },
};
