import {
  AdjustmentsHorizontalIcon,
  BanknotesIcon,
  ChartPieIcon,
  ClipboardDocumentCheckIcon,
  DocumentDuplicateIcon,
  DocumentTextIcon,
  ExclamationTriangleIcon,
  LifebuoyIcon,
  MegaphoneIcon,
  PhotoIcon,
  PlayCircleIcon,
  RectangleStackIcon,
  ShieldCheckIcon,
  SparklesIcon,
  UsersIcon,
} from '@heroicons/react/24/solid'
import { ReactNode } from 'react'

export type SidebarSectionKey =
  | 'Visão Geral'
  | 'Operação'
  | 'Monetização'
  | 'Moderação e Segurança'
  | 'Conteúdo e Comunicação'
  | 'Configurações'

export type SidebarLink = {
  label: string
  icon: ReactNode
  href: string
  section: SidebarSectionKey
  notificationKey?: 'tickets' | 'denuncias' | 'sugestoes' | 'revisoes'
}

export const sidebarLinks: SidebarLink[] = [
  {
    label: 'Dashboard',
    icon: <ChartPieIcon className="h-5 w-5" />,
    href: '/admin/dashboard',
    section: 'Visão Geral',
  },
  {
    label: 'Progresso do wizard',
    icon: <ChartPieIcon className="h-5 w-5" />,
    href: '/admin/wizard-progress',
    section: 'Visão Geral',
  },
  {
    label: 'Anúncios',
    icon: <RectangleStackIcon className="h-5 w-5" />,
    href: '/admin/anuncios',
    section: 'Operação',
  },
  {
    label: 'Gestão de Stories',
    icon: <PlayCircleIcon className="h-5 w-5" />,
    href: '/admin/stories',
    section: 'Operação',
  },
  {
    label: 'Usuários',
    icon: <UsersIcon className="h-5 w-5" />,
    href: '/admin/usuarios',
    section: 'Operação',
  },
  {
    label: 'Tickets',
    icon: <LifebuoyIcon className="h-5 w-5" />,
    href: '/admin/tickets',
    section: 'Operação',
    notificationKey: 'tickets',
  },
  {
    label: 'Denúncias',
    icon: <ExclamationTriangleIcon className="h-5 w-5" />,
    href: '/admin/denuncias',
    section: 'Operação',
    notificationKey: 'denuncias',
  },
  {
    label: 'Relatórios de receita',
    icon: <BanknotesIcon className="h-5 w-5" />,
    href: '/admin/financeiro',
    section: 'Monetização',
  },
  {
    label: 'Monetização',
    icon: <SparklesIcon className="h-5 w-5" />,
    href: '/admin/creditos',
    section: 'Monetização',
  },
  {
    label: 'Compliance',
    icon: <AdjustmentsHorizontalIcon className="h-5 w-5" />,
    href: '/admin/compliance',
    section: 'Moderação e Segurança',
  },
  {
    label: 'Registros',
    icon: <DocumentTextIcon className="h-5 w-5" />,
    href: '/admin/registros',
    section: 'Moderação e Segurança',
  },
  {
    label: 'Logs visitantes',
    icon: <DocumentTextIcon className="h-5 w-5" />,
    href: '/admin/compliance#visitor-logs',
    section: 'Moderação e Segurança',
  },
  {
    label: 'Documentos visitantes',
    icon: <DocumentTextIcon className="h-5 w-5" />,
    href: '/admin/compliance#visitor-document-fallback',
    section: 'Moderação e Segurança',
  },
  {
    label: 'Avisos',
    icon: <ClipboardDocumentCheckIcon className="h-5 w-5" />,
    href: '/admin/avisos',
    section: 'Conteúdo e Comunicação',
  },
  {
    label: 'Textos do site',
    icon: <DocumentTextIcon className="h-5 w-5" />,
    href: '/admin/termos-footer',
    section: 'Conteúdo e Comunicação',
  },
  {
    label: 'Categorias da home',
    icon: <RectangleStackIcon className="h-5 w-5" />,
    href: '/admin/categorias-home',
    section: 'Conteúdo e Comunicação',
  },
  {
    label: 'Blog',
    icon: <PhotoIcon className="h-5 w-5" />,
    href: '/admin/blog',
    section: 'Conteúdo e Comunicação',
  },
  {
    label: 'FAQs',
    icon: <DocumentDuplicateIcon className="h-5 w-5" />,
    href: '/admin/faqs',
    section: 'Conteúdo e Comunicação',
  },
  {
    label: 'Sugestões',
    icon: <MegaphoneIcon className="h-5 w-5" />,
    href: '/admin/sugestoes',
    section: 'Conteúdo e Comunicação',
    notificationKey: 'sugestoes',
  },
  {
    label: 'Gerenciar staff',
    icon: <ShieldCheckIcon className="h-5 w-5" />,
    href: '/admin/staff',
    section: 'Configurações',
  },
]
