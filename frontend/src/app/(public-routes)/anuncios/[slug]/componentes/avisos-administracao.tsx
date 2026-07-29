'use client'

import { useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight, Clock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { listarAvisosPublicos, type AvisoPublico } from '@/lib/aviso-api'

function dataHora(value: string) {
  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'America/Sao_Paulo',
  }).format(new Date(value))
}

export function AvisosAdministracao() {
  const [avisos, setAvisos] = useState<AvisoPublico[]>([])
  const [index, setIndex] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    void listarAvisosPublicos('ANUNCIO_RODAPE', 5, controller.signal)
      .then((items) => {
        setAvisos(items)
        setIndex(0)
      })
      .catch(() => setAvisos([]))
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (avisos.length <= 1) return
    const interval = window.setInterval(() => setIndex((value) => (value + 1) % avisos.length), 6000)
    return () => window.clearInterval(interval)
  }, [avisos.length])

  if (!avisos.length) return null
  const aviso = avisos[index]

  return (
    <section className="rounded-lg border border-zinc-200 bg-white p-4">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-zinc-950">Avisos da administracao</h2>
        {avisos.length > 1 ? (
          <div className="flex gap-1">
            <Button size="icon" variant="ghost" aria-label="Aviso anterior" onClick={() => setIndex((value) => (value - 1 + avisos.length) % avisos.length)}><ChevronLeft className="h-4 w-4" /></Button>
            <Button size="icon" variant="ghost" aria-label="Proximo aviso" onClick={() => setIndex((value) => (value + 1) % avisos.length)}><ChevronRight className="h-4 w-4" /></Button>
          </div>
        ) : null}
      </div>
      <div className="mt-3 rounded-md border border-pink-100 bg-pink-50 p-4">
        <h3 className="text-sm font-semibold text-zinc-950">{aviso.titulo}</h3>
        <p className="mt-2 whitespace-pre-line text-sm text-zinc-700">{aviso.descricao}</p>
        <p className="mt-3 flex items-center gap-1 text-xs text-zinc-500">
          <Clock className="h-3.5 w-3.5" />
          {aviso.ativoAte ? `Disponivel ate ${dataHora(aviso.ativoAte)}` : `Publicado em ${dataHora(aviso.publicadoEm)}`}
        </p>
      </div>
      {avisos.length > 1 ? (
        <div className="mt-3 flex justify-center gap-2" aria-label="Navegacao dos avisos">
          {avisos.map((item, itemIndex) => (
            <button key={item.id} type="button" className={`h-2 w-2 rounded-full ${itemIndex === index ? 'bg-pink-500' : 'bg-zinc-300'}`} aria-label={`Ir para aviso ${itemIndex + 1}`} onClick={() => setIndex(itemIndex)} />
          ))}
        </div>
      ) : null}
    </section>
  )
}
