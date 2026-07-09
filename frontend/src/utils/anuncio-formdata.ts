export type BuildAnuncioFormDataInput = {
  titulo: string
  categoria: string
  precoNumerico: string
  horario: string
  descricao: string
  linkConteudo?: string
  cidadeId: string
  bairroId: string
  pontoReferenciaTexto?: string
  servicos: string[]
  locaisAtendimento: string[]
  fotosExistentes: string[]
  videosExistentes: string[]
  fotosNovas: File[]
  videosNovos: File[]
  midiasAlteradas?: boolean
  fotosAlteradas?: boolean
  videosAlterados?: boolean
}

export function buildAnuncioEditFormData(d: BuildAnuncioFormDataInput) {
  const fd = new FormData()
  const fotosAlteradas = Boolean(d.fotosAlteradas) || d.fotosNovas.length > 0
  const videosAlterados = Boolean(d.videosAlterados) || d.videosNovos.length > 0
  const midiasAlteradas = Boolean(d.midiasAlteradas) || fotosAlteradas || videosAlterados

  fd.append('titulo', d.titulo)
  fd.append('categoria', d.categoria)
  fd.append('preco', d.precoNumerico.trim())
  fd.append('horario', d.horario)
  fd.append('descricao', d.descricao)
  fd.append('linkConteudo', d.linkConteudo || '')
  fd.append('cidadeId', d.cidadeId)
  fd.append('bairroId', d.bairroId)
  fd.append('pontoReferenciaTexto', d.pontoReferenciaTexto || '')

  d.servicos.forEach((s) => fd.append('servicos', s))
  d.locaisAtendimento.forEach((l) => fd.append('locaisAtendimento', l))
  fd.append('localAtendimento', d.locaisAtendimento[0] ?? '')

  fd.append('midiasAlteradas', String(midiasAlteradas))

  if (fotosAlteradas) {
    if (d.fotosExistentes.length === 0) {
      fd.append('fotosExistentes', '')
    } else {
      d.fotosExistentes.forEach((u) => fd.append('fotosExistentes', u))
    }
  }

  if (videosAlterados) {
    if (d.videosExistentes.length === 0) {
      // Spring precisa receber o campo para distinguir lista vazia explicita de campo ausente.
      fd.append('videosExistentes', '')
    } else {
      d.videosExistentes.forEach((u) => fd.append('videosExistentes', u))
    }
  }

  d.fotosNovas.forEach((f) => fd.append('novasFotos', f))
  d.videosNovos.forEach((v) => fd.append('novosVideos', v))

  return fd
}
