/** @type {import('tailwindcss').Config} */
// FutData: equivalencias de los tokens de Figma ("Mockups FutData") en Tailwind v3.
// Los colores apuntan a variables CSS de tokens.css, por eso cambian solos con data-theme.
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Plus Jakarta Sans"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      // Estilos de texto: Display, Heading, Body, Label, KPI
      fontSize: {
        display: ['28px', { lineHeight: '36px', fontWeight: '700' }], // Display/H1
        h2: ['22px', { lineHeight: '30px', fontWeight: '600' }],      // Heading/H2
        h3: ['18px', { lineHeight: '26px', fontWeight: '600' }],      // Heading/H3
        body: ['14px', { lineHeight: '22px', fontWeight: '400' }],    // Body/Base
        'body-medium': ['14px', { lineHeight: '22px', fontWeight: '500' }], // Body/Medium
        small: ['12px', { lineHeight: '18px', fontWeight: '400' }],   // Body/Small
        label: ['11px', { lineHeight: '16px', fontWeight: '600' }],   // Label/Caps
        kpi: ['26px', { lineHeight: '32px', fontWeight: '700' }],     // KPI/Number
      },
      backgroundColor: {
        app: 'var(--color-bg-app)',
        surface: 'var(--color-bg-surface)',
        sidebar: 'var(--color-bg-sidebar)',
        'brand-primary': 'var(--color-brand-primary)',
        'brand-primary-hover': 'var(--color-brand-primary-hover)',
        'brand-accent': 'var(--color-brand-accent)',
        success: 'var(--color-status-success-bg)',
        warning: 'var(--color-status-warning-bg)',
        danger: 'var(--color-status-danger-bg)',
      },
      textColor: {
        primary: 'var(--color-text-primary)',
        secondary: 'var(--color-text-secondary)',
        'on-dark': 'var(--color-text-on-dark)',
        'brand-accent': 'var(--color-brand-accent-text)',
        success: 'var(--color-status-success-text)',
        warning: 'var(--color-status-warning-text)',
        danger: 'var(--color-status-danger-text)',
      },
      borderColor: {
        DEFAULT: 'var(--color-border-default)',
        default: 'var(--color-border-default)',
        success: 'var(--color-status-success-text)',
        warning: 'var(--color-status-warning-text)',
        danger: 'var(--color-status-danger-text)',
      },
      ringColor: { focus: 'var(--color-focus-ring)' },
      outlineColor: { focus: 'var(--color-focus-ring)' },
    },
  },
  plugins: [],
};
