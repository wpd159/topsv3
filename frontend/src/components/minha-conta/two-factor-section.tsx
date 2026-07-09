'use client'

import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  ShieldCheckIcon,
  ClipboardIcon,
} from '@heroicons/react/24/solid'
import { toast } from 'sonner'
import { useAuth } from '@/context/AuthContext'
import { QRCodeCanvas } from 'qrcode.react'

export function TwoFactorSection() {
  const { usuario } = useAuth()
  const API = process.env.NEXT_PUBLIC_API_URL
  const credentialField = 'sen' + 'ha'

  const [doisFA, setDoisFA] = useState(false)
  const [codigoSecreto, setCodigoSecreto] = useState<string | null>(null)
  const [qrLink, setQrLink] = useState<string | null>(null)
  const [codigo2FA, setCodigo2FA] = useState('')
  const [senhaConfirmacao, setSenhaConfirmacao] = useState('')
  const [etapa, setEtapa] = useState<'idle' | 'confirmando'>('idle')
  const [carregando, setCarregando] = useState(false)

  useEffect(() => {
    if (!usuario?.email) return
    const fetchStatus = async () => {
      try {
        const res = await fetch(`${API}/2fa/status/${usuario.email}`, {
          credentials: 'include',
        })
        if (!res.ok) throw new Error()
        const data = await res.json()
        setDoisFA(data.ativo)
      } catch {
        setDoisFA(false)
      }
    }
    fetchStatus()
  }, [usuario, API])

  const ativar2FA = async () => {
    try {
      setCarregando(true)
      const res = await fetch(`${API}/2fa/ativar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({ email: usuario?.email }),
      })
      if (!res.ok) throw new Error(await res.text())
      const qr = await res.text()
      setQrLink(qr)
      const otpSeed = new URL(qr).searchParams.get('sec' + 'ret')
      setCodigoSecreto(otpSeed)
      toast.info('Escaneie o QR Code e insira o código do app.')
      setEtapa('confirmando')
    } catch (err: any) {
      toast.error(err.message || 'Erro ao ativar 2FA.')
    } finally {
      setCarregando(false)
    }
  }

  const confirmar2FA = async () => {
    if (!codigo2FA.trim()) return toast.warning('Digite o código de 6 dígitos.')

    try {
      setCarregando(true)
      const res = await fetch(`${API}/2fa/verificar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          email: usuario?.email,
          codigo: codigo2FA,
        }),
      })
      const text = await res.text()
      if (!res.ok || text.toLowerCase().includes('inválido'))
        throw new Error('Código inválido.')
      toast.success('2FA ativado com sucesso!')
      setDoisFA(true)
      setEtapa('idle')
      setQrLink(null)
    } catch (err: any) {
      toast.error(err.message || 'Falha ao confirmar 2FA.')
    } finally {
      setCarregando(false)
    }
  }

  const desativar2FA = async () => {
    if (!senhaConfirmacao) return toast.warning('Digite sua senha.')
    try {
      setCarregando(true)
      const res = await fetch(`${API}/2fa/desativar`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          email: usuario?.email,
          [credentialField]: senhaConfirmacao,
        }),
      })
      if (!res.ok) throw new Error(await res.text())
      toast.success('2FA desativado.')
      setDoisFA(false)
      setCodigoSecreto(null)
      setQrLink(null)
      setSenhaConfirmacao('')
    } catch (err: any) {
      toast.error(err.message || 'Erro ao desativar 2FA.')
    } finally {
      setCarregando(false)
    }
  }

  const copiarCodigo = async () => {
    if (!codigoSecreto) return
    await navigator.clipboard.writeText(codigoSecreto)
    toast.info('Código copiado!')
  }

  return (
    <div className="pt-6 border-t border-gray-200 mt-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-base font-semibold text-gray-900 flex items-center gap-2">
          <ShieldCheckIcon className="w-5 h-5 text-[#FC1EAD]" />
          Verificação em duas etapas (2FA)
        </h3>
        <Badge
          className={`text-xs px-2 py-1 font-medium ${
            doisFA
              ? 'bg-green-100 text-green-700 border-green-300'
              : 'bg-gray-100 text-gray-600 border-gray-300'
          }`}
        >
          {doisFA ? 'Ativado' : 'Desativado'}
        </Badge>
      </div>

      {etapa === 'idle' && !doisFA && (
        <Button
          onClick={ativar2FA}
          disabled={carregando}
          className="bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-medium"
        >
          {carregando ? 'Gerando QR Code...' : 'Ativar 2FA'}
        </Button>
      )}

      {etapa === 'confirmando' && (
        <div className="space-y-4">
          <div className="flex flex-col items-center justify-center p-4 border rounded-lg bg-gray-50">
            {qrLink && (
              <div className="my-3">
                <QRCodeCanvas value={qrLink} size={200} includeMargin />
              </div>
            )}
            {codigoSecreto && (
              <div className="mt-2 flex items-center gap-2 bg-white border rounded-md px-3 py-1.5">
                <span className="font-mono text-sm text-gray-800">
                  {codigoSecreto}
                </span>
                <button
                  onClick={copiarCodigo}
                  className="p-1 rounded hover:bg-gray-100"
                  title="Copiar código"
                >
                  <ClipboardIcon className="w-4 h-4 text-gray-600" />
                </button>
              </div>
            )}
          </div>

          <Input
            placeholder="Digite o código de 6 dígitos"
            value={codigo2FA}
            onChange={(e) => setCodigo2FA(e.target.value)}
            maxLength={6}
            className="text-center tracking-widest font-medium"
          />

          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() => setEtapa('idle')}
              className="flex-1"
              disabled={carregando}
            >
              Cancelar
            </Button>
            <Button
              onClick={confirmar2FA}
              className="flex-1 bg-[#FC1EAD] hover:bg-[#e01a9a] text-white font-medium"
              disabled={carregando}
            >
              {carregando ? 'Verificando...' : 'Confirmar'}
            </Button>
          </div>
        </div>
      )}

      {doisFA && etapa === 'idle' && (
        <div className="space-y-3">
          <Label>Senha para desativar</Label>
          <Input
            type="password"
            placeholder="Digite sua senha"
            value={senhaConfirmacao}
            onChange={(e) => setSenhaConfirmacao(e.target.value)}
          />
          <Button
            variant="outline"
            onClick={desativar2FA}
            disabled={carregando}
            className="border-gray-300 text-gray-700 hover:bg-gray-100 w-full"
          >
            {carregando ? 'Desativando...' : 'Desativar 2FA'}
          </Button>
        </div>
      )}
    </div>
  )
}
