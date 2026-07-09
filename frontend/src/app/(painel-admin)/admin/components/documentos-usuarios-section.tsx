'use client'

import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  ArrowDownTrayIcon,
  ArrowTopRightOnSquareIcon,
  DocumentTextIcon,
  EyeIcon,
  PencilSquareIcon,
  PhotoIcon,
} from '@heroicons/react/24/solid'
import { cn } from '@/lib/utils'

type DocVM = {
  url: string
  label: string
  isPdf: boolean
  fileName: string
  typeLabel: string
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

function isPdfUrl(url: string, fileName: string) {
  return (
    /(?:\.pdf)(?:$|[?#])/i.test(url) ||
    /\.pdf$/i.test(fileName) ||
    url.toLowerCase().includes('application/pdf')
  )
}

async function loadAsObjectUrl(url: string): Promise<string> {
  let sameOrigin = false
  try {
    sameOrigin =
      new URL(url, typeof window !== 'undefined' ? window.location.href : undefined).origin ===
      window.location.origin
  } catch {
    sameOrigin = false
  }

  const res = await fetch(url, {
    cache: 'no-store',
    mode: 'cors',
    credentials: sameOrigin ? 'include' : 'omit',
  })
  if (!res.ok) {
    throw new Error(`Falha ao baixar imagem (${res.status})`)
  }

  const blob = await res.blob()
  return URL.createObjectURL(blob)
}

export default function DocumentosUsuarioSection({
  documentos,
  manageHref,
  showTable = true,
}: {
  documentos: string[]
  manageHref?: string
  showTable?: boolean
}) {
  const docs = useMemo<DocVM[]>(() => {
    const arr = Array.isArray(documentos) ? documentos.filter(Boolean) : []
    const uniqueDocs = Array.from(
      new Map(
        arr.map((url) => {
          const normalized = url.split('?')[0].split('#')[0].trim().toLowerCase()
          return [normalized, url]
        })
      ).values()
    )

    return uniqueDocs.map((url, idx) => {
      const fileName = fileNameFromUrl(url)
      const isPdf = isPdfUrl(url, fileName)
      return {
        url,
        label:
          idx === 0 ? 'Documento (frente)' : idx === 1 ? 'Documento (verso)' : `Documento ${idx + 1}`,
        isPdf,
        fileName,
        typeLabel: isPdf ? 'PDF' : 'Imagem',
      }
    })
  }, [documentos])

  const [open, setOpen] = useState(false)
  const [active, setActive] = useState<DocVM | null>(null)
  const [imgError, setImgError] = useState<Record<string, boolean>>({})
  const [blobUrls, setBlobUrls] = useState<Record<string, string>>({})
  const [loadingBlob, setLoadingBlob] = useState<Record<string, boolean>>({})

  useEffect(() => {
    return () => {
      Object.values(blobUrls).forEach((url) => {
        try {
          URL.revokeObjectURL(url)
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

    setLoadingBlob((prev) => ({ ...prev, [url]: true }))
    try {
      const blobUrl = await loadAsObjectUrl(url)
      setBlobUrls((prev) => ({ ...prev, [url]: blobUrl }))
    } catch {
      setImgError((prev) => ({ ...prev, [url]: true }))
    } finally {
      setLoadingBlob((prev) => ({ ...prev, [url]: false }))
    }
  }

  if (!docs.length) return null

  return (
    <section className="mb-8 w-full rounded-xl border bg-white shadow-sm">
      <div className="flex flex-col gap-3 border-b bg-[#FC1EAD]/10 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <DocumentTextIcon className="h-5 w-5 text-[#C41E73]" />
            <h2 className="text-base font-semibold text-gray-800">Documentos enviados</h2>
          </div>
          <p className="mt-1 text-sm text-gray-600">
            Arquivos enviados pelo usuário, com preview e ações rápidas.
          </p>
        </div>

        {manageHref ? (
          <Button
            variant="outline"
            className="border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
            onClick={() => window.location.assign(manageHref)}
          >
            <PencilSquareIcon className="mr-2 h-4 w-4" />
            Gerenciar documentos
          </Button>
        ) : null}
      </div>

      <div className="grid grid-cols-1 gap-4 p-6 sm:grid-cols-2 lg:grid-cols-3">
        {docs.map((doc) => {
          const broken = Boolean(imgError[doc.url])
          const src = blobUrls[doc.url] || doc.url
          const isLoading = Boolean(loadingBlob[doc.url])

          return (
            <div key={`${doc.url}-${doc.fileName}`} className="overflow-hidden rounded-xl border bg-white shadow-sm">
              <div className="relative flex h-44 items-center justify-center border-b bg-gray-50">
                {doc.isPdf ? (
                  <div className="flex flex-col items-center justify-center px-4 text-center">
                    <DocumentTextIcon className="h-10 w-10 text-[#C41E73]" />
                    <p className="mt-2 line-clamp-2 text-xs text-gray-600">{doc.fileName}</p>
                    <span className="mt-1 text-[10px] text-gray-500">PDF</span>
                  </div>
                ) : broken ? (
                  <div className="flex flex-col items-center justify-center px-4 text-center">
                    <PhotoIcon className="h-10 w-10 text-gray-400" />
                    <p className="mt-2 text-xs text-gray-600">Pré-visualização indisponível</p>
                  </div>
                ) : (
                  <>
                    <img
                      src={src}
                      alt={doc.label}
                      className="h-full w-full object-cover"
                      loading="lazy"
                      referrerPolicy="no-referrer"
                      onError={() => void ensureBlob(doc.url)}
                    />
                    {isLoading ? (
                      <div className="absolute inset-0 flex items-center justify-center bg-white/60 text-xs text-gray-600">
                        Carregando prévia...
                      </div>
                    ) : null}
                  </>
                )}
              </div>

              <div className="space-y-3 p-4">
                <div>
                  <p className="font-semibold text-gray-900">{doc.label}</p>
                  <p className="line-clamp-1 text-xs text-gray-500">{doc.fileName}</p>
                </div>

                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    className="flex-1 border-[#FC1EAD]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                    onClick={() => visualizar(doc)}
                  >
                    <EyeIcon className="mr-2 h-4 w-4" />
                    Visualizar
                  </Button>

                  <Button
                    asChild
                    variant="outline"
                    className="border-gray-300 text-gray-700 hover:bg-gray-50"
                    title="Baixar documento"
                  >
                    <a href={doc.url} target="_blank" rel="noopener noreferrer" download={doc.fileName}>
                      <ArrowDownTrayIcon className="h-4 w-4" />
                    </a>
                  </Button>

                  <Button
                    variant="outline"
                    className="border-gray-300 text-gray-700 hover:bg-gray-50"
                    onClick={() => window.open(doc.url, '_blank', 'noopener,noreferrer')}
                    title="Abrir em nova aba"
                  >
                    <ArrowTopRightOnSquareIcon className="h-4 w-4" />
                  </Button>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {showTable && (
        <div className="border-t px-6 py-5">
          <div className="overflow-hidden rounded-xl border border-gray-100">
            <div className="grid grid-cols-[120px_minmax(0,1fr)_220px] gap-4 bg-gray-50 px-4 py-3 text-[11px] font-semibold uppercase tracking-wide text-gray-500">
              <span>Tipo</span>
              <span>Arquivo</span>
              <span className="text-right">Ações</span>
            </div>

            <div className="divide-y divide-gray-100">
              {docs.map((doc) => (
                <div
                  key={`${doc.url}-row`}
                  className="grid grid-cols-1 gap-3 px-4 py-4 text-sm text-gray-700 md:grid-cols-[120px_minmax(0,1fr)_220px] md:items-center"
                >
                  <div className="font-medium text-gray-900">{doc.typeLabel}</div>
                  <div className="min-w-0">
                    <p className="truncate font-medium text-gray-900">{doc.fileName}</p>
                    <p className="text-xs text-gray-500">{doc.label}</p>
                  </div>
                  <div className="flex flex-wrap justify-start gap-2 md:justify-end">
                    <Button size="sm" variant="outline" onClick={() => visualizar(doc)}>
                      <EyeIcon className="mr-1 h-4 w-4" />
                      Ver
                    </Button>
                    <Button size="sm" variant="outline" asChild>
                      <a href={doc.url} target="_blank" rel="noopener noreferrer" download={doc.fileName}>
                        <ArrowDownTrayIcon className="mr-1 h-4 w-4" />
                        Baixar
                      </a>
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className={cn('overflow-hidden p-0 sm:max-w-4xl')}>
          {active ? (
            <>
              <div className="border-b px-4 py-3">
                <DialogHeader>
                  <DialogTitle className="font-semibold text-gray-800">
                    {active.label} <span className="text-sm text-gray-400">{active.fileName}</span>
                  </DialogTitle>
                </DialogHeader>
              </div>

              <div className="bg-black/5">
                {active.isPdf ? (
                  <div className="p-3">
                    <iframe
                      src={active.url}
                      className="h-[70vh] w-full rounded-md bg-white"
                      title={active.fileName}
                    />
                    <div className="mt-3 flex justify-end">
                      <Button
                        variant="outline"
                        onClick={() => window.open(active.url, '_blank', 'noopener,noreferrer')}
                      >
                        <ArrowTopRightOnSquareIcon className="mr-2 h-4 w-4" />
                        Abrir em nova aba
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="p-3">
                    <img
                      src={blobUrls[active.url] || active.url}
                      alt={active.label}
                      className="max-h-[70vh] w-full rounded-md bg-white object-contain"
                      referrerPolicy="no-referrer"
                      onError={() => void ensureBlob(active.url)}
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
