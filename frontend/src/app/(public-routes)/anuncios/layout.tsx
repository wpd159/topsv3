import type { Metadata } from "next"

export const metadata: Metadata = {
  other: {
    rating: "RTA-5042-1996-1400-1577-RTA",
  },
}

export default function AnunciosLayout({ children }: { children: React.ReactNode }) {
  return children
}
