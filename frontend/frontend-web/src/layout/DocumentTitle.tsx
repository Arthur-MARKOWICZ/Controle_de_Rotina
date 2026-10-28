import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

import { tituloPorRota } from './documentTitle'

export function DocumentTitle() {
  const { pathname } = useLocation()

  useEffect(() => {
    document.title = tituloPorRota(pathname)
  }, [pathname])

  return null
}
