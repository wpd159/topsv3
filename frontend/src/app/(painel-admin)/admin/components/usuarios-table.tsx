'use client'

import { useRouter } from 'next/navigation'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  BanknotesIcon,
  EyeIcon,
  FolderOpenIcon,
  PowerIcon,
} from '@heroicons/react/24/solid'
import { formatCPF } from '@/utils/formatter'
import {
  type AdminUsuario,
  formatarDataHoraBR,
  formatarLocalUsuario,
  formatarTelefoneExibicao,
  getTipoUsuarioMeta,
  getUsuarioHandle,
  getUsuarioNomePrincipal,
} from './admin-usuarios-utils'

type Props = {
  usuarios: AdminUsuario[]
  loading?: boolean
  onToggleStatus: (usuario: AdminUsuario) => void
  onAddCredit: (usuario: AdminUsuario) => void
}

export default function GerenciarUsuariosTable({
  usuarios,
  loading = false,
  onToggleStatus,
  onAddCredit,
}: Props) {
  const router = useRouter()

  const irParaDetalhes = (id: number) => router.push(`/admin/usuarios/${id}`)
  const irParaAnuncios = (id: number) => router.push(`/admin/usuarios/${id}#anuncios`)

  if (loading) {
    return <div className="py-10 text-center text-gray-500">Carregando usuários...</div>
  }

  if (usuarios.length === 0) {
    return <div className="py-10 text-center text-gray-400">Nenhum usuário encontrado.</div>
  }

  return (
    <div className="overflow-hidden rounded-2xl border border-gray-100 bg-white shadow-sm">
      <div className="border-b bg-gradient-to-r from-[#FC1EAD]/10 to-transparent px-5 py-4">
        <h3 className="text-base font-semibold text-gray-800">Base de usuários</h3>
        <p className="mt-1 text-xs text-gray-500">
          Gestão cadastral, comercial e operacional sem misturar métricas de performance dos anúncios.
        </p>
      </div>

      <div className="hidden overflow-x-auto lg:block">
        <Table>
          <TableHeader>
            <TableRow className="border-b bg-gray-50/70 text-[11px] uppercase tracking-wider text-gray-500">
              <TableHead className="px-6 py-3 font-semibold">Usuário</TableHead>
              <TableHead className="px-6 py-3 font-semibold">Local</TableHead>
              <TableHead className="px-6 py-3 font-semibold text-center">Anúncios</TableHead>
              <TableHead className="px-6 py-3 font-semibold text-center">Status</TableHead>
              <TableHead className="px-6 py-3 font-semibold text-center">Tipo</TableHead>
              <TableHead className="px-6 py-3 font-semibold text-right">Ações</TableHead>
            </TableRow>
          </TableHeader>

          <TableBody>
            {usuarios.map((usuario, index) => {
              const ativo = usuario.status === 'ATIVO'
              const tipo = getTipoUsuarioMeta(usuario)
              const anunciosAtivos = Number(usuario.anunciosAtivos || 0)
              const anunciosPendentes = Number(usuario.anunciosPendentes || 0)

              return (
                <TableRow
                  key={usuario.id}
                  className={`${index % 2 === 0 ? 'bg-white' : 'bg-gray-50/40'} transition-all hover:bg-[#FC1EAD]/5`}
                >
                  <TableCell className="px-6 py-4">
                    <div className="space-y-1">
                      <div className="font-medium text-gray-900">{getUsuarioNomePrincipal(usuario)}</div>
                      <div className="text-xs text-gray-500">
                        {getUsuarioHandle(usuario) || '—'}
                        {usuario.email ? <span className="ml-2">{usuario.email}</span> : null}
                      </div>
                      <div className="text-xs text-gray-400">
                        {usuario.cpf ? formatCPF(usuario.cpf) : 'CPF —'}
                        {usuario.telefone ? <span className="ml-2">{formatarTelefoneExibicao(usuario.telefone)}</span> : null}
                      </div>
                      <div className="text-xs text-gray-400">
                        Cadastro {formatarDataHoraBR(usuario.criadoEm ?? usuario.dataCadastro)}
                      </div>
                    </div>
                  </TableCell>

                  <TableCell className="px-6 text-sm text-gray-700">
                    {formatarLocalUsuario(usuario)}
                  </TableCell>

                  <TableCell className="px-6 text-center">
                    <div className="space-y-1">
                      <div className="font-semibold text-gray-900">{usuario.totalAnuncios || 0}</div>
                      <div className="text-xs text-gray-500">
                        {anunciosAtivos} ativos
                        {anunciosPendentes > 0 ? ` · ${anunciosPendentes} pendentes` : ''}
                      </div>
                    </div>
                  </TableCell>

                  <TableCell className="px-6 text-center">
                    <Badge
                      variant="outline"
                      className={`rounded-md border px-2 py-1 text-[11px] font-medium ${
                        ativo
                          ? 'border-green-300 bg-green-100 text-green-700'
                          : 'border-red-300 bg-red-100 text-red-700'
                      }`}
                    >
                      {ativo ? 'Ativo' : 'Inativo'}
                    </Badge>
                  </TableCell>

                  <TableCell className="px-6 text-center">
                    <Badge variant="outline" className={`rounded-md border px-2 py-1 text-[11px] font-medium ${tipo.className}`}>
                      {tipo.label}
                    </Badge>
                  </TableCell>

                  <TableCell className="px-6">
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="border-gray-300 text-gray-700 hover:bg-gray-100"
                        onClick={() => irParaAnuncios(usuario.id)}
                      >
                        <FolderOpenIcon className="mr-1 h-4 w-4" />
                        Anúncios
                      </Button>

                      <Button
                        size="sm"
                        variant="outline"
                        className="border-[#FC1EAD]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                        onClick={() => onAddCredit(usuario)}
                      >
                        <BanknotesIcon className="mr-1 h-4 w-4" />
                        Crédito
                      </Button>

                      <Button
                        size="sm"
                        variant="outline"
                        className="border-[#C41E73]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                        onClick={() => irParaDetalhes(usuario.id)}
                      >
                        <EyeIcon className="mr-1 h-4 w-4" />
                        Ver
                      </Button>

                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={() => onToggleStatus(usuario)}
                        className="rounded-md border border-gray-200 text-gray-500 hover:bg-gray-100"
                        title={ativo ? 'Desativar usuário' : 'Ativar usuário'}
                      >
                        <PowerIcon className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>

      <div className="grid gap-4 p-4 lg:hidden">
        {usuarios.map((usuario) => {
          const ativo = usuario.status === 'ATIVO'
          const tipo = getTipoUsuarioMeta(usuario)

          return (
            <div key={usuario.id} className="rounded-xl border border-gray-100 bg-white p-4 shadow-sm">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="space-y-1">
                  <div className="font-semibold text-gray-900">{getUsuarioNomePrincipal(usuario)}</div>
                  <div className="text-xs text-gray-500">
                    {getUsuarioHandle(usuario) || '—'}
                    {usuario.email ? <span className="ml-2">{usuario.email}</span> : null}
                  </div>
                  <div className="text-xs text-gray-500">{formatarLocalUsuario(usuario)}</div>
                  <div className="text-xs text-gray-400">
                    {usuario.telefone ? formatarTelefoneExibicao(usuario.telefone) : 'Telefone —'}
                  </div>
                  <div className="text-xs text-gray-400">
                    Cadastro {formatarDataHoraBR(usuario.criadoEm ?? usuario.dataCadastro)}
                  </div>
                </div>

                <div className="flex flex-col items-end gap-2">
                  <Badge
                    variant="outline"
                    className={`rounded-md border px-2 py-1 text-[11px] font-medium ${
                      ativo
                        ? 'border-green-300 bg-green-100 text-green-700'
                        : 'border-red-300 bg-red-100 text-red-700'
                    }`}
                  >
                    {ativo ? 'Ativo' : 'Inativo'}
                  </Badge>
                  <Badge variant="outline" className={`rounded-md border px-2 py-1 text-[11px] font-medium ${tipo.className}`}>
                    {tipo.label}
                  </Badge>
                </div>
              </div>

              <div className="mt-3 grid grid-cols-2 gap-3 rounded-xl bg-gray-50 p-3 text-sm">
                <div>
                  <p className="text-[11px] uppercase tracking-wide text-gray-500">Anúncios</p>
                  <p className="font-semibold text-gray-900">{usuario.totalAnuncios || 0}</p>
                </div>
                <div>
                  <p className="text-[11px] uppercase tracking-wide text-gray-500">Créditos</p>
                  <p className="font-semibold text-[#C41E73]">{usuario.totalCreditos || 0}</p>
                </div>
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                <Button size="sm" variant="outline" onClick={() => irParaAnuncios(usuario.id)}>
                  <FolderOpenIcon className="mr-1 h-4 w-4" />
                  Anúncios
                </Button>
                <Button
                  size="sm"
                  variant="outline"
                  className="border-[#FC1EAD]/40 text-[#C41E73] hover:bg-[#FC1EAD]/10"
                  onClick={() => onAddCredit(usuario)}
                >
                  <BanknotesIcon className="mr-1 h-4 w-4" />
                  Crédito
                </Button>
                <Button size="sm" variant="outline" onClick={() => irParaDetalhes(usuario.id)}>
                  <EyeIcon className="mr-1 h-4 w-4" />
                  Ver
                </Button>
                <Button size="sm" variant="outline" onClick={() => onToggleStatus(usuario)}>
                  <PowerIcon className="mr-1 h-4 w-4" />
                  {ativo ? 'Desativar' : 'Ativar'}
                </Button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
