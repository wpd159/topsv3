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
    title: "Acompanhante feminina",
    description: "Perfis femininos em destaque para navegar por cidade e bairro.",
    image: "/cards/acompanhante-feminina.jpg",
    href: "/acompanhantes/go/goiania"
  },
  {
    title: "Transex e travestis",
    description: "Categorias preservadas visualmente para futura integração segura.",
    image: "/cards/acompanhante-trans.jpg",
    href: "/acompanhantes/go/goiania"
  },
  {
    title: "Massagens",
    description: "Caminhos públicos preparados para listagens e filtros da V3.",
    image: "/cards/massagem.jpg",
    href: "/acompanhantes/go/goiania/setor-bueno"
  },
  {
    title: "Encontros casuais",
    description: "Atalhos visuais sem chamada ao backend antigo da produção.",
    image: "/cards/casual.jpg",
    href: "/anuncios/demo-goiania-livre-premium"
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
                sizes="(max-width: 760px) 100vw, (max-width: 1120px) 50vw, 280px"
              />
            </span>
            <span className="public-category-body">
              <strong>{category.title}</strong>
              <small>{category.description}</small>
              <em>Ver mais</em>
            </span>
          </Link>
        ))}
      </div>
    </section>
  );
}
