"use client"

export function BlogHeader() {
  return (
    <header className="w-full bg-white">
      <div className="w-full px-6 py-4">
        <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-gray-700">
          <a href="#" className="text-[#FC1EAD] border-b-2 border-[#FC1EAD] pb-1">
            Início
          </a>
          <a href="#">Notícias</a>
          <a href="#">Sexo e Sexualidade</a>
          <a href="#">Responsabilidade Social</a>
        </nav>

        <div className="w-full border-b absolute border-gray-200 mt-4" />
      </div>
    </header>
  )
}