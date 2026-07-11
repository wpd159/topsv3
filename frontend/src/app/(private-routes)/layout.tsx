import { PublicChrome } from '@/components/layout/public-chrome'
import { PrivateSessionGuard } from '@/components/auth/private-session-guard'

export default function PrivateRoutesLayout({ children }: { children: React.ReactNode }) {
  return (
    <PublicChrome>
      <PrivateSessionGuard>{children}</PrivateSessionGuard>
    </PublicChrome>
  )
}
