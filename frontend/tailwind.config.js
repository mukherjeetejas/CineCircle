/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: '#0A0A0A',
        surface: '#141414',
        'surface-2': '#1C1C1C',
        line: '#2A2A2A',
        amber: {
          DEFAULT: '#FFB000',
          hover: '#FFC233',
          muted: '#3D2A00',
        },
        ink: '#F5F5F5',
        muted: '#9CA3AF',
        danger: '#EF4444',
      },
      fontFamily: {
        display: ['"Space Grotesk"', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
      },
      boxShadow: {
        glow: '0 0 0 1px #FFB00055',
        'glow-lg': '0 0 24px -4px #FFB00066',
      },
      keyframes: {
        'pop': {
          '0%': { transform: 'scale(1)' },
          '50%': { transform: 'scale(1.25)' },
          '100%': { transform: 'scale(1)' },
        },
      },
      animation: {
        pop: 'pop 220ms ease-out',
      },
    },
  },
  plugins: [],
}
