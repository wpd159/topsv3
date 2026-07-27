'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useAuth } from '@/context/AuthContext'
import {
  incluirFavorito,
  listarFavoritos,
  removerFavorito,
  type FavoritoPublico,
} from '@/lib/favoritos-api'

type FavoritosContextType = {
  itens: FavoritoPublico[]
  carregando: boolean
  erro: string | null
  isFavorito: (slug: string) => boolean
  isPendente: (slug: string) => boolean
  alternar: (slug: string) => Promise<boolean>
  recarregar: () => Promise<void>
}

const FavoritosContext = createContext<FavoritosContextType | undefined>(undefined)

export function FavoritosProvider({ children }: { children: ReactNode }) {
  const { usuario, carregando: carregandoSessao } = useAuth()
  const [itens, setItens] = useState<FavoritoPublico[]>([])
  const [slugs, setSlugs] = useState<Set<string>>(new Set())
  const [pendentes, setPendentes] = useState<Set<string>>(new Set())
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const requestId = useRef(0)
  const pendentesRef = useRef<Set<string>>(new Set())
  const sessaoPublica = usuario?.cargo === 'USUARIO'

  const aplicar = useCallback((favoritos: FavoritoPublico[]) => {
    setItens(favoritos)
    setSlugs(new Set(favoritos.map((item) => item.slug)))
  }, [])

  const recarregar = useCallback(async () => {
    if (!sessaoPublica) return
    const current = ++requestId.current
    setCarregando(true)
    setErro(null)
    try {
      const favoritos = await listarFavoritos()
      if (requestId.current === current) aplicar(favoritos)
    } catch (error) {
      if (requestId.current === current) {
        setErro(error instanceof Error ? error.message : 'Não foi possível carregar os favoritos.')
      }
    } finally {
      if (requestId.current === current) setCarregando(false)
    }
  }, [aplicar, sessaoPublica])

  useEffect(() => {
    if (carregandoSessao) return
    if (!sessaoPublica) {
      requestId.current++
      setItens([])
      setSlugs(new Set())
      setPendentes(new Set())
      pendentesRef.current.clear()
      setErro(null)
      setCarregando(false)
      return
    }
    void recarregar()
  }, [carregandoSessao, recarregar, sessaoPublica, usuario?.id])

  const alternar = useCallback(
    async (slug: string) => {
      if (!sessaoPublica || pendentesRef.current.has(slug)) return false
      const estavaFavorito = slugs.has(slug)
      const indiceAnterior = itens.findIndex((item) => item.slug === slug)
      const itemAnterior = indiceAnterior >= 0 ? itens[indiceAnterior] : null
      pendentesRef.current.add(slug)
      setPendentes((current) => new Set(current).add(slug))
      setErro(null)
      setSlugs((current) => {
        const next = new Set(current)
        if (estavaFavorito) next.delete(slug)
        else next.add(slug)
        return next
      })
      if (estavaFavorito) setItens((current) => current.filter((item) => item.slug !== slug))

      try {
        let novoEstado: boolean
        if (estavaFavorito) {
          novoEstado = (await removerFavorito(slug)).favorito
        } else {
          novoEstado = (await incluirFavorito(slug)).favorito
          try {
            const atualizados = await listarFavoritos()
            aplicar(atualizados)
          } catch (refreshError) {
            setErro(
              refreshError instanceof Error
                ? refreshError.message
                : 'Favorito incluído, mas a lista não pôde ser atualizada.'
            )
          }
        }
        setSlugs((current) => {
          const next = new Set(current)
          if (novoEstado) next.add(slug)
          else next.delete(slug)
          return next
        })
        return novoEstado
      } catch (error) {
        setSlugs((current) => {
          const next = new Set(current)
          if (estavaFavorito) next.add(slug)
          else next.delete(slug)
          return next
        })
        if (estavaFavorito && itemAnterior) {
          setItens((current) => {
            if (current.some((item) => item.slug === slug)) return current
            const next = [...current]
            next.splice(Math.min(indiceAnterior, next.length), 0, itemAnterior)
            return next
          })
        }
        const message = error instanceof Error ? error.message : 'Não foi possível atualizar o favorito.'
        setErro(message)
        throw error
      } finally {
        pendentesRef.current.delete(slug)
        setPendentes((current) => {
          const next = new Set(current)
          next.delete(slug)
          return next
        })
      }
    },
    [aplicar, itens, sessaoPublica, slugs]
  )

  const value = useMemo<FavoritosContextType>(
    () => ({
      itens,
      carregando,
      erro,
      isFavorito: (slug) => slugs.has(slug),
      isPendente: (slug) => pendentes.has(slug),
      alternar,
      recarregar,
    }),
    [alternar, carregando, erro, itens, pendentes, recarregar, slugs]
  )

  return <FavoritosContext.Provider value={value}>{children}</FavoritosContext.Provider>
}

export function useFavoritos() {
  const context = useContext(FavoritosContext)
  if (!context) throw new Error('useFavoritos deve ser usado dentro de FavoritosProvider')
  return context
}
