/** @type {import('next').NextConfig} */
const nextConfig = {
  // Non rimuovere la "/" finale: le API FastAPI la richiedono (es. /api/v1/documenti/).
  // Senza questa opzione Next reindirizza a /documenti e FastAPI risponde con un redirect
  // assoluto verso localhost:8000, che salta il proxy (header non leggibili, e da un altro
  // PC della rete "localhost" non è il server).
  skipTrailingSlashRedirect: true,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8000/api/:path*",
      },
    ];
  },
};

module.exports = nextConfig;
