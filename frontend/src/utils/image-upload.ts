export const ALLOWED_IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp'

export const UNSUPPORTED_IMAGE_MESSAGE =
  'Formato de imagem não compatível. Envie fotos em JPG, PNG ou WEBP.'

export const INCOMPATIBLE_IMAGE_FORMAT_MESSAGE =
  'Essa foto está em formato HEIC/HEIF e não é compatível. Envie uma imagem JPG, PNG ou WEBP.'

export const isHeic = (f: File) => {
  const type = (f.type || '').toLowerCase()
  return (
    type === 'image/heic' ||
    type === 'image/heif' ||
    type === 'image/heic-sequence' ||
    type === 'image/heif-sequence' ||
    /\.heic$/i.test(f.name) ||
    /\.heif$/i.test(f.name)
  )
}

export async function convertHeicToJpeg(file: File) {
  const heic2any = (await import('heic2any')).default
  const blob = (await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.9 })) as Blob
  const newName = file.name.replace(/\.(heic|heif)$/i, '') + '.jpg'
  return new File([blob], newName, { type: 'image/jpeg' })
}

export function isImagemValida(file: File) {
  if (isHeic(file)) return false

  const type = (file.type || '').toLowerCase()
  const name = file.name || ''

  return (
    type === 'image/jpeg' ||
    type === 'image/jpg' ||
    type === 'image/pjpeg' ||
    type === 'image/png' ||
    type === 'image/webp' ||
    /\.(jpe?g|png|webp)$/i.test(name)
  )
}
