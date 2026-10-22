/** @type {import('next').NextConfig} Nexora — © Roberto Di Flumeri */
const BACKEND = process.env.NEXORA_BACKEND_URL || "http://127.0.0.1:8300";
const sviluppo = process.env.NODE_ENV !== "production";

// Content Security Policy: solo risorse proprie (le API passano dal proxy /api)
const CSP = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${sviluppo ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "connect-src 'self'",
  "font-src 'self'",
  "frame-src 'self' blob:",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join("; ");

module.exports = {
  skipTrailingSlashRedirect: true,
  poweredByHeader: false,
  experimental: { proxyTimeout: 120_000 },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${BACKEND}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: CSP },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "X-Frame-Options", value: "SAMEORIGIN" },
          // la fotocamera serve solo alla scansione dei barcode
          { key: "Permissions-Policy", value: "camera=(self), microphone=(), geolocation=()" },
        ],
      },
      { source: "/sw.js", headers: [{ key: "Cache-Control", value: "no-cache" }] },
    ];
  },
};
