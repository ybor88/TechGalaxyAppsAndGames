/** @type {import('tailwindcss').Config} */
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
        blu: "rgb(var(--blu) / <alpha-value>)",
        viola: "rgb(var(--viola) / <alpha-value>)",
        ok: "rgb(var(--ok) / <alpha-value>)",
        attenzione: "rgb(var(--attenzione) / <alpha-value>)",
        errore: "rgb(var(--errore) / <alpha-value>)",
      },
      borderRadius: { xl: "0.9rem", "2xl": "1.2rem" },
    },
  },
  plugins: [],
};
