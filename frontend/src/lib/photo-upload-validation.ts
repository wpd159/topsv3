// Mirrors MidiaUploadValidator and the configured defaults in application.yml.
// This is a preventive browser check; the backend remains the final authority.
export const PHOTO_MAX_BYTES = 20 * 1024 * 1024
export const PHOTO_MAX_DIMENSION = 20_000
export const PHOTO_MAX_PIXELS = 40_000_000
export const PHOTO_UPLOAD_ACCEPT = '.jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp'
export const PHOTO_UPLOAD_GUIDANCE = 'Fotos JPG, PNG ou WebP, com até 20 MiB (20.971.520 bytes) por arquivo.'
export const PHOTO_FORMAT_MESSAGE = 'Formato não aceito. Selecione uma foto JPG, PNG ou WebP.'
export const JPEG_COMPATIBILITY_MESSAGE = 'Não conseguimos enviar esta foto. Abra a imagem em um editor e salve uma nova cópia em JPG ou PNG. Depois, selecione essa cópia.'

export type PhotoUploadValidationResult = { valid: true } | { valid: false; message: string }

const validations = new WeakMap<File, Promise<PhotoUploadValidationResult>>()
const pngSignature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
const unreadableMessage = 'Não conseguimos ler esta foto. Abra a imagem em um editor e salve uma nova cópia em JPG ou PNG. Depois, selecione essa cópia.'

function extension(file: Pick<File, 'name'>): string {
  const name = file.name.trim().toLowerCase()
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1)
}

// Used only by the existing mixed photo/video selectors. MIME alone must not
// let an unsupported photo bypass validation by claiming to be a video.
export function isSupportedUploadVideo(file: Pick<File, 'name'>): boolean {
  return ['mp4', 'mov'].includes(extension(file))
}

async function imageDimensions(blob: Blob): Promise<{ width: number; height: number }> {
  if (typeof createImageBitmap === 'function') {
    const bitmap = await createImageBitmap(blob, { imageOrientation: 'none' })
    try {
      return { width: bitmap.width, height: bitmap.height }
    } finally {
      bitmap.close()
    }
  }
  const url = URL.createObjectURL(blob)
  try {
    return await new Promise((resolve, reject) => {
      const image = new Image()
      image.onload = () => resolve({ width: image.naturalWidth, height: image.naturalHeight })
      image.onerror = () => reject(new Error('Photo decoding failed'))
      image.src = url
    })
  } finally {
    URL.revokeObjectURL(url)
  }
}

async function validate(file: File): Promise<PhotoUploadValidationResult> {
  const invalid = (message: string): PhotoUploadValidationResult => ({ valid: false, message })
  if (file.size === 0) return invalid('O arquivo está vazio. Selecione outra foto.')
  if (file.size > PHOTO_MAX_BYTES) {
    return invalid('A foto deve ter no máximo 20 MiB (20.971.520 bytes). Reduza o tamanho do arquivo e selecione novamente.')
  }
  const ext = extension(file)
  if (!['jpg', 'jpeg', 'png', 'webp'].includes(ext)) return invalid(PHOTO_FORMAT_MESSAGE)
  try {
    const bytes = new Uint8Array(await file.arrayBuffer())
    const jpeg = bytes[0] === 0xff && bytes[1] === 0xd8
    let mime: string
    if (jpeg && (ext === 'jpg' || ext === 'jpeg')) {
      // The current backend requires EOI at the very end, including for a
      // decodable JPEG with extra bytes appended after its FFD9 marker.
      if (bytes.length < 12 || bytes[bytes.length - 2] !== 0xff || bytes[bytes.length - 1] !== 0xd9) {
        return invalid(JPEG_COMPATIBILITY_MESSAGE)
      }
      mime = 'image/jpeg'
    } else if (bytes.length >= 12 && ext === 'png' && pngSignature.every((byte, index) => bytes[index] === byte)) {
      mime = 'image/png'
    } else if (bytes.length >= 12 && ext === 'webp'
      && bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46
      && bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) {
      mime = 'image/webp'
    } else {
      return invalid(PHOTO_FORMAT_MESSAGE)
    }
    // Supply the verified media type only to the decoder. The original File,
    // its bytes and its name are never converted or changed before upload.
    const { width, height } = await imageDimensions(file.slice(0, file.size, mime))
    if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1) {
      return invalid(jpeg ? JPEG_COMPATIBILITY_MESSAGE : unreadableMessage)
    }
    if (width > PHOTO_MAX_DIMENSION || height > PHOTO_MAX_DIMENSION || width * height > PHOTO_MAX_PIXELS) {
      return invalid('A foto deve ter no máximo 20.000 pixels em cada lado e 40 milhões de pixels no total. Reduza as dimensões da imagem e selecione novamente.')
    }
    return { valid: true }
  } catch {
    return invalid(ext === 'jpg' || ext === 'jpeg' ? JPEG_COMPATIBILITY_MESSAGE : unreadableMessage)
  }
}

export function validatePhotoUpload(file: File): Promise<PhotoUploadValidationResult> {
  let result = validations.get(file)
  if (!result) {
    result = validate(file)
    validations.set(file, result)
  }
  return result
}

export class PhotoUploadValidationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'PhotoUploadValidationError'
  }
}

export async function assertPhotoUpload(file: File): Promise<void> {
  const result = await validatePhotoUpload(file)
  if (!result.valid) throw new PhotoUploadValidationError(result.message)
}
