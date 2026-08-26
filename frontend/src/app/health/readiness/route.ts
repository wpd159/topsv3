import { NextResponse } from 'next/server'
import { probeInternalBackendReadiness } from '@/lib/server-health'

export const dynamic = 'force-dynamic'

export async function GET() {
  const internalApiReady = await probeInternalBackendReadiness()
  return NextResponse.json(
    {
      status: internalApiReady ? 'UP' : 'DOWN',
      app: 'topsdojob-v3-frontend',
      components: {
        application: 'UP',
        'internal-api': internalApiReady ? 'UP' : 'DOWN',
      },
    },
    {
      status: internalApiReady ? 200 : 503,
      headers: { 'Cache-Control': 'no-store' },
    },
  )
}
