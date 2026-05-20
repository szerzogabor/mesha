import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {},
  },
  safelist: [
    // Force-include all dark: variant classes used across the app.
    // This ensures they appear in the CSS output regardless of content scanning.
    "dark:bg-gray-700",
    "dark:bg-gray-800",
    "dark:bg-gray-900",
    "dark:bg-gray-950",
    "dark:bg-gray-800/50",
    "dark:text-gray-100",
    "dark:text-gray-200",
    "dark:text-gray-300",
    "dark:text-gray-400",
    "dark:text-gray-500",
    "dark:border-gray-700",
    "dark:border-gray-800",
    "dark:divide-gray-800",
    "dark:placeholder-gray-500",
    "dark:focus:ring-gray-500",
    "dark:hover:bg-gray-600",
    "dark:hover:bg-gray-700",
    "dark:hover:bg-gray-800",
    "dark:hover:bg-gray-800/50",
    "dark:hover:border-gray-500",
    "dark:hover:text-gray-200",
    "dark:hover:text-gray-300",
    "dark:hover:text-white",
    "dark:border-gray-500",
    "dark:border-gray-400",
    "dark:text-white",
  ],
  plugins: [],
};
export default config;
