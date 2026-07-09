'use client'

import { Camera, Crown, PlayCircle, Sparkles } from 'lucide-react'
import { GaleriaFotos } from '@/components/anuncios/galeria-fotos'
import { VideoUploader } from '@/components/anuncios/editar/video-uploader'
import { StepPanel } from './wizard-ui'

type WizardStepFotosProps = {
  initialFiles: File[]
  initialUrls?: string[]
  fotoNomes: string[]
  onChange: (payload: File[]) => void
  onChangeExistentes?: (payload: string[]) => void
  onMediaTouched?: () => void
  maxFotos?: number
  maxMB?: number
  videosExistentes?: string[]
  onChangeVideosExistentes?: (payload: string[]) => void
  videosNovos?: File[]
  onChangeVideosNovos?: (payload: File[]) => void
  onVideoTouched?: () => void
  canUploadVideos?: boolean
}

export function WizardStepFotos({
  initialFiles,
  initialUrls = [],
  fotoNomes,
  onChange,
  onChangeExistentes,
  onMediaTouched,
  maxFotos = 4,
  maxMB = 20,
  videosExistentes,
  onChangeVideosExistentes,
  videosNovos,
  onChangeVideosNovos,
  onVideoTouched,
  canUploadVideos,
}: WizardStepFotosProps) {
  const hasVideoControls =
    Array.isArray(videosExistentes) ||
    typeof onChangeVideosExistentes === 'function' ||
    Array.isArray(videosNovos) ||
    typeof onChangeVideosNovos === 'function' ||
    typeof canUploadVideos === 'boolean'

  const premiumFotos = maxFotos > 4
  const premiumVideos = Boolean(canUploadVideos)
  const totalFotos = initialUrls.length + initialFiles.length

  return (
    <StepPanel>
      <section className="space-y-5 rounded-[28px] border border-zinc-200/80 bg-white p-5 shadow-[0_18px_60px_rgba(24,24,27,0.04)] sm:p-6">
        <div className="flex items-start gap-3">
          <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50 text-zinc-700">
            <Camera className="h-5 w-5" />
          </div>
          <div className="space-y-2">
            <h3 className="text-lg font-semibold tracking-normal text-zinc-950">
              Escolha fotos que representem bem seu perfil
            </h3>
            <p className="max-w-2xl text-sm leading-6 text-zinc-600">
              Perfis com boas fotos costumam receber mais visualizações e transmitir mais
              confiança.
            </p>
          </div>
        </div>

        <GaleriaFotos
          initialUrls={initialUrls}
          initialFiles={initialFiles}
          onChange={onChange}
          onChangeExistentes={onChangeExistentes}
          onMediaTouched={onMediaTouched}
          maxCount={maxFotos}
          variant="wizard"
        />

        <div className="grid gap-3 rounded-[24px] border border-zinc-200 bg-zinc-50/80 p-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
          <div className="space-y-2">
            <p className="text-sm font-semibold text-zinc-900">
              Seu plano atual permite até {maxFotos} {maxFotos === 1 ? 'foto' : 'fotos'}.
            </p>
            <p className="text-sm leading-6 text-zinc-600">
              Ative os recursos premium{' '}
              <span className="inline-flex items-center gap-1 rounded-full border border-fuchsia-200 bg-white px-2 py-0.5 text-xs font-semibold text-fuchsia-700">
                <Sparkles className="h-3.5 w-3.5" />
                Mais Fotos
              </span>{' '}
              e{' '}
              <span className="inline-flex items-center gap-1 rounded-full border border-rose-200 bg-white px-2 py-0.5 text-xs font-semibold text-rose-700">
                <PlayCircle className="h-3.5 w-3.5" />
                Vídeo
              </span>{' '}
              para adicionar até 10 fotos, vídeos e aumentar a visibilidade do seu anúncio.
            </p>
          </div>

          <div className="grid gap-2 rounded-[20px] border border-zinc-200 bg-white p-3 text-sm text-zinc-600 shadow-sm sm:min-w-[220px]">
            <div className="flex items-center justify-between gap-3">
              <span>Fotos adicionadas</span>
              <span className="font-semibold text-zinc-900">
                {totalFotos}/{maxFotos}
              </span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span>Mais Fotos</span>
              <span className={premiumFotos ? 'font-semibold text-zinc-900' : 'text-zinc-500'}>
                {premiumFotos ? 'Ativo' : 'Upgrade premium'}
              </span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span>Vídeo</span>
              <span className={premiumVideos ? 'font-semibold text-zinc-900' : 'text-zinc-500'}>
                {premiumVideos ? 'Ativo' : 'Upgrade premium'}
              </span>
            </div>
          </div>
        </div>

        <p className="text-xs leading-5 text-zinc-500">
          Até {maxMB}MB por imagem. Formatos JPG, PNG ou WEBP.{' '}
          {fotoNomes.length > 0
            ? `${fotoNomes.length} arquivo(s) novo(s) selecionado(s).`
            : 'Fotos bem iluminadas costumam transmitir mais confiança e aumentar a taxa de contato.'}
        </p>
      </section>

      {hasVideoControls ? (
        <section className="space-y-5 rounded-[28px] border border-zinc-200/80 bg-white p-5 shadow-[0_18px_60px_rgba(24,24,27,0.04)] sm:p-6">
          <div className="flex items-start gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl border border-zinc-200 bg-zinc-50 text-zinc-700">
              <PlayCircle className="h-5 w-5" />
            </div>
            <div className="space-y-2">
              <h3 className="text-lg font-semibold tracking-normal text-zinc-950">
                Adicione vídeos ao anúncio
              </h3>
              <p className="max-w-2xl text-sm leading-6 text-zinc-600">
                Vídeos ajudam a mostrar mais presença, detalhes do perfil e costumam aumentar a
                taxa de contato quando o recurso está disponível no anúncio.
              </p>
            </div>
          </div>

          <div className="rounded-[24px] border border-zinc-200 bg-zinc-50/80 p-4">
            <VideoUploader
              existing={videosExistentes ?? []}
              onChangeExisting={onChangeVideosExistentes}
              newVideos={videosNovos ?? []}
              onChangeNew={onChangeVideosNovos}
              onMediaTouched={onVideoTouched}
              canUpload={canUploadVideos ?? false}
            />
          </div>

          {!premiumVideos ? (
            <div className="inline-flex items-center gap-2 rounded-full border border-zinc-200 bg-white px-3 py-1.5 text-xs font-medium text-zinc-600 shadow-sm">
              <Crown className="h-3.5 w-3.5 text-fuchsia-600" />
              Ative o recurso premium Vídeo para enviar clipes ao anúncio.
            </div>
          ) : null}
        </section>
      ) : null}
    </StepPanel>
  )
}
