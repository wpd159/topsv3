import Image from "next/image";
import Link from "next/link";

type CategoryCard = {
  title: string;
  description: string;
  image: string;
  href: string;
};

const categories: readonly CategoryCard[] = [
  {
    title: "Acompanhantes Femininas",
    description: "Encontre as melhores acompanhantes femininas do Brasil.",
    image: "/cards/acompanhante-feminina.jpg",
    href: "/acompanhantes/go/goiania"
  },
  {
    title: "Sexo Virtual",
    description: "Videochamadas, conteúdo exclusivo e atendimento online.",
    image: "/cards/casual.jpg",
    href: "/anuncios/demo-goiania-livre-premium"
  },
  {
    title: "Acompanhantes Masculinos",
    description: "Os mais desejados acompanhantes masculinos estão aqui.",
    image: "/cards/acompanhante-masculino.jpg",
    href: "/acompanhantes/go/goiania"
  },
  {
    title: "Transex e Travestis",
    description: "As mais lindas transex e travestis do país te esperam.",
    image: "/cards/acompanhante-trans.jpg",
    href: "/acompanhantes/go/goiania"
  },
  {
    title: "Massagens",
    description: "Relaxe com massagistas experientes e sensuais.",
    image: "/cards/massagem.jpg",
    href: "/acompanhantes/go/goiania/setor-bueno"
  }
];

export function PublicCategorySection() {
  return (
    <section className="public-category-section" aria-labelledby="public-category-title">
      <h2 id="public-category-title">Categorias em destaque</h2>
      <div className="public-category-grid">
        {categories.map((category) => (
          <Link className="public-category-card" href={category.href} key={category.title}>
            <span className="public-category-image">
              <Image
                src={category.image}
                alt=""
                fill
                sizes="(max-width: 760px) 728px, (max-width: 1120px) 50vw, 280px"
              />
              <span className="public-category-title">{category.title}</span>
            </span>
            <span className="public-category-body">
              <small>{category.description}</small>
              <em>Ver mais</em>
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
