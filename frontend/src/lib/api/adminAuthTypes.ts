export type AdminMeDto = {
  autenticado: boolean;
  usuarioId: string;
  nome: string | null;
  email: string | null;
  papeis: readonly string[];
  permissoes: readonly string[];
};

export type AdminPermissionDto = {
  codigo: string;
  descricao: string;
};

export type AdminPermissionsDto = {
  permissoes: readonly AdminPermissionDto[];
};

export type AdminAuthStatusDto = {
  autenticado: boolean;
  status: string;
};

export type AdminAuthResponse<T> =
  | {
      ok: true;
      data: T;
      status: number;
      requestId: string;
    }
  | {
      ok: false;
      data: null;
      status: number;
      requestId: string;
      message: string;
    };
