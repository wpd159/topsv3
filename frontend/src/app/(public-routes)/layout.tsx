import { PublicChrome } from '@/components/layout/public-chrome'

export default function PublicRoutesLayout({ children }: { children: React.ReactNode }) {
  return <PublicChrome>{children}</PublicChrome>
}
