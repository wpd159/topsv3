import { Skeleton } from "@/components/ui/skeleton"

export function HeaderSkeleton() {
  return (
    <header className="flex items-center justify-between px-6 md:py-10 py-5 bg-white border-b">
      {/* Logo */}
      <div className="flex items-center gap-2">
        <Skeleton className="w-[140px] h-[48px] rounded-md" />
        <Skeleton className="w-10 h-4 rounded-md" />
      </div>

      {/* Links principais */}
      <div className="hidden md:flex items-center gap-8">
        <Skeleton className="w-20 h-5 rounded-md" />
        <Skeleton className="w-20 h-5 rounded-md" />
        <Skeleton className="w-20 h-5 rounded-md" />
      </div>

      {/* Botões principais */}
      <div className="hidden md:flex items-center gap-4">
        <Skeleton className="w-28 h-10 rounded-md" />
        <Skeleton className="w-28 h-10 rounded-md" />
        <Skeleton className="w-44 h-10 rounded-md" />
      </div>

      {/* Menu mobile */}
      <div className="md:hidden">
        <Skeleton className="w-10 h-10 rounded-md" />
      </div>
    </header>
  )
}
