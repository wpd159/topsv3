'use client'

import {
  CameraIcon,
  IdentificationIcon,
  FaceSmileIcon,
  LockClosedIcon,
} from "@heroicons/react/24/outline"

export default function SegurancaSection() {
  const itens = [
    {
      icon: <CameraIcon className="w-6 h-6 text-gray-800" />,
      titulo: "Mídias 360° revisadas",
      descricao:
        "Cada foto e vídeo é verificado pela nossa equipe para garantir que o perfil é real e atualizado.",
    },
    {
      icon: <IdentificationIcon className="w-6 h-6 text-gray-800" />,
      titulo: "Identidade confirmada",
      descricao:
        "Todos os profissionais passam por verificação documental antes de seus anúncios ficarem públicos.",
    },
    {
      icon: <LockClosedIcon className="w-6 h-6 text-gray-800" />,
      titulo: "Conteúdo protegido",
      descricao:
        "Fotos e vídeos são armazenados com segurança e auditados regularmente para evitar falsificações.",
    },
  ]

  return (
    <section className="py-20 px-6 border-t border-gray-100">
      <div className="max-w-6xl mx-auto text-center">
        <h2 className="text-3xl sm:text-4xl font-bold mb-4 tracking-tight">
          Confiança que <span className="text-pink-500">se sente</span> em cada detalhe!
        </h2>
        <p className="text-gray-600 max-w-2xl mx-auto mb-16 text-base">
          Verificações reais, autenticações constantes e um time dedicado à segurança de quem anuncia e contrata.
        </p>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {itens.map((item, i) => (
            <div
              key={i}
              className="flex flex-col items-start sm:items-center bg-white rounded-xl border border-gray-300 hover:-translate-y-1 transition-all duration-300 p-6 text-left sm:text-center"
            >
              <div className="mb-3">{item.icon}</div>
              <h3 className="font-semibold text-gray-900 text-base mb-1">
                {item.titulo}
              </h3>
              <p className="text-gray-500 text-sm leading-relaxed">
                {item.descricao}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
