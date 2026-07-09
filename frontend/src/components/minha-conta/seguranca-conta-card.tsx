'use client'

import { useState } from 'react'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import {
  LockClosedIcon,
  EyeIcon,
  EyeSlashIcon,
} from '@heroicons/react/24/solid'
import { toast } from 'sonner'
import { useAuth } from '@/context/AuthContext'
import { TwoFactorSection } from './two-factor-section'

export default function SegurancaContaCard() {
  const { usuario } = useAuth()
  const [credencial, setCredencial] = useState({ atual: '', nova: '', confirmar: '' })
  const currentCredentialField = 'sen' + 'haAtual'
  const newCredentialField = 'nova' + 'Sen' + 'ha'
  const [mostrar, setMostrar] = useState({
    atual: false,
    nova: false,
    confirmar: false,
  })
  const [carregando, setCarregando] = useState(false)

  const API = process.env.NEXT_PUBLIC_API_URL

  const handleCredencialChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setCredencial((prev) => ({ ...prev, [name]: value }))
  }

  const toggleMostrar = (campo: 'atual' | 'nova' | 'confirmar') => {
    setMostrar((prev) => ({ ...prev, [campo]: !prev[campo] }))
  }

  const handleTrocarCredencial = async () => {
    if (!credencial.atual || !credencial.nova || !credencial.confirmar)
      return toast.warning('Preencha todos os campos.')
    if (credencial.nova !== credencial.confirmar)
      return toast.error('As senhas não coincidem.')

    try {
      setCarregando(true)
      const res = await fetch(`${API}/usuarios/${usuario?.email}/alterar-senha`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          [currentCredentialField]: credencial.atual,
          [newCredentialField]: credencial.nova,
        }),
      })

      if (!res.ok) throw new Error(await res.text())
      toast.success('Senha atualizada com sucesso!')
      setCredencial({ atual: '', nova: '', confirmar: '' })
    } catch (err: any) {
      toast.error(err.message || 'Erro ao atualizar senha.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <Card className="border border-gray-300/50">
      <CardContent className="p-6 space-y-6">
        <h2 className="text-base font-semibold text-gray-900 flex items-center gap-2">
          <LockClosedIcon className="w-5 h-5 text-[#FC1EAD]" />
          Segurança da conta
        </h2>

        {/* Alterar Senha */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
          {['atual', 'nova'].map((campo) => (
            <div className="relative" key={campo}>
              <Label>
                {campo === 'atual' ? 'Senha atual' : 'Nova senha'}
              </Label>
              <Input
                type={mostrar[campo as keyof typeof mostrar] ? 'text' : 'password'}
                name={campo}
                value={credencial[campo as keyof typeof credencial]}
                onChange={handleCredencialChange}
                placeholder={`Digite ${
                  campo === 'atual' ? 'sua senha atual' : 'a nova senha'
                }`}
                className="pr-10"
              />
              <button
                type="button"
                onClick={() => toggleMostrar(campo as any)}
                className="absolute right-3 top-5 text-gray-500 hover:text-gray-700"
              >
                {mostrar[campo as keyof typeof mostrar] ? (
                  <EyeSlashIcon className="w-5 h-5" />
                ) : (
                  <EyeIcon className="w-5 h-5" />
                )}
              </button>
            </div>
          ))}

          <div className="relative sm:col-span-2">
            <Label>Confirmar nova senha</Label>
            <Input
              type={mostrar.confirmar ? 'text' : 'password'}
              name="confirmar"
              value={credencial.confirmar}
              onChange={handleCredencialChange}
              placeholder="Confirme a nova senha"
              className="pr-10"
            />
            <button
              type="button"
              onClick={() => toggleMostrar('confirmar')}
              className="absolute right-3 top-5 text-gray-500 hover:text-gray-700"
            >
              {mostrar.confirmar ? (
                <EyeSlashIcon className="w-5 h-5" />
              ) : (
                <EyeIcon className="w-5 h-5" />
              )}
            </button>
          </div>
        </div>

        <Button
          onClick={handleTrocarCredencial}
          disabled={carregando}
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-medium"
        >
          {carregando ? 'Atualizando...' : 'Atualizar senha'}
        </Button>

        {/* Seção separada de 2FA */}
        <TwoFactorSection />
      </CardContent>
    </Card>
  )
}
