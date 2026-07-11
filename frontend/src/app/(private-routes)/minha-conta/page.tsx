'use client'

import { useEffect, useState } from 'react'
import { EnvelopeIcon, PhoneIcon, UserCircleIcon } from '@heroicons/react/24/outline'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PainelShell } from '@/components/painel-anunciante/painel-shell'
import { useAuth } from '@/context/AuthContext'
import {
  PublicAuthApiError,
  updatePublicProfile,
} from '@/lib/public-auth-api'

export default function MinhaContaPage() {
  const { usuario, refresh } = useAuth()
  const [username, setUsername] = useState('')
  const [telefone, setTelefone] = useState('')
  const [salvando, setSalvando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  useEffect(() => {
    setUsername(usuario?.username ?? '')
    setTelefone(usuario?.telefone ?? '')
  }, [usuario?.telefone, usuario?.username])

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (salvando) return
    setErro(null)
    setSalvando(true)
    try {
      const atualizado = await updatePublicProfile({ username, telefone })
      setUsername(atualizado.username)
      setTelefone(atualizado.telefone ?? '')
      await refresh()
      toast.success('Perfil atualizado com sucesso.')
    } catch (error) {
      const message =
        error instanceof PublicAuthApiError
          ? error.message
          : 'Não foi possível atualizar o perfil.'
      setErro(message)
      toast.error(message)
    } finally {
      setSalvando(false)
    }
  }

  return (
    <PainelShell
      title="Minha conta"
      description="Consulte seus dados e edite as informações disponíveis para esta conta."
    >
      <div className="pb-10">
        <Card className="mx-auto max-w-3xl border border-slate-200 shadow-sm">
          <CardContent className="p-6 md:p-8">
            <div className="flex items-center gap-3 border-b border-slate-100 pb-5">
              <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-pink-50 text-[#FC1EAD]">
                <UserCircleIcon className="h-6 w-6" />
              </span>
              <div>
                <h2 className="text-xl font-bold text-slate-900">Informações pessoais</h2>
                <p className="text-sm text-slate-500">Dados carregados da sua conta atual.</p>
              </div>
            </div>

            <form className="mt-6 space-y-5" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <Label htmlFor="perfil-username">Nome de usuário</Label>
                <div className="relative">
                  <UserCircleIcon className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="perfil-username"
                    name="username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    minLength={3}
                    maxLength={120}
                    autoComplete="username"
                    required
                    className="h-12 rounded-full pl-12 focus-visible:border-[#FC1EAD] focus-visible:ring-[#FC1EAD]/20"
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="perfil-email">E-mail</Label>
                <div className="relative">
                  <EnvelopeIcon className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="perfil-email"
                    value={usuario?.email ?? ''}
                    disabled
                    className="h-12 rounded-full bg-slate-50 pl-12"
                  />
                </div>
                <p className="text-xs text-slate-500">
                  A alteração de e-mail ainda não faz parte do contrato desta fase.
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="perfil-telefone">Telefone</Label>
                <div className="relative">
                  <PhoneIcon className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="perfil-telefone"
                    name="telefone"
                    type="tel"
                    value={telefone}
                    onChange={(event) => setTelefone(event.target.value)}
                    autoComplete="tel"
                    required
                    className="h-12 rounded-full pl-12 focus-visible:border-[#FC1EAD] focus-visible:ring-[#FC1EAD]/20"
                  />
                </div>
              </div>

              {erro ? (
                <p role="alert" className="rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
                  {erro}
                </p>
              ) : null}

              <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setUsername(usuario?.username ?? '')
                    setTelefone(usuario?.telefone ?? '')
                    setErro(null)
                  }}
                  className="min-h-11 rounded-2xl px-5"
                >
                  Cancelar
                </Button>
                <Button
                  type="submit"
                  disabled={salvando}
                  className="min-h-11 rounded-2xl bg-[#FC1EAD] px-5 text-white hover:bg-[#df1698]"
                >
                  {salvando ? 'Salvando...' : 'Salvar alterações'}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </PainelShell>
  )
}
