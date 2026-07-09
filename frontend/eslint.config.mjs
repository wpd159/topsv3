import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

const eslintConfig = [
  // Reset do frontend V3 (copia fiel do clone): o clone nunca teve ESLint
  // configurado (nem "eslint" nem "eslint-config-next" no package.json dele),
  // entao o codigo real nunca foi escrito para satisfazer regras estritas de
  // TypeScript. "next/typescript" (no-explicit-any, no-unused-vars estrito
  // etc.) gera centenas de avisos em codigo que e copia fiel, nao codigo novo
  // - corrigir tudo isso reescreveria trechos que devem ficar identicos ao
  // clone. Mantemos so "next/core-web-vitals" (regras reais de React/Next:
  // rules-of-hooks, no-img-element, etc.), que sinaliza problemas de verdade
  // sem impor estilo que o proprio clone nunca seguiu.
  ...compat.extends("next/core-web-vitals"),
];

export default eslintConfig;
