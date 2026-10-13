/** @type {import('next').NextConfig} © Roberto Di Flumeri */
const BACKEND = process.env.GHOSTME_BACKEND_URL || "http://127.0.0.1:8100";

module.exports = {
  // le API usano percorsi senza "/" finale: nessun redirect automatico
  skipTrailingSlashRedirect: true,
  poweredByHeader: false,
  async rewrites() {
    return [{ source: "/v1/:path*", destination: `${BACKEND}/v1/:path*` }];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "same-origin" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
      { source: "/sw.js", headers: [{ key: "Cache-Control", value: "no-cache" }] },
    ];
  },
};
