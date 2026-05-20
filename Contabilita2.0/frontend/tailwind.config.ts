/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        // Brand palette — estratta dal logo Contabilità 2.0
        brand: {
          50:  "#eef2ff",
          100: "#d4ddf7",
          200: "#a9bbee",
          400: "#4169c8",
          500: "#2a52b0",
          600: "#1e3f96",
          700: "#1a3580",
          800: "#16297a",
          900: "#1e3a8a", // navy principale del logo
          950: "#0f1f5c",
        },
        "brand-green": {
          50:  "#f0fdf4",
          400: "#4ade80",
          500: "#22c55e",
          600: "#16a34a", // verde foglie del logo
          700: "#15803d",
        },
        "brand-orange": {
          50:  "#fff7ed",
          400: "#fb923c",
          500: "#f97316", // arancio freccia del logo
          600: "#ea580c",
          700: "#c2410c",
        },
        "brand-gold": {
          400: "#fbbf24",
          500: "#f59e0b", // oro moneta/sole del logo
          600: "#d97706",
        },
      },
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};
