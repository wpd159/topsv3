"use client";

import { PublicAgeGateStories } from "../skeleton/PublicAgeGateStories";

type PublicStoriesGateProps = {
  slug: string;
  enabled: boolean;
};

export function PublicStoriesGate({ slug, enabled }: PublicStoriesGateProps) {
  return (
    <section className="public-stories-flow" aria-label="Stories protegidos">
      <div className="public-section-heading">
        <h2>Stories</h2>
        <p>Conteudo BLOQUEADO permanece protegido por confirmacao de idade.</p>
      </div>
      <PublicAgeGateStories slug={slug} enabled={enabled} />
    </section>
  );
}
