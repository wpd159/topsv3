import type { Metadata } from 'next'
import { PublicChrome } from '@/components/layout/public-chrome'

export const metadata: Metadata = {
  other: {
    rating: 'adult',
  },
}

export default function PublicRoutesLayout({ children }: { children: React.ReactNode }) {
  return <PublicChrome>{children}</PublicChrome>
}
