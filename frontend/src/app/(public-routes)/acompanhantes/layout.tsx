import type { Metadata } from "next"

export const metadata: Metadata = {
  other: {
    rating: "adult",
  },
}

export default function AcompanhantesLayout({ children }: { children: React.ReactNode }) {
  return children
}
