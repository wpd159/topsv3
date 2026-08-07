import type { Metadata } from "next"
import AnunciosUsuarioClient from "./anuncios-usuario-client"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string }>
}): Promise<Metadata> {
  await params

  return {
    title: "Anúncios da anunciante | Tops do Job",
    description: "Listagem dos anúncios públicos disponíveis desta anunciante no Tops do Job.",
    robots: buildPublicRobotsMetadata(false),
  }
}

export default async function AnunciosUsuarioPage({
  params,
}: {
  params: Promise<{ username: string }>
}) {
  const { username: rawUsername } = await params
  const publicUsername = rawUsername ? decodeURIComponent(rawUsername) : ""

  return <AnunciosUsuarioClient publicUsername={publicUsername} />
}
