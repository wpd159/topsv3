'use client'
import { forwardRef } from 'react'

export const AnuncioSobre = forwardRef<HTMLDivElement, { descricao: string }>(
  ({ descricao }, ref) => (
    <div ref={ref} className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 scroll-mt-24">
      <h2 className="text-lg font-semibold text-gray-900 mb-2">Sobre mim</h2>
      <p className="text-gray-700 leading-relaxed">{descricao}</p>
    </div>
  )
)

AnuncioSobre.displayName = 'AnuncioSobre'
