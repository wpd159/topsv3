export const getCookie = (name: string) => {
  if (typeof document === 'undefined') return ''
  const m = document.cookie.split('; ').find((r) => r.startsWith(`${name}=`))
  return m ? decodeURIComponent(m.split('=')[1]) : ''
}