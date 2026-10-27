import { describe, expect, it } from 'vitest'

import { ApiError } from '../api/authApi'
import { readApiJson } from './apiClient'

describe('readApiJson', () => {
  it('parseia JSON válido', async () => {
    const response = new Response(JSON.stringify({ ok: true }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
    await expect(readApiJson<{ ok: boolean }>(response)).resolves.toEqual({ ok: true })
  })

  it('detecta HTML da SPA e aponta para o proxy', async () => {
    const response = new Response(
      '<!doctype html><html><body><div id="root"></div></body></html>',
      {
        status: 200,
        headers: { 'Content-Type': 'text/html' },
      },
    )
    try {
      await readApiJson(response)
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).message).toMatch(/SPA|Ctrl\+Shift\+R|--build frontend/)
    }
  })

  it('detecta 502 Bad Gateway do nginx sem culpar o proxy', async () => {
    const response = new Response(
      '<html><head><title>502 Bad Gateway</title></head><body>Bad Gateway</body></html>',
      { status: 502, headers: { 'Content-Type': 'text/html' } },
    )
    try {
      await readApiJson(response)
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).message).toMatch(/Backend indisponível/)
    }
  })

  it('em 401 com corpo vazio pede novo login', async () => {
    const response = new Response('', { status: 401 })
    try {
      await readApiJson(response)
      expect.fail('deveria ter lançado ApiError')
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError)
      expect(error).toMatchObject({
        status: 401,
        message: 'Não autenticado. Faça login novamente.',
      })
    }
  })
})
