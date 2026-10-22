/** @type {import('tailwindcss').Config} Nexora — © Roberto Di Flumeri */
module.exports = {
  content: ["./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        fondo: "rgb(var(--fondo) / <alpha-value>)",
        pannello: "rgb(var(--pannello) / <alpha-value>)",
        bordo: "rgb(var(--bordo) / <alpha-value>)",
        testo: "rgb(var(--testo) / <alpha-value>)",
        tenue: "rgb(var(--tenue) / <alpha-value>)",
        primario: "rgb(var(--primario) / <alpha-value>)",
        ciano: "rgb(var(--ciano) / <alpha-value>)",
        viola: "rgb(var(--viola) / <alpha-value>)",
        notte: "rgb(var(--notte) / <alpha-value>)",
        ok: "rgb(var(--ok) / <alpha-value>)",
        attenzione: "rgb(var(--attenzione) / <alpha-value>)",
        errore: "rgb(var(--errore) / <alpha-value>)",
      },
      borderRadius: { xl: "0.8rem", "2xl": "1.1rem" },
    },
  },
  plugins: [],
};
