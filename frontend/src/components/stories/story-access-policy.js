export function sanitizeStoryFeedItem(item) {
  const previewUrl = item?.previewState === 'AVAILABLE'
    && typeof item?.previewUrl === 'string'
    && item.previewUrl.trim()
    ? item.previewUrl
    : null
  return { ...item, previewUrl }
}

export function sanitizeStoryViewerItem(item) {
  const midiaUrl = item?.viewerState === 'LIBERADO'
    && typeof item?.midiaUrl === 'string'
    && item.midiaUrl.trim()
    ? item.midiaUrl
    : null
  return { ...item, midiaUrl }
}

export function canNavigateFromStory(item, mediaReady, verificationOpen) {
  return item?.viewerState === 'LIBERADO'
    && Boolean(item?.midiaUrl)
    && Boolean(mediaReady)
    && !verificationOpen
}
