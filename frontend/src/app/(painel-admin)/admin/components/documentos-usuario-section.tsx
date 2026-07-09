'use client'

import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  EyeIcon,
  ArrowTopRightOnSquareIcon,
  DocumentTextIcon,
  PhotoIcon,
} from '@heroicons/react/24/solid'
import { cn } from '@/lib/utils'

type DocVM = {
  url: string
  label: string
  isPdf: boolean
  fileName: string
}

function isPdfUrl(url: string) {
  return /(?:\.pdf)(?:$|[?#])/i.test(url) || url.toLowerCase().includes('application/pdf')
}

function fileNameFromUrl(url: string) {
  try {
    const clean = url.split('?')[0].split('#')[0]
    const last = clean.split('/').pop() || ''
    return decodeURIComponent(last) || 'arquivo'
  } catch {
    return 'arquivo'
  }
}

async function loadAsObjectUrl(url: string): Promise<string> {
  // pega a imagem como blob e cria uma URL local (não depende de content-type/cors do <img>)
  const res = await fetch(url, { cache: 'no-store', credentials: 'omit' })
  if (!res.ok) throw new Error(`Falha ao baixar imagem (${res.status})`)
  const blob = await res.blob()
  return URL.createObjectURL(blob)
}

export default function DocumentosUsuarioSection({ documentos }: { documentos: string[] }) {
  const docs = useMemo<DocVM[]>(() => {
    const arr = Array.isArray(documentos) ? documentos.filter(Boolean) : []
    return arr.map((url, idx) => {
      const fileName = fileNameFromUrl(url)
      return {
        url,
        label: `Documento ${idx + 1}`,
        isPdf: isPdfUrl(url),
        fileName,
      }
    })
  }, [documentos])

  const [open, setOpen] = useState(false)
  const [active, setActive] = useState<DocVM | null>(null)

  // marca falha total (nem blob resolve)
  const [imgError, setImgError] = useState<Record<string, boolean>>({})
  // cache de blob urls por doc.url
  const [blobUrls, setBlobUrls] = useState<Record<string, string>>({})
  // evita fetch duplicado em loop
  const [loadingBlob, setLoadingBlob] = useState<Record<string, boolean>>({})

  // cleanup de object URLs (evita memory leak)
  useEffect(() => {
    return () => {
      Object.values(blobUrls).forEach((u) => {
        try {
          URL.revokeObjectURL(u)
        } catch {}
      })
    }
  }, [blobUrls])

  const visualizar = (doc: DocVM) => {
    setActive(doc)
    setOpen(true)
  }

  const ensureBlob = async (url: string) => {
    if (blobUrls[url] || loadingBlob[url]) return
    setLoadingBlob((p) => ({ ...p, [url]: true }))
    try {
      const blobUrl = await loadAsObjectUrl(url)
      setBlobUrls((p) => ({ ...p, [url]: blobUrl }))
    } catch {
      setImgError((p) => ({ ...p, [url]: true }))
    } finally {
      setLoadingBlob((p) => ({ ...p, [url]: false }))
    }
  }

  if (!docs.length) return null

  return (
    <section className="w-full rounded-xl border bg-white shadow-sm mb-8">
      <div className="px-6 py-4 border-b bg-[#FC1EAD]/10">
        <div className="flex items-center gap-2">
          <DocumentTextIcon className="w-5 h-5 text-[#C41E73]" />
          <h2 className="text-base font-semibold text-gray-800">Documentos Enviados</h2>
        </div>
        <p className="text-sm text-gray-600 mt-1">Arquivos enviados pelo usuário para validação.</p>
      </div>

      <div className="p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {docs.map((doc) => {
          const broken = !!imgError[doc.url]
          const src = blobUrls[doc.url] || doc.url
          const isLoading = !!loadingBlob[doc.url]

          return (
            <div key={`${doc.url}-${doc.fileName}`} className="rounded-xl border bg-white shadow-sm overflow-hidden">
              <div className="h-44 bg-gray-50 border-b flex items-center justify-center relative">
                {doc.isPdf ? (
                  <div className="flex flex-col items-center justify-center text-center px-4">
                    <DocumentTextIcon className="w-10 h-10 text-[#C41E73]" />
                    <p className="mt-2 text-xs text-gray-600 line-clamp-2">{doc.fileName}</p>
                    <span className="mt-1 text-[10px] text-gray-500">PDF</span>
                  </div>
                ) : broken ? (
                  <div className="flex flex-col items-center justify-center text-center px-4">
                    <PhotoIcon className="w-10 h-10 text-gray-400" />
                    <p className="mt-2 text-xs text-gray-600">Pré-visualização indisponível</p>
                  </div>
                ) : (
                  <>
                    <img
                      src={src}
                      alt={doc.label}
                      className="w-full h-full object-cover"
                      loading="lazy"
                      crossOrigin="anonymous"
                      // 🔥 primeira falha: tenta blob fallback
                      onError={() => ensureBlob(doc.url)}
                    />

                    {isLoading ? (
                      <div className="absolute inset-0 flex items-center justify-center bg-white/60 text-xs text-gray-600">
                        Carregando prévia...
                      </div>
                    ) : null}
                  </>
                )}
              </div>

              <div className="p-4 space-y-3">
                <div>
                  <p className="font-semibold text-gray-900">{doc.label}</p>
                  <p className="text-xs text-gray-500 line-clamp-1">{doc.fileName}</p>
                </div>

                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    className="flex-1 border-[#FC1EAD]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                    onClick={() => visualizar(doc)}
                  >
                    <EyeIcon className="w-4 h-4 mr-2" />
                    Visualizar
                  </Button>

                  <Button
                    variant="outline"
                    className="border-gray-300 text-gray-700 hover:bg-gray-50"
                    onClick={() => window.open(doc.url, '_blank', 'noopener,noreferrer')}
                    title="Abrir em nova aba"
                  >
                    <ArrowTopRightOnSquareIcon className="w-4 h-4" />
                  </Button>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className={cn('sm:max-w-4xl p-0 overflow-hidden')}>
          {active ? (
            <>
              <div className="border-b px-4 py-3">
                <DialogHeader>
                  <DialogTitle className="font-semibold text-gray-800">
                    {active.label}{' '}
                    <span className="text-gray-400 text-sm">{active.fileName}</span>
                  </DialogTitle>
                </DialogHeader>
              </div>

              <div className="bg-black/5">
                {active.isPdf ? (
                  <div className="p-3">
                    <iframe
                      src={active.url}
                      className="w-full h-[70vh] rounded-md bg-white"
                      title={active.fileName}
                    />
                    <div className="mt-3 flex justify-end">
                      <Button
                        variant="outline"
                        onClick={() => window.open(active.url, '_blank', 'noopener,noreferrer')}
                      >
                        <ArrowTopRightOnSquareIcon className="w-4 h-4 mr-2" />
                        Abrir em nova aba
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="p-3">
                    <img
                      src={blobUrls[active.url] || active.url}
                      alt={active.label}
                      className="w-full max-h-[70vh] object-contain rounded-md bg-white"
                      crossOrigin="anonymous"
                      onError={() => ensureBlob(active.url)}
                    />
                  </div>
                )}
              </div>
            </>
          ) : null}
        </DialogContent>
      </Dialog>
    </section>
  )
}
