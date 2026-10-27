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
        display: ['1.75rem', { lineHeight: '2.25rem', fontWeight: '700' }], // Display/H1
        h2: ['1.375rem', { lineHeight: '1.875rem', fontWeight: '600' }],      // Heading/H2
        h3: ['1.125rem', { lineHeight: '1.625rem', fontWeight: '600' }],      // Heading/H3
        body: ['0.875rem', { lineHeight: '1.375rem', fontWeight: '400' }],    // Body/Base
        'body-medium': ['0.875rem', { lineHeight: '1.375rem', fontWeight: '500' }], // Body/Medium
        small: ['0.75rem', { lineHeight: '1.125rem', fontWeight: '400' }],   // Body/Small
        label: ['0.6875rem', { lineHeight: '1rem', fontWeight: '600' }],   // Label/Caps
        kpi: ['1.625rem', { lineHeight: '2rem', fontWeight: '700' }],     // KPI/Number
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
