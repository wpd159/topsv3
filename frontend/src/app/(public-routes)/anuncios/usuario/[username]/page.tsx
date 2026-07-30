import type { Metadata } from "next"
import AnunciosUsuarioClient from "./anuncios-usuario-client"
import { buildPublicRobotsMetadata } from "@/lib/seo/search-indexing-policy"

export async function generateMetadata({
  params,
}: {
  params: Promise<{ username: string }>
}): Promise<Metadata> {
  const { username: rawUsername } = await params
  const login = decodeURIComponent(rawUsername || "").trim()
  const title = login
    ? `Anúncios de @${login} | Tops do Job`
    : "Anúncios do usuário | Tops do Job"

  return {
    title,
    description: "Listagem de anúncios ativos deste perfil na plataforma Tops do Job.",
    robots: buildPublicRobotsMetadata(false),
  }
}

export default async function AnunciosUsuarioPage({
  params,
}: {
  params: Promise<{ username: string }>
}) {
  const { username: rawUsername } = await params
  const username = rawUsername ? decodeURIComponent(rawUsername) : ""

  return <AnunciosUsuarioClient username={username} />
}
