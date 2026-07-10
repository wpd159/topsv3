import type { Metadata } from 'next'

export const metadata: Metadata = {
  title: 'Diagnóstico Next — básico',
  robots: {
    index: false,
    follow: false,
    nocache: true,
    googleBot: { index: false, follow: false, noarchive: true },
  },
}

const fieldStyle = {
  display: 'block',
  width: '100%',
  padding: '16px',
  border: '1px solid #9ca3af',
  borderRadius: '12px',
  background: '#f3f4f6',
  color: '#111111',
  fontSize: '16px',
  marginBottom: '16px',
  boxSizing: 'border-box' as const,
}

// Teste 1 da matriz de diagnostico: Next + root layout + providers + CSS
// global, com o minimo de markup possivel (sem componentes compartilhados,
// sem Tailwind nas classes, sem estado/mascara/validacao/API).
export default function DiagnosticoNextBasicoPage() {
  return (
    <div style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
      <h1 style={{ fontSize: 20 }}>Diagnóstico Next — básico</h1>
      <p style={{ fontSize: 14, color: '#333333' }}>
        Teste 1: Next + root layout + providers + CSS global. Toque em cada campo, digite,
        apague e role a página. Observe se aparece qualquer mancha ou repetição visual.
      </p>

      <input type="text" placeholder="Campo de texto" style={fieldStyle} />
      <input type="tel" placeholder="Campo de telefone" style={fieldStyle} />
      <input type="email" placeholder="Campo de e-mail" style={fieldStyle} />
      <input type="password" placeholder="Campo de senha" style={fieldStyle} />
      <input type="date" style={fieldStyle} />
      <textarea rows={4} placeholder="Campo de texto longo" style={fieldStyle} />
      <button type="button" style={fieldStyle}>
        Botão simples
      </button>
    </div>
  )
}
