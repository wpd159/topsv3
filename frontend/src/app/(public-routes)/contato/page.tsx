import { buildPublicStaticMetadata } from "@/lib/seo/public-static-metadata"
import ContatoPageClient from "./contato-page-client"

export const metadata = buildPublicStaticMetadata({
  path: "/contato",
  title: "Contato e suporte | Tops do Job",
  description:
    "Consulte os canais de atendimento, privacidade e segurança do Tops do Job e saiba como falar com a equipe pelo suporte interno.",
})

export default function ContatoPage() {
  return <ContatoPageClient />
}
