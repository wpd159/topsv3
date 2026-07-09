'use client'

import { cn } from "@/lib/utils"
import { ReactNode } from "react"

interface DashboardStatCardProps {
  label: string
  value: string | number
  icon: ReactNode
  className?: string
  color?: 'pink' | 'green' | 'blue' | 'yellow'
}

export function DashboardStatCard({
  label,
  value,
  icon,
  className,
  color = 'pink',
}: DashboardStatCardProps) {
  const colorMap = {
    pink: 'from-[#FC1EAD]/10 to-transparent text-[#FC1EAD]',
    green: 'from-green-100 to-transparent text-green-600',
    blue: 'from-blue-100 to-transparent text-blue-600',
    yellow: 'from-yellow-100 to-transparent text-yellow-600',
  }

  return (
    <div
      className={cn(
        "bg-white border border-gray-100 rounded-xl shadow-sm hover:shadow-md transition-all duration-200 overflow-hidden",
        className
      )}
    >
      {/* Header com gradiente */}
      <div
        className={cn(
          "px-4 py-2 border-b bg-gradient-to-r",
          colorMap[color]
        )}
      >
        <div className="flex items-center justify-between">
          <span className="text-xs font-medium text-gray-500">{label}</span>
          <div className="p-2 rounded-md bg-white/70 border border-gray-100">
            {icon}
          </div>
        </div>
      </div>

      {/* Valor principal */}
      <div className="px-4 py-4 flex flex-col">
        <span className="text-2xl font-semibold text-gray-800 leading-none">
          {value}
        </span>
      </div>
    </div>
  )
}
