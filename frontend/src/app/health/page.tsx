import { ApiClientError, fetchLocalApi } from "../../lib/api/client";
import { publicEnv } from "../../lib/config/publicEnv";

type HealthResponse = {
  status: string;
  app: string;
  environment: string;
  efiPixMockMode: boolean;
  requestId: string;
};

type BackendHealth =
  | { kind: "online"; result: Awaited<ReturnType<typeof fetchLocalApi<HealthResponse>>> }
  | { kind: "offline"; message: string; requestId?: string };

async function readBackendHealth(): Promise<BackendHealth> {
  try {
    return {
      kind: "online",
      result: await fetchLocalApi<HealthResponse>("/api/health")
    };
  } catch (error) {
    if (error instanceof ApiClientError) {
      return {
        kind: "offline",
        message: error.message,
        requestId: error.requestId
      };
    }
    return {
      kind: "offline",
      message: "API local indisponível."
    };
  }
}

export default async function HealthPage() {
  const backendHealth = await readBackendHealth();

  return (
    <main>
      <section className="shell">
        <span className="status">LOCAL</span>
        <h1>Health local</h1>
        <dl className="health-grid">
          <div>
            <dt>Frontend</dt>
            <dd>UP</dd>
          </div>
          <div>
            <dt>Ambiente</dt>
            <dd>{publicEnv.appEnv}</dd>
          </div>
          <div>
            <dt>API local</dt>
            <dd>{publicEnv.apiBaseUrl}</dd>
          </div>
          <div>
            <dt>Backend</dt>
            <dd>{backendHealth.kind === "online" ? backendHealth.result.data.status : "OFFLINE"}</dd>
          </div>
        </dl>

        {backendHealth.kind === "online" ? (
          <div className="panel">
            <p>Contrato `/api/health` respondido pela API local.</p>
            <dl className="health-grid compact">
              <div>
                <dt>Aplicação</dt>
                <dd>{backendHealth.result.data.app}</dd>
              </div>
              <div>
                <dt>Mock Pix</dt>
                <dd>{backendHealth.result.data.efiPixMockMode ? "ativo" : "inativo"}</dd>
              </div>
              <div>
                <dt>Status HTTP</dt>
                <dd>{backendHealth.result.status}</dd>
              </div>
              <div>
                <dt>Request ID</dt>
                <dd>{backendHealth.result.requestId}</dd>
              </div>
            </dl>
          </div>
        ) : (
          <div className="panel muted">
            <p>{backendHealth.message}</p>
            {backendHealth.requestId ? <p>Request ID local: {backendHealth.requestId}</p> : null}
          </div>
        )}
      </section>
    </main>
  );
}
