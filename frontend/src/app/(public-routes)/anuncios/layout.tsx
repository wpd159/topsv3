import type { Metadata } from "next"

export const metadata: Metadata = {
  other: {
    rating: "adult",
  },
}

export default function AnunciosLayout({ children }: { children: React.ReactNode }) {
  return children
}
