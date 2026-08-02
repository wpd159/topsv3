export function sanitizeStoryFeedItem(item) {
  const previewUrl = item?.previewState === 'AVAILABLE'
    && typeof item?.previewUrl === 'string'
    && item.previewUrl.trim()
    ? item.previewUrl
    : null
  const anuncioBloqueado = item?.modoConteudo === 'ANUNCIO' && item?.previewState !== 'AVAILABLE'
  return {
    ...item,
    previewUrl,
    displayUsername: anuncioBloqueado ? null : item?.displayUsername ?? null,
    idade: anuncioBloqueado ? null : item?.idade ?? null,
  }
}

export function sanitizeStoryViewerItem(item) {
  const midiaUrl = item?.viewerState === 'LIBERADO'
    && typeof item?.midiaUrl === 'string'
    && item.midiaUrl.trim()
    ? item.midiaUrl
    : null
  const anuncioLiberado = item?.viewerState === 'LIBERADO' && item?.modoConteudo === 'ANUNCIO'
  return {
    ...item,
    midiaUrl,
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
