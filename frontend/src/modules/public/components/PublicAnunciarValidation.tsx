import type { SolicitarAnuncioValidationErrorDto } from "../../../lib/api/publicTypes";

type PublicAnunciarValidationProps = {
  errors: readonly SolicitarAnuncioValidationErrorDto[];
};

const FIELD_LABELS: Record<string, string> = {
  nomeExibicao: "Nome para exibição",
  email: "E-mail",
  whatsapp: "WhatsApp",
  uf: "Estado",
  cidade: "Cidade",
  bairro: "Bairro",
  titulo: "Título",
  descricao: "Descrição",
  preco: "Valor",
  categoria: "Categoria",
  aceiteTermos: "Termos",
  confirmacaoIdade: "Maioridade",
  payload: "Formulário"
};

const MESSAGE_BY_CODE: Record<string, string> = {
  CAMPO_OBRIGATORIO: "preencha este campo",
  TAMANHO_INVALIDO: "confira o tamanho do texto",
  EMAIL_NAO_RESERVADO: "informe um e-mail permitido nesta etapa",
  EMAIL_NAO_SINTETICO: "informe um e-mail permitido nesta etapa",
  WHATSAPP_INVALIDO: "informe um WhatsApp permitido nesta etapa",
  WHATSAPP_OBRIGATORIO: "informe um WhatsApp permitido nesta etapa",
  WHATSAPP_NAO_SINTETICO: "informe um WhatsApp permitido nesta etapa",
  UF_INVALIDA: "informe a sigla do estado",
  PRECO_OBRIGATORIO: "informe um valor",
  PRECO_INVALIDO: "informe um valor maior que zero",
  ACEITE_TERMOS_OBRIGATORIO: "confirme os termos",
  CONFIRMACAO_IDADE_OBRIGATORIA: "confirme a maioridade",
  TITULO_CONTATO_OU_REDE_SOCIAL: "não coloque contato ou rede social no título",
  TITULO_SPAM: "ajuste o título do anúncio",
  CAMPO_PERIGOSO: "remova informações que não pertencem a esta etapa",
  CAMPO_NAO_PERMITIDO: "remova informações que não pertencem a esta etapa",
  PAYLOAD_INVALIDO: "revise os dados do formulario",
  ERRO_ENVIO: "tente novamente em instantes",
  LOCALIDADE_SINTETICA_NAO_ENCONTRADA: "confira a localidade informada"
};

export function PublicAnunciarValidation({ errors }: PublicAnunciarValidationProps) {
  if (errors.length === 0) {
    return null;
  }

  return (
    <div className="public-form-feedback public-form-feedback-error" role="alert">
      <strong>Revise os campos destacados.</strong>
      <ul>
        {errors.map((error) => (
          <li key={`${error.campo}-${error.codigo}`}>
            <span>{fieldLabel(error.campo)}</span>
            {messageFor(error)}
          </li>
        ))}
      </ul>
    </div>
  );
}

function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? "Campo";
}

function messageFor(error: SolicitarAnuncioValidationErrorDto): string {
  return MESSAGE_BY_CODE[error.codigo] ?? error.mensagem;
}
