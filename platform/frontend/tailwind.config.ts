import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  darkMode: "class",
  theme: {
    extend: {
      colors: {
        "bg-app": "var(--bg-app)",
        "bg-surface": "var(--bg-surface)",
        "bg-surface-hover": "var(--bg-surface-hover)",
        "bg-surface-raised": "var(--bg-surface-raised)",
        "sidebar-bg": "var(--sidebar-bg)",
        "sidebar-border": "var(--sidebar-border)",
        "sidebar-text": "var(--sidebar-text)",
        "sidebar-text-muted": "var(--sidebar-text-muted)",
        "sidebar-text-active": "var(--sidebar-text-active)",
        "sidebar-item-active": "var(--sidebar-item-active)",
        "sidebar-item-hover": "var(--sidebar-item-hover)",
        "border-default": "var(--border)",
        "border-strong": "var(--border-strong)",
        "text-primary": "var(--text-primary)",
        "text-secondary": "var(--text-secondary)",
        "text-tertiary": "var(--text-tertiary)",
        "text-placeholder": "var(--text-placeholder)",
        "accent": "var(--accent)",
        "accent-hover": "var(--accent-hover)",
        "accent-muted": "var(--accent-muted)",
        "accent-muted-text": "var(--accent-muted-text)",
        "destructive": "var(--destructive)",
        "destructive-muted": "var(--destructive-muted)",
        "success": "var(--success)",
        "success-muted": "var(--success-muted)",
        "warning": "var(--warning)",
        "warning-muted": "var(--warning-muted)",
        "input-bg": "var(--input-bg)",
        "input-border": "var(--input-border)",
      },
    },
  },
  plugins: [],
};
export default config;
