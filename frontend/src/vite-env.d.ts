/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Where the API is, when it is not on the same address as the web app, for
   * example https://forgeai-api.onrender.com. Read at build time. Left unset in
   * development, where the Vite dev server forwards /api to the local service.
   */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
