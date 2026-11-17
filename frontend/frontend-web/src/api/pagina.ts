import type { components } from './schema'

/** Metadados de paginação do contrato (`PagedModel`): `{ size, number, totalElements, totalPages }`. */
export type PageMetadata = components['schemas']['PageMetadata']

/**
 * Formato de toda listagem paginada da API. Usado pelos domínios cujos itens ainda são
 * tipados à mão; tag e tarefa usam o `PagedModel*` gerado direto.
 */
export type Pagina<T> = {
  content: T[]
  page: PageMetadata
}
