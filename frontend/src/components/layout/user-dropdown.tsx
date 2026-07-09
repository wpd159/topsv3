'use client'

import { useState, useRef, useEffect } from "react"
import { useRouter, usePathname } from "next/navigation"
import {
  UserCircleIcon,
  ChevronDownIcon,
  Cog6ToothIcon,
  RectangleStackIcon,
  HeartIcon,
  ChatBubbleLeftRightIcon,
  ShieldCheckIcon,
  BanknotesIcon,
  ArrowRightOnRectangleIcon,
} from "@heroicons/react/24/solid"
import { useAuth } from "@/context/AuthContext"

export default function UserDropdown({ usuario, novasMensagens, setNovasMensagens }: any) {
  const router = useRouter()
  const pathname = usePathname()
  const [open, setOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  const cargo = usuario?.cargo || "Usuário"
  const nome = usuario?.username || "Usuário"
  const role = usuario?.cargo?.toUpperCase() || ""
  const isAdmin = ["ADMIN", "MODERADOR"].includes(role)
  const { logout } = useAuth()

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener("mousedown", handleClickOutside)
    return () => document.removeEventListener("mousedown", handleClickOutside)
  }, [])

  return (
    <div ref={dropdownRef} className="relative">
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2 text-[#C41E73] font-medium text-sm hover:opacity-90 transition"
      >
        <UserCircleIcon className="w-7 h-7 text-[#C41E73]" />
        <span className="truncate max-w-[120px]">{nome}</span>
        <ChevronDownIcon className="w-4 h-4 text-gray-500" />
      </button>

      {open && (
        <div className="absolute right-0 mt-3 w-56 bg-white border border-gray-100 rounded-xl shadow-lg z-50">
          <ul className="text-sm text-gray-700 py-2">
            <li className="px-4 py-2 text-xs text-gray-400 uppercase border-b">{cargo}</li>

            <DropdownItem icon={Cog6ToothIcon} label="Minha Conta" onClick={() => router.push("/minha-conta")} />
            <DropdownItem icon={RectangleStackIcon} label="Meus Anúncios" onClick={() => router.push("/meus-anuncios")} />
            <DropdownItem icon={BanknotesIcon} label="Créditos" onClick={() => router.push("/creditos")} />
            <DropdownItem icon={HeartIcon} label="Favoritos" onClick={() => router.push("/favoritos")} />

            <li
              className="relative flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer"
              onClick={() => {
                router.push("/chat")
                setNovasMensagens(0)
              }}
            >
              <ChatBubbleLeftRightIcon className="w-5 h-5 text-gray-500" />
              Chat
              {novasMensagens > 0 && (
                <span className="ml-auto bg-[#FC1EAD] text-white text-[10px] font-bold w-5 h-5 rounded-full flex items-center justify-center animate-pulse">
                  {novasMensagens}
                </span>
              )}
            </li>

            {isAdmin && (
              <DropdownItem
                icon={ShieldCheckIcon}
                label="Painel Administrativo"
                onClick={() => router.push("/admin/dashboard")}
                highlight
              />
            )}

            <li className="border-t my-1"></li>

            <li
              className="flex items-center gap-2 px-4 py-2 text-red-600 hover:bg-red-50 cursor-pointer"
              onClick={() => {
                logout()
                router.push("/")
              }}
            >
              <ArrowRightOnRectangleIcon className="w-5 h-5 text-red-500" />
              Sair
            </li>
          </ul>
        </div>
      )}
    </div>
  )
}

function DropdownItem({ icon: Icon, label, onClick, highlight }: any) {
  return (
    <li
      className={`flex items-center gap-2 px-4 py-2 hover:bg-gray-50 cursor-pointer ${
        highlight ? "text-[#C41E73] font-medium" : ""
      }`}
      onClick={onClick}
    >
      <Icon className="w-5 h-5 text-gray-500" />
      {label}
    </li>
  )
}
