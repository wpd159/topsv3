import { NextResponse } from 'next/server'

export const dynamic = 'force-dynamic'

export function GET() {
  return NextResponse.json(
    {
      status: 'UP',
      app: 'topsdojob-v3-frontend',
      components: { application: 'UP' },
    },
    {
      status: 200,
      headers: { 'Cache-Control': 'no-store' },
    },
  )
}
