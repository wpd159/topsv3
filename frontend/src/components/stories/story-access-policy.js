export function sanitizeStoryFeedItem(item) {
  const previewUrl = item?.previewState === 'AVAILABLE'
    && typeof item?.previewUrl === 'string'
    && item.previewUrl.trim()
    ? item.previewUrl
    : null
  const acessoLiberado = item?.previewState === 'AVAILABLE'
  const anuncioBloqueado = item?.modoConteudo === 'ANUNCIO' && !acessoLiberado
  return {
    ...item,
    previewUrl,
    usuarioUsername: acessoLiberado ? item?.usuarioUsername ?? null : null,
    profileNavigable: acessoLiberado && item?.profileNavigable === true,
    displayUsername: anuncioBloqueado ? null : item?.displayUsername ?? null,
    idade: anuncioBloqueado ? null : item?.idade ?? null,
  }
}

function sanitizeStoryViewerMedia(media) {
  if (!media || media.autorizada !== true) return null
  const tipo = media.tipo === 'VIDEO' ? 'VIDEO' : media.tipo === 'FOTO' ? 'FOTO' : null
  const urlPublica = typeof media.urlPublica === 'string' ? media.urlPublica.trim() : ''
  const urlSegura = (urlPublica.startsWith('/') || urlPublica.startsWith('https://'))
    && !/[?&](?:X-Amz-|AWSAccessKeyId=|Signature=)/i.test(urlPublica)
  if (!tipo || !urlPublica || !urlSegura) return null
  return {
    id: typeof media.id === 'string' ? media.id : null,
    tipo,
    ordem: Number.isInteger(media.ordem) ? media.ordem : null,
    autorizada: true,
    urlPublica,
    largura: Number.isInteger(media.largura) ? media.largura : null,
    altura: Number.isInteger(media.altura) ? media.altura : null,
    mimeType: typeof media.mimeType === 'string' ? media.mimeType : null,
  }
}

export function sanitizeStoryViewerItem(item) {
  const acessoLiberado = item?.viewerState === 'LIBERADO'
  const midiaUrl = acessoLiberado
    && typeof item?.midiaUrl === 'string'
    && item.midiaUrl.trim()
    ? item.midiaUrl
    : null
  const anuncioLiberado = item?.viewerState === 'LIBERADO' && item?.modoConteudo === 'ANUNCIO'
  const midias = anuncioLiberado && Array.isArray(item?.midias)
    ? item.midias.map(sanitizeStoryViewerMedia).filter(Boolean)
    : []
  return {
    ...item,
    midiaUrl,
    midias,
    usuarioUsername: acessoLiberado ? item?.usuarioUsername ?? null : null,
    profileNavigable: acessoLiberado && item?.profileNavigable === true,
    displayUsername: anuncioLiberado || item?.modoConteudo !== 'ANUNCIO'
      ? item?.displayUsername ?? null
      : null,
    idade: anuncioLiberado || item?.modoConteudo !== 'ANUNCIO'
      ? item?.idade ?? null
      : null,
    cidade: anuncioLiberado && typeof item?.cidade === 'string' ? item.cidade : null,
    uf: anuncioLiberado && typeof item?.uf === 'string' ? item.uf : null,
    preco: anuncioLiberado && typeof item?.preco === 'number' ? item.preco : null,
    resumo: anuncioLiberado && typeof item?.resumo === 'string' ? item.resumo : null,
  }
}

export function canNavigateFromStory(item, contentReady, verificationOpen) {
  return item?.viewerState === 'LIBERADO'
    && (item?.modoConteudo === 'ANUNCIO' || Boolean(item?.midiaUrl))
    && Boolean(contentReady)
    && !verificationOpen
}
